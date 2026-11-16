package app.feedgateway;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.PingMessage;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;

/**
 * THE LIVENESS PING, and why it goes through the queue rather than around it.
 *
 * <p>Until 2026-11-16 nothing in this gateway ever asked a client whether it was still there.
 * {@code afterConnectionClosed} and {@code handleTransportError} detach correctly — they simply never
 * fire when a client VANISHES without closing the connection: a browser quit, a laptop asleep, a
 * network dropped. The socket stayed counted against the account's cap for the life of the pod. That
 * day eight slots were held by browsers that no longer existed, several {@code openFor=5546s}, and the
 * owner's next connection was refused on a page that was working.
 *
 * <p>The write deadline does not catch it: a dead TCP peer absorbs writes into the kernel send buffer,
 * so the send neither fails nor blocks — those eight sat there for an hour and a half while the gateway
 * happily broadcast to them.
 */
class SocketLivenessPingTest {

    static final class Metrics implements OutboundChannel.Metrics {
        final AtomicInteger coalesced = new AtomicInteger();
        final AtomicInteger sent = new AtomicInteger();
        public void enqueued(int bytes) {}
        public void coalesced() { coalesced.incrementAndGet(); }
        public void sent(int bytes) { sent.incrementAndGet(); }
        public void disconnectedSlow() {}
        public void writeError() {}
        public void droppedOnClose(int n) {}
    }

    /** Records every frame the drainer actually writes, in order. */
    static final class Wire {
        final List<WebSocketMessage<?>> frames = new ArrayList<>();
        WebSocketSession session() {
            WebSocketSession s = mock(WebSocketSession.class);
            when(s.getId()).thenReturn("liveness-fixture");
            when(s.isOpen()).thenReturn(true);
            try {
                doAnswer(inv -> { frames.add(inv.getArgument(0)); return null; }).when(s).sendMessage(any());
            } catch (Exception e) {
                throw new AssertionError(e);
            }
            return s;
        }
        List<Class<?>> kinds() {
            List<Class<?>> out = new ArrayList<>();
            for (WebSocketMessage<?> f : frames) out.add(f.getClass());
            return out;
        }
    }

    private static OutboundChannel channel(WebSocketSession s, java.util.concurrent.Executor writers,
                                           Metrics m) {
        return new OutboundChannel(s, writers, task -> {}, 100, 1 << 20, m, ignored -> {});
    }

    @Test
    void aPingIsActuallyWrittenToTheWireAsAPingFrame() {
        var tasks = new OutboundIdleDispatcherTest.Manual();
        var wire = new Wire();
        var channel = channel(wire.session(), tasks, new Metrics());

        assertTrue(channel.enqueuePing(), "the ping must be accepted by an open channel");
        tasks.all();

        assertEquals(List.of(PingMessage.class), wire.kinds(),
                "a liveness ping must reach the wire as a PING frame — a text payload is not something a "
                        + "browser answers without the page's cooperation");
    }

    @Test
    void pingsCoalesceSoTheyCanNeverPileUpBehindAStalledWriter() {
        var tasks = new OutboundIdleDispatcherTest.Manual();
        var wire = new Wire();
        var metrics = new Metrics();
        var channel = channel(wire.session(), tasks, metrics);

        // Three sweeps while nothing drains — exactly the case the ping exists to detect.
        assertTrue(channel.enqueuePing());
        channel.enqueuePing();
        channel.enqueuePing();
        assertEquals(1, channel.queueDepth(), "three pings must collapse to one pending ping");
        assertEquals(2, metrics.coalesced.get(), "the two replaced pings must be counted as coalesced");

        tasks.all();
        assertEquals(List.of(PingMessage.class), wire.kinds(), "exactly one ping goes out");
    }

    @Test
    void aPingNeverDisplacesARealFrame() {
        var tasks = new OutboundIdleDispatcherTest.Manual();
        var wire = new Wire();
        var channel = channel(wire.session(), tasks, new Metrics());

        // A real frame under a real coalesce key, then a ping: the ping must queue BESIDE it, not over it.
        assertTrue(channel.enqueue("{\"type\":\"gamma-zone\"}", "gamma-zone"));
        assertTrue(channel.enqueuePing());
        assertEquals(2, channel.queueDepth(), "the ping must not have replaced the data frame");

        tasks.all();
        assertEquals(List.of(TextMessage.class, PingMessage.class), wire.kinds(),
                "the data frame keeps its place in line; the ping follows it");
    }

    @Test
    void theCoalesceKeyCannotCollideWithAnyRealOne() {
        // Coalesce keys are built from event names, symbols and strike keys. A NUL cannot occur in one,
        // which is the whole reason this key is safe — if that ever stopped being true, a ping could
        // silently swallow a live frame.
        assertTrue(OutboundChannel.PING_COALESCE_KEY.indexOf('\u0000') >= 0,
                "the ping's key must contain a character no real coalesce key can produce");
    }

    @Test
    void theDeathRuleIsStrictlyGreaterThanTheDeadline() {
        // The one judgement here that can hurt somebody. Lenient and the leak stays; strict and a
        // working page loses its feed mid-session.
        assertFalse(FeedGatewayService.socketIsDead(1_000L, 1_000L + 120_000L, 120_000L),
                "a socket sitting EXACTLY on the deadline is alive — a timer lands there exactly");
        assertTrue(FeedGatewayService.socketIsDead(1_000L, 1_001L + 120_000L, 120_000L),
                "one millisecond past the deadline is dead");
        assertFalse(FeedGatewayService.socketIsDead(1_000L, 1_000L, 120_000L), "just heard from");
    }

    @Test
    void anUnknownLastHeardTimeIsNeverDead() {
        // A socket that attached between the registry read and this check has no entry yet. Judging it
        // on a silence that began before it existed would evict the newest connection every sweep.
        assertFalse(FeedGatewayService.socketIsDead(null, Long.MAX_VALUE, 1L));
    }

    @Test
    void aClosedChannelRefusesAPingRatherThanResurrectingItself() {
        var tasks = new OutboundIdleDispatcherTest.Manual();
        var wire = new Wire();
        var channel = channel(wire.session(), tasks, new Metrics());
        channel.shutdown();

        assertFalse(channel.enqueuePing(), "a closed channel must refuse the ping");
        tasks.all();
        assertEquals(List.of(), wire.kinds(), "and must write nothing");
    }
}

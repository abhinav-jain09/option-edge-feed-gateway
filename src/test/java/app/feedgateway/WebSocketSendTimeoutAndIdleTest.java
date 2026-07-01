package app.feedgateway;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.stubbing.Answer;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.PingMessage;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

/**
 * Tests for the bounded WebSocket send-timeout + idle-session sweep (fix/gateway-ws-send-timeout).
 *
 * <p>Covers the three cases the incident review demanded:
 * <ul>
 *   <li>A blocked {@code sendMessage} is force-closed after the deadline and the session is dropped
 *       from tracking so the writer thread returns to the pool.</li>
 *   <li>A well-behaved (fast) send does NOT trip the timeout — the socket stays open.</li>
 *   <li>The idle sweep closes a socket with no inbound frames AND no outbound sends for the idle
 *       threshold — the TCP half-open case that never fails a send at all.</li>
 * </ul>
 */
class WebSocketSendTimeoutAndIdleTest {

    private final ExecutorService writers = Executors.newSingleThreadExecutor();
    private final List<String> sent = new CopyOnWriteArrayList<>();
    private final AtomicInteger writeErrors = new AtomicInteger();
    private final CountDownLatch closedCb = new CountDownLatch(1);

    private final OutboundChannel.Metrics metrics = new OutboundChannel.Metrics() {
        @Override public void enqueued(int bytes) { }
        @Override public void coalesced() { }
        @Override public void sent(int bytes) { }
        @Override public void disconnectedSlow() { }
        @Override public void writeError() { writeErrors.incrementAndGet(); }
        @Override public void droppedOnClose(int n) { }
    };

    @AfterEach
    void tearDown() {
        writers.shutdownNow();
    }

    private WebSocketSession blockingSession(CountDownLatch entered, CountDownLatch release) throws Exception {
        WebSocketSession ws = mock(WebSocketSession.class);
        when(ws.getId()).thenReturn("blocked");
        when(ws.isOpen()).thenReturn(true);
        Answer<Void> answer = inv -> {
            sent.add(((TextMessage) inv.getArgument(0)).getPayload());
            entered.countDown();
            release.await(5, TimeUnit.SECONDS);
            return null;
        };
        org.mockito.Mockito.doAnswer(answer).when(ws).sendMessage(any());
        org.mockito.Mockito.doAnswer(inv -> { release.countDown(); return null; }).when(ws).close();
        org.mockito.Mockito.doAnswer(inv -> { release.countDown(); return null; }).when(ws).close(any(CloseStatus.class));
        return ws;
    }

    private WebSocketSession fastSession() throws Exception {
        WebSocketSession ws = mock(WebSocketSession.class);
        when(ws.getId()).thenReturn("fast");
        when(ws.isOpen()).thenReturn(true);
        org.mockito.Mockito.doAnswer(inv -> {
            sent.add(((TextMessage) inv.getArgument(0)).getPayload());
            return null;
        }).when(ws).sendMessage(any());
        return ws;
    }

    private OutboundChannel channel(WebSocketSession ws) {
        return new OutboundChannel(ws, writers, 1000, 1L << 20, metrics, c -> closedCb.countDown());
    }

    /** A hung sendMessage is force-closed once the deadline elapses, and the session drops out of tracking. */
    @Test
    void blockedSendPastTimeoutClosesTheSessionAsNotReliable() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        WebSocketSession ws = blockingSession(entered, release);
        OutboundChannel ch = channel(ws);

        ch.enqueue("stuck", null);
        assertTrue(entered.await(2, TimeUnit.SECONDS), "the writer entered sendMessage");

        // Simulate the watchdog running well past the 5s deadline while the send is in flight.
        long timeoutMs = 5_000L;
        boolean disconnected = ch.enforceWriteDeadline(System.currentTimeMillis() + 10_000L, timeoutMs);

        assertTrue(disconnected, "the stuck send is force-closed");
        assertTrue(ch.isClosed(), "the channel is closed so the writer returns to the pool");
        assertEquals(1, writeErrors.get(), "one write-error metric was recorded");
        assertTrue(closedCb.await(2, TimeUnit.SECONDS), "onClose fires so FeedGatewayService drops the session");
        org.mockito.Mockito.verify(ws).close(CloseStatus.SESSION_NOT_RELIABLE);
    }

    /** A normal send that finishes well under the timeout keeps the socket open. */
    @Test
    void fastSendUnderTimeoutKeepsTheSessionOpen() throws Exception {
        OutboundChannel ch = channel(fastSession());

        ch.enqueue("ok", null);
        // Give the writer thread a moment to drain.
        long deadline = System.currentTimeMillis() + 2_000L;
        while (sent.isEmpty() && System.currentTimeMillis() < deadline) {
            Thread.sleep(5);
        }
        assertEquals(List.of("ok"), sent);

        // Watchdog runs shortly after — no send in flight, so nothing to force-close.
        boolean disconnected = ch.enforceWriteDeadline(System.currentTimeMillis() + 100L, 5_000L);
        assertFalse(disconnected, "an idle channel with no in-flight send is not force-closed");
        assertFalse(ch.isClosed(), "the healthy socket stays open");
        assertEquals(0, writeErrors.get(), "no error metric was recorded");
    }

    /** A socket with no I/O in either direction past the idle threshold is closed by the sweep. */
    @Test
    void idleSweepClosesASessionWithNoActivity() throws Exception {
        WebSocketSession ws = mock(WebSocketSession.class);
        when(ws.getId()).thenReturn("idle");
        when(ws.isOpen()).thenReturn(true);

        OutboundChannel ch = channel(ws);
        long idleTimeoutMs = 300_000L; // 5 minutes

        // Not yet idle — a well-under-threshold sweep must NOT close.
        assertFalse(ch.enforceIdleTimeout(System.currentTimeMillis(), idleTimeoutMs));
        assertFalse(ch.isClosed());

        // Fake time forward past the idle threshold; the sweep closes the socket with SESSION_NOT_RELIABLE.
        long future = System.currentTimeMillis() + idleTimeoutMs + 1_000L;
        boolean disconnected = ch.enforceIdleTimeout(future, idleTimeoutMs);
        assertTrue(disconnected, "an idle socket past the threshold is closed by the sweep");
        assertTrue(ch.isClosed());
        assertTrue(closedCb.await(2, TimeUnit.SECONDS));
        org.mockito.Mockito.verify(ws).close(CloseStatus.SESSION_NOT_RELIABLE);
    }

    /**
     * Passive-browser P2 fix: sendPing emits a WebSocket PING frame on the writer pool, and a subsequent
     * pong (delivered via {@code notePongReceived}) keeps the idle sweep quiet even when the client never
     * sends application frames. Verifies both halves in one flow.
     */
    @Test
    void sendPingEmitsPingFrameAndPongResetsIdleClock() throws Exception {
        CountDownLatch pinged = new CountDownLatch(1);
        java.util.List<Object> frames = new CopyOnWriteArrayList<>();
        WebSocketSession ws = mock(WebSocketSession.class);
        when(ws.getId()).thenReturn("passive");
        when(ws.isOpen()).thenReturn(true);
        org.mockito.Mockito.doAnswer(inv -> {
            Object arg = inv.getArgument(0);
            frames.add(arg);
            if (arg instanceof PingMessage) {
                pinged.countDown();
            }
            return null;
        }).when(ws).sendMessage(any());

        OutboundChannel ch = channel(ws);
        ch.sendPing();

        assertTrue(pinged.await(2, TimeUnit.SECONDS), "sendPing dispatched a PingMessage to the session");
        assertTrue(frames.stream().anyMatch(f -> f instanceof PingMessage),
                "at least one PingMessage was sent via session.sendMessage");

        // Simulate a pong arriving — the handler calls notePongReceived which bumps lastActivityAtMs.
        Thread.sleep(5);
        ch.notePongReceived();

        long idleTimeoutMs = 300_000L;
        long checkAt = System.currentTimeMillis() + (idleTimeoutMs / 2);
        assertFalse(ch.enforceIdleTimeout(checkAt, idleTimeoutMs),
                "a pong within the window keeps the idle sweep from evicting the passive client");
        assertFalse(ch.isClosed());
        assertEquals(0, writeErrors.get());
    }

    /**
     * Round-3 P2: even if pings SUCCEED at the write layer, an absence of pong frames must still evict
     * the socket after the idle timeout. TCP happily buffers to a zombie peer (dead NIC / closed lid),
     * so a successful ping-write is NOT proof of liveness — only a pong is. This test writes a ping
     * successfully, then never delivers a pong, then advances the clock past the idle threshold: the
     * sweep MUST close. On the pre-fix code the ping-write itself stamped lastActivityAtMs and the sweep
     * would spare the zombie forever.
     */
    @Test
    void pingSentButNoPongStillEvictsAfterTimeout() throws Exception {
        java.util.List<Object> frames = new CopyOnWriteArrayList<>();
        CountDownLatch pinged = new CountDownLatch(1);
        WebSocketSession ws = mock(WebSocketSession.class);
        when(ws.getId()).thenReturn("zombie");
        when(ws.isOpen()).thenReturn(true);
        org.mockito.Mockito.doAnswer(inv -> {
            Object arg = inv.getArgument(0);
            frames.add(arg);
            if (arg instanceof PingMessage) {
                pinged.countDown();
            }
            return null;
        }).when(ws).sendMessage(any());

        OutboundChannel ch = channel(ws);
        long baseline = ch.lastActivityAtMs();

        ch.sendPing();
        assertTrue(pinged.await(2, TimeUnit.SECONDS), "the ping was written to the session");
        // The successful ping-WRITE must NOT bump lastActivityAtMs — only a pong may.
        assertEquals(baseline, ch.lastActivityAtMs(),
                "a successful ping-write is not proof of peer liveness and must not refresh lastActivityAtMs");

        long idleTimeoutMs = 300_000L;
        long future = System.currentTimeMillis() + idleTimeoutMs + 1_000L;
        assertTrue(ch.enforceIdleTimeout(future, idleTimeoutMs),
                "with a ping sent but no pong the sweep still closes after the timeout");
        assertTrue(ch.isClosed());
    }

    /**
     * Round-3 P1: a ping firing while a data drain is stuck in {@code sendMessage} must NOT clear the
     * drain's {@code sendStartedAtMs} watchdog state. Under the pre-fix code the ping ran on a second
     * writer thread and, on its way out, set {@code sendStartedAtMs = 0} — which defeated the watchdog
     * exactly when it was needed. Under the fix, pings are serialized through the drain gate, so a ping
     * cannot run concurrently with an in-flight data send at all.
     */
    @Test
    void pingDoesNotClearWatchdogOfInFlightDataSend() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        // Use a two-thread writer pool so a stuck data send would leave room for a concurrent ping task
        // on the pre-fix code. The fix must keep them serialized regardless of pool width.
        ExecutorService twoThread = Executors.newFixedThreadPool(2);
        try {
            WebSocketSession ws = mock(WebSocketSession.class);
            when(ws.getId()).thenReturn("stuck-with-ping");
            when(ws.isOpen()).thenReturn(true);
            Answer<Void> answer = inv -> {
                Object arg = inv.getArgument(0);
                if (arg instanceof TextMessage) {
                    sent.add(((TextMessage) arg).getPayload());
                    entered.countDown();
                    release.await(5, TimeUnit.SECONDS);
                }
                // Any PingMessage returns immediately (fast) — this is the exact regression scenario.
                return null;
            };
            org.mockito.Mockito.doAnswer(answer).when(ws).sendMessage(any());
            org.mockito.Mockito.doAnswer(inv -> { release.countDown(); return null; })
                    .when(ws).close(any(CloseStatus.class));

            OutboundChannel ch = new OutboundChannel(
                    ws, twoThread, 1000, 1L << 20, metrics, c -> closedCb.countDown());

            ch.enqueue("stuck", null);
            assertTrue(entered.await(2, TimeUnit.SECONDS), "data send is in flight");

            // Fire a ping while the data send is wedged. On the pre-fix code this ran on thread 2 and
            // reset sendStartedAtMs to 0 in its finally block.
            ch.sendPing();
            Thread.sleep(50); // give a hypothetical concurrent ping task time to run its finally

            long timeoutMs = 5_000L;
            boolean disconnected = ch.enforceWriteDeadline(System.currentTimeMillis() + 10_000L, timeoutMs);
            assertTrue(disconnected,
                    "watchdog must still fire on the stuck data send even after a ping was requested");
            assertTrue(ch.isClosed());
            assertTrue(closedCb.await(2, TimeUnit.SECONDS));
        } finally {
            twoThread.shutdownNow();
        }
    }

    /** Recording an inbound frame resets the idle clock — a chatty client is not evicted. */
    @Test
    void inboundActivityResetsTheIdleClock() throws Exception {
        WebSocketSession ws = mock(WebSocketSession.class);
        when(ws.getId()).thenReturn("chatty");
        when(ws.isOpen()).thenReturn(true);
        OutboundChannel ch = channel(ws);

        long idleTimeoutMs = 300_000L;
        // Time-travel almost to the threshold, then note inbound activity: the clock resets.
        Thread.sleep(5); // ensure the base timestamp is in the past by at least 1ms
        ch.noteInboundActivity();
        long checkAt = System.currentTimeMillis() + (idleTimeoutMs / 2);
        assertFalse(ch.enforceIdleTimeout(checkAt, idleTimeoutMs), "the sweep does not close an active client");
        assertFalse(ch.isClosed());
    }
}

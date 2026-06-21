package app.feedgateway;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import app.feedgateway.mtsession.ConcurrencyLimits;
import app.feedgateway.mtsession.MarketDataSource;
import app.feedgateway.mtsession.Selection;
import app.feedgateway.mtsession.SessionRoutingEngine;
import app.feedgateway.mtsession.StrikeWindow;
import app.feedgateway.mtsession.SubscriptionManager;
import app.feedgateway.mtsession.gateway.ReplayChronology;
import app.feedgateway.mtsession.gateway.ReplayParams;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.kafka.common.TopicPartition;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.junit.jupiter.api.Test;

/**
 * P0: replay cancellation must barrier on a generation. A reader that is no longer the session's owner
 * (superseded by a newer replay, a stop, or return-to-live) must emit NOTHING — not even a record that an
 * earlier poll already returned — and its result is CANCELED, not COMPLETED.
 */
class FeedGatewayReplayCancellationTest {

    private final List<String> sink = new ArrayList<>();

    private FeedGatewayService svc() throws Exception {
        SessionRoutingEngine engine = new SessionRoutingEngine(new ConcurrencyLimits(5, 5, 100), new SubscriptionManager());
        engine.registerAppSession("app:u1", "u1",
                new Selection(MarketDataSource.DATABENTO, "SPX", "20260612", StrikeWindow.ALL), Set.of());
        engine.attachSocket("app:u1", "s1");
        FeedGatewayService svc =
                new FeedGatewayService(new GatewaySettings(), new ObjectMapper(), new HpsfGatewayViewMapper(), engine);
        svc.runOutboundWritesInline(); // synchronous delivery for deterministic assertions
        Field running = FeedGatewayService.class.getDeclaredField("running");
        running.setAccessible(true);
        ((AtomicBoolean) running.get(svc)).set(true);
        svc.addClient(socket());
        sink.clear();
        return svc;
    }

    private WebSocketSession socket() throws Exception {
        WebSocketSession ws = mock(WebSocketSession.class);
        when(ws.getId()).thenReturn("s1");
        when(ws.isOpen()).thenReturn(true);
        doAnswer(inv -> {
            sink.add(((TextMessage) inv.getArgument(0)).getPayload());
            return null;
        }).when(ws).sendMessage(any());
        return ws;
    }

    private static ReplayParams params() {
        return ReplayParams.of("app:u1", "SPX", "20260612",
                "2026-06-12T14:00:00Z", "2026-06-12T14:20:00Z", 1000, 30L * 60_000L, 200_000);
    }

    /** A single already-buffered, drained partition holding one index-price record ready to emit. */
    private static Map<TopicPartition, FeedGatewayService.ReplayPartitionState> oneBufferedRecord() {
        FeedGatewayService.ReplayPartitionState st =
                new FeedGatewayService.ReplayPartitionState(false, "index-price", 1L);
        st.drained = true; // already at target — no polling needed, just drain the buffer
        st.buffer.addLast(new FeedGatewayService.MergeRecord(
                new ReplayChronology.Cursor(100L, "underlying.es.trades", 0, 0L),
                "index-price", "{\"price\":1.0}", false));
        Map<TopicPartition, FeedGatewayService.ReplayPartitionState> parts = new LinkedHashMap<>();
        parts.put(new TopicPartition("underlying.es.trades", 0), st);
        return parts;
    }

    @Test
    void ownerGenerationEmitsTheBufferedRecord() throws Exception {
        // Positive control: while this handle is still the owner, the buffered record IS delivered.
        FeedGatewayService svc = svc();
        FeedGatewayService.ReplayHandle handle = svc.registerOwnerHandleForTest("app:u1");

        FeedGatewayService.ReplayResult result = svc.mergeReplayPartitions(
                "app:u1", params(), MarketDataSource.DATABENTO, null, null, oneBufferedRecord(), handle);

        assertEquals(FeedGatewayService.ReplayOutcome.COMPLETED, result.outcome());
        assertEquals(1L, result.delivered());
        assertEquals(1, sink.size(), "the owner generation delivers the record");
    }

    @Test
    void supersededGenerationEmitsNothingAndIsCanceled() throws Exception {
        // The reader holds the OLD handle; a newer replay generation has since taken ownership of the
        // session. The already-buffered record must NOT be sent, and the result is CANCELED.
        FeedGatewayService svc = svc();
        FeedGatewayService.ReplayHandle oldHandle = svc.registerOwnerHandleForTest("app:u1");
        svc.registerOwnerHandleForTest("app:u1"); // newer generation supersedes oldHandle

        FeedGatewayService.ReplayResult result = svc.mergeReplayPartitions(
                "app:u1", params(), MarketDataSource.DATABENTO, null, null, oneBufferedRecord(), oldHandle);

        assertEquals(FeedGatewayService.ReplayOutcome.CANCELED, result.outcome());
        assertEquals(0L, result.delivered());
        assertTrue(sink.isEmpty(), "a superseded reader must deliver no records");
    }

    @Test
    void clearedActiveFlagCancelsTheReader() throws Exception {
        FeedGatewayService svc = svc();
        FeedGatewayService.ReplayHandle handle = svc.registerOwnerHandleForTest("app:u1");
        handle.active.set(false); // e.g. cancelActiveReplay flipped it

        FeedGatewayService.ReplayResult result = svc.mergeReplayPartitions(
                "app:u1", params(), MarketDataSource.DATABENTO, null, null, oneBufferedRecord(), handle);

        assertEquals(FeedGatewayService.ReplayOutcome.CANCELED, result.outcome());
        assertTrue(sink.isEmpty(), "a stopped reader must deliver no records");
    }

    /**
     * P0 (replay resource safety): when the LAST socket of an AppSession disconnects, an in-flight replay
     * must be canceled and the session left replay mode — otherwise the reader keeps consuming Kafka unheard.
     */
    @Test
    void lastSocketCloseCancelsInFlightReplay() throws Exception {
        FeedGatewayService svc = svc();
        SessionRoutingEngine engine = engineOf(svc);

        // Simulate an in-flight replay for app:u1: a handle registered as the owner AND in replayHandles,
        // with the session in replay mode (exactly the state startReplay installs before the reader runs).
        FeedGatewayService.ReplayHandle handle = svc.registerOwnerHandleForTest("app:u1");
        replayHandlesOf(svc).put("app:u1", handle);
        engine.setReplayMode("app:u1", true);
        assertTrue(engine.isReplaying("app:u1"));

        // The sole socket disconnects: the handler detaches it from routing, then removeClient runs.
        engine.detachSocket("s1");
        svc.removeClient(sessionWith("s1", "app:u1"));

        assertFalse(handle.active.get(), "in-flight reader was woken/canceled");
        assertFalse(replayHandlesOf(svc).containsKey("app:u1"), "in-flight replay handle removed");
        assertFalse(engine.isReplaying("app:u1"), "session left replay mode");
    }

    /**
     * The replay must survive a NON-last socket closing: with a second socket still attached, closing one
     * socket leaves the in-flight reader running for the remaining listener.
     */
    @Test
    void nonLastSocketCloseKeepsReplayRunning() throws Exception {
        FeedGatewayService svc = svc();
        SessionRoutingEngine engine = engineOf(svc);
        engine.attachSocket("app:u1", "s2"); // a second live socket for the same session

        FeedGatewayService.ReplayHandle handle = svc.registerOwnerHandleForTest("app:u1");
        replayHandlesOf(svc).put("app:u1", handle);
        engine.setReplayMode("app:u1", true);

        engine.detachSocket("s1");
        svc.removeClient(sessionWith("s1", "app:u1"));

        assertTrue(handle.active.get(), "reader still active — another socket is listening");
        assertTrue(replayHandlesOf(svc).containsKey("app:u1"), "replay handle retained");
        assertTrue(engine.isReplaying("app:u1"), "session stays in replay mode");
    }

    /**
     * P0 (replay resource safety, slow-client path): a slow client force-evicted by its OutboundChannel
     * routes through onSlowDisconnect; if it was its AppSession's last socket the in-flight replay must be
     * canceled too — not just on a normal close.
     */
    @Test
    void slowDisconnectOfLastSocketCancelsInFlightReplay() throws Exception {
        FeedGatewayService svc = svc();
        SessionRoutingEngine engine = engineOf(svc);
        // Replace s1's outbound channel with one whose session carries the AppSession attribute the real
        // handshake binds, so onSlowDisconnect can resolve the AppSession id from channel.session().
        svc.addClient(sessionWith("s1", "app:u1"));

        FeedGatewayService.ReplayHandle handle = svc.registerOwnerHandleForTest("app:u1");
        replayHandlesOf(svc).put("app:u1", handle);
        engine.setReplayMode("app:u1", true);

        invokeOnSlowDisconnect(svc, outboundOf(svc).get("s1"));

        assertFalse(handle.active.get(), "slow-evicted last socket cancels the in-flight reader");
        assertFalse(replayHandlesOf(svc).containsKey("app:u1"), "in-flight replay handle removed");
        assertFalse(engine.isReplaying("app:u1"), "session left replay mode");
    }

    /** A disconnect with NO active replay must be a clean no-op: no exception, no state change. */
    @Test
    void disconnectWithNoActiveReplayIsANoOp() throws Exception {
        FeedGatewayService svc = svc();
        SessionRoutingEngine engine = engineOf(svc);
        engine.detachSocket("s1");

        svc.removeClient(sessionWith("s1", "app:u1")); // must not throw

        assertTrue(replayHandlesOf(svc).isEmpty(), "no replay handle created or left behind");
        assertFalse(engine.isReplaying("app:u1"), "session was never in replay mode");
    }

    /**
     * Regression for the teardown race: a logout/expiry sweep can remove the AppSession BEFORE the socket's
     * removeClient runs. Cancelling the reader must still succeed and must NOT throw when the now-absent
     * session can no longer accept setReplayMode.
     */
    @Test
    void lastSocketCloseAfterSessionTeardownDoesNotThrow() throws Exception {
        FeedGatewayService svc = svc();
        SessionRoutingEngine engine = engineOf(svc);
        FeedGatewayService.ReplayHandle handle = svc.registerOwnerHandleForTest("app:u1");
        replayHandlesOf(svc).put("app:u1", handle);
        engine.setReplayMode("app:u1", true);

        engine.teardownAppSession("app:u1"); // session gone before the socket close is processed

        svc.removeClient(sessionWith("s1", "app:u1")); // must not throw on the absent-session setReplayMode

        assertFalse(handle.active.get(), "reader still canceled despite the session being gone");
        assertFalse(replayHandlesOf(svc).containsKey("app:u1"), "in-flight replay handle removed");
    }

    private static void invokeOnSlowDisconnect(FeedGatewayService svc, OutboundChannel channel)
            throws Exception {
        Method m = FeedGatewayService.class.getDeclaredMethod("onSlowDisconnect", OutboundChannel.class);
        m.setAccessible(true);
        m.invoke(svc, channel);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, OutboundChannel> outboundOf(FeedGatewayService svc) throws Exception {
        Field f = FeedGatewayService.class.getDeclaredField("outbound");
        f.setAccessible(true);
        return (Map<String, OutboundChannel>) f.get(svc);
    }

    private static SessionRoutingEngine engineOf(FeedGatewayService svc) throws Exception {
        Field f = FeedGatewayService.class.getDeclaredField("routingEngine");
        f.setAccessible(true);
        return (SessionRoutingEngine) f.get(svc);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, FeedGatewayService.ReplayHandle> replayHandlesOf(FeedGatewayService svc)
            throws Exception {
        Field f = FeedGatewayService.class.getDeclaredField("replayHandles");
        f.setAccessible(true);
        return (Map<String, FeedGatewayService.ReplayHandle>) f.get(svc);
    }

    /** A socket mock carrying the AppSession id in its handshake attributes (as the interceptor binds it). */
    private WebSocketSession sessionWith(String socketId, String appSessionId) throws Exception {
        WebSocketSession ws = mock(WebSocketSession.class);
        when(ws.getId()).thenReturn(socketId);
        when(ws.isOpen()).thenReturn(true);
        doAnswer(inv -> {
            sink.add(((TextMessage) inv.getArgument(0)).getPayload());
            return null;
        }).when(ws).sendMessage(any());
        Map<String, Object> attrs = new LinkedHashMap<>();
        attrs.put(app.feedgateway.mtsession.gateway.TicketHandshakeInterceptor.ATTR_APP_SESSION_ID, appSessionId);
        when(ws.getAttributes()).thenReturn(attrs);
        return ws;
    }
}

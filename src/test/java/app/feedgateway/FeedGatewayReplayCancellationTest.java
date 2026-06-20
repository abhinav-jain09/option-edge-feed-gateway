package app.feedgateway;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
}

package app.feedgateway;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import app.feedgateway.mtsession.ConcurrencyLimits;
import app.feedgateway.mtsession.MarketDataSource;
import app.feedgateway.mtsession.Selection;
import app.feedgateway.mtsession.SessionRoutingEngine;
import app.feedgateway.mtsession.StrikeWindow;
import app.feedgateway.mtsession.SubscriptionManager;
import app.feedgateway.mtsession.gateway.ReplayParams;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Field;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * P0: an empty Kafka poll is not the end of a replay. Completion is decided ONLY by every active partition
 * reaching its captured target offset; empty polls are retried until a bounded deadline, and a timeout is
 * reported as INCOMPLETE (failure), never a silent REPLAY_COMPLETE.
 */
class FeedGatewayReplayCompletionTest {

    private static final long MAX_WINDOW_MS = 30L * 60L * 1000L;

    private FeedGatewayService running(SessionRoutingEngine engine) throws Exception {
        FeedGatewayService svc =
                new FeedGatewayService(new GatewaySettings(), new ObjectMapper(), new HpsfGatewayViewMapper(), engine);
        Field f = FeedGatewayService.class.getDeclaredField("running");
        f.setAccessible(true);
        ((AtomicBoolean) f.get(svc)).set(true);
        return svc;
    }

    private static SessionRoutingEngine engine() {
        SessionRoutingEngine engine = new SessionRoutingEngine(new ConcurrencyLimits(5, 5, 100), new SubscriptionManager());
        engine.registerAppSession("app:u1", "u1",
                new Selection(MarketDataSource.DATABENTO, "SPX", "20260612", StrikeWindow.ALL), Set.of());
        return engine;
    }

    private static ReplayParams params() {
        return ReplayParams.of("app:u1", "SPX", "20260612",
                "2026-06-12T14:00:00Z", "2026-06-12T14:20:00Z", 1000, MAX_WINDOW_MS, 200_000);
    }

    @SuppressWarnings("unchecked")
    private static KafkaConsumer<String, Object> emptyPollingConsumer(TopicPartition tp, long reportedPosition) {
        KafkaConsumer<String, Object> consumer = mock(KafkaConsumer.class);
        when(consumer.poll(any(Duration.class))).thenReturn(ConsumerRecords.empty());
        when(consumer.position(eq(tp))).thenReturn(reportedPosition);
        return consumer;
    }

    private static FeedGatewayService.ReplayHandle ownerHandle(FeedGatewayService svc, String appSessionId) {
        return svc.registerOwnerHandleForTest(appSessionId);
    }

    @AfterEach
    void clearTimeout() {
        System.clearProperty("GATEWAY_REPLAY_IDLE_TIMEOUT_MS");
    }

    @Test
    void emptyPollsShortOfTargetTimeOutAsIncompleteNotComplete() throws Exception {
        // The partition's target offset is 5 but the consumer's position never advances past 0 (every poll
        // is empty). After the deadline this must be INCOMPLETE — NOT a zero-record REPLAY_COMPLETE.
        System.setProperty("GATEWAY_REPLAY_IDLE_TIMEOUT_MS", "150");
        FeedGatewayService svc = running(engine());
        TopicPartition tp = new TopicPartition("underlying.es.trades", 0);
        Map<TopicPartition, FeedGatewayService.ReplayPartitionState> parts = new LinkedHashMap<>();
        parts.put(tp, new FeedGatewayService.ReplayPartitionState(false, "index-price", 5L));

        FeedGatewayService.ReplayResult result = svc.mergeReplayPartitions(
                "app:u1", params(), MarketDataSource.DATABENTO,
                null, emptyPollingConsumer(tp, 0L), parts, ownerHandle(svc, "app:u1"));

        assertEquals(FeedGatewayService.ReplayOutcome.TIMED_OUT, result.outcome());
        assertEquals(0L, result.delivered());
    }

    @Test
    void completionRequiresEveryPartitionToReachItsTargetOffset() throws Exception {
        // Same empty polls, but the consumer position has reached the captured target (5) — the partition is
        // genuinely drained, so the run completes (with zero matching records) rather than timing out.
        System.setProperty("GATEWAY_REPLAY_IDLE_TIMEOUT_MS", "150");
        FeedGatewayService svc = running(engine());
        TopicPartition tp = new TopicPartition("underlying.es.trades", 0);
        Map<TopicPartition, FeedGatewayService.ReplayPartitionState> parts = new LinkedHashMap<>();
        parts.put(tp, new FeedGatewayService.ReplayPartitionState(false, "index-price", 5L));

        FeedGatewayService.ReplayResult result = svc.mergeReplayPartitions(
                "app:u1", params(), MarketDataSource.DATABENTO,
                null, emptyPollingConsumer(tp, 5L), parts, ownerHandle(svc, "app:u1"));

        assertEquals(FeedGatewayService.ReplayOutcome.COMPLETED, result.outcome());
        assertEquals(0L, result.delivered());
    }
}

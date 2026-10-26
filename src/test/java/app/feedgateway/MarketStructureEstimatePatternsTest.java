package app.feedgateway;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.consumer.OffsetAndTimestamp;
import org.apache.kafka.common.Node;
import org.apache.kafka.common.PartitionInfo;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.Test;

/**
 * The dev-only pre-market estimate pattern sets (processing market-structure-service
 * PREMARKET-ESTIMATE-CANDLESTICK-PATTERNS-REQUIREMENT.md P6): a second topic that feeds the SAME retention and event as the regular
 * sets, so the set with the newest asOfMs wins per timeframe, live and in recovery. Off by default and never without the patterns stream.
 */
class MarketStructureEstimatePatternsTest {
    private static final TopicPartition REGULAR = new TopicPartition("structure.patterns", 0);
    private static final TopicPartition ESTIMATE = new TopicPartition("structure.estimate.patterns", 0);
    private static final FeedGatewayService.TopicBinding BINDING = new FeedGatewayService.TopicBinding("DATABENTO", "market-structure-patterns");
    private static final Map<String, FeedGatewayService.TopicBinding> BINDINGS = Map.of(REGULAR.topic(), BINDING, ESTIMATE.topic(), BINDING);

    private static FeedGatewayService service() {
        GatewaySettings settings = spy(new GatewaySettings());
        doReturn(true).when(settings).marketStructurePatternsEnabled();
        doReturn(true).when(settings).marketStructureEstimatePatternsEnabled();
        doReturn(REGULAR.topic()).when(settings).marketStructurePatternsTopic();
        doReturn(ESTIMATE.topic()).when(settings).marketStructureEstimatePatternsTopic();
        return new FeedGatewayService(settings, new ObjectMapper(), new HpsfGatewayViewMapper(), null);
    }

    private static String text(String tf, long asOfMs, String basis) {
        return TaFixtures.frame(TaFixtures.patternSet(tf, asOfMs)).replace("2026-10-16.r0000000000", "2026-10-16." + basis + "0000000000");
    }

    @Test
    void isOffByDefaultNeedsThePatternsStreamAndNamesItsOwnTopic() {
        GatewaySettings s = new GatewaySettings();
        assertFalse(s.marketStructureEstimatePatternsEnabled());
        assertEquals("options.spx.market-structure.estimate.patterns", s.marketStructureEstimatePatternsTopic());
        GatewaySettings only = spy(new GatewaySettings());
        doReturn(false).when(only).marketStructurePatternsEnabled();
        System.setProperty("GATEWAY_MARKET_STRUCTURE_ESTIMATE_PATTERNS_ENABLED", "true");
        try {
            assertFalse(only.marketStructureEstimatePatternsEnabled(), "the estimate flag alone enables nothing");
            GatewaySettings both = spy(new GatewaySettings());
            doReturn(true).when(both).marketStructurePatternsEnabled();
            assertTrue(both.marketStructureEstimatePatternsEnabled());
        } finally {
            System.clearProperty("GATEWAY_MARKET_STRUCTURE_ESTIMATE_PATTERNS_ENABLED");
        }
    }

    @Test
    void theRegularSetTakesOverFromTheEstimateAndALateEstimateSetIsRefused() {
        var s = service();
        assertTrue(s.retainMarketStructurePatterns("5m", 1000L, text("5m", 1000L, "e")), "the estimate set is retained overnight");
        assertTrue(s.retainMarketStructurePatterns("5m", 2000L, text("5m", 2000L, "r")), "the first regular set replaces it");
        assertFalse(s.retainMarketStructurePatterns("5m", 1500L, text("5m", 1500L, "e")),
                "the estimate session's terminal set arrives after the handover and is refused");
        assertEquals(text("5m", 2000L, "r"), s.marketStructurePatternsLatestForTest("5m"));
        assertEquals(1, s.marketStructurePatternsRegressionsForTest());
        assertTrue(s.retainMarketStructurePatterns("1m", 500L, text("1m", 500L, "e")), "other timeframes are independent");
    }

    @Test
    void bothTopicsAreBoundToTheSameEventOnlyWhenTheEstimateFlagIsOn() throws Exception {
        String source = Files.readString(Path.of("src/main/java/app/feedgateway/FeedGatewayService.java"));
        assertTrue(source.contains("if (settings.marketStructureEstimatePatternsEnabled()) {\n                // dev only"));
        assertTrue(source.contains("topicEvents.put(settings.marketStructureEstimatePatternsTopic(), new TopicBinding(\"DATABENTO\", \"market-structure-patterns\"));"));
        assertTrue(source.contains("topics.add(settings.marketStructureEstimatePatternsTopic());"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void recoveryKeepsTheNewestSetWhicheverTopicIsReadLast() throws Exception {
        for (boolean estimateLast : List.of(true, false)) {
            var s = service();
            String regular = text("1m", 2000L, "r"), estimate = text("1m", 1000L, "e");
            s.runOutboundWritesInline();
            var socket = mock(org.springframework.web.socket.WebSocketSession.class);
            when(socket.getId()).thenReturn("estimate-recovery");
            when(socket.isOpen()).thenReturn(true);
            s.addClient(socket);
            clearInvocations(socket);
            KafkaConsumer<String, Object> consumer = mock(KafkaConsumer.class);
            Node node = new Node(1, "b", 9092);
            when(consumer.listTopics(any(Duration.class))).thenReturn(Map.of(
                    REGULAR.topic(), List.of(new PartitionInfo(REGULAR.topic(), 0, node, new Node[]{node}, new Node[]{node})),
                    ESTIMATE.topic(), List.of(new PartitionInfo(ESTIMATE.topic(), 0, node, new Node[]{node}, new Node[]{node}))));
            when(consumer.offsetsForTimes(anyMap())).thenReturn(Map.of(REGULAR, new OffsetAndTimestamp(0, 1), ESTIMATE, new OffsetAndTimestamp(0, 1)));
            when(consumer.endOffsets(anyCollection(), any(Duration.class))).thenReturn(Map.of(REGULAR, 1L, ESTIMATE, 1L));
            var positions = new java.util.concurrent.ConcurrentHashMap<TopicPartition, Long>(Map.of(REGULAR, 0L, ESTIMATE, 0L));
            doAnswer(call -> { positions.put(call.getArgument(0), (Long) call.getArgument(1)); return null; }).when(consumer).seek(any(TopicPartition.class), anyLong());
            doAnswer(call -> { for (Object tp : (java.util.Collection<?>) call.getArgument(0)) positions.put((TopicPartition) tp, 1L); return null; }).when(consumer).seekToEnd(anyCollection());
            when(consumer.assignment()).thenReturn(java.util.Set.of(REGULAR, ESTIMATE));
            when(consumer.position(any(TopicPartition.class))).thenAnswer(call -> positions.get((TopicPartition) call.getArgument(0)));
            var polls = new java.util.concurrent.atomic.AtomicInteger();
            when(consumer.poll(any(Duration.class))).thenAnswer(call -> {
                int poll = polls.incrementAndGet();
                if (poll <= 2) {
                    boolean regularNow = estimateLast ? poll == 1 : poll == 2;
                    TopicPartition tp = regularNow ? REGULAR : ESTIMATE;
                    positions.put(tp, 1L);
                    String key = regularNow ? "2026-10-16.r0000000000|1m" : "2026-10-16.e0000000000|1m";
                    Object value = regularNow ? TaFixtures.patternSet("1m", 2000L) : TaFixtures.patternSet("1m", 1000L);
                    return new ConsumerRecords<>(Map.of(tp, List.of(new ConsumerRecord<String, Object>(tp.topic(), 0, 0, key, value))));
                }
                s.setRunningForTest(false);
                return ConsumerRecords.empty();
            });
            s.setRunningForTest(true);
            s.runLiveConsumerAttemptForTest("avro-live", BINDINGS, new java.util.concurrent.atomic.AtomicBoolean(), consumer, false);
            assertEquals(TaFixtures.frame(TaFixtures.patternSet("1m", 2000L)), s.marketStructurePatternsLatestForTest("1m"),
                    "the regular set wins whichever topic was read last (estimateLast=" + estimateLast + ")");
            assertEquals(estimateLast ? 1 : 0, s.marketStructurePatternsRegressionsForTest(),
                    "an older estimate set read after the regular one is a counted regression; read before it, it is simply replaced");
        }
    }
}

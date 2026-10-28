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
 * The dev-only pre-market estimate structure record (processing market-structure-service, session profile PREMARKET_ESTIMATE): a second
 * topic that feeds the SAME retention and event as the regular record, so the record with the newest asOfMs wins, live and in recovery.
 * Off by default and never without the structure stream.
 */
class MarketStructureEstimateUiTest {
    private static final TopicPartition REGULAR = new TopicPartition("structure.ui", 0);
    private static final TopicPartition ESTIMATE = new TopicPartition("structure.estimate.ui", 0);
    private static final FeedGatewayService.TopicBinding BINDING = new FeedGatewayService.TopicBinding("DATABENTO", "market-structure");
    private static final Map<String, FeedGatewayService.TopicBinding> BINDINGS = Map.of(REGULAR.topic(), BINDING, ESTIMATE.topic(), BINDING);

    private static FeedGatewayService service() {
        GatewaySettings settings = spy(new GatewaySettings());
        doReturn(true).when(settings).marketStructureEnabled();
        doReturn(true).when(settings).marketStructureEstimateEnabled();
        doReturn(REGULAR.topic()).when(settings).marketStructureTopic();
        doReturn(ESTIMATE.topic()).when(settings).marketStructureEstimateTopic();
        return new FeedGatewayService(settings, new ObjectMapper(), new HpsfGatewayViewMapper(), null);
    }

    @Test
    void isOffByDefaultNeedsTheStructureStreamAndNamesItsOwnTopic() {
        GatewaySettings s = new GatewaySettings();
        assertFalse(s.marketStructureEstimateEnabled());
        assertEquals("options.spx.market-structure.estimate.ui", s.marketStructureEstimateTopic());
        GatewaySettings only = spy(new GatewaySettings());
        doReturn(false).when(only).marketStructureEnabled();
        System.setProperty("GATEWAY_MARKET_STRUCTURE_ESTIMATE_ENABLED", "true");
        try {
            assertFalse(only.marketStructureEstimateEnabled(), "the estimate flag alone enables nothing");
            GatewaySettings both = spy(new GatewaySettings());
            doReturn(true).when(both).marketStructureEnabled();
            assertTrue(both.marketStructureEstimateEnabled());
        } finally {
            System.clearProperty("GATEWAY_MARKET_STRUCTURE_ESTIMATE_ENABLED");
        }
    }

    @Test
    void theRegularRecordTakesOverFromTheEstimateAndALateEstimateRecordIsRefused() {
        var s = service();
        assertTrue(s.retainMarketStructure(1000L, TaFixtures.frame(TaFixtures.uiProjection(1000L))), "the estimate record is retained overnight");
        assertTrue(s.retainMarketStructure(2000L, TaFixtures.frame(TaFixtures.uiProjection(2000L))), "the first regular record replaces it");
        assertFalse(s.retainMarketStructure(1500L, TaFixtures.frame(TaFixtures.uiProjection(1500L))),
                "the estimate session's terminal record arrives after the handover and is refused");
        assertEquals(TaFixtures.frame(TaFixtures.uiProjection(2000L)), s.marketStructureLatestForTest().get());
    }

    @Test
    void aWithdrawalOnTheEstimateTopicNeverErasesTheSharedRetentionButTheRegularOneStill() {
        var s = service();
        String regular = TaFixtures.frame(TaFixtures.uiProjection(2000L));
        assertTrue(s.retainMarketStructure(2000L, regular));
        s.evictMarketStructureTombstone("market-structure", new ConsumerRecord<>(ESTIMATE.topic(), 0, 1L, "k", null));
        assertEquals(regular, s.marketStructureLatestForTest().get(), "the estimate topic's tombstone is only counted");
        assertFalse(s.retainMarketStructure(1000L, TaFixtures.frame(TaFixtures.uiProjection(1000L))), "the baseline survived: an old estimate record is still refused");
        s.evictMarketStructureTombstone("market-structure", new ConsumerRecord<>(REGULAR.topic(), 0, 1L, "k", null));
        assertNull(s.marketStructureLatestForTest().get(), "the regular topic's tombstone still withdraws");
    }

    @Test
    void bothTopicsAreBoundToTheSameEventOnlyWhenTheEstimateFlagIsOn() throws Exception {
        String source = Files.readString(Path.of("src/main/java/app/feedgateway/FeedGatewayService.java"));
        assertTrue(source.contains("if (settings.marketStructureEstimateEnabled()) {\n                // dev only"));
        assertTrue(source.contains("topicEvents.put(settings.marketStructureEstimateTopic(), new TopicBinding(\"DATABENTO\", \"market-structure\"));"));
        assertTrue(source.contains("topics.add(settings.marketStructureEstimateTopic());"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void recoveryKeepsTheNewestRecordWhicheverTopicIsReadLast() throws Exception {
        for (boolean estimateLast : List.of(true, false)) {
            var s = service();
            s.runOutboundWritesInline();
            var socket = mock(org.springframework.web.socket.WebSocketSession.class);
            when(socket.getId()).thenReturn("estimate-structure-recovery");
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
                    Object value = TaFixtures.uiProjection(regularNow ? 2000L : 1000L);
                    return new ConsumerRecords<>(Map.of(tp, List.of(new ConsumerRecord<String, Object>(tp.topic(), 0, 0, "k", value))));
                }
                s.setRunningForTest(false);
                return ConsumerRecords.empty();
            });
            s.setRunningForTest(true);
            s.runLiveConsumerAttemptForTest("avro-live", BINDINGS, new java.util.concurrent.atomic.AtomicBoolean(), consumer, false);
            assertEquals(TaFixtures.frame(TaFixtures.uiProjection(2000L)), s.marketStructureLatestForTest().get(),
                    "the regular record wins whichever topic was read last (estimateLast=" + estimateLast + ")");
        }
    }
}

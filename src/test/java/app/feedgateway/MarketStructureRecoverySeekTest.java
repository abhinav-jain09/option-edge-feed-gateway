package app.feedgateway;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.OffsetAndTimestamp;
import org.apache.kafka.common.Node;
import org.apache.kafka.common.PartitionInfo;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.Test;

class MarketStructureRecoverySeekTest {
    private static final TopicPartition STRUCTURE = new TopicPartition("structure.ui", 0);
    private static final TopicPartition OTHER = new TopicPartition("other.ui", 0);
    private static final Map<String, FeedGatewayService.TopicBinding> BINDINGS = Map.of(
        STRUCTURE.topic(), new FeedGatewayService.TopicBinding("DATABENTO", "market-structure"),
        OTHER.topic(), new FeedGatewayService.TopicBinding("DATABENTO", "snapshot"));

    private static FeedGatewayService service() {
        GatewaySettings settings = spy(new GatewaySettings());
        doReturn(true).when(settings).marketStructureEnabled();
        doReturn(STRUCTURE.topic()).when(settings).marketStructureTopic();
        return new FeedGatewayService(settings, new ObjectMapper(), new HpsfGatewayViewMapper(), null);
    }

    @Test
    @SuppressWarnings("unchecked")
    void coldAndRetryStartsOverrideEndForTheQuietStructureSnapshotOnly() {
        for (boolean retry : List.of(false, true)) {
            KafkaConsumer<String, Object> consumer = mock(KafkaConsumer.class);
            when(consumer.offsetsForTimes(anyMap())).thenAnswer(call -> {
                Map<TopicPartition, Long> cuts = call.getArgument(0);
                return Map.of(STRUCTURE, new OffsetAndTimestamp(41, cuts.get(STRUCTURE)));
            });
            long before = System.currentTimeMillis();
            service().liveBootstrapSeek(consumer, List.of(STRUCTURE, OTHER), BINDINGS, retry);
            long after = System.currentTimeMillis();
            verify(consumer, atLeastOnce()).seek(STRUCTURE, 41);
            verify(consumer, never()).seek(eq(OTHER), anyLong());
            var calls = mockingDetails(consumer).getInvocations().stream()
                .filter(c -> c.getMethod().getName().equals("offsetsForTimes")).toList();
            Map<TopicPartition, Long> lastCut = (Map<TopicPartition, Long>) calls.get(calls.size() - 1).getArgument(0);
            assertEquals(java.util.Set.of(STRUCTURE), lastCut.keySet());
            assertTrue(lastCut.get(STRUCTURE) >= before - Duration.ofDays(7).toMillis());
            assertTrue(lastCut.get(STRUCTURE) <= after - Duration.ofDays(7).toMillis());
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void aNewlyDiscoveredStructureTopicRecoversItsExistingSnapshot() {
        var s = service();
        KafkaConsumer<String, Object> consumer = mock(KafkaConsumer.class);
        when(consumer.offsetsForTimes(anyMap())).thenReturn(Map.of(STRUCTURE, new OffsetAndTimestamp(19, 1)));
        var refresh = s.refreshForTest(List.of(STRUCTURE, OTHER), List.of(STRUCTURE), List.of(OTHER));
        s.liveAdoptionSeek(consumer, refresh, BINDINGS);
        verify(consumer).seek(STRUCTURE, 19);
        verify(consumer, never()).seek(eq(OTHER), anyLong());
    }

    @Test
    @SuppressWarnings("unchecked")
    void anEmptyOrExpiredStructureWindowStaysAtEndAndDisabledBindingsDoNothing() {
        var s = service();
        KafkaConsumer<String, Object> consumer = mock(KafkaConsumer.class);
        when(consumer.offsetsForTimes(anyMap())).thenReturn(Map.of());
        s.seekMarketStructureSnapshot(consumer, List.of(STRUCTURE, OTHER), BINDINGS);
        verify(consumer).seekToEnd(List.of(STRUCTURE));
        verify(consumer, never()).seek(any(), anyLong());
        reset(consumer);
        s.seekMarketStructureSnapshot(consumer, List.of(OTHER), BINDINGS);
        verifyNoInteractions(consumer);
    }

    @Test
    @SuppressWarnings("unchecked")
    void coldConsumerRestoresTheFinalSnapshotIntoTheNextHelloWithoutAnotherPublication() {
        var s = service();
        String payload = "{\"schemaVersion\":1,\"service\":\"market-structure\",\"state\":\"OK\","
            + "\"sessionDate\":\"2026-10-13\",\"asOfMs\":1791921600000,\"sessionEnded\":true,"
            + "\"sessionReferences\":{\"scope\":\"SPX_RTH\",\"rthOpenMs\":1791898200000,"
            + "\"rthCloseMs\":1791921600000,\"dayHigh\":7814.15,\"dayLow\":7786.11,\"previousClose\":null},"
            + "\"history\":\"" + "x".repeat(101 * 1024) + "\"}";
        KafkaConsumer<String, Object> consumer = mock(KafkaConsumer.class);
        Node node = new Node(1,"b",9092);
        when(consumer.listTopics(any(Duration.class))).thenReturn(Map.of(STRUCTURE.topic(),
            List.of(new PartitionInfo(STRUCTURE.topic(),0,node,new Node[]{node},new Node[]{node}))));
        when(consumer.offsetsForTimes(anyMap())).thenReturn(Map.of(STRUCTURE,new OffsetAndTimestamp(0,1)));
        var position = new java.util.concurrent.atomic.AtomicLong();
        doAnswer(call -> {position.set(1);return null;}).when(consumer).seekToEnd(anyCollection());
        doAnswer(call -> {position.set(call.getArgument(1));return null;}).when(consumer).seek(eq(STRUCTURE),anyLong());
        when(consumer.assignment()).thenReturn(java.util.Set.of(STRUCTURE));
        when(consumer.position(STRUCTURE)).thenAnswer(call -> position.get());
        var polls = new java.util.concurrent.atomic.AtomicInteger();
        when(consumer.poll(any(Duration.class))).thenAnswer(call -> {
            if (polls.incrementAndGet()==1 && position.get()==0) {
                position.set(1);
                return new ConsumerRecords<>(Map.of(STRUCTURE,List.of(
                    new ConsumerRecord<String,Object>(STRUCTURE.topic(),0,0,"SPX",payload))));
            }
            s.setRunningForTest(false);
            return ConsumerRecords.empty();
        });
        s.setRunningForTest(true);
        s.runLiveConsumerAttemptForTest("state-live",Map.of(STRUCTURE.topic(),BINDINGS.get(STRUCTURE.topic())),
            new java.util.concurrent.atomic.AtomicBoolean(),consumer,false);
        assertEquals(payload,s.marketStructureLatestForTest().get(),"cold startup must recover the quiet committed snapshot");
        assertTrue(s.cvdHelloJson().contains("\"structure\":"+payload),"connect hello must carry original reference prices and asOf");
    }
}

package app.feedgateway;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
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

/** The live loop end to end: a malformed pattern record is dropped and counted, retains nothing and reaches no socket; a valid one is retained and broadcast; a keyed tombstone withdraws its timeframe only. */
class MarketStructurePatternsLoopTest {
    private static final TopicPartition PATTERNS = new TopicPartition("structure.patterns", 0);
    private static final Map<String, FeedGatewayService.TopicBinding> BINDINGS = Map.of(
        PATTERNS.topic(), new FeedGatewayService.TopicBinding("DATABENTO", "market-structure-patterns"));

    private static FeedGatewayService service() {
        GatewaySettings settings = spy(new GatewaySettings());
        doReturn(true).when(settings).marketStructurePatternsEnabled();
        doReturn(PATTERNS.topic()).when(settings).marketStructurePatternsTopic();
        return new FeedGatewayService(settings, new ObjectMapper(), new HpsfGatewayViewMapper(), null);
    }

    private static org.apache.avro.generic.GenericRecord set(String tf, long asOfMs) {
        return TaFixtures.patternSet(tf, asOfMs);
    }

    @Test
    @SuppressWarnings("unchecked")
    void malformedRecordsAreDroppedValidOnesRetainedAndBroadcastTombstonesKeyed() throws Exception {
        var s = service();
        s.runOutboundWritesInline();
        var socket = mock(org.springframework.web.socket.WebSocketSession.class);
        when(socket.getId()).thenReturn("patterns-loop");
        when(socket.isOpen()).thenReturn(true);
        s.addClient(socket);
        clearInvocations(socket);
        KafkaConsumer<String, Object> consumer = mock(KafkaConsumer.class);
        Node node = new Node(1, "b", 9092);
        when(consumer.listTopics(any(Duration.class))).thenReturn(Map.of(PATTERNS.topic(),
            List.of(new PartitionInfo(PATTERNS.topic(), 0, node, new Node[]{node}, new Node[]{node}))));
        when(consumer.offsetsForTimes(anyMap())).thenReturn(Map.of());           // nothing retained on the broker: no recovery window
        when(consumer.endOffsets(anyCollection(), any(Duration.class))).thenReturn(Map.of(PATTERNS, 0L));
        when(consumer.endOffsets(anyCollection())).thenReturn(Map.of(PATTERNS, 0L));
        var position = new java.util.concurrent.atomic.AtomicLong();
        doAnswer(call -> { position.set(0L); return null; }).when(consumer).seekToEnd(anyCollection());
        when(consumer.assignment()).thenReturn(java.util.Set.of(PATTERNS));
        when(consumer.position(PATTERNS)).thenAnswer(call -> position.get());
        Object valid1m = set("1m", 100L), valid5m = set("5m", 100L);
        Object malformed = TaFixtures.put(set("1m", 200L), "service", "other");
        Object older1m = set("1m", 50L);
        var polls = new java.util.concurrent.atomic.AtomicInteger();
        when(consumer.poll(any(Duration.class))).thenAnswer(call -> {
            int poll = polls.incrementAndGet();
            position.set(poll);
            switch (poll) {
                case 1: return records(poll, "2026-10-16.r0000000000|1m", valid1m);
                case 2: return records(poll, "2026-10-16.r0000000000|1m", malformed);
                case 3: return records(poll, "2026-10-16.r0000000000|1m", GatewayAvroDeserializer.UNDECODABLE);
                case 4: return records(poll, "2026-10-16.r0000000000|5m", valid5m);
                case 5: return records(poll, "2026-10-16.r0000000000|1m", older1m);
                case 6: return records(poll, "bad|extra|5m", null);                       // malformed key: withdraws nothing
                case 7: return records(poll, "2026-10-16.r0000000000|5m", null);         // keyed tombstone: 5m only
                default: s.setRunningForTest(false); return ConsumerRecords.empty();
            }
        });
        s.setRunningForTest(true);
        s.runLiveConsumerAttemptForTest("avro-live", Map.of(PATTERNS.topic(), BINDINGS.get(PATTERNS.topic())),
            new java.util.concurrent.atomic.AtomicBoolean(), consumer, false);
        assertEquals(TaFixtures.frame((org.apache.avro.generic.GenericRecord) valid1m), s.marketStructurePatternsLatestForTest("1m"), "the valid 1m set is retained; the malformed, unparseable and older ones never displaced it");
        assertNull(s.marketStructurePatternsLatestForTest("5m"), "the keyed tombstone withdrew 5m");
        assertEquals(4L, s.marketStructurePatternsDropsForTest(), "two malformed values, the malformed-key tombstone and the keyed tombstone are the four drops");
        assertEquals(1L, s.marketStructurePatternsRegressionsForTest(), "the older 1m set is a regression");
        verify(socket, times(2)).sendMessage(any());                                      // valid1m and valid5m only
    }

    private static ConsumerRecords<String, Object> records(int poll, String key, Object value) {
        return new ConsumerRecords<>(Map.of(PATTERNS, List.of(new ConsumerRecord<String, Object>(PATTERNS.topic(), 0, poll - 1, key, value))));
    }
}

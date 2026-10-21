package app.feedgateway;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.avro.generic.GenericRecord;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.consumer.OffsetAndTimestamp;
import org.apache.kafka.common.Node;
import org.apache.kafka.common.PartitionInfo;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

/**
 * The live loop end to end for the studies relay: the history window is re-read from a cut-off back, held privately until the
 * captured end offsets are consumed, then promoted once (hello and frames), and live delivery continues after it.
 */
class StudiesLoopTest {
    private static final TopicPartition BARS = new TopicPartition("studies.bars", 0);
    private static final TopicPartition CURRENT = new TopicPartition("studies.current", 0);
    private static final Map<String, FeedGatewayService.TopicBinding> BINDINGS = Map.of(
            BARS.topic(), new FeedGatewayService.TopicBinding("DATABENTO", StudiesRelay.BAR_EVENT),
            CURRENT.topic(), new FeedGatewayService.TopicBinding("DATABENTO", StudiesRelay.CURRENT_EVENT));
    private static final ObjectMapper JSON = new ObjectMapper();

    private static FeedGatewayService service() {
        GatewaySettings settings = spy(new GatewaySettings());
        doReturn(true).when(settings).studiesEnabled();
        doReturn(java.util.Set.of(BARS.topic())).when(settings).studiesBarsTopics();
        doReturn(java.util.Set.of(CURRENT.topic())).when(settings).studiesCurrentTopics();
        return new FeedGatewayService(settings, JSON, new HpsfGatewayViewMapper(), null);
    }

    private static ConsumerRecords<String, Object> records(TopicPartition tp, long offset, String key, Object value) {
        return new ConsumerRecords<>(Map.of(tp, List.of(new ConsumerRecord<String, Object>(tp.topic(), 0, offset, key, value))));
    }

    @Test
    @SuppressWarnings("unchecked")
    void historyIsRecoveredPrivatelyPromotedOnceAndLiveDeliveryResumes() throws Exception {
        var s = service();
        s.runOutboundWritesInline();
        var socket = mock(WebSocketSession.class);
        when(socket.getId()).thenReturn("studies-loop");
        when(socket.isOpen()).thenReturn(true);
        s.addClient(socket);
        clearInvocations(socket);

        KafkaConsumer<String, Object> consumer = mock(KafkaConsumer.class);
        Node node = new Node(1, "b", 9092);
        when(consumer.listTopics(any(Duration.class))).thenReturn(Map.of(
                BARS.topic(), List.of(new PartitionInfo(BARS.topic(), 0, node, new Node[] {node}, new Node[] {node})),
                CURRENT.topic(), List.of(new PartitionInfo(CURRENT.topic(), 0, node, new Node[] {node}, new Node[] {node}))));
        when(consumer.offsetsForTimes(anyMap())).thenReturn(Map.of(BARS, new OffsetAndTimestamp(0, 1), CURRENT, new OffsetAndTimestamp(0, 1)));
        Map<TopicPartition, Long> end = Map.of(BARS, 3L, CURRENT, 1L);
        when(consumer.endOffsets(anyCollection(), any(Duration.class))).thenReturn(end);
        when(consumer.endOffsets(anyCollection())).thenReturn(end);
        when(consumer.beginningOffsets(anyCollection())).thenReturn(Map.of(CURRENT, 0L));
        Map<TopicPartition, Long> position = new HashMap<>();
        doAnswer(call -> { position.putAll(end); return null; }).when(consumer).seekToEnd(anyCollection());
        doAnswer(call -> { position.put(call.getArgument(0), call.getArgument(1)); return null; }).when(consumer).seek(any(TopicPartition.class), anyLong());
        when(consumer.assignment()).thenReturn(java.util.Set.of(BARS, CURRENT));
        when(consumer.position(any(TopicPartition.class))).thenAnswer(call -> position.getOrDefault(call.<TopicPartition>getArgument(0), 0L));

        List<ConsumerRecords<String, Object>> script = new ArrayList<>();
        GenericRecord b0 = StudiesFixtures.bar("SPX", "T5M", 0), b1 = StudiesFixtures.bar("SPX", "T5M", 1), b2 = StudiesFixtures.bar("SPX", "T5M", 2);
        script.add(records(BARS, 0, "SPX|5m", b0));
        script.add(records(BARS, 1, "SPX|5m", b1));
        script.add(records(BARS, 2, "SPX|5m", b2));
        script.add(records(CURRENT, 0, "SPX", StudiesFixtures.current("SPX", 2)));   // a state is only ever taken live
        GenericRecord live = StudiesFixtures.bar("SPX", "T5M", 3);
        script.add(records(BARS, 3, "SPX|5m", live));
        AtomicInteger poll = new AtomicInteger();
        when(consumer.poll(any(Duration.class))).thenAnswer(call -> {
            int n = poll.getAndIncrement();
            if (n < script.size()) {
                ConsumerRecords<String, Object> batch = script.get(n);
                batch.partitions().forEach(tp -> position.merge(tp, 1L, (old, one) -> Math.max(old, batch.records(tp).get(0).offset() + 1)));
                if (n < 3) {   // recovery polls: nothing is visible to a connecting page or a socket yet
                    JsonNode hello = JSON.readTree(s.cvdHelloJson());
                    assertEquals(0, hello.get("studyBars").get("5m").size(), "partial history never changes a connect hello (poll " + n + ")");
                    assertTrue(hello.get("studies").get("5m").isNull());
                    verify(socket, times(0)).sendMessage(any());
                }
                return batch;
            }
            s.setRunningForTest(false);
            return ConsumerRecords.empty();
        });
        s.setRunningForTest(true);
        s.runLiveConsumerAttemptForTest("avro-live", BINDINGS, new AtomicBoolean(), consumer, false);

        var sent = org.mockito.ArgumentCaptor.forClass(org.springframework.web.socket.WebSocketMessage.class);
        verify(socket, times(5)).sendMessage(sent.capture());   // promotion: 3 bars, then the live current, then the live bar
        List<String> types = new ArrayList<>();
        for (var m : sent.getAllValues()) {
            types.add(JSON.readTree(((TextMessage) m).getPayload()).get("type").asText());
        }
        assertEquals(List.of("indicator-studies", "indicator-studies", "indicator-studies", "indicator-studies-current", "indicator-studies"), types,
                "promotion sends the bars, then the live current state, then the live bar follows");
        JsonNode hello = JSON.readTree(s.cvdHelloJson());
        assertEquals(4, hello.get("studyBars").get("5m").size(), "the recovered history plus the live bar");
        assertEquals("2", hello.get("studies").get("15m").get("revision").asText());

        // the cut-off is the recovery window back from now, for the bars partition ONLY: the current-state topic is never re-read
        var times = mockingDetails(consumer).getInvocations().stream().filter(c -> c.getMethod().getName().equals("offsetsForTimes")).toList();
        Map<TopicPartition, Long> cut = (Map<TopicPartition, Long>) times.get(times.size() - 1).getArgument(0);
        assertEquals(List.of(BARS), List.copyOf(cut.keySet()));
        assertTrue(Math.abs(cut.get(BARS) - (System.currentTimeMillis() - 72L * 3_600_000L)) < 60_000L);
        org.mockito.Mockito.verify(consumer, org.mockito.Mockito.never()).seek(eq(CURRENT), anyLong());
    }

    @Test
    @SuppressWarnings("unchecked")
    void malformedRecordsAreDroppedAndCountedAndReachNoSocket() throws Exception {
        var s = service();
        s.runOutboundWritesInline();
        var socket = mock(WebSocketSession.class);
        when(socket.getId()).thenReturn("studies-drop");
        when(socket.isOpen()).thenReturn(true);
        s.addClient(socket);
        clearInvocations(socket);
        KafkaConsumer<String, Object> consumer = mock(KafkaConsumer.class);
        Node node = new Node(1, "b", 9092);
        when(consumer.listTopics(any(Duration.class))).thenReturn(Map.of(
                BARS.topic(), List.of(new PartitionInfo(BARS.topic(), 0, node, new Node[] {node}, new Node[] {node})),
                CURRENT.topic(), List.of(new PartitionInfo(CURRENT.topic(), 0, node, new Node[] {node}, new Node[] {node}))));
        when(consumer.offsetsForTimes(anyMap())).thenReturn(Map.of());
        Map<TopicPartition, Long> end = Map.of(BARS, 0L, CURRENT, 0L);
        when(consumer.endOffsets(anyCollection(), any(Duration.class))).thenReturn(end);
        when(consumer.endOffsets(anyCollection())).thenReturn(end);
        when(consumer.beginningOffsets(anyCollection())).thenReturn(end);
        Map<TopicPartition, Long> position = new HashMap<>(end);
        when(consumer.assignment()).thenReturn(java.util.Set.of(BARS, CURRENT));
        when(consumer.position(any(TopicPartition.class))).thenAnswer(call -> position.getOrDefault(call.<TopicPartition>getArgument(0), 0L));
        GenericRecord good = StudiesFixtures.bar("SPX", "T1M", 0);
        GenericRecord wrongMethod = StudiesFixtures.bar("SPX", "T1M", 1);
        wrongMethod.put("methodVersion", "STUDIES_V9");
        List<ConsumerRecords<String, Object>> script = List.of(
                records(BARS, 0, "SPX|1m", good),
                records(BARS, 1, "SPX|1m", wrongMethod),
                records(BARS, 2, "SPX|1m", null),                    // tombstone
                records(BARS, 3, "SPX|30s", StudiesFixtures.bar("SPX", "T30S", 2)),
                records(BARS, 4, "SPX|1m", StudiesFixtures.bar("SPX", "T1M", 0)),   // replayed duplicate
                records(CURRENT, 0, "SPX", StudiesFixtures.current("SPX", 5)),
                records(CURRENT, 1, "SPX", StudiesFixtures.current("SPX", 4)));    // regression
        AtomicInteger poll = new AtomicInteger();
        when(consumer.poll(any(Duration.class))).thenAnswer(call -> {
            int n = poll.getAndIncrement();
            if (n < script.size()) {
                return script.get(n);
            }
            s.setRunningForTest(false);
            return ConsumerRecords.empty();
        });
        s.setRunningForTest(true);
        s.runLiveConsumerAttemptForTest("avro-live", BINDINGS, new AtomicBoolean(), consumer, false);
        verify(socket, times(2)).sendMessage(any());   // the good bar and the first current state, nothing else
        String metrics = s.metrics();
        assertTrue(metrics.contains("gateway_studies_drops_total 3\n"), metrics);          // wrong method, tombstone, 30s
        assertTrue(metrics.contains("gateway_studies_duplicates_total 1\n"), metrics);
        assertTrue(metrics.contains("gateway_studies_regressions_total 1\n"), metrics);
        assertTrue(metrics.contains("gateway_studies_enabled 1\n"));
    }
}

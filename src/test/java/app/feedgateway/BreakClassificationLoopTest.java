package app.feedgateway;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
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
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.Node;
import org.apache.kafka.common.PartitionInfo;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

/**
 * The live loop end to end for the Phase F break-classification relay: the topic is re-read from {@code end - 600} records, the history below the captured end offset is held privately
 * (a connecting page sees nothing of it), promoted once (marks before outcomes), and live delivery continues; an undecodable value and a tombstone are counted and reach no socket.
 */
class BreakClassificationLoopTest {
    private static final TopicPartition BRK = new TopicPartition("break.t", 0);
    private static final Map<String, FeedGatewayService.TopicBinding> BINDINGS = Map.of(BRK.topic(), new FeedGatewayService.TopicBinding("DATABENTO", BreakClassificationRelay.EVENT));
    private static final ObjectMapper JSON = new ObjectMapper();

    private static FeedGatewayService service() {
        GatewaySettings settings = spy(new GatewaySettings());
        doReturn(true).when(settings).breakClassificationEnabled();
        return new FeedGatewayService(settings, JSON, new HpsfGatewayViewMapper(), null);
    }

    private static ConsumerRecords<String, Object> records(long offset, String key, Object value) {
        return new ConsumerRecords<>(Map.of(BRK, List.of(new ConsumerRecord<String, Object>(BRK.topic(), 0, offset, key, value))));
    }

    @Test
    @SuppressWarnings("unchecked")
    void historyIsRecoveredPrivatelyPromotedOnceAndLiveDeliveryResumes() throws Exception {
        var s = service();
        s.runOutboundWritesInline();
        var socket = mock(WebSocketSession.class);
        when(socket.getId()).thenReturn("break-loop");
        when(socket.isOpen()).thenReturn(true);
        s.addClient(socket);
        clearInvocations(socket);

        KafkaConsumer<String, Object> consumer = mock(KafkaConsumer.class);
        Node node = new Node(1, "b", 9092);
        when(consumer.listTopics(any(Duration.class))).thenReturn(Map.of(BRK.topic(), List.of(new PartitionInfo(BRK.topic(), 0, node, new Node[] {node}, new Node[] {node}))));
        when(consumer.offsetsForTimes(anyMap())).thenReturn(Map.of());
        Map<TopicPartition, Long> end = Map.of(BRK, 2L);
        when(consumer.endOffsets(anyCollection(), any(Duration.class))).thenReturn(end);
        when(consumer.endOffsets(anyCollection())).thenReturn(end);
        when(consumer.beginningOffsets(anyCollection())).thenReturn(Map.of(BRK, 0L));
        Map<TopicPartition, Long> position = new HashMap<>();
        doAnswer(call -> { position.putAll(end); return null; }).when(consumer).seekToEnd(anyCollection());
        doAnswer(call -> { position.put(call.getArgument(0), call.getArgument(1)); return null; }).when(consumer).seek(any(TopicPartition.class), anyLong());
        when(consumer.assignment()).thenReturn(java.util.Set.of(BRK));
        when(consumer.position(any(TopicPartition.class))).thenAnswer(call -> position.getOrDefault(call.<TopicPartition>getArgument(0), 0L));

        List<ConsumerRecords<String, Object>> script = new ArrayList<>();
        script.add(records(0, "SPX|1m", BreakFixtures.mark("M1", "2026-10-22", 1)));              // history
        script.add(records(1, "SPX|M1", BreakFixtures.outcome(1, "RECLAIMED")));                  // history
        script.add(records(2, "SPX|5m", BreakFixtures.mark("M5", "2026-10-22", 2)));              // live
        script.add(records(3, "status", BreakFixtures.status("LIVE")));                           // live
        script.add(records(4, "SPX|5m", GatewayAvroDeserializer.UNDECODABLE));
        script.add(records(5, "SPX|5m", null));
        AtomicInteger poll = new AtomicInteger();
        when(consumer.poll(any(Duration.class))).thenAnswer(call -> {
            int n = poll.getAndIncrement();
            if (n < script.size()) {
                ConsumerRecords<String, Object> batch = script.get(n);
                position.merge(BRK, 1L, (old, one) -> Math.max(old, batch.records(BRK).get(0).offset() + 1));
                if (n < 2) {   // recovery polls: nothing is visible to a connecting page or a socket yet
                    assertEquals("{\"1m\":null,\"5m\":null,\"15m\":null,\"status\":null}", JSON.readTree(s.cvdHelloJson()).get("breaks").toString(),
                            "partial history never changes a connect hello (poll " + n + ")");
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
        verify(socket, times(4)).sendMessage(sent.capture());
        List<String> types = new ArrayList<>();
        for (var m : sent.getAllValues()) {
            types.add(JSON.readTree(((TextMessage) m).getPayload()).get("type").asText());
        }
        assertEquals(List.of("break-classification-mark", "break-classification-outcome", "break-classification-mark", "break-classification-status"), types,
                "the history after its end offset (marks before outcomes), then the live frames in log order");
        JsonNode hello = JSON.readTree(s.cvdHelloJson()).get("breaks");
        assertEquals(1, hello.get("1m").get("marks").size());
        assertEquals("RECLAIMED", hello.get("1m").get("outcomes").get(0).get("label").asText());
        assertEquals(1, hello.get("5m").get("marks").size());
        assertEquals("LIVE", hello.get("status").get("statusKind").asText());
        String metrics = s.metrics();
        assertTrue(metrics.contains("gateway_break_classification_drops_total 2\n"), metrics);   // the undecodable value and the tombstone
        assertTrue(metrics.contains("gateway_break_classification_accepted_total 4\n"), metrics);
        verify(consumer).seek(BRK, 0L);   // end - 600 clamped to the log start
    }

    @Test
    @SuppressWarnings("unchecked")
    void theSeekIsSixHundredRecordsBeforeTheCapturedEndNotANewerOne() {
        var s = service();
        KafkaConsumer<String, Object> consumer = mock(KafkaConsumer.class);
        when(consumer.endOffsets(anyCollection(), any(Duration.class))).thenReturn(Map.of(BRK, 10_000L));
        when(consumer.endOffsets(anyCollection())).thenReturn(Map.of(BRK, 12_500L));
        when(consumer.beginningOffsets(anyCollection())).thenReturn(Map.of(BRK, 0L));
        s.new TaRecovery().begin(consumer, java.util.Set.of(BRK), BINDINGS);
        s.seekTaRelays(consumer, java.util.Set.of(BRK), BINDINGS);
        verify(consumer).seek(BRK, 10_000L - 600);
    }
}

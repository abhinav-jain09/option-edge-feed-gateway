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
 * The live loop end to end for the chart relays of Phases B, D and E: each relay re-reads its topic from a bounded number of records
 * before the end, holds what is below the captured end offsets privately (a column read before its chart waits for it), promotes once,
 * and live delivery continues; an undecodable value and a tombstone are counted and reach no socket.
 */
class TaRelaysLoopTest {
    private static final TopicPartition TOOLS = new TopicPartition("tools.t", 0);
    private static final TopicPartition CHART = new TopicPartition("tt.chart", 0);
    private static final TopicPartition COLUMNS = new TopicPartition("tt.columns", 0);
    private static final TopicPartition WAVES = new TopicPartition("waves.w", 0);
    private static final Map<String, FeedGatewayService.TopicBinding> BINDINGS = Map.of(
            TOOLS.topic(), new FeedGatewayService.TopicBinding("DATABENTO", TimeframeSetRelay.TOOLS_EVENT),
            CHART.topic(), new FeedGatewayService.TopicBinding("DATABENTO", TradeToolsRelay.ROLES[0]),
            COLUMNS.topic(), new FeedGatewayService.TopicBinding("DATABENTO", TradeToolsRelay.ROLES[1]),
            WAVES.topic(), new FeedGatewayService.TopicBinding("DATABENTO", WaveCycleRelay.WAVES_EVENT));
    private static final ObjectMapper JSON = new ObjectMapper();

    private static FeedGatewayService service() {
        GatewaySettings settings = spy(new GatewaySettings());
        doReturn(true).when(settings).structureToolsEnabled();
        doReturn(true).when(settings).tradeToolsEnabled();
        doReturn(true).when(settings).waveCycleEnabled();
        return new FeedGatewayService(settings, JSON, new HpsfGatewayViewMapper(), null);
    }

    private static ConsumerRecords<String, Object> records(TopicPartition tp, long offset, String key, Object value) {
        return new ConsumerRecords<>(Map.of(tp, List.of(new ConsumerRecord<String, Object>(tp.topic(), 0, offset, key, value))));
    }

    @Test
    @SuppressWarnings("unchecked")
    void historyIsRecoveredPrivatelyPromotedOncePerRelayAndLiveDeliveryResumes() throws Exception {
        var s = service();
        s.runOutboundWritesInline();
        var socket = mock(WebSocketSession.class);
        when(socket.getId()).thenReturn("ta-loop");
        when(socket.isOpen()).thenReturn(true);
        s.addClient(socket);
        clearInvocations(socket);

        KafkaConsumer<String, Object> consumer = mock(KafkaConsumer.class);
        Node node = new Node(1, "b", 9092);
        Map<String, List<PartitionInfo>> topics = new HashMap<>();
        for (TopicPartition tp : BINDINGS.keySet().stream().map(t -> new TopicPartition(t, 0)).toList()) {
            topics.put(tp.topic(), List.of(new PartitionInfo(tp.topic(), 0, node, new Node[] {node}, new Node[] {node})));
        }
        when(consumer.listTopics(any(Duration.class))).thenReturn(topics);
        when(consumer.offsetsForTimes(anyMap())).thenReturn(Map.of());
        Map<TopicPartition, Long> end = Map.of(TOOLS, 2L, CHART, 1L, COLUMNS, 1L, WAVES, 0L);
        when(consumer.endOffsets(anyCollection(), any(Duration.class))).thenReturn(end);
        when(consumer.endOffsets(anyCollection())).thenReturn(end);
        when(consumer.beginningOffsets(anyCollection())).thenReturn(Map.of(TOOLS, 0L, CHART, 0L, COLUMNS, 0L, WAVES, 0L));
        Map<TopicPartition, Long> position = new HashMap<>();
        doAnswer(call -> { position.putAll(end); return null; }).when(consumer).seekToEnd(anyCollection());
        doAnswer(call -> { position.put(call.getArgument(0), call.getArgument(1)); return null; }).when(consumer).seek(any(TopicPartition.class), anyLong());
        when(consumer.assignment()).thenReturn(java.util.Set.of(TOOLS, CHART, COLUMNS, WAVES));
        when(consumer.position(any(TopicPartition.class))).thenAnswer(call -> position.getOrDefault(call.<TopicPartition>getArgument(0), 0L));

        List<ConsumerRecords<String, Object>> script = new ArrayList<>();
        script.add(records(COLUMNS, 0, "c|ESZ6|1", TaFixtures.column("c", 1, 11)));              // read before its chart
        script.add(records(TOOLS, 0, "s|5m", TaFixtures.tools("M5", 10, "OK")));
        script.add(records(TOOLS, 1, "s|5m", TaFixtures.tools("M5", 20, "OK")));
        script.add(records(CHART, 0, "c", TaFixtures.chart("c", "M5", 10)));
        script.add(records(TOOLS, 2, "s|5m", TaFixtures.tools("M5", 30, "OK")));                 // live
        script.add(records(WAVES, 0, "SPX|5m", TaFixtures.waveSet("M5", 1, 1, "OK")));           // live
        script.add(records(WAVES, 1, "SPX|5m", GatewayAvroDeserializer.UNDECODABLE));
        script.add(records(WAVES, 2, "SPX|5m", null));
        AtomicInteger poll = new AtomicInteger();
        when(consumer.poll(any(Duration.class))).thenAnswer(call -> {
            int n = poll.getAndIncrement();
            if (n < script.size()) {
                ConsumerRecords<String, Object> batch = script.get(n);
                batch.partitions().forEach(tp -> position.merge(tp, 1L, (old, one) -> Math.max(old, batch.records(tp).get(0).offset() + 1)));
                if (n < 2) {   // recovery polls: nothing is visible to a connecting page or a socket yet
                    JsonNode hello = JSON.readTree(s.cvdHelloJson());
                    assertTrue(hello.get("tools").get("5m").isNull(), "partial history never changes a connect hello (poll " + n + ")");
                    assertTrue(hello.get("tradeTools").get("5m").isNull());
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
        verify(socket, times(5)).sendMessage(sent.capture());
        List<String> types = new ArrayList<>();
        for (var m : sent.getAllValues()) {
            types.add(JSON.readTree(((TextMessage) m).getPayload()).get("type").asText());
        }
        assertEquals(List.of("market-structure-tools", "trade-tools-pf-chart", "trade-tools-pf-column", "market-structure-tools", "wave-cycle-waves"), types,
                "tools promoted after its end offsets, the chart then its waiting column after theirs, then the live frames");
        JsonNode hello = JSON.readTree(s.cvdHelloJson());
        assertEquals(30, hello.get("tools").get("5m").get("asOfMs").asLong());
        assertEquals("c", hello.get("tradeTools").get("5m").get("pfCharts").get(0).get("chartId").asText());
        assertEquals(1, hello.get("tradeTools").get("5m").get("pfColumns").size(), "the column that waited for its chart");
        assertEquals(1, hello.get("waves").get("5m").get("waves").get("env").get("epoch").asInt());
        String metrics = s.metrics();
        assertTrue(metrics.contains("gateway_wave_cycle_drops_total 2\n"), metrics);   // the undecodable value and the tombstone
        assertTrue(metrics.contains("gateway_structure_tools_accepted_total 3\n"), metrics);
        assertTrue(metrics.contains("gateway_trade_tools_enabled 1\n"));
        // the tail re-read: a bounded number of records before the end of each partition (end - tail, bounded by the beginning)
        verify(consumer).seek(TOOLS, 0L);
        verify(consumer).seek(COLUMNS, 0L);
    }
}

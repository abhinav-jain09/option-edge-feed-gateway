package app.feedgateway;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import io.confluent.kafka.serializers.KafkaAvroSerializer;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
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
import org.apache.kafka.common.Node;
import org.apache.kafka.common.PartitionInfo;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The live loop end to end for the alert relay: the staged recovery of both profiles, the hello that says "not ready" until it is promoted, the per-socket snapshots at promotion,
 * live delivery, the wire path (Confluent framing through a registry and the gateway deserializer) and the switches (design 6.1).
 */
class PatternAlertLoopTest {
    private static final TopicPartition REGULAR = new TopicPartition("alerts.r", 0);
    private static final TopicPartition ESTIMATE = new TopicPartition("alerts.e", 0);
    private static final Map<String, FeedGatewayService.TopicBinding> BINDINGS = Map.of(
            REGULAR.topic(), new FeedGatewayService.TopicBinding("DATABENTO", PatternAlertRelay.REGULAR_EVENT),
            ESTIMATE.topic(), new FeedGatewayService.TopicBinding("DATABENTO", PatternAlertRelay.ESTIMATE_EVENT));

    @BeforeEach
    void clock() {
        PatternAlertRelay.clockForTest = () -> PatternAlertFixtures.barEnd(250) + 5_000L;
    }

    @AfterEach
    void unclock() {
        PatternAlertRelay.clockForTest = null;
    }

    private static ConsumerRecords<String, Object> records(TopicPartition tp, long offset, Object value) {
        return new ConsumerRecords<>(Map.of(tp, List.of(new ConsumerRecord<String, Object>(tp.topic(), 0, offset, "k", value))));
    }

    @Test
    @SuppressWarnings("unchecked")
    void historyIsStagedPromotedOnceEverySocketGetsItsSnapshotAndLiveDeliveryResumes() throws Exception {
        var s = PatternAlertServiceTest.service(false);
        var socket = new PatternAlertServiceTest.Wire("loop");
        s.addClient(socket.socket);
        assertFalse(socket.hello().get("alerts").get("profiles").get("regular").get("ready").asBoolean(), "a hello before the recovery is promoted says so");

        KafkaConsumer<String, Object> consumer = mock(KafkaConsumer.class);
        Node node = new Node(1, "b", 9092);
        Map<String, List<PartitionInfo>> topics = new HashMap<>();
        for (TopicPartition tp : List.of(REGULAR, ESTIMATE)) {
            topics.put(tp.topic(), List.of(new PartitionInfo(tp.topic(), 0, node, new Node[] {node}, new Node[] {node})));
        }
        when(consumer.listTopics(any(Duration.class))).thenReturn(topics);
        when(consumer.offsetsForTimes(anyMap())).thenReturn(Map.of());
        Map<TopicPartition, Long> end = Map.of(REGULAR, 3L, ESTIMATE, 0L);
        when(consumer.endOffsets(anyCollection(), any(Duration.class))).thenReturn(end);
        when(consumer.endOffsets(anyCollection())).thenReturn(end);
        when(consumer.beginningOffsets(anyCollection())).thenReturn(Map.of(REGULAR, 0L, ESTIMATE, 0L));
        Map<TopicPartition, Long> position = new HashMap<>();
        doAnswer(call -> { position.putAll(end); return null; }).when(consumer).seekToEnd(anyCollection());
        doAnswer(call -> { position.put(call.getArgument(0), call.getArgument(1)); return null; }).when(consumer).seek(any(TopicPartition.class), anyLong());
        when(consumer.assignment()).thenReturn(java.util.Set.of(REGULAR, ESTIMATE));
        when(consumer.position(any(TopicPartition.class))).thenAnswer(call -> position.getOrDefault(call.<TopicPartition>getArgument(0), 0L));

        List<ConsumerRecords<String, Object>> script = new ArrayList<>();
        script.add(records(REGULAR, 0, PatternAlertFixtures.triangleBreak(1, "SUPPRESSED_STALE")));            // history
        script.add(records(REGULAR, 1, PatternAlertFixtures.watermark(PatternAlertFixtures.DATE, "TF_1M", 5, 4, true, 1)));
        script.add(records(REGULAR, 2, PatternAlertFixtures.wedgeBreak(2, "RAISED")));                          // history, end offset 3
        script.add(records(REGULAR, 3, PatternAlertFixtures.wedgeReturn(3, "RAISED")));                         // live
        script.add(records(ESTIMATE, 0, PatternAlertFixtures.numbered(PatternAlertFixtures.DATE, 1, "RAISED", 248)));   // live, its own profile
        script.add(records(REGULAR, 4, GatewayAvroDeserializer.UNDECODABLE));
        script.add(records(REGULAR, 5, null));
        AtomicInteger poll = new AtomicInteger();
        when(consumer.poll(any(Duration.class))).thenAnswer(call -> {
            int n = poll.getAndIncrement();
            if (n < script.size()) {
                ConsumerRecords<String, Object> batch = script.get(n);
                batch.partitions().forEach(tp -> position.merge(tp, 1L, (old, one) -> Math.max(old, batch.records(tp).get(0).offset() + 1)));
                if (n < 2) {
                    JsonNode hello = PatternAlertServiceTest.readTree(s.cvdHelloJson());
                    assertFalse(hello.get("alerts").get("profiles").get("regular").get("ready").asBoolean(), "partial history never changes a hello (poll " + n + ")");
                    assertEquals(0, socket.alerts("snapshot").size());
                    assertEquals(0, socket.alerts("event").size(), "history is never broadcast");
                }
                return batch;
            }
            s.setRunningForTest(false);
            return ConsumerRecords.empty();
        });
        s.setRunningForTest(true);
        s.runLiveConsumerAttemptForTest("avro-live", BINDINGS, new AtomicBoolean(), consumer, false);

        List<String> shapes = new ArrayList<>();
        for (JsonNode m : socket.sent) {
            if (m.path("type").asText().startsWith("market-structure-alerts")) {
                shapes.add(m.get("type").asText() + ":" + m.get("data").get("type").asText() + ":" + m.get("data").get("profile").asText());
            }
        }
        assertEquals(List.of("market-structure-alerts:snapshot:regular", "market-structure-alerts-estimate:snapshot:estimate",
                "market-structure-alerts:event:regular", "market-structure-alerts-estimate:event:estimate"), shapes,
                "a snapshot per profile when the recovery is promoted, then the live frames, each on its profile's event");
        JsonNode snapshot = socket.alerts("snapshot").get(0);
        assertTrue(snapshot.get("ready").asBoolean());
        assertEquals(2, snapshot.get("eventSeqHigh").asLong(), "the history up to the captured end: the live record 3 is not in it");
        assertEquals(2, snapshot.get("events").size());
        JsonNode live = socket.alerts("event").get(0);
        assertEquals(3, live.get("event").get("eventSeq").asLong());
        assertTrue(live.get("display").asBoolean() && live.get("speak").asBoolean());
        String metrics = s.metrics();
        assertTrue(metrics.contains("gateway_pattern_alerts_drops_total 2\n"), metrics);   // the undecodable value and the tombstone
        assertTrue(metrics.contains("gateway_pattern_alerts_watermarks_ignored_total 1\n"), metrics);
        assertTrue(metrics.contains("gateway_pattern_alerts_enabled 1\n"));
        verify(consumer).seek(REGULAR, 0L);   // end - 8192, clamped to the beginning
        // the page's cursor reply after the snapshot is bounded by the snapshot, so event 3 (delivered live) is never in it
        int mark = socket.sent.size();
        s.handleFootprintBasicMessage(socket.socket, PatternAlertServiceTest.resume("initial", "regular", PatternAlertFixtures.DATE, 0));
        JsonNode d = PatternAlertServiceTest.reply(socket, mark);
        assertEquals("display", d.get("type").asText());
        assertEquals(2, d.get("eventSeqHigh").asLong());
        assertEquals(1, d.get("events").size());
    }

    @Test
    void theTopicsAreBoundOnTheAvroLiveConsumerOnlyBehindTheActiveFlagAndAreTolerant() throws Exception {
        String src = Files.readString(Path.of("src/main/java/app/feedgateway/FeedGatewayService.java"));
        int avro = src.indexOf("private void runAvroLiveConsumer()");
        int json = src.indexOf("private void runJsonStateLiveConsumer()");
        String avroSrc = src.substring(avro, methodEnd(src, avro));
        String jsonSrc = src.substring(json, methodEnd(src, json));
        assertTrue(avroSrc.contains("if (settings.patternAlertsActive()) {"), "bound behind the active flag");
        assertTrue(avroSrc.contains("bindChartTopic(topicEvents, settings.patternAlertsTopic(), PatternAlertRelay.REGULAR_EVENT)"));
        assertTrue(avroSrc.contains("bindChartTopic(topicEvents, settings.patternAlertsEstimateTopic(), PatternAlertRelay.ESTIMATE_EVENT)"));
        assertFalse(jsonSrc.contains("patternAlerts"), "the converted alert topics are Avro only");
        Method tolerant = FeedGatewayService.class.getDeclaredMethod("tolerantTopics");
        tolerant.setAccessible(true);
        FeedGatewayService on = PatternAlertServiceTest.service(false);
        @SuppressWarnings("unchecked")
        List<String> topics = (List<String>) tolerant.invoke(on);
        assertTrue(topics.contains("options.spx.market-structure.pattern-alerts") && topics.contains("options.spx.market-structure.estimate.pattern-alerts"));
        FeedGatewayService off = new FeedGatewayService(new GatewaySettings(), PatternAlertServiceTest.JSON, new HpsfGatewayViewMapper(), null);
        @SuppressWarnings("unchecked")
        List<String> none = (List<String>) tolerant.invoke(off);
        assertFalse(none.contains("options.spx.market-structure.pattern-alerts"));
    }

    @Test
    void aRecordThatCrossedTheWireIsTheSameFrameAsTheRecordThatWasWritten() {
        GenericRecord e = PatternAlertFixtures.wedgeBreak(2, "RAISED");
        Map<String, Object> config = new HashMap<>();
        config.put("schema.registry.url", "mock://pattern-alert-wire");
        config.put("auto.register.schemas", true);
        config.put("value.subject.name.strategy", "io.confluent.kafka.serializers.subject.RecordNameStrategy");
        Map<String, Object> reader = new HashMap<>(config);
        reader.put("specific.avro.reader", "false");
        reader.put(GatewayAvroDeserializer.SPX_PRICE_TOPIC, "underlying.spx.price");
        reader.put(GatewayAvroDeserializer.TOLERANT_TOPICS, "t.alert");
        Object decoded;
        try (KafkaAvroSerializer serializer = new KafkaAvroSerializer(); GatewayAvroDeserializer deserializer = new GatewayAvroDeserializer()) {
            serializer.configure(config, false);
            deserializer.configure(reader, false);
            decoded = deserializer.deserialize("t.alert", serializer.serialize("t.alert", e));
        }
        PatternAlertRelay direct = new PatternAlertRelay(PatternAlertFixtures.WINDOW_MS, 256, 16L << 20, () -> PatternAlertFixtures.barEnd(250) + 5_000L);
        PatternAlertRelay wired = new PatternAlertRelay(PatternAlertFixtures.WINDOW_MS, 256, 16L << 20, () -> PatternAlertFixtures.barEnd(250) + 5_000L);
        assertEquals(direct.onLive(PatternAlertRelay.REGULAR_EVENT, e)[1], wired.onLive(PatternAlertRelay.REGULAR_EVENT, decoded)[1]);
    }

    @Test
    void theRegularAndTheEstimateTopicsOfOneProfileCannotBeTheSameTopic() {
        Map<String, FeedGatewayService.TopicBinding> bindings = new HashMap<>();
        FeedGatewayService.bindChartTopic(bindings, "t.same", PatternAlertRelay.REGULAR_EVENT);
        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class, () -> FeedGatewayService.bindChartTopic(bindings, "t.same", PatternAlertRelay.ESTIMATE_EVENT));
    }

    /** The index of the brace that closes the method whose signature starts at {@code from} (string and char literals and comments are skipped). */
    private static int methodEnd(String src, int from) {
        int i = src.indexOf('{', from), depth = 0;
        for (; i < src.length(); i++) {
            char c = src.charAt(i);
            if (c == '"' || c == '\'') {
                for (i++; src.charAt(i) != c; i++) {
                    if (src.charAt(i) == '\\') {
                        i++;
                    }
                }
            } else if (c == '/' && src.charAt(i + 1) == '/') {
                i = src.indexOf('\n', i);
            } else if (c == '/' && src.charAt(i + 1) == '*') {
                i = src.indexOf("*/", i) + 1;
            } else if (c == '{') {
                depth++;
            } else if (c == '}' && --depth == 0) {
                return i;
            }
        }
        throw new IllegalStateException("unbalanced braces");
    }
}

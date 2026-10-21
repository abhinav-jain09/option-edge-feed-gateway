package app.feedgateway;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.confluent.kafka.serializers.KafkaAvroSerializer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.avro.generic.GenericRecord;
import org.junit.jupiter.api.Test;

/**
 * The production wire path for every displayed record of CHART-STRUCTURE-TOOLS to ELLIOTT-FIB-CYCLE-TOOLS: Confluent framing, a registry, {@link GatewayAvroDeserializer}
 * (Utf8 strings, generic enum symbols, generic arrays and maps), then the relay: the frame is the rendering of the record that was
 * written, so decoding changes nothing the browser sees.
 */
class TaRelaysWireTest {
    private static Object roundTrip(String scope, String topic, GenericRecord record) {
        Map<String, Object> config = new HashMap<>();
        config.put("schema.registry.url", "mock://" + scope);
        config.put("auto.register.schemas", true);
        config.put("value.subject.name.strategy", "io.confluent.kafka.serializers.subject.RecordNameStrategy");
        Map<String, Object> reader = new HashMap<>(config);
        reader.put("specific.avro.reader", "false");
        reader.put(GatewayAvroDeserializer.SPX_PRICE_TOPIC, "underlying.spx.price");
        reader.put(GatewayAvroDeserializer.TOLERANT_TOPICS, topic);
        try (KafkaAvroSerializer serializer = new KafkaAvroSerializer(); GatewayAvroDeserializer deserializer = new GatewayAvroDeserializer()) {
            serializer.configure(config, false);
            deserializer.configure(reader, false);
            return deserializer.deserialize(topic, serializer.serialize(topic, record));
        }
    }

    private static void sameFrame(String scope, GenericRecord record) {
        Object decoded = roundTrip(scope, "t." + scope, record);
        assertNotNull(decoded);
        assertEquals(TaFixtures.frame(record), AvroFrameJson.render((GenericRecord) decoded, Integer.MAX_VALUE, false), scope);
    }

    @Test
    void everyDisplayedRecordIsTheSameFrameAfterTheWire() {
        sameFrame("tools", TaFixtures.tools("M5", 5, "OK"));
        sameFrame("geometry", TaFixtures.geometrySet("TF_5M", 5));
        sameFrame("geometryUnavailable", TaFixtures.geometryUnavailable("TF_15M", 5));
        sameFrame("waves", TaFixtures.waveSet("M5", 1, 2, "OK"));
        sameFrame("cycles", TaFixtures.cycleSet("M5", 1, 2, "OK"));
        GenericRecord chart = TaFixtures.chart("c", "M5", 1);
        chart.put("params", Map.of("boxTicks", "4", "atrLen", "14"));
        sameFrame("chart", chart);
        sameFrame("column", TaFixtures.column("c", 3, 2));
        for (String kind : List.of("PfBreakEvent", "PfLineEvent", "PfCountEvent", "PfMaCrossEvent", "PfVolumeDivergenceEvent", "PfMoneyFlowDivergenceEvent", "PfBreakVolumeCheck")) {
            sameFrame(kind, TaFixtures.pfEvent(kind, "c", "k", 5, 6));
        }
        sameFrame("nodes", TaFixtures.nodes("RTH", 20, 7));
        sameFrame("flow", TaFixtures.flowBar("M15", 900_000, 8));
        sameFrame("climax", TaFixtures.climax("k", "M5", 300_000, 9));
    }

    @Test
    void aDecodedRecordFlowsThroughItsRelayToTheSameFrameTheLiveBroadcastCarries() {
        GenericRecord tools = TaFixtures.tools("M5", 5, "OK");
        String[] frame = TimeframeSetRelay.structureTools().onLive(TimeframeSetRelay.TOOLS_EVENT, roundTrip("relay", "t.relay", tools));
        assertEquals(TaFixtures.frame(tools), frame[1]);
        GenericRecord chart = TaFixtures.chart("c", "M1", 5);
        chart.put("params", Map.of("a", "1"));
        String[] chartFrame = new TradeToolsRelay().onLive("trade-tools-pf-chart", roundTrip("chartrelay", "t.chartrelay", chart));
        assertTrue(chartFrame[1].contains("\"params\":{\"a\":\"1\"}"), chartFrame[1]);
    }

    @Test
    void thePhaseDCheckpointAndHealthRecordsAreNeverRelayedOnAnyRole() throws Exception {
        try (var files = Files.list(Path.of("src", "test", "resources", "ta"))) {
            List<String> names = files.map(p -> p.getFileName().toString()).toList();
            assertTrue(names.containsAll(List.of("TtCheckpointManifest.avsc", "TtCheckpointChunk.avsc", "TtFamilyStatus.avsc")));
        }
        var relay = new TradeToolsRelay();
        for (String internal : List.of("TtCheckpointManifest", "TtCheckpointChunk", "TtFamilyStatus")) {
            for (String role : TradeToolsRelay.ROLES) {
                assertNull(relay.onLive(role, TaFixtures.minimal(TaFixtures.schema(internal))), internal + " on " + role);
            }
        }
        assertEquals(18, relay.dropsForTest());
    }
}

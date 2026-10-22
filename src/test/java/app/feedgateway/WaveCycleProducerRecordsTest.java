package app.feedgateway;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.confluent.kafka.schemaregistry.avro.AvroSchema;
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient;
import io.confluent.kafka.serializers.KafkaAvroSerializer;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericRecord;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

/**
 * ELLIOTT-FIB-CYCLE-TOOLS revision 21 (WaveSet and CycleSet version 2): the waves relay forwards the measured evidence VERBATIM. The records here are the bytes the
 * REAL producer published (wave-cycle-service of options-edge-processing, commit 063897ee, Rig with the registered default parameter set, Confluent-framed, built by
 * the web repository's scripts/waves-fixtures/WavesFixtureGen, second argument): they pass through the production {@link GatewayAvroDeserializer} against a registry
 * that holds the version-2 schema under the producer's ids, then through {@link WaveCycleRelay}; the frame must equal, node for node, the JSON the generator wrote from the
 * decoded record by its OWN code (independent of the gateway's renderer). The gateway adds, drops and renames nothing and judges none of the new vocabularies (the page is the full validator).
 */
class WaveCycleProducerRecordsTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String WAVE_SUBJECT = "com.optionsedge.contracts.wavecycle.WaveSet", CYCLE_SUBJECT = "com.optionsedge.contracts.wavecycle.CycleSet";

    private static final List<String> SCOPES = List.of("e21-real", "e21-evidence", "e21-size", "e21-v1");

    /** The mock registry keeps its scopes in static state: drop them so nothing leaks into another test of the JVM. */
    @AfterAll
    static void dropScopes() {
        SCOPES.forEach(io.confluent.kafka.schemaregistry.testutil.MockSchemaRegistry::dropScope);
    }

    private static JsonNode records() throws Exception {
        try (InputStream in = WaveCycleProducerRecordsTest.class.getResourceAsStream("/ta-producer/wave-cycle-records.json")) {
            return JSON.readTree(in).get("records");
        }
    }

    private static Schema schema(String dir, String name) throws Exception {
        try (InputStream in = WaveCycleProducerRecordsTest.class.getResourceAsStream("/" + dir + "/" + name + ".avsc")) {
            return new Schema.Parser().parse(new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
        }
    }

    /** A registry holding the producer's schemas under the ids its bytes carry, and the production deserializer configured for it. */
    private static GatewayAvroDeserializer deserializer(String scope, String topic, String schemaDir, int waveId, int cycleId) throws Exception {
        SchemaRegistryClient registry = io.confluent.kafka.schemaregistry.testutil.MockSchemaRegistry.getClientForScope(scope);
        registry.register(WAVE_SUBJECT, new AvroSchema(schema(schemaDir, "WaveSet")), 1, waveId);
        registry.register(CYCLE_SUBJECT, new AvroSchema(schema(schemaDir, "CycleSet")), 1, cycleId);
        Map<String, Object> reader = new HashMap<>();
        reader.put("schema.registry.url", "mock://" + scope);
        reader.put("auto.register.schemas", false);
        reader.put("value.subject.name.strategy", "io.confluent.kafka.serializers.subject.RecordNameStrategy");
        reader.put("specific.avro.reader", "false");
        reader.put(GatewayAvroDeserializer.SPX_PRICE_TOPIC, "underlying.spx.price");
        reader.put(GatewayAvroDeserializer.TOLERANT_TOPICS, topic);
        GatewayAvroDeserializer d = new GatewayAvroDeserializer();
        d.configure(reader, false);
        return d;
    }

    private static JsonNode frameData(String[] frame) throws Exception {
        assertNotNull(frame);
        return JSON.readTree(frame[1]);
    }

    @Test
    void everyRealProducerRecordIsRelayedAsTheExactJsonTheProducerSideDecodingWrote() throws Exception {
        JsonNode records = records();
        assertEquals(6, records.size(), "waves and cycles of 1m, 5m and 15m");
        try (GatewayAvroDeserializer d = deserializer("e21-real", "t.e21", "ta", 101, 102)) {
            var relay = new WaveCycleRelay();
            for (JsonNode r : records) {
                byte[] bytes = Base64.getDecoder().decode(r.get("framedBase64").asText());
                assertEquals(r.get("producerSchemaId").asInt(), java.nio.ByteBuffer.wrap(bytes, 1, 4).getInt(), "the producer's own frame");
                Object decoded = d.deserialize("t.e21", bytes);
                assertTrue(decoded instanceof GenericRecord, r.get("name").asText() + " must decode, not be UNDECODABLE");
                String event = r.get("event").asText();
                String[] frame = relay.onLive(event, decoded);
                assertNotNull(frame, r.get("name").asText());
                assertEquals(event, frame[0]);
                assertEquals(r.get("json"), frameData(frame), r.get("name").asText() + ": the frame is the record, verbatim");
            }
            assertEquals(0, relay.dropsForTest());
            assertEquals(0, relay.regressionsForTest());
            assertEquals(6, relay.acceptedForTest());
        }
    }

    @Test
    void theMeasuredEvidenceReachesTheHelloAndTheLiveFrameWithItsFixedStatementsUntouched() throws Exception {
        JsonNode records = records();
        try (GatewayAvroDeserializer d = deserializer("e21-evidence", "t.e21e", "ta", 101, 102)) {
            var relay = new WaveCycleRelay();
            for (JsonNode r : records) {
                relay.onLive(r.get("event").asText(), d.deserialize("t.e21e", Base64.getDecoder().decode(r.get("framedBase64").asText())));
            }
            JsonNode hello = JSON.readTree("{" + relay.helloField(1 << 20).substring(1) + "}").get("waves");
            for (String tf : List.of("5m", "15m")) {
                for (String kind : List.of("waves", "cycles")) {
                    JsonNode cal = hello.get(tf).get(kind).get("calibration");
                    assertEquals("CALIBRATED", cal.get("state").asText(), tf + " " + kind);
                    assertEquals(13, cal.get("results").size(), tf + " " + kind);
                    List<String> families = new ArrayList<>();
                    cal.get("results").forEach(x -> families.add(x.get("family").asText()));
                    assertEquals(List.of("COUNT_DETECTION", "RETRACEMENT", "WAVE_PROJECTION", "WAVE_EQUALITY", "ALTERNATION", "FOURTH_WAVE_REFERENCE", "CHANNEL", "FIB_TIME", "CYCLE_WINDOW",
                            "CYCLE_TRANSLATION", "CYCLE_OSCILLATOR", "CYCLE_20D", "SEASONALITY"), families);
                    assertEquals("Fibonacci time targets: no measured edge beyond matched random controls (n=6001, adjusted p=0.982, history 2017-10..2026-10)",
                            cal.get("results").get(7).get("statement").asText());
                    assertEquals("NOT_TESTABLE", cal.get("results").get(9).get("measuredEdge").asText());
                    assertTrue(cal.get("results").get(9).get("effectMilli").isNull() || cal.get("results").get(9).get("effectMilli").isInt(), "unions render bare");
                }
                for (JsonNode count : hello.get(tf).get("waves").get("counts")) {
                    assertEquals("CALIBRATED", count.get("measurement").get("state").asText());
                    assertEquals("NONE", count.get("measurement").get("measuredEdge").asText());
                    assertTrue(count.get("measurement").get("statement").asText().startsWith("Elliott count candidates: no measured edge beyond matched random controls"));
                }
            }
            // the 1m set is not calibrated: no summary, and every count says so
            assertTrue(hello.get("1m").get("waves").get("calibration").isNull());
            assertTrue(hello.get("1m").get("cycles").get("calibration").isNull());
            for (JsonNode count : hello.get("1m").get("waves").get("counts")) {
                assertEquals("NOT_CALIBRATED", count.get("measurement").get("state").asText());
                assertEquals("NOT_TESTABLE", count.get("measurement").get("measuredEdge").asText());
            }
        }
    }

    @Test
    void theRealRecordsFitTheHelloWithRoomToSpare() throws Exception {
        JsonNode records = records();
        try (GatewayAvroDeserializer d = deserializer("e21-size", "t.e21s", "ta", 101, 102)) {
            var relay = new WaveCycleRelay();
            int jsonBytes = 0;
            for (JsonNode r : records) {
                String[] frame = relay.onLive(r.get("event").asText(), d.deserialize("t.e21s", Base64.getDecoder().decode(r.get("framedBase64").asText())));
                jsonBytes += StudiesRelay.utf8Length(frame[1]);
                assertTrue(StudiesRelay.utf8Length(frame[1]) < TaRelay.FRAME_MAX_BYTES);
            }
            String part = relay.helloField(Long.MAX_VALUE);
            assertTrue(StudiesRelay.utf8Length(part) < WaveCycleRelay.HELLO_PART_BYTES / 2, "a day of real sets (" + jsonBytes + " bytes) uses under half of the 384 KiB part: " + StudiesRelay.utf8Length(part));
            JsonNode hello = JSON.readTree("{" + part.substring(1) + "}").get("waves");
            for (String tf : List.of("1m", "5m", "15m")) {
                assertFalse(hello.get(tf).isNull(), tf);
            }
        }
    }

    @Test
    void aVersion1RecordOfAnUpgradedRegistryStillRelaysAndCarriesNoInventedFields() throws Exception {
        // the version-1 schema under the ids a not-yet-upgraded producer stamps (2401, 2402): the relay renders the WRITER schema, so the frame has no calibration or measurement
        String scope = "e21-v1";
        SchemaRegistryClient registry = io.confluent.kafka.schemaregistry.testutil.MockSchemaRegistry.getClientForScope(scope);
        registry.register(WAVE_SUBJECT, new AvroSchema(schema("ta-v1", "WaveSet")), 1, 2401);
        registry.register(CYCLE_SUBJECT, new AvroSchema(schema("ta-v1", "CycleSet")), 1, 2402);
        registry.register(WAVE_SUBJECT, new AvroSchema(schema("ta", "WaveSet")), 2, 2406);
        registry.register(CYCLE_SUBJECT, new AvroSchema(schema("ta", "CycleSet")), 2, 2407);
        Map<String, Object> config = new HashMap<>();
        config.put("schema.registry.url", "mock://" + scope);
        config.put("auto.register.schemas", false);
        config.put("use.latest.version", false);
        config.put("value.subject.name.strategy", "io.confluent.kafka.serializers.subject.RecordNameStrategy");
        Map<String, Object> reader = new HashMap<>(config);
        reader.put("specific.avro.reader", "false");
        reader.put(GatewayAvroDeserializer.SPX_PRICE_TOPIC, "underlying.spx.price");
        reader.put(GatewayAvroDeserializer.TOLERANT_TOPICS, "t.v1");
        GenericRecord oldWave = TaFixtures.minimal(schema("ta-v1", "WaveSet"));
        GenericRecord oldCycle = TaFixtures.minimal(schema("ta-v1", "CycleSet"));
        for (GenericRecord r : List.of(oldWave, oldCycle)) {
            GenericRecord env = TaFixtures.child(r, "env");
            TaFixtures.put(env, "schemaVersion", 1);
            TaFixtures.put(env, "timeframe", "M5");
            TaFixtures.put(env, "epoch", 1);
            TaFixtures.put(env, "seq", 1L);
            TaFixtures.put(env, "state", "OK");
            TaFixtures.put(env, "asOfMs", 1_790_000_000_001L);
        }
        try (KafkaAvroSerializer serializer = new KafkaAvroSerializer(); GatewayAvroDeserializer d = new GatewayAvroDeserializer()) {
            serializer.configure(config, false);
            d.configure(reader, false);
            var relay = new WaveCycleRelay();
            byte[] wave = serializer.serialize("t.v1", oldWave);
            assertEquals(2401, java.nio.ByteBuffer.wrap(wave, 1, 4).getInt(), "the serializer finds the exact schema, so a version-1 record keeps its version-1 id");
            JsonNode w = frameData(relay.onLive(WaveCycleRelay.WAVES_EVENT, d.deserialize("t.v1", wave)));
            JsonNode c = frameData(relay.onLive(WaveCycleRelay.CYCLES_EVENT, d.deserialize("t.v1", serializer.serialize("t.v1", oldCycle))));
            assertFalse(w.has("calibration"));
            assertFalse(c.has("calibration"));
            assertEquals(2, relay.acceptedForTest());
        }
    }
}

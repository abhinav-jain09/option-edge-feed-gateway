package app.feedgateway;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.optionsedge.contracts.liquidityvalidator.LiquidityValidatorSchemas;
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient;
import io.confluent.kafka.schemaregistry.testutil.MockSchemaRegistry;
import io.confluent.kafka.serializers.KafkaAvroSerializer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * The validator label relay at its boundary, fed the RAW bytes a consumer hands it, against an in-memory registry holding the fixed ids (3601-3604): the id check
 * before decoding, the registry decode through the gateway's own deserializer, the result -> label / sentence / tone table, the live / catch-up / speak windows,
 * the connect replay (cached, never spoken) and a disabled relay.
 */
class LiquidityValidatorRelayTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final GatewaySettings SETTINGS = new GatewaySettings();
    private static final String TOPIC = SETTINGS.liquidityValidatorTopic();
    private static final AtomicLong SCOPES = new AtomicLong();
    private static final long NOW = 1_800_000_000_000L;

    private static final class Sink implements java.util.function.BiConsumer<String, String> {
        final List<String[]> frames = new ArrayList<>();

        @Override
        public void accept(String event, String json) {
            frames.add(new String[] {event, json});
        }

        JsonNode json(int i) throws Exception {
            return JSON.readTree(frames.get(i)[1]);
        }
    }

    private final KafkaAvroSerializer serializer = new KafkaAvroSerializer();
    private final GatewayAvroDeserializer deserializer = new GatewayAvroDeserializer();

    LiquidityValidatorRelayTest() {
        String scope = "liquidity-validator-relay-" + SCOPES.incrementAndGet();
        SchemaRegistryClient registry = MockSchemaRegistry.getClientForScope(scope);
        LiquidityValidatorSchemas.register(registry);
        serializer.configure(LiquidityValidatorSchemas.serdeConfig("mock://" + scope), false);
        Map<String, Object> reader = new HashMap<>();
        reader.put("schema.registry.url", "mock://" + scope);
        reader.put("specific.avro.reader", "false");
        reader.put(GatewayAvroDeserializer.SPX_PRICE_TOPIC, "underlying.spx.price");
        reader.put(GatewayAvroDeserializer.TOLERANT_TOPICS, TOPIC);
        deserializer.configure(reader, false);
    }

    @AfterEach
    void close() {
        serializer.close();
        deserializer.close();
    }

    /** A value for every field of the schema: the evaluation record's own required fields are overwritten by the caller. */
    private static Object blank(Schema s) {
        switch (s.getType()) {
            case STRING:
                return "";
            case LONG:
                return 0L;
            case INT:
                return 0;
            case BOOLEAN:
                return false;
            case ARRAY:
                return new ArrayList<>();
            case UNION:
                return null;
            case RECORD:
                GenericRecord r = new GenericData.Record(s);
                for (Schema.Field f : s.getFields()) {
                    r.put(f.name(), blank(f.schema()));
                }
                return r;
            default:
                throw new IllegalStateException("unhandled " + s.getType());
        }
    }

    private static GenericRecord evaluation(String annotationId, String result, long evaluatedAt) {
        GenericRecord r = (GenericRecord) blank(LiquidityValidatorSchemas.EVALUATION.schema());
        r.put("evaluationId", "ev-" + annotationId);
        r.put("tradeDate", "2026-11-06");
        r.put("annotationId", annotationId);
        r.put("patternId", "SYMMETRICAL_TRIANGLE");
        r.put("timeframe", "1m");
        r.put("direction", "UP");
        r.put("releaseVisibleAtMs", evaluatedAt - 10_000L);
        r.put("result", result);
        r.put("resultReasons", new ArrayList<>(List.of("WALL_DATA_BLOCKED")));
        r.put("evaluatedAtWallMs", evaluatedAt);
        return r;
    }

    private byte[] bytes(GenericRecord r) {
        return serializer.serialize("any-topic", r);
    }

    private LiquidityValidatorRelay relay(Sink sink) {
        return new LiquidityValidatorRelay(SETTINGS, sink, deserializer);
    }

    @Test
    void aLiveEvaluationIsRelayedWithItsLabelSentenceAndToneAndSpeakTrue() throws Exception {
        Sink sink = new Sink();
        LiquidityValidatorRelay relay = relay(sink);
        byte[] b = bytes(evaluation("ann-1", "STRONG_CONTINUATION", NOW));
        assertEquals(3604, LiquidityValidatorSchemas.assertFramedAs(b, LiquidityValidatorSchemas.EVALUATION));
        relay.accept(TOPIC, "k", b, NOW + 500);
        assertEquals(1, sink.frames.size());
        assertEquals("liquidity-validator", sink.frames.get(0)[0]);
        JsonNode j = sink.json(0);
        assertEquals("ann-1", j.get("annotationId").asText());
        assertEquals("1m", j.get("tf").asText());
        assertEquals("SYMMETRICAL_TRIANGLE", j.get("patternId").asText());
        assertEquals("UP", j.get("direction").asText());
        assertEquals("STRONG_CONTINUATION", j.get("result").asText());
        assertEquals("Liquidity: strong continuation", j.get("label").asText());
        assertEquals("Liquidity confirms, strong continuation", j.get("speech").asText());
        assertEquals("good", j.get("tone").asText());
        assertEquals("WALL_DATA_BLOCKED", j.get("resultReasons").get(0).asText());
        assertTrue(j.get("speak").booleanValue());
        assertFalse(j.get("cached").booleanValue());
        assertEquals(NOW, j.get("evaluatedAtWallMs").longValue());
    }

    @Test
    void everyResultHasItsOwnSentenceAndTheToneFollowsWhatItSays() {
        Map<String, String> tone = Map.of("STRONG_CONTINUATION", "good", "CONTINUATION", "good", "WEAKENING", "warn", "FAILURE_LIKELY", "warn", "LATE_SIGNAL", "warn", "UNCERTAIN", "neutral");
        java.util.Set<String> sentences = new java.util.HashSet<>();
        for (var e : tone.entrySet()) {
            LiquidityValidatorRelay.Wording w = LiquidityValidatorRelay.wording(e.getKey());
            assertEquals(e.getValue(), w.tone(), e.getKey());
            assertTrue(sentences.add(w.speech()), "a distinct sentence per result: " + e.getKey());
        }
        assertNull(LiquidityValidatorRelay.wording("SOMETHING_NEW"), "an unknown result is refused, never guessed");
    }

    @Test
    void anEvaluationOutsideTheReplayWindowIsRingedForAConnectButNeverBroadcastAndNeverSpoken() throws Exception {
        Sink sink = new Sink();
        LiquidityValidatorRelay relay = relay(sink);
        relay.accept(TOPIC, "k", bytes(evaluation("old", "LATE_SIGNAL", NOW - 3_600_000L)), NOW);
        assertEquals(0, sink.frames.size(), "the session-open catch-up is not live");
        Sink late = new Sink();
        relay.replay(late, NOW);
        assertEquals(1, late.frames.size());
        JsonNode j = late.json(0);
        assertTrue(j.get("cached").booleanValue());
        assertFalse(j.get("speak").booleanValue());
        assertEquals("old", j.get("annotationId").asText());
    }

    @Test
    void aLiveRecordPastTheVoiceWindowIsShownButNotSpoken() throws Exception {
        Sink sink = new Sink();
        LiquidityValidatorRelay relay = relay(sink);
        relay.accept(TOPIC, "k", bytes(evaluation("slowish", "UNCERTAIN", NOW)), NOW + SETTINGS.liquidityValidatorVoiceFreshMs() + 1_000L);
        assertEquals(1, sink.frames.size());
        assertFalse(sink.json(0).get("speak").booleanValue());
        assertFalse(sink.json(0).get("cached").booleanValue());
    }

    @Test
    void aWrongSchemaIdIsRefusedBeforeDecodingAndACorruptBodyIsCounted() {
        Sink sink = new Sink();
        LiquidityValidatorRelay relay = relay(sink);
        byte[] b = bytes(evaluation("a", "UNCERTAIN", NOW));
        byte[] v1 = b.clone();
        v1[4] = (byte) (v1[4] - 3);   // id 3601: the archival first version is not what this relay reads
        relay.accept(TOPIC, "k", v1, NOW);
        assertEquals(1, relay.schemaIdRefusedForTest());
        relay.accept(TOPIC, "k", null, NOW);
        relay.accept("some.other.topic", "k", b, NOW);
        assertEquals(2, relay.dropsForTest(), "a tombstone and a foreign topic");
        assertEquals(0, sink.frames.size());
    }

    @Test
    void aRecordWithoutAnnotationIdOrWithAnUnknownResultNeverReachesASocket() {
        Sink sink = new Sink();
        LiquidityValidatorRelay relay = relay(sink);
        relay.accept(TOPIC, "k", bytes(evaluation("", "UNCERTAIN", NOW)), NOW);
        relay.accept(TOPIC, "k", bytes(evaluation("x", "BRAND_NEW_RESULT", NOW)), NOW);
        assertEquals(0, sink.frames.size());
        assertEquals(2, relay.dropsForTest());
    }

    @Test
    void theRingKeepsOnlyTheNewestAndAReplayIsInLogOrder() throws Exception {
        Sink sink = new Sink();
        LiquidityValidatorRelay relay = relay(sink);
        for (int i = 0; i < SETTINGS.liquidityValidatorRing() + 3; i++) {
            relay.accept(TOPIC, "k", bytes(evaluation("a" + i, "UNCERTAIN", NOW)), NOW);
        }
        Sink replay = new Sink();
        relay.replay(replay, NOW);
        assertEquals(SETTINGS.liquidityValidatorRing(), replay.frames.size());
        assertEquals("a3", replay.json(0).get("annotationId").asText());
    }

    @Test
    void aDisabledRelayConsumesNothing() {
        LiquidityValidatorRelay relay = relay(new Sink());
        relay.start();
        assertFalse(relay.consuming(), "LIQUIDITY_VALIDATOR_RELAY_ENABLED defaults to false");
    }
}

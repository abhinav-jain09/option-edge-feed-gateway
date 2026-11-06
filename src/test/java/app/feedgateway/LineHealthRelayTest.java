package app.feedgateway;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.optionsedge.contracts.linehealth.LineHealthSchemas;
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient;
import io.confluent.kafka.schemaregistry.testutil.MockSchemaRegistry;
import io.confluent.kafka.serializers.KafkaAvroSerializer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.generic.GenericRecordBuilder;
import org.apache.kafka.common.serialization.Deserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * The line-health relay at its boundary, fed the raw bytes a consumer hands it. THE RECORDS ARE TEST FIXTURES built from the real schemas of the contracts jar
 * (ids 3701, 3702, 3703, 3705) and an in-memory registry; none of them has ever been on a Kafka topic.
 */
class LineHealthRelayTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final GatewaySettings SETTINGS = new GatewaySettings();
    private static final String STATE = SETTINGS.lineHealthStateTopic(), EVENTS = SETTINGS.lineHealthEventsTopic();
    private static final String VOICE = SETTINGS.lineHealthVoiceTopic(), STATUS = SETTINGS.lineHealthStatusTopic();
    private static final AtomicLong SCOPES = new AtomicLong();
    private static final long NOW = 1_800_000_000_000L;

    private static final class Sink implements java.util.function.BiConsumer<String, String> {
        final List<String[]> frames = new ArrayList<>();

        @Override
        public void accept(String event, String json) {
            frames.add(new String[] {event, json});
        }

        List<String> events() {
            List<String> out = new ArrayList<>();
            frames.forEach(f -> out.add(f[0]));
            return out;
        }

        JsonNode json(int i) throws Exception {
            return JSON.readTree(frames.get(i)[1]);
        }
    }

    private final KafkaAvroSerializer serializer = new KafkaAvroSerializer();
    private final GatewayAvroDeserializer deserializer = new GatewayAvroDeserializer();
    private final AtomicLong decodes = new AtomicLong();

    LineHealthRelayTest() {
        String scope = "line-health-relay-" + SCOPES.incrementAndGet();
        SchemaRegistryClient registry = MockSchemaRegistry.getClientForScope(scope);
        LineHealthSchemas.register(registry);
        serializer.configure(LineHealthSchemas.serdeConfig("mock://" + scope), false);
        Map<String, Object> reader = new HashMap<>();
        reader.put("schema.registry.url", "mock://" + scope);
        reader.put("specific.avro.reader", "false");
        reader.put(GatewayAvroDeserializer.SPX_PRICE_TOPIC, "underlying.spx.price");
        reader.put(GatewayAvroDeserializer.TOLERANT_TOPICS, STATE + "," + EVENTS + "," + VOICE + "," + STATUS);
        deserializer.configure(reader, false);
    }

    @AfterEach
    void close() {
        serializer.close();
        deserializer.close();
    }

    private LineHealthRelay relay(Sink sink) {
        Deserializer<Object> counting = (topic, data) -> {
            decodes.incrementAndGet();
            return deserializer.deserialize(topic, data);
        };
        LineHealthRelay relay = new LineHealthRelay(SETTINGS, sink, counting);
        relay.goLiveForTest();
        return relay;
    }

    private byte[] bytes(GenericRecord record) {
        return serializer.serialize("any-topic", record);
    }

    private static GenericRecordBuilder state(String lineId, long sequence, String state, Integer gauge, String ownerTag) {
        return new GenericRecordBuilder(LineHealthSchemas.STATE.schema()).set("schemaVersion", 1).set("asOfMs", NOW).set("lineId", lineId).set("chartKey", "L|" + lineId)
                .set("stateRevision", 1L).set("systemSequence", ownerTag == null ? sequence : null).set("role", "SUPPORT").set("state", state).set("gauge", gauge)
                .set("lineCents", 761200L).set("ownerTag", ownerTag);
    }

    private static GenericRecord status(long stateSequence, long systemRecordCount) {
        return new GenericRecordBuilder(LineHealthSchemas.STATUS.schema()).set("schemaVersion", 1).set("asOfMs", NOW).set("stateSequence", stateSequence)
                .set("systemRecordCount", systemRecordCount).build();
    }

    private static GenericRecord voice(String eventId) {
        return new GenericRecordBuilder(LineHealthSchemas.VOICE.schema()).set("schemaVersion", 1).set("asOfMs", NOW).set("eventId", eventId).set("lineId", "A")
                .set("text", "Support 7612 giving way, sellers pushing through").set("repeat", false).build();
    }

    private static GenericRecord event(String eventId, String ownerTag) {
        GenericRecord body = state("A", 1, "GIVING_WAY", 3, null).build();
        return new GenericRecordBuilder(LineHealthSchemas.EVENT.schema()).set("schemaVersion", 1).set("asOfMs", NOW).set("eventId", eventId).set("lineId", "A")
                .set("ownerTag", ownerTag).set("eventType", "GIVING_WAY").set("warnVoice", true).build();
    }

    @Test
    void eachRecordIsCheckedForItsFixedIdDecodedAndRelayedUnderItsFrameWithNullsOmittedAndMarkedLive() throws Exception {
        Sink sink = new Sink();
        LineHealthRelay relay = relay(sink);
        byte[] state = bytes(state("A", 1, "TOUCHING", 7, null).build());
        assertEquals(3701, LineHealthSchemas.assertFramedAs(state, LineHealthSchemas.STATE));
        relay.accept(STATE, "A", state);
        relay.accept(EVENTS, "A|GIVING_WAY|" + NOW, bytes(event("A|GIVING_WAY|" + NOW, null)));
        relay.accept(VOICE, "E1", bytes(voice("E1")));
        assertEquals(List.of("line-health-state", "line-health-event", "line-health-voice"), sink.events());
        JsonNode frame = sink.json(0);
        assertEquals(7, frame.get("gauge").asInt());
        assertEquals("L|A", frame.get("chartKey").asText());
        assertFalse(frame.get("cached").asBoolean(), "a live forward is cached:false");
        assertFalse(frame.has("bookState"), "a null member is omitted, not sent as null");
        assertEquals("Support 7612 giving way, sellers pushing through", sink.json(2).get("text").asText());
        assertEquals(0, relay.dropsForTest());
        assertEquals(3, decodes.get());
    }

    @Test
    void aWrongSchemaIdIsRefusedBeforeAnyDecodingAndAWrongKeyIsDropped() {
        Sink sink = new Sink();
        LineHealthRelay relay = relay(sink);
        relay.accept(STATE, "A", bytes(status(1, 1)));   // a status record on the state topic
        assertEquals(1, relay.schemaIdRefusedForTest(LineHealthSchemas.STATE));
        assertEquals(0, decodes.get(), "nothing is decoded when the id check refuses");
        relay.accept(STATE, "WRONG", bytes(state("A", 1, "TOUCHING", 7, null).build()));
        relay.accept(STATE, "A", "{\"json\":true}".getBytes());
        relay.accept(STATE, "A", null);
        assertEquals(2, relay.dropsForTest(), "a key that is not the lineId, and a tombstone");
        assertTrue(sink.frames.isEmpty());
    }

    @Test
    void aStatusIsHeldUntilTheStateConsumerHasReachedTheStateEndCapturedWithIt() throws Exception {
        Sink sink = new Sink();
        LineHealthRelay relay = relay(sink);
        relay.accept(STATE, "A", bytes(state("A", 1, "TOUCHING", 7, null).build()));
        relay.accept(STATUS, "SPX", bytes(status(10, 1)));
        relay.captureBound(5);          // the state end when the status was read
        relay.release(4);               // the state consumer is behind
        assertEquals(List.of("line-health-state"), sink.events(), "the status frame waits");
        assertEquals(-1, relay.releasedCountForTest());
        relay.release(5);
        assertEquals(List.of("line-health-state", "line-health-status"), sink.events());
        assertEquals(1, relay.releasedCountForTest());
        assertEquals(10, sink.json(1).get("stateSequence").asInt());
    }

    @Test
    void theHelloCheckpointCarriesStatusStatesAndTheWatermarkAsTheLargerOfCountAndLargestSequence() throws Exception {
        Sink sink = new Sink();
        LineHealthRelay relay = relay(sink);
        assertEquals("{\"status\":null,\"states\":[],\"watermark\":null}", relay.helloField());
        relay.accept(STATE, "A", bytes(state("A", 3, "TOUCHING", 7, null).build()));
        relay.accept(STATE, "B", bytes(state("B", 9, "APPROACHING", 0, null).build()));   // newer than the status: a record between two status records
        relay.accept(STATUS, "SPX", bytes(status(10, 8)));
        relay.captureBound(2);
        relay.release(2);
        JsonNode hello = JSON.readTree(relay.helloField());
        assertEquals(9, hello.get("watermark").asLong(), "max(8, 9)");
        assertEquals(2, hello.get("states").size());
        assertEquals(10, hello.get("status").get("stateSequence").asLong());
        assertFalse(hello.get("status").has("cached"));
        assertEquals("{\"lineHealth\":" + relay.helloField() + "}", relay.resumeReply());
    }

    @Test
    void aRemovedLineLeavesTheCheckpointAndStaysGoneAfterALaterRefresh() throws Exception {
        Sink sink = new Sink();
        LineHealthRelay relay = relay(sink);
        relay.accept(STATE, "A", bytes(state("A", 1, "TOUCHING", 7, null).build()));
        relay.accept(STATE, "B", bytes(state("B", 2, "TOUCHING", 4, null).build()));
        relay.accept(STATE, "A", bytes(state("A", 3, "REMOVED", null, null).build()));
        relay.accept(STATUS, "SPX", bytes(status(5, 3)));
        relay.captureBound(3);
        relay.release(3);
        JsonNode hello = JSON.readTree(relay.helloField());
        assertEquals(1, hello.get("states").size());
        assertEquals("B", hello.get("states").get(0).get("lineId").asText());
        assertEquals(3, hello.get("watermark").asLong(), "the removal's sequence still counts");
    }

    @Test
    void aUserLineRecordReachesNoSocketAndNoHelloUntilTheOwnerFilterExists() throws Exception {
        Sink sink = new Sink();
        LineHealthRelay relay = relay(sink);
        relay.accept(STATE, "ui-9f-20261106-d1", bytes(state("ui-9f-20261106-d1", 0, "TOUCHING", 6, "9f").build()));
        relay.accept(EVENTS, "x", bytes(event("x", "9f")));
        relay.accept(STATUS, "SPX", bytes(status(1, 0)));
        relay.captureBound(0);
        relay.release(0);
        assertEquals(List.of("line-health-status"), sink.events());
        assertEquals(2, relay.userRecordsRefusedForTest());
        assertEquals(0, JSON.readTree(relay.helloField()).get("states").size());
    }

    @Test
    void aDisabledRelayConsumesNothing() {
        LineHealthRelay relay = new LineHealthRelay(SETTINGS, new Sink(), null);
        relay.start();
        assertFalse(relay.consuming());
    }
}

package app.feedgateway;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.confluent.kafka.schemaregistry.avro.AvroSchema;
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient;
import io.confluent.kafka.schemaregistry.testutil.MockSchemaRegistry;
import io.confluent.kafka.serializers.KafkaAvroSerializer;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.generic.GenericRecordBuilder;
import org.apache.kafka.common.serialization.Deserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * The break-line relay at its boundary, fed the raw bytes a consumer hands it (design liq-S section 14.8, requirements BS13-W-007 and BS13-W-008). THE RECORDS ARE TEST FIXTURES
 * built from the five Avro schemas of design section 5 (src/test/resources/breakline, ids 3901-3905) and an in-memory registry; none of them has ever been on a Kafka topic.
 */
class BreakLineRelayTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final GatewaySettings SETTINGS = new GatewaySettings();
    private static final String STATE = SETTINGS.breakLineStateTopic(), EVENTS = SETTINGS.breakLineEventsTopic(), STATUS = SETTINGS.breakLineStatusTopic();
    private static final AtomicLong SCOPES = new AtomicLong();
    private static final long NOW = 1_800_000_000_000L;
    private static final String ALICE = LineHealthOwnerTag.of("alice"), BOB = LineHealthOwnerTag.of("bob");

    static Schema schema(String name) {
        try (InputStream in = BreakLineRelayTest.class.getResourceAsStream("/breakline/" + name + ".avsc")) {
            return new Schema.Parser().parse(in);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    static final Schema STATE_SCHEMA = schema("BreakLineState"), EVENT_SCHEMA = schema("BreakLineEvent"), STATUS_SCHEMA = schema("BreakLineStatus");

    private static final class Sinks implements java.util.function.BiConsumer<String, String>, BreakLineRelay.TargetedSink {
        final List<String[]> global = new ArrayList<>();
        final List<String[]> targeted = new ArrayList<>();   // owner, event, json

        @Override
        public void accept(String event, String json) {
            global.add(new String[] {event, json});
        }

        @Override
        public void accept(String ownerTag, String event, String json) {
            targeted.add(new String[] {ownerTag, event, json});
        }
    }

    private final KafkaAvroSerializer serializer = new KafkaAvroSerializer();
    private final GatewayAvroDeserializer deserializer = new GatewayAvroDeserializer();
    private final AtomicLong decodes = new AtomicLong();

    BreakLineRelayTest() throws Exception {
        String scope = "break-line-relay-" + SCOPES.incrementAndGet();
        SchemaRegistryClient registry = MockSchemaRegistry.getClientForScope(scope);
        registry.register("com.optionsedge.contracts.breakline.BreakLineState", new AvroSchema(STATE_SCHEMA), 1, 3902);
        registry.register("com.optionsedge.contracts.breakline.BreakLineEvent", new AvroSchema(EVENT_SCHEMA), 1, 3903);
        registry.register("com.optionsedge.contracts.breakline.BreakLineStatus", new AvroSchema(STATUS_SCHEMA), 1, 3904);
        Map<String, Object> w = new HashMap<>();
        w.put("schema.registry.url", "mock://" + scope);
        w.put("auto.register.schemas", false);
        w.put("use.latest.version", false);
        w.put("value.subject.name.strategy", "io.confluent.kafka.serializers.subject.RecordNameStrategy");
        serializer.configure(w, false);
        Map<String, Object> reader = new HashMap<>();
        reader.put("schema.registry.url", "mock://" + scope);
        reader.put("specific.avro.reader", "false");
        reader.put(GatewayAvroDeserializer.SPX_PRICE_TOPIC, "underlying.spx.price");
        reader.put(GatewayAvroDeserializer.TOLERANT_TOPICS, STATE + "," + EVENTS + "," + STATUS);
        deserializer.configure(reader, false);
    }

    @AfterEach
    void close() {
        serializer.close();
        deserializer.close();
    }

    private BreakLineRelay relay(Sinks sinks) {
        Deserializer<Object> counting = (topic, data) -> {
            decodes.incrementAndGet();
            return deserializer.deserialize(topic, data);
        };
        BreakLineRelay relay = new BreakLineRelay(SETTINGS, sinks, sinks, counting);
        relay.goLive();
        return relay;
    }

    private byte[] bytes(GenericRecord record) {
        return serializer.serialize("any-topic", record);
    }

    static GenericRecordBuilder state(String lineId, String owner, String lineStatus, long sequence, String state, Integer score) {
        return new GenericRecordBuilder(STATE_SCHEMA).set("schemaVersion", 1).set("messageType", "BREAK_LINE_STATE").set("lineId", lineId).set("ownerTag", owner)
                .set("lineStatus", lineStatus).set("lineRevision", 1L).set("domain", "SPX").set("priceUnits", 725200L).set("state", state).set("score", score)
                .set("scoreStatus", score == null ? "PENDING" : "SCORED").set("asOfEpochMillis", NOW).set("eventSequence", sequence).set("explanation", "Line broke up through 7,252.00 at 10:41:07.");
    }

    static GenericRecord status(boolean flag) {
        return new GenericRecordBuilder(STATUS_SCHEMA).set("schemaVersion", 1).set("messageType", "BREAK_LINE_STATUS").set("statusEpochMillis", NOW).set("dataAsOfEpochMillis", NOW - 800)
                .set("dataLagMs", 800L).set("mode", "SHADOW").set("calibrationState", "CORPUS_ONLY_PROVISIONAL").set("calibrationFlag", flag).set("activeLines", 2).set("activeOwners", 2).build();
    }

    static GenericRecord event(String eventId, String lineId, String owner, String scope) {
        return new GenericRecordBuilder(EVENT_SCHEMA).set("schemaVersion", 1).set("messageType", "BREAK_LINE_EVENT").set("eventId", eventId).set("scope", scope).set("lineId", lineId)
                .set("ownerTag", owner).set("eventSequence", 5L).set("eventEpochMillis", NOW).set("fromState", "TESTING").set("toState", "BROKEN").build();
    }

    @Test
    void aStateGoesOnlyToItsOwnerWithNullsOmittedAndTheStatusToEveryone() throws Exception {
        Sinks sinks = new Sinks();
        BreakLineRelay relay = relay(sinks);
        byte[] state = bytes(state("bl-" + ALICE + "-d1", ALICE, "ACTIVE", 1, "TESTING", null).build());
        assertEquals(3902, BreakLineRelay.frameId(state));
        relay.accept(STATE, "bl-" + ALICE + "-d1", state);
        relay.accept(STATUS, "break-line", bytes(status(false)));
        relay.accept(EVENTS, "bl-" + ALICE + "-d1|5", bytes(event("bl-" + ALICE + "-d1|5", "bl-" + ALICE + "-d1", ALICE, "TRANSITION")));
        assertEquals(1, sinks.global.size());
        assertEquals("break-line-status", sinks.global.get(0)[0]);
        assertEquals(2, sinks.targeted.size());
        assertEquals(ALICE, sinks.targeted.get(0)[0]);
        assertEquals("break-line-state", sinks.targeted.get(0)[1]);
        assertEquals("break-line-event", sinks.targeted.get(1)[1]);
        JsonNode frame = JSON.readTree(sinks.targeted.get(0)[2]);
        assertEquals("TESTING", frame.get("state").asText());
        assertFalse(frame.has("score"), "a null member is omitted, not sent as null");
        assertFalse(frame.get("cached").asBoolean());
        assertEquals(0, relay.dropsForTest());
        assertEquals(3, decodes.get());
    }

    @Test
    void noStateOrEventFrameIsEverGlobalAndTheAllowlistHoldsOnlyTheHeartbeat() {
        assertTrue(FeedGatewayService.GLOBAL_BROADCAST_EVENTS.contains(BreakLineRelay.STATUS_EVENT));
        assertFalse(FeedGatewayService.GLOBAL_BROADCAST_EVENTS.contains(BreakLineRelay.STATE_EVENT), "a user's line is never broadcast");
        assertFalse(FeedGatewayService.GLOBAL_BROADCAST_EVENTS.contains(BreakLineRelay.EVENT_EVENT));
    }

    @Test
    void aRecordWithNoOwnerReachesNoSocketAndABookkeepingEventIsNotRelayed() {
        Sinks sinks = new Sinks();
        BreakLineRelay relay = relay(sinks);
        relay.accept(STATE, "bl-x", bytes(state("bl-x", null, "ACTIVE", 1, "TESTING", null).build()));
        relay.accept(EVENTS, "watermark|1", bytes(event("watermark|1", null, null, "WATERMARK_ADVANCE")));
        relay.accept(EVENTS, "bl-x|2", bytes(event("bl-x|2", "bl-x", null, "TRANSITION")));
        assertTrue(sinks.global.isEmpty());
        assertTrue(sinks.targeted.isEmpty());
        assertEquals(2, relay.userRecordsRefusedForTest());
        assertEquals(1, relay.internalEventsNotRelayedForTest());
    }

    @Test
    void aWrongSchemaIdIsRefusedBeforeAnyDecodingAndAWrongKeyIsDropped() {
        Sinks sinks = new Sinks();
        BreakLineRelay relay = relay(sinks);
        relay.accept(STATE, "break-line", bytes(status(false)));   // a status record on the state topic
        assertEquals(1, relay.schemaIdRefusedForTest(BreakLineRelay.Kind.STATE));
        assertEquals(0, decodes.get(), "nothing is decoded when the id check refuses");
        relay.accept(STATE, "WRONG", bytes(state("bl-a", ALICE, "ACTIVE", 1, "TESTING", null).build()));
        relay.accept(STATE, "bl-a", "{\"json\":true}".getBytes());
        relay.accept(STATE, "bl-a", null);
        relay.accept(STATUS, "WRONG", bytes(status(false)));
        assertEquals(3, relay.dropsForTest(), "a key that is not the lineId, a tombstone, a status under the wrong key");
        assertEquals(2, relay.schemaIdRefusedForTest(BreakLineRelay.Kind.STATE), "a status frame, and a JSON body that is not framed");
        assertTrue(sinks.global.isEmpty() && sinks.targeted.isEmpty());
    }

    @Test
    void aSchemaVersionOtherThanOneIsRefused() {
        Sinks sinks = new Sinks();
        BreakLineRelay relay = relay(sinks);
        relay.accept(STATE, "bl-a", bytes(state("bl-a", ALICE, "ACTIVE", 1, "TESTING", null).set("schemaVersion", 2).build()));
        assertEquals(1, relay.dropsForTest());
        assertTrue(sinks.targeted.isEmpty());
    }

    @Test
    void theHelloCheckpointCarriesOnlyTheCallersLinesAndTheStatus() throws Exception {
        Sinks sinks = new Sinks();
        BreakLineRelay relay = relay(sinks);
        assertEquals("{\"status\":null,\"states\":[],\"version\":" + versionOf(relay.helloField(ALICE)) + "}", relay.helloField(ALICE));
        relay.accept(STATE, "bl-a1", bytes(state("bl-a1", ALICE, "ACTIVE", 1, "TESTING", null).build()));
        relay.accept(STATE, "bl-a2", bytes(state("bl-a2", ALICE, "ACTIVE", 1, "APPROACHING", null).build()));
        relay.accept(STATE, "bl-b1", bytes(state("bl-b1", BOB, "ACTIVE", 1, "BROKEN", 71).build()));
        relay.accept(STATUS, "break-line", bytes(status(false)));
        JsonNode alice = JSON.readTree(relay.helloField(ALICE)), bob = JSON.readTree(relay.helloField(BOB)), nobody = JSON.readTree(relay.helloField(null));
        assertEquals(2, alice.get("states").size());
        assertEquals(1, bob.get("states").size());
        assertEquals("bl-b1", bob.get("states").get(0).get("lineId").asText());
        assertEquals(0, nobody.get("states").size(), "a socket with no subject receives no line");
        assertEquals(800, alice.get("status").get("dataLagMs").asLong());
        assertFalse(alice.get("status").has("cached"));
        assertFalse(relay.helloField(ALICE).contains("bl-b1"), "no byte of another owner's line is in the serialized hello");
        assertEquals("{\"breakLine\":" + relay.helloField(ALICE) + "}", relay.resumeReply(ALICE));
    }

    private static long versionOf(String field) throws Exception {
        return JSON.readTree(field).get("version").asLong();
    }

    @Test
    void aRemovedLineLeavesTheCheckpointIsForwardedToItsOwnerAndStaysGone() throws Exception {
        Sinks sinks = new Sinks();
        BreakLineRelay relay = relay(sinks);
        relay.accept(STATE, "bl-a1", bytes(state("bl-a1", ALICE, "ACTIVE", 1, "TESTING", null).build()));
        relay.accept(STATE, "bl-a2", bytes(state("bl-a2", ALICE, "ACTIVE", 1, "TESTING", null).build()));
        relay.accept(STATE, "bl-a1", bytes(state("bl-a1", ALICE, "REMOVED", 2, null, null).build()));
        assertEquals(1, relay.foldedLinesForTest());
        JsonNode hello = JSON.readTree(relay.helloField(ALICE));
        assertEquals(1, hello.get("states").size());
        assertEquals("bl-a2", hello.get("states").get(0).get("lineId").asText());
        JsonNode last = JSON.readTree(sinks.targeted.get(2)[2]);
        assertEquals("REMOVED", last.get("lineStatus").asText());
        assertEquals(ALICE, sinks.targeted.get(2)[0]);
    }

    @Test
    void aCheckpointTooLargeForTheHelloFollowsItInPartsAndNoLineIsDropped() throws Exception {
        Sinks sinks = new Sinks();
        System.setProperty("BREAK_LINE_HELLO_MAX_BYTES", "1500");
        BreakLineRelay relay;
        try {
            relay = new BreakLineRelay(new GatewaySettings(), sinks, sinks, deserializer);
        } finally {
            // the setting is read when the checkpoint is built, so it stays set until the end of this test
        }
        relay.goLive();
        for (int i = 0; i < 12; i++) {
            relay.accept(STATE, "bl-a" + i, bytes(state("bl-a" + i, ALICE, "ACTIVE", 1, "TESTING", null).build()));
        }
        JsonNode hello = JSON.readTree(relay.helloField(ALICE));
        assertTrue(hello.get("states").isNull());
        int parts = hello.get("parts").asInt();
        assertTrue(parts > 1);
        List<String> frames = relay.checkpointParts(ALICE);
        assertEquals(parts, frames.size());
        int lines = 0;
        for (String f : frames) {
            JsonNode p = JSON.readTree(f);
            assertEquals(hello.get("version").asLong(), p.get("version").asLong(), "a part shares the hello's version");
            lines += p.get("states").size();
        }
        assertEquals(12, lines);
        assertTrue(relay.checkpointParts(BOB).stream().allMatch(f -> !f.contains(ALICE)));
        System.clearProperty("BREAK_LINE_HELLO_MAX_BYTES");
    }

    @Test
    void theCalibrationFlagTravelsInTheStatusUnchanged() throws Exception {
        Sinks sinks = new Sinks();
        BreakLineRelay relay = relay(sinks);
        relay.accept(STATUS, "break-line", bytes(status(false)));
        relay.accept(STATUS, "break-line", bytes(status(true)));
        assertFalse(JSON.readTree(sinks.global.get(0)[1]).get("calibrationFlag").asBoolean());
        assertTrue(JSON.readTree(sinks.global.get(1)[1]).get("calibrationFlag").asBoolean());
    }

    @Test
    void disabledByDefaultNoThreadAndNoConsumer() {
        BreakLineRelay relay = new BreakLineRelay(SETTINGS, new Sinks(), new Sinks(), null);
        relay.start();
        assertFalse(relay.consuming());
        assertFalse(SETTINGS.breakLineRelayEnabled());
    }
}

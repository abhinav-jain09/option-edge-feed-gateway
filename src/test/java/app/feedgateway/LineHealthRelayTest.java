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
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.MockConsumer;
import org.apache.kafka.clients.consumer.OffsetResetStrategy;
import org.apache.kafka.common.TopicPartition;
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
                .set("systemRecordCount", systemRecordCount).set("statusEpoch", 1L).build();
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

    private static final String TAG_A = LineHealthOwnerTag.of("alice-subject"), TAG_B = LineHealthOwnerTag.of("bob-subject");

    private static final class Targeted implements LineHealthRelay.TargetedSink {
        final List<String[]> frames = new ArrayList<>();

        @Override
        public void accept(String ownerTag, String event, String json) {
            frames.add(new String[] {ownerTag, event, json});
        }
    }

    private LineHealthRelay relay(Sink sink, Targeted targeted) {
        Deserializer<Object> counting = (topic, data) -> deserializer.deserialize(topic, data);
        LineHealthRelay relay = new LineHealthRelay(SETTINGS, sink, targeted, counting);
        relay.goLiveForTest();
        return relay;
    }

    private static GenericRecord userVoice(String eventId, String ownerTag) {
        return new GenericRecordBuilder(LineHealthSchemas.VOICE.schema()).set("schemaVersion", 1).set("asOfMs", NOW).set("eventId", eventId).set("lineId", "ui")
                .set("text", "Support 7612 giving way").set("repeat", false).set("ownerTag", ownerTag).build();
    }

    @Test
    void ownerTagMatchesTheWebTierVectors() {
        // the same vectors are asserted by the web tier's LevelAbsorptionManualLinePublisher.ownerTag test: SHA-256 of the UTF-8 subject, first 16 bytes, lowercase hex
        assertEquals("2bd806c97f0e00af1a1fc3328fa763a9", LineHealthOwnerTag.of("alice"));
        assertEquals(32, LineHealthOwnerTag.of("anything").length());
        assertEquals("2bd806c97f0e00af1a1fc3328fa763a9", LineHealthOwnerTag.of("alice"));
    }

    @Test
    void liveUserRecordsGoOnlyThroughTheTargetedSinkOfTheirOwner() throws Exception {
        Sink sink = new Sink();
        Targeted targeted = new Targeted();
        LineHealthRelay relay = relay(sink, targeted);
        relay.accept(STATE, "ui-a", bytes(state("ui-a", 0, "TOUCHED", 6, TAG_A).build()));
        relay.accept(EVENTS, "ev-a", bytes(event("ev-a", TAG_A)));
        relay.accept(VOICE, "vo-a", bytes(userVoice("vo-a", TAG_A)));
        relay.accept(STATE, "ui-b", bytes(state("ui-b", 0, "TOUCHED", 6, TAG_B).build()));
        assertEquals(List.of(), sink.events(), "no global frame carries a user line");
        assertEquals(4, targeted.frames.size());
        assertEquals(TAG_A, targeted.frames.get(0)[0]);
        assertEquals("line-health-state", targeted.frames.get(0)[1]);
        assertEquals("line-health-event", targeted.frames.get(1)[1]);
        assertEquals("line-health-voice", targeted.frames.get(2)[1]);
        assertEquals(TAG_B, targeted.frames.get(3)[0]);
        assertEquals(0, relay.userRecordsRefusedForTest());
    }

    @Test
    void aSystemRecordStillGoesToTheGlobalSink() {
        Sink sink = new Sink();
        Targeted targeted = new Targeted();
        LineHealthRelay relay = relay(sink, targeted);
        relay.accept(STATE, "A", bytes(state("A", 1, "TOUCHED", 6, null).build()));
        assertEquals(List.of("line-health-state"), sink.events());
        assertEquals(0, targeted.frames.size());
    }

    @Test
    void helloAndResumeCarryOnlyTheCallersOwnLines_noOtherUsersLineInAnyPart() throws Exception {
        Sink sink = new Sink();
        LineHealthRelay relay = relay(sink, new Targeted());
        relay.accept(STATE, "A", bytes(state("A", 1, "TOUCHED", 6, null).build()));
        relay.accept(STATE, "ui-a", bytes(state("ui-a", 0, "TOUCHED", 6, TAG_A).build()));
        relay.accept(STATE, "ui-b", bytes(state("ui-b", 0, "TOUCHED", 6, TAG_B).build()));
        relay.accept(STATUS, "SPX", bytes(status(1, 1)));
        relay.captureBound(3);
        relay.release(3);
        assertEquals(List.of("A", "ui-a"), lineIds(relay.helloField(TAG_A)));
        assertEquals(List.of("A", "ui-b"), lineIds(relay.helloField(TAG_B)));
        assertEquals(List.of("A"), lineIds(relay.helloField(null)), "a socket without a subject gets system lines only");
        assertFalse(relay.resumeReply(TAG_A).contains("ui-b"));
        assertFalse(relay.resumeReply(TAG_B).contains("ui-a"));
        assertFalse(relay.resumeReply(null).contains("ui-"));
        assertTrue(relay.checkpointParts(TAG_A).isEmpty());
    }

    @Test
    void thePartsOfALargeCheckpointAreFilteredToo() throws Exception {
        Sink sink = new Sink();
        LineHealthRelay relay = relay(sink, new Targeted());
        String wide = "\u20ac".repeat(40);
        for (int i = 0; i < 2500; i++) {
            relay.accept(STATE, "ui-a" + i, bytes(state("ui-a" + i, 0, "TOUCHED", 6, TAG_A).set("patternId", wide).build()));
            relay.accept(STATE, "ui-b" + i, bytes(state("ui-b" + i, 0, "TOUCHED", 6, TAG_B).set("patternId", wide).build()));
        }
        relay.accept(STATUS, "SPX", bytes(status(1, 0)));
        relay.captureBound(5000);
        relay.release(5000);
        assertTrue(JSON.readTree(relay.helloField(TAG_A)).get("states").isNull(), "the checkpoint was split");
        StringBuilder all = new StringBuilder(relay.helloField(TAG_A));
        int found = 0;
        for (String part : relay.checkpointParts(TAG_A)) {
            all.append(part);
            for (JsonNode st : JSON.readTree(part).get("states")) {
                assertTrue(st.get("lineId").asText().startsWith("ui-a"));
                found++;
            }
        }
        assertEquals(2500, found);
        assertFalse(all.toString().contains("ui-b"));
    }

    @Test
    void aRemovedUserLineNeverReappearsInALaterHello() throws Exception {
        Sink sink = new Sink();
        Targeted targeted = new Targeted();
        LineHealthRelay relay = relay(sink, targeted);
        relay.accept(STATE, "ui-a", bytes(state("ui-a", 0, "TOUCHED", 6, TAG_A).build()));
        relay.accept(STATE, "ui-a", bytes(state("ui-a", 0, "REMOVED", null, TAG_A).build()));
        relay.accept(STATUS, "SPX", bytes(status(1, 0)));
        relay.captureBound(2);
        relay.release(2);
        assertEquals(List.of(), lineIds(relay.helloField(TAG_A)));
        assertEquals(2, targeted.frames.size(), "the removal itself is delivered live, to its owner");
    }

    @Test
    void aUserRecordWithoutATagIsStillRefusedAndTheEventSnapshotIsNotTrustedForTheOwner() {
        Sink sink = new Sink();
        Targeted targeted = new Targeted();
        LineHealthRelay relay = relay(sink, targeted);
        relay.accept(STATE, "U1", bytes(state("U1", 1, "TOUCHED", 6, null).set("isUser", true).build()));
        assertEquals(List.of(), sink.events());
        assertEquals(0, targeted.frames.size());
        assertEquals(1, relay.userRecordsRefusedForTest());
    }

    private static List<String> lineIds(String helloField) throws Exception {
        List<String> ids = new ArrayList<>();
        for (JsonNode st : JSON.readTree(helloField).get("states")) {
            ids.add(st.get("lineId").asText());
        }
        return ids;
    }

    @Test
    void aDisabledRelayConsumesNothing() {
        LineHealthRelay relay = new LineHealthRelay(SETTINGS, new Sink(), null);
        relay.start();
        assertFalse(relay.consuming());
    }

    @Test
    void anInconsistentUserRecordIsRefusedToo_noFlagOrKindOrNestedSnapshotGetsPastTheFilter() throws Exception {
        Sink sink = new Sink();
        LineHealthRelay relay = relay(sink);
        relay.accept(STATE, "U1", bytes(state("U1", 1, "TOUCHING", 6, null).set("isUser", true).build()));            // says user, carries no tag
        relay.accept(STATE, "U2", bytes(state("U2", 1, "TOUCHING", 6, null).set("kind", "USER_LINE").build()));      // a USER_ kind, no tag
        GenericRecord userBody = state("A", 1, "GIVING_WAY", 3, null).set("isUser", true).build();
        relay.accept(EVENTS, "e1", bytes(new GenericRecordBuilder(LineHealthSchemas.EVENT.schema()).set("schemaVersion", 1).set("asOfMs", NOW).set("eventId", "e1")
                .set("snapshot", new GenericRecordBuilder(LineHealthSchemas.EVENT.schema().getField("snapshot").schema().getTypes().get(1)).set("lineId", "U9").set("isUser", true).build()).build()));
        assertEquals(List.of(), sink.events(), "nothing reaches a socket");
        assertEquals(3, relay.userRecordsRefusedForTest());
        assertEquals(0, JSON.readTree(relay.helloField()).get("states").size());
    }

    @Test
    void theBoundaryRequiresTheSemanticFieldsAndSynthesizesNone() {
        Sink sink = new Sink();
        LineHealthRelay relay = relay(sink);
        relay.accept(STATE, "A", bytes(state("A", 0, "TOUCHING", 6, null).set("systemSequence", null).build()));        // a system line without its sequence
        relay.accept(STATUS, "SPX", bytes(new GenericRecordBuilder(LineHealthSchemas.STATUS.schema()).set("schemaVersion", 1).set("asOfMs", NOW).set("stateSequence", 5L).set("statusEpoch", 1L).build()));   // no systemRecordCount
        assertEquals(2, relay.dropsForTest());
        assertTrue(sink.frames.isEmpty());
    }

    @Test
    void aWorstCaseFullyPopulatedStateStaysSmallEnoughForSeveralHundredLinesInTheHello() throws Exception {
        GenericRecordBuilder b = new GenericRecordBuilder(LineHealthSchemas.STATE.schema());
        for (org.apache.avro.Schema.Field f : LineHealthSchemas.STATE.schema().getFields()) {
            org.apache.avro.Schema inner = f.schema().getType() == org.apache.avro.Schema.Type.UNION ? f.schema().getTypes().get(1) : f.schema();
            switch (inner.getType()) {
                case STRING -> b.set(f.name(), "ABCDEFGHIJKLMNOPQRSTUVWX");
                case INT -> b.set(f.name(), 1234567);
                case LONG -> b.set(f.name(), 1_800_000_000_000L);
                case BOOLEAN -> b.set(f.name(), true);
                case ARRAY -> b.set(f.name(), List.of("ABCDEFGHIJKLMNOPQRSTUVWX", "ABCDEFGHIJKLMNOPQRSTUVWX", "ABCDEFGHIJKLMNOPQRSTUVWX"));
                default -> { }
            }
        }
        b.set("schemaVersion", 1).set("ownerTag", null).set("isUser", false).set("kind", "STRUCTURE").set("state", "TOUCHING").set("lineId", "L-0123456789").set("chartKey", "L|0123456789");
        int one = LineHealthRelay.render(LineHealthSchemas.STATE, "L-0123456789", b.build()).toString().getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
        assertTrue(one < 2600, "a fully populated state is " + one + " bytes");
        // 400 worst-case lines do not fit one hello of 400,000 bytes: the checkpoint follows in parts that together carry every line, and the hello says so
        Sink sink = new Sink();
        LineHealthRelay relay = relay(sink);
        for (int k = 0; k < 400; k++) {
            relay.accept(STATE, "L-" + k, bytes(withIdentity(b, "L-" + k, k + 1)));
        }
        relay.accept(STATUS, "SPX", bytes(status(5, 400)));
        relay.captureBound(400);
        relay.release(400);
        JsonNode hello = JSON.readTree(relay.helloField());
        assertTrue(hello.get("states").isNull());
        List<String> parts = relay.checkpointParts();
        assertEquals(hello.get("parts").asInt(), parts.size());
        assertTrue(parts.size() >= 3);
        int lines = 0;
        for (String part : parts) {
            assertTrue(part.getBytes(java.nio.charset.StandardCharsets.UTF_8).length < 400_100, "each part is within the hello bound");
            JsonNode node = JSON.readTree(part);
            assertEquals(hello.get("version").asLong(), node.get("version").asLong());
            lines += node.get("states").size();
        }
        assertEquals(400, lines, "no line is dropped to make the hello fit");
        assertEquals(400, hello.get("watermark").asLong());
    }

    private static GenericRecord withIdentity(GenericRecordBuilder b, String id, long sequence) {
        return b.set("lineId", id).set("chartKey", "L|" + id).set("systemSequence", sequence).build();
    }

    @Test
    void theBootstrapScanFoldsOnlyWhatLiesBelowTheCapturedEndsAndTheLiveTailStartsExactlyThere() throws Exception {
        Sink sink = new Sink();
        LineHealthRelay relay = relay(sink);
        relay.runningForTest();
        MockConsumer<String, byte[]> consumer = new MockConsumer<>(OffsetResetStrategy.EARLIEST);
        TopicPartition state = new TopicPartition(STATE, 0), status = new TopicPartition(STATUS, 0);
        consumer.assign(List.of(state, status));
        consumer.updateBeginningOffsets(Map.of(state, 0L, status, 0L));
        consumer.updateEndOffsets(Map.of(state, 2L, status, 1L));
        consumer.addRecord(new ConsumerRecord<>(STATE, 0, 0L, "A", bytes(state("A", 1, "TOUCHING", 7, null).build())));
        consumer.addRecord(new ConsumerRecord<>(STATE, 0, 1L, "B", bytes(state("B", 2, "TOUCHING", 4, null).build())));
        consumer.addRecord(new ConsumerRecord<>(STATE, 0, 2L, "C", bytes(state("C", 3, "TOUCHING", 1, null).build())));   // at the captured end: the live tail's
        consumer.addRecord(new ConsumerRecord<>(STATUS, 0, 0L, "SPX", bytes(status(5, 2))));
        consumer.addRecord(new ConsumerRecord<>(STATUS, 0, 1L, "SPX", bytes(status(6, 3))));                                 // beyond the captured end
        relay.bootstrap(consumer, state, status);
        JsonNode hello = JSON.readTree(relay.helloField());
        assertEquals(2, hello.get("states").size(), "line C lies at the captured end and is not folded by the scan");
        assertEquals(5, hello.get("status").get("stateSequence").asLong(), "the status beyond the captured end is not folded either");
        assertEquals(2L, relay.bootstrapEndsForTest().get(state));
    }

    @Test
    void aUnicodeStateIsMeasuredInUtf8BytesWhenTheCheckpointIsSplit() throws Exception {
        Sink sink = new Sink();
        LineHealthRelay relay = relay(sink);
        String wide = "\u20ac".repeat(40);   // 3 bytes per character
        for (int k = 0; k < 6000; k++) {
            relay.accept(STATE, "L-" + k, bytes(state("L-" + k, k + 1, "TOUCHING", 7, null).set("patternId", wide).build()));
        }
        relay.accept(STATUS, "SPX", bytes(status(5, 6000)));
        relay.captureBound(6000);
        relay.release(6000);
        for (String part : relay.checkpointParts()) {
            assertTrue(part.getBytes(java.nio.charset.StandardCharsets.UTF_8).length <= SETTINGS.lineHealthHelloMaxBytes() + 400, "a part is within the bound in bytes, not characters");
        }
    }

    @Test
    void aStatusWithoutAnEpochIsRefusedAndAChangedEpochRequestsARebuildOfTheFold() {
        Sink sink = new Sink();
        LineHealthRelay relay = relay(sink);
        relay.accept(STATUS, "SPX", bytes(new GenericRecordBuilder(LineHealthSchemas.STATUS.schema()).set("schemaVersion", 1).set("asOfMs", NOW).set("stateSequence", 5L).set("systemRecordCount", 2L).build()));
        assertEquals(1, relay.dropsForTest(), "no statusEpoch, no status");
        relay.accept(STATUS, "SPX", bytes(status(6, 2)));
        assertFalse(relay.epochChangedForTest());
        relay.accept(STATUS, "SPX", bytes(new GenericRecordBuilder(LineHealthSchemas.STATUS.schema()).set("schemaVersion", 1).set("asOfMs", NOW).set("stateSequence", 1L).set("systemRecordCount", 0L).set("statusEpoch", 2L).build()));
        assertTrue(relay.epochChangedForTest(), "a new epoch is a continuity reset: the consumer loop rebuilds from the topics");
    }

    @Test
    void epochsReadByTheBootstrapScanAreNotAResetOnlyAChangeOnTheLiveTailIs() {
        Sink sink = new Sink();
        LineHealthRelay relay = new LineHealthRelay(SETTINGS, sink, (topic, data) -> deserializer.deserialize(topic, data));   // not live: the bootstrap scan
        GenericRecord epoch1 = new GenericRecordBuilder(LineHealthSchemas.STATUS.schema()).set("schemaVersion", 1).set("asOfMs", NOW).set("stateSequence", 9L).set("systemRecordCount", 3L).set("statusEpoch", 1L).build();
        GenericRecord epoch2 = new GenericRecordBuilder(LineHealthSchemas.STATUS.schema()).set("schemaVersion", 1).set("asOfMs", NOW).set("stateSequence", 1L).set("systemRecordCount", 0L).set("statusEpoch", 2L).build();
        relay.accept(STATUS, "SPX", bytes(epoch1));
        relay.accept(STATUS, "SPX", bytes(epoch2));
        assertFalse(relay.epochChangedForTest(), "history of two epochs in the scan is not a reset");
        relay.goLiveForTest();
        relay.accept(STATUS, "SPX", bytes(new GenericRecordBuilder((org.apache.avro.generic.GenericData.Record) epoch2).set("stateSequence", 2L).build()));
        assertFalse(relay.epochChangedForTest());
        relay.accept(STATUS, "SPX", bytes(new GenericRecordBuilder((org.apache.avro.generic.GenericData.Record) epoch2).set("stateSequence", 1L).set("statusEpoch", 3L).build()));
        assertTrue(relay.epochChangedForTest());
    }
}

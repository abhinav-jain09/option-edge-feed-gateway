package app.feedgateway;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.confluent.kafka.schemaregistry.avro.AvroSchema;
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient;
import io.confluent.kafka.schemaregistry.testutil.MockSchemaRegistry;
import io.confluent.kafka.serializers.KafkaAvroSerializer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericRecordBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

/** Rev 14 (BS13-SL-021, A-021): version 2 frames (ids 3906-3908) and system lines. The version 2 state schema is v1 plus the optional fields of liq-S 19.7, built here. */
class BreakLineSystemRelayTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final GatewaySettings SETTINGS = new GatewaySettings();
    private static final String STATE = SETTINGS.breakLineStateTopic(), EVENTS = SETTINGS.breakLineEventsTopic(), STATUS = SETTINGS.breakLineStatusTopic();
    private static final String ALICE = LineHealthOwnerTag.of("alice"), BOB = LineHealthOwnerTag.of("bob");
    private static final long NOW = 1_800_000_000_000L;

    private static Schema v2(Schema v1, String... extra) throws Exception {
        ObjectNode n = (ObjectNode) JSON.readTree(v1.toString());
        ArrayNode fields = (ArrayNode) n.get("fields");
        for (String f : extra) {
            ObjectNode o = fields.addObject();
            o.put("name", f);
            o.putArray("type").add("null").add("string");
            o.putNull("default");
        }
        return new Schema.Parser().parse(n.toString());
    }

    private final KafkaAvroSerializer serializer = new KafkaAvroSerializer();
    private final GatewayAvroDeserializer deserializer = new GatewayAvroDeserializer();
    private final Schema stateV2, eventV2, statusV2;
    private final List<String[]> targeted = new ArrayList<>();

    BreakLineSystemRelayTest() throws Exception {
        stateV2 = v2(BreakLineRelayTest.STATE_SCHEMA, "lineOrigin", "sourceKind", "chartKey");
        eventV2 = v2(BreakLineRelayTest.EVENT_SCHEMA, "lineOrigin", "sourceKind");
        statusV2 = v2(BreakLineRelayTest.STATUS_SCHEMA, "systemEmptyReason");
        String scope = "break-line-system-" + System.nanoTime();
        SchemaRegistryClient registry = MockSchemaRegistry.getClientForScope(scope);
        registry.register("com.optionsedge.contracts.breakline.BreakLineState", new AvroSchema(BreakLineRelayTest.STATE_SCHEMA), 1, 3902);
        registry.register("com.optionsedge.contracts.breakline.BreakLineState", new AvroSchema(stateV2), 2, 3906);
        registry.register("com.optionsedge.contracts.breakline.BreakLineStatus", new AvroSchema(BreakLineRelayTest.STATUS_SCHEMA), 1, 3904);
        registry.register("com.optionsedge.contracts.breakline.BreakLineEvent", new AvroSchema(BreakLineRelayTest.EVENT_SCHEMA), 1, 3903);
        registry.register("com.optionsedge.contracts.breakline.BreakLineEvent", new AvroSchema(eventV2), 2, 3907);
        registry.register("com.optionsedge.contracts.breakline.BreakLineStatus", new AvroSchema(statusV2), 2, 3908);
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

    private BreakLineRelay relay() {
        BreakLineRelay r = new BreakLineRelay(SETTINGS, (e, j) -> { }, (o, e, j) -> targeted.add(new String[] {o, e, j}), deserializer);
        r.goLive();
        return r;
    }

    private byte[] v2State(String lineId, String owner, String origin) {
        GenericRecordBuilder b = new GenericRecordBuilder(stateV2).set("schemaVersion", 2).set("messageType", "BREAK_LINE_STATE").set("lineId", lineId).set("ownerTag", owner)
                .set("lineStatus", "ACTIVE").set("lineRevision", 1L).set("domain", "SPX").set("priceUnits", 725000L).set("state", "TESTING").set("scoreStatus", "PENDING")
                .set("asOfEpochMillis", NOW).set("eventSequence", 1L).set("lineOrigin", origin).set("sourceKind", "GAMMA_HOLD");
        return serializer.serialize("t", b.build());
    }

    @Test
    void aSystemLineOfVersionTwoReachesEverySocketWithASubjectAndIsInEveryOwnersCheckpoint() throws Exception {
        BreakLineRelay r = relay();
        r.accept(STATE, "sy-gm-725000", v2State("sy-gm-725000", "SYSTEM", "SYSTEM"));
        r.accept(STATE, "bl-a-1", v2State("bl-a-1", ALICE, "USER"));
        assertEquals("SYSTEM", targeted.get(0)[0]);
        assertEquals(ALICE, targeted.get(1)[0]);
        String alice = r.helloField(ALICE), bob = r.helloField(BOB), nobody = r.helloField(null);
        assertTrue(alice.contains("sy-gm-725000") && alice.contains("bl-a-1"));
        assertTrue(bob.contains("sy-gm-725000") && !bob.contains("bl-a-1"));
        assertFalse(nobody.contains("sy-gm-725000"), "a signed-out socket gets no system line");
    }

    @Test
    void versionTwoEventsAndStatusAreAcceptedAndASystemEventFansOutToAllSignedIn() throws Exception {
        List<String[]> global = new ArrayList<>();
        BreakLineRelay r = new BreakLineRelay(SETTINGS, (e, j) -> global.add(new String[] {e, j}), (o, e, j) -> targeted.add(new String[] {o, e, j}), deserializer);
        r.goLive();
        GenericRecordBuilder ev = new GenericRecordBuilder(eventV2).set("schemaVersion", 2).set("messageType", "BREAK_LINE_EVENT").set("eventId", "sy-gm-725000|1")
                .set("scope", "SYSTEM_LINE_CREATED").set("lineId", "sy-gm-725000").set("ownerTag", "SYSTEM").set("eventSequence", 1L).set("eventEpochMillis", NOW)
                .set("lineOrigin", "SYSTEM").set("sourceKind", "GAMMA_HOLD");
        r.accept(EVENTS, "sy-gm-725000|1", serializer.serialize("t", ev.build()));
        assertEquals("SYSTEM", targeted.get(0)[0]);
        assertEquals("break-line-event", targeted.get(0)[1]);
        GenericRecordBuilder st = new GenericRecordBuilder(statusV2).set("schemaVersion", 2).set("messageType", "BREAK_LINE_STATUS").set("statusEpochMillis", NOW)
                .set("dataAsOfEpochMillis", NOW - 5).set("dataLagMs", 5L).set("mode", "SHADOW").set("calibrationState", "CORPUS_ONLY_PROVISIONAL").set("calibrationFlag", false)
                .set("activeLines", 1).set("activeOwners", 1).set("systemEmptyReason", "NO_SPOT");
        r.accept(STATUS, "break-line", serializer.serialize("t", st.build()));
        assertEquals("break-line-status", global.get(0)[0]);
        assertTrue(global.get(0)[1].contains("NO_SPOT"));
    }

    @Test
    void theRecordSchemaVersionMustMatchTheFrameIdVersion() throws Exception {
        BreakLineRelay r = relay();
        byte[] v2WithVersion1 = serializer.serialize("t", new GenericRecordBuilder(stateV2).set("schemaVersion", 1).set("messageType", "BREAK_LINE_STATE").set("lineId", "bl-a-9")
                .set("ownerTag", ALICE).set("lineStatus", "ACTIVE").set("eventSequence", 1L).set("lineOrigin", "USER").build());
        r.accept(STATE, "bl-a-9", v2WithVersion1);
        byte[] v1WithVersion2 = serializer.serialize("t", BreakLineRelayTest.state("bl-a-8", ALICE, "ACTIVE", 1, "TESTING", null).set("schemaVersion", 2).build());
        r.accept(STATE, "bl-a-8", v1WithVersion2);
        assertEquals(0, r.foldedLinesForTest());
        assertEquals(2, r.dropsForTest());
    }

    @Test
    void aMismatchOfOriginAndOwnerReachesNobody() throws Exception {
        BreakLineRelay r = relay();
        r.accept(STATE, "sy-x", v2State("sy-x", ALICE, "SYSTEM"));
        r.accept(STATE, "bl-y", v2State("bl-y", "SYSTEM", "USER"));
        r.accept(STATE, "bl-z", v2State("bl-z", "SYSTEM", null));
        assertEquals(0, targeted.size());
        assertEquals(3, r.userRecordsRefusedForTest());
        assertEquals(0, r.foldedLinesForTest());
    }

    @Test
    void versionOneStillWorksAndAForeignIdIsRefused() throws Exception {
        BreakLineRelay r = relay();
        r.accept(STATE, "bl-a-1", serializer.serialize("t", BreakLineRelayTest.state("bl-a-1", ALICE, "ACTIVE", 1, "TESTING", null).build()));
        assertEquals(1, r.foldedLinesForTest());
        byte[] bad = v2State("bl-a-2", ALICE, "USER");
        bad[4] = 7;   // frame id 3906 -> 3911 (not in the family)
        r.accept(STATE, "bl-a-2", bad);
        assertEquals(1, r.schemaIdRefusedForTest(BreakLineRelay.Kind.STATE));
    }

    private final List<List<String>> received = new ArrayList<>();

    private WebSocketSession socket(String subject) throws Exception {
        WebSocketSession ws = mock(WebSocketSession.class);
        when(ws.isOpen()).thenReturn(true);
        when(ws.getId()).thenReturn("s" + received.size());
        Map<String, Object> attributes = new HashMap<>();
        if (subject != null) attributes.put("sub", subject);
        when(ws.getAttributes()).thenReturn(attributes);
        List<String> frames = new ArrayList<>();
        received.add(frames);
        org.mockito.Mockito.doAnswer(c -> frames.add(((TextMessage) c.getArgument(0)).getPayload())).when(ws).sendMessage(org.mockito.ArgumentMatchers.any());
        return ws;
    }

    @Test
    void theSystemTargetReachesEverySignedInSocketAndNoSignedOutOne() throws Exception {
        FeedGatewayService service = new FeedGatewayService(new GatewaySettings(), new ObjectMapper(), new HpsfGatewayViewMapper(), null);
        service.runOutboundWritesInline();
        @SuppressWarnings("unchecked")
        org.springframework.beans.factory.ObjectProvider<app.feedgateway.mtsession.SessionRoutingEngine> none = mock(org.springframework.beans.factory.ObjectProvider.class);
        FeedWebSocketHandler handler = new FeedWebSocketHandler(service, none);
        for (String s : new String[] {"alice", "bob", null}) handler.afterConnectionEstablished(socket(s));
        received.forEach(List::clear);
        service.streamBreakLineTo("SYSTEM", "break-line-state", "{\"lineId\":\"sy-gm-1\"}");
        assertEquals(1, received.get(0).size());
        assertEquals(1, received.get(1).size());
        assertEquals(0, received.get(2).size());
    }
}

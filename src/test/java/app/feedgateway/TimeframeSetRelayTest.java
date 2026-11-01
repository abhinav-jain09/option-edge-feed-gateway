package app.feedgateway;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.apache.avro.generic.GenericRecord;
import org.junit.jupiter.api.Test;

/** The structure-tools and geometry relays: boundary, per-timeframe retention and ordering, the hello part and its budget, recovery. */
class TimeframeSetRelayTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final long BIG = 200_000;

    private static GenericRecord tools(String tf, long asOf) {
        return TaFixtures.tools(tf, asOf, "OK");
    }

    private static GenericRecord sized(GenericRecord r, long bytes) {
        return TaFixtures.put(r, "parameterSetHash", "h".repeat((int) bytes));
    }

    @Test
    void theLatestSetPerTimeframeIsRetainedAnOlderOneIsARegressionAnEqualOneReplaces() {
        var relay = TimeframeSetRelay.structureTools();
        String[] first = relay.onLive(TimeframeSetRelay.TOOLS_EVENT, tools("M5", 100));
        assertEquals(TimeframeSetRelay.TOOLS_EVENT, first[0]);
        assertEquals(TaFixtures.frame(tools("M5", 100)), first[1], "the frame is the record field by field");
        assertNull(relay.onLive(TimeframeSetRelay.TOOLS_EVENT, tools("M5", 99)));
        assertEquals(1, relay.regressionsForTest());
        assertEquals(0, relay.dropsForTest());
        assertEquals(TaFixtures.frame(tools("M5", 100)), relay.onLive(TimeframeSetRelay.TOOLS_EVENT, tools("M5", 100))[1], "equal asOfMs replaces and is forwarded");
        assertEquals(TaFixtures.frame(tools("M5", 101)), relay.onLive(TimeframeSetRelay.TOOLS_EVENT, tools("M5", 101))[1]);
        relay.onLive(TimeframeSetRelay.TOOLS_EVENT, tools("M1", 5));
        assertEquals(4, relay.acceptedForTest(), "five records, one regression");
    }

    @Test
    void everythingOutsideTheBoundaryIsDroppedAndCountedNeverRepaired() throws Exception {
        var relay = TimeframeSetRelay.structureTools();
        String event = TimeframeSetRelay.TOOLS_EVENT;
        assertNull(relay.onLive(event, TaFixtures.put(tools("M1", 1), "service", "other")));
        assertNull(relay.onLive(event, TaFixtures.put(tools("M1", 1), "schemaVersion", 2)));
        assertNull(relay.onLive(event, tools("H1", 1)));
        assertNull(relay.onLive(event, tools("H4", 1)));
        assertNull(relay.onLive(event, TaFixtures.geometrySet("TF_1M", 1)), "another record is never relayed");
        assertNull(relay.onLive(event, GatewayAvroDeserializer.UNDECODABLE));
        assertNull(relay.onLive(event, "{\"state\":\"OK\"}"), "JSON text is not a record");
        assertNull(relay.onLive(event, TaFixtures.put(tools("M1", 1), "asOfMs", -1L)));
        assertNull(relay.onLive(event, sized(tools("M1", 1), TaRelay.FRAME_MAX_BYTES)), "over the frame guard");
        relay.onDrop();   // a tombstone
        assertEquals(10, relay.dropsForTest());
        assertEquals(0, relay.acceptedForTest());
        assertEquals("{\"1m\":null,\"5m\":null,\"15m\":null}", JSON.readTree("{" + relay.helloField(1 << 20).substring(1) + "}").get("tools").toString());
    }

    @Test
    void unavailableToolsSetsAreRelayedLikeAnyOther() {
        var relay = TimeframeSetRelay.structureTools();
        GenericRecord unavailable = TaFixtures.put(TaFixtures.tools("M15", 7, "UNAVAILABLE"), "unavailableReason", "NOT_READY");
        String[] frame = relay.onLive(TimeframeSetRelay.TOOLS_EVENT, unavailable);
        assertTrue(frame[1].contains("\"state\":\"UNAVAILABLE\"") && frame[1].contains("\"unavailableReason\":\"NOT_READY\""));
    }

    @Test
    void geometryRelaysTheSetAndTheShortUnavailableRecordAndRefusesAMismatchedState() throws Exception {
        var relay = TimeframeSetRelay.geometry();
        String event = TimeframeSetRelay.GEOMETRY_EVENT;
        assertEquals(TimeframeSetRelay.GEOMETRY_EVENT, relay.onLive(event, TaFixtures.geometrySet("TF_5M", 10))[0]);
        String[] unavailable = relay.onLive(event, TaFixtures.geometryUnavailable("TF_15M", 11));
        JsonNode node = JSON.readTree(unavailable[1]);
        assertEquals(List.of("schemaVersion", "service", "state", "reason", "sessionDate", "asOfMs", "timeframe"),
                java.util.stream.StreamSupport.stream(java.util.Spliterators.spliteratorUnknownSize(node.fieldNames(), 0), false).toList(),
                "exactly the seven fields of GeometryUnavailable");
        assertNull(relay.onLive(event, TaFixtures.put(TaFixtures.geometrySet("TF_1M", 1), "state", "UNAVAILABLE")), "a set is state OK");
        assertNull(relay.onLive(event, TaFixtures.put(TaFixtures.geometryUnavailable("TF_1M", 1), "state", "OK")), "the short record is state UNAVAILABLE");
        assertNull(relay.onLive(event, TaFixtures.geometrySet("TF_1H", 1)) , "an unknown timeframe symbol");
        assertNull(relay.onLive(event, tools("M1", 1)), "the tools record is not geometry");
        assertEquals(4, relay.dropsForTest());
        String hello = relay.helloField(1 << 20);
        JsonNode parsed = JSON.readTree("{" + hello.substring(1) + "}").get("geometry");
        assertTrue(parsed.get("1m").isNull());
        assertEquals("OK", parsed.get("5m").get("state").asText());
        assertEquals("UNAVAILABLE", parsed.get("15m").get("state").asText());
    }

    @Test
    void theHelloSendsWholeTimeframesInOrderWhileTheyFitAndNullForTheRest() throws Exception {
        var relay = TimeframeSetRelay.structureTools();
        relay.onLive(TimeframeSetRelay.TOOLS_EVENT, sized(tools("M1", 1), BIG));
        relay.onLive(TimeframeSetRelay.TOOLS_EVENT, sized(tools("M5", 1), BIG));
        relay.onLive(TimeframeSetRelay.TOOLS_EVENT, tools("M15", 1));
        JsonNode hello = JSON.readTree("{" + relay.helloField(1 << 20).substring(1) + "}").get("tools");
        assertFalse(hello.get("1m").isNull());
        assertTrue(hello.get("5m").isNull(), "400 KB does not fit 384 KiB: the second set is null");
        assertTrue(hello.get("15m").isNull(), "…and the small third one is NOT sent past a set that did not fit (contract order)");
        JsonNode narrower = JSON.readTree("{" + relay.helloField(BIG / 2).substring(1) + "}").get("tools");
        assertTrue(narrower.get("1m").isNull(), "a smaller part budget (the hello arbitration) nulls it, never thins it");
        assertSame(relay.helloField(1 << 20), relay.helloField(1 << 20), "cached until the retained state changes");
        assertEquals(TaFixtures.frame(sized(tools("M1", 1), BIG)), hello.get("1m").toString(), "the hello member is the live frame");
    }

    @Test
    void aRecoveryIsPrivateUntilPromotedAndPromotionBroadcastsOnlyWhatIsNewer() {
        var relay = TimeframeSetRelay.structureTools();
        String event = TimeframeSetRelay.TOOLS_EVENT;
        relay.onLive(event, tools("M1", 100));
        String before = relay.helloField(1 << 20);
        relay.beginRecovery();
        relay.onStaged(event, tools("M1", 90));
        relay.onStaged(event, tools("M5", 80));
        relay.onStaged(event, tools("M15", 70));
        relay.onStaged(event, tools("M5", 60));    // regression inside the staging
        assertSame(before, relay.helloField(1 << 20), "nothing is visible before the promotion");
        relay.onLive(event, tools("M15", 75));     // a live record that arrived while recovering
        List<String[]> frames = relay.promote();
        assertEquals(1, frames.size(), "1m: the held 100 is newer than the staged 90; 15m: live 75 beats staged 70; only 5m is new");
        assertEquals(TaFixtures.frame(tools("M5", 80)), frames.get(0)[1]);
        assertTrue(relay.promote().isEmpty(), "a promotion without a staging is empty");
        assertEquals(2, relay.regressionsForTest(), "the staged 1m 90 behind the held 100 and the staged 5m 60 behind its 80");
    }

    @Test
    void aRecoveryTailCoversThreeFolds() {
        assertEquals(12, TimeframeSetRelay.structureTools().tailRecords(TimeframeSetRelay.TOOLS_EVENT));
        assertTrue(TimeframeSetRelay.geometry().handles(TimeframeSetRelay.GEOMETRY_EVENT));
        assertFalse(TimeframeSetRelay.geometry().handles(TimeframeSetRelay.TOOLS_EVENT));
        assertArrayEquals(new String[] {"1m", "5m", "15m"}, TaRelay.TF);
    }
}

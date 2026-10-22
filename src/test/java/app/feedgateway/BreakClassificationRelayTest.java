package app.feedgateway;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;
import org.junit.jupiter.api.Test;

/** Phase F relay rules on synthetic records built from the producer's schemas: the boundary, the retention, the caps, the session rollover, the hello and the recovery. */
class BreakClassificationRelayTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String E = BreakClassificationRelay.EVENT;
    private static final String D1 = "2026-10-21", D2 = "2026-10-22";

    private static String[] live(BreakClassificationRelay relay, Object record) {
        return relay.onLive(E, record);
    }

    private static JsonNode hello(BreakClassificationRelay relay, long budget) throws Exception {
        return JSON.readTree("{" + relay.helloField(budget).substring(1) + "}").get("breaks");
    }

    private static long counter(BreakClassificationRelay relay, String reason) {
        String key = "gateway_break_classification_" + reason + "_total ";
        String metrics = relay.metrics();
        int at = metrics.indexOf("\n" + key) + 1;
        return Long.parseLong(metrics.substring(at + key.length(), metrics.indexOf('\n', at)));
    }

    private static List<String> ids(JsonNode records) {
        List<String> ids = new ArrayList<>();
        records.forEach(r -> ids.add(r.get("classificationId").asText()));
        return ids;
    }

    @Test
    void aMarkIsRelayedAsItsFrameTypeAndEveryFieldAppearsInSchemaOrder() throws Exception {
        var relay = new BreakClassificationRelay();
        String[] frame = live(relay, BreakFixtures.mark("M5", D1, 1));
        assertEquals(BreakClassificationRelay.MARK_FRAME, frame[0]);
        JsonNode data = JSON.readTree(frame[1]);
        List<String> names = new ArrayList<>();
        data.fieldNames().forEachRemaining(names::add);
        List<String> expected = new ArrayList<>();
        BreakFixtures.schema("BreakMark").getFields().forEach(f -> expected.add(f.name()));
        assertEquals(expected, names, "no renames, additions or omissions; null fields are present");
        assertTrue(data.get("scorePermille").isNull() && data.get("esBarKey").isNull());
        assertEquals("M5", data.get("timeframe").asText());
        assertEquals(1, relay.acceptedForTest());
    }

    @Test
    void aNotEvaluatedMarkAndAnUnknownCandidateStateAreRelayed() {
        var relay = new BreakClassificationRelay();
        GenericRecord notEvaluated = BreakFixtures.mark("M1", D1, 1);
        TaFixtures.put(notEvaluated, "state", "NOT_EVALUATED");
        TaFixtures.put(notEvaluated, "reason", "NO_FLOW_EVIDENCE");
        assertNotNull(live(relay, notEvaluated));
        GenericRecord candidate = BreakFixtures.mark("M1", D1, 2);
        TaFixtures.put(TaFixtures.child(candidate, "calibration"), "candidateState", "UNKNOWN");
        assertNotNull(live(relay, candidate), "calibration.candidateState may legitimately be UNKNOWN");
        assertEquals(0, relay.dropsForTest());
    }

    @Test
    @SuppressWarnings("unchecked")
    void everyRequiredEnumThatIsUnknownIsACountedDropAndRetainsNothing() throws Exception {
        var relay = new BreakClassificationRelay();
        int drops = 0;
        for (String field : List.of("timeframe", "stage", "direction", "state", "reason", "todBucket")) {
            GenericRecord r = BreakFixtures.mark("M1", D1, 1);
            TaFixtures.put(r, field, "UNKNOWN");
            assertNull(live(relay, r), field);
            assertEquals(++drops, relay.dropsForTest(), field);
        }
        GenericRecord calibration = BreakFixtures.mark("M1", D1, 1);
        TaFixtures.put(TaFixtures.child(calibration, "calibration"), "state", "UNKNOWN");
        assertNull(live(relay, calibration));
        GenericRecord feature = BreakFixtures.mark("M1", D1, 1);
        Schema contribution = feature.getSchema().getField("contributions").schema().getElementType();
        GenericRecord c = TaFixtures.minimal(contribution);
        TaFixtures.put(c, "featureId", "UNKNOWN");
        ((List<Object>) feature.get("contributions")).add(c);
        assertNull(live(relay, feature));
        GenericRecord outcome = BreakFixtures.outcome(1, "UNKNOWN");
        assertNull(live(relay, outcome));
        assertNull(live(relay, BreakFixtures.status("UNKNOWN")));
        assertEquals(10, relay.dropsForTest());
        assertEquals(0, relay.acceptedForTest());
        assertEquals("{\"1m\":null,\"5m\":null,\"15m\":null,\"status\":null}", hello(relay, 1 << 20).toString());
    }

    @Test
    void aNonForwardedTimeframeAndMalformedIdsHashesDatesAndVersionsAreCountedDrops() {
        var relay = new BreakClassificationRelay();
        // an evolved writer schema with a timeframe the page does not know reaches the relay as a symbol the gateway does not forward
        Schema evolved = new Schema.Parser().parse(BreakFixtures.text("/break/BreakMark.avsc").replace("\"M15\"", "\"M15\",\"H1\""));
        GenericRecord h1 = BreakFixtures.mark("M1", D1, 1);
        GenericRecord copy = new GenericData.Record(evolved);
        for (Schema.Field f : h1.getSchema().getFields()) {
            copy.put(f.name(), h1.get(f.name()));
        }
        copy.put("timeframe", new GenericData.EnumSymbol(evolved.getField("timeframe").schema(), "H1"));
        assertNull(live(relay, copy));
        List<GenericRecord> bad = new ArrayList<>();
        for (String id : new String[] {"ABC", BreakFixtures.hex(1).toUpperCase().replace("0", "A"), "g".repeat(64), "a".repeat(63)}) {
            GenericRecord r = BreakFixtures.mark("M1", D1, 1);
            TaFixtures.put(r, "classificationId", id);
            bad.add(r);
        }
        for (String date : new String[] {"2026-10-2", "2026/10/22", "20261022", "2026-10-2x", ""}) {
            bad.add(BreakFixtures.mark("M1", date, 1));
        }
        for (String field : new String[] {"vendoredKitHash", "parameterSetHash"}) {
            GenericRecord r = BreakFixtures.mark("M1", D1, 1);
            TaFixtures.put(r, field, "x");
            bad.add(r);
        }
        GenericRecord version = BreakFixtures.mark("M1", D1, 1);
        TaFixtures.put(version, "schemaVersion", 2);
        bad.add(version);
        GenericRecord outcomeHash = BreakFixtures.outcome(1, "STALL");
        TaFixtures.put(outcomeHash, "parameterSetHash", "short");
        bad.add(outcomeHash);
        GenericRecord statusVersion = BreakFixtures.status("LIVE");
        TaFixtures.put(statusVersion, "schemaVersion", 0);
        bad.add(statusVersion);
        for (GenericRecord r : bad) {
            assertNull(live(relay, r), r.toString());
        }
        assertEquals(1 + bad.size(), relay.dropsForTest());
        assertEquals(0, counter(relay, "orphan"), "a malformed outcome is a boundary drop, not an orphan");
    }

    @Test
    void aRecordOfAnotherTypeAndAnUndecodableValueAreCountedDrops() {
        var relay = new BreakClassificationRelay();
        assertNull(live(relay, TaFixtures.tools("M1", 1, "OK")));
        assertNull(live(relay, GatewayAvroDeserializer.UNDECODABLE));
        assertNull(live(relay, "{\"retained\":\"json\"}"));
        assertEquals(3, relay.dropsForTest());
    }

    @Test
    void anOversizeRecordIsRefusedAndNotRepaired() {
        var relay = new BreakClassificationRelay();
        GenericRecord big = BreakFixtures.mark("M1", D1, 1);
        TaFixtures.put(big, "source", "s".repeat(TaRelay.FRAME_MAX_BYTES));
        assertNull(live(relay, big));
        assertEquals(1, relay.dropsForTest());
        assertNotNull(live(relay, BreakFixtures.mark("M1", D1, 2)));
    }

    @Test
    void aMarkOfAnEarlierSessionThanTheRetainedOneIsLateAndOnlyItsTimeframeIsAffected() throws Exception {
        var relay = new BreakClassificationRelay();
        live(relay, BreakFixtures.mark("M1", D2, 1));
        live(relay, BreakFixtures.mark("M5", D1, 2));
        assertNull(live(relay, BreakFixtures.mark("M1", D1, 3)));
        assertEquals(1, counter(relay, "late"));
        assertEquals(0, relay.dropsForTest(), "late is its own reason");
        assertNotNull(live(relay, BreakFixtures.mark("M5", D1, 4)), "the 5m timeframe has its own session");
        assertEquals(List.of(BreakFixtures.hex(1)), ids(hello(relay, 1 << 20).get("1m").get("marks")));
        assertEquals(List.of(BreakFixtures.hex(2), BreakFixtures.hex(4)), ids(hello(relay, 1 << 20).get("5m").get("marks")));
    }

    @Test
    void theFirstMarkOfALaterSessionClearsThatTimeframesMarksAndOutcomesOnly() throws Exception {
        var relay = new BreakClassificationRelay();
        live(relay, BreakFixtures.mark("M1", D1, 1));
        live(relay, BreakFixtures.outcome(1, "RECLAIMED"));
        live(relay, BreakFixtures.mark("M5", D1, 2));
        live(relay, BreakFixtures.outcome(2, "STALL"));
        live(relay, BreakFixtures.mark("M1", D2, 3));
        JsonNode hello = hello(relay, 1 << 20);
        assertEquals(List.of(BreakFixtures.hex(3)), ids(hello.get("1m").get("marks")));
        assertEquals(0, hello.get("1m").get("outcomes").size());
        assertEquals(1, hello.get("5m").get("marks").size());
        assertEquals(1, hello.get("5m").get("outcomes").size());
        assertNull(live(relay, BreakFixtures.outcome(1, "RECLAIMED")), "the cleared mark's outcome is an orphan now");
        assertEquals(1, counter(relay, "orphan"));
    }

    @Test
    void the65thMarkEvictsTheFirstArrivalWithItsOutcomeAndAReplacementAtTheCapEvictsNothing() throws Exception {
        var relay = new BreakClassificationRelay();
        for (int i = 1; i <= 64; i++) {
            live(relay, BreakFixtures.mark("M15", D1, i));
        }
        live(relay, BreakFixtures.outcome(1, "STALL"));
        live(relay, BreakFixtures.outcome(2, "STALL"));
        GenericRecord again = BreakFixtures.mark("M15", D1, 7);
        TaFixtures.put(again, "source", "REPLACED");
        assertNotNull(live(relay, again));
        assertEquals(0, counter(relay, "evicted"), "a re-delivered id at the cap is a replacement");
        assertNotNull(live(relay, BreakFixtures.mark("M15", D1, 65)));
        assertEquals(1, counter(relay, "evicted"));
        JsonNode part = hello(relay, 1 << 20).get("15m");
        assertEquals(64, part.get("marks").size());
        assertEquals(BreakFixtures.hex(2), part.get("marks").get(0).get("classificationId").asText(), "arrival order; the first arrival left");
        assertEquals(BreakFixtures.hex(65), part.get("marks").get(63).get("classificationId").asText());
        assertEquals("REPLACED", part.get("marks").get(5).get("source").asText(), "the replacement keeps its place");
        assertEquals(List.of(BreakFixtures.hex(2)), ids(part.get("outcomes")), "the evicted mark's outcome left with it");
        assertNull(live(relay, BreakFixtures.outcome(1, "STALL")));
        assertEquals(1, counter(relay, "orphan"));
    }

    @Test
    void outcomesBelongToTheTimeframeOfTheirMarkAreIdempotentAndOneIsKeptPerId() throws Exception {
        var relay = new BreakClassificationRelay();
        assertNull(live(relay, BreakFixtures.outcome(1, "STALL")));
        assertEquals(1, counter(relay, "orphan"));
        live(relay, BreakFixtures.mark("M5", D1, 1));
        live(relay, BreakFixtures.mark("M15", D1, 2));
        String[] first = live(relay, BreakFixtures.outcome(2, "RECLAIMED"));
        assertEquals(BreakClassificationRelay.OUTCOME_FRAME, first[0]);
        assertArrayEquals(first, live(relay, BreakFixtures.outcome(2, "RECLAIMED")), "an identical re-delivery is idempotent");
        assertNull(live(relay, BreakFixtures.outcome(2, "STALL")), "a different second outcome is a conflict");
        assertEquals(1, counter(relay, "conflict"));
        JsonNode hello = hello(relay, 1 << 20);
        assertEquals(0, hello.get("5m").get("outcomes").size());
        assertEquals(1, hello.get("15m").get("outcomes").size());
        assertEquals("RECLAIMED", hello.get("15m").get("outcomes").get(0).get("label").asText());
    }

    @Test
    void theStatusIsTheLatestByArrivalAndEveryKindIsRelayed() throws Exception {
        var relay = new BreakClassificationRelay();
        for (String kind : List.of("STARTING", "LIVE", "ES_STREAM_STALLED", "PARENT_UNAVAILABLE", "PARAMETER_SET_CHANGED", "FOLD_FAILURE", "FAILED_CONTRACT")) {
            String[] frame = live(relay, BreakFixtures.status(kind));
            assertEquals(BreakClassificationRelay.STATUS_FRAME, frame[0]);
            assertEquals(kind, hello(relay, 1 << 20).get("status").get("statusKind").asText());
        }
        assertEquals(7, relay.acceptedForTest());
    }

    @Test
    void framesLeaveInLogOrderAndTheHelloHoldsArrivalOrder() throws Exception {
        var relay = new BreakClassificationRelay();
        List<GenericRecord> log = List.of(BreakFixtures.mark("M1", D1, 3), BreakFixtures.status("LIVE"), BreakFixtures.mark("M1", D1, 1), BreakFixtures.outcome(3, "STALL"),
                BreakFixtures.mark("M1", D1, 2), BreakFixtures.outcome(1, "STALL"));
        List<String> types = new ArrayList<>();
        for (GenericRecord r : log) {
            types.add(live(relay, r)[0]);
        }
        assertEquals(List.of("mark", "status", "mark", "outcome", "mark", "outcome"), types.stream().map(t -> t.substring("break-classification-".length())).toList());
        JsonNode part = hello(relay, 1 << 20).get("1m");
        assertEquals(List.of(BreakFixtures.hex(3), BreakFixtures.hex(1), BreakFixtures.hex(2)), ids(part.get("marks")));
        assertEquals(List.of(BreakFixtures.hex(3), BreakFixtures.hex(1)), ids(part.get("outcomes")));
    }

    @Test
    void theHelloSendsWholeTimeframesInOrderWhileTheyFitAndNullForTheRestWithTheStatusAccountedFirst() throws Exception {
        var relay = new BreakClassificationRelay();
        live(relay, BreakFixtures.status("LIVE"));
        live(relay, BreakFixtures.mark("M1", D1, 1));
        GenericRecord big = BreakFixtures.mark("M5", D1, 2);
        TaFixtures.put(big, "source", "b".repeat(200_000));
        live(relay, big);
        live(relay, BreakFixtures.mark("M15", D1, 3));
        JsonNode all = hello(relay, 1 << 20);
        assertFalse(all.get("1m").isNull() || all.get("5m").isNull() || all.get("15m").isNull(), "all fit under the part cap");
        // 200 KB + the small ones exceed nothing yet; a second big mark in 5m passes the 256 KiB part
        GenericRecord big2 = BreakFixtures.mark("M5", D1, 4);
        TaFixtures.put(big2, "source", "b".repeat(100_000));
        live(relay, big2);
        JsonNode trimmed = hello(relay, 1 << 20);
        assertFalse(trimmed.get("1m").isNull());
        assertTrue(trimmed.get("5m").isNull(), "a timeframe is whole or not at all");
        assertTrue(trimmed.get("15m").isNull(), "the first one that does not fit ends the list even if a later one would fit");
        assertEquals("LIVE", trimmed.get("status").get("statusKind").asText(), "the status is always sent");
        // a budget smaller than the part cap trims earlier, down to the status alone; a budget below the status leaves the skeleton
        long statusBytes = StudiesRelay.utf8Length(JSON.writeValueAsString(trimmed.get("status")));
        assertTrue(hello(relay, statusBytes + 1).get("1m").isNull());
        assertEquals("LIVE", hello(relay, statusBytes + 1).get("status").get("statusKind").asText());
        assertEquals("{\"1m\":null,\"5m\":null,\"15m\":null,\"status\":null}", hello(relay, 10).toString());
    }

    @Test
    void theStatusIsAccountedBeforeTheTimeframesSoTheBudgetIsTheSumOfBoth() throws Exception {
        var relay = new BreakClassificationRelay();
        String status = live(relay, BreakFixtures.status("LIVE"))[1];
        String mark = live(relay, BreakFixtures.mark("M1", D1, 1))[1];
        long both = StudiesRelay.utf8Length(status) + 1 + StudiesRelay.utf8Length(mark) + 1;
        assertFalse(hello(relay, both).get("1m").isNull(), "the exact sum fits");
        assertTrue(hello(relay, both - 1).get("1m").isNull(), "one byte less and the timeframe gives way, not the status");
        assertEquals("LIVE", hello(relay, both - 1).get("status").get("statusKind").asText());
    }

    @Test
    void theHelloIsCachedUntilTheStateOrTheBudgetChanges() {
        var relay = new BreakClassificationRelay();
        live(relay, BreakFixtures.mark("M1", D1, 1));
        String first = relay.helloField(1 << 20);
        assertTrue(first == relay.helloField(1 << 20), "the same state and budget render once");
        live(relay, BreakFixtures.mark("M1", D1, 2));
        assertFalse(first.equals(relay.helloField(1 << 20)));
    }

    // ── recovery ───────────────────────────────────────────────────────────────────────────────────────

    @Test
    void coldStartPromotesTheHistoryOnceAndBroadcastsItMarksBeforeOutcomes() throws Exception {
        var relay = new BreakClassificationRelay();
        relay.beginRecovery();
        relay.onStaged(E, BreakFixtures.mark("M1", D1, 1));
        relay.onStaged(E, BreakFixtures.mark("M5", D1, 2));
        relay.onStaged(E, BreakFixtures.outcome(1, "STALL"));
        relay.onStaged(E, BreakFixtures.status("LIVE"));
        assertEquals("{\"1m\":null,\"5m\":null,\"15m\":null,\"status\":null}", hello(relay, 1 << 20).toString(), "nothing is visible before the promotion");
        List<String[]> frames = relay.promote();
        assertEquals(List.of("break-classification-mark", "break-classification-outcome", "break-classification-mark", "break-classification-status"),
                frames.stream().map(f -> f[0]).toList());
        JsonNode hello = hello(relay, 1 << 20);
        assertEquals(1, hello.get("1m").get("outcomes").size());
        assertEquals("LIVE", hello.get("status").get("statusKind").asText());
        assertTrue(relay.promote().isEmpty(), "a second promotion owes nothing");
    }

    @Test
    void historyIsOlderThanEveryLiveRecordItFillsOnlyFreeRoomAndNeverReplacesLiveState() throws Exception {
        var relay = new BreakClassificationRelay();
        relay.beginRecovery();
        live(relay, BreakFixtures.mark("M1", D1, 10));          // live during the recovery
        live(relay, BreakFixtures.status("ES_STREAM_STALLED"));
        relay.onStaged(E, BreakFixtures.mark("M1", D1, 1));      // history, older
        relay.onStaged(E, BreakFixtures.mark("M1", D1, 2));
        relay.onStaged(E, BreakFixtures.status("STARTING"));
        List<String[]> frames = relay.promote();
        assertEquals(2, frames.size(), "only the two history marks are news; the older status is not");
        JsonNode part = hello(relay, 1 << 20);
        assertEquals(List.of(BreakFixtures.hex(1), BreakFixtures.hex(2), BreakFixtures.hex(10)), ids(part.get("1m").get("marks")), "history first: it is older");
        assertEquals("ES_STREAM_STALLED", part.get("status").get("statusKind").asText());
    }

    @Test
    void historyNeverEvictsALiveMarkAndTakesTheNewestOfWhatFits() throws Exception {
        var relay = new BreakClassificationRelay();
        relay.beginRecovery();
        for (int i = 100; i < 160; i++) {
            live(relay, BreakFixtures.mark("M5", D1, i));       // 60 live marks, room for 4
        }
        for (int i = 1; i <= 10; i++) {
            relay.onStaged(E, BreakFixtures.mark("M5", D1, i));
        }
        assertEquals(4, relay.promote().size());
        List<String> ids = ids(hello(relay, 1 << 20).get("5m").get("marks"));
        assertEquals(64, ids.size());
        assertEquals(List.of(BreakFixtures.hex(7), BreakFixtures.hex(8), BreakFixtures.hex(9), BreakFixtures.hex(10), BreakFixtures.hex(100)), ids.subList(0, 5));
    }

    @Test
    void aGreaterSessionInTheHistoryReplacesASmallerLiveOneAndASmallerHistorySessionIsIgnored() throws Exception {
        var relay = new BreakClassificationRelay();
        live(relay, BreakFixtures.mark("M1", D1, 1));
        live(relay, BreakFixtures.mark("M5", D2, 2));
        relay.beginRecovery();
        relay.onStaged(E, BreakFixtures.mark("M1", D2, 3));
        relay.onStaged(E, BreakFixtures.mark("M5", D1, 4));
        assertEquals(1, relay.promote().size());
        JsonNode hello = hello(relay, 1 << 20);
        assertEquals(List.of(BreakFixtures.hex(3)), ids(hello.get("1m").get("marks")));
        assertEquals(List.of(BreakFixtures.hex(2)), ids(hello.get("5m").get("marks")));
    }

    @Test
    void aRetryOverlappingWhatLiveAlreadyHoldsBroadcastsNothingAndKeepsLiveOrderAndOutcomes() throws Exception {
        var relay = new BreakClassificationRelay();
        live(relay, BreakFixtures.mark("M1", D1, 1));
        live(relay, BreakFixtures.mark("M1", D1, 2));
        live(relay, BreakFixtures.outcome(1, "STALL"));
        String before = relay.helloField(1 << 20);
        relay.beginRecovery();
        relay.onStaged(E, BreakFixtures.mark("M1", D1, 1));
        relay.onStaged(E, BreakFixtures.mark("M1", D1, 2));
        relay.onStaged(E, BreakFixtures.outcome(1, "STALL"));
        assertTrue(relay.promote().isEmpty());
        assertEquals(before, relay.helloField(1 << 20));
    }

    @Test
    void aLiveOutcomeWhoseMarkIsStillBeingStagedWaitsForThePromotionAndIsNeitherAnOrphanNorLost() throws Exception {
        var relay = new BreakClassificationRelay();
        relay.beginRecovery();
        relay.onStaged(E, BreakFixtures.mark("M5", D1, 9));               // offset 9, below the captured end
        assertNull(live(relay, BreakFixtures.outcome(9, "RECLAIMED")));   // offset 10, at the captured end: live path, its mark is only staged
        assertEquals(0, counter(relay, "orphan"));
        assertEquals("{\"1m\":null,\"5m\":null,\"15m\":null,\"status\":null}", hello(relay, 1 << 20).toString(), "nothing is visible before the promotion");
        assertNull(live(relay, BreakFixtures.outcome(9, "STALL")), "a different second outcome is still a conflict");
        assertEquals(1, counter(relay, "conflict"));
        List<String[]> frames = relay.promote();
        assertEquals(List.of("break-classification-mark", "break-classification-outcome"), frames.stream().map(f -> f[0]).toList());
        JsonNode part = hello(relay, 1 << 20).get("5m");
        assertEquals("RECLAIMED", part.get("outcomes").get(0).get("label").asText());
        assertEquals(1, part.get("marks").size());
        assertEquals(2, relay.acceptedForTest(), "the staged mark and the adopted outcome, once each");
        assertEquals(0, counter(relay, "orphan"));
    }

    @Test
    void aParkedOutcomeWhoseStagedMarkTheLiveCapacityLeavesNoRoomForIsAnOrphanAtThePromotion() throws Exception {
        var relay = new BreakClassificationRelay();
        relay.beginRecovery();
        relay.onStaged(E, BreakFixtures.mark("M1", D1, 9));
        for (int i = 100; i < 164; i++) {
            live(relay, BreakFixtures.mark("M1", D1, i));          // 64 live marks fill the timeframe
        }
        assertNull(live(relay, BreakFixtures.outcome(9, "RECLAIMED")));
        assertEquals(0, counter(relay, "orphan"), "not decided before the promotion");
        assertTrue(relay.promote().isEmpty(), "the older mark finds no room, so neither it nor its outcome is relayed");
        assertEquals(1, counter(relay, "orphan"));
        assertEquals(65, relay.acceptedForTest(), "64 live marks and the staged mark; the parked outcome was never accepted");
        JsonNode part = hello(relay, 1 << 20).get("1m");
        assertEquals(64, part.get("marks").size());
        assertEquals(0, part.get("outcomes").size());
    }

    @Test
    void aParkedOutcomeWhoseStagedMarkIsOfAnEarlierSessionThanTheLiveOneIsAnOrphanAtThePromotion() {
        var relay = new BreakClassificationRelay();
        relay.beginRecovery();
        relay.onStaged(E, BreakFixtures.mark("M1", D1, 9));
        live(relay, BreakFixtures.mark("M1", D2, 10));
        assertNull(live(relay, BreakFixtures.outcome(9, "RECLAIMED")));
        assertTrue(relay.promote().isEmpty());
        assertEquals(1, counter(relay, "orphan"));
    }

    @Test
    void aLiveOutcomeForAMarkThatIsNeitherLiveNorStagedIsStillAnOrphanDuringARecovery() {
        var relay = new BreakClassificationRelay();
        relay.beginRecovery();
        assertNull(live(relay, BreakFixtures.outcome(9, "RECLAIMED")));
        assertEquals(1, counter(relay, "orphan"));
        assertTrue(relay.promote().isEmpty());
    }

    @Test
    void anOutcomeInTheHistoryForAMarkLiveEvictedOrNeverHeldIsNotResurrected() throws Exception {
        var relay = new BreakClassificationRelay();
        relay.beginRecovery();
        relay.onStaged(E, BreakFixtures.outcome(9, "STALL"));   // its mark is below the tail: an orphan
        assertEquals(1, counter(relay, "orphan"));
        assertTrue(relay.promote().isEmpty());
        assertEquals("{\"1m\":null,\"5m\":null,\"15m\":null,\"status\":null}", hello(relay, 1 << 20).toString());
    }

    @Test
    void theRecoveryTailCoversThreeTimeframesOfMarksAndOutcomesAndTheStatus() {
        assertEquals(600, new BreakClassificationRelay().tailRecords(E));
        assertTrue(600 >= 3 * (2 * BreakClassificationRelay.MARKS_PER_TF) + 1);
        assertTrue(new BreakClassificationRelay().handles(E));
        assertFalse(new BreakClassificationRelay().handles("wave-cycle-waves"));
    }
}

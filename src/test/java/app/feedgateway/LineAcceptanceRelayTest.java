package app.feedgateway;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.apache.avro.generic.GenericRecord;
import org.junit.jupiter.api.Test;

/** Line acceptance relay rules on synthetic records of the producer's own schemas: the boundary, the merge into one view, the monotonicity rules, retention, the hello and the recovery. */
class LineAcceptanceRelayTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String E = LineAcceptanceRelay.EVENT, M = LineAcceptanceRelay.MONITOR_EVENT, S = LineAcceptanceRelay.STATUS_EVENT;
    private static final String D1 = "2026-11-02", D2 = "2026-11-03";

    private static JsonNode hello(LineAcceptanceRelay relay, long budget) throws Exception {
        return JSON.readTree("{" + relay.helloField(budget).substring(1) + "}").get("lineAcceptance");
    }

    private static long counter(LineAcceptanceRelay relay, String name) {
        String key = "gateway_line_acceptance_" + name + "_total ";
        String metrics = relay.metrics();
        int at = metrics.indexOf("\n" + key) + 1;
        return Long.parseLong(metrics.substring(at + key.length(), metrics.indexOf('\n', at)));
    }

    @Test
    void aCoreIsRelayedAsTheMergedViewAndLaterPartsReSendTheWholeView() throws Exception {
        var relay = new LineAcceptanceRelay();
        String[] f = relay.onLive(E, LineAcceptanceFixtures.core("M5", D1, "ep-1", 1, 1000));
        assertEquals(LineAcceptanceRelay.EPISODE_FRAME, f[0]);
        JsonNode v = JSON.readTree(f[1]);
        assertEquals("5m", v.get("timeframe").asText(), "the view is keyed by the page's timeframe name; the records keep M5");
        assertEquals("M5", v.get("core").get("timeframe").asText());
        assertEquals("ep-1", v.get("episodeId").asText());
        assertEquals("REJECTED", v.get("core").get("s0Verdict").asText());
        assertTrue(v.get("evidence").isNull() && v.get("outcome").isNull() && v.get("integrity").isNull() && v.get("lean").isNull());
        JsonNode v2 = JSON.readTree(relay.onLive(E, LineAcceptanceFixtures.outcome("M5", D1, "ep-1", 1, "FAILED", 2, 2000))[1]);
        assertEquals("FAILED", v2.get("outcome").get("s1Outcome").asText());
        assertEquals("REJECTED", v2.get("core").get("s0Verdict").asText(), "the view keeps the core");
        JsonNode v3 = JSON.readTree(relay.onLive(E, LineAcceptanceFixtures.lean("M5", D1, "ep-1", 3, "NO_LEAN", 3, 3000))[1]);
        assertEquals("NO_LEAN", v3.get("lean").get("lean").asText());
        assertEquals(0, relay.dropsForTest());
    }

    @Test
    void anEpisodePartBeforeItsCoreIsHeldNotRelayedAndTheCoreReleasesTheView() throws Exception {
        var relay = new LineAcceptanceRelay();
        assertNull(relay.onLive(E, LineAcceptanceFixtures.evidence("M1", D1, "ep-1", 1, 1, 1000)));
        JsonNode v = JSON.readTree(relay.onLive(E, LineAcceptanceFixtures.core("M1", D1, "ep-1", 2, 2000))[1]);
        assertEquals(1, v.get("evidence").get("evidenceVersion").asInt());
    }

    @Test
    void theBoundaryDropsAnUnknownEnumAWrongSchemaVersionABadDateAndTheWrongTopic() {
        var relay = new LineAcceptanceRelay();
        GenericRecord unknown = LineAcceptanceFixtures.core("M5", D1, "a", 1, 1);
        TaFixtures.put(unknown, "s0Verdict", "UNKNOWN");
        GenericRecord badVersion = LineAcceptanceFixtures.core("M5", D1, "b", 2, 2);
        TaFixtures.put(badVersion, "schemaVersion", 2);
        GenericRecord badDate = LineAcceptanceFixtures.core("M5", "2026-1-2", "c", 3, 3);
        GenericRecord badTf = LineAcceptanceFixtures.core("UNKNOWN", D1, "d", 4, 4);
        for (GenericRecord r : List.of(unknown, badVersion, badDate, badTf)) {
            assertNull(relay.onLive(E, r));
        }
        assertNull(relay.onLive(M, LineAcceptanceFixtures.core("M5", D1, "e", 5, 5)), "a core on the monitor topic");
        assertNull(relay.onLive(E, null), "a tombstone");
        assertEquals(6, relay.dropsForTest());
        assertEquals(0, relay.episodesForTest("5m"));
    }

    @Test
    void publishSeqMustRiseAndAPartVersionOnlyRises() {
        var relay = new LineAcceptanceRelay();
        assertNotNull(relay.onLive(E, LineAcceptanceFixtures.core("M5", D1, "a", 10, 1000)));
        assertNull(relay.onLive(E, LineAcceptanceFixtures.core("M5", D1, "b", 9, 1100)), "publishSeq regressed");
        assertEquals(1, relay.regressionsForTest());
        assertNotNull(relay.onLive(E, LineAcceptanceFixtures.evidence("M5", D1, "a", 2, 11, 1200)));
        assertNull(relay.onLive(E, LineAcceptanceFixtures.evidence("M5", D1, "a", 1, 12, 1300)), "an older evidence version");
        assertEquals(2, relay.regressionsForTest());
        assertNull(relay.onLive(E, LineAcceptanceFixtures.evidence("M5", D1, "a", 3, 13, 1000)), "asOfMs went backwards for the same episode and part");
        assertEquals(3, relay.regressionsForTest());
    }

    @Test
    void anIdenticalRedeliveryIsIdempotentAndADifferentRecordForTheSamePartAndVersionIsAConflict() {
        var relay = new LineAcceptanceRelay();
        assertNotNull(relay.onLive(E, LineAcceptanceFixtures.core("M5", D1, "a", 1, 1000)));
        assertNull(relay.onLive(E, LineAcceptanceFixtures.core("M5", D1, "a", 1, 1000)));
        assertEquals(1, counter(relay, "duplicate"));
        GenericRecord other = LineAcceptanceFixtures.core("M5", D1, "a", 2, 1001);
        TaFixtures.put(other, "s0Verdict", "ACCEPTED");
        assertNull(relay.onLive(E, other), "a second S0 core for the same episode is refused");
        assertEquals(1, counter(relay, "conflict"));
    }

    @Test
    void aLaterSessionClearsTheTimeframeAndAnEarlierOneIsLate() throws Exception {
        var relay = new LineAcceptanceRelay();
        relay.onLive(E, LineAcceptanceFixtures.core("M5", D1, "a", 1, 1000));
        relay.onLive(E, LineAcceptanceFixtures.core("M1", D1, "z", 1, 1000));
        assertNotNull(relay.onLive(E, LineAcceptanceFixtures.core("M5", D2, "b", 1, 2000)));
        assertEquals(1, relay.episodesForTest("5m"));
        assertEquals(1, relay.episodesForTest("1m"), "another timeframe is untouched");
        assertNull(relay.onLive(E, LineAcceptanceFixtures.core("M5", D1, "c", 2, 3000)));
        assertEquals(1, counter(relay, "late"));
        assertEquals("b", hello(relay, 1 << 20).get("5m").get("episodes").get(0).get("episodeId").asText());
    }

    @Test
    void theNextEpisodeOverTheCapEvictsTheOldest() {
        var relay = new LineAcceptanceRelay();
        for (int i = 0; i < LineAcceptanceRelay.EPISODES_PER_TF + 3; i++) {
            relay.onLive(E, LineAcceptanceFixtures.core("M15", D1, "e" + i, i, 1000 + i));
        }
        assertEquals(LineAcceptanceRelay.EPISODES_PER_TF, relay.episodesForTest("15m"));
        assertEquals(3, counter(relay, "evicted"));
    }

    @Test
    void theHelloCarriesWholeTimeframesMonitorsCoverageAndStatusAndNullWhereNothingIsHeld() throws Exception {
        var relay = new LineAcceptanceRelay();
        JsonNode empty = hello(relay, 1 << 20);
        assertTrue(empty.get("1m").isNull() && empty.get("5m").isNull() && empty.get("15m").isNull());
        assertEquals(0, empty.get("status").size(), "the field's presence is the flag: an empty stream still says the gateway has one");
        relay.onLive(E, LineAcceptanceFixtures.core("M5", D1, "a", 1, 1000));
        relay.onLive(E, LineAcceptanceFixtures.coverage("M5", D1, 1, 2, 1100));
        relay.onLive(M, LineAcceptanceFixtures.monitor("M5", D1, "vwap:mean", "ARMED", 1, 1200));
        relay.onLive(S, LineAcceptanceFixtures.status("cell-1", 1, "UNVALIDATED"));
        JsonNode h = hello(relay, 1 << 20);
        assertTrue(h.get("1m").isNull());
        assertEquals(D1, h.get("5m").get("sessionDate").asText());
        assertEquals(1, h.get("5m").get("episodes").size());
        assertEquals(0, h.get("5m").get("omitted").asInt());
        assertEquals(1, h.get("5m").get("coverage").get("coverageVersion").asInt());
        assertEquals("ARMED", h.get("5m").get("monitors").get(0).get("monitorState").asText());
        assertEquals("UNVALIDATED", h.get("status").get(0).get("state").asText());
    }

    @Test
    void aHelloThatDoesNotFitTrimsTheOldestEpisodesAndSaysHowMany() throws Exception {
        var relay = new LineAcceptanceRelay();
        for (int i = 0; i < 30; i++) {
            relay.onLive(E, LineAcceptanceFixtures.core("M5", D1, "e" + i, i, 1000 + i));
        }
        int one = JSON.readTree(relay.helloField(1 << 20).substring(1) .replaceFirst("^\"lineAcceptance\":", "")).get("5m").get("episodes").get(0).toString().length();
        JsonNode h = hello(relay, 20L * one);
        int kept = h.get("5m").get("episodes").size();
        assertTrue(kept > 0 && kept < 30, "kept " + kept);
        assertEquals(30 - kept, h.get("5m").get("omitted").asInt());
        assertEquals("e29", h.get("5m").get("episodes").get(kept - 1).get("episodeId").asText(), "the newest survive");
    }

    @Test
    void monitorsAreLatestPerLineAndAnOlderOneIsRefused() {
        var relay = new LineAcceptanceRelay();
        assertNotNull(relay.onLive(M, LineAcceptanceFixtures.monitor("M1", D1, "vwap:mean", "ARMED", 1, 2000)));
        assertNotNull(relay.onLive(M, LineAcceptanceFixtures.monitor("M1", D1, "vwap:mean", "APPROACHING", 2, 3000)));
        assertNull(relay.onLive(M, LineAcceptanceFixtures.monitor("M1", D1, "vwap:upper", "ARMED", 1, 3500)), "publishSeq regressed");
        assertEquals(1, relay.regressionsForTest());
    }

    @Test
    void aStatusCellKeepsItsHighestVersion() throws Exception {
        var relay = new LineAcceptanceRelay();
        assertNotNull(relay.onLive(S, LineAcceptanceFixtures.status("c", 1, "UNVALIDATED")));
        assertNotNull(relay.onLive(S, LineAcceptanceFixtures.status("c", 2, "NO_MEASURED_EDGE")));
        assertNull(relay.onLive(S, LineAcceptanceFixtures.status("c", 1, "UNVALIDATED")));
        JsonNode h = hello(relay, 1 << 20);
        assertEquals(1, h.get("status").size());
        assertEquals("NO_MEASURED_EDGE", h.get("status").get(0).get("state").asText());
    }

    @Test
    void aRecoveryMergesOlderHistoryUnderWhatLiveAlreadyHoldsAndReturnsWhatLiveGained() throws Exception {
        var relay = new LineAcceptanceRelay();
        relay.onLive(E, LineAcceptanceFixtures.core("M5", D1, "live", 50, 5000));
        relay.beginRecovery();
        relay.onStaged(E, LineAcceptanceFixtures.core("M5", D1, "old", 1, 1000));
        relay.onStaged(E, LineAcceptanceFixtures.outcome("M5", D1, "old", 1, "HELD", 2, 1500));
        relay.onStaged(E, LineAcceptanceFixtures.core("M5", D1, "live", 49, 4900));   // the same core live already holds
        relay.onStaged(S, LineAcceptanceFixtures.status("c", 1, "UNVALIDATED"));
        assertEquals(0, relay.episodesForTest("5m") - 1, "nothing is visible before the promotion");
        List<String[]> frames = relay.promote();
        assertEquals(2, frames.size(), "the old episode (as one merged view) and the status; the live core is not re-sent");
        JsonNode old = JSON.readTree(frames.get(0)[1]);
        assertEquals("old", old.get("episodeId").asText());
        assertEquals("HELD", old.get("outcome").get("s1Outcome").asText());
        assertEquals(2, relay.episodesForTest("5m"));
        assertEquals(0, relay.regressionsForTest(), "history below live is not a regression");
    }

    @Test
    void aRecoveryOfAnEarlierSessionChangesNothingAndALaterOneReplacesLive() {
        var relay = new LineAcceptanceRelay();
        relay.onLive(E, LineAcceptanceFixtures.core("M5", D2, "live", 5, 5000));
        relay.beginRecovery();
        relay.onStaged(E, LineAcceptanceFixtures.core("M5", D1, "old", 1, 1000));
        assertTrue(relay.promote().isEmpty());
        assertEquals(1, relay.episodesForTest("5m"));
    }

    @Test
    void aFrameIsTheSchemaOrderedRenderingOfTheRecordWithNoStringsAdded() throws Exception {
        var relay = new LineAcceptanceRelay();
        JsonNode core = JSON.readTree(relay.onLive(E, LineAcceptanceFixtures.core("M5", D1, "a", 1, 1000))[1]).get("core");
        List<String> names = new java.util.ArrayList<>();
        core.fieldNames().forEachRemaining(names::add);
        List<String> expected = new java.util.ArrayList<>();
        LineAcceptanceFixtures.schema("LineEpisodeCore").getFields().forEach(f -> expected.add(f.name()));
        assertEquals(expected, names);
        assertFalse(core.toString().toLowerCase().contains("likely"));
    }
}

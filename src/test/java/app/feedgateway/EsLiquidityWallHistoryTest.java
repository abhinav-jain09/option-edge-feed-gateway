package app.feedgateway;

import static app.feedgateway.EsLiquidityWallFixtures.NOW;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.avro.generic.GenericRecord;
import org.junit.jupiter.api.Test;

/**
 * The 60-second session projection (section 8 "The 60-second projection, exactly") folded from its three sources: bucket rows (retained; sizes, states,
 * counts, quality, window quantities), BORN/RETIRED events (retained; the walls alive at each minute's end) and dashboard frames (uptime only; lean, side
 * read, confirmed side, spot, layer B, wall scores).
 */
class EsLiquidityWallHistoryTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    /** NOW is an exact minute boundary: the END of minute NOW/60000 - 1 (a minute covers (m*60000, (m+1)*60000]). */
    private static final long MINUTE_A = NOW / 60_000L - 1;
    private static final long MINUTE_B = MINUTE_A + 1;

    private static long et(String isoLocal) {
        return LocalDateTime.parse(isoLocal).atZone(EsLiquidityWallHistory.ET).toInstant().toEpochMilli();
    }

    private static GenericRecord row(long bucketEnd, String quality, String side, long tick, long lastSize, String state) {
        return EsLiquidityWallFixtures.bucket(bucketEnd, quality, side, tick, lastSize, state).build();
    }

    @Test
    void minutesAreAlignedToTheCollectorClockAndTheTradeDateRollsAtSixPmEt() {
        assertEquals(MINUTE_A, EsLiquidityWallHistory.minuteOf(NOW));
        assertEquals(MINUTE_B, EsLiquidityWallHistory.minuteOf(NOW + 1));
        assertEquals(MINUTE_A, EsLiquidityWallHistory.minuteOf(NOW - 59_999));
        assertEquals(LocalDate.of(2026, 11, 4), EsLiquidityWallHistory.tradeDate(et("2026-11-04T17:59:59.999")));
        assertEquals(LocalDate.of(2026, 11, 5), EsLiquidityWallHistory.tradeDate(et("2026-11-04T18:00:00")));
        assertEquals(et("2026-11-04T18:00:00"), EsLiquidityWallHistory.sessionStartMs(LocalDate.of(2026, 11, 5)));
    }

    @Test
    void twoWindowsFoldRowsIntoSizesStatePrecedenceQualityCountsAndTheDashboardIntoItsOwnFields() throws Exception {
        EsLiquidityWallHistory h = new EsLiquidityWallHistory();
        h.dashboardCoverageFrom(NOW - 10_000L);
        h.bucketsSeekOrigin("SESSION_OPEN", NOW - 3_600_000L);
        h.eventsSeekOrigin("SESSION_OPEN", NOW - 3_600_000L);
        // window A: three buckets LIVE / GAP / LIVE; the BID level NEUTRAL -> INSUFFICIENT_DATA -> EATEN, the ASK level only in the last bucket
        h.foldBucket(row(NOW - 2000, "LIVE", "BID", 30440L, 150L, "NEUTRAL"));
        h.foldBucket(EsLiquidityWallFixtures.bucket(NOW - 1000, "GAP", "BID", 30440L, 400L, "INSUFFICIENT_DATA").set("smoothedSize", 300L).set("added", null).build());
        h.foldBucket(EsLiquidityWallFixtures.bucket(NOW, "LIVE", "BID", 30440L, 100L, "EATEN").set("smoothedSize", 120L).set("added", 41L).build());
        h.foldBucket(row(NOW, "LIVE", "ASK", 30460L, 900L, "STABLE_WALL"));
        h.foldEvent(EsLiquidityWallFixtures.born(NOW, "C1"));
        // two dashboard frames of window A (the relay was up): lean -100 then -412, the second listing wall C1 with its score
        h.foldDashboard(EsLiquidityWallFixtures.dashboard(NOW - 2000, "LIVE").set("walls", List.of()).set("leanThousandths", -100).build());
        h.foldDashboard(EsLiquidityWallFixtures.dashboard(NOW));
        // window B: one DEGRADED bucket whose only level is INSUFFICIENT_DATA, a RETIRED, and a FLIP and a DEFENDED that section 8 does not project
        h.foldBucket(row(NOW + 1000, "DEGRADED", "BID", 30440L, 50L, "INSUFFICIENT_DATA"));
        h.foldDashboard(EsLiquidityWallFixtures.dashboard(NOW + 1000, "DEGRADED").set("walls", List.of())
                .set("spotTick", null).set("leanThousandths", null).set("sideRead", null).set("confirmedSide", null).set("layerBEnabled", false).build());
        h.foldEvent(EsLiquidityWallFixtures.retired(NOW + 1000, "C1", "BREACHED"));
        h.foldEvent(EsLiquidityWallFixtures.flip(NOW + 2000));
        h.foldEvent(EsLiquidityWallFixtures.event(NOW + 3000, "DEFENDED").set("wall", EsLiquidityWallFixtures.eventWall("C1", "RESISTANCE", 30459L, 30461L, NOW)).build());
        // records of an older trade date are late and fold nothing
        h.foldDashboard(EsLiquidityWallFixtures.dashboard(NOW - 3 * 86_400_000L));
        h.foldBucket(row(NOW - 3 * 86_400_000L, "LIVE", "BID", 30440L, 1L, "NEUTRAL"));

        JsonNode v = JSON.readTree(h.render(NOW + 5000));
        assertEquals(EsLiquidityWallHistory.tradeDate(NOW).toString(), v.get("tradeDate").asText());
        assertEquals("ESZ6", v.get("contract").asText());
        assertEquals("Rithmic Test", v.get("depthEnvironment").asText());
        assertEquals("SINCE_START", v.get("dashboardCoverage").asText());
        assertEquals(NOW - 10_000L, v.get("dashboardCoverageFromMs").longValue());
        assertEquals("SESSION_OPEN", v.get("bucketsCoverage").asText());
        assertEquals("SESSION_OPEN", v.get("eventsCoverage").asText());
        assertEquals(3, v.get("framesFolded").intValue());
        assertEquals(1, v.get("framesLate").intValue());
        assertEquals(5, v.get("bucketRowsFolded").intValue());
        assertEquals(1, v.get("bucketRowsLate").intValue());
        assertEquals(2, v.get("bucketRowsNotLive").intValue());
        assertEquals(2, v.get("eventsFolded").intValue(), "BORN and RETIRED only");
        assertEquals(2, v.get("eventsNotProjected").intValue(), "the FLIP and the DEFENDED stay on the live stream");
        assertEquals(2, v.get("minutes").size());

        JsonNode a = v.get("minutes").get(0);
        assertEquals(MINUTE_A, a.get("minute").longValue());
        assertEquals(MINUTE_A * 60_000L, a.get("startMs").longValue());
        assertEquals(NOW, a.get("endMs").longValue());
        assertEquals(3, a.get("buckets").intValue(), "distinct bucket ends of the rows, not rows");
        assertEquals(2, a.get("measuredBuckets").intValue());
        assertEquals(1, a.get("missingBuckets").intValue());
        assertEquals("GAP", a.get("quality").asText(), "the worst non-degraded quality of the minute");
        assertEquals(NOW - 2000, a.get("firstBucketEnd").longValue());
        assertEquals(NOW, a.get("lastBucketEnd").longValue());
        assertEquals("Rithmic Test", a.get("depthEnvironment").asText());
        assertEquals(2, a.get("dashboardFrames").intValue());
        assertEquals(NOW, a.get("dashboardLastBucketEnd").longValue());
        assertEquals(30449L, a.get("spotTick").longValue());
        assertEquals(-412, a.get("leanThousandths").get("last").intValue());
        assertEquals(-412, a.get("leanThousandths").get("min").intValue());
        assertEquals(-100, a.get("leanThousandths").get("max").intValue());
        assertEquals("UP_HEAVY", a.get("confirmedSide").asText());
        assertTrue(a.get("layerBEnabled").booleanValue());
        JsonNode bid = a.get("levels").get(0);
        assertEquals("BID", bid.get("side").asText());
        assertEquals(30440L, bid.get("tick").longValue());
        assertEquals(3, bid.get("buckets").intValue());
        assertEquals(400L, bid.get("maxSize").longValue(), "the maximum lastSize over the minute's rows");
        assertEquals(100L, bid.get("lastSize").longValue());
        assertEquals(300L, bid.get("maxSmoothedSize").longValue());
        assertEquals("EATEN", bid.get("state").asText(), "the highest-precedence state any row showed, an INSUFFICIENT_DATA row notwithstanding");
        assertEquals(NOW, bid.get("windowBucketEnd").longValue());
        assertEquals(41L, bid.get("added").longValue(), "the window of the minute's last LIVE bucket");
        assertEquals("STABLE_WALL", a.get("levels").get(1).get("state").asText());
        assertEquals(1, a.get("walls").size(), "C1 is alive at the minute's end (BORN in it, RETIRED in the next)");
        JsonNode c1 = a.get("walls").get(0);
        assertEquals("C1", c1.get("clusterId").asText());
        assertEquals("RESISTANCE", c1.get("lane").asText());
        assertEquals(30459L, c1.get("loTick").longValue());
        assertEquals(NOW, c1.get("birthBucketEnd").longValue(), "the BORN event's bucket end");
        assertEquals("STRONG", c1.get("grade").asText(), "scored: the minute's last dashboard frame listed it");
        assertEquals(26, c1.get("scorePublished").intValue());
        assertEquals(1, a.get("events").size());
        assertEquals("BORN", a.get("events").get(0).get("kind").asText());

        JsonNode b = v.get("minutes").get(1);
        assertEquals(MINUTE_B, b.get("minute").longValue());
        assertEquals(1, b.get("buckets").intValue());
        assertEquals(0, b.get("measuredBuckets").intValue());
        assertEquals(1, b.get("missingBuckets").intValue());
        assertEquals("DEGRADED", b.get("quality").asText());
        assertEquals(1, b.get("dashboardFrames").intValue());
        assertFalse(b.get("layerBEnabled").booleanValue());
        assertNull(b.get("spotTick"), "an absent spot is elided, never zero");
        assertNull(b.get("leanThousandths"), "an unread lean is elided");
        assertNull(b.get("confirmedSide"));
        assertEquals("INSUFFICIENT_DATA", b.get("levels").get(0).get("state").asText(), "INSUFFICIENT_DATA only when every row showed it");
        assertNull(b.get("levels").get(0).get("windowBucketEnd"), "no LIVE bucket: no window quantities");
        assertEquals(0, b.get("walls").size(), "C1 was RETIRED in this minute");
        assertEquals(1, b.get("events").size(), "the minute lists its BORN and RETIRED events only (section 8)");
        assertEquals("RETIRED", b.get("events").get(0).get("kind").asText());
        assertEquals("BREACHED", b.get("events").get(0).get("retireReason").asText());
        assertFalse(v.toString().contains("FLIP"), "no flip, defence or break event is projected");
    }

    @Test
    void aMinuteBeforeTheRelayStartedIsReconstructedFromRowsAndEventsAloneWithTheDashboardFieldsElided() throws Exception {
        EsLiquidityWallHistory h = new EsLiquidityWallHistory();
        long start = NOW + 120_000L;   // the relay started two minutes after minute A: nothing of the dashboard reached it for A
        h.dashboardCoverageFrom(start);
        h.bucketsSeekOrigin("SESSION_OPEN", NOW - 3_600_000L);
        h.eventsSeekOrigin("SESSION_OPEN", NOW - 3_600_000L);
        h.foldEvent(EsLiquidityWallFixtures.born(NOW - 30_000L, "C1"));   // alive through A, never retired
        h.foldBucket(row(NOW - 1000, "LIVE", "BID", 30440L, 150L, "NEUTRAL"));
        h.foldBucket(row(NOW, "STALE", "BID", 30440L, 120L, "INSUFFICIENT_DATA"));
        h.foldBucket(row(NOW, "STALE", "ASK", 30460L, 900L, "INSUFFICIENT_DATA"));
        // then a live minute two minutes later, with a dashboard frame that scores C1
        h.foldBucket(row(start + 1000, "LIVE", "ASK", 30460L, 880L, "STABLE_WALL"));
        h.foldDashboard(EsLiquidityWallFixtures.dashboard(start + 1000));

        JsonNode v = JSON.readTree(h.render(start + 5000));
        assertEquals("SINCE_START", v.get("dashboardCoverage").asText());
        assertEquals(start, v.get("dashboardCoverageFromMs").longValue());
        JsonNode a = v.get("minutes").get(0);
        assertEquals(MINUTE_A, a.get("minute").longValue());
        assertEquals(2, a.get("buckets").intValue());
        assertEquals(1, a.get("measuredBuckets").intValue());
        assertEquals("STALE", a.get("quality").asText());
        assertEquals("Rithmic Test", a.get("depthEnvironment").asText(), "the rows carry the environment: the test-system label survives a restart");
        assertEquals(0, a.get("dashboardFrames").intValue());
        for (String field : List.of("dashboardLastBucketEnd", "layerBEnabled", "spotTick", "leanThousandths", "sideRead", "confirmedSide")) {
            assertNull(a.get(field), field + " is dashboard-only: not measured before the relay started, never guessed");
        }
        JsonNode bid = a.get("levels").get(0);
        assertEquals(150L, bid.get("maxSize").longValue());
        assertEquals(120L, bid.get("lastSize").longValue());
        assertEquals("NEUTRAL", bid.get("state").asText());
        assertEquals(NOW - 1000, bid.get("windowBucketEnd").longValue());
        assertEquals(40L, bid.get("added").longValue());
        assertEquals("INSUFFICIENT_DATA", a.get("levels").get(1).get("state").asText());
        assertEquals(1, a.get("walls").size(), "the wall alive at the minute's end comes from the events ledger");
        JsonNode c1 = a.get("walls").get(0);
        assertEquals("C1", c1.get("clusterId").asText());
        assertEquals(NOW - 30_000L, c1.get("birthBucketEnd").longValue());
        for (String field : List.of("grade", "scorePublished", "heldBuckets", "scoreSettled", "defendedState", "brokenState")) {
            assertNull(c1.get(field), field + " needs a dashboard frame of the minute: elided");
        }
        JsonNode live = v.get("minutes").get(1);
        assertEquals(1, live.get("dashboardFrames").intValue());
        assertEquals("STRONG", live.get("walls").get(0).get("grade").asText(), "the same wall, scored by the live minute's frame");
        assertEquals(NOW - 30_000L, live.get("walls").get(0).get("birthBucketEnd").longValue(), "identity from the ledger even when the frame lists it");
    }

    @Test
    void oneSurvivingCompactedDashboardFrameClaimsNoEarlierDashboardCoverage() throws Exception {
        EsLiquidityWallHistory h = new EsLiquidityWallHistory();
        long start = NOW + 1000;
        h.dashboardCoverageFrom(start);
        h.bucketsSeekOrigin("SESSION_OPEN", NOW - 3_600_000L);
        h.eventsSeekOrigin("SESSION_OPEN", NOW - 3_600_000L);
        for (int i = 3; i >= 1; i--) {   // three earlier minutes, rows only (what retention keeps after a restart)
            h.foldBucket(row(NOW - i * 60_000L, "LIVE", "BID", 30440L, 100L + i, "NEUTRAL"));
        }
        h.foldDashboard(EsLiquidityWallFixtures.dashboard(start + 500));   // the one frame compaction left, in the live minute
        JsonNode v = JSON.readTree(h.render(start + 2000));
        assertEquals("SINCE_START", v.get("dashboardCoverage").asText());
        assertEquals(start, v.get("dashboardCoverageFromMs").longValue());
        assertEquals(1, v.get("framesFolded").intValue());
        assertEquals(4, v.get("minutes").size());
        for (int i = 0; i < 3; i++) {
            JsonNode m = v.get("minutes").get(i);
            assertEquals(0, m.get("dashboardFrames").intValue(), "no dashboard coverage is claimed for a minute before the relay started");
            assertNull(m.get("leanThousandths"));
            assertNull(m.get("layerBEnabled"));
            assertEquals(1, m.get("buckets").intValue(), "the retained rows still describe it");
        }
        assertEquals(1, v.get("minutes").get(3).get("dashboardFrames").intValue());
        assertFalse(v.toString().contains("\"dashboardCoverage\":\"SESSION_OPEN\""));
    }

    @Test
    void theWindowQuantitiesAreTheMinutesLastLiveBucketRowAndAbsentWithoutOne() throws Exception {
        EsLiquidityWallHistory h = new EsLiquidityWallHistory();
        // two rows of the BID level in minute A: the earlier LIVE, the later GAP (its window is null by section 5): the minute carries the earlier window
        h.foldBucket(EsLiquidityWallFixtures.bucket(NOW - 1000, "LIVE", "BID", 30440L, 150L, "NEUTRAL").set("hidden", null).set("refillCount", null).build());
        h.foldBucket(EsLiquidityWallFixtures.bucket(NOW, "GAP", "BID", 30440L, 120L, "INSUFFICIENT_DATA").set("added", 999L).build());
        // a level with only a GAP row: sizes and state, no window
        h.foldBucket(row(NOW, "GAP", "ASK", 30460L, 900L, "INSUFFICIENT_DATA"));
        // minute B: the later LIVE row wins regardless of arrival order
        h.foldBucket(EsLiquidityWallFixtures.bucket(NOW + 2000, "LIVE", "BID", 30440L, 90L, "EATEN").set("added", 7L).build());
        h.foldBucket(EsLiquidityWallFixtures.bucket(NOW + 1000, "LIVE", "BID", 30440L, 95L, "EATEN").set("added", 3L).build());

        JsonNode v = JSON.readTree(h.render(NOW + 5000));
        assertEquals(5, v.get("bucketRowsFolded").intValue());
        assertEquals(2, v.get("bucketRowsNotLive").intValue());
        JsonNode a = v.get("minutes").get(0);
        JsonNode bid = a.get("levels").get(0);
        assertEquals(NOW - 1000, bid.get("windowBucketEnd").longValue(), "the window ending at the minute's last LIVE bucket, not the later GAP one");
        assertEquals(40L, bid.get("added").longValue());
        assertEquals(50L, bid.get("decrease").longValue());
        assertEquals(45L, bid.get("traded").longValue());
        assertEquals(45L, bid.get("accounted").longValue());
        assertEquals(5L, bid.get("unaccounted").longValue());
        assertEquals(1, bid.get("pullCount").intValue());
        assertNull(bid.get("hidden"), "null in the window: elided, never zero");
        assertNull(bid.get("refillCount"));
        assertEquals(150L, bid.get("maxSize").longValue());
        assertEquals(120L, bid.get("lastSize").longValue(), "the GAP row still carries the level's last size");
        JsonNode ask = a.get("levels").get(1);
        assertEquals(900L, ask.get("maxSize").longValue());
        assertEquals("INSUFFICIENT_DATA", ask.get("state").asText());
        assertNull(ask.get("windowBucketEnd"), "a level with no LIVE bucket row carries no window quantities");
        assertNull(ask.get("added"));
        JsonNode b = v.get("minutes").get(1).get("levels").get(0);
        assertEquals(NOW + 2000, b.get("windowBucketEnd").longValue());
        assertEquals(7L, b.get("added").longValue());
        assertEquals(95L, b.get("maxSize").longValue());
        assertEquals(90L, b.get("lastSize").longValue());
    }

    @Test
    void aRestartBucketRanksAboveStaleAndBelowDegradedAndItsRowFoldsSizesButNoWindow() throws Exception {
        EsLiquidityWallHistory h = new EsLiquidityWallHistory();
        h.foldBucket(row(NOW - 3000, "LIVE", "BID", 30440L, 150L, "NEUTRAL"));
        h.foldBucket(row(NOW - 2000, "STALE", "BID", 30440L, 140L, "INSUFFICIENT_DATA"));
        h.foldBucket(EsLiquidityWallFixtures.bucket(NOW - 1000, "RESTART", "BID", 30440L, 500L, "INSUFFICIENT_DATA").set("added", 999L).build());
        JsonNode a = JSON.readTree(h.render(NOW)).get("minutes").get(0);
        assertEquals("RESTART", a.get("quality").asText(), "RESTART outranks STALE");
        assertEquals(3, a.get("buckets").intValue());
        assertEquals(1, a.get("measuredBuckets").intValue());
        assertEquals(2, a.get("missingBuckets").intValue());
        JsonNode bid = a.get("levels").get(0);
        assertEquals(3, bid.get("buckets").intValue(), "the RESTART row is a row of the level");
        assertEquals(500L, bid.get("maxSize").longValue(), "its sizes fold");
        assertEquals(500L, bid.get("lastSize").longValue());
        assertEquals("NEUTRAL", bid.get("state").asText(), "its INSUFFICIENT_DATA does not outrank a measured state");
        assertEquals(NOW - 3000, bid.get("windowBucketEnd").longValue(), "its window is never taken");
        assertEquals(40L, bid.get("added").longValue());
        // DEGRADED outranks RESTART; a literal outside the five ranks between STALE and RESTART
        h.foldBucket(row(NOW, "DEGRADED", "ASK", 30460L, 1L, "INSUFFICIENT_DATA"));
        assertEquals("DEGRADED", JSON.readTree(h.render(NOW)).get("minutes").get(0).get("quality").asText());
        EsLiquidityWallHistory other = new EsLiquidityWallHistory();
        other.foldBucket(row(NOW - 1000, "STALE", "BID", 30440L, 1L, "INSUFFICIENT_DATA"));
        other.foldBucket(row(NOW, "SOMETHING_NEW", "BID", 30440L, 1L, "INSUFFICIENT_DATA"));
        assertEquals("OTHER", JSON.readTree(other.render(NOW)).get("minutes").get(0).get("quality").asText());
        other.foldBucket(row(NOW - 2000, "RESTART", "BID", 30440L, 1L, "INSUFFICIENT_DATA"));
        assertEquals("RESTART", JSON.readTree(other.render(NOW)).get("minutes").get(0).get("quality").asText());
    }

    @Test
    void aNewerTradeDateClearsTheProjectionAndItsIdentity() {
        EsLiquidityWallHistory h = new EsLiquidityWallHistory();
        h.foldBucket(row(NOW, "LIVE", "BID", 30440L, 150L, "NEUTRAL"));
        h.foldDashboard(EsLiquidityWallFixtures.dashboard(NOW));
        assertEquals(1, h.minuteCount());
        long nextSession = EsLiquidityWallHistory.sessionStartMs(EsLiquidityWallHistory.tradeDate(NOW).plusDays(1)) + 1000;
        h.foldEvent(EsLiquidityWallFixtures.born(nextSession, "C9"));
        assertEquals(1, h.minuteCount());
        assertEquals(EsLiquidityWallHistory.tradeDate(NOW).plusDays(1), h.tradeDate());
        assertTrue(h.render(nextSession).contains("\"contract\":\"ESZ6\""), "the new session's first record (an event) carries the identity");
    }
}

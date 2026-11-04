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
import org.junit.jupiter.api.Test;

/** The 60-second session projection (section 8 "The 60-second projection, exactly") folded over two windows. */
class EsLiquidityWallHistoryTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    /** NOW is an exact minute boundary: the END of minute NOW/60000 - 1 (a minute covers (m*60000, (m+1)*60000]). */
    private static final long MINUTE_A = NOW / 60_000L - 1;
    private static final long MINUTE_B = MINUTE_A + 1;

    private static long et(String isoLocal) {
        return LocalDateTime.parse(isoLocal).atZone(EsLiquidityWallHistory.ET).toInstant().toEpochMilli();
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
    void twoWindowsFoldMaxSizeStatePrecedenceQualityCountsWallsAndEvents() throws Exception {
        EsLiquidityWallHistory h = new EsLiquidityWallHistory();
        h.seekOrigin("SESSION_OPEN", NOW - 3_600_000L);
        // window A: three buckets, LIVE / GAP / LIVE, the level NEUTRAL -> INSUFFICIENT_DATA -> EATEN, the wall first absent then present
        h.foldDashboard(EsLiquidityWallFixtures.dashboard(NOW - 2000, "LIVE")
                .set("levels", List.of(EsLiquidityWallFixtures.level("BID", 30440L, 150L, 140L, "NEUTRAL"))).set("walls", List.of()).set("leanThousandths", -100).build());
        h.foldDashboard(EsLiquidityWallFixtures.dashboard(NOW - 1000, "GAP")
                .set("levels", List.of(EsLiquidityWallFixtures.level("BID", 30440L, 400L, 300L, "INSUFFICIENT_DATA"))).set("walls", List.of()).set("leanThousandths", null).build());
        h.foldDashboard(EsLiquidityWallFixtures.dashboard(NOW, "LIVE")
                .set("levels", List.of(EsLiquidityWallFixtures.level("BID", 30440L, 100L, 120L, "EATEN"), EsLiquidityWallFixtures.level("ASK", 30460L, 900L, 880L, "STABLE_WALL")))
                .set("leanThousandths", -412).build());
        h.foldEvent(EsLiquidityWallFixtures.born(NOW, "C1"));
        // window B: one DEGRADED bucket whose only level is INSUFFICIENT_DATA, a RETIRED, and a FLIP and a DEFENDED that section 8 does not project
        h.foldDashboard(EsLiquidityWallFixtures.dashboard(NOW + 1000, "DEGRADED")
                .set("levels", List.of(EsLiquidityWallFixtures.level("BID", 30440L, 50L, 60L, "INSUFFICIENT_DATA"))).set("walls", List.of())
                .set("spotTick", null).set("leanThousandths", null).set("sideRead", null).set("confirmedSide", null).set("layerBEnabled", false).build());
        h.foldEvent(EsLiquidityWallFixtures.retired(NOW + 1000, "C1", "BREACHED"));
        h.foldEvent(EsLiquidityWallFixtures.flip(NOW + 2000));
        h.foldEvent(EsLiquidityWallFixtures.event(NOW + 3000, "DEFENDED").set("wall", EsLiquidityWallFixtures.eventWall("C1", "RESISTANCE", 30459L, 30461L, NOW)).build());
        // a frame of an older trade date is late and folds nothing
        h.foldDashboard(EsLiquidityWallFixtures.dashboard(NOW - 3 * 86_400_000L));

        JsonNode v = JSON.readTree(h.render(NOW + 5000));
        assertEquals(EsLiquidityWallHistory.tradeDate(NOW).toString(), v.get("tradeDate").asText());
        assertEquals("ESZ6", v.get("contract").asText());
        assertEquals("Rithmic Test", v.get("depthEnvironment").asText());
        assertEquals("SESSION_OPEN", v.get("coverage").asText());
        assertEquals(4, v.get("framesFolded").intValue());
        assertEquals(1, v.get("framesLate").intValue());
        assertEquals(2, v.get("eventsFolded").intValue(), "BORN and RETIRED only");
        assertEquals(2, v.get("eventsNotProjected").intValue(), "the FLIP and the DEFENDED stay on the live stream");
        assertEquals(2, v.get("minutes").size());

        JsonNode a = v.get("minutes").get(0);
        assertEquals(MINUTE_A, a.get("minute").longValue());
        assertEquals(MINUTE_A * 60_000L, a.get("startMs").longValue());
        assertEquals(NOW, a.get("endMs").longValue());
        assertEquals(3, a.get("buckets").intValue());
        assertEquals(2, a.get("measuredBuckets").intValue());
        assertEquals(1, a.get("missingBuckets").intValue());
        assertEquals("GAP", a.get("quality").asText(), "the worst non-degraded quality of the minute");
        assertEquals(NOW - 2000, a.get("firstBucketEnd").longValue());
        assertEquals(NOW, a.get("lastBucketEnd").longValue());
        assertEquals(30449L, a.get("spotTick").longValue());
        assertEquals(-412, a.get("leanThousandths").get("last").intValue());
        assertEquals(-412, a.get("leanThousandths").get("min").intValue());
        assertEquals(-100, a.get("leanThousandths").get("max").intValue());
        assertEquals("UP_HEAVY", a.get("confirmedSide").asText());
        assertTrue(a.get("layerBEnabled").booleanValue());
        JsonNode bid = a.get("levels").get(0);
        assertEquals("BID", bid.get("side").asText());
        assertEquals(30440L, bid.get("tick").longValue());
        assertEquals(400L, bid.get("maxSize").longValue(), "the maximum lastSize over the minute's buckets");
        assertEquals(100L, bid.get("lastSize").longValue());
        assertEquals(300L, bid.get("maxSmoothedSize").longValue());
        assertEquals(3, bid.get("buckets").intValue());
        assertEquals("EATEN", bid.get("state").asText(), "the highest-precedence state any bucket showed, an INSUFFICIENT_DATA bucket notwithstanding");
        assertEquals("STABLE_WALL", a.get("levels").get(1).get("state").asText());
        assertEquals(1, a.get("walls").size(), "the walls live at the minute's end are the last frame's");
        assertEquals("C1", a.get("walls").get(0).get("clusterId").asText());
        assertEquals("STRONG", a.get("walls").get(0).get("grade").asText());
        assertEquals(1, a.get("events").size());
        assertEquals("BORN", a.get("events").get(0).get("kind").asText());
        assertEquals("C1", a.get("events").get(0).get("clusterId").asText());

        JsonNode b = v.get("minutes").get(1);
        assertEquals(MINUTE_B, b.get("minute").longValue());
        assertEquals(1, b.get("buckets").intValue());
        assertEquals(0, b.get("measuredBuckets").intValue());
        assertEquals(1, b.get("missingBuckets").intValue());
        assertEquals("DEGRADED", b.get("quality").asText());
        assertFalse(b.get("layerBEnabled").booleanValue());
        assertNull(b.get("spotTick"), "an absent spot is elided, never zero");
        assertNull(b.get("leanThousandths"), "an unread lean is elided");
        assertNull(b.get("confirmedSide"));
        assertEquals("INSUFFICIENT_DATA", b.get("levels").get(0).get("state").asText(), "INSUFFICIENT_DATA only when every bucket showed it");
        assertEquals(0, b.get("walls").size());
        assertEquals(1, b.get("events").size(), "the minute lists its BORN and RETIRED events only (section 8)");
        assertEquals("RETIRED", b.get("events").get(0).get("kind").asText());
        assertEquals("BREACHED", b.get("events").get(0).get("retireReason").asText());
        assertFalse(v.toString().contains("FLIP"), "no flip, defence or break event is projected");
        assertFalse(v.toString().contains("Added"), "the print-matched quantities are not on the dashboard record and are never invented");
    }

    @Test
    void theWindowQuantitiesAreTheMinutesLastLiveBucketRowAndAbsentWithoutOne() throws Exception {
        EsLiquidityWallHistory h = new EsLiquidityWallHistory();
        h.foldDashboard(EsLiquidityWallFixtures.dashboard(NOW - 1000));
        // two bucket rows of the BID level in minute A: the earlier LIVE, the later GAP (its window is null by section 5): the minute carries the earlier
        h.foldBucket(EsLiquidityWallFixtures.bucket(NOW - 1000, "LIVE", "BID", 30440L, 150L, "NEUTRAL").set("hidden", null).set("refillCount", null).build());
        h.foldBucket(EsLiquidityWallFixtures.bucket(NOW, "GAP", "BID", 30440L, 120L, "INSUFFICIENT_DATA").set("added", 999L).build());
        // a LIVE row of a level the dashboard never listed: window quantities only, sizes and state not measured
        h.foldBucket(EsLiquidityWallFixtures.bucket(NOW - 1000, "LIVE", "ASK", 30470L, 70L, "LOW_LIQUIDITY").build());
        // a LIVE row in minute B for a level whose later LIVE row must win
        h.foldBucket(EsLiquidityWallFixtures.bucket(NOW + 2000, "LIVE", "BID", 30440L, 90L, "EATEN").set("added", 7L).build());
        h.foldBucket(EsLiquidityWallFixtures.bucket(NOW + 1000, "LIVE", "BID", 30440L, 95L, "EATEN").set("added", 3L).build());

        JsonNode v = JSON.readTree(h.render(NOW + 5000));
        assertEquals(4, v.get("bucketsFolded").intValue());
        assertEquals(1, v.get("bucketsNotLive").intValue());
        JsonNode a = v.get("minutes").get(0);
        JsonNode bid = a.get("levels").get(0);
        assertEquals(30440L, bid.get("tick").longValue());
        assertEquals(NOW - 1000, bid.get("windowBucketEnd").longValue(), "the window ending at the minute's last LIVE bucket, not the later GAP one");
        assertEquals(40L, bid.get("added").longValue());
        assertEquals(50L, bid.get("decrease").longValue());
        assertEquals(45L, bid.get("traded").longValue());
        assertEquals(45L, bid.get("accounted").longValue());
        assertEquals(5L, bid.get("unaccounted").longValue());
        assertEquals(1, bid.get("pullCount").intValue());
        assertNull(bid.get("hidden"), "null in the window: elided, never zero");
        assertNull(bid.get("refillCount"));
        assertEquals(150L, bid.get("maxSize").longValue(), "the sizes stay the dashboard's");
        JsonNode ask = a.get("levels").get(1);
        assertEquals(30460L, ask.get("tick").longValue());
        assertNull(ask.get("windowBucketEnd"), "a level with no LIVE bucket row carries no window quantities");
        assertNull(ask.get("added"));
        JsonNode askOnlyBuckets = a.get("levels").get(2);
        assertEquals(30470L, askOnlyBuckets.get("tick").longValue());
        assertEquals(0, askOnlyBuckets.get("buckets").intValue());
        assertNull(askOnlyBuckets.get("maxSize"), "sizes come from the dashboard only: not measured here");
        assertNull(askOnlyBuckets.get("state"));
        assertEquals(40L, askOnlyBuckets.get("added").longValue());
        JsonNode b = v.get("minutes").get(1).get("levels").get(0);
        assertEquals(NOW + 2000, b.get("windowBucketEnd").longValue(), "the later LIVE row wins regardless of arrival order");
        assertEquals(7L, b.get("added").longValue());
    }

    @Test
    void aNewerTradeDateClearsTheProjection() {
        EsLiquidityWallHistory h = new EsLiquidityWallHistory();
        h.foldDashboard(EsLiquidityWallFixtures.dashboard(NOW));
        assertEquals(1, h.minuteCount());
        long nextSession = EsLiquidityWallHistory.sessionStartMs(EsLiquidityWallHistory.tradeDate(NOW).plusDays(1)) + 1000;
        h.foldDashboard(EsLiquidityWallFixtures.dashboard(nextSession));
        assertEquals(1, h.minuteCount());
        assertEquals(EsLiquidityWallHistory.tradeDate(NOW).plusDays(1), h.tradeDate());
    }
}

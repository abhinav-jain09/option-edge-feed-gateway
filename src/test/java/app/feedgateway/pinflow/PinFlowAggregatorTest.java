package app.feedgateway.pinflow;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * §5.1 deterministic aggregation — unit-tested on synthetic in-memory rows (no live DB). Covers the
 * per-strike baseline (first observation emits 0), carry-forward across a missing minute, negative
 * clamp, spot carry-forward, and the gamma-leader argmax/tie-break/zero-gamma carry-forward.
 */
class PinFlowAggregatorTest {

    private static final ZoneId ET = ZoneId.of("America/New_York");
    private static final LocalDate DAY = LocalDate.of(2026, 6, 23);

    private static long minute(int hh, int mm) {
        return ZonedDateTime.of(DAY.atTime(hh, mm), ET).toInstant().toEpochMilli();
    }

    private static PinFlowAggregator.StrikeMinuteRow sr(int hh, int mm, int strike, long callCum,
                                                        long putCum, Integer spot) {
        return new PinFlowAggregator.StrikeMinuteRow(minute(hh, mm), strike,
                BigDecimal.valueOf(callCum), BigDecimal.valueOf(putCum),
                spot == null ? null : BigDecimal.valueOf(spot));
    }

    private static PinFlowAggregator.GexMinuteRow gr(int hh, int mm, int strike, long gex) {
        return new PinFlowAggregator.GexMinuteRow(minute(hh, mm), strike, BigDecimal.valueOf(gex));
    }

    @Test
    void firstObservationEmitsZeroNotCumulative() {
        // A strike first SEEN at frame 0 with a big cumulative must emit 0, not 5,000 ($K).
        List<PinFlowAggregator.StrikeMinuteRow> strikeRows = List.of(
                sr(9, 30, 7510, 5_000_000L, 0L, 7550),
                sr(9, 31, 7510, 5_400_000L, 0L, 7551)); // +400,000 → 400 $K
        PinFlowResponse r = PinFlowAggregator.aggregate(strikeRows, List.of(), ET);

        assertEquals(List.of("09:30", "09:31"), r.t());
        assertEquals(List.of(7510), r.k());
        assertEquals(0, r.cc().get(0).get(0), "first observation seeds baseline, emits 0");
        assertEquals(400, r.cc().get(1).get(0), "delta 400,000 / 1000 = 400 $K");
    }

    @Test
    void carriesBaselineForwardAcrossMissingMinute() {
        // 7510 present at 09:30 and 09:32 but ABSENT at 09:31 (only 7515 is there). The 09:31 delta
        // for 7510 must be 0 (baseline carried forward), and 09:32 measured against the 09:30 baseline.
        List<PinFlowAggregator.StrikeMinuteRow> strikeRows = List.of(
                sr(9, 30, 7510, 1_000_000L, 0L, 7550),
                sr(9, 31, 7515, 2_000_000L, 0L, 7550),   // 7510 absent this minute
                sr(9, 32, 7510, 1_700_000L, 0L, 7550));  // +700,000 vs the 09:30 baseline → 700 $K
        PinFlowResponse r = PinFlowAggregator.aggregate(strikeRows, List.of(), ET);

        assertEquals(List.of(7510, 7515), r.k());
        int i7510 = r.k().indexOf(7510);
        assertEquals(0, r.cc().get(0).get(i7510), "first observation → 0");
        assertEquals(0, r.cc().get(1).get(i7510), "absent minute → baseline carried forward, delta 0");
        assertEquals(700, r.cc().get(2).get(i7510), "measured against the last seen baseline, not the missing minute");
    }

    @Test
    void clampsNegativeDeltasToZero() {
        // A cumulative that decreases (should never happen; defensive) clamps to 0.
        List<PinFlowAggregator.StrikeMinuteRow> strikeRows = List.of(
                sr(9, 30, 7500, 3_000_000L, 0L, 7550),
                sr(9, 31, 7500, 2_000_000L, 0L, 7550)); // -1,000,000 → clamp 0
        PinFlowResponse r = PinFlowAggregator.aggregate(strikeRows, List.of(), ET);
        assertEquals(0, r.cc().get(1).get(0), "negative delta clamps to 0");
    }

    @Test
    void putDeltaAndRoundHalfUp() {
        List<PinFlowAggregator.StrikeMinuteRow> strikeRows = List.of(
                sr(9, 30, 7520, 0L, 1_000L, 7550),
                sr(9, 31, 7520, 0L, 1_500L, 7550)); // +500 → 0.5 $K → round half-up → 1
        PinFlowResponse r = PinFlowAggregator.aggregate(strikeRows, List.of(), ET);
        assertEquals(1, r.cp().get(1).get(0), "500 / 1000 rounds half-up to 1 $K");
        assertEquals(0, r.cc().get(1).get(0), "no call flow");
    }

    @Test
    void spotAveragesAndCarriesForward() {
        // 09:30 has two band rows (avg spot), 09:31 has a null spot → carry-forward.
        List<PinFlowAggregator.StrikeMinuteRow> strikeRows = List.of(
                sr(9, 30, 7550, 0L, 0L, 7550),
                sr(9, 30, 7560, 0L, 0L, 7560),        // avg(7550,7560) = 7555
                sr(9, 31, 7550, 0L, 0L, null));       // null spot → carry forward 7555
        PinFlowResponse r = PinFlowAggregator.aggregate(strikeRows, List.of(), ET);
        assertEquals(7555, r.sp().get(0));
        assertEquals(7555, r.sp().get(1), "null spot carries the previous minute forward");
    }

    @Test
    void gammaLeaderArgmaxTieBreakLowestAndAllNegativeSelectsMax() {
        // 09:30: two strikes tie on net_gex → lowest wins. 09:31: all negative → the max (least negative).
        List<PinFlowAggregator.GexMinuteRow> gexRows = List.of(
                gr(9, 30, 7575, 100L),
                gr(9, 30, 7570, 100L),                 // tie → 7570 (lowest)
                gr(9, 31, 7560, -50L),
                gr(9, 31, 7565, -10L));                // all negative → max is -10 → 7565
        PinFlowResponse r = PinFlowAggregator.aggregate(List.of(), gexRows, ET);
        assertEquals(7570, r.lead().get(0), "tie-break lowest strike");
        assertEquals(7565, r.lead().get(1), "all-negative minute selects the max (least negative), not carry-forward");
    }

    @Test
    void gammaLeaderCarriesForwardOnlyOnZeroGammaMinute() {
        // Frame grid spans 09:30..09:32 (spot rows fill the gaps). 09:31 has NO gamma rows → carry 09:30.
        List<PinFlowAggregator.StrikeMinuteRow> strikeRows = List.of(
                sr(9, 30, 7550, 0L, 0L, 7550),
                sr(9, 31, 7550, 0L, 0L, 7550),
                sr(9, 32, 7550, 0L, 0L, 7550));
        List<PinFlowAggregator.GexMinuteRow> gexRows = List.of(
                gr(9, 30, 7575, 200L),
                gr(9, 32, 7580, 300L));                // 09:31 has zero gamma rows
        PinFlowResponse r = PinFlowAggregator.aggregate(strikeRows, gexRows, ET);
        assertEquals(List.of("09:30", "09:31", "09:32"), r.t());
        assertEquals(7575, r.lead().get(0));
        assertEquals(7575, r.lead().get(1), "zero-gamma minute carries the previous lead forward");
        assertEquals(7580, r.lead().get(2));
    }

    @Test
    void leadIsZeroBeforeFirstGammaMinute() {
        // 09:30 has no gamma at all; the lead has no prior → 0.
        List<PinFlowAggregator.StrikeMinuteRow> strikeRows = List.of(sr(9, 30, 7550, 0L, 0L, 7550));
        PinFlowResponse r = PinFlowAggregator.aggregate(strikeRows, List.of(), ET);
        assertEquals(0, r.lead().get(0), "no prior lead → 0");
    }

    @Test
    void frameGridIsUnionOfMinutesFromBothTables() {
        List<PinFlowAggregator.StrikeMinuteRow> strikeRows = List.of(sr(9, 30, 7550, 0L, 0L, 7550));
        List<PinFlowAggregator.GexMinuteRow> gexRows = List.of(gr(9, 45, 7575, 10L));
        PinFlowResponse r = PinFlowAggregator.aggregate(strikeRows, gexRows, ET);
        assertEquals(List.of("09:30", "09:45"), r.t(), "frames = sorted distinct minutes across BOTH tables");
    }

    @Test
    void duplicateMinuteStrikeTakesMax() {
        // Defensive: two rows for the same (minute,strike) → the MAX cumulative is used.
        List<PinFlowAggregator.StrikeMinuteRow> strikeRows = new ArrayList<>();
        strikeRows.add(sr(9, 30, 7510, 1_000_000L, 0L, 7550)); // baseline seed
        strikeRows.add(sr(9, 31, 7510, 1_200_000L, 0L, 7550));
        strikeRows.add(sr(9, 31, 7510, 1_400_000L, 0L, 7550)); // dup, higher → wins
        PinFlowResponse r = PinFlowAggregator.aggregate(strikeRows, List.of(), ET);
        assertEquals(400, r.cc().get(1).get(0), "max cumulative (1.4M) - baseline (1.0M) = 400 $K");
    }
}

package app.feedgateway.pinflow;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The chart's price line comes from pin_spot_minute when available. pin_strike_minute.spot is a
 * leakage-guarded TRAINING column and is frequently NULL — on dev it was 100% NULL while the spot feed was
 * healthy, which drew a flat line. History wins; absent history reproduces the old behaviour exactly.
 */
class PinFlowSpotHistoryTest {

    private static final ZoneId NY = ZoneId.of("America/New_York");
    private static final long M0 = 1_784_000_040_000L / 60_000L * 60_000L;
    private static final long M1 = M0 + 60_000L;

    private static PinFlowAggregator.StrikeMinuteRow row(long minute, BigDecimal spot) {
        return new PinFlowAggregator.StrikeMinuteRow(minute, 7500, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, spot, "2026-07-20");
    }

    @Test
    void spotHistoryFillsTheLineWhenTheTrainingColumnIsNull() {
        // Exactly the dev failure: every strike row has spot NULL, so the old path drew 0/flat.
        List<PinFlowAggregator.StrikeMinuteRow> rows = List.of(row(M0, null), row(M1, null));
        Map<Long, BigDecimal> history = Map.of(
                M0, new BigDecimal("7478.80"),
                M1, new BigDecimal("7482.30"));

        PinFlowResponse out = PinFlowAggregator.aggregate(rows, List.of(), NY, history);

        assertEquals(List.of(7479, 7482), out.sp(), "price line comes from the spot history");
    }

    @Test
    void historyWinsOverTheStrikeColumn() {
        List<PinFlowAggregator.StrikeMinuteRow> rows = List.of(row(M0, new BigDecimal("7000.00")));
        Map<Long, BigDecimal> history = Map.of(M0, new BigDecimal("7478.80"));

        PinFlowResponse out = PinFlowAggregator.aggregate(rows, List.of(), NY, history);

        assertEquals(List.of(7479), out.sp(), "authoritative history overrides the guarded column");
    }

    @Test
    void withoutHistoryBehaviourIsUnchanged() {
        // Environments whose writer has not been upgraded must behave exactly as before.
        List<PinFlowAggregator.StrikeMinuteRow> rows = List.of(row(M0, new BigDecimal("7401.00")));

        PinFlowResponse legacy = PinFlowAggregator.aggregate(rows, List.of(), NY);
        PinFlowResponse empty = PinFlowAggregator.aggregate(rows, List.of(), NY, Map.of());

        assertEquals(List.of(7401), legacy.sp());
        assertEquals(legacy.sp(), empty.sp(), "empty history == legacy path");
    }

    @Test
    void missingMinutesStillCarryForward() {
        // History covers only the first minute; the second must carry it forward, not drop to 0.
        List<PinFlowAggregator.StrikeMinuteRow> rows = List.of(row(M0, null), row(M1, null));
        Map<Long, BigDecimal> history = Map.of(M0, new BigDecimal("7478.80"));

        PinFlowResponse out = PinFlowAggregator.aggregate(rows, List.of(), NY, history);

        assertEquals(List.of(7479, 7479), out.sp(), "carry-forward across a gap in the history");
    }
}

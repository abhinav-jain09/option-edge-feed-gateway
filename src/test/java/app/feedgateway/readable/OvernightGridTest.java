package app.feedgateway.readable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** <b>C-60</b>: the fixed time grid, and the backfill that makes a stall self-healing. */
class OvernightGridTest {

    /** 2026-11-19 21:00:00Z = 16:00 New York, the previous cash close. */
    private static final long CLOSE = 1_795_122_000_000L;
    private static final long MIN = 60_000L;
    private static final long SLOT = OvernightGrid.SLOT_MS;

    private static OvernightGrid.Bar bar(long atMs, double o, double h, double l, double c,
            long vol) {
        return OvernightGrid.Bar.spx(atMs, o, h, l, c, vol, "ANCHORED", "1m", null, null, null);
    }

    @Test
    @DisplayName("every slot from the close to now exists, in order, blank ones included")
    void theGridIsATimeGridAndNotAListOfWhatArrived() {
        // one bar in the first slot, one in the fourth; nothing between
        List<OvernightGrid.Bar> bars = List.of(
                bar(CLOSE, 7180, 7181, 7179, 7180.5, 100),
                bar(CLOSE + 3 * SLOT + MIN, 7160, 7162, 7159, 7161, 200));
        ReadablePage.Overnight o = OvernightGrid.build(
                CLOSE + 3 * SLOT + 5 * MIN, CLOSE, 7176.24, bars, null, null);

        assertEquals(4, o.slots().size(), "four slots spanned, none dropped");
        for (int i = 0; i < 4; i++) {
            assertEquals(CLOSE + i * SLOT, o.slots().get(i).startMs(),
                    "slot " + i + " is in its own place, in order");
        }
        assertTrue(o.slots().get(0).filled());
        assertFalse(o.slots().get(1).filled(), "a slot with no bars is present and blank");
        assertFalse(o.slots().get(2).filled());
        assertTrue(o.slots().get(3).filled());
        assertNull(o.slots().get(1).close(), "blank as a block, never a carried-forward price");
    }

    @Test
    @DisplayName("a late bar lands in its OWN slot, which is the backfill")
    void backfillFillsTheSlotThatWasBlankRatherThanAppending() {
        long now = CLOSE + 2 * SLOT + MIN;
        List<OvernightGrid.Bar> before = List.of(bar(CLOSE, 7180, 7181, 7179, 7180.5, 100));
        ReadablePage.Overnight first = OvernightGrid.build(now, CLOSE, 7176.24, before, null, null);
        assertFalse(first.slots().get(1).filled(), "the middle slot is blank on the first build");

        // the producer catches up and the bar for the middle slot arrives late
        List<OvernightGrid.Bar> after = new ArrayList<>(before);
        after.add(bar(CLOSE + SLOT + 2 * MIN, 7170, 7172, 7168, 7171, 150));
        ReadablePage.Overnight second = OvernightGrid.build(now, CLOSE, 7176.24, after, null, null);

        assertEquals(first.slots().size(), second.slots().size(), "the grid did not grow");
        assertTrue(second.slots().get(1).filled(), "the late bar filled the slot it belongs to");
        assertEquals(7171.0, second.slots().get(1).close(), 1e-9);
        assertEquals(CLOSE + SLOT, second.slots().get(1).startMs(),
                "and it kept that slot's own timestamp, it was not appended at the end");
    }

    @Test
    @DisplayName("open is the earliest bar's and close the latest bar's, whatever the input order")
    void openAndCloseFollowTimeAndNotListOrder() {
        List<OvernightGrid.Bar> shuffled = List.of(
                bar(CLOSE + 5 * MIN, 7175, 7176, 7174, 7175.5, 10),
                bar(CLOSE, 7180, 7182, 7178, 7179, 20),
                bar(CLOSE + 10 * MIN, 7170, 7171, 7165, 7166, 30));
        ReadablePage.Slot slot = OvernightGrid
                .build(CLOSE + 12 * MIN, CLOSE, 7176.24, shuffled, null, null)
                .slots().get(0);
        assertEquals(7180.0, slot.open(), 1e-9, "the earliest bar's open");
        assertEquals(7166.0, slot.close(), 1e-9, "the latest bar's close");
        assertEquals(7182.0, slot.high(), 1e-9);
        assertEquals(7165.0, slot.low(), 1e-9);
        assertEquals(60L, slot.volume(), "volume sums");
    }

    @Test
    @DisplayName("the extremes span the TICK path as well as the bars")
    void theTickLowBelowEveryBarIsTheNightsLow() {
        // The measured case from 2026-11-20: the tick low sat under every bar's low.
        List<OvernightGrid.Bar> bars = List.of(bar(CLOSE, 7180, 7181, 7179, 7180.5, 100));
        ReadablePage.Overnight o = OvernightGrid.build(
                CLOSE + MIN, CLOSE, 7176.24, bars, 7181.74, 7148.74);
        assertEquals(7181.74, o.high(), 1e-9, "the tick high won");
        assertEquals(7148.74, o.low(), 1e-9, "and the tick low, which no bar reached");
    }

    @Test
    @DisplayName("with no ticks the bars' own extremes are used, and vice versa")
    void eitherSourceAloneStillGivesExtremes() {
        List<OvernightGrid.Bar> bars = List.of(bar(CLOSE, 7180, 7185, 7175, 7180.5, 100));
        ReadablePage.Overnight barsOnly =
                OvernightGrid.build(CLOSE + MIN, CLOSE, 7176.24, bars, null, null);
        assertEquals(7185.0, barsOnly.high(), 1e-9);
        assertEquals(7175.0, barsOnly.low(), 1e-9);

        ReadablePage.Overnight ticksOnly =
                OvernightGrid.build(CLOSE + MIN, CLOSE, 7176.24, List.of(), 7190.0, 7100.0);
        assertEquals(7190.0, ticksOnly.high(), 1e-9);
        assertEquals(7100.0, ticksOnly.low(), 1e-9);
        assertFalse(ticksOnly.slots().isEmpty(), "and the grid still has its slots");
        assertFalse(ticksOnly.slots().get(0).filled());
    }

    @Test
    @DisplayName("a bar outside the night is ignored rather than folded into an edge slot")
    void barsOutsideTheSpanAreIgnored() {
        List<OvernightGrid.Bar> bars = List.of(
                bar(CLOSE - 10 * MIN, 9999, 9999, 9999, 9999, 1),   // before the close
                bar(CLOSE + 100 * MIN, 8888, 8888, 8888, 8888, 1),  // after now
                bar(CLOSE, 7180, 7181, 7179, 7180.5, 100));
        ReadablePage.Overnight o =
                OvernightGrid.build(CLOSE + 5 * MIN, CLOSE, 7176.24, bars, null, null);
        assertEquals(1, o.slots().size());
        assertEquals(7180.5, o.slots().get(0).close(), 1e-9,
                "neither the earlier nor the later bar reached this slot");
        assertEquals(100L, o.slots().get(0).volume());
    }

    @Test
    @DisplayName("a basis that is not ANCHORED is named on the row, not dropped")
    void anUnanchoredBasisIsKeptAndNamed() {
        List<OvernightGrid.Bar> bars = List.of(
                OvernightGrid.Bar.spx(CLOSE, 7180, 7181, 7179, 7180.5, 100, "QUARANTINED", "1m", null, null, null));
        ReadablePage.Slot slot = OvernightGrid
                .build(CLOSE + MIN, CLOSE, 7176.24, bars, null, null).slots().get(0);
        assertTrue(slot.filled(), "the row is kept");
        assertEquals("QUARANTINED", slot.basisState(), "and its state is named");
    }

    @Test
    @DisplayName("slots are aligned to the quarter hour, not offset from the first bar")
    void slotsAreFloored() {
        assertEquals(CLOSE, OvernightGrid.slotOf(CLOSE));
        assertEquals(CLOSE, OvernightGrid.slotOf(CLOSE + 14 * MIN + 59_000L));
        assertEquals(CLOSE + SLOT, OvernightGrid.slotOf(CLOSE + SLOT));
    }

    @Test
    @DisplayName("a night that has not begun is refused rather than rendered backwards")
    void nowBeforeTheCloseIsRefused() {
        assertThrows(IllegalArgumentException.class,
                () -> OvernightGrid.build(CLOSE - 1, CLOSE, 7176.24, List.of(), null, null));
    }

    @Test
    @DisplayName("the whole night renders inside C-61's character bound")
    void aFullNightOfSlotsFitsThePage() {
        // the cash close to the next open is about 17.5 hours = 70 slots, all filled
        List<OvernightGrid.Bar> bars = new ArrayList<>();
        long span = 70 * SLOT;
        for (long at = CLOSE; at < CLOSE + span; at += MIN) {
            bars.add(bar(at, 7180, 7181, 7179, 7180.5, 1_234));
        }
        ReadablePage.Overnight o =
                OvernightGrid.build(CLOSE + span, CLOSE, 7176.24, bars, 7181.0, 7179.0);
        assertEquals(71, o.slots().size());
        String page = ReadablePage.render(CLOSE + span, new ReadablePage.Price(
                CLOSE + span, 7180.5, "ES_BASIS_DERIVED", "LIVE_ES_BASIS"), o, null, List.of());
        assertTrue(page.length() < ReadablePage.MAX_CHARS,
                "a whole night of slots leaves room for the board: " + page.length());
    }
}

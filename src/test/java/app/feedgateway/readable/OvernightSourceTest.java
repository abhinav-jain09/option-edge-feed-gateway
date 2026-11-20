package app.feedgateway.readable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The two overnight chains, held apart and bounded to one night. */
class OvernightSourceTest {

    private static final long CLOSE = 1_795_122_000_000L;
    private static final long MIN = 60_000L;

    private static OvernightGrid.Bar bar(long at, double close) {
        return new OvernightGrid.Bar(at, close, close, close, close, 10L, "ANCHORED", "1m", null, null, null);
    }

    private static ReadablePage.Price price(long at, double spx) {
        return new ReadablePage.Price(at, spx, "ES_BASIS_DERIVED", "LIVE_ES_BASIS");
    }

    @Test
    @DisplayName("a bar re-read after a restart replaces rather than duplicates")
    void barsAreKeyedByTheirOwnStart() {
        OvernightSource s = new OvernightSource();
        s.nightStartsAt(CLOSE);
        s.onBar(bar(CLOSE, 7180));
        s.onBar(bar(CLOSE, 7180));
        s.onBar(bar(CLOSE + MIN, 7181));
        assertEquals(2, s.barCount(), "the same bar twice is one bar");
    }

    @Test
    @DisplayName("the bound is the NIGHT, so a long run cannot accumulate across sessions")
    void rollingTheNightDropsTheOldOne() {
        OvernightSource s = new OvernightSource();
        s.nightStartsAt(CLOSE);
        s.onBar(bar(CLOSE, 7180));
        s.onBar(bar(CLOSE + 60 * MIN, 7170));
        assertEquals(2, s.barCount());

        s.nightStartsAt(CLOSE + 30 * MIN);
        assertEquals(1, s.barCount(), "the previous night's bars are gone");
        assertEquals(CLOSE + 60 * MIN, s.newestBarStartMs("1m"));
    }

    @Test
    @DisplayName("a bar before the night's start is refused and counted, never folded in")
    void aBarBeforeTheNightIsRefused() {
        OvernightSource s = new OvernightSource();
        s.nightStartsAt(CLOSE);
        s.onBar(bar(CLOSE - MIN, 9999));
        assertEquals(0, s.barCount());
        assertEquals(1, s.barsRefused(), "refusals are counted rather than silent");
        s.onBar(null);
        assertEquals(2, s.barsRefused());
    }

    @Test
    @DisplayName("an older price never replaces a newer one")
    void thePriceNeverGoesBackwards() {
        OvernightSource s = new OvernightSource();
        s.onPrice(price(CLOSE + 10 * MIN, 7170));
        s.onPrice(price(CLOSE + 5 * MIN, 7999));
        assertEquals(7170.0, s.lastPrice().spx(), 1e-9,
                "the out-of-order delivery after a seek did not rewind the page");
        assertEquals(1, s.pricesRefused());
        s.onPrice(price(CLOSE + 11 * MIN, 7171));
        assertEquals(7171.0, s.lastPrice().spx(), 1e-9, "and a newer one still lands");
    }

    @Test
    @DisplayName("the price survives a bar stall, because they are different chains")
    void aStalledBarChainLeavesThePriceAlone() {
        // the measured case: bars stop at 23:16Z, the price runs on for forty minutes
        OvernightSource s = new OvernightSource();
        s.nightStartsAt(CLOSE);
        s.onBar(bar(CLOSE + 76 * MIN, 7171.45));
        s.onPrice(price(CLOSE + 116 * MIN, 7161.20));

        assertEquals(CLOSE + 76 * MIN, s.newestBarStartMs("1m"), "the bar chain is behind");
        assertEquals(7161.20, s.lastPrice().spx(), 1e-9, "the price is not");
        assertTrue(s.lastPrice().epochMs() - s.newestBarStartMs("1m") > 30 * MIN,
                "and the gap between them is visible, which is what the page reports");
    }

    @Test
    @DisplayName("an empty source renders a page that says so rather than a zero")
    void anEmptySourceIsHonest() {
        OvernightSource s = new OvernightSource();
        assertNull(s.lastPrice());
        assertEquals(0, s.barCount());
        String page = ReadablePage.render(CLOSE, s.lastPrice(), null, null, List.of());
        assertTrue(page.startsWith("NO PRICE"));
    }

    @Test
    @DisplayName("the previous cash close is the last INDEX print, not the ES derivation")
    void thePreviousCashCloseIsMeasuredFromTheIndex() {
        OvernightSource s = new OvernightSource();
        // the cash session: the index itself is printing
        s.onPrice(new ReadablePage.Price(CLOSE - 120_000L, 7176.80,
                "NATIVE_SPX_INDEX", "LIVE_INDEX"));
        s.onPrice(new ReadablePage.Price(CLOSE - 66_000L, 7176.24,
                "NATIVE_SPX_INDEX", "LIVE_INDEX"));
        // C-98: WHILE THE INDEX IS STILL PRINTING THERE IS NO CLOSE YET, and that is the change.
        // Under the old rule the newest index print was the close immediately -- which is how a
        // pre-market print became "yesterday's close" on 2026-11-20. A session is closed by the
        // index going quiet, not by a print being the newest one.
        assertNull(s.previousCashClose(), "no silence yet, so no session has ended");
        assertNotNull(s.closeAbsenceReason());

        // then the index stops and the night runs on the ES-basis derivation for hours. Those
        // prints never become the close; they advance the clock, and after three hours of index
        // silence the last index print IS the close.
        int overnightTicks = 240;                 // four hours, so the three-hour silence elapses
        for (int i = 1; i <= overnightTicks; i++) {
            s.onPrice(new ReadablePage.Price(CLOSE + i * 60_000L, 7150.0 + i,
                    "ES_BASIS_DERIVED", "LIVE_ES_BASIS"));
        }
        assertEquals(7176.24, s.previousCashClose().spx(), 1e-9,
                "the reference price FREEZES at the close and the overnight ticks never move it");
        assertEquals(7150.0 + overnightTicks, s.lastPrice().spx(), 1e-9,
                "while the last price keeps moving");
    }

    @Test
    @DisplayName("an UNKNOWN source is never taken as a cash close")
    void anUnrecognisedSourceIsNotAClose() {
        // A wrong reference price would be frozen for a whole night, which is worse than none.
        OvernightSource s = new OvernightSource();
        s.onPrice(new ReadablePage.Price(CLOSE, 9999.0, "SOMETHING_NEW", "UNKNOWN"));
        assertNull(s.previousCashClose(), "unrecognised is not an index print");
        assertEquals(9999.0, s.lastPrice().spx(), 1e-9, "but it is still the last price");
    }

    @Test
    @DisplayName("before any index print there is no reference price, and that is said not invented")
    void noIndexPrintMeansNoReference() {
        OvernightSource s = new OvernightSource();
        s.onPrice(new ReadablePage.Price(CLOSE, 7160.0, "ES_BASIS_DERIVED", "LIVE_ES_BASIS"));
        assertNull(s.previousCashClose());
    }

    @Test
    @DisplayName("a 30-second bar never collides with the minute that contains it")
    void theTwoTimeframesAreKeptApart() {
        // The platform publishes the same stretch at several lengths. If they shared a key, a
        // minute laid beside its own two halves would double every volume on the row.
        OvernightSource s = new OvernightSource();
        s.nightStartsAt(CLOSE);
        s.onBar(new OvernightGrid.Bar(CLOSE, 7180, 7180, 7180, 7180, 100L, "ANCHORED", "1m", null, null, null));
        s.onBar(new OvernightGrid.Bar(CLOSE, 7180, 7180, 7180, 7180, 60L, "ANCHORED", "30s", null, null, null));
        s.onBar(new OvernightGrid.Bar(CLOSE + 30_000L, 7181, 7181, 7181, 7181, 40L, "ANCHORED",
                "30s", null, null, null));

        assertEquals(1, s.barCount("1m"), "the minute is held once");
        assertEquals(2, s.barCount("30s"), "and its two halves separately");
        assertEquals(3, s.barCount(), "three records in all");
        assertEquals(1, s.bars("1m").size(), "and a reader of one timeframe sees only that one");
        assertEquals(100L, s.bars("1m").iterator().next().volume(),
                "a Collection and no longer a List: bars() hands over a VIEW of the held map "
                        + "rather than copying the night on every render, which is the "
                        + "allocation that was on the stack when the heap ran out on 2026-11-20");
        assertEquals(100L, s.bars("30s").stream().mapToLong(OvernightGrid.Bar::volume).sum(),
                "the halves sum to the minute, which is exactly why they must not be mixed");
        assertEquals(java.util.Set.of("1m", "30s"), s.timeframes());
    }

    @Test
    @DisplayName("a bar with no timeframe is refused outright, not given a default")
    void aBarWithoutATimeframeIsRefused() {
        assertThrows(IllegalArgumentException.class,
                () -> new OvernightGrid.Bar(CLOSE, 7180, 7180, 7180, 7180, 1L, "ANCHORED", null,
                        null, null, null));
        assertThrows(IllegalArgumentException.class,
                () -> new OvernightGrid.Bar(CLOSE, 7180, 7180, 7180, 7180, 1L, "ANCHORED", " ",
                        null, null, null));
    }
}

package app.feedgateway.readable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The two overnight chains, held apart and bounded to one night. */
class OvernightSourceTest {

    private static final long CLOSE = 1_795_122_000_000L;
    private static final long MIN = 60_000L;

    private static OvernightGrid.Bar bar(long at, double close) {
        return new OvernightGrid.Bar(at, close, close, close, close, 10L, "ANCHORED");
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
        assertEquals(CLOSE + 60 * MIN, s.newestBarStartMs());
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

        assertEquals(CLOSE + 76 * MIN, s.newestBarStartMs(), "the bar chain is behind");
        assertEquals(7161.20, s.lastPrice().spx(), 1e-9, "the price is not");
        assertTrue(s.lastPrice().epochMs() - s.newestBarStartMs() > 30 * MIN,
                "and the gap between them is visible, which is what the page reports");
    }

    @Test
    @DisplayName("an empty source renders a page that says so rather than a zero")
    void anEmptySourceIsHonest() {
        OvernightSource s = new OvernightSource();
        assertNull(s.lastPrice());
        assertEquals(0, s.barCount());
        String page = ReadablePage.render(CLOSE, s.lastPrice(), null, null);
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
        assertEquals(7176.24, s.previousCashClose().spx(), 1e-9, "the last index print");

        // then the index stops and the night runs on the ES-basis derivation for hours
        for (int i = 1; i <= 50; i++) {
            s.onPrice(new ReadablePage.Price(CLOSE + i * 60_000L, 7150.0 + i,
                    "ES_BASIS_DERIVED", "LIVE_ES_BASIS"));
        }
        assertEquals(7176.24, s.previousCashClose().spx(), 1e-9,
                "the reference price FREEZES at the close and the overnight ticks never move it");
        assertEquals(7200.0, s.lastPrice().spx(), 1e-9, "while the last price keeps moving");
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
}

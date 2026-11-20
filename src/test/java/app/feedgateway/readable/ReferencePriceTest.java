package app.feedgateway.readable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * C-74: a gateway that restarts overnight recovers the previous cash close instead of waiting for
 * the cash open.
 *
 * <p>The clock in these tests is the real one of the fault: the cash close of 2026-11-19 at 16:00
 * New York, and a gateway starting at 03:00 Amsterdam — 21:00 New York the same evening — when the
 * index has not printed for five hours and will not print again for eleven.
 */
class ReferencePriceTest {

    /** 2026-11-19 16:00:00 New York — the cash close. */
    private static final long CLOSE = 1_795_122_000_000L;
    /** 03:00 Amsterdam on 2026-11-20 = 21:00 New York on 2026-11-19. */
    private static final long STARTED_AT_0300 = CLOSE + 5L * 3_600_000L;

    private static String priceJson(long atMs, double spx, String source, String quality) {
        return "{\"schemaVersion\":1,\"symbol\":\"SPX\",\"price\":\"" + spx + "\","
                + "\"canonicalSpot\":\"" + spx + "\",\"source\":\"" + source + "\","
                + "\"quality\":\"" + quality + "\",\"spotEventTimeNs\":" + (atMs * 1_000_000L) + "}";
    }

    private static String barJson(long startMs, long spxCloseCents) {
        return "{\"schemaVersion\":6,\"symbol\":\"ES.v.0\",\"timeframe\":\"1m\","
                + "\"sessionDate\":\"2026-11-19\",\"observations\":{\"barStartMs\":" + startMs
                + ",\"complete\":true,\"openCents\":718550,\"closeCents\":718975,"
                + "\"spxEqOpenCents\":" + (spxCloseCents - 20) + ",\"spxEqHighCents\":"
                + (spxCloseCents + 30) + ",\"spxEqLowCents\":" + (spxCloseCents - 40)
                + ",\"spxEqCloseCents\":" + spxCloseCents + ",\"spxEqBasisState\":\"ANCHORED\","
                + "\"volume\":1200}}";
    }

    /** History as the gateway would read it back: newest-first prices, oldest-first bars. */
    private static ReferencePrice.History history(List<String> prices, List<String> bars) {
        return new ReferencePrice.History() {
            @Override
            public List<String> pricesBefore(long beforeMs, int limit) {
                return prices;
            }

            @Override
            public List<String> cashSessionBars(long beforeMs) {
                return bars;
            }
        };
    }

    /** The real shape: an index print at the close, then hours of ES-basis derivation after it. */
    private static List<String> theNightsPrices() {
        List<String> out = new ArrayList<>();
        out.add(priceJson(CLOSE - 1_000L, 7176.24, "NATIVE_SPX_INDEX", "LIVE_INDEX"));
        for (int i = 1; i <= 5; i++) {
            out.add(priceJson(CLOSE + i * 3_600_000L, 7160.00 + i,
                    "ES_BASIS_DERIVED", "LIVE_ES_BASIS"));
        }
        return out;
    }

    @Test
    @DisplayName("started at 03:00 with history on the topic, the close is the index print 7176.24")
    void theIndexPrintIsRecovered() {
        ReferencePrice.Resolved r = ReferencePrice.resolve(CLOSE,
                history(theNightsPrices(), List.of(barJson(CLOSE - 60_000L, 717_624L))));

        assertEquals(ReferencePrice.From.INDEX_PRINT, r.from());
        assertEquals(7176.24, r.close().spx(), 1e-9,
                "the previous cash close, recovered rather than waited for");
        assertTrue(r.label().contains("SPX index's own last print"), r.label());
    }

    @Test
    @DisplayName("the overnight derivation is never taken as a close, however new it is")
    void theBasisDerivationIsNeverTheClose() {
        // Five ES_BASIS_DERIVED prints sit AFTER the index print and are newer. The newest record
        // is not the close; the newest INDEX record is.
        ReferencePrice.Resolved r = ReferencePrice.resolve(CLOSE + 6L * 3_600_000L,
                history(theNightsPrices(), List.of()));
        assertEquals(ReferencePrice.From.INDEX_PRINT, r.from());
        assertEquals(7176.24, r.close().spx(), 1e-9,
                "a wrong reference would freeze the whole night's rows against a derived price");
    }

    @Test
    @DisplayName("a page restarted at 03:00 then carries the close and its source in words")
    void theRestartedPageCarriesIt() {
        ReadableBoardRenderer r = new ReadableBoardRenderer(() -> null, () -> STARTED_AT_0300);
        r.seedReference(ReferencePrice.resolve(CLOSE,
                history(theNightsPrices(), List.of(barJson(CLOSE - 60_000L, 717_624L)))));
        // one overnight price so the page has something live to show
        r.onPriceJson(priceJson(STARTED_AT_0300 - 5_000L, 7159.95, "ES_BASIS_DERIVED",
                "LIVE_ES_BASIS"), STARTED_AT_0300 - 5_000L);

        String page = r.render();
        assertFalse(page.contains("NO REFERENCE PRICE YET"),
                "this is the whole defect: a gateway restarted overnight must not say this");
        assertTrue(page.contains("7176.24"), "the recovered close is on the page");
        assertTrue(page.contains("REFERENCE — previous cash close from the SPX index's own last "
                + "print"), "and the page says WHICH source it came from: " + page);
    }

    @Test
    @DisplayName("with the index print gone, the close is a bar's SPX-equivalent, labelled as such")
    void theBarIsTheFallbackAndSaysSo() {
        List<String> onlyOvernight = new ArrayList<>(theNightsPrices());
        onlyOvernight.removeIf(j -> j.contains("NATIVE_SPX_INDEX"));

        ReferencePrice.Resolved r = ReferencePrice.resolve(CLOSE, history(onlyOvernight,
                List.of(barJson(CLOSE - 120_000L, 717_500L), barJson(CLOSE - 60_000L, 717_624L))));

        assertEquals(ReferencePrice.From.FOOTPRINT_BAR, r.from());
        assertEquals(7176.24, r.close().spx(), 1e-9, "the LAST complete bar of the session");
        assertTrue(r.label().contains("SPX-equivalent"),
                "an index print and a futures bar's SPX-equivalent are different facts and the "
                        + "page must not let them read alike: " + r.label());
        assertEquals("FOOTPRINT_BAR_SPX_EQ", r.close().source());
    }

    @Test
    @DisplayName("the session high and low come from the session's own bars")
    void theRangeComesFromTheBars() {
        ReferencePrice.Resolved r = ReferencePrice.resolve(CLOSE,
                history(theNightsPrices(),
                        List.of(barJson(CLOSE - 180_000L, 717_000L),
                                barJson(CLOSE - 120_000L, 718_000L),
                                barJson(CLOSE - 60_000L, 717_624L))));
        assertEquals(7180.30, r.high(), 1e-9);
        assertEquals(7169.60, r.low(), 1e-9);
    }

    @Test
    @DisplayName("with neither source the absence is stated with its reason, never guessed")
    void neitherSourceIsAnExplainedAbsence() {
        ReferencePrice.Resolved r = ReferencePrice.resolve(CLOSE, history(List.of(), List.of()));
        assertEquals(ReferencePrice.From.NONE, r.from());
        assertNull(r.close());
        assertTrue(r.reason().contains("not recoverable"), r.reason());
        assertTrue(r.label().startsWith("NO REFERENCE PRICE"), r.label());

        ReferencePrice.Resolved none = ReferencePrice.resolve(CLOSE, null);
        assertEquals(ReferencePrice.From.NONE, none.from());
        assertTrue(none.reason().contains("cannot read price history back"), none.reason());
    }

    @Test
    @DisplayName("a live index print always wins over a recovered one")
    void aLivePrintBeatsARecoveredOne() {
        OvernightSource s = new OvernightSource();
        s.onPrice(OvernightDecoders.price(
                priceJson(CLOSE, 7176.24, "NATIVE_SPX_INDEX", "LIVE_INDEX"), CLOSE));
        s.seedPreviousClose(new ReadablePage.Price(CLOSE - 86_400_000L, 7000.00,
                "FOOTPRINT_BAR_SPX_EQ", "CASH_SESSION_LAST_COMPLETE_BAR"));
        assertEquals(7176.24, s.previousCashClose().spx(), 1e-9,
                "a recovered close must never displace one actually observed on the topic, or a "
                        + "restart would pin the page to it for the rest of the session");
    }
}

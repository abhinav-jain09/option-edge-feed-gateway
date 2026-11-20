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

    /**
     * History as the gateway reads it: a real time WINDOW, so a test cannot accidentally hand the
     * resolver records it would never have seen. Every fixture record carries its own instant and
     * only those inside the asked-for window are returned.
     */
    private static ReferencePrice.History history(List<String> prices, List<String> bars) {
        return new ReferencePrice.History() {
            @Override
            public List<String> pricesBetween(long fromMs, long toMs, int limit) {
                return within(prices, fromMs, toMs, ReferencePriceTest::priceAt);
            }

            @Override
            public List<String> barsBetween(long fromMs, long toMs) {
                return within(bars, fromMs, toMs, ReferencePriceTest::barAt);
            }
        };
    }

    private static List<String> within(List<String> all, long fromMs, long toMs,
            java.util.function.ToLongFunction<String> at) {
        List<String> out = new ArrayList<>();
        for (String j : all) {
            long t = at.applyAsLong(j);
            if (t >= fromMs && t <= toMs) {
                out.add(j);
            }
        }
        return out;
    }

    private static long priceAt(String json) {
        java.util.regex.Matcher m =
                java.util.regex.Pattern.compile("\"spotEventTimeNs\":(\\d+)").matcher(json);
        return m.find() ? Long.parseLong(m.group(1)) / 1_000_000L : 0L;
    }

    private static long barAt(String json) {
        java.util.regex.Matcher m =
                java.util.regex.Pattern.compile("\"barStartMs\":(\\d+)").matcher(json);
        return m.find() ? Long.parseLong(m.group(1)) : 0L;
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
        // C-98: resolved AT the instant the test's own name says, rather than one second after the
        // print. The rule is "the last index print before a silence of three hours or more", so
        // the instant matters: this fixture's index print is followed by hours of basis derivation,
        // and it is a close because the INDEX has been quiet across them. Asking one second after
        // the print asks about a session that has not ended, and the answer there is the bar
        // fallback -- which `aCloseBeforeTheSilenceHasElapsedUsesTheBar` below now covers.
        ReferencePrice.Resolved r = ReferencePrice.resolve(STARTED_AT_0300,
                history(theNightsPrices(), List.of(barJson(CLOSE - 60_000L, 717_624L))));

        assertEquals(ReferencePrice.From.INDEX_PRINT, r.from(), r.reason());
        assertEquals(7176.24, r.close().spx(), 1e-9,
                "the previous cash close, recovered rather than waited for");
        assertTrue(r.label().contains("SPX index's own last print"), r.label());
    }

    @Test
    @DisplayName("THE LIVE FAILURE: asked at 22:39, it finds the 16:04 index print, not an 18:15 bar")
    void theIndexPrintIsReachedFromTheMiddleOfTheNight() {
        // Exactly what the deployed page got wrong on 2026-11-20. The index print is at 16:04:03
        // New York; the price topic then carries hours of ES_BASIS_DERIVED on top of it (measured
        // live: the newest 4,003 records contain no index print at all); and the bars run on until
        // they stall at 18:16. Asked at 22:39, the first version seeked to 96 hours back, read 500
        // records FORWARD, saw nothing useful, and fell through to an 18:15 bar -- labelling an
        // evening bar "a cash-session bar" and putting the close 4.8 points out.
        long close = CLOSE + 4L * 60_000L + 3_000L;          // 16:04:03 New York
        long askedAt = CLOSE + 6L * 3_600_000L + 39L * 60_000L;   // 22:39 New York

        List<String> prices = new ArrayList<>();
        prices.add(priceJson(close, 7176.24, "NATIVE_SPX_INDEX", "LIVE_INDEX"));
        // six and a half hours of basis-derived prints after it, every ten seconds
        for (long t = close + 10_000L; t <= askedAt; t += 10_000L) {
            prices.add(priceJson(t, 7160.00, "ES_BASIS_DERIVED", "LIVE_ES_BASIS"));
        }
        List<String> bars = List.of(
                barJson(CLOSE - 60_000L, 717_624L),                       // inside the session
                barJson(CLOSE + 2L * 3_600_000L + 15L * 60_000L, 717_145L));  // 18:15, outside it

        ReferencePrice.Resolved r = ReferencePrice.resolve(askedAt, history(prices, bars));

        assertEquals(ReferencePrice.From.INDEX_PRINT, r.from(),
                "the index print is there and must be found: " + r.reason());
        assertEquals(7176.24, r.close().spx(), 1e-9, "and it is the close, not an evening bar");
        assertEquals(close, r.close().epochMs(), "stamped 16:04:03, not 18:15");
        assertTrue(prices.size() > 2000,
                "the fixture really does bury the print under hours of basis prints: "
                        + prices.size() + " records");
    }

    @Test
    @DisplayName("an evening bar is never called a cash-session bar")
    void onlyACashSessionBarCanBeTheFallback() {
        // No index print at all, and the only bars are after the close. The label would be false,
        // so there is no resolution rather than a mislabelled one.
        long askedAt = CLOSE + 6L * 3_600_000L;
        ReferencePrice.Resolved r = ReferencePrice.resolve(askedAt, history(List.of(),
                List.of(barJson(CLOSE + 2L * 3_600_000L + 15L * 60_000L, 717_145L))));
        assertEquals(ReferencePrice.From.NONE, r.from(),
                "an 18:15 bar is not the cash session's and must not be presented as it: "
                        + r.reason());
        assertNull(r.close());

        // Add one bar INSIDE the session and it becomes the fallback.
        ReferencePrice.Resolved ok = ReferencePrice.resolve(askedAt, history(List.of(),
                List.of(barJson(CLOSE - 60_000L, 717_624L),
                        barJson(CLOSE + 2L * 3_600_000L + 15L * 60_000L, 717_145L))));
        assertEquals(ReferencePrice.From.FOOTPRINT_BAR, ok.from());
        assertEquals(7176.24, ok.close().spx(), 1e-9,
                "the last bar INSIDE the session, not the newest bar in the window");
    }

    @Test
    @DisplayName("the cash session is 09:30 to 16:00 New York, and the boundaries are checked")
    void theCashSessionBoundsAreTheRealOnes() {
        long open = java.time.LocalDate.of(2026, 11, 19).atTime(9, 30)
                .atZone(java.time.ZoneId.of("America/New_York")).toInstant().toEpochMilli();
        assertTrue(ReferencePrice.inCashSession(open), "09:30 is in");
        assertTrue(ReferencePrice.inCashSession(open + 6L * 3_600_000L + 29L * 60_000L),
                "15:59 is in");
        assertFalse(ReferencePrice.inCashSession(open - 60_000L), "09:29 is not");
        assertFalse(ReferencePrice.inCashSession(open + 6L * 3_600_000L + 30L * 60_000L),
                "16:00 itself is not: the close ENDS the session");
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
        // C-98: resolved at the instant the page is being rendered at, which is what a restart
        // actually does. Resolving at CLOSE asked about a session that had not ended three hours
        // earlier, so the answer was the bar fallback and the page carried the bar's label -- true,
        // but not what this test is about.
        r.seedReference(ReferencePrice.resolve(STARTED_AT_0300,
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

    /**
     * <b>C-133 changed this, and the trade-off is stated rather than left to be found.</b>
     *
     * <p>C-98 made a live index print become the close once the index went quiet for three hours,
     * so a recovered close could be displaced without a restart. C-133 anchors a close that was
     * resolved from history: a gap in the feed can be manufactured — by a stall, or by a read that
     * stopped short — and must not rewrite the owner's previous close in the middle of his night.
     * So a seeded close now HOLDS. What that gives up is the live roll at 16:00, which this class
     * never had either; it needs a cash-close boundary applied to the running service, and that is
     * the open item in the Discussion tab.
     *
     * <p>The second half keeps the silence rule under test where it still governs: a source that
     * has resolved nothing is not anchored, and there the quiet still promotes the print.
     */
    @Test
    @DisplayName("C-133: a seeded close is ANCHORED and the quiet cannot displace it")
    void aSeededCloseIsAnchoredAgainstTheQuiet() {
        OvernightSource s = new OvernightSource();
        s.onPrice(OvernightDecoders.price(
                priceJson(CLOSE, 7176.24, "NATIVE_SPX_INDEX", "LIVE_INDEX"), CLOSE));
        s.seedPreviousClose(new ReadablePage.Price(CLOSE - 86_400_000L, 7000.00,
                "FOOTPRINT_BAR_SPX_EQ", "CASH_SESSION_LAST_COMPLETE_BAR"));
        assertEquals(7000.00, s.previousCashClose().spx(), 1e-9,
                "while the index is still printing, no session has ended: the recovered close is "
                        + "the only completed one, and a newer print is not a close for being newer");

        for (long t = CLOSE + 600_000L; t <= CLOSE + 4L * 3_600_000L; t += 600_000L) {
            s.onPrice(OvernightDecoders.price(
                    priceJson(t, 7160.00, "ES_BASIS_DERIVED", "LIVE_ES_BASIS"), t));
        }
        assertEquals(7000.00, s.previousCashClose().spx(), 1e-9,
                "the resolved close is anchored, so four hours of quiet do not rewrite it");
    }

    @Test
    @DisplayName("with nothing seeded the silence rule still governs: the quiet promotes the print")
    void withNothingSeededTheQuietStillPromotesThePrint() {
        OvernightSource s = new OvernightSource();
        s.onPrice(OvernightDecoders.price(
                priceJson(CLOSE, 7176.24, "NATIVE_SPX_INDEX", "LIVE_INDEX"), CLOSE));
        assertNull(s.previousCashClose(), "no session has ended yet, so there is no close");
        for (long t = CLOSE + 600_000L; t <= CLOSE + 4L * 3_600_000L; t += 600_000L) {
            s.onPrice(OvernightDecoders.price(
                    priceJson(t, 7160.00, "ES_BASIS_DERIVED", "LIVE_ES_BASIS"), t));
        }
        assertEquals(7176.24, s.previousCashClose().spx(), 1e-9,
                "unanchored, three hours of index quiet still end the session and promote the "
                        + "print -- the live rule is unchanged where nothing was resolved");
    }

    @Test
    @DisplayName("before the silence has elapsed the close is the bar fallback, labelled as such")
    void aCloseBeforeTheSilenceHasElapsedUsesTheBar() {
        // The consequence of C-98 that is worth a test of its own: in the window between the
        // index's last print and three hours later, no session has ended by the rule, so the
        // resolver falls to the cash-session bar and SAYS it did. The owner is never shown an
        // index figure labelled as a close before that close exists.
        ReferencePrice.Resolved r = ReferencePrice.resolve(CLOSE,
                history(theNightsPrices(), List.of(barJson(CLOSE - 60_000L, 717_624L))));
        assertEquals(ReferencePrice.From.FOOTPRINT_BAR, r.from(), r.reason());
        assertTrue(r.label().contains("footprint bar"), r.label());
    }
}

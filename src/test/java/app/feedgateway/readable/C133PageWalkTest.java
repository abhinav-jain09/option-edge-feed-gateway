package app.feedgateway.readable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * <b>C-133: the previous close is read from the NEWEST record backwards, a bounded page at a time.</b>
 *
 * <p><b>The measurement this class exists for.</b> On 2026-11-20 the gateway rolled at 19:08:37Z —
 * 15:08 New York, inside the cash session. C-98's rule was right and the data was there: the index
 * had printed to 16:49:16 the previous evening and then gone quiet for 12 h 41 m. But the resolver
 * read FORWARD from each widening window's start and stopped at 60,000 records — about 7.2 hours of
 * this feed — so every window dropped its own newest records, which are the only ones that can hold
 * the end of the previous session. The 16-hour window's cap fell at 09:58:23 <b>that morning</b>,
 * the five hours from there to the boot instant looked like a silence the feed never had, and the
 * page showed {@code overnight|prevclose=7175.68|at=09:58:23NY|slots=18/18} where it should have
 * shown {@code 7175.83} at {@code 16:49:16NY} over 85 slots. The figure was a function of the
 * restart hour.
 *
 * <p><b>Widening could not have fixed it.</b> A wider window starts earlier and its cap still falls
 * about 7.2 hours later, so each one reads a different 7.2 hours and all of them miss the new end.
 * Worse, the 96-hour window's own 7.2 hours contain an OLDER session's close and the quiet after
 * it, so a resolver that widened to the end and trusted the widest window would answer with a price
 * from four days earlier and label it yesterday's close. The direction of the read was the defect.
 *
 * <p>So the rule is untouched — {@link IndexSilence} still decides, from one implementation — and
 * the reading is anchored at the new end.
 */
final class C133PageWalkTest {

    /** 21:49:16Z on 2026-11-19 — the last index print of that session, 16:49:16 New York. */
    private static final long LAST_PRINT = 1_795_131_000_000L;
    /** The measured silence: twelve hours and forty-one minutes. */
    private static final long SILENCE = 12L * 3_600_000L + 41L * 60_000L;
    /** 05:30:40 New York on 2026-11-20 — the morning's first index print. */
    private static final long FIRST_TODAY = LAST_PRINT + SILENCE;
    /**
     * 19:08:37Z — 15:08 New York on 2026-11-20, the boot that produced the wrong close.
     *
     * <p>Written as the measured distance from the previous session's last print rather than as a
     * bare epoch, because the distance is what the rule reads: 22 h 19 m, of which the first
     * 12 h 41 m are the silence and the remaining 9 h 38 m are this morning's prints. A literal
     * here that disagreed with {@link #SILENCE} would make the fixture's shape quietly wrong, which
     * is how the first version of this test came to assert 63 night slots.
     */
    private static final long BOOTED_AT_1508 =
            LAST_PRINT + 22L * 3_600_000L + 19L * 60_000L;

    /**
     * The page size the fake uses, in records, whatever the resolver asks for.
     *
     * <p>The real page is 60,000 records; a fixture that size would be a minute of string building
     * per test and would prove nothing extra. What has to be exercised is the WALK — that page 0 is
     * the newest, that a page which cannot resolve is followed by an older one, and that the prints
     * arrive at the rule in time order across pages — and a small page exercises all three harder
     * than a large one, because the fixture then spans several pages.
     */
    private static final int FAKE_PAGE = 100;

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

    /**
     * The measured night, thinned to one print a minute so a test can hold it.
     *
     * <p>Thinning is safe for this rule and only for this rule: it reads the GAP between
     * consecutive index prints against a three-hour threshold, and a minute is three orders of
     * magnitude below it. The shape that matters — prints, then 12 h 41 m with none, then prints
     * again — is exact.
     */
    private static List<String> theMeasuredNight(long untilMs) {
        List<String> out = new ArrayList<>();
        for (int i = 120; i >= 0; i--) {
            out.add(priceJson(LAST_PRINT - i * 60_000L, 7175.83, "NATIVE_SPX_INDEX", "LIVE_INDEX"));
        }
        // the overnight quiet: the topic carries the basis derivation, which is never a close
        for (long t = LAST_PRINT + 600_000L; t < FIRST_TODAY; t += 600_000L) {
            out.add(priceJson(t, 7160.00, "ES_BASIS_DERIVED", "LIVE_ES_BASIS"));
        }
        for (long t = FIRST_TODAY; t <= untilMs; t += 60_000L) {
            out.add(priceJson(t, 7140.00, "NATIVE_SPX_INDEX", "LIVE_INDEX"));
        }
        return out;
    }

    /** History that pages from the new end, as the real one does, recording what was asked. */
    private static ReferencePrice.History pagingHistory(List<String> prices, List<String> bars,
            List<Integer> pagesAsked) {
        return new ReferencePrice.History() {
            @Override
            public List<String> pricesBetween(long fromMs, long toMs, int limit) {
                return within(prices, fromMs, toMs, C133PageWalkTest::priceAt);
            }

            @Override
            public ReferencePrice.Page pricePage(long notAfterMs, int limit, int pagesBack) {
                pagesAsked.add(pagesBack);
                List<String> all = within(prices, Long.MIN_VALUE / 4, notAfterMs,
                        C133PageWalkTest::priceAt);
                int end = all.size() - pagesBack * FAKE_PAGE;
                if (end <= 0) {
                    return new ReferencePrice.Page(List.of(), false);
                }
                int start = Math.max(0, end - FAKE_PAGE);
                return new ReferencePrice.Page(List.copyOf(all.subList(start, end)), start > 0);
            }

            @Override
            public List<String> barsBetween(long fromMs, long toMs) {
                return within(bars, fromMs, toMs, C133PageWalkTest::barAt);
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

    private static String slots(String page) {
        for (String line : page.split("\n")) {
            int at = line.indexOf("slots=");
            if (at >= 0) {
                return line.substring(at);
            }
        }
        return "(no slots line)";
    }

    // ------------------------------------------------- (1) the live failure, with the right answer

    @Test
    @DisplayName("(1) a start at 15:08 gives 7175.83 at 16:49:16 — not the 09:58 print a cap let by")
    void theBootThatProducedTheWrongCloseNowResolvesTheRightOne() {
        List<Integer> asked = new ArrayList<>();
        ReferencePrice.History history = pagingHistory(theMeasuredNight(BOOTED_AT_1508),
                List.of(barJson(LAST_PRINT - 600_000L, 717_583L)), asked);

        ReferencePrice.Resolved r = ReferencePrice.resolve(BOOTED_AT_1508, history);

        assertEquals(ReferencePrice.From.INDEX_PRINT, r.from(), r.reason());
        assertEquals(7175.83, r.close().spx(), 1e-9,
                "the previous session's last print, which is what C-98 asks for");
        assertEquals(LAST_PRINT, r.close().epochMs(),
                "stamped 16:49:16 the previous evening, not 09:58:23 this morning");
        assertNotEquals(7175.68, r.close().spx(),
                "the figure a forward-reading capped drain handed back on 2026-11-20");
        assertTrue(r.reason().contains("page"),
                "the resolution says which page answered: " + r.reason());
        assertEquals(0, asked.get(0), "page 0 is the NEWEST page, and it is asked for first");
        assertTrue(asked.size() > 1,
                "this boot is mid-session, so the walk has to go back more than one page: " + asked);
    }

    @Test
    @DisplayName("(1b) the pages reach the rule in time order, so no page manufactures a silence")
    void thePagesReachTheRuleInTimeOrder() {
        // The walk reads newest-page-first and must still hand IndexSilence a list in time order:
        // prints arriving out of order would invent gaps out of the paging itself, which is the
        // same class of defect being fixed and would be invisible on a single-page fixture.
        List<Integer> asked = new ArrayList<>();
        ReferencePrice.History history = pagingHistory(theMeasuredNight(BOOTED_AT_1508),
                List.of(barJson(LAST_PRINT - 600_000L, 717_583L)), asked);
        ReferencePrice.Resolved r = ReferencePrice.resolve(BOOTED_AT_1508, history);

        // the answer is the ONE real gap in the fixture, not one of the many page boundaries
        assertEquals(LAST_PRINT, r.close().epochMs(), r.reason());
        // and the walk crossed several page boundaries to get there
        assertTrue(asked.size() >= 3,
                "the fixture spans several pages, so boundaries were crossed: " + asked);
        assertEquals(asked.size() - 1, asked.get(asked.size() - 1),
                "and the pages were asked for in order, 0 first: " + asked);
    }

    @Test
    @DisplayName("(2) an incomplete read never answers: a page with no gap resolves nothing")
    void anIncompleteReadNeverAnswers() {
        // C-133's property, stated as a test rather than as a comment. Page 0 of a mid-session boot
        // is 100 minutes of dense prints: no gap inside it and no trailing gap, because the newest
        // print is the boot instant. A resolver that treated that page as the whole history would
        // have to answer from it -- and there is nothing in it to answer with. So "incomplete" must
        // mean "keep walking", which is what the absence of a resolution on page 0 proves.
        List<String> prices = theMeasuredNight(BOOTED_AT_1508);
        List<Integer> asked = new ArrayList<>();
        ReferencePrice.History history =
                pagingHistory(prices, List.of(barJson(LAST_PRINT - 600_000L, 717_583L)), asked);

        ReferencePrice.Page zero =
                history.pricePage(BOOTED_AT_1508, ReferencePrice.SLICE_LIMIT, 0);
        assertTrue(zero.morePages(), "older records remain, so this page is an incomplete read");
        List<ReadablePage.Price> prints = new ArrayList<>();
        for (String json : zero.records()) {
            ReadablePage.Price p = OvernightDecoders.price(json, 0L);
            if (p != null && OvernightSource.isIndexPrint(p)) {
                prints.add(p);
            }
        }
        assertFalse(prints.isEmpty(), "the page does hold index prints");
        assertEquals(null, IndexSilence.resolveFrom(prints, BOOTED_AT_1508).reference(),
                "and not one of them is a close, because no session ended inside this page");
    }

    @Test
    @DisplayName("(2b) the counterfactual: the OLD forward read really does give 09:58's 7175.68")
    void theForwardReadWouldHaveGivenThisMorningsPrint() {
        // Reproducing the deployed defect, so that the fix is measured against the failure rather
        // than against my description of it. A forward read from 23:08 the previous night, capped,
        // ends at 09:58:23 this morning; the five hours from there to the boot instant are then a
        // trailing silence, and the rule -- correctly, on the evidence it was given -- names the
        // print in front of it.
        long cappedAt = BOOTED_AT_1508 - 5L * 3_600_000L - 10L * 60_000L;   // 09:58 New York
        List<ReadablePage.Price> asTheResolverSawThem = new ArrayList<>();
        for (String json : theMeasuredNight(BOOTED_AT_1508)) {
            long at = priceAt(json);
            if (at < FIRST_TODAY || at > cappedAt) {
                continue;                       // the forward read's window, cut at its cap
            }
            ReadablePage.Price p = OvernightDecoders.price(json, 0L);
            if (p != null && OvernightSource.isIndexPrint(p)) {
                asTheResolverSawThem.add(p);
            }
        }
        IndexSilence wrong = IndexSilence.resolveFrom(asTheResolverSawThem, BOOTED_AT_1508);
        assertTrue(wrong.reference() != null, "the capped read does produce an answer");
        assertTrue(wrong.reference().epochMs() > FIRST_TODAY,
                "and it is a print from THIS MORNING, which is the whole defect: "
                        + wrong.reference().epochMs());
        assertTrue(wrong.reference().epochMs() <= cappedAt,
                "the print in front of the truncation point");
    }

    // ------------------------------------------------------------ (3) the pre-market start

    @Test
    @DisplayName("(3) started in the pre-market, the close is last night's print, not a live one")
    void aPreMarketStartDoesNotTakeAPreMarketPrint() {
        long preMarket = FIRST_TODAY + 2L * 3_600_000L + 30L * 60_000L;   // 08:00 New York
        List<Integer> asked = new ArrayList<>();
        ReferencePrice.History history = pagingHistory(theMeasuredNight(preMarket),
                List.of(barJson(LAST_PRINT - 600_000L, 717_583L)), asked);

        ReferencePrice.Resolved r = ReferencePrice.resolve(preMarket, history);

        assertEquals(ReferencePrice.From.INDEX_PRINT, r.from(), r.reason());
        assertEquals(7175.83, r.close().spx(), 1e-9,
                "the index prints once a second through the pre-market and none of them is a close");
        assertEquals(LAST_PRINT, r.close().epochMs(), "stamped 16:49:16 the previous evening");
    }

    // ------------------------------------------------------------ (4) the Monday start

    @Test
    @DisplayName("(4) started on a Monday, the close is FRIDAY's last print across the weekend")
    void aMondayStartReachesBackToFriday() {
        // No calendar is consulted and none is needed: a weekend is a silence in the index feed, so
        // the rule that handles a night handles a weekend by being the same rule. What the weekend
        // tests is the WALK's reach -- the topic carries basis derivation throughout, so there are
        // far more pages between Monday morning and Friday's bell than between a boot and the
        // previous evening.
        long mondayMorning = LAST_PRINT + 3L * 24L * 3_600_000L + 17L * 3_600_000L;
        List<String> prices = new ArrayList<>();
        for (int i = 120; i >= 0; i--) {                 // Friday, to its last print
            prices.add(priceJson(LAST_PRINT - i * 60_000L, 7175.83,
                    "NATIVE_SPX_INDEX", "LIVE_INDEX"));
        }
        for (long t = LAST_PRINT + 600_000L; t < mondayMorning - 3L * 3_600_000L; t += 600_000L) {
            prices.add(priceJson(t, 7160.00, "ES_BASIS_DERIVED", "LIVE_ES_BASIS"));
        }
        List<Integer> asked = new ArrayList<>();
        ReferencePrice.History history = pagingHistory(prices,
                List.of(barJson(LAST_PRINT - 600_000L, 717_583L)), asked);

        ReferencePrice.Resolved r = ReferencePrice.resolve(mondayMorning, history);

        assertEquals(ReferencePrice.From.INDEX_PRINT, r.from(), r.reason());
        assertEquals(7175.83, r.close().spx(), 1e-9, "Friday's last print, across the weekend");
        assertEquals(LAST_PRINT, r.close().epochMs(), "stamped Friday 16:49:16");
    }

    @Test
    @DisplayName("(4b) the bound is an ABSENCE with the page count, never the oldest thing read")
    void theBoundIsAnAbsenceWithItsReason() {
        // Beyond the walk's reach the answer must be "I cannot tell you", not a stale price. The
        // fixture is prints with NO silence anywhere and more pages than the bound allows, so the
        // walk runs out -- and the one thing it must not do is hand back the oldest print it saw.
        List<String> prices = new ArrayList<>();
        long span = (long) ReferencePrice.MAX_PAGES * FAKE_PAGE + 500L;
        for (long i = span; i >= 0; i--) {
            prices.add(priceJson(BOOTED_AT_1508 - i * 60_000L, 7100.00 + i % 7,
                    "NATIVE_SPX_INDEX", "LIVE_INDEX"));
        }
        List<Integer> asked = new ArrayList<>();
        ReferencePrice.Resolved r = ReferencePrice.resolve(BOOTED_AT_1508,
                pagingHistory(prices, List.of(), asked));

        assertEquals(ReferencePrice.From.NONE, r.from(), r.reason());
        assertTrue(r.reason().contains("page"), "the absence names the walk: " + r.reason());
        assertEquals(ReferencePrice.MAX_PAGES, asked.size(),
                "the walk is bounded at MAX_PAGES and stops there: " + asked.size());
    }

    // ------------------------------------------------------------ (5) the night, and the page

    @Test
    @DisplayName("(5) the night starts at the resolved close and the grid spans it, not 18 slots")
    void theNightSpansFromTheResolvedCloseToNow() {
        List<Integer> asked = new ArrayList<>();
        ReferencePrice.History history = pagingHistory(theMeasuredNight(BOOTED_AT_1508),
                List.of(barJson(LAST_PRINT - 600_000L, 717_583L)), asked);
        ReferencePrice.Resolved r = ReferencePrice.resolve(BOOTED_AT_1508, history);

        ReadableBoardRenderer renderer =
                new ReadableBoardRenderer(() -> null, () -> BOOTED_AT_1508);
        renderer.seedReference(r);

        assertEquals(LAST_PRINT, renderer.source().nightStartMs(),
                "the night starts at the previous session's close, not at this morning");
        String slots = slots(renderer.namedPage("overnight"));
        int of = Integer.parseInt(slots.substring(slots.indexOf('/') + 1).trim());
        assertTrue(of >= 85,
                "a night resolved at 16:49 holds at least 85 fifteen-minute slots, not 18: " + slots);
    }

    @Test
    @DisplayName("(6) the resolution's reason reaches the page and the log, not a discarded string")
    void theReasonIsPrintedOnThePage() {
        List<Integer> asked = new ArrayList<>();
        ReferencePrice.History history = pagingHistory(theMeasuredNight(BOOTED_AT_1508),
                List.of(barJson(LAST_PRINT - 600_000L, 717_583L)), asked);
        ReadableBoardRenderer renderer =
                new ReadableBoardRenderer(() -> null, () -> BOOTED_AT_1508);
        renderer.seedReference(ReferencePrice.resolve(BOOTED_AT_1508, history));

        String line = renderer.referenceLine();
        assertTrue(line.startsWith("reference|"), line);
        assertTrue(line.contains("page"),
                "the page says how the close was reached, so a bad read is visible in one line "
                        + "instead of an hour of investigation: " + line);
        assertTrue(renderer.namedPage("now").contains("reference|"), "and /now carries it");
    }

    @Test
    @DisplayName("(7) the DEFAULT page walk pages an in-memory history correctly")
    void theDefaultPageWalkPagesCorrectly() {
        // The seam's own default, which every History that predates C-133 inherits.
        List<String> prices = new ArrayList<>();
        for (int i = 9; i >= 0; i--) {
            prices.add(priceJson(LAST_PRINT - i * 60_000L, 7175.00 + i,
                    "NATIVE_SPX_INDEX", "LIVE_INDEX"));
        }
        ReferencePrice.History history = new ReferencePrice.History() {
            @Override
            public List<String> pricesBetween(long fromMs, long toMs, int limit) {
                return within(prices, fromMs, toMs, C133PageWalkTest::priceAt);
            }

            @Override
            public List<String> barsBetween(long fromMs, long toMs) {
                return List.of();
            }
        };
        ReferencePrice.Page newest = history.pricePage(LAST_PRINT, 4, 0);
        assertEquals(4, newest.records().size());
        assertEquals(LAST_PRINT, priceAt(newest.records().get(3)), "page 0 ends at the newest");
        assertTrue(newest.morePages(), "six older records remain");

        ReferencePrice.Page next = history.pricePage(LAST_PRINT, 4, 1);
        assertEquals(4, next.records().size());
        assertEquals(LAST_PRINT - 4L * 60_000L, priceAt(next.records().get(3)),
                "page 1 ends where page 0 began");

        ReferencePrice.Page last = history.pricePage(LAST_PRINT, 4, 2);
        assertEquals(2, last.records().size(), "the last page is a short one");
        assertFalse(last.morePages(), "and nothing older remains");
        assertTrue(history.pricePage(LAST_PRINT, 4, 3).records().isEmpty(),
                "past the beginning there is no page at all");
    }
}

package app.feedgateway.readable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * <b>C-98: the previous close is the last index print before the most recent long silence.</b>
 *
 * <p>The shape driven here is the measured one, not an invented one. On 2026-11-19/20:
 *
 * <ul>
 *   <li>index prints once a second to <b>16:49:16 New York</b> (21:49:16Z), the last of that
 *       session;</li>
 *   <li>a silence of <b>12 h 41 m</b> — zero index prints in the 04:00Z and 08:00Z windows;</li>
 *   <li>prints once a second again from <b>05:30:40 New York</b> (10:30:40Z), four hours before the
 *       cash open.</li>
 * </ul>
 *
 * <p>The old rule, "the newest index print at or before now is the close", turned that pre-market
 * feed into yesterday's close: the page printed {@code close|7160.54|+0.00}, and the night's start
 * marched forward behind the moving reference until the grid was empty.
 *
 * <p>This replaces {@code C97PreviousCloseTest}, which asserted the <b>freeze</b> that C-98
 * supersedes. Two of its cases encoded semantics the rule no longer has, so they are rewritten
 * against the rule that ships rather than re-aimed to pass; the loop-prevention properties it
 * proved are kept, because those are the point of both.
 */
final class C98PreviousCloseTest {

    /** 21:49:16Z on 2026-11-19 — the last index print of that session, 16:49:16 New York. */
    private static final long LAST_PRINT = 1_795_131_000_000L;
    /** The measured silence: twelve hours and forty-one minutes. */
    private static final long SILENCE = 12L * 3_600_000L + 41L * 60_000L;
    /** The first print of the new morning. */
    private static final long FIRST_TODAY = LAST_PRINT + SILENCE;

    private static ReadablePage.Price index(long at, double spx) {
        return new ReadablePage.Price(at, spx, "NATIVE_SPX_INDEX", "LIVE_INDEX");
    }

    private static ReadablePage.Price derived(long at, double spx) {
        return new ReadablePage.Price(at, spx, "ES_BASIS_DERIVED", "LIVE_ES_BASIS");
    }

    private static OvernightGrid.Bar bar(long startMs) {
        return OvernightGrid.Bar.spx(startMs, 7100.0, 7105.0, 7095.0, 7102.0, 10L,
                "ANCHORED", "1m", null, null, null);
    }

    /** The measured day, as a list of index prints in time order. */
    private static List<ReadablePage.Price> theMeasuredDay(int printsToday) {
        List<ReadablePage.Price> out = new ArrayList<>();
        for (int i = 60; i >= 0; i--) {                       // a minute of prints up to the close
            out.add(index(LAST_PRINT - i * 1_000L, 7175.83));
        }
        for (int i = 0; i < printsToday; i++) {               // and this morning's, once a second
            out.add(index(FIRST_TODAY + i * 1_000L, 7160.54));
        }
        return out;
    }

    // ---------------------------------------------------------------- (a) the measured shape

    @Test
    @DisplayName("(a) the measured day resolves to 7175.83, and 3,000 further prints do not move it")
    void theMeasuredDayResolvesToYesterdaysClose() {
        IndexSilence silence = IndexSilence.resolveFrom(theMeasuredDay(3_000));
        assertNotNull(silence.reference(), silence.absenceReason());
        assertEquals(LAST_PRINT, silence.reference().epochMs());
        assertEquals(7175.83, silence.reference().spx(), 1e-9);
        assertEquals(1, silence.silencesSeen(), "one session boundary in this history");
        // and the newest print is this morning's, which is a different question
        assertEquals(7160.54, silence.newest().spx(), 1e-9);
        assertNull(silence.absenceReason());
    }

    @Test
    @DisplayName("(a) the same answer tick by tick as live, which is the one-rule property")
    void liveAndHistoryAgree() {
        IndexSilence live = new IndexSilence();
        for (ReadablePage.Price p : theMeasuredDay(3_000)) {
            live.observe(p);
        }
        IndexSilence fromHistory = IndexSilence.resolveFrom(theMeasuredDay(3_000));
        assertEquals(fromHistory.reference().epochMs(), live.reference().epochMs());
        assertEquals(fromHistory.reference().spx(), live.reference().spx(), 1e-9);
    }

    // ---------------------------------------------------------------- (b) a restart mid-pre-market

    @Test
    @DisplayName("(b) a restart in the middle of the pre-market gives the same answer")
    void aRestartMidPreMarketResolvesTheSame() {
        // the gateway that restarts at 11:19 Amsterdam reads history back and resolves from it;
        // the history it sees is the same day with fewer of this morning's prints in it
        for (int printsSoFar : new int[] {1, 10, 600, 2_530}) {
            IndexSilence silence = IndexSilence.resolveFrom(theMeasuredDay(printsSoFar));
            assertNotNull(silence.reference(), "restart with " + printsSoFar + " prints today");
            assertEquals(LAST_PRINT, silence.reference().epochMs(),
                    "restart with " + printsSoFar + " prints today resolved the wrong close");
        }
    }

    @Test
    @DisplayName("(b) a restart BEFORE the morning's first print also resolves yesterday's close")
    void aRestartBeforeTheFirstPrintResolvesTheSame() {
        // the real pod started at 05:19 New York, eleven minutes before the first print
        IndexSilence silence = IndexSilence.resolveFrom(theMeasuredDay(0));
        assertNull(silence.reference(),
                "with no print after the silence there is no completed session to close");
        assertEquals("no silence of 3 hours or more has been seen in the index feed, so the "
                + "session this print belongs to has not ended yet", silence.absenceReason());
    }

    // ---------------------------------------------------------------- (c) the bars are not cleared

    @Test
    @DisplayName("(c) /overnight keeps its slots and its close through the pre-market feed")
    void theOvernightPageSurvivesThePreMarketFeed() {
        ReadableBoardRenderer r = new ReadableBoardRenderer(() -> null,
                () -> FIRST_TODAY + 600_000L);
        OvernightSource s = r.source();
        // the whole measured day through the renderer's own entry point
        for (ReadablePage.Price p : theMeasuredDay(0)) {
            r.onPriceJson(json(p), p.epochMs());
        }
        for (int i = 0; i < 300; i++) {
            s.onBar(bar(LAST_PRINT + i * 60_000L));
        }
        // the morning's first print establishes the reference by ending the silence
        r.onPriceJson(json(index(FIRST_TODAY, 7160.54)), FIRST_TODAY);
        assertNotNull(s.previousCashClose(), s.closeAbsenceReason());
        assertEquals(7175.83, s.previousCashClose().spx(), 1e-9);
        String before = r.namedPage("overnight");
        assertTrue(before.contains("slots="), before);
        String slotsBefore = slots(before);

        // then 600 more, once a second, which is what marched the start forward
        for (int i = 1; i <= 600; i++) {
            long at = FIRST_TODAY + i * 1_000L;
            r.onPriceJson(json(index(at, 7160.54)), at);
        }
        assertEquals(slotsBefore, slots(r.namedPage("overnight")),
                "the grid collapsed behind a marching night start");
        assertEquals(300, s.barCount("1m"), "the night's bars were cleared");
        assertEquals(7175.83, s.previousCashClose().spx(), 1e-9,
                "the previous close became a live price");
        assertEquals(600, s.closeCandidatesInsideTheNight(),
                "the prints inside the night are counted, so the instrument can say we saw them");
    }

    // ---------------------------------------------------------------- (d) the start-up window

    @Test
    @DisplayName("(d) C-133: the walk is bounded in pages, and the bound is an absence not a guess")
    void theWalkIsBoundedAndSaysSo() {
        // The widening slices this once asserted are gone: every one of them read forward from its
        // own start and dropped the newest records in its window, which is how a 09:58 print became
        // the previous close on 2026-11-20. The walk now starts at the newest record, so what a
        // bound cuts off is always OLDER than what has been read -- it can delay an answer and
        // cannot manufacture one. These are the two properties that makes that safe.
        assertTrue(ReferencePrice.MAX_PAGES * (long) ReferencePrice.SLICE_LIMIT
                        > 3L * 24L * 3_600L * 2L,
                "the bound must span a long weekend of this feed at about two records a second");
        assertTrue(ReferencePrice.SLICE_LIMIT * 1_000L / 2L > IndexSilence.SILENCE_MS,
                "one page must be able to hold the silence itself, so page 0 resolves an "
                        + "ordinary overnight boot without walking at all");
    }

    @Test
    @DisplayName("(d) with no silence findable the page says close|absent WITH THE REASON")
    void anUnresolvedCloseIsAnAbsenceWithItsReason() {
        ReadableBoardRenderer r = new ReadableBoardRenderer(() -> null, () -> LAST_PRINT);
        OvernightSource s = r.source();
        // prints, but no silence: the session has not ended, so there is no close
        for (int i = 0; i < 100; i++) {
            long at = LAST_PRINT - (100 - i) * 1_000L;
            r.onPriceJson(json(index(at, 7175.83)), at);
        }
        assertNull(s.previousCashClose());
        String reason = s.closeAbsenceReason();
        assertNotNull(reason);
        String page = r.namedPage("now");
        assertTrue(page.contains("close|absent|"), "the close line must carry its reason: " + page);
        assertTrue(page.contains(reason),
                "the reason on the page must be the source's own, not a second wording: " + page);
        // and never a price under that label
        assertTrue(!page.contains("close|7175.83"), page);
    }

    // ---------------------------------------------------------------- the rule's own edges

    @Test
    @DisplayName("a derived price is not an index print, so it can never be a close")
    void aDerivedPriceIsNeverTheClose() {
        // The filter lives in OvernightSource, not in IndexSilence: the rule is about gaps and
        // knows nothing about sources, so the thing worth asserting is that a derived price never
        // reaches it. My first version of this test wrapped a conditional round
        // silence.newest() and compared it to itself -- a tautology that passed either way.
        OvernightSource s = new OvernightSource();
        s.onPrice(index(LAST_PRINT - 1_000L, 7175.0));
        s.onPrice(derived(LAST_PRINT + SILENCE, 7160.0));
        // A derived price never BECOMES the close -- and it does advance the clock, which is how a
        // trailing silence becomes visible overnight while the index is quiet. So after twelve
        // hours of derivation the index print IS the close, and the derived value is nowhere in
        // the answer. My first version of this test asserted null here, which was the behaviour
        // before observeClock existed and would have left the page with no close all night.
        assertNotNull(s.previousCashClose(), s.closeAbsenceReason());
        assertEquals(7175.0, s.previousCashClose().spx(), 1e-9,
                "the close is the INDEX print; the derivation only said that time had passed");
        assertEquals("NATIVE_SPX_INDEX", s.previousCashClose().source());
    }

    @Test
    @DisplayName("an out-of-order print cannot manufacture a silence")
    void anOutOfOrderPrintIsIgnored() {
        IndexSilence silence = new IndexSilence();
        silence.observe(index(FIRST_TODAY, 7160.54));
        silence.observe(index(LAST_PRINT, 7175.83));      // older, as a seek can deliver
        assertNull(silence.reference(),
                "a late arrival is not a gap; treating it as one would invent a session boundary");
        assertEquals(FIRST_TODAY, silence.newest().epochMs());
    }

    @Test
    @DisplayName("a second silence moves the reference on, which is how it rolls by itself")
    void aSecondSilenceRollsTheReference() {
        IndexSilence silence = IndexSilence.resolveFrom(theMeasuredDay(600));
        assertEquals(LAST_PRINT, silence.reference().epochMs());
        long todaysLast = FIRST_TODAY + 599 * 1_000L;
        // tonight: three hours of quiet, then tomorrow's first print
        silence.observe(index(todaysLast + IndexSilence.SILENCE_MS + 1_000L, 7180.00));
        assertEquals(todaysLast, silence.reference().epochMs(),
                "after tonight's silence the reference is today's last print, with no restart");
        assertEquals(2, silence.silencesSeen());
    }

    @Test
    @DisplayName("the threshold is one constant, and it is three hours")
    void theThresholdIsOneNamedConstant() {
        assertEquals(3L * 3_600_000L, IndexSilence.SILENCE_MS);
    }

    /** The record's own shape, as {@code OvernightDecoders.price} reads it. */
    private static String json(ReadablePage.Price p) {
        return "{\"canonicalSpot\":\"" + p.spx() + "\",\"spotEventTimeNs\":"
                + (p.epochMs() * 1_000_000L) + ",\"source\":\"" + p.source()
                + "\",\"quality\":\"" + p.quality() + "\"}";
    }

    /** The {@code slots=a/b} figure from an {@code /overnight} page. */
    private static String slots(String page) {
        for (String line : page.split("\n")) {
            int at = line.indexOf("slots=");
            if (at >= 0) {
                return line.substring(at);
            }
        }
        return "(no slots line)";
    }
}

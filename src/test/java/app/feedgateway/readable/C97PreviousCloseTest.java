package app.feedgateway.readable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * <b>C-97 item 4: a live index print must never become the previous close, and must never wipe the
 * night.</b>
 *
 * <p>These drive the loop that emptied the owner's page, not a sentence on it. On 2026-11-20 the
 * pre-market `underlying.spx.price` feed published {@code NATIVE_SPX_INDEX} / {@code LIVE_INDEX}
 * once a second; each print replaced the previous-close reference, the renderer read the moved
 * reference as a session roll, and {@code nightStartsAt} cleared the history behind it. The page
 * went from 26 of 51 slots to 0 of 1 in fourteen minutes and printed {@code close|7160.54|+0.00}.
 */
final class C97PreviousCloseTest {

    private static final long CLOSE_MS = 1_795_000_000_000L;      // the previous session's close

    private static ReadablePage.Price indexPrint(long at, double spx) {
        return new ReadablePage.Price(at, spx, "NATIVE_SPX_INDEX", "LIVE_INDEX");
    }

    private static ReadablePage.Price derived(long at, double spx) {
        return new ReadablePage.Price(at, spx, "ES_BASIS_DERIVED", "LIVE_ES_BASIS");
    }

    private static OvernightGrid.Bar bar(long startMs) {
        return new OvernightGrid.Bar(startMs, 7100.0, 7105.0, 7095.0, 7102.0, 10L,
                "ANCHORED", "1m", null, null, null);
    }

    private static OvernightSource nightStartedAtTheClose() {
        OvernightSource s = new OvernightSource();
        s.seedPreviousClose(indexPrint(CLOSE_MS, 7175.83));
        s.nightStartsAt(CLOSE_MS);
        return s;
    }

    @Test
    @DisplayName("a pre-market index print does NOT become the previous close")
    void aLiveIndexPrintIsNotTheClose() {
        OvernightSource s = nightStartedAtTheClose();
        for (int i = 1; i <= 600; i++) {
            s.onPrice(indexPrint(CLOSE_MS + i * 1_000L, 7160.54));   // one a second, as measured
        }
        ReadablePage.Price reference = s.previousCashClose();
        assertNotNull(reference);
        assertEquals(CLOSE_MS, reference.epochMs(),
                "the reference moved: this is the loop that printed close|7160.54|+0.00");
        assertEquals(7175.83, reference.spx(), 1e-9);
        assertEquals(600, s.closeCandidatesInsideTheNight(),
                "every one of them counted, so the instrument can say we are ignoring them");
        // and the newest price is still the newest price -- only the CLOSE is frozen
        assertEquals(7160.54, s.lastPrice().spx(), 1e-9);
    }

    @Test
    @DisplayName("the print AT the night's start IS the close, so the boundary is inclusive")
    void theBoundaryBelongsToTheClose() {
        OvernightSource s = new OvernightSource();
        s.nightStartsAt(CLOSE_MS);
        s.onPrice(indexPrint(CLOSE_MS, 7175.83));
        assertNotNull(s.previousCashClose(), "an index print at the start is the close itself");
        assertEquals(CLOSE_MS, s.previousCashClose().epochMs());
        assertEquals(0, s.closeCandidatesInsideTheNight());
    }

    /**
     * <b>THE DAMAGE, from the page's own side.</b>
     *
     * <p>My first version of this test called {@code source.onPrice} directly and asserted the bar
     * count. It passed on the unfixed code, because {@code onPrice} alone never clears anything —
     * the clearing happens when the RENDERER reads a moved reference and calls
     * {@code nightStartsAt}. A test that cannot fail is worse than no test: it reported coverage of
     * the one thing that broke. This drives the real path and asserts what the owner sees, the slot
     * count on {@code /overnight}.
     */
    @Test
    @DisplayName("THE DAMAGE: /overnight keeps its slots through a pre-market index feed")
    void theOvernightPageKeepsItsSlots() {
        ReadableBoardRenderer r = new ReadableBoardRenderer(() -> null,
                () -> CLOSE_MS + 300 * 60_000L);
        OvernightSource s = r.source();
        r.onPriceJson(json(CLOSE_MS, 7175.83, "NATIVE_SPX_INDEX", "LIVE_INDEX"), CLOSE_MS);
        for (int i = 0; i < 300; i++) {
            s.onBar(bar(CLOSE_MS + i * 60_000L));
        }
        String before = r.namedPage("overnight");
        assertTrue(before.contains("slots="), before);
        String slotsBefore = slots(before);

        // the pre-market index feed, once a second, through the renderer -- the real path
        for (int i = 1; i <= 600; i++) {
            long at = CLOSE_MS + 300 * 60_000L + i * 1_000L;
            r.onPriceJson(json(at, 7160.54, "NATIVE_SPX_INDEX", "LIVE_INDEX"), at);
        }
        String after = r.namedPage("overnight");
        assertEquals(slotsBefore, slots(after),
                "the grid collapsed behind a marching night start: " + after);
        assertEquals(300, s.barCount("1m"), "the night's bars were cleared");
        assertTrue(after.contains("prevclose=7175.83"),
                "the previous close became a live price: " + after);
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

    @Test
    @DisplayName("the renderer never moves the night start FORWARD on a price")
    void theRendererDoesNotMoveTheStartForward() {
        // The renderer's own guard, driven through the public price entry point. The reference is
        // frozen in the source as well; this proves the second half of the loop independently, so
        // relaxing either one alone cannot restore it.
        ReadableBoardRenderer r = new ReadableBoardRenderer(() -> null, () -> CLOSE_MS);
        OvernightSource source = r.source();          // the renderer owns it; this is its seam
        source.seedPreviousClose(indexPrint(CLOSE_MS, 7175.83));
        for (int i = 0; i < 200; i++) {
            source.onBar(bar(CLOSE_MS + i * 60_000L));
        }
        // the first price settles the start at the close
        r.onPriceJson(json(CLOSE_MS, 7175.83, "NATIVE_SPX_INDEX", "LIVE_INDEX"), CLOSE_MS);
        long settled = source.nightStartMs();
        assertEquals(CLOSE_MS, settled, "the start is the close");
        // then the pre-market feed, which used to march it forward once a second
        for (int i = 1; i <= 300; i++) {
            long at = CLOSE_MS + 200 * 60_000L + i * 1_000L;
            r.onPriceJson(json(at, 7160.54, "NATIVE_SPX_INDEX", "LIVE_INDEX"), at);
        }
        assertEquals(settled, source.nightStartMs(), "the night start marched forward");
        assertEquals(200, source.barCount("1m"), "the night was cleared behind the moving start");
    }

    @Test
    @DisplayName("an ES-basis derived price is not a close candidate at all, as before")
    void aDerivedPriceIsNeverTheClose() {
        OvernightSource s = nightStartedAtTheClose();
        s.onPrice(derived(CLOSE_MS - 60_000L, 7140.0));   // even BEFORE the night's start
        assertEquals(7175.83, s.previousCashClose().spx(), 1e-9);
        assertEquals(0, s.closeCandidatesInsideTheNight(),
                "a derived price is excluded by isIndexPrint, not by the night boundary");
    }

    /**
     * The record's own shape, as {@code OvernightDecoders.price} reads it: {@code canonicalSpot}
     * (or {@code price}) as TEXT, the instant in {@code spotEventTimeNs}, plus source and quality.
     * Built to the decoder's contract rather than to a guess, so a change there fails this test
     * instead of silently decoding to null and passing.
     */
    private static String json(long epochMs, double spx, String source, String quality) {
        return "{\"canonicalSpot\":\"" + spx + "\",\"spotEventTimeNs\":"
                + (epochMs * 1_000_000L) + ",\"source\":\"" + source
                + "\",\"quality\":\"" + quality + "\"}";
    }
}

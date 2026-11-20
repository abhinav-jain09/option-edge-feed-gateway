package app.feedgateway.readable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The gamma block, built from the map record's real shape (read off
 * {@code options.spx.whatif.map} on 2026-11-19: {@code strikeCents} with a nested {@code board}
 * carrying {@code netGex}, {@code gammaSign} and {@code holdOrAccelerate}).
 */
class BoardLinesTest {

    private static final long CYCLE = 1_795_124_682_036L;

    /** Spot 7177.56 and three strikes, so the grid around it has real rows and real gaps. */
    private static String board(String... strikeJson) {
        return "{\"spot\":7177.56,\"expiry\":\"20261119\",\"minutesToExpiry\":1.0,"
                + "\"strikes\":[" + String.join(",", strikeJson) + "]}";
    }

    private static String strike(long cents, double netGex, String reading) {
        return "{\"strikeCents\":" + cents + ",\"dayVolume\":0,"
                + "\"board\":{\"netGex\":" + netGex + ",\"absGex\":" + Math.abs(netGex)
                + ",\"gammaSign\":\"" + (netGex < 0 ? "NEGATIVE" : "POSITIVE") + "\","
                + "\"holdOrAccelerate\":\"" + reading + "\",\"excluded\":false,"
                + "\"excludedReason\":null}}";
    }

    @Test
    @DisplayName("every grid strike gets a line, and a missing one says 'no row'")
    void aMissingStrikeIsPrintedNotSkipped() {
        // Only 7175 and 7180 are on the record. Every other 5-point strike within 100 points must
        // still appear, as "no row" -- a skipped strike is an invisible hole in a ladder.
        ReadablePage.Board b = BoardLines.from(
                board(strike(717_500L, -8.31e9, "ACCELERATE"),
                      strike(718_000L, 1.2e9, "HOLD")), CYCLE);
        assertNotNull(b);
        assertEquals(2, b.rowsPresent());
        assertEquals(41, b.rowsExpected(),
                "the 5-point grid a hundred points either side of spot is 41 strikes");
        assertEquals(41, b.lines().size(), "and every one of them is a line");
        long noRow = b.lines().stream().filter(l -> l.contains("|norow")).count();
        assertEquals(39, noRow, "the 39 the gamma service did not publish are printed as no row");
    }

    @Test
    @DisplayName("the reading and the net gamma are the record's own, not recomputed")
    void theRecordsOwnFiguresAreFormatted() {
        ReadablePage.Board b = BoardLines.from(
                board(strike(717_500L, -8.31e9, "ACCELERATE")), CYCLE);
        String row = b.lines().stream().filter(l -> l.contains("7175")).findFirst().orElseThrow();
        assertEquals("7175|-8.31B|A", row,
                "C-66's compact row: strike|netgamma|read, no padding, the reading as its code");
    }

    @Test
    @DisplayName("spot's own strike is marked, so the owner finds price without counting")
    void spotIsMarked() {
        ReadablePage.Board b = BoardLines.from(board(strike(717_500L, -1e9, "HOLD")), CYCLE);
        long marked = b.lines().stream().filter(l -> l.endsWith("|spot")).count();
        assertEquals(1, marked, "exactly one line carries the spot marker");
        String row = b.lines().stream().filter(l -> l.endsWith("|spot")).findFirst().orElseThrow();
        assertTrue(row.startsWith("7180|"),
                "spot 7177.56 rounds to the 7180 grid line: " + row);
    }

    @Test
    @DisplayName("an excluded strike says WHY, instead of looking like a quiet one")
    void anExcludedStrikeNamesItsReason() {
        String excluded = "{\"strikeCents\":717500,\"dayVolume\":0,\"board\":{\"netGex\":0.0,"
                + "\"absGex\":0.0,\"gammaSign\":\"POSITIVE\",\"holdOrAccelerate\":\"NEUTRAL\","
                + "\"excluded\":true,\"excludedReason\":\"under GAMMA_DIVIDE_FLOOR\"}}";
        ReadablePage.Board b = BoardLines.from(board(excluded), CYCLE);
        String row = b.lines().stream().filter(l -> l.startsWith("7175|")).findFirst().orElseThrow();
        assertTrue(row.contains("excluded=under GAMMA_DIVIDE_FLOOR"), row);
    }

    @Test
    @DisplayName("the block carries its OWN cycle time, so it is never read as now")
    void theBlockCarriesItsOwnCycleTime() {
        ReadablePage.Board b = BoardLines.from(board(strike(717_500L, -1e9, "HOLD")), CYCLE);
        assertEquals(CYCLE, b.cycleMs());
        assertEquals(7177.56, b.spot(), 1e-9);
        // and it survives into the rendered page's heading
        String page = Pages.board(CYCLE + 60_000L,
                new ReadablePage.Price(CYCLE, 7177.0, "ES_BASIS_DERIVED", "LIVE_ES_BASIS"), b);
        assertTrue(page.contains("date=2026-11-19"), "the date is on the page");
        assertTrue(page.contains("rows=1/41"), "and the completeness figure, compactly");
    }

    @Test
    @DisplayName("no board, a board with no spot, and garbage are all absent rather than empty")
    void absentRatherThanEmpty() {
        assertNull(BoardLines.from(null, CYCLE));
        assertNull(BoardLines.from("", CYCLE));
        assertNull(BoardLines.from("not json", CYCLE));
        assertNull(BoardLines.from("[1,2]", CYCLE));
        assertNull(BoardLines.from("{\"strikes\":[]}", CYCLE), "no spot means no board");
        assertNull(BoardLines.from("{\"spot\":7177.56}", CYCLE), "no strikes array means no board");
    }

    @Test
    @DisplayName("an empty strike list still prints the whole grid as no row")
    void anEmptyBoardIsAllNoRow() {
        ReadablePage.Board b = BoardLines.from(board(), CYCLE);
        assertNotNull(b);
        assertEquals(0, b.rowsPresent());
        assertEquals(41, b.lines().size());
        assertEquals(41, b.lines().stream().filter(l -> l.contains("|norow")).count(),
                "a board that published nothing is 41 printed absences, not a blank section");
    }

    @Test
    @DisplayName("net gamma abbreviates by size and always carries its sign")
    void theUnitsAreTheOwners() {
        assertEquals("-8.31B", BoardLines.money(-8.31e9));
        assertEquals("+1.20B", BoardLines.money(1.2e9));
        assertEquals("-861M", BoardLines.money(-861e6));
        assertEquals("+1M", BoardLines.money(1_014_368.75),
                "just over a million abbreviates; the record's exact figure is not the point here");
        assertEquals("+999999", BoardLines.money(999_999.0), "under a million prints in full");
        assertEquals("+0", BoardLines.money(0.0));
    }
}

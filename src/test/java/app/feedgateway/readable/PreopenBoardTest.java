package app.feedgateway.readable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The pre-open board, driven by records of the shape actually published.
 *
 * <p>The fixtures are the real thing: a record copied field for field off
 * {@code options.databento.gex.strike.preopen} on 2026-11-19, and the spot that board carried at
 * 06:43 New York. A fixture invented to suit the reader would prove nothing about the feed.
 */
class PreopenBoardTest {

    /** The spot the 06:43:02 New York board of 2026-11-19 actually carried. */
    private static final double SPOT = 7418.4638671875;
    private static final String UPDATED = "2026-11-19T11:43:02.906Z";
    private static final long UPDATED_MS = 1_795_088_582_906L;

    private static String record(String expiry, double strike, Double netGex, String sign,
                                 double spot, String updatedAt) {
        StringBuilder s = new StringBuilder("{\"symbol\":\"SPX\",\"expiry\":\"" + expiry + "\"");
        s.append(",\"strike\":").append(strike);
        if (netGex != null) {
            s.append(",\"netGex\":").append(netGex);
        }
        if (sign != null) {
            s.append(",\"gammaSign\":\"").append(sign).append('"');
        }
        s.append(",\"canonicalSpot\":").append(spot);
        s.append(",\"updatedAt\":\"").append(updatedAt).append('"');
        s.append(",\"staleAfterMs\":300000}");
        return s.toString();
    }

    /** The whole 41-row grid around the measured spot, as the feed in fact supplied it. */
    private static List<String> wholeBoard() {
        List<String> out = new ArrayList<>();
        double centre = Math.round(SPOT / 5.0) * 5.0;
        for (int i = -20; i <= 20; i++) {
            out.add(record("20261119", centre + i * 5.0, 2.6703191198E8, "POSITIVE", SPOT, UPDATED));
        }
        return out;
    }

    @Test
    @DisplayName("the measured 06:43 board is complete: 41 of the owner's 41 rows")
    void theMeasuredBoardIsComplete() {
        ReadablePage.Board b = PreopenBoard.from(wholeBoard(), UPDATED_MS + 1_000L);
        assertNotNull(b, "the records of a real pre-open board must make a board");
        assertEquals(41, b.rowsExpected(), "the owner's grid is 41 rows");
        assertEquals(41, b.rowsPresent(),
                "the pre-open feed covered every grid row at 06:43 and the board must say so");
        assertEquals(SPOT, b.spot(), 1e-9, "spot is the record's own canonicalSpot, untranslated");
        assertEquals(UPDATED_MS, b.cycleMs(), "the board is stamped with the record's own instant");
    }

    @Test
    @DisplayName("no what-if reading is invented: every read cell is empty")
    void theReadCellIsEmptyBecauseThereIsNoReading() {
        ReadablePage.Board b = PreopenBoard.from(wholeBoard(), UPDATED_MS + 1_000L);
        for (String line : b.lines()) {
            String[] cells = line.split("\\|", -1);
            assertTrue(cells.length >= 3, "a row has at least strike, net gamma and read: " + line);
            assertEquals("", cells[2],
                    "the pre-open record carries no hold-or-accelerate reading, so the read cell "
                            + "must be empty rather than filled with a guess: " + line);
        }
        assertFalse(String.join("\n", b.lines()).contains("POSITIVE"),
                "gammaSign must never be promoted into the reading column");
    }

    @Test
    @DisplayName("the board names itself, and never as the map board")
    void theBoardNamesItsOwnSource() {
        ReadablePage.Board b = PreopenBoard.from(wholeBoard(), UPDATED_MS + 1_000L);
        assertEquals(PreopenBoard.SOURCE, b.source());
        assertFalse(b.source().equals(BoardLines.SOURCE),
                "the pre-open board must not be able to read as the map service's board");
        String page = Pages.board(UPDATED_MS + 1_000L, null, b);
        assertTrue(page.contains("|src=" + PreopenBoard.SOURCE),
                "the page states which board this is: " + page.lines().findFirst().orElse(""));
    }

    @Test
    @DisplayName("a board with no source cannot be constructed at all")
    void aBoardMustNameItsSource() {
        assertThrows(IllegalArgumentException.class,
                () -> new ReadablePage.Board(UPDATED_MS, SPOT, 1, 41, "  ", null, null,
                        List.of("7420||")));
    }

    @Test
    @DisplayName("an ES pre-open record is not the owner's board and is refused")
    void anEsRecordIsNotThisBoard() {
        String es = record("20261119", 7405.0, 1.0E8, "POSITIVE", SPOT, UPDATED)
                .replace("\"symbol\":\"SPX\"", "\"symbol\":\"ES\"");
        assertNull(PreopenBoard.from(List.of(es), UPDATED_MS),
                "an ES record must not build the SPX board");
        assertNull(PreopenBoard.decode(es));
    }

    @Test
    @DisplayName("the earliest expiry is the board; a later expiry's rows are not mixed in")
    void theEarliestExpiryIsTheBoard() {
        List<String> records = new ArrayList<>(wholeBoard());
        // A later expiry at a strike the near board does not hold. If it leaked in, the row would
        // be present instead of "no row" and the count would be wrong.
        records.add(record("20261231", 9000.0, 9.9E9, "POSITIVE", SPOT, UPDATED));
        ReadablePage.Board b = PreopenBoard.from(records, UPDATED_MS + 1_000L);
        assertEquals(41, b.rowsPresent(), "a later expiry must not add a row to this board");
        assertFalse(String.join("\n", b.lines()).contains("9000"),
                "a later expiry's strike must not appear on this board");
    }

    @Test
    @DisplayName("the newest record for a strike wins, whatever order they arrive in")
    void theNewestRecordPerStrikeWins() {
        double centre = Math.round(SPOT / 5.0) * 5.0;
        List<String> records = List.of(
                record("20261119", centre, 1.0E8, "POSITIVE", SPOT, "2026-11-19T11:43:02.906Z"),
                record("20261119", centre, 5.0E8, "POSITIVE", SPOT, "2026-11-19T11:48:02.906Z"),
                record("20261119", centre, 2.0E8, "POSITIVE", SPOT, "2026-11-19T11:45:02.906Z"));
        ReadablePage.Board b = PreopenBoard.from(records, UPDATED_MS + 400_000L);
        String spotRow = b.lines().stream().filter(l -> l.endsWith("|spot")).findFirst().orElseThrow();
        assertTrue(spotRow.contains("+500M"),
                "the 11:48 value is the newest and must be the one shown: " + spotRow);
    }

    @Test
    @DisplayName("a strike the pre-open feed did not publish is printed, never skipped")
    void aGapIsPrinted() {
        double centre = Math.round(SPOT / 5.0) * 5.0;
        List<String> records = List.of(
                record("20261119", centre, 1.0E8, "POSITIVE", SPOT, UPDATED));
        ReadablePage.Board b = PreopenBoard.from(records, UPDATED_MS + 1_000L);
        assertEquals(41, b.rowsExpected(), "the grid is still the owner's 41 rows");
        assertEquals(1, b.rowsPresent());
        assertEquals(41, b.lines().size(), "every grid row is printed, present or not");
        assertEquals(40, b.lines().stream().filter(l -> l.contains("norow")).count(),
                "the forty strikes with no record are printed as no row");
    }

    @Test
    @DisplayName("a lapsed board prints every row and says so in its source")
    void aLapsedBoardIsStillWholeAndSaysSo() {
        ReadablePage.Board b = PreopenBoard.from(wholeBoard(), UPDATED_MS + 25L * 60_000L);
        assertEquals(41, b.rowsPresent(),
                "withholding rows because the feed went quiet would turn a stale board into an "
                        + "empty one, which reads as no gamma rather than no news");
        // C-73 puts the lapse ON THE ROW, with its age, rather than on the board as a whole.
        long marked = b.lines().stream().filter(l -> l.contains("|stale25m")).count();
        assertEquals(41, marked,
                "every lapsed row carries its own stale mark and its age: " + b.lines().get(0));
        assertEquals(PreopenBoard.SOURCE, b.source(),
                "and the source still names the board rather than doubling as a staleness flag");
    }

    @Test
    @DisplayName("the producer disagreeing with itself about the sign is marked on the row")
    void aSignMismatchIsMarked() {
        double centre = Math.round(SPOT / 5.0) * 5.0;
        ReadablePage.Board b = PreopenBoard.from(List.of(
                record("20261119", centre, -3.0E8, "POSITIVE", SPOT, UPDATED)), UPDATED_MS + 1_000L);
        String spotRow = b.lines().stream().filter(l -> l.endsWith("|spot")).findFirst().orElseThrow();
        assertTrue(spotRow.contains("signmismatch=POSITIVE"),
                "a negative net gamma called POSITIVE must be visible: " + spotRow);

        ReadablePage.Board agreeing = PreopenBoard.from(List.of(
                record("20261119", centre, -3.0E8, "NEGATIVE", SPOT, UPDATED)), UPDATED_MS + 1_000L);
        assertFalse(String.join("\n", agreeing.lines()).contains("signmismatch"),
                "agreement adds nothing, because the figure already carries its sign");
    }

    @Test
    @DisplayName("malformed records are ignored, and nothing usable yields no board")
    void malformedRecordsDoNotBreakTheBoard() {
        assertNull(PreopenBoard.from(null, UPDATED_MS));
        assertNull(PreopenBoard.from(List.of(), UPDATED_MS));
        assertNull(PreopenBoard.from(List.of("", "   ", "not json", "{}", "[]"), UPDATED_MS));
        assertNull(PreopenBoard.decode("{\"symbol\":\"SPX\",\"expiry\":\"20261119\"}"),
                "a record with no strike, spot or instant cannot be a row");

        List<String> mixed = new ArrayList<>(wholeBoard());
        mixed.add("not json at all");
        assertEquals(41, PreopenBoard.from(mixed, UPDATED_MS + 1_000L).rowsPresent(),
                "one unreadable record must not cost the board its rows");
    }

    @Test
    @DisplayName("no basis is applied: spot and strikes pass through exactly as published")
    void nothingIsTranslated() {
        ReadablePage.Board b = PreopenBoard.from(wholeBoard(), UPDATED_MS + 1_000L);
        assertEquals(SPOT, b.spot(), 1e-9);
        assertTrue(b.lines().stream().anyMatch(l -> l.startsWith("7420|")),
                "the grid is centred on the published spot's own nearest 5-point strike");
    }
}

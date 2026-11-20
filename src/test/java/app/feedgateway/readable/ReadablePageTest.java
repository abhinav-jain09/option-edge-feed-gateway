package app.feedgateway.readable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** <b>C-61</b>: the page the service serves, asserted as the reviewer and the owner will read it. */
class ReadablePageTest {

    /** 2026-11-20 00:49:00 Amsterdam = 18:49:00 New York = 23:49:00Z. */
    private static final long NOW = 1_795_132_140_000L;
    private static final long FIFTEEN = 15 * 60_000L;
    /** The previous cash close: 2026-11-19 16:00 New York. */
    private static final long PREV_CLOSE_MS = 1_795_122_000_000L;

    private static ReadablePage.Price price(long atMs, double spx) {
        return new ReadablePage.Price(atMs, spx, "ES_BASIS_DERIVED", "LIVE_ES_BASIS");
    }

    private static ReadablePage.Slot filled(long startMs, double close) {
        return new ReadablePage.Slot(startMs, close - 2, close + 3, close - 4, close,
                12_345L, "ANCHORED");
    }

    private static ReadablePage.Slot blank(long startMs) {
        return new ReadablePage.Slot(startMs, null, null, null, null, null, null);
    }

    private static ReadablePage.Overnight overnight(List<ReadablePage.Slot> slots,
            Double high, Double low) {
        return new ReadablePage.Overnight(7176.24, PREV_CLOSE_MS, slots, high, low);
    }

    @Test
    @DisplayName("the first line is in the owner's order: price time, price, then the write time")
    void theFirstLineCarriesBothClocksThenThePriceThenTheWriteTime() {
        String page = ReadablePage.render(NOW, price(NOW - 30_000L, 7167.95), null, null, List.of());
        String first = page.lines().findFirst().orElseThrow();
        assertTrue(first.startsWith("Last price 18:48:30 New York / 00:48:30 Amsterdam: 7167.95"),
                "the price's own time comes first, in both zones: " + first);
        assertTrue(first.contains("Written 00:49:00 Amsterdam"),
                "and the write time comes last, so a current write time can never be mistaken for "
                        + "a current price: " + first);
        assertTrue(first.contains("ES_BASIS_DERIVED") && first.contains("LIVE_ES_BASIS"),
                "the producer's own words for where the price came from");
        assertFalse(first.startsWith("STALE"), "thirty seconds old is not stale");
    }

    @Test
    @DisplayName("STALE opens the line past 20 minutes and says since when; 20 exactly does not")
    void staleOpensTheLineOnlyPastTheBound() {
        String atBound = ReadablePage.render(NOW,
                price(NOW - ReadablePage.STALE_AFTER_SECONDS * 1000L, 7167.95), null, null, List.of());
        assertFalse(atBound.startsWith("STALE"),
                "exactly twenty minutes is not yet stale, so the bound is not off by one");

        String past = ReadablePage.render(NOW,
                price(NOW - (ReadablePage.STALE_AFTER_SECONDS + 60) * 1000L, 7167.95), null, null, List.of());
        assertTrue(past.startsWith("STALE — the last price is 21 minutes old"),
                "and past it the word is the first thing on the page: " + past.lines().findFirst());
        assertTrue(past.contains("unchanged since 18:28:00 New York"), "with since when");
    }

    @Test
    @DisplayName("a slot with no bars keeps its place, its timestamp and empty cells")
    void aBlankSlotIsARowAndNotAnOmission() {
        List<ReadablePage.Slot> slots = List.of(
                filled(PREV_CLOSE_MS, 7179.99),
                blank(PREV_CLOSE_MS + FIFTEEN),
                filled(PREV_CLOSE_MS + 2 * FIFTEEN, 7156.74));
        String page = ReadablePage.render(NOW, price(NOW, 7167.95),
                overnight(slots, 7181.74, 7148.74), null, List.of());

        assertTrue(page.contains("16:15"), "the blank slot's own timestamp is printed");
        assertTrue(page.contains("2 of 3 15-minute slots carry bars"),
                "and the count tells the owner how complete the night is");
        // the blank row is a row: its line has the timestamp and dashes, not a missing line
        String blankRow = page.lines()
                .filter(l -> l.contains("16:15"))
                .findFirst().orElseThrow();
        assertTrue(blankRow.contains("-"), "empty cells, not a dropped row: " + blankRow);
        assertFalse(blankRow.contains("7179.99"), "and it did not inherit its neighbour's prices");
    }

    @Test
    @DisplayName("the high and low are the ones passed in, because the TICK low beats every bar's")
    void theHighAndLowAreNotDerivedFromTheSlots() {
        // Measured on 2026-11-20: the tick low 7148.74 sat below every bar's low. A page that
        // recomputed the extremes from its own rows would have understated the night's range.
        List<ReadablePage.Slot> slots = List.of(filled(PREV_CLOSE_MS, 7179.99));
        String page = ReadablePage.render(NOW, price(NOW, 7167.95),
                overnight(slots, 7181.74, 7148.74), null, List.of());
        assertTrue(page.contains("High 7181.74, low 7148.74"),
                "the figures given, not the ones the rows imply");
        assertFalse(page.contains("low 7175.99"), "which is what deriving from the one row gives");
    }

    @Test
    @DisplayName("with no board the page says so and does NOT show yesterday's")
    void noBoardSaysNoBoard() {
        String page = ReadablePage.render(NOW, price(NOW, 7167.95), null, null, List.of());
        assertTrue(page.contains("No board yet"));
        assertTrue(page.contains("reads as today's"),
                "and it says WHY yesterday's is withheld, which is C-60 addendum (d)");
    }

    @Test
    @DisplayName("a board carries its own cycle time and date, and rows N of M")
    void aBoardNamesItsOwnCycleAndItsCompleteness() {
        ReadablePage.Board board = new ReadablePage.Board(
                1_795_120_362_000L, 7177.56, 31, 41, BoardLines.SOURCE,
                new ReadablePage.Extreme(7180.0, -8.31e9, 2.44),
                new ReadablePage.Extreme(7175.0, 4.10e9, 2.56),
                List.of("7180|-8.31B|A", "7175||norow"));
        String page = ReadablePage.render(NOW, price(NOW, 7167.95), null, board, List.of());
        assertTrue(page.contains("cycle 2026-11-19 15:32:42 New York"),
                "its own date AND time, so yesterday's board can never read as today's");
        assertTrue(page.contains("rows 31 of 41"), "the reviewer's own completeness figure");
        assertTrue(page.contains("7175||norow"), "a missing strike is printed, never skipped");
        assertTrue(page.contains("norow=gamma service published no row"),
                "and the page defines that token itself, so `norow` is not a word the owner has "
                        + "to infer from an empty cell");
        assertTrue(page.contains("source whatif-map"),
                "the board names which of the two boards this is");
    }

    @Test
    @DisplayName("the page is capped, the cut is NAMED, and the price survives it")
    void thePageIsCappedAndSaysSo() {
        List<String> many = new ArrayList<>();
        for (int i = 0; i < 4_000; i++) {
            many.add("7000  -1.00B  ACCELERATE  padding padding padding padding padding");
        }
        ReadablePage.Board huge = new ReadablePage.Board(NOW, 7177.56, 41, 41, BoardLines.SOURCE, null, null, many);
        String page = ReadablePage.render(NOW, price(NOW, 7167.95), null, huge, List.of());

        assertTrue(page.length() <= ReadablePage.MAX_CHARS,
                "C-61's bound is enforced, not hoped for: " + page.length());
        assertTrue(page.contains("[TRUNCATED"), "and the cut is named rather than silent");
        assertTrue(page.lines().findFirst().orElseThrow().contains("7167.95"),
                "the price survives the cut, because it is what the owner is reading");
    }

    @Test
    @DisplayName("no price at all is said plainly, not rendered as a zero")
    void noPriceIsSaidPlainly() {
        String page = ReadablePage.render(NOW, null, null, null, List.of());
        assertTrue(page.startsWith("NO PRICE"));
        assertFalse(page.contains("0.00"), "absence is not a zero price");
    }

    @Test
    @DisplayName("a price with no time is refused, because staleness could not be judged")
    void aPriceWithNoTimeIsRefused() {
        assertThrows(IllegalArgumentException.class,
                () -> new ReadablePage.Price(0L, 7167.95, "ES_BASIS_DERIVED", "LIVE_ES_BASIS"));
    }

    @Test
    @DisplayName("a slot's basis state is printed when it is not ANCHORED, never dropped")
    void anUnanchoredBasisIsNamedOnItsRow() {
        ReadablePage.Slot quarantined = new ReadablePage.Slot(
                PREV_CLOSE_MS, 7170.0, 7172.0, 7168.0, 7171.0, 500L, "QUARANTINED");
        String page = ReadablePage.render(NOW, price(NOW, 7167.95),
                overnight(List.of(quarantined), 7172.0, 7168.0), null, List.of());
        assertTrue(page.contains("QUARANTINED"),
                "C-60 item (4): print the row with the state named, do not drop it");
        assertTrue(page.contains("7171.00"), "and its prices are still there");
    }

    @Test
    @DisplayName("every time on the page is given in both New York and Amsterdam")
    void bothClocksEverywhere() {
        List<ReadablePage.Slot> slots = List.of(filled(PREV_CLOSE_MS, 7179.99));
        String page = ReadablePage.render(NOW, price(NOW, 7167.95),
                overnight(slots, 7181.74, 7148.74),
                new ReadablePage.Board(NOW, 7177.56, 41, 41, BoardLines.SOURCE,
                        null, null, List.of("7180|-8.31B|A")), List.of());
        assertTrue(page.contains("SLOT(NY)") && page.contains("SLOT(AMS)"),
                "the grid names both zones in its header");
        assertTrue(page.contains("16:00") && page.contains("22:00"),
                "and each slot carries both");
    }
}

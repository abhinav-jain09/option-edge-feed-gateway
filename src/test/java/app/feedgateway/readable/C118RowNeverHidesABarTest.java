package app.feedgateway.readable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * <b>C-118: a row never hides a bar that exists</b> &mdash; the reviewer's own replay, "nine bars
 * lacking the SPX fields between two that have them".
 *
 * <p><b>The defect, measured.</b> On 2026-11-20 nine consecutive 30-second rows on the owner's
 * Recent table were blank. They were not missing bars: they were exactly the nine bars whose
 * {@code spxEqCloseCents} and {@code spxEqBasisState} were absent, boundary-exact on both sides
 * (C-117). Each carried a real volume, a real buyer and seller count and a real delta, and the page
 * showed none of it. Two independent copies of the same mistake did it:
 * {@link OvernightDecoders#bar} returned null when the SPX prices were missing, so the bar never
 * reached the renderer; and {@code RecentRows.Row.filled()} was {@code price != null}, so it would
 * have blanked the row anyway.
 *
 * <p><b>What a row shows now.</b> Volume, bought, sold, delta and side always, because they are the
 * bar's own counts and need no translation. The three price cells carry the SPX-equivalent prices
 * where the platform translated the bar, and otherwise the bar's <b>own futures prices marked
 * {@code ES}</b>, with {@code noSPX} in the basis cell. <b>Nothing on this page translates</b>
 * (C-103): the futures cents are printed as the bar carried them, and the 19-point basis is not
 * applied, reversed or inferred anywhere here.
 */
final class C118RowNeverHidesABarTest {

    private static final java.time.ZoneId NY = java.time.ZoneId.of("America/New_York");

    /** The first slot of the run, 09:25:00 New York on 2026-11-20. */
    private static final long FIRST = 1_795_181_100_000L;

    /**
     * One bar as the platform publishes it, with or without the SPX-equivalent fields.
     *
     * <p>Built as JSON and decoded, rather than constructed as a record, because the decoder is
     * where the bar was being dropped. A test that handed {@code RecentRows} a ready-made bar would
     * have passed against the broken decoder.
     */
    private static String barJson(long startMs, long esCents, boolean translated, long volume,
                                  long buyers, long sellers, long delta) {
        StringBuilder o = new StringBuilder();
        o.append("{\"schemaVersion\":6,\"symbol\":\"ES.v.0\",\"timeframe\":\"30s\",")
                .append("\"widthMs\":30000,\"sessionDate\":\"2026-11-20\",\"observations\":{")
                .append("\"barStartMs\":").append(startMs)
                .append(",\"barEndMs\":").append(startMs + 30_000L)
                .append(",\"complete\":true,\"barQuality\":\"READABLE\"")
                .append(",\"openCents\":").append(esCents)
                .append(",\"highCents\":").append(esCents + 100)
                .append(",\"lowCents\":").append(esCents - 100)
                .append(",\"closeCents\":").append(esCents + 25);
        if (translated) {
            long spx = esCents - 1830;      // the day's measured basis, as the PRODUCER published it
            o.append(",\"spxEqOpenCents\":").append(spx)
                    .append(",\"spxEqHighCents\":").append(spx + 100)
                    .append(",\"spxEqLowCents\":").append(spx - 100)
                    .append(",\"spxEqCloseCents\":").append(spx + 25)
                    .append(",\"spxEqBasisState\":\"ANCHORED\"");
        }
        o.append(",\"volume\":").append(volume)
                .append(",\"buyerContracts\":").append(buyers)
                .append(",\"sellerContracts\":").append(sellers)
                .append(",\"delta\":").append(delta)
                .append("}}");
        return o.toString();
    }

    /**
     * The reviewer's shape: a translated bar, then nine untranslated, then a translated one.
     *
     * <p>Eleven consecutive 30-second slots, so the run has a boundary on each side and a blank
     * row anywhere in the middle is the defect rather than the edge of the window.
     */
    private static List<OvernightGrid.Bar> elevenBars() {
        List<OvernightGrid.Bar> bars = new ArrayList<>();
        for (int i = 0; i < 11; i++) {
            boolean translated = i == 0 || i == 10;
            OvernightGrid.Bar bar = OvernightDecoders.bar(barJson(
                    FIRST + i * 30_000L, 716_000L + i * 25L, translated,
                    100L + i, 60L + i, 40L, 20L - i));
            assertNotNull(bar, "bar " + i + " must decode: it exists on the topic");
            bars.add(bar);
        }
        return bars;
    }

    @Test
    @DisplayName("C-118: nine untranslated bars between two translated ones, and NOT ONE row is blank")
    void nineUntranslatedBarsAreAllOnThePage() {
        List<OvernightGrid.Bar> bars = elevenBars();
        long nowMs = FIRST + 10L * 30_000L;
        List<RecentRows.Row> rows = RecentRows.build(nowMs, 11, bars);
        assertEquals(11, rows.size());

        for (int i = 0; i < 11; i++) {
            RecentRows.Row r = rows.get(i);          // newest first
            String line = RecentRows.line(r, NY);
            assertTrue(r.barPresent(),
                    "row " + i + " must know its bar is here: " + line);
            assertFalse(line.contains("||||||||"),
                    "THE RULE: row " + i + " is a real bar and must not render as eight empty "
                            + "cells. This is the line the owner saw nine times: " + line);
            assertEquals(Long.valueOf(100L + (10 - i)), r.volume(),
                    "every row carries its own volume, translated or not: " + line);
            assertTrue(line.contains("|" + r.volume() + "|"),
                    "and the volume is PRINTED: " + line);
            assertEquals("B", r.dominant(), "and the side, from the bar's own delta: " + line);
        }
    }

    @Test
    @DisplayName("C-118: the nine print FUTURES prices marked ES, with noSPX in the basis cell")
    void theUntranslatedRowsPrintFuturesPricesMarked() {
        List<RecentRows.Row> rows = RecentRows.build(FIRST + 10L * 30_000L, 11, elevenBars());
        int marked = 0;
        for (RecentRows.Row r : rows) {
            String line = RecentRows.line(r, NY);
            if (r.barWithoutSpxPrice()) {
                marked++;
                assertTrue(line.contains("|ES7"),
                        "an untranslated row prints the bar's OWN futures price, marked, so it "
                                + "can never be read as an index price: " + line);
                assertTrue(line.endsWith("|" + RecentRows.NO_SPX),
                        "and the basis cell says why rather than leaving a blank that the page's "
                                + "own legend calls 'no data': " + line);
            } else {
                assertFalse(line.contains("ES"),
                        "a translated row prints index prices and nothing else: " + line);
                assertTrue(line.endsWith("|A"), "with its own anchored basis: " + line);
            }
        }
        assertEquals(9, marked, "exactly the nine bars the platform did not translate");
    }

    @Test
    @DisplayName("C-118: no basis is applied — the ES cell is the bar's own number, to the cent")
    void nothingIsTranslatedByThePage() {
        List<RecentRows.Row> rows = RecentRows.build(FIRST + 10L * 30_000L, 11, elevenBars());
        RecentRows.Row middle = rows.get(5);                      // one of the nine
        assertTrue(middle.barWithoutSpxPrice());
        // bar index 5: esCents 716125, so closeCents 716150 -> 7161.50 exactly as published
        assertEquals(7161.50, middle.futuresPrice(), 1e-9);
        assertTrue(RecentRows.line(middle, NY).contains("ES7161.50"),
                "the cell is the published futures close to the cent, with no basis applied, "
                        + "reversed or inferred: " + RecentRows.line(middle, NY));
    }

    @Test
    @DisplayName("C-118: a slot with NO bar is still the one blank row, and the grid line says so")
    void aSlotWithNoBarIsTheOnlyBlankRow() {
        List<OvernightGrid.Bar> bars = new ArrayList<>(elevenBars());
        bars.remove(5);                                 // one slot genuinely has no bar
        List<RecentRows.Row> rows = RecentRows.build(FIRST + 10L * 30_000L, 11, bars);

        int blank = 0;
        for (RecentRows.Row r : rows) {
            if (!r.barPresent()) {
                blank++;
                assertEquals("", RecentRows.line(r, NY).replaceAll("^[^|]*\\|", "")
                        .replace("|", ""), "a slot with no bar is empty cells, and that is the "
                        + "one case the legend's 'no data for that slot' was ever about");
            }
        }
        assertEquals(1, blank);

        String grid = Pages.gridStateLine(rows);
        assertEquals("grid|rows=11|missing=1|noSPX=8|complete=2\n", grid,
                "the stall line names the three states APART, counted off the rendered rows: "
                        + "collapsing any two of them is how nine real bars read as no data");
    }

    @Test
    @DisplayName("C-118: the night's 15-minute slot shows its volume when no bar was translated")
    void aSlotWhoseBarsWereNeverTranslatedStillShowsItsVolume() {
        // the same defect one grid up: Slot.filled() was close != null, so a slot of untranslated
        // bars printed as a slot that never had one, and the volume that did arrive was hidden.
        List<OvernightGrid.Bar> bars = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            bars.add(OvernightDecoders.bar(barJson(FIRST + i * 30_000L, 716_000L + i * 25L,
                    false, 100L, 60L, 40L, 20L)));
        }
        ReadablePage.Overnight night = OvernightGrid.build(FIRST + 120_000L,
                FIRST, 7100.0, bars, null, null);
        ReadablePage.Slot slot = night.slots().get(0);
        assertTrue(slot.filled(),
                "a bar reached this slot, so the slot is not one that never had a bar");
        assertFalse(slot.hasPrices(), "and it has no index price to print");
        assertEquals(Long.valueOf(400L), slot.volume(),
                "every bar's volume is in the slot: the count is the bar's own and needed no basis");
        assertEquals("noSPX", slot.basisState(),
                "and the basis column says why, rather than leaving a blank the legend calls "
                        + "'no data'");
    }

    /** A map record as the gateway caches it: two strikes, and a hold block when asked for. */
    private static String mapJson(boolean held) {
        String hold = held
                ? ",\"boardHold\":{\"heldFromCycleMs\":1795181100000,\"ageMs\":130000,"
                        + "\"reason\":\"no record has arrived on the gamma strike topic\","
                        + "\"strikesSoFar\":17}"
                : "";
        return "{\"spot\":7100.0,\"strikes\":["
                + "{\"strikeCents\":710000,\"board\":{\"netGex\":1.0e9,"
                + "\"holdOrAccelerate\":\"HOLD\"}},"
                + "{\"strikeCents\":710500,\"board\":{\"netGex\":-2.0e9,"
                + "\"holdOrAccelerate\":\"ACCELERATE\"}}]" + hold + "}";
    }

    @Test
    @DisplayName("C-118/R-B-9: board| carries the published strike count and the hold")
    void theBoardLineCarriesThePublishedCountAndTheHold() {
        ReadablePage.Board live = BoardLines.from(mapJson(false), FIRST);
        assertNotNull(live);
        assertEquals(2, live.published(),
                "the strikes the RECORD carried, which is a different question from how much of "
                        + "the owner's 41-row grid is full");
        assertEquals(null, live.hold(), "absent means live");

        ReadablePage.Board heldBoard = BoardLines.from(mapJson(true), FIRST);
        assertNotNull(heldBoard);
        assertNotNull(heldBoard.hold());
        assertEquals(130_000L, heldBoard.hold().ageMs());
        assertEquals(17, heldBoard.hold().strikesSoFar());

        String liveLine = firstLineStartingWith(Pages.board(FIRST, null, heldBoard), "board|");
        assertTrue(liveLine.contains("|published=2"), liveLine);
        assertTrue(liveLine.contains("|held|age="), liveLine);
        assertTrue(liveLine.contains("|sofar=17"), liveLine);
        assertTrue(liveLine.contains("|reason=no record has arrived"), liveLine);
        assertTrue(liveLine.indexOf("|held") > liveLine.indexOf("cycle="),
                "on the SAME line as the cycle time: a reader who takes in the cycle and stops "
                        + "has read a fresh-looking board. " + liveLine);

        String plain = firstLineStartingWith(Pages.board(FIRST, null, live), "board|");
        assertFalse(plain.contains("held"), "a live board says nothing about a hold: " + plain);
    }

    @Test
    @DisplayName("board| names the chain's own ends, so a SHORT GRID is not a MISSING FEED")
    void theBoardLineNamesTheChainsEnds() {
        ReadablePage.Board board = BoardLines.from(mapJson(false), FIRST);
        assertNotNull(board);
        assertEquals(710_000L, board.chainLowCents());
        assertEquals(710_500L, board.chainHighCents());

        String line = firstLineStartingWith(Pages.board(FIRST, null, board), "board|");
        assertTrue(line.contains("|chain=7100-7105"), line);
        assertTrue(line.contains("|rows="), "beside the row count and not instead of it: " + line);
        // rows=N/41 with norow on six lines reads the same whether the chain stopped above the
        // bottom of the owner's window (a short grid, following price) or spanned it and lost six
        // strikes (a feed fault). These two numbers are what tell them apart.
    }

    private static String firstLineStartingWith(String page, String prefix) {
        for (String line : page.split("\n")) {
            if (line.startsWith(prefix)) {
                return line;
            }
        }
        throw new AssertionError("no line starting with " + prefix + " in " + page);
    }

    @Test
    @DisplayName("C-118: the three headline rows follow the same rule as the table")
    void theHeadlineRowsShowTheBarToo() {
        List<RecentRows.Row> rows = RecentRows.build(FIRST + 9L * 30_000L, 10, elevenBars());
        String head = Pages.headline(FIRST + 9L * 30_000L, null, null, null, null, rows, null,
                null, null, null);
        for (String line : head.split("\n")) {
            if (line.startsWith("r1|") || line.startsWith("r2|") || line.startsWith("r3|")) {
                assertTrue(line.contains("|ES7"),
                        "r1-r3 are what the owner reads first, so a bar hidden there is hidden "
                                + "where it matters most: " + line);
                assertTrue(line.endsWith("|B"), "and the side is still on the line: " + line);
            }
        }
        assertTrue(head.contains("grid|rows=10|missing=0|noSPX=9|complete=1"),
                "and the headline carries the three-state count: " + head);
    }
}

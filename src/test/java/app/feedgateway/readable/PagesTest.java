package app.feedgateway.readable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** <b>C-66</b> and its compact-form addendum, asserted rule by rule. */
class PagesTest {

    /** 2026-11-20 01:25:03 Amsterdam = 19:25:03 New York. */
    private static final long NOW = 1_795_134_303_000L;
    private static final long CLOSE = 1_795_121_934_000L;

    private static ReadablePage.Price price(long atMs, double spx) {
        return new ReadablePage.Price(atMs, spx, "ES_BASIS_DERIVED", "LIVE_ES_BASIS");
    }

    private static ReadablePage.Board board() {
        return BoardLines.from("{\"spot\":7177.56,\"strikes\":[{\"strikeCents\":717500,"
                + "\"board\":{\"netGex\":-8.31E9,\"holdOrAccelerate\":\"ACCELERATE\","
                + "\"excludedReason\":null}}]}", 1_795_124_682_036L);
    }

    @Test
    @DisplayName("every page opens with the SAME first line, in the owner's order")
    void everyPageOpensTheSameWay() {
        ReadablePage.Price p = price(NOW - 5_000L, 7174.45);
        String expected = Pages.firstLine(NOW, p);
        assertTrue(expected.startsWith("last=19:24:58NY/01:24:58AMS"),
                "the PRICE's own time, five seconds before this write: " + expected);
        assertTrue(expected.contains("NY/") && expected.contains("AMS"), expected);
        assertTrue(expected.contains("px=7174.45"), expected);
        assertTrue(expected.contains("written="), expected);

        for (String page : List.of(
                Pages.board(NOW, p, board()),
                Pages.overnight(NOW, p, null),
                Pages.index(NOW, p, List.of(new Pages.PageStat("/now", NOW, 1234))))) {
            assertEquals(expected, page.lines().findFirst().orElseThrow(),
                    "whichever page he opens tells him how current the data is");
        }
    }

    @Test
    @DisplayName("STALE opens the line past 20 minutes, with the age")
    void staleOpensEveryPage() {
        String page = Pages.board(NOW, price(NOW - 21 * 60_000L, 7174.45), board());
        assertTrue(page.startsWith("STALE(21m)|"), page.lines().findFirst().orElse(""));
    }

    @Test
    @DisplayName("no padding, no alignment, and an empty cell is nothing between two separators")
    void theFormatIsCompact() {
        ReadablePage.Overnight o = new ReadablePage.Overnight(7176.24, CLOSE,
                List.of(new ReadablePage.Slot(CLOSE, 7177.94, 7181.74, 7162.99, 7179.99,
                                258939L, "ANCHORED"),
                        new ReadablePage.Slot(CLOSE + 900_000L, null, null, null, null, null,
                                null)),
                7181.74, 7148.74);
        String page = Pages.overnight(NOW, price(NOW, 7174.45), o);

        for (String line : page.lines().toList()) {
            assertFalse(line.contains("  "), "no run of spaces anywhere: [" + line + "]");
        }
        assertTrue(page.contains("|258939|"), "volumes are plain integers, no separators");
        assertFalse(page.contains("258,939"), "and certainly not grouped");
        assertTrue(page.contains("|||||"), "a blank slot is empty cells, not dashes or zeroes");
        assertTrue(page.contains("ny|ams|o|h|l|c|vol|basis"), "one short header line");
    }

    @Test
    @DisplayName("values common to every row are stated ONCE above the table, not per row")
    void onceOnlyValuesAreNotRepeated() {
        String page = Pages.board(NOW, price(NOW, 7174.45), board());
        assertEquals(1, page.lines().filter(l -> l.contains("spot=")).count(),
                "spot appears once, on the line above the table");
        assertEquals(1, page.lines().filter(l -> l.startsWith("board|cycle=")).count(),
                "and so does the cycle time");
        // 41 strike rows, and not one of them repeats the cycle or the spot
        long rows = page.lines().filter(l -> l.matches("^7[0-9]{3}\\|.*")).count();
        assertEquals(41, rows, "every grid strike is a row");
        assertEquals(0, page.lines().filter(l -> l.matches("^7[0-9]{3}\\|.*cycle=.*")).count(),
                "no row carries a per-cycle value");
    }

    @Test
    @DisplayName("paging never drops a row, however many there are")
    void pagingDropsNothing() {
        // C-66: "never drop a level". 4,000 rows cannot fit one page, so they must be split.
        List<String> rows = new ArrayList<>();
        for (int i = 0; i < 4_000; i++) {
            rows.add("7000.25|" + i + "|12345|67890|A");
        }
        List<String> header = List.of(Pages.firstLine(NOW, price(NOW, 7174.45)), Pages.LEGEND,
                "footprint|spot=7174.45", "px|buy|sell|abs");
        List<String> pages = Pages.paged(header, rows);

        assertTrue(pages.size() > 1, "they were split, not truncated: " + pages.size() + " pages");
        for (String page : pages) {
            assertTrue(page.length() <= Pages.MAX_CHARS,
                    "each page is within the bound: " + page.length());
            assertTrue(page.startsWith(header.get(0)), "and each repeats the first line");
        }
        // every single row survives somewhere, exactly once
        int found = 0;
        for (String page : pages) {
            for (String line : page.lines().toList()) {
                if (line.matches("^7000\\.25\\|\\d+\\|.*")) {
                    found++;
                }
            }
        }
        assertEquals(4_000, found, "every row is present exactly once across the pages");
    }

    @Test
    @DisplayName("a single page that would overflow is capped and says so")
    void theBoundIsEnforcedOnSinglePages() {
        String out = Pages.cap("x".repeat(Pages.MAX_CHARS + 5_000));
        assertTrue(out.length() <= Pages.MAX_CHARS, "within the bound: " + out.length());
        assertTrue(out.contains("truncated|"), "and the cut is named, not silent");
    }

    @Test
    @DisplayName("an absent section gives ONE line of reason, not prose and not silence")
    void absentSectionsSayWhyInOneLine() {
        String noBoard = Pages.board(NOW, price(NOW, 7174.45), null);
        List<String> lines = noBoard.lines().toList();
        assertEquals(3, lines.size(), "first line, legend, one reason: " + lines);
        assertTrue(lines.get(2).startsWith("board|absent|"), lines.get(2));

        String noOvernight = Pages.overnight(NOW, price(NOW, 7174.45), null);
        assertTrue(noOvernight.lines().toList().get(2).startsWith("overnight|absent|"));
    }

    @Test
    @DisplayName("the legend expands the codes, so no page needs a word for a reading")
    void theLegendCarriesTheCodes() {
        String page = Pages.board(NOW, price(NOW, 7174.45), board());
        assertTrue(page.contains("A=accelerate"), "the legend expands A");
        assertTrue(page.contains("|A|spot") || page.contains("|A\n") || page.contains("|A|"),
                "and the rows carry the code rather than the word");
        assertFalse(page.contains("ACCELERATE"), "the word itself is not on the page");
    }

    @Test
    @DisplayName("with no price at all every page still opens honestly")
    void noPriceIsStillAFirstLine() {
        String page = Pages.board(NOW, null, board());
        assertTrue(page.startsWith("NO PRICE|written="), page.lines().findFirst().orElse(""));
        assertTrue(page.contains("nothing below is a current price"));
    }

    @Test
    @DisplayName("/index names each page with its write time and size")
    void theIndexShowsWhatIsCurrent() {
        String page = Pages.index(NOW, price(NOW, 7174.45), List.of(
                new Pages.PageStat("/now", NOW, 4200),
                new Pages.PageStat("/board", NOW - 30_000L, 1800),
                new Pages.PageStat("/footprint", 0L, 0)));
        assertTrue(page.contains("page|written|chars"), "one short header");
        assertTrue(page.contains("/now|01:25:03AMS|4200"), page);
        assertTrue(page.contains("/footprint||0"),
                "a page never written has an empty time rather than a fake one");
    }
}

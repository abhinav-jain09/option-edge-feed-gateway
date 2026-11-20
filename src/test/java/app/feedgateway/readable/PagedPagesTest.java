package app.feedgateway.readable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** <b>C-66</b>: the paged pages — nothing is ever dropped to make something fit. */
class PagedPagesTest {

    private static final long NOW = 1_795_134_303_000L;
    private static final long CLOSE = 1_795_121_934_000L;

    private static ReadablePage.Price price() {
        return new ReadablePage.Price(NOW - 2_000L, 7163.95, "ES_BASIS_DERIVED", "LIVE_ES_BASIS");
    }

    private static final BasisRead ANCHORED =
            BasisRead.of("{\"level\":19.26,\"levelKind\":\"ANCHORED\"}");

    @Test
    @DisplayName("/recent-full carries a whole night of 30-second rows across pages, losing none")
    void aWholeNightOfFineRowsSurvives() {
        // the cash close to now is about four and a half hours = roughly 540 slots
        long slot = RecentRows.slotOf(NOW);
        List<OvernightGrid.Bar> bars = new ArrayList<>();
        int slots = (int) ((NOW - CLOSE) / RecentRows.SLOT_MS) + 1;
        for (int i = 0; i < slots; i++) {
            bars.add(new OvernightGrid.Bar(slot - (long) i * RecentRows.SLOT_MS,
                    7163, 7165, 7161, 7163.95, 123456L, "ANCHORED", "30s", 60000L, 63456L,
                    -3456L));
        }
        List<RecentRows.Row> rows = RecentRows.build(NOW, slots, bars);
        assertTrue(rows.size() > 400, "a night is hundreds of rows: " + rows.size());

        List<String> pages = Pages.recentFull(NOW, price(), rows);
        assertTrue(pages.size() > 1, "more than one page: " + pages.size());
        for (String page : pages) {
            assertTrue(page.length() <= Pages.MAX_CHARS, "each within the bound: " + page.length());
            assertTrue(page.startsWith("last="), "and each repeats the first line");
            assertTrue(page.contains(RecentRows.header()), "and the column header");
        }
        long printed = pages.stream().flatMap(String::lines)
                .filter(l -> l.contains("|7163.95|")).count();
        assertEquals(rows.size(), printed,
                "every fine row is present exactly once, which is why this page exists");
    }

    @Test
    @DisplayName("/footprint names the basis it translated under, on every page")
    void everyFootprintPageNamesItsBasis() {
        List<FootprintByPrice.Level> levels = new ArrayList<>();
        for (int i = 0; i < 1_500; i++) {
            levels.add(new FootprintByPrice.Level(7160.25 + i * 0.25,
                    7140.99 + i * 0.25, 7L, 5L, 0L));
        }
        List<String> pages = Pages.footprint(NOW, price(), 7163.95, levels, ANCHORED);
        assertTrue(pages.size() > 1, "paged: " + pages.size());
        for (String page : pages) {
            assertTrue(page.contains("basis=19.26 ANCHORED"),
                    "a reader who lands on page 2 must still see what the prices rest on");
            assertTrue(page.length() <= Pages.MAX_CHARS);
        }
        long printed = pages.stream().flatMap(String::lines)
                .filter(l -> l.matches("^[0-9.]+\\|[0-9.]+\\|7\\|5\\|\\|12$")).count();
        assertEquals(1_500, printed, "no level dropped");
    }

    @Test
    @DisplayName("an empty footprint is one page with one line of reason")
    void anEmptyFootprintSaysWhy() {
        List<String> pages = Pages.footprint(NOW, price(), 7163.95, List.of(), ANCHORED);
        assertEquals(1, pages.size());
        assertTrue(pages.get(0).contains("absent|no level published for this window"));
        assertTrue(pages.get(0).startsWith("last="));
    }

    @Test
    @DisplayName("an absent basis is said rather than left blank")
    void anAbsentBasisIsNamed() {
        List<String> pages = Pages.footprint(NOW, price(), 7163.95,
                List.of(new FootprintByPrice.Level(7160.25, null, 7L, 5L, 0L)), null);
        assertTrue(pages.get(0).contains("basis=absent"),
                "so a reader knows the SPX column is empty for a reason");
    }

    @Test
    @DisplayName("an empty recent-full is one page that says so")
    void anEmptyRecentFullSaysWhy() {
        List<String> pages = Pages.recentFull(NOW, price(), List.of());
        assertEquals(1, pages.size());
        assertTrue(pages.get(0).contains("absent|no 30-second bar held for this span"));
    }

    @Test
    @DisplayName("/index lists each page with its write time and size")
    void theIndexIsOneLinePerPage() {
        String page = Pages.index(NOW, price(), List.of(
                new Pages.PageStat("/now", NOW, 4200),
                new Pages.PageStat("/footprint/1", NOW, 19800),
                new Pages.PageStat("/footprint/2", NOW, 8100),
                new Pages.PageStat("/recent-full/1", NOW - 30_000L, 19900),
                new Pages.PageStat("/board", NOW, 1800),
                new Pages.PageStat("/overnight", NOW, 1500),
                new Pages.PageStat("/days", 0L, 0)));
        assertTrue(page.contains("page|written|chars"));
        assertTrue(page.contains("/footprint/2|"), "the continuation pages are listed too");
        assertTrue(page.contains("/days||0"),
                "a page never written has an empty time, not a fake one");
        assertTrue(page.length() < 1_000, "the index is tiny by design: " + page.length());
    }
}

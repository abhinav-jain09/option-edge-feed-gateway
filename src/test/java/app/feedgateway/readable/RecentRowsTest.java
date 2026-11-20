package app.feedgateway.readable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** <b>C-64</b>: 30-second rows, newest first, carrying the platform's own footprint numbers. */
class RecentRowsTest {

    private static final ZoneId NY = ZoneId.of("America/New_York");
    /** 2026-11-20 00:25:10Z, so the newest 30-second slot starts at :25:00. */
    private static final long NOW = 1_795_134_310_000L;
    private static final long SLOT = RecentRows.SLOT_MS;

    private static OvernightGrid.Bar bar(long at, double close, long vol, long buy, long sell,
            String tf) {
        return new OvernightGrid.Bar(at, close - 1, close + 2, close - 3, close, vol, "ANCHORED",
                tf, buy, sell, buy - sell);
    }

    @Test
    @DisplayName("sixty rows, newest first, every slot present whether or not a bar arrived")
    void aFixedGridNewestFirst() {
        long newest = RecentRows.slotOf(NOW);
        List<RecentRows.Row> rows = RecentRows.build(NOW, RecentRows.DEFAULT_ROWS,
                List.of(bar(newest, 7174.45, 300, 180, 120, "30s"),
                        bar(newest - 10 * SLOT, 7170.00, 250, 100, 150, "30s")));
        assertEquals(60, rows.size(), "C-64's sixty rows is thirty minutes");
        assertEquals(newest, rows.get(0).startMs(), "newest first");
        assertEquals(newest - 59 * SLOT, rows.get(59).startMs(), "oldest last");
        assertTrue(rows.get(0).filled());
        assertFalse(rows.get(1).filled(), "a slot with no bar is a row in its own place");
        assertTrue(rows.get(10).filled(), "and the older bar is in the slot it belongs to");
    }

    @Test
    @DisplayName("the footprint numbers are the bar's own, and delta is NOT our subtraction")
    void theFootprintIsThePlatformsOwn() {
        long newest = RecentRows.slotOf(NOW);
        // A bar with unknown-side contracts: buyers minus sellers is 60, but the producer's own
        // delta says 50. The row must carry 50.
        OvernightGrid.Bar bar = new OvernightGrid.Bar(newest, 7173, 7175, 7172, 7174.45, 300L,
                "ANCHORED", "30s", 180L, 120L, 50L);
        RecentRows.Row r = RecentRows.build(NOW, 1, List.of(bar)).get(0);
        assertEquals(300L, r.volume());
        assertEquals(180L, r.buyers());
        assertEquals(120L, r.sellers());
        assertEquals(50L, r.delta(),
                "the producer's delta, not buyers minus sellers, which would read 60");
        assertEquals("B", r.dominant(), "and the dominant side is the sign of THAT delta");
    }

    @Test
    @DisplayName("a MINUTE bar is ignored here, or it would double a slot's volume")
    void onlyThirtySecondBarsBuildThisTable() {
        long newest = RecentRows.slotOf(NOW);
        List<RecentRows.Row> rows = RecentRows.build(NOW, 2,
                List.of(bar(newest, 7174.45, 600, 300, 300, "1m")));
        assertFalse(rows.get(0).filled(),
                "a 1-minute bar covers two of these slots; taking it would double whichever one "
                        + "it landed in");
    }

    @Test
    @DisplayName("the dominant side distinguishes a measured zero from an absent delta")
    void zeroDeltaIsAMeasurement() {
        assertEquals("B", RecentRows.dominant(5L));
        assertEquals("S", RecentRows.dominant(-5L));
        assertEquals("-", RecentRows.dominant(0L), "balanced is a measurement");
        assertEquals("", RecentRows.dominant(null), "absent is not balanced");
    }

    @Test
    @DisplayName("the compact line has one cell per column and empties where there is no data")
    void theLineIsCompact() {
        long newest = RecentRows.slotOf(NOW);
        List<RecentRows.Row> rows = RecentRows.build(NOW, 2,
                List.of(bar(newest, 7174.45, 300, 180, 120, "30s")));
        String filled = RecentRows.line(rows.get(0), NY);
        String blank = RecentRows.line(rows.get(1), NY);

        assertEquals(RecentRows.header().split("\\|", -1).length,
                filled.split("\\|", -1).length,
                "a filled row has exactly as many cells as the header names: " + filled);
        assertEquals(RecentRows.header().split("\\|", -1).length,
                blank.split("\\|", -1).length,
                "and so does a blank one, so the columns never shift: " + blank);
        assertTrue(filled.contains("|7174.45|"), filled);
        assertTrue(filled.contains("|180|120|60|B|"), "buy, sell, delta, dominant: " + filled);
        assertFalse(filled.contains("  "), "no padding anywhere: " + filled);
        // CHANGED 2026-11-20 on the reviewer's instruction, and the old assertion was not wrong
        // so much as outvoted by a reader. It said "ANCHORED is the normal case and costs no
        // characters" -- a deliberate choice to spend nothing on the common case and show only the
        // exception. But the page's own legend says "empty=no data for that slot", so the blank
        // read as "this bar has no basis" when the bar had one and it was anchored; the reviewer
        // read the whole column as always-empty and asked for it filled or removed. One letter is
        // the compromise: it costs one character, it keeps the column meaningful, and an unusual
        // state still stands out because it carries its full name.
        assertTrue(filled.endsWith("|A"),
                "the normal case is one letter now, not nothing: " + filled);
        assertTrue(blank.endsWith("||||||||"), "a blank row is empty cells: " + blank);
    }

    @Test
    @DisplayName("a basis that is not ANCHORED is named on the row")
    void anUnanchoredBasisShows() {
        long newest = RecentRows.slotOf(NOW);
        OvernightGrid.Bar q = new OvernightGrid.Bar(newest, 7173, 7175, 7172, 7174.45, 10L,
                "QUARANTINED", "30s", 5L, 5L, 0L);
        String line = RecentRows.line(RecentRows.build(NOW, 1, List.of(q)).get(0), NY);
        assertTrue(line.endsWith("QUARANTINED"), line);
    }

    @Test
    @DisplayName("slots are aligned to the half minute, and a table of no rows is refused")
    void boundaries() {
        assertEquals(1_795_134_300_000L, RecentRows.slotOf(NOW));
        assertEquals(1_795_134_300_000L, RecentRows.slotOf(NOW - 10_000L));
        assertThrows(IllegalArgumentException.class, () -> RecentRows.build(NOW, 0, List.of()));
    }

    @Test
    @DisplayName("sixty rows with their header fit well inside one page")
    void sixtyRowsFitAPage() {
        long newest = RecentRows.slotOf(NOW);
        java.util.List<OvernightGrid.Bar> bars = new java.util.ArrayList<>();
        for (int i = 0; i < 60; i++) {
            bars.add(bar(newest - (long) i * SLOT, 7174.45, 999999, 500000, 499999, "30s"));
        }
        StringBuilder page = new StringBuilder(RecentRows.header()).append('\n');
        for (RecentRows.Row r : RecentRows.build(NOW, 60, bars)) {
            page.append(RecentRows.line(r, NY)).append('\n');
        }
        assertTrue(page.length() < 4_000,
                "the whole Recent table costs under 4,000 characters: " + page.length());
    }

    @Test
    @DisplayName("the basis cell is ALWAYS written, because an empty cell means 'no data' here")
    void theBasisCellIsAlwaysWritten() {
        // The reviewer read this column as always-empty and asked for it filled or removed. It was
        // blank whenever the state was ANCHORED -- the normal case -- while the page's own legend
        // says "empty=no data for that slot", so the blank said the bar had no basis when it had
        // one.
        String anchored = RecentRows.line(RecentRows.build(NOW, 1,
                List.of(bar(NOW - 1_000L, 7142.45, 67, 33, 34, "30s"))).get(0), NY);
        assertTrue(anchored.endsWith("|A"),
                "one letter for the normal case, so sixty rows do not carry eight characters of "
                        + "ANCHORED: " + anchored);

        OvernightGrid.Bar odd = new OvernightGrid.Bar(NOW - 1_000L, 7141.0, 7144.0, 7139.0,
                7142.45, 67L, "QUARANTINED", "30s", 33L, 34L, -1L);
        String quarantined = RecentRows.line(
                RecentRows.build(NOW, 1, List.of(odd)).get(0), NY);
        assertTrue(quarantined.endsWith("|QUARANTINED"),
                "and an unusual state gets its full name, so it stands out: " + quarantined);
    }

    @Test
    @DisplayName("a bar with no basis state at all reads ? -- the only 'no data' this column has")
    void aBarWithNoBasisStateReadsQuestionMark() {
        OvernightGrid.Bar none = new OvernightGrid.Bar(NOW - 1_000L, 7141.0, 7144.0, 7139.0,
                7142.45, 67L, null, "30s", 33L, 34L, -1L);
        String line = RecentRows.line(RecentRows.build(NOW, 1, List.of(none)).get(0), NY);
        assertTrue(line.endsWith("|?"), line);
    }
}

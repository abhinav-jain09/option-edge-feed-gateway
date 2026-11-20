package app.feedgateway.readable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.TreeMap;

/**
 * <b>C-64</b>: the Recent table — one row per <b>30 seconds</b>, newest first, carrying the price
 * and the platform's own footprint numbers.
 *
 * <p><b>30 seconds is the owner's figure and the platform's own resolution.</b> He said so twice
 * ("I told you that 30 second. Why 15 minute now?"), and the measurement agrees: of the last 400
 * records on {@code es.futures.footprint.bars}, 241 are {@code 30s}. So every row carries real
 * numbers and nothing is spread across a minute it does not belong to. The reviewer's fallback —
 * filling the footprint only where a minute closes — is not needed and is not implemented, because
 * an unused fallback is a branch nobody tests.
 *
 * <p><b>A fixed time grid, like the night's.</b> Every 30-second slot in the window gets a row
 * whether or not a bar arrived, so a gap is visible in its own place rather than closing up. Rows
 * are rebuilt from the whole window at every write, which is the backfill.
 *
 * <p><b>Nothing is computed here that the platform publishes.</b> Volume, buyer-initiated and
 * seller-initiated volume and delta are the bar's own fields. The dominant side is the <b>sign of
 * the platform's own delta</b> and not a second opinion derived from the two volumes: those can
 * disagree with delta when a bar carries unknown-side contracts, and the published delta is the
 * producer's answer.
 */
public final class RecentRows {

    /** The owner's row width. */
    public static final long SLOT_MS = 30_000L;

    /** C-64: the last thirty minutes, which is sixty rows. */
    public static final int DEFAULT_ROWS = 60;

    private RecentRows() {
    }

    /** One row, already formatted for C-66's compact table. */
    public record Row(long startMs, Double price, Double high, Double low, Long volume,
                      Long buyers, Long sellers, Long delta, String dominant, String basisState) {

        /** True where a bar delivered this slot. */
        public boolean filled() {
            return price != null;
        }
    }

    /**
     * Build the rows, newest first.
     *
     * @param nowMs the write instant; the newest slot is the one containing it
     * @param rows  how many 30-second slots to cover
     * @param bars  the {@code 30s} bars held for the night, in any order
     */
    public static List<Row> build(long nowMs, int rows, List<OvernightGrid.Bar> bars) {
        if (rows < 1) {
            throw new IllegalArgumentException("a table of no rows is not a table: " + rows);
        }
        TreeMap<Long, OvernightGrid.Bar> bySlot = new TreeMap<>();
        for (OvernightGrid.Bar bar : bars) {
            if (!RecentRows.SLOT_MS_MATCHES.equals(bar.timeframe())) {
                // Only the owner's own resolution. A minute bar here would cover two slots and
                // double the volume of whichever one it landed in.
                continue;
            }
            bySlot.put(slotOf(bar.startMs()), bar);
        }
        long newest = slotOf(nowMs);
        List<Row> out = new ArrayList<>(rows);
        for (int i = 0; i < rows; i++) {
            long at = newest - (long) i * SLOT_MS;
            OvernightGrid.Bar bar = bySlot.get(at);
            if (bar == null) {
                out.add(new Row(at, null, null, null, null, null, null, null, null, null));
            } else {
                out.add(new Row(at, bar.close(), bar.high(), bar.low(), bar.volume(),
                        bar.buyers(), bar.sellers(), bar.delta(), dominant(bar.delta()),
                        bar.basisState()));
            }
        }
        return out;
    }

    /** The timeframe this table is built from, named once. */
    static final String SLOT_MS_MATCHES = "30s";

    /**
     * The dominant side from the platform's own delta: {@code B} buyers, {@code S} sellers, empty
     * where delta is absent, and {@code -} where it is exactly zero — which is a measurement and
     * not a missing one.
     */
    static String dominant(Long delta) {
        if (delta == null) {
            return "";
        }
        if (delta > 0) {
            return "B";
        }
        if (delta < 0) {
            return "S";
        }
        return "-";
    }

    /** The 30-second slot a millisecond belongs to, floored so the grid is aligned. */
    public static long slotOf(long epochMs) {
        return Math.floorDiv(epochMs, SLOT_MS) * SLOT_MS;
    }

    /** C-66's compact header for this table. */
    public static String header() {
        return "ny|px|h|l|vol|buy|sell|delta|dom|basis";
    }

    /** One compact line: empty cells are nothing between separators. */
    public static String line(Row r, java.time.ZoneId zone) {
        StringBuilder s = new StringBuilder(64);
        s.append(Pages.hms(r.startMs(), zone)).append('|');
        if (!r.filled()) {
            s.append("||||||||");
            return s.toString();
        }
        s.append(num(r.price())).append('|').append(num(r.high())).append('|')
                .append(num(r.low())).append('|')
                .append(r.volume() == null ? "" : r.volume()).append('|')
                .append(r.buyers() == null ? "" : r.buyers()).append('|')
                .append(r.sellers() == null ? "" : r.sellers()).append('|')
                .append(r.delta() == null ? "" : r.delta()).append('|')
                .append(r.dominant() == null ? "" : r.dominant()).append('|')
                .append(basisCell(r.basisState()));
        return s.toString();
    }

    /**
     * The row's own basis state, <b>always written</b>.
     *
     * <p>This cell was blank whenever the state was {@code ANCHORED} — the normal case — on the
     * theory that an exception is worth showing and the rule is not. The page's own legend says
     * {@code empty=no data for that slot}, so a blank here read as "this bar has no basis" when the
     * bar had one and it was anchored. The reviewer read the column as always-empty and asked for
     * it filled or removed; filled is right, because the state is genuinely <b>per bar</b> — each
     * carries its own {@code spxEqBasisState} — so a row that differs from its neighbours is a real
     * thing to see, and it can only stand out if the others say something.
     *
     * <p>One letter for the normal case so sixty rows do not carry eight characters of
     * {@code ANCHORED}, and the state verbatim for anything else, because an unusual state is
     * worth its full name. {@code ?} where the bar carried none at all, which is the only case the
     * legend's "no data" now covers.
     */
    private static String basisCell(String state) {
        if (state == null || state.isBlank()) {
            return "?";
        }
        return "ANCHORED".equals(state) ? "A" : state;
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private static String num(Double v) {
        return v == null ? "" : String.format(Locale.ROOT, "%.2f", v);
    }
}

package app.feedgateway.readable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * <b>C-60's fixed time grid, with C-60's backfill</b> (its third addendum, and item (e) of the
 * second).
 *
 * <p><b>It is a time grid and not a list of what arrived.</b> Every 15-minute slot from the previous
 * cash close to now gets a row, in order, whether or not a bar exists for it. A slot without bars is
 * a row with its timestamp and empty cells; when the data arrives, at the next build or by
 * backfill, it lands in that same row. Rows are never removed, merged away or re-ordered within the
 * night.
 *
 * <p><b>It REBUILDS rather than appends, and that is the backfill.</b> Every call is handed the
 * whole span and recomputes every slot, so a stall, a restart or a missed write fills itself in with
 * no special path and nothing to remember. There is deliberately no incremental mode: an append
 * would have to know what it had already written, and the thing it would get wrong is exactly the
 * case this exists for.
 *
 * <p><b>Nothing is interpolated.</b> A gap stays a gap. The 17:00-to-18:00 New York futures break is
 * a run of blank slots and so is a producer that has stopped; the page cannot tell them apart and
 * does not pretend to, which is why the stall is reported separately rather than inferred here.
 */
public final class OvernightGrid {

    /** The owner's 15-minute slot. */
    public static final long SLOT_MS = 15L * 60L * 1000L;

    /**
     * One bar, with the platform's SPX-equivalent prices where it carries them and its own futures
     * prices always.
     *
     * <p><b>C-118: the SPX prices are NULLABLE, and that is the whole point.</b> They were
     * primitive, so a bar the platform had not translated could not be represented here at all and
     * {@link OvernightDecoders} dropped it &mdash; which is how nine real bars became nine blank
     * rows on 2026-11-20 (C-117). A bar that exists is now always representable: it keeps its
     * volume, its buyer- and seller-initiated contracts, its delta and its own futures prices, and
     * only the translated prices are absent.
     *
     * <p><b>The two price sets are never mixed and nothing here translates.</b> C-103 holds: this
     * record carries what the bar carried, the renderer prints whichever set is there and says
     * which, and no basis is applied anywhere on this page.
     *
     * @param open       the platform's {@code spxEqOpenCents} as points, or null where the bar
     *                   carries no SPX-equivalent price at all
     * @param basisState the producer's own {@code spxEqBasisState}; a bar whose basis is not
     *                   {@code ANCHORED} is kept and its state named, never dropped (C-60 item 4)
     * @param timeframe  the producer's own bar length ({@code 30s} or {@code 1m}). <b>It is part of
     *                   the identity</b>: the platform publishes the same minute at several lengths,
     *                   and a 1-minute bar summed beside the two 30-second bars it contains would
     *                   double every volume on the row
     * @param buyers     buyer-initiated contracts, the producer's own {@code buyerContracts}, or
     *                   null where the bar does not carry it
     * @param sellers    seller-initiated contracts, {@code sellerContracts}, or null
     * @param delta      the producer's own {@code delta}. <b>Kept even though buyers and sellers
     *                   are here</b>: the two need not differ by delta when a bar carries
     *                   unknown-side contracts, and the published delta is the producer's answer
     *                   rather than our subtraction
     * @param futuresOpen the bar's own {@code openCents} as points &mdash; the FUTURES price, never
     *                   to be printed as an index price
     */
    public record Bar(long startMs, Double open, Double high, Double low, Double close,
                      long volume, String basisState, String timeframe,
                      Long buyers, Long sellers, Long delta,
                      Double futuresOpen, Double futuresHigh, Double futuresLow,
                      Double futuresClose) {

        public Bar {
            if (timeframe == null || timeframe.isBlank()) {
                throw new IllegalArgumentException(
                        "a bar without its timeframe cannot be kept apart from the bars it is made "
                                + "of: a 30s and a 1m bar at the same instant would sum into one "
                                + "slot and double-count the volume");
            }
            int spx = (open == null ? 0 : 1) + (high == null ? 0 : 1)
                    + (low == null ? 0 : 1) + (close == null ? 0 : 1);
            if (spx != 0 && spx != 4) {
                // A half-translated bar is the one shape the page cannot render honestly: three
                // index prices and one futures price on the same row would read as one series.
                throw new IllegalArgumentException(
                        "a bar carries all four SPX-equivalent prices or none of them, and this "
                                + "one carries " + spx + ": a row with three index prices and one "
                                + "futures price reads as four prices of the same kind");
            }
            int fut = (futuresOpen == null ? 0 : 1) + (futuresHigh == null ? 0 : 1)
                    + (futuresLow == null ? 0 : 1) + (futuresClose == null ? 0 : 1);
            if (fut != 0 && fut != 4) {
                throw new IllegalArgumentException(
                        "a bar carries all four futures prices or none of them, and this one "
                                + "carries " + fut);
            }
            if (spx == 0 && fut == 0) {
                throw new IllegalArgumentException(
                        "a bar with neither an SPX-equivalent nor a futures price has no price at "
                                + "all: there is nothing for a row to print and nothing C-118 can "
                                + "recover");
            }
        }

        /** True where the platform translated this bar, which is the normal case. */
        public boolean hasSpx() {
            return close != null;
        }

        /** True where the bar's own futures prices are here, so C-118 has something to print. */
        public boolean hasFutures() {
            return futuresClose != null;
        }

        /**
         * A translated bar, in the shape this record had before C-118.
         *
         * <p>Kept as a factory rather than a second constructor so that the call sites which
         * genuinely mean "the platform translated this one" read that way, and so that adding the
         * futures prices did not silently re-point seventeen hand-written test constructions at a
         * different meaning.
         */
        public static Bar spx(long startMs, double open, double high, double low, double close,
                              long volume, String basisState, String timeframe,
                              Long buyers, Long sellers, Long delta) {
            return new Bar(startMs, open, high, low, close, volume, basisState, timeframe,
                    buyers, sellers, delta, null, null, null, null);
        }
    }

    private OvernightGrid() {
    }

    /**
     * Build the grid over the whole span.
     *
     * @param nowMs            the build instant; the last slot is the one containing it
     * @param previousCloseMs  the previous cash close, which is where the night starts
     * @param previousClose    its SPX price
     * @param bars             every bar in the span, in any order; bars outside it are ignored.
     *                         A {@code Collection} and not a {@code List} because this only
     *                         iterates it, which lets the source hand over a VIEW rather than
     *                         a copy per call (OvernightSource.bars)
     * @param tickHigh         the high over the live tick path, or null where there is none
     * @param tickLow          the low over the live tick path, or null
     */
    public static ReadablePage.Overnight build(long nowMs, long previousCloseMs,
            double previousClose, java.util.Collection<Bar> bars, Double tickHigh,
            Double tickLow) {
        if (nowMs < previousCloseMs) {
            throw new IllegalArgumentException(
                    "the night cannot end before it began: now " + nowMs + " < close "
                            + previousCloseMs);
        }
        Map<Long, Accumulator> bySlot = new TreeMap<>();
        for (Bar bar : bars) {
            if (bar.startMs() < previousCloseMs || bar.startMs() > nowMs) {
                continue;
            }
            bySlot.computeIfAbsent(slotOf(bar.startMs()), k -> new Accumulator()).add(bar);
        }

        List<ReadablePage.Slot> slots = new ArrayList<>();
        long first = slotOf(previousCloseMs);
        long last = slotOf(nowMs);
        for (long at = first; at <= last; at += SLOT_MS) {
            Accumulator a = bySlot.get(at);
            slots.add(a == null
                    ? new ReadablePage.Slot(at, null, null, null, null, null, null)
                    : a.toSlot(at));
        }

        // The extremes span BOTH sources. On 2026-11-20 the tick low sat below every bar's low, so
        // taking either alone understates the night's range.
        Double high = widest(tickHigh, bySlot.values().stream()
                .map(a -> a.high).filter(java.util.Objects::nonNull)
                .max(Double::compare).orElse(null), true);
        Double low = widest(tickLow, bySlot.values().stream()
                .map(a -> a.low).filter(java.util.Objects::nonNull)
                .min(Double::compare).orElse(null), false);
        return new ReadablePage.Overnight(previousClose, previousCloseMs, slots, high, low);
    }

    /** The slot a millisecond belongs to, floored, so the grid is aligned and not offset. */
    public static long slotOf(long epochMs) {
        return Math.floorDiv(epochMs, SLOT_MS) * SLOT_MS;
    }

    private static Double widest(Double a, Double b, boolean takeMax) {
        if (a == null) {
            return b;
        }
        if (b == null) {
            return a;
        }
        return takeMax ? Math.max(a, b) : Math.min(a, b);
    }

    /**
     * One slot under construction. Open is the earliest bar's, close the latest bar's.
     *
     * <p><b>C-118: a bar with no SPX-equivalent price still brings its VOLUME.</b> The volume is
     * the bar's own count and needs no translation, so dropping it would hide trade that happened.
     * Only the four prices wait for a translated bar, and a slot whose bars were all untranslated
     * carries its volume with empty price cells and says {@code noSPX} in its basis column.
     */
    private static final class Accumulator {
        private long openAt = Long.MAX_VALUE;
        private long closeAt = Long.MIN_VALUE;
        private Double open;
        private Double close;
        private Double high;
        private Double low;
        private long volume;
        private int barsWithoutSpx;
        private final Set<String> states = new LinkedHashSet<>();

        void add(Bar bar) {
            volume += bar.volume();
            if (!bar.hasSpx()) {
                barsWithoutSpx++;
                states.add("noSPX");
                return;
            }
            if (bar.startMs() < openAt) {
                openAt = bar.startMs();
                open = bar.open();
            }
            if (bar.startMs() > closeAt) {
                closeAt = bar.startMs();
                close = bar.close();
            }
            high = high == null ? bar.high() : Math.max(high, bar.high());
            low = low == null ? bar.low() : Math.min(low, bar.low());
            states.add(bar.basisState() == null ? "UNKNOWN" : bar.basisState());
        }

        ReadablePage.Slot toSlot(long at) {
            return new ReadablePage.Slot(at, open, high, low, close, volume,
                    String.join(",", states));
        }
    }
}

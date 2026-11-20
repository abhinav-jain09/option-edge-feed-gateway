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
     * One one-minute bar, already translated by the platform.
     *
     * @param basisState the producer's own {@code spxEqBasisState}; a bar whose basis is not
     *                   {@code ANCHORED} is kept and its state named, never dropped (C-60 item 4)
     */
    public record Bar(long startMs, double open, double high, double low, double close,
                      long volume, String basisState) {
    }

    private OvernightGrid() {
    }

    /**
     * Build the grid over the whole span.
     *
     * @param nowMs            the build instant; the last slot is the one containing it
     * @param previousCloseMs  the previous cash close, which is where the night starts
     * @param previousClose    its SPX price
     * @param bars             every bar in the span, in any order; bars outside it are ignored
     * @param tickHigh         the high over the live tick path, or null where there is none
     * @param tickLow          the low over the live tick path, or null
     */
    public static ReadablePage.Overnight build(long nowMs, long previousCloseMs,
            double previousClose, List<Bar> bars, Double tickHigh, Double tickLow) {
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
                .map(a -> a.high).max(Double::compare).orElse(null), true);
        Double low = widest(tickLow, bySlot.values().stream()
                .map(a -> a.low).min(Double::compare).orElse(null), false);
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

    /** One slot under construction. Open is the earliest bar's, close the latest bar's. */
    private static final class Accumulator {
        private long openAt = Long.MAX_VALUE;
        private long closeAt = Long.MIN_VALUE;
        private double open;
        private double close;
        private double high = Double.NEGATIVE_INFINITY;
        private double low = Double.POSITIVE_INFINITY;
        private long volume;
        private final Set<String> states = new LinkedHashSet<>();

        void add(Bar bar) {
            if (bar.startMs() < openAt) {
                openAt = bar.startMs();
                open = bar.open();
            }
            if (bar.startMs() > closeAt) {
                closeAt = bar.startMs();
                close = bar.close();
            }
            high = Math.max(high, bar.high());
            low = Math.min(low, bar.low());
            volume += bar.volume();
            states.add(bar.basisState() == null ? "UNKNOWN" : bar.basisState());
        }

        ReadablePage.Slot toSlot(long at) {
            return new ReadablePage.Slot(at, open, high, low, close, volume,
                    String.join(",", states));
        }
    }
}

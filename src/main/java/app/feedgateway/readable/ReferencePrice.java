package app.feedgateway.readable;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * <b>C-74</b>: the previous cash close, recovered at start-up instead of waited for.
 *
 * <p><b>The defect this exists to fix.</b> The page takes the previous cash close from the last
 * print of the SPX index itself, measured rather than taken from a calendar — which is right. But
 * the index does not print overnight, so a gateway that restarts at 03:00 has never seen one, and
 * the overnight rows stayed empty until 15:30 Amsterdam. On a night when the gateway restarts, the
 * owner's whole backfill is gone exactly when he needs it at 12:30.
 *
 * <p><b>Two sources, in order, and the page says which one answered.</b> First the last index print
 * before the previous cash close, read back from {@code underlying.spx.price} by time. Failing
 * that — the topic may no longer hold it — the last complete cash-session bar of
 * {@code es.futures.footprint.bars}, taken as its <b>SPX-equivalent</b> close and labelled as
 * such. "The index's own last print" and "a futures bar's SPX-equivalent" are different facts, and
 * the owner must not have to guess which he is reading. If neither answers, the page says NO
 * REFERENCE PRICE <i>with the reason</i>, which is the one honest outcome left.
 *
 * <p>Nothing here derives a basis: a footprint bar already carries {@code spxEq*Cents} with its
 * own {@code spxEqBasisState}, and a bar whose basis is not anchored is refused by the decoder
 * rather than translated here.
 */
public final class ReferencePrice {

    /** Which source answered. On the page in words, never inferred by the reader. */
    public enum From {
        /** The SPX index's own last print before the close. */
        INDEX_PRINT,
        /** A cash-session footprint bar's SPX-equivalent close. */
        FOOTPRINT_BAR,
        /** Neither source could answer. */
        NONE
    }

    /**
     * The resolved reference, or an explained absence.
     *
     * @param close the close itself, or null when {@code from} is {@link From#NONE}
     * @param high  the session high, or {@code Double.NaN} when it could not be recovered
     * @param low   the session low, or {@code Double.NaN} when it could not be recovered
     */
    public record Resolved(ReadablePage.Price close, double high, double low, From from,
                           String reason) {
        public Resolved {
            if (from == null) {
                throw new IllegalArgumentException("a reference price must say where it came from");
            }
            if (reason == null || reason.isBlank()) {
                throw new IllegalArgumentException("every outcome carries its reason, including "
                        + from);
            }
            if (from != From.NONE && close == null) {
                throw new IllegalArgumentException(from + " without a close is not a resolution");
            }
        }

        /** The words the page prints, so both renderers say the same thing. */
        public String label() {
            switch (from) {
                case INDEX_PRINT:
                    return "previous cash close from the SPX index's own last print";
                case FOOTPRINT_BAR:
                    return "previous cash close from a cash-session footprint bar's "
                            + "SPX-equivalent close";
                default:
                    return "NO REFERENCE PRICE — " + reason;
            }
        }
    }

    /**
     * The history the gateway can read back at start-up. Two seeks, both bounded, both by time.
     *
     * <p>A seam rather than a Kafka call so the resolution is driven in a test with real record
     * shapes: the thing that broke here was never the decoding, it was that nobody ever looked
     * back at all.
     */
    public interface History {
        /**
         * Every price record in {@code [fromMs, toMs]}, as published, up to {@code limit}.
         *
         * <p>A <b>window</b>, not a look-back with a record cap. The first version of this took
         * {@code (beforeMs, limit)} and the implementation seeked to {@code beforeMs - 96h} and
         * read 500 records FORWARD — so it returned 500 records from four days ago and never saw
         * the recent close. Reaching the 16:04 index print from 22:39 meant scanning about 24,000
         * basis-derived records (measured: the newest 4,003 carry no index print at all). The
         * caller now asks for the window it wants and steps outward, so the work is bounded at
         * each step and the answer is the one it was looking for.
         */
        List<String> pricesBetween(long fromMs, long toMs, int limit);

        /**
         * Bars in {@code [fromMs, toMs]}, as published, oldest first.
         */
        List<String> barsBetween(long fromMs, long toMs);
    }

    private ReferencePrice() {
    }

    /**
     * Resolve the reference, preferring the index print.
     *
     * @param beforeMs the instant to look back from — the previous cash close
     * @param history  the seeks, or null where the gateway cannot read back at all
     */
    /**
     * How far back to look for the index print, in widening steps.
     *
     * <p>The index prints only during the cash session, so from any overnight hour the answer is a
     * few hours back and the FIRST or second slice finds it. The wider slices exist for a weekend
     * or a holiday. Each is drained on its own, so no step is unbounded.
     */
    static final long[] PRICE_SLICES_MS = {
        2L * 3_600_000L, 8L * 3_600_000L, 16L * 3_600_000L, 30L * 3_600_000L, 96L * 3_600_000L
    };

    /** A bound on one slice's drain. A slice that needs more than this is already too wide. */
    static final int SLICE_LIMIT = 60_000;

    private static final ZoneId NY = ZoneId.of("America/New_York");
    /** The cash session, which is what "a cash-session bar" has to mean. */
    static final LocalTime CASH_OPEN = LocalTime.of(9, 30);
    static final LocalTime CASH_CLOSE = LocalTime.of(16, 0);

    /**
     * Resolve the reference, preferring the index print.
     *
     * @param beforeMs the instant to look back from
     * @param history  the seeks, or null where the gateway cannot read back at all
     */
    public static Resolved resolve(long beforeMs, History history) {
        if (history == null) {
            return new Resolved(null, Double.NaN, Double.NaN, From.NONE,
                    "this gateway cannot read price history back, so the close can only be "
                            + "learned by waiting for the next index print");
        }

        // C-98: THE INDEX PRINT, BY THE SILENCE RULE, in widening slices.
        //
        // What stood here was: "the newest one at or before the instant IS the previous cash
        // close, because the index prints only while the cash session is open." That sentence was
        // a comment stating a premise as a fact, and the premise is false -- measured on
        // 2026-11-20, the index published once a second right through the pre-market. So the
        // "newest index print" was a price from this morning, and this resolver handed it back as
        // yesterday's close on every restart.
        //
        // The rule is now IndexSilence's: the last index print BEFORE the most recent gap of three
        // hours or more. The same class runs live in OvernightSource, so the start-up answer and
        // the running answer come from one implementation.
        //
        // A SLICE MUST SPAN THE SILENCE, which is why the widening matters more than it did. The
        // narrowest slice is two hours and cannot contain a three-hour gap, so it can never
        // resolve; it is kept because it is cheap and because a slice that resolves nothing costs
        // one drain. The eight-hour slice is the first that can see a normal overnight quiet, and
        // the wider ones exist for a weekend. A resolution says which slice found it, so a reader
        // can tell a close found in eight hours from one found in ninety-six.
        for (long slice : PRICE_SLICES_MS) {
            List<ReadablePage.Price> prints = indexPrintsInOrder(
                    safe(history.pricesBetween(beforeMs - slice, beforeMs, SLICE_LIMIT)), beforeMs);
            // beforeMs closes a trailing silence: a boot in the middle of the night has no index
            // print after the gap, which is exactly when the overnight page needs the reference
            IndexSilence silence = IndexSilence.resolveFrom(prints, beforeMs);
            ReadablePage.Price reference = silence.reference();
            if (reference != null) {
                List<String> bars = safe(history.barsBetween(
                        sessionOpenMsFor(reference.epochMs()), reference.epochMs()));
                double[] range = rangeOf(bars);
                return new Resolved(reference, range[0], range[1], From.INDEX_PRINT,
                        "the last index print before a silence of "
                                + (IndexSilence.SILENCE_MS / 3_600_000L) + " hours or more, found "
                                + "within " + (slice / 3_600_000L) + " hours");
            }
        }

        // No index print anywhere we can read. The bars are the other measurement of the same
        // session and carry their own SPX-equivalent, so nothing is derived here -- but only a bar
        // INSIDE the cash session may be called a cash-session bar. The first version of this took
        // the newest bar in a 30-hour window, which overnight is an evening bar, and labelled it
        // as the cash session's. That label was false and the figure was 4.8 points out.
        List<String> window = safe(history.barsBetween(beforeMs - 30L * 3_600_000L, beforeMs));
        OvernightGrid.Bar last = null;
        for (String json : window) {
            OvernightGrid.Bar b = OvernightDecoders.bar(json);
            if (b == null || !b.hasSpx() || !inCashSession(b.startMs())) {
                // C-118: an untranslated bar is admitted to the page, but it cannot BE the
                // previous close -- that figure is an SPX price and this estate never derives one.
                continue;
            }
            if (last == null || b.startMs() > last.startMs()) {
                last = b;
            }
        }
        if (last != null) {
            List<String> sessionBars = new ArrayList<>();
            for (String json : window) {
                OvernightGrid.Bar b = OvernightDecoders.bar(json);
                if (b != null && b.hasSpx() && inCashSession(b.startMs())
                        && sameNyDate(b.startMs(), last.startMs())) {
                    sessionBars.add(json);
                }
            }
            double[] range = rangeOf(sessionBars);
            return new Resolved(
                    new ReadablePage.Price(last.startMs(), last.close(), "FOOTPRINT_BAR_SPX_EQ",
                            "CASH_SESSION_LAST_COMPLETE_BAR"),
                    range[0], range[1], From.FOOTPRINT_BAR,
                    "no index print remains on the price topic, so the close is the last complete "
                            + "cash-session bar's SPX-equivalent");
        }

        return new Resolved(null, Double.NaN, Double.NaN, From.NONE,
                "neither an index print nor a complete cash-session bar remains in the history "
                        + "this gateway can read, so the close is not recoverable and the "
                        + "overnight rows appear at the next index print");
    }

    /** The newest index print at or before the instant, or null. */
    /**
     * Every index print in the slice, in time order, up to {@code beforeMs}.
     *
     * <p><b>Sorted, and the sort is load-bearing.</b> {@link IndexSilence} measures the gap between
     * consecutive prints, so a list in arrival order would manufacture silences out of a
     * multi-partition drain's interleaving. It replaces {@code newestIndexPrint}, which needed no
     * order because it only took a maximum — and taking that maximum was the defect.
     */
    private static List<ReadablePage.Price> indexPrintsInOrder(List<String> records, long beforeMs) {
        List<ReadablePage.Price> out = new ArrayList<>();
        for (String json : records) {
            ReadablePage.Price p = OvernightDecoders.price(json, 0L);
            if (p == null || !OvernightSource.isIndexPrint(p) || p.epochMs() > beforeMs) {
                continue;               // the overnight derivation is never a close
            }
            out.add(p);
        }
        out.sort(java.util.Comparator.comparingLong(ReadablePage.Price::epochMs));
        return out;
    }

    /** Is this bar inside the cash session, which is what the label claims? */
    static boolean inCashSession(long atMs) {
        LocalTime t = Instant.ofEpochMilli(atMs).atZone(NY).toLocalTime();
        return !t.isBefore(CASH_OPEN) && t.isBefore(CASH_CLOSE);
    }

    private static boolean sameNyDate(long a, long b) {
        return Instant.ofEpochMilli(a).atZone(NY).toLocalDate()
                .equals(Instant.ofEpochMilli(b).atZone(NY).toLocalDate());
    }

    /** The cash open of the New York date the given instant falls in. */
    private static long sessionOpenMsFor(long atMs) {
        return Instant.ofEpochMilli(atMs).atZone(NY).toLocalDate()
                .atTime(CASH_OPEN).atZone(NY).toInstant().toEpochMilli();
    }

    /** The session's high and low from its bars, or NaN where there are none. */
    private static double[] rangeOf(List<String> bars) {
        double high = Double.NaN;
        double low = Double.NaN;
        for (String json : safe(bars)) {
            OvernightGrid.Bar b = OvernightDecoders.bar(json);
            if (b == null || !b.hasSpx()) {
                // C-118: the session's range is an SPX range. An untranslated bar contributes no
                // index price to it, and unboxing its null here would end the page with an NPE.
                continue;
            }
            high = Double.isNaN(high) ? b.high() : Math.max(high, b.high());
            low = Double.isNaN(low) ? b.low() : Math.min(low, b.low());
        }
        return new double[] {high, low};
    }

    private static List<String> safe(List<String> in) {
        return in == null ? List.of() : in;
    }
}

package app.feedgateway.readable;

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
         * Price records at or before {@code beforeMs}, newest first, as published.
         * May return fewer than asked, or none.
         */
        List<String> pricesBefore(long beforeMs, int limit);

        /**
         * The complete bars of the cash session that ended at or before {@code beforeMs}, oldest
         * first, as published.
         */
        List<String> cashSessionBars(long beforeMs);
    }

    private ReferencePrice() {
    }

    /**
     * Resolve the reference, preferring the index print.
     *
     * @param beforeMs the instant to look back from — the previous cash close
     * @param history  the seeks, or null where the gateway cannot read back at all
     */
    public static Resolved resolve(long beforeMs, History history) {
        if (history == null) {
            return new Resolved(null, Double.NaN, Double.NaN, From.NONE,
                    "this gateway cannot read price history back, so the close can only be "
                            + "learned by waiting for the next index print");
        }

        List<String> prices = safe(history.pricesBefore(beforeMs, 500));
        ReadablePage.Price newestIndexPrint = null;
        for (String json : prices) {
            ReadablePage.Price p = OvernightDecoders.price(json, 0L);
            if (p == null || !OvernightSource.isIndexPrint(p)) {
                continue;                       // the overnight derivation is never a close
            }
            if (p.epochMs() > beforeMs) {
                continue;                       // after the close is not this session's close
            }
            if (newestIndexPrint == null || p.epochMs() > newestIndexPrint.epochMs()) {
                newestIndexPrint = p;
            }
        }
        if (newestIndexPrint != null) {
            double[] range = rangeOf(history.cashSessionBars(beforeMs));
            return new Resolved(newestIndexPrint, range[0], range[1], From.INDEX_PRINT,
                    "the last index print at or before the close");
        }

        // The index print is gone from the topic. The bars are the other measurement of the same
        // session, and they carry their own SPX-equivalent, so nothing is derived here.
        List<String> bars = safe(history.cashSessionBars(beforeMs));
        OvernightGrid.Bar last = null;
        for (String json : bars) {
            OvernightGrid.Bar b = OvernightDecoders.bar(json);
            if (b == null) {
                continue;
            }
            if (last == null || b.startMs() > last.startMs()) {
                last = b;
            }
        }
        if (last != null) {
            double[] range = rangeOf(bars);
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

    /** The session's high and low from its bars, or NaN where there are none. */
    private static double[] rangeOf(List<String> bars) {
        double high = Double.NaN;
        double low = Double.NaN;
        for (String json : safe(bars)) {
            OvernightGrid.Bar b = OvernightDecoders.bar(json);
            if (b == null) {
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

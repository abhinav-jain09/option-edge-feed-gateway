package app.feedgateway.mtsession;

/**
 * A resolved, inclusive strike range {@code [lo, hi]} used as the per-user delivery filter
 * (OE-DDD-001 §8.6 Delivery Filter). The API layer resolves an ATM±N window to concrete
 * bounds before it reaches the routing core, keeping routing deterministic and test-friendly.
 *
 * <p>{@link #ALL} matches every strike and is used for underlying events and for IBKR's fixed
 * chain where no per-user strike narrowing applies.
 */
public record StrikeWindow(double lo, double hi) {

    public static final StrikeWindow ALL =
            new StrikeWindow(Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY);

    public StrikeWindow {
        if (Double.isNaN(lo) || Double.isNaN(hi)) {
            throw new IllegalArgumentException("Strike bounds must not be NaN");
        }
        if (lo > hi) {
            throw new IllegalArgumentException("Strike window lo (" + lo + ") > hi (" + hi + ")");
        }
    }

    public static StrikeWindow of(double lo, double hi) {
        return new StrikeWindow(lo, hi);
    }

    /** Inclusive containment test for the per-user strike delivery filter. */
    public boolean contains(double strike) {
        return strike >= lo && strike <= hi;
    }

    /** The smallest window covering both this and {@code other} (used for the subscribed-range union). */
    public StrikeWindow union(StrikeWindow other) {
        return new StrikeWindow(Math.min(lo, other.lo), Math.max(hi, other.hi));
    }
}

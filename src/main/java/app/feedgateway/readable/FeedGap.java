package app.feedgateway.readable;

/**
 * A window of the session whose bars arrived <b>long after the minutes they describe</b>.
 *
 * <p><b>Why the page must say this.</b> On 2026-11-20 the ES feed stopped at 18:16:05 New York and
 * resumed at 23:46:53. The bars for those five and a half hours were backfilled afterwards —
 * measured median publication lag inside the window <b>9,863 s (2h44m)</b>, maximum 5h29m, against
 * ~5 s on either side of it. The data therefore exists on the platform, but the indicators
 * service's session VWAP did not take it in: the tape's 1m bars total 35,218 contracts for the
 * session while the service's own {@code session.volume.cumulativeAnalysis} read 17,208. So the
 * VWAP the owner trades against was built without a large part of the session, and nothing in the
 * record says so — the service has {@code FEED_GAP}, {@code STALE_FEED} and {@code RETENTION_GAP}
 * in its reason vocabulary and reports {@code DEADLINE_FINALIZED} instead.
 *
 * <p>We do not repair the indicators service; we label what it gives us. This class is the label,
 * and it is derived from <b>our own arrival times</b> — the one thing we can measure ourselves —
 * never from a claim in the record.
 *
 * <p>The threshold is deliberately far above a normal bar: a 1m bar normally reaches us about 65
 * seconds after it opens (its own minute, plus ~5 s), so three minutes cannot be tripped by
 * ordinary jitter and was tripped by every bar in that window by two orders of magnitude.
 */
public final class FeedGap {

    /** A bar later than this after its own start was not live when it described the market. */
    static final long LATE_AFTER_MS = 180_000L;

    private long earliestLateStartMs = Long.MAX_VALUE;
    private long latestLateStartMs = Long.MIN_VALUE;
    private long lateBars;

    /**
     * One bar, with the instant it reached us.
     *
     * <p>Only the window's edges are kept. A gap is one outage in practice, and two outages in one
     * session would widen these edges to cover both — which is the honest summary for a label of a
     * few characters, and the per-bar detail stays on {@code /footprint} and {@code /recent-full}.
     */
    public void observe(long barStartMs, long arrivedMs) {
        if (arrivedMs - barStartMs <= LATE_AFTER_MS) {
            return;
        }
        lateBars++;
        earliestLateStartMs = Math.min(earliestLateStartMs, barStartMs);
        latestLateStartMs = Math.max(latestLateStartMs, barStartMs);
    }

    /** How many bars arrived late. Zero is the normal day, and it is a fact worth publishing. */
    public long lateBars() {
        return lateBars;
    }

    /**
     * The label for the headline, or null when no bar arrived late.
     *
     * <p>Short on purpose — the reviewer asked for a few characters on the {@code vwap|} line,
     * because the owner reads that line at a glance and a sentence there would not be read at all.
     */
    public String label() {
        if (lateBars == 0) {
            return null;
        }
        return "gap " + ReadablePage.hms(earliestLateStartMs, ReadablePage.NY).substring(0, 5)
                + "-" + ReadablePage.hms(latestLateStartMs, ReadablePage.NY).substring(0, 5);
    }
}

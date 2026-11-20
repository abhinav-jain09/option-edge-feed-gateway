package app.feedgateway.readable;

import java.util.Collection;

/**
 * A window of the session whose bars were <b>published long after the minutes they describe</b>.
 *
 * <p><b>Why the page must say this.</b> On 2026-11-20 the ES feed stopped at 18:16:05 New York and
 * resumed at 23:46:53. The bars for those five and a half hours were backfilled afterwards —
 * measured median publication lag inside the window <b>9,863 s (2h44m)</b>, maximum 5h29m, against
 * ~5 s on either side of it. The data therefore exists on the platform, but the indicators
 * service's session VWAP did not take it in: the tape's 1m bars total 35,218 contracts for the
 * session while the service's own {@code session.volume.cumulativeAnalysis} read 17,208. So the
 * VWAP the owner trades against was built without a large part of the session, and nothing in the
 * record says so — the service has {@code FEED_GAP}, {@code STALE_FEED} and {@code RETENTION_GAP}
 * in its reason vocabulary and reports {@code DEADLINE_FINALIZED} instead. We do not repair the
 * indicators service; we label what it gives us.
 *
 * <p><b>The lateness is the BROKER's publish time against the bar's own minute</b>, and the window
 * is {@link #measure measured afresh} from a set of records rather than accumulated as they arrive.
 * The first version of this class used the gateway's own wall clock at arrival, which the reviewer
 * rejected for a reason I had not seen: a restart rewinds nothing and re-reads nothing, so after a
 * restart every bar either looks late against its own minute (naming the whole session a gap) or is
 * never seen at all (losing the real gap). <b>#898's own rollout is such a restart</b>, so the
 * label would have disappeared exactly when it was first needed. The broker's stamp travels with
 * the record, so the same records always give the same window — which is why this takes a
 * collection and replaces its state, instead of counting events. The measurement is a pure
 * function of the records, and that is the whole of its restart-invariance.
 *
 * <p>The threshold is deliberately far above a normal bar: a 1m bar is normally published about 65
 * seconds after it opens (its own minute, plus ~5 s), so three minutes cannot be tripped by
 * ordinary jitter and was tripped by every bar in that window by two orders of magnitude.
 */
public final class FeedGap {

    /** A bar published later than this after it CLOSED was not live when it described the market. */
    static final long LATE_AFTER_MS = 180_000L;

    /**
     * One bar: the minute it describes, the instant it CLOSED, and the instant the broker holds
     * for the record carrying it.
     *
     * <p>Both times are here because they answer different questions, and conflating them is the
     * defect this record's shape exists to prevent. <b>Lateness is measured from the close</b>
     * ({@code barEndMs}), because a bar cannot be published before it closes: the topic carries
     * five timeframes — measured live on 2026-11-20: 30s, 1m, 5m, 15m, 30m — so a 30m bar is
     * {@code 1,800 s} after its own START by construction, and a threshold applied to the start
     * flags every bar above 1m. That is exactly what shipped: the page read
     * {@code gap 18:00-01:45}, naming the whole session, which is the failure the reviewer
     * predicted for a restart and which arrived instead through the timeframes. <b>The window is
     * named from the start</b> ({@code barStartMs}), because what the owner needs is the minutes
     * the missing bars describe, not the minutes they were written in.
     */
    public record Published(long barStartMs, long barEndMs, long publishedMs) {

        /** How long after this bar CLOSED the broker holds its record. */
        long lagMs() {
            return publishedMs - barEndMs;
        }

        /**
         * This bar's timeframe, which it carries without a field of its own.
         *
         * <p>{@code barEndMs - barStartMs} <i>is</i> the width, so the timeframe needs no extra
         * component and cannot disagree with the two times already here. That matters for
         * {@link FeedGap#measure}: the window is named from the smallest timeframe alone, and a
         * width taken from a separate field could be set while the times said otherwise.
         */
        long widthMs() {
            return barEndMs - barStartMs;
        }
    }

    private long earliestLateStartMs = Long.MAX_VALUE;
    private long latestLateStartMs = Long.MIN_VALUE;
    private long lateBars;
    private long barsMeasured;
    private long finestWidthMs;

    /**
     * Measure the window from these records, replacing whatever was measured before.
     *
     * <p>Replacing rather than accumulating is deliberate: the same records must always produce the
     * same window, whether this is the first measurement after a start-up or the twentieth of the
     * session. An empty collection measures nothing and <b>leaves the previous measurement
     * standing</b> — a drain that failed or a window with no bars in it is not evidence that the
     * gap has closed, and the one thing worse than no label is a label that flickers.
     */
    public void measure(Collection<Published> bars) {
        if (bars == null || bars.isEmpty()) {
            return;
        }
        // THE WINDOW IS NAMED FROM THE SMALLEST TIMEFRAME ONLY (C-96 item 3).
        //
        // Measuring lateness from barEndMs stopped the whole session being called a gap, but it
        // left the window's START wrong in a way that looked right: the page read
        // `gap 18:00-23:43` while the feed died at 18:16:05. A 30-minute bar that OPENED at 18:00
        // closed at 18:30, inside the outage, and was published hours late -- so it is genuinely
        // late, and its barStartMs of 18:00 dragged the window's start back sixteen minutes. Every
        // wider timeframe does the same thing by its own width; the 30-minute bar simply does it
        // worst. A window built from mixed widths therefore names minutes the feed was alive.
        //
        // The smallest width PRESENT is used rather than a hardcoded 30s, because the topic's set
        // of timeframes is a property of the producer and not of this class: on 2026-11-20 it
        // carries 30s, 1m, 5m, 15m and 30m, and a literal would silently measure nothing the day
        // the finest one changed. The width comes from barEndMs - barStartMs, so it cannot
        // disagree with the times the lateness is measured from.
        long finest = Long.MAX_VALUE;
        for (Published b : bars) {
            if (b != null && b.widthMs() > 0L) {
                finest = Math.min(finest, b.widthMs());
            }
        }
        long earliest = Long.MAX_VALUE;
        long latest = Long.MIN_VALUE;
        long late = 0;
        long measured = 0;
        for (Published b : bars) {
            if (b == null || b.widthMs() != finest) {
                continue;                       // a wider bar's start is not this window's start
            }
            measured++;
            if (b.lagMs() <= LATE_AFTER_MS) {
                continue;
            }
            late++;
            earliest = Math.min(earliest, b.barStartMs());
            latest = Math.max(latest, b.barStartMs());
        }
        if (measured == 0) {
            return;     // nothing of the finest width in this drain: leave the measurement standing
        }
        barsMeasured = measured;
        finestWidthMs = finest;
        lateBars = late;
        earliestLateStartMs = earliest;
        latestLateStartMs = latest;
    }

    /** The timeframe the last measurement used, in milliseconds, or 0 before any measurement. */
    public long finestWidthMs() {
        return finestWidthMs;
    }

    /** How many bars were late in the last measurement. Zero is the normal day, and is published. */
    public long lateBars() {
        return lateBars;
    }

    /** How many bars the last measurement looked at, so "measured nothing" is distinguishable. */
    public long barsMeasured() {
        return barsMeasured;
    }

    /**
     * The label for the headline, or null when no bar was published late.
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

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
 *
 * <p><b>C-107: a LIST of episodes, not one window.</b> The single earliest-to-latest window is a
 * lie whenever the session has more than one outage: two separate ten-minute gaps eight hours
 * apart named the eight hours between them, which is most of a session the feed was alive for. An
 * episode is <b>two or more consecutive late bars of the finest width</b>, and it <b>ends at the
 * first measured bar that is not late</b> &mdash; that is what "lateness returns to normal" means,
 * taken off the measured bars themselves rather than from a clock. A single late bar is jitter
 * above the threshold and is counted but is not an episode, because one bar cannot show a window
 * the owner's VWAP was built without.
 *
 * <p><b>The daily maintenance break is not an episode.</b> The ES contract halts between 17:00 and
 * 18:00 New York, and the bars either side of it are published in a burst afterwards. That is the
 * exchange's own schedule and not a feed fault, so an episode lying wholly inside that hour is
 * dropped. One that merely overlaps it is kept whole: the 2026-11-20 outage ran 18:16 to 23:46 and
 * clipping it at the break's edge would shorten the only episode that mattered.
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

    /**
     * The New York hour the ES contract is halted in. A run of late bars wholly inside it is the
     * exchange's own schedule and not a feed fault (C-107).
     */
    static final int BREAK_START_HOUR_NY = 17;
    static final int BREAK_END_HOUR_NY = 18;

    /**
     * One run of consecutive late bars (C-107).
     *
     * @param fromStartMs the first late bar's own minute
     * @param toStartMs   the last late bar's own minute
     * @param bars        how many consecutive late bars the run holds, always two or more
     */
    public record Episode(long fromStartMs, long toStartMs, int bars) {

        public Episode {
            if (bars < 2) {
                throw new IllegalArgumentException(
                        "one late bar is jitter above the threshold, not an episode: " + bars);
            }
            if (toStartMs < fromStartMs) {
                throw new IllegalArgumentException("an episode cannot end before it began");
            }
        }

        /** The minutes this run describes, which is what the owner's VWAP was built without. */
        public long spanMs() {
            return toStartMs - fromStartMs;
        }

        /** {@code 18:16-23:43}, the New York minutes, for the page. */
        public String window() {
            return ReadablePage.hms(fromStartMs, ReadablePage.NY).substring(0, 5)
                    + "-" + ReadablePage.hms(toStartMs, ReadablePage.NY).substring(0, 5);
        }
    }

    private long lateBars;
    private long barsMeasured;
    private long finestWidthMs;
    private java.util.List<Episode> episodes = java.util.List.of();

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
        // C-107: the finest-width bars IN TIME ORDER, so "consecutive" is a property of the
        // session and not of whatever order the drain handed them over in. The previous version
        // never needed the order, because a minimum and a maximum do not care about it; a run
        // does, and reading runs off an unordered collection would split one outage into as many
        // episodes as the iteration order had inversions.
        java.util.List<Published> finestBars = new java.util.ArrayList<>();
        for (Published b : bars) {
            if (b != null && b.widthMs() == finest) {
                finestBars.add(b);
            }
        }
        if (finestBars.isEmpty()) {
            return;     // nothing of the finest width in this drain: leave the measurement standing
        }
        finestBars.sort(java.util.Comparator.comparingLong(Published::barStartMs));

        long late = 0;
        java.util.List<Episode> found = new java.util.ArrayList<>();
        long runFrom = -1L;
        long runTo = -1L;
        int runBars = 0;
        for (Published b : finestBars) {
            if (b.lagMs() > LATE_AFTER_MS) {
                late++;
                if (runBars == 0) {
                    runFrom = b.barStartMs();
                }
                runTo = b.barStartMs();
                runBars++;
                continue;
            }
            // THE RUN ENDS HERE: a measured bar that is not late is lateness returning to normal.
            closeRun(found, runFrom, runTo, runBars);
            runBars = 0;
        }
        closeRun(found, runFrom, runTo, runBars);

        barsMeasured = finestBars.size();
        finestWidthMs = finest;
        lateBars = late;
        episodes = java.util.List.copyOf(found);
    }

    /**
     * Add a finished run as an episode, unless it is one bar or lies wholly in the daily break.
     */
    private static void closeRun(java.util.List<Episode> into, long from, long to, int bars) {
        if (bars < 2) {
            return;                 // one late bar is jitter, not a window the VWAP missed
        }
        if (whollyInTheBreak(from, to)) {
            return;                 // the exchange's own halt, not a feed fault
        }
        into.add(new Episode(from, to, bars));
    }

    /** True where both ends of the run fall inside the same New York maintenance hour. */
    static boolean whollyInTheBreak(long fromMs, long toMs) {
        java.time.ZonedDateTime a =
                java.time.Instant.ofEpochMilli(fromMs).atZone(ReadablePage.NY);
        java.time.ZonedDateTime b =
                java.time.Instant.ofEpochMilli(toMs).atZone(ReadablePage.NY);
        return a.toLocalDate().equals(b.toLocalDate())
                && a.getHour() >= BREAK_START_HOUR_NY && a.getHour() < BREAK_END_HOUR_NY
                && b.getHour() >= BREAK_START_HOUR_NY && b.getHour() < BREAK_END_HOUR_NY;
    }

    /**
     * <b>C-107</b>: every late episode of the last measurement, in time order, longest-first
     * ordering left to the caller that needs it.
     */
    public java.util.List<Episode> episodes() {
        return episodes;
    }

    /** The two longest episodes by span, longest first (C-107: "show the two longest"). */
    public java.util.List<Episode> twoLongest() {
        java.util.List<Episode> bySpan = new java.util.ArrayList<>(episodes);
        bySpan.sort(java.util.Comparator.comparingLong(Episode::spanMs).reversed()
                .thenComparingLong(Episode::fromStartMs));
        return java.util.List.copyOf(bySpan.subList(0, Math.min(2, bySpan.size())));
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
        if (episodes.isEmpty()) {
            // C-107: late bars with no episode among them is jitter above the threshold, and
            // naming it a gap on the owner's vwap| line would cry wolf. The count is still
            // published through lateBars() for anyone who wants it.
            return null;
        }
        StringBuilder s = new StringBuilder(40);
        s.append("gap ").append(episodes.size()).append(episodes.size() == 1 ? " ep " : " eps ");
        java.util.List<Episode> longest = twoLongest();
        for (int i = 0; i < longest.size(); i++) {
            if (i > 0) {
                s.append(", ");
            }
            s.append(longest.get(i).window());
        }
        return s.toString();
    }
}

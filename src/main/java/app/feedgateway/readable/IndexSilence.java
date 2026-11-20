package app.feedgateway.readable;

import java.util.List;

/**
 * <b>C-98: the previous cash close is the last index print before the most recent long silence.</b>
 *
 * <p><b>Why the obvious rule was wrong.</b> {@code ReferencePrice} said, in a comment that read as
 * a fact: <i>"the newest one at or before the instant IS the previous cash close, because the index
 * prints only while the cash session is open"</i>. That premise is false, and it was measured false
 * on 2026-11-20: {@code underlying.spx.price} published {@code source=NATIVE_SPX_INDEX} /
 * {@code quality=LIVE_INDEX} <b>once per second through the pre-market</b> — thirteen consecutive
 * records from 10:50:53Z to 10:51:05Z. So "the newest index print" became a price from this morning,
 * the page printed {@code close|7160.54|+0.00}, and the night's start marched forward behind it and
 * cleared the grid.
 *
 * <p><b>Why freezing the reference was not enough either.</b> My first fix held the reference still
 * once the night had a start. That protects a running process and not a restarting one: every roll
 * restarts the gateway <i>while the index is printing</i>, and the start-up resolution would pick a
 * pre-market print again. The hole was in the resolution, so the resolution is what changed.
 *
 * <p><b>The rule, and what makes it self-sufficient.</b> The reference is the last index print
 * <b>before the most recent gap of {@link #SILENCE_MS} or more</b> in that feed. Yesterday that is
 * 16:49:16 New York, 7175.83. It needs no session calendar and no second copy of a market rule —
 * only the feed's own silence, which is a property of the data. And it <b>rolls by itself</b>:
 * tonight, once the index has been quiet for three hours, today's last print becomes the reference.
 * Until that silence has happened it stays on yesterday's, which is the correct answer for every
 * instant in between.
 *
 * <p><b>One rule, used by both paths.</b> {@link #observe} is incremental and O(1), so the live
 * consumer feeds it tick by tick; {@link #resolveFrom} runs the same {@code observe} over a
 * history slice, so the start-up resolution and the running service cannot disagree. There is no
 * second implementation to drift.
 *
 * <p><b>An unresolved reference is an absence with a reason, never a price.</b> Until a silence has
 * been seen there is no previous close, and the page says so — {@code close|absent|…}. A live price
 * under that label is the defect this class exists to prevent, so producing one is not among its
 * outcomes.
 */
public final class IndexSilence {

    /**
     * The gap that separates one session's index prints from the next, three hours.
     *
     * <p>One named constant, and the figure is chosen to sit in the wide middle of what it has to
     * separate: the index prints at least once a second while it is printing, and the quiet between
     * the end of one day's prints and the start of the next is measured in hours — on 2026-11-19 it
     * ran from 16:49:16 New York to the pre-market, six hours and more. Three hours is far above any
     * within-session interruption and far below the real overnight quiet, so neither a brief feed
     * stall nor a holiday can be mistaken for the other.
     */
    public static final long SILENCE_MS = 3L * 3_600_000L;

    private final long silenceMs;
    private ReadablePage.Price previous;          // the newest index print seen so far
    private ReadablePage.Price reference;         // the last print before the newest long silence
    private boolean anchored;                     // C-133: resolved from history, not from a gap
    private long silencesSeen;

    public IndexSilence() {
        this(SILENCE_MS);
    }

    IndexSilence(long silenceMs) {
        this.silenceMs = silenceMs;
    }

    /**
     * One index print, in time order.
     *
     * <p>Out-of-order prints are ignored rather than folded in: the rule is about a gap between
     * consecutive prints, and a record arriving late after a seek would manufacture a silence that
     * the feed never had. The caller's own ordering guarantee is therefore enforced here instead of
     * assumed.
     *
     * @param print an index print, as published; anything else is not this class's business
     */
    public void observe(ReadablePage.Price print) {
        if (print == null) {
            return;
        }
        if (previous != null && print.epochMs() < previous.epochMs()) {
            return;                               // out of order: not a gap, just a late arrival
        }
        if (previous != null && print.epochMs() - previous.epochMs() >= silenceMs) {
            // A silence just ended. The print BEFORE it is the close of the session that ended --
            // unless the reference was resolved from history and is therefore ANCHORED, in which
            // case the gap is counted without being acted on (C-133).
            if (!anchored) {
                reference = previous;
            }
            silencesSeen++;
        }
        previous = print;
    }

    /**
     * <b>Time has passed, and a silence that is still running is still a silence.</b>
     *
     * <p>This half was missing from my first implementation, and the existing suite found it. With
     * only {@link #observe}, a silence became visible when the <b>next</b> index print arrived — so
     * all night, from the last print until the following morning's first one, there was no
     * reference at all and the page would have shown {@code close|absent} for twelve hours. That is
     * worse than the defect being fixed. The reviewer's own words are "the most recent silence of
     * three hours or more", and a gap that has not ended yet is the most recent one.
     *
     * <p><b>The clock is the feed's, not the wall's.</b> It is driven by the timestamps of the
     * records flowing past — overnight the price topic carries the ES-basis derivation once a
     * second, which is a clock this class can read without deriving anything and which a replay
     * reproduces exactly. A wall clock here would make a replay disagree with the live run, which
     * is the whole reason {@code ReferencePrice} takes a history seam instead of calling Kafka.
     *
     * @param nowMs the newest instant this service has seen on the feed
     */
    public void observeClock(long nowMs) {
        if (previous == null || reference == previous || anchored) {
            return;
        }
        if (nowMs - previous.epochMs() >= silenceMs) {
            reference = previous;
            silencesSeen++;
        }
    }

    /** Run the same rule over a history slice, which must be in time order. */
    public static IndexSilence resolveFrom(List<ReadablePage.Price> prints) {
        return resolveFrom(prints, 0L);
    }

    /**
     * Run the same rule over a history slice, which must be in time order, as at {@code nowMs}.
     *
     * <p>{@code nowMs} closes a TRAILING silence: a slice that ends in the middle of the night has
     * no print after the gap, and without this the resolution would find no reference exactly when
     * the overnight page needs one. Zero means "do not consider a trailing silence", which is what
     * a caller with no clock should say rather than guess.
     */
    public static IndexSilence resolveFrom(List<ReadablePage.Price> prints, long nowMs) {
        IndexSilence silence = new IndexSilence();
        if (prints != null) {
            for (ReadablePage.Price p : prints) {
                silence.observe(p);
            }
        }
        if (nowMs > 0L) {
            silence.observeClock(nowMs);
        }
        return silence;
    }

    /** The previous cash close, or null where no long silence has been seen yet. */
    public ReadablePage.Price reference() {
        return reference;
    }

    /** Why there is no reference, or null when there is one. */
    public String absenceReason() {
        if (reference != null) {
            return null;
        }
        if (previous == null) {
            return "no index print has been seen at all, so there is no close to name";
        }
        return "no silence of " + (silenceMs / 3_600_000L) + " hours or more has been seen in the "
                + "index feed, so the session this print belongs to has not ended yet";
    }

    /** How many long silences this instance has seen. Zero with a reference is impossible. */
    public long silencesSeen() {
        return silencesSeen;
    }

    /** The newest index print observed, whether or not it is the reference. For the instrument. */
    public ReadablePage.Price newest() {
        return previous;
    }

    /** Adopt a reference resolved by the same rule over history, when none has been seen live. */
    void adopt(ReadablePage.Price resolved) {
        if (resolved != null && reference == null) {
            reference = resolved;
            anchored = true;
        }
    }

    /**
     * <b>C-133: a close RESOLVED FROM HISTORY is anchored, and a later gap cannot rewrite it.</b>
     *
     * <p>Until C-133 an adopted reference was an ordinary one: a silence seen afterwards, or a
     * clock reading that closed a trailing gap, would replace it. That is harmless while every
     * silence is a real session boundary, and it is not harmless once we know how a silence can be
     * manufactured — a feed stall of three hours on a quiet afternoon would otherwise rewrite the
     * owner's previous close in the middle of his night, and he would have no way to tell. A
     * reference that came out of a deliberate read of history is the better evidence of the two,
     * so it holds.
     *
     * <p><b>What this gives up, stated here rather than left to be found at 16:00:</b> an anchored
     * reference does not roll to today's close on its own; it holds the prior session's until the
     * next start-up resolution. That was already true of the frozen reference this replaces.
     * Rolling it live needs a cash-close boundary applied to the running service, which is the open
     * item in the Discussion tab and not something to infer from a tick.
     */
    boolean anchored() {
        return anchored;
    }
}

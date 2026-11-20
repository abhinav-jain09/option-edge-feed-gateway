package app.feedgateway.readable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * <b>When each published figure last CHANGED</b>, so a label that says a figure is latched can also
 * say whether it is still moving.
 *
 * <p><b>The defect this closes.</b> The page prints {@code 7130.21(LATCHED)} and a reader takes
 * that for a frozen number. It is not. Measured on {@code es.options.indicators.snapshot.current},
 * twelve consecutive records from 12:24:52 to 12:25:52 New York on 2026-11-20 carried the session
 * VWAP as <b>thirteen distinct values</b> &mdash; 7130.4788 down to 7130.2148, drifting 0.264
 * points in a minute &mdash; and <b>every one of them</b> was {@code state=INCOMPLETE},
 * {@code reason=DEADLINE_FINALIZED}, {@code incompleteKind=LATCHED}. What the indicators service
 * latched is its <b>coverage verdict</b>: one 30-second bar was partial, so the family was marked
 * incomplete and never un-marked for the rest of the session (C-113). The value kept advancing with
 * every tick.
 *
 * <p>So {@code LATCHED} alone tells the owner the wrong thing in the common case and the right
 * thing in the rare one, and nothing on the page separates them. This class is what separates them,
 * <b>by measurement rather than by assumption</b>: it remembers each figure's last value and the
 * instant it last changed, and the page prints the elapsed time. A figure that moved three seconds
 * ago and one that has not moved in four minutes then read differently, and neither reading is a
 * guess about what {@code LATCHED} ought to mean.
 *
 * <p><b>No threshold.</b> There is deliberately no "stale after N seconds" rule here: the page
 * prints how long ago the figure moved and lets the reader judge. A threshold would be this
 * estate's own opinion about the producer's cadence, and the producer's cadence is not ours.
 */
public final class LatchWatch {

    /** A ceiling on the figures remembered, to bound memory. Measured need: a handful. */
    static final int MAX_FIGURES = 512;

    /**
     * @param changedAtMs when this value was first seen to DIFFER from the one before it, or
     *                    {@code -1} where this figure has only ever been read once. One reading
     *                    cannot say when a figure moved, and reporting 0s from it would be a claim
     *                    this gateway has not earned.
     */
    private record Seen(double value, long changedAtMs) {
    }

    private final Map<String, Seen> seen = new ConcurrentHashMap<>();

    /**
     * Take one reading of a named figure.
     *
     * <p><b>The instant is the record's, not ours</b>, where the caller has one: a figure's age has
     * to survive a restart of this gateway, and a clock of our own would restart with it.
     *
     * @param name  the figure, e.g. {@code vwapes.vwap}
     * @param value the figure as published, or null where the producer published none
     * @param atMs  the instant this reading belongs to
     */
    public void observe(String name, Double value, long atMs) {
        if (name == null || value == null || atMs <= 0L) {
            return;
        }
        Seen held = seen.get(name);
        if (held == null) {
            if (seen.size() >= MAX_FIGURES) {
                return;
            }
            seen.put(name, new Seen(value, -1L));      // first reading: no change seen yet
            return;
        }
        if (Double.compare(held.value(), value) != 0) {
            seen.put(name, new Seen(value, atMs));
        }
        // An unchanged value deliberately leaves changedAtMs alone: that stamp is the age this
        // exists to report, and refreshing it on every identical reading would report zero for ever.
    }

    /**
     * How long ago this figure last changed, or null where it has never been seen to change.
     *
     * <p>Null on the FIRST reading too, which is honest: one reading cannot say when a figure moved.
     */
    public Long movedAgoMs(String name, long nowMs) {
        Seen held = seen.get(name);
        if (held == null || nowMs <= 0L || held.changedAtMs() < 0L) {
            return null;
        }
        return Math.max(0L, nowMs - held.changedAtMs());
    }

    /** How many figures are remembered, for a readiness probe. */
    public int figuresHeld() {
        return seen.size();
    }

    /**
     * The cell the page appends inside a figure's state brackets: {@code ,moved 3s} where the
     * figure has been seen to change, and nothing at all where it has not.
     *
     * <p>Nothing rather than a word, because "this gateway has only seen this figure once" is a
     * fact about US and not about the figure, and the page should not spend a reader's attention on
     * it.
     */
    public String movementCell(String name, long nowMs) {
        Long ago = movedAgoMs(name, nowMs);
        if (ago == null) {
            return "";
        }
        return ",moved " + Ago.of(ago);
    }
}

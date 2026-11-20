package app.feedgateway.readable;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The plain words at the top of a page saying which of its inputs have stopped, and since when.
 *
 * <p><b>Why these lines come before any table.</b> On the night of 2026-11-19 the es4 trade path
 * stopped at 18:16 New York. The footprint bars stopped with it, and so did the ES indicators
 * snapshot — which is where the VWAP and the holding times come from. Every one of those columns
 * then went on showing its last value, correctly stamped but hours old, and a reader coming to the
 * page at 06:30 would have found three columns that looked current and were not. A stalled input
 * that is only discoverable by comparing timestamps is a stalled input nobody discovers.
 *
 * <p>Each line names the input, the New York instant of its newest record, how long ago that was,
 * and <b>which parts of the page below are frozen as a result</b> — because the reader's question
 * is never "is the feed up" but "can I trust this number".
 *
 * <p>An input with no record at all is a different statement from one that stopped, and says so:
 * "nothing ever received" cannot be given an age.
 */
public final class StalledInputs {

    /**
     * How long an input may be quiet before it is called stalled. Deliberately generous: these
     * feeds are bar- and snapshot-driven and a gap of a minute or two is ordinary, while the fault
     * this exists to surface lasted hours. A bound tight enough to fire on ordinary quiet would
     * train the reader to ignore the line.
     */
    public static final long STALLED_AFTER_MS = 10L * 60_000L;

    /** One input a page depends on. */
    public record Input(String name, long newestMs, String affects) {
    }

    private StalledInputs() {
    }

    /**
     * The banner, or an empty string when every input is current.
     *
     * @param nowMs  the write instant
     * @param inputs the inputs THIS page depends on, in the order they should be read
     */
    public static String banner(long nowMs, List<Input> inputs) {
        if (inputs == null || inputs.isEmpty()) {
            return "";
        }
        List<String> lines = new ArrayList<>();
        for (Input in : inputs) {
            if (in == null || in.name() == null) {
                continue;
            }
            if (in.newestMs() <= 0L) {
                lines.add("STALLED INPUT — " + in.name() + ": nothing ever received. "
                        + in.affects() + " below are empty, not stale.");
                continue;
            }
            long age = nowMs - in.newestMs();
            if (age > STALLED_AFTER_MS) {
                lines.add("STALLED INPUT — " + in.name() + ": no record since "
                        + ReadablePage.hms(in.newestMs(), ReadablePage.NY) + " New York ("
                        + ago(age) + " ago). " + in.affects()
                        + " below are frozen at that moment, not current.");
            }
        }
        if (lines.isEmpty()) {
            return "";
        }
        return String.join("\n", lines) + "\n";
    }

    /** A gap in the units a reader thinks in, not milliseconds. */
    static String ago(long ms) {
        long minutes = Math.max(0, ms / 60_000L);
        if (minutes < 60) {
            return minutes + "m";
        }
        return String.format(Locale.ROOT, "%dh%02dm", minutes / 60, minutes % 60);
    }

    /** For a test or a log: the instant as this class reads it. */
    static Instant at(long ms) {
        return Instant.ofEpochMilli(ms);
    }
}

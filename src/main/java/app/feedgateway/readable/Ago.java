package app.feedgateway.readable;

import java.util.Locale;

/**
 * How long ago, to the SECOND under a minute.
 *
 * <p><b>Why not {@link StalledInputs#ago}.</b> That one rounds to whole minutes, which is right for
 * "this input has stalled" and wrong for anything a reader acts on inside a minute: it prints 12
 * seconds and 50 seconds alike as {@code 0m}. The owner reads a latched figure's movement, and a
 * strike's delta, while a move is in progress, and those two ages are the difference between a
 * figure he can use and one he cannot.
 *
 * <p>Above a minute the figure is already too old to trade off, so minutes are enough there and
 * hours get {@code 1h04m}.
 */
final class Ago {

    private Ago() {
    }

    static String of(long ms) {
        long seconds = Math.max(0L, ms) / 1000L;
        if (seconds < 60L) {
            return seconds + "s";
        }
        long minutes = seconds / 60L;
        if (minutes < 60L) {
            return minutes + "m";
        }
        return String.format(Locale.ROOT, "%dh%02dm", minutes / 60L, minutes % 60L);
    }
}

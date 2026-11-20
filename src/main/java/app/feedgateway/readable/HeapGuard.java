package app.feedgateway.readable;

/**
 * <b>Whether this JVM has room to build the readable page's heaviest block.</b>
 *
 * <p>This exists because of what happened on 2026-11-20. The gateway ran out of heap
 * ({@code -Xmx1536m}) about an hour and fifty minutes into the session, and from then on every
 * render and every {@code /now} request threw {@link OutOfMemoryError} from inside the overnight
 * grid. The pod's {@code restartCount} stayed <b>0</b>: liveness answered 200 throughout, so the
 * service looked healthy while the one page the owner reads answered <b>500</b> for seventeen
 * minutes before anybody fetched it.
 *
 * <p><b>The rule this restores is the project's own.</b> A read that cannot be measured is
 * published <b>absent with its reason</b>, and no single block may refuse the whole page. An
 * {@code OutOfMemoryError} out of one block broke both halves of that at once: the block was not
 * absent, it was fatal, and it took the page with it.
 *
 * <p><b>What this is not.</b> It is not a diagnosis. The allocation that failed is where the heap
 * ran out, not necessarily what retains it, and the image carries no {@code jcmd} or {@code jmap}
 * with which to take a histogram. So this class also exists to make the next occurrence
 * <b>readable</b>: {@link #line} is printed on every render, so the heap's path through a session
 * is one {@code grep} rather than a dump nobody can take.
 */
public final class HeapGuard {

    /**
     * How much of the heap must be free to attempt the block, as a share of the maximum.
     *
     * <p>Twelve per cent of 1,536 MiB is about 184 MiB. The figure is deliberately generous: the
     * block being skipped is a formatting pass over a few thousand small objects, so the cost of
     * skipping it one render too early is one page of absent overnight rows, and the cost of
     * skipping it one render too late is the whole page for the rest of the session.
     */
    public static final double FREE_SHARE_MIN = 0.12;

    private HeapGuard() {
    }

    /** Bytes the heap could still grow to. */
    static long maxBytes() {
        long max = Runtime.getRuntime().maxMemory();
        return max == Long.MAX_VALUE ? Runtime.getRuntime().totalMemory() : max;
    }

    /** Bytes in use, counting what the JVM has committed and not yet freed. */
    static long usedBytes() {
        Runtime r = Runtime.getRuntime();
        return r.totalMemory() - r.freeMemory();
    }

    /** True where the heap has less than {@link #FREE_SHARE_MIN} of itself left. */
    public static boolean low() {
        long max = maxBytes();
        if (max <= 0) {
            return false;                    // nothing to measure against: do not degrade blindly
        }
        return (double) (max - usedBytes()) / (double) max < FREE_SHARE_MIN;
    }

    /** The page's own words for the absence, with the figures in them. */
    public static String reason() {
        return "the overnight rows are ABSENT because this gateway is low on heap ("
                + describe() + "): the block is skipped rather than allowed to fail the page";
    }

    /** "used 1402 of 1536 MiB". */
    public static String describe() {
        return "used " + mib(usedBytes()) + " of " + mib(maxBytes()) + " MiB";
    }

    /** The instrument: one line per render, so a session's heap path is greppable. */
    public static String line(int nightBars, int recentBars) {
        return "readable heap " + describe() + " free=" + freePercent() + "% bars night="
                + nightBars + " recent=" + recentBars + (low() ? " LOW" : "");
    }

    private static long freePercent() {
        long max = maxBytes();
        return max <= 0 ? -1 : Math.round(100.0 * (max - usedBytes()) / max);
    }

    private static long mib(long bytes) {
        return Math.round(bytes / 1048576.0);
    }
}

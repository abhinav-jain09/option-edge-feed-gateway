package app.feedgateway.readable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * <b>C-65 (3)</b>: which side of the VWAP line price is on, how long it has been there, and the
 * session's crosses, longest holds and band attempts.
 *
 * <p><b>Observed as it happens, not recomputed against today's VWAP — and that distinction is the
 * whole correctness of this class.</b> C-65 asks for the figures to be recomputed from the tick
 * path at every write. Taken literally against the <b>current</b> VWAP that is wrong: the VWAP moves
 * all session, so comparing a tick from three hours ago with the VWAP as it stands now would put
 * that tick on whichever side suits the present, and the crossing count would change every time the
 * line moved. What is recomputed here instead is the path of <b>sides</b>: each time a snapshot
 * arrives, the price of that moment is compared with the VWAP of that moment — the only comparison
 * that is ever true — and the transitions are kept. Holding times then follow exactly.
 *
 * <p><b>ES against ES</b> (C-69): both the price and the VWAP come from the ES instrument, so the
 * basis does not enter any figure here. The SPX-equivalent is a display cell elsewhere.
 *
 * <p><b>It knows only what it has seen.</b> After a gateway restart the session's earlier crosses
 * are gone, and {@link Result#observedFromMs()} says from when the figures are true. A count that
 * silently restarted at zero would read as a quiet session rather than a short memory.
 */
public final class VwapSideTracker {

    /** Which side of the line. */
    public static final String ABOVE = "A";
    public static final String BELOW = "B";
    /** Exactly on it — a measurement, not a missing one. */
    public static final String ON = "=";

    /** At most this many band attempts are kept, newest first (C-66 prints ten). */
    public static final int MAX_ATTEMPTS = 20;

    /** One attempt at a band: when it happened and how long price stayed beyond it. */
    public record Attempt(String band, long atMs, long heldMs, boolean open) {
    }

    /** What the page prints. */
    /**
     * @param esPrice the price the side was last decided from, in ES
     * @param esVwap  the line it was compared against, in ES, from the SAME record (C-83)
     */
    public record Result(String side, long sideSinceMs, Long lastCrossMs, int crosses,
                         long longestAboveMs, long longestBelowMs, long observedFromMs,
                         List<Attempt> attempts, String anchorName, int anchorChanges,
                         Double esPrice, Double esVwap) {
    }

    private String side;
    /**
     * <b>C-83</b>: the two figures the side was last decided from, published on the {@code hold|}
     * line so the comparison is checkable on the page.
     *
     * <p>The reviewer computed the side himself from the page's {@code now|} line plus the basis
     * and got the opposite letter — correctly, because {@code now|} comes from
     * {@code underlying.spx.price} while the side is decided from the indicators record's OWN
     * price against that same record's own VWAP. The page was giving a reader two of the three
     * numbers, from different sources. These are the two the comparison actually used, both in ES.
     */
    private Double lastEsPrice;
    private Double lastEsVwap;
    private long sideSince;
    private Long lastCross;
    private int crosses;
    private long longestAbove;
    private long longestBelow;
    private long observedFrom;
    private long lastSeen;
    private String anchorName = "";
    /**
     * How many times the anchor changed under this tracker. Not decoration: see the reset in
     * {@link #observe}. The page prints it so a short path is explained rather than puzzling.
     */
    private int anchorChanges;
    private final Deque<Attempt> attempts = new ArrayDeque<>();
    private Attempt openAttempt;

    /**
     * Observe one moment: the ES price and the ES VWAP <b>as they both stood then</b>.
     *
     * @param upper1 the upper one-sigma band, or null where the producer published none
     * @param lower1 the lower one-sigma band, or null
     */
    public void observe(long atMs, double esPrice, double esVwap, Double upper1, Double lower1,
            String anchor) {
        if (atMs <= 0) {
            return;
        }
        if (observedFrom == 0) {
            observedFrom = atMs;
        }
        lastEsPrice = esPrice;
        lastEsVwap = esVwap;
        if (anchor != null && !anchor.isBlank()) {
            if (!anchorName.isEmpty() && !anchorName.equals(anchor)) {
                // THE ANCHOR CHANGED, so this is a different line and the old path cannot be
                // continued onto it. forHolding() moves from vwapAnalysis to vwapRth once RTH
                // leaves WARMING_UP, and the two lines sit at different prices -- so carrying the
                // side across would record a cross that price never made, and the owner would be
                // shown a crossing caused by our own choice of anchor. The path therefore RESTARTS
                // here, observedFromMs moves to this instant so the page tells the truth about how
                // much was rebuilt, and nothing is counted across the boundary.
                side = null;
                sideSince = 0;
                lastCross = null;
                crosses = 0;
                longestAbove = 0;
                longestBelow = 0;
                observedFrom = atMs;
                attempts.clear();
                openAttempt = null;
                anchorChanges++;
            }
            this.anchorName = anchor;
        }
        String now = esPrice > esVwap ? ABOVE : esPrice < esVwap ? BELOW : ON;
        if (side == null) {
            side = now;
            sideSince = atMs;
            lastSeen = atMs;
            trackBand(atMs, esPrice, upper1, lower1);
            return;
        }
        if (!now.equals(side)) {
            // A cross. Credit the stretch just ended to whichever side it was, then start the new
            // one AT this instant: a gap between the two would lose time that price was somewhere.
            long held = Math.max(0, atMs - sideSince);
            if (ABOVE.equals(side)) {
                longestAbove = Math.max(longestAbove, held);
            } else if (BELOW.equals(side)) {
                longestBelow = Math.max(longestBelow, held);
            }
            // ON is not a side anyone holds, so moving off it is not a cross and moving onto it is
            // not either: a cross is ABOVE to BELOW or back, which is what the owner is counting.
            if (!ON.equals(side) && !ON.equals(now)) {
                crosses++;
                lastCross = atMs;
            }
            side = now;
            sideSince = atMs;
        }
        lastSeen = atMs;
        trackBand(atMs, esPrice, upper1, lower1);
    }

    /**
     * Band attempts: an attempt opens when price goes beyond a band and closes when it comes back.
     * A rejection is an attempt whose held time is zero or near it, which is why the held time is
     * recorded rather than a verdict — \"rejected\" is the reader's conclusion from the number.
     */
    private void trackBand(long atMs, double esPrice, Double upper1, Double lower1) {
        String beyond = null;
        if (upper1 != null && esPrice > upper1) {
            beyond = "U1";
        } else if (lower1 != null && esPrice < lower1) {
            beyond = "L1";
        }
        if (beyond == null) {
            if (openAttempt != null) {
                close(atMs);
            }
            return;
        }
        if (openAttempt == null) {
            openAttempt = new Attempt(beyond, atMs, 0L, true);
            return;
        }
        if (!openAttempt.band().equals(beyond)) {
            // Straight from one band to the other without passing between: close the first here.
            close(atMs);
            openAttempt = new Attempt(beyond, atMs, 0L, true);
            return;
        }
        openAttempt = new Attempt(openAttempt.band(), openAttempt.atMs(),
                atMs - openAttempt.atMs(), true);
    }

    private void close(long atMs) {
        attempts.addFirst(new Attempt(openAttempt.band(), openAttempt.atMs(),
                Math.max(0, atMs - openAttempt.atMs()), false));
        while (attempts.size() > MAX_ATTEMPTS) {
            attempts.removeLast();
        }
        openAttempt = null;
    }

    /** The figures as they stand. Null where nothing has been observed yet. */
    public Result result() {
        if (side == null) {
            return null;
        }
        long heldNow = Math.max(0, lastSeen - sideSince);
        long above = ABOVE.equals(side) ? Math.max(longestAbove, heldNow) : longestAbove;
        long below = BELOW.equals(side) ? Math.max(longestBelow, heldNow) : longestBelow;
        List<Attempt> out = new ArrayList<>();
        if (openAttempt != null) {
            out.add(openAttempt);
        }
        out.addAll(attempts);
        return new Result(side, sideSince, lastCross, crosses, above, below, observedFrom,
                List.copyOf(out), anchorName, anchorChanges, lastEsPrice, lastEsVwap);
    }
}

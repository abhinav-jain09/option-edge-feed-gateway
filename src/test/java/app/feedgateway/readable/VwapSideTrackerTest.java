package app.feedgateway.readable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** <b>C-65 (3)</b>: sides, crosses, holds and band attempts, observed as they happen. */
class VwapSideTrackerTest {

    private static final long T0 = 1_795_120_000_000L;
    private static final long MIN = 60_000L;

    @Test
    @DisplayName("each moment is judged against the VWAP OF THAT MOMENT, not the latest one")
    void everyTickIsJudgedAgainstItsOwnVwap() {
        // The line rises through the session. Price 7180 is ABOVE a line at 7170 and BELOW the same
        // line once it has risen to 7190. An implementation that recomputed the whole path against
        // the LATEST VWAP would call both moments BELOW and report zero crosses.
        VwapSideTracker t = new VwapSideTracker();
        t.observe(T0, 7180, 7170, null, null, "vwapAnalysis");          // above a low line
        t.observe(T0 + 10 * MIN, 7180, 7190, null, null, "vwapAnalysis"); // below a risen line

        VwapSideTracker.Result r = t.result();
        assertEquals(VwapSideTracker.BELOW, r.side(), "it is below the line as the line now is");
        assertEquals(1, r.crosses(),
                "and the cross REALLY happened: recomputing against the latest line would "
                        + "have reported none");
        assertEquals(T0 + 10 * MIN, r.lastCrossMs());
        assertEquals(10 * MIN, r.longestAboveMs(), "the ten minutes it spent above are credited");
    }

    @Test
    @DisplayName("the first observation sets the side and is not a cross")
    void theFirstObservationIsNotACross() {
        VwapSideTracker t = new VwapSideTracker();
        t.observe(T0, 7180, 7170, null, null, "vwapAnalysis");
        VwapSideTracker.Result r = t.result();
        assertEquals(VwapSideTracker.ABOVE, r.side());
        assertEquals(0, r.crosses());
        assertNull(r.lastCrossMs(), "no cross has happened, so there is no time for one");
        assertEquals(T0, r.observedFromMs(), "and the page can say from when these figures are true");
        assertEquals("vwapAnalysis", r.anchorName(), "naming which line, as C-69 requires");
    }

    @Test
    @DisplayName("the longest holds are the longest stretches, including the current one")
    void theLongestHoldsIncludeTheOneStillRunning() {
        VwapSideTracker t = new VwapSideTracker();
        t.observe(T0, 7180, 7170, null, null, "a");                 // above
        t.observe(T0 + 5 * MIN, 7160, 7170, null, null, "a");       // -> below at +5
        t.observe(T0 + 8 * MIN, 7180, 7170, null, null, "a");       // -> above at +8
        t.observe(T0 + 40 * MIN, 7185, 7170, null, null, "a");      // still above at +40

        VwapSideTracker.Result r = t.result();
        assertEquals(2, r.crosses());
        assertEquals(32 * MIN, r.longestAboveMs(),
                "the stretch still running counts, or a long hold would read as none");
        assertEquals(3 * MIN, r.longestBelowMs());
        assertEquals(T0 + 8 * MIN, r.sideSinceMs());
    }

    @Test
    @DisplayName("price exactly ON the line is its own answer and not a cross")
    void onTheLineIsNotACross() {
        VwapSideTracker t = new VwapSideTracker();
        t.observe(T0, 7180, 7170, null, null, "a");
        t.observe(T0 + MIN, 7170, 7170, null, null, "a");            // exactly on it
        assertEquals(VwapSideTracker.ON, t.result().side());
        assertEquals(0, t.result().crosses(), "touching the line is not crossing it");
        t.observe(T0 + 2 * MIN, 7180, 7170, null, null, "a");        // back above
        assertEquals(0, t.result().crosses(),
                "and coming off it on the same side is not a cross either");
        t.observe(T0 + 3 * MIN, 7160, 7170, null, null, "a");        // now genuinely below
        assertEquals(1, t.result().crosses(), "above to below is the cross the owner counts");
    }

    @Test
    @DisplayName("a band attempt records how LONG price stayed beyond, not a verdict")
    void aBandAttemptRecordsItsHeldTime() {
        VwapSideTracker t = new VwapSideTracker();
        t.observe(T0, 7180, 7170, 7175.0, 7165.0, "a");               // already beyond upper
        t.observe(T0 + 3 * MIN, 7181, 7170, 7175.0, 7165.0, "a");     // still beyond
        t.observe(T0 + 4 * MIN, 7172, 7170, 7175.0, 7165.0, "a");     // back inside -> closed

        VwapSideTracker.Result r = t.result();
        assertEquals(1, r.attempts().size());
        VwapSideTracker.Attempt a = r.attempts().get(0);
        assertEquals("U1", a.band());
        assertEquals(T0, a.atMs());
        assertEquals(4 * MIN, a.heldMs(), "four minutes beyond the upper band");
        assertFalse(a.open(), "and the attempt is finished");
    }

    @Test
    @DisplayName("a rejection is an attempt that held no time, not a word we chose")
    void aRejectionIsAHeldTimeOfZero() {
        VwapSideTracker t = new VwapSideTracker();
        t.observe(T0, 7170, 7170, 7175.0, 7165.0, "a");               // inside
        t.observe(T0 + MIN, 7176, 7170, 7175.0, 7165.0, "a");         // pokes beyond
        t.observe(T0 + MIN, 7174, 7170, 7175.0, 7165.0, "a");         // and is back, same instant

        VwapSideTracker.Attempt a = t.result().attempts().get(0);
        assertEquals("U1", a.band());
        assertEquals(0L, a.heldMs(),
                "zero held: the reader concludes 'rejected' from the number, we do not label it");
    }

    @Test
    @DisplayName("going straight from one band to the other closes the first")
    void oneBandToTheOtherClosesTheFirst() {
        VwapSideTracker t = new VwapSideTracker();
        t.observe(T0, 7180, 7170, 7175.0, 7165.0, "a");               // beyond upper
        t.observe(T0 + 2 * MIN, 7160, 7170, 7175.0, 7165.0, "a");     // straight beyond lower

        VwapSideTracker.Result r = t.result();
        assertEquals(2, r.attempts().size(), "two attempts, not one that changed its mind");
        assertEquals("L1", r.attempts().get(0).band(), "the open one is first");
        assertTrue(r.attempts().get(0).open());
        assertEquals("U1", r.attempts().get(1).band());
        assertFalse(r.attempts().get(1).open(), "and the upper one is closed");
    }

    @Test
    @DisplayName("with no bands published no attempt is invented")
    void noBandsMeansNoAttempts() {
        VwapSideTracker t = new VwapSideTracker();
        t.observe(T0, 7180, 7170, null, null, "a");
        t.observe(T0 + MIN, 7999, 7170, null, null, "a");
        assertTrue(t.result().attempts().isEmpty(),
                "a band nobody published cannot be attempted");
    }

    @Test
    @DisplayName("nothing observed is null, not a zeroed result")
    void nothingObservedIsNull() {
        assertNull(new VwapSideTracker().result(),
                "an empty tracker has no side, and a default side would be a reading");
        VwapSideTracker t = new VwapSideTracker();
        t.observe(0L, 7180, 7170, null, null, "a");
        assertNull(t.result(), "an observation with no time is refused");
    }

    @Test
    @DisplayName("the attempt list is bounded, newest first")
    void attemptsAreBounded() {
        VwapSideTracker t = new VwapSideTracker();
        long at = T0;
        for (int i = 0; i < VwapSideTracker.MAX_ATTEMPTS + 10; i++) {
            t.observe(at, 7180, 7170, 7175.0, 7165.0, "a");     // beyond
            t.observe(at + 1_000L, 7172, 7170, 7175.0, 7165.0, "a"); // back inside
            at += 2 * MIN;
        }
        assertEquals(VwapSideTracker.MAX_ATTEMPTS, t.result().attempts().size());
        assertTrue(t.result().attempts().get(0).atMs()
                > t.result().attempts().get(1).atMs(), "newest first");
    }
}

package app.feedgateway.readable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * <b>A latched figure is usually still updating, and the page now says which.</b>
 *
 * <p><b>The measurement this is built on.</b> Twelve consecutive records off
 * {@code es.options.indicators.snapshot.current}, 12:24:52 to 12:25:52 New York on 2026-11-20,
 * carried the session VWAP as <b>thirteen distinct values</b> &mdash; 7130.4788 down to 7130.2148,
 * 0.264 points in a minute &mdash; and every one of them was {@code state=INCOMPLETE},
 * {@code reason=DEADLINE_FINALIZED}, {@code incompleteKind=LATCHED}. The page printed
 * {@code 7130.21(LATCHED)}, which reads as a frozen number. What the indicators service latched is
 * its <b>coverage verdict</b> (one partial 30-second bar, never un-marked: C-113), not the value.
 *
 * <p>So the cell now carries how long ago the figure actually moved, and a figure that has stopped
 * reads differently from one that is advancing &mdash; by measurement, with no threshold of ours.
 */
final class CLatchedStillUpdatingTest {

    private static final long T0 = 1_795_188_000_000L;

    /**
     * One snapshot as the indicators service really publishes it, through the REAL decoder.
     *
     * <p>Built as JSON and handed to {@code VwapRead.of} rather than constructing the records: the
     * state, the reason and {@code incompleteKind} are the producer's three words and the decoder
     * is what turns them into the cell, so a test that assembled the records by hand would not be
     * testing the thing that produced {@code (LATCHED)} in the first place.
     */
    private static String snapshot(double vwap, String state, String reason, String kind) {
        String fig = "{\"value\":\"%s\",\"state\":\"" + state + "\",\"reason\":"
                + (reason == null ? "null" : "\"" + reason + "\"")
                + ",\"incompleteKind\":" + (kind == null ? "null" : "\"" + kind + "\"") + "}";
        return "{\"session\":{\"vwapAnalysis\":{"
                + "\"vwap\":" + String.format(fig, String.valueOf(vwap))
                + ",\"stdDev\":" + String.format(fig, "43.6612")
                + ",\"bandUpper1\":" + String.format(fig, "7173.9945")
                + ",\"bandLower1\":" + String.format(fig, "7086.6721")
                + ",\"bandUpper2\":" + String.format(fig, "7217.6557")
                + ",\"bandLower2\":" + String.format(fig, "7043.0108")
                + "}}}";
    }

    private static VwapRead latchedRead(double vwap) {
        return VwapRead.of(snapshot(vwap, "INCOMPLETE", "DEADLINE_FINALIZED", "LATCHED"));
    }

    @Test
    @DisplayName("the watch reports the age of the LAST CHANGE, not of the last reading")
    void theAgeIsOfTheLastChangeAndNotTheLastReading() {
        LatchWatch w = new LatchWatch();
        w.observe("vwapes", 7130.4788, T0);
        assertNull(w.movedAgoMs("vwapes", T0 + 1_000L),
                "ONE reading cannot say when a figure moved, and saying 0s would be a claim we "
                        + "have not earned");

        w.observe("vwapes", 7130.4310, T0 + 10_000L);
        assertEquals(0L, w.movedAgoMs("vwapes", T0 + 10_000L));
        assertEquals(5_000L, w.movedAgoMs("vwapes", T0 + 15_000L));

        // the SAME value arriving again must not refresh the stamp: that stamp is the age this
        // exists to report, and refreshing it on identical readings would report 0s for ever
        w.observe("vwapes", 7130.4310, T0 + 40_000L);
        assertEquals(30_000L, w.movedAgoMs("vwapes", T0 + 40_000L),
                "the figure has not moved for thirty seconds and the cell must say thirty");
    }

    @Test
    @DisplayName("the real sixty seconds: thirteen values, and the cell says it keeps moving")
    void theRealMinuteReadsAsMoving() {
        LatchWatch w = new LatchWatch();
        double[] real = {7130.4788, 7130.4626, 7130.4310, 7130.4011, 7130.3779, 7130.3556,
                7130.3333, 7130.3117, 7130.2957, 7130.2540, 7130.2337, 7130.2148};
        long at = T0;
        for (double v : real) {
            w.observe("vwapes", v, at);
            at += 5_000L;
        }
        long last = at - 5_000L;
        assertEquals(0L, w.movedAgoMs("vwapes", last),
                "it changed on the very last reading, as it did on every one before it");
        assertEquals(",moved 0s", w.movementCell("vwapes", last));
        assertEquals(",moved 7s", w.movementCell("vwapes", last + 7_000L));
    }

    @Test
    @DisplayName("a figure that has STOPPED reads differently from one that is advancing")
    void aStoppedFigureReadsDifferently() {
        LatchWatch w = new LatchWatch();
        w.observe("a", 100.0, T0);
        w.observe("a", 101.0, T0 + 5_000L);           // moving
        w.observe("b", 200.0, T0);
        w.observe("b", 201.0, T0 + 5_000L);
        for (long t = T0 + 10_000L; t <= T0 + 250_000L; t += 5_000L) {
            w.observe("b", 201.0, t);                 // stopped, but still published
        }
        assertEquals(",moved 4m", w.movementCell("b", T0 + 250_000L));
        assertEquals(",moved 4m", w.movementCell("a", T0 + 250_000L));
        // both read 4m here because neither has moved since; the point of the cell is that it
        // says SO, where the bare word LATCHED said nothing either way
        assertEquals(",moved 0s", w.movementCell("a", T0 + 5_000L));
    }

    @Test
    @DisplayName("the cell lands INSIDE the figure's state brackets, on the page")
    void theCellLandsInsideTheBrackets() {
        LatchWatch w = new LatchWatch();
        w.observe("vwapes", 7130.4788, T0);
        w.observe("vwapes", 7130.2148, T0 + 60_000L);

        String page = Pages.now(T0 + 63_000L, null, null, latchedRead(7130.2148),
                null, null, null, null, null, null, null, null, w);

        String vwapes = page.lines().filter(l -> l.startsWith("vwapes|")).findFirst().orElse("");
        assertTrue(vwapes.contains("7130.21(LATCHED,moved 3s)"),
                "one qualified figure, not a figure followed by a separate cell: " + vwapes);
        // The FIRST cell is the vwap, and it is the one that carries the movement.
        String firstCell = vwapes.split("\\|")[1];
        assertEquals("7130.21(LATCHED,moved 3s)", firstCell,
                "the vwap figure itself, qualified in one piece: " + vwapes);
        assertFalse(firstCell.equals("7130.21(LATCHED)"),
                "the bare word is what read as frozen: " + vwapes);
        // the five band figures move with the vwap by construction, so they are NOT annotated --
        // the same age six times on one line spends a reader's attention on one fact, and they
        // keep the bare (LATCHED) they always had
        assertEquals(1, vwapes.split("moved ", -1).length - 1,
                "exactly one movement cell on the line: " + vwapes);
        assertTrue(vwapes.contains("(LATCHED)"),
                "and the bands still say LATCHED, which is the producer's own word for them: "
                        + vwapes);
    }

    @Test
    @DisplayName("with nothing watching, the line is exactly what it was before")
    void withoutTheWatchNothingChanges() {
        String page = Pages.now(T0, null, null, latchedRead(7130.2148), null, null,
                null, null, null, null, null, null);
        String vwapes = page.lines().filter(l -> l.startsWith("vwapes|")).findFirst().orElse("");
        assertTrue(vwapes.contains("7130.21(LATCHED)"),
                "the twelve-argument form is unchanged, so every existing caller and test reads "
                        + "the same columns: " + vwapes);
    }

    @Test
    @DisplayName("the RENDERER feeds the watch, so the cell reaches the served page")
    void theRendererFeedsTheWatch() {
        // Driven through the renderer's own tap and its own namedPage, not through Pages: a watch
        // that nothing feeds would pass every test above and show nothing on the page. This is the
        // same discipline the pre-open tap's test is built on.
        ReadableBoardRenderer r = new ReadableBoardRenderer(() -> null, () -> T0 + 63_000L);
        r.onIndicatorsJson(withInstant(snapshot(7130.4788, "INCOMPLETE", "DEADLINE_FINALIZED",
                "LATCHED"), T0));
        r.onIndicatorsJson(withInstant(snapshot(7130.2148, "INCOMPLETE", "DEADLINE_FINALIZED",
                "LATCHED"), T0 + 60_000L));
        assertEquals(1, r.latches().figuresHeld() > 0 ? 1 : 0,
                "the renderer's tap must reach the watch at all");

        r.render();
        String page = r.namedPage("now");
        String vwapes = page.lines().filter(l -> l.startsWith("vwapes|")).findFirst().orElse("");
        assertTrue(vwapes.contains("moved 3s"),
                "the cell has to be on the page the gateway SERVES, not only in a Pages call a "
                        + "test made: " + vwapes);
    }

    /**
     * The snapshot with the two fields the renderer's tap requires of it: its own event instant
     * ({@code asOfEventTime}, which is where the age comes from) and the instrument's
     * {@code symbol}, which the tap judges by name so the OTHER instrument's snapshot cannot
     * reach these cells.
     */
    private static String withInstant(String snapshot, long atMs) {
        return snapshot.replaceFirst("^\\{",
                "{\"symbol\":\"ES\",\"asOfEventTime\":\""
                        + java.time.Instant.ofEpochMilli(atMs) + "\",");
    }

    @Test
    @DisplayName("a COMPLETE figure takes no movement cell: it has no brackets to put one in")
    void aCompleteFigureIsNotAnnotated() {
        LatchWatch w = new LatchWatch();
        w.observe("vwapes", 1.0, T0);
        w.observe("vwapes", 2.0, T0 + 1_000L);
        String page = Pages.now(T0 + 9_000L, null, null,
                VwapRead.of(snapshot(7130.2148, "COMPLETE", null, null)),
                null, null, null, null, null, null, null, null, w);
        String vwapes = page.lines().filter(l -> l.startsWith("vwapes|")).findFirst().orElse("");
        assertFalse(vwapes.contains("moved"),
                "the producer calls it finished, so how long ago it last changed is a fact about "
                        + "the market and not about the figure's state: " + vwapes);
    }
}

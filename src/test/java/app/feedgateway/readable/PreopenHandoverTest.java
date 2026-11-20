package app.feedgateway.readable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The handover between the two boards, which is the whole point of the pre-open path: the owner
 * must have a readable ladder at 06:30 New York, and must never be shown the pre-open one once the
 * map service has a real board.
 */
class PreopenHandoverTest {

    private static final double SPOT = 7418.4638671875;
    private static final long NOW = 1_795_088_582_906L + 1_000L;

    private static String preopen(double strike, double netGex) {
        return "{\"symbol\":\"SPX\",\"expiry\":\"20261119\",\"strike\":" + strike
                + ",\"netGex\":" + netGex + ",\"gammaSign\":\"POSITIVE\",\"canonicalSpot\":" + SPOT
                + ",\"updatedAt\":\"2026-11-19T11:43:02.906Z\",\"staleAfterMs\":300000}";
    }

    private static final String MAP_RECORD =
            "{\"spot\":7418.46,\"strikes\":[{\"strikeCents\":742000,"
                    + "\"board\":{\"netGex\":-8.31E9,\"holdOrAccelerate\":\"ACCELERATE\"}}]}";

    @Test
    @DisplayName("with no map record, the pre-open board is on the page and names itself")
    void thePreopenBoardStandsInBeforeTheOpen() {
        ReadableBoardRenderer r = new ReadableBoardRenderer(() -> null, () -> NOW);
        r.onPreopenJson(preopen(7420.0, 2.67E8));
        String page = r.render();

        assertTrue(page.contains("GAMMA BOARD"), "the owner has a board at all");
        assertTrue(page.contains("source " + PreopenBoard.SOURCE),
                "and the page says which board it is");
        assertFalse(page.contains("No board yet"),
                "the page must not claim there is no board when the pre-open one exists");
        assertTrue(page.contains("+267M"), "the published net gamma is on it");
    }

    @Test
    @DisplayName("the map record always wins, and the handover is visible on the page")
    void theMapRecordAlwaysWins() {
        ReadableBoardRenderer r = new ReadableBoardRenderer(() -> MAP_RECORD, () -> NOW);
        r.onPreopenJson(preopen(7420.0, 2.67E8));
        String page = r.render();

        assertTrue(page.contains("source " + BoardLines.SOURCE),
                "once the map service has a board, that is the board shown");
        assertFalse(page.contains(PreopenBoard.SOURCE),
                "the pre-open board must not appear beside it");
        assertTrue(page.contains("-8.31B"), "the map record's own figure is the one printed");
        assertFalse(page.contains("+267M"),
                "the two boards are never merged -- the owner could not tell which rows were which");
    }

    @Test
    @DisplayName("C-73: the switch to the map board is announced, with the time it happened")
    void theSwitchIsAnnouncedWithItsTime() {
        long[] clock = {NOW};
        java.util.concurrent.atomic.AtomicReference<String> map = new java.util.concurrent.atomic.AtomicReference<>(null);
        ReadableBoardRenderer r = new ReadableBoardRenderer(map::get, () -> clock[0]);

        // 06:43 — the owner reads the pre-open ladder.
        r.onPreopenJson(preopen(7420.0, 2.67E8));
        String before = r.render();
        assertTrue(before.contains("source " + PreopenBoard.SOURCE), before);
        assertFalse(before.contains("switched from"), "nothing has switched yet");

        // 09:15:58 — the map service publishes its first board of the day.
        clock[0] = NOW + 9_000_000L;
        map.set(MAP_RECORD);
        String after = r.render();
        assertTrue(after.contains("source " + BoardLines.SOURCE), after);
        assertTrue(after.contains("switched from " + PreopenBoard.SOURCE + " at "),
                "the owner has been reading the other ladder for hours; the change is announced: "
                        + after.lines().filter(l -> l.contains("Spot")).findFirst().orElse(after));
        assertTrue(after.contains("New York)"), "with the time it happened");

        // And the line keeps saying WHEN it happened rather than drifting to "just now".
        String switchLine = after.lines().filter(l -> l.contains("switched from")).findFirst().orElseThrow();
        clock[0] = NOW + 12_000_000L;
        String later = r.render();
        String laterLine = later.lines().filter(l -> l.contains("switched from")).findFirst().orElseThrow();
        assertEquals(switchLine, laterLine, "the recorded instant does not move with the clock");
    }

    @Test
    @DisplayName("C-73: a previous session's pre-open board is never shown as today's")
    void aPreviousSessionIsNamedNotPassedOffAsToday() {
        // The record is from 2026-11-19; the page is written a day later.
        long nextDay = NOW + 24L * 3_600_000L;
        ReadableBoardRenderer r = new ReadableBoardRenderer(() -> null, () -> nextDay);
        r.onPreopenJson(preopen(7420.0, 2.67E8));
        String page = r.render();

        assertTrue(page.contains("PREVIOUS SESSION 2026-11-19"),
                "the board names the session it belongs to: " + page);
        assertTrue(page.contains("GAMMA BOARD"),
                "and it is still shown rather than vanishing -- a board that disappears at "
                        + "midnight New York tells the reader nothing at all");
        assertTrue(page.contains("cycle 2026-11-19"), "its own cycle date is on the heading too");
    }

    @Test
    @DisplayName("with neither board, the page still says so rather than showing nothing")
    void withNeitherBoardThePageSaysSo() {
        ReadableBoardRenderer r = new ReadableBoardRenderer(() -> null, () -> NOW);
        String page = r.render();
        assertTrue(page.contains("No board yet"), "the absence is stated: " + page);
    }
}

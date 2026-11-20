package app.feedgateway.readable;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * <b>The VWAP line never loses the last known figures.</b>
 *
 * <p>The reviewer's requirement, after the cells went from {@code 7184.26(LATCHED)} to
 * {@code SOURCE_HAS_NO_VOLUME}: where the source stops publishing a figure, print the last known
 * figures with their state and time, <b>plus</b> the new state. Both halves are load-bearing — the
 * figures so the reader keeps what was last true, the new state so nothing stale is read as
 * current.
 */
class VwapHeldLineTest {

    private static final long NOW = java.time.Instant.parse("2026-11-20T05:05:00Z").toEpochMilli();

    private static String esWithFigure(String at, String vwap) {
        return "{\"symbol\":\"ES\",\"marketState\":\"OPEN\",\"currentPrice\":\"7158.75\","
                + "\"asOfEventTime\":\"" + at + "\","
                + "\"session\":{\"vwapAnalysis\":{"
                + "\"vwap\":{\"value\":\"" + vwap + "\",\"state\":\"INCOMPLETE\","
                + "\"reason\":\"DEADLINE_FINALIZED\",\"incompleteKind\":\"LATCHED\"},"
                + "\"stdDev\":{\"value\":\"5.0993\",\"state\":\"INCOMPLETE\"}},"
                + "\"vwapRth\":{\"vwap\":{\"value\":null,\"state\":\"WARMING_UP\","
                + "\"reason\":\"WARMING_UP_WINDOW\"}}}}";
    }

    /** The same instrument, now publishing no figure at all. */
    private static String esWithNoFigure(String at) {
        return "{\"symbol\":\"ES\",\"marketState\":\"OPEN\",\"currentPrice\":\"7158.75\","
                + "\"asOfEventTime\":\"" + at + "\","
                + "\"session\":{\"vwapAnalysis\":{"
                + "\"vwap\":{\"value\":null,\"state\":\"ABSENT\","
                + "\"reason\":\"SOURCE_HAS_NO_VOLUME\"}},"
                + "\"vwapRth\":{\"vwap\":{\"value\":null,\"state\":\"ABSENT\","
                + "\"reason\":\"SOURCE_HAS_NO_VOLUME\"}}}}";
    }

    private static String nowPage(ReadableBoardRenderer r) {
        return r.namedPage("now");
    }

    @Test
    @DisplayName("when the source stops publishing a figure, the line keeps the last one and says so")
    void theLineKeepsTheLastKnownFigure() {
        ReadableBoardRenderer r = new ReadableBoardRenderer(() -> null, () -> NOW);
        r.onIndicatorsJson(esWithFigure("2026-11-20T05:04:09.870Z", "7177.4637"));
        r.onIndicatorsJson(esWithNoFigure("2026-11-20T05:04:14.870Z"));

        String page = nowPage(r);
        assertTrue(page.contains("7177.46"),
                "the last known figure is still on the line:\n" + page);
        assertTrue(page.contains("LATCHED"),
                "with its own state, as published:\n" + page);
        assertTrue(page.contains("held=00:04:09NY"),
                "and the time it was read, so nothing reads as current:\n" + page);
        assertTrue(page.contains("now=ABSENT/SOURCE_HAS_NO_VOLUME"),
                "beside the state the source publishes NOW:\n" + page);
    }

    @Test
    @DisplayName("a line that never had a figure says so, and names the state in its way")
    void aLineThatNeverHadAFigureSaysSo() {
        ReadableBoardRenderer r = new ReadableBoardRenderer(() -> null, () -> NOW);
        r.onIndicatorsJson(esWithNoFigure("2026-11-20T05:04:09.870Z"));

        String page = nowPage(r);
        assertTrue(page.contains("vwapes|absent|ABSENT/SOURCE_HAS_NO_VOLUME"),
                "no figure was ever held, so there is nothing to keep:\n" + page);
        assertFalse(page.contains("held="),
                "and it does not claim to be holding one:\n" + page);
    }

    @Test
    @DisplayName("the warming RTH line does not borrow the session line's figure")
    void theRthLineDoesNotBorrowTheSessionFigure() {
        ReadableBoardRenderer r = new ReadableBoardRenderer(() -> null, () -> NOW);
        r.onIndicatorsJson(esWithFigure("2026-11-20T05:04:09.870Z", "7177.4637"));

        String page = nowPage(r);
        String rth = page.lines().filter(l -> l.startsWith("vwaprth|")).findFirst().orElse("");
        assertTrue(rth.contains("absent"), "RTH is warming up, so it has no figure: " + rth);
        assertFalse(rth.contains("7177.46"),
                "and it must not show the session line's figure as its own: " + rth);
    }

    @Test
    @DisplayName("/footprint says absent, never a spot of 0.00")
    void footprintSaysAbsentRatherThanZero() {
        List<String> pages = Pages.footprint(NOW, null, null, List.of(), null);
        String page = String.join("\n", pages);
        assertTrue(page.contains("footprint|spot=absent"),
                "a night with no board has no spot, and 0.00 is a price:\n" + page);
        assertFalse(page.contains("spot=0.00"), page);
    }

    @Test
    @DisplayName("and a real spot is still printed as a price")
    void aRealSpotIsStillAPrice() {
        String page = String.join("\n", Pages.footprint(NOW, null, 7163.95, List.of(), null));
        assertTrue(page.contains("footprint|spot=7163.95"), page);
    }
}

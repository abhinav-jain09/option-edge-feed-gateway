package app.feedgateway.readable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The owner's 06:30 figure: the distance from spot to the largest wall each side.
 *
 * <p>The first test reproduces the real 2026-11-19 pre-open board, measured off
 * {@code options.databento.gex.strike.preopen}, so the numbers here are the ones the feed actually
 * carried rather than a fixture chosen to be convenient.
 */
class ExtremesTest {

    private static final double SPOT = 7418.4638671875;

    private static String record(double strike, double netGex) {
        return "{\"symbol\":\"SPX\",\"expiry\":\"20261119\",\"strike\":" + strike
                + ",\"netGex\":" + netGex + ",\"gammaSign\":\""
                + (netGex >= 0 ? "POSITIVE" : "NEGATIVE") + "\",\"canonicalSpot\":" + SPOT
                + ",\"updatedAt\":\"2026-11-19T11:43:02.906Z\",\"staleAfterMs\":300000}";
    }

    @Test
    @DisplayName("the real 06:43 board: 7455 above at 36.54 points, 7410 below at 8.46")
    void theMeasuredBoardReproducesItsOwnWalls() {
        List<String> records = new ArrayList<>();
        double centre = Math.round(SPOT / 5.0) * 5.0;
        for (int i = -20; i <= 20; i++) {
            double k = centre + i * 5.0;
            if (k == 7455.0 || k == 7410.0) {
                continue;               // the two walls are added below with their real figures
            }
            records.add(record(k, 1.0E7));
        }
        // The two the feed in fact showed largest on 2026-11-19 at 06:43 New York.
        records.add(record(7455.0, 6.041E8));
        records.add(record(7410.0, 3.288E8));

        ReadablePage.Board b = PreopenBoard.from(records, 1_795_088_583_906L);
        assertNotNull(b.largestAbove());
        assertNotNull(b.largestBelow());
        assertEquals(7455.0, b.largestAbove().strike(), 1e-9);
        assertEquals(36.54, b.largestAbove().distancePoints(), 0.005,
                "the distance the owner reads, from the board's own spot");
        assertEquals(7410.0, b.largestBelow().strike(), 1e-9);
        assertEquals(8.46, b.largestBelow().distancePoints(), 0.005);
    }

    @Test
    @DisplayName("equal walls resolve the same way every render: the nearer strike wins")
    void tiesAreBrokenDeterministicallyByDistance() {
        List<String> records = new ArrayList<>();
        // Five strikes above spot at EXACTLY the same net gamma. Without a tie-break the winner
        // came out of hash order and could differ between two renders of the same board.
        for (double k : new double[] {7425.0, 7445.0, 7465.0, 7485.0, 7505.0}) {
            records.add(record(k, 5.0E8));
        }
        ReadablePage.Extreme first = PreopenBoard.from(records, 1_795_088_583_906L).largestAbove();
        List<String> shuffled = new ArrayList<>(records);
        java.util.Collections.reverse(shuffled);
        ReadablePage.Extreme second =
                PreopenBoard.from(shuffled, 1_795_088_583_906L).largestAbove();

        assertEquals(7425.0, first.strike(), 1e-9, "the nearest of the equal walls");
        assertEquals(first.strike(), second.strike(), 1e-9,
                "and the SAME answer whatever order the records arrived in");
    }

    @Test
    @DisplayName("largest is by net-gamma magnitude, not by strike price")
    void largestMeansTheBiggestWallNotTheFurthestStrike() {
        List<String> records = List.of(
                record(7500.0, 1.0E7),      // furthest above, but tiny
                record(7425.0, 9.0E8),      // nearest above, and the real wall
                record(7300.0, 2.0E7),      // furthest below, tiny
                record(7415.0, 8.0E8));     // nearest below, the real wall
        ReadablePage.Board b = PreopenBoard.from(records, 1_795_088_583_906L);
        assertEquals(7425.0, b.largestAbove().strike(), 1e-9,
                "the highest strike above spot is only the edge of the grid and tells a reader "
                        + "nothing; the biggest wall is the figure");
        assertEquals(7415.0, b.largestBelow().strike(), 1e-9);
    }

    @Test
    @DisplayName("a large NEGATIVE wall wins on magnitude, and keeps its sign in the figure")
    void aNegativeWallCountsByMagnitude() {
        List<String> records = List.of(
                record(7425.0, 1.0E8),
                record(7430.0, -9.0E8));
        ReadablePage.Board b = PreopenBoard.from(records, 1_795_088_583_906L);
        assertEquals(7430.0, b.largestAbove().strike(), 1e-9, "magnitude decides which strike");
        assertTrue(b.largestAbove().netGex() < 0, "and the sign is kept, not lost to an absolute");
    }

    @Test
    @DisplayName("a strike exactly at spot is on neither side")
    void aStrikeAtSpotBelongsToNeitherSide() {
        // canonicalSpot is exactly a grid strike here.
        String at = "{\"symbol\":\"SPX\",\"expiry\":\"20261119\",\"strike\":7420.0,"
                + "\"netGex\":9.9E9,\"gammaSign\":\"POSITIVE\",\"canonicalSpot\":7420.0,"
                + "\"updatedAt\":\"2026-11-19T11:43:02.906Z\",\"staleAfterMs\":300000}";
        ReadablePage.Board b = PreopenBoard.from(List.of(at), 1_795_088_583_906L);
        assertNull(b.largestAbove(),
                "a wall at zero distance is not one the owner can trade either way, so it is "
                        + "reported on neither side rather than on both");
        assertNull(b.largestBelow());
    }

    @Test
    @DisplayName("a side with no strike at all is null, and the page says so rather than omitting it")
    void anEmptySideIsNull() {
        ReadablePage.Board b = PreopenBoard.from(List.of(record(7425.0, 1.0E8)),
                1_795_088_583_906L);
        assertNotNull(b.largestAbove());
        assertNull(b.largestBelow(), "nothing below spot on this board");

        String page = Pages.board(1_795_088_583_906L, null, b);
        assertTrue(page.contains("largest|below|none"), "the compact page states the absence: " + page);
        assertTrue(page.contains("largest|above|7425"), page);
    }

    @Test
    @DisplayName("a strike the producer gave no figure for cannot be the largest of anything")
    void aStrikeWithNoFigureIsNotAWall() {
        String noFigure = "{\"symbol\":\"SPX\",\"expiry\":\"20261119\",\"strike\":7500.0,"
                + "\"gammaSign\":\"POSITIVE\",\"canonicalSpot\":" + SPOT
                + ",\"updatedAt\":\"2026-11-19T11:43:02.906Z\",\"staleAfterMs\":300000}";
        ReadablePage.Board b = PreopenBoard.from(List.of(noFigure, record(7425.0, 1.0E8)),
                1_795_088_583_906L);
        assertEquals(7425.0, b.largestAbove().strike(), 1e-9,
                "the strike with no net gamma is skipped for this figure, though its row is still "
                        + "printed on the board");
    }

    @Test
    @DisplayName("the map board answers through the same calculator, so the two cannot drift")
    void theMapBoardUsesTheSameDefinition() {
        String map = "{\"spot\":7418.46,\"strikes\":["
                + "{\"strikeCents\":745500,\"board\":{\"netGex\":6.041E8,\"holdOrAccelerate\":\"HOLD\"}},"
                + "{\"strikeCents\":741000,\"board\":{\"netGex\":3.288E8,\"holdOrAccelerate\":\"HOLD\"}}"
                + "]}";
        ReadablePage.Board b = BoardLines.from(map, 1_795_088_583_906L);
        assertEquals(7455.0, b.largestAbove().strike(), 1e-9);
        assertEquals(7410.0, b.largestBelow().strike(), 1e-9);
        assertEquals(36.54, b.largestAbove().distancePoints(), 0.01);
        assertEquals(8.46, b.largestBelow().distancePoints(), 0.01);
    }
}

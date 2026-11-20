package app.feedgateway.readable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * <b>C-122: the strike's delta-band edges on its row.</b>
 *
 * <p><b>Why the owner wants them.</b> His reading of a stab through a strike is how far price has to
 * travel to bring that strike's delta close to one, and that distance widens with volatility and
 * with time to expiry. The map service already computes exactly that: {@code deltaBand.upperEdge}
 * is the price at which this strike's delta reaches the band's upper threshold, from the strike's
 * own IV and its time to expiry (R-PUB-1 row d). So the figure he measures in points already
 * exists, in the one service that holds the model for it.
 *
 * <p><b>They are PRICES, not deltas.</b> C-120 put the strike's delta itself on the row
 * ({@code cd}/{@code pd}); these two are the prices the delta would reach its thresholds at. The
 * two answer different questions and the page carries both.
 */
final class C122DeltaBandEdgesTest {

    /** A map record with one strike, whose band is the document's 0.05 / 0.95. */
    private static String mapJson(String band) {
        return "{\"spot\":7000.0,\"expiry\":\"2026-11-20\",\"strikes\":["
                + "{\"strikeCents\":700000,\"board\":{\"netGex\":1.0e9,"
                + "\"holdOrAccelerate\":\"HOLD\"}" + (band == null ? "" : "," + band)
                + "}]}";
    }

    private static String band(double upperEdge, double lowerEdge,
                               String upperThreshold, String lowerThreshold) {
        return "\"deltaBand\":{\"upperEdge\":" + upperEdge + ",\"lowerEdge\":" + lowerEdge
                + ",\"upperThreshold\":" + upperThreshold
                + ",\"lowerThreshold\":" + lowerThreshold + "}";
    }

    private static String rowFor(String json, String strike) {
        ReadablePage.Board board = BoardLines.from(json, 1L);
        assertNotNull(board, json);
        for (String row : board.lines()) {
            if (row.startsWith(strike + "|")) {
                return row;
            }
        }
        throw new AssertionError("no row for " + strike + " in " + board.lines());
    }

    @Test
    @DisplayName("C-122: the two edges are on the row, named from the document's thresholds")
    void theTwoEdgesAreOnTheRow() {
        String row = rowFor(mapJson(band(7042.50, 6975.00, "0.95", "0.05")), "7000");
        assertTrue(row.contains("|d95=7042.50"),
                "to the CENT: strikeOf would print 7042.5 and drop a cent the map service "
                        + "computed, and these are prices a distance is measured with: " + row);
        assertTrue(row.contains("|d05=6975.00"), row);
        assertTrue(row.length() < 125, "C-66 keeps a board row under 125 characters: " + row);
    }

    @Test
    @DisplayName("C-122: the names come from the RECORD's thresholds, never assumed")
    void theNamesComeFromTheRecord() {
        // A band computed at 0.90 / 0.10 must not print as d95 / d05: a figure wearing the wrong
        // name is worse than no figure, and the record publishes its thresholds precisely so a
        // reader can re-derive the edges.
        String row = rowFor(mapJson(band(7042.50, 6975.00, "0.90", "0.10")), "7000");
        assertTrue(row.contains("|d90=7042.50"), row);
        assertTrue(row.contains("|d10=6975.00"), row);
        assertFalse(row.contains("d95"), row);
        assertFalse(row.contains("d05"), row);
    }

    @Test
    @DisplayName("C-122: an edge published with no threshold says the name is unconfirmed")
    void anEdgeWithoutItsThresholdSaysSo() {
        String row = rowFor(mapJson(
                "\"deltaBand\":{\"upperEdge\":7042.50,\"lowerEdge\":6975.00}"), "7000");
        assertTrue(row.contains("|d95?=7042.50"),
                "the edge is published and its threshold is not, so the name carries a question "
                        + "rather than a claim: " + row);
        assertTrue(row.contains("|d05?=6975.00"), row);
    }

    @Test
    @DisplayName("C-122: a strike with no band gets NO cells, not empty ones")
    void aStrikeWithNoBandGetsNoCells() {
        String row = rowFor(mapJson(null), "7000");
        assertFalse(row.contains("d95"), row);
        assertFalse(row.contains("="), "no empty cells either: " + row);
        String noRow = rowFor(mapJson(band(1.0, 1.0, "0.95", "0.05")), "7005");
        assertTrue(noRow.contains("norow"),
                "and a strike the gamma service published nothing for is still norow: " + noRow);
        assertFalse(noRow.contains("d95"),
                "with no band of its own, because the band belongs to a row that exists: "
                        + noRow);
    }

    @Test
    @DisplayName("C-122: the edges sit beside C-120's deltas, which answer a different question")
    void theEdgesSitBesideTheDeltas() {
        // the two are different facts about one strike: the delta NOW, and the price at which the
        // delta would reach the band's thresholds
        String row = rowFor(mapJson(band(7042.50, 6975.00, "0.95", "0.05")), "7000");
        assertTrue(row.startsWith("7000|+1.00B|H|d95=7042.50|d05=6975.00"),
                "strike, net gamma, reading, then the two edges: " + row);
    }
}

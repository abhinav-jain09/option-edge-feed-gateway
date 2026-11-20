package app.feedgateway.readable;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;

/**
 * The largest net-gamma wall either side of spot, for the owner's 06:30 read.
 *
 * <p>The owner's requirement is the <b>distance from spot to the largest strike above and below</b>.
 * "Largest" is by the magnitude of net gamma, not by strike price: the highest strike above spot is
 * only the edge of the grid. Both boards answer the same way through this one class, so the map
 * board and the pre-open board cannot drift into two different definitions of the same figure.
 *
 * <p>It is computed from the <b>numbers</b>, never from the rendered rows. Parsing the page back
 * would make the figure depend on the row format, and the row format has already changed once.
 *
 * <p>A strike exactly at spot belongs to neither side: it is not above and not below, and counting
 * it on one side would report a wall at zero distance that the owner cannot trade either way.
 */
final class Extremes {

    private Extremes() {
    }

    /** From the map record's per-strike nodes, keyed by strike in cents. */
    static ReadablePage.Extreme largest(Map<Long, JsonNode> byCents, double spot, boolean above) {
        Long bestCents = null;
        double bestNet = 0.0;
        for (Map.Entry<Long, JsonNode> e : byCents.entrySet()) {
            JsonNode net = e.getValue().path("board").path("netGex");
            if (!net.isNumber()) {
                continue;               // a strike with no figure cannot be the largest of anything
            }
            double strike = e.getKey() / 100.0;
            if (!onSide(strike, spot, above)) {
                continue;
            }
            if (bestCents == null
                    || beats(net.doubleValue(), strike, bestNet, bestCents / 100.0, spot)) {
                bestCents = e.getKey();
                bestNet = net.doubleValue();
            }
        }
        return bestCents == null ? null : extreme(bestCents / 100.0, bestNet, spot);
    }

    /** From the pre-open rows, keyed by strike in cents. */
    static ReadablePage.Extreme largestOfRows(Map<Long, PreopenBoard.Row> byCents, double spot,
                                              boolean above) {
        Long bestCents = null;
        double bestNet = 0.0;
        for (Map.Entry<Long, PreopenBoard.Row> e : byCents.entrySet()) {
            Double net = e.getValue().netGex();
            if (net == null) {
                continue;
            }
            double strike = e.getKey() / 100.0;
            if (!onSide(strike, spot, above)) {
                continue;
            }
            if (bestCents == null || beats(net, strike, bestNet, bestCents / 100.0, spot)) {
                bestCents = e.getKey();
                bestNet = net;
            }
        }
        return bestCents == null ? null : extreme(bestCents / 100.0, bestNet, spot);
    }

    /**
     * Does this candidate beat the one held? Magnitude first, and then a <b>total, deterministic</b>
     * tie-break: the strike NEARER spot wins, and if two are equidistant the lower strike wins.
     *
     * <p>The tie-break is not decoration. A pre-open board routinely carries several strikes at the
     * same net gamma, and without it the winner came out of {@code HashMap} iteration order — so
     * the distance the owner reads could change between two renders of the SAME board, with
     * nothing on the page to say why. Nearer-wins is also the answer that is useful: of two equal
     * walls, the one he reaches first is the one that matters.
     */
    private static boolean beats(double net, double strike, double bestNet, double bestStrike,
                                 double spot) {
        double a = Math.abs(net);
        double b = Math.abs(bestNet);
        if (a != b) {
            return a > b;
        }
        double da = Math.abs(strike - spot);
        double db = Math.abs(bestStrike - spot);
        if (da != db) {
            return da < db;
        }
        return strike < bestStrike;
    }

    private static boolean onSide(double strike, double spot, boolean above) {
        return above ? strike > spot : strike < spot;
    }

    private static ReadablePage.Extreme extreme(double strike, double net, double spot) {
        return new ReadablePage.Extreme(strike, net, Math.abs(strike - spot));
    }
}

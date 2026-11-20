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
 * C-71: the session's path of sides, rebuilt at start-up so a restart does not lose the day.
 *
 * <p>The snapshot shape here is the one measured on Dev on 2026-11-20, including the detail that
 * matters: {@code currentPrice} is a decimal <b>string</b>, and the holding anchor is
 * {@code vwapAnalysis} while {@code vwapRth} is still {@code WARMING_UP}.
 */
class VwapRebuildTest {

    /** 2026-11-19 14:00:00Z, mid-session. */
    private static final long T0 = 1_795_093_200_000L;

    private static String snapshot(String atIso, String price, String vwap, String rthVwap) {
        String rth = rthVwap == null
                ? "\"vwapRth\":{\"vwap\":{\"value\":null,\"state\":\"WARMING_UP\","
                        + "\"reason\":\"WARMING_UP_WINDOW\"}}"
                : "\"vwapRth\":{\"vwap\":{\"value\":\"" + rthVwap + "\",\"state\":\"COMPLETE\"},"
                        + "\"bandUpper1\":{\"value\":\"" + rthVwap + "\",\"state\":\"COMPLETE\"},"
                        + "\"bandLower1\":{\"value\":\"" + rthVwap + "\",\"state\":\"COMPLETE\"}}";
        return "{\"symbol\":\"ES\",\"marketState\":\"OPEN\","
                + "\"currentPrice\":\"" + price + "\","
                + "\"asOfEventTime\":\"" + atIso + "\","
                + "\"session\":{\"vwapAnalysis\":{"
                + "\"vwap\":{\"value\":\"" + vwap + "\",\"state\":\"COMPLETE\"},"
                + "\"stdDev\":{\"value\":\"5.0993\",\"state\":\"COMPLETE\"},"
                + "\"bandUpper1\":{\"value\":\"" + (Double.parseDouble(vwap) + 5)
                + "\",\"state\":\"COMPLETE\"},"
                + "\"bandLower1\":{\"value\":\"" + (Double.parseDouble(vwap) - 5)
                + "\",\"state\":\"COMPLETE\"}},"
                + rth + "}}";
    }

    private static String iso(long ms) {
        return java.time.Instant.ofEpochMilli(ms).toString();
    }

    @Test
    @DisplayName("a replayed session reconstructs the sides, the cross and the held times")
    void theSessionIsReconstructed() {
        List<String> history = List.of(
                snapshot(iso(T0), "7190.00", "7187.00", null),                  // above
                snapshot(iso(T0 + 600_000L), "7191.00", "7187.00", null),       // still above
                snapshot(iso(T0 + 1_800_000L), "7180.00", "7187.00", null),     // below: one cross
                snapshot(iso(T0 + 2_400_000L), "7181.00", "7187.00", null));    // still below

        VwapSideTracker t = new VwapSideTracker();
        assertEquals(4, VwapRebuild.replay(t, history), "every snapshot was usable");

        VwapSideTracker.Result r = t.result();
        assertEquals(VwapSideTracker.BELOW, r.side(), "it ends below the line");
        assertEquals(1, r.crosses(), "one crossing, not four");
        assertEquals(T0, r.observedFromMs(),
                "observedFrom is the true start of what was rebuilt, as C-71 requires");
        assertEquals(T0 + 1_800_000L, r.lastCrossMs().longValue());
        assertEquals(1_800_000L, r.longestAboveMs(),
                "thirty minutes above, credited at the cross");
        assertEquals("vwapAnalysis", r.anchorName(),
                "and it names the line it used, since RTH was still warming");
    }

    @Test
    @DisplayName("out-of-order delivery gives the same answer as in-order")
    void eventTimeOrderDecidesNotArrivalOrder() {
        List<String> ordered = List.of(
                snapshot(iso(T0), "7190.00", "7187.00", null),
                snapshot(iso(T0 + 600_000L), "7191.00", "7187.00", null),
                snapshot(iso(T0 + 1_800_000L), "7180.00", "7187.00", null));
        List<String> shuffled = new ArrayList<>(ordered);
        java.util.Collections.reverse(shuffled);

        VwapSideTracker a = new VwapSideTracker();
        VwapSideTracker b = new VwapSideTracker();
        VwapRebuild.replay(a, ordered);
        VwapRebuild.replay(b, shuffled);

        assertEquals(a.result().crosses(), b.result().crosses(),
                "the tracker credits held time from one observation to the next, so an "
                        + "out-of-order replay would produce negative stretches");
        assertEquals(a.result().side(), b.result().side());
        assertEquals(a.result().longestAboveMs(), b.result().longestAboveMs());
        assertEquals(a.result().observedFromMs(), b.result().observedFromMs());
    }

    @Test
    @DisplayName("THE defect: each moment's price is paired with that moment's own VWAP")
    void eachPriceMeetsItsOwnLine() {
        // The line MOVES. Against its own line each price is above; against the FINAL line the
        // first two prices would be below, which would invent two crossings.
        List<String> history = List.of(
                snapshot(iso(T0), "7150.00", "7149.00", null),
                snapshot(iso(T0 + 600_000L), "7170.00", "7169.00", null),
                snapshot(iso(T0 + 1_200_000L), "7200.00", "7199.00", null));

        VwapSideTracker t = new VwapSideTracker();
        VwapRebuild.replay(t, history);
        VwapSideTracker.Result r = t.result();

        assertEquals(VwapSideTracker.ABOVE, r.side(), "every moment was above its own line");
        assertEquals(0, r.crosses(),
                "comparing an old price against the line as it stands NOW would put it on "
                        + "whichever side suits the present, and the count would change every "
                        + "time the line moved");
    }

    @Test
    @DisplayName("an anchor change restarts the path rather than inventing a cross")
    void anAnchorChangeRestartsThePath() {
        // vwapAnalysis at 7187 with price above it, then RTH becomes usable at 7195 with the same
        // price now below THAT line. Carrying the side across would record a crossing that price
        // never made: the line changed, not the price.
        List<String> history = List.of(
                snapshot(iso(T0), "7190.00", "7187.00", null),
                snapshot(iso(T0 + 600_000L), "7190.00", "7187.00", "7195.00"));

        VwapSideTracker t = new VwapSideTracker();
        VwapRebuild.replay(t, history);
        VwapSideTracker.Result r = t.result();

        assertEquals(1, r.anchorChanges(), "the change is recorded");
        assertEquals(0, r.crosses(), "and nothing is counted across the boundary");
        assertEquals(T0 + 600_000L, r.observedFromMs(),
                "observedFrom moves to the restart, so the page tells the truth about how much "
                        + "of the session this path actually covers");
        assertEquals("vwapRth", r.anchorName());
    }

    @Test
    @DisplayName("a snapshot that cannot place price is refused, never counted as ON the line")
    void unusableSnapshotsAreRefused() {
        assertNull(VwapRebuild.decode(null));
        assertNull(VwapRebuild.decode("not json"));
        // no price of its own moment
        assertNull(VwapRebuild.decode(snapshot(iso(T0), "7190.00", "7187.00", null)
                .replace("\"currentPrice\":\"7190.00\",", "")));
        // no instant
        assertNull(VwapRebuild.decode(snapshot(iso(T0), "7190.00", "7187.00", null)
                .replace("\"asOfEventTime\":\"" + iso(T0) + "\",", "")));
        // the holding anchor has no usable VWAP at all
        assertNull(VwapRebuild.decode("{\"currentPrice\":\"7190.00\",\"asOfEventTime\":\""
                + iso(T0) + "\",\"session\":{\"vwapAnalysis\":{\"vwap\":{\"value\":null,"
                + "\"state\":\"WARMING_UP\"}},\"vwapRth\":{\"vwap\":{\"value\":null,"
                + "\"state\":\"WARMING_UP\"}}}}"),
                "a snapshot that cannot place price must not be counted as ON the line, which "
                        + "would be a measurement nobody made");

        VwapSideTracker t = new VwapSideTracker();
        assertEquals(1, VwapRebuild.replay(t, List.of("rubbish",
                snapshot(iso(T0), "7190.00", "7187.00", null))),
                "one unreadable record must not cost the rebuild the rest of the session");
        assertEquals(0, VwapRebuild.replay(new VwapSideTracker(), List.of()));
        assertEquals(0, VwapRebuild.replay(null, List.of("x")));
    }

    @Test
    @DisplayName("the price is a decimal string on the wire, as the producer writes it")
    void thePriceIsAStringOnTheWire() {
        VwapRebuild.Moment m = VwapRebuild.decode(snapshot(iso(T0), "7189.50", "7187.0082", null));
        assertNotNull(m, "the measured shape must decode");
        assertEquals(7189.50, m.price(), 1e-9);
        assertEquals(7187.0082, m.vwap(), 1e-9);
        assertTrue(m.upper1() != null && m.lower1() != null, "the bands come with it");
    }
}

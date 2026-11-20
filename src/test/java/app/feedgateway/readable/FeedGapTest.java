package app.feedgateway.readable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The feed gap, measured by OUR arrival times and labelled on the {@code vwap|} headline line.
 *
 * <p>The real window, 2026-11-19/20: the ES feed stopped at 18:16:05 New York and resumed at
 * 23:46:53. Its bars were backfilled afterwards — median publication lag inside the window 2h44m
 * against ~5 s on either side — and the indicators service's session VWAP does not contain their
 * volume. The reviewer's requirement is that the owner sees that in a few characters, since the
 * service itself reports only {@code DEADLINE_FINALIZED}.
 */
class FeedGapTest {

    /** 2026-11-19 18:00:00 New York. */
    private static final long SESSION_OPEN =
            java.time.Instant.parse("2026-11-19T23:00:00Z").toEpochMilli();
    private static final long GAP_FROM = SESSION_OPEN + 16L * 60_000L + 5_000L;   // 18:16:05
    private static final long GAP_TO =
            java.time.Instant.parse("2026-11-20T04:46:53Z").toEpochMilli();       // 23:46:53
    private static final long NOW = java.time.Instant.parse("2026-11-20T05:30:00Z").toEpochMilli();

    private static String bar(long startMs) {
        return "{\"schemaVersion\":6,\"symbol\":\"ES.v.0\",\"timeframe\":\"1m\","
                + "\"sessionDate\":\"2026-11-19\",\"observations\":{\"barStartMs\":" + startMs
                + ",\"complete\":true,\"openCents\":718550,\"closeCents\":718975,"
                + "\"spxEqOpenCents\":716530,\"spxEqHighCents\":716580,"
                + "\"spxEqLowCents\":716510,\"spxEqCloseCents\":716550,"
                + "\"spxEqBasisState\":\"ANCHORED\","
                + "\"volume\":1200,\"buyerContracts\":700,\"sellerContracts\":500}}";
    }

    @Test
    @DisplayName("a bar that arrives in its own minute is not a gap")
    void aLiveBarIsNotAGap() {
        FeedGap g = new FeedGap();
        // The measured normal shape: a 1m bar reaches us about 65 s after it opened.
        g.observe(SESSION_OPEN, SESSION_OPEN + 65_000L);
        g.observe(SESSION_OPEN + 60_000L, SESSION_OPEN + 125_000L);
        assertEquals(0, g.lateBars());
        assertNull(g.label(), "a normal day carries no label at all");
    }

    @Test
    @DisplayName("backfilled bars become the window, by their own minutes")
    void backfilledBarsBecomeTheWindow() {
        FeedGap g = new FeedGap();
        // Every bar of the outage arrives at the reconnect, hours after the minute it describes.
        for (long t = GAP_FROM; t < GAP_TO; t += 60_000L) {
            g.observe(t, GAP_TO + 5_000L);
        }
        assertTrue(g.lateBars() > 300, "the real window held 330 1m bars: " + g.lateBars());
        assertEquals("gap 18:16-23:43", g.label(),
                "the window is named by the MINUTES the bars describe, not by when they arrived "
                        + "-- and it ends three minutes before the reconnect because the last few "
                        + "bars arrived INSIDE the lateness threshold, which is the threshold "
                        + "doing its job rather than an off-by-one");
    }

    @Test
    @DisplayName("the label reaches the vwap| headline line, where the owner reads it")
    void theLabelReachesTheHeadline() {
        ReadableBoardRenderer r = new ReadableBoardRenderer(() -> null, () -> NOW);
        // Late bars through the REAL tap, so the wiring is driven rather than asserted about.
        for (long t = GAP_FROM; t < GAP_FROM + 10L * 60_000L; t += 60_000L) {
            r.onBarJson(bar(t));
        }
        String page = r.namedPage("now");
        String vwapLine = page.lines().filter(l -> l.startsWith("vwap|")).findFirst().orElse("");
        assertTrue(vwapLine.contains("gap 18:16-18:25"),
                "the gap belongs on the line whose figure it damages: " + vwapLine);
        assertTrue(vwapLine.length() <= 64, "C-75 keeps headline lines short: " + vwapLine);
    }

    @Test
    @DisplayName("a live feed adds nothing to the headline")
    void aLiveFeedAddsNothing() {
        ReadableBoardRenderer r = new ReadableBoardRenderer(() -> null, () -> NOW);
        r.onBarJson(bar(NOW - 65_000L));
        String page = r.namedPage("now");
        assertFalse(page.contains("gap "),
                "a label that is always there stops being read:\n" + page);
    }
}

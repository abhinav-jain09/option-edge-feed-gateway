package app.feedgateway.readable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The feed gap, measured from the BROKER's publish time and labelled on the {@code vwap|} line.
 *
 * <p>The real window, 2026-11-19/20: the ES feed stopped at 18:16:05 New York and resumed at
 * 23:46:53. Its bars were backfilled afterwards — median publication lag inside the window 2h44m
 * against ~5 s on either side — and the indicators service's session VWAP does not contain their
 * volume. The owner has to see that in a few characters, since the service itself reports only
 * {@code DEADLINE_FINALIZED}.
 *
 * <p>The first version of this measured lateness from the gateway's own arrival clock. The reviewer
 * rejected it: a restart re-reads nothing, so the label would either name the whole session or
 * vanish — and the rollout that ships it is itself a restart. The tests that matter here are
 * therefore the two that pin the measurement to the records: {@link #theWallClockIsNotTheMeasure()}
 * and {@link #aRestartMeasuresTheSameWindow()}.
 */
class FeedGapTest {

    /** 2026-11-19 18:00:00 New York. */
    private static final long SESSION_OPEN =
            java.time.Instant.parse("2026-11-19T23:00:00Z").toEpochMilli();
    private static final long GAP_FROM = SESSION_OPEN + 16L * 60_000L + 5_000L;   // 18:16:05
    private static final long GAP_TO =
            java.time.Instant.parse("2026-11-20T04:46:53Z").toEpochMilli();       // 23:46:53
    private static final long NOW = java.time.Instant.parse("2026-11-20T05:30:00Z").toEpochMilli();

    /** The five widths the topic really carries, measured live on 2026-11-20. */
    private static final long[] WIDTHS_MS = {30_000L, 60_000L, 300_000L, 900_000L, 1_800_000L};

    /** A bar of this width, published punctually: five seconds after it CLOSED. */
    private static FeedGap.Published punctual(long startMs, long widthMs) {
        return new FeedGap.Published(startMs, startMs + widthMs, startMs + widthMs + 5_000L);
    }

    /** The same bar, backfilled at {@code publishedMs} long after it closed. */
    private static FeedGap.Published backfilled(long startMs, long widthMs, long publishedMs) {
        return new FeedGap.Published(startMs, startMs + widthMs, publishedMs);
    }

    /**
     * The session as it really was, <b>at every timeframe the topic carries</b>: live, then an
     * outage backfilled at the reconnect, then live again.
     *
     * <p>The earlier version of this helper built 1m bars only, which is why eight green tests said
     * nothing about the defect that shipped: a 30m bar is 1,800 s after its own start by
     * construction, so a threshold applied to the start flagged every bar above 1m and the label
     * named the whole session.
     */
    private static List<FeedGap.Published> theRealSession() {
        List<FeedGap.Published> bars = new ArrayList<>();
        for (long width : WIDTHS_MS) {
            for (long t = SESSION_OPEN; t < GAP_FROM; t += width) {
                bars.add(punctual(t, width));
            }
            for (long t = GAP_FROM; t < GAP_TO; t += width) {
                bars.add(backfilled(t, width, GAP_TO + 5_000L));
            }
            for (long t = GAP_TO; t < NOW; t += width) {
                bars.add(punctual(t, width));
            }
        }
        return bars;
    }

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
    @DisplayName("a bar published in its own minute is not a gap")
    void aLiveBarIsNotAGap() {
        FeedGap g = new FeedGap();
        g.measure(List.of(punctual(SESSION_OPEN, 60_000L),
                punctual(SESSION_OPEN + 60_000L, 60_000L)));
        assertEquals(0, g.lateBars());
        assertEquals(2, g.barsMeasured(), "it did look at them, which is not the same as finding none");
        assertNull(g.label(), "a normal day carries no label at all");
    }

    @Test
    @DisplayName("the backfilled window is named by the minutes the bars describe")
    void theBackfilledWindowIsNamed() {
        FeedGap g = new FeedGap();
        g.measure(theRealSession());
        assertTrue(g.lateBars() > 300, "the real window held 330 1m bars: " + g.lateBars());
        assertEquals("gap 18:16-23:43", g.label(),
                "named by the bars' own minutes, not by when they arrived. The edge is set by the "
                        + "NARROWEST timeframe: a 30s bar starting 23:43 closes at 23:44 and is "
                        + "backfilled at 23:46:58, a lag just over the three-minute threshold, "
                        + "while the bars after it closed near enough to the reconnect to be "
                        + "punctual. I first wrote 23:16 here by guessing instead of deriving it, "
                        + "and the code was right.");
    }

    @Test
    @DisplayName("THE DEFECT: our own clock is not the measure, however late we read the records")
    void theWallClockIsNotTheMeasure() {
        // Every bar of the session, published punctually, read by a gateway that started hours
        // later. The version this replaces took `now - barStart` and would have called the whole
        // session a gap.
        List<FeedGap.Published> onTime = new ArrayList<>();
        for (long width : WIDTHS_MS) {
            for (long t = SESSION_OPEN; t < NOW; t += width) {
                onTime.add(punctual(t, width));
            }
        }
        FeedGap g = new FeedGap();
        g.measure(onTime);
        assertEquals(0, g.lateBars(),
                "a punctually published bar is never late, no matter when we read it");
        assertNull(g.label(), "so a gateway starting at 06:00 does not invent a six-hour gap");
    }

    @Test
    @DisplayName("THE REVIEWER'S TEST: a restart measures the same window as before it")
    void aRestartMeasuresTheSameWindow() {
        List<FeedGap.Published> session = theRealSession();

        FeedGap before = new FeedGap();
        before.measure(session);

        // A restart: a brand-new instance, nothing carried over, the same records re-read.
        FeedGap after = new FeedGap();
        after.measure(session);

        assertEquals(before.label(), after.label(),
                "the label is a pure function of the records, which is the whole of its "
                        + "restart-invariance");
        assertEquals("gap 18:16-23:43", after.label());

        // And measuring twice on the same instance replaces rather than accumulates.
        after.measure(session);
        assertEquals(before.lateBars(), after.lateBars(),
                "a second measurement of the same records counts the same bars, not twice as many");
    }

    @Test
    @DisplayName("a measurement that read nothing leaves the previous one standing")
    void anEmptyMeasurementDoesNotFlickerTheLabel() {
        FeedGap g = new FeedGap();
        g.measure(theRealSession());
        String had = g.label();
        g.measure(List.of());
        assertEquals(had, g.label(),
                "a failed drain is not evidence that the gap closed, and a headline that flickers "
                        + "is worse than one that is late");
    }

    @Test
    @DisplayName("the label reaches the vwap| headline line, where the owner reads it")
    void theLabelReachesTheHeadline() {
        ReadableBoardRenderer r = new ReadableBoardRenderer(() -> null, () -> NOW);
        r.measureFeedGap(theRealSession());
        String page = r.namedPage("now");
        String vwapLine = page.lines().filter(l -> l.startsWith("vwap|")).findFirst().orElse("");
        assertTrue(vwapLine.contains("gap 18:16-"),
                "the gap belongs on the line whose figure it damages: " + vwapLine);
        assertTrue(vwapLine.length() <= 64, "C-75 keeps headline lines short: " + vwapLine);
    }

    @Test
    @DisplayName("the live bar tap cannot set a gap, because it has no broker stamp")
    void theLiveTapCannotSetAGap() {
        ReadableBoardRenderer r = new ReadableBoardRenderer(() -> null, () -> NOW);
        // Bars whose own minutes are hours old, through the live tap. Under the old code this was
        // the whole session becoming a gap.
        for (long t = SESSION_OPEN; t < SESSION_OPEN + 20L * 60_000L; t += 60_000L) {
            r.onBarJson(bar(t));
        }
        assertEquals(0, r.feedGap().lateBars(),
                "the tap feeds the overnight store and nothing else; the gap is measured from the "
                        + "records' own stamps, by the service");
        assertFalse(r.namedPage("now").contains("gap "),
                "so no label appears from the arrival path at all");
    }

    @Test
    @DisplayName("a live feed adds nothing to the headline")
    void aLiveFeedAddsNothing() {
        ReadableBoardRenderer r = new ReadableBoardRenderer(() -> null, () -> NOW);
        List<FeedGap.Published> live = new ArrayList<>();
        for (long width : WIDTHS_MS) {
            for (long t = NOW - 4 * 1_800_000L; t < NOW; t += width) {
                live.add(punctual(t, width));
            }
        }
        r.measureFeedGap(live);
        assertFalse(r.namedPage("now").contains("gap "),
                "a label that is always there stops being read");
    }

    @Test
    @DisplayName("THE SHIPPED DEFECT: a wide timeframe published on time is NOT late")
    void aWideTimeframeIsNotLateJustForBeingWide() {
        // This is what reached the page as `gap 18:00-01:45`. Every one of these bars was
        // published five seconds after it CLOSED -- punctual at every width -- but a 30m bar is
        // 1,800 s after its own START, so a threshold applied to the start called them all late
        // and the window spanned the session.
        FeedGap g = new FeedGap();
        List<FeedGap.Published> wide = new ArrayList<>();
        for (long width : WIDTHS_MS) {
            wide.add(punctual(SESSION_OPEN, width));
        }
        g.measure(wide);

        assertEquals(5, g.barsMeasured(), "one bar of each width the topic carries");
        assertEquals(0, g.lateBars(),
                "a 30m bar published 5 s after its close is punctual; measured from its START it "
                        + "would be 1,800 s 'late', which is how the whole session got named");
        assertNull(g.label());
    }

    @Test
    @DisplayName("and a wide timeframe genuinely backfilled IS late, at its own minutes")
    void aWideTimeframeBackfilledIsStillCaught() {
        FeedGap g = new FeedGap();
        g.measure(List.of(backfilled(GAP_FROM, 1_800_000L, GAP_TO + 5_000L)));
        assertEquals(1, g.lateBars(), "hours after its close, not minutes");
        assertTrue(g.label().startsWith("gap 18:16"),
                "named by the minutes it describes, not by when it arrived: " + g.label());
    }
}

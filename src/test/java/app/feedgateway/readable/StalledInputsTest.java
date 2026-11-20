package app.feedgateway.readable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The stalled-input lines, driven by the real stall they exist because of.
 *
 * <p>2026-11-19: the es4 trade path stopped at 18:16:02 New York. The footprint bars and the ES
 * indicators snapshot stopped with it, and every column fed by them kept printing its last value.
 */
class StalledInputsTest {

    /** 2026-11-19T23:16:02.624Z — 18:16:02 New York, the instant both feeds stopped. */
    private static final long STOPPED = 1795130162624L;

    @Test
    @DisplayName("a stalled input is named, with its last instant, its age and what it freezes")
    void aStalledInputIsNamedWithWhatItFreezes() {
        long at0630 = STOPPED + 12L * 3_600_000L + 14L * 60_000L;
        String banner = StalledInputs.banner(at0630, List.of(
                new StalledInputs.Input("footprint bars", STOPPED,
                        "The overnight slots and their volumes"),
                new StalledInputs.Input("ES indicators snapshot", STOPPED,
                        "The VWAP line and the holding times")));

        assertTrue(banner.contains("STALLED INPUT — footprint bars"), banner);
        assertTrue(banner.contains("ES indicators snapshot"), banner);
        assertTrue(banner.contains("18:16:02 New York"),
                "the instant of its newest record, in the owner's own clock: " + banner);
        assertTrue(banner.contains("12h14m ago"), "and how long ago that was: " + banner);
        assertTrue(banner.contains("The VWAP line and the holding times below are frozen at that "
                + "moment, not current"), "and WHICH columns the reader cannot trust: " + banner);
        assertEquals(2, banner.lines().count(), "one line per stalled input, nothing else");
    }

    @Test
    @DisplayName("a current input produces no line at all")
    void aCurrentInputIsSilent() {
        String banner = StalledInputs.banner(STOPPED + 60_000L, List.of(
                new StalledInputs.Input("footprint bars", STOPPED, "The overnight slots")));
        assertEquals("", banner,
                "a minute of quiet is ordinary for a bar feed; a bound that fired on it would "
                        + "train the reader to ignore the line");
    }

    @Test
    @DisplayName("only the stalled inputs are listed, never the healthy ones beside them")
    void onlyTheStalledOnesAreListed() {
        long now = STOPPED + 3L * 3_600_000L;
        String banner = StalledInputs.banner(now, List.of(
                new StalledInputs.Input("footprint bars", STOPPED, "The overnight slots"),
                new StalledInputs.Input("SPX price", now - 5_000L, "The first line")));
        assertTrue(banner.contains("footprint bars"), banner);
        assertFalse(banner.contains("SPX price"),
                "the live input is not mentioned: the banner is a list of what cannot be trusted, "
                        + "not a status board");
    }

    @Test
    @DisplayName("an input never received is a different statement from one that stopped")
    void neverReceivedIsNotStale() {
        String banner = StalledInputs.banner(STOPPED, List.of(
                new StalledInputs.Input("ES indicators snapshot", 0L,
                        "The VWAP line and the holding times")));
        assertTrue(banner.contains("nothing ever received"), banner);
        assertTrue(banner.contains("are empty, not stale"),
                "an input with no record at all cannot be given an age, and saying it is 'stale "
                        + "since 1970' would be worse than saying nothing: " + banner);
        assertFalse(banner.contains("1970"), banner);
    }

    @Test
    @DisplayName("the age reads in hours and minutes once it passes an hour")
    void theAgeReadsInTheUnitsAReaderThinksIn() {
        assertEquals("0m", StalledInputs.ago(30_000L));
        assertEquals("11m", StalledInputs.ago(11L * 60_000L));
        assertEquals("59m", StalledInputs.ago(59L * 60_000L));
        assertEquals("1h00m", StalledInputs.ago(60L * 60_000L));
        assertEquals("12h14m", StalledInputs.ago(12L * 3_600_000L + 14L * 60_000L));
    }

    @Test
    @DisplayName("the banner sits above every table on the served page")
    void theBannerIsAboveTheTables() {
        long nightClose = STOPPED - 2L * 3_600_000L;
        ReadableBoardRenderer r = new ReadableBoardRenderer(() -> null,
                () -> STOPPED + 10L * 3_600_000L);
        // an index print to anchor the night, then bars that stop at the stall
        r.onPriceJson(priceJson(nightClose * 1_000_000L, 7176.24, "NATIVE_SPX_INDEX",
                "LIVE_INDEX"), nightClose);
        r.onBarJson(barJson(nightClose + 60_000L, 717_999L, 258_939L));

        String page = r.render();
        int banner = page.indexOf("STALLED INPUT");
        assertTrue(banner >= 0, "the stall is named: " + page);
        int firstTable = page.indexOf("SLOT(NY)");
        if (firstTable >= 0) {
            assertTrue(banner < firstTable,
                    "and it sits ABOVE the first table, which is the whole requirement");
        }
        assertTrue(page.contains("footprint bars"), page);
    }

    private static String priceJson(long atNs, double spx, String source, String quality) {
        return "{\"schemaVersion\":1,\"symbol\":\"SPX\",\"price\":\"" + spx + "\","
                + "\"canonicalSpot\":\"" + spx + "\",\"source\":\"" + source + "\","
                + "\"quality\":\"" + quality + "\",\"spotEventTimeNs\":" + atNs + "}";
    }

    private static String barJson(long startMs, long spxCloseCents, long volume) {
        return "{\"schemaVersion\":6,\"symbol\":\"ES.v.0\",\"timeframe\":\"1m\","
                + "\"sessionDate\":\"2026-11-20\",\"observations\":{\"barStartMs\":" + startMs
                + ",\"complete\":true,\"openCents\":718550,\"closeCents\":718975,"
                + "\"spxEqOpenCents\":" + (spxCloseCents - 20) + ",\"spxEqHighCents\":"
                + (spxCloseCents + 30) + ",\"spxEqLowCents\":" + (spxCloseCents - 40)
                + ",\"spxEqCloseCents\":" + spxCloseCents + ",\"spxEqBasisState\":\"ANCHORED\","
                + "\"volume\":" + volume + "}}";
    }
}

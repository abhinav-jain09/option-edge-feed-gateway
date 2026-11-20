package app.feedgateway.readable;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * <b>C-63, end to end on the SERVED BYTES</b> — the reviewer's standing form: real records in, and
 * the page a reader would actually get asserted to carry the first line, a filled slot, a blank
 * slot and one strike row.
 *
 * <p>Every record here is one Dev published on 2026-11-19/20.
 */
class ReadableBoardRendererTest {

    /** 2026-11-20 01:25 Amsterdam = 19:25 New York = 00:25Z on the 20th. */
    private static final long NOW = 1_795_134_300_000L;
    /** The cash close: 2026-11-19 15:58:54 New York. */
    private static final long CLOSE = 1_795_121_934_000L;
    private static final long MIN = 60_000L;

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

    private static String boardJson() {
        return "{\"spot\":7177.56,\"expiry\":\"20261119\",\"minutesToExpiry\":1.0,"
                + "\"fingerprint\":{\"valuationTimeMs\":1795124682036},"
                + "\"strikes\":[{\"strikeCents\":717500,\"dayVolume\":0,\"board\":{"
                + "\"netGex\":-8.31E9,\"absGex\":8.31E9,\"gammaSign\":\"NEGATIVE\","
                + "\"holdOrAccelerate\":\"ACCELERATE\",\"excluded\":false,"
                + "\"excludedReason\":null}}]}";
    }

    @Test
    @DisplayName("the served bytes carry the first line, a filled slot, a blank slot and a strike row")
    void theServedBytesCarryEverythingTheOwnerReads() {
        ReadableBoardRenderer renderer =
                new ReadableBoardRenderer(ReadableBoardRendererTest::boardJson, () -> NOW);

        // the cash session's last index print: this is what fixes the night's start AND the
        // reference price, measured rather than taken from a calendar
        renderer.onPriceJson(priceJson(CLOSE * 1_000_000L, 7176.24, "NATIVE_SPX_INDEX",
                "LIVE_INDEX"), CLOSE);
        // then the overnight ES-basis derivation, which is what is live now
        renderer.onPriceJson(priceJson((NOW - 5_000L) * 1_000_000L, 7174.45, "ES_BASIS_DERIVED",
                "LIVE_ES_BASIS"), NOW - 5_000L);
        // bars for the first slot of the night, and for one slot three hours later -- leaving the
        // slots between them BLANK, which is tonight's real shape after the feed stalled
        renderer.onBarJson(barJson(CLOSE + MIN, 717_999L, 258_939L));
        renderer.onBarJson(barJson(CLOSE + 2 * MIN, 717_624L, 59_857L));
        renderer.onBarJson(barJson(CLOSE + 125 * MIN, 717_145L, 602L));

        String page = renderer.render();
        assertTrue(page.length() <= ReadablePage.MAX_CHARS, "within C-61's bound");

        // 1. the first line, in the owner's order, with both clocks and the write time
        String first = page.lines().findFirst().orElseThrow();
        assertTrue(first.startsWith("Last price "), first);
        assertTrue(first.contains("New York") && first.contains("Amsterdam"), first);
        assertTrue(first.contains("7174.45"), "the LIVE price, not the last bar's close: " + first);
        assertTrue(first.contains("ES_BASIS_DERIVED"), first);
        assertFalse(first.startsWith("STALE"), "five seconds old is not stale: " + first);

        // 2. the reference price is the index print, frozen at the close
        assertTrue(page.contains("7176.24"), "the previous cash close is on the page");

        // 3. a FILLED slot, with its SPX-equivalent close and its volume
        assertTrue(page.contains("7179.99"), "the first slot's close is SPX-equivalent");
        assertFalse(page.contains("7189.75"),
                "and NOT the raw ES close, which these fixtures also carry");
        assertTrue(page.contains("258,939") || page.contains("318796"),
                "its volume is on the row");

        // 4. a BLANK slot: a row with its timestamp and dashes, keeping its place
        long blankRows = page.lines().filter(l -> l.contains(" -          -          -")).count();
        assertTrue(blankRows > 0, "the slots the bars never delivered are printed blank");
        assertTrue(page.contains("blank"), "and the count says how many");

        // 5. one strike row from the map record, with the record's OWN reading
        assertTrue(page.contains("|A"),
                "the reading the map service published, as C-66's one-letter code");
        assertTrue(page.contains("A=accelerate"),
                "and the page DEFINES that code on itself -- a bare `A` would leave the owner "
                        + "guessing at a letter, which is what going compact nearly cost");

        // 5a. the owner's 06:30 figures: the distance to the largest wall each side. The fixture
        // has one strike, 7175, BELOW spot 7177.56 -- so below is that strike and above is stated
        // as absent rather than left off the page.
        assertTrue(page.contains("Largest net gamma BELOW spot: 7175"), page);
        assertTrue(page.contains("2.56 points away"),
                "measured from the board's own spot, not from the write time");
        assertTrue(page.contains("Largest net gamma ABOVE spot: none on the board yet"),
                "an absent side is printed, because a missing line reads as a page that forgot");
        assertTrue(page.contains("-8.31B"), "and its net gamma");
        assertTrue(page.contains("no row"),
                "with the grid's gaps printed rather than skipped");
        assertTrue(page.contains("rows 1 of 41"), "and the completeness figure the owner reads");

        // 6. the board's OWN cycle time and date, so yesterday's board is never read as today's
        assertTrue(page.contains("cycle 2026-11-19"), "from the record's own fingerprint");

        // 7. plain text: nothing a script would have to run, nothing a web read cannot see
        assertFalse(page.contains("<script"), "no script");
        assertFalse(page.contains("<html"), "no markup at all");
        assertFalse(page.contains("fetch("), "nothing loaded afterwards");
    }

    @Test
    @DisplayName("STALE opens the page when the price stops, even while the page keeps rendering")
    void aStalePriceIsAnnouncedOnTheFirstLine() {
        ReadableBoardRenderer renderer = new ReadableBoardRenderer(() -> null, () -> NOW);
        renderer.onPriceJson(priceJson((NOW - 25 * MIN) * 1_000_000L, 7174.45,
                "ES_BASIS_DERIVED", "LIVE_ES_BASIS"), NOW - 25 * MIN);
        String page = renderer.render();
        assertTrue(page.startsWith("STALE"),
                "twenty-five minutes without a price opens the page with the word: " + page.lines()
                        .findFirst().orElse(""));
        assertTrue(page.contains("25 minutes old"), page.lines().findFirst().orElse(""));
    }

    @Test
    @DisplayName("before anything arrives the page says so rather than being empty or an error")
    void theUnrenderedAndEmptyStatesAreBothHonest() {
        ReadableBoardRenderer renderer = new ReadableBoardRenderer(() -> null, () -> NOW);
        assertTrue(renderer.page().startsWith("NOT YET RENDERED"),
                "before the first render, the page says the renderer has not run");
        String page = renderer.render();
        assertTrue(page.startsWith("NO PRICE"), "with nothing in, it says there is no price");
        assertTrue(page.contains("No board yet"), "and no board");
        assertTrue(page.contains("NO REFERENCE PRICE YET"),
                "and that the night has no reference price, which is why there are no rows");
        assertFalse(page.isBlank(), "never empty");
    }

    @Test
    @DisplayName("a board with no record at all still renders the price and the grid")
    void noBoardDoesNotCostThePrice() {
        ReadableBoardRenderer renderer = new ReadableBoardRenderer(() -> null, () -> NOW);
        renderer.onPriceJson(priceJson(CLOSE * 1_000_000L, 7176.24, "NATIVE_SPX_INDEX",
                "LIVE_INDEX"), CLOSE);
        renderer.onPriceJson(priceJson((NOW - 1_000L) * 1_000_000L, 7174.45, "ES_BASIS_DERIVED",
                "LIVE_ES_BASIS"), NOW - 1_000L);
        renderer.onBarJson(barJson(CLOSE + MIN, 717_999L, 100L));
        String page = renderer.render();
        assertTrue(page.contains("7174.45"), "the price survives a missing board");
        assertTrue(page.contains("7179.99"), "so does the grid");
        assertTrue(page.contains("No board yet"));
    }

    @Test
    @DisplayName("the render time is recorded, so a stopped renderer is visible")
    void theRenderTimeIsRecorded() {
        ReadableBoardRenderer renderer = new ReadableBoardRenderer(() -> null, () -> NOW);
        renderer.render();
        org.junit.jupiter.api.Assertions.assertEquals(NOW, renderer.renderedAtMs());
        org.junit.jupiter.api.Assertions.assertEquals(30_000L,
                ReadableBoardRenderer.RENDER_INTERVAL_MS, "C-61's cadence");
    }
}

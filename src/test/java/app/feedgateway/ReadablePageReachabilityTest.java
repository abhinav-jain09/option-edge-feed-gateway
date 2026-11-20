package app.feedgateway;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.feedgateway.readable.ReadableBoardRenderer;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Every page the reviewer is promised must be REACHABLE, and this test drives the controller to
 * prove it.
 *
 * <p><b>Why it exists.</b> On 2026-11-20 the whole of C-66's page set — {@code /now},
 * {@code /board}, {@code /footprint}, {@code /recent-full}, {@code /index} — together with C-64's
 * rows, C-65's holding times, C-69's VWAP and basis cells and C-75's headline, was written, tested
 * and <b>served by nothing</b>. `Pages` was not called from any controller. A suite of 2,317 green
 * tests said nothing about it, because every one of them called {@code Pages.now(..., new VwapHeld(), null)} directly.
 * That is the same defect class as a module unreachable from {@code main}: the unit was correct and
 * nothing executed the wiring.
 *
 * <p>So this test feeds real records through the gateway's own ingestion path and then asks the
 * <b>controller</b> for every page, asserting each one carries content only that page can produce.
 * A stub that answered with a blank body would pass a null check; it cannot pass these.
 */
class ReadablePageReachabilityTest {

    /**
     * NOW, not a fixed instant. {@code updateCache} has a freshness gate that evicts a stale
     * record before it reaches any tap, so a fixture dated to a past session would be dropped and
     * this test would report the wiring broken when it is the fixture that is old.
     */
    private static final long T0 = System.currentTimeMillis();

    /**
     * The fixture's expiry, derived from the SAME clock that stamps {@code updatedAt}.
     *
     * <p>It was the literal {@code "20261119"} beside an {@code updatedAt} of
     * {@code System.currentTimeMillis()}, which made the record self-contradictory the moment New
     * York crossed midnight: {@link app.feedgateway.readable.PreopenSource} prunes expiries older
     * than the newest record's own New York date, so the strike was pruned and
     * {@code gateway_readable_preopen_strikes} read 0. The test passed all day and went red at
     * 00:00 New York with no change to the code it covers. A fixture that mixes a wall clock with
     * a hard-coded date is a test with an expiry date of its own.
     */
    private static final String EXPIRY = java.time.Instant.ofEpochMilli(T0)
            .atZone(java.time.ZoneId.of("America/New_York")).toLocalDate()
            .format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE);

    private static String iso(long ms) {
        return java.time.Instant.ofEpochMilli(ms).toString();
    }

    /**
     * The snapshot as the indicator boundary emits it, with a fresh instant.
     *
     * <p>{@code runId} and {@code revision} are REQUIRED by the supersession gate the tap sits
     * behind: a record without them is refused outright, which is correct — a retired-run record
     * must never advance the owner's crossing count. The real record carries a UUID runId and an
     * integral revision (measured on Dev: revision 18287).
     */
    private static String indicators(double price, long atMs, long revision) {
        return indicators(price, atMs, atMs, revision);
    }

    /**
     * A snapshot with its two instants SEPARATE, which is the shape of a frozen feed: the
     * service keeps republishing (so {@code publishedAt} is current and the cache's freshness
     * gate admits it) while the measurement stands still (so {@code asOfEventTime} is hours
     * old). One instant for both made the record stale to that gate, so it never reached the
     * tap at all -- which is how this test first reported the page broken when the FIXTURE
     * was what the gateway would never have accepted.
     */
    private static String indicators(double price, long measuredAtMs, long publishedAtMs,
            long revision) {
        long atMs = measuredAtMs;
        // schemaVersion 1 and a parseable publishedAt are required too: indicatorsCacheKey is
        // STRICT and returns null without them, so the record never reaches the cache case at all.
        return "{\"symbol\":\"ES\",\"schemaVersion\":1,\"marketState\":\"OPEN\","
                + "\"currentPrice\":\"" + price + "\","
                + "\"publishedAt\":\"" + iso(publishedAtMs) + "\","
                + "\"runId\":\"14d0b4ba-3da1-4600-b08f-22cd54ca8e1a\",\"revision\":" + revision + ","
                + "\"asOfEventTime\":\"" + iso(atMs) + "\",\"session\":{\"vwapAnalysis\":{"
                + "\"vwap\":{\"value\":\"7187.0082\",\"state\":\"COMPLETE\"},"
                + "\"stdDev\":{\"value\":\"5.0993\",\"state\":\"COMPLETE\"},"
                + "\"bandUpper1\":{\"value\":\"7192.1075\",\"state\":\"COMPLETE\"},"
                + "\"bandLower1\":{\"value\":\"7181.9089\",\"state\":\"COMPLETE\"}},"
                + "\"vwapRth\":{\"vwap\":{\"value\":null,\"state\":\"WARMING_UP\"}}}}";
    }

    private static final String BASIS = "{\"level\":19.26,\"levelKind\":\"ANCHORED\"}";

    private static final String PROFILE =
            "{\"schema\":1,\"symbol\":\"ES.v.0\",\"timeframe\":\"session\","
            + "\"sessionDate\":\"2026-11-20\",\"gridCents\":25,\"levelCount\":4,"
            + "\"asOfMs\":1795134000000,"
            + "\"columns\":[\"priceCents\",\"sellerInit\",\"buyerInit\",\"unknown\"],"
            + "\"levels\":[[716025,5,0,0],[716050,10,1,0],[716075,21,15,0],[716100,35,22,2]]}";

    private static String preopen(double strike, double netGex) {
        return "{\"symbol\":\"SPX\",\"expiry\":\"" + EXPIRY + "\",\"strike\":" + strike
                + ",\"netGex\":" + netGex + ",\"gammaSign\":\"POSITIVE\","
                + "\"canonicalSpot\":7160.50,\"updatedAt\":\"" + iso(T0 - 60_000L) + "\","
                + "\"staleAfterMs\":300000}";
    }

    private static String priceJson(long atMs, double spx, String source, String quality) {
        return "{\"schemaVersion\":1,\"symbol\":\"SPX\",\"price\":\"" + spx + "\","
                + "\"canonicalSpot\":\"" + spx + "\",\"source\":\"" + source + "\","
                + "\"quality\":\"" + quality + "\",\"spotEventTimeNs\":" + (atMs * 1_000_000L) + "}";
    }

    private static String barJson(long startMs, long spxCloseCents, String timeframe) {
        return "{\"schemaVersion\":6,\"symbol\":\"ES.v.0\",\"timeframe\":\"" + timeframe + "\","
                + "\"sessionDate\":\"2026-11-19\",\"observations\":{\"barStartMs\":" + startMs
                + ",\"complete\":true,\"openCents\":718550,\"closeCents\":718975,"
                + "\"spxEqOpenCents\":" + (spxCloseCents - 20) + ",\"spxEqHighCents\":"
                + (spxCloseCents + 30) + ",\"spxEqLowCents\":" + (spxCloseCents - 40)
                + ",\"spxEqCloseCents\":" + spxCloseCents + ",\"spxEqBasisState\":\"ANCHORED\","
                + "\"volume\":1200,\"buyerContracts\":700,\"sellerContracts\":500}}";
    }

    private static FeedGatewayService service() {
        return new FeedGatewayService(new GatewaySettings(), new ObjectMapper(),
                new HpsfGatewayViewMapper(), null);
    }

    /** One record through the REAL ingestion path, with its own broker timestamp. */
    private static void ingestAt(FeedGatewayService service, String event, String key, String json,
            long timestampMs) throws Exception {
        Class<?> bindingClass = Class.forName("app.feedgateway.FeedGatewayService$TopicBinding");
        Constructor<?> ctor = bindingClass.getDeclaredConstructor(String.class, String.class);
        ctor.setAccessible(true);
        Object binding = ctor.newInstance("DATABENTO", event);
        Method updateCache = FeedGatewayService.class.getDeclaredMethod(
                "updateCache", bindingClass, ConsumerRecord.class, String.class);
        updateCache.setAccessible(true);
        ConsumerRecord<String, Object> record = new ConsumerRecord<String, Object>(
                "t", 0, 1L, timestampMs,
                org.apache.kafka.common.record.TimestampType.CREATE_TIME, 0, 0, key, null,
                new org.apache.kafka.common.header.internals.RecordHeaders(),
                java.util.Optional.empty());
        updateCache.invoke(service, binding, record, json);
    }

    /** One record through the REAL ingestion path, by the event name the registration uses. */
    private static void ingest(FeedGatewayService service, String event, String key, String json)
            throws Exception {
        Class<?> bindingClass = Class.forName("app.feedgateway.FeedGatewayService$TopicBinding");
        Constructor<?> ctor = bindingClass.getDeclaredConstructor(String.class, String.class);
        ctor.setAccessible(true);
        Object binding = ctor.newInstance("DATABENTO", event);
        Method updateCache = FeedGatewayService.class.getDeclaredMethod(
                "updateCache", bindingClass, ConsumerRecord.class, String.class);
        updateCache.setAccessible(true);
        updateCache.invoke(service, binding,
                new ConsumerRecord<String, Object>("t", 0, 1L, key, null), json);
    }

    /** A gateway fed everything the pages need, through its own paths. */
    private static FeedGatewayService fed() throws Exception {
        FeedGatewayService s = service();
        ingest(s, "spx-basis", "SPX", BASIS);
        ingest(s, "gex-preopen", "SPX|" + EXPIRY + "|7160", preopen(7160.0, 2.67E8));
        ingest(s, "gex-preopen", "SPX|" + EXPIRY + "|7165", preopen(7165.0, 6.04E8));
        ingest(s, "gex-preopen", "SPX|" + EXPIRY + "|7155", preopen(7155.0, 3.28E8));

        // The index print that fixes the night and the reference price, then a live overnight
        // price, then 30-second bars for the Recent table. Without a price every page returns its
        // one NO PRICE line, which is correct behaviour and proves nothing about the cells.
        long close = T0 - 3_600_000L;
        ingestAt(s, "spx-price", "SPX",
                priceJson(close, 7176.24, "NATIVE_SPX_INDEX", "LIVE_INDEX"), close);
        ingestAt(s, "spx-price", "SPX",
                priceJson(T0 - 5_000L, 7160.50, "ES_BASIS_DERIVED", "LIVE_ES_BASIS"), T0 - 5_000L);
        for (int i = 1; i <= 4; i++) {
            s.readableBarForTest(barJson(T0 - i * 30_000L, 716_050L, "30s"));
            s.readableBarForTest(barJson(T0 - i * 60_000L, 716_050L, "1m"));
        }
        s.renderReadableBoard();
        return s;
    }

    @Test
    @DisplayName("every promised page is reachable through the controller and carries its own content")
    void everyPromisedPageIsReachable() throws Exception {
        FeedGatewayService service = fed();
        ReadableBoardController controller = new ReadableBoardController(service);

        List<String> missing = new ArrayList<>();
        for (String name : ReadableBoardRenderer.PAGES) {
            String body = controller.namedPage(name, 1).getBody();
            if (body == null || body.isBlank() || body.startsWith("nosuchpage|")) {
                missing.add(name + " -> " + (body == null ? "null" : body.lines().findFirst().orElse("")));
            }
        }
        assertTrue(missing.isEmpty(),
                "a page the reviewer is promised must be served by something; these are not: "
                        + missing);
    }

    @Test
    @DisplayName("/board carries the pre-open board the owner reads at 06:30")
    void theBoardPageCarriesTheBoard() throws Exception {
        String page = new ReadableBoardController(fed()).namedPage("board", 1).getBody();
        assertNotNull(page);
        assertTrue(page.contains("|src=IBKR pre-open"),
                "the board names its source, so it cannot read as the map board: " + page);
        assertTrue(page.contains("|rows="), "and its completeness: " + page);
        assertTrue(page.contains("largest|above|") && page.contains("largest|below|"),
                "and the owner's two wall distances: " + page);
    }

    @Test
    @DisplayName("/now carries C-75's headline, C-69's VWAP cell and C-65's holding line")
    void theNowPageCarriesTheCellsNothingElseServes() throws Exception {
        FeedGatewayService service = fed();
        ingestAt(service, "indicators", "ES", indicators(7190.00, T0 - 10_000L, 18287),
                T0 - 10_000L);
        String page = new ReadableBoardController(service).namedPage("now", 1).getBody();
        assertNotNull(page);

        assertTrue(page.contains("\nnow|"), "C-75's headline is on it: " + page);
        assertTrue(page.contains("vwapes|"),
                "C-69's ES VWAP cell, which only this page renders: " + page);
        assertTrue(page.contains("7187.01"),
                "with the producer's own figure, which nothing else on the service renders");
        assertTrue(page.contains("basis|19.26 ANCHORED"),
                "and the basis cell, which proves the basis tap reached the page too");
        assertTrue(page.contains("hold|side="),
                "C-65's holding line, advanced by the indicators tap: " + page);
    }

    @Test
    @DisplayName("/footprint reads the profile by column NAME, which proves that tap is wired")
    void theFootprintPageCarriesTheProfile() throws Exception {
        FeedGatewayService service = fed();
        ingest(service, "gamma-zone-profile", "ES.v.0|session", PROFILE);
        String page = new ReadableBoardController(service).namedPage("footprint", 1).getBody();
        assertNotNull(page);
        assertTrue(page.contains("px|espx|buy|sell|unk|total"),
                "the by-price header, which only this page produces: " + page);
    }

    @Test
    @DisplayName("the metrics distinguish 'no records yet' from 'the tap is not running'")
    void theMetricsTellBrokenFromEmpty() throws Exception {
        // A fresh gateway: no pre-open records at all. This is what overnight looks like, and the
        // page says "No board yet" either way -- which is exactly why these numbers exist.
        FeedGatewayService empty = service();
        String before = empty.metrics();
        assertTrue(before.contains("gateway_readable_preopen_records_total 0"), "no records yet");
        assertTrue(before.contains("gateway_readable_board_rows 0"), "and so no board");
        assertTrue(before.contains("gateway_readable_reference_resolved 0"),
                "and no recovered close");
        assertTrue(before.contains("gateway_readable_side_observations_total 0"));

        // One real pre-open record through the real path, and the numbers move.
        ingest(empty, "gex-preopen", "SPX|" + EXPIRY + "|7160", preopen(7160.0, 2.67E8));
        String after = empty.metrics();
        assertTrue(after.contains("gateway_readable_preopen_records_total 1"),
                "a rising count is how an operator knows the tap is alive at 06:30 New York");
        assertTrue(after.contains("gateway_readable_preopen_strikes 1"));
        assertTrue(after.contains("gateway_readable_preopen_unreadable_total 0"));
        assertTrue(after.contains("gateway_readable_preopen_refused_at_cap_total 0"),
                "the ceiling never binds against the real feed");

        // An unreadable record is counted separately, because a shape change on the topic must
        // not look like a quiet feed.
        ingest(empty, "gex-preopen", "SPX|" + EXPIRY + "|7165", "not json at all");
        assertTrue(empty.metrics().contains("gateway_readable_preopen_unreadable_total 1"),
                "a shape change shows up here rather than as silence");

        // And the side path and the reference both report once they have something.
        ingestAt(empty, "indicators", "ES", indicators(7190.00, T0 - 10_000L, 18287), T0 - 10_000L);
        assertTrue(empty.metrics().contains("gateway_readable_side_observations_total 1"),
                "a flat zero during a session would mean the holding times are not advancing");
    }

    @Test
    @DisplayName("/now names BOTH stalled inputs, with each feed's own instant")
    void theNowPageNamesTheIndicatorSnapshotToo() throws Exception {
        // The reviewer's check: "the stalled lines name the footprint bars AND the indicator
        // snapshot with the time they stopped". /now used to get the long page's list, which named
        // only the bars -- so the one feed that was actually down went unnamed on the one page
        // whose VWAP cells it feeds.
        // A gateway with a live price and NO bars at all -- which is tonight's real shape, since
        // the trade path stopped before this pod started -- and an indicators snapshot whose own
        // instant is five hours old.
        FeedGatewayService service = service();
        ingestAt(service, "spx-price", "SPX",
                priceJson(T0 - 5_000L, 7160.50, "ES_BASIS_DERIVED", "LIVE_ES_BASIS"), T0 - 5_000L);
        // Frozen five hours ago but STILL BEING REPUBLISHED, exactly as the live one is: the
        // gateway admits it on publishedAt and the page must report its asOfEventTime.
        long stoppedAt = T0 - 5L * 3_600_000L;
        ingestAt(service, "indicators", "ES",
                indicators(7190.00, stoppedAt, T0 - 5_000L, 18287), T0 - 5_000L);

        String page = new ReadableBoardController(service).namedPage("now", 1).getBody();
        assertNotNull(page);

        assertTrue(page.contains("ES indicators snapshot"),
                "the feed that is down must be named: " + page);
        assertTrue(page.contains("footprint bars"),
                "and so must the bars, which this pod has never received: " + page);
        assertTrue(page.contains("never received"),
                "an input with no record at all says so rather than being given an age");
        // On /now the stalled lines are C-75's COMPACT form -- name, age, instant -- because every
        // headline line has to stay inside 64 characters for the reviewer's reader to return it
        // whole. "Which columns it freezes" is the long page's prose form, asserted in
        // StalledInputsTest; here the contract is the reviewer's own words: name and the time it
        // stopped.
        assertTrue(page.contains("stalled|ES indicators snapshot|"),
                "the compact stalled line names the feed: " + page);
        assertTrue(page.contains("stalled|footprint bars|"), page);

        // The instant is the snapshot's OWN asOfEventTime, not its arrival: a frozen snapshot is
        // republished for hours with the same instant, and arrival would call it current.
        String stalledLine = page.lines()
                .filter(l -> l.contains("ES indicators snapshot")).findFirst().orElseThrow();
        assertTrue(stalledLine.contains("5h00m") || stalledLine.contains("4h5"),
                "the age is measured from the record's own instant: " + stalledLine);
        assertTrue(stalledLine.contains("since ") && stalledLine.contains("NY"),
                "and it names the time it stopped, which is the reviewer's own wording: "
                        + stalledLine);
        assertTrue(stalledLine.length() <= 64,
                "and it still fits C-75's bound: " + stalledLine.length() + " chars");

        // And a CURRENT snapshot produces no line for it, so the banner stays a list of what
        // cannot be trusted rather than a status board.
        FeedGatewayService fresh = fed();
        ingestAt(fresh, "indicators", "ES", indicators(7190.00, T0 - 10_000L, 18288), T0 - 10_000L);
        String ok = new ReadableBoardController(fresh).namedPage("now", 1).getBody();
        assertFalse(ok.contains("ES indicators snapshot"),
                "a live snapshot is not listed as stalled: " + ok);
    }

    @Test
    @DisplayName("an unknown page name lists the names rather than leaving a reader guessing")
    void anUnknownNameListsTheNames() throws Exception {
        String body = new ReadableBoardController(fed()).namedPage("nope", 1).getBody();
        assertNotNull(body);
        assertTrue(body.startsWith("nosuchpage|nope|pages are "), body);
        for (String name : ReadableBoardRenderer.PAGES) {
            assertTrue(body.contains(name), "every name is listed: " + body);
        }
    }

    @Test
    @DisplayName("C-71's rebuild reaches the page the controller serves")
    void theRebuildReachesTheServedPage() throws Exception {
        FeedGatewayService service = fed();
        // Two moments, each above its own line, then one below: one crossing, rebuilt at start-up.
        List<String> history = List.of(
                indicators(7190.00, T0 - 1_800_000L, 18287),
                indicators(7180.00, T0 - 900_000L, 18288));
        assertEquals(2, service.rebuildReadableSides(history), "both snapshots were usable");

        String page = new ReadableBoardController(service).namedPage("now", 1).getBody();
        assertTrue(page.contains("hold|side=B"),
                "the rebuilt path ends below the line, on the page itself: " + page);
        assertTrue(page.contains("|crosses=1"),
                "and the crossing it reconstructed is the owner's, not zero: " + page);
    }
}

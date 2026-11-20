package app.feedgateway;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.feedgateway.readable.ReferencePrice;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * C-74 through the service: the start-up read-back must actually reach the page.
 *
 * <p>Driven rather than asserted about. The resolver has its own tests; what this one proves is the
 * thing that fails silently — that {@code seedReadableReference} is wired to the renderer the
 * controller serves from, so a recovered close reaches the bytes a reader receives. A source pin
 * saying the call exists would have passed on a method nothing called.
 */
class ReadableReferenceWiringTest {

    /** 2026-11-19 16:00 New York, the cash close, and a start five hours later. */
    private static final long CLOSE = 1_795_122_000_000L;

    private static String priceJson(long atMs, double spx, String source, String quality) {
        return "{\"schemaVersion\":1,\"symbol\":\"SPX\",\"price\":\"" + spx + "\","
                + "\"canonicalSpot\":\"" + spx + "\",\"source\":\"" + source + "\","
                + "\"quality\":\"" + quality + "\",\"spotEventTimeNs\":" + (atMs * 1_000_000L) + "}";
    }

    private static ReferencePrice.History history(List<String> prices, List<String> bars) {
        return new ReferencePrice.History() {
            @Override
            public List<String> pricesBetween(long fromMs, long toMs, int limit) {
                return prices;
            }

            @Override
            public List<String> barsBetween(long fromMs, long toMs) {
                return bars;
            }
        };
    }

    private static FeedGatewayService service() {
        return new FeedGatewayService(new GatewaySettings(), new ObjectMapper(),
                new HpsfGatewayViewMapper(), null);
    }

    @Test
    @DisplayName("a close recovered at start-up reaches the bytes the controller serves")
    void theRecoveredCloseReachesTheServedPage() throws Exception {
        FeedGatewayService service = service();
        List<String> prices = new ArrayList<>();
        prices.add(priceJson(CLOSE - 1_000L, 7176.24, "NATIVE_SPX_INDEX", "LIVE_INDEX"));
        prices.add(priceJson(CLOSE + 3_600_000L, 7160.10, "ES_BASIS_DERIVED", "LIVE_ES_BASIS"));

        service.seedReadableReference(history(prices, List.of()));
        service.renderReadableBoard();
        String page = service.readableBoardPage();

        assertNotNull(page);
        assertTrue(page.contains("7176.24"),
                "the recovered close must be on the page the controller serves: " + page);
        assertTrue(page.contains("REFERENCE — previous cash close from the SPX index's own last "
                + "print"), "named by source, because an index print and a futures bar's "
                + "SPX-equivalent are different facts: " + page);
        assertFalse(page.contains("NO REFERENCE PRICE YET"),
                "which is the whole point of C-74");
    }

    @Test
    @DisplayName("with nothing recoverable the page states the absence and the gateway still runs")
    void nothingRecoverableIsStatedNotFatal() throws Exception {
        FeedGatewayService service = service();
        service.seedReadableReference(history(List.of(), List.of()));
        service.renderReadableBoard();
        String page = service.readableBoardPage();

        assertTrue(page.contains("NO REFERENCE PRICE"), page);
        assertTrue(page.contains("not recoverable"),
                "with the reason, so an operator is not left guessing: " + page);
    }

    @Test
    @DisplayName("the seed is called from start-up, not merely defined")
    void theSeedIsCalledFromStartUp() throws Exception {
        // The one thing reflection can establish cheaply and exactly: start() references the
        // no-argument seed. The behavioural half is the test above; this half guards against the
        // method existing and never being invoked, which is how the live block went unreached
        // through four changes in the map service.
        String src = java.nio.file.Files.readString(
                java.nio.file.Path.of("src/main/java/app/feedgateway/FeedGatewayService.java"));
        int start = src.indexOf("public void start() {");
        assertTrue(start > 0, "start() must exist");
        int end = src.indexOf("\n    }", start);
        String body = src.substring(start, end);
        assertTrue(body.contains("seedReadableReference();"),
                "start() must call the read-back; a seed nothing calls recovers nothing");

        // And the field it writes through is the one the controller reads from.
        Field f = FeedGatewayService.class.getDeclaredField("readableBoard");
        f.setAccessible(true);
        assertNotNull(f.get(service()), "the renderer the seed writes to must exist");
    }

    @Test
    @DisplayName("C-71's rebuild seeks the ES indicator topic, not the SPX one")
    void theRebuildSeeksTheEsTopic() {
        FeedGatewayService service = service();
        GatewaySettings settings = new GatewaySettings();

        String seeked = service.readableSidesTopic();
        assertEquals("es.options.indicators.snapshot.current", seeked,
                "the readable pages read ES against ES, so the rebuild reads the ES stream");
        assertNotEquals(settings.indicatorsSnapshotTopic(), seeked,
                "and NOT the prefix-resolved local topic, which on dev is the SPX computation: "
                        + "every record on it carries vwap ABSENT / SOURCE_HAS_NO_VOLUME, so a "
                        + "rebuild that seeked it converted thousands of records and replayed zero");
        assertTrue(settings.indicatorsSnapshotTopics().contains(seeked),
                "it is one of the topics the gateway already subscribes to, not a new binding");
    }

    @Test
    @DisplayName("a snapshot of the other instrument replays nothing, by name")
    void theOtherInstrumentReplaysNothing() {
        FeedGatewayService service = service();
        String spx = "{\"symbol\":\"SPX\",\"currentPrice\":\"6980.12\","
                + "\"asOfEventTime\":\"2026-11-20T05:04:09.870Z\","
                + "\"session\":{\"vwapAnalysis\":{\"vwap\":{\"value\":null,"
                + "\"state\":\"ABSENT\",\"reason\":\"SOURCE_HAS_NO_VOLUME\"}}}}";
        assertEquals(0, service.rebuildReadableSides(List.of(spx, spx)),
                "SPX snapshots are refused on their symbol, so none is replayed");
    }

    /**
     * The real basis record off `spx.basis.state`, 2026-11-19 16:47:44 New York, with the two
     * `a1*ReportRef` fields left out: they carry a local filesystem path and the basis reading
     * does not look at them.
     */
    private static final String BASIS = "{\"schemaVersion\":1,\"algorithmVersion\":\"v2.0\","
            + "\"level\":19.26000000000022,\"valueReferenceMs\":1795122000000,"
            + "\"levelKind\":\"ANCHORED\",\"expiryMs\":1797604200000,"
            + "\"newestEvidenceMs\":null,\"epochId\":5,\"esContract\":\"10252\","
            + "\"contractGeneration\":0,\"quarantine\":null,\"noAnchorBaseline\":false,"
            + "\"driftGuardSuspendedByAck\":false,\"nextSessionQuarantinedOpen\":true,"
            + "\"trustedAnchor\":{\"level\":19.26000000000022,"
            + "\"referenceMs\":1795122000000,\"expiryMs\":1797604200000,"
            + "\"esContract\":\"10252\",\"contractGeneration\":0},"
            + "\"unavailableReason\":null,\"configHash\":\"e79ac06545a5115d\"}";

    /** The real ES snapshot shape, with a session VWAP that carries a figure. */
    private static final String ES_SNAPSHOT =
            "{\"symbol\":\"ES\",\"schemaVersion\":1,\"marketState\":\"OPEN\","
            + "\"currentPrice\":\"7158.75\","
            + "\"asOfEventTime\":\"2026-11-20T05:04:09.870Z\","
            + "\"session\":{\"vwapAnalysis\":{\"vwap\":{\"value\":\"7177.4637\","
            + "\"state\":\"INCOMPLETE\",\"reason\":\"DEADLINE_FINALIZED\","
            + "\"incompleteKind\":\"LATCHED\"},"
            + "\"stdDev\":{\"value\":\"5.0993\",\"state\":\"INCOMPLETE\"}},"
            + "\"vwapRth\":{\"vwap\":{\"value\":null,\"state\":\"WARMING_UP\","
            + "\"reason\":\"WARMING_UP_WINDOW\"}}}}";

    @Test
    @DisplayName("a basis seeded at start-up fills the SPX-equivalent column the same minute")
    void aSeededBasisFillsTheSpxColumn() throws Exception {
        FeedGatewayService service = service();

        // Without the seed, this is what the owner saw all night: the level had been on the topic
        // for eight hours and the page said the producer had published nothing.
        service.readableBoardForTest().onIndicatorsJson(ES_SNAPSHOT);
        service.renderReadableBoard();
        assertTrue(service.readableNamedPage("now", 1)
                        .contains("vwapspx|absent|no basis published on spx.basis.state"),
                "the absence as it stands before the seed");

        service.seedReadableBasis(BASIS);
        service.renderReadableBoard();
        String page = service.readableNamedPage("now", 1);

        assertTrue(page.contains("basis|"),
                "the basis line appears once the level is known:\n" + page);
        assertFalse(page.contains("vwapspx|absent"),
                "and the SPX-equivalent column is no longer blank:\n" + page);
        assertTrue(page.contains("7158.20"),
                "7177.4637 ES minus the 19.26 ANCHORED level, translated by the platform's own "
                        + "published basis and never by arithmetic of ours:\n" + page);
        assertTrue(page.contains("nextopen=QUARANTINED"),
                "and the record's own next-open quarantine travels with it:\n" + page);
    }

    @Test
    @DisplayName("a blank or unreadable basis leaves the column absent rather than guessing a level")
    void anUnreadableBasisLeavesTheColumnAbsent() throws Exception {
        FeedGatewayService service = service();
        service.readableBoardForTest().onIndicatorsJson(ES_SNAPSHOT);
        service.seedReadableBasis("not json at all");
        service.renderReadableBoard();
        String page = service.readableNamedPage("now", 1);
        assertTrue(page.contains("vwapspx|absent"),
                "an unreadable level is no level:\n" + page);
    }
}

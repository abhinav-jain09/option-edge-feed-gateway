package app.feedgateway;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * Early-auction verdict ledger relay (EARLY-AUCTION-VERDICT-STAGE1-GATE1.md): opt-in, state CACHE
 * consumer only, no WebSocket event — a keyed, append-ordered session view behind the polled
 * {@code GET /api/early-auction/events} endpoint, retained for the current plus previous trade date.
 * Identity is rebuilt from the VALIDATED payload (EAV-R0d), never taken from the Kafka key (CODE
 * round 1), so a malformed or foreign key can neither mint a duplicate row nor collapse two records.
 */
class EarlyAuctionVerdictWiringTest {

    private static FeedGatewayService service() {
        return new FeedGatewayService(new GatewaySettings(), new ObjectMapper(), new HpsfGatewayViewMapper(), null);
    }

    /** One contracted ledger record: the EAV-R0d identity spine plus a kind-specific remainder. */
    private static String rec(String kind, String tradeDate, String discriminator, long seq) {
        return "{\"schemaVersion\":1,\"kind\":\"" + kind + "\",\"literalsHash\":\"abcd1234\","
                + "\"sessionId\":\"dev|" + tradeDate + "|r0\",\"tradeDate\":\"" + tradeDate + "\""
                + (discriminator == null ? "" : ",\"discriminator\":\"" + discriminator + "\"")
                + ",\"seq\":" + seq + "}";
    }

    private static String source() throws Exception {
        return Files.readString(Path.of("src/main/java/app/feedgateway/FeedGatewayService.java"));
    }

    @Test
    void isOptInAndNamesItsOwnTopic() {
        GatewaySettings s = new GatewaySettings();
        assertFalse(s.earlyAuctionVerdictEnabled(), "OFF by default — enabling the relay is the owner's own step");
        assertEquals("early-auction.verdicts.shadow", s.earlyAuctionVerdictTopic());
        assertEquals(7L * 24 * 3_600_000L, s.earlyAuctionVerdictSeekBackMs(),
                "the ledger's retention is infinite, so the WINDOW must bound the restart replay");
    }

    @Test
    void boundaryAcceptsOnlyOneObjectCarryingTheIdentitySpine() {
        var s = service();
        String ok = rec("PREDICTION", "2026-10-14", "p1", 1);
        assertEquals("2026-10-14", s.validateEarlyAuctionVerdict(ok));
        assertEquals("2026-10-14", s.validateEarlyAuctionVerdict(rec("SESSION_CLOSE", "2026-10-14", null, 2)),
                "a terminal has no discriminator and is still a contracted record");
        assertEquals("2026-10-14", s.validateEarlyAuctionVerdict(rec("SOME_FUTURE_KIND_V2", "2026-10-14", "d", 3)),
                "schema evolution is additive: a kind this gateway has never heard of is still a ledger record");
        assertNull(s.validateEarlyAuctionVerdict(null));
        assertNull(s.validateEarlyAuctionVerdict("not json"));
        assertNull(s.validateEarlyAuctionVerdict("[1,2]"));
        assertNull(s.validateEarlyAuctionVerdict(ok + "null"), "\"{…}null\" parses leniently but is NOT one object");
        assertNull(s.validateEarlyAuctionVerdict(ok + " {}"));
        assertNull(s.validateEarlyAuctionVerdict(ok.replace("\"kind\":\"PREDICTION\",", "")), "kind is the spine");
        assertNull(s.validateEarlyAuctionVerdict(ok.replace("\"kind\":\"PREDICTION\"", "\"kind\":\"prediction!\"")),
                "a kind is SHAPED (A-Z, digits, underscores), never free text");
        assertNull(s.validateEarlyAuctionVerdict(ok.replace("\"kind\":\"PREDICTION\"", "\"kind\":7")),
                "a numeric kind is a foreign record, not a lenient spelling");
        assertNull(s.validateEarlyAuctionVerdict(ok.replace("\"sessionId\":\"dev|2026-10-14|r0\",", "")));
        assertNull(s.validateEarlyAuctionVerdict(
                ok.replace("\"sessionId\":\"dev|2026-10-14|r0\"", "\"sessionId\":42")),
                "a numeric sessionId is refused (CODE round 1)");
        assertNull(s.validateEarlyAuctionVerdict(ok.replace("\"literalsHash\":\"abcd1234\",", "")));
        assertNull(s.validateEarlyAuctionVerdict(
                ok.replace("\"literalsHash\":\"abcd1234\"", "\"literalsHash\":1234")),
                "a numeric literalsHash is refused (CODE round 1)");
        assertNull(s.validateEarlyAuctionVerdict(
                ok.replace("\"sessionId\":\"dev|2026-10-14|r0\"", "\"sessionId\":\"" + "x".repeat(300) + "\"")),
                "an identity component is retained memory, so it is bounded");
        assertNull(s.validateEarlyAuctionVerdict(ok.replace("\"tradeDate\":\"2026-10-14\"", "\"tradeDate\":\"20261014\"")),
                "tradeDate must be YYYY-MM-DD");
        assertNull(s.validateEarlyAuctionVerdict(ok.replace("\"tradeDate\":\"2026-10-14\"", "\"tradeDate\":\"2026-99-99\"")),
                "2026-99-99 is date-SHAPED, not a date (CODE round 1)");
        String oversize = ok.replace(",\"seq\":1",
                ",\"pad\":\"" + "x".repeat(FeedGatewayService.EARLY_AUCTION_VERDICT_MAX_BYTES) + "\",\"seq\":1");
        assertNull(s.validateEarlyAuctionVerdict(oversize));
    }

    @Test
    void identityIsThePayloadsOwnLedgerKeyAndKeepsAppendOrder() {
        var s = service();
        s.onEarlyAuctionVerdictRecord(rec("PREDICTION", "2026-10-14", "p1", 1), 10);
        s.onEarlyAuctionVerdictRecord(rec("OUTCOME", "2026-10-14", "p1", 2), 11);
        // crash-recovery re-emission: the same payload identity at a later offset is a semantic no-op
        s.onEarlyAuctionVerdictRecord(rec("PREDICTION", "2026-10-14", "p1", 1), 12);
        var page = s.earlyAuctionVerdictPage(null, -1, 100);
        assertEquals("2026-10-14", page.tradeDate());
        assertEquals(2, page.events().size(), "the redelivered record is dropped, not duplicated");
        assertEquals(10, page.events().get(0).offset());
        assertEquals(11, page.events().get(1).offset(), "append (offset) order is the page order");
        // TWO terminals for one session fold onto one row — one terminal per session IS the contract
        s.onEarlyAuctionVerdictRecord(rec("SESSION_CLOSE", "2026-10-14", null, 3), 13);
        s.onEarlyAuctionVerdictRecord(rec("SESSION_CLOSE", "2026-10-14", null, 3), 14);
        assertEquals(3, s.earlyAuctionVerdictsCached());
        // a record the boundary refuses is counted, never stored
        s.onEarlyAuctionVerdictRecord("not json", 15);
        assertEquals(1, s.earlyAuctionVerdictDropsForTest());
        assertEquals(3, s.earlyAuctionVerdictsCached());
    }

    @Test
    void distinctRecordsWithoutADiscriminatorNeverCollapse() {
        // Outside the contract (a NON-terminal record missing its discriminator), the view must
        // choose visibility over dedupe: two distinct MISS records must both survive (CODE round 1),
        // keyed by their own offsets — while a redelivery of the SAME offset still folds.
        var s = service();
        s.onEarlyAuctionVerdictRecord(rec("MISS", "2026-10-14", null, 1), 20);
        s.onEarlyAuctionVerdictRecord(rec("MISS", "2026-10-14", null, 2), 21);
        assertEquals(2, s.earlyAuctionVerdictsCached(), "distinct keyless records are never hidden behind each other");
        s.onEarlyAuctionVerdictRecord(rec("MISS", "2026-10-14", null, 1), 20);
        assertEquals(2, s.earlyAuctionVerdictsCached(), "the same offset redelivered still folds");
    }

    @Test
    void pageCursorsByOffsetAndHonoursTheLimit() {
        var s = service();
        for (int i = 0; i < 5; i++) {
            s.onEarlyAuctionVerdictRecord(rec("MISS", "2026-10-14", "m" + i, i), i);
        }
        var first = s.earlyAuctionVerdictPage(null, -1, 2);
        assertEquals(2, first.events().size());
        assertEquals(1, first.events().get(1).offset());
        var rest = s.earlyAuctionVerdictPage(null, first.events().get(1).offset(), 100);
        assertEquals(3, rest.events().size(), "afterOffset is an exclusive cursor");
        assertEquals(2, rest.events().get(0).offset());
        var labelled = s.earlyAuctionVerdictPage("2026-10-13", -1, 100);
        assertEquals("2026-10-13", labelled.tradeDate(), "a date the view does not hold answers an empty page, labelled");
        assertTrue(labelled.events().isEmpty());
    }

    @Test
    void retentionHoldsTheTwoGreatestTradeDates() {
        var s = service();
        s.onEarlyAuctionVerdictRecord(rec("SESSION_CLOSE", "2026-10-10", null, 1), 1);
        s.onEarlyAuctionVerdictRecord(rec("SESSION_CLOSE", "2026-10-13", null, 2), 2);
        s.onEarlyAuctionVerdictRecord(rec("PREDICTION", "2026-10-14", "p1", 3), 3);
        assertEquals("2026-10-14", s.earlyAuctionVerdictTradeDateForTest());
        assertTrue(s.earlyAuctionVerdictPage("2026-10-10", -1, 100).events().isEmpty(),
                "a third-greatest date is evicted when a newer one rolls in");
        assertEquals(1, s.earlyAuctionVerdictPage("2026-10-13", -1, 100).events().size(),
                "the previous date stays inspectable");
        // a dead session's replay (older than the retained previous date) is dropped, never resurrected
        s.onEarlyAuctionVerdictRecord(rec("MISS", "2026-10-09", "m1", 4), 4);
        assertTrue(s.earlyAuctionVerdictPage("2026-10-09", -1, 100).events().isEmpty());
        assertEquals(1, s.earlyAuctionVerdictDropsForTest());
    }

    @Test
    void capRefusesWithinADateButARollForwardAlwaysAdmits() {
        var s = service();
        for (int i = 0; i < FeedGatewayService.EARLY_AUCTION_VERDICT_MAX_RECORDS; i++) {
            s.onEarlyAuctionVerdictRecord(rec("MISS", "2026-10-14", "m" + i, i), i);
        }
        assertEquals(FeedGatewayService.EARLY_AUCTION_VERDICT_MAX_RECORDS, s.earlyAuctionVerdictsCached());
        s.onEarlyAuctionVerdictRecord(rec("MISS", "2026-10-14", "overflow", -1), 99_999);
        assertEquals(1, s.earlyAuctionVerdictRefusedAtCapForTest(), "within one date the HEAD is the evidence: the tail is refused");
        assertEquals(FeedGatewayService.EARLY_AUCTION_VERDICT_MAX_RECORDS, s.earlyAuctionVerdictsCached());
        // a NEW trade date must never be frozen out by a view pinned full with yesterday
        s.onEarlyAuctionVerdictRecord(rec("PREDICTION", "2026-10-15", "p1", 0), 100_000);
        assertEquals("2026-10-15", s.earlyAuctionVerdictTradeDateForTest());
        assertEquals(1, s.earlyAuctionVerdictsCached());
    }

    @Test
    void pageCarriesTheEnabledFlagSoThePanelCanTellRelayOffFromQuiet() {
        assertFalse(service().earlyAuctionVerdictPage(null, -1, 10).enabled());
        System.setProperty("GATEWAY_EARLY_AUCTION_VERDICT_ENABLED", "true");
        try {
            assertTrue(service().earlyAuctionVerdictPage(null, -1, 10).enabled());
        } finally {
            System.clearProperty("GATEWAY_EARLY_AUCTION_VERDICT_ENABLED");
        }
    }

    @Test
    void cacheConsumerBindsTheTopicBehindTheFlagAndLiveConsumerDoesNot() throws Exception {
        String src = source();
        int live = src.indexOf("private void runJsonStateLiveConsumer()");
        int cache = src.indexOf("private void runJsonStateCacheConsumer()");
        assertTrue(live > 0 && cache > 0);
        String binding = "topicEvents.put(settings.earlyAuctionVerdictTopic(),";
        int at = src.indexOf(binding);
        assertTrue(at > 0, "the cache consumer binds the topic");
        assertEquals(at, src.lastIndexOf(binding), "bound exactly once");
        int cacheEnd = src.indexOf("\n    }\n", cache);
        assertTrue(at > cache && at < cacheEnd, "the binding lives in the CACHE consumer — the opposite polarity of market-structure");
        assertTrue(src.lastIndexOf("if (settings.earlyAuctionVerdictEnabled()) {", at) > cache, "…behind the flag");
        int liveEnd = src.indexOf("\n    }\n", live);
        assertFalse(src.substring(live, liveEnd).contains("earlyAuctionVerdictTopic"), "the live consumer never subscribes");
    }

    @Test
    void deliveryIsAKeyedUpsertBeforeTheGenericCacheAndNeverBroadcasts() throws Exception {
        String src = source();
        int branch = src.indexOf("if (binding != null && \"early-auction-verdict\".equals(binding.event())) {");
        assertTrue(branch > 0);
        String body = src.substring(branch, src.indexOf("continue;", branch));
        assertTrue(body.contains("onEarlyAuctionVerdictRecord(json, record.offset())"),
                "the keyed upsert is the branch's whole work, and the Kafka key is never its identity");
        assertFalse(body.contains("broadcast("), "poll-only view: nothing reaches the sockets");
        assertTrue(branch < src.indexOf("updateCache(binding, record, json);", branch),
                "the branch precedes the generic cache path");
        assertTrue(src.contains("if (\"early-auction-verdict\".equals(event)) {")
                        && src.contains("CachePolicy.noEviction(settings.earlyAuctionVerdictSeekBackMs())"),
                "the seek-back window, not the infinite topic retention, bounds the restart replay");
    }

    @Test
    void controllerShapesOneWellFormedEnvelope() throws Exception {
        var s = service();
        s.onEarlyAuctionVerdictRecord(rec("PREDICTION", "2026-10-14", "p1", 1), 7);
        GatewayController controller = new GatewayController(s,
                (app.feedgateway.liquidityhistory.LiquidityHistoryStore) null,
                (java.util.function.Function<String, app.feedgateway.liquidityhistory.LiquidityHistoryAuth.Result>) null);
        String json = controller.earlyAuctionEvents("", -1, 500);
        com.fasterxml.jackson.databind.JsonNode n = new ObjectMapper()
                .readerFor(com.fasterxml.jackson.databind.JsonNode.class)
                .with(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .readValue(json);
        assertFalse(n.path("enabled").asBoolean(true));
        assertEquals("2026-10-14", n.path("tradeDate").asText());
        assertEquals(1, n.path("events").size());
        assertEquals(7, n.path("events").get(0).path("offset").asLong());
        assertEquals("PREDICTION", n.path("events").get(0).path("record").path("kind").asText(),
                "the record rides VERBATIM inside the envelope");
        String refused = controller.earlyAuctionEvents("14-10-2026", -1, 500);
        assertTrue(refused.contains("\"error\""), "an echoed tradeDate is validated, never escaped");
        new ObjectMapper().readTree(refused);
    }

    @Test
    void metricsCarryTheStreamNames() {
        String m = service().metrics();
        assertTrue(m.contains("gateway_early_auction_verdict_enabled 0"));
        assertTrue(m.contains("gateway_early_auction_verdict_records_total 0"));
        assertTrue(m.contains("gateway_early_auction_verdict_drops_total 0"));
        assertTrue(m.contains("gateway_early_auction_verdict_refused_at_cap_total 0"));
        assertTrue(m.contains("gateway_early_auction_verdict_cached 0"));
        assertTrue(m.contains("gateway_early_auction_verdict_requests_total 0"));
    }
}

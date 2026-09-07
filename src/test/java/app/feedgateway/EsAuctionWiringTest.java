package app.feedgateway;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

/**
 * The SPX Auction Desk stream ({@code es-auction}), modelled on es-cvd: opt-in flag + topic knob,
 * binding registered in BOTH consumers only when enabled, verbatim standalone pass-through, the
 * keyed minute view behind {@code /api/auction/minutes} (ordering, cursor, correction and
 * trade-date retention rules) and the connect hello.
 */
class EsAuctionWiringTest {

    private static final String SRC = "src/main/java/app/feedgateway/FeedGatewayService.java";

    @AfterEach
    void clearFlags() {
        System.clearProperty("GATEWAY_ES_AUCTION_ENABLED");
        System.clearProperty("KAFKA_ES_AUCTION_TOPIC");
    }

    private static FeedGatewayService service() {
        return new FeedGatewayService(new GatewaySettings(), new ObjectMapper(), new HpsfGatewayViewMapper(), null);
    }

    private static String minute(String tradeDate, String hhmm, int rev, String marker) {
        return "{\"sessionId\":\"" + tradeDate + "-RTH\",\"tradeDate\":\"" + tradeDate + "\",\"minute\":\"" + hhmm
                + "\",\"effectiveTs\":\"" + tradeDate + "T" + hhmm + ":00Z\",\"publishTs\":\"" + tradeDate + "T" + hhmm
                + ":02Z\",\"correctionRev\":" + rev + ",\"call\":{\"callId\":\"" + marker + "\",\"side\":\"SELL_CALLS\"},"
                + "\"traderLine\":\"" + marker + "\",\"events\":[]}";
    }

    private static String key(String tradeDate, String hhmm) {
        return tradeDate + "|" + hhmm;
    }

    private static WebSocketSession socket(String id, List<String> sink) throws Exception {
        WebSocketSession ws = mock(WebSocketSession.class);
        when(ws.getId()).thenReturn(id);
        when(ws.isOpen()).thenReturn(true);
        doAnswer(inv -> {
            sink.add(((TextMessage) inv.getArgument(0)).getPayload());
            return null;
        }).when(ws).sendMessage(any());
        return ws;
    }

    // ---- settings ----

    @Test void isOptInAndUsesTheEsAuctionTopicByDefault() {
        GatewaySettings off = new GatewaySettings();
        assertFalse(off.esAuctionEnabled(), "opt-in: the ES-only topic does not exist on every cluster");
        assertEquals("es.futures.auction", off.esAuctionTopic());
    }

    @Test void theFlagAndTopicKnobAreHonoured() {
        System.setProperty("GATEWAY_ES_AUCTION_ENABLED", "true");
        System.setProperty("KAFKA_ES_AUCTION_TOPIC", "es.futures.auction.v2");
        GatewaySettings on = new GatewaySettings();
        assertTrue(on.esAuctionEnabled());
        assertEquals("es.futures.auction.v2", on.esAuctionTopic());
    }

    // ---- wiring ----

    @SuppressWarnings("unchecked")
    private static Map<String, Object> bindings(FeedGatewayService s) throws Exception {
        Method m = FeedGatewayService.class.getDeclaredMethod("addEsAuctionTopics", Map.class);
        m.setAccessible(true);
        Map<String, Object> topicEvents = new LinkedHashMap<>();
        m.invoke(s, topicEvents);
        return topicEvents;
    }

    @Test void theBindingIsRegisteredOnlyWhenEnabled() throws Exception {
        assertTrue(bindings(service()).isEmpty(), "flag off: no subscription, no topic-missing churn");
        System.setProperty("GATEWAY_ES_AUCTION_ENABLED", "true");
        Map<String, Object> on = bindings(service());
        assertEquals(1, on.size());
        String binding = String.valueOf(on.get("es.futures.auction"));
        assertTrue(binding.contains("DATABENTO") && binding.contains("es-auction"),
                "TopicBinding(DATABENTO, es-auction) on the configured topic, got " + binding);
    }

    @Test void bootstrapAndLiveConsumersShareOneAuctionTopicWiringPath() throws Exception {
        String source = Files.readString(Path.of(SRC));
        assertEquals(2, occurrences(source, "addEsAuctionTopics(topicEvents);"),
                "cache + live consumers must stay symmetric — the es-cvd rule");
        assertEquals(1, occurrences(source,
                "topicEvents.put(settings.esAuctionTopic(), new TopicBinding(\"DATABENTO\", \"es-auction\"));"));
        int helper = source.indexOf("private void addEsAuctionTopics(");
        int helperEnd = source.indexOf("\n    }", helper);
        assertTrue(helper >= 0 && helperEnd > helper);
        assertTrue(source.substring(helper, helperEnd).contains("if (!settings.esAuctionEnabled()) return;"),
                "the helper is the single place the flag gates the subscription");
        // Live builder must reach the helper (the es-cvd defect class: right branch, wrong loop).
        int liveBuilder = source.indexOf("private void runJsonStateLiveConsumer()");
        int liveRun = source.indexOf("runLiveConsumer(\"state-live\"", liveBuilder);
        assertTrue(source.substring(liveBuilder, liveRun).contains("addEsAuctionTopics("));
        int cacheBuilder = source.indexOf("private void runJsonStateCacheConsumer()");
        int cacheRun = source.indexOf("runAssignedCacheConsumer(\"state\"", cacheBuilder);
        assertTrue(source.substring(cacheBuilder, cacheRun).contains("addEsAuctionTopics("));
    }

    @Test void deliveryIsRawPassThroughStandaloneAndAllowlisted() throws Exception {
        Method raw = FeedGatewayService.class.getDeclaredMethod("isRawPassThroughEvent", String.class);
        raw.setAccessible(true);
        assertTrue((boolean) raw.invoke(null, "es-auction"), "enrichJson must never restamp the producer's fields");
        assertTrue(FeedGatewayService.isGlobalBroadcastEvent("es-auction"),
                "without the allowlist entry per-session (auth) mode drops every frame as non-routable");
        String source = Files.readString(Path.of(SRC));
        int direct = source.indexOf("if (\"es-auction\".equals(binding.event()))");
        int generic = source.indexOf("String cacheKey = updateCache(binding, record, json);", direct);
        assertTrue(direct >= 0 && generic > direct, "the branch precedes the generic cache-key gate");
        assertTrue(source.substring(direct, generic).contains("onEsAuctionRecord(record.key()"),
                "the handler is fed the KAFKA KEY (tradeDate|HH:mm) — the record's identity");
        // Not coalescable: every minute (and every correction) is a distinct record that must be delivered.
        int setStart = source.indexOf("COALESCABLE_EVENTS = Set.of(");
        int setEnd = source.indexOf(");", setStart);
        assertFalse(source.substring(setStart, setEnd).contains("\"es-auction\""));
    }

    // ---- forwarding ----

    @Test void everyRecordIsBroadcastVerbatimUnderTheEsAuctionType() throws Exception {
        var s = service();
        s.runOutboundWritesInline();
        List<String> sink = new ArrayList<>();
        s.addClient(socket("s1", sink));
        sink.clear();
        String payload = minute("2026-09-08", "09:31", 0, "first");
        s.onEsAuctionRecord(key("2026-09-08", "09:31"), payload);
        assertEquals(List.of("{\"type\":\"es-auction\",\"data\":" + payload + "}"), sink);
        // A newer correction replaces the view entry and reaches the wire; a superseded one, a
        // re-emission of an identical record (a consumer retry's replay) and a dead-session record
        // never do — the wire carries exactly what the view accepted (code review round 1, item 2).
        s.onEsAuctionRecord(key("2026-09-08", "09:31"), minute("2026-09-08", "09:31", 2, "corr"));
        s.onEsAuctionRecord(key("2026-09-08", "09:31"), minute("2026-09-08", "09:31", 1, "late"));
        s.onEsAuctionRecord(key("2026-09-08", "09:31"), minute("2026-09-08", "09:31", 2, "corr"));
        assertEquals(2, sink.size(), "first + newer correction only");
        assertEquals(4L, s.esAuctionRecordsForTest(), "every record is counted as received");
        var page = s.auctionMinutesPage(null, null, 10);
        assertEquals(1, page.minutes().size());
        assertTrue(page.minutes().get(0).contains("\"callId\":\"corr\""), "latest correctionRev wins");
    }

    // ---- restart hydration (cache consumer) ----

    @Test void cacheConsumerHydratesTheViewWithoutBroadcasting() throws Exception {
        var s = service();
        s.runOutboundWritesInline();
        List<String> sink = new ArrayList<>();
        s.addClient(socket("s1", sink));
        sink.clear();
        s.onEsAuctionCacheRecord(key("2026-09-08", "09:30"), minute("2026-09-08", "09:30", 0, "hydrated"));
        s.onEsAuctionCacheRecord(key("2026-09-08", "09:31"), minute("2026-09-08", "09:31", 0, "hydrated2"));
        assertTrue(sink.isEmpty(), "hydration must never reach the wire, got " + sink);
        assertEquals(0L, s.esAuctionRecordsForTest(), "records_total counts what was FORWARDED, not hydrated");
        var page = s.auctionMinutesPage(null, null, 10);
        assertEquals("2026-09-08", page.tradeDate());
        assertEquals(2, page.minutes().size());
        assertTrue(page.minutes().get(0).contains("\"callId\":\"hydrated\""));
        assertEquals("{\"tradeDate\":\"2026-09-08\",\"minutes\":2,\"lastMinute\":\"09:31\"}", s.esAuctionHelloJson(),
                "the connect hello sees the hydrated view");

        // The CACHE consumer loop routes es-auction to the hydration handler BEFORE the generic
        // updateCache fall-through (whose default has no shape for it), and that branch never broadcasts.
        String source = Files.readString(Path.of(SRC));
        int cacheLoop = source.indexOf("private void runAssignedCacheConsumerOnce(");
        int cacheLoopEnd = source.indexOf("clearReachedBootstrapBarriers(bootstrappingPartitions, partitions, consumer::position);", cacheLoop);
        assertTrue(cacheLoop >= 0 && cacheLoopEnd > cacheLoop);
        String loop = source.substring(cacheLoop, cacheLoopEnd);
        int branch = loop.indexOf("if (binding != null && \"es-auction\".equals(binding.event()))");
        int fallThrough = loop.indexOf("updateCache(binding, record, json);\n                }", branch);
        assertTrue(branch >= 0 && fallThrough > branch, "the hydration branch precedes the generic updateCache fall-through");
        String body = loop.substring(branch, fallThrough);
        assertTrue(body.contains("onEsAuctionCacheRecord(record.key()"), "fed the KAFKA KEY, like the live branch");
        assertFalse(body.contains("broadcast("), "upsert-only: the live consumer owns the wire");
        assertFalse(body.contains("onEsAuctionRecord("), "the cache loop must not use the broadcasting handler");
        // The hydration handler itself is upsert-only.
        int handler = source.indexOf("void onEsAuctionCacheRecord(String key, String json)");
        int handlerEnd = source.indexOf("\n    }", handler);
        String handlerBody = source.substring(handler, handlerEnd);
        assertTrue(handlerBody.contains("upsertEsAuctionMinuteOutcome(key, json, false)"), "the cache path applies the record with live=false, so it can never emit or mark a record as emitted");
        assertFalse(handlerBody.contains("broadcast(") || handlerBody.contains("incrementAndGet()"));
    }

    @Test void livePathStillUpsertsAndBroadcasts() throws Exception {
        var s = service();
        s.runOutboundWritesInline();
        List<String> sink = new ArrayList<>();
        s.addClient(socket("s1", sink));
        sink.clear();
        String payload = minute("2026-09-08", "09:30", 0, "live");
        s.onEsAuctionRecord(key("2026-09-08", "09:30"), payload);
        assertEquals(List.of("{\"type\":\"es-auction\",\"data\":" + payload + "}"), sink);
        assertEquals(1L, s.esAuctionRecordsForTest());
        assertEquals(1, s.auctionMinutesPage(null, null, 10).minutes().size());
        // And the LIVE consumer loop is untouched: es-auction still routes to the broadcasting handler.
        String source = Files.readString(Path.of(SRC));
        int liveLoop = source.indexOf("private void runLiveConsumerOnce(");
        int liveBranch = source.indexOf("if (\"es-auction\".equals(binding.event()))", liveLoop);
        int liveNext = source.indexOf("continue;", liveBranch);
        assertTrue(liveLoop >= 0 && liveBranch > liveLoop && liveNext > liveBranch);
        String liveBody = source.substring(liveBranch, liveNext);
        assertTrue(liveBody.contains("onEsAuctionRecord(record.key()"));
        assertFalse(liveBody.contains("onEsAuctionCacheRecord("));
    }

    @Test void hydrationThenLiveUpdateForTheSameMinuteKeepsTheHigherCorrectionRev() throws Exception {
        var s = service();
        s.runOutboundWritesInline();
        List<String> sink = new ArrayList<>();
        s.addClient(socket("s1", sink));
        sink.clear();
        // Hydration rebuilt rev 2; a live re-emission of the superseded rev 1 must not regress the view,
        // and must not reach the wire either — the pages already hold rev 2 (code review round 1, item 2).
        s.onEsAuctionCacheRecord(key("2026-09-08", "09:30"), minute("2026-09-08", "09:30", 2, "hydrated-r2"));
        s.onEsAuctionRecord(key("2026-09-08", "09:30"), minute("2026-09-08", "09:30", 1, "live-r1"));
        assertEquals(0, sink.size(), "a superseded correction is dropped from the view AND from the wire");
        assertTrue(s.auctionMinutesPage(null, null, 10).minutes().get(0).contains("\"callId\":\"hydrated-r2\""));
        // A genuinely newer live correction replaces it.
        s.onEsAuctionRecord(key("2026-09-08", "09:30"), minute("2026-09-08", "09:30", 3, "live-r3"));
        assertTrue(s.auctionMinutesPage(null, null, 10).minutes().get(0).contains("\"callId\":\"live-r3\""));
        // Reverse order: live first (rev 0), then a hydration replay carrying the later rev 1 wins;
        // a hydration replay of an OLDER rev never regresses a live-applied minute.
        s.onEsAuctionRecord(key("2026-09-08", "09:31"), minute("2026-09-08", "09:31", 0, "live-r0"));
        s.onEsAuctionCacheRecord(key("2026-09-08", "09:31"), minute("2026-09-08", "09:31", 1, "hydrated-r1"));
        s.onEsAuctionCacheRecord(key("2026-09-08", "09:31"), minute("2026-09-08", "09:31", 0, "hydrated-r0-late"));
        var page = s.auctionMinutesPage(null, null, 10);
        assertEquals(2, page.minutes().size(), "one row per minute, never a duplicate");
        assertTrue(page.minutes().get(1).contains("\"callId\":\"hydrated-r1\""));
        assertEquals(2, sink.size(), "only the live records the VIEW ACCEPTED reached the wire (rev 3 and the new 09:31); the superseded rev 1 did not");
    }

    @Test void restartSeekBackCoversTheRetainedTradeDatesNotTheGenericWindow() throws Exception {
        GatewaySettings settings = new GatewaySettings();
        assertEquals(604_800_000L, settings.esAuctionSeekBackMs(), "default = the topic's 7-day retention");
        System.setProperty("GATEWAY_ES_AUCTION_ENABLED", "true");
        var s = service();
        Map<String, Object> topicEvents = bindings(s);
        Method window = FeedGatewayService.class.getDeclaredMethod("windowTtlMsFor", TopicPartition.class, Map.class, long.class);
        window.setAccessible(true);
        long seekBack = (long) window.invoke(s, new TopicPartition("es.futures.auction", 0), topicEvents, System.currentTimeMillis());
        assertEquals(settings.esAuctionSeekBackMs(), seekBack,
                "the cache consumer must replay both retained trade dates after a restart");
        assertTrue(seekBack > settings.cacheTtlMs(), "the generic 15-min window rebuilt only the last few minutes");
    }

    // ---- keyed view + backfill ----

    @Test void backfillPagesAreInMinuteOrderWithAnInclusiveFromAndAnExactCursor() {
        var s = service();
        for (String m : List.of("10:02", "09:30", "09:45", "09:31", "10:01")) {
            s.upsertEsAuctionMinute(key("2026-09-08", m), minute("2026-09-08", m, 0, m));
        }
        var first = s.auctionMinutesPage("2026-09-08", "", 2);
        assertEquals("2026-09-08", first.tradeDate());
        assertEquals(2, first.minutes().size());
        assertTrue(first.minutes().get(0).contains("\"minute\":\"09:30\""));
        assertTrue(first.minutes().get(1).contains("\"minute\":\"09:31\""));
        assertEquals("09:45", first.nextCursor(), "the cursor is the first minute NOT returned");
        var second = s.auctionMinutesPage("2026-09-08", first.nextCursor(), 2);
        assertTrue(second.minutes().get(0).contains("\"minute\":\"09:45\""), "from is inclusive");
        assertTrue(second.minutes().get(1).contains("\"minute\":\"10:01\""));
        assertEquals("10:02", second.nextCursor());
        var third = s.auctionMinutesPage("2026-09-08", second.nextCursor(), 2);
        assertEquals(1, third.minutes().size());
        assertNull(third.nextCursor(), "a page that reaches the end carries no cursor");
        var exact = s.auctionMinutesPage("2026-09-08", null, 5);
        assertEquals(5, exact.minutes().size());
        assertNull(exact.nextCursor(), "a full page with nothing after it is still the end");
        assertEquals(4L, s.esAuctionBackfillRequestsForTest());
    }

    @Test void defaultTradeDateIsTheCurrentOneAndAnUnknownDateAnswersEmpty() {
        var s = service();
        var empty = s.auctionMinutesPage(null, null, 10);
        assertNull(empty.tradeDate(), "nothing held yet: no trade date to label the page with");
        assertTrue(empty.minutes().isEmpty());
        assertNull(empty.nextCursor());
        s.upsertEsAuctionMinute(key("2026-09-08", "09:30"), minute("2026-09-08", "09:30", 0, "a"));
        assertEquals("2026-09-08", s.auctionMinutesPage("", "", 10).tradeDate());
        var unknown = s.auctionMinutesPage("2026-01-02", "", 10);
        assertEquals("2026-01-02", unknown.tradeDate());
        assertTrue(unknown.minutes().isEmpty());
    }

    @Test void retentionIsCurrentPlusPreviousTradeDateAndRolloverIsMonotonic() {
        var s = service();
        s.upsertEsAuctionMinute(key("2026-09-04", "09:30"), minute("2026-09-04", "09:30", 0, "thu"));
        s.upsertEsAuctionMinute(key("2026-09-05", "09:30"), minute("2026-09-05", "09:30", 0, "fri"));
        s.upsertEsAuctionMinute(key("2026-09-08", "09:30"), minute("2026-09-08", "09:30", 0, "mon"));
        assertEquals("2026-09-08", s.esAuctionTradeDate());
        assertEquals(2, s.esAuctionMinutesCached(), "Thursday is gone, Friday + Monday remain");
        assertEquals(1, s.auctionMinutesPage("2026-09-05", "", 10).minutes().size(), "previous date still served");
        assertTrue(s.auctionMinutesPage("2026-09-04", "", 10).minutes().isEmpty());
        // A late correction for the PREVIOUS date still lands; a dead-session record never does.
        assertTrue(s.upsertEsAuctionMinute(key("2026-09-05", "09:31"), minute("2026-09-05", "09:31", 0, "fri-late")));
        assertFalse(s.upsertEsAuctionMinute(key("2026-09-04", "09:31"), minute("2026-09-04", "09:31", 0, "thu-late")));
        assertEquals("2026-09-08", s.esAuctionTradeDate(), "rollover is monotonic");
        assertEquals(3, s.esAuctionMinutesCached());
    }

    @Test void retentionIsOrderIndependentAcrossHydrationAndLive() {
        // A restart's replay is not sorted across partitions and can race live ingestion: the view
        // must keep the two GREATEST dates whatever the arrival order (code review round 1, item 1).
        var s = service();
        s.onEsAuctionCacheRecord(key("2026-09-08", "09:30"), minute("2026-09-08", "09:30", 0, "mon"));
        s.onEsAuctionCacheRecord(key("2026-09-04", "09:30"), minute("2026-09-04", "09:30", 0, "thu"));
        s.onEsAuctionCacheRecord(key("2026-09-05", "09:30"), minute("2026-09-05", "09:30", 0, "fri"));
        s.onEsAuctionCacheRecord(key("2026-09-05", "09:31"), minute("2026-09-05", "09:31", 0, "fri2"));
        assertEquals("2026-09-08", s.esAuctionTradeDate());
        assertEquals(2, s.auctionMinutesPage("2026-09-05", "", 10).minutes().size(), "Friday retained although it arrived after Monday");
        assertTrue(s.auctionMinutesPage("2026-09-04", "", 10).minutes().isEmpty(), "Thursday evicted once two greater dates exist");
        assertEquals(3, s.esAuctionMinutesCached());
        assertEquals(FeedGatewayService.EsAuctionUpsert.DROPPED, s.upsertEsAuctionMinuteOutcome(key("2026-09-03", "09:30"), minute("2026-09-03", "09:30", 0, "wed")));
    }

    @Test void aConsumerRetryReplayNeverRebroadcastsWhatThePagesAlreadyHold() throws Exception {
        var s = service();
        s.runOutboundWritesInline();
        List<String> sink = new ArrayList<>();
        s.addClient(socket("s1", sink));
        sink.clear();
        for (String m : List.of("09:30", "09:31", "09:32")) s.onEsAuctionRecord(key("2026-09-08", m), minute("2026-09-08", m, 0, m));
        assertEquals(3, sink.size());
        for (String m : List.of("09:30", "09:31", "09:32")) s.onEsAuctionRecord(key("2026-09-08", m), minute("2026-09-08", m, 0, m));   // the retry's replay
        s.onEsAuctionRecord(key("2026-09-05", "09:30"), minute("2026-09-05", "09:30", 0, "fri"));      // a previous date first seen now: NEW, broadcast
        s.onEsAuctionRecord(key("2026-09-04", "09:30"), minute("2026-09-04", "09:30", 0, "thu"));      // dead session: dropped, not broadcast
        assertEquals(4, sink.size(), "replayed duplicates and dead-session records stay off the wire");
        assertEquals(4, s.esAuctionMinutesCached());
    }

    @Test void aMinuteTheCacheConsumerSeesFirstIsStillBroadcastToThePages() throws Exception {
        // Code review round 2: the cache consumer keeps polling after hydration and races the live path
        // on every new offset. Gating the broadcast on the VIEW outcome dropped such a minute from every
        // connected page, because the live copy looked like a duplicate. The emitted ledger is the gate.
        var s = service();
        s.runOutboundWritesInline();
        List<String> sink = new ArrayList<>();
        s.addClient(socket("s1", sink));
        sink.clear();
        String payload = minute("2026-09-08", "09:30", 0, "raced");
        s.onEsAuctionCacheRecord(key("2026-09-08", "09:30"), payload);   // the cache consumer got there first
        assertEquals(0, sink.size(), "hydration never broadcasts");
        s.onEsAuctionRecord(key("2026-09-08", "09:30"), payload);        // the live consumer sees the same record
        assertEquals(List.of("{\"type\":\"es-auction\",\"data\":" + payload + "}"), sink, "the pages still receive it");
        s.onEsAuctionRecord(key("2026-09-08", "09:30"), payload);        // a retry replay of the very same record
        assertEquals(1, sink.size(), "and only once");
        // A newer correction the cache consumer also saw first still reaches the wire.
        String corrected = minute("2026-09-08", "09:30", 1, "raced-r1");
        s.onEsAuctionCacheRecord(key("2026-09-08", "09:30"), corrected);
        s.onEsAuctionRecord(key("2026-09-08", "09:30"), corrected);
        assertEquals(2, sink.size());
        assertTrue(sink.get(1).contains("raced-r1"));
    }

    @Test void theBackfillEndpointRefusesParametersItWouldEchoUnescaped() {
        // Code review round 3: the response echoes tradeDate and the cursor, so a value carrying a quote
        // or a backslash would emit malformed application/json. Only the contract's own shapes are accepted.
        var s = service();
        s.upsertEsAuctionMinute(key("2026-09-08", "09:30"), minute("2026-09-08", "09:30", 0, "a"));
        GatewayController c = new GatewayController(s, new org.springframework.beans.factory.ObjectProvider<>() {
            @Override public app.feedgateway.liquidityhistory.LiquidityHistoryStore getObject() { return null; }
            @Override public app.feedgateway.liquidityhistory.LiquidityHistoryStore getObject(Object... args) { return null; }
            @Override public app.feedgateway.liquidityhistory.LiquidityHistoryStore getIfAvailable() { return null; }
            @Override public app.feedgateway.liquidityhistory.LiquidityHistoryStore getIfUnique() { return null; }
        });
        String bad = c.auctionMinutes("2026-09-08\" ,\"x\":\"", "", 10);
        assertTrue(bad.contains("\"error\""), "a quote in tradeDate is refused: " + bad);
        assertFalse(bad.contains("x\":\""), "and never reaches the body: " + bad);
        assertTrue(c.auctionMinutes("", "10:31\\", 10).contains("\"error\""), "a backslash in from is refused too");
        String ok = c.auctionMinutes("2026-09-08", "09:30", 10);
        assertFalse(ok.contains("\"error\""), "the contract's own shapes still work: " + ok);
        assertTrue(ok.startsWith("{\"tradeDate\":\"2026-09-08\""), ok);
    }

    @Test void theViewStoresMaterializedMinutesNotCorrectionEnvelopes() {
        // Code review round 3: /api/auction/minutes promises MINUTES. A page loading cold cannot rebuild
        // one from a patch whose base it never receives, so the view applies the envelope to the minute
        // it corrects and serves the result; the wire still carries the envelope verbatim.
        var s = service();
        String base = "{\"tradeDate\":\"2026-09-08\",\"minute\":\"09:30\",\"correctionRev\":0,\"callId\":\"c0\","
                + "\"call\":{\"instruction\":\"HOLD\"},\"profile\":{\"rows\":[1,2,3],\"vva\":{\"poc\":10}},"
                + "\"references\":[{\"refId\":\"r1\",\"row\":5},{\"refId\":\"r2\",\"row\":6}],\"publishTs\":\"T0\"}";
        assertTrue(s.upsertEsAuctionMinute(key("2026-09-08", "09:30"), base));
        String env = "{\"tradeDate\":\"2026-09-08\",\"minute\":\"09:30\",\"correctionRev\":1,\"callOmitted\":true,"
                + "\"emittedBy\":\"CORRECTION\",\"publishTs\":\"T1\",\"corrected\":["
                + "{\"op\":\"SET\",\"path\":\"/profile/vva/poc\",\"value\":99},"
                + "{\"op\":\"SET\",\"path\":\"/references/r1/row\",\"value\":42},"
                + "{\"op\":\"DELETE\",\"path\":\"/references/r2\"}]}";
        assertTrue(s.upsertEsAuctionMinute(key("2026-09-08", "09:30"), env));
        String stored = s.auctionMinutesPage("2026-09-08", "", 10).minutes().get(0);
        assertTrue(stored.contains("\"poc\":99"), "a nested SET applied: " + stored);
        assertTrue(stored.contains("\"refId\":\"r1\",\"row\":42"), "an identity-addressed SET applied: " + stored);
        assertFalse(stored.contains("\"r2\""), "the identity-addressed DELETE applied: " + stored);
        assertTrue(stored.contains("\"rows\":[1,2,3]"), "everything unchanged survived: " + stored);
        assertTrue(stored.contains("\"instruction\":\"HOLD\""), "the call stands as published");
        assertTrue(stored.contains("\"correctionRev\":1") && stored.contains("\"publishTs\":\"T1\""), "the envelope's header replaced the base's");
        assertFalse(stored.contains("callOmitted"), "the materialized record is a MINUTE, not a patch");
        // An envelope with no minute to correct is counted, never stored as if it were one.
        assertFalse(s.upsertEsAuctionMinute(key("2026-09-08", "09:31"), env.replace("09:30", "09:31")));
        assertTrue(s.auctionMinutesPage("2026-09-08", "", 10).minutes().size() == 1, "no orphan patch entered the view");
    }

    @Test void aSocketThatConnectsExactlyAsHydrationCompletesStillGetsItsHello() throws Exception {
        // Code review round 4: hydration can complete between the readiness check and the insertion,
        // flushing an empty set; the socket must not stay pending forever. Whichever side removes it
        // owns the send, so it is delivered exactly once.
        System.setProperty("GATEWAY_ES_AUCTION_ENABLED", "true");
        var s = service();
        s.runOutboundWritesInline();
        s.upsertEsAuctionMinute(key("2026-09-08", "09:30"), minute("2026-09-08", "09:30", 0, "a"));
        s.markStateCaughtUpForTest();                       // hydration finished BEFORE this socket arrives
        List<String> sink = new ArrayList<>();
        s.addClient(socket("late", sink));
        assertEquals(1, sink.stream().filter(m -> m.contains("\"type\":\"es-auction-hello\"")).count(), "exactly one hello");
        assertEquals(0, s.esAuctionHelloPendingForTest(), "nothing left pending");
    }

    @Test void theAuctionIsSourceIndependentForCatchUpAndItsHistoryIsNeverReplayedLive() throws Exception {
        // Code review round 5, item 1: es-auction is ES-global. Left out of the source-independent barrier
        // lists, an IBKR selection would let catch-up be declared before the seven-day auction replay ended.
        String source = java.nio.file.Files.readString(java.nio.file.Path.of("src/main/java/app/feedgateway/FeedGatewayService.java"));
        int catchUp = source.indexOf("private Map<TopicPartition, Long> catchUpEndOffsets(");
        int barriers = source.indexOf("private Map<TopicPartition, Long> selectedSourceBarriers(");
        assertTrue(catchUp > 0 && barriers > 0);
        assertTrue(source.substring(catchUp, catchUp + 1600).contains("\"es-auction\".equals(binding.event())"), "catchUpEndOffsets gates on es-auction");
        assertTrue(source.substring(barriers, barriers + 1600).contains("\"es-auction\".equals(preOpenBinding.event())"), "selectedSourceBarriers gates on es-auction too");
        // Item 2: a live retry seeks the cache window for every topic, which for es-auction is SEVEN DAYS.
        // Replayed on the live path those minutes would all pass the emitted ledger and be broadcast.
        int live = source.indexOf("private void runLiveConsumerOnce(");
        String body = source.substring(live, live + 1400);
        assertTrue(body.contains("resumeEsAuction(consumer, partitions)"), "the live retry takes the auction off the generic cache-window seek");
        int resume = source.indexOf("private void resumeEsAuction(");
        assertTrue(resume > 0 && source.substring(resume, resume + 500).contains("seekEsAuctionWithin(consumer, tp)"), "resuming per partition (round 6)");
    }

    @Test void aLiveRetryResumesTheAuctionCursorAndADiscoveredPartitionIsRecovered() throws Exception {
        // Code review round 6: seeking to END on every retry drops whatever was produced during the gap —
        // the cache consumer hydrates it silently and the pages, already helloed, never backfill again.
        String source = java.nio.file.Files.readString(java.nio.file.Path.of("src/main/java/app/feedgateway/FeedGatewayService.java"));
        int note = source.indexOf("private void noteCvdSpxLevelsProgress(");
        assertTrue(source.substring(note, note + 700).contains("esAuctionNextOffset.put("), "the live path records a per-partition cursor");
        int resume = source.indexOf("private void resumeEsAuction(");
        String body = source.substring(resume, resume + 500);
        assertTrue(body.contains("seekEsAuctionWithin(consumer, tp)"), "the retry resumes per partition");
        assertFalse(body.contains("consumer.seekToEnd(owned)"), "and no longer seeks the whole topic to END");
        int within = source.indexOf("private void seekEsAuctionWithin(");
        String w = source.substring(within, within + 900);
        assertTrue(w.contains("consumer.seek(owned, cursor)") && w.contains("cursor < beginning || cursor > end"), "END only without a usable cursor");
        // A partition discovered after startup recovers rather than jumping to END.
        int refresh = source.indexOf("List<TopicPartition> recoverEsAuction = new ArrayList<>();");
        assertTrue(refresh > 0, "the discovery path has an auction bucket");
        assertTrue(source.contains("recoverEsAuction.add(partition)") && source.contains("consumer.seekToBeginning(List.of(p))"));
    }

    @Test void foreignShapesNeverPoisonTheView() {
        var s = service();
        assertFalse(s.upsertEsAuctionMinute(null, "{\"unrelated\":true}"));
        assertFalse(s.upsertEsAuctionMinute("2026-09-08|09:30", "not json at all"));
        assertFalse(s.upsertEsAuctionMinute(null, "[1,2,3]"));
        assertEquals(0, s.esAuctionMinutesCached());
        // No Kafka key: the payload's own tradeDate/minute identify the record.
        assertTrue(s.upsertEsAuctionMinute(null, minute("2026-09-08", "09:30", 0, "keyless")));
        assertEquals(1, s.auctionMinutesPage(null, null, 10).minutes().size());
    }

    // ---- hello ----

    @Test void helloReportsWhatTheGatewayHoldsForTheCurrentTradeDate() {
        var s = service();
        assertEquals("{\"tradeDate\":null,\"minutes\":0,\"lastMinute\":null}", s.esAuctionHelloJson());
        s.upsertEsAuctionMinute(key("2026-09-05", "15:59"), minute("2026-09-05", "15:59", 0, "fri"));
        s.upsertEsAuctionMinute(key("2026-09-08", "09:31"), minute("2026-09-08", "09:31", 0, "b"));
        s.upsertEsAuctionMinute(key("2026-09-08", "09:30"), minute("2026-09-08", "09:30", 0, "a"));
        assertEquals("{\"tradeDate\":\"2026-09-08\",\"minutes\":2,\"lastMinute\":\"09:31\"}", s.esAuctionHelloJson(),
                "the previous date's minutes are held for backfill but never counted in the hello");
    }

    @Test void helloIsSentOnConnectOnlyWhenEnabled() throws Exception {
        var off = service();
        off.runOutboundWritesInline();
        List<String> offSink = new ArrayList<>();
        off.addClient(socket("off", offSink));
        assertFalse(offSink.stream().anyMatch(m -> m.contains("\"type\":\"es-auction-hello\"")));

        System.setProperty("GATEWAY_ES_AUCTION_ENABLED", "true");
        var on = service();
        on.runOutboundWritesInline();
        on.upsertEsAuctionMinute(key("2026-09-08", "09:30"), minute("2026-09-08", "09:30", 0, "a"));
        List<String> onSink = new ArrayList<>();
        on.addClient(socket("on", onSink));
        // The hello waits for hydration (code review round 3): a page must never bound its backfill by a
        // PARTIAL view. Until then the socket is HELD, and it is flushed the moment hydration completes.
        assertFalse(on.esAuctionHelloReady(), "the state cache consumer has not caught up in this harness");
        assertFalse(onSink.stream().anyMatch(m -> m.contains("\"type\":\"es-auction-hello\"")), "held, not sent");
        assertEquals(1, on.esAuctionHelloPendingForTest());
        on.markStateCaughtUpForTest();
        assertEquals(0, on.esAuctionHelloPendingForTest(), "the held socket was flushed");
        assertTrue(onSink.contains("{\"type\":\"es-auction-hello\",\"data\":"
                        + "{\"tradeDate\":\"2026-09-08\",\"minutes\":1,\"lastMinute\":\"09:30\"}}"),
                "got " + onSink);
        // Sent from the same connect path as cvd-hello, for both routing modes.
        String source = Files.readString(Path.of(SRC));
        int addClient = source.indexOf("public void addClient(WebSocketSession session)");
        int replayEnd = source.indexOf("if (perSessionRouting())", addClient);
        String head = source.substring(addClient, replayEnd);
        assertTrue(head.contains("esAuctionEnabled()") && head.contains("send(session, \"es-auction-hello\""));
    }

    // ---- metrics ----

    @Test void metricsFollowTheGatewayNaming() {
        var s = service();
        s.onEsAuctionRecord(key("2026-09-08", "09:30"), minute("2026-09-08", "09:30", 0, "a"));
        s.auctionMinutesPage(null, null, 10);
        String metrics = s.metrics();
        assertTrue(metrics.contains("# TYPE gateway_es_auction_records_total counter\ngateway_es_auction_records_total 1\n"));
        assertTrue(metrics.contains("# TYPE gateway_es_auction_minutes_cached gauge\ngateway_es_auction_minutes_cached 1\n"));
        assertTrue(metrics.contains("# TYPE gateway_es_auction_backfill_requests_total counter\n"
                + "gateway_es_auction_backfill_requests_total 1\n"));
    }

    private static int occurrences(String text, String needle) {
        int count = 0;
        for (int at = 0; (at = text.indexOf(needle, at)) >= 0; at += needle.length()) count++;
        return count;
    }
}

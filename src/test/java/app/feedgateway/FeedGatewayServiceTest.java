package app.feedgateway;

import app.feedgateway.mtsession.gateway.ReplayParams;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;

import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FeedGatewayServiceTest {
    @Test
    void epochOnlySelectionReassertDoesNotRollOrReplaceTheActiveBoard() {
        FeedGatewayService service = service();
        service.seedReadySelectionForTest("DATABENTO", "ES", "20260715", 100L);

        service.applySelectionForTest("DATABENTO", "ES", "20260715", 200L);

        assertEquals(0L, service.rolloverCountForTest(),
                "same-contract reassertion must not run the reset/rollover lifecycle");
        assertEquals(100L, service.activeSelectionEpochForTest(),
                "the established ready selection must remain authoritative");
        assertEquals(1, service.readySelectionKeyGaugeForTest(),
                "same-contract reassertion must preserve readiness");
    }

    @Test
    void realContractChangeStillRunsTheRolloverLifecycle() {
        FeedGatewayService service = service();
        service.seedReadySelectionForTest("DATABENTO", "ES", "20260715", 100L);

        service.applySelectionForTest("DATABENTO", "ES", "20260716", 200L);

        assertEquals(1L, service.rolloverCountForTest());
        assertEquals(200L, service.activeSelectionEpochForTest());
        assertEquals(0, service.readySelectionKeyGaugeForTest(),
                "a real chain change must wait for the new selection to become ready");
    }

    @Test
    void sourceSwitchReplayIncludesCachedVixPrice() {
        assertEquals(
                List.of("snapshot", "pace", "pace-rank", "directional-pressure", "vix-price", "index-price", "strike-flow", "delta-flow", "strike-intel", "strike-invasion", "liquidity-heatmap", "mission-pace", "mission-control", "spread-skew", "volume-sandwich", "mission-sandwich", "option-price-behavior", "opb-v2-by-option", "opb-v2-session", "gex-by-strike", "strike-sr", "gex-magnet", "es-gex", "max-pain"),
                FeedGatewayService.sourceSwitchReplayEvents()
        );
    }

    @Test
    void dealerLedgerFreshnessUsesPayloadEventTimeNotKafkaArrivalTime() throws Exception {
        // A producer catching up on a backlog appends records now (fresh arrival) whose asOfEventTimeMs is
        // old — freshness MUST track the payload event time, else a stale permission passes the 15s TTL.
        FeedGatewayService service = service();
        long oldEventTime = 1_700_000_000_000L;
        // 5-arg ctor sets timestamp = NO_TIMESTAMP (-1); the payload asOfEventTimeMs must still win.
        ConsumerRecord<String, String> record = new ConsumerRecord<>(
                "dealer-ledger-state", 0, 0L, "SPXW|20260704",
                "{\"symbol\":\"SPXW\",\"expiry\":\"20260704\",\"asOfEventTimeMs\":" + oldEventTime + "}");
        assertEquals(oldEventTime, eventCacheTimestamp(service, "dealer-ledger", record));
    }

    @Test
    void dealerLedgerFreshnessUsesShortLiveSignalTtlNotGenericCacheTtl() throws Exception {
        // BLOCKING guard: dealer-ledger state is a live permission heartbeat, so its freshness MUST use
        // the short dealer-ledger TTL (default 15s), never the generic 15-min cache TTL — otherwise a
        // stalled producer's last ARMED/DEFENDED would join as fresh and render as an active permission.
        FeedGatewayService service = service();
        long now = 1_000_000_000L;
        long ttl = new GatewaySettings().dealerLedgerTtlMs();
        assertTrue(ttl > 0 && ttl <= 60_000L, "dealer-ledger TTL must be a short live-signal window");
        // Just past the short TTL ⇒ EXPIRED (would still be 'fresh' under the 15-min generic window).
        assertTrue(isExpired(service, "dealer-ledger", now - ttl - 1, now));
        // Within the short TTL ⇒ fresh.
        assertFalse(isExpired(service, "dealer-ledger", now - ttl + 1_000, now));
        // Contrast: a generic event of the same 30s age is NOT expired — proves dealer-ledger is NOT
        // sharing the generic TTL.
        assertFalse(isExpired(service, "strike-flow", now - 30_000, now));
    }

    @Test
    void dealerLedgerTopicsAreOptionalSoTheirAbsenceCannotStarveTheSharedConsumer() throws Exception {
        // Kafka is wiped + services restart daily, and the dealer-ledger producer may not be deployed,
        // so both DL topics are absent at gateway startup. They MUST be optional or partitionsFor would
        // block the shared JSON consumer waiting for them, starving strike-flow / mission-pace / etc.
        FeedGatewayService service = service();
        GatewaySettings settings = new GatewaySettings();
        assertTrue(isOptionalTopic(service, settings.dealerLedgerProfileTopic()));
        assertTrue(isOptionalTopic(service, settings.dealerLedgerStateTopic()));
        // strike-invasion is a brand-new topic whose producer may not be deployed during a staged
        // rollout — it MUST be optional (like strike-intel) so its absence cannot starve the shared
        // JSON consumer (strike-flow / mission-pace / etc.).
        assertTrue(isOptionalTopic(service, settings.strikeInvasionTopic()));
        // Both spread-skew topics come from the same brand-new spread-skew-service, which may not be
        // deployed during a staged rollout — BOTH must be optional (like strike-invasion) so their
        // absence cannot starve the shared JSON consumer.
        assertTrue(isOptionalTopic(service, settings.spreadSkewTopic()));
        assertTrue(isOptionalTopic(service, settings.spreadSkewEventsTopic()));
        // A mandatory feed must still be mandatory (guards against over-broadening the optional set).
        assertFalse(isOptionalTopic(service, settings.databentoStrikeFlowTopic()));
    }

    // ----- 0DTE binary direction / unusual-movement option-chain tint -----------------------------

    @Test
    void vixOptionInteligenceTopicIsOptionalAndUsesShortControlSignalTtl() throws Exception {
        FeedGatewayService service = service();
        GatewaySettings settings = new GatewaySettings();
        assertEquals(
                "options.spx.vix-option-inteligence-service.current",
                settings.vixOptionInteligenceTopic());
        assertTrue(isOptionalTopic(service, settings.vixOptionInteligenceTopic()),
                "a staged producer rollout must not starve the shared JSON consumer");
        assertEquals(15_000L, settings.zeroDteIntelligenceTtlMs());
        long now = System.currentTimeMillis();
        assertFalse(isExpired(service, "zero-dte-intelligence", now - 14_999L, now));
        assertTrue(isExpired(service, "zero-dte-intelligence", now - 15_001L, now),
                "an old decision must return the full chain to neutral");
    }

    @Test
    void zeroDteIntelligenceUsesPayloadDecisionTimeAndSourceSymbolSessionKey() throws Exception {
        FeedGatewayService service = service();
        GatewaySettings settings = new GatewaySettings();
        long decisionTime = System.currentTimeMillis() - 1_000L;
        String payload = "{\"symbol\":\"SPX\",\"sessionDate\":\"2026-07-19\","
                + "\"asOfEventTimeMs\":" + decisionTime + ",\"marketDirection\":\"DOWN\","
                + "\"intensity\":\"UNUSUAL\",\"qualityStatus\":\"GOOD\",\"actionable\":true}";
        ConsumerRecord<String, String> record = recordAt(
                settings.vixOptionInteligenceTopic(), 0, 1L, "ignored", payload, System.currentTimeMillis());

        assertEquals(decisionTime, eventCacheTimestamp(service, "zero-dte-intelligence", record),
                "fresh Kafka arrival must not disguise a historical decision");
        assertEquals("DATABENTO|SPX|20260719",
                updateCache(service, topicBinding("DATABENTO", "zero-dte-intelligence"), record, payload));
    }

    @Test
    void zeroDteIntelligenceReplaySendsOnlyFreshStandaloneState() throws Exception {
        FeedGatewayService service = service();
        GatewaySettings settings = new GatewaySettings();
        long now = System.currentTimeMillis();
        String fresh = "{\"symbol\":\"SPX\",\"sessionDate\":\"2026-07-19\","
                + "\"asOfEventTimeMs\":" + (now - 1_000L) + ",\"marketDirection\":\"UP\","
                + "\"intensity\":\"UNUSUAL\",\"unusualTriggers\":[\"CALL_BUY_BURST\"]}";
        updateCache(service, topicBinding("DATABENTO", "zero-dte-intelligence"),
                recordAt(settings.vixOptionInteligenceTopic(), 0, 1L, "SPX|2026-07-19", fresh, now), fresh);

        List<String> sink = new ArrayList<>();
        Method replay = FeedGatewayService.class.getDeclaredMethod(
                "replayZeroDteIntelligenceCached", WebSocketSession.class);
        replay.setAccessible(true);
        replay.invoke(service, recordingSession(sink));

        assertEquals(1, sink.size());
        assertTrue(sink.get(0).contains("\"type\":\"zero-dte-intelligence\""));
        assertTrue(sink.get(0).contains("\"marketDirection\":\"UP\""));

        String stale = "{\"symbol\":\"SPX\",\"sessionDate\":\"2026-07-20\","
                + "\"asOfEventTimeMs\":" + (now - 60_000L) + ",\"marketDirection\":\"DOWN\"}";
        assertNull(updateCache(service, topicBinding("DATABENTO", "zero-dte-intelligence"),
                recordAt(settings.vixOptionInteligenceTopic(), 0, 2L, "SPX|2026-07-20", stale, now), stale),
                "historical snapshot/replay records must fail closed at ingest");
    }

    // ----- delta-flow gateway consumer (per-strike DeltaFlowStrikeSnapshot) -----------------------

    @Test
    void deltaFlowCacheKeyIsSymbolExpiryStrikeFromPayloadIdentity() throws Exception {
        // The helper derives symbol|expiry|strike from the payload (source is prepended later by
        // updateCache), mirroring gexCacheKey — delta-flow is per-strike, not chain-level.
        FeedGatewayService service = service();
        assertEquals("SPX|20260622|6005", deltaFlowCacheKey(
                service,
                "{\"symbol\":\"SPX\",\"expiry\":\"20260622\",\"strike\":6005,\"sessionNetDeltaFlow\":42}",
                "fallback-key"));
    }

    @Test
    void deltaFlowUpdateCacheStoresSourcePrefixedKey() throws Exception {
        // After updateCache the stored/returned cache key is DATABENTO|SPX|expiry|strike (source prepended).
        FeedGatewayService service = service();
        String json = "{\"marketDataSource\":\"DATABENTO\",\"symbol\":\"SPX\",\"expiry\":\"20260622\","
                + "\"strike\":6005,\"sessionNetDeltaFlow\":42,\"asOfEventTimeMs\":" + System.currentTimeMillis() + "}";
        String key = updateCache(service, topicBinding("DATABENTO", "delta-flow"),
                new ConsumerRecord<>(new GatewaySettings().databentoDeltaFlowByStrikeTopic(), 0, 1L, "SPX|20260622|6005", json),
                json);
        assertEquals("DATABENTO|SPX|20260622|6005", key,
                "updateCache must prepend the source to the delta-flow cache key");
        assertTrue(service.healthJson().contains("\"deltaFlows\":1"), "delta-flow must be cached");
    }

    // ----- es-gex (ES-on-SPX aligned whole-book, JSON, roll-forward) ------------------------------------

    private static String esGexBook(long emitEventTimeMs) {
        return "{\"recordType\":\"book\",\"source\":\"ES_ON_SPX\",\"symbol\":\"SPX\",\"expiry\":\"20260622\","
                + "\"settlementType\":\"PM\",\"basisState\":\"MEASURED\",\"basis\":40,\"basisMeasuredAtMs\":" + emitEventTimeMs
                + ",\"emitEventTimeMs\":" + emitEventTimeMs + ",\"dataEventTimeMs\":" + emitEventTimeMs
                + ",\"upstreamHeartbeatEventMs\":" + emitEventTimeMs + ",\"upstreamHealth\":\"OK\","
                + "\"buckets\":[{\"spxStrike\":5580,\"esNetGexSum\":-123456,\"contributorCount\":1,\"truncated\":false,"
                + "\"contributors\":[{\"esStrike\":5620,\"netGex\":-123456}]}]}";
    }

    @Test
    void healthReportsEsGexDisabledNotZeroWhenFeatureOff() throws Exception {
        // ES environment shape: GATEWAY_ES_GEX_ENABLED unset -> no aligned-topic consumer. A bare
        // esGex:0 there reads like a data fault (it misled a live prod triage on 2026-07-19);
        // health must say "disabled" and carry the explicit flag instead.
        System.clearProperty("GATEWAY_ES_GEX_ENABLED");
        FeedGatewayService service = service();
        String health = service.healthJson();
        assertTrue(health.contains("\"esGexEnabled\":false"), "health must carry the explicit enable flag");
        assertTrue(health.contains("\"esGex\":\"disabled\""), "disabled env must not report a misleading 0 count");
        assertFalse(health.contains("\"esGex\":0"), "no bare zero for a feature that is off");
    }

    @Test
    void healthReportsEsGexCountWhenFeatureOn() throws Exception {
        System.setProperty("GATEWAY_ES_GEX_ENABLED", "true");
        try {
            FeedGatewayService service = service();
            String health = service.healthJson();
            assertTrue(health.contains("\"esGexEnabled\":true"), "flag must reflect the enabled state");
            assertTrue(health.contains("\"esGex\":0"), "enabled env reports the real (initially 0) cache count");
        } finally {
            System.clearProperty("GATEWAY_ES_GEX_ENABLED");
        }
    }

    @Test
    void cachedReplayIncludesFreshEsGexForMatchingSpxSelectionOnly() throws Exception {
        FeedGatewayService service = service();
        GatewaySettings settings = new GatewaySettings();
        long now = System.currentTimeMillis();
        String json = esGexBook(now);
        updateCache(service, topicBinding("DATABENTO", "es-gex"),
                recordAt(settings.esGexSpxAlignedTopic(), 0, 1L, "SPX|20260622", json, now), json);

        setActiveSelection(service, "DATABENTO", "SPX", "20260622");
        assertEquals(1, cachedEvents(service, List.of("es-gex"), now).size(),
                "a fresh ES-on-SPX book replays to a matching SPX DATABENTO client");

        setActiveSelection(service, "DATABENTO", "SPY", "20260622");
        assertTrue(cachedEvents(service, List.of("es-gex"), now).isEmpty(),
                "a different symbol must not receive this book");
    }

    @Test
    void staleEsGexBookUsesPayloadEmitTimeNotArrival() throws Exception {
        FeedGatewayService service = service();
        GatewaySettings settings = new GatewaySettings();
        long now = System.currentTimeMillis();
        long staleEmit = now - 13L * 3600_000L; // older than the 12h book window
        String json = esGexBook(staleEmit);
        updateCache(service, topicBinding("DATABENTO", "es-gex"),
                recordAt(settings.esGexSpxAlignedTopic(), 0, 1L, "SPX|20260622", json, now), json);
        setActiveSelection(service, "DATABENTO", "SPX", "20260622");
        assertTrue(cachedEvents(service, List.of("es-gex"), now).isEmpty(),
                "a stale (old emitEventTimeMs) book must not replay — roll-forward freshness uses payload time");
    }

    @Test
    void cachedReplayIncludesFreshDeltaFlowForMatchingDatabentoSelectionOnly() throws Exception {
        FeedGatewayService service = service();
        GatewaySettings settings = new GatewaySettings();
        long now = System.currentTimeMillis();
        String json = "{\"marketDataSource\":\"DATABENTO\",\"symbol\":\"SPX\",\"expiry\":\"20260622\","
                + "\"strike\":6005,\"sessionNetDeltaFlow\":42,\"asOfEventTimeMs\":" + now + "}";
        updateCache(service, topicBinding("DATABENTO", "delta-flow"),
                recordAt(settings.databentoDeltaFlowByStrikeTopic(), 0, 1L, "SPX|20260622|6005", json, now), json);

        // Matching DATABENTO/SPX/20260622 selection replays it.
        setActiveSelection(service, "DATABENTO", "SPX", "20260622");
        assertEquals(1, cachedEvents(service, List.of("delta-flow"), now).size(),
                "fresh delta-flow must replay to a matching DATABENTO client");

        // Wrong source (IBKR) is filtered (delta-flow is DATABENTO-only).
        setActiveSelection(service, "IBKR", "SPX", "20260622");
        assertTrue(cachedEvents(service, List.of("delta-flow"), now).isEmpty(),
                "IBKR selection must never receive DATABENTO delta-flow");

        // Wrong symbol is filtered by the selection barrier.
        setActiveSelection(service, "DATABENTO", "SPY", "20260622");
        assertTrue(cachedEvents(service, List.of("delta-flow"), now).isEmpty(),
                "a different symbol must not receive this delta-flow");
    }

    @Test
    void staleCachedDeltaFlowIsNotReplayed() throws Exception {
        // FIX 1: a delta-flow whose freshness (event time) is old must NOT be replayed on connect — the
        // isCacheFresh gate in cachedEvents drops it, so a catching-up/backfilled producer cannot render
        // a stale delta-flow as a live signal.
        FeedGatewayService service = service();
        GatewaySettings settings = new GatewaySettings();
        long now = System.currentTimeMillis();
        // asOfEventTimeMs is well past the generic 15-min TTL; eventCacheTimestamp uses the payload time.
        long staleEventTime = now - 60L * 60_000L;
        String json = "{\"marketDataSource\":\"DATABENTO\",\"symbol\":\"SPX\",\"expiry\":\"20260622\","
                + "\"strike\":6005,\"sessionNetDeltaFlow\":42,\"asOfEventTimeMs\":" + staleEventTime + "}";
        // Fresh Kafka ARRIVAL time (record timestamp = now) — only the payload event time makes it stale.
        updateCache(service, topicBinding("DATABENTO", "delta-flow"),
                recordAt(settings.databentoDeltaFlowByStrikeTopic(), 0, 1L, "SPX|20260622|6005", json, now), json);

        setActiveSelection(service, "DATABENTO", "SPX", "20260622");
        assertTrue(cachedEvents(service, List.of("delta-flow"), now).isEmpty(),
                "a stale (old payload event time) delta-flow must not be replayed");
    }

    @Test
    void deltaFlowFreshnessUsesPayloadEventTimeNotKafkaArrivalTime() throws Exception {
        // FIX 1: eventCacheTimestamp for delta-flow returns the payload asOfEventTimeMs, not the record
        // arrival timestamp (mirrors dealer-ledger). The 5-arg ctor sets record timestamp = -1, so the
        // payload event time must be what is returned.
        FeedGatewayService service = service();
        long oldEventTime = 1_700_000_000_000L;
        ConsumerRecord<String, String> record = new ConsumerRecord<>(
                "delta-flow-by-strike", 0, 0L, "SPX|20260622|6005",
                "{\"symbol\":\"SPX\",\"expiry\":\"20260622\",\"strike\":6005,\"sessionNetDeltaFlow\":42,"
                        + "\"asOfEventTimeMs\":" + oldEventTime + "}");
        assertEquals(oldEventTime, eventCacheTimestamp(service, "delta-flow", record));
    }

    // ----- strike-intel gateway consumer (per-strike StrikeIntelligenceSignal) --------------------

    @Test
    void strikeIntelCacheKeyIsSymbolExpiryStrikeFromPayloadIdentity() throws Exception {
        // The helper derives symbol|expiry|strike from the payload (source is prepended later by
        // updateCache), mirroring deltaFlowCacheKey — strike-intel is per-strike, not chain-level.
        FeedGatewayService service = service();
        assertEquals("SPX|20260622|6005", strikeIntelCacheKey(
                service,
                "{\"symbol\":\"SPX\",\"expiry\":\"20260622\",\"strike\":6005,\"strikeRole\":\"MAGNET\"}",
                "fallback-key"));
    }

    @Test
    void strikeIntelUpdateCacheStoresSourcePrefixedKey() throws Exception {
        // After updateCache the stored/returned cache key is DATABENTO|SPX|expiry|strike (source prepended).
        FeedGatewayService service = service();
        String json = "{\"marketDataSource\":\"DATABENTO\",\"symbol\":\"SPX\",\"expiry\":\"20260622\","
                + "\"strike\":6005,\"strikeRole\":\"MAGNET\",\"eventTimeMs\":" + System.currentTimeMillis() + "}";
        String key = updateCache(service, topicBinding("DATABENTO", "strike-intel"),
                new ConsumerRecord<>(new GatewaySettings().strikeIntelByStrikeTopic(), 0, 1L, "SPX|20260622|6005", json),
                json);
        assertEquals("DATABENTO|SPX|20260622|6005", key,
                "updateCache must prepend the source to the strike-intel cache key");
        assertTrue(service.healthJson().contains("\"strikeIntels\":1"), "strike-intel must be cached");
        Method statusJson = FeedGatewayService.class.getDeclaredMethod("statusJson");
        statusJson.setAccessible(true);
        assertTrue(((String) statusJson.invoke(service)).contains("\"strikeIntels\":1"),
                "statusJson must report the strike-intel cache count (delta-flow counterpart)");
    }

    // ----- strike-invasion gateway consumer (per-strike, per-direction StrikeInvasionSnapshot, SPX-only, NO expiry) --

    @Test
    void strikeInvasionCacheKeyIsSymbolStrikeDirectionFromPayloadIdentity() throws Exception {
        // strike-invasion is SPX-only and carries NO expiry, so the key is symbol|strike|direction
        // (mirrors strikeIntelCacheKey minus the expiry segment, plus the contract-v2 direction;
        // source is prepended later by updateCache). Direction comes from the PAYLOAD, never the
        // Kafka record key shape.
        FeedGatewayService service = service();
        assertEquals("SPX|6005|UP", strikeInvasionCacheKey(
                service,
                "{\"symbol\":\"SPX\",\"strike\":6005,\"direction\":\"UP\",\"invasionState\":\"INVADED\"}",
                "fallback-key"));
        assertEquals("SPX|6005|DOWN", strikeInvasionCacheKey(
                service,
                "{\"symbol\":\"SPX\",\"strike\":6005,\"direction\":\"DOWN\",\"invasionState\":\"INVADED\"}",
                "fallback-key"));
        // Pre-v2 records carry no direction and were upside-only: they must key as UP (replacing an
        // older UP entry, never duplicating the strike). Blank direction gets the same default.
        assertEquals("SPX|6005|UP", strikeInvasionCacheKey(
                service,
                "{\"symbol\":\"SPX\",\"strike\":6005,\"invasionState\":\"INVADED\"}",
                "fallback-key"));
        assertEquals("SPX|6005|UP", strikeInvasionCacheKey(
                service,
                "{\"symbol\":\"SPX\",\"strike\":6005,\"direction\":\"\",\"invasionState\":\"INVADED\"}",
                "fallback-key"));
    }

    @Test
    void strikeInvasionUpdateCacheStoresSourcePrefixedKey() throws Exception {
        // After updateCache the stored/returned cache key is DATABENTO|SPX|strike|direction (source
        // prepended, NO expiry; direction defaults to UP for a pre-v2 direction-less record).
        FeedGatewayService service = service();
        String json = "{\"marketDataSource\":\"DATABENTO\",\"symbol\":\"SPX\","
                + "\"strike\":6005,\"invasionState\":\"INVADED\",\"eventTimeMs\":" + System.currentTimeMillis() + "}";
        String key = updateCache(service, topicBinding("DATABENTO", "strike-invasion"),
                new ConsumerRecord<>(new GatewaySettings().strikeInvasionTopic(), 0, 1L, "SPX|6005", json),
                json);
        assertEquals("DATABENTO|SPX|6005|UP", key,
                "updateCache must prepend the source to the strike-invasion cache key");

        String down = "{\"marketDataSource\":\"DATABENTO\",\"symbol\":\"SPX\",\"strike\":6005,"
                + "\"direction\":\"DOWN\",\"invasionState\":\"INVADED\",\"eventTimeMs\":" + System.currentTimeMillis() + "}";
        String downKey = updateCache(service, topicBinding("DATABENTO", "strike-invasion"),
                new ConsumerRecord<>(new GatewaySettings().strikeInvasionTopic(), 0, 2L, "6005:DOWN", down),
                down);
        assertEquals("DATABENTO|SPX|6005|DOWN", downKey,
                "a DOWN record must key separately from the same strike's UP record");
    }

    @Test
    void enrichJsonStampsActiveSelectionExpiryOnStrikeInvasion() throws Exception {
        // StrikeInvasionSnapshot carries NO expiry, but contract routing (RoutingKeyDeriver /
        // matchesSelectionNode / cached-replay barrier) matches on symbol|expiry and REJECTS a blank
        // expiry. enrichJson must stamp the active SPX selection's expiry so the record routes like
        // strike-intel instead of being dropped as a blank-expiry contract record.
        FeedGatewayService service = service();
        // The stamp comes from the market-calendar trading date, NOT the per-session selection.
        String today = currentTradingDateExpiry();
        setActiveSelection(service, "DATABENTO", "SPX", "20991231"); // deliberately NOT the calendar date
        String enriched = enrichJson(service,
                "{\"symbol\":\"SPX\",\"strike\":6005,\"state\":\"ACCEPTED_ABOVE\"}",
                topicBinding("DATABENTO", "strike-invasion"));
        assertEquals(today, new ObjectMapper().readTree(enriched).get("expiry").asText(),
                "strike-invasion must inherit the calendar 0DTE expiry (not the manual selection) so it is routable");
    }

    @Test
    void cachedReplayIncludesFreshStrikeInvasionForMatchingSpxSelectionOnly() throws Exception {
        // End-to-end: a strike-invasion record (no expiry in payload) enriched under an SPX selection
        // must replay to a matching DATABENTO SPX client — and only to it. Before the enrichJson expiry
        // stamp this dropped entirely (blank-expiry contract records never match a selection).
        FeedGatewayService service = service();
        GatewaySettings settings = new GatewaySettings();
        long now = System.currentTimeMillis();
        String today = currentTradingDateExpiry();
        setActiveSelection(service, "DATABENTO", "SPX", today);
        String enriched = enrichJson(service,
                "{\"symbol\":\"SPX\",\"strike\":6005,\"state\":\"ACCEPTED_ABOVE\",\"asOfEventTimeMs\":" + now + "}",
                topicBinding("DATABENTO", "strike-invasion"));
        updateCache(service, topicBinding("DATABENTO", "strike-invasion"),
                recordAt(settings.strikeInvasionTopic(), 0, 1L, "SPX|6005", enriched, now), enriched);

        assertEquals(1, cachedEvents(service, List.of("strike-invasion"), now).size(),
                "fresh strike-invasion must replay to a matching DATABENTO SPX 0DTE client");

        // Wrong source (IBKR) is filtered (strike-invasion is DATABENTO-only).
        setActiveSelection(service, "IBKR", "SPX", today);
        assertTrue(cachedEvents(service, List.of("strike-invasion"), now).isEmpty(),
                "IBKR selection must never receive DATABENTO strike-invasion");

        // Wrong symbol is filtered by the selection barrier.
        setActiveSelection(service, "DATABENTO", "SPY", today);
        assertTrue(cachedEvents(service, List.of("strike-invasion"), now).isEmpty(),
                "a different symbol must not receive this strike-invasion");

        // Wrong EXPIRY: a session that manually selected a non-0DTE SPX chain must NOT receive the 0DTE
        // invasion signal — the record is stamped with the calendar 0DTE date, independent of the
        // session's selection, so the selection barrier correctly filters a later expiry.
        setActiveSelection(service, "DATABENTO", "SPX", "20991231");
        assertTrue(cachedEvents(service, List.of("strike-invasion"), now).isEmpty(),
                "a non-0DTE SPX chain must not receive the 0DTE strike-invasion");
    }

    @Test
    void staleCachedStrikeInvasionIsNotReplayed() throws Exception {
        // A strike-invasion whose PAYLOAD event time (asOfEventTimeMs) is old must NOT replay on connect,
        // even though its Kafka ARRIVAL time is fresh — a catching-up/backfilling producer must not render
        // a stale invasion action as live (mirrors strike-intel; relies on the eventCacheTimestamp branch).
        FeedGatewayService service = service();
        GatewaySettings settings = new GatewaySettings();
        long now = System.currentTimeMillis();
        long staleEventTime = now - 60L * 60_000L;
        setActiveSelection(service, "DATABENTO", "SPX", currentTradingDateExpiry());
        String enriched = enrichJson(service,
                "{\"symbol\":\"SPX\",\"strike\":6005,\"state\":\"ACCEPTED_ABOVE\",\"asOfEventTimeMs\":" + staleEventTime + "}",
                topicBinding("DATABENTO", "strike-invasion"));
        // Fresh Kafka ARRIVAL time (record timestamp = now) — only the payload event time makes it stale.
        updateCache(service, topicBinding("DATABENTO", "strike-invasion"),
                recordAt(settings.strikeInvasionTopic(), 0, 1L, "SPX|6005", enriched, now), enriched);

        assertTrue(cachedEvents(service, List.of("strike-invasion"), now).isEmpty(),
                "a stale (old payload event time) strike-invasion must not be replayed");
    }

    @Test
    void strikeInvasionUpAndDownRecordsForTheSameStrikeCoexistInTheEnvelope() throws Exception {
        // Contract v2: one strike can legitimately carry BOTH a live UP record (SHORT_CALL_CANDIDATE
        // domain) and a DOWN record (SHORT_PUT_CANDIDATE domain) at the same time. With the old
        // symbol|strike cache key the second record OVERWROTE the first, silencing an actionable trade
        // verdict in the UI — the direction-qualified key must keep both alive through cache, cached
        // connect replay, and the strikeInvasions envelope array.
        FeedGatewayService service = service();
        GatewaySettings settings = new GatewaySettings();
        long now = System.currentTimeMillis();
        setActiveSelection(service, "DATABENTO", "SPX", currentTradingDateExpiry());
        String up = enrichJson(service,
                "{\"symbol\":\"SPX\",\"strike\":6005,\"direction\":\"UP\",\"state\":\"ACCEPTED_ABOVE\","
                        + "\"asOfEventTimeMs\":" + now + "}",
                topicBinding("DATABENTO", "strike-invasion"));
        String down = enrichJson(service,
                "{\"symbol\":\"SPX\",\"strike\":6005,\"direction\":\"DOWN\",\"state\":\"ACCEPTED_BELOW\","
                        + "\"asOfEventTimeMs\":" + now + "}",
                topicBinding("DATABENTO", "strike-invasion"));
        updateCache(service, topicBinding("DATABENTO", "strike-invasion"),
                recordAt(settings.strikeInvasionTopic(), 0, 1L, "6005:UP", up, now), up);
        updateCache(service, topicBinding("DATABENTO", "strike-invasion"),
                recordAt(settings.strikeInvasionTopic(), 0, 2L, "6005:DOWN", down, now), down);

        List<?> events = cachedEvents(service, List.of("strike-invasion"), now);
        assertEquals(2, events.size(),
                "UP and DOWN invasion records for the same strike must coexist (neither may overwrite the other)");
        List<String> jsons = new ArrayList<>();
        for (Object event : events) {
            jsons.add(cachedEventJson(event));
        }
        assertTrue(jsons.contains(up), "the UP record must survive the DOWN record's arrival");
        assertTrue(jsons.contains(down), "the DOWN record must be cached alongside the UP record");

        // Both raw records (direction passes through untouched) reach the strikeInvasions envelope array.
        String envelope = uiBatchEnvelopeJsonStrikeInvasion(service, jsons);
        assertTrue(envelope.contains(up) && envelope.contains(down),
                "the strikeInvasions envelope array must carry BOTH directions; was: " + envelope);
    }

    @Test
    void directionlessStrikeInvasionKeysAsUpAndReplacesTheOlderUpRecord() throws Exception {
        // Pre-v2 records carry no direction and were upside-only: a direction-less record must key as UP —
        // REPLACING the strike's older UP record (same cache slot, same monotonic freshness gate), never
        // duplicating the strike in the envelope.
        FeedGatewayService service = service();
        GatewaySettings settings = new GatewaySettings();
        long now = System.currentTimeMillis();
        setActiveSelection(service, "DATABENTO", "SPX", currentTradingDateExpiry());
        String olderUp = enrichJson(service,
                "{\"symbol\":\"SPX\",\"strike\":6005,\"direction\":\"UP\",\"state\":\"ACCEPTED_ABOVE\","
                        + "\"asOfEventTimeMs\":" + (now - 2_000L) + "}",
                topicBinding("DATABENTO", "strike-invasion"));
        String legacy = enrichJson(service,
                "{\"symbol\":\"SPX\",\"strike\":6005,\"state\":\"INVADED\","
                        + "\"asOfEventTimeMs\":" + now + "}",
                topicBinding("DATABENTO", "strike-invasion"));
        updateCache(service, topicBinding("DATABENTO", "strike-invasion"),
                recordAt(settings.strikeInvasionTopic(), 0, 1L, "6005:UP", olderUp, now - 2_000L), olderUp);
        updateCache(service, topicBinding("DATABENTO", "strike-invasion"),
                recordAt(settings.strikeInvasionTopic(), 0, 2L, "SPX|6005", legacy, now), legacy);

        List<?> events = cachedEvents(service, List.of("strike-invasion"), now);
        assertEquals(1, events.size(),
                "a direction-less record must REPLACE the strike's UP record, not duplicate the strike");
        assertEquals(legacy, cachedEventJson(events.get(0)),
                "the newer direction-less record must win the shared UP cache slot");

        // The shared slot also shares the per-record monotonic event-time gate: an OLDER direction-less
        // record must be dropped by the UP slot's freshness gate (updateCache returns null).
        String staleLegacy = enrichJson(service,
                "{\"symbol\":\"SPX\",\"strike\":6005,\"state\":\"RETREATED\","
                        + "\"asOfEventTimeMs\":" + (now - 1_000L) + "}",
                topicBinding("DATABENTO", "strike-invasion"));
        assertNull(updateCache(service, topicBinding("DATABENTO", "strike-invasion"),
                        recordAt(settings.strikeInvasionTopic(), 0, 3L, "SPX|6005", staleLegacy, now), staleLegacy),
                "an older direction-less record must be rejected by the UP slot's monotonic event-time gate");
    }

    @Test
    void replayReStampsStrikeInvasionExpiryToTheReplayWindow() throws Exception {
        // strike-invasion carries no expiry; enrichJson stamps the LIVE calendar date. In replay the record
        // belongs to the replay window's chain, so emitReplayRecord re-stamps params.expiry() — without it
        // the historical (live-dated) record would fail the expiry-matched replayMatches filter and be
        // silently dropped from the private replay stream.
        FeedGatewayService service = service();
        String enriched = enrichJson(service,
                "{\"symbol\":\"SPX\",\"strike\":6005,\"state\":\"ACCEPTED_ABOVE\"}",
                topicBinding("DATABENTO", "strike-invasion"));
        // A historical replay window (a date that is NOT the current calendar trading date).
        ReplayParams params = new ReplayParams("app:u1", "SPX", "20260612", 1_000L, 2_000L, 1000, null);
        assertNotEquals("20260612", currentTradingDateExpiry(),
                "test precondition: the replay window must differ from the live calendar date");

        // The live-dated record does NOT match a historical replay window...
        assertFalse(replayMatches(service, params, "strike-invasion", enriched),
                "an unstamped (live-dated) strike-invasion must not match a historical replay window");
        // ...but after the replay re-stamp (params.expiry) it does.
        String restamped = stampExpiry(service, enriched, params.expiry());
        assertTrue(replayMatches(service, params, "strike-invasion", restamped),
                "re-stamping the replay window expiry makes the replayed strike-invasion match");
    }

    // ----- ES 09:15 open-direction gateway consumer (once-a-day forecast + H1/H2/H3 outcomes) ------

    @Test
    void esOpenDirectionTopicsAreOptionalSoTheirAbsenceCannotStarveTheSharedConsumer() throws Exception {
        // The forecast producer is a brand-new service that may not be deployed (and after the daily
        // Kafka wipe the topics are absent until it first produces) — both topics MUST be optional so
        // their absence can never block/crash-loop the shared JSON consumer.
        FeedGatewayService service = service();
        GatewaySettings settings = new GatewaySettings();
        assertTrue(isOptionalTopic(service, settings.esOpenDirectionForecastTopic()));
        assertTrue(isOptionalTopic(service, settings.esOpenDirectionOutcomeTopic()));
    }

    @Test
    void esOpenDirectionCacheKeysAreTradeDateAndTradeDateHorizon() throws Exception {
        // Forecast: ONE cache entry per tradeDate (last-value-wins). Outcome: tradeDate|horizon, so the
        // day's H1/H2/H3 all survive side-by-side and a late-joining client replays every outcome
        // resolved so far (source is prepended later by updateCache).
        FeedGatewayService service = service();
        assertEquals("2026-07-11", esOpenDirectionForecastCacheKey(
                service,
                "{\"tradeDate\":\"2026-07-11\",\"status\":\"FORECASTED\",\"direction\":\"UP\"}",
                "fallback-key"));
        assertEquals("2026-07-11|H1", esOpenDirectionOutcomeCacheKey(
                service,
                "{\"tradeDate\":\"2026-07-11\",\"horizon\":\"H1\",\"correct\":true}",
                "fallback-key"));
        // Malformed payloads fall back to the Kafka key rather than throwing.
        assertEquals("fallback-key", esOpenDirectionForecastCacheKey(service, "not json", "fallback-key"));
        assertEquals("fallback-key", esOpenDirectionOutcomeCacheKey(service, "not json", "fallback-key"));
    }

    @Test
    void esOpenDirectionUpdateCacheStoresSourcePrefixedKeys() throws Exception {
        FeedGatewayService service = service();
        GatewaySettings settings = new GatewaySettings();
        long now = System.currentTimeMillis();
        String forecast = "{\"tradeDate\":\"2026-07-11\",\"status\":\"FORECASTED\",\"direction\":\"UP\"}";
        assertEquals("DATABENTO|2026-07-11",
                updateCache(service, topicBinding("DATABENTO", "es-open-direction-forecast"),
                        recordAt(settings.esOpenDirectionForecastTopic(), 0, 1L, "2026-07-11", forecast, now),
                        forecast),
                "updateCache must prepend the source to the forecast cache key");
        String outcome = "{\"tradeDate\":\"2026-07-11\",\"horizon\":\"H1\",\"correct\":true}";
        assertEquals("DATABENTO|2026-07-11|H1",
                updateCache(service, topicBinding("DATABENTO", "es-open-direction-outcome"),
                        recordAt(settings.esOpenDirectionOutcomeTopic(), 0, 1L, "2026-07-11", outcome, now),
                        outcome),
                "updateCache must prepend the source to the outcome cache key");
    }

    @Test
    void esOpenDirectionUsesLongSessionTtlNotGenericCacheWindow() throws Exception {
        // The whole point of the panel: a forecast published at 09:15 must still be served to a client
        // that connects at 11:00 (and at 15:59). A 2h-old (even 7h-old) forecast/outcome must be FRESH
        // under the long 12h window, while a generic event of the same age is long expired.
        FeedGatewayService service = service();
        long now = System.currentTimeMillis();
        long ttl = new GatewaySettings().esOpenDirectionTtlMs();
        assertTrue(ttl >= 12L * 3_600_000L, "es-open-direction TTL must cover the whole trading session");
        assertFalse(isExpired(service, "es-open-direction-forecast", now - 2L * 3_600_000L, now),
                "a 2h-old forecast (09:15 -> 11:15) must still be fresh");
        assertFalse(isExpired(service, "es-open-direction-outcome", now - 7L * 3_600_000L, now),
                "a 7h-old outcome (09:30 window vs late-day connect) must still be fresh");
        assertTrue(isExpired(service, "es-open-direction-forecast", now - ttl - 1, now),
                "a forecast past the 12h window must expire (yesterday never replays as today)");
        // Contrast: a generic event of 2h age IS expired — proves the events are not on the generic TTL.
        assertTrue(isExpired(service, "strike-flow", now - 2L * 3_600_000L, now));
    }

    @Test
    void lateJoiningClientReplaysForecastAndAllResolvedOutcomes() throws Exception {
        // End-to-end late-join contract: forecast cached at 09:15 (2h-old Kafka record) + H1/H2 outcomes,
        // then a client connects at ~11:31 — the standalone replay must deliver all three envelopes
        // (never dropped by the 15s market-data staleness gates, never selection-gated).
        FeedGatewayService service = service();
        GatewaySettings settings = new GatewaySettings();
        long now = System.currentTimeMillis();
        String forecast = "{\"tradeDate\":\"2026-07-11\",\"status\":\"FORECASTED\",\"direction\":\"UP\","
                + "\"evidenceStrength\":68}";
        updateCache(service, topicBinding("DATABENTO", "es-open-direction-forecast"),
                recordAt(settings.esOpenDirectionForecastTopic(), 0, 1L, "2026-07-11", forecast, now - 2L * 3_600_000L),
                forecast);
        String h1 = "{\"tradeDate\":\"2026-07-11\",\"horizon\":\"H1\",\"correct\":true,\"realizedDirection\":\"UP_WIN\"}";
        updateCache(service, topicBinding("DATABENTO", "es-open-direction-outcome"),
                recordAt(settings.esOpenDirectionOutcomeTopic(), 0, 1L, "2026-07-11", h1, now - 3_600_000L),
                h1);
        String h2 = "{\"tradeDate\":\"2026-07-11\",\"horizon\":\"H2\",\"correct\":false,\"realizedDirection\":\"DOWN_WIN\"}";
        updateCache(service, topicBinding("DATABENTO", "es-open-direction-outcome"),
                recordAt(settings.esOpenDirectionOutcomeTopic(), 0, 2L, "2026-07-11", h2, now - 60_000L),
                h2);

        List<String> sink = new ArrayList<>();
        Method replay = FeedGatewayService.class.getDeclaredMethod("replayEsOpenDirectionCached", WebSocketSession.class);
        replay.setAccessible(true);
        replay.invoke(service, recordingSession(sink));

        assertEquals(3, sink.size(), "late join must replay the forecast + BOTH resolved outcomes; got: " + sink);
        assertTrue(sink.get(0).contains("\"type\":\"es-open-direction-forecast\"")
                        && sink.get(0).contains("\"evidenceStrength\":68"),
                "forecast envelope first; was: " + sink.get(0));
        assertTrue(sink.stream().filter(s -> s.contains("\"type\":\"es-open-direction-outcome\"")).count() == 2,
                "both H1 and H2 outcomes must replay; was: " + sink);
        assertTrue(sink.stream().anyMatch(s -> s.contains("\"horizon\":\"H1\"")), "H1 must replay");
        assertTrue(sink.stream().anyMatch(s -> s.contains("\"horizon\":\"H2\"")), "H2 must replay");
    }

    @Test
    void esOpenDirectionEventsAreGlobalBroadcastInPerSessionMode() {
        // Per-session (auth) mode drops any event GatewayRecordMapper cannot route unless it is an
        // allowlisted GLOBAL advisory — without this, the panel silently goes dark once
        // GATEWAY_AUTH_ENABLED=true (the exact short-premium HIGH-1 review finding).
        assertTrue(FeedGatewayService.isGlobalBroadcastEvent("es-open-direction-forecast"));
        assertTrue(FeedGatewayService.isGlobalBroadcastEvent("es-open-direction-outcome"));
    }

    @Test
    void esOpenDirectionStatusIsOptionalGlobalAndOnTheShortFiveMinuteWindow() throws Exception {
        // The 60s live-status heartbeat shares the siblings' delivery class (optional topic, global
        // broadcast in per-session mode) but NOT their freshness: a status is only meaningful while
        // CURRENT, so it lives on the SHORT esOpenDirectionStatusTtlMs window (default 5 min) — never
        // the 12h forecast/outcome window that would replay a stale overnight status as live.
        FeedGatewayService service = service();
        GatewaySettings settings = new GatewaySettings();
        assertTrue(isOptionalTopic(service, settings.esOpenDirectionStatusTopic()),
                "status producer may be absent (not deployed / no active session) — must be optional");
        assertTrue(FeedGatewayService.isGlobalBroadcastEvent("es-open-direction-status"),
                "status must fan out in per-session (auth) mode like its siblings");
        assertEquals(300_000L, settings.esOpenDirectionStatusTtlMs(), "default TTL must be 5 minutes");
        long now = System.currentTimeMillis();
        assertFalse(isExpired(service, "es-open-direction-status", now - 2L * 60_000L, now),
                "a 2-min-old status (heartbeat is 60s) must still be fresh");
        assertTrue(isExpired(service, "es-open-direction-status", now - 6L * 60_000L, now),
                "a 6-min-old status must be STALE — never routed or replayed as current");
        // Contrast: the forecast sibling of the same age is comfortably fresh on its 12h window.
        assertFalse(isExpired(service, "es-open-direction-forecast", now - 6L * 60_000L, now));
    }

    @Test
    void freshEsOpenDirectionStatusIsCachedByTradeDateAndReplayedToLateJoiner() throws Exception {
        // Late-join contract: the current (fresh) heartbeat is cached last-value-wins under
        // DATABENTO|tradeDate and replayed standalone on connect, so a client that opens the dashboard
        // mid-overnight-session immediately shows the live strip instead of waiting up to 60s.
        FeedGatewayService service = service();
        GatewaySettings settings = new GatewaySettings();
        long now = System.currentTimeMillis();
        String older = "{\"tradeDate\":\"2026-07-13\",\"state\":\"CATCHING_UP\",\"tradesBuffered\":10,"
                + "\"observabilityOnly\":true}";
        assertEquals("DATABENTO|2026-07-13",
                updateCache(service, topicBinding("DATABENTO", "es-open-direction-status"),
                        recordAt(settings.esOpenDirectionStatusTopic(), 0, 1L, "2026-07-13", older, now - 2L * 60_000L),
                        older),
                "updateCache must key the status by source|tradeDate");
        String current = "{\"tradeDate\":\"2026-07-13\",\"state\":\"MONITORING\",\"tradesBuffered\":1842,"
                + "\"lastPrice\":6321.25,\"observabilityOnly\":true}";
        assertEquals("DATABENTO|2026-07-13",
                updateCache(service, topicBinding("DATABENTO", "es-open-direction-status"),
                        recordAt(settings.esOpenDirectionStatusTopic(), 0, 2L, "2026-07-13", current, now - 60_000L),
                        current));

        List<String> sink = new ArrayList<>();
        Method replay = FeedGatewayService.class.getDeclaredMethod("replayEsOpenDirectionCached", WebSocketSession.class);
        replay.setAccessible(true);
        replay.invoke(service, recordingSession(sink));

        assertEquals(1, sink.size(), "exactly the CURRENT status must replay (last heartbeat wins); got: " + sink);
        assertTrue(sink.get(0).contains("\"type\":\"es-open-direction-status\"")
                        && sink.get(0).contains("\"state\":\"MONITORING\"")
                        && sink.get(0).contains("\"tradesBuffered\":1842"),
                "the latest heartbeat must replay verbatim (JSON pass-through); was: " + sink.get(0));
    }

    @Test
    void staleEsOpenDirectionStatusIsNeitherCachedNorReplayed() throws Exception {
        // Staleness fail-closed: a status record older than the 5-min window (dead producer, gateway
        // catching up on an overnight backlog) makes updateCache return null — which suppresses the
        // live broadcast (the cacheKey == null gate) — and nothing replays to a late joiner, so the
        // UI strip simply stays hidden instead of showing a misleading overnight state.
        FeedGatewayService service = service();
        GatewaySettings settings = new GatewaySettings();
        long now = System.currentTimeMillis();
        String stale = "{\"tradeDate\":\"2026-07-13\",\"state\":\"MONITORING\",\"tradesBuffered\":42,"
                + "\"observabilityOnly\":true}";
        assertNull(updateCache(service, topicBinding("DATABENTO", "es-open-direction-status"),
                        recordAt(settings.esOpenDirectionStatusTopic(), 0, 1L, "2026-07-13", stale, now - 6L * 60_000L),
                        stale),
                "a 6-min-old status must be dropped at ingest (null cacheKey = never live-routed)");

        List<String> sink = new ArrayList<>();
        Method replay = FeedGatewayService.class.getDeclaredMethod("replayEsOpenDirectionCached", WebSocketSession.class);
        replay.setAccessible(true);
        replay.invoke(service, recordingSession(sink));
        assertTrue(sink.isEmpty(), "a stale status must never replay to a late joiner; got: " + sink);
    }

    // ----- greek-move-authenticity CURRENT verdict relay ------------------------------------------

    @Test
    void greekMoveAuthCurrentTopicIsOptionalGlobalAndOnTheShortFiveMinuteWindow() throws Exception {
        // The move-authenticity CURRENT verdict is a standalone global advisory (optional topic, global
        // broadcast in per-session mode) whose only value is being CURRENT — so it lives on the SHORT
        // greekMoveAuthTtlMs window (default 5 min, the es-open-direction STATUS freshness class), never a
        // long window that would replay a stale overnight verdict as live.
        FeedGatewayService service = service();
        GatewaySettings settings = new GatewaySettings();
        assertEquals("options.spx.greek-move-auth.current", settings.greekMoveAuthCurrentTopic(),
                "default topic must be the contract constant GreekMoveAuthTopics.GREEK_MOVE_AUTH_CURRENT");
        assertTrue(isOptionalTopic(service, settings.greekMoveAuthCurrentTopic()),
                "a brand-new standalone producer may be absent — the topic must be optional");
        assertTrue(FeedGatewayService.isGlobalBroadcastEvent("greek-move-auth"),
                "the verdict must fan out in per-session (auth) mode like the open-direction siblings");
        assertEquals(300_000L, settings.greekMoveAuthTtlMs(), "default TTL must be 5 minutes");
        long now = System.currentTimeMillis();
        assertFalse(isExpired(service, "greek-move-auth", now - 2L * 60_000L, now),
                "a 2-min-old verdict must still be fresh");
        assertTrue(isExpired(service, "greek-move-auth", now - 6L * 60_000L, now),
                "a 6-min-old verdict must be STALE — never routed or replayed as current");
    }

    @Test
    void greekMoveAuthUsesPayloadDecisionTimeAndSymbolKey() throws Exception {
        // Freshness tracks the PAYLOAD asOfEventTimeMs, not the Kafka arrival time; the cache key is the
        // symbol source-prefixed to source|symbol (last-value-wins per SPX/ES, the same convention as
        // es-open-direction-status' source|tradeDate) — a fresh-arriving backfilled verdict must expire
        // from its own decision time.
        FeedGatewayService service = service();
        GatewaySettings settings = new GatewaySettings();
        long decisionTime = System.currentTimeMillis() - 1_000L;
        String payload = "{\"schemaVersion\":1,\"symbol\":\"SPX\",\"asOfEventTimeMs\":" + decisionTime + ","
                + "\"verdict\":\"REAL_UP\",\"isReal\":true,\"moveDirection\":\"UP\",\"actionable\":true}";
        ConsumerRecord<String, String> record = recordAt(
                settings.greekMoveAuthCurrentTopic(), 0, 1L, "SPX", payload, System.currentTimeMillis());

        assertEquals(decisionTime, eventCacheTimestamp(service, "greek-move-auth", record),
                "fresh Kafka arrival must not disguise a historical verdict");
        assertEquals("DATABENTO|SPX",
                updateCache(service, topicBinding("DATABENTO", "greek-move-auth"), record, payload),
                "updateCache must key the verdict by source|symbol (symbol is the distinguishing component)");
    }

    @Test
    void futureGreekMoveAuthVerdictFailsClosedAndCannotPoisonLaterValidUpdates() throws Exception {
        // Clock-skew freeze-safety: a verdict stamped implausibly in the FUTURE (bad clock / corrupt
        // producer) must fail closed at ingest — otherwise its future event time would evade expiry AND
        // poison the monotonic last-value-wins supersede gate, rejecting every subsequent CORRECT verdict
        // as "older" until wall time catches up (a frozen move-authenticity track).
        FeedGatewayService service = service();
        GatewaySettings settings = new GatewaySettings();
        long now = System.currentTimeMillis();
        String future = "{\"schemaVersion\":1,\"symbol\":\"SPX\",\"asOfEventTimeMs\":" + (now + 60L * 60_000L)
                + ",\"verdict\":\"REAL_UP\",\"isReal\":true,\"moveDirection\":\"UP\",\"actionable\":true}";
        assertNull(updateCache(service, topicBinding("DATABENTO", "greek-move-auth"),
                        recordAt(settings.greekMoveAuthCurrentTopic(), 0, 1L, "SPX", future, now),
                        future),
                "an hour-ahead verdict must be dropped at ingest (fail closed), never cached");

        // A subsequent correctly-timed verdict must still be accepted (the future record left no poison).
        String current = "{\"schemaVersion\":1,\"symbol\":\"SPX\",\"asOfEventTimeMs\":" + (now - 5_000L)
                + ",\"verdict\":\"REAL_UP\",\"isReal\":true,\"moveDirection\":\"UP\",\"actionable\":true}";
        assertEquals("DATABENTO|SPX",
                updateCache(service, topicBinding("DATABENTO", "greek-move-auth"),
                        recordAt(settings.greekMoveAuthCurrentTopic(), 0, 2L, "SPX", current, now),
                        current),
                "a valid verdict after a future one must be accepted — the future record must not freeze the symbol");
    }

    @Test
    void freshGreekMoveAuthVerdictIsCachedBySymbolAndReplayedToLateJoiner() throws Exception {
        // Late-join contract: the current (fresh) verdict is cached last-value-wins under the symbol and
        // replayed standalone on connect, so a client that opens the dashboard mid-session immediately
        // shows the current move-authenticity track instead of waiting for the next live verdict.
        FeedGatewayService service = service();
        GatewaySettings settings = new GatewaySettings();
        long now = System.currentTimeMillis();
        String older = "{\"schemaVersion\":1,\"symbol\":\"SPX\",\"asOfEventTimeMs\":" + (now - 2L * 60_000L)
                + ",\"verdict\":\"FAKE\",\"isReal\":false,\"moveDirection\":\"UP\",\"actionable\":false}";
        assertEquals("DATABENTO|SPX",
                updateCache(service, topicBinding("DATABENTO", "greek-move-auth"),
                        recordAt(settings.greekMoveAuthCurrentTopic(), 0, 1L, "SPX", older, now - 2L * 60_000L),
                        older),
                "updateCache must key the verdict by source|symbol");
        String current = "{\"schemaVersion\":1,\"symbol\":\"SPX\",\"asOfEventTimeMs\":" + (now - 30_000L)
                + ",\"verdict\":\"REAL_UP\",\"isReal\":true,\"moveDirection\":\"UP\",\"actionable\":true}";
        assertEquals("DATABENTO|SPX",
                updateCache(service, topicBinding("DATABENTO", "greek-move-auth"),
                        recordAt(settings.greekMoveAuthCurrentTopic(), 0, 2L, "SPX", current, now - 30_000L),
                        current));

        List<String> sink = new ArrayList<>();
        Method replay = FeedGatewayService.class.getDeclaredMethod("replayGreekMoveAuthCached", WebSocketSession.class);
        replay.setAccessible(true);
        replay.invoke(service, recordingSession(sink));

        assertEquals(1, sink.size(), "exactly the CURRENT verdict must replay (last-value-wins); got: " + sink);
        assertTrue(sink.get(0).contains("\"type\":\"greek-move-auth\"")
                        && sink.get(0).contains("\"verdict\":\"REAL_UP\""),
                "the latest verdict must replay verbatim (JSON pass-through); was: " + sink.get(0));
    }

    @Test
    void staleGreekMoveAuthVerdictIsNeitherCachedNorReplayed() throws Exception {
        // Staleness fail-closed: a verdict older than the 5-min window (dead producer, gateway catching up
        // on a backlog) makes updateCache return null — which suppresses the live broadcast (the cacheKey
        // == null gate) — and nothing replays to a late joiner, so the UI track simply stays hidden.
        FeedGatewayService service = service();
        GatewaySettings settings = new GatewaySettings();
        long now = System.currentTimeMillis();
        String stale = "{\"schemaVersion\":1,\"symbol\":\"SPX\",\"asOfEventTimeMs\":" + (now - 6L * 60_000L)
                + ",\"verdict\":\"REAL_UP\",\"isReal\":true,\"moveDirection\":\"UP\",\"actionable\":true}";
        assertNull(updateCache(service, topicBinding("DATABENTO", "greek-move-auth"),
                        recordAt(settings.greekMoveAuthCurrentTopic(), 0, 1L, "SPX", stale, now - 6L * 60_000L),
                        stale),
                "a 6-min-old verdict must be dropped at ingest (null cacheKey = never live-routed)");

        List<String> sink = new ArrayList<>();
        Method replay = FeedGatewayService.class.getDeclaredMethod("replayGreekMoveAuthCached", WebSocketSession.class);
        replay.setAccessible(true);
        replay.invoke(service, recordingSession(sink));
        assertTrue(sink.isEmpty(), "a stale verdict must never replay to a late joiner; got: " + sink);
    }

    @Test
    void cachedReplayIncludesFreshStrikeIntelForMatchingDatabentoSelectionOnly() throws Exception {
        FeedGatewayService service = service();
        GatewaySettings settings = new GatewaySettings();
        long now = System.currentTimeMillis();
        String json = "{\"marketDataSource\":\"DATABENTO\",\"symbol\":\"SPX\",\"expiry\":\"20260622\","
                + "\"strike\":6005,\"strikeRole\":\"MAGNET\",\"eventTimeMs\":" + now + "}";
        updateCache(service, topicBinding("DATABENTO", "strike-intel"),
                recordAt(settings.strikeIntelByStrikeTopic(), 0, 1L, "SPX|20260622|6005", json, now), json);

        // Matching DATABENTO/SPX/20260622 selection replays it.
        setActiveSelection(service, "DATABENTO", "SPX", "20260622");
        assertEquals(1, cachedEvents(service, List.of("strike-intel"), now).size(),
                "fresh strike-intel must replay to a matching DATABENTO client");

        // Wrong source (IBKR) is filtered (strike-intel is DATABENTO-only).
        setActiveSelection(service, "IBKR", "SPX", "20260622");
        assertTrue(cachedEvents(service, List.of("strike-intel"), now).isEmpty(),
                "IBKR selection must never receive DATABENTO strike-intel");

        // Wrong symbol is filtered by the selection barrier.
        setActiveSelection(service, "DATABENTO", "SPY", "20260622");
        assertTrue(cachedEvents(service, List.of("strike-intel"), now).isEmpty(),
                "a different symbol must not receive this strike-intel");
    }

    @Test
    void staleCachedStrikeIntelIsNotReplayed() throws Exception {
        // A strike-intel whose freshness (event time) is old must NOT be replayed on connect — the
        // isCacheFresh gate in cachedEvents drops it, so a catching-up/backfilled producer cannot render
        // a stale strike-intel as a live signal (mirrors delta-flow).
        FeedGatewayService service = service();
        GatewaySettings settings = new GatewaySettings();
        long now = System.currentTimeMillis();
        long staleEventTime = now - 60L * 60_000L;
        String json = "{\"marketDataSource\":\"DATABENTO\",\"symbol\":\"SPX\",\"expiry\":\"20260622\","
                + "\"strike\":6005,\"strikeRole\":\"MAGNET\",\"eventTimeMs\":" + staleEventTime + "}";
        // Fresh Kafka ARRIVAL time (record timestamp = now) — only the payload event time makes it stale.
        updateCache(service, topicBinding("DATABENTO", "strike-intel"),
                recordAt(settings.strikeIntelByStrikeTopic(), 0, 1L, "SPX|20260622|6005", json, now), json);

        setActiveSelection(service, "DATABENTO", "SPX", "20260622");
        assertTrue(cachedEvents(service, List.of("strike-intel"), now).isEmpty(),
                "a stale (old payload event time) strike-intel must not be replayed");
    }

    @Test
    void markSelectionReadyIsOneShotAndGuardedByActiveSelection() throws Exception {
        FeedGatewayService service = service();
        setActiveSelection(service, "DATABENTO", "SPX", "20260623");
        Object active = activeSelectionOf(service);

        // First readiness for the active selection transitions readySelectionKey (and triggers the
        // one-shot source-ready + cached convergence re-push; harmless here with no clients/cache).
        invokeMarkSelectionReady(service, active);
        String key1 = readySelectionKey(service);
        assertFalse(key1.isEmpty(), "first ready must transition readySelectionKey");

        // One-shot: a second readiness for the SAME selection must not re-transition (no client spam).
        invokeMarkSelectionReady(service, active);
        assertEquals(key1, readySelectionKey(service), "markSelectionReady must be one-shot per selection");

        // Token guard: a readiness signal for a NON-active selection must be ignored entirely.
        setReadySelectionKey(service, "");
        Object superseded = newActiveSelection("DATABENTO", "SPX", "20260622");
        invokeMarkSelectionReady(service, superseded);
        assertTrue(readySelectionKey(service).isEmpty(),
                "a superseded selection must never mark ready or converge dashboards");
    }

    @Test
    void selectionReadyRepushesCachedStrikesAfterRoll() throws Exception {
        String source = Files.readString(Path.of("src/main/java/app/feedgateway/FeedGatewayService.java"));
        // Convergence re-push: markSelectionReady broadcasts source-ready, THEN re-pushes cached state so
        // open dashboards repopulate after the daily roll. Ordering matters (source-ready precedes replay).
        int readyIdx = source.indexOf(
                "broadcast(\"source-ready\", activeSelectionJson(selection, \"source-ready\"));");
        assertTrue(readyIdx > 0, "markSelectionReady must broadcast source-ready");
        int repushIdx = source.indexOf("broadcastCachedState(sourceSwitchReplayEvents());", readyIdx);
        assertTrue(repushIdx > readyIdx, "convergence cached re-push must come AFTER source-ready");
        // Readiness commit is atomic under readyLock with a LIVE active-selection re-check (no superseded
        // selection can announce/converge) and a one-shot per selection key.
        assertTrue(source.contains("synchronized (readyLock)"),
                "markSelectionReady must commit readiness atomically under readyLock");
        assertTrue(source.contains("!key.equals(selectionKey(activeSelection.get()))"),
                "markSelectionReady must re-validate against the live active selection");
        // Forward decision uses a selection captured ONCE per record (no mid-record activeSelection re-read).
        assertTrue(source.contains("recordSelectedForward(binding, json, decided)"),
                "forward path must carry the decided selection into recordSelectedForward");
        assertTrue(source.contains("shouldForward(binding, json, record, ActiveSelection selection)")
                        || source.contains("ConsumerRecord<?, ?> record, ActiveSelection selection)"),
                "shouldForward must have a selection-carrying overload");
        // Cache-arrival trigger: a cached-but-not-forwarded snapshot for the active selection still converges
        // (covers the closed-market case where the seed snapshot arrives already older than maxStaleMs).
        assertTrue(source.contains("matchesActiveSelection(json, current)"),
                "cache-arrival path must mark ready only for snapshots matching the active selection");
        assertTrue(source.contains("markSelectionReady(current);"),
                "cache-arrival path must call markSelectionReady");
    }

    @Test
    void selectionReadyDeliversSourceReadyThenCachedStrikesToOpenClient() throws Exception {
        FeedGatewayService service = service();
        setActiveSelection(service, "DATABENTO", "SPX", "20260623");
        long now = System.currentTimeMillis();

        // A fresh snapshot for the active selection is cached (the post-roll seed strike).
        String snapshotJson = "{\"marketDataSource\":\"DATABENTO\",\"symbol\":\"SPX\",\"expiry\":\"20260623\",\"strike\":7000}";
        String key = updateCache(service, topicBinding("DATABENTO", "snapshot"),
                recordAt("options.databento.display", 0, 1L, "SPX|20260623|7000", snapshotJson, now),
                snapshotJson);
        assertEquals("DATABENTO|SPX|20260623|7000", key, "snapshot must be cached under source|symbol|expiry|strike");

        // An already-open dashboard.
        List<String> sent = new ArrayList<>();
        addRecordingClient(service, sent);

        // Converge: readiness for the active selection must deliver source-ready THEN the cached strike.
        invokeMarkSelectionReady(service, activeSelectionOf(service));

        int readyIdx = -1;
        int batchIdx = -1;
        for (int i = 0; i < sent.size(); i++) {
            String msg = sent.get(i);
            if (readyIdx < 0 && msg.contains("source-ready")) {
                readyIdx = i;
            }
            if (batchIdx < 0 && msg.contains("\"expiry\":\"20260623\"") && msg.contains("7000")) {
                batchIdx = i;
            }
        }
        assertTrue(readyIdx >= 0, "open client must receive source-ready after a roll");
        assertTrue(batchIdx > readyIdx, "cached strike batch must arrive AFTER source-ready (ordering)");

        // One-shot: a second readiness for the same selection must NOT re-broadcast (no client spam).
        int before = sent.size();
        invokeMarkSelectionReady(service, activeSelectionOf(service));
        assertEquals(before, sent.size(), "second readiness for the same selection must not re-broadcast");
    }

    @Test
    void databentoGexTopicHasExpectedDefault() {
        assertEquals("options.databento.gex.strike", new GatewaySettings().databentoGexTopic());
    }

    @Test
    void databentoMaxPainTopicHasExpectedDefault() {
        assertEquals("options.databento.maxpain", new GatewaySettings().databentoMaxPainTopic());
    }

    @Test
    void maxPainCacheKeyDerivesFromPayloadSymbolAndExpiry() throws Exception {
        FeedGatewayService service = service();
        String json = "{\"symbol\":\"spx\",\"expiry\":\"2026-07-10\",\"status\":\"VALID\"}";
        assertEquals("SPX|20260710", maxPainCacheKey(service, json, "fallback"));
        // Missing symbol -> fallback
        assertEquals("fallback", maxPainCacheKey(service, "{\"expiry\":\"20260710\"}", "fallback"));
        // Non-JSON -> fallback (defensive; never throws)
        assertEquals("fallback", maxPainCacheKey(service, "not-json", "fallback"));
    }

    @Test
    void opbV2ByOptionCacheKeyIncludesSideSoCallAndPutDoNotCollide() throws Exception {
        FeedGatewayService service = service();
        String call = "{\"symbol\":\"spx\",\"expiry\":\"2026-07-10\",\"strike\":5500.0,\"optionType\":\"CALL\"}";
        String put = "{\"symbol\":\"spx\",\"expiry\":\"2026-07-10\",\"strike\":5500.0,\"optionType\":\"PUT\"}";
        // Per-contract events: same strike, opposite side must land in distinct cache slots. Strike is
        // normalized (formatStrike) so 5500.0 and 5500 collapse to a single slot.
        assertEquals("SPX|20260710|5500|CALL", opbV2ByOptionCacheKey(service, call, "fallback"));
        assertEquals("SPX|20260710|5500|PUT", opbV2ByOptionCacheKey(service, put, "fallback"));
        assertEquals("SPX|20260710|5500|CALL",
                opbV2ByOptionCacheKey(service, call.replace("5500.0", "5500"), "fallback"));
        // Missing side -> fall back to optionKey, then to the Kafka key.
        String keyed = "{\"symbol\":\"SPX\",\"expiry\":\"20260710\",\"strike\":5500.0,\"optionKey\":\"SPX-20260710-5500-C\"}";
        assertEquals("SPX|20260710|5500|SPX-20260710-5500-C", opbV2ByOptionCacheKey(service, keyed, "fallback"));
        assertEquals("fallback", opbV2ByOptionCacheKey(service, "{\"symbol\":\"SPX\",\"expiry\":\"20260710\"}", "fallback"));
        assertEquals("fallback", opbV2ByOptionCacheKey(service, "not-json", "fallback"));
    }

    @Test
    void isMaxPainExpiredReturnsTrueOnlyForTerminalStatus() throws Exception {
        FeedGatewayService service = service();
        assertTrue(isMaxPainExpired(service, "{\"status\":\"TERMINAL\"}"));   // v2 terminal status
        assertTrue(isMaxPainExpired(service, "{\"status\":\"EXPIRED\"}"));    // v1 terminal status (back-compat)
        assertFalse(isMaxPainExpired(service, "{\"status\":\"VALID\"}"));
        assertFalse(isMaxPainExpired(service, "{\"status\":\"EMPTY\"}"));
        assertFalse(isMaxPainExpired(service, "{}"));
        // Malformed JSON must NOT throw — defensive.
        assertFalse(isMaxPainExpired(service, "not-json"));
        assertFalse(isMaxPainExpired(service, null));
        assertFalse(isMaxPainExpired(service, ""));
    }

    @Test
    void uiBatchEnvelopeCarriesMaxPainArrayKey() throws Exception {
        FeedGatewayService service = service();
        // A single max-pain JSON in the batch must surface under the "maxPains" array on the wire.
        String json = "{\"messageType\":\"MAX_PAIN\",\"symbol\":\"SPX\",\"expiry\":\"20260710\",\"status\":\"VALID\",\"maxPainStrike\":4500.0}";
        String envelope = uiBatchEnvelopeJsonMaxPain(service, List.of(json));
        assertTrue(envelope.contains("\"maxPains\":[" + json + "]"),
                "batch envelope must carry the maxPains array with the record; was: " + envelope);
        // Existing gex array must still be present (no regression).
        assertTrue(envelope.contains("\"gexByStrike\":[]"));
    }

    @Test
    void uiBatchEnvelopeCarriesOpbV2ByOptionArrayKey() throws Exception {
        FeedGatewayService service = service();
        String json = "{\"symbol\":\"SPX\",\"expiry\":\"20260710\",\"strike\":5500.0,\"residualZScore\":3.2,\"behaviorLabel\":\"CALL_OVERPERFORMING\"}";
        String envelope = uiBatchEnvelopeJsonOpbV2ByOption(service, List.of(json));
        assertTrue(envelope.contains("\"opbV2ByOptions\":[" + json + "]"),
                "batch envelope must carry the opbV2ByOptions array; was: " + envelope);
        assertTrue(envelope.contains("\"optionPriceBehaviors\":[]"));
    }

    @Test
    void uiBatchEnvelopeCarriesOpbV2SessionArrayKey() throws Exception {
        FeedGatewayService service = service();
        String json = "{\"symbol\":\"SPX\",\"tradingDate\":\"2026-07-10\",\"directionalPressureZ\":2.7,\"perContractAnomalyZ\":1.4}";
        String envelope = uiBatchEnvelopeJsonOpbV2Session(service, List.of(json));
        assertTrue(envelope.contains("\"opbV2Sessions\":[" + json + "]"),
                "batch envelope must carry the opbV2Sessions array; was: " + envelope);
    }

    @Test
    void indexPriceCacheKeyUsesPayloadSymbolInsteadOfKafkaTradeKey() {
        FeedGatewayService service = new FeedGatewayService(
                new GatewaySettings(),
                new ObjectMapper(),
                new HpsfGatewayViewMapper(),
                null
        );

        String firstEsTrade = "{\"symbol\":\"ES.v.0\",\"instrumentId\":\"42140864\",\"price\":7580.5}";
        String nextEsTrade = "{\"symbol\":\"ES.v.0\",\"instrumentId\":\"42140864\",\"price\":7580.75}";
        String vixPrice = "{\"symbol\":\"VIX\",\"price\":16.2}";

        assertEquals("ES.V.0", service.indexPriceCacheKey(firstEsTrade, "trade-1"));
        assertEquals("ES.V.0", service.indexPriceCacheKey(nextEsTrade, "trade-2"));
        assertEquals("VIX", service.indexPriceCacheKey(vixPrice, "vix-record"));
    }

    @Test
    void paceCacheKeyUsesNumericStrikePayloadIdentity() throws Exception {
        FeedGatewayService service = service();

        assertEquals("IBKR|SPX|20260616|7585", paceCacheKey(
                service,
                "{\"source\":\"IBKR\",\"symbol\":\"SPX\",\"expiry\":\"2026-06-16\",\"strike\":7585}",
                "fallback"
        ));
    }

    @Test
    void paceCacheKeyPreservesDecimalStrikePayloadIdentity() throws Exception {
        FeedGatewayService service = service();

        assertEquals("DATABENTO|SPX|20260616|7585.5", paceCacheKey(
                service,
                "{\"marketDataSource\":\"DATABENTO\",\"symbol\":\"spx\",\"expiry\":\"20260616\",\"strike\":7585.5}",
                "fallback"
        ));
    }

    @Test
    void paceCacheKeyFallsBackWhenRequiredFieldsAreMissing() throws Exception {
        FeedGatewayService service = service();

        assertEquals("fallback-key", paceCacheKey(
                service,
                "{\"source\":\"IBKR\",\"symbol\":\"SPX\",\"strike\":7585}",
                "fallback-key"
        ));
    }

    @Test
    void paceCacheKeyFallsBackWhenSourceIsMissing() throws Exception {
        FeedGatewayService service = service();

        assertEquals("fallback-key", paceCacheKey(
                service,
                "{\"symbol\":\"SPX\",\"expiry\":\"20260616\",\"strike\":7585}",
                "fallback-key"
        ));
    }

    @Test
    void gexCacheKeyUsesPayloadIdentity() throws Exception {
        FeedGatewayService service = service();

        // Source is prepended by updateCache, so the helper returns symbol|expiry|strike.
        assertEquals("SPX|20260612|6005", gexCacheKey(
                service,
                "{\"source\":\"DATABENTO\",\"symbol\":\"spx\",\"expiry\":\"2026-06-12\",\"strike\":6005}",
                "fallback"
        ));
    }

    @Test
    void gexCacheKeyPreservesDecimalStrikePayloadIdentity() throws Exception {
        FeedGatewayService service = service();

        assertEquals("SPX|20260612|6005.5", gexCacheKey(
                service,
                "{\"symbol\":\"SPX\",\"expiry\":\"20260612\",\"strike\":6005.5}",
                "fallback"
        ));
    }

    @Test
    void gexCacheKeyFallsBackWhenStrikeMissing() throws Exception {
        FeedGatewayService service = service();

        assertEquals("fallback-key", gexCacheKey(
                service,
                "{\"symbol\":\"SPX\",\"expiry\":\"20260612\"}",
                "fallback-key"
        ));
    }

    @Test
    void databentoGexHistoryDerivesSameCacheKeyAsPlainGex() throws Exception {
        // The merge of the databento gex-history `history` map onto the databento gex row hinges on
        // BOTH records deriving the SAME cache key. The history record (a superset emitted by
        // databento-gex-history-service) carries symbol|expiry|strike identical to the plain gex
        // record, so gexCacheKey() lands them in the same gex-by-strike cache slot.
        FeedGatewayService service = service();

        String plainGex = "{\"source\":\"DATABENTO\",\"symbol\":\"SPX\",\"expiry\":\"20260612\",\"strike\":6005,\"netGex\":-1.0}";
        String gexHistory = "{\"source\":\"DATABENTO\",\"symbol\":\"SPX\",\"expiry\":\"20260612\",\"strike\":6005,\"netGex\":-1.0,"
                + "\"history\":{\"5m\":{\"window\":\"5m\",\"available\":true,\"netGex\":-2.0,\"delta\":1.0,\"direction\":\"UP\",\"sampledAt\":\"2026-06-23T19:54:00Z\"}}}";

        String plainKey = gexCacheKey(service, plainGex, "fallbackA");
        String historyKey = gexCacheKey(service, gexHistory, "fallbackB");
        assertEquals("SPX|20260612|6005", plainKey);
        assertEquals(plainKey, historyKey);
    }

    @Test
    void databentoGexHistoryBindsOnJsonStateConsumersNotAvro() throws Exception {
        // The databento gex HISTORY topic is JSON (databento-gex-history-service emits String/JSON),
        // unlike the Avro databento gex topic. It must bind on the JSON state cache + live consumers
        // (so its `history` map merges onto the gex rows) and must NOT appear on the Avro consumers.
        String source = Files.readString(Path.of("src/main/java/app/feedgateway/FeedGatewayService.java"));
        String historyBinding =
                "topicEvents.put(settings.databentoGexHistoryTopic(), new TopicBinding(\"DATABENTO\", \"gex-by-strike\"));";

        for (String method : List.of("runJsonStateCacheConsumer", "runJsonStateLiveConsumer")) {
            assertTrue(methodBody(source, method).contains(historyBinding),
                    method + " must bind the DATABENTO gex-history topic (JSON)");
        }
        for (String method : List.of("runAvroCacheConsumer", "runAvroLiveConsumer")) {
            assertFalse(methodBody(source, method).contains(historyBinding),
                    method + " must NOT bind the DATABENTO gex-history topic (it is JSON, not Avro)");
        }
    }

    @Test
    void paceCacheStoresSameStrikeSeparatelyBySource() throws Exception {
        FeedGatewayService service = service();
        Object ibkrBinding = topicBinding("IBKR", "pace");
        Object databentoBinding = topicBinding("DATABENTO", "pace");

        String ibkrKey = updateCache(
                service,
                ibkrBinding,
                new ConsumerRecord<>("options.ibkr.pace", 0, 1L, "ignored", ""),
                "{\"source\":\"IBKR\",\"symbol\":\"SPX\",\"expiry\":\"20260616\",\"strike\":7585,\"eventTime\":\"2026-06-16T14:00:00Z\"}"
        );
        String databentoKey = updateCache(
                service,
                databentoBinding,
                new ConsumerRecord<>("options.databento.pace", 0, 2L, "ignored", ""),
                "{\"marketDataSource\":\"DATABENTO\",\"symbol\":\"SPX\",\"expiry\":\"20260616\",\"strike\":7585,\"eventTime\":\"2026-06-16T14:00:00Z\"}"
        );

        assertEquals("IBKR|SPX|20260616|7585", ibkrKey);
        assertEquals("DATABENTO|SPX|20260616|7585", databentoKey);
    }

    @Test
    void paceCacheKeyFallsBackForMalformedJson() throws Exception {
        FeedGatewayService service = service();

        assertEquals("fallback-key", paceCacheKey(service, "{not-json", "fallback-key"));
    }

    @Test
    void catchUpRequiresOnlyActiveSource() {
        assertTrue(FeedGatewayService.requiresCatchUpForActiveSource("DATABENTO", "DATABENTO"));
        assertFalse(FeedGatewayService.requiresCatchUpForActiveSource("DATABENTO", "IBKR"));
    }

    @Test
    void cachedOptionSnapshotsCanReplayPastLiveStaleWindowForEitherSource() {
        assertFalse(FeedGatewayService.enforceCachedReplayMaxStale("snapshot", "DATABENTO"));
        assertFalse(FeedGatewayService.enforceCachedReplayMaxStale("snapshot", "IBKR"));
        assertTrue(FeedGatewayService.enforceCachedReplayMaxStale("pace", "DATABENTO"));
        assertTrue(FeedGatewayService.enforceCachedReplayMaxStale("pace", "IBKR"));

        assertFalse(FeedGatewayService.enforceCachedReplayOffsetBarrier("snapshot", "DATABENTO"));
        assertFalse(FeedGatewayService.enforceCachedReplayOffsetBarrier("snapshot", "IBKR"));
        assertTrue(FeedGatewayService.enforceCachedReplayOffsetBarrier("pace", "DATABENTO"));
        assertTrue(FeedGatewayService.enforceCachedReplayOffsetBarrier("pace", "IBKR"));
    }

    @Test
    void slowGexByStrikeIsExemptFromCachedReplayBarriersLikeMaxPain() {
        // GEX is a once-daily-OI signal whose latest per-strike record is routinely older than the 15s
        // selection barrier — it must replay on connect like snapshot/max-pain, not be re-dropped as stale.
        assertFalse(FeedGatewayService.enforceCachedReplayMaxStale("gex-by-strike", "DATABENTO"));
        assertFalse(FeedGatewayService.enforceCachedReplayMaxStale("gex-by-strike", "IBKR"));
        assertFalse(FeedGatewayService.enforceCachedReplayOffsetBarrier("gex-by-strike", "DATABENTO"));
        assertFalse(FeedGatewayService.enforceCachedReplayOffsetBarrier("gex-by-strike", "IBKR"));
        // Regression guard: max-pain stays exempt, fast flow signals stay gated.
        assertFalse(FeedGatewayService.enforceCachedReplayMaxStale("max-pain", "DATABENTO"));
        assertTrue(FeedGatewayService.enforceCachedReplayMaxStale("strike-flow", "DATABENTO"));
    }

    @Test
    void gexByStrikeUsesLongLastValueWinsTtlLikeMaxPain() {
        // Default: GEX shares max-pain's 12h window so a slow strike is not evicted after 15 min.
        GatewaySettings s = new GatewaySettings();
        assertEquals(s.maxPainTtlMs(), s.gexByStrikeTtlMs());
    }

    @Test
    void cachedOptionSnapshotsCanReplayBeforeNewSelectionTime() {
        FeedGatewayService service = new FeedGatewayService(
                new GatewaySettings(),
                new ObjectMapper(),
                new HpsfGatewayViewMapper(),
                null
        );

        assertTrue(service.passesSelectionTimeBarrierForTest(100L, 200L, false));
        assertFalse(service.passesSelectionTimeBarrierForTest(100L, 200L, true));
    }

    @Test
    void gatewayKafkaFetchSettingsAreBoundedByDefault() {
        GatewaySettings settings = new GatewaySettings();

        assertEquals(100, settings.maxPollRecords());
        assertEquals(4 * 1024 * 1024, settings.fetchMaxBytes());
        assertEquals(512 * 1024, settings.maxPartitionFetchBytes());
        assertEquals(512 * 1024, settings.receiveBufferBytes());
    }

    @Test
    void gatewayKafkaFetchSettingsCanBeOverriddenWithMinimums() {
        withSystemProperty("GATEWAY_KAFKA_MAX_POLL_RECORDS", "0", () ->
                assertEquals(1, new GatewaySettings().maxPollRecords()));
        withSystemProperty("GATEWAY_KAFKA_FETCH_MAX_BYTES", "128", () ->
                assertEquals(1024, new GatewaySettings().fetchMaxBytes()));
    }

    @Test
    void gatewayInitialExpiryHonorsConfiguredDateWithoutClockRollover() {
        // The gateway must mirror the deploy-resolved IB_EXPIRY (= the Databento feed's chain date)
        // and never advance it on a local clock rule. A configured expiry is returned verbatim no
        // matter the time of day, so the gateway's default selection and the feed stay on the same
        // date — otherwise the chain points at a date the feed never publishes and goes empty.
        withSystemProperty("IB_EXPIRY", "20260615", () ->
                assertEquals("20260615", new GatewaySettings().initialExpiry()));
        assertEquals("20260615", GatewaySettings.normalizeExpiry("2026-06-15"));
    }

    @Test
    void cachedSelectionRejectsOlderSelectionEpochs() throws Exception {
        ObjectMapper mapper = new ObjectMapper();

        assertFalse(FeedGatewayService.matchesSelectionNode(
                mapper.readTree("{\"marketDataSource\":\"DATABENTO\",\"symbol\":\"SPX\",\"expiry\":\"20260615\","
                        + "\"selectionEpoch\":100,\"strike\":7580}"),
                "DATABENTO",
                "SPX",
                "20260615",
                200,
                true
        ));
        assertTrue(FeedGatewayService.matchesSelectionNode(
                mapper.readTree("{\"marketDataSource\":\"DATABENTO\",\"symbol\":\"SPX\",\"expiry\":\"20260615\","
                        + "\"selectionEpoch\":200,\"strike\":7585}"),
                "DATABENTO",
                "SPX",
                "20260615",
                200,
                true
        ));
    }

    @Test
    void cachedSnapshotReplayCanIgnoreOlderSelectionEpoch() throws Exception {
        ObjectMapper mapper = new ObjectMapper();

        assertTrue(FeedGatewayService.matchesSelectionNode(
                mapper.readTree("{\"marketDataSource\":\"IBKR\",\"symbol\":\"SPX\",\"expiry\":\"20260616\","
                        + "\"selectionEpoch\":100,\"strike\":7580}"),
                "IBKR",
                "SPX",
                "20260616",
                200,
                false
        ));
    }

    @Test
    void strikeFlowGatewayContractConsumesCachesAndExposesUiBatchHealthAndMetrics() throws Exception {
        FeedGatewayService service = new FeedGatewayService(
                new GatewaySettings(),
                new ObjectMapper(),
                new HpsfGatewayViewMapper(),
                null
        );
        GatewaySettings settings = new GatewaySettings();
        String source = Files.readString(Path.of("src/main/java/app/feedgateway/FeedGatewayService.java"));
        String payload = "{\"eventType\":\"strike-flow\",\"marketDataSource\":\"DATABENTO\","
                + "\"symbol\":\"SPX\",\"expiry\":\"20260619\",\"strikes\":[]}";
        Object binding = topicBinding("DATABENTO", "strike-flow");
        ConsumerRecord<String, String> record = new ConsumerRecord<>(
                settings.databentoStrikeFlowTopic(),
                0,
                12L,
                "SPX|20260619",
                payload
        );

        String cacheKey = updateCache(service, binding, record, payload);
        String eventEnvelope = envelopeJson(service, "strike-flow", payload);
        String batchEnvelope = uiBatchEnvelopeJson(service, List.of(payload));

        assertEquals("options.databento.strike-flow", settings.databentoStrikeFlowTopic());
        assertTrue(source.contains("topicEvents.put(settings.databentoStrikeFlowTopic(), new TopicBinding(\"DATABENTO\", \"strike-flow\"));"));
        assertEquals("DATABENTO|SPX|20260619", cacheKey);
        assertTrue(eventEnvelope.contains("\"type\":\"strike-flow\""));
        assertTrue(batchEnvelope.contains("\"strikeFlows\":[{\"eventType\":\"strike-flow\""));
        assertTrue(service.healthJson().contains("\"strikeFlows\":1"));
        assertTrue(service.metrics().contains("options_edge_feed_gateway_strike_flows 1"));
    }

    @Test
    void optionPriceBehaviorGatewayContractConsumesCachesAndExposesUiBatchHealthAndMetrics() throws Exception {
        FeedGatewayService service = service();
        GatewaySettings settings = new GatewaySettings();
        String source = Files.readString(Path.of("src/main/java/app/feedgateway/FeedGatewayService.java"));
        String payload = "{\"symbol\":\"SPX\",\"tradingDate\":\"20260702\",\"marketDataSource\":\"DATABENTO\","
                + "\"sessionBehaviorScore\":1.2,\"rolling10sBehaviorScore\":0.4,\"rolling1mBehaviorScore\":0.7}";
        Object binding = topicBinding("DATABENTO", "option-price-behavior");
        ConsumerRecord<String, String> record = new ConsumerRecord<>(
                settings.optionPriceBehaviorDashboardTopic(),
                0,
                12L,
                "SPX|20260702",
                payload
        );

        String cacheKey = updateCache(service, binding, record, payload);
        String eventEnvelope = envelopeJson(service, "option-price-behavior", payload);
        String batchEnvelope = uiBatchEnvelopeJsonOptionPriceBehavior(service, List.of(payload));

        assertEquals("option-price-behavior-dashboard", settings.optionPriceBehaviorDashboardTopic());
        assertTrue(source.contains("topicEvents.put(settings.optionPriceBehaviorDashboardTopic(), new TopicBinding(\"DATABENTO\", \"option-price-behavior\"));"));
        assertEquals("DATABENTO|SPX|20260702", cacheKey);
        assertTrue(eventEnvelope.contains("\"type\":\"option-price-behavior\""));
        assertTrue(batchEnvelope.contains("\"optionPriceBehaviors\":[{\"symbol\":\"SPX\""));
        assertTrue(service.healthJson().contains("\"optionPriceBehaviors\":1"));
        assertTrue(service.metrics().contains("options_edge_feed_gateway_option_price_behaviors 1"));
    }

    @Test
    void missionPaceGatewayContractConsumesCachesAndExposesUiBatchHealthAndMetrics() throws Exception {
        FeedGatewayService service = service();
        GatewaySettings settings = new GatewaySettings();
        String source = Files.readString(Path.of("src/main/java/app/feedgateway/FeedGatewayService.java"));
        String payload = "{\"eventType\":\"mission-pace\",\"symbol\":\"SPX\",\"expiry\":\"20260612\","
                + "\"spot\":6004.8,\"timestampMs\":1,\"rankedStrikes\":[{\"strike\":6005,\"missionPaceScore\":91.4}]}";
        Object binding = topicBinding("DATABENTO", "mission-pace");
        ConsumerRecord<String, String> record = new ConsumerRecord<>(
                settings.databentoPaceMissionTopic(),
                0,
                12L,
                "SPX|20260612",
                payload
        );

        String cacheKey = updateCache(service, binding, record, payload);
        String eventEnvelope = envelopeJson(service, "mission-pace", payload);
        String batchEnvelope = uiBatchEnvelopeJsonMissionPace(service, List.of(payload));

        // Default topic resolves to the mission-pace topic and binds on the JSON/state path.
        assertEquals("options.databento.pace.mission", settings.databentoPaceMissionTopic());
        assertTrue(source.contains("topicEvents.put(settings.databentoPaceMissionTopic(), new TopicBinding(\"DATABENTO\", \"mission-pace\"));"));
        // Cache key is symbol|expiry (per-market, no strike — like max-pain). updateCache prepends the
        // source, so the full slot is source|symbol|expiry.
        assertEquals("DATABENTO|SPX|20260612", cacheKey);
        assertTrue(eventEnvelope.contains("\"type\":\"mission-pace\""));
        assertTrue(batchEnvelope.contains("\"missionPaces\":[{\"eventType\":\"mission-pace\""));
        assertTrue(service.healthJson().contains("\"missionPaces\":1"));
        assertTrue(service.metrics().contains("options_edge_feed_gateway_mission_paces 1"));
    }

    @Test
    void missionPaceForwardsForActiveMarketDespiteSourceSwitchOffsetBarrier() throws Exception {
        FeedGatewayService service = service();
        GatewaySettings settings = new GatewaySettings();
        setActiveSelection(service, "DATABENTO", "SPX", "20260612");
        // A source-switch offset barrier sits ABOVE the record offset — this is the production
        // condition that was silently dropping every fresh mission-pace frame (it is a low-frequency
        // per-market signal, so its offset stays "below" the barrier captured at the last switch).
        setOffsetBarrier(service, settings.databentoPaceMissionTopic(), 0, 100L);

        String payload = "{\"eventType\":\"mission-pace\",\"symbol\":\"SPX\",\"expiry\":\"20260612\","
                + "\"spot\":6004.8,\"timestampMs\":1,\"rankedStrikes\":[{\"strike\":6005,\"missionPaceScore\":91.4}]}";
        Object binding = topicBinding("DATABENTO", "mission-pace");
        ConsumerRecord<String, String> record = new ConsumerRecord<>(
                settings.databentoPaceMissionTopic(), 0, 12L, "SPX|20260612", payload); // offset 12 < barrier 100

        // Per-market signal must forward for the active market despite the per-strike offset barrier.
        assertTrue(shouldForward(service, binding, payload, record),
                "mission-pace for the active market must forward despite the source-switch offset barrier");

        // Cross-market safety: a frame for a DIFFERENT expiry must NOT leak to the active selection.
        String otherMarket = payload.replace("20260612", "20260613");
        ConsumerRecord<String, String> otherRecord = new ConsumerRecord<>(
                settings.databentoPaceMissionTopic(), 0, 13L, "SPX|20260613", otherMarket);
        assertFalse(shouldForward(service, binding, otherMarket, otherRecord),
                "mission-pace for a different market must not leak to the active selection");
    }

    @Test
    void missionSandwichForwardsForActiveMarketDespiteSourceSwitchOffsetBarrier() throws Exception {
        FeedGatewayService service = service();
        GatewaySettings settings = new GatewaySettings();
        setActiveSelection(service, "DATABENTO", "SPX", "20260612");
        // Same production condition that silently dropped every fresh mission-sandwich frame: it is a
        // low-frequency per-market signal (symbol|expiry), so its offset stays "below" the source-switch
        // barrier captured at the last switch. Without the shouldForward special-case it is dropped as
        // inactiveDropped/sourceStale and the option-chain never renders the sandwich.
        setOffsetBarrier(service, settings.databentoMissionSandwichTopic(), 0, 100L);

        String payload = "{\"eventType\":\"mission-sandwich\",\"source\":\"DATABENTO\",\"symbol\":\"SPX\","
                + "\"expiry\":\"20260612\",\"spot\":6004.8,\"timestampMs\":1,"
                + "\"callSandwich\":{\"side\":\"CALL\",\"tilt\":\"UPPER_HEAVY\",\"lowerStrike\":6000.0,"
                + "\"midStrike\":6005.0,\"upperStrike\":6010.0,\"lowerVolume\":100,\"upperVolume\":200,\"wallVolume\":300}}";
        Object binding = topicBinding("DATABENTO", "mission-sandwich");
        ConsumerRecord<String, String> record = new ConsumerRecord<>(
                settings.databentoMissionSandwichTopic(), 0, 12L, "SPX|20260612", payload); // offset 12 < barrier 100

        // Per-market signal must forward for the active market despite the per-strike offset barrier.
        assertTrue(shouldForward(service, binding, payload, record),
                "mission-sandwich for the active market must forward despite the source-switch offset barrier");

        // Cross-market safety: a frame for a DIFFERENT expiry must NOT leak to the active selection.
        String otherMarket = payload.replace("20260612", "20260613");
        ConsumerRecord<String, String> otherRecord = new ConsumerRecord<>(
                settings.databentoMissionSandwichTopic(), 0, 13L, "SPX|20260613", otherMarket);
        assertFalse(shouldForward(service, binding, otherMarket, otherRecord),
                "mission-sandwich for a different market must not leak to the active selection");
    }

    @Test
    void cachedMissionPaceReplayBypassesOffsetBarrierButKeepsTimeBarrier() throws Exception {
        FeedGatewayService service = service();
        GatewaySettings settings = new GatewaySettings();
        setActiveSelection(service, "DATABENTO", "SPX", "20260612");
        setOffsetBarrier(service, settings.databentoPaceMissionTopic(), 0, 100L);
        String payload = "{\"eventType\":\"mission-pace\",\"symbol\":\"SPX\",\"expiry\":\"20260612\","
                + "\"spot\":6004.8,\"timestampMs\":1,\"rankedStrikes\":[]}";
        Object binding = topicBinding("DATABENTO", "mission-pace");
        // No-timestamp record -> cacheTimestamp falls back to now, so the cached entry is FRESH.
        ConsumerRecord<String, String> record = new ConsumerRecord<>(
                settings.databentoPaceMissionTopic(), 0, 12L, "SPX|20260612", payload); // offset 12 < barrier 100
        updateCache(service, binding, record, payload);

        // Fresh + offset barrier above the record offset -> still replayed (offset bypassed).
        assertEquals(1, cachedEventCount(service, "mission-pace", System.currentTimeMillis()),
                "fresh cached mission-pace must replay on connect despite the offset barrier");

        // Older than maxStaleMs -> excluded (the time barrier is still enforced).
        ageCacheEventTimes(service, "mission-pace:", System.currentTimeMillis() - 60_000L);
        assertEquals(0, cachedEventCount(service, "mission-pace", System.currentTimeMillis()),
                "stale cached mission-pace must NOT replay (time barrier enforced)");
    }

    @Test
    void cachedReplayOnConnectIncludesMissionPaceForMatchingDatabentoSelection() throws Exception {
        FeedGatewayService service = service();
        GatewaySettings settings = new GatewaySettings();
        long now = System.currentTimeMillis();
        String payload = "{\"eventType\":\"mission-pace\",\"symbol\":\"SPX\",\"expiry\":\"20260612\","
                + "\"spot\":6004.8,\"timestampMs\":1,\"rankedStrikes\":[{\"strike\":6005,\"missionPaceScore\":91.4}]}";
        updateCache(service, topicBinding("DATABENTO", "mission-pace"),
                recordAt(settings.databentoPaceMissionTopic(), 0, 1L, "SPX|20260612", payload, now), payload);

        setActiveSelection(service, "DATABENTO", "SPX", "20260612");
        assertEquals(1, cachedEvents(service, List.of("mission-pace"), now).size(),
                "cached mission-pace must replay to a freshly-connected DATABENTO client");

        // Cached source-switch replay must include mission-pace in its event list.
        assertTrue(FeedGatewayService.sourceSwitchReplayEvents().contains("mission-pace"));
    }

    @Test
    void missionControlGatewayContractConsumesCachesAndExposesUiBatchHealthAndMetrics() throws Exception {
        FeedGatewayService service = service();
        GatewaySettings settings = new GatewaySettings();
        String source = Files.readString(Path.of("src/main/java/app/feedgateway/FeedGatewayService.java"));
        String payload = "{\"eventType\":\"mission-control\",\"symbol\":\"SPX\",\"expiry\":\"20260612\","
                + "\"spot\":6004.8,\"timestampMs\":1,\"rankedStrikes\":[{\"strike\":6005,\"missionControlScore\":91.4}]}";
        Object binding = topicBinding("DATABENTO", "mission-control");
        ConsumerRecord<String, String> record = new ConsumerRecord<>(
                settings.missionControlTopic(),
                0,
                12L,
                "SPX|20260612",
                payload
        );

        String cacheKey = updateCache(service, binding, record, payload);
        String eventEnvelope = envelopeJson(service, "mission-control", payload);
        String batchEnvelope = uiBatchEnvelopeJsonMissionControl(service, List.of(payload));

        // Default topic resolves to the mission-control topic and binds on the JSON/state path.
        assertEquals("options.spx.mission-control.current", settings.missionControlTopic());
        assertTrue(source.contains("topicEvents.put(settings.missionControlTopic(), new TopicBinding(\"DATABENTO\", \"mission-control\"));"));
        // Cache key is symbol|expiry (per-market, no strike — like max-pain). updateCache prepends the
        // source, so the full slot is source|symbol|expiry.
        assertEquals("DATABENTO|SPX|20260612", cacheKey);
        assertTrue(eventEnvelope.contains("\"type\":\"mission-control\""));
        assertTrue(batchEnvelope.contains("\"missionControls\":[{\"eventType\":\"mission-control\""));
        assertTrue(service.healthJson().contains("\"missionControls\":1"));
        assertTrue(service.metrics().contains("options_edge_feed_gateway_mission_controls 1"));
    }

    @Test
    void missionControlForwardsForActiveMarketDespiteSourceSwitchOffsetBarrier() throws Exception {
        FeedGatewayService service = service();
        GatewaySettings settings = new GatewaySettings();
        setActiveSelection(service, "DATABENTO", "SPX", "20260612");
        // A source-switch offset barrier sits ABOVE the record offset — this is the production
        // condition that was silently dropping every fresh mission-control frame (it is a low-frequency
        // per-market signal, so its offset stays "below" the barrier captured at the last switch).
        setOffsetBarrier(service, settings.missionControlTopic(), 0, 100L);

        String payload = "{\"eventType\":\"mission-control\",\"symbol\":\"SPX\",\"expiry\":\"20260612\","
                + "\"spot\":6004.8,\"timestampMs\":1,\"rankedStrikes\":[{\"strike\":6005,\"missionControlScore\":91.4}]}";
        Object binding = topicBinding("DATABENTO", "mission-control");
        ConsumerRecord<String, String> record = new ConsumerRecord<>(
                settings.missionControlTopic(), 0, 12L, "SPX|20260612", payload); // offset 12 < barrier 100

        // Per-market signal must forward for the active market despite the per-strike offset barrier.
        assertTrue(shouldForward(service, binding, payload, record),
                "mission-control for the active market must forward despite the source-switch offset barrier");

        // Cross-market safety: a frame for a DIFFERENT expiry must NOT leak to the active selection.
        String otherMarket = payload.replace("20260612", "20260613");
        ConsumerRecord<String, String> otherRecord = new ConsumerRecord<>(
                settings.missionControlTopic(), 0, 13L, "SPX|20260613", otherMarket);
        assertFalse(shouldForward(service, binding, otherMarket, otherRecord),
                "mission-control for a different market must not leak to the active selection");
    }

    @Test
    void cachedMissionControlReplayBypassesOffsetBarrierButKeepsTimeBarrier() throws Exception {
        FeedGatewayService service = service();
        GatewaySettings settings = new GatewaySettings();
        setActiveSelection(service, "DATABENTO", "SPX", "20260612");
        setOffsetBarrier(service, settings.missionControlTopic(), 0, 100L);
        String payload = "{\"eventType\":\"mission-control\",\"symbol\":\"SPX\",\"expiry\":\"20260612\","
                + "\"spot\":6004.8,\"timestampMs\":1,\"rankedStrikes\":[]}";
        Object binding = topicBinding("DATABENTO", "mission-control");
        // No-timestamp record -> cacheTimestamp falls back to now, so the cached entry is FRESH.
        ConsumerRecord<String, String> record = new ConsumerRecord<>(
                settings.missionControlTopic(), 0, 12L, "SPX|20260612", payload); // offset 12 < barrier 100
        updateCache(service, binding, record, payload);

        // Fresh + offset barrier above the record offset -> still replayed (offset bypassed).
        assertEquals(1, cachedEventCount(service, "mission-control", System.currentTimeMillis()),
                "fresh cached mission-control must replay on connect despite the offset barrier");

        // Older than maxStaleMs -> excluded (the time barrier is still enforced).
        ageCacheEventTimes(service, "mission-control:", System.currentTimeMillis() - 60_000L);
        assertEquals(0, cachedEventCount(service, "mission-control", System.currentTimeMillis()),
                "stale cached mission-control must NOT replay (time barrier enforced)");
    }

    @Test
    void cachedReplayOnConnectIncludesMissionControlForMatchingDatabentoSelection() throws Exception {
        FeedGatewayService service = service();
        GatewaySettings settings = new GatewaySettings();
        long now = System.currentTimeMillis();
        String payload = "{\"eventType\":\"mission-control\",\"symbol\":\"SPX\",\"expiry\":\"20260612\","
                + "\"spot\":6004.8,\"timestampMs\":1,\"rankedStrikes\":[{\"strike\":6005,\"missionControlScore\":91.4}]}";
        updateCache(service, topicBinding("DATABENTO", "mission-control"),
                recordAt(settings.missionControlTopic(), 0, 1L, "SPX|20260612", payload, now), payload);

        setActiveSelection(service, "DATABENTO", "SPX", "20260612");
        assertEquals(1, cachedEvents(service, List.of("mission-control"), now).size(),
                "cached mission-control must replay to a freshly-connected DATABENTO client");

        // Cached source-switch replay must include mission-control in its event list.
        assertTrue(FeedGatewayService.sourceSwitchReplayEvents().contains("mission-control"));
    }

    // ----- spread-skew gateway consumer (whole-underlying SpreadSkewSnapshot, mission-control mirror) -----

    /** The spread-skew snapshot payload per the producer contract: underlying + nullable expiry, ts = event time. */
    private static String spreadSkewPayload(long ts) {
        return "{\"schemaVersion\":1,\"ts\":" + ts + ",\"runId\":\"r-1\",\"sessionDate\":\"2026-07-11\","
                + "\"underlying\":\"SPX\",\"expiry\":\"2026-07-11\",\"spot\":6004.8,\"anchor\":6005.0,"
                + "\"degraded\":false,\"lateSession\":false,\"eventDay\":false,"
                + "\"headline\":{\"state\":\"CALL_SKEW\",\"z\":2.4,\"conflict\":false,"
                + "\"baselineSessionsMin\":5,\"baselineRequired\":10},"
                + "\"participatingOffsets\":[10,15,20],\"levels\":[]}";
    }

    @Test
    void spreadSkewGatewayContractConsumesCachesAndExposesUiBatchHealthAndMetrics() throws Exception {
        FeedGatewayService service = service();
        GatewaySettings settings = new GatewaySettings();
        String source = Files.readString(Path.of("src/main/java/app/feedgateway/FeedGatewayService.java"));
        String payload = spreadSkewPayload(System.currentTimeMillis());
        Object binding = topicBinding("DATABENTO", "spread-skew");
        ConsumerRecord<String, String> record = new ConsumerRecord<>(
                settings.spreadSkewTopic(),
                0,
                12L,
                "SPX",
                payload
        );

        String cacheKey = updateCache(service, binding, record, payload);
        String eventEnvelope = envelopeJson(service, "spread-skew", payload);
        String batchEnvelope = uiBatchEnvelopeJsonSpreadSkew(service, List.of(payload));

        // Default topic resolves to the spread-skew topic and binds on the JSON/state path.
        assertEquals("options.spx.spread-skew.current", settings.spreadSkewTopic());
        assertTrue(source.contains("topicEvents.put(settings.spreadSkewTopic(), new TopicBinding(\"DATABENTO\", \"spread-skew\"));"));
        // SINGLE-VALUE cache keyed by the underlying alone (no expiry segment — one snapshot covers the
        // whole underlying). updateCache prepends the source, so the full slot is source|underlying.
        assertEquals("DATABENTO|SPX", cacheKey);
        assertTrue(eventEnvelope.contains("\"type\":\"spread-skew\""));
        assertTrue(batchEnvelope.contains("\"spreadSkews\":[{\"schemaVersion\""));
        assertTrue(service.healthJson().contains("\"spreadSkews\":1"));
        assertTrue(service.metrics().contains("options_edge_feed_gateway_spread_skews 1"));
    }

    @Test
    void spreadSkewCacheIsSingleValueLastSnapshotWins() throws Exception {
        FeedGatewayService service = service();
        GatewaySettings settings = new GatewaySettings();
        long now = System.currentTimeMillis();
        Object binding = topicBinding("DATABENTO", "spread-skew");
        updateCache(service, binding,
                recordAt(settings.spreadSkewTopic(), 0, 1L, "SPX", spreadSkewPayload(now - 5_000), now - 5_000),
                spreadSkewPayload(now - 5_000));
        updateCache(service, binding,
                recordAt(settings.spreadSkewTopic(), 0, 2L, "SPX", spreadSkewPayload(now), now),
                spreadSkewPayload(now));
        // Both records collapse into ONE source|underlying slot — the second (newer ts) wins.
        assertTrue(service.healthJson().contains("\"spreadSkews\":1"),
                "spread-skew must be a single-value cache (last snapshot wins)");
        // An out-of-order OLDER ts must be rejected by the monotonic event-time gate.
        assertNull(updateCache(service, binding,
                recordAt(settings.spreadSkewTopic(), 0, 3L, "SPX", spreadSkewPayload(now - 10_000), now),
                spreadSkewPayload(now - 10_000)));
    }

    @Test
    void spreadSkewForwardsForActiveMarketDespiteSourceSwitchOffsetBarrier() throws Exception {
        FeedGatewayService service = service();
        GatewaySettings settings = new GatewaySettings();
        setActiveSelection(service, "DATABENTO", "SPX", "20260711");
        // A source-switch offset barrier sits ABOVE the record offset — like mission-control, the
        // low-frequency spread-skew frame's offset stays "below" the barrier captured at the last switch.
        setOffsetBarrier(service, settings.spreadSkewTopic(), 0, 100L);

        long now = System.currentTimeMillis();
        String payload = spreadSkewPayload(now);
        Object binding = topicBinding("DATABENTO", "spread-skew");
        ConsumerRecord<String, String> record =
                recordAt(settings.spreadSkewTopic(), 0, 12L, "SPX", payload, now); // offset 12 < barrier 100

        // Per-market signal must forward for the active market despite the per-strike offset barrier.
        assertTrue(shouldForward(service, binding, payload, record),
                "spread-skew for the active market must forward despite the source-switch offset barrier");

        // Payload-time freshness: a STALE ts must NOT forward even on a fresh Kafka arrival time.
        String stale = spreadSkewPayload(now - 60_000);
        ConsumerRecord<String, String> staleRecord =
                recordAt(settings.spreadSkewTopic(), 0, 13L, "SPX", stale, now); // arrival fresh, ts stale
        assertFalse(shouldForward(service, binding, stale, staleRecord),
                "spread-skew freshness must track the payload ts, not the Kafka arrival time");

        // Cross-market safety: a frame for a DIFFERENT expiry must NOT leak to the active selection.
        String otherMarket = payload.replace("\"expiry\":\"2026-07-11\"", "\"expiry\":\"2026-07-12\"");
        ConsumerRecord<String, String> otherRecord =
                recordAt(settings.spreadSkewTopic(), 0, 14L, "SPX", otherMarket, now);
        assertFalse(shouldForward(service, binding, otherMarket, otherRecord),
                "spread-skew for a different market must not leak to the active selection");

        // A NULL expiry (producer cannot resolve the 0DTE chain) still covers the active session.
        String nullExpiry = payload.replace("\"expiry\":\"2026-07-11\"", "\"expiry\":null");
        ConsumerRecord<String, String> nullExpiryRecord =
                recordAt(settings.spreadSkewTopic(), 0, 15L, "SPX", nullExpiry, now);
        assertTrue(shouldForward(service, binding, nullExpiry, nullExpiryRecord),
                "a null-expiry spread-skew frame must still reach the active selection");
    }

    @Test
    void spreadSkewMissingOrInvalidTsFailsClosedAndIsNotRescuedByFreshKafkaArrival() throws Exception {
        // eventCacheTimestamp for spread-skew has deliberately NO Kafka-arrival fallback: a snapshot
        // whose ts is missing, non-numeric or negative must fail closed (never cached, never
        // forwarded) even when the record's Kafka timestamp is brand new.
        FeedGatewayService service = service();
        GatewaySettings settings = new GatewaySettings();
        setActiveSelection(service, "DATABENTO", "SPX", "20260711");
        long now = System.currentTimeMillis();
        Object binding = topicBinding("DATABENTO", "spread-skew");
        String fresh = spreadSkewPayload(now);
        List<String> malformed = List.of(
                fresh.replace("\"ts\":" + now + ",", ""),                  // ts missing entirely
                fresh.replace("\"ts\":" + now, "\"ts\":\"not-a-number\""), // non-numeric ts
                fresh.replace("\"ts\":" + now, "\"ts\":-5"));              // negative ts
        long offset = 12L;
        for (String payload : malformed) {
            ConsumerRecord<String, String> record =
                    recordAt(settings.spreadSkewTopic(), 0, offset++, "SPX", payload, now); // arrival FRESH
            assertTrue(eventCacheTimestamp(service, "spread-skew", record) < 0,
                    "missing/invalid ts must fail closed, not fall back to the Kafka arrival time");
            assertNull(updateCache(service, binding, record, payload),
                    "a snapshot without a valid ts must never be cached");
            assertFalse(shouldForward(service, binding, payload, record),
                    "a snapshot without a valid ts must never forward");
        }
        assertEquals(0, cachedEventCount(service, "spread-skew", now),
                "no malformed snapshot may end up replayable");
    }

    @Test
    void cachedSpreadSkewReplayBypassesOffsetBarrierButKeepsTimeBarrier() throws Exception {
        FeedGatewayService service = service();
        GatewaySettings settings = new GatewaySettings();
        setActiveSelection(service, "DATABENTO", "SPX", "20260711");
        setOffsetBarrier(service, settings.spreadSkewTopic(), 0, 100L);
        String payload = spreadSkewPayload(System.currentTimeMillis());
        Object binding = topicBinding("DATABENTO", "spread-skew");
        // No-timestamp record -> the payload ts (fresh) is the cache event time.
        ConsumerRecord<String, String> record = new ConsumerRecord<>(
                settings.spreadSkewTopic(), 0, 12L, "SPX", payload); // offset 12 < barrier 100
        updateCache(service, binding, record, payload);

        // Fresh + offset barrier above the record offset -> still replayed (offset bypassed).
        assertEquals(1, cachedEventCount(service, "spread-skew", System.currentTimeMillis()),
                "fresh cached spread-skew must replay on connect despite the offset barrier");

        // Older than maxStaleMs -> excluded (the time barrier is still enforced).
        ageCacheEventTimes(service, "spread-skew:", System.currentTimeMillis() - 60_000L);
        assertEquals(0, cachedEventCount(service, "spread-skew", System.currentTimeMillis()),
                "stale cached spread-skew must NOT replay (time barrier enforced)");
    }

    @Test
    void cachedReplayOnConnectIncludesSpreadSkewForMatchingDatabentoSelection() throws Exception {
        FeedGatewayService service = service();
        GatewaySettings settings = new GatewaySettings();
        long now = System.currentTimeMillis();
        String payload = spreadSkewPayload(now);
        updateCache(service, topicBinding("DATABENTO", "spread-skew"),
                recordAt(settings.spreadSkewTopic(), 0, 1L, "SPX", payload, now), payload);

        setActiveSelection(service, "DATABENTO", "SPX", "20260711");
        assertEquals(1, cachedEvents(service, List.of("spread-skew"), now).size(),
                "cached spread-skew must replay to a freshly-connected DATABENTO client");

        // Wrong source (IBKR) is filtered (spread-skew is DATABENTO-only).
        setActiveSelection(service, "IBKR", "SPX", "20260711");
        assertTrue(cachedEvents(service, List.of("spread-skew"), now).isEmpty(),
                "IBKR selection must never receive DATABENTO spread-skew");

        // Wrong symbol is filtered by the underlying match.
        setActiveSelection(service, "DATABENTO", "SPY", "20260711");
        assertTrue(cachedEvents(service, List.of("spread-skew"), now).isEmpty(),
                "a different symbol must not receive this spread-skew");

        // Cached source-switch replay must include spread-skew in its event list.
        assertTrue(FeedGatewayService.sourceSwitchReplayEvents().contains("spread-skew"));
    }

    @Test
    void spreadSkewEventIsBroadcastStandaloneAndNeverCachedLikeTurnAlert() throws Exception {
        FeedGatewayService service = service();
        GatewaySettings settings = new GatewaySettings();
        String source = Files.readString(Path.of("src/main/java/app/feedgateway/FeedGatewayService.java"));

        // Default topic resolves to the spread-skew events topic and binds on BOTH JSON consumers
        // (cache + live kept symmetric), as its own standalone event type.
        assertEquals("options.spx.spread-skew.events", settings.spreadSkewEventsTopic());
        for (String method : List.of("runJsonStateCacheConsumer", "runJsonStateLiveConsumer")) {
            assertTrue(methodBody(source, method).contains(
                    "topicEvents.put(settings.spreadSkewEventsTopic(), new TopicBinding(\"DATABENTO\", \"spread-skew-event\"));"),
                    method + " must bind the spread-skew events topic (JSON)");
        }
        // The live consumer broadcasts it STANDALONE via the early dedicated branch (before the
        // cache/selection machinery), exactly like turn-alert.
        assertTrue(source.contains("if (\"spread-skew-event\".equals(binding.event())) {"),
                "spread-skew-event needs the early standalone-broadcast branch (turn-alert mirror)");

        // Behavioral: the broadcast reaches a connected client as its own message.type...
        List<String> sent = new ArrayList<>();
        addRecordingClient(service, sent);
        String payload = spreadSkewPayload(System.currentTimeMillis())
                .replace("\"participatingOffsets\"",
                        "\"eventId\":\"e-1\",\"transitionType\":\"FIRE\",\"previousState\":\"NEUTRAL\","
                                + "\"newState\":\"CALL_SKEW\",\"alertEligible\":true,\"alertSuppressedReason\":null,"
                                + "\"participatingOffsets\"");
        broadcast(service, "spread-skew-event", payload);
        assertTrue(sent.stream().anyMatch(m -> m.contains("\"type\":\"spread-skew-event\"")),
                "spread-skew-event must reach connected clients standalone");
        // ...and is never cached or replayed: no cache slot, and not in the source-switch replay list.
        assertEquals(0, cachedEventCount(service, "spread-skew-event", System.currentTimeMillis()));
        assertFalse(FeedGatewayService.sourceSwitchReplayEvents().contains("spread-skew-event"));
    }

    @Test
    void databentoGexGatewayContractConsumesCachesAndExposesUiBatchHealthAndMetrics() throws Exception {
        FeedGatewayService service = service();
        GatewaySettings settings = new GatewaySettings();
        String source = Files.readString(Path.of("src/main/java/app/feedgateway/FeedGatewayService.java"));
        String payload = "{\"source\":\"DATABENTO\",\"symbol\":\"SPX\",\"expiry\":\"20260612\","
                + "\"strike\":6005,\"callGex\":1.0,\"putGex\":-2.0,\"netGex\":-1.0,"
                + "\"gammaSign\":\"NEGATIVE\",\"updatedAt\":\"2026-06-12T14:31:00Z\"}";
        Object binding = topicBinding("DATABENTO", "gex-by-strike");
        ConsumerRecord<String, String> record = new ConsumerRecord<>(
                settings.databentoGexTopic(),
                0,
                12L,
                "SPX|20260612|6005",
                payload
        );

        String cacheKey = updateCache(service, binding, record, payload);
        String eventEnvelope = envelopeJson(service, "gex-by-strike", payload);
        String batchEnvelope = uiBatchEnvelopeJsonGex(service, List.of(payload));

        assertEquals("options.databento.gex.strike", settings.databentoGexTopic());
        assertTrue(source.contains("topicEvents.put(settings.databentoGexTopic(), new TopicBinding(\"DATABENTO\", \"gex-by-strike\"));"));
        assertEquals("DATABENTO|SPX|20260612|6005", cacheKey);
        assertTrue(eventEnvelope.contains("\"type\":\"gex-by-strike\""));
        assertTrue(batchEnvelope.contains("\"gexByStrike\":[{"));
        assertTrue(service.healthJson().contains("\"gexByStrike\":1"));
        assertTrue(service.metrics().contains("options_edge_feed_gateway_gex_by_strike 1"));
    }

    @Test
    void databentoGexPassesShouldForwardForActiveDatabentoSelection() throws Exception {
        FeedGatewayService service = service();
        GatewaySettings settings = new GatewaySettings();
        setActiveSelection(service, "DATABENTO", "SPX", "20260612");
        String payload = "{\"source\":\"DATABENTO\",\"symbol\":\"SPX\",\"expiry\":\"20260612\",\"strike\":6005,\"netGex\":-1.0}";
        Object binding = topicBinding("DATABENTO", "gex-by-strike");
        ConsumerRecord<String, String> record = new ConsumerRecord<>(
                settings.databentoGexTopic(), 0, 12L, "SPX|20260612|6005", payload);

        assertTrue(shouldForward(service, binding, payload, record));
    }

    @Test
    void gexByStrikeIsIsolatedBetweenIbkrAndDatabento() throws Exception {
        FeedGatewayService service = service();
        GatewaySettings settings = new GatewaySettings();
        String payload = "{\"source\":\"DATABENTO\",\"symbol\":\"SPX\",\"expiry\":\"20260612\",\"strike\":6005,\"netGex\":-1.0}";
        ConsumerRecord<String, String> record = new ConsumerRecord<>(
                settings.databentoGexTopic(), 0, 12L, "SPX|20260612|6005", payload);

        // Active source DATABENTO must not forward an IBKR-bound GEX record...
        setActiveSelection(service, "DATABENTO", "SPX", "20260612");
        assertFalse(shouldForward(service, topicBinding("IBKR", "gex-by-strike"), payload, record));

        // ...and active source IBKR must not forward a DATABENTO-bound GEX record.
        setActiveSelection(service, "IBKR", "SPX", "20260612");
        assertFalse(shouldForward(service, topicBinding("DATABENTO", "gex-by-strike"), payload, record));
    }

    // ---- DATABENTO gex + max-pain are Avro on the wire: must be consumed via the Avro path, not JSON ----

    @Test
    void databentoGexAndMaxPainAreClassifiedAsAvroNotJsonAcrossCacheLiveAndReplay() throws Exception {
        String source = Files.readString(Path.of("src/main/java/app/feedgateway/FeedGatewayService.java"));
        String gexBinding = "topicEvents.put(settings.databentoGexTopic(), new TopicBinding(\"DATABENTO\", \"gex-by-strike\"));";
        String maxPainBinding = "topicEvents.put(settings.databentoMaxPainTopic(), new TopicBinding(\"DATABENTO\", \"max-pain\"));";

        // Avro CACHE + LIVE consumers MUST bind both DATABENTO gex and max-pain (Avro deserialization).
        for (String method : List.of("runAvroCacheConsumer", "runAvroLiveConsumer")) {
            String body = methodBody(source, method);
            assertTrue(body.contains(gexBinding), method + " must bind DATABENTO gex (Avro)");
            assertTrue(body.contains(maxPainBinding), method + " must bind DATABENTO max-pain (Avro)");
        }
        // JSON/string consumers MUST NOT bind them (reading Avro as JSON garbles the value), but must keep
        // the genuinely-JSON strike-flow.
        for (String method : List.of("runJsonStateCacheConsumer", "runJsonStateLiveConsumer")) {
            String body = methodBody(source, method);
            assertFalse(body.contains(gexBinding), method + " must NOT bind DATABENTO gex on the JSON consumer");
            assertFalse(body.contains(maxPainBinding), method + " must NOT bind DATABENTO max-pain on the JSON consumer");
            assertTrue(body.contains("databentoStrikeFlowTopic()"), method + " keeps the JSON strike-flow binding");
        }
        // Replay classification must match: DATABENTO gex + max-pain in avroTopics, NOT stringTopics.
        assertTrue(source.contains("avroTopics.put(settings.databentoGexTopic(), \"gex-by-strike\");"));
        assertTrue(source.contains("avroTopics.put(settings.databentoMaxPainTopic(), \"max-pain\");"));
        assertFalse(source.contains("stringTopics.put(settings.databentoGexTopic(), \"gex-by-strike\");"));
        assertFalse(source.contains("stringTopics.put(settings.databentoMaxPainTopic(), \"max-pain\");"));
        // Mission-pace is genuinely JSON (String/JSON), like strike-flow: it must be in stringTopics, NOT Avro.
        assertTrue(source.contains("stringTopics.put(settings.databentoPaceMissionTopic(), \"mission-pace\");"));
        assertFalse(source.contains("avroTopics.put(settings.databentoPaceMissionTopic(), \"mission-pace\");"));
        // JSON/string CACHE + LIVE consumers must bind the mission-pace topic (it is JSON, not Avro).
        for (String method : List.of("runJsonStateCacheConsumer", "runJsonStateLiveConsumer")) {
            assertTrue(methodBody(source, method).contains(
                    "topicEvents.put(settings.databentoPaceMissionTopic(), new TopicBinding(\"DATABENTO\", \"mission-pace\"));"),
                    method + " must bind the DATABENTO mission-pace topic (JSON)");
        }
        for (String method : List.of("runAvroCacheConsumer", "runAvroLiveConsumer")) {
            assertFalse(methodBody(source, method).contains(
                    "topicEvents.put(settings.databentoPaceMissionTopic(), new TopicBinding(\"DATABENTO\", \"mission-pace\"));"),
                    method + " must NOT bind the DATABENTO mission-pace topic (it is JSON, not Avro)");
        }
        // Mission-control is genuinely JSON (String/JSON), like strike-flow: it must be in stringTopics, NOT Avro.
        assertTrue(source.contains("stringTopics.put(settings.missionControlTopic(), \"mission-control\");"));
        assertFalse(source.contains("avroTopics.put(settings.missionControlTopic(), \"mission-control\");"));
        // JSON/string CACHE + LIVE consumers must bind the mission-control topic (it is JSON, not Avro).
        for (String method : List.of("runJsonStateCacheConsumer", "runJsonStateLiveConsumer")) {
            assertTrue(methodBody(source, method).contains(
                    "topicEvents.put(settings.missionControlTopic(), new TopicBinding(\"DATABENTO\", \"mission-control\"));"),
                    method + " must bind the DATABENTO mission-control topic (JSON)");
        }
        for (String method : List.of("runAvroCacheConsumer", "runAvroLiveConsumer")) {
            assertFalse(methodBody(source, method).contains(
                    "topicEvents.put(settings.missionControlTopic(), new TopicBinding(\"DATABENTO\", \"mission-control\"));"),
                    method + " must NOT bind the DATABENTO mission-control topic (it is JSON, not Avro)");
        }
        // Spread-skew is genuinely JSON (String/JSON), like mission-control: it must be in stringTopics, NOT Avro.
        assertTrue(source.contains("stringTopics.put(settings.spreadSkewTopic(), \"spread-skew\");"));
        assertFalse(source.contains("avroTopics.put(settings.spreadSkewTopic(), \"spread-skew\");"));
        // JSON/string CACHE + LIVE consumers must bind the spread-skew topic (it is JSON, not Avro).
        for (String method : List.of("runJsonStateCacheConsumer", "runJsonStateLiveConsumer")) {
            assertTrue(methodBody(source, method).contains(
                    "topicEvents.put(settings.spreadSkewTopic(), new TopicBinding(\"DATABENTO\", \"spread-skew\"));"),
                    method + " must bind the DATABENTO spread-skew topic (JSON)");
        }
        for (String method : List.of("runAvroCacheConsumer", "runAvroLiveConsumer")) {
            assertFalse(methodBody(source, method).contains("spread-skew"),
                    method + " must NOT bind spread-skew (it is JSON, not Avro)");
        }
        // Unified S/R (strike-sr) is DATABENTO-only Avro: bound in the Avro consumers + avroTopics,
        // and NEVER in the JSON consumers.
        assertTrue(source.contains("avroTopics.put(settings.unifiedSrTopic(), \"strike-sr\");"));
        for (String method : List.of("runAvroCacheConsumer", "runAvroLiveConsumer")) {
            assertTrue(methodBody(source, method).contains(
                    "topicEvents.put(settings.unifiedSrTopic(), new TopicBinding(\"DATABENTO\", \"strike-sr\"));"),
                    method + " must bind the unified S/R topic (Avro)");
        }
        for (String method : List.of("runJsonStateCacheConsumer", "runJsonStateLiveConsumer")) {
            assertFalse(methodBody(source, method).contains("strike-sr"),
                    method + " must NOT bind the unified S/R topic (it is Avro, not JSON)");
        }
        // strike-invasion is genuinely JSON (StrikeInvasionSnapshot), like strike-intel: it must be a
        // stringTopic in windowed replay (NOT Avro), and bound on the JSON consumers only.
        assertTrue(source.contains("stringTopics.put(settings.strikeInvasionTopic(), \"strike-invasion\");"),
                "windowed replay must consume strike-invasion as a JSON stringTopic");
        assertFalse(source.contains("avroTopics.put(settings.strikeInvasionTopic(), \"strike-invasion\");"),
                "strike-invasion must never be read via the Avro path");
        // Run-scoped (orchestrated) replay must DROP it (not in the per-run replicator contract), mirroring
        // strike-intel / delta-flow / liquidity-heatmap.
        assertTrue(source.contains("stringTopics.remove(settings.strikeInvasionTopic());"),
                "run-scoped replay must exclude strike-invasion (no replay.<runId> topic for it)");
        for (String method : List.of("runJsonStateCacheConsumer", "runJsonStateLiveConsumer")) {
            assertTrue(methodBody(source, method).contains(
                    "topicEvents.put(settings.strikeInvasionTopic(), new TopicBinding(\"DATABENTO\", \"strike-invasion\"));"),
                    method + " must bind the DATABENTO strike-invasion topic (JSON)");
        }
        for (String method : List.of("runAvroCacheConsumer", "runAvroLiveConsumer")) {
            assertFalse(methodBody(source, method).contains("strike-invasion"),
                    method + " must NOT bind strike-invasion (it is JSON, not Avro)");
        }
        // Legacy caught-up gating: max-pain (DATABENTO-only Avro) under avroCaughtUp; gex-by-strike
        // (multi-source) under BOTH flags.
        assertTrue(source.contains(
                "sendCachedState(session, List.of(\"snapshot\", \"pace\", \"pace-rank\", \"directional-pressure\", \"max-pain\", \"strike-sr\", \"gex-magnet\"));"));
        assertTrue(source.contains("if (avroCaughtUp.get() && stateCaughtUp.get()) {"));
        // gex legacy cached replay is source-aware (no hard IBKR-only filter).
        assertFalse(source.contains(".filter(entry -> \"IBKR\".equals(selection.source()))"));
        // The Avro consumer uses RecordNameStrategy for the record-name subjects these schemas register under.
        assertTrue(source.contains(
                "io.confluent.kafka.serializers.subject.RecordNameStrategy"));
    }

    @Test
    void avroMaxPainRecordIsDeserializedCachedAndDeliverable() throws Exception {
        // Behavioral coverage (Codex NIT): a real Avro GenericRecord for the max-pain schema must convert
        // to JSON (avroJson), cache under DATABENTO|symbol|expiry (maxPainCacheKey), and be a valid
        // routable/deliverable max-pain (status read by isMaxPainExpired). This is the path that was
        // silently dropped when max-pain was read as a String.
        org.apache.avro.Schema schema = org.apache.avro.SchemaBuilder.record("MaxPainSnapshot")
                .namespace("app.options.maxpain").fields()
                .name("messageType").type().stringType().noDefault()
                .name("source").type().stringType().noDefault()
                .name("symbol").type().stringType().noDefault()
                .name("expiry").type().stringType().noDefault()
                .name("status").type().stringType().noDefault()
                .name("maxPainStrike").type().doubleType().noDefault()
                .endRecord();
        org.apache.avro.generic.GenericRecord rec = new org.apache.avro.generic.GenericData.Record(schema);
        rec.put("messageType", "MAX_PAIN");
        rec.put("source", "DATABENTO");
        rec.put("symbol", "SPX");
        rec.put("expiry", "20260622");
        rec.put("status", "VALID");
        rec.put("maxPainStrike", 4500.0);

        FeedGatewayService service = service();
        String json = avroJson(service, rec);
        assertTrue(json.contains("\"maxPainStrike\":4500"), "avroJson must preserve maxPainStrike numerically");
        assertEquals("SPX|20260622", maxPainCacheKey(service, json, "fallback"),
                "avro->json max-pain must key by symbol|expiry");
        assertFalse(isMaxPainExpired(service, json), "VALID status must not be terminal");

        // And it caches + delivers via the same updateCache the Avro consumer uses (cacheKey non-null).
        String key = updateCache(service, topicBinding("DATABENTO", "max-pain"),
                new ConsumerRecord<>(new GatewaySettings().databentoMaxPainTopic(), 0, 1L, "SPX|20260622", json),
                json);
        assertEquals("DATABENTO|SPX|20260622", key);
        assertTrue(service.healthJson().contains("\"maxPain\":1"));
    }

    private static String avroJson(FeedGatewayService service, Object genericRecord) throws Exception {
        Method m = FeedGatewayService.class.getDeclaredMethod("avroJson", Object.class);
        m.setAccessible(true);
        return (String) m.invoke(service, genericRecord);
    }

    /** The body of a no-arg private method, from its signature to the start of the next private method. */
    private static String methodBody(String source, String methodName) {
        int start = source.indexOf("private void " + methodName + "()");
        if (start < 0) {
            throw new IllegalArgumentException("method not found: " + methodName);
        }
        int next = source.indexOf("\n    private ", start + 1);
        return next < 0 ? source.substring(start) : source.substring(start, next);
    }

    // ---- Max-pain last-value-wins: a slow daily-OI signal must not use the generic 15-min freshness ----

    @Test
    void maxPainTtlMsDefaultsTo12hAndIsOverridable() {
        assertEquals(43_200_000L, new GatewaySettings().maxPainTtlMs());
        withSystemProperty("GATEWAY_MAXPAIN_TTL_MS", "60000",
                () -> assertEquals(60_000L, new GatewaySettings().maxPainTtlMs()));
        // <= 0 is honored (preserves the "do not cache stale state" semantics, NOT infinite).
        withSystemProperty("GATEWAY_MAXPAIN_TTL_MS", "0",
                () -> assertEquals(0L, new GatewaySettings().maxPainTtlMs()));
    }

    @Test
    void optionChainFreshnessIsMarketAwareTenMinDuringRthNeverOffHours() throws Exception {
        FeedGatewayService service = service();
        GatewaySettings settings = new GatewaySettings();
        long now = 2_000_000_000_000L;
        long elevenMinAgo = now - 11L * 60_000L;
        long fiveMinAgo = now - 5L * 60_000L;

        java.util.Map<String, Object> displayTopic =
                java.util.Map.of(settings.databentoDisplayTopic(), topicBinding("DATABENTO", "snapshot"));
        org.apache.kafka.common.TopicPartition displayPart =
                new org.apache.kafka.common.TopicPartition(settings.databentoDisplayTopic(), 0);

        // DURING market hours: the structural chain (snapshot) uses the 10-min RTH TTL.
        overrideRth(service, true);
        assertTrue(isExpiredEvent(service, "snapshot", elevenMinAgo, now), "snapshot >10min expires in RTH");
        assertFalse(isExpiredEvent(service, "snapshot", fiveMinAgo, now), "snapshot <10min fresh in RTH");
        assertEquals(settings.optionChainRthCacheTtlMs(), windowTtlMsForAt(service, displayPart, displayTopic, now));

        // OFF market hours: the published chain is NEVER evicted (so strikes stay visible), with a bounded seek.
        overrideRth(service, false);
        assertFalse(isExpiredEvent(service, "snapshot", now - 25L * 3_600_000L, now), "off-hours never evicts");
        assertEquals(settings.optionChainOffHoursSeekBackMs(),
                windowTtlMsForAt(service, displayPart, displayTopic, now));

        // Fast order-flow signals are NOT market-aware: they keep the generic 15-min TTL, so an 11-min-old
        // strike-flow is NOT expired (a market-aware 10-min TTL would have expired it) — proving the scope.
        assertFalse(isExpiredEvent(service, "strike-flow", elevenMinAgo, now), "strike-flow uses generic 15min TTL");
        assertEquals(settings.cacheTtlMs(), windowTtlMsForAt(service,
                new org.apache.kafka.common.TopicPartition(settings.databentoStrikeFlowTopic(), 0),
                java.util.Map.of(settings.databentoStrikeFlowTopic(), topicBinding("DATABENTO", "strike-flow")), now));
    }

    @Test
    void offHoursStaleSnapshotStillReplaysToConnectingClientWhileFastSignalDoesNot() throws Exception {
        // The user-facing contract: off-hours a connecting client still gets the published strikes (snapshot),
        // even hours old; a stale fast-signal (strike-flow) does NOT replay (its freshness barrier stands).
        FeedGatewayService service = service();
        GatewaySettings settings = new GatewaySettings();
        setActiveSelection(service, "DATABENTO", "SPX", "20260623");
        overrideRth(service, false); // off market hours
        long now = System.currentTimeMillis();
        long fortyMinAgo = now - 40L * 60_000L;

        String snapJson = "{\"marketDataSource\":\"DATABENTO\",\"symbol\":\"SPX\",\"expiry\":\"20260623\",\"strike\":7000}";
        updateCache(service, topicBinding("DATABENTO", "snapshot"),
                recordAt(settings.databentoDisplayTopic(), 0, 1L, "SPX|20260623|7000", snapJson, fortyMinAgo), snapJson);
        // A 40-min-old fast strike-flow for the same selection: still cached but it must NOT replay (stale).
        String sfJson = "{\"marketDataSource\":\"DATABENTO\",\"symbol\":\"SPX\",\"expiry\":\"20260623\",\"strike\":7000,\"netFlow\":1.0}";
        updateCache(service, topicBinding("DATABENTO", "strike-flow"),
                recordAt(settings.databentoStrikeFlowTopic(), 0, 1L, "SPX|20260623|7000", sfJson, fortyMinAgo), sfJson);

        assertEquals(1, cachedEvents(service, List.of("snapshot"), now).size(),
                "a 40-min-old snapshot still replays off-hours (never evicted)");
        assertEquals(0, cachedEvents(service, List.of("strike-flow"), now).size(),
                "a 40-min-old fast strike-flow does NOT replay (generic TTL evicted it)");
    }

    @Test
    void isExpiredIsEventAwareForMaxPainVersusFast() throws Exception {
        FeedGatewayService service = service();
        overrideRth(service, true); // hold market hours fixed so the fast-event TTL is deterministic
        long now = 2_000_000_000_000L;
        long thirtyFiveMinAgo = now - 35L * 60_000L;
        // A 35-min-old record during RTH: the structural snapshot expires (10-min TTL); max-pain does not (12h).
        assertTrue(isExpiredEvent(service, "snapshot", thirtyFiveMinAgo, now));
        assertFalse(isExpiredEvent(service, "max-pain", thirtyFiveMinAgo, now));
        // Beyond the 12h max-pain window, even max-pain expires (bounded, not infinite).
        assertTrue(isExpiredEvent(service, "max-pain", now - 13L * 3_600_000L, now));
    }

    @Test
    void seekWindowTtlMapsMaxPainToLongWindowAndOthersToGeneric() throws Exception {
        FeedGatewayService service = service();
        overrideRth(service, true); // structural snapshot seek == RTH TTL while in market hours
        GatewaySettings settings = new GatewaySettings();
        long now = 2_000_000_000_000L;
        java.util.Map<String, Object> topicEvents = new java.util.HashMap<>();
        topicEvents.put(settings.databentoMaxPainTopic(), topicBinding("DATABENTO", "max-pain"));
        topicEvents.put(settings.databentoDisplayTopic(), topicBinding("DATABENTO", "snapshot"));
        topicEvents.put(settings.databentoStrikeFlowTopic(), topicBinding("DATABENTO", "strike-flow"));

        assertEquals(settings.maxPainTtlMs(), windowTtlMsForAt(service,
                new org.apache.kafka.common.TopicPartition(settings.databentoMaxPainTopic(), 0), topicEvents, now));
        // Structural snapshot is market-aware (RTH 10-min seek); fast strike-flow stays generic.
        assertEquals(settings.optionChainRthCacheTtlMs(), windowTtlMsForAt(service,
                new org.apache.kafka.common.TopicPartition(settings.databentoDisplayTopic(), 0), topicEvents, now));
        assertEquals(settings.cacheTtlMs(), windowTtlMsForAt(service,
                new org.apache.kafka.common.TopicPartition(settings.databentoStrikeFlowTopic(), 0), topicEvents, now));
        // Null map (the hpsf callers) → generic window for every partition (unchanged behaviour).
        assertEquals(settings.cacheTtlMs(), windowTtlMsForAt(service,
                new org.apache.kafka.common.TopicPartition(settings.databentoMaxPainTopic(), 0), null, now));
    }

    @Test
    void agedNonTerminalMaxPainSurvivesIngestWhileAgedFastEventIsEvicted() throws Exception {
        FeedGatewayService service = service();
        overrideRth(service, true); // ingest uses wall-clock now; hold market hours so the fast TTL applies
        GatewaySettings settings = new GatewaySettings();
        long now = System.currentTimeMillis();
        long thirtyFiveMinAgo = now - 35L * 60_000L;

        String maxPainJson = "{\"messageType\":\"MAX_PAIN\",\"marketDataSource\":\"DATABENTO\","
                + "\"symbol\":\"SPX\",\"expiry\":\"20260622\",\"status\":\"VALID\",\"maxPainStrike\":4500.0}";
        String key = updateCache(service, topicBinding("DATABENTO", "max-pain"),
                recordAt(settings.databentoMaxPainTopic(), 0, 1L, "SPX|20260622", maxPainJson, thirtyFiveMinAgo),
                maxPainJson);
        assertEquals("DATABENTO|SPX|20260622", key, "aged-but-valid max-pain must be cached, not evicted");
        assertTrue(service.healthJson().contains("\"maxPain\":1"));

        // Same age, a FAST event (strike-flow) is still evicted on ingest by the generic 15-min window.
        String sfJson = "{\"eventType\":\"strike-flow\",\"marketDataSource\":\"DATABENTO\","
                + "\"symbol\":\"SPX\",\"expiry\":\"20260622\",\"strikes\":[]}";
        String sfKey = updateCache(service, topicBinding("DATABENTO", "strike-flow"),
                recordAt(settings.databentoStrikeFlowTopic(), 0, 1L, "SPX|20260622", sfJson, thirtyFiveMinAgo),
                sfJson);
        assertEquals(null, sfKey, "aged fast event must still be evicted on ingest");
        assertTrue(service.healthJson().contains("\"strikeFlows\":0"));
    }

    @Test
    void maxPainBeyondTheTwelveHourWindowIsEvictedOnIngest() throws Exception {
        FeedGatewayService service = service();
        GatewaySettings settings = new GatewaySettings();
        long now = System.currentTimeMillis();
        String json = "{\"messageType\":\"MAX_PAIN\",\"marketDataSource\":\"DATABENTO\","
                + "\"symbol\":\"SPX\",\"expiry\":\"20260622\",\"status\":\"VALID\"}";
        String key = updateCache(service, topicBinding("DATABENTO", "max-pain"),
                recordAt(settings.databentoMaxPainTopic(), 0, 1L, "SPX|20260622", json, now - 13L * 3_600_000L),
                json);
        assertEquals(null, key, "max-pain older than the 12h bound must be evicted (not infinite retention)");
        assertTrue(service.healthJson().contains("\"maxPain\":0"));
    }

    @Test
    void periodicPurgeKeepsAgedMaxPainButEvictsAgedFastEvent() throws Exception {
        FeedGatewayService service = service();
        GatewaySettings settings = new GatewaySettings();
        long now = System.currentTimeMillis();

        String maxPainJson = "{\"messageType\":\"MAX_PAIN\",\"marketDataSource\":\"DATABENTO\","
                + "\"symbol\":\"SPX\",\"expiry\":\"20260622\",\"status\":\"VALID\"}";
        updateCache(service, topicBinding("DATABENTO", "max-pain"),
                new ConsumerRecord<>(settings.databentoMaxPainTopic(), 0, 1L, "SPX|20260622", maxPainJson), maxPainJson);
        String sfJson = "{\"eventType\":\"strike-flow\",\"marketDataSource\":\"DATABENTO\","
                + "\"symbol\":\"SPX\",\"expiry\":\"20260622\",\"strikes\":[]}";
        updateCache(service, topicBinding("DATABENTO", "strike-flow"),
                new ConsumerRecord<>(settings.databentoStrikeFlowTopic(), 0, 1L, "SPX|20260622", sfJson), sfJson);
        assertTrue(service.healthJson().contains("\"maxPain\":1"));
        assertTrue(service.healthJson().contains("\"strikeFlows\":1"));

        // Run the periodic purge 20 minutes into the future: the fast event ages past 15 min and is
        // evicted; the max-pain (12h window) survives.
        purgeExpiredCache(service, now + 20L * 60_000L);
        assertTrue(service.healthJson().contains("\"maxPain\":1"), "aged max-pain must survive periodic purge");
        assertTrue(service.healthJson().contains("\"strikeFlows\":0"), "aged fast event must be purged");
    }

    @Test
    void cachedReplayIncludesAgedMaxPainForMatchingDatabentoSelection() throws Exception {
        FeedGatewayService service = service();
        GatewaySettings settings = new GatewaySettings();
        long now = System.currentTimeMillis();
        // Ingest an AGED (35-min) max-pain — older than maxStaleMs (15s) so this proves both the event-aware
        // isCacheFresh AND the cached-replay max-stale exemption let it through to a connecting client.
        String json = "{\"messageType\":\"MAX_PAIN\",\"marketDataSource\":\"DATABENTO\","
                + "\"symbol\":\"SPX\",\"expiry\":\"20260622\",\"status\":\"VALID\",\"maxPainStrike\":4500.0}";
        updateCache(service, topicBinding("DATABENTO", "max-pain"),
                recordAt(settings.databentoMaxPainTopic(), 0, 1L, "SPX|20260622", json, now - 35L * 60_000L), json);

        setActiveSelection(service, "DATABENTO", "SPX", "20260622");
        List<?> replay = cachedEvents(service, List.of("max-pain"), now);
        assertEquals(1, replay.size(), "aged max-pain must be replayed to a freshly-connected DATABENTO client");

        // An IBKR-selected session must NOT receive the DATABENTO max-pain (isolation preserved).
        setActiveSelection(service, "IBKR", "SPX", "20260622");
        assertTrue(cachedEvents(service, List.of("max-pain"), now).isEmpty(),
                "IBKR selection must never receive DATABENTO max-pain");
    }

    @Test
    void cachedReplayMaxPainBelowTheOffsetBarrierStillReplays() throws Exception {
        // Codex Gate-2 NIT: prove the OFFSET-barrier exemption (not just the max-stale one). A slow
        // max-pain's latest record can sit at an offset BELOW the session's per-partition barrier (set
        // when faster topics advanced past selection); without the exemption it would be filtered.
        FeedGatewayService service = service();
        GatewaySettings settings = new GatewaySettings();
        String json = "{\"messageType\":\"MAX_PAIN\",\"marketDataSource\":\"DATABENTO\","
                + "\"symbol\":\"SPX\",\"expiry\":\"20260622\",\"status\":\"VALID\",\"maxPainStrike\":4500.0}";
        // Cache the max-pain at a LOW offset (1)...
        updateCache(service, topicBinding("DATABENTO", "max-pain"),
                new ConsumerRecord<>(settings.databentoMaxPainTopic(), 0, 1L, "SPX|20260622", json), json);
        // ...then raise the offset barrier for that partition far above it (100).
        setOffsetBarrier(service, settings.databentoMaxPainTopic(), 0, 100L);

        setActiveSelection(service, "DATABENTO", "SPX", "20260622");
        assertEquals(1, cachedEvents(service, List.of("max-pain"), System.currentTimeMillis()).size(),
                "max-pain below the offset barrier must still replay (offset-barrier exemption)");
    }

    @Test
    void terminalExpiredMaxPainEvictsCacheButReturnsKeyForOneLiveForward() throws Exception {
        FeedGatewayService service = service();
        GatewaySettings settings = new GatewaySettings();
        // First a VALID max-pain is cached...
        String valid = "{\"messageType\":\"MAX_PAIN\",\"marketDataSource\":\"DATABENTO\","
                + "\"symbol\":\"SPX\",\"expiry\":\"20260622\",\"status\":\"VALID\"}";
        updateCache(service, topicBinding("DATABENTO", "max-pain"),
                new ConsumerRecord<>(settings.databentoMaxPainTopic(), 0, 1L, "SPX|20260622", valid), valid);
        assertTrue(service.healthJson().contains("\"maxPain\":1"));

        // ...then the terminal EXPIRED must still evict the cache AND return the key for one live forward.
        String expired = "{\"messageType\":\"MAX_PAIN\",\"marketDataSource\":\"DATABENTO\","
                + "\"symbol\":\"SPX\",\"expiry\":\"20260622\",\"status\":\"EXPIRED\"}";
        String key = updateCache(service, topicBinding("DATABENTO", "max-pain"),
                new ConsumerRecord<>(settings.databentoMaxPainTopic(), 0, 2L, "SPX|20260622", expired), expired);
        assertEquals("DATABENTO|SPX|20260622", key, "terminal EXPIRED must return a key for the one-time live forward");
        assertTrue(service.healthJson().contains("\"maxPain\":0"), "terminal EXPIRED must evict the cache");
    }

    private static void withSystemProperty(String key, String value, Runnable assertion) {
        String previous = System.getProperty(key);
        try {
            System.setProperty(key, value);
            assertion.run();
        } finally {
            if (previous == null) {
                System.clearProperty(key);
            } else {
                System.setProperty(key, previous);
            }
        }
    }

    private static Object topicBinding(String source, String event) throws Exception {
        Class<?> type = Class.forName("app.feedgateway.FeedGatewayService$TopicBinding");
        Constructor<?> constructor = type.getDeclaredConstructor(String.class, String.class);
        constructor.setAccessible(true);
        return constructor.newInstance(source, event);
    }

    private static FeedGatewayService service() {
        return new FeedGatewayService(
                new GatewaySettings(),
                new ObjectMapper(),
                new HpsfGatewayViewMapper(),
                null /* routingEngine: legacy broadcast path */
        );
    }

    private static boolean isOptionalTopic(FeedGatewayService service, String topic) throws Exception {
        Method method = FeedGatewayService.class.getDeclaredMethod("isOptionalTopic", String.class);
        method.setAccessible(true);
        return (boolean) method.invoke(service, topic);
    }

    private static boolean isExpired(FeedGatewayService service, String event, long eventTime, long now) throws Exception {
        Method method = FeedGatewayService.class.getDeclaredMethod("isExpired", String.class, long.class, long.class);
        method.setAccessible(true);
        return (boolean) method.invoke(service, event, eventTime, now);
    }

    private static long eventCacheTimestamp(FeedGatewayService service, String event, ConsumerRecord<?, ?> record) throws Exception {
        Method method = FeedGatewayService.class.getDeclaredMethod(
                "eventCacheTimestamp", String.class, ConsumerRecord.class, String.class);
        method.setAccessible(true);
        return (long) method.invoke(service, event, record, record.value());
    }

    private static String paceCacheKey(FeedGatewayService service, String json, String fallback) throws Exception {
        Method method = FeedGatewayService.class.getDeclaredMethod("paceCacheKey", String.class, String.class);
        method.setAccessible(true);
        return (String) method.invoke(service, json, fallback);
    }

    private static String gexCacheKey(FeedGatewayService service, String json, String fallback) throws Exception {
        Method method = FeedGatewayService.class.getDeclaredMethod("gexCacheKey", String.class, String.class);
        method.setAccessible(true);
        return (String) method.invoke(service, json, fallback);
    }

    private static String deltaFlowCacheKey(FeedGatewayService service, String json, String fallback) throws Exception {
        Method method = FeedGatewayService.class.getDeclaredMethod("deltaFlowCacheKey", String.class, String.class);
        method.setAccessible(true);
        return (String) method.invoke(service, json, fallback);
    }

    private static String strikeIntelCacheKey(FeedGatewayService service, String json, String fallback) throws Exception {
        Method method = FeedGatewayService.class.getDeclaredMethod("strikeIntelCacheKey", String.class, String.class);
        method.setAccessible(true);
        return (String) method.invoke(service, json, fallback);
    }

    private static String strikeInvasionCacheKey(FeedGatewayService service, String json, String fallback) throws Exception {
        Method method = FeedGatewayService.class.getDeclaredMethod("strikeInvasionCacheKey", String.class, String.class);
        method.setAccessible(true);
        return (String) method.invoke(service, json, fallback);
    }

    private static String esOpenDirectionForecastCacheKey(FeedGatewayService service, String json, String fallback) throws Exception {
        Method method = FeedGatewayService.class.getDeclaredMethod("esOpenDirectionForecastCacheKey", String.class, String.class);
        method.setAccessible(true);
        return (String) method.invoke(service, json, fallback);
    }

    private static String esOpenDirectionOutcomeCacheKey(FeedGatewayService service, String json, String fallback) throws Exception {
        Method method = FeedGatewayService.class.getDeclaredMethod("esOpenDirectionOutcomeCacheKey", String.class, String.class);
        method.setAccessible(true);
        return (String) method.invoke(service, json, fallback);
    }

    private static String maxPainCacheKey(FeedGatewayService service, String json, String fallback) throws Exception {
        Method method = FeedGatewayService.class.getDeclaredMethod("maxPainCacheKey", String.class, String.class);
        method.setAccessible(true);
        return (String) method.invoke(service, json, fallback);
    }

    private static String opbV2ByOptionCacheKey(FeedGatewayService service, String json, String fallback) throws Exception {
        Method method = FeedGatewayService.class.getDeclaredMethod("opbV2ByOptionCacheKey", String.class, String.class);
        method.setAccessible(true);
        return (String) method.invoke(service, json, fallback);
    }

    private static boolean isMaxPainExpired(FeedGatewayService service, String json) throws Exception {
        Method method = FeedGatewayService.class.getDeclaredMethod("isMaxPainExpired", String.class);
        method.setAccessible(true);
        return (boolean) method.invoke(service, json);
    }

    private static String updateCache(
            FeedGatewayService service,
            Object binding,
            ConsumerRecord<String, String> record,
            String json
    ) throws Exception {
        Class<?> bindingType = Class.forName("app.feedgateway.FeedGatewayService$TopicBinding");
        Method method = FeedGatewayService.class.getDeclaredMethod("updateCache", bindingType, ConsumerRecord.class, String.class);
        method.setAccessible(true);
        return (String) method.invoke(service, binding, record, json);
    }

    private static String enrichJson(FeedGatewayService service, String json, Object binding) throws Exception {
        Class<?> bindingType = Class.forName("app.feedgateway.FeedGatewayService$TopicBinding");
        Method method = FeedGatewayService.class.getDeclaredMethod("enrichJson", String.class, bindingType);
        method.setAccessible(true);
        return (String) method.invoke(service, json, binding);
    }

    private static String stampExpiry(FeedGatewayService service, String json, String expiry) throws Exception {
        Method method = FeedGatewayService.class.getDeclaredMethod("stampExpiry", String.class, String.class);
        method.setAccessible(true);
        return (String) method.invoke(service, json, expiry);
    }

    private static boolean replayMatches(FeedGatewayService service, ReplayParams params, String event, String json)
            throws Exception {
        Method method = FeedGatewayService.class.getDeclaredMethod(
                "replayMatches", ReplayParams.class, String.class, String.class);
        method.setAccessible(true);
        return (boolean) method.invoke(service, params, event, json);
    }

    /** The market-calendar trading date the gateway stamps onto expiry-less strike-invasion records. */
    private static String currentTradingDateExpiry() {
        return new GatewaySettings().marketCalendar().currentTradingDate(java.time.Instant.now())
                .format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE);
    }

    /** A ConsumerRecord with an explicit event timestamp (CREATE_TIME), for testing age-based eviction. */
    private static ConsumerRecord<String, String> recordAt(
            String topic, int partition, long offset, String key, String value, long timestampMs) {
        return new ConsumerRecord<>(topic, partition, offset, timestampMs,
                org.apache.kafka.common.record.TimestampType.CREATE_TIME, -1, -1, key, value,
                new org.apache.kafka.common.header.internals.RecordHeaders(), java.util.Optional.empty());
    }

    /** Force the market-hours decision so the cache POLICY is deterministic regardless of wall clock. */
    private static void overrideRth(FeedGatewayService service, Boolean rth) throws Exception {
        Method m = FeedGatewayService.class.getDeclaredMethod("overrideRegularTradingHoursForTest", Boolean.class);
        m.setAccessible(true);
        m.invoke(service, rth);
    }

    private static boolean isExpiredEvent(FeedGatewayService service, String event, long eventTime, long nowMs)
            throws Exception {
        Method m = FeedGatewayService.class.getDeclaredMethod("isExpired", String.class, long.class, long.class);
        m.setAccessible(true);
        return (boolean) m.invoke(service, event, eventTime, nowMs);
    }

    private static long windowTtlMsForAt(FeedGatewayService service,
            org.apache.kafka.common.TopicPartition partition, Object topicEvents, long nowMs) throws Exception {
        Method m = FeedGatewayService.class.getDeclaredMethod(
                "windowTtlMsFor", org.apache.kafka.common.TopicPartition.class, java.util.Map.class, long.class);
        m.setAccessible(true);
        return (long) m.invoke(service, partition, topicEvents, nowMs);
    }

    private static void purgeExpiredCache(FeedGatewayService service, long nowMs) throws Exception {
        Method m = FeedGatewayService.class.getDeclaredMethod("purgeExpiredCache", long.class);
        m.setAccessible(true);
        m.invoke(service, nowMs);
    }

    private static List<?> cachedEvents(FeedGatewayService service, List<String> events, long nowMs)
            throws Exception {
        Method m = FeedGatewayService.class.getDeclaredMethod("cachedEvents", List.class, long.class);
        m.setAccessible(true);
        return (List<?>) m.invoke(service, events, nowMs);
    }

    /** The raw json of one CachedEvent (private record) returned by {@link #cachedEvents}. */
    private static String cachedEventJson(Object cachedEvent) throws Exception {
        Method m = cachedEvent.getClass().getDeclaredMethod("json");
        m.setAccessible(true);
        return (String) m.invoke(cachedEvent);
    }

    @SuppressWarnings("unchecked")
    private static void setOffsetBarrier(FeedGatewayService service, String topic, int partition, long barrier)
            throws Exception {
        Field field = FeedGatewayService.class.getDeclaredField("offsetBarriers");
        field.setAccessible(true);
        AtomicReference<java.util.Map<org.apache.kafka.common.TopicPartition, Long>> ref =
                (AtomicReference<java.util.Map<org.apache.kafka.common.TopicPartition, Long>>) field.get(service);
        ref.set(java.util.Map.of(new org.apache.kafka.common.TopicPartition(topic, partition), barrier));
    }

    private static boolean shouldForward(
            FeedGatewayService service,
            Object binding,
            String json,
            ConsumerRecord<String, String> record
    ) throws Exception {
        Class<?> bindingType = Class.forName("app.feedgateway.FeedGatewayService$TopicBinding");
        Method method = FeedGatewayService.class.getDeclaredMethod("shouldForward", bindingType, String.class, ConsumerRecord.class);
        method.setAccessible(true);
        return (boolean) method.invoke(service, binding, json, record);
    }

    @SuppressWarnings("unchecked")
    @Test
    void autoRollOverridesStaleExpiredSelection() throws Exception {
        // A stale control selection pinned an EXPIRED expiry AFTER today's auto-roll already fired
        // (autoRolledExpiry == target). The auto-roll must override it, not defer for the day.
        System.setProperty("IB_EXPIRY", "AUTO");
        try {
            FeedGatewayService service = service();
            String target = currentTradingDateExpiry();
            setAutoRolledExpiry(service, target);                 // already rolled today
            setActiveSelection(service, "DATABENTO", "ES", "20000101"); // clearly-expired selection
            invokeMaybeAutoRollExpiry(service);
            assertEquals(target, activeExpiry(service),
                    "a stale/expired control selection must be auto-rolled to the session target");
        } finally {
            System.clearProperty("IB_EXPIRY");
        }
    }

    @Test
    void autoRollHoldsFutureSelection() throws Exception {
        // A control selection for a FUTURE expiry (>= target) is a deliberate pick and must hold.
        System.setProperty("IB_EXPIRY", "AUTO");
        try {
            FeedGatewayService service = service();
            setAutoRolledExpiry(service, currentTradingDateExpiry());
            setActiveSelection(service, "DATABENTO", "ES", "29991231"); // clearly-future selection
            invokeMaybeAutoRollExpiry(service);
            assertEquals("29991231", activeExpiry(service),
                    "a future control selection must not be auto-rolled away");
        } finally {
            System.clearProperty("IB_EXPIRY");
        }
    }

    private static void setActiveSelection(FeedGatewayService service, String src, String symbol, String expiry) throws Exception {
        Field field = FeedGatewayService.class.getDeclaredField("activeSelection");
        field.setAccessible(true);
        ((AtomicReference<Object>) field.get(service)).set(newActiveSelection(src, symbol, expiry));
    }

    private static void setAutoRolledExpiry(FeedGatewayService service, String v) throws Exception {
        Field f = FeedGatewayService.class.getDeclaredField("autoRolledExpiry");
        f.setAccessible(true);
        f.set(service, v);
    }

    private static String activeExpiry(FeedGatewayService service) throws Exception {
        Field f = FeedGatewayService.class.getDeclaredField("activeSelection");
        f.setAccessible(true);
        Object sel = ((AtomicReference<?>) f.get(service)).get();
        Method m = sel.getClass().getDeclaredMethod("expiry");
        m.setAccessible(true);
        return (String) m.invoke(sel);
    }

    private static void invokeMaybeAutoRollExpiry(FeedGatewayService service) throws Exception {
        Method m = FeedGatewayService.class.getDeclaredMethod("maybeAutoRollExpiry");
        m.setAccessible(true);
        m.invoke(service);
    }

    private static int cachedEventCount(FeedGatewayService service, String event, long nowMs) throws Exception {
        Method m = FeedGatewayService.class.getDeclaredMethod("cachedEvents", java.util.List.class, long.class);
        m.setAccessible(true);
        return ((java.util.List<?>) m.invoke(service, java.util.List.of(event), nowMs)).size();
    }

    @SuppressWarnings("unchecked")
    private static void ageCacheEventTimes(FeedGatewayService service, String versionKeySubstr, long timeMs) throws Exception {
        Field field = FeedGatewayService.class.getDeclaredField("cacheEventTimes");
        field.setAccessible(true);
        java.util.Map<String, Long> map = (java.util.Map<String, Long>) field.get(service);
        for (String key : map.keySet()) {
            if (key.contains(versionKeySubstr)) {
                map.put(key, timeMs);
            }
        }
    }

    private static void broadcast(FeedGatewayService service, String event, String json) throws Exception {
        Method method = FeedGatewayService.class.getDeclaredMethod("broadcast", String.class, String.class);
        method.setAccessible(true);
        method.invoke(service, event, json);
    }

    /** A synchronous recording WebSocketSession (untracked -> direct send) capturing sent payloads. */
    private static WebSocketSession recordingSession(List<String> sink) {
        return (WebSocketSession) Proxy.newProxyInstance(
                WebSocketSession.class.getClassLoader(),
                new Class<?>[]{WebSocketSession.class},
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "isOpen": return Boolean.TRUE;
                        case "getId": return "rec-session";
                        case "sendMessage":
                            if (args[0] instanceof TextMessage tm) {
                                sink.add(tm.getPayload());
                            }
                            return null;
                        case "toString": return "RecordingSession";
                        case "hashCode": return System.identityHashCode(proxy);
                        case "equals": return proxy == args[0];
                        default:
                            Class<?> rt = method.getReturnType();
                            if (rt == boolean.class) return Boolean.FALSE;
                            if (rt == int.class) return 0;
                            if (rt == long.class) return 0L;
                            return null;
                    }
                });
    }

    /** Registers a synchronous recording WebSocketSession (untracked -> direct send) and captures payloads. */
    @SuppressWarnings("unchecked")
    private static void addRecordingClient(FeedGatewayService service, List<String> sink) throws Exception {
        WebSocketSession session = (WebSocketSession) Proxy.newProxyInstance(
                WebSocketSession.class.getClassLoader(),
                new Class<?>[]{WebSocketSession.class},
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "isOpen": return Boolean.TRUE;
                        case "getId": return "rec-session";
                        case "sendMessage":
                            if (args[0] instanceof TextMessage tm) {
                                sink.add(tm.getPayload());
                            }
                            return null;
                        case "toString": return "RecordingSession";
                        case "hashCode": return System.identityHashCode(proxy);
                        case "equals": return proxy == args[0];
                        default:
                            Class<?> rt = method.getReturnType();
                            if (rt == boolean.class) return Boolean.FALSE;
                            if (rt == int.class) return 0;
                            if (rt == long.class) return 0L;
                            return null;
                    }
                });
        Field clientsField = FeedGatewayService.class.getDeclaredField("clients");
        clientsField.setAccessible(true);
        ((Collection<WebSocketSession>) clientsField.get(service)).add(session);
    }

    private static Object newActiveSelection(String src, String symbol, String expiry) throws Exception {
        Class<?> selType = Class.forName("app.feedgateway.FeedGatewayService$ActiveSelection");
        Constructor<?> constructor = selType.getDeclaredConstructor(String.class, String.class, String.class, long.class, long.class);
        constructor.setAccessible(true);
        return constructor.newInstance(src, symbol, expiry, 0L, 0L);
    }

    @SuppressWarnings("unchecked")
    private static Object activeSelectionOf(FeedGatewayService service) throws Exception {
        Field field = FeedGatewayService.class.getDeclaredField("activeSelection");
        field.setAccessible(true);
        return ((AtomicReference<Object>) field.get(service)).get();
    }

    private static void invokeMarkSelectionReady(FeedGatewayService service, Object selection) throws Exception {
        Class<?> selType = Class.forName("app.feedgateway.FeedGatewayService$ActiveSelection");
        Method m = FeedGatewayService.class.getDeclaredMethod("markSelectionReady", selType);
        m.setAccessible(true);
        m.invoke(service, selection);
    }

    @SuppressWarnings("unchecked")
    private static String readySelectionKey(FeedGatewayService service) throws Exception {
        Field field = FeedGatewayService.class.getDeclaredField("readySelectionKey");
        field.setAccessible(true);
        return ((AtomicReference<String>) field.get(service)).get();
    }

    @SuppressWarnings("unchecked")
    private static void setReadySelectionKey(FeedGatewayService service, String value) throws Exception {
        Field field = FeedGatewayService.class.getDeclaredField("readySelectionKey");
        field.setAccessible(true);
        ((AtomicReference<String>) field.get(service)).set(value);
    }

    private static String uiBatchEnvelopeJsonGex(FeedGatewayService service, List<String> gexByStrike) throws Exception {
        Method method = uiBatchEnvelopeMethod();
        method.setAccessible(true);
        return (String) method.invoke(
                service,
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), gexByStrike, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of()
        );
    }

    @Test
    void uiBatchEnvelopeCarriesStrikeSrArrayKey() throws Exception {
        FeedGatewayService service = service();
        String json = "{\"messageType\":\"UNIFIED_SR_LEVEL\",\"symbol\":\"SPX\",\"bucketStrike\":6050.0,\"dominantSide\":\"RESISTANCE\"}";
        String envelope = uiBatchEnvelopeJsonStrikeSr(service, List.of(json));
        assertTrue(envelope.contains("\"strikeSr\":[" + json + "]"),
                "batch envelope must carry the strikeSr array; was: " + envelope);
        assertTrue(envelope.contains("\"gexByStrike\":[]"));
    }

    @Test
    void uiBatchEnvelopeCarriesGexMagnetsArrayKey() throws Exception {
        FeedGatewayService service = service();
        String json = "{\"messageType\":\"GEX_MAGNET\",\"symbol\":\"SPX\",\"expiry\":\"20260710\",\"magnetStrike\":6050.0}";
        String envelope = uiBatchEnvelopeJsonGexMagnet(service, List.of(json));
        assertTrue(envelope.contains("\"gexMagnets\":[" + json + "]"),
                "batch envelope must carry the gexMagnets array; was: " + envelope);
        assertTrue(envelope.contains("\"strikeSr\":[]"));
    }

    @Test
    void gexMagnetTopicBindsToGexMagnetEventOnTheAvroConsumers() throws Exception {
        String source = Files.readString(Path.of("src/main/java/app/feedgateway/FeedGatewayService.java"));
        // gex-magnet is DATABENTO-only Avro: bound in the Avro consumers + avroTopics (verbatim passthrough).
        assertTrue(source.contains("avroTopics.put(settings.databentoGexMagnetTopic(), \"gex-magnet\");"));
        for (String method : List.of("runAvroCacheConsumer", "runAvroLiveConsumer")) {
            assertTrue(methodBody(source, method).contains(
                    "topicEvents.put(settings.databentoGexMagnetTopic(), new TopicBinding(\"DATABENTO\", \"gex-magnet\"));"),
                    method + " must bind the gex-magnet topic (Avro)");
        }
        for (String method : List.of("runJsonStateCacheConsumer", "runJsonStateLiveConsumer")) {
            assertFalse(methodBody(source, method).contains("gex-magnet"),
                    method + " must NOT bind the gex-magnet topic (it is Avro, not JSON)");
        }
    }

    @Test
    void uiBatchEnvelopeCarriesStrikeInvasionsArrayKey() throws Exception {
        FeedGatewayService service = service();
        String json = "{\"symbol\":\"SPX\",\"strike\":6005,\"invasionState\":\"INVADED\"}";
        String envelope = uiBatchEnvelopeJsonStrikeInvasion(service, List.of(json));
        assertTrue(envelope.contains("\"strikeInvasions\":[" + json + "]"),
                "batch envelope must carry the strikeInvasions array; was: " + envelope);
        assertTrue(envelope.contains("\"strikeIntels\":[]"));
    }

    @Test
    void liquidityHeatmapUsesShortTtlNotGenericCacheWindow() throws Exception {
        FeedGatewayService service = service();
        long now = 10_000_000L;
        // 6s-old frame: expired on the 5s liquidity TTL...
        assertTrue(isExpiredEvent(service, "liquidity-heatmap", now - 6_000, now));
        // ...while a 4s-old frame is fresh, and strike-flow keeps the generic 15-min window.
        assertFalse(isExpiredEvent(service, "liquidity-heatmap", now - 4_000, now));
        assertFalse(isExpiredEvent(service, "strike-flow", now - 6_000, now));
    }

    @Test
    void expiredLiquidityHeatmapFramesAreEvictedFromTheCacheMap() throws Exception {
        FeedGatewayService service = service();
        java.lang.reflect.Field mapField = FeedGatewayService.class.getDeclaredField("liquidityHeatmaps");
        mapField.setAccessible(true);
        @SuppressWarnings("unchecked")
        java.util.Map<String, String> cache = (java.util.Map<String, String>) mapField.get(service);
        java.lang.reflect.Field timesField = FeedGatewayService.class.getDeclaredField("cacheEventTimes");
        timesField.setAccessible(true);
        @SuppressWarnings("unchecked")
        java.util.Map<String, Long> times = (java.util.Map<String, Long>) timesField.get(service);
        cache.put("SPX|20260702", "{\"cells\":[]}");
        times.put("liquidity-heatmap:SPX|20260702", System.currentTimeMillis() - 60_000); // way past 5s TTL
        Method purge = FeedGatewayService.class.getDeclaredMethod("purgeExpiredCache", long.class);
        purge.setAccessible(true);
        purge.invoke(service, System.currentTimeMillis());
        // The backing map must be evicted too — otherwise health/metrics gauges report stale frames.
        assertTrue(cache.isEmpty(), "expired liquidity-heatmap frame must be evicted from the cache map");
    }

    @Test
    void liquidityHeatmapCacheKeyIsPayloadDerivedSymbolExpiry() throws Exception {
        FeedGatewayService service = service();
        Method m = FeedGatewayService.class.getDeclaredMethod("strikeFlowCacheKey", String.class, String.class);
        m.setAccessible(true);
        String key = (String) m.invoke(service,
                "{\"symbol\":\"spx\",\"expiry\":\"2026-07-02\",\"cells\":[]}", "kafka-key-fallback");
        assertEquals("SPX|20260702", key);
        assertEquals("kafka-key-fallback", m.invoke(service, "not json", "kafka-key-fallback"));
    }

    @Test
    void liquidityHeatmapFreshnessUsesRewrittenPayloadTimeNotKafkaRecordTime() throws Exception {
        FeedGatewayService service = service();
        GatewaySettings settings = new GatewaySettings();
        long now = System.currentTimeMillis();
        String json = "{\"schemaVersion\":1,\"symbol\":\"SPX\",\"expiry\":\"2026-07-06\","
                + "\"bucketStartMs\":" + (now - 1_500) + ","
                + "\"bucketEndMs\":" + (now - 500) + ","
                + "\"asOfEventTimeMs\":" + (now - 750) + ","
                + "\"freshness\":\"LIVE\",\"inputQuality\":\"FULL\",\"cells\":[]}";

        String key = updateCache(service, topicBinding("DATABENTO", "liquidity-heatmap"),
                recordAt(settings.strikeLiquidityTopic(), 0, 1L, "SPX|20260706", json, now - 60_000),
                json);

        assertEquals("DATABENTO|SPX|20260706", key);
    }

    @Test
    void uiBatchEnvelopeCarriesLiquidityHeatmapsArrayKey() throws Exception {
        FeedGatewayService service = service();
        String json = "{\"schemaVersion\":1,\"symbol\":\"SPX\",\"expiry\":\"2026-07-02\","
                + "\"bucketStartMs\":1,\"freshness\":\"LIVE\",\"inputQuality\":\"FULL\",\"cells\":[]}";
        String envelope = uiBatchEnvelopeJsonLiquidityHeatmap(service, List.of(json));
        assertTrue(envelope.contains("\"liquidityHeatmaps\":[" + json + "]"),
                "batch envelope must carry the liquidityHeatmaps array; was: " + envelope);
        assertTrue(envelope.contains("\"strikeFlows\":[]"));
        assertTrue(envelope.contains("\"missionPaces\":[]"));
    }

    private static String uiBatchEnvelopeJsonLiquidityHeatmap(FeedGatewayService service,
                                                              List<String> liquidityHeatmaps) throws Exception {
        Method method = uiBatchEnvelopeMethod();
        method.setAccessible(true);
        return (String) method.invoke(
                service,
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                liquidityHeatmaps, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of()
        );
    }

    private static String uiBatchEnvelopeJsonStrikeSr(FeedGatewayService service, List<String> strikeSr) throws Exception {
        Method method = uiBatchEnvelopeMethod();
        method.setAccessible(true);
        return (String) method.invoke(
                service,
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), strikeSr, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of()
        );
    }

    private static String uiBatchEnvelopeJsonGexMagnet(FeedGatewayService service, List<String> gexMagnet) throws Exception {
        Method method = uiBatchEnvelopeMethod();
        method.setAccessible(true);
        return (String) method.invoke(
                service,
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), gexMagnet, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of()
        );
    }

    private static String uiBatchEnvelopeJsonStrikeInvasion(FeedGatewayService service, List<String> strikeInvasions) throws Exception {
        Method method = uiBatchEnvelopeMethod();
        method.setAccessible(true);
        return (String) method.invoke(
                service,
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), strikeInvasions, List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of()
        );
    }

    private static String uiBatchEnvelopeJsonMaxPain(FeedGatewayService service, List<String> maxPains) throws Exception {
        Method method = uiBatchEnvelopeMethod();
        method.setAccessible(true);
        return (String) method.invoke(
                service,
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), maxPains, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of()
        );
    }

    private static String uiBatchEnvelopeJsonOptionPriceBehavior(
            FeedGatewayService service,
            List<String> optionPriceBehaviors
    ) throws Exception {
        Method method = uiBatchEnvelopeMethod();
        method.setAccessible(true);
        return (String) method.invoke(
                service,
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), optionPriceBehaviors, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of()
        );
    }

    private static String uiBatchEnvelopeJsonOpbV2ByOption(FeedGatewayService service, List<String> opbV2ByOptions) throws Exception {
        Method method = uiBatchEnvelopeMethod();
        method.setAccessible(true);
        return (String) method.invoke(
                service,
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), opbV2ByOptions, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of()
        );
    }

    private static String uiBatchEnvelopeJsonOpbV2Session(FeedGatewayService service, List<String> opbV2Sessions) throws Exception {
        Method method = uiBatchEnvelopeMethod();
        method.setAccessible(true);
        return (String) method.invoke(
                service,
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), opbV2Sessions, List.of(), List.of(), List.of(), List.of(), List.of(), List.of()
        );
    }

    private static String uiBatchEnvelopeJsonMissionPace(FeedGatewayService service, List<String> missionPaces) throws Exception {
        Method method = uiBatchEnvelopeMethod();
        method.setAccessible(true);
        return (String) method.invoke(
                service,
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), missionPaces, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of()
        );
    }

    private static String uiBatchEnvelopeJsonMissionControl(FeedGatewayService service, List<String> missionControls) throws Exception {
        Method method = uiBatchEnvelopeMethod();
        method.setAccessible(true);
        return (String) method.invoke(
                service,
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), missionControls, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of()
        );
    }

    private static String uiBatchEnvelopeJsonSpreadSkew(FeedGatewayService service, List<String> spreadSkews) throws Exception {
        Method method = uiBatchEnvelopeMethod();
        method.setAccessible(true);
        return (String) method.invoke(
                service,
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), spreadSkews, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of()
        );
    }

    private static String envelopeJson(FeedGatewayService service, String event, String json) throws Exception {
        Method method = FeedGatewayService.class.getDeclaredMethod("envelopeJson", String.class, String.class);
        method.setAccessible(true);
        return (String) method.invoke(service, event, json);
    }

    private static String uiBatchEnvelopeJson(FeedGatewayService service, List<String> strikeFlows) throws Exception {
        Method method = uiBatchEnvelopeMethod();
        method.setAccessible(true);
        return (String) method.invoke(
                service,
                // positional args: snapshots, paces, paceRanks, directionalPressures, strikeFlows,
                // deltaFlows, then the remaining latest-state lists — pass empty except strikeFlows.
                List.of(), List.of(), List.of(), List.of(), strikeFlows, List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of()
        );
    }

    private static Method uiBatchEnvelopeMethod() throws Exception {
        return FeedGatewayService.class.getDeclaredMethod(
                "uiBatchEnvelopeJson",
                List.class, List.class, List.class, List.class, List.class, List.class,
                List.class, List.class, List.class, List.class, List.class, List.class,
                List.class, List.class, List.class, List.class, List.class, List.class, List.class,
                List.class, List.class, List.class, List.class, List.class, List.class, List.class,
                List.class, List.class
        );
    }
}

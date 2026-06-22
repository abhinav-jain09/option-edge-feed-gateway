package app.feedgateway;

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
import static org.junit.jupiter.api.Assertions.assertTrue;

class FeedGatewayServiceTest {
    @Test
    void sourceSwitchReplayIncludesCachedVixPrice() {
        assertEquals(
                List.of("snapshot", "pace", "directional-pressure", "vix-price", "index-price", "strike-flow", "volume-sandwich", "gex-by-strike", "max-pain"),
                FeedGatewayService.sourceSwitchReplayEvents()
        );
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
    void isMaxPainExpiredReturnsTrueOnlyForTerminalStatus() throws Exception {
        FeedGatewayService service = service();
        assertTrue(isMaxPainExpired(service, "{\"status\":\"EXPIRED\"}"));
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
    void gatewayInitialExpiryRollsAfterOptionMarketClose() {
        ZonedDateTime beforeClose = ZonedDateTime.of(2026, 6, 15, 15, 30, 0, 0,
                ZoneId.of("America/New_York"));
        ZonedDateTime afterClose = ZonedDateTime.of(2026, 6, 15, 16, 15, 0, 0,
                ZoneId.of("America/New_York"));

        assertEquals("20260615", GatewaySettings.normalizeMarketExpiry("20260615", beforeClose));
        assertEquals("20260616", GatewaySettings.normalizeMarketExpiry("20260615", afterClose));
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
        // Legacy caught-up gating: max-pain (DATABENTO-only Avro) under avroCaughtUp; gex-by-strike
        // (multi-source) under BOTH flags.
        assertTrue(source.contains(
                "sendCachedState(session, List.of(\"snapshot\", \"pace\", \"directional-pressure\", \"max-pain\"));"));
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
    void cacheTtlMsForEventUsesTheLongWindowForMaxPainOnly() throws Exception {
        FeedGatewayService service = service();
        assertEquals(new GatewaySettings().maxPainTtlMs(), cacheTtlMsForEvent(service, "max-pain"));
        assertEquals(new GatewaySettings().cacheTtlMs(), cacheTtlMsForEvent(service, "snapshot"));
        assertEquals(new GatewaySettings().cacheTtlMs(), cacheTtlMsForEvent(service, "strike-flow"));
    }

    @Test
    void isExpiredIsEventAwareForMaxPainVersusFast() throws Exception {
        FeedGatewayService service = service();
        long now = 2_000_000_000_000L;
        long thirtyFiveMinAgo = now - 35L * 60_000L;
        // The exact scenario observed live: a 35-min-old record. Fast events expire; max-pain does not.
        assertTrue(isExpiredEvent(service, "snapshot", thirtyFiveMinAgo, now));
        assertFalse(isExpiredEvent(service, "max-pain", thirtyFiveMinAgo, now));
        // Beyond the 12h max-pain window, even max-pain expires (bounded, not infinite).
        assertTrue(isExpiredEvent(service, "max-pain", now - 13L * 3_600_000L, now));
    }

    @Test
    void seekWindowTtlMapsMaxPainToLongWindowAndOthersToGeneric() throws Exception {
        FeedGatewayService service = service();
        GatewaySettings settings = new GatewaySettings();
        java.util.Map<String, Object> topicEvents = new java.util.HashMap<>();
        topicEvents.put(settings.databentoMaxPainTopic(), topicBinding("DATABENTO", "max-pain"));
        topicEvents.put(settings.databentoStrikeFlowTopic(), topicBinding("DATABENTO", "strike-flow"));

        assertEquals(settings.maxPainTtlMs(), windowTtlMsFor(service,
                new org.apache.kafka.common.TopicPartition(settings.databentoMaxPainTopic(), 0), topicEvents));
        assertEquals(settings.cacheTtlMs(), windowTtlMsFor(service,
                new org.apache.kafka.common.TopicPartition(settings.databentoStrikeFlowTopic(), 0), topicEvents));
        // Null map (the hpsf callers) → generic window for every partition (unchanged behaviour).
        assertEquals(settings.cacheTtlMs(), windowTtlMsFor(service,
                new org.apache.kafka.common.TopicPartition(settings.databentoMaxPainTopic(), 0), null));
    }

    @Test
    void agedNonTerminalMaxPainSurvivesIngestWhileAgedFastEventIsEvicted() throws Exception {
        FeedGatewayService service = service();
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

    private static String maxPainCacheKey(FeedGatewayService service, String json, String fallback) throws Exception {
        Method method = FeedGatewayService.class.getDeclaredMethod("maxPainCacheKey", String.class, String.class);
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

    /** A ConsumerRecord with an explicit event timestamp (CREATE_TIME), for testing age-based eviction. */
    private static ConsumerRecord<String, String> recordAt(
            String topic, int partition, long offset, String key, String value, long timestampMs) {
        return new ConsumerRecord<>(topic, partition, offset, timestampMs,
                org.apache.kafka.common.record.TimestampType.CREATE_TIME, -1, -1, key, value,
                new org.apache.kafka.common.header.internals.RecordHeaders(), java.util.Optional.empty());
    }

    private static long cacheTtlMsForEvent(FeedGatewayService service, String event) throws Exception {
        Method m = FeedGatewayService.class.getDeclaredMethod("cacheTtlMsForEvent", String.class);
        m.setAccessible(true);
        return (long) m.invoke(service, event);
    }

    private static boolean isExpiredEvent(FeedGatewayService service, String event, long eventTime, long nowMs)
            throws Exception {
        Method m = FeedGatewayService.class.getDeclaredMethod("isExpired", String.class, long.class, long.class);
        m.setAccessible(true);
        return (boolean) m.invoke(service, event, eventTime, nowMs);
    }

    private static long windowTtlMsFor(FeedGatewayService service,
            org.apache.kafka.common.TopicPartition partition, Object topicEvents) throws Exception {
        Method m = FeedGatewayService.class.getDeclaredMethod(
                "windowTtlMsFor", org.apache.kafka.common.TopicPartition.class, java.util.Map.class);
        m.setAccessible(true);
        return (long) m.invoke(service, partition, topicEvents);
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
    private static void setActiveSelection(FeedGatewayService service, String src, String symbol, String expiry) throws Exception {
        Field field = FeedGatewayService.class.getDeclaredField("activeSelection");
        field.setAccessible(true);
        ((AtomicReference<Object>) field.get(service)).set(newActiveSelection(src, symbol, expiry));
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
        Method method = FeedGatewayService.class.getDeclaredMethod(
                "uiBatchEnvelopeJson",
                List.class, List.class, List.class, List.class, List.class, List.class,
                List.class, List.class, List.class, List.class, List.class, List.class, List.class
        );
        method.setAccessible(true);
        return (String) method.invoke(
                service,
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                gexByStrike, List.of(), List.of(), List.of(), List.of(), List.of(), List.of()
        );
    }

    private static String uiBatchEnvelopeJsonMaxPain(FeedGatewayService service, List<String> maxPains) throws Exception {
        Method method = FeedGatewayService.class.getDeclaredMethod(
                "uiBatchEnvelopeJson",
                List.class, List.class, List.class, List.class, List.class, List.class,
                List.class, List.class, List.class, List.class, List.class, List.class, List.class
        );
        method.setAccessible(true);
        return (String) method.invoke(
                service,
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), maxPains, List.of(), List.of(), List.of(), List.of(), List.of()
        );
    }

    private static String envelopeJson(FeedGatewayService service, String event, String json) throws Exception {
        Method method = FeedGatewayService.class.getDeclaredMethod("envelopeJson", String.class, String.class);
        method.setAccessible(true);
        return (String) method.invoke(service, event, json);
    }

    private static String uiBatchEnvelopeJson(FeedGatewayService service, List<String> strikeFlows) throws Exception {
        Method method = FeedGatewayService.class.getDeclaredMethod(
                "uiBatchEnvelopeJson",
                List.class, List.class, List.class, List.class, List.class, List.class,
                List.class, List.class, List.class, List.class, List.class, List.class, List.class
        );
        method.setAccessible(true);
        return (String) method.invoke(
                service,
                List.of(), List.of(), List.of(), strikeFlows, List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of()
        );
    }
}

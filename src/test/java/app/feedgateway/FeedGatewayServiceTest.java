package app.feedgateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FeedGatewayServiceTest {
    @Test
    void sourceSwitchReplayIncludesCachedVixPrice() {
        assertEquals(
                List.of("snapshot", "pace", "directional-pressure", "vix-price", "index-price", "strike-flow", "volume-sandwich", "gex-by-strike"),
                FeedGatewayService.sourceSwitchReplayEvents()
        );
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

    private static String envelopeJson(FeedGatewayService service, String event, String json) throws Exception {
        Method method = FeedGatewayService.class.getDeclaredMethod("envelopeJson", String.class, String.class);
        method.setAccessible(true);
        return (String) method.invoke(service, event, json);
    }

    private static String uiBatchEnvelopeJson(FeedGatewayService service, List<String> strikeFlows) throws Exception {
        Method method = FeedGatewayService.class.getDeclaredMethod(
                "uiBatchEnvelopeJson",
                List.class,
                List.class,
                List.class,
                List.class,
                List.class,
                List.class,
                List.class,
                List.class,
                List.class,
                List.class,
                List.class,
                List.class
        );
        method.setAccessible(true);
        return (String) method.invoke(
                service,
                List.of(),
                List.of(),
                List.of(),
                strikeFlows,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of()
        );
    }
}

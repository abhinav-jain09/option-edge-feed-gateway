package app.feedgateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

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
                List.of("snapshot", "pace", "directional-pressure", "vix-price", "index-price", "volume-sandwich", "gex-by-strike"),
                FeedGatewayService.sourceSwitchReplayEvents()
        );
    }

    @Test
    void indexPriceCacheKeyUsesPayloadSymbolInsteadOfKafkaTradeKey() {
        FeedGatewayService service = new FeedGatewayService(
                new GatewaySettings(),
                new ObjectMapper(),
                new HpsfGatewayViewMapper()
        );

        String firstEsTrade = "{\"symbol\":\"ES.v.0\",\"instrumentId\":\"42140864\",\"price\":7580.5}";
        String nextEsTrade = "{\"symbol\":\"ES.v.0\",\"instrumentId\":\"42140864\",\"price\":7580.75}";
        String vixPrice = "{\"symbol\":\"VIX\",\"price\":16.2}";

        assertEquals("ES.V.0", service.indexPriceCacheKey(firstEsTrade, "trade-1"));
        assertEquals("ES.V.0", service.indexPriceCacheKey(nextEsTrade, "trade-2"));
        assertEquals("VIX", service.indexPriceCacheKey(vixPrice, "vix-record"));
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
                new HpsfGatewayViewMapper()
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
}

package app.feedgateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Integration guard for the {@code /api/seller-activity} data source: proves the endpoint accessor
 * ({@link FeedGatewayService#cachedStrikeFlowSnapshot}) resolves the SAME key the REAL caching path
 * ({@link FeedGatewayService#updateCache}) writes, prefers DATABENTO deterministically, and honors the
 * freshness gate. These are exactly the gaps a mocked-accessor controller test conceals.
 */
class SellerActivityCacheIntegrationTest {

    private FeedGatewayService service() {
        return new FeedGatewayService(new GatewaySettings(), new ObjectMapper(), new HpsfGatewayViewMapper(), null);
    }

    private static String snapshot(String symbol, String expiryYmd, long timestampMs) {
        return "{\"symbol\":\"" + symbol + "\",\"expiry\":\"" + expiryYmd + "\",\"timestampMs\":"
                + timestampMs + ",\"strikes\":[]}";
    }

    @Test
    void accessorResolvesTheFreshSourcePrefixedKeyThatUpdateCacheWrites() {
        FeedGatewayService service = service();
        long now = System.currentTimeMillis();
        String json = snapshot("SPX", "20260724", now);

        String writtenKey = service.cacheStrikeFlowForTest("DATABENTO", "SPX|20260724", json, now);
        assertEquals("DATABENTO|SPX|20260724", writtenKey, "updateCache source-prefixes the key");

        // resolves that SAME cached entry from symbol + expiry (failed before the fix)
        assertEquals(json, service.cachedStrikeFlowSnapshot("SPX", "2026-07-24"));
        assertEquals(json, service.cachedStrikeFlowSnapshot(" spx ".trim(), "2026-07-24"), "case-insensitive");
        assertNull(service.cachedStrikeFlowSnapshot("SPX", "2026-07-25"), "wrong expiry -> null");
        assertNull(service.cachedStrikeFlowSnapshot("QQQ", "2026-07-24"), "wrong symbol -> null");
        assertNull(service.cachedStrikeFlowSnapshot(null, "2026-07-24"));
    }

    @Test
    void doesNotServeAnExpiredSnapshot() {
        FeedGatewayService service = service();
        long stale = System.currentTimeMillis() - Duration.ofMinutes(45).toMillis();
        service.cacheStrikeFlowForTest("DATABENTO", "SPX|20260725", snapshot("SPX", "20260725", stale), stale);
        assertNull(service.cachedStrikeFlowSnapshot("SPX", "2026-07-25"),
                "an expired strike-flow snapshot must not be served");
    }

    @Test
    void prefersDatabentoDeterministicallyOverAnotherSource() {
        FeedGatewayService service = service();
        long now = System.currentTimeMillis();
        String databento = snapshot("SPX", "20260726", now);
        // an IBKR snapshot for the SAME symbol|expiry must never be returned for seller-activity.
        service.cacheStrikeFlowForTest("IBKR", "SPX|20260726", snapshot("SPX", "20260726", now - 1000L), now - 1000L);
        service.cacheStrikeFlowForTest("DATABENTO", "SPX|20260726", databento, now);
        assertEquals(databento, service.cachedStrikeFlowSnapshot("SPX", "2026-07-26"),
                "always the DATABENTO snapshot, deterministically");
    }
}

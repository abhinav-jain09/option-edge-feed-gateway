package app.feedgateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Integration guard for the {@code /api/seller-activity} data source: proves the endpoint accessor
 * ({@link FeedGatewayService#cachedStrikeFlowSnapshot}) resolves the SAME key the REAL caching path
 * ({@link FeedGatewayService#updateCache}) writes. This is the exact gap a mocked-accessor controller
 * test conceals — updateCache SOURCE-prefixes the key ("DATABENTO|SPX|20260724"), so a bare
 * "SPX|20260724" lookup always missed.
 */
class SellerActivityCacheIntegrationTest {

    private FeedGatewayService service() {
        return new FeedGatewayService(new GatewaySettings(), new ObjectMapper(), new HpsfGatewayViewMapper(), null);
    }

    @Test
    void accessorResolvesTheSourcePrefixedKeyThatUpdateCacheWrites() {
        FeedGatewayService service = service();
        String json = "{\"symbol\":\"SPX\",\"expiry\":\"20260724\",\"timestampMs\":1780000500000,\"strikes\":[]}";

        String writtenKey = service.cacheStrikeFlowForTest("DATABENTO", "SPX|20260724", json);
        assertEquals("DATABENTO|SPX|20260724", writtenKey,
                "updateCache source-prefixes the strike-flow cache key");

        // The accessor must resolve that SAME cached entry from symbol + expiry (failed before the fix).
        assertEquals(json, service.cachedStrikeFlowSnapshot("SPX", "2026-07-24"));
        assertEquals(json, service.cachedStrikeFlowSnapshot(" spx ".trim(), "2026-07-24"),
                "symbol match is case-insensitive");
        assertNull(service.cachedStrikeFlowSnapshot("SPX", "2026-07-25"), "wrong expiry -> null");
        assertNull(service.cachedStrikeFlowSnapshot("QQQ", "2026-07-24"), "wrong symbol -> null");
        assertNull(service.cachedStrikeFlowSnapshot(null, "2026-07-24"));
    }
}

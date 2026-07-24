package app.feedgateway;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class SellerActivityCacheIntegrationTest {

    private final ObjectMapper mapper = new ObjectMapper();

    private FeedGatewayService service() {
        return new FeedGatewayService(new GatewaySettings(), mapper, new HpsfGatewayViewMapper(), null);
    }

    private static String activity(String source, String symbol, String expiry, double strike,
                                   long timestampMs, long count) {
        return "{\"eventType\":\"SELLER_ACTIVITY\",\"source\":\"" + source
                + "\",\"symbol\":\"" + symbol + "\",\"expiry\":\"" + expiry
                + "\",\"strike\":" + strike + ",\"timestampMs\":" + timestampMs
                + ",\"bucketMinutes\":1,\"points\":[{\"timestampMs\":" + timestampMs
                + ",\"sellTradeCount\":" + count + ",\"callSellTradeCount\":" + count
                + ",\"putSellTradeCount\":0}]}";
    }

    @Test
    void assemblesFreshPerStrikeRecordsIntoTheAggregatorContract() throws Exception {
        FeedGatewayService service = service();
        long now = System.currentTimeMillis();
        service.cacheSellerActivityForTest("DATABENTO", "ignored",
                activity("DATABENTO", "SPX", "20260724", 7520, now, 2), now);
        service.cacheSellerActivityForTest("DATABENTO", "ignored",
                activity("DATABENTO", "SPX", "20260724", 7515, now - 1000, 3), now - 1000);
        service.cacheSellerActivityForTest("DATABENTO", "ignored",
                activity("DATABENTO", "QQQ", "20260724", 500, now, 9), now);

        JsonNode snapshot = mapper.readTree(service.cachedSellerActivitySnapshot("spx", "2026-07-24"));
        assertEquals("SPX", snapshot.path("symbol").asText());
        assertEquals("20260724", snapshot.path("expiry").asText());
        assertEquals(now, snapshot.path("timestampMs").asLong());
        assertEquals(2, snapshot.path("strikes").size());
        assertEquals(7515.0, snapshot.path("strikes").get(0).path("strike").asDouble());
        assertEquals(3, snapshot.path("strikes").get(0).path("sellerActivity")
                .path("points").get(0).path("sellTradeCount").asLong());
        assertEquals(7520.0, snapshot.path("strikes").get(1).path("strike").asDouble());
        assertNull(service.cachedSellerActivitySnapshot("SPX", "2026-07-25"));
    }

    @Test
    void ignoresOldStrikeFlowSnapshotsAndExpiredSellerRecords() {
        FeedGatewayService service = service();
        long now = System.currentTimeMillis();
        service.cacheStrikeFlowForTest("DATABENTO", "SPX|20260724",
                "{\"symbol\":\"SPX\",\"expiry\":\"20260724\",\"timestampMs\":" + now
                        + ",\"strikes\":[{\"strike\":7515,\"sellerActivity\":{\"bucketMinutes\":1,\"points\":[]}}]}",
                now);
        assertNull(service.cachedSellerActivitySnapshot("SPX", "2026-07-24"),
                "the endpoint must not read the obsolete chain-level cache");

        long stale = now - Duration.ofMinutes(45).toMillis();
        service.cacheSellerActivityForTest("DATABENTO", "ignored",
                activity("DATABENTO", "SPX", "20260724", 7515, stale, 3), stale);
        assertNull(service.cachedSellerActivitySnapshot("SPX", "2026-07-24"));
    }
}

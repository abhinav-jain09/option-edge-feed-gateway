package app.feedgateway.selleractivity;

import app.feedgateway.FeedGatewayService;
import app.feedgateway.liquidityhistory.LiquidityHistoryAuth;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SellerActivityControllerTest {

    private FeedGatewayService service;
    private LiquidityHistoryAuth auth;
    private SellerActivityController controller;

    @BeforeEach
    void setUp() {
        service = mock(FeedGatewayService.class);
        auth = mock(LiquidityHistoryAuth.class);
        controller = new SellerActivityController(service, auth, new ObjectMapper());
    }

    private void authOk() {
        when(auth.authenticate(any())).thenReturn(new LiquidityHistoryAuth.Result(200, "user"));
    }

    private int status(String symbol, String expiry, String sample, String mode) {
        return controller.sellerActivity(symbol, expiry, sample, mode, "Bearer token").getStatusCode().value();
    }

    @Test
    void unauthenticatedReturnsAuthStatusAndNeverTouchesTheCache() {
        when(auth.authenticate(any())).thenReturn(new LiquidityHistoryAuth.Result(401, null));
        assertEquals(401, status("SPX", "2026-07-24", null, null));
        verifyNoInteractions(service);
    }

    @Test
    void rejectsInvalidRequestParams() {
        authOk();
        assertEquals(400, status(null, "2026-07-24", null, null), "missing symbol");
        assertEquals(400, status("SPX", "20260724", null, null), "non-ISO expiry shape");
        assertEquals(400, status("SPX", "2026-99-99", null, null), "impossible date (strict parse)");
        assertEquals(400, status("SPX", "2026-02-30", null, null), "non-existent day (strict parse)");
        assertEquals(400, status("SPX", "2026-07-24", "7", null), "unsupported sample");
        assertEquals(400, status("SPX", "2026-07-24", "30", "buy"), "unsupported mode");
    }

    @Test
    void missingSnapshotReturns200WithEmptyEnvelope() {
        authOk();
        when(service.cachedStrikeFlowSnapshot("SPX", "2026-07-24")).thenReturn(null);
        ResponseEntity<ObjectNode> r = controller.sellerActivity("SPX", "2026-07-24", null, null, "Bearer token");
        assertEquals(200, r.getStatusCode().value());
        ObjectNode body = r.getBody();
        assertEquals("SPX", body.path("symbol").asText());
        assertEquals(30, body.path("sampleMinutes").asInt());       // default
        assertEquals("combined", body.path("mode").asText());       // default
        assertTrue(body.path("leaderStrike").isNull());
        assertEquals(0, body.path("series").size());
    }

    @Test
    void rateLimitsPerPrincipalAfterTheBudgetIsExhausted() {
        authOk(); // principal "user"; snapshot is unstubbed -> null -> 200 empty envelopes
        for (int i = 0; i < 120; i++) {
            assertEquals(200, status("SPX", "2026-07-24", null, null), "within budget");
        }
        assertEquals(429, status("SPX", "2026-07-24", null, null), "over the per-minute budget -> 429");
    }

    @Test
    void boundsConcurrentAggregationsWith503WhenSlotsAreExhausted() {
        SellerActivityController limited =
                new SellerActivityController(service, auth, new ObjectMapper(), 1); // one aggregation slot
        when(auth.authenticate(any())).thenReturn(new LiquidityHistoryAuth.Result(200, "user"));
        // simulate an in-flight aggregation holding the only slot
        limited.aggregationSlots.acquireUninterruptibly();
        try {
            assertEquals(503, limited.sellerActivity("SPX", "2026-07-24", null, null, "Bearer token")
                    .getStatusCode().value(), "no free slot -> 503 (protects the shared gateway heap)");
        } finally {
            limited.aggregationSlots.release();
        }
        // slot freed -> served, and the finally-release keeps the pool from leaking
        assertEquals(200, limited.sellerActivity("SPX", "2026-07-24", null, null, "Bearer token")
                .getStatusCode().value());
        assertEquals(1, limited.aggregationSlots.availablePermits(), "permit released after serving");
    }

    @Test
    void authenticatedRequestNormalizesSymbolAndAggregatesCachedSnapshot() {
        authOk();
        String snapshot = "{\"symbol\":\"SPX\",\"expiry\":\"20260724\",\"timestampMs\":1780000500000,"
                + "\"strikes\":[{\"strike\":7515.0,\"sellerActivity\":{\"bucketMinutes\":1,\"points\":["
                + "{\"timestampMs\":60000,\"sellTradeCount\":5,\"callSellTradeCount\":3,\"putSellTradeCount\":2}]}}]}";
        when(service.cachedStrikeFlowSnapshot("SPX", "2026-07-24")).thenReturn(snapshot);

        // lower-case, padded symbol must normalize to the cache key "SPX".
        ResponseEntity<ObjectNode> r = controller.sellerActivity(" spx ", "2026-07-24", "30", "combined", "Bearer token");
        assertEquals(200, r.getStatusCode().value());
        ObjectNode body = r.getBody();
        assertEquals("SPX", body.path("symbol").asText());
        assertEquals(1_780_000_500_000L, body.path("asOfMs").asLong());
        assertEquals(1, body.path("series").size());
        assertEquals(7515.0, body.path("series").get(0).path("strike").asDouble());
        assertEquals(5L, body.path("series").get(0).path("points").get(0).path("count").asLong());
        assertEquals(7515.0, body.path("leaderStrike").asDouble());
    }
}

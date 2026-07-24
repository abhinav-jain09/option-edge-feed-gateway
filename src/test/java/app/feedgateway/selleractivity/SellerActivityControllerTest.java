package app.feedgateway.selleractivity;

import app.feedgateway.FeedGatewayService;
import app.feedgateway.liquidityhistory.LiquidityHistoryAuth;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

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

    /** Invoke the streaming body (which also RELEASES the aggregation permit) and parse the JSON. */
    private static JsonNode read(ResponseEntity<StreamingResponseBody> r) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        r.getBody().writeTo(baos);
        return new ObjectMapper().readTree(baos.toByteArray());
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
    void missingSnapshotReturns200WithEmptyEnvelope() throws IOException {
        authOk();
        when(service.cachedSellerActivitySnapshot("SPX", "2026-07-24")).thenReturn(null);
        ResponseEntity<StreamingResponseBody> r = controller.sellerActivity("SPX", "2026-07-24", null, null, "Bearer token");
        assertEquals(200, r.getStatusCode().value());
        JsonNode body = read(r);
        assertEquals("SPX", body.path("symbol").asText());
        assertEquals(30, body.path("sampleMinutes").asInt());       // default
        assertEquals("combined", body.path("mode").asText());       // default
        assertTrue(body.path("leaderStrike").isNull());
        assertEquals(0, body.path("series").size());
    }

    @Test
    void rateLimitsPerPrincipalAfterTheBudgetIsExhausted() {
        // High concurrency limit so the SEMAPHORE never gates here (streams aren't invoked in this test) —
        // this exercises the per-minute RATE limiter. Snapshot unstubbed -> null -> 200 empty envelopes.
        SellerActivityController c = new SellerActivityController(service, auth, new ObjectMapper(), 1000);
        when(auth.authenticate(any())).thenReturn(new LiquidityHistoryAuth.Result(200, "user"));
        for (int i = 0; i < 120; i++) {
            assertEquals(200, c.sellerActivity("SPX", "2026-07-24", null, null, "Bearer token")
                    .getStatusCode().value(), "within budget");
        }
        assertEquals(429, c.sellerActivity("SPX", "2026-07-24", null, null, "Bearer token")
                .getStatusCode().value(), "over the per-minute budget -> 429");
    }

    @Test
    void holdsTheAggregationSlotThroughTheWriteAnd503sWhenExhausted() throws IOException {
        SellerActivityController limited =
                new SellerActivityController(service, auth, new ObjectMapper(), 1); // one aggregation slot
        when(auth.authenticate(any())).thenReturn(new LiquidityHistoryAuth.Result(200, "user"));
        // simulate an in-flight request holding the only slot
        limited.aggregationSlots.acquireUninterruptibly();
        try {
            assertEquals(503, limited.sellerActivity("SPX", "2026-07-24", null, null, "Bearer token")
                    .getStatusCode().value(), "no free slot -> 503 (protects the shared gateway heap)");
        } finally {
            limited.aggregationSlots.release();
        }
        // slot free -> served; the permit is held UNTIL the write completes (bounds slow-client backpressure)
        ResponseEntity<StreamingResponseBody> served =
                limited.sellerActivity("SPX", "2026-07-24", null, null, "Bearer token");
        assertEquals(200, served.getStatusCode().value());
        assertEquals(0, limited.aggregationSlots.availablePermits(), "slot held while the response is unwritten");
        served.getBody().writeTo(new ByteArrayOutputStream()); // complete the write
        assertEquals(1, limited.aggregationSlots.availablePermits(), "permit released after the write completes");
    }

    @Test
    void authenticatedRequestNormalizesSymbolAndAggregatesCachedSnapshot() throws IOException {
        authOk();
        String snapshot = "{\"symbol\":\"SPX\",\"expiry\":\"20260724\",\"timestampMs\":1780000500000,"
                + "\"strikes\":[{\"strike\":7515.0,\"sellerActivity\":{\"bucketMinutes\":1,\"points\":["
                + "{\"timestampMs\":60000,\"sellTradeCount\":5,\"callSellTradeCount\":3,\"putSellTradeCount\":2}]}}]}";
        when(service.cachedSellerActivitySnapshot("SPX", "2026-07-24")).thenReturn(snapshot);

        // lower-case, padded symbol must normalize to the cache key "SPX".
        ResponseEntity<StreamingResponseBody> r = controller.sellerActivity(" spx ", "2026-07-24", "30", "combined", "Bearer token");
        assertEquals(200, r.getStatusCode().value());
        JsonNode body = read(r);
        assertEquals("SPX", body.path("symbol").asText());
        assertEquals(1_780_000_500_000L, body.path("asOfMs").asLong());
        assertEquals(1, body.path("series").size());
        assertEquals(7515.0, body.path("series").get(0).path("strike").asDouble());
        assertEquals(5L, body.path("series").get(0).path("points").get(0).path("count").asLong());
        assertEquals(7515.0, body.path("leaderStrike").asDouble());
    }
}

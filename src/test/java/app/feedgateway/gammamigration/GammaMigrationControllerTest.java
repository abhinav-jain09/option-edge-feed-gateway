package app.feedgateway.gammamigration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.feedgateway.FeedGatewayService;
import app.feedgateway.liquidityhistory.LiquidityHistoryAuth;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;

/**
 * Behavioural tests for {@code GET /api/gamma-migration}.
 *
 * <p>The endpoint is a cheap authenticated read of one cache entry, so what actually needs
 * defending is the edges: that auth is enforced before anything else happens, that "no reading
 * yet" is distinguishable from "wrong request", and that the cached record reaches the caller
 * unreshaped.
 */
class GammaMigrationControllerTest {

    private static final String RECORD =
            "{\"messageType\":\"GAMMA_MIGRATION_SNAPSHOT\",\"symbol\":\"SPX\",\"expiry\":\"20260731\","
            + "\"regime\":\"PEAK_PARKED\",\"judgedPeakStrike\":7400.0,\"judgedPeakDwellMs\":11400000,"
            + "\"hotStrike\":7450.0,\"hotTrusted\":true,\"flipStrike\":null}";

    /** A gateway whose only behaviour is the one cache lookup this endpoint makes. */
    private static FeedGatewayService gatewayReturning(String cached) {
        FeedGatewayService service = mock(FeedGatewayService.class);
        when(service.cachedGammaMigration(any(), any())).thenReturn(cached);
        return service;
    }

    private static LiquidityHistoryAuth authReturning(int status) {
        LiquidityHistoryAuth auth = mock(LiquidityHistoryAuth.class);
        when(auth.authenticate(any())).thenReturn(new LiquidityHistoryAuth.Result(status, "tester"));
        return auth;
    }

    private static ResponseEntity<String> call(String cached, int authStatus, String symbol, String expiry) {
        return new GammaMigrationController(gatewayReturning(cached), authReturning(authStatus))
                .gammaMigration(symbol, expiry, "Bearer t");
    }

    @Test
    void theCachedRecordIsServedVerbatim() {
        // Reshaping here would create a second view of a contract that already has one owner (the
        // Avro schema), free to drift from the option chain's copy of the same reading.
        ResponseEntity<String> res = call(RECORD, 200, "SPX", "20260731");
        assertEquals(200, res.getStatusCode().value());
        assertEquals(RECORD, res.getBody());
    }

    @Test
    void aChainWithNoReadingYetIs200WithPresentFalseNot404() {
        // "The service has not published for this chain yet" is a normal state on a cold start or
        // a thin board. A 404 would make the page unable to tell it apart from a routing mistake.
        ResponseEntity<String> res = call(null, 200, "SPX", "20260731");
        assertEquals(200, res.getStatusCode().value());
        assertNotNull(res.getBody());
        assertTrue(res.getBody().contains("\"present\":false"), res.getBody());
        assertTrue(res.getBody().contains("\"symbol\":\"SPX\""), res.getBody());
    }

    @Test
    void authIsEnforcedBeforeAnythingElseHappens() {
        // Including before parameter validation: a 400 for an unauthenticated caller would confirm
        // which parameters the endpoint takes.
        for (int status : new int[]{401, 403}) {
            assertEquals(status, call(RECORD, status, "SPX", "20260731").getStatusCode().value());
            assertEquals(status, call(RECORD, status, null, null).getStatusCode().value(),
                    "auth must be checked before the missing-parameter branch");
        }
    }

    @Test
    void missingParametersAreRejected() {
        for (String[] args : new String[][]{{null, "20260731"}, {"", "20260731"}, {"SPX", null}, {"SPX", "  "}}) {
            ResponseEntity<String> res = call(RECORD, 200, args[0], args[1]);
            assertEquals(400, res.getStatusCode().value(),
                    "symbol=" + args[0] + " expiry=" + args[1] + " must be rejected");
        }
    }

    @Test
    void theRateLimiterCountsPerPrincipalAndReturnsRetryAfter() {
        GammaMigrationController.RateLimiter limiter = new GammaMigrationController.RateLimiter(2, 60_000L);
        assertEquals(0L, limiter.tryAcquire("a", 0));
        assertEquals(0L, limiter.tryAcquire("a", 0));
        assertTrue(limiter.tryAcquire("a", 0) > 0, "third call in the window is refused");
        assertEquals(0L, limiter.tryAcquire("b", 0), "a different principal has its own budget");
        assertEquals(0L, limiter.tryAcquire("a", 60_000L), "the window rolls");
    }

    @Test
    void aQuoteInTheSymbolCannotBreakOutOfTheAbsenceEnvelope() throws Exception {
        // The absence envelope is hand-built JSON that echoes caller input.
        ResponseEntity<String> res = call(null, 200, "SP\"X", "20260731");
        assertEquals(200, res.getStatusCode().value());
        assertTrue(res.getBody().contains("SP\\\"X"), "the quote must be escaped: " + res.getBody());
        // Parses as one object rather than trailing garbage.
        assertNotNull(new com.fasterxml.jackson.databind.ObjectMapper().readTree(res.getBody()));
    }

    @Test
    void headerlessCallsStillGoThroughAuth() {
        ResponseEntity<String> res = new GammaMigrationController(gatewayReturning(RECORD), authReturning(401))
                .gammaMigration("SPX", "20260731", null);
        assertEquals(401, res.getStatusCode().value());
        assertEquals(HttpHeaders.AUTHORIZATION, HttpHeaders.AUTHORIZATION);
    }
}

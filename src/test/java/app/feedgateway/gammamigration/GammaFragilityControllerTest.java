package app.feedgateway.gammamigration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.feedgateway.FeedGatewayService;
import app.feedgateway.liquidityhistory.LiquidityHistoryAuth;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;

/**
 * Behavioural tests for {@code GET /api/gamma-fragility}.
 *
 * <p>Same edges as its sibling — auth first, absence distinguishable from a bad request, the
 * record unreshaped — plus the one that is specific to this topic: it reads a DIFFERENT cache.
 * Both endpoints live in one class hierarchy and take the same parameters, so pointing this one
 * at {@code cachedGammaMigration} would compile, pass a casual read, and serve the wrong record
 * under the right name.
 */
class GammaFragilityControllerTest {

    private static final String RECORD =
            "{\"messageType\":\"GAMMA_LEADER_FRAGILITY\",\"symbol\":\"SPX\",\"expiry\":\"20260731\","
            + "\"sessionStartMs\":1786714200000,\"rotationCount\":7,\"movesTruncated\":false,"
            + "\"windows\":[{\"moves\":1,\"netPts\":-10.0,\"grossPts\":10.0,\"fromStrike\":7760.0,"
            + "\"toStrike\":7750.0,\"strikeKnown\":true,\"spotMovePts\":-2.5,\"spotSpanSec\":69,"
            + "\"spotKnown\":true,\"complete\":true}],"
            + "\"moves\":[{\"offsetSec\":300,\"fromStrike\":7760.0,\"toStrike\":7750.0}]}";

    private static final String MIGRATION_RECORD =
            "{\"messageType\":\"GAMMA_MIGRATION_SNAPSHOT\",\"regime\":\"PEAK_PARKED\"}";

    private static FeedGatewayService gatewayReturning(String cached) {
        return gatewayReturning(cached, new String[]{"SPX", "20260731"});
    }

    private static FeedGatewayService gatewayReturning(String cached, String[] activeSelection) {
        FeedGatewayService service = mock(FeedGatewayService.class);
        when(service.cachedGammaFragility(any(), any())).thenReturn(cached);
        // Deliberately DIFFERENT, so a controller reading the wrong cache is caught rather than
        // silently passing on an identical stub.
        when(service.cachedGammaMigration(any(), any())).thenReturn(MIGRATION_RECORD);
        when(service.activeSymbolExpiry()).thenReturn(activeSelection);
        return service;
    }

    private static LiquidityHistoryAuth authReturning(int status) {
        LiquidityHistoryAuth auth = mock(LiquidityHistoryAuth.class);
        when(auth.authenticate(any())).thenReturn(new LiquidityHistoryAuth.Result(status, "tester"));
        return auth;
    }

    private static ResponseEntity<String> call(String cached, int authStatus, String symbol, String expiry) {
        return new GammaFragilityController(gatewayReturning(cached), authReturning(authStatus),
                new com.fasterxml.jackson.databind.ObjectMapper())
                .gammaFragility(symbol, expiry, "Bearer t");
    }

    @Test
    void theROTATIONcacheIsTheOneItReads() {
        // The two endpoints take the same parameters and live side by side, so reading the wrong
        // cache compiles and serves a plausible record under the wrong name.
        FeedGatewayService service = gatewayReturning(RECORD);
        ResponseEntity<String> res = new GammaFragilityController(service, authReturning(200),
                new com.fasterxml.jackson.databind.ObjectMapper())
                .gammaFragility("SPX", "20260731", "Bearer t");

        assertEquals(RECORD, res.getBody(), "the rotation record must be served verbatim");
        verify(service).cachedGammaFragility("SPX", "20260731");
        verify(service, never()).cachedGammaMigration(any(), any());
    }

    @Test
    void aChainWhosePeakNeverMovedIs200WithPresentFalseNot404() {
        // Absence is NORMAL on this topic in a way it is not for the snapshot: it only speaks when
        // the peak has actually moved, so a board that sat still all session has published
        // nothing. The page must be able to render "no moves yet" rather than an error.
        ResponseEntity<String> res = call(null, 200, "SPX", "20260731");
        assertEquals(200, res.getStatusCode().value());
        assertNotNull(res.getBody());
        assertTrue(res.getBody().contains("\"present\":false"), res.getBody());
        assertTrue(res.getBody().contains("\"symbol\":\"SPX\""), res.getBody());
    }

    @Test
    void authIsEnforcedBeforeTheCacheIsTouchedAtAll() {
        FeedGatewayService service = gatewayReturning(RECORD);
        ResponseEntity<String> res = new GammaFragilityController(service, authReturning(401),
                new com.fasterxml.jackson.databind.ObjectMapper())
                .gammaFragility("SPX", "20260731", null);

        assertEquals(401, res.getStatusCode().value());
        verify(service, never()).cachedGammaFragility(any(), any());
    }

    @Test
    void anUnentitledCallerIs403() {
        assertEquals(403, call(RECORD, 403, "SPX", "20260731").getStatusCode().value());
    }

    @Test
    void missingParametersFallBackToTheChainTheAppIsOn() {
        // Opening the page plainly must work: the card is about the CURRENT board.
        FeedGatewayService service = gatewayReturning(RECORD, new String[]{"SPX", "20260814"});
        ResponseEntity<String> res = new GammaFragilityController(service, authReturning(200),
                new com.fasterxml.jackson.databind.ObjectMapper())
                .gammaFragility(null, null, "Bearer t");

        assertEquals(200, res.getStatusCode().value());
        verify(service).cachedGammaFragility("SPX", "20260814");
    }

    @Test
    void anExplicitChainStillWinsOverTheActiveSelection() {
        FeedGatewayService service = gatewayReturning(RECORD, new String[]{"SPX", "20260814"});
        new GammaFragilityController(service, authReturning(200),
                new com.fasterxml.jackson.databind.ObjectMapper())
                .gammaFragility("SPX", "20260731", "Bearer t");
        verify(service).cachedGammaFragility("SPX", "20260731");
    }

    @Test
    void aGatewayWithNoSelectionYetIsStillA400() {
        // The one genuine "nothing to show": the gateway itself does not know which chain is live.
        for (String[] selection : new String[][]{{null, null}, {"", ""}}) {
            FeedGatewayService service = gatewayReturning(RECORD, selection);
            ResponseEntity<String> res = new GammaFragilityController(service, authReturning(200),
                    new com.fasterxml.jackson.databind.ObjectMapper())
                    .gammaFragility(null, null, "Bearer t");
            assertEquals(400, res.getStatusCode().value());
            assertTrue(res.getBody().contains("no active selection"), res.getBody());
        }
    }

    @Test
    void theRateLimiterIsPerPrincipalAndSaysWhenToComeBack() {
        GammaMigrationController.RateLimiter limiter =
                new GammaMigrationController.RateLimiter(2, 60_000L);
        assertEquals(0L, limiter.tryAcquire("a", 0L));
        assertEquals(0L, limiter.tryAcquire("a", 1L));
        assertTrue(limiter.tryAcquire("a", 2L) > 0, "the third call in the window must be refused");
        assertEquals(0L, limiter.tryAcquire("b", 2L), "…but a different principal has its own budget");
        assertEquals(0L, limiter.tryAcquire("a", 60_001L), "and the window rolls");
    }

    @Test
    void aThrottledCallerGetsRetryAfterRatherThanASilent429() {
        GammaFragilityController controller = new GammaFragilityController(
                gatewayReturning(RECORD), authReturning(200),
                new com.fasterxml.jackson.databind.ObjectMapper());
        ResponseEntity<String> last = null;
        for (int i = 0; i < 130; i++) {
            last = controller.gammaFragility("SPX", "20260731", "Bearer t");
        }
        assertEquals(429, last.getStatusCode().value());
        assertNotNull(last.getHeaders().getFirst(HttpHeaders.RETRY_AFTER),
                "a 429 without Retry-After tells the page nothing about when to try again");
    }
}

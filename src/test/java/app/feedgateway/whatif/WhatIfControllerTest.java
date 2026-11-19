package app.feedgateway.whatif;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import app.feedgateway.FeedGatewayService;
import app.feedgateway.liquidityhistory.LiquidityHistoryAuth;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

/**
 * {@code GET /api/what-if} — the board on a route of its own.
 *
 * <p>The test that matters here is {@link #theBoardIsServedWithNoMigrationRecord()}: the whole
 * reason this route exists is that the board was unreachable without an active gamma-migration
 * record, and the owner saw an empty page because of it.
 */
class WhatIfControllerTest {

    /** A map record as the map service publishes it, shortened to the fields the page draws. */
    private static final String RECORD =
            "{\"service\":\"A\",\"symbol\":\"SPX\",\"expiry\":\"20261119\",\"spot\":7181.09,"
            + "\"minutesToExpiry\":58.59,\"strikes\":[{\"strikeCents\":718000,"
            + "\"board\":{\"netGex\":-2.6e8,\"holdOrAccelerate\":\"ACCELERATE\",\"strength\":0.46},"
            + "\"deltaBand\":{\"lowerEdge\":7011.2,\"upperEdge\":7189.4}},"
            + "{\"strikeCents\":760000,\"board\":{\"netGex\":1.0e6,"
            + "\"holdOrAccelerate\":\"NEUTRAL\",\"strength\":0.01}}]}";

    private static FeedGatewayService gatewayReturning(String cached) {
        FeedGatewayService service = mock(FeedGatewayService.class);
        when(service.cachedWhatIfMap(any(), any())).thenReturn(cached);
        when(service.activeSymbolExpiry()).thenReturn(new String[]{"SPX", "20261119"});
        return service;
    }

    private static LiquidityHistoryAuth authReturning(int status) {
        LiquidityHistoryAuth auth = mock(LiquidityHistoryAuth.class);
        when(auth.authenticate(any()))
                .thenReturn(new LiquidityHistoryAuth.Result(status, "tester"));
        return auth;
    }

    private static ResponseEntity<String> call(String cached, int authStatus) {
        return new WhatIfController(gatewayReturning(cached), authReturning(authStatus),
                new ObjectMapper()).whatIf(null, null, "Bearer t");
    }

    @Test
    @DisplayName("The board is served when the gateway holds NO gamma-migration record")
    void theBoardIsServedWithNoMigrationRecord() {
        // The gateway mock answers cachedWhatIfMap and NOTHING else: cachedGammaMigration is never
        // consulted, which is the point. On /api/gamma-migration the same state returned
        // {"present": false} before merge was ever reached, so the four columns could not render.
        ResponseEntity<String> response = call(RECORD, 200);

        assertEquals(200, response.getStatusCode().value());
        assertEquals(RECORD, response.getBody(), "served VERBATIM: no reshaping, no second owner");
        assertTrue(response.getBody().contains("\"strikes\""), "the ladder's own rows");
        assertFalse(response.getBody().contains("present"),
                "a real record is the record, not an envelope around it");
    }

    @Test
    @DisplayName("C-43: all strikes travel, and a strike with no band carries no band key")
    void everyStrikeTravelsAndAnAbsentBandIsAbsent() {
        String body = call(RECORD, 200).getBody();
        assertTrue(body.contains("718000") && body.contains("760000"),
                "both strikes, including the far one: C-43's all-strikes rule");
        // the second strike has no deltaBand at all -- absent, not a zero that reads as a level
        int firstBand = body.indexOf("deltaBand");
        assertEquals(firstBand, body.lastIndexOf("deltaBand"),
                "exactly one band in the payload: the strike without one carries no key");
    }

    @Test
    @DisplayName("An absence is 200 with present:false, never 404")
    void anAbsenceIsTwoHundred() {
        ResponseEntity<String> response = call(null, 200);
        assertEquals(200, response.getStatusCode().value(),
                "a cold start is a normal state and a page must tell it from a routing mistake");
        assertTrue(response.getBody().contains("\"present\":false"), response.getBody());
        assertTrue(response.getBody().contains("SPX"), "and it echoes the chain it is about");
    }

    @Test
    @DisplayName("Auth is the same gate as every other route: 401 and 403 pass straight through")
    void authIsTheSameGate() {
        assertEquals(401, call(RECORD, 401).getStatusCode().value());
        assertEquals(403, call(RECORD, 403).getStatusCode().value());
    }
}

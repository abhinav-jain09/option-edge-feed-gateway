package app.feedgateway;

import static app.feedgateway.EsLiquidityWallFixtures.NOW;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.feedgateway.liquidityhistory.LiquidityHistoryAuth;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

/** {@code GET /api/es-liquidity-history}: 404 when the relay is off, the projection when on, only the held trade date. */
class EsLiquidityWallHistoryControllerTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    /** Both auth switches off: the open local-dev mode of every /api read (the bearer path is LiquidityHistoryAuth's, tested there). */
    private static final LiquidityHistoryAuth OPEN = new LiquidityHistoryAuth(new GatewaySettings(), null);

    @Test
    void aDisabledRelayAnswersNotFound() {
        EsLiquidityWallHistoryController c = new EsLiquidityWallHistoryController(() -> false, new EsLiquidityWallHistory(), OPEN);
        assertEquals(404, c.serve(null, null, NOW).getStatusCode().value());
    }

    @Test
    void theProjectionIsServedAsJsonForTheHeldTradeDateOnly() throws Exception {
        EsLiquidityWallHistory history = new EsLiquidityWallHistory();
        history.foldDashboard(EsLiquidityWallFixtures.dashboard(NOW));
        EsLiquidityWallHistoryController c = new EsLiquidityWallHistoryController(() -> true, history, OPEN);
        ResponseEntity<String> ok = c.serve(null, null, NOW + 100);
        assertEquals(200, ok.getStatusCode().value());
        assertEquals("no-store", ok.getHeaders().getCacheControl());
        JsonNode v = JSON.readTree(ok.getBody());
        assertEquals(1, v.get("minutes").size());
        assertEquals(NOW + 100, v.get("servedAtMs").longValue());
        assertEquals(200, c.serve(history.tradeDate().toString(), null, NOW + 100).getStatusCode().value());
        ResponseEntity<String> other = c.serve(history.tradeDate().minusDays(1).toString(), null, NOW + 100);
        assertEquals(404, other.getStatusCode().value());
        assertTrue(other.getBody().contains("only the current trade date is held"));
    }
}

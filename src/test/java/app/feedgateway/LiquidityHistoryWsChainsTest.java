package app.feedgateway;

import app.feedgateway.mtsession.ConcurrencyLimits;
import app.feedgateway.mtsession.MarketDataSource;
import app.feedgateway.mtsession.Selection;
import app.feedgateway.mtsession.SessionRoutingEngine;
import app.feedgateway.mtsession.StrikeWindow;
import app.feedgateway.mtsession.SubscriptionManager;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Codex Gate-2 finding 3 / AC15: the liquidity-history eviction-preference hook must protect the
 * chains of EVERY live per-session selection in multi-tenant mode, not just the single global
 * active selection — otherwise a chain another user is actively watching evicts first.
 */
class LiquidityHistoryWsChainsTest {

    private static SessionRoutingEngine engineWithTwoSessions() {
        SessionRoutingEngine engine =
                new SessionRoutingEngine(new ConcurrencyLimits(5, 5, 100), new SubscriptionManager());
        engine.registerAppSession("app:u1", "u1",
                new Selection(MarketDataSource.DATABENTO, "SPX", "20260612", StrikeWindow.ALL), Set.of());
        engine.registerAppSession("app:u2", "u2",
                new Selection(MarketDataSource.DATABENTO, "SPX", "20260620", StrikeWindow.ALL), Set.of());
        return engine;
    }

    @Test
    void perSessionModeProtectsTheDistinctChainsOfAllLiveSessions() {
        FeedGatewayService service = new FeedGatewayService(new GatewaySettings(), new ObjectMapper(),
                new HpsfGatewayViewMapper(), engineWithTwoSessions());
        assertEquals(Set.of("SPX|2026-06-12", "SPX|2026-06-20"), service.liquidityHistoryWsChains(),
                "both sessions' chains must be eviction-protected, in the store's symbol|ISO format");
    }

    @Test
    void perSessionModeWithNoLiveSessionsProtectsNothing() {
        SessionRoutingEngine engine =
                new SessionRoutingEngine(new ConcurrencyLimits(5, 5, 100), new SubscriptionManager());
        FeedGatewayService service = new FeedGatewayService(new GatewaySettings(), new ObjectMapper(),
                new HpsfGatewayViewMapper(), engine);
        assertEquals(Set.of(), service.liquidityHistoryWsChains());
    }

    @Test
    void legacyBroadcastModeWithoutClientsProtectsNothing() {
        FeedGatewayService service = new FeedGatewayService(new GatewaySettings(), new ObjectMapper(),
                new HpsfGatewayViewMapper(), null);
        assertEquals(Set.of(), service.liquidityHistoryWsChains(),
                "legacy path unchanged: no connected clients, no protected chains");
    }
}

package app.feedgateway;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import app.feedgateway.mtsession.ConcurrencyLimits;
import app.feedgateway.mtsession.MarketDataSource;
import app.feedgateway.mtsession.Selection;
import app.feedgateway.mtsession.SessionRoutingEngine;
import app.feedgateway.mtsession.StrikeWindow;
import app.feedgateway.mtsession.SubscriptionManager;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

/**
 * Per-session routing must NOT broadcast: malformed or non-allowlisted market-data events are
 * dropped, never fanned out, so they can never reach another user's socket. Only explicitly
 * allowlisted global lifecycle events may broadcast.
 */
class FeedGatewayPerSessionBroadcastTest {

    private SessionRoutingEngine engine;
    private FeedGatewayService svc;
    private final List<String> u1 = new ArrayList<>(); // user 1 socket s1 (SPX/20260612)
    private final List<String> u2 = new ArrayList<>(); // user 2 socket s2 (SPX/20260620)

    @BeforeEach
    void setUp() throws Exception {
        engine = new SessionRoutingEngine(new ConcurrencyLimits(5, 5, 100), new SubscriptionManager());
        engine.registerAppSession("app:u1", "u1",
                new Selection(MarketDataSource.DATABENTO, "SPX", "20260612", StrikeWindow.ALL), Set.of());
        engine.attachSocket("app:u1", "s1");
        engine.registerAppSession("app:u2", "u2",
                new Selection(MarketDataSource.DATABENTO, "SPX", "20260620", StrikeWindow.ALL), Set.of());
        engine.attachSocket("app:u2", "s2");

        svc = new FeedGatewayService(new GatewaySettings(), new ObjectMapper(), new HpsfGatewayViewMapper(), engine);
        svc.addClient(socket("s1", u1));
        svc.addClient(socket("s2", u2));
        u1.clear();
        u2.clear(); // discard the initial status/cache-replay sent on connect
    }


    private WebSocketSession socket(String id, List<String> sink) throws Exception {
        WebSocketSession ws = mock(WebSocketSession.class);
        when(ws.getId()).thenReturn(id);
        when(ws.isOpen()).thenReturn(true);
        doAnswer(inv -> {
            sink.add(((TextMessage) inv.getArgument(0)).getPayload());
            return null;
        }).when(ws).sendMessage(any());
        return ws;
    }

    private void routeOrBroadcast(String source, String event, String json) throws Exception {
        Method m = FeedGatewayService.class.getDeclaredMethod("routeOrBroadcast", String.class, String.class, String.class);
        m.setAccessible(true);
        m.invoke(svc, source, event, json);
    }

    private void broadcast(String event, String json) throws Exception {
        Method m = FeedGatewayService.class.getDeclaredMethod("broadcast", String.class, String.class);
        m.setAccessible(true);
        m.invoke(svc, event, json);
    }

    @Test
    void malformedMarketDataIsDroppedNotBroadcast() throws Exception {
        for (String event : List.of("snapshot", "pace", "strike-flow")) {
            u1.clear();
            u2.clear();
            routeOrBroadcast("DATABENTO", event, "{ not valid json :: ");
            assertTrue(u1.isEmpty(), "malformed " + event + " leaked to its own user");
            assertTrue(u2.isEmpty(), "malformed " + event + " leaked to OTHER user");
        }
        assertTrue(svc.healthJson().contains("\"droppedNonRoutableEvents\":"));
    }

    @Test
    void wellFormedSnapshotRoutesOnlyToTheMatchingUser() throws Exception {
        routeOrBroadcast("DATABENTO", "snapshot",
                "{\"symbol\":\"SPX\",\"expiry\":\"20260612\",\"strike\":7500}");
        assertEquals(1, u1.size());     // u1 selected SPX/20260612
        assertTrue(u2.isEmpty());       // u2 is on 20260620 — no cross-user leak
    }

    @Test
    void broadcastSuppressesMarketDataButAllowsGlobalLifecycleEvents() throws Exception {
        broadcast("snapshot", "{\"symbol\":\"SPX\",\"expiry\":\"20260612\",\"strike\":7500}");
        assertTrue(u1.isEmpty(), "snapshot must not broadcast in per-session mode");
        assertTrue(u2.isEmpty(), "snapshot must not broadcast in per-session mode");

        broadcast("status", "{\"ok\":true}");
        assertEquals(1, u1.size(), "allowlisted global event reaches all sockets");
        assertEquals(1, u2.size(), "allowlisted global event reaches all sockets");
    }

    @Test
    void databentoBindingWithIbkrPayloadIsRejectedAndNotDelivered() throws Exception {
        // Misrouted record: Databento topic binding, but the payload declares IBKR. u1 is a Databento
        // SPX/20260612 session — it must NOT receive this source-mismatched record.
        routeOrBroadcast("DATABENTO", "snapshot",
                "{\"symbol\":\"SPX\",\"expiry\":\"20260612\",\"strike\":7500,\"marketDataSource\":\"IBKR\"}");
        assertTrue(u1.isEmpty(), "source-mismatched record must not reach the binding-source user");
        assertTrue(u2.isEmpty(), "source-mismatched record must not reach any user");

        // Positive control: the same record with a matching source IS delivered to u1.
        routeOrBroadcast("DATABENTO", "snapshot",
                "{\"symbol\":\"SPX\",\"expiry\":\"20260612\",\"strike\":7500,\"marketDataSource\":\"DATABENTO\"}");
        assertEquals(1, u1.size());
        assertTrue(u2.isEmpty());
    }

    // ---- P0: HPSF signals/audit must route per-session, never via the all-client batch ----

    private void routeHpsf(String event, String key, String json, String expiry) throws Exception {
        Class<?> updateCls = Class.forName("app.feedgateway.FeedGatewayService$HpsfCacheUpdate");
        Constructor<?> ctor = updateCls.getDeclaredConstructor(
                String.class, String.class, String.class, String.class);
        ctor.setAccessible(true);
        Object update = ctor.newInstance(event, key, json, expiry);
        Method m = FeedGatewayService.class.getDeclaredMethod("routeHpsfPerSession", updateCls);
        m.setAccessible(true);
        m.invoke(svc, update);
    }

    private void enqueuePending(String event, String key, String json) throws Exception {
        Method m = FeedGatewayService.class.getDeclaredMethod(
                "enqueuePending", String.class, String.class, String.class);
        m.setAccessible(true);
        m.invoke(svc, event, key, json);
    }

    private void flushPendingBatch() throws Exception {
        Method m = FeedGatewayService.class.getDeclaredMethod("flushPendingBatch");
        m.setAccessible(true);
        m.invoke(svc);
    }

    @Test
    void hpsfSignalRoutesOnlyToTheMatchingExpirySession() throws Exception {
        // hpsf-latest-signal for the 20260612 chain — only u1 selected that expiry.
        routeHpsf("hpsf-latest-signal", "20260612", "{\"action\":\"BUY_CALL_CONFIRMED\"}", "20260612");
        assertEquals(1, u1.size(), "HPSF signal must reach the session on its chain");
        assertTrue(u2.isEmpty(), "HPSF signal must NOT leak to a session on a different expiry");
    }

    @Test
    void hpsfAuditAndExitIntentRouteByExpiry() throws Exception {
        // Audit + exit-intent carry sensitive decision internals — they must follow the same routing.
        routeHpsf("hpsf-audit", "20260620", "{\"selectedAction\":\"NO_TRADE\"}", "20260620");
        routeHpsf("hpsf-exit-intent", "20260620", "{\"exitAction\":\"EXIT_NOW\"}", "20260620");
        assertEquals(2, u2.size(), "audit + exit-intent reach the matching session");
        assertTrue(u1.isEmpty(), "audit/exit-intent must NOT leak to another tenant's session");
    }

    @Test
    void hpsfMarketFlowRoutesToEverySessionOfTheUnderlying() throws Exception {
        // Market-flow is a whole-underlying summary (no expiry) — both SPX sessions receive it, but it
        // still flows through the engine + entitlement checks (not the all-client batch).
        routeHpsf("hpsf-market-flow", "SPX", "{\"marketBias\":\"BULLISH\"}", null);
        assertEquals(1, u1.size());
        assertEquals(1, u2.size());
    }

    @Test
    void hpsfWithoutExpiryIsDroppedNotBroadcast() throws Exception {
        // A contract-scoped HPSF event with no chain key cannot be routed — it must be dropped, never
        // fanned out to every socket.
        routeHpsf("hpsf-latest-signal", "k", "{\"action\":\"NO_TRADE\"}", "  ");
        assertTrue(u1.isEmpty(), "unroutable HPSF event must not reach any session");
        assertTrue(u2.isEmpty(), "unroutable HPSF event must not broadcast");
    }

    @Test
    void allClientBatchPathIsUnreachableInTenantMode() throws Exception {
        // Even if something reaches the legacy batch, enqueue is a no-op and the flush sends nothing in
        // per-session mode — the cross-tenant broadcast path is dead.
        enqueuePending("hpsf-latest-signal", "20260612", "{\"action\":\"BUY_CALL_CONFIRMED\"}");
        enqueuePending("snapshot", "20260612|7500", "{\"symbol\":\"SPX\",\"expiry\":\"20260612\"}");
        flushPendingBatch();
        assertTrue(u1.isEmpty(), "batch flush must send nothing in tenant mode");
        assertTrue(u2.isEmpty(), "batch flush must send nothing in tenant mode");
    }

    @Test
    void allowlistClassifiesMarketDataAsNonGlobal() {
        assertTrue(FeedGatewayService.isGlobalBroadcastEvent("status"));
        assertTrue(FeedGatewayService.isGlobalBroadcastEvent("reset"));
        assertTrue(FeedGatewayService.isGlobalBroadcastEvent("source-switching"));
        assertFalse(FeedGatewayService.isGlobalBroadcastEvent("snapshot"));
        assertFalse(FeedGatewayService.isGlobalBroadcastEvent("pace"));
        assertFalse(FeedGatewayService.isGlobalBroadcastEvent("strike-flow"));
        assertFalse(FeedGatewayService.isGlobalBroadcastEvent("index-price"));
    }
}

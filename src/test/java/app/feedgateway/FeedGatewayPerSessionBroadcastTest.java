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
        svc.runOutboundWritesInline(); // synchronous delivery for deterministic assertions
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
    void dropNowcastBroadcastsToEverySocketInPerSessionMode() throws Exception {
        // drop-classifier SHADOW verdict: a GLOBAL advisory (identical for every user) —
        // it MUST be in GLOBAL_BROADCAST_EVENTS or authenticated prod silently drops it
        // (UI review r1 finding #1).
        broadcast("drop-nowcast",
                "{\"message_type\":\"NOWCAST\",\"mode\":\"SHADOW\","
                + "\"drop_id\":\"ES-20260812-093500484-S31096-DN\",\"eval_index\":1,"
                + "\"t_break_ms\":1786541700484,\"ref_level\":7774.0,"
                + "\"ref_kind\":\"SWING_LOW\",\"category_k1\":\"LIQUIDITY_SWEEP\"}");
        assertEquals(1, u1.size(), "drop-nowcast must reach every per-session socket");
        assertEquals(1, u2.size(), "drop-nowcast must reach every per-session socket");
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
    @SuppressWarnings("unchecked")
    void cachedVixReplaysAsVixPriceNotIndexPrice() throws Exception {
        // P1 (preserve original event type): a VIX entry in the cache must replay to a newly-connected socket
        // as a `vix-price` event — not flattened into `index-price` as before.
        java.lang.reflect.Field f = FeedGatewayService.class.getDeclaredField("vixPrices");
        f.setAccessible(true);
        ((java.util.Map<String, String>) f.get(svc)).put("IBKR|VIX", "{\"price\":15.5}");

        engine.registerAppSession("app:u3", "u3",
                new Selection(MarketDataSource.DATABENTO, "SPX", "20260612", StrikeWindow.ALL), Set.of());
        engine.attachSocket("app:u3", "s3");
        List<String> u3 = new ArrayList<>();
        svc.addClient(socket("s3", u3)); // triggers per-session cached replay

        assertTrue(u3.stream().anyMatch(m -> m.contains("\"type\":\"vix-price\"")),
                "cached VIX is replayed as vix-price");
        assertFalse(u3.stream().anyMatch(m -> m.contains("\"type\":\"index-price\"") && m.contains("15.5")),
                "cached VIX must NOT be replayed mislabelled as index-price");
    }

    @Test
    void sharedVixReachesDatabentoSessionsEvenThoughItIsBoundToIbkr() throws Exception {
        // P1: VIX is bound to the IBKR topic, but it is a SHARED underlying. Both DATABENTO sessions (u1, u2)
        // must receive it — the previous bug indexed them under DATABENTO|VIX while VIX routed to IBKR|VIX.
        routeOrBroadcast("IBKR", "vix-price", "{\"price\":15.5}");
        assertEquals(1, u1.size(), "DATABENTO session u1 receives the shared VIX");
        assertEquals(1, u2.size(), "DATABENTO session u2 receives the shared VIX");
    }

    @Test
    void allowlistClassifiesMarketDataAsNonGlobal() {
        assertTrue(FeedGatewayService.isGlobalBroadcastEvent("status"));
        assertTrue(FeedGatewayService.isGlobalBroadcastEvent("reset"));
        assertTrue(FeedGatewayService.isGlobalBroadcastEvent("source-switching"));
        // Discrete spread-skew transitions are a global one-shot alert (turn-alert sibling) — allowlisted
        // so they are not silently dropped in per-session mode; the SNAPSHOT stays routed market data.
        assertTrue(FeedGatewayService.isGlobalBroadcastEvent("spread-skew-event"));
        // Server-rated Δ-flow acceleration: one chain-global frame per second from the web tier;
        // without the allowlist entry, per-session (auth) mode drops every frame as non-routable —
        // the exact defect that once silenced es-cvd.
        assertTrue(FeedGatewayService.isGlobalBroadcastEvent("delta-flow-accel"));
        assertFalse(FeedGatewayService.isGlobalBroadcastEvent("spread-skew"));
        assertFalse(FeedGatewayService.isGlobalBroadcastEvent("snapshot"));
        assertFalse(FeedGatewayService.isGlobalBroadcastEvent("pace"));
        assertFalse(FeedGatewayService.isGlobalBroadcastEvent("strike-flow"));
        assertFalse(FeedGatewayService.isGlobalBroadcastEvent("index-price"));
    }

    @Test
    void perSessionLiveMissionPaceRoutesOnlyToTheMatchingMarket() throws Exception {
        // Per-session live mission-pace is contract-scoped (source|symbol|expiry): it reaches only the
        // session that selected that exact market, with no cross-market leak.
        routeOrBroadcast("DATABENTO", "mission-pace",
                "{\"eventType\":\"mission-pace\",\"symbol\":\"SPX\",\"expiry\":\"20260612\","
                        + "\"spot\":6004.8,\"rankedStrikes\":[]}");
        assertEquals(1, u1.size(), "mission-pace reaches the SPX|20260612 session");
        assertTrue(u2.isEmpty(), "mission-pace must not leak to the SPX|20260620 session");
    }

    @Test
    @SuppressWarnings("unchecked")
    void perSessionCachedMissionPaceReplaysOnlyToTheMatchingMarketOnConnect() throws Exception {
        java.lang.reflect.Field f = FeedGatewayService.class.getDeclaredField("missionPaces");
        f.setAccessible(true);
        ((java.util.Map<String, String>) f.get(svc)).put("DATABENTO|SPX|20260612",
                "{\"eventType\":\"mission-pace\",\"symbol\":\"SPX\",\"expiry\":\"20260612\",\"rankedStrikes\":[]}");
        java.lang.reflect.Field times = FeedGatewayService.class.getDeclaredField("cacheEventTimes");
        times.setAccessible(true);
        ((java.util.Map<String, Long>) times.get(svc)).put("mission-pace:DATABENTO|SPX|20260612",
                System.currentTimeMillis());

        engine.registerAppSession("app:u3", "u3",
                new Selection(MarketDataSource.DATABENTO, "SPX", "20260612", StrikeWindow.ALL), Set.of());
        engine.attachSocket("app:u3", "s3");
        List<String> u3 = new ArrayList<>();
        svc.addClient(socket("s3", u3)); // triggers per-session cached replay

        assertTrue(u3.stream().anyMatch(m -> m.contains("\"type\":\"mission-pace\"")),
                "fresh cached mission-pace must replay to the matching per-session market on connect");
        assertTrue(u2.stream().noneMatch(m -> m.contains("\"type\":\"mission-pace\"")),
                "cached mission-pace must not leak to a different selected expiry");
    }

    @Test
    void perSessionLiveMissionControlRoutesOnlyToTheMatchingMarket() throws Exception {
        // Per-session live mission-control is contract-scoped (source|symbol|expiry): it reaches only the
        // session that selected that exact market, with no cross-market leak.
        routeOrBroadcast("DATABENTO", "mission-control",
                "{\"eventType\":\"mission-control\",\"symbol\":\"SPX\",\"expiry\":\"20260612\","
                        + "\"spot\":6004.8,\"rankedStrikes\":[]}");
        assertEquals(1, u1.size(), "mission-control reaches the SPX|20260612 session");
        assertTrue(u2.isEmpty(), "mission-control must not leak to the SPX|20260620 session");
    }

    @Test
    @SuppressWarnings("unchecked")
    void perSessionCachedMissionControlIsNotReplayedOnConnect() throws Exception {
        // mission-control cached frames carry no per-session selectionEpoch, so they are deliberately NOT
        // cache-replayed on connect (a newly attached socket bootstraps from the next live frame).
        java.lang.reflect.Field f = FeedGatewayService.class.getDeclaredField("missionControls");
        f.setAccessible(true);
        ((java.util.Map<String, String>) f.get(svc)).put("DATABENTO|SPX|20260612",
                "{\"eventType\":\"mission-control\",\"symbol\":\"SPX\",\"expiry\":\"20260612\",\"rankedStrikes\":[]}");

        engine.registerAppSession("app:u4", "u4",
                new Selection(MarketDataSource.DATABENTO, "SPX", "20260612", StrikeWindow.ALL), Set.of());
        engine.attachSocket("app:u4", "s4");
        List<String> u4 = new ArrayList<>();
        svc.addClient(socket("s4", u4)); // triggers per-session cached replay

        assertFalse(u4.stream().anyMatch(m -> m.contains("\"type\":\"mission-control\"")),
                "cached mission-control must NOT be replayed on connect in per-session mode");
    }

    // ---- spread-skew in per-session (auth) mode: global event broadcast + nullable-expiry routing ----

    @Test
    void spreadSkewEventBroadcastsToEverySocketInPerSessionMode() throws Exception {
        // The discrete transition is a GLOBAL one-shot alert (turn-alert sibling, symbol-filtered
        // client-side): with per-session routing ENABLED it must fan out via the
        // GLOBAL_BROADCAST_EVENTS allowlist rather than being dropped as non-routable market data.
        broadcast("spread-skew-event",
                "{\"underlying\":\"SPX\",\"expiry\":\"2026-06-12\",\"ts\":1,"
                        + "\"eventId\":\"e-1\",\"transitionType\":\"FIRE\",\"newState\":\"CALL_SKEW\"}");
        assertEquals(1, u1.size(), "spread-skew-event must reach user 1 in per-session mode");
        assertEquals(1, u2.size(), "spread-skew-event must reach user 2 in per-session mode");
        assertTrue(u1.get(0).contains("\"type\":\"spread-skew-event\""),
                "delivered standalone under its own message.type");
    }

    @Test
    void perSessionLiveSpreadSkewRoutesByExpiryAndNullExpiryReachesEveryUnderlyingSession() throws Exception {
        // Present matching expiry: contract-scoped — only the SPX|20260612 session receives it.
        routeOrBroadcast("DATABENTO", "spread-skew",
                "{\"underlying\":\"SPX\",\"expiry\":\"2026-06-12\",\"ts\":1,\"headline\":{\"state\":\"CALL_SKEW\"}}");
        assertEquals(1, u1.size(), "spread-skew reaches the SPX|20260612 session");
        assertTrue(u2.isEmpty(), "spread-skew must not leak to the SPX|20260620 session");

        // NULL expiry (EXPIRY_MISSING / degraded heartbeat): routes by source+underlying — BOTH SPX
        // sessions receive it instead of the frame being silently dropped as a blank contract key.
        u1.clear();
        u2.clear();
        routeOrBroadcast("DATABENTO", "spread-skew",
                "{\"underlying\":\"SPX\",\"expiry\":null,\"ts\":2,\"degraded\":true,"
                        + "\"headline\":{\"state\":\"EXPIRY_MISSING\"}}");
        assertEquals(1, u1.size(), "null-expiry spread-skew must reach the SPX|20260612 session");
        assertEquals(1, u2.size(), "null-expiry spread-skew must reach the SPX|20260620 session");

        // Present MISMATCHING expiry: still contract-scoped — reaches neither session (no leak).
        u1.clear();
        u2.clear();
        routeOrBroadcast("DATABENTO", "spread-skew",
                "{\"underlying\":\"SPX\",\"expiry\":\"2026-06-13\",\"ts\":3,\"headline\":{\"state\":\"CALL_SKEW\"}}");
        assertTrue(u1.isEmpty(), "a different-expiry spread-skew must not reach the 20260612 session");
        assertTrue(u2.isEmpty(), "a different-expiry spread-skew must not reach the 20260620 session");
    }

    @Test
    @SuppressWarnings("unchecked")
    void perSessionCachedSpreadSkewWithNullExpiryReplaysOnConnect() throws Exception {
        // A fresh cached null-expiry snapshot (single-value slot DATABENTO|SPX) must replay to a
        // newly-connected session of that underlying — the reconnect/return-to-live bootstrap path.
        java.lang.reflect.Field f = FeedGatewayService.class.getDeclaredField("spreadSkews");
        f.setAccessible(true);
        ((java.util.Map<String, String>) f.get(svc)).put("DATABENTO|SPX",
                "{\"underlying\":\"SPX\",\"expiry\":null,\"ts\":1,\"degraded\":true,"
                        + "\"headline\":{\"state\":\"EXPIRY_MISSING\"}}");
        java.lang.reflect.Field times = FeedGatewayService.class.getDeclaredField("cacheEventTimes");
        times.setAccessible(true);
        ((java.util.Map<String, Long>) times.get(svc)).put("spread-skew:DATABENTO|SPX",
                System.currentTimeMillis());

        engine.registerAppSession("app:u5", "u5",
                new Selection(MarketDataSource.DATABENTO, "SPX", "20260612", StrikeWindow.ALL), Set.of());
        engine.attachSocket("app:u5", "s5");
        List<String> u5 = new ArrayList<>();
        svc.addClient(socket("s5", u5)); // triggers per-session cached replay

        assertTrue(u5.stream().anyMatch(m -> m.contains("\"type\":\"spread-skew\"")),
                "fresh cached null-expiry spread-skew must replay on connect in per-session mode");
    }

    @Test
    @SuppressWarnings("unchecked")
    void perSessionCachedSpreadSkewWithMismatchingExpiryIsNotReplayedOnConnect() throws Exception {
        // A cached snapshot pinned to a DIFFERENT chain stays contract-scoped: it must NOT replay to a
        // session viewing another expiry.
        java.lang.reflect.Field f = FeedGatewayService.class.getDeclaredField("spreadSkews");
        f.setAccessible(true);
        ((java.util.Map<String, String>) f.get(svc)).put("DATABENTO|SPX",
                "{\"underlying\":\"SPX\",\"expiry\":\"2026-06-13\",\"ts\":1,\"headline\":{\"state\":\"CALL_SKEW\"}}");
        java.lang.reflect.Field times = FeedGatewayService.class.getDeclaredField("cacheEventTimes");
        times.setAccessible(true);
        ((java.util.Map<String, Long>) times.get(svc)).put("spread-skew:DATABENTO|SPX",
                System.currentTimeMillis());

        engine.registerAppSession("app:u6", "u6",
                new Selection(MarketDataSource.DATABENTO, "SPX", "20260612", StrikeWindow.ALL), Set.of());
        engine.attachSocket("app:u6", "s6");
        List<String> u6 = new ArrayList<>();
        svc.addClient(socket("s6", u6)); // triggers per-session cached replay

        assertFalse(u6.stream().anyMatch(m -> m.contains("\"type\":\"spread-skew\"")),
                "a cached spread-skew for a different expiry must NOT replay to this session");
    }
}

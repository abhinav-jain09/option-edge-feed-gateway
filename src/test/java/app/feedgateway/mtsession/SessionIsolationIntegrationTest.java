package app.feedgateway.mtsession;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * End-to-end isolation tests proving the OE-DDD-001 invariants (I1 no cross-session leak,
 * I2 shared cache per contract, I3 IBKR identical fan-out) and the ref-counted subscription
 * lifecycle (FR-4, FR-7, FR-9, FR-10, NFR-3, NFR-7).
 */
class SessionIsolationIntegrationTest {

    private static final Set<String> DBNTO = Set.of();
    private static final Set<String> IBKR_ENT = Set.of(EntitlementPolicy.IBKR_USER);

    private SessionRoutingEngine engine() {
        return new SessionRoutingEngine(new ConcurrencyLimits(10, 10, 1000), new SubscriptionManager());
    }

    private static Selection dbnto(String symbol, String expiry, StrikeWindow w) {
        return new Selection(MarketDataSource.DATABENTO, symbol, expiry, w);
    }

    @Test
    @DisplayName("I1 — one user's selection never leaks to another (FR-4)")
    void noCrossSessionLeak() {
        SessionRoutingEngine e = engine();
        e.registerAppSession("a1", "u1", dbnto("SPX", "20260612", StrikeWindow.ALL), DBNTO);
        e.attachSocket("a1", "s1");
        e.registerAppSession("a2", "u2", dbnto("NDX", "20260620", StrikeWindow.ALL), DBNTO);
        e.attachSocket("a2", "s2");

        assertEquals(Set.of("s1"), e.route(RoutableRecord.contract(
                MarketDataSource.DATABENTO, EventType.PACE, "SPX", "20260612", 7500, 0)));
        assertEquals(Set.of("s2"), e.route(RoutableRecord.contract(
                MarketDataSource.DATABENTO, EventType.PACE, "NDX", "20260620", 18000, 0)));
    }

    @Test
    @DisplayName("Two SPX users with disjoint strike windows are isolated by the delivery filter")
    void strikeWindowIsolationOnSameContract() {
        SessionRoutingEngine e = engine();
        e.registerAppSession("a1", "u1", dbnto("SPX", "20260612", StrikeWindow.of(7000, 7400)), DBNTO);
        e.attachSocket("a1", "s1");
        e.registerAppSession("a2", "u2", dbnto("SPX", "20260612", StrikeWindow.of(7600, 8000)), DBNTO);
        e.attachSocket("a2", "s2");

        assertEquals(Set.of("s1"), e.route(RoutableRecord.contract(
                MarketDataSource.DATABENTO, EventType.SNAPSHOT, "SPX", "20260612", 7100, 0)));
        assertEquals(Set.of("s2"), e.route(RoutableRecord.contract(
                MarketDataSource.DATABENTO, EventType.SNAPSHOT, "SPX", "20260612", 7800, 0)));
        // a strike in neither window reaches nobody, though both share the upstream subscription
        assertTrue(e.route(RoutableRecord.contract(
                MarketDataSource.DATABENTO, EventType.SNAPSHOT, "SPX", "20260612", 7500, 0)).isEmpty());
        // ...and the upstream subscription is shared (one key, ref-count 2, union range)
        SubscriptionKey k = new SubscriptionKey(MarketDataSource.DATABENTO, "SPX", "20260612");
        assertEquals(2, e.subscriptions().refCount(k));
        assertEquals(StrikeWindow.of(7000, 8000), e.subscriptions().subscribedRange(k).orElseThrow());
        assertEquals(1, e.subscriptions().distinctSubscriptions());
    }

    @Test
    @DisplayName("I2 — shared cache holds one entry per contract regardless of subscriber count (NFR-3)")
    void sharedCacheIsPerContract() {
        SessionRoutingEngine e = engine();
        for (int i = 1; i <= 5; i++) {
            e.registerAppSession("a" + i, "u" + i, dbnto("SPX", "20260612", StrikeWindow.ALL), DBNTO);
            e.attachSocket("a" + i, "s" + i);
        }
        Set<String> out = e.route(RoutableRecord.contract(
                MarketDataSource.DATABENTO, EventType.SNAPSHOT, "SPX", "20260612", 7500, 0));
        assertEquals(5, out.size());                   // all five sockets served
        assertEquals(1, e.cache().size());             // ...from a single cache entry
        assertEquals(1, e.distinctContractIndexSize());
    }

    @Test
    @DisplayName("I3 — all IBKR users share one key and get identical fan-out (FR-7)")
    void ibkrIdenticalFanout() {
        SessionRoutingEngine e = engine();
        e.registerAppSession("a3", "u3", new Selection(MarketDataSource.IBKR, "SPX", "20260617", StrikeWindow.ALL), IBKR_ENT);
        e.attachSocket("a3", "s3");
        e.registerAppSession("a4", "u4", new Selection(MarketDataSource.IBKR, "SPX", "20260617", StrikeWindow.ALL), IBKR_ENT);
        e.attachSocket("a4", "s4");

        Set<String> out = e.route(RoutableRecord.contract(
                MarketDataSource.IBKR, EventType.SNAPSHOT, "SPX", "20260617", 7500, 0));
        assertEquals(Set.of("s3", "s4"), out);
        assertEquals(0, e.subscriptions().distinctSubscriptions(), "IBKR uses no Databento subscriptions");
    }

    @Test
    @DisplayName("Ref-count sharing: K users on one contract = one subscription; last release unsubscribes (FR-9/FR-10)")
    void refCountSharingAndRelease() {
        SessionRoutingEngine e = engine();
        SubscriptionKey k = new SubscriptionKey(MarketDataSource.DATABENTO, "SPX", "20260612");
        for (int i = 1; i <= 3; i++) {
            e.registerAppSession("a" + i, "u" + i, dbnto("SPX", "20260612", StrikeWindow.ALL), DBNTO);
        }
        assertEquals(3, e.subscriptions().refCount(k));
        assertEquals(1, e.subscriptions().distinctSubscriptions());

        e.teardownAppSession("a1");
        e.teardownAppSession("a2");
        assertEquals(1, e.subscriptions().refCount(k));
        e.teardownAppSession("a3");
        assertEquals(0, e.subscriptions().refCount(k));
        assertTrue(e.subscriptions().desiredSet().isEmpty());
    }

    @Test
    @DisplayName("Underlying events route by source/underlying, not by contract")
    void underlyingRouting() {
        SessionRoutingEngine e = engine();
        e.registerAppSession("a1", "u1", dbnto("SPX", "20260612", StrikeWindow.ALL), DBNTO);
        e.attachSocket("a1", "s1");
        e.registerAppSession("a2", "u2", dbnto("NDX", "20260620", StrikeWindow.ALL), DBNTO);
        e.attachSocket("a2", "s2");

        // VIX is relevant to every session of the source
        assertEquals(Set.of("s1", "s2"),
                e.route(RoutableRecord.underlying(MarketDataSource.DATABENTO, EventType.VIX_PRICE, 0)));
        // SPX index price only to the SPX session
        assertEquals(Set.of("s1"),
                e.route(RoutableRecord.underlying(MarketDataSource.DATABENTO, EventType.INDEX_PRICE, 0)));
    }

    @Test
    @DisplayName("Daily 0DTE roll migrates IBKR sessions to the new expiry (FR-8/§10.2)")
    void dailyRollMigratesIbkr() {
        SessionRoutingEngine e = engine();
        e.registerAppSession("a3", "u3", new Selection(MarketDataSource.IBKR, "SPX", "20260617", StrikeWindow.ALL), IBKR_ENT);
        e.attachSocket("a3", "s3");

        // before roll: today's expiry routes
        assertEquals(Set.of("s3"), e.route(RoutableRecord.contract(
                MarketDataSource.IBKR, EventType.SNAPSHOT, "SPX", "20260617", 7500, 0)));
        // roll to next 0DTE
        e.changeSelection("a3", new Selection(MarketDataSource.IBKR, "SPX", "20260618", StrikeWindow.ALL));
        assertTrue(e.route(RoutableRecord.contract(
                MarketDataSource.IBKR, EventType.SNAPSHOT, "SPX", "20260617", 7500, 0)).isEmpty());
        assertEquals(Set.of("s3"), e.route(RoutableRecord.contract(
                MarketDataSource.IBKR, EventType.SNAPSHOT, "SPX", "20260618", 7500, 0)));
    }

    @Test
    @DisplayName("Property: every delivery matches the receiving session's own selection (no leak across a mixed fleet)")
    void noLeakAcrossMixedFleet() {
        SessionRoutingEngine e = engine();
        // deterministic fleet: 3 symbols × 2 users each, one socket per user
        String[][] fleet = {
                {"SPX", "20260612"}, {"SPX", "20260612"},
                {"NDX", "20260620"}, {"NDX", "20260620"},
                {"RUT", "20260619"}, {"RUT", "20260619"},
        };
        Map<String, Selection> selByApp = new LinkedHashMap<>();
        for (int i = 0; i < fleet.length; i++) {
            String app = "a" + i;
            Selection sel = dbnto(fleet[i][0], fleet[i][1], StrikeWindow.ALL);
            e.registerAppSession(app, "u" + i, sel, DBNTO);
            e.attachSocket(app, "s" + i);
            selByApp.put("s" + i, sel);
        }

        for (String[] contract : List.of(new String[]{"SPX", "20260612"},
                new String[]{"NDX", "20260620"}, new String[]{"RUT", "20260619"})) {
            Set<String> delivered = e.route(RoutableRecord.contract(
                    MarketDataSource.DATABENTO, EventType.PACE, contract[0], contract[1], 7500, 0));
            assertFalse(delivered.isEmpty());
            // invariant: every delivered socket's session matches the record's contract exactly
            for (String socket : delivered) {
                Selection sel = selByApp.get(socket);
                assertEquals(Normalization.symbol(contract[0]), sel.symbol());
                assertEquals(Normalization.expiry(contract[1]), sel.expiry());
            }
        }
    }

    @Test
    @DisplayName("Multi-tab: two sockets on one AppSession both receive its data")
    void multiTabBothSocketsServed() {
        SessionRoutingEngine e = engine();
        e.registerAppSession("a1", "u1", dbnto("SPX", "20260612", StrikeWindow.ALL), DBNTO);
        e.attachSocket("a1", "tab1");
        e.attachSocket("a1", "tab2");
        assertEquals(Set.of("tab1", "tab2"), e.route(RoutableRecord.contract(
                MarketDataSource.DATABENTO, EventType.PACE, "SPX", "20260612", 7500, 0)));
    }
}

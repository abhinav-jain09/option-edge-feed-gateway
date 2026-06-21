package app.feedgateway.mtsession;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import org.junit.jupiter.api.Test;

class SessionRoutingEngineTest {

    private static final Set<String> DBNTO = Set.of();
    private static final Set<String> IBKR = Set.of(EntitlementPolicy.IBKR_USER);

    private SessionRoutingEngine engine(ConcurrencyLimits limits) {
        return new SessionRoutingEngine(limits, new SubscriptionManager());
    }

    private SessionRoutingEngine engine() {
        return engine(new ConcurrencyLimits(5, 5, 100));
    }

    private static Selection dbnto(String symbol, String expiry, StrikeWindow w) {
        return new Selection(MarketDataSource.DATABENTO, symbol, expiry, w);
    }

    @Test
    void registerAttachRouteDelivers() {
        SessionRoutingEngine e = engine();
        e.registerAppSession("app1", "u1", dbnto("SPX", "20260612", StrikeWindow.ALL), DBNTO);
        e.attachSocket("app1", "s1");
        Set<String> out = e.route(RoutableRecord.contract(
                MarketDataSource.DATABENTO, EventType.PACE, "SPX", "20260612", 7500, 0));
        assertEquals(Set.of("s1"), out);
    }

    @Test
    void perUserConcurrencyLimitEnforced() {
        SessionRoutingEngine e = engine(new ConcurrencyLimits(1, 5, 100));
        e.registerAppSession("app1", "u1", dbnto("SPX", "20260612", StrikeWindow.ALL), DBNTO);
        CapacityException ex = assertThrows(CapacityException.class, () ->
                e.registerAppSession("app2", "u1", dbnto("NDX", "20260620", StrikeWindow.ALL), DBNTO));
        assertEquals(CapacityException.Limit.APP_SESSIONS_PER_USER, ex.limit());
    }

    @Test
    void globalConcurrencyLimitEnforced() {
        SessionRoutingEngine e = engine(new ConcurrencyLimits(5, 5, 1));
        e.registerAppSession("app1", "u1", dbnto("SPX", "20260612", StrikeWindow.ALL), DBNTO);
        CapacityException ex = assertThrows(CapacityException.class, () ->
                e.registerAppSession("app2", "u2", dbnto("NDX", "20260620", StrikeWindow.ALL), DBNTO));
        assertEquals(CapacityException.Limit.TOTAL_APP_SESSIONS, ex.limit());
    }

    @Test
    void perAppSessionSocketLimitEnforced() {
        SessionRoutingEngine e = engine(new ConcurrencyLimits(5, 2, 100));
        e.registerAppSession("app1", "u1", dbnto("SPX", "20260612", StrikeWindow.ALL), DBNTO);
        e.attachSocket("app1", "s1");
        e.attachSocket("app1", "s2");
        CapacityException ex = assertThrows(CapacityException.class, () -> e.attachSocket("app1", "s3"));
        assertEquals(CapacityException.Limit.SOCKETS_PER_APP_SESSION, ex.limit());
    }

    @Test
    void ibkrSelectionRequiresEntitlement() {
        SessionRoutingEngine e = engine();
        assertThrows(NotEntitledException.class, () ->
                e.registerAppSession("app1", "u1",
                        new Selection(MarketDataSource.IBKR, "SPX", "20260617", StrikeWindow.ALL), DBNTO));
    }

    @Test
    void changeSelectionReindexes() {
        SessionRoutingEngine e = engine();
        e.registerAppSession("app1", "u1", dbnto("SPX", "20260612", StrikeWindow.ALL), DBNTO);
        e.attachSocket("app1", "s1");
        e.changeSelection("app1", dbnto("NDX", "20260620", StrikeWindow.ALL));

        assertTrue(e.route(RoutableRecord.contract(
                MarketDataSource.DATABENTO, EventType.PACE, "SPX", "20260612", 7500, 0)).isEmpty());
        assertEquals(Set.of("s1"), e.route(RoutableRecord.contract(
                MarketDataSource.DATABENTO, EventType.PACE, "NDX", "20260620", 18000, 0)));
    }

    @Test
    void strikeWindowFiltersDelivery() {
        SessionRoutingEngine e = engine();
        e.registerAppSession("app1", "u1", dbnto("SPX", "20260612", StrikeWindow.of(7000, 8000)), DBNTO);
        e.attachSocket("app1", "s1");
        assertEquals(Set.of("s1"), e.route(RoutableRecord.contract(
                MarketDataSource.DATABENTO, EventType.SNAPSHOT, "SPX", "20260612", 7500, 0)));
        assertTrue(e.route(RoutableRecord.contract(
                MarketDataSource.DATABENTO, EventType.SNAPSHOT, "SPX", "20260612", 9000, 0)).isEmpty());
    }

    @Test
    void detachSocketStopsDelivery() {
        SessionRoutingEngine e = engine();
        e.registerAppSession("app1", "u1", dbnto("SPX", "20260612", StrikeWindow.ALL), DBNTO);
        e.attachSocket("app1", "s1");
        e.detachSocket("s1");
        assertTrue(e.route(RoutableRecord.contract(
                MarketDataSource.DATABENTO, EventType.PACE, "SPX", "20260612", 7500, 0)).isEmpty());
    }

    @Test
    void teardownReleasesSubscription() {
        SessionRoutingEngine e = engine();
        Selection sel = dbnto("SPX", "20260612", StrikeWindow.ALL);
        e.registerAppSession("app1", "u1", sel, DBNTO);
        assertEquals(1, e.subscriptions().refCount(sel.subscriptionKey()));
        e.teardownAppSession("app1");
        assertEquals(0, e.subscriptions().refCount(sel.subscriptionKey()));
        assertEquals(0, e.appSessionCount());
    }

    @Test
    void epochBarrierDropsStaleRecords() {
        SessionRoutingEngine e = engine();
        e.registerAppSession("app1", "u1", dbnto("SPX", "20260612", StrikeWindow.ALL), DBNTO);
        e.attachSocket("app1", "s1");
        // selection change raises the per-session epoch to 2
        e.changeSelection("app1", dbnto("SPX", "20260613", StrikeWindow.ALL));

        // stale record (epoch 1 < 2) is dropped; current (epoch 2) and untagged (0) are delivered
        assertTrue(e.route(new RoutableRecord(MarketDataSource.DATABENTO, EventType.PACE,
                "SPX", "20260613", java.util.OptionalDouble.of(7500), 1, null, null)).isEmpty());
        assertEquals(Set.of("s1"), e.route(new RoutableRecord(MarketDataSource.DATABENTO, EventType.PACE,
                "SPX", "20260613", java.util.OptionalDouble.of(7500), 2, null, null)));
        assertEquals(Set.of("s1"), e.route(new RoutableRecord(MarketDataSource.DATABENTO, EventType.PACE,
                "SPX", "20260613", java.util.OptionalDouble.of(7500), 0, null, null)));
    }

    @Test
    void ibkrSelectionAcquiresNoDatabentoSubscription() {
        SessionRoutingEngine e = engine();
        Selection ibkr = new Selection(MarketDataSource.IBKR, "SPX", "20260617", StrikeWindow.ALL);
        e.registerAppSession("app1", "u1", ibkr, IBKR);
        assertEquals(0, e.subscriptions().distinctSubscriptions());
    }
}

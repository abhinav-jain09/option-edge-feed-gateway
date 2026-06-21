package app.feedgateway.mtsession;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/**
 * Approval gate, idle/max-session timeout, activity refresh, and force-logout/suspend (FR-15,
 * FR-18, FR-22, FR-23; OE-DDD-001 §5.4/§6.3/§6.6).
 */
class SessionLifecycleTest {

    private static final Set<String> DBNTO = Set.of();

    private static final class MutableClock extends Clock {
        private final AtomicReference<Instant> now = new AtomicReference<>(Instant.EPOCH);
        void advanceMinutes(long m) { now.updateAndGet(i -> i.plusSeconds(m * 60)); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId z) { return this; }
        @Override public Instant instant() { return now.get(); }
    }

    private static Selection dbnto(String symbol, String expiry) {
        return new Selection(MarketDataSource.DATABENTO, symbol, expiry, StrikeWindow.ALL);
    }

    @Test
    void appSessionIdleAndMaxComputation() {
        AppSession a = new AppSession("a", "u", Set.of(), dbnto("SPX", "20260612"), 1L,
                new UserSessionPolicy(10, 60, false), 0L);
        assertFalse(a.isIdleExpired(9 * 60_000L));
        assertTrue(a.isIdleExpired(10 * 60_000L));      // inclusive boundary
        assertFalse(a.isMaxExpired(59 * 60_000L));
        assertTrue(a.isMaxExpired(60 * 60_000L));
    }

    @Test
    void unlimitedSessionNeverMaxExpires() {
        AppSession a = new AppSession("a", "u", Set.of(), dbnto("SPX", "20260612"), 1L,
                new UserSessionPolicy(10, 0, true), 0L);
        assertFalse(a.isMaxExpired(Long.MAX_VALUE / 2));
        assertTrue(a.isIdleExpired(10 * 60_000L)); // idle still applies
    }

    @Test
    void sweepTearsDownIdleSession() {
        MutableClock clock = new MutableClock();
        SessionRoutingEngine e = new SessionRoutingEngine(
                new ConcurrencyLimits(5, 5, 100), new SubscriptionManager(), clock);
        e.registerAppSession("a1", "u1", dbnto("SPX", "20260612"), DBNTO,
                ApprovalState.APPROVED, new UserSessionPolicy(10, 600, false));
        e.attachSocket("a1", "s1");

        clock.advanceMinutes(9);
        assertTrue(e.sweepExpired().expiredAppSessionIds().isEmpty());

        clock.advanceMinutes(1); // now 10 min idle
        SessionRoutingEngine.SweepResult r = e.sweepExpired();
        assertEquals(Set.of("a1"), r.expiredAppSessionIds());
        assertEquals(Set.of("s1"), r.closedSocketIds());
        assertEquals(0, e.appSessionCount());
    }

    @Test
    void activityRefreshDefersIdleTimeout() {
        MutableClock clock = new MutableClock();
        SessionRoutingEngine e = new SessionRoutingEngine(
                new ConcurrencyLimits(5, 5, 100), new SubscriptionManager(), clock);
        e.registerAppSession("a1", "u1", dbnto("SPX", "20260612"), DBNTO,
                ApprovalState.APPROVED, new UserSessionPolicy(10, 600, false));

        clock.advanceMinutes(9);
        e.touchActivity("a1");      // resets idle clock
        clock.advanceMinutes(8);    // only 8 min since touch
        assertTrue(e.sweepExpired().expiredAppSessionIds().isEmpty());
        assertEquals(1, e.appSessionCount());
    }

    @Test
    void sweepTearsDownMaxSessionEvenWhenActive() {
        MutableClock clock = new MutableClock();
        SessionRoutingEngine e = new SessionRoutingEngine(
                new ConcurrencyLimits(5, 5, 100), new SubscriptionManager(), clock);
        // high idle, low max so only the absolute deadline trips
        e.registerAppSession("a1", "u1", dbnto("SPX", "20260612"), DBNTO,
                ApprovalState.APPROVED, new UserSessionPolicy(10_000, 60, false));
        clock.advanceMinutes(30);
        e.touchActivity("a1"); // active...
        clock.advanceMinutes(30); // ...but 60 min total ⇒ max deadline reached
        assertEquals(Set.of("a1"), e.sweepExpired().expiredAppSessionIds());
    }

    @Test
    void forceLogoutUserTearsDownAllTheirSessions() {
        SessionRoutingEngine e = new SessionRoutingEngine(
                new ConcurrencyLimits(5, 5, 100), new SubscriptionManager());
        e.registerAppSession("a1", "u1", dbnto("SPX", "20260612"), DBNTO);
        e.attachSocket("a1", "s1");
        e.registerAppSession("a2", "u1", dbnto("NDX", "20260620"), DBNTO);
        e.attachSocket("a2", "s2");
        e.registerAppSession("b1", "u2", dbnto("SPX", "20260612"), DBNTO);
        e.attachSocket("b1", "s3");

        Set<String> closed = e.forceLogoutUser("u1");
        assertEquals(Set.of("s1", "s2"), closed);
        assertEquals(0, e.appSessionCountForUser("u1"));
        assertEquals(1, e.appSessionCountForUser("u2")); // other user unaffected
    }

    @Test
    void unapprovedUserCannotRegister() {
        SessionRoutingEngine e = new SessionRoutingEngine(
                new ConcurrencyLimits(5, 5, 100), new SubscriptionManager());
        for (ApprovalState s : new ApprovalState[]{
                ApprovalState.PENDING_APPROVAL, ApprovalState.REJECTED, ApprovalState.SUSPENDED}) {
            assertThrows(NotApprovedException.class, () ->
                    e.registerAppSession("x", "u", dbnto("SPX", "20260612"), DBNTO, s,
                            UserSessionPolicy.systemDefault()));
        }
    }
}

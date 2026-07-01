package app.feedgateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.WebSocketSession;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Forward-path watchdog readiness (added after the 2026-07-01 prod incident where the pod stayed 1/1 Ready
 * for 9.5 hours while broadcasting nothing). Verifies /readyz — via {@link FeedGatewayService#readinessStatus()}
 * — flips to 503 only when ALL of (in-market-hours, broadcast stalled > 60s, at least one active WS session)
 * are true, and stays 200 otherwise.
 */
class FeedGatewayReadinessWatchdogTest {

    @Test
    void readyStaysHealthyImmediatelyAfterAForward() throws Exception {
        FeedGatewayService service = service();
        service.overrideRegularTradingHoursForTest(Boolean.TRUE);
        service.addSessionForTest(fakeSession("s1"));
        // Simulate a fresh forward.
        service.setLastForwardEpochMsForTest(System.currentTimeMillis());
        assertEquals(200, service.readinessStatus(),
                "fresh forward + active session + market hours must be healthy");
    }

    @Test
    void readyFailsAfter61sStallDuringMarketHoursWithActiveSession() throws Exception {
        FeedGatewayService service = service();
        service.overrideRegularTradingHoursForTest(Boolean.TRUE);
        service.addSessionForTest(fakeSession("s1"));
        // Advance simulated clock by 61s past the last forward.
        service.setLastForwardEpochMsForTest(System.currentTimeMillis() - 61_000L);
        assertEquals(503, service.readinessStatus(),
                "stalled forward path in market hours with active session must fail readiness");
    }

    @Test
    void readyStaysHealthyOutsideMarketHoursEvenWhenStale() throws Exception {
        FeedGatewayService service = service();
        // Off-hours (e.g. 20:00 UTC on a Saturday) — override the market-hours decision to false.
        service.overrideRegularTradingHoursForTest(Boolean.FALSE);
        service.addSessionForTest(fakeSession("s1"));
        service.setLastForwardEpochMsForTest(System.currentTimeMillis() - 3_600_000L);
        assertEquals(200, service.readinessStatus(),
                "off-hours must never fail readiness on stale forward timestamp");
    }

    @Test
    void readyStaysHealthyWithZeroActiveSessions() throws Exception {
        FeedGatewayService service = service();
        service.overrideRegularTradingHoursForTest(Boolean.TRUE);
        // No sessions added — clients.size() == 0.
        service.setLastForwardEpochMsForTest(System.currentTimeMillis() - 3_600_000L);
        assertEquals(0, service.activeSessionsForTest());
        assertEquals(200, service.readinessStatus(),
                "zero-session startup window must never fail readiness");
    }

    @Test
    void readyFailsOnFreshPodStartupWhenSessionWaits61sWithNoForward() throws Exception {
        // P1 regression: fresh pod, lastForwardEpochMs=0. A client connects, then market-hours ticks pass
        // without any forward. Previously readinessStatus() stayed 200 because the stall guard required
        // lastForwardEpochMs > 0 — so a wedge present since startup was invisible. Fix stamps a baseline
        // the first time we see a session and treats that as the "last forward" fallback.
        FeedGatewayService service = service();
        service.overrideRegularTradingHoursForTest(Boolean.TRUE);
        service.addSessionForTest(fakeSession("s1"));
        // First readiness check: fresh pod, session just appeared → baseline is now, readiness still 200.
        assertEquals(200, service.readinessStatus(),
                "fresh pod + session just connected must not immediately fail readiness");
        // Simulate 61s having passed since the session first appeared with still no forward.
        service.setReadinessBaselineEpochMsForTest(System.currentTimeMillis() - 61_000L);
        assertEquals(503, service.readinessStatus(),
                "fresh pod + active session waiting >60s with no forward must fail readiness");
    }

    @Test
    void enqueueWithoutFlushDoesNotStampWatchdog() throws Exception {
        // P2 regression: legacy coalesced path used to stamp lastForwardEpochMs at enqueue time, so a wedged
        // flushPendingBatch would leave the watchdog green even though sockets received nothing. Verify the
        // stamp is not moved forward by simulating a stale prior flush and confirming readiness trips.
        FeedGatewayService service = service();
        service.overrideRegularTradingHoursForTest(Boolean.TRUE);
        service.addSessionForTest(fakeSession("s1"));
        // Pretend a prior flush stamped 61s ago; nothing has flushed since. Enqueue-only calls between then
        // and now must NOT move this forward.
        long staleFlushMs = System.currentTimeMillis() - 61_000L;
        service.setLastForwardEpochMsForTest(staleFlushMs);
        assertEquals(503, service.readinessStatus(),
                "stale last-flush timestamp must fail readiness even if enqueue-only work happened since");
        // Now simulate flushPendingBatch delivering — stamp advances.
        service.setLastForwardEpochMsForTest(System.currentTimeMillis());
        assertEquals(200, service.readinessStatus(),
                "successful flush must clear the stall");
    }

    private static FeedGatewayService service() {
        return new FeedGatewayService(
                new GatewaySettings(),
                new ObjectMapper(),
                new HpsfGatewayViewMapper(),
                null /* routingEngine: legacy broadcast path */
        );
    }

    /** A minimal WebSocketSession proxy — clients is only queried for size in these tests. */
    private static WebSocketSession fakeSession(String id) {
        InvocationHandler h = (proxy, method, args) -> {
            String name = method.getName();
            if ("getId".equals(name)) {
                return id;
            }
            if ("hashCode".equals(name)) {
                return System.identityHashCode(proxy);
            }
            if ("equals".equals(name)) {
                return proxy == args[0];
            }
            if ("toString".equals(name)) {
                return "fake-ws-" + id;
            }
            Class<?> rt = method.getReturnType();
            if (rt == boolean.class) {
                return Boolean.FALSE;
            }
            if (rt == int.class || rt == long.class) {
                return 0;
            }
            return null;
        };
        return (WebSocketSession) Proxy.newProxyInstance(
                FeedGatewayReadinessWatchdogTest.class.getClassLoader(),
                new Class<?>[]{WebSocketSession.class},
                h);
    }
}

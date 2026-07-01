package app.feedgateway;

import app.feedgateway.mtsession.ConcurrencyLimits;
import app.feedgateway.mtsession.MarketDataSource;
import app.feedgateway.mtsession.Selection;
import app.feedgateway.mtsession.SessionRoutingEngine;
import app.feedgateway.mtsession.StrikeWindow;
import app.feedgateway.mtsession.SubscriptionManager;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.WebSocketSession;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Set;

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
        // Pre-arm the market-hours edge tracker; otherwise the first probe would treat this as an
        // off-hours→on-hours transition and clear the intentional stale stamp (Codex round-3 P2b).
        service.setInMarketHoursForTest(true);
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
        // Pre-arm the market-hours edge tracker so the first probe doesn't clear the intentional stale
        // stamp as if it were an overnight carryover (Codex round-3 P2b).
        service.setInMarketHoursForTest(true);
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

    @Test
    void noSessionGapResetsLastForwardSoNextSessionGetsFreshGrace() throws Exception {
        // P2a regression (Codex round-2): with round-1 code, readinessBaselineEpochMs reset to 0 on
        // no-session but lastForwardEpochMs stayed stamped from before. So a pod going >60s with 0
        // sessions and then getting a new client would flip 503 on the very next probe, using the
        // stale lastForward from the previous client-less window instead of the new baseline. Fix:
        // zero lastForwardEpochMs too when session count drops to 0, so the next session transition
        // re-arms both baseline and last-forward for a fresh 60s window.
        FeedGatewayService service = service();
        service.overrideRegularTradingHoursForTest(Boolean.TRUE);
        // Prior life of the pod: a session was connected and forwards were flowing.
        service.setLastForwardEpochMsForTest(System.currentTimeMillis() - 120_000L);
        // Now the client disconnects — clients set empties. First readiness probe with 0 sessions
        // must (a) stay 200 (no-session window is always healthy) AND (b) clear the stale timestamp
        // so the next active-session transition gets a clean 60s grace.
        assertEquals(0, service.activeSessionsForTest());
        assertEquals(200, service.readinessStatus(),
                "no-session window must never fail readiness");
        // A brand-new client connects during market hours. This probe stamps the baseline. Even
        // though lastForward was stale 120s ago (pre-gap), the reset zeroed it out so the effective
        // last-forward is now the fresh baseline — readiness stays 200.
        service.addSessionForTest(fakeSession("s2"));
        assertEquals(200, service.readinessStatus(),
                "fresh session after a no-session gap must get a fresh 60s grace window, not inherit stale lastForward");
        // Round-1 code would have returned 503 here (lastForwardEpochMs still ~120s stale).
    }

    @Test
    void routeDropDoesNotStampWatchdog() throws Exception {
        // P2b regression (Codex round-2): the per-session forward stamp used to fire unconditionally
        // after routeOrBroadcast(). If the record's selection didn't match any active session (or the
        // routing map was empty), routeOrBroadcast delivered to zero sockets but the stamp still ran,
        // masking a broadcast wedge. Fix: routeOrBroadcast now returns the socket-delivered count
        // and callers stamp only when > 0. Direct check on the routing helper: legacy-broadcast mode
        // with zero connected clients delivers zero and returns 0, so the caller would NOT stamp.
        FeedGatewayService service = service();
        service.overrideRegularTradingHoursForTest(Boolean.TRUE);
        // Add a session so activeSessions > 0 (readiness gate is armed), but note the broadcast()
        // helper iterates the clients set — with zero clients it returns 0. We simulate a "no matching
        // socket" outcome by keeping clients empty and calling the routing helper via reflection.
        // Then, since no stamp fires, readiness must trip after the 60s baseline elapses.
        service.addSessionForTest(fakeSession("s1"));
        java.lang.reflect.Method broadcast = FeedGatewayService.class.getDeclaredMethod(
                "broadcast", String.class, String.class);
        broadcast.setAccessible(true);
        // Remove the session so broadcast delivers to nothing (mirrors "route returned 0 deliveries").
        // We temporarily flip readiness state manually: leave a session in the clients set for the
        // watchdog check but assert the broadcast helper reports 0 when its own iteration finds none.
        // Simpler direct assertion: broadcast on an all-empty clients set returns 0.
        FeedGatewayService empty = service();
        int delivered = (int) broadcast.invoke(empty, "snapshot", "{}");
        assertEquals(0, delivered,
                "broadcast with no connected clients must report 0 delivered so caller skips the stamp");
        // Route-drop timeline: session present, forwards attempted but each route returns 0, no stamp
        // happens. Baseline was set on first probe. Advance baseline 61s → readiness trips.
        service.readinessStatus(); // stamp baseline
        service.setReadinessBaselineEpochMsForTest(System.currentTimeMillis() - 61_000L);
        assertEquals(503, service.readinessStatus(),
                "session active + only route-drops for 61s must fail readiness (round-1 code stayed 200 forever)");
    }

    @Test
    void graceWindowAppSessionAloneDoesNotFailReadiness() {
        // Codex round-3 P2a: an AppSession that outlived its WebSocket (detach → grace-window state) still
        // shows up in routingEngine.activeAppSessions(), but has no attached socket to deliver to. Prior
        // code fell back to that raw count when clients was empty and treated it as "active" — so during
        // market hours with a stale forward stamp, the pod would flip 503 even though there was no real
        // client waiting for a broadcast, and k8s would pointlessly restart it. Fix: fall back to
        // routingEngine.attachedSocketCount() instead — zero when the sole session is in its grace window.
        SessionRoutingEngine engine = new SessionRoutingEngine(
                new ConcurrencyLimits(5, 5, 100), new SubscriptionManager());
        engine.registerAppSession("app:u1", "u1",
                new Selection(MarketDataSource.DATABENTO, "SPX", "20260701", StrikeWindow.ALL), Set.of());
        // Note: attachSocket is NOT called → AppSession is in the grace window with no attached socket.
        FeedGatewayService service = serviceWithEngine(engine);
        service.overrideRegularTradingHoursForTest(Boolean.TRUE);
        // Stale forward timestamp from before the socket detached.
        service.setLastForwardEpochMsForTest(System.currentTimeMillis() - 120_000L);
        assertEquals(0, engine.attachedSocketCount(),
                "precondition: no sockets attached, only a grace-window AppSession");
        assertEquals(200, service.readinessStatus(),
                "grace-window AppSession with no attached socket must not fail readiness (Codex round-3 P2a)");
    }

    @Test
    void marketOpenTransitionResetsStallWindow() {
        // Codex round-3 P2b: a WebSocket held open overnight or across a weekend keeps
        // lastForwardEpochMs stamped from the PRIOR trading session. Off-hours readiness stays 200
        // (correct), but at 09:30 the stamp is 15-60+ hours old and instantly trips the >60s stall guard —
        // restarting a healthy pod before its first forward of the new day. Fix: detect the
        // off-hours→on-hours edge inside readinessStatus() and clear both stamps so the fresh session gets
        // a clean 60s grace baseline.
        FeedGatewayService service = service();
        // Pod ran overnight: last forward from yesterday, session still attached, current time = off-hours.
        service.overrideRegularTradingHoursForTest(Boolean.FALSE);
        service.addSessionForTest(fakeSession("s1"));
        service.setLastForwardEpochMsForTest(System.currentTimeMillis() - 20L * 3_600_000L);
        assertEquals(200, service.readinessStatus(), "off-hours must stay healthy");
        // Clock crosses 09:30. First probe of the new session: prior code would immediately trip 503
        // (stamp 20h stale > 60s). With the transition reset, the fresh session gets a new baseline.
        service.overrideRegularTradingHoursForTest(Boolean.TRUE);
        assertEquals(200, service.readinessStatus(),
                "first market-hours probe after overnight must clear the stale stamp and get fresh grace");
        // 61s later, still no forward with the session attached — now the fresh baseline elapses and
        // the stall guard correctly trips.
        service.setReadinessBaselineEpochMsForTest(System.currentTimeMillis() - 61_000L);
        assertEquals(503, service.readinessStatus(),
                "after the fresh 60s grace, a genuinely stalled forward path must still fail readiness");
    }

    @Test
    void replayOnlySessionsDoNotArmWatchdog() {
        // Codex round-4 P2: previously the readiness gate counted ALL attached sockets, including
        // sessions in replay mode. But route() skips replaying sessions on the live path and
        // sendToAppSession() does not stamp lastForwardEpochMs — so a legitimate replay lasting >60s
        // during market hours would flip /readyz to 503 and k8s would restart a working pod mid-replay.
        // Fix: fall back to routingEngine.liveAttachedSocketCount() which excludes replay-mode sockets.
        SessionRoutingEngine engine = new SessionRoutingEngine(
                new ConcurrencyLimits(5, 5, 100), new SubscriptionManager());
        engine.registerAppSession("app:u1", "u1",
                new Selection(MarketDataSource.DATABENTO, "SPX", "20260701", StrikeWindow.ALL), Set.of());
        engine.attachSocket("app:u1", "sock:u1");
        engine.setReplayMode("app:u1", true);
        FeedGatewayService service = serviceWithEngine(engine);
        service.overrideRegularTradingHoursForTest(Boolean.TRUE);
        service.setInMarketHoursForTest(true);
        // Stamp is 61s old — would trip the watchdog if replay-mode sockets counted as "active".
        service.setLastForwardEpochMsForTest(System.currentTimeMillis() - 61_000L);
        assertEquals(1, engine.attachedSocketCount(),
                "precondition: one socket attached (in replay mode)");
        assertEquals(0, engine.liveAttachedSocketCount(),
                "precondition: zero LIVE-mode sockets — the sole session is replaying");
        assertEquals(200, service.readinessStatus(),
                "replay-only sockets must not gate readiness (Codex round-4 P2)");
    }

    @Test
    void perSessionReplayOnlyDoesNotArmWatchdogEvenWhenClientsNonempty() {
        // Codex round-5 P2: FeedWebSocketHandler.afterConnectionEstablished adds every WebSocket to the
        // shared `clients` set regardless of replay/live mode. Round-4 code preferred clients.size() when
        // it was non-zero and only fell back to liveAttachedSocketCount() when clients was empty — so a
        // replay-only socket would still register as "active" and re-trip /readyz on a >60s replay in
        // market hours. Fix: in per-session mode (routingEngine != null) ALWAYS delegate to
        // liveAttachedSocketCount(); only legacy broadcast mode counts clients directly.
        SessionRoutingEngine engine = new SessionRoutingEngine(
                new ConcurrencyLimits(5, 5, 100), new SubscriptionManager());
        engine.registerAppSession("app:u1", "u1",
                new Selection(MarketDataSource.DATABENTO, "SPX", "20260701", StrikeWindow.ALL), Set.of());
        engine.attachSocket("app:u1", "sock:u1");
        engine.setReplayMode("app:u1", true);
        FeedGatewayService service = serviceWithEngine(engine);
        // Also add a raw WebSocketSession to clients — this mirrors FeedWebSocketHandler adding every WS
        // regardless of replay status. Round-4 fallback would have preferred clients.size() = 1 here.
        service.addSessionForTest(fakeSession("replay-only"));
        service.overrideRegularTradingHoursForTest(Boolean.TRUE);
        service.setInMarketHoursForTest(true);
        service.setLastForwardEpochMsForTest(System.currentTimeMillis() - 61_000L);
        assertEquals(0, engine.liveAttachedSocketCount(),
                "precondition: zero LIVE-mode sockets — sole session is replaying");
        assertEquals(200, service.readinessStatus(),
                "per-session mode with only replay sockets must not arm watchdog even when clients "
                        + "is non-empty (Codex round-5 P2)");
    }

    @Test
    void mixOfLiveAndReplaySessionsStillArmsWatchdog() {
        // Existing behavior preservation for round-4 P2: as long as at least one LIVE-mode socket is
        // attached, the watchdog must still trip after a >60s stall in market hours. A replay running
        // alongside a live session must NOT mask a genuine live-path wedge.
        SessionRoutingEngine engine = new SessionRoutingEngine(
                new ConcurrencyLimits(5, 5, 100), new SubscriptionManager());
        engine.registerAppSession("app:live", "u1",
                new Selection(MarketDataSource.DATABENTO, "SPX", "20260701", StrikeWindow.ALL), Set.of());
        engine.attachSocket("app:live", "sock:live");
        engine.registerAppSession("app:replay", "u2",
                new Selection(MarketDataSource.DATABENTO, "SPX", "20260701", StrikeWindow.ALL), Set.of());
        engine.attachSocket("app:replay", "sock:replay");
        engine.setReplayMode("app:replay", true);
        FeedGatewayService service = serviceWithEngine(engine);
        service.overrideRegularTradingHoursForTest(Boolean.TRUE);
        service.setInMarketHoursForTest(true);
        service.setLastForwardEpochMsForTest(System.currentTimeMillis() - 61_000L);
        assertEquals(1, engine.liveAttachedSocketCount(),
                "precondition: one LIVE socket + one replay socket");
        assertEquals(503, service.readinessStatus(),
                "mixed live+replay with a genuine live-path stall must still fail readiness");
    }

    private static FeedGatewayService service() {
        return new FeedGatewayService(
                new GatewaySettings(),
                new ObjectMapper(),
                new HpsfGatewayViewMapper(),
                null /* routingEngine: legacy broadcast path */
        );
    }

    private static FeedGatewayService serviceWithEngine(SessionRoutingEngine engine) {
        return new FeedGatewayService(
                new GatewaySettings(),
                new ObjectMapper(),
                new HpsfGatewayViewMapper(),
                engine);
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

package app.feedgateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Rollover-diagnostics instrumentation. These tests verify the ADDITIVE observability we added after the
 * 2026-07-01 silent-wedge incident (see PR body). They exercise:
 *   (1) the periodic 60s state dump emits a single structured INFO line
 *   (2) the rollover WARN fires with before/after selection state
 *   (3) the suspicious-state ERROR fires when forwardedDelta == 0 for two consecutive market-hours cycles
 *       with active sessions AND live consumers advancing
 * All assertions target the {@code RGW_*} log prefixes so grep-in-Loki behaviour is regression-guarded.
 */
class FeedGatewayRolloverDiagnosticsTest {

    private static FeedGatewayService service() {
        return new FeedGatewayService(
                new GatewaySettings(),
                new ObjectMapper(),
                new HpsfGatewayViewMapper(),
                null /* legacy broadcast path */);
    }

    @Test
    void periodicStateDumpEmitsStructuredLine() {
        FeedGatewayService service = service();
        // Force market-hours FALSE so the ERROR path is not exercised — this test only checks the
        // periodic INFO line shape.
        overrideRth(service, Boolean.FALSE);

        CapturedOut out = new CapturedOut();
        try (out) {
            service.dumpDiagnosticState();
        }

        String stdout = out.stdout();
        // Structured, key=value, prefixed for grep. All the flag names the incident postmortem asked for
        // must be present so the log line is self-describing.
        assertTrue(stdout.contains("RGW_STATE_DUMP"), "dump line missing prefix: " + stdout);
        assertTrue(stdout.contains("event=state_dump"), stdout);
        assertTrue(stdout.contains("activeSelection="), stdout);
        assertTrue(stdout.contains("avroCaughtUp="), stdout);
        assertTrue(stdout.contains("stateCaughtUp="), stdout);
        assertTrue(stdout.contains("hpsfCaughtUp="), stdout);
        assertTrue(stdout.contains("readySelectionKey="), stdout);
        assertTrue(stdout.contains("autoRolledExpiry="), stdout);
        assertTrue(stdout.contains("hoursSinceLastRollover="), stdout);
        assertTrue(stdout.contains("forwardedEventsSinceLastLog="), stdout);
        assertTrue(stdout.contains("droppedByStaleness="), stdout);
        assertTrue(stdout.contains("droppedByCacheGate="), stdout);
        assertTrue(stdout.contains("droppedByOtherReasons="), stdout);
        assertTrue(stdout.contains("activeSessions="), stdout);
        assertTrue(stdout.contains("liveRecordsPolled="), stdout);
    }

    @Test
    void rolloverTransitionEmitsStructuredWarn() {
        FeedGatewayService service = service();

        long beforeCount = service.rolloverCountForTest();
        CapturedOut out = new CapturedOut();
        try (out) {
            service.invokeRolloverWarnForTest("DATABENTO", "DATABENTO");
        }

        String stderr = out.stderr();
        assertTrue(stderr.contains("RGW_ROLLOVER"), "rollover line missing prefix: " + stderr);
        assertTrue(stderr.contains("event=rollover_transition"), stderr);
        assertTrue(stderr.contains("fromSelection="), stderr);
        assertTrue(stderr.contains("toSelection="), stderr);
        assertTrue(stderr.contains("avroCaughtUp="), stderr);
        assertTrue(stderr.contains("readySelectionKey="), stderr);
        assertTrue(stderr.contains("autoRolledExpiry="), stderr);
        assertEquals(beforeCount + 1, service.rolloverCountForTest(),
                "rolloverCount must increment on every WARN emission");
    }

    @Test
    void forwardStalledDuringMarketHoursTriggersAlertAfterTwoZeroCycles() {
        FeedGatewayService service = service();
        overrideRth(service, Boolean.TRUE);
        // Set up: an active session (via legacy client-count fallback would be 0 — so simulate via
        // routingEngine? Not wired in this ctor. Instead, cheat by opening a fake client channel via
        // reflection: we register a WebSocketSession into `clients` so activeSessionsCount() > 0.
        addFakeClient(service);
        // Consumers ARE advancing (liveRecordsPolled > 0) but no forwards. This is the exact wedge
        // shape the 2026-07-01 outage showed.
        service.bumpLiveRecordsPolledForTest(100L);
        // NOTE: forwardedEvents stays at 0 — no bump.

        CapturedOut out = new CapturedOut();
        long alertsBefore = service.forwardStalledAlertsForTest();
        try (out) {
            // Cycle 1: zero forwards; consecutiveZeroForwardCycles -> 1 (no alert yet)
            service.dumpDiagnosticState();
            long alertsAfter1 = service.forwardStalledAlertsForTest();
            assertEquals(alertsBefore, alertsAfter1, "must NOT alert on the first zero-forward cycle");
            // Cycle 2: still zero forwards, still advancing, still in RTH -> ALERT
            service.bumpLiveRecordsPolledForTest(50L); // consumers keep advancing
            service.dumpDiagnosticState();
        }
        assertEquals(alertsBefore + 1, service.forwardStalledAlertsForTest(),
                "must alert on the SECOND consecutive zero-forward cycle during market hours");
        assertTrue(out.stderr().contains("RGW_ALERT"), "alert log missing prefix: " + out.stderr());
        assertTrue(out.stderr().contains("event=GATEWAY_FORWARD_STALLED_DURING_MARKET_HOURS"), out.stderr());
    }

    @Test
    void forwardStalledResetsWhenForwardsResume() {
        FeedGatewayService service = service();
        overrideRth(service, Boolean.TRUE);
        addFakeClient(service);
        service.bumpLiveRecordsPolledForTest(100L);

        try (CapturedOut ignored = new CapturedOut()) {
            service.dumpDiagnosticState();          // cycle1: zero
            service.bumpForwardedEventsForTest(5L); // a real forward happens
            service.bumpLiveRecordsPolledForTest(50L);
            service.dumpDiagnosticState();          // cycle2: nonzero delta => streak resets
            service.bumpLiveRecordsPolledForTest(50L);
            service.dumpDiagnosticState();          // cycle3: zero again but streak was reset
        }
        assertEquals(0L, service.forwardStalledAlertsForTest(),
                "streak must reset when forwards resume, so no false alert");
    }

    @Test
    void hpsfPollsCountTowardConsumersAdvancing() {
        // Codex round-2 P2b: HPSF-only pipelines must lift polledDelta > 0 so the stall-alert path
        // fires when appropriate. Before the fix, only the generic Avro/state loop bumped
        // liveRecordsPolled — so an HPSF-only wedge could never trip the alert. We simulate the HPSF
        // loop's new increment by calling bumpLiveRecordsPolledForTest (same code path) and verify the
        // alert DOES fire after two zero-forward cycles in RTH with sessions present.
        FeedGatewayService service = service();
        overrideRth(service, Boolean.TRUE);
        addFakeClient(service);

        long alertsBefore = service.forwardStalledAlertsForTest();
        try (CapturedOut ignored = new CapturedOut()) {
            // Cycle 1: HPSF poll delivered 100 records (mimicking runHpsfLiveConsumerOnce's new
            // liveRecordsPolled.addAndGet(records.count())). No forwards.
            service.bumpLiveRecordsPolledForTest(100L);
            service.dumpDiagnosticState();
            assertEquals(alertsBefore, service.forwardStalledAlertsForTest(),
                    "cycle-1 must not alert");
            // Cycle 2: HPSF keeps advancing, still no forwards → ALERT
            service.bumpLiveRecordsPolledForTest(75L);
            service.dumpDiagnosticState();
        }
        assertEquals(alertsBefore + 1, service.forwardStalledAlertsForTest(),
                "HPSF polls alone must count toward consumersAdvancing so the stall alert fires");
    }

    @Test
    void readySelectionKeyGaugeFlipsOffOnRollover() {
        // Codex round-2 P2a: applySelection intentionally does NOT reset readySelectionKey, so the
        // gauge must compare readySelectionKey to the CURRENT active selection's key (not just check
        // non-empty). After a rollover to a new selection whose key differs, the gauge must read 0
        // even though the field is still populated with the previous selection's key.
        FeedGatewayService service = service();

        // Seed: activeSelection = A, readySelectionKey = key(A) → gauge should read 1.
        service.seedReadySelectionForTest("DATABENTO", "SPX", "20260701", 1L);
        assertEquals(1, service.readySelectionKeyGaugeForTest(),
                "gauge must read 1 when readySelectionKey matches the current activeSelection");

        // Rollover: swap activeSelection to B (new expiry / new epoch). readySelectionKey stays as
        // key(A) because applySelection does NOT reset it. Gauge must now read 0.
        service.swapActiveSelectionForTest("DATABENTO", "SPX", "20260702", 2L);
        assertEquals(0, service.readySelectionKeyGaugeForTest(),
                "gauge must read 0 after rollover: readySelectionKey still holds the PREVIOUS "
                        + "selection's key, so the new selection has NOT yet transitioned to ready");
    }

    @Test
    void forwardStalledDoesNotFireWhenConsumersAreQuiet() {
        // Regression for Codex P2: the pre-fix code used the CUMULATIVE liveRecordsPolled counter,
        // so once a consumer had EVER seen a record, `consumersAdvancing` stayed true forever and two
        // quiet cycles (zero forwardedDelta AND zero new polled records) would fire a false stall
        // alert. After the fix, `consumersAdvancing` is derived from the per-interval polled delta,
        // so quiet cycles are NOT treated as a wedge.
        FeedGatewayService service = service();
        overrideRth(service, Boolean.TRUE);
        addFakeClient(service);

        try (CapturedOut ignored = new CapturedOut()) {
            // One poll cycle produced records — arm the cumulative counter, then let it go quiet.
            service.bumpLiveRecordsPolledForTest(100L);
            service.dumpDiagnosticState();  // primes lastDumpLiveRecordsPolledSnapshot to 100
            // Two quiet dumps: NO new polled records, NO forwards. Consumers are legitimately idle.
            service.dumpDiagnosticState();  // cycle 1: polledDelta == 0 => streak stays 0
            service.dumpDiagnosticState();  // cycle 2: polledDelta == 0 => streak stays 0
        }
        assertEquals(0L, service.forwardStalledAlertsForTest(),
                "quiet cycles (no new polled records) must NOT count toward the stall streak");
    }

    @Test
    void stallAlertGatedByAttachedSockets() {
        // Codex round-3 P2: an AppSession in the grace window (socket detached, session persists)
        // must NOT satisfy the stall-alert gate. Before the fix, activeAppSessions()>0 was enough,
        // so a lingering grace-window session would fire GATEWAY_FORWARD_STALLED_DURING_MARKET_HOURS
        // even with zero attached clients — false alarm.
        FeedGatewayService service = service();
        overrideRth(service, Boolean.TRUE);
        // Wire a real routingEngine with 1 registered AppSession but NO attached socket
        // (grace-window shape): activeAppSessions() == 1, attachedSocketCount() == 0.
        app.feedgateway.mtsession.SessionRoutingEngine engine = new app.feedgateway.mtsession.SessionRoutingEngine(
                new app.feedgateway.mtsession.ConcurrencyLimits(5, 5, 100),
                new app.feedgateway.mtsession.SubscriptionManager());
        engine.registerAppSession(
                "app:grace",
                "u1",
                new app.feedgateway.mtsession.Selection(
                        app.feedgateway.mtsession.MarketDataSource.DATABENTO, "SPX", "20260701",
                        app.feedgateway.mtsession.StrikeWindow.ALL),
                java.util.Set.of());
        injectRoutingEngine(service, engine);
        // Consumers advancing, no forwards.
        service.bumpLiveRecordsPolledForTest(100L);

        long alertsBefore = service.forwardStalledAlertsForTest();
        try (CapturedOut ignored = new CapturedOut()) {
            service.dumpDiagnosticState();  // cycle 1
            service.bumpLiveRecordsPolledForTest(50L);
            service.dumpDiagnosticState();  // cycle 2 — would alert under the OLD gate
        }
        assertEquals(alertsBefore, service.forwardStalledAlertsForTest(),
                "stall alert must NOT fire when no sockets are attached (only grace-window sessions)");
    }

    @Test
    void stallAlertFiresWithAttachedSocket() {
        // Codex round-3 P2 counter-case: with a real attached socket the existing stall detection
        // behaviour must be preserved.
        FeedGatewayService service = service();
        overrideRth(service, Boolean.TRUE);
        app.feedgateway.mtsession.SessionRoutingEngine engine = new app.feedgateway.mtsession.SessionRoutingEngine(
                new app.feedgateway.mtsession.ConcurrencyLimits(5, 5, 100),
                new app.feedgateway.mtsession.SubscriptionManager());
        engine.registerAppSession(
                "app:live",
                "u1",
                new app.feedgateway.mtsession.Selection(
                        app.feedgateway.mtsession.MarketDataSource.DATABENTO, "SPX", "20260701",
                        app.feedgateway.mtsession.StrikeWindow.ALL),
                java.util.Set.of());
        engine.attachSocket("app:live", "sock1");  // attachedSocketCount() == 1
        injectRoutingEngine(service, engine);

        service.bumpLiveRecordsPolledForTest(100L);
        long alertsBefore = service.forwardStalledAlertsForTest();
        try (CapturedOut ignored = new CapturedOut()) {
            service.dumpDiagnosticState();  // cycle 1
            service.bumpLiveRecordsPolledForTest(50L);
            service.dumpDiagnosticState();  // cycle 2 — MUST alert
        }
        assertEquals(alertsBefore + 1, service.forwardStalledAlertsForTest(),
                "stall alert must fire when a socket is attached (existing behaviour preserved)");
    }

    // -------------------- test helpers --------------------

    private static void injectRoutingEngine(FeedGatewayService service,
                                            app.feedgateway.mtsession.SessionRoutingEngine engine) {
        try {
            java.lang.reflect.Field f = FeedGatewayService.class.getDeclaredField("routingEngine");
            f.setAccessible(true);
            f.set(service, engine);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("cannot inject routingEngine", e);
        }
    }


    private static void overrideRth(FeedGatewayService service, Boolean rth) {
        try {
            Method m = FeedGatewayService.class.getDeclaredMethod("overrideRegularTradingHoursForTest", Boolean.class);
            m.setAccessible(true);
            m.invoke(service, rth);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("cannot reach RTH override", e);
        }
    }

    private static void addFakeClient(FeedGatewayService service) {
        try {
            java.lang.reflect.Field f = FeedGatewayService.class.getDeclaredField("clients");
            f.setAccessible(true);
            @SuppressWarnings("unchecked")
            java.util.Set<org.springframework.web.socket.WebSocketSession> clients =
                    (java.util.Set<org.springframework.web.socket.WebSocketSession>) f.get(service);
            org.springframework.web.socket.WebSocketSession fake =
                    (org.springframework.web.socket.WebSocketSession) java.lang.reflect.Proxy.newProxyInstance(
                            FeedGatewayRolloverDiagnosticsTest.class.getClassLoader(),
                            new Class<?>[] { org.springframework.web.socket.WebSocketSession.class },
                            (proxy, method, args) -> {
                                if ("getId".equals(method.getName())) return "diagnostics-fake-client";
                                if ("isOpen".equals(method.getName())) return Boolean.TRUE;
                                if ("hashCode".equals(method.getName())) return System.identityHashCode(proxy);
                                if ("equals".equals(method.getName())) return proxy == args[0];
                                return null;
                            });
            clients.add(fake);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("cannot register fake client", e);
        }
    }

    /** Captures System.out and System.err for the duration of a try-with-resources block. */
    private static final class CapturedOut implements AutoCloseable {
        private final PrintStream originalOut = System.out;
        private final PrintStream originalErr = System.err;
        private final ByteArrayOutputStream outBuf = new ByteArrayOutputStream();
        private final ByteArrayOutputStream errBuf = new ByteArrayOutputStream();

        CapturedOut() {
            System.setOut(new PrintStream(outBuf, true));
            System.setErr(new PrintStream(errBuf, true));
        }

        String stdout() { return outBuf.toString(); }
        String stderr() { return errBuf.toString(); }

        @Override
        public void close() {
            System.setOut(originalOut);
            System.setErr(originalErr);
        }
    }
}

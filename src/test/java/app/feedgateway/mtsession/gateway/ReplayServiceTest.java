package app.feedgateway.mtsession.gateway;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.feedgateway.mtsession.ConcurrencyLimits;
import app.feedgateway.mtsession.MarketDataSource;
import app.feedgateway.mtsession.Selection;
import app.feedgateway.mtsession.SessionRoutingEngine;
import app.feedgateway.mtsession.StrikeWindow;
import app.feedgateway.mtsession.SubscriptionManager;
import app.feedgateway.mtsession.auth.JwtVerificationException;
import app.feedgateway.mtsession.auth.TokenVerifier;
import app.feedgateway.mtsession.auth.VerifiedPrincipal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ReplayServiceTest {

    private static final long MAX_WINDOW_MS = 30L * 60L * 1000L;
    private static final int MAX_RECORDS = 200_000;
    private static final String START = "2026-06-12T14:00:00Z";
    private static final String END = "2026-06-12T14:20:00Z";

    private static TokenVerifier verifierFor(String userId) {
        return token -> new VerifiedPrincipal(userId, userId, Set.of(), "options-edge-web");
    }

    private static final class RecordingRunner implements ReplayRunner {
        final List<String> calls = new ArrayList<>();

        @Override
        public Mode startReplay(ReplayParams params) {
            calls.add("start:" + params.sessionId() + ":" + params.symbol() + ":" + params.expiry());
            return Mode.REPLAY_RUNNING;
        }

        @Override
        public Mode stopReplay(String appSessionId) {
            calls.add("stop:" + appSessionId);
            return Mode.REPLAY_COMPLETE;
        }

        @Override
        public Mode resumeLive(String appSessionId) {
            calls.add("resume:" + appSessionId);
            return Mode.LIVE;
        }
    }

    private SessionRoutingEngine engineWith(String appSessionId, String userId) {
        SessionRoutingEngine engine = new SessionRoutingEngine(new ConcurrencyLimits(5, 5, 100), new SubscriptionManager());
        engine.registerAppSession(appSessionId, userId,
                new Selection(MarketDataSource.DATABENTO, "SPX", "20260612", StrikeWindow.ALL), Set.of());
        return engine;
    }

    /** Records each runId it is asked to authorize; allows by default unless {@code deny} is set. */
    private static final class RecordingAuthorizer implements ReplayRunAuthorizer {
        final List<String> authorized = new ArrayList<>();
        boolean deny;
        String replayDate; // when set, returned as the authorized run's authoritative chain date

        @Override
        public AuthorizedRun authorizeRun(String bearerToken, String runId) {
            authorized.add(runId);
            if (deny) {
                throw new ReplayRunAuthorizationException("denied: " + runId);
            }
            return new AuthorizedRun(replayDate);
        }
    }

    private static final ReplayRunAuthorizer ALLOW_ALL =
            (token, runId) -> ReplayRunAuthorizer.AuthorizedRun.UNKNOWN;
    private static final ReplayRunLister EMPTY_LISTER = token -> List.of();

    private ReplayService service(SessionRoutingEngine engine, ReplayRunner runner, String userId,
                                  boolean enabled, boolean prodBlocked) {
        return service(engine, runner, ALLOW_ALL, userId, enabled, prodBlocked);
    }

    private ReplayService service(SessionRoutingEngine engine, ReplayRunner runner,
                                  ReplayRunAuthorizer authorizer, String userId,
                                  boolean enabled, boolean prodBlocked) {
        return new ReplayService(verifierFor(userId), engine, runner, authorizer, EMPTY_LISTER, enabled,
                prodBlocked, MAX_WINDOW_MS, MAX_RECORDS);
    }

    private static ReplayService.ReplayRequest req(String sessionId) {
        return new ReplayService.ReplayRequest(sessionId, "SPX", "20260612", START, END, 1000, null);
    }

    @Test
    void startVerifiesBindsSessionAndDelegates() throws Exception {
        SessionRoutingEngine engine = engineWith("app:u1", "u1");
        RecordingRunner runner = new RecordingRunner();
        ReplayService svc = service(engine, runner, "u1", true, false);

        ReplayService.ReplayAck ack = svc.start("tok", req("app:u1"));

        assertSame(ReplayRunner.Mode.REPLAY_RUNNING, ack.mode());
        assertEquals(List.of("start:app:u1:SPX:20260612"), runner.calls);
    }

    @Test
    void startThreadsRunIdIntoParams() throws Exception {
        SessionRoutingEngine engine = engineWith("app:u1", "u1");
        ReplayService svc = service(engine, new RecordingRunner(), "u1", true, false);
        ReplayService.ReplayRequest withRun =
                new ReplayService.ReplayRequest("app:u1", "SPX", "20260612", START, END, 1000, "r-123");

        ReplayService.ReplayAck ack = svc.start("tok", withRun);

        assertEquals("r-123", ack.params().runId());
        assertTrue(ack.params().hasRun());
    }

    @Test
    void runBackedReplayPinsExpiryToTheAuthorizedRunDate() throws Exception {
        // The client supplies a MISMATCHED expiry (e.g. the live 0DTE date); the gateway must override it
        // with the owned run's session date so the per-record expiry filter cannot drop the run's strikes.
        SessionRoutingEngine engine = engineWith("app:u1", "u1");
        RecordingRunner runner = new RecordingRunner();
        RecordingAuthorizer authz = new RecordingAuthorizer();
        authz.replayDate = "2026-06-22";
        ReplayService svc = service(engine, runner, authz, "u1", true, false);
        ReplayService.ReplayRequest mismatched =
                new ReplayService.ReplayRequest("app:u1", "SPX", "20260623", START, END, 1000, "r-123");

        ReplayService.ReplayAck ack = svc.start("tok", mismatched);

        assertEquals("20260622", ack.params().expiry(), "expiry must be pinned to the run's session date");
        assertEquals(List.of("start:app:u1:SPX:20260622"), runner.calls);
    }

    @Test
    void runBackedReplayKeepsClientExpiryWhenRunDateUnknown() throws Exception {
        // Older orchestrator / unparseable body -> AuthorizedRun.replayDate is null -> no override.
        SessionRoutingEngine engine = engineWith("app:u1", "u1");
        RecordingRunner runner = new RecordingRunner();
        RecordingAuthorizer authz = new RecordingAuthorizer(); // replayDate stays null
        ReplayService svc = service(engine, runner, authz, "u1", true, false);
        ReplayService.ReplayRequest withRun =
                new ReplayService.ReplayRequest("app:u1", "SPX", "20260612", START, END, 1000, "r-123");

        ReplayService.ReplayAck ack = svc.start("tok", withRun);

        assertEquals("20260612", ack.params().expiry(), "client expiry is kept when the run date is unknown");
    }

    @Test
    void runBackedReplayIsDeniedWhenTheCallerDoesNotOwnTheRun() {
        // P0: owning app:u1's WS session must NOT let u1 read another user's orchestrated run.
        SessionRoutingEngine engine = engineWith("app:u1", "u1");
        RecordingRunner runner = new RecordingRunner();
        RecordingAuthorizer authz = new RecordingAuthorizer();
        authz.deny = true;
        ReplayService svc = service(engine, runner, authz, "u1", true, false);
        ReplayService.ReplayRequest someoneElsesRun =
                new ReplayService.ReplayRequest("app:u1", "SPX", "20260612", START, END, 1000, "r-not-mine");

        assertThrows(ReplayRunAuthorizer.ReplayRunAuthorizationException.class,
                () -> svc.start("tok", someoneElsesRun));
        assertEquals(List.of("r-not-mine"), authz.authorized, "the runId must be authorized before reading");
        assertTrue(runner.calls.isEmpty(), "no topic read may start when run authorization fails");
    }

    @Test
    void runBackedReplayAuthorizesTheRunIdWithTheCallersToken() throws Exception {
        SessionRoutingEngine engine = engineWith("app:u1", "u1");
        RecordingAuthorizer authz = new RecordingAuthorizer();
        ReplayService svc = service(engine, new RecordingRunner(), authz, "u1", true, false);
        ReplayService.ReplayRequest withRun =
                new ReplayService.ReplayRequest("app:u1", "SPX", "20260612", START, END, 1000, "r-mine");

        svc.start("tok", withRun);
        assertEquals(List.of("r-mine"), authz.authorized);
    }

    @Test
    void liveSliceReplaySkipsRunAuthorization() throws Exception {
        // No runId → the caller replays only their own session's live data; no orchestrator check needed.
        SessionRoutingEngine engine = engineWith("app:u1", "u1");
        RecordingAuthorizer authz = new RecordingAuthorizer();
        authz.deny = true; // would fail if consulted
        ReplayService svc = service(engine, new RecordingRunner(), authz, "u1", true, false);

        svc.start("tok", req("app:u1"));
        assertTrue(authz.authorized.isEmpty(), "live-slice replay must not consult run authorization");
    }

    @Test
    void startRejectsSessionIdThatIsNotTheCallers() {
        SessionRoutingEngine engine = engineWith("app:u1", "u1");
        ReplayService svc = service(engine, new RecordingRunner(), "u1", true, false);
        // authenticated as u1 (app:u1) but asking to drive someone else's session
        assertThrows(IllegalArgumentException.class, () -> svc.start("tok", req("app:u2")));
    }

    @Test
    void startRequiresAnActiveSession() {
        SessionRoutingEngine empty = new SessionRoutingEngine(new ConcurrencyLimits(5, 5, 100), new SubscriptionManager());
        ReplayService svc = service(empty, new RecordingRunner(), "u1", true, false);
        assertThrows(IllegalStateException.class, () -> svc.start("tok", req("app:u1")));
    }

    @Test
    void startRejectsWindowOverThirtyMinutes() {
        SessionRoutingEngine engine = engineWith("app:u1", "u1");
        ReplayService svc = service(engine, new RecordingRunner(), "u1", true, false);
        ReplayService.ReplayRequest tooLong =
                new ReplayService.ReplayRequest("app:u1", "SPX", "20260612", START, "2026-06-12T14:40:00Z", 1000, null);
        assertThrows(IllegalArgumentException.class, () -> svc.start("tok", tooLong));
    }

    @Test
    void disabledServiceRejectsEverything() {
        SessionRoutingEngine engine = engineWith("app:u1", "u1");
        ReplayService svc = service(engine, new RecordingRunner(), "u1", false, false);
        assertThrows(ReplayService.ReplayDisabledException.class, () -> svc.start("tok", req("app:u1")));
    }

    @Test
    void prodBlockedServiceRejectsEverything() {
        SessionRoutingEngine engine = engineWith("app:u1", "u1");
        ReplayService svc = service(engine, new RecordingRunner(), "u1", true, true);
        assertThrows(ReplayService.ReplayDisabledException.class, () -> svc.start("tok", req("app:u1")));
    }

    @Test
    void stopAndResumeRequireOwnership() throws Exception {
        SessionRoutingEngine engine = engineWith("app:u1", "u1");
        RecordingRunner runner = new RecordingRunner();
        ReplayService svc = service(engine, runner, "u1", true, false);

        assertSame(ReplayRunner.Mode.REPLAY_COMPLETE, svc.stop("tok", "app:u1"));
        assertSame(ReplayRunner.Mode.LIVE, svc.resume("tok", "app:u1"));
        assertEquals(List.of("stop:app:u1", "resume:app:u1"), runner.calls);

        assertThrows(IllegalArgumentException.class, () -> svc.stop("tok", "app:u2"));
        assertThrows(IllegalArgumentException.class, () -> svc.resume("tok", ""));
    }

    @Test
    void invalidTokenSurfacesAsJwtException() {
        SessionRoutingEngine engine = engineWith("app:u1", "u1");
        TokenVerifier failing = token -> {
            throw new JwtVerificationException("bad");
        };
        ReplayService svc = new ReplayService(failing, engine, new RecordingRunner(), ALLOW_ALL, EMPTY_LISTER,
                true, false, MAX_WINDOW_MS, MAX_RECORDS);
        assertThrows(JwtVerificationException.class, () -> svc.start("tok", req("app:u1")));
    }

    @Test
    void listRunsVerifiesTokenThenDelegatesToLister() throws Exception {
        SessionRoutingEngine engine = engineWith("app:u1", "u1");
        ReplayRunView view = new ReplayRunView("r-1", "RUNNING", "2026-06-12", "14:00", "14:20");
        ReplayRunLister lister = token -> List.of(view);
        ReplayService svc = new ReplayService(verifierFor("u1"), engine, new RecordingRunner(), ALLOW_ALL,
                lister, true, false, MAX_WINDOW_MS, MAX_RECORDS);

        assertEquals(List.of(view), svc.listRuns("tok"));
    }

    @Test
    void listRunsRejectsAnInvalidToken() {
        SessionRoutingEngine engine = engineWith("app:u1", "u1");
        TokenVerifier failing = token -> {
            throw new JwtVerificationException("bad");
        };
        ReplayRunLister boom = token -> {
            throw new AssertionError("lister must not be called when the token is invalid");
        };
        ReplayService svc = new ReplayService(failing, engine, new RecordingRunner(), ALLOW_ALL, boom,
                true, false, MAX_WINDOW_MS, MAX_RECORDS);
        assertThrows(JwtVerificationException.class, () -> svc.listRuns("tok"));
    }

    @Test
    void listRunsIsBlockedWhenReplayDisabled() {
        SessionRoutingEngine engine = engineWith("app:u1", "u1");
        ReplayRunLister boom = token -> {
            throw new AssertionError("lister must not be called when replay is disabled");
        };
        ReplayService svc = new ReplayService(verifierFor("u1"), engine, new RecordingRunner(), ALLOW_ALL,
                boom, false, false, MAX_WINDOW_MS, MAX_RECORDS);
        assertThrows(ReplayService.ReplayDisabledException.class, () -> svc.listRuns("tok"));
    }

    @Test
    void recordCapIsClamped() {
        ReplayParams p = ReplayParams.of("app:u1", "spx", "2026-06-12", START, END, 9_999_999, MAX_WINDOW_MS, MAX_RECORDS);
        assertEquals(MAX_RECORDS, p.maxRecords());
        assertEquals("SPX", p.symbol());
        assertEquals("20260612", p.expiry());
        assertTrue(p.endUtcMs() > p.startUtcMs());
    }
}

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

    private ReplayService service(SessionRoutingEngine engine, ReplayRunner runner, String userId,
                                  boolean enabled, boolean prodBlocked) {
        return new ReplayService(verifierFor(userId), engine, runner, enabled, prodBlocked, MAX_WINDOW_MS, MAX_RECORDS);
    }

    private static ReplayService.ReplayRequest req(String sessionId) {
        return new ReplayService.ReplayRequest(sessionId, "SPX", "20260612", START, END, 1000);
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
                new ReplayService.ReplayRequest("app:u1", "SPX", "20260612", START, "2026-06-12T14:40:00Z", 1000);
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
        ReplayService svc = new ReplayService(failing, engine, new RecordingRunner(), true, false, MAX_WINDOW_MS, MAX_RECORDS);
        assertThrows(JwtVerificationException.class, () -> svc.start("tok", req("app:u1")));
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

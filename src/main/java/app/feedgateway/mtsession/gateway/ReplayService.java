package app.feedgateway.mtsession.gateway;

import app.feedgateway.mtsession.SessionRoutingEngine;
import app.feedgateway.mtsession.auth.JwtVerificationException;
import app.feedgateway.mtsession.auth.TokenVerifier;
import app.feedgateway.mtsession.auth.VerifiedPrincipal;
import java.util.Objects;

/**
 * Control plane for per-session Live↔Replay switching (OE-DDD replay, reqs 3–6, 11). Verifies the
 * caller's bearer JWT, binds the request to the caller's own AppSession ({@code app:<userId>}),
 * enforces the safety gates (enabled flag, non-prod, ownership), validates the window via
 * {@link ReplayParams}, and delegates the data plane to a {@link ReplayRunner}.
 *
 * <p>Pure logic (no Spring) so it is unit-testable without Kafka or a web context.
 */
public final class ReplayService {

    private final TokenVerifier verifier;
    private final SessionRoutingEngine engine;
    private final ReplayRunner runner;
    private final boolean enabled;
    private final boolean prodBlocked;
    private final long maxWindowMs;
    private final int maxRecordsCap;

    public ReplayService(TokenVerifier verifier, SessionRoutingEngine engine, ReplayRunner runner,
                         boolean enabled, boolean prodBlocked, long maxWindowMs, int maxRecordsCap) {
        this.verifier = Objects.requireNonNull(verifier, "verifier");
        this.engine = Objects.requireNonNull(engine, "engine");
        this.runner = Objects.requireNonNull(runner, "runner");
        this.enabled = enabled;
        this.prodBlocked = prodBlocked;
        this.maxWindowMs = maxWindowMs;
        this.maxRecordsCap = maxRecordsCap;
    }

    /** Thrown when replay is administratively disabled (flag off, or prod); caller maps to 403. */
    public static final class ReplayDisabledException extends RuntimeException {
        public ReplayDisabledException(String message) {
            super(message);
        }
    }

    /** Result of a control call: the new mode and the echoed window (for the UI). */
    public record ReplayAck(ReplayRunner.Mode mode, ReplayParams params) {
    }

    /**
     * Start a replay. Verifies the token, confirms {@code request.sessionId} is the caller's own
     * AppSession, validates the window, and starts streaming.
     */
    public ReplayAck start(String bearerToken, ReplayRequest request) throws JwtVerificationException {
        ensureEnabled();
        String appSessionId = authenticatedSession(bearerToken);
        Objects.requireNonNull(request, "request");
        ReplayParams params = ReplayParams.of(request.sessionId(), request.symbol(), request.expiry(),
                request.startUtc(), request.endUtc(), request.maxRecords(), maxWindowMs, maxRecordsCap,
                request.runId());
        if (!appSessionId.equals(params.sessionId())) {
            throw new IllegalArgumentException("sessionId does not match the authenticated session");
        }
        if (engine.appSession(appSessionId).isEmpty()) {
            throw new IllegalStateException("no active session to replay; connect first");
        }
        ReplayRunner.Mode mode = runner.startReplay(params);
        return new ReplayAck(mode, params);
    }

    /** Stop an in-flight replay for the caller's own session. */
    public ReplayRunner.Mode stop(String bearerToken, String sessionId) throws JwtVerificationException {
        ensureEnabled();
        String appSessionId = ownedSession(bearerToken, sessionId);
        return runner.stopReplay(appSessionId);
    }

    /** Return the caller's own session to live data. */
    public ReplayRunner.Mode resume(String bearerToken, String sessionId) throws JwtVerificationException {
        ensureEnabled();
        String appSessionId = ownedSession(bearerToken, sessionId);
        return runner.resumeLive(appSessionId);
    }

    private void ensureEnabled() {
        if (!enabled) {
            throw new ReplayDisabledException("historical replay is disabled");
        }
        if (prodBlocked) {
            throw new ReplayDisabledException("historical replay is disabled in prod");
        }
    }

    private String authenticatedSession(String bearerToken) throws JwtVerificationException {
        VerifiedPrincipal principal = verifier.verify(bearerToken);
        String userId = principal.userId();
        if (userId == null || userId.isBlank()) {
            throw new JwtVerificationException("token has no subject");
        }
        return "app:" + userId;
    }

    private String ownedSession(String bearerToken, String sessionId) throws JwtVerificationException {
        String appSessionId = authenticatedSession(bearerToken);
        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException("sessionId is required");
        }
        if (!appSessionId.equals(sessionId.trim())) {
            throw new IllegalArgumentException("sessionId does not match the authenticated session");
        }
        return appSessionId;
    }

    /**
     * Request body shared by the start endpoint (req. 6). {@code runId} is optional: when present, the
     * gateway streams the orchestrated run's local {@code *.replay.<runId>.*} topics; when absent, it
     * falls back to slicing the live topics by timestamp.
     */
    public record ReplayRequest(String sessionId, String symbol, String expiry,
                                String startUtc, String endUtc, Integer maxRecords, String runId) {
    }
}

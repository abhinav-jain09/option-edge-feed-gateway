package app.feedgateway.mtsession.gateway;

import app.feedgateway.mtsession.auth.JwtVerificationException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * Per-session Live↔Replay control endpoints (reqs 3–5). Registered only when both the auth flag and
 * the replay flag are on; the {@link ReplayService} enforces token verification, ownership, the
 * non-prod gate, and window bounds (reqs 6, 11). Replay data itself flows over the session's
 * existing WebSocket (req. 8) — these endpoints only switch mode.
 */
@RestController
@ConditionalOnProperty(name = {"gateway.auth.enabled", "databento.replay.ui.enabled"}, havingValue = "true")
public final class ReplayController {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ReplayService replayService;

    public ReplayController(ReplayService replayService) {
        this.replayService = replayService;
    }

    @PostMapping(value = "/api/replay/historical/start", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> start(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody ReplayService.ReplayRequest request) {
        return guarded(authorization, token -> {
            ReplayService.ReplayAck ack = replayService.start(token, request);
            ReplayParams p = ack.params();
            String runIdJson = p.runId() == null ? "null" : "\"" + esc(p.runId()) + "\"";
            String body = "{\"mode\":\"" + ack.mode() + "\",\"sessionId\":\"" + esc(p.sessionId())
                    + "\",\"symbol\":\"" + esc(p.symbol()) + "\",\"expiry\":\"" + esc(p.expiry())
                    + "\",\"startUtcMs\":" + p.startUtcMs() + ",\"endUtcMs\":" + p.endUtcMs()
                    + ",\"maxRecords\":" + p.maxRecords() + ",\"runId\":" + runIdJson + "}";
            return ResponseEntity.ok(body);
        });
    }

    @PostMapping(value = "/api/replay/historical/stop", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> stop(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody ModeRequest request) {
        return guarded(authorization, token ->
                ResponseEntity.ok("{\"mode\":\"" + replayService.stop(token, request.sessionId()) + "\"}"));
    }

    @PostMapping(value = "/api/replay/live/resume", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> resume(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody ModeRequest request) {
        return guarded(authorization, token ->
                ResponseEntity.ok("{\"mode\":\"" + replayService.resume(token, request.sessionId()) + "\"}"));
    }

    /**
     * Run-picker source (PR-2 of the runId bridge): the CALLER's orchestrated replay runs, projected to an
     * ownership-safe view (no ownerId / no internals). The gateway forwards the bearer to the orchestrator,
     * which filters by ownership; the lister is fail-closed (an unreachable/erroring orchestrator yields an
     * empty array, never a raw error). Per-token, so never shared-cached.
     */
    @GetMapping(value = "/api/replay/historical/runs", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> runs(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        return guarded(authorization, token -> {
            List<ReplayRunView> runs = replayService.listRuns(token);
            String body;
            try {
                // Serialize with Jackson, not hand-built JSON: it escapes control chars / quotes / backslashes
                // correctly so an adversarial run field can never produce invalid (or injectable) JSON.
                body = MAPPER.writeValueAsString(runs);
            } catch (JsonProcessingException e) {
                body = "[]"; // fail-closed: never emit a partial/invalid array
            }
            return ResponseEntity.ok()
                    .header("Cache-Control", "no-store, no-cache, must-revalidate")
                    .header("Pragma", "no-cache")
                    .header("Vary", "Authorization")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body);
        });
    }

    private interface Action {
        ResponseEntity<String> run(String token) throws JwtVerificationException;
    }

    private ResponseEntity<String> guarded(String authorization, Action action) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return error(HttpStatus.UNAUTHORIZED, "missing bearer token");
        }
        String token = authorization.substring("Bearer ".length()).trim();
        try {
            return action.run(token);
        } catch (JwtVerificationException e) {
            return error(HttpStatus.UNAUTHORIZED, "invalid token");
        } catch (ReplayService.ReplayDisabledException e) {
            return error(HttpStatus.FORBIDDEN, e.getMessage());
        } catch (ReplayRunAuthorizer.ReplayRunAuthorizationException e) {
            // The caller does not own (or we could not confirm ownership of) the requested runId.
            return error(HttpStatus.FORBIDDEN, e.getMessage());
        } catch (IllegalArgumentException e) {
            return error(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (IllegalStateException e) {
            return error(HttpStatus.CONFLICT, e.getMessage());
        }
    }

    private static ResponseEntity<String> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON)
                .body("{\"error\":\"" + esc(message) + "\"}");
    }

    private static String esc(String s) {
        return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    /** Body for stop/resume (req. 6 — sessionId scopes the call to the caller's session). */
    public record ModeRequest(String sessionId) {
    }
}

package app.feedgateway.replay;

import app.feedgateway.GatewaySettings;
import app.feedgateway.replay.ReplayControlService.ReplayControlException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST control surface the UI drives to attach a client session to a replay run and to return it to live.
 * Opt-in ({@code GATEWAY_REPLAY_ENABLED}); every call resolves the caller's principal from the bearer JWT
 * and re-checks that the principal OWNS the target session before driving the state machine — so a user
 * can only control their own session, re-verified on every request (fail closed: 401 no principal, 403
 * not owner, 409 illegal transition, 404 when replay is disabled).
 */
@RestController
@RequestMapping("/api/replay")
public class ReplayControlController {

    public record AttachRequest(String sessionId, String runId) {
    }

    public record SessionRequest(String sessionId) {
    }

    private final GatewaySettings settings;
    private final ReplayControlService control;
    private final ReplaySessionOwnership ownership;
    private final ReplaySessionRegistry registry;
    private final ReplayPrincipalResolver principals;

    public ReplayControlController(GatewaySettings settings, ReplayControlService control,
                                   ReplaySessionOwnership ownership, ReplaySessionRegistry registry,
                                   ReplayPrincipalResolver principals) {
        this.settings = settings;
        this.control = control;
        this.ownership = ownership;
        this.registry = registry;
        this.principals = principals;
    }

    @PostMapping("/attach")
    public ResponseEntity<Map<String, Object>> attach(
            @RequestHeader(value = "Authorization", required = false) String auth,
            @RequestBody AttachRequest req) {
        return guarded(auth, req == null ? null : req.sessionId(), principal -> {
            ReplayMode mode = control.attach(req.sessionId(), req.runId(), principal);
            return ok(mode, req.runId());
        });
    }

    @PostMapping("/return-to-live")
    public ResponseEntity<Map<String, Object>> returnToLive(
            @RequestHeader(value = "Authorization", required = false) String auth,
            @RequestBody SessionRequest req) {
        return guarded(auth, req == null ? null : req.sessionId(), principal -> {
            ReplayMode mode = control.returnToLive(req.sessionId());
            return ok(mode, null);
        });
    }

    @GetMapping("/mode")
    public ResponseEntity<Map<String, Object>> mode(
            @RequestHeader(value = "Authorization", required = false) String auth,
            @RequestParam String sessionId) {
        return guarded(auth, sessionId, principal -> {
            ReplaySessionState st = registry.get(sessionId);
            ReplayMode mode = st == null ? ReplayMode.LIVE : st.mode();
            return ok(mode, st == null ? null : st.runId());
        });
    }

    /** Common gate: replay-enabled → authenticated → owns the session, then run the action. */
    private ResponseEntity<Map<String, Object>> guarded(String auth, String sessionId,
                                                        java.util.function.Function<String, ResponseEntity<Map<String, Object>>> action) {
        if (!settings.replayEnabled()) {
            return status(HttpStatus.NOT_FOUND, "replay disabled");
        }
        Optional<String> principal = principals.principal(auth);
        if (principal.isEmpty()) {
            return status(HttpStatus.UNAUTHORIZED, "unauthenticated");
        }
        if (sessionId == null || sessionId.isBlank() || !ownership.owns(sessionId, principal.get())) {
            return status(HttpStatus.FORBIDDEN, "not the owner of this session");
        }
        try {
            return action.apply(principal.get());
        } catch (ReplayControlException e) {
            return status(HttpStatus.CONFLICT, e.getMessage());
        }
    }

    private static ResponseEntity<Map<String, Object>> ok(ReplayMode mode, String runId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("mode", mode.name());
        if (runId != null) {
            body.put("runId", runId);
        }
        return ResponseEntity.ok(body);
    }

    private static ResponseEntity<Map<String, Object>> status(HttpStatus status, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", message);
        return ResponseEntity.status(status).body(body);
    }
}

package app.feedgateway.replay;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Tracks which authenticated principal owns each open WebSocket session, so a replay control request can
 * be authorized: a user may only drive replay for a session they own. Bound at WS handshake (from the
 * verified JWT {@code sub}) and cleared on disconnect. Thread-safe.
 */
@Component
public final class ReplaySessionOwnership {

    private final Map<String, String> principalBySession = new ConcurrentHashMap<>();

    public void bind(String sessionId, String principal) {
        if (sessionId != null && principal != null && !principal.isBlank()) {
            principalBySession.put(sessionId, principal);
        }
    }

    public void unbind(String sessionId) {
        if (sessionId != null) {
            principalBySession.remove(sessionId);
        }
    }

    /** True iff {@code principal} is the (non-null) owner of an existing {@code sessionId}. */
    public boolean owns(String sessionId, String principal) {
        if (sessionId == null || principal == null || principal.isBlank()) {
            return false; // fail closed: unknown session or no principal is never an owner
        }
        return principal.equals(principalBySession.get(sessionId));
    }
}

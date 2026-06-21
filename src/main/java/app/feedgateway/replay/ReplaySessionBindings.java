package app.feedgateway.replay;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * The control→data-plane seam: the run a session is currently bound to read from
 * ({@code *.replay.<runId>.*}). The control plane sets/clears the binding on every replay transition; the
 * data plane (the per-session replay Kafka reader — a follow-up) consults it to decide which run's records
 * to stream to which socket. Keeping this a tiny, explicit, thread-safe map keeps the control plane fully
 * decoupled from the consumer implementation.
 */
@Component
public final class ReplaySessionBindings {

    private final Map<String, String> runBySession = new ConcurrentHashMap<>();

    /** Bind the session to a run (it should now receive only that run's replay records). */
    public void bind(String sessionId, String runId) {
        if (sessionId != null && runId != null && !runId.isBlank()) {
            runBySession.put(sessionId, runId);
        }
    }

    /** Clear any replay binding (the session returns to live). */
    public void clear(String sessionId) {
        if (sessionId != null) {
            runBySession.remove(sessionId);
        }
    }

    /** The run the session is bound to, if any. */
    public Optional<String> runFor(String sessionId) {
        return Optional.ofNullable(sessionId == null ? null : runBySession.get(sessionId));
    }
}

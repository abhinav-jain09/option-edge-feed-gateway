package app.feedgateway.replay;

import app.feedgateway.replay.ReplaySessionStateMachine.Transition;
import java.util.Objects;

/**
 * Control plane for per-session replay: the gateway calls {@link #attach}/{@link #returnToLive} (e.g.
 * from a REST endpoint the UI hits when its run goes RUNNING / when the user clicks "return to live").
 * It drives {@link ReplaySessionRegistry} and fires the {@link TransitionListener} so the data plane can
 * (re)wire the run's replay topics and CLEAR the session's caches on every transition edge.
 *
 * <p>Decoupled by design: authorization and the data-plane wiring are injected interfaces, so this
 * control logic carries no dependency on Keycloak/JWT, Redis, or Kafka and is fully unit-testable. The
 * {@link Authorizer} is consulted on EVERY attach (never a cached decision) so ownership of the session
 * and the run is re-verified each time — fail closed on a negative.
 */
public final class ReplayControlService {

    /** Re-verifies, on every attach, that {@code principal} owns {@code sessionId} and may read {@code runId}. */
    @FunctionalInterface
    public interface Authorizer {
        boolean canAttach(String sessionId, String runId, String principal);
    }

    /** Invoked on every ACCEPTED transition so the data plane clears caches + (re)wires/stops topics. */
    @FunctionalInterface
    public interface TransitionListener {
        void onTransition(String sessionId, Transition transition);
    }

    /** Thrown when a control request is refused (unauthorized or an illegal transition). */
    public static final class ReplayControlException extends RuntimeException {
        public ReplayControlException(String message) {
            super(message);
        }
    }

    private final ReplaySessionRegistry registry;
    private final Authorizer authorizer;
    private final TransitionListener listener;

    public ReplayControlService(ReplaySessionRegistry registry, Authorizer authorizer, TransitionListener listener) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.authorizer = Objects.requireNonNull(authorizer, "authorizer");
        this.listener = Objects.requireNonNull(listener, "listener");
    }

    /**
     * Attach (or re-attach) the session to {@code runId}. Re-authorizes FIRST and creates NO session state
     * on a negative — an unauthorized attach leaves the registry untouched (fail closed). Returns the new
     * mode on success.
     */
    public ReplayMode attach(String sessionId, String runId, String principal) {
        boolean authorized = authorizer.canAttach(sessionId, runId, principal); // re-auth EVERY attach, BEFORE any state
        Transition t = registry.attach(sessionId, runId, authorized);
        return commit(sessionId, t);
    }

    /** The data plane confirmed records are flowing for the run. */
    public ReplayMode markStreaming(String sessionId) {
        return commit(sessionId, registry.streaming(sessionId));
    }

    /** The run's window completed. */
    public ReplayMode markTerminal(String sessionId) {
        return commit(sessionId, registry.terminal(sessionId));
    }

    /** Return the session to live (clears replay rows; the data plane re-sends the live cache). */
    public ReplayMode returnToLive(String sessionId) {
        return commit(sessionId, registry.returnToLive(sessionId));
    }

    /** The live cache was re-sent; the session is fully live again. */
    public ReplayMode markLiveResumed(String sessionId) {
        return commit(sessionId, registry.liveResumed(sessionId));
    }

    private ReplayMode commit(String sessionId, Transition t) {
        if (!t.accepted()) {
            throw new ReplayControlException(t.rejection().orElse("rejected"));
        }
        listener.onTransition(sessionId, t); // clear caches + (re)wire data plane on every edge
        return t.state().mode();
    }
}

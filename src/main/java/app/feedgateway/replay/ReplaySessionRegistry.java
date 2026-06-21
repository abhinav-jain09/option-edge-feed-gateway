package app.feedgateway.replay;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import app.feedgateway.replay.ReplaySessionStateMachine.Transition;

/**
 * Thread-safe registry of per-session replay state. Each client session has exactly one
 * {@link ReplaySessionState}; transitions are applied <b>atomically</b> (compute-under-lock per key) so
 * concurrent control calls and data-plane callbacks for the same session never race. A rejected
 * transition leaves the stored state unchanged.
 *
 * <p>Sessions are isolated by key: a transition on one session can never affect another — the foundation
 * of the design's two-session isolation requirement.
 */
public final class ReplaySessionRegistry {

    private final Map<String, ReplaySessionState> sessions = new ConcurrentHashMap<>();

    /** Register a freshly-connected session in LIVE (idempotent); returns its state. */
    public ReplaySessionState register(String sessionId) {
        return sessions.computeIfAbsent(sessionId, ReplaySessionState::live);
    }

    /** Drop a disconnected session. */
    public void remove(String sessionId) {
        sessions.remove(sessionId);
    }

    public ReplaySessionState get(String sessionId) {
        return sessions.get(sessionId);
    }

    /**
     * Attach the session to a run. {@code authorized} MUST be re-computed by the caller on every call
     * (ownership of session + run re-verified); the state machine fails closed if it is false.
     */
    public Transition attach(String sessionId, String runId, boolean authorized) {
        return apply(sessionId, cur -> ReplaySessionStateMachine.attach(cur, runId, authorized));
    }

    public Transition streaming(String sessionId) {
        return apply(sessionId, ReplaySessionStateMachine::streaming);
    }

    public Transition terminal(String sessionId) {
        return apply(sessionId, ReplaySessionStateMachine::terminal);
    }

    public Transition returnToLive(String sessionId) {
        return apply(sessionId, ReplaySessionStateMachine::returnToLive);
    }

    public Transition liveResumed(String sessionId) {
        return apply(sessionId, ReplaySessionStateMachine::liveResumed);
    }

    /** Apply a transition atomically; store the new state only if it was accepted. */
    private Transition apply(String sessionId, Function<ReplaySessionState, Transition> transition) {
        Transition[] out = new Transition[1];
        sessions.compute(sessionId, (id, cur) -> {
            ReplaySessionState current = cur != null ? cur : ReplaySessionState.live(id);
            Transition t = transition.apply(current);
            out[0] = t;
            return t.accepted() ? t.state() : current;
        });
        return out[0];
    }
}

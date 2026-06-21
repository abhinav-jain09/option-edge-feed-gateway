package app.feedgateway.replay;

import java.util.Objects;

/**
 * The per-session replay state — the immutable snapshot the gateway holds for ONE client session as it
 * moves through the replay lifecycle. Carries the authoritative {@code runId} the session is attached to
 * and a monotonically-increasing {@code generation} that is bumped on EVERY transition, so the data
 * plane can fence stale emits (a record produced for an old generation is dropped — the generation
 * barrier that prevents replay rows bleeding into live or across runs).
 *
 * <p>Pure and immutable; all transitions go through {@link ReplaySessionStateMachine}.
 */
public record ReplaySessionState(String sessionId, ReplayMode mode, String runId, long generation) {

    public ReplaySessionState {
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(mode, "mode");
        // runId is non-null only while attached to a run (the REPLAY_* / RETURN_TO_LIVE phases).
    }

    /** The initial LIVE state for a freshly-connected session. */
    public static ReplaySessionState live(String sessionId) {
        return new ReplaySessionState(sessionId, ReplayMode.LIVE, null, 0L);
    }

    public boolean isReplaying() {
        return mode == ReplayMode.REPLAY_ATTACHING || mode == ReplayMode.REPLAY_STREAMING;
    }

    ReplaySessionState withMode(ReplayMode next, String nextRunId) {
        return new ReplaySessionState(sessionId, next, nextRunId, generation + 1);
    }
}

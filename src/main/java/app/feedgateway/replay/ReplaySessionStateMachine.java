package app.feedgateway.replay;

import java.util.Optional;

/**
 * The pure per-session replay state machine. Every transition returns a {@link Transition} carrying the
 * next {@link ReplaySessionState}, whether the session's caches MUST be cleared, and an optional
 * rejection reason (the transition is refused and the state is unchanged).
 *
 * <p>Two safety invariants are enforced here so the data plane cannot bleed replay rows into live or
 * across runs:
 * <ul>
 *   <li><b>authorize on every attach</b> — {@link #attach} is refused unless the caller passes
 *       {@code authorized=true} (the caller re-verifies ownership of the session + the run on EVERY
 *       attach, never trusting a prior authorization); and</li>
 *   <li><b>clear caches + bump the generation on every transition</b> — each accepted transition sets
 *       {@code cacheClear=true} and increments {@link ReplaySessionState#generation()}, so any in-flight
 *       record stamped with an older generation is fenced out.</li>
 * </ul>
 * Pure: no Kafka, no Spring, no sockets — fully unit-testable.
 */
public final class ReplaySessionStateMachine {

    /** Result of a transition attempt. {@code rejection} present ⇒ {@code state} is unchanged. */
    public record Transition(ReplaySessionState state, boolean cacheCleared, Optional<String> rejection) {
        static Transition accept(ReplaySessionState next) {
            return new Transition(next, true, Optional.empty());
        }

        static Transition reject(ReplaySessionState unchanged, String why) {
            return new Transition(unchanged, false, Optional.of(why));
        }

        public boolean accepted() {
            return rejection.isEmpty();
        }
    }

    private ReplaySessionStateMachine() {
    }

    /**
     * Attach the session to {@code runId} (or re-attach to a different run). REQUIRES a fresh
     * authorization — the caller re-verifies on every call. Clears caches + bumps the generation so no
     * record from the prior live/replay state survives the switch.
     */
    public static Transition attach(ReplaySessionState cur, String runId, boolean authorized) {
        if (runId == null || runId.isBlank()) {
            return Transition.reject(cur, "runId is required");
        }
        if (!authorized) {
            return Transition.reject(cur, "attach not authorized"); // re-auth fails closed
        }
        return Transition.accept(cur.withMode(ReplayMode.REPLAY_ATTACHING, runId.trim()));
    }

    /** The data plane confirmed the run's replay topics are wired and records are flowing. */
    public static Transition streaming(ReplaySessionState cur) {
        if (cur.mode() != ReplayMode.REPLAY_ATTACHING) {
            return Transition.reject(cur, "streaming only from REPLAY_ATTACHING (was " + cur.mode() + ")");
        }
        return Transition.accept(cur.withMode(ReplayMode.REPLAY_STREAMING, cur.runId()));
    }

    /** The run's window completed; the session stays on the rendered result until return-to-live. */
    public static Transition terminal(ReplaySessionState cur) {
        if (cur.mode() != ReplayMode.REPLAY_STREAMING && cur.mode() != ReplayMode.REPLAY_ATTACHING) {
            return Transition.reject(cur, "terminal only from an active replay (was " + cur.mode() + ")");
        }
        return Transition.accept(cur.withMode(ReplayMode.REPLAY_TERMINAL, cur.runId()));
    }

    /** Begin returning to live from any replay phase. Idempotent from RETURN_TO_LIVE; rejected from LIVE. */
    public static Transition returnToLive(ReplaySessionState cur) {
        if (cur.mode() == ReplayMode.LIVE) {
            return Transition.reject(cur, "already LIVE");
        }
        return Transition.accept(cur.withMode(ReplayMode.RETURN_TO_LIVE, null));
    }

    /** The data plane re-sent the live cache; the session is fully live again. */
    public static Transition liveResumed(ReplaySessionState cur) {
        if (cur.mode() != ReplayMode.RETURN_TO_LIVE) {
            return Transition.reject(cur, "liveResumed only from RETURN_TO_LIVE (was " + cur.mode() + ")");
        }
        return Transition.accept(cur.withMode(ReplayMode.LIVE, null));
    }
}

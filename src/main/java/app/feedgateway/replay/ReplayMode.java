package app.feedgateway.replay;

/**
 * The per-session replay lifecycle states (design: gateway per-session replay state machine).
 *
 * <pre>
 *   LIVE              ── attach(runId) ─▶ REPLAY_ATTACHING ── streaming ─▶ REPLAY_STREAMING
 *   REPLAY_STREAMING  ── runComplete ──▶ REPLAY_TERMINAL
 *   {ATTACHING,STREAMING,TERMINAL} ── returnToLive ─▶ RETURN_TO_LIVE ── liveResumed ─▶ LIVE
 *   {any replay/terminal} ── attach(otherRun) ─▶ REPLAY_ATTACHING   (re-authorized, caches cleared)
 * </pre>
 */
public enum ReplayMode {
    /** Streaming the live feed to the session. */
    LIVE,
    /** A run was attached and authorized; the data plane is wiring up the run's replay topics. */
    REPLAY_ATTACHING,
    /** The session is receiving ONLY this run's {@code *.replay.<runId>.*} records. */
    REPLAY_STREAMING,
    /** The run finished (window complete); no more replay records will be sent until return-to-live. */
    REPLAY_TERMINAL,
    /** Transitioning back to live: replay rows cleared, live cache about to be re-sent. */
    RETURN_TO_LIVE
}

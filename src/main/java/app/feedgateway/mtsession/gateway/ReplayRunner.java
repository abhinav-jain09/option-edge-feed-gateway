package app.feedgateway.mtsession.gateway;

/**
 * The data-plane half of per-session replay (req. 7/8). Implemented by the gateway, which owns the
 * Kafka consumers and the per-socket send path. The {@link ReplayService} (control plane) calls
 * these after verifying the bearer token and validating {@link ReplayParams}.
 *
 * <p>Implementations must NOT publish to live topics — replay reads historical records read-only
 * and streams them only to the requesting session's sockets.
 */
public interface ReplayRunner {

    /** Mode reported back to the UI badge (req. 1). */
    enum Mode { LIVE, REPLAY_RUNNING, REPLAY_COMPLETE, RETURNING_TO_LIVE }

    /** Start streaming the requested historical window to the session; returns REPLAY_RUNNING. */
    Mode startReplay(ReplayParams params);

    /** Stop an in-flight replay (records already sent remain shown); returns REPLAY_COMPLETE. */
    Mode stopReplay(String appSessionId);

    /** Leave replay mode and resume live delivery (clears replay rows, replays live cache); LIVE. */
    Mode resumeLive(String appSessionId);
}

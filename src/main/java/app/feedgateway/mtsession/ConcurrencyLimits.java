package app.feedgateway.mtsession;

/**
 * Configurable session concurrency limits (OE-DDD-001 §6.8, FR-27).
 *
 * @param maxAppSessionsPerUser    cap on concurrent AppSessions for one user
 * @param maxSocketsPerAppSession  cap on SocketSessions attached to one AppSession
 * @param maxTotalAppSessions      global cap on AppSessions for this gateway instance
 */
public record ConcurrencyLimits(int maxAppSessionsPerUser, int maxSocketsPerAppSession, int maxTotalAppSessions) {

    public ConcurrencyLimits {
        if (maxAppSessionsPerUser < 1 || maxSocketsPerAppSession < 1 || maxTotalAppSessions < 1) {
            throw new IllegalArgumentException("All concurrency limits must be >= 1");
        }
    }

    /** Defaults from OE-DDD-001 §6.8. */
    public static ConcurrencyLimits defaults() {
        return new ConcurrencyLimits(1, 4, 200);
    }
}

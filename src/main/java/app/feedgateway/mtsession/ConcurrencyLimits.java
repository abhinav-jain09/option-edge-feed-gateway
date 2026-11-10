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

    /**
     * The defaults, with any limit the operator set replaced. A page opens several sockets (feed, footprint,
     * CVD, indicators, auction ...) and a user often has several tabs, so the §6.8 default of 4 sockets per
     * AppSession is reached with two or three pages and every further socket is refused ("attach rejected",
     * the page shows "Feed unavailable"). A null means "not set"; a value below 1 is refused loudly, never
     * silently replaced, by the constructor.
     */
    public static ConcurrencyLimits of(Integer maxAppSessionsPerUser, Integer maxSocketsPerAppSession,
                                       Integer maxTotalAppSessions) {
        ConcurrencyLimits d = defaults();
        return new ConcurrencyLimits(
                maxAppSessionsPerUser == null ? d.maxAppSessionsPerUser() : maxAppSessionsPerUser,
                maxSocketsPerAppSession == null ? d.maxSocketsPerAppSession() : maxSocketsPerAppSession,
                maxTotalAppSessions == null ? d.maxTotalAppSessions() : maxTotalAppSessions);
    }
}

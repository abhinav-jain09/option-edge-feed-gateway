package app.feedgateway.mtsession.gateway;

/**
 * Authorizes that the bearer's {@code (issuer, subject)} actually OWNS a replay {@code runId}, against
 * the orchestrator — the source of truth for run ownership ({@code ownerId = issuer|subject}).
 *
 * <p>P0 (runId authz): the replay API verifies that the destination WebSocket {@code sessionId} belongs
 * to the bearer subject, but owning the session does NOT prove ownership of an orchestrated run. The
 * supplied {@code runId} is converted directly into replay-topic names that the gateway then reads, so a
 * user who obtains or guesses another user's run id could otherwise stream that run's market data. The
 * gateway must therefore authorize {@code (issuer, subject, runId)} before any topic is read.
 *
 * <p>Implementations MUST fail closed: deny on an explicit non-owner response AND on any inability to
 * positively confirm ownership (orchestrator unreachable, timeout, malformed response).
 */
public interface ReplayRunAuthorizer {

    /**
     * @return a description of the authorized run (its {@code replayDate} when the orchestrator response
     *         could be parsed, else null) — used by the gateway to pin a run-backed replay to the run's
     *         authoritative chain expiry. Returning normally means ownership was positively confirmed.
     * @throws ReplayRunAuthorizationException if the caller is not a confirmed owner of {@code runId},
     *                                         or ownership could not be confirmed.
     */
    AuthorizedRun authorizeRun(String bearerToken, String runId);

    /**
     * Minimal projection of an authorized run. {@code replayDate} is the run's exchange-local session date
     * (ISO {@code yyyy-MM-dd}) when known, else null — never an authorization signal (authorization is
     * conveyed by returning vs throwing), only the source for the run's authoritative chain expiry.
     */
    record AuthorizedRun(String replayDate) {
        public static final AuthorizedRun UNKNOWN = new AuthorizedRun(null);
    }

    /** Thrown when ownership of {@code runId} cannot be positively confirmed for the caller. */
    final class ReplayRunAuthorizationException extends RuntimeException {
        public ReplayRunAuthorizationException(String message) {
            super(message);
        }

        public ReplayRunAuthorizationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}

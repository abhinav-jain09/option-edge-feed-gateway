package app.feedgateway.mtsession;

/**
 * Thrown when a concurrency limit (OE-DDD-001 §6.8, FR-27) would be exceeded. The {@link #limit}
 * names which ceiling was hit so the API layer can map it to the right response
 * ({@code 503 CAPACITY} or {@code 409 SESSION_LIMIT}).
 */
public final class CapacityException extends RuntimeException {

    public enum Limit { TOTAL_APP_SESSIONS, APP_SESSIONS_PER_USER, SOCKETS_PER_APP_SESSION }

    private final Limit limit;

    public CapacityException(Limit limit, String message) {
        super(message);
        this.limit = limit;
    }

    public Limit limit() {
        return limit;
    }
}

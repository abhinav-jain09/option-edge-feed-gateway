package app.feedgateway.mtsession;

/** Thrown when a session attempts a mode/action it is not entitled to (FR-12). Maps to HTTP 403. */
public final class NotEntitledException extends RuntimeException {
    public NotEntitledException(String message) {
        super(message);
    }
}

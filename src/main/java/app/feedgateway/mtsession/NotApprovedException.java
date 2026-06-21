package app.feedgateway.mtsession;

/**
 * Thrown when a user who is not {@link ApprovalState#APPROVED} attempts to obtain a session
 * (OE-DDD-001 §5.4, FR-15). Maps to HTTP 403 NOT_APPROVED.
 */
public final class NotApprovedException extends RuntimeException {
    public NotApprovedException(String message) {
        super(message);
    }
}

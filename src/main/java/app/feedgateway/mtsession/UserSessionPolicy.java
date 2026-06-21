package app.feedgateway.mtsession;

/**
 * Per-user session-timeout policy (OE-DDD-001 §6.3, FR-17).
 *
 * <p>Invariants enforced on construction:
 * <ul>
 *   <li>{@code idleTimeoutMinutes >= 10} (policy floor);</li>
 *   <li>{@code maxSessionMinutes >= 0}, where {@code 0} means unlimited;</li>
 *   <li>unlimited ({@code maxSessionMinutes == 0}) is permitted only when {@code unlimitedAllowed}.</li>
 * </ul>
 */
public record UserSessionPolicy(int idleTimeoutMinutes, int maxSessionMinutes, boolean unlimitedAllowed) {

    public static final int MIN_IDLE_MINUTES = 10;

    public UserSessionPolicy {
        if (idleTimeoutMinutes < MIN_IDLE_MINUTES) {
            throw new IllegalArgumentException(
                    "idleTimeoutMinutes must be >= " + MIN_IDLE_MINUTES + ", got " + idleTimeoutMinutes);
        }
        if (maxSessionMinutes < 0) {
            throw new IllegalArgumentException("maxSessionMinutes must be >= 0, got " + maxSessionMinutes);
        }
        if (maxSessionMinutes == 0 && !unlimitedAllowed) {
            throw new IllegalArgumentException("unlimited session (maxSessionMinutes=0) requires unlimitedAllowed=true");
        }
    }

    public boolean isUnlimited() {
        return maxSessionMinutes == 0;
    }

    /** System default: idle 30 min, max 10 h, unlimited not allowed (OE-DDD-001 §6.3). */
    public static UserSessionPolicy systemDefault() {
        return new UserSessionPolicy(30, 600, false);
    }
}

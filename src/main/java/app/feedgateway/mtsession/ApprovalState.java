package app.feedgateway.mtsession;

/**
 * Administrator-gated access state for a user (OE-DDD-001 §5.4).
 *
 * <p>Only {@link #APPROVED} grants data access; transitions are governed by
 * {@link ApprovalStateMachine}.
 */
public enum ApprovalState {
    PENDING_APPROVAL,
    APPROVED,
    REJECTED,
    SUSPENDED;

    /** Whether a user in this state may obtain sessions / data (FR-15, FR-23). */
    public boolean grantsAccess() {
        return this == APPROVED;
    }
}

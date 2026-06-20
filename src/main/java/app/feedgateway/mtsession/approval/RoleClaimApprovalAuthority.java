package app.feedgateway.mtsession.approval;

import java.util.Objects;

/**
 * Treats a dedicated, ADMIN-GRANTED realm role as the approval record (dev/simple deployments). The role
 * (e.g. {@code oe-approved}) must NOT be a Keycloak default role, so public self-registration does not grant
 * it — only an administrator assigning it approves the user. Access is bounded by the token's own expiry.
 *
 * <p>This is an explicit, logged opt-in. It cannot enforce mid-session REVOCATION on its own (a role is fixed
 * for the life of a token), so security-critical deployments should use {@link HttpApprovalAuthority} against
 * the authoritative approval platform, which the reaper can re-query for live suspension/expiry.
 */
public final class RoleClaimApprovalAuthority implements ApprovalAuthority {

    private final String approvalRole;

    public RoleClaimApprovalAuthority(String approvalRole) {
        this.approvalRole = Objects.requireNonNull(approvalRole, "approvalRole");
        if (approvalRole.isBlank()) {
            throw new IllegalArgumentException("approvalRole must not be blank");
        }
    }

    @Override
    public ApprovalDecision decide(ApprovalQuery query) {
        if (query == null || query.roles() == null || !query.roles().contains(approvalRole)) {
            return ApprovalDecision.DENY;
        }
        long expiresAtMs = query.tokenExpiresAt() != null ? query.tokenExpiresAt().toEpochMilli() : 0L;
        return ApprovalDecision.approved(expiresAtMs);
    }
}

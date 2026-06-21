package app.feedgateway.mtsession.approval;

import app.feedgateway.mtsession.ApprovalState;
import java.time.Instant;
import java.util.Set;

/**
 * Authoritative gate for whether a user may obtain sessions / receive data (OE-DDD-001 §5.4, FR-15).
 *
 * <p>P0 (approval enforcement): approval is NOT implied by holding a valid token. The realm enables public
 * self-registration, so "authenticated" must never mean "approved". This port consults an authoritative
 * approval record and is consulted BOTH at ticket issuance AND continuously during active sessions (see
 * {@code ApprovalReaper}). Every implementation MUST fail closed — deny when the record is missing,
 * unavailable, expired, suspended, rejected, or merely pending.
 */
public interface ApprovalAuthority {

    ApprovalDecision decide(ApprovalQuery query);

    /** What we know about the caller when asking for an approval decision. */
    record ApprovalQuery(String issuer, String userId, Set<String> roles, Instant tokenExpiresAt) {
    }

    /** An approval decision with an optional hard expiry (0 = no expiry). */
    record ApprovalDecision(ApprovalState state, long expiresAtMs) {

        /** The universal deny — used for every "missing / unavailable / error" outcome. */
        public static final ApprovalDecision DENY = new ApprovalDecision(ApprovalState.PENDING_APPROVAL, 0L);

        public static ApprovalDecision approved(long expiresAtMs) {
            return new ApprovalDecision(ApprovalState.APPROVED, expiresAtMs);
        }

        /** Grants access only if APPROVED and not past its expiry — fail-closed on null/expired. */
        public boolean grantsAccess(long nowMs) {
            return state != null && state.grantsAccess() && (expiresAtMs <= 0L || nowMs < expiresAtMs);
        }
    }

    /** Fail-closed authority: denies everyone. The default when no approval source is configured. */
    final class DenyAll implements ApprovalAuthority {
        @Override
        public ApprovalDecision decide(ApprovalQuery query) {
            return ApprovalDecision.DENY;
        }
    }
}

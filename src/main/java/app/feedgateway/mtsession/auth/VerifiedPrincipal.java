package app.feedgateway.mtsession.auth;

import java.util.Set;

/**
 * The trusted identity extracted from a verified Keycloak access token (OE-DDD-001 §5.1/§5.3).
 *
 * @param userId   the canonical user id ({@code sub} claim)
 * @param username the {@code preferred_username} claim (may be null)
 * @param roles    realm roles from {@code realm_access.roles}
 * @param clientId the authorized party ({@code azp} claim)
 */
public record VerifiedPrincipal(String userId, String username, Set<String> roles, String clientId,
                               java.time.Instant expiresAt) {

    public VerifiedPrincipal {
        roles = roles == null ? Set.of() : Set.copyOf(roles);
    }

    /** Back-compat: principal without a token-expiry (the socket reaper simply won't time-bound it). */
    public VerifiedPrincipal(String userId, String username, Set<String> roles, String clientId) {
        this(userId, username, roles, clientId, null);
    }

    public boolean hasRole(String role) {
        return roles.contains(role);
    }
}

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
public record VerifiedPrincipal(String userId, String username, Set<String> roles, String clientId) {

    public VerifiedPrincipal {
        roles = roles == null ? Set.of() : Set.copyOf(roles);
    }

    public boolean hasRole(String role) {
        return roles.contains(role);
    }
}

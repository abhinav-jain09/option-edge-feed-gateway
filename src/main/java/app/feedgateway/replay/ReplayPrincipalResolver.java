package app.feedgateway.replay;

import java.util.Optional;

/**
 * Resolves the authenticated principal ({@code sub}) from a request's {@code Authorization: Bearer <jwt>}
 * header for the replay control endpoints. Returns empty when the header is missing or the token is
 * invalid/expired — the controller then fails the request closed (401), never assuming an identity.
 */
@FunctionalInterface
public interface ReplayPrincipalResolver {
    Optional<String> principal(String authorizationHeader);
}

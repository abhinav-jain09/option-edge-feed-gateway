package app.feedgateway.mtsession.auth;

/**
 * Verifies an access token and returns its principal (OE-DDD-001 §5.3). A seam over
 * {@link KeycloakJwtVerifier} so the ticket-issuing service is unit-testable without a live IdP.
 */
public interface TokenVerifier {

    VerifiedPrincipal verify(String token) throws JwtVerificationException;
}

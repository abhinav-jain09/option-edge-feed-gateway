package app.feedgateway.mtsession.auth;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.jwk.source.JWKSourceBuilder;
import com.nimbusds.jose.proc.JWSKeySelector;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.proc.ConfigurableJWTProcessor;
import com.nimbusds.jwt.proc.DefaultJWTClaimsVerifier;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Verifies Keycloak-issued access tokens against the realm JWKS (OE-DDD-001 §5.3, FM-2).
 *
 * <p>Enforces: RS256 signature against the realm's published keys (fetched and cached from the
 * {@code /protocol/openid-connect/certs} endpoint), exact issuer match, presence of {@code sub}
 * and a valid {@code exp}/{@code iat}, and — when configured — that the authorized party
 * ({@code azp}) equals the expected browser client. Any failure throws {@link JwtVerificationException}.
 *
 * <p>Thread-safe and intended to be a long-lived singleton; the JWKS source caches keys and refreshes
 * on rotation.
 */
public final class KeycloakJwtVerifier implements TokenVerifier {

    private final String issuer;
    private final String expectedClientId; // nullable → azp not enforced
    private final ConfigurableJWTProcessor<SecurityContext> processor;

    /**
     * @param issuerUri        e.g. {@code http://localhost:8099/realms/optionsedge}
     * @param expectedClientId required {@code azp}, or {@code null} to skip the azp check
     */
    public KeycloakJwtVerifier(String issuerUri, String expectedClientId) {
        this.issuer = Objects.requireNonNull(issuerUri, "issuerUri");
        this.expectedClientId = expectedClientId;
        this.processor = buildProcessor(issuerUri);
    }

    private static ConfigurableJWTProcessor<SecurityContext> buildProcessor(String issuerUri) {
        URL jwksUrl;
        try {
            jwksUrl = URI.create(issuerUri + "/protocol/openid-connect/certs").toURL();
        } catch (MalformedURLException | IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid issuer URI: " + issuerUri, e);
        }
        JWKSource<SecurityContext> jwkSource = JWKSourceBuilder.create(jwksUrl).build();
        JWSKeySelector<SecurityContext> keySelector =
                new JWSVerificationKeySelector<>(JWSAlgorithm.RS256, jwkSource);

        ConfigurableJWTProcessor<SecurityContext> p = new DefaultJWTProcessor<>();
        p.setJWSKeySelector(keySelector);
        p.setJWTClaimsSetVerifier(new DefaultJWTClaimsVerifier<>(
                new JWTClaimsSet.Builder().issuer(issuerUri).build(),
                Set.of("sub", "exp", "iat")));
        return p;
    }

    /**
     * Verify a compact JWS access token and return its principal.
     *
     * @throws JwtVerificationException if the token is invalid in any way.
     */
    public VerifiedPrincipal verify(String token) throws JwtVerificationException {
        if (token == null || token.isBlank()) {
            throw new JwtVerificationException("empty token");
        }
        final JWTClaimsSet claims;
        try {
            claims = processor.process(token, null);
        } catch (Exception e) {
            throw new JwtVerificationException("token verification failed: " + e.getMessage(), e);
        }

        String azp;
        try {
            azp = claims.getStringClaim("azp");
        } catch (java.text.ParseException e) {
            throw new JwtVerificationException("invalid azp claim", e);
        }
        if (expectedClientId != null && !expectedClientId.equals(azp)) {
            throw new JwtVerificationException("unexpected authorized party (azp): " + azp);
        }

        String username;
        try {
            username = claims.getStringClaim("preferred_username");
        } catch (java.text.ParseException e) {
            username = null;
        }
        return new VerifiedPrincipal(claims.getSubject(), username, extractRealmRoles(claims), azp);
    }

    public String issuer() {
        return issuer;
    }

    private static Set<String> extractRealmRoles(JWTClaimsSet claims) {
        Map<String, Object> realmAccess;
        try {
            realmAccess = claims.getJSONObjectClaim("realm_access");
        } catch (java.text.ParseException e) {
            return Set.of();
        }
        if (realmAccess == null) {
            return Set.of();
        }
        Object roles = realmAccess.get("roles");
        if (!(roles instanceof List<?> list)) {
            return Set.of();
        }
        Set<String> out = new LinkedHashSet<>();
        for (Object r : list) {
            if (r != null) {
                out.add(r.toString());
            }
        }
        return out;
    }
}

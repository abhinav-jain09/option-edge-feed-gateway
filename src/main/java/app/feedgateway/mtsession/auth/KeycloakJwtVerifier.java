package app.feedgateway.mtsession.auth;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.jwk.source.JWKSourceBuilder;
import com.nimbusds.jose.proc.JWSKeySelector;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jose.util.DefaultResourceRetriever;
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
 * and a valid {@code exp}/{@code iat}, that the token's {@code aud} contains the expected audience
 * (when configured), and — when configured — that the authorized party ({@code azp}) equals the
 * expected browser client. Any failure throws {@link JwtVerificationException}.
 *
 * <p>Algorithm is pinned to RS256 via {@link JWSVerificationKeySelector}, so {@code alg=none} and
 * HS256-with-the-public-key confusion attacks cannot select a key.
 *
 * <p>The JWKS fetch uses a bounded {@link DefaultResourceRetriever} (connect + read timeouts, size cap)
 * with a retrying, rate-limited, refresh-ahead cache, so a slow/flapping Keycloak JWKS endpoint can never
 * hang a verification thread indefinitely (review finding #9).
 *
 * <p>Thread-safe; intended to be a long-lived singleton.
 */
public final class KeycloakJwtVerifier implements TokenVerifier {

    // JWKS fetch bounds — a slow JWKS endpoint must not wedge a request thread.
    private static final int JWKS_CONNECT_TIMEOUT_MS = 2_000;
    private static final int JWKS_READ_TIMEOUT_MS = 2_000;
    private static final int JWKS_SIZE_LIMIT_BYTES = 256 * 1024;

    private final String issuer;
    private final String expectedClientId;  // nullable → azp not enforced
    private final String expectedAudience;   // nullable/blank → aud not enforced
    private final ConfigurableJWTProcessor<SecurityContext> processor;

    public KeycloakJwtVerifier(String issuerUri, String expectedClientId) {
        this(issuerUri, null, expectedClientId, (String) null);
    }

    /** Test seam: build a verifier whose keys come from an in-memory {@link JWKSource} (no network). */
    static KeycloakJwtVerifier forJwkSource(String issuerUri, String expectedClientId, String expectedAudience,
                                            JWKSource<SecurityContext> jwkSource) {
        return new KeycloakJwtVerifier(issuerUri, expectedClientId, expectedAudience, jwkSource);
    }

    /**
     * @param issuerUri        the issuer the tokens carry (validated exactly), e.g. as seen by the browser
     * @param jwksUrlOverride  where to fetch signing keys (may differ from the issuer when Keycloak is
     *                         reached via a different network path); null → derive from the issuer
     * @param expectedClientId required {@code azp}, or {@code null} to skip the azp check
     * @param expectedAudience required value within {@code aud}, or {@code null}/blank to skip the aud check
     */
    public KeycloakJwtVerifier(String issuerUri, String jwksUrlOverride, String expectedClientId,
                               String expectedAudience) {
        this(issuerUri, expectedClientId, expectedAudience,
                jwkSourceFromUrl((jwksUrlOverride == null || jwksUrlOverride.isBlank())
                        ? issuerUri + "/protocol/openid-connect/certs"
                        : jwksUrlOverride));
    }

    private KeycloakJwtVerifier(String issuerUri, String expectedClientId, String expectedAudience,
                                JWKSource<SecurityContext> jwkSource) {
        this.issuer = Objects.requireNonNull(issuerUri, "issuerUri");
        this.expectedClientId = expectedClientId;
        this.expectedAudience = (expectedAudience == null || expectedAudience.isBlank()) ? null : expectedAudience;
        this.processor = buildProcessor(issuerUri, Objects.requireNonNull(jwkSource, "jwkSource"));
    }

    private static JWKSource<SecurityContext> jwkSourceFromUrl(String jwksUrlStr) {
        URL jwksUrl;
        try {
            jwksUrl = URI.create(jwksUrlStr).toURL();
        } catch (MalformedURLException | IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid JWKS URL: " + jwksUrlStr, e);
        }
        DefaultResourceRetriever retriever =
                new DefaultResourceRetriever(JWKS_CONNECT_TIMEOUT_MS, JWKS_READ_TIMEOUT_MS, JWKS_SIZE_LIMIT_BYTES);
        return JWKSourceBuilder.create(jwksUrl, retriever)
                .retrying(true)
                .rateLimited(true)
                .refreshAheadCache(true)
                .build();
    }

    private static ConfigurableJWTProcessor<SecurityContext> buildProcessor(
            String issuerUri, JWKSource<SecurityContext> jwkSource) {
        // RS256 pinned -> alg=none / HS256-with-public-key confusion cannot select a key.
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

        // Audience: the token must be intended for this surface. Keycloak access tokens may carry several
        // audiences (e.g. "account"), so require the expected value to be present (not an exact-equals).
        if (expectedAudience != null) {
            List<String> aud = claims.getAudience();
            if (aud == null || !aud.contains(expectedAudience)) {
                throw new JwtVerificationException("token audience does not include " + expectedAudience);
            }
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

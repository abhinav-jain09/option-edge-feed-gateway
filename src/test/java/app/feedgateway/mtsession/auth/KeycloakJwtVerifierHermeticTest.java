package app.feedgateway.mtsession.auth;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.PlainJWT;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * HERMETIC (no network) cryptographic verification tests for {@link KeycloakJwtVerifier} (review finding
 * #4). A generated RSA JWKS is injected via the test seam and tokens are signed locally, so every accept/
 * reject path runs deterministically on a bare {@code mvn test} — it never skips for missing infra.
 */
class KeycloakJwtVerifierHermeticTest {

    private static final String ISSUER = "https://kc.test/realms/optionsedge";
    private static final String CLIENT = "options-edge-web";
    private static final String AUD = "options-edge-web";

    private static RSAKey rsa;                 // signing key: kid "k1", includes the private key
    private static KeycloakJwtVerifier verifier;

    @BeforeAll
    static void setUp() throws Exception {
        rsa = new RSAKeyGenerator(2048).keyID("k1").generate();
        JWKSource<SecurityContext> src = new ImmutableJWKSet<>(new JWKSet(rsa.toPublicJWK()));
        verifier = KeycloakJwtVerifier.forJwkSource(ISSUER, CLIENT, AUD, src);
    }

    private static JWTClaimsSet.Builder validClaims() {
        long now = System.currentTimeMillis();
        return new JWTClaimsSet.Builder()
                .issuer(ISSUER).subject("u1").audience(AUD).claim("azp", CLIENT)
                .issueTime(new Date(now)).expirationTime(new Date(now + 300_000));
    }

    private static String signRs256(JWTClaimsSet claims, String kid) throws Exception {
        SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(kid).build(), claims);
        jwt.sign(new RSASSASigner(rsa));
        return jwt.serialize();
    }

    /** A verifier that ALSO enforces the access-token type (typ=Bearer), as production does. */
    private static KeycloakJwtVerifier typedVerifier() {
        JWKSource<SecurityContext> src = new ImmutableJWKSet<>(new JWKSet(rsa.toPublicJWK()));
        return KeycloakJwtVerifier.forJwkSource(ISSUER, CLIENT, AUD, "Bearer", src);
    }

    @Test
    void acceptsAccessTokenTypeBearer() throws Exception {
        assertEquals("u1",
                typedVerifier().verify(signRs256(validClaims().claim("typ", "Bearer").build(), "k1")).userId());
    }

    @Test
    void rejectsRefreshOrIdTokenByType() throws Exception {
        KeycloakJwtVerifier v = typedVerifier();
        assertThrows(JwtVerificationException.class,
                () -> v.verify(signRs256(validClaims().claim("typ", "Refresh").build(), "k1")));
        assertThrows(JwtVerificationException.class,
                () -> v.verify(signRs256(validClaims().claim("typ", "ID").build(), "k1")));
        // A token with no typ at all is rejected when token-type is enforced.
        assertThrows(JwtVerificationException.class,
                () -> v.verify(signRs256(validClaims().build(), "k1")));
    }

    @Test
    void validTokenIsAccepted() throws Exception {
        VerifiedPrincipal p = verifier.verify(signRs256(validClaims().build(), "k1"));
        assertEquals("u1", p.userId());
        assertEquals(CLIENT, p.clientId());
    }

    @Test
    void tamperedSignatureIsRejected() throws Exception {
        String t = signRs256(validClaims().build(), "k1");
        String tampered = t.substring(0, t.length() - 4) + (t.endsWith("AAAA") ? "BBBB" : "AAAA");
        assertThrows(JwtVerificationException.class, () -> verifier.verify(tampered));
    }

    @Test
    void algNoneIsRejected() {
        String unsigned = new PlainJWT(validClaims().build()).serialize();
        assertThrows(JwtVerificationException.class, () -> verifier.verify(unsigned));
    }

    @Test
    void hs256SignedWithThePublicKeyIsRejected() throws Exception {
        // Classic alg-confusion: HS256 using the RSA modulus bytes as the MAC secret. RS256 is pinned, so
        // the key selector never offers a symmetric key — must be rejected.
        byte[] secret = rsa.toRSAPublicKey().getModulus().toByteArray();
        if (secret.length < 32) {
            secret = Arrays.copyOf(secret, 32);
        }
        SignedJWT hs = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.HS256).keyID("k1").build(),
                validClaims().build());
        hs.sign(new MACSigner(secret));
        assertThrows(JwtVerificationException.class, () -> verifier.verify(hs.serialize()));
    }

    @Test
    void wrongIssuerIsRejected() throws Exception {
        String t = signRs256(validClaims().issuer("https://evil/realms/x").build(), "k1");
        assertThrows(JwtVerificationException.class, () -> verifier.verify(t));
    }

    @Test
    void expiredTokenIsRejected() throws Exception {
        long now = System.currentTimeMillis();
        String t = signRs256(validClaims().expirationTime(new Date(now - 120_000)).build(), "k1");
        assertThrows(JwtVerificationException.class, () -> verifier.verify(t));
    }

    @Test
    void wrongAudienceIsRejected() throws Exception {
        assertThrows(JwtVerificationException.class,
                () -> verifier.verify(signRs256(validClaims().audience("someone-else").build(), "k1")));
    }

    @Test
    void missingAudienceIsRejected() throws Exception {
        long now = System.currentTimeMillis();
        JWTClaimsSet noAud = new JWTClaimsSet.Builder()
                .issuer(ISSUER).subject("u1").claim("azp", CLIENT)
                .issueTime(new Date(now)).expirationTime(new Date(now + 300_000)).build();
        assertThrows(JwtVerificationException.class, () -> verifier.verify(signRs256(noAud, "k1")));
    }

    @Test
    void wrongAuthorizedPartyIsRejected() throws Exception {
        assertThrows(JwtVerificationException.class,
                () -> verifier.verify(signRs256(validClaims().claim("azp", "evil-client").build(), "k1")));
    }

    @Test
    void unknownKidIsRejected() throws Exception {
        assertThrows(JwtVerificationException.class,
                () -> verifier.verify(signRs256(validClaims().build(), "unknown-kid")));
    }
}

package app.feedgateway.mtsession.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import app.feedgateway.mtsession.EntitlementPolicy;
import app.feedgateway.mtsession.MarketDataSource;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Integration test against a live local Keycloak (see {@code dev/keycloak/run-keycloak.sh}).
 * Proves real JWKS verification end-to-end: a token is obtained from Keycloak via the password
 * grant and verified by {@link KeycloakJwtVerifier} (OE-DDD-001 §5.3, FM-2).
 *
 * <p>If Keycloak is not reachable the whole class is skipped (assumption), so the normal build
 * stays green without external infra.
 */
class KeycloakJwtVerifierIntegrationTest {

    private static final String ISSUER = "http://localhost:8099/realms/optionsedge";
    private static final String TOKEN_URL = ISSUER + "/protocol/openid-connect/token";
    private static final String CLIENT = "options-edge-web";

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3)).build();
    private static final ObjectMapper JSON = new ObjectMapper();

    @BeforeAll
    static void requireKeycloak() {
        assumeTrue(reachable(), "Keycloak not reachable at " + ISSUER + " — skipping integration test");
    }

    @Test
    @DisplayName("Valid trader token verifies and yields sub + realm roles")
    void verifiesValidTraderToken() throws Exception {
        String token = fetchToken("testtrader", "Test1234!");
        VerifiedPrincipal p = new KeycloakJwtVerifier(ISSUER, CLIENT).verify(token);

        assertNotNull(p.userId());
        assertEquals("testtrader", p.username());
        assertEquals(CLIENT, p.clientId());
        assertTrue(p.hasRole("trader"));
        assertTrue(p.hasRole("ibkr-user"));
        // entitlement mapping derived from the verified roles
        assertTrue(EntitlementPolicy.canTrade(p.roles()));
        assertTrue(EntitlementPolicy.canSelect(MarketDataSource.IBKR, p.roles()));
    }

    @Test
    @DisplayName("Tampered signature is rejected")
    void rejectsTamperedToken() throws Exception {
        String token = fetchToken("testtrader", "Test1234!");
        String[] parts = token.split("\\.");
        char first = parts[2].charAt(0);
        parts[2] = (first == 'A' ? 'B' : 'A') + parts[2].substring(1);
        String tampered = parts[0] + "." + parts[1] + "." + parts[2];

        assertThrows(JwtVerificationException.class,
                () -> new KeycloakJwtVerifier(ISSUER, CLIENT).verify(tampered));
    }

    @Test
    @DisplayName("Unexpected authorized party (azp) is rejected")
    void rejectsUnexpectedClient() throws Exception {
        String token = fetchToken("testtrader", "Test1234!");
        assertThrows(JwtVerificationException.class,
                () -> new KeycloakJwtVerifier(ISSUER, "some-other-client").verify(token));
    }

    @Test
    @DisplayName("Garbage / empty tokens are rejected")
    void rejectsGarbage() {
        KeycloakJwtVerifier v = new KeycloakJwtVerifier(ISSUER, CLIENT);
        assertThrows(JwtVerificationException.class, () -> v.verify("not.a.jwt"));
        assertThrows(JwtVerificationException.class, () -> v.verify(""));
        assertThrows(JwtVerificationException.class, () -> v.verify(null));
    }

    @Test
    @DisplayName("A baseline user lacks trader/ibkr-user entitlements")
    void baselineUserHasNoElevatedEntitlements() throws Exception {
        String token = fetchToken("databentouser", "Test1234!");
        VerifiedPrincipal p = new KeycloakJwtVerifier(ISSUER, CLIENT).verify(token);

        assertTrue(p.hasRole("user"));
        assertFalse(p.hasRole("trader"));
        assertFalse(p.hasRole("ibkr-user"));
        assertFalse(EntitlementPolicy.canTrade(p.roles()));
        assertFalse(EntitlementPolicy.canSelect(MarketDataSource.IBKR, p.roles()));
        assertTrue(EntitlementPolicy.canSelect(MarketDataSource.DATABENTO, p.roles()));
    }

    // ---- helpers ----

    private static boolean reachable() {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(ISSUER + "/.well-known/openid-configuration"))
                    .timeout(Duration.ofSeconds(3)).GET().build();
            return HTTP.send(req, BodyHandlers.ofString()).statusCode() == 200;
        } catch (Exception e) {
            return false;
        }
    }

    private static String fetchToken(String username, String password) throws Exception {
        String form = "grant_type=password"
                + "&client_id=" + CLIENT
                + "&username=" + username
                + "&password=" + password;
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(TOKEN_URL))
                .timeout(Duration.ofSeconds(5))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(BodyPublishers.ofString(form))
                .build();
        HttpResponse<String> resp = HTTP.send(req, BodyHandlers.ofString());
        if (resp.statusCode() != 200) {
            throw new IllegalStateException("token endpoint returned " + resp.statusCode() + ": " + resp.body());
        }
        return JSON.readTree(resp.body()).get("access_token").asText();
    }
}

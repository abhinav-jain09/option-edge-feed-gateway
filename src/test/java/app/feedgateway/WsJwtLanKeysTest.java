package app.feedgateway;

import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.*;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.*;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.JwtException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import static org.junit.jupiter.api.Assertions.*;

class WsJwtLanKeysTest {
    @Test void internalKeyFetchRetainsIssuerAudienceExpiryAndSignatureChecks() throws Exception {
        RSAKey key = new RSAKeyGenerator(2048).keyID("lan-test").generate();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/certs", exchange -> {
            byte[] bytes = new JWKSet(key.toPublicJWK()).toString().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (var body = exchange.getResponseBody()) { body.write(bytes); }
        });
        server.start();
        String oldIssuer = System.getProperty("WS_AUTH_ISSUER_URI");
        String oldKeys = System.getProperty("GATEWAY_KEYCLOAK_JWKS_URL");
        String oldAudience = System.getProperty("WS_AUTH_AUDIENCE");
        try {
            System.setProperty("WS_AUTH_ISSUER_URI", "https://unreachable.invalid/realms/dev");
            System.setProperty("WS_AUTH_AUDIENCE", "options-edge-web");
            System.setProperty("GATEWAY_KEYCLOAK_JWKS_URL", "http://127.0.0.1:" + server.getAddress().getPort() + "/certs");
            var decoder = WsJwtHandshakeInterceptor.buildWsDecoder(new GatewaySettings());
            assertEquals("tester", decoder.decode(token(key, "https://unreachable.invalid/realms/dev", "options-edge-web", 300)).getSubject());
            assertThrows(JwtException.class, () -> decoder.decode(token(key, "https://other.invalid", "options-edge-web", 300)));
            assertThrows(JwtException.class, () -> decoder.decode(token(key, "https://unreachable.invalid/realms/dev", "wrong-audience", 300)));
            assertThrows(JwtException.class, () -> decoder.decode(token(key, "https://unreachable.invalid/realms/dev", "options-edge-web", -300)));
            RSAKey other = new RSAKeyGenerator(2048).keyID("lan-test").generate();
            assertThrows(JwtException.class, () -> decoder.decode(token(other, "https://unreachable.invalid/realms/dev", "options-edge-web", 300)));
        } finally {
            restore("WS_AUTH_ISSUER_URI", oldIssuer); restore("GATEWAY_KEYCLOAK_JWKS_URL", oldKeys);
            restore("WS_AUTH_AUDIENCE", oldAudience); server.stop(0);
        }
    }
    private static void restore(String name, String value) {
        if (value == null) System.clearProperty(name); else System.setProperty(name, value);
    }
    private static String token(RSAKey key, String issuer, String audience, int seconds) throws Exception {
        var claims = new JWTClaimsSet.Builder().issuer(issuer).audience(audience).subject("tester")
                .issueTime(Date.from(Instant.now().minusSeconds(600)))
                .expirationTime(Date.from(Instant.now().plusSeconds(seconds))).build();
        var jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.getKeyID()).build(), claims);
        jwt.sign(new RSASSASigner(key)); return jwt.serialize();
    }
}

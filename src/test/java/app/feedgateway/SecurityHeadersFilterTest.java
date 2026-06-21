package app.feedgateway;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * P1: the CSP is a DEPLOYMENT-AWARE response header — its connect-src is derived from the configured
 * Keycloak issuer so PKCE token/refresh/revoke calls work across dev/prod without hardcoding an origin —
 * while scripts stay confined to 'self' (no inline JS, no CDN).
 */
class SecurityHeadersFilterTest {

    private String cspWithIssuer(String issuer) {
        Map<String, String> prev = new HashMap<>();
        prev.put("GATEWAY_KEYCLOAK_ISSUER", System.getProperty("GATEWAY_KEYCLOAK_ISSUER"));
        try {
            System.setProperty("GATEWAY_KEYCLOAK_ISSUER", issuer);
            return new SecurityHeadersFilter(new GatewaySettings()).contentSecurityPolicy();
        } finally {
            prev.forEach((k, v) -> {
                if (v == null) {
                    System.clearProperty(k);
                } else {
                    System.setProperty(k, v);
                }
            });
        }
    }

    @Test
    void connectSrcAllowsTheConfiguredKeycloakOrigin() {
        String csp = cspWithIssuer("https://keycloak.prod.example.com:8443/realms/optionsedge");
        assertTrue(csp.contains("connect-src 'self' https://keycloak.prod.example.com:8443"),
                "connect-src must allow the deployment's Keycloak origin (scheme://host:port), got: " + csp);
        assertFalse(csp.contains("localhost"), "no hardcoded localhost in the CSP");
    }

    @Test
    void cspIsRestrictiveRegardlessOfIssuer() {
        String csp = cspWithIssuer("http://192.168.100.102:8089/realms/optionsedge");
        assertTrue(csp.contains("default-src 'none'"));
        assertTrue(csp.contains("script-src 'self'"));
        assertTrue(csp.contains("frame-ancestors 'none'"));
        assertTrue(csp.contains("base-uri 'none'"));
        // script-src must not weaken to inline/eval/CDN.
        String scriptSrc = csp.replaceAll(".*script-src([^;]*);.*", "$1");
        assertFalse(scriptSrc.contains("'unsafe-inline'"));
        assertFalse(scriptSrc.contains("'unsafe-eval'"));
        assertFalse(scriptSrc.contains("http"));
    }

    @Test
    void blankIssuerYieldsSelfOnlyConnectSrc() {
        String csp = cspWithIssuer("");
        assertTrue(csp.contains("connect-src 'self';"), "with no issuer, connect-src is self-only: " + csp);
    }

    @Test
    void originOfParsesSchemeHostPort() {
        assertEquals("https://kc.example.com:8443",
                SecurityHeadersFilter.originOf("https://kc.example.com:8443/realms/optionsedge"));
        assertEquals("http://localhost", SecurityHeadersFilter.originOf("http://localhost/realms/x"));
        assertEquals("", SecurityHeadersFilter.originOf(""));
        assertEquals("", SecurityHeadersFilter.originOf("not a url"));
    }
}

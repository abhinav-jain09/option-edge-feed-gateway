package app.feedgateway;

import app.feedgateway.mtsession.ConcurrencyLimits;
import app.feedgateway.mtsession.InMemoryTicketStore;
import app.feedgateway.mtsession.NotApprovedException;
import app.feedgateway.mtsession.SessionRoutingEngine;
import app.feedgateway.mtsession.SubscriptionManager;
import app.feedgateway.mtsession.approval.ApprovalAuthority;
import app.feedgateway.mtsession.auth.JwtVerificationException;
import app.feedgateway.mtsession.auth.TokenVerifier;
import app.feedgateway.mtsession.auth.VerifiedPrincipal;
import app.feedgateway.mtsession.gateway.TicketHandshakeInterceptor;
import app.feedgateway.mtsession.gateway.WsTicketService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class WsJwtHandshakeInterceptorTest {

    // --- token extraction from the Sec-WebSocket-Protocol subprotocol ---

    @Test
    void extractsTokenAfterBearerMarker() {
        HttpHeaders headers = new HttpHeaders();
        headers.add("Sec-WebSocket-Protocol", "oc.bearer, eyJ.token.value");
        assertEquals("eyJ.token.value", WsJwtHandshakeInterceptor.extractToken(headers));
    }

    @Test
    void extractsTokenWhenOfferedAsSeparateHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.add("Sec-WebSocket-Protocol", "oc.bearer");
        headers.add("Sec-WebSocket-Protocol", "abc.def.ghi");
        assertEquals("abc.def.ghi", WsJwtHandshakeInterceptor.extractToken(headers));
    }

    @Test
    void returnsNullWhenMarkerMissing() {
        HttpHeaders headers = new HttpHeaders();
        headers.add("Sec-WebSocket-Protocol", "some.other.proto");
        assertNull(WsJwtHandshakeInterceptor.extractToken(headers));
    }

    @Test
    void returnsNullWhenMarkerHasNoToken() {
        HttpHeaders headers = new HttpHeaders();
        headers.add("Sec-WebSocket-Protocol", "oc.bearer");
        assertNull(WsJwtHandshakeInterceptor.extractToken(headers));
    }

    @Test
    void returnsNullWhenNoSubprotocolHeader() {
        assertNull(WsJwtHandshakeInterceptor.extractToken(new HttpHeaders()));
    }

    // --- handshake gating ---

    @Test
    void allowsHandshakeWhenAuthDisabled() {
        GatewaySettings settings = new GatewaySettings(); // WS_AUTH_ENABLED defaults to false
        WsJwtHandshakeInterceptor interceptor = new WsJwtHandshakeInterceptor(settings, failingDecoder());

        Map<String, Object> attributes = new HashMap<>();
        boolean allowed = interceptor.beforeHandshake(requestWith(new HttpHeaders()), mock(ServerHttpResponse.class),
                null, attributes);

        assertTrue(allowed);
        assertTrue(attributes.isEmpty());
    }

    @Test
    void rejectsHandshakeWithMissingTokenWhenAuthEnabled() {
        withAuthEnabled(() -> {
            WsJwtHandshakeInterceptor interceptor =
                    new WsJwtHandshakeInterceptor(new GatewaySettings(), failingDecoder());
            ServerHttpResponse response = mock(ServerHttpResponse.class);

            boolean allowed = interceptor.beforeHandshake(requestWith(new HttpHeaders()), response,
                    null, new HashMap<>());

            assertFalse(allowed);
            org.mockito.Mockito.verify(response).setStatusCode(HttpStatus.UNAUTHORIZED);
        });
    }

    @Test
    void rejectsHandshakeWithInvalidTokenWhenAuthEnabled() {
        withAuthEnabled(() -> {
            WsJwtHandshakeInterceptor interceptor =
                    new WsJwtHandshakeInterceptor(new GatewaySettings(), failingDecoder());
            ServerHttpResponse response = mock(ServerHttpResponse.class);

            HttpHeaders headers = new HttpHeaders();
            headers.add("Sec-WebSocket-Protocol", "oc.bearer, bad.token");

            boolean allowed = interceptor.beforeHandshake(requestWith(headers), response, null, new HashMap<>());

            assertFalse(allowed);
            org.mockito.Mockito.verify(response).setStatusCode(HttpStatus.UNAUTHORIZED);
        });
    }

    @Test
    void allowsHandshakeWithValidTokenAndCapturesClaims() {
        withAuthEnabled(() -> {
            Instant exp = Instant.parse("2030-01-01T00:00:00Z");
            Jwt jwt = Jwt.withTokenValue("good.token")
                    .header("alg", "RS256")
                    .claim("sub", "user-123")
                    .subject("user-123")
                    .issuedAt(Instant.parse("2026-01-01T00:00:00Z"))
                    .expiresAt(exp)
                    .build();
            JwtDecoder decoder = token -> jwt;
            WsJwtHandshakeInterceptor interceptor =
                    new WsJwtHandshakeInterceptor(new GatewaySettings(), decoder);

            HttpHeaders headers = new HttpHeaders();
            headers.add("Sec-WebSocket-Protocol", "oc.bearer, good.token");
            Map<String, Object> attributes = new HashMap<>();

            boolean allowed = interceptor.beforeHandshake(requestWith(headers), mock(ServerHttpResponse.class),
                    null, attributes);

            assertTrue(allowed);
            assertEquals("user-123", attributes.get("sub"));
            assertEquals(exp.toEpochMilli(), attributes.get("authExpiresAtEpochMs"));
        });
    }

    /** GatewaySettings reads WS_AUTH_ENABLED from env or system property; toggle it for the duration. */
    private static void withAuthEnabled(Runnable body) {
        String previous = System.getProperty("WS_AUTH_ENABLED");
        System.setProperty("WS_AUTH_ENABLED", "true");
        try {
            body.run();
        } finally {
            if (previous == null) {
                System.clearProperty("WS_AUTH_ENABLED");
            } else {
                System.setProperty("WS_AUTH_ENABLED", previous);
            }
        }
    }

    private static JwtDecoder failingDecoder() {
        return token -> {
            throw new JwtException("invalid");
        };
    }

    private static ServerHttpRequest requestWith(HttpHeaders headers) {
        ServerHttpRequest request = mock(ServerHttpRequest.class);
        when(request.getHeaders()).thenReturn(headers);
        return request;
    }

    // --- DUAL-AUTH (multi-tenant mode): WsTicketService present, bearer flow MUST bind AppSession ---

    /**
     * Helper that captures prior {@code IB_SYMBOL}/{@code IB_EXPIRY} sys-property values, sets them to the
     * supplied values for the duration of the test body, then restores them — never plain-clearing. Plain
     * clearing would corrupt later tests that depend on a pre-set value (review finding L-1).
     */
    private static void withSymbolAndExpiry(String symbol, String expiry, Runnable body) {
        String prevSym = System.getProperty("IB_SYMBOL");
        String prevExp = System.getProperty("IB_EXPIRY");
        if (symbol == null) System.clearProperty("IB_SYMBOL"); else System.setProperty("IB_SYMBOL", symbol);
        if (expiry == null) System.clearProperty("IB_EXPIRY"); else System.setProperty("IB_EXPIRY", expiry);
        try {
            body.run();
        } finally {
            if (prevSym == null) System.clearProperty("IB_SYMBOL"); else System.setProperty("IB_SYMBOL", prevSym);
            if (prevExp == null) System.clearProperty("IB_EXPIRY"); else System.setProperty("IB_EXPIRY", prevExp);
        }
    }

    private static WsTicketService multiTenantService(String token, VerifiedPrincipal principal,
                                                      ApprovalAuthority approval) {
        TokenVerifier verifier = t -> {
            if (token.equals(t)) {
                return principal;
            }
            throw new JwtVerificationException("bad token");
        };
        SessionRoutingEngine engine =
                new SessionRoutingEngine(new ConcurrencyLimits(1, 4, 100), new SubscriptionManager());
        AtomicInteger seq = new AtomicInteger();
        InMemoryTicketStore store = new InMemoryTicketStore(Clock.systemUTC(), () -> "tk-" + seq.incrementAndGet());
        return new WsTicketService(verifier, store, engine, approval,
                "https://kc.test/realms/optionsedge", Duration.ofSeconds(10));
    }

    @Test
    void multiTenantBearerBindsAppSessionViaTicketService() {
        withSymbolAndExpiry("SPX", "20260612", () -> {
            GatewaySettings settings = new GatewaySettings();
            Instant exp = Instant.parse("2030-01-01T00:00:00Z");
            VerifiedPrincipal principal = new VerifiedPrincipal("u-bearer", "alice",
                    Set.of("user", "trader"), "options-edge-web", exp);
            WsTicketService svc =
                    multiTenantService("good.bearer", principal, q -> ApprovalAuthority.ApprovalDecision.approved(0L));
            // The decoder is unused in multi-tenant mode; pass one that would fail so the test proves we go
            // through WsTicketService.verify, not the legacy NimbusJwtDecoder.
            WsJwtHandshakeInterceptor interceptor =
                    new WsJwtHandshakeInterceptor(settings, failingDecoder(), svc);

            HttpHeaders headers = new HttpHeaders();
            headers.add("Sec-WebSocket-Protocol", "oc.bearer, good.bearer");
            Map<String, Object> attrs = new HashMap<>();

            boolean ok = interceptor.beforeHandshake(requestWith(headers), mock(ServerHttpResponse.class), null, attrs);

            assertTrue(ok);
            assertEquals("app:u-bearer", attrs.get(TicketHandshakeInterceptor.ATTR_APP_SESSION_ID));
            assertEquals("u-bearer", attrs.get(TicketHandshakeInterceptor.ATTR_USER_ID));
            assertEquals(exp.toEpochMilli(), attrs.get(WsJwtHandshakeInterceptor.AUTH_EXPIRES_AT_ATTR),
                    "the reaper must see token expiry on the bearer-flow socket too");
            assertEquals("app:u-bearer", "app:" + svc.appSessionForUser("u-bearer").userId());
        });
    }

    @Test
    void multiTenantBearerSkipsWhenAppSessionAlreadyBound() {
        // Ticket interceptor already accepted this handshake and bound the AppSession. The bearer
        // interceptor MUST NOT reject for missing `oc.bearer` — it sees the attr and steps out.
        withSymbolAndExpiry("SPX", "20260612", () -> {
            GatewaySettings settings = new GatewaySettings();
            VerifiedPrincipal principal =
                    new VerifiedPrincipal("u-ticket", "t", Set.of("user"), "options-edge-web", null);
            WsTicketService svc =
                    multiTenantService("never.used", principal, q -> ApprovalAuthority.ApprovalDecision.approved(0L));
            WsJwtHandshakeInterceptor interceptor =
                    new WsJwtHandshakeInterceptor(settings, failingDecoder(), svc);

            Map<String, Object> attrs = new HashMap<>();
            attrs.put(TicketHandshakeInterceptor.ATTR_APP_SESSION_ID, "app:u-ticket");

            // No `oc.bearer` offered (ticket flow) — must NOT touch the response.
            ServerHttpResponse resp = mock(ServerHttpResponse.class);
            boolean ok = interceptor.beforeHandshake(requestWith(new HttpHeaders()), resp, null, attrs);

            assertTrue(ok);
            verifyNoInteractions(resp);
        });
    }

    @Test
    void multiTenantBearerWithMissingTokenIs401() {
        withSymbolAndExpiry("SPX", "20260612", () -> {
            GatewaySettings settings = new GatewaySettings();
            WsTicketService svc = multiTenantService("anything",
                    new VerifiedPrincipal("u", "u", Set.of(), "c", null),
                    q -> ApprovalAuthority.ApprovalDecision.approved(0L));
            WsJwtHandshakeInterceptor interceptor =
                    new WsJwtHandshakeInterceptor(settings, failingDecoder(), svc);

            ServerHttpResponse resp = mock(ServerHttpResponse.class);
            boolean ok = interceptor.beforeHandshake(requestWith(new HttpHeaders()), resp, null, new HashMap<>());

            assertFalse(ok);
            org.mockito.Mockito.verify(resp).setStatusCode(HttpStatus.UNAUTHORIZED);
        });
    }

    @Test
    void multiTenantBearerNotApprovedIs401AndDoesNotRegisterAppSession() {
        // P0: a valid token is NOT approval. Default-deny when the approval authority does not grant access.
        withSymbolAndExpiry("SPX", "20260612", () -> {
            GatewaySettings settings = new GatewaySettings();
            VerifiedPrincipal principal =
                    new VerifiedPrincipal("u-denied", "d", Set.of("user"), "options-edge-web", null);
            WsTicketService svc = multiTenantService("good", principal, q -> ApprovalAuthority.ApprovalDecision.DENY);
            WsJwtHandshakeInterceptor interceptor =
                    new WsJwtHandshakeInterceptor(settings, failingDecoder(), svc);

            HttpHeaders headers = new HttpHeaders();
            headers.add("Sec-WebSocket-Protocol", "oc.bearer, good");
            ServerHttpResponse resp = mock(ServerHttpResponse.class);

            boolean ok = interceptor.beforeHandshake(requestWith(headers), resp, null, new HashMap<>());

            assertFalse(ok);
            org.mockito.Mockito.verify(resp).setStatusCode(HttpStatus.UNAUTHORIZED);
            assertNull(svc.appSessionForUser("u-denied"),
                    "denied principal MUST NOT result in an AppSession registration");
        });
    }

    @Test
    void multiTenantBearerWithBlankSymbolFailsClosed() {
        // Selection requires non-blank symbol+expiry. With IB_SYMBOL unset (blank) we expect 401, not a
        // half-attached socket. This guards against silent misconfiguration in GATEWAY_AUTH mode.
        withSymbolAndExpiry("", "", () -> {
            VerifiedPrincipal principal =
                    new VerifiedPrincipal("u-misconf", "m", Set.of("user"), "options-edge-web", null);
            WsTicketService svc =
                    multiTenantService("good", principal, q -> ApprovalAuthority.ApprovalDecision.approved(0L));
            WsJwtHandshakeInterceptor interceptor =
                    new WsJwtHandshakeInterceptor(new GatewaySettings(), failingDecoder(), svc);

            HttpHeaders headers = new HttpHeaders();
            headers.add("Sec-WebSocket-Protocol", "oc.bearer, good");
            ServerHttpResponse resp = mock(ServerHttpResponse.class);

            boolean ok = interceptor.beforeHandshake(requestWith(headers), resp, null, new HashMap<>());

            assertFalse(ok);
            org.mockito.Mockito.verify(resp).setStatusCode(HttpStatus.UNAUTHORIZED);
        });
    }

    @Test
    void multiTenantBearerWithUnparseableSourceFailsClosed() {
        // Codex finding M-1: an unparseable APP_MARKET_DATA_SOURCE must fail closed, not silently default
        // an approved user to DATABENTO and bind a Selection the operator did not configure.
        // Codex round-2 L-1 caveat: GatewaySettings.value() prefers env vars over sys props, so this test
        // can only meaningfully exercise the sys-prop override when the env var is NOT set. We assume that
        // and skip with a clear note when CI leaks APP_MARKET_DATA_SOURCE into the test JVM.
        org.junit.jupiter.api.Assumptions.assumeTrue(System.getenv("APP_MARKET_DATA_SOURCE") == null,
                "APP_MARKET_DATA_SOURCE env-var is set; cannot exercise sys-prop fail-closed in this JVM");
        String prevSrc = System.getProperty("APP_MARKET_DATA_SOURCE");
        System.setProperty("APP_MARKET_DATA_SOURCE", "GARBAGE-NOT-A-SOURCE");
        try {
            withSymbolAndExpiry("SPX", "20260612", () -> {
                VerifiedPrincipal principal =
                        new VerifiedPrincipal("u-bad-src", "b", Set.of("user"), "options-edge-web", null);
                WsTicketService svc = multiTenantService("good", principal,
                        q -> ApprovalAuthority.ApprovalDecision.approved(0L));
                WsJwtHandshakeInterceptor interceptor =
                        new WsJwtHandshakeInterceptor(new GatewaySettings(), failingDecoder(), svc);

                HttpHeaders headers = new HttpHeaders();
                headers.add("Sec-WebSocket-Protocol", "oc.bearer, good");
                ServerHttpResponse resp = mock(ServerHttpResponse.class);

                boolean ok = interceptor.beforeHandshake(requestWith(headers), resp, null, new HashMap<>());

                assertFalse(ok);
                org.mockito.Mockito.verify(resp).setStatusCode(HttpStatus.UNAUTHORIZED);
                assertNull(svc.appSessionForUser("u-bad-src"),
                        "unparseable source MUST NOT silently route to DATABENTO; no AppSession leaked");
            });
        } finally {
            if (prevSrc == null) {
                System.clearProperty("APP_MARKET_DATA_SOURCE");
            } else {
                System.setProperty("APP_MARKET_DATA_SOURCE", prevSrc);
            }
        }
    }

    @Test
    void multiTenantBearerWithSharedSourceFailsClosed() {
        // Codex round-2 L-2: SHARED is parseable but is NEVER a primary {@link MarketDataSource}. A
        // misconfigured APP_MARKET_DATA_SOURCE=SHARED previously surfaced as an UNCAUGHT NotEntitledException
        // (HTTP-500-class), not a controlled 401. With the round-2 fix, defaultSelection() rejects SHARED
        // up front AND we catch NotEntitledException defensively. Both routes must yield 401, never crash.
        org.junit.jupiter.api.Assumptions.assumeTrue(System.getenv("APP_MARKET_DATA_SOURCE") == null,
                "APP_MARKET_DATA_SOURCE env-var is set; cannot exercise sys-prop fail-closed in this JVM");
        String prevSrc = System.getProperty("APP_MARKET_DATA_SOURCE");
        System.setProperty("APP_MARKET_DATA_SOURCE", "SHARED");
        try {
            withSymbolAndExpiry("SPX", "20260612", () -> {
                VerifiedPrincipal principal =
                        new VerifiedPrincipal("u-shared", "s", Set.of("user"), "options-edge-web", null);
                WsTicketService svc = multiTenantService("good", principal,
                        q -> ApprovalAuthority.ApprovalDecision.approved(0L));
                WsJwtHandshakeInterceptor interceptor =
                        new WsJwtHandshakeInterceptor(new GatewaySettings(), failingDecoder(), svc);

                HttpHeaders headers = new HttpHeaders();
                headers.add("Sec-WebSocket-Protocol", "oc.bearer, good");
                ServerHttpResponse resp = mock(ServerHttpResponse.class);

                boolean ok = interceptor.beforeHandshake(requestWith(headers), resp, null, new HashMap<>());

                assertFalse(ok);
                org.mockito.Mockito.verify(resp).setStatusCode(HttpStatus.UNAUTHORIZED);
                assertNull(svc.appSessionForUser("u-shared"),
                        "SHARED source MUST NOT register an AppSession; fail closed at defaultSelection");
            });
        } finally {
            if (prevSrc == null) {
                System.clearProperty("APP_MARKET_DATA_SOURCE");
            } else {
                System.setProperty("APP_MARKET_DATA_SOURCE", prevSrc);
            }
        }
    }

    @Test
    void subProtocolIsAdvertisedByHandler() {
        FeedWebSocketHandler handler = new FeedWebSocketHandler(mock(FeedGatewayService.class),
                mock(org.springframework.beans.factory.ObjectProvider.class));
        assertEquals(List.of("oc.bearer"), handler.getSubProtocols());
    }
}

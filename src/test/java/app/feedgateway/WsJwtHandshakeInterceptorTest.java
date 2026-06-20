package app.feedgateway;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
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

    @Test
    void subProtocolIsAdvertisedByHandler() {
        FeedWebSocketHandler handler = new FeedWebSocketHandler(mock(FeedGatewayService.class));
        assertEquals(List.of("oc.bearer"), handler.getSubProtocols());
    }
}

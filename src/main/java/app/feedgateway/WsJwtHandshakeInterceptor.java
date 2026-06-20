package app.feedgateway;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Authenticates the WebSocket handshake with a Keycloak JWT. The browser {@code WebSocket(url, protocols)}
 * API can set ONLY the {@code Sec-WebSocket-Protocol} header (no Authorization header; bearer-in-query is
 * prohibited), so the client offers {@code ["oc.bearer", <accessToken>]}; this interceptor extracts and
 * validates the token (signature + iss + aud + exp) before the socket is established. A missing/invalid
 * token is rejected with 401. The handler echoes the {@code oc.bearer} subprotocol (see FeedWebSocketHandler).
 */
@Component
public class WsJwtHandshakeInterceptor implements HandshakeInterceptor {

    static final String BEARER_SUBPROTOCOL = "oc.bearer";
    /** Session attribute holding the token's expiry (epoch ms), so the reaper can close it when it lapses. */
    static final String AUTH_EXPIRES_AT_ATTR = "authExpiresAtEpochMs";
    private static final String SEC_WEBSOCKET_PROTOCOL = "Sec-WebSocket-Protocol";

    private final GatewaySettings settings;
    private volatile JwtDecoder decoder; // lazily built from settings; injectable for tests

    public WsJwtHandshakeInterceptor(GatewaySettings settings) {
        this.settings = settings;
    }

    WsJwtHandshakeInterceptor(GatewaySettings settings, JwtDecoder decoder) {
        this.settings = settings;
        this.decoder = decoder;
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        if (!settings.wsAuthEnabled()) {
            return true; // local dev: open
        }
        String token = extractToken(request.getHeaders());
        if (token == null) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
        try {
            Jwt jwt = decoder().decode(token);
            attributes.put("sub", jwt.getSubject());
            if (jwt.getExpiresAt() != null) {
                attributes.put(AUTH_EXPIRES_AT_ATTR, jwt.getExpiresAt().toEpochMilli());
            }
            return true;
        } catch (JwtException e) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                              WebSocketHandler wsHandler, Exception exception) {
        // no-op
    }

    /** Token from the offered subprotocols {@code ["oc.bearer", "<token>"]} — the value after the marker. */
    static String extractToken(HttpHeaders headers) {
        List<String> offered = new ArrayList<>();
        for (String header : headers.getOrEmpty(SEC_WEBSOCKET_PROTOCOL)) {
            for (String part : header.split(",")) {
                String trimmed = part.trim();
                if (!trimmed.isEmpty()) {
                    offered.add(trimmed);
                }
            }
        }
        int marker = offered.indexOf(BEARER_SUBPROTOCOL);
        if (marker >= 0 && marker + 1 < offered.size()) {
            return offered.get(marker + 1);
        }
        return null;
    }

    private JwtDecoder decoder() {
        JwtDecoder current = decoder;
        if (current == null) {
            synchronized (this) {
                current = decoder;
                if (current == null) {
                    current = buildDecoder();
                    decoder = current;
                }
            }
        }
        return current;
    }

    private JwtDecoder buildDecoder() {
        String issuer = settings.wsAuthIssuer();
        if (issuer == null || issuer.isBlank()) {
            throw new IllegalStateException("WS_AUTH_ISSUER_URI is required when WS_AUTH_ENABLED=true");
        }
        NimbusJwtDecoder nimbus = NimbusJwtDecoder.withIssuerLocation(issuer).build();
        OAuth2TokenValidator<Jwt> withIssuer = JwtValidators.createDefaultWithIssuer(issuer);
        OAuth2TokenValidator<Jwt> audience = new AudienceValidator(settings.wsAuthAudience());
        nimbus.setJwtValidator(new DelegatingOAuth2TokenValidator<>(withIssuer, audience));
        return nimbus;
    }
}

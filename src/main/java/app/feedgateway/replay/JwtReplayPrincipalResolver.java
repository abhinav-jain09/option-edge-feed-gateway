package app.feedgateway.replay;

import app.feedgateway.GatewaySettings;
import java.util.Optional;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

/**
 * {@link ReplayPrincipalResolver} that verifies the bearer JWT against the gateway's configured issuer
 * (the same trust used by the WS handshake) and returns its {@code sub}. Fails closed: a missing,
 * malformed, or invalid/expired token yields {@link Optional#empty()}.
 */
public final class JwtReplayPrincipalResolver implements ReplayPrincipalResolver {

    private static final String BEARER = "Bearer ";

    private final GatewaySettings settings;
    private volatile JwtDecoder decoder; // lazily built; overridable for tests

    public JwtReplayPrincipalResolver(GatewaySettings settings) {
        this.settings = settings;
    }

    JwtReplayPrincipalResolver(GatewaySettings settings, JwtDecoder decoder) {
        this.settings = settings;
        this.decoder = decoder;
    }

    @Override
    public Optional<String> principal(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith(BEARER)) {
            return Optional.empty();
        }
        String token = authorizationHeader.substring(BEARER.length()).trim();
        if (token.isEmpty()) {
            return Optional.empty();
        }
        try {
            Jwt jwt = decoder().decode(token);
            String sub = jwt.getSubject();
            return (sub == null || sub.isBlank()) ? Optional.empty() : Optional.of(sub);
        } catch (JwtException | IllegalStateException e) {
            return Optional.empty(); // invalid / expired / untrusted → fail closed
        }
    }

    private JwtDecoder decoder() {
        JwtDecoder d = decoder;
        if (d == null) {
            synchronized (this) {
                if (decoder == null) {
                    String issuer = settings.wsAuthIssuer();
                    if (issuer == null || issuer.isBlank()) {
                        throw new IllegalStateException("replay control requires WS_AUTH_ISSUER_URI to be set");
                    }
                    decoder = NimbusJwtDecoder.withIssuerLocation(issuer).build();
                }
                d = decoder;
            }
        }
        return d;
    }
}

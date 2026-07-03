package app.feedgateway.liquidityhistory;

import app.feedgateway.GatewaySettings;
import app.feedgateway.WsJwtHandshakeInterceptor;
import app.feedgateway.mtsession.NotApprovedException;
import app.feedgateway.mtsession.NotEntitledException;
import app.feedgateway.mtsession.auth.JwtVerificationException;
import app.feedgateway.mtsession.auth.VerifiedPrincipal;
import app.feedgateway.mtsession.gateway.WsTicketService;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

/**
 * Bearer authentication for {@code /api/liquidity-history}, reusing the SAME token validation as
 * the WS handshake per mode (spec §3 "AUTH: via the SAME TokenVerifier as the WS"):
 *
 * <ul>
 *   <li>Multi-tenant ({@code GATEWAY_AUTH_ENABLED=true}, {@link WsTicketService} bean present):
 *       {@link WsTicketService#verify} then the shared {@link WsTicketService#ensureAppSession}
 *       helper with the same default {@link WsJwtHandshakeInterceptor#defaultSelection} — so the
 *       REST path enforces IDENTICAL approval/entitlement semantics to the {@code oc.bearer}
 *       handshake. Invalid/expired token → 401; valid-but-unapproved/unentitled → 403.</li>
 *   <li>Legacy ({@code WS_AUTH_ENABLED=true}, no ticket service): the same
 *       {@code WS_AUTH_ISSUER_URI}/{@code WS_AUTH_AUDIENCE} NimbusJwtDecoder the WS handshake
 *       builds. The WS handshake enforces NO entitlement beyond token validity in this mode, so —
 *       per the spec's entitlement-parity rule — REST enforces none either: 401 or OK, never 403.</li>
 *   <li>Both off (local dev): the endpoint is OPEN; logged loudly at construction so an operator
 *       can never mistake it for a secured deployment.</li>
 * </ul>
 */
public final class LiquidityHistoryAuth {

    /** {@code status} 200 = authenticated; else the HTTP status to return (401/403). */
    public record Result(int status, String principal) {
        static Result ok(String principal) {
            return new Result(200, principal == null || principal.isBlank() ? "anonymous" : principal);
        }
    }

    private final GatewaySettings settings;
    private final WsTicketService ticketService; // null outside multi-tenant mode
    private volatile JwtDecoder decoder;         // lazily built (legacy mode); injectable for tests

    public LiquidityHistoryAuth(GatewaySettings settings, WsTicketService ticketService) {
        this.settings = settings;
        this.ticketService = ticketService;
        if (ticketService == null && !settings.wsAuthEnabled()) {
            System.out.println("WARN /api/liquidity-history is OPEN (GATEWAY_AUTH_ENABLED and "
                    + "WS_AUTH_ENABLED are both off) — local-dev mode only");
        }
    }

    /** Test seam: legacy mode with an injected decoder (mirrors WsJwtHandshakeInterceptor's test ctor). */
    LiquidityHistoryAuth(GatewaySettings settings, WsTicketService ticketService, JwtDecoder decoder) {
        this.settings = settings;
        this.ticketService = ticketService;
        this.decoder = decoder;
    }

    public Result authenticate(String authorizationHeader) {
        boolean multiTenant = ticketService != null;
        if (!multiTenant && !settings.wsAuthEnabled()) {
            return Result.ok("anonymous"); // WS auth disabled entirely → endpoint open (logged at boot)
        }
        String token = bearerToken(authorizationHeader);
        if (token == null) {
            return new Result(401, null);
        }
        if (multiTenant) {
            try {
                VerifiedPrincipal principal = ticketService.verify(token);
                // Same approval/entitlement enforcement as the WS bearer handshake — deliberately the
                // SHARED helper, not a re-implementation, so the two paths can never diverge.
                ticketService.ensureAppSession(principal,
                        WsJwtHandshakeInterceptor.defaultSelection(settings));
                return Result.ok(principal.userId());
            } catch (JwtVerificationException invalid) {
                return new Result(401, null);
            } catch (NotApprovedException | NotEntitledException unentitled) {
                return new Result(403, null); // valid token, no entitlement (spec §3)
            } catch (IllegalArgumentException misconfigured) {
                // Blank symbol/expiry or unparseable source: fail closed with 401, exactly like the
                // WS handshake does for the same misconfiguration.
                return new Result(401, null);
            }
        }
        try {
            Jwt jwt = decoder().decode(token);
            return Result.ok(jwt.getSubject());
        } catch (JwtException invalid) {
            return new Result(401, null);
        }
    }

    private static String bearerToken(String header) {
        if (header == null) {
            return null;
        }
        String trimmed = header.trim();
        if (trimmed.length() <= 7 || !trimmed.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return null;
        }
        String token = trimmed.substring(7).trim();
        return token.isEmpty() ? null : token;
    }

    private JwtDecoder decoder() {
        JwtDecoder current = decoder;
        if (current == null) {
            synchronized (this) {
                current = decoder;
                if (current == null) {
                    current = WsJwtHandshakeInterceptor.buildWsDecoder(settings);
                    decoder = current;
                }
            }
        }
        return current;
    }
}

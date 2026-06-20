package app.feedgateway.mtsession.gateway;

import app.feedgateway.mtsession.AppSession;
import app.feedgateway.mtsession.Selection;
import app.feedgateway.mtsession.SessionRoutingEngine;
import app.feedgateway.mtsession.TicketStore;
import app.feedgateway.mtsession.WsTicket;
import app.feedgateway.mtsession.auth.JwtVerificationException;
import app.feedgateway.mtsession.auth.TokenVerifier;
import app.feedgateway.mtsession.auth.VerifiedPrincipal;
import java.time.Duration;
import java.util.Objects;

/**
 * Mints WebSocket tickets after verifying a bearer JWT (OE-DDD-001 §5.3 / §11.1 step 1). Pure logic
 * (no Spring): verify token → ensure the user's AppSession exists with the requested selection →
 * mint a single-use ticket bound to that AppSession.
 *
 * <p>One AppSession per user for now ({@code MAX_APPSESSIONS_PER_USER}=1; multi-tab shares it,
 * OQ-2). Entitlements come from the verified realm roles. Approval defaults to APPROVED here; the
 * authoritative approval gate lives in the Config-Control platform (§5.4) and is layered in later.
 */
public final class WsTicketService {

    /** Result of a successful mint. */
    public record TicketIssued(String ticketId, String appSessionId, long expiresInMs) {
    }

    private final TokenVerifier verifier;
    private final TicketStore ticketStore;
    private final SessionRoutingEngine engine;
    private final Duration ticketTtl;

    public WsTicketService(TokenVerifier verifier, TicketStore ticketStore,
                           SessionRoutingEngine engine, Duration ticketTtl) {
        this.verifier = Objects.requireNonNull(verifier);
        this.ticketStore = Objects.requireNonNull(ticketStore);
        this.engine = Objects.requireNonNull(engine);
        this.ticketTtl = Objects.requireNonNull(ticketTtl);
    }

    /**
     * Verify {@code bearerToken}, register/refresh the caller's AppSession with {@code selection},
     * and mint a single-use WS ticket.
     *
     * @throws JwtVerificationException if the token is invalid (caller maps to 401)
     * @throws app.feedgateway.mtsession.NotEntitledException if not entitled to the source (→ 403)
     */
    public TicketIssued issue(String bearerToken, Selection selection) throws JwtVerificationException {
        VerifiedPrincipal principal = verifier.verify(bearerToken);
        String userId = principal.userId();
        if (userId == null || userId.isBlank()) {
            throw new JwtVerificationException("token has no subject");
        }
        String appSessionId = "app:" + userId;

        if (engine.appSession(appSessionId).isPresent()) {
            engine.changeSelection(appSessionId, selection); // also re-validates entitlement
        } else {
            engine.registerAppSession(appSessionId, userId, selection, principal.roles());
        }
        WsTicket ticket = ticketStore.mint(userId, appSessionId, ticketTtl, principal.expiresAt());
        return new TicketIssued(ticket.ticketId(), appSessionId, ticketTtl.toMillis());
    }

    /** Expose the (possibly newly created) AppSession for callers/tests. */
    public AppSession appSessionForUser(String userId) {
        return engine.appSession("app:" + userId).orElse(null);
    }
}

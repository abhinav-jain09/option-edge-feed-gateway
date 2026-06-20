package app.feedgateway.mtsession.gateway;

import app.feedgateway.mtsession.TicketStore;
import app.feedgateway.mtsession.WsTicket;
import java.util.Objects;
import java.util.Optional;

/**
 * Pure decision logic for the WebSocket handshake (OE-DDD-001 §5.3 / §11.1 step 3): redeem the
 * single-use ticket and resolve the AppSession binding. The Spring {@code HandshakeInterceptor} is
 * a thin adapter over this so the policy is unit-testable.
 *
 * <p>When auth is disabled the handshake is a pass-through (preserves legacy behaviour during the
 * flag-gated migration, DDD §12).
 */
public final class HandshakeTicketAuthenticator {

    /** Outcome of a handshake authentication attempt. */
    public record Decision(boolean accept, boolean passthrough, String appSessionId, String userId,
                           java.time.Instant tokenExpiresAt, String reason) {

        static Decision disabled() {
            return new Decision(true, true, null, null, null, "auth-disabled");
        }

        static Decision accepted(String appSessionId, String userId, java.time.Instant tokenExpiresAt) {
            return new Decision(true, false, appSessionId, userId, tokenExpiresAt, "ok");
        }

        static Decision rejected(String reason) {
            return new Decision(false, false, null, null, null, reason);
        }
    }

    private final TicketStore ticketStore;

    public HandshakeTicketAuthenticator(TicketStore ticketStore) {
        this.ticketStore = Objects.requireNonNull(ticketStore);
    }

    /**
     * Decide whether to accept a handshake.
     *
     * @param authEnabled whether ticket auth is enforced
     * @param ticketId    the opaque ticket presented (from subprotocol or query)
     */
    public Decision authenticate(boolean authEnabled, String ticketId) {
        if (!authEnabled) {
            return Decision.disabled();
        }
        if (ticketId == null || ticketId.isBlank()) {
            return Decision.rejected("missing ticket");
        }
        Optional<WsTicket> redeemed = ticketStore.redeem(ticketId);
        if (redeemed.isEmpty()) {
            return Decision.rejected("invalid or already-used ticket");
        }
        WsTicket ticket = redeemed.get();
        return Decision.accepted(ticket.appSessionId(), ticket.userId(), ticket.tokenExpiresAt());
    }
}

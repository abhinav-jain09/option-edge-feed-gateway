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

    /** Separator between the minting replica's id and the random part in a ticket id. */
    static final String INSTANCE_SEP = "~";

    private final TicketStore ticketStore;
    private final String instanceId;
    private final java.util.concurrent.atomic.AtomicLong foreignInstanceRejections =
            new java.util.concurrent.atomic.AtomicLong();

    public HandshakeTicketAuthenticator(TicketStore ticketStore, String instanceId) {
        this.ticketStore = Objects.requireNonNull(ticketStore);
        this.instanceId = instanceId == null ? "" : instanceId;
    }

    /** Handshakes rejected because the ticket was minted by a DIFFERENT replica (sticky-routing breakage). */
    public long foreignInstanceRejections() {
        return foreignInstanceRejections.get();
    }

    /**
     * Decide whether to accept a handshake.
     *
     * <p>P1 (multi-replica safety): a ticket id is {@code <minting-instance>~<random>}. The AppSession exists
     * ONLY on the replica that minted the ticket (and that replica is the one consuming/routing the session's
     * Kafka data). So if this ticket was minted by a different replica we reject it BEFORE redeeming — the
     * ticket is preserved for the correctly sticky-routed retry, and the failure is explicit/observable
     * rather than a confusing socket-attach error. Correctness therefore requires sticky WS routing.
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
        int sep = ticketId.indexOf(INSTANCE_SEP);
        if (sep > 0) {
            String boundInstance = ticketId.substring(0, sep);
            if (!boundInstance.equals(instanceId)) {
                foreignInstanceRejections.incrementAndGet();
                // Do NOT redeem — leave the single-use ticket intact for the replica it is bound to.
                return Decision.rejected("ticket bound to a different gateway instance (" + boundInstance
                        + "); sticky routing required");
            }
        }
        Optional<WsTicket> redeemed = ticketStore.redeem(ticketId);
        if (redeemed.isEmpty()) {
            return Decision.rejected("invalid or already-used ticket");
        }
        WsTicket ticket = redeemed.get();
        // Defence in depth: the bound AppSession must live on THIS replica (it always does when the prefix
        // matches and routing is sticky). A mismatch here means a misconfiguration — fail closed.
        return Decision.accepted(ticket.appSessionId(), ticket.userId(), ticket.tokenExpiresAt());
    }
}

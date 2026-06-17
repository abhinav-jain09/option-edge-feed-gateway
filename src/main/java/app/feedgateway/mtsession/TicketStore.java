package app.feedgateway.mtsession;

import java.time.Duration;
import java.util.Optional;

/**
 * Mint/redeem store for {@link WsTicket}s (OE-DDD-001 §5.3). Production backs this with Redis
 * (atomic {@code GETDEL}); {@link InMemoryTicketStore} provides the reference semantics.
 */
public interface TicketStore {

    /** Mint a single-use ticket valid for {@code ttl}. */
    WsTicket mint(String userId, String appSessionId, Duration ttl);

    /**
     * Atomically redeem a ticket: returns it exactly once if present and unexpired, removing it so
     * it can never be reused. Returns empty for unknown, already-redeemed, or expired tickets.
     */
    Optional<WsTicket> redeem(String ticketId);
}

package app.feedgateway.mtsession;

import java.time.Instant;
import java.util.Objects;

/**
 * A short-lived, single-use WebSocket handshake ticket (OE-DDD-001 §5.3, DR-7). The opaque id is
 * exchanged for a WS upgrade and bound to the issuing user + AppSession.
 */
public record WsTicket(String ticketId, String userId, String appSessionId, Instant expiresAt) {

    public WsTicket {
        Objects.requireNonNull(ticketId, "ticketId");
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(appSessionId, "appSessionId");
        Objects.requireNonNull(expiresAt, "expiresAt");
    }

    public boolean isExpired(Instant now) {
        return !now.isBefore(expiresAt);
    }
}

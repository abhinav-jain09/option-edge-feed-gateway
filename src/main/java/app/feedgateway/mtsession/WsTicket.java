package app.feedgateway.mtsession;

import java.time.Instant;
import java.util.Objects;

/**
 * A short-lived, single-use WebSocket handshake ticket (OE-DDD-001 §5.3, DR-7). The opaque id is
 * exchanged for a WS upgrade and bound to the issuing user + AppSession.
 */
public record WsTicket(String ticketId, String userId, String appSessionId, Instant expiresAt,
                       Instant tokenExpiresAt) {

    public WsTicket {
        Objects.requireNonNull(ticketId, "ticketId");
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(appSessionId, "appSessionId");
        Objects.requireNonNull(expiresAt, "expiresAt");
        // tokenExpiresAt may be null (legacy/tests); when present, the socket reaper closes the bound
        // socket once the backing access token expires (review finding #11).
    }

    public WsTicket(String ticketId, String userId, String appSessionId, Instant expiresAt) {
        this(ticketId, userId, appSessionId, expiresAt, null);
    }

    public boolean isExpired(Instant now) {
        return !now.isBefore(expiresAt);
    }
}

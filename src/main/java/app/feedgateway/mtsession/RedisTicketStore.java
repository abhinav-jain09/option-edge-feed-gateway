package app.feedgateway.mtsession;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.lettuce.core.SetArgs;
import io.lettuce.core.api.sync.RedisCommands;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Redis-backed {@link TicketStore} (OE-DDD-001 §5.3/§6.5, DR-7). Mint writes the ticket with a
 * native Redis TTL; redeem uses {@code GETDEL} so a ticket is delivered to at most one caller and
 * can never be replayed (FM-12) — even across multiple gateway instances sharing the same Redis.
 *
 * <p>Takes a {@link RedisCommands} so it is connection-agnostic and integration-testable; the
 * gateway supplies a pooled/clustered client.
 */
public final class RedisTicketStore implements TicketStore {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final RedisCommands<String, String> redis;
    private final Clock clock;
    private final Supplier<String> idGenerator;
    private final String keyPrefix;

    public RedisTicketStore(RedisCommands<String, String> redis, Clock clock,
                            Supplier<String> idGenerator, String keyPrefix) {
        this.redis = redis;
        this.clock = clock;
        this.idGenerator = idGenerator;
        this.keyPrefix = keyPrefix;
    }

    @Override
    public WsTicket mint(String userId, String appSessionId, Duration ttl, Instant tokenExpiresAt) {
        if (ttl.isNegative() || ttl.isZero()) {
            throw new IllegalArgumentException("ticket ttl must be positive");
        }
        Instant expiresAt = clock.instant().plus(ttl);
        WsTicket ticket = new WsTicket(idGenerator.get(), userId, appSessionId, expiresAt, tokenExpiresAt);
        redis.set(key(ticket.ticketId()), serialize(ticket),
                SetArgs.Builder.px(ttl.toMillis()));
        return ticket;
    }

    @Override
    public Optional<WsTicket> redeem(String ticketId) {
        if (ticketId == null) {
            return Optional.empty();
        }
        String json = redis.getdel(key(ticketId)); // atomic single-use
        if (json == null) {
            return Optional.empty();
        }
        WsTicket ticket = deserialize(json);
        if (ticket == null || ticket.isExpired(clock.instant())) {
            return Optional.empty();
        }
        return Optional.of(ticket);
    }

    private String key(String ticketId) {
        return keyPrefix + ticketId;
    }

    private static String serialize(WsTicket t) {
        try {
            return MAPPER.writeValueAsString(
                    new Dto(t.ticketId(), t.userId(), t.appSessionId(), t.expiresAt().toEpochMilli(),
                            t.tokenExpiresAt() == null ? -1L : t.tokenExpiresAt().toEpochMilli()));
        } catch (Exception e) {
            throw new IllegalStateException("failed to serialize ticket", e);
        }
    }

    private static WsTicket deserialize(String json) {
        try {
            Dto d = MAPPER.readValue(json, Dto.class);
            return new WsTicket(d.ticketId(), d.userId(), d.appSessionId(), Instant.ofEpochMilli(d.expiresAtMs()),
                    d.tokenExpiresAtMs() < 0 ? null : Instant.ofEpochMilli(d.tokenExpiresAtMs()));
        } catch (Exception e) {
            return null;
        }
    }

    private record Dto(
            @JsonProperty("t") String ticketId,
            @JsonProperty("u") String userId,
            @JsonProperty("a") String appSessionId,
            @JsonProperty("e") long expiresAtMs,
            @JsonProperty("te") long tokenExpiresAtMs) {
    }
}

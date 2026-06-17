package app.feedgateway.mtsession;

import java.time.Clock;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Reference {@link TicketStore} with the exact single-use + TTL semantics required of the Redis
 * implementation (OE-DDD-001 §5.3, FM-12). Time is injected via {@link Clock} and id generation via
 * a {@link Supplier} so behaviour is fully deterministic under test.
 *
 * <p>Thread-safe: {@link #redeem} uses an atomic remove so concurrent redemptions of the same
 * ticket yield it to at most one caller (modelling Redis {@code GETDEL}).
 */
public final class InMemoryTicketStore implements TicketStore {

    private final ConcurrentHashMap<String, WsTicket> tickets = new ConcurrentHashMap<>();
    private final Clock clock;
    private final Supplier<String> idGenerator;

    public InMemoryTicketStore(Clock clock, Supplier<String> idGenerator) {
        this.clock = clock;
        this.idGenerator = idGenerator;
    }

    @Override
    public WsTicket mint(String userId, String appSessionId, Duration ttl) {
        if (ttl.isNegative() || ttl.isZero()) {
            throw new IllegalArgumentException("ticket ttl must be positive");
        }
        WsTicket ticket = new WsTicket(idGenerator.get(), userId, appSessionId, clock.instant().plus(ttl));
        tickets.put(ticket.ticketId(), ticket);
        return ticket;
    }

    @Override
    public Optional<WsTicket> redeem(String ticketId) {
        if (ticketId == null) {
            return Optional.empty();
        }
        WsTicket ticket = tickets.remove(ticketId); // atomic single-use (GETDEL)
        if (ticket == null) {
            return Optional.empty();
        }
        if (ticket.isExpired(clock.instant())) {
            return Optional.empty(); // already removed above; expired tickets are not honoured
        }
        return Optional.of(ticket);
    }

    /** Drop expired tickets (housekeeping; Redis does this via TTL). */
    public int purgeExpired() {
        var now = clock.instant();
        int[] removed = {0};
        tickets.values().removeIf(t -> {
            boolean exp = t.isExpired(now);
            if (exp) {
                removed[0]++;
            }
            return exp;
        });
        return removed[0];
    }

    public int size() {
        return tickets.size();
    }
}

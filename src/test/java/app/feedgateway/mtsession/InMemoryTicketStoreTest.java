package app.feedgateway.mtsession;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class InMemoryTicketStoreTest {

    private final Instant t0 = Instant.parse("2026-06-17T12:00:00Z");

    private static final class MutableClock extends Clock {
        private final AtomicReference<Instant> now;
        MutableClock(Instant start) { this.now = new AtomicReference<>(start); }
        void advance(Duration d) { now.updateAndGet(i -> i.plus(d)); }
        @Override public ZoneOffset getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId z) { return this; }
        @Override public Instant instant() { return now.get(); }
    }

    private InMemoryTicketStore store(MutableClock clock) {
        AtomicInteger seq = new AtomicInteger();
        return new InMemoryTicketStore(clock, () -> "ticket-" + seq.incrementAndGet());
    }

    @Test
    void mintThenRedeemOnce() {
        MutableClock clock = new MutableClock(t0);
        InMemoryTicketStore s = store(clock);
        WsTicket t = s.mint("user-1", "app-1", Duration.ofSeconds(10));
        WsTicket redeemed = s.redeem(t.ticketId()).orElseThrow();
        assertEquals("user-1", redeemed.userId());
        assertEquals("app-1", redeemed.appSessionId());
        // single use: second redeem fails
        assertTrue(s.redeem(t.ticketId()).isEmpty());
    }

    @Test
    void expiredTicketNotHonoured() {
        MutableClock clock = new MutableClock(t0);
        InMemoryTicketStore s = store(clock);
        WsTicket t = s.mint("user-1", "app-1", Duration.ofSeconds(10));
        clock.advance(Duration.ofSeconds(11));
        assertTrue(s.redeem(t.ticketId()).isEmpty());
    }

    @Test
    void redeemUnknownReturnsEmpty() {
        InMemoryTicketStore s = store(new MutableClock(t0));
        assertTrue(s.redeem("nope").isEmpty());
        assertTrue(s.redeem(null).isEmpty());
    }

    @Test
    void purgeExpiredRemovesOnlyExpired() {
        MutableClock clock = new MutableClock(t0);
        InMemoryTicketStore s = store(clock);
        s.mint("u", "a", Duration.ofSeconds(5));
        s.mint("u", "a", Duration.ofSeconds(30));
        clock.advance(Duration.ofSeconds(10));
        assertEquals(1, s.purgeExpired());
        assertEquals(1, s.size());
    }

    @Test
    void nonPositiveTtlRejected() {
        InMemoryTicketStore s = store(new MutableClock(t0));
        assertThrows(IllegalArgumentException.class, () -> s.mint("u", "a", Duration.ZERO));
        assertThrows(IllegalArgumentException.class, () -> s.mint("u", "a", Duration.ofSeconds(-1)));
    }
}

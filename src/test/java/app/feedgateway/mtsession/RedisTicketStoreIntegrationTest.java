package app.feedgateway.mtsession;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Integration test against a live local Redis (see {@code dev/redis/run-redis.sh}). Proves the
 * single-use {@code GETDEL} ticket semantics on real Redis (OE-DDD-001 §5.3, FM-12). Skips if Redis
 * is not reachable so the normal build stays green.
 */
class RedisTicketStoreIntegrationTest {

    private static final String URI = "redis://localhost:6380";
    private static RedisClient client;
    private static StatefulRedisConnection<String, String> conn;
    private static RedisCommands<String, String> commands;

    private static final class MutableClock extends Clock {
        private final AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-06-17T12:00:00Z"));
        void advance(Duration d) { now.updateAndGet(i -> i.plus(d)); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId z) { return this; }
        @Override public Instant instant() { return now.get(); }
    }

    @BeforeAll
    static void connect() {
        boolean ok = false;
        try {
            client = RedisClient.create(URI);
            client.setDefaultTimeout(Duration.ofSeconds(2));
            conn = client.connect();
            ok = "PONG".equalsIgnoreCase(conn.sync().ping());
            commands = conn.sync();
        } catch (Exception e) {
            ok = false;
        }
        assumeTrue(ok, "Redis not reachable at " + URI + " — skipping integration test");
    }

    @AfterAll
    static void close() {
        if (conn != null) {
            conn.close();
        }
        if (client != null) {
            client.shutdown();
        }
    }

    private RedisTicketStore store(Clock clock) {
        AtomicInteger seq = new AtomicInteger();
        return new RedisTicketStore(commands, clock, () -> "it-" + System.nanoTime() + "-" + seq.incrementAndGet(),
                "oe:ticket:test:");
    }

    @Test
    @DisplayName("Mint then redeem once; replay fails (GETDEL)")
    void mintRedeemOnce() {
        RedisTicketStore s = store(new MutableClock());
        WsTicket t = s.mint("user-1", "app-1", Duration.ofSeconds(30));
        WsTicket got = s.redeem(t.ticketId()).orElseThrow();
        assertEquals("user-1", got.userId());
        assertEquals("app-1", got.appSessionId());
        assertTrue(s.redeem(t.ticketId()).isEmpty(), "second redeem must fail (single-use)");
    }

    @Test
    @DisplayName("Expired ticket (clock past expiry) is not honoured")
    void expiredNotHonoured() {
        MutableClock clock = new MutableClock();
        RedisTicketStore s = store(clock);
        WsTicket t = s.mint("user-1", "app-1", Duration.ofSeconds(30));
        clock.advance(Duration.ofSeconds(31));
        assertTrue(s.redeem(t.ticketId()).isEmpty());
    }

    @Test
    @DisplayName("Redeeming an unknown ticket returns empty")
    void unknownReturnsEmpty() {
        RedisTicketStore s = store(new MutableClock());
        assertTrue(s.redeem("does-not-exist").isEmpty());
        assertTrue(s.redeem(null).isEmpty());
    }

    @Test
    @DisplayName("Redis TTL eventually evicts the key")
    void redisTtlEvicts() throws Exception {
        RedisTicketStore s = store(Clock.systemUTC());
        WsTicket t = s.mint("user-1", "app-1", Duration.ofMillis(300));
        String key = "oe:ticket:test:" + t.ticketId();
        assertTrue(commands.exists(key) == 1L);
        Thread.sleep(600);
        assertEquals(0L, commands.exists(key), "key should be gone after TTL");
    }
}

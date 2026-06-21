package app.feedgateway.mtsession;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Proves the single-use ticket guarantee under a real thread RACE (review finding #4): when many threads
 * redeem the same ticket simultaneously, EXACTLY one wins. Hermetic — no infra, never skips.
 */
class InMemoryTicketStoreConcurrencyTest {

    @Test
    void concurrentRedeemOfSameTicketYieldsExactlyOneWinner() throws Exception {
        InMemoryTicketStore store = new InMemoryTicketStore(Clock.systemUTC(), () -> "tkt-" + UUID.randomUUID());
        int tickets = 400;
        int contenders = 8;
        ExecutorService pool = Executors.newFixedThreadPool(16);
        try {
            for (int i = 0; i < tickets; i++) {
                String id = store.mint("u", "app", Duration.ofSeconds(30)).ticketId();
                AtomicInteger winners = new AtomicInteger();
                CountDownLatch start = new CountDownLatch(1);
                CountDownLatch done = new CountDownLatch(contenders);
                for (int t = 0; t < contenders; t++) {
                    pool.submit(() -> {
                        try {
                            start.await();
                            if (store.redeem(id).isPresent()) {
                                winners.incrementAndGet();
                            }
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        } finally {
                            done.countDown();
                        }
                    });
                }
                start.countDown(); // release all contenders at once for maximum contention
                done.await(10, TimeUnit.SECONDS);
                assertEquals(1, winners.get(), "ticket " + id + " was redeemed by more than one caller");
            }
        } finally {
            pool.shutdownNow();
        }
    }
}

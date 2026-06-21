package app.feedgateway.mtsession;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

/**
 * SessionRoutingEngine must be internally thread-safe: concurrent register/change/attach/detach/
 * teardown/route/sweep must never corrupt its indexes. The only exceptions tolerated are domain
 * outcomes of legitimate races (operating on a session another thread just tore down / a full cap);
 * a structural-corruption exception (ConcurrentModificationException, NPE, …) fails the test.
 */
class SessionRoutingEngineConcurrencyTest {

    private static final int USERS = 24;
    private static final int OPS = 4000;

    private static Selection sel(int i) {
        return new Selection(MarketDataSource.DATABENTO, "SPX", "2026" + (1000 + (i % 50)), StrikeWindow.ALL);
    }

    private static RoutableRecord rec(int i) {
        Selection s = sel(i);
        return RoutableRecord.contract(MarketDataSource.DATABENTO, EventType.SNAPSHOT, s.symbol(), s.expiry(), 7500.0, 0L);
    }

    private static boolean isDomainRace(Throwable t) {
        return t instanceof IllegalStateException        // unknown / already-registered / already-attached
                || t instanceof CapacityException
                || t instanceof NotEntitledException
                || t instanceof NotApprovedException;
    }

    @Test
    void concurrentMutationAndRoutingStaysConsistent() throws Exception {
        SessionRoutingEngine engine =
                new SessionRoutingEngine(new ConcurrencyLimits(1000, 1000, 100_000), new SubscriptionManager());
        for (int i = 0; i < USERS; i++) {
            engine.registerAppSession("app:u" + i, "u" + i, sel(i), Set.of());
            engine.attachSocket("app:u" + i, "live-s" + i);
        }

        List<Throwable> corruption = new CopyOnWriteArrayList<>();
        AtomicBoolean go = new AtomicBoolean(false);
        ExecutorService pool = Executors.newFixedThreadPool(16);
        List<Runnable> workers = new ArrayList<>();

        // Routers: read the indexes while everyone else mutates them.
        for (int t = 0; t < 4; t++) {
            workers.add(() -> {
                for (int i = 0; i < OPS; i++) {
                    try {
                        engine.route(rec(i));
                        engine.shouldDeliverToSocket(rec(i), "live-s" + (i % USERS));
                    } catch (Throwable e) {
                        if (!isDomainRace(e)) corruption.add(e);
                    }
                }
            });
        }
        // Attach/detach churn on the stable sessions.
        for (int t = 0; t < 4; t++) {
            final int tid = t;
            workers.add(() -> {
                for (int i = 0; i < OPS; i++) {
                    String app = "app:u" + (i % USERS), sock = "s" + tid + "-" + i;
                    try {
                        engine.attachSocket(app, sock);
                        engine.detachSocket(sock);
                    } catch (Throwable e) {
                        if (!isDomainRace(e)) corruption.add(e);
                    }
                }
            });
        }
        // changeSelection churn (reindexes contract/underlying maps under route()).
        for (int t = 0; t < 4; t++) {
            workers.add(() -> {
                for (int i = 0; i < OPS; i++) {
                    try {
                        engine.changeSelection("app:u" + (i % USERS), sel(i + 1));
                    } catch (Throwable e) {
                        if (!isDomainRace(e)) corruption.add(e);
                    }
                }
            });
        }
        // register/teardown the same ids from several threads (write-write contention) + sweep.
        for (int t = 0; t < 4; t++) {
            workers.add(() -> {
                for (int i = 0; i < OPS; i++) {
                    String id = "app:churn" + (i % 8);
                    try {
                        engine.registerAppSession(id, "churn" + (i % 8), sel(i), Set.of());
                        engine.attachSocket(id, "cs" + i);
                        engine.teardownAppSession(id);
                        if ((i & 31) == 0) engine.sweepExpired();
                    } catch (Throwable e) {
                        if (!isDomainRace(e)) corruption.add(e);
                    }
                }
            });
        }

        CountDownLatch ready = new CountDownLatch(workers.size());
        List<java.util.concurrent.Future<?>> futures = new ArrayList<>();
        for (Runnable w : workers) {
            futures.add(pool.submit(() -> {
                ready.countDown();
                while (!go.get()) {
                    Thread.onSpinWait();
                }
                w.run();
            }));
        }
        ready.await(10, TimeUnit.SECONDS);
        go.set(true); // release all threads at once for maximum contention
        for (java.util.concurrent.Future<?> f : futures) {
            f.get(60, TimeUnit.SECONDS);
        }
        pool.shutdownNow();

        assertTrue(corruption.isEmpty(),
                "thread-safety corruption: " + corruption.stream().map(Object::toString).limit(5).toList());

        // Engine is still internally consistent and functional after the storm.
        for (int i = 0; i < USERS; i++) {
            assertTrue(engine.appSession("app:u" + i).isPresent(), "stable session u" + i + " was lost");
        }
        // Re-pin u0 to a known contract + socket and prove routing still resolves correctly.
        engine.changeSelection("app:u0", sel(0));
        engine.attachSocket("app:u0", "verify-s0");
        assertTrue(engine.route(rec(0)).contains("verify-s0"), "engine routing broken after concurrency storm");
        assertEquals(USERS, countStable(engine), "stable sessions must survive");
    }

    private static int countStable(SessionRoutingEngine engine) {
        int n = 0;
        for (int i = 0; i < USERS; i++) {
            if (engine.appSession("app:u" + i).isPresent()) {
                n++;
            }
        }
        return n;
    }
}

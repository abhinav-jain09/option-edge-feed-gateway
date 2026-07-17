package app.feedgateway.pinflow;

import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * §6.5 result-cache coalescing (P2) and §6.1 deadline cancellation (P2) for {@link PinFlowStore}.
 *
 * <ul>
 *   <li><b>Atomic coalescing:</b> N concurrent misses for the SAME {@code (date,lo,hi)} key trigger
 *       exactly ONE DB fetch — the first caller runs the query inline, the rest await the same future.</li>
 *   <li><b>Deadline cancellation:</b> interrupting the worker thread mid-query causes the store to call
 *       {@link PreparedStatement#cancel()} and close the connection, freeing the pooled resource promptly
 *       instead of blocking for the full statement timeout.</li>
 * </ul>
 */
class PinFlowStoreTest {

    private static final ZoneId ET = ZoneId.of("America/New_York");
    private static final LocalDate DATE = LocalDate.of(2026, 6, 23);

    // ---- P2: cache coalescing — N concurrent misses for the same key = ONE DB fetch. ----

    @Test
    void concurrentMissesForSameKeyTriggerExactlyOneFetch() throws Exception {
        int callers = 8;
        AtomicInteger connectionsOpened = new AtomicInteger();
        // Each getConnection() blocks on this latch so all callers pile up on the SAME key concurrently
        // before the first fetch completes — the worst case for a racy get→query→put cache.
        CountDownLatch releaseQuery = new CountDownLatch(1);
        DataSource ds = new FakeDataSource(() -> {
            connectionsOpened.incrementAndGet();
            return connectionProxy(new FakeStatement(releaseQuery, new AtomicBoolean()), new AtomicBoolean());
        });
        PinFlowStore store = new PinFlowStore(ds, ET, 10, 8_000L);

        CountDownLatch allStarted = new CountDownLatch(callers);
        AtomicInteger successes = new AtomicInteger();
        Thread[] threads = new Thread[callers];
        for (int i = 0; i < callers; i++) {
            threads[i] = new Thread(() -> {
                allStarted.countDown();
                store.query(DATE, 7490, 7590, 1_000L);
                successes.incrementAndGet();
            });
            threads[i].start();
        }
        // Wait until all caller threads are running, give them a beat to converge on computeIfAbsent,
        // then release the single in-flight fetch.
        assertTrue(allStarted.await(5, TimeUnit.SECONDS));
        Thread.sleep(100L);
        releaseQuery.countDown();
        for (Thread t : threads) {
            t.join(5_000L);
        }

        assertEquals(callers, successes.get(), "every caller must get a result");
        assertEquals(1, connectionsOpened.get(),
                "concurrent misses for the same key must coalesce into exactly ONE DB fetch");
    }

    @Test
    void failedFetchIsNotCachedSoNextCallerRetries() {
        AtomicInteger connectionsOpened = new AtomicInteger();
        AtomicBoolean fail = new AtomicBoolean(true);
        DataSource ds = new FakeDataSource(() -> {
            connectionsOpened.incrementAndGet();
            if (fail.get()) {
                throw new SQLException("boom", "08006"); // connection failure SQLState
            }
            return connectionProxy(new FakeStatement(new CountDownLatch(0), new AtomicBoolean()),
                    new AtomicBoolean());
        });
        PinFlowStore store = new PinFlowStore(ds, ET, 10, 8_000L);

        try {
            store.query(DATE, 7490, 7590, 1_000L);
        } catch (PinFlowStore.DbUnavailableException expected) {
            // errors must never be cached
        }
        fail.set(false); // DB recovers
        store.query(DATE, 7490, 7590, 1_000L); // must re-fetch, not serve a cached error
        assertEquals(2, connectionsOpened.get(), "a failed fetch must NOT be cached; the next call re-fetches");
    }

    @Test
    void interruptedFollowerReturnsPromptlyWithoutWaitingForOwnersDbWork() throws Exception {
        AtomicInteger connectionsOpened = new AtomicInteger();
        // The OWNER blocks forever inside executeQuery (never released) — models a slow DB query. The
        // FOLLOWER coalesces onto the same key and must NOT block until the owner finishes: when its own
        // thread is interrupted (deadline / disconnect → Future.cancel(true)), it must return fast (503).
        CountDownLatch neverReleased = new CountDownLatch(1);
        CountDownLatch ownerInQuery = new CountDownLatch(1);
        DataSource ds = new FakeDataSource(() -> {
            connectionsOpened.incrementAndGet();
            return connectionProxy(
                    new FakeStatement(neverReleased, new AtomicBoolean(), ownerInQuery), new AtomicBoolean());
        });
        PinFlowStore store = new PinFlowStore(ds, ET, 10, 8_000L);

        // Owner: wins computeIfAbsent, runs the DB work, blocks inside executeQuery.
        Thread owner = new Thread(() -> {
            try {
                store.query(DATE, 7490, 7590, 1_000L);
            } catch (PinFlowStore.DbUnavailableException ignored) {
                // owner may surface cancellation when the test tears down — irrelevant to this assertion.
            }
        });
        owner.start();
        assertTrue(ownerInQuery.await(5, TimeUnit.SECONDS), "owner must reach executeQuery and hold the fetch");

        // Follower: coalesces onto the SAME key, so it awaits the shared future (no second connection).
        AtomicBoolean followerThrew = new AtomicBoolean();
        CountDownLatch followerDone = new CountDownLatch(1);
        Thread follower = new Thread(() -> {
            try {
                store.query(DATE, 7490, 7590, 1_000L);
            } catch (PinFlowStore.DbUnavailableException cancelled) {
                followerThrew.set(true);
            } finally {
                followerDone.countDown();
            }
        });
        follower.start();
        // Give the follower a beat to converge on the shared future, then interrupt it (its deadline fired).
        Thread.sleep(150L);
        follower.interrupt();

        // The follower MUST return promptly (well before the owner's blocked query would ever finish).
        assertTrue(followerDone.await(2, TimeUnit.SECONDS),
                "an interrupted follower must return promptly, not block on the owner's DB work");
        assertTrue(followerThrew.get(),
                "an interrupted follower must surface as DbUnavailableException (→ 503)");
        assertEquals(1, connectionsOpened.get(),
                "the follower must coalesce onto the owner's fetch, not open a second connection");

        // Tear down: unblock the owner so its thread exits.
        neverReleased.countDown();
        owner.join(5_000L);
    }

    // ---- P2: deadline cancellation — interrupting the worker cancels the statement + frees the conn. ----

    @Test
    void interruptedQueryCancelsStatementAndClosesConnection() throws Exception {
        CountDownLatch queryEntered = new CountDownLatch(1);
        AtomicBoolean cancelCalled = new AtomicBoolean();
        AtomicBoolean connClosed = new AtomicBoolean();
        // executeQuery blocks until cancel() flips the flag (mirrors a real driver aborting on cancel).
        FakeStatement stmt = new FakeStatement(new CountDownLatch(1), cancelCalled, queryEntered);
        DataSource ds = new FakeDataSource(() -> connectionProxy(stmt, connClosed));
        PinFlowStore store = new PinFlowStore(ds, ET, 10, 8_000L);

        AtomicBoolean threw = new AtomicBoolean();
        Thread worker = new Thread(() -> {
            try {
                store.query(DATE, 7490, 7590, 1_000L);
            } catch (PinFlowStore.DbUnavailableException cancelled) {
                threw.set(true); // cancellation surfaces as the 503-mapped DbUnavailableException
            }
        });
        worker.start();

        // Wait until the query is actually blocked inside executeQuery, then interrupt (deadline fired).
        assertTrue(queryEntered.await(5, TimeUnit.SECONDS), "worker must reach executeQuery");
        worker.interrupt();
        worker.join(5_000L);

        assertTrue(cancelCalled.get(), "interrupt must trigger Statement.cancel() to abort the query");
        assertTrue(connClosed.get(), "the connection must be closed promptly on cancellation");
        assertTrue(threw.get(), "a cancelled query must surface as DbUnavailableException (→ 503)");
    }

    // ================= fake JDBC stack =================

    private interface ConnectionSupplier {
        Connection get() throws SQLException;
    }

    private static final class FakeDataSource implements DataSource {
        private final ConnectionSupplier supplier;

        FakeDataSource(ConnectionSupplier supplier) {
            this.supplier = supplier;
        }

        @Override public Connection getConnection() throws SQLException { return supplier.get(); }
        @Override public Connection getConnection(String u, String p) throws SQLException { return supplier.get(); }
        @Override public PrintWriter getLogWriter() { return null; }
        @Override public void setLogWriter(PrintWriter out) { }
        @Override public void setLoginTimeout(int seconds) { }
        @Override public int getLoginTimeout() { return 0; }
        @Override public Logger getParentLogger() { return null; }
        @Override public <T> T unwrap(Class<T> iface) { return null; }
        @Override public boolean isWrapperFor(Class<?> iface) { return false; }
    }

    private static final class FakeConnection implements java.lang.reflect.InvocationHandler {
        private final FakeStatement statement;
        private final AtomicBoolean closed;

        private FakeConnection(FakeStatement statement, AtomicBoolean closed) {
            this.statement = statement;
            this.closed = closed;
        }

        static Connection of(FakeStatement statement, AtomicBoolean closed) {
            return (Connection) java.lang.reflect.Proxy.newProxyInstance(
                    Connection.class.getClassLoader(), new Class<?>[]{Connection.class},
                    new FakeConnection(statement, closed));
        }

        @Override
        public Object invoke(Object proxy, java.lang.reflect.Method method, Object[] args) {
            switch (method.getName()) {
                case "prepareStatement":
                    return statement.proxy();
                case "close":
                    closed.set(true);
                    return null;
                case "setReadOnly":
                    return null;
                case "isReadOnly":
                    return true;
                case "toString":
                    return "FakeConnection";
                default:
                    return defaultFor(method.getReturnType());
            }
        }
    }

    private static Object defaultFor(Class<?> t) {
        if (t == boolean.class) return false;
        if (t == int.class) return 0;
        if (t == long.class) return 0L;
        if (t.isPrimitive()) return 0;
        return null;
    }

    private static Connection connectionProxy(FakeStatement statement, AtomicBoolean closed) {
        return FakeConnection.of(statement, closed);
    }

    private static final class FakeStatement implements java.lang.reflect.InvocationHandler {
        private final CountDownLatch releaseQuery; // count 0 = don't block
        private final AtomicBoolean cancelCalled;
        private final CountDownLatch queryEntered; // signalled when executeQuery is reached

        FakeStatement(CountDownLatch releaseQuery, AtomicBoolean cancelCalled) {
            this(releaseQuery, cancelCalled, new CountDownLatch(0));
        }

        FakeStatement(CountDownLatch releaseQuery, AtomicBoolean cancelCalled, CountDownLatch queryEntered) {
            this.releaseQuery = releaseQuery;
            this.cancelCalled = cancelCalled;
            this.queryEntered = queryEntered;
        }

        PreparedStatement proxy() {
            return (PreparedStatement) java.lang.reflect.Proxy.newProxyInstance(
                    PreparedStatement.class.getClassLoader(),
                    new Class<?>[]{PreparedStatement.class}, this);
        }

        @Override
        public Object invoke(Object proxy, java.lang.reflect.Method method, Object[] args) throws SQLException {
            switch (method.getName()) {
                case "executeQuery":
                    queryEntered.countDown();
                    // Block until released OR cancel() aborts us. A real JDBC driver blocks on socket I/O
                    // and does NOT respond to Thread.interrupt(); it only aborts when cancel() runs on
                    // another thread. We model that: interrupts are swallowed here, so the ONLY way out
                    // (short of release) is the watcher-driven cancel() → proves the watcher path works.
                    while (releaseQuery.getCount() > 0) {
                        if (cancelCalled.get()) {
                            throw new SQLException("query cancelled", "57014"); // query_canceled SQLState
                        }
                        try {
                            if (releaseQuery.await(5, TimeUnit.MILLISECONDS)) {
                                break;
                            }
                        } catch (InterruptedException e) {
                            // Model a driver that ignores thread interrupts: keep the interrupt status set
                            // (a socket-blocked driver never clears it) and loop, so the ONLY exit is the
                            // watcher-driven cancel().
                            Thread.currentThread().interrupt();
                        }
                    }
                    if (cancelCalled.get()) {
                        throw new SQLException("query cancelled", "57014");
                    }
                    return emptyResultSet();
                case "cancel":
                    cancelCalled.set(true);
                    return null;
                case "close":
                    return null;
                case "toString":
                    return "FakeStatement";
                default:
                    return defaultFor(method.getReturnType());
            }
        }
    }

    private static ResultSet emptyResultSet() {
        return (ResultSet) java.lang.reflect.Proxy.newProxyInstance(
                ResultSet.class.getClassLoader(), new Class<?>[]{ResultSet.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("next")) {
                        return false;
                    }
                    if (method.getName().equals("close")) {
                        return null;
                    }
                    return defaultFor(method.getReturnType());
                });
    }
}

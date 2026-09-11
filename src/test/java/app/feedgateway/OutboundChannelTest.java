package app.feedgateway;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.stubbing.Answer;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

/**
 * P0: a slow/blocked/adversarial socket must never block the producer (Kafka) thread. The per-socket
 * channel buffers asynchronously, coalesces replaceable snapshots, bounds the queue, enforces a write
 * deadline, and disconnects deterministically — all without the enqueuer ever waiting on socket I/O.
 */
class OutboundChannelTest {

    private final ExecutorService writers = Executors.newSingleThreadExecutor();
    private final List<String> sent = new CopyOnWriteArrayList<>();
    private final AtomicInteger coalesced = new AtomicInteger();
    private final AtomicInteger slowDisconnects = new AtomicInteger();
    private final AtomicInteger writeErrors = new AtomicInteger();
    private final AtomicInteger droppedOnClose = new AtomicInteger();
    private final CountDownLatch closedCb = new CountDownLatch(1);

    private final OutboundChannel.Metrics metrics = new OutboundChannel.Metrics() {
        @Override public void enqueued(int bytes) { }
        @Override public void coalesced() { coalesced.incrementAndGet(); }
        @Override public void sent(int bytes) { }
        @Override public void disconnectedSlow() { slowDisconnects.incrementAndGet(); }
        @Override public void writeError() { writeErrors.incrementAndGet(); }
        @Override public void droppedOnClose(int n) { droppedOnClose.addAndGet(n); }
    };

    @AfterEach
    void tearDown() {
        writers.shutdownNow();
    }

    /** A session whose first sendMessage signals it entered, then blocks until released. */
    private WebSocketSession blockingSession(CountDownLatch entered, CountDownLatch release) throws Exception {
        WebSocketSession ws = mock(WebSocketSession.class);
        when(ws.getId()).thenReturn("s1");
        when(ws.isOpen()).thenReturn(true);
        Answer<Void> answer = inv -> {
            sent.add(((TextMessage) inv.getArgument(0)).getPayload());
            entered.countDown();
            release.await(5, TimeUnit.SECONDS);
            return null;
        };
        org.mockito.Mockito.doAnswer(answer).when(ws).sendMessage(any());
        // unblock any blocked writer when the channel force-closes the session
        org.mockito.Mockito.doAnswer(inv -> { release.countDown(); return null; }).when(ws).close();
        return ws;
    }

    private WebSocketSession recordingSession() throws Exception {
        WebSocketSession ws = mock(WebSocketSession.class);
        when(ws.getId()).thenReturn("s1");
        when(ws.isOpen()).thenReturn(true);
        org.mockito.Mockito.doAnswer(inv -> {
            sent.add(((TextMessage) inv.getArgument(0)).getPayload());
            return null;
        }).when(ws).sendMessage(any());
        return ws;
    }

    private OutboundChannel channel(WebSocketSession ws, int maxMessages, long maxBytes) {
        return new OutboundChannel(ws, writers, maxMessages, maxBytes, metrics, c -> closedCb.countDown());
    }

    @Test
    void enqueueDoesNotBlockTheProducerWhileTheWriterIsStuck() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        OutboundChannel ch = channel(blockingSession(entered, release), 1000, 1 << 20);

        assertTrue(ch.enqueue("{\"m\":1}", null));
        assertTrue(entered.await(2, TimeUnit.SECONDS), "writer should be in sendMessage");

        // The writer is blocked in I/O; these producer enqueues must still return immediately (buffered).
        long t0 = System.nanoTime();
        for (int i = 2; i <= 50; i++) {
            assertTrue(ch.enqueue("{\"m\":" + i + "}", null));
        }
        long elapsedMs = (System.nanoTime() - t0) / 1_000_000;
        assertTrue(elapsedMs < 1000, "enqueue must not block on socket I/O (took " + elapsedMs + "ms)");
        assertTrue(ch.queueDepth() > 0, "messages are buffered, not sent synchronously");

        release.countDown();
    }

    @Test
    void replaceableSnapshotsAreCoalescedToLatest() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        OutboundChannel ch = channel(blockingSession(entered, release), 1000, 1 << 20);

        ch.enqueue("first", null);                       // drains immediately, blocks the writer
        assertTrue(entered.await(2, TimeUnit.SECONDS));

        String key = "snapshot|SPX|20260612|7500";
        for (int i = 1; i <= 5; i++) {
            ch.enqueue("snap-" + i, key);                // same contract → coalesce to the latest
        }
        assertEquals(4, coalesced.get(), "5 same-key snapshots collapse to 1 (4 coalesced away)");

        release.countDown();
        waitForSent(2);                                  // "first" + the single coalesced latest
        assertTrue(sent.contains("snap-5"), "the latest snapshot is delivered");
        assertFalse(sent.contains("snap-3"), "an intermediate snapshot is dropped by coalescing");
    }

    @Test
    void exceedingTheQueueBoundDisconnectsTheClientDeterministically() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        WebSocketSession ws = blockingSession(entered, release);
        OutboundChannel ch = channel(ws, 2, 1 << 20);    // tiny queue bound

        ch.enqueue("first", null);                       // in-flight (writer blocked)
        assertTrue(entered.await(2, TimeUnit.SECONDS));
        ch.enqueue("a", null);                           // queue=1
        ch.enqueue("b", null);                           // queue=2
        boolean accepted = ch.enqueue("c", null);        // queue=3 > 2 → overflow → disconnect

        assertFalse(accepted, "the overflowing enqueue is rejected");
        assertTrue(closedCb.await(2, TimeUnit.SECONDS), "the slow client is disconnected");
        assertEquals(1, slowDisconnects.get());
        assertTrue(ch.isClosed());
        org.mockito.Mockito.verify(ws).close();          // socket actually closed
        release.countDown();
    }

    @Test
    void nonCoalescableMessagesAreAllDeliveredInOrder() throws Exception {
        OutboundChannel ch = channel(recordingSession(), 1000, 1 << 20);
        for (int i = 1; i <= 5; i++) {
            ch.enqueue("ctrl-" + i, null);
        }
        waitForSent(5);
        assertEquals(List.of("ctrl-1", "ctrl-2", "ctrl-3", "ctrl-4", "ctrl-5"), sent);
        assertEquals(0, coalesced.get());
    }

    @Test
    void writeDeadlineForceClosesAStuckSend() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        WebSocketSession ws = blockingSession(entered, release);
        OutboundChannel ch = channel(ws, 1000, 1 << 20);

        ch.enqueue("stuck", null);
        assertTrue(entered.await(2, TimeUnit.SECONDS), "writer is blocked in the send");

        // Simulate the watchdog firing well past the deadline while the send is in flight.
        boolean disconnected = ch.enforceWriteDeadline(System.currentTimeMillis() + 10_000, 5_000);
        assertTrue(disconnected, "a send past the deadline is force-closed");
        assertTrue(ch.isClosed());
        assertEquals(1, writeErrors.get());
        // the session close runs off the watchdog's thread (strike re-review #1): it happens, just not inline
        org.mockito.Mockito.verify(ws, org.mockito.Mockito.timeout(2_000)).close();
        assertTrue(closedCb.await(2, TimeUnit.SECONDS));
    }

    /**
     * ES footprint strike re-review #1: closing a session can block behind the write still in flight on it.
     * The enqueuer that overflows must not wait for that: the channel is closed and its queue dropped at once,
     * every later enqueue is refused, and the session close — then onClose, exactly once — runs on a shared
     * closer thread, whenever the close can complete.
     */
    @Test
    void anOverflowNeverWaitsForABlockedSessionClose_andTheTeardownStillHappensExactlyOnce() throws Exception {
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        CountDownLatch closeEntered = new CountDownLatch(1), closeRelease = new CountDownLatch(1), onClosed = new CountDownLatch(1);
        java.util.concurrent.atomic.AtomicReference<String> closeThread = new java.util.concurrent.atomic.AtomicReference<>();
        AtomicInteger onCloseCalls = new AtomicInteger();
        WebSocketSession ws = mock(WebSocketSession.class);
        when(ws.getId()).thenReturn("s1");
        when(ws.isOpen()).thenReturn(true);
        org.mockito.Mockito.doAnswer(inv -> { entered.countDown(); release.await(10, TimeUnit.SECONDS); return null; }).when(ws).sendMessage(any());
        org.mockito.Mockito.doAnswer(inv -> {
            closeThread.set(Thread.currentThread().getName());
            closeEntered.countDown();
            closeRelease.await(10, TimeUnit.SECONDS);
            return null;
        }).when(ws).close();
        OutboundChannel ch = new OutboundChannel(ws, writers, 2, 1 << 20, metrics, c -> { onCloseCalls.incrementAndGet(); onClosed.countDown(); });
        try {
            ch.enqueue("first", null);
            assertTrue(entered.await(2, TimeUnit.SECONDS), "the writer is stuck in its send");
            ch.enqueue("a", null);
            ch.enqueue("b", null);
            long t0 = System.nanoTime();
            assertFalse(ch.enqueue("c", null), "the overflowing enqueue is refused");
            assertTrue((System.nanoTime() - t0) / 1_000_000 < 1_000, "…and returns without waiting for the close");
            assertTrue(ch.isClosed());
            assertEquals(0, ch.queueDepth(), "its queue is dropped at once");
            assertFalse(ch.enqueue("d", null), "every later enqueue is refused while the close is still in progress");
            assertTrue(closeEntered.await(2, TimeUnit.SECONDS));
            assertTrue(closeThread.get().startsWith("options-edge-ws-closer-"), "the close runs on a shared closer thread: " + closeThread.get());
            assertEquals(0, onCloseCalls.get(), "onClose follows the session close: until then the channel stays registered, closed");
            assertFalse(ch.enforceWriteDeadline(System.currentTimeMillis() + 60_000, 5_000), "the watchdog cannot close it a second time");
            assertEquals(1, slowDisconnects.get());
        } finally {
            closeRelease.countDown();
            release.countDown();
        }
        assertTrue(onClosed.await(2, TimeUnit.SECONDS), "the teardown completes once the close returns");
        assertEquals(1, onCloseCalls.get());
        org.mockito.Mockito.verify(ws, org.mockito.Mockito.times(1)).close();
    }

    // ---- strike re-review round 3: teardowns share one bounded pool --------------------------------------

    /** Live threads named like a closer: the shared pool's, or any a thread-per-close executor started. */
    static int liveCloserThreads() {
        int n = 0;
        for (Thread t : Thread.getAllStackTraces().keySet()) {
            if (t.isAlive() && t.getName().startsWith("options-edge-ws-closer")) n++;
        }
        return n;
    }

    /** The shared pool is process-wide: start from an idle one, so "exactly the bound is busy" is this test's. */
    static void awaitSharedClosersIdle() throws InterruptedException {
        long deadline = System.currentTimeMillis() + 10_000;
        while ((OutboundChannel.TEARDOWN.busy() > 0 || OutboundChannel.TEARDOWN.queued() > 0) && System.currentTimeMillis() < deadline) {
            Thread.sleep(10);
        }
        assertEquals(0, OutboundChannel.TEARDOWN.busy(), "an earlier test left a closer thread busy");
        assertEquals(0, OutboundChannel.TEARDOWN.queued(), "an earlier test left teardowns queued");
    }

    private WebSocketSession recordingSession(String id, List<String> into) throws Exception {
        WebSocketSession ws = mock(WebSocketSession.class);
        when(ws.getId()).thenReturn(id);
        when(ws.isOpen()).thenReturn(true);
        org.mockito.Mockito.doAnswer(inv -> { into.add(((TextMessage) inv.getArgument(0)).getPayload()); return null; }).when(ws).sendMessage(any());
        return ws;
    }

    private static void awaitSize(List<String> got, int n) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5_000;
        while (got.size() < n && System.currentTimeMillis() < deadline) Thread.sleep(5);
    }

    /**
     * The reviewer's reproduction: 64 channels whose close() blocks overflow at once. A thread per close meant
     * 64 closer threads alive at the same time (a timeout bounds how long each lives, not how many there are),
     * and in a real mass overflow a Thread.start() that cannot get a native thread throws on the drainer. Now
     * no more than {@link OutboundChannel#CLOSER_THREADS} closer threads ever exist, exactly that many closes
     * are in progress while the rest wait in the queue, the fan-out and every enqueue return at once, the
     * healthy channels keep receiving, and once the closes can return every channel is torn down exactly once.
     */
    @Test
    void sixtyFourChannelsOverflowingAtOnce_neverHoldMoreCloserThreadsThanTheBound_andStallNoSender() throws Exception {
        awaitSharedClosersIdle();
        int slowCount = 64, max = 5, frames = 20;
        CountDownLatch closeRelease = new CountDownLatch(1), allTornDown = new CountDownLatch(slowCount);
        AtomicInteger closesInProgress = new AtomicInteger();
        Map<String, AtomicInteger> onCloseCalls = new ConcurrentHashMap<>(), closeCalls = new ConcurrentHashMap<>();
        Executor stuckWriters = task -> { };                           // their writers never get to run: each queue only fills
        List<OutboundChannel> slow = new ArrayList<>();
        for (int i = 0; i < slowCount; i++) {
            String id = "slow-" + i;
            WebSocketSession ws = mock(WebSocketSession.class);
            when(ws.getId()).thenReturn(id);
            when(ws.isOpen()).thenReturn(true);
            org.mockito.Mockito.doAnswer(inv -> {
                closeCalls.computeIfAbsent(id, k -> new AtomicInteger()).incrementAndGet();
                closesInProgress.incrementAndGet();
                try { closeRelease.await(30, TimeUnit.SECONDS); } finally { closesInProgress.decrementAndGet(); }
                return null;
            }).when(ws).close();
            slow.add(new OutboundChannel(ws, stuckWriters, max, 1 << 20, metrics, c -> {
                onCloseCalls.computeIfAbsent(c.socketId(), k -> new AtomicInteger()).incrementAndGet();
                allTornDown.countDown();
            }));
        }
        List<String> got1 = new CopyOnWriteArrayList<>(), got2 = new CopyOnWriteArrayList<>();
        OutboundChannel h1 = new OutboundChannel(recordingSession("h1", got1), writers, 1000, 1 << 20, metrics, c -> { });
        OutboundChannel h2 = new OutboundChannel(recordingSession("h2", got2), writers, 1000, 1 << 20, metrics, c -> { });
        List<OutboundChannel> fanOut = new ArrayList<>();
        fanOut.add(h1);
        fanOut.addAll(slow);
        fanOut.add(h2);
        AtomicLong slowestEnqueueNs = new AtomicLong();
        AtomicReference<Throwable> thrown = new AtomicReference<>();
        Thread drainer = new Thread(() -> {
            try {
                for (int f = 0; f < frames; f++) {
                    for (OutboundChannel c : fanOut) {
                        long t0 = System.nanoTime();
                        c.enqueue("{\"f\":" + f + "}", null);
                        slowestEnqueueNs.accumulateAndGet(System.nanoTime() - t0, Math::max);
                    }
                }
            } catch (Throwable t) {
                thrown.set(t);
            }
        }, "strike-drainer");
        AtomicInteger peak = new AtomicInteger();
        AtomicBoolean sampling = new AtomicBoolean(true);
        Thread sampler = new Thread(() -> {
            while (sampling.get()) {
                peak.accumulateAndGet(liveCloserThreads(), Math::max);
                try { Thread.sleep(2); } catch (InterruptedException e) { return; }
            }
        }, "closer-sampler");
        try {
            sampler.start();
            drainer.start();
            drainer.join(10_000);
            assertFalse(drainer.isAlive(), "the fan-out finished while 64 closes are blocked");
            assertNull(thrown.get(), "nothing was thrown at the fan-out");
            assertTrue(slowestEnqueueNs.get() / 1_000_000 < 500, "no enqueue waited for a close (slowest " + slowestEnqueueNs.get() / 1_000_000 + " ms)");
            for (OutboundChannel c : slow) assertTrue(c.isClosed(), c.socketId() + " overflowed and is closed");
            assertEquals(slowCount, slowDisconnects.get());
            long deadline = System.currentTimeMillis() + 5_000;
            while (closesInProgress.get() < OutboundChannel.CLOSER_THREADS && System.currentTimeMillis() < deadline) Thread.sleep(5);
            Thread.sleep(300);                                          // room for any closer thread beyond the bound to appear
            sampling.set(false);
            sampler.join(5_000);
            System.out.println("[closer-bound] OutboundChannelTest: 64 overflowing channels, peak live closer threads "
                    + peak.get() + " (bound " + OutboundChannel.CLOSER_THREADS + "), closes in progress " + closesInProgress.get()
                    + ", queued " + OutboundChannel.TEARDOWN.queued());
            assertTrue(peak.get() <= OutboundChannel.CLOSER_THREADS,
                    "64 overflowing channels held " + peak.get() + " live closer threads at once; the bound is " + OutboundChannel.CLOSER_THREADS);
            assertEquals(OutboundChannel.CLOSER_THREADS, closesInProgress.get(), "exactly the bound's closes are in progress…");
            assertEquals(slowCount - OutboundChannel.CLOSER_THREADS, OutboundChannel.TEARDOWN.queued(), "…and the others wait in the queue");
            assertTrue(onCloseCalls.isEmpty(), "onClose follows each session close: " + onCloseCalls.keySet());
            awaitSize(got1, frames);
            awaitSize(got2, frames);
            List<String> want = new ArrayList<>();
            for (int f = 0; f < frames; f++) want.add("{\"f\":" + f + "}");
            assertEquals(want, got1, "a healthy channel received every frame, in order");
            assertEquals(want, got2, "…and so did the one after all 64 slow ones");
        } finally {
            sampling.set(false);
            closeRelease.countDown();
        }
        assertTrue(allTornDown.await(10, TimeUnit.SECONDS), "every teardown ran once the closes could return (" + allTornDown.getCount() + " left)");
        assertEquals(slowCount, onCloseCalls.size());
        for (OutboundChannel c : slow) {
            assertEquals(1, onCloseCalls.get(c.socketId()).get(), "onClose exactly once for " + c.socketId());
            assertEquals(1, closeCalls.get(c.socketId()).get(), "one session close for " + c.socketId());
        }
    }

    /**
     * A teardown the closers cannot accept — an executor that throws OutOfMemoryError as a Thread.start() does
     * when no native thread is left, or one that rejects — is not thrown at the fan-out (the frame still
     * reaches every other channel) and not run inline; it stays pending, and the watchdog's retry hands it over
     * later: the channel is torn down exactly once.
     */
    @Test
    void aTeardownThatCannotBeHandedOverNeverAbortsTheFanOut_andTheWatchdogTearsItDownLater() throws Exception {
        AtomicBoolean accept = new AtomicBoolean(false);
        List<Runnable> accepted = new CopyOnWriteArrayList<>();
        Executor noNativeThread = task -> {
            if (!accept.get()) throw new OutOfMemoryError("unable to create native thread: possibly out of memory or process/resource limits reached");
            accepted.add(task);
        };
        Executor rejecting = task -> {
            if (!accept.get()) throw new RejectedExecutionException("closers refused");
            accepted.add(task);
        };
        Map<String, AtomicInteger> onCloseCalls = new ConcurrentHashMap<>();
        Executor stuckWriters = task -> { };
        WebSocketSession wsOom = mock(WebSocketSession.class), wsRej = mock(WebSocketSession.class);
        when(wsOom.getId()).thenReturn("bad-oom");
        when(wsRej.getId()).thenReturn("bad-rejected");
        when(wsOom.isOpen()).thenReturn(true);
        when(wsRej.isOpen()).thenReturn(true);
        java.util.function.Consumer<OutboundChannel> onClose = c -> onCloseCalls.computeIfAbsent(c.socketId(), k -> new AtomicInteger()).incrementAndGet();
        OutboundChannel bad1 = new OutboundChannel(wsOom, stuckWriters, noNativeThread, 2, 1 << 20, metrics, onClose);
        OutboundChannel bad2 = new OutboundChannel(wsRej, stuckWriters, rejecting, 2, 1 << 20, metrics, onClose);
        List<String> got1 = new CopyOnWriteArrayList<>(), got2 = new CopyOnWriteArrayList<>();
        OutboundChannel h1 = new OutboundChannel(recordingSession("h1", got1), writers, 1000, 1 << 20, metrics, c -> { });
        OutboundChannel h2 = new OutboundChannel(recordingSession("h2", got2), writers, 1000, 1 << 20, metrics, c -> { });
        List<OutboundChannel> fanOut = List.of(bad1, h1, bad2, h2);
        int frames = 5;
        Throwable thrown = null;
        try {
            for (int f = 0; f < frames; f++) for (OutboundChannel c : fanOut) c.enqueue("{\"f\":" + f + "}", null);
        } catch (Throwable t) {
            thrown = t;
        }
        assertNull(thrown, "a teardown that could not be handed over was thrown at the fan-out: " + thrown);
        assertTrue(bad1.isClosed() && bad2.isClosed(), "both overflowed and are closed");
        assertTrue(bad1.teardownPending() && bad2.teardownPending(), "…with their teardowns pending, not lost");
        awaitSize(got1, frames);
        awaitSize(got2, frames);
        List<String> want = new ArrayList<>();
        for (int f = 0; f < frames; f++) want.add("{\"f\":" + f + "}");
        assertEquals(want, got1, "the channel after the failed hand-over received every frame");
        assertEquals(want, got2, "…and so did the last one");
        assertTrue(onCloseCalls.isEmpty(), "nothing ran the teardown inline");
        org.mockito.Mockito.verify(wsOom, org.mockito.Mockito.never()).close();
        assertFalse(bad1.retryPendingTeardown(), "a watchdog tick while the closers still refuse…");
        assertTrue(bad1.teardownPending(), "…leaves it pending, and throws nothing");
        accept.set(true);
        assertTrue(bad1.retryPendingTeardown(), "the next tick hands it over");
        assertTrue(bad2.retryPendingTeardown());
        assertFalse(bad1.retryPendingTeardown(), "…once");
        assertFalse(bad1.teardownPending() || bad2.teardownPending());
        assertEquals(2, accepted.size());
        for (Runnable r : accepted) { r.run(); r.run(); }             // even a closer that ran a teardown twice
        assertEquals(1, onCloseCalls.get("bad-oom").get(), "onClose exactly once");
        assertEquals(1, onCloseCalls.get("bad-rejected").get(), "onClose exactly once");
        org.mockito.Mockito.verify(wsOom, org.mockito.Mockito.times(1)).close();
        org.mockito.Mockito.verify(wsRej, org.mockito.Mockito.times(1)).close();
        assertFalse(bad1.enqueue("late", null), "and it never writes again");
    }

    /**
     * When every closer thread is stuck in a close, the other teardowns wait in the queue — nothing else does
     * — and the write watchdog's close deadline frees the threads: an overdue close is interrupted (once), its
     * teardown finishes (onClose still after the close attempt, once), and the queued ones proceed.
     */
    @Test
    void whenEveryCloserIsStuck_theOtherTeardownsWait_andTheCloseDeadlineFreesTheThreads() throws Exception {
        OutboundChannel.TeardownPool pool = new OutboundChannel.TeardownPool(2, "test-closer-", 1_000);
        try {
            CountDownLatch never = new CountDownLatch(1), allTornDown = new CountDownLatch(5);
            AtomicInteger inProgress = new AtomicInteger(), interruptedCloses = new AtomicInteger();
            Map<String, AtomicInteger> onCloseCalls = new ConcurrentHashMap<>();
            List<OutboundChannel> channels = new ArrayList<>();
            for (int i = 0; i < 5; i++) {
                WebSocketSession ws = mock(WebSocketSession.class);
                when(ws.getId()).thenReturn("stuck-" + i);
                when(ws.isOpen()).thenReturn(true);
                org.mockito.Mockito.doAnswer(inv -> {
                    inProgress.incrementAndGet();
                    try {
                        never.await();                                  // a close that never returns on its own
                        return null;
                    } catch (InterruptedException e) {
                        interruptedCloses.incrementAndGet();
                        throw new IOException("close interrupted");     // what a container close does when interrupted
                    } finally {
                        inProgress.decrementAndGet();
                    }
                }).when(ws).close();
                channels.add(new OutboundChannel(ws, task -> { }, pool, 1, 1 << 20, metrics, c -> {
                    onCloseCalls.computeIfAbsent(c.socketId(), k -> new AtomicInteger()).incrementAndGet();
                    allTornDown.countDown();
                }));
            }
            long t0 = System.nanoTime();
            for (OutboundChannel c : channels) { c.enqueue("a", null); c.enqueue("b", null); }
            assertTrue((System.nanoTime() - t0) / 1_000_000 < 500, "five overflows, none waiting for a close");
            long deadline = System.currentTimeMillis() + 5_000;
            while (inProgress.get() < 2 && System.currentTimeMillis() < deadline) Thread.sleep(5);
            Thread.sleep(200);
            assertEquals(2, inProgress.get(), "both closer threads are stuck…");
            assertEquals(3, pool.queued(), "…and the other three teardowns wait in the queue");
            assertTrue(onCloseCalls.isEmpty());
            assertEquals(0, pool.interruptOverdue(System.currentTimeMillis()), "a close within its deadline is left alone");
            deadline = System.currentTimeMillis() + 10_000;
            while (allTornDown.getCount() > 0 && System.currentTimeMillis() < deadline) {
                pool.interruptOverdue(System.currentTimeMillis() + 2_000);  // a watchdog tick past the deadline
                Thread.sleep(20);
            }
            assertEquals(0, allTornDown.getCount(), "the deadline freed the stuck threads and the queued teardowns ran");
            assertEquals(5, interruptedCloses.get(), "every close was interrupted once it overran the deadline");
            for (OutboundChannel c : channels) assertEquals(1, onCloseCalls.get(c.socketId()).get(), "onClose exactly once");
        } finally {
            pool.shutdownNow();
        }
    }

    // ---- strike re-review round 4: no failure ends a closer thread ------------------------------------------

    /** Live threads whose name starts with {@code prefix}. */
    static int liveThreads(String prefix) {
        int n = 0;
        for (Thread t : Thread.getAllStackTraces().keySet()) {
            if (t.isAlive() && t.getName().startsWith(prefix)) n++;
        }
        return n;
    }

    /** A channel on {@code pool} whose writer never runs; its session close runs out of heap on the first call if {@code closeFailsOnce}. */
    private OutboundChannel teardownChannel(String id, OutboundChannel.TeardownPool pool, boolean closeFailsOnce,
                                            Map<String, AtomicInteger> closeCalls, Map<String, AtomicInteger> onCloseCalls) throws Exception {
        WebSocketSession ws = mock(WebSocketSession.class);
        when(ws.getId()).thenReturn(id);
        when(ws.isOpen()).thenReturn(true);
        org.mockito.Mockito.doAnswer(inv -> {
            int call = closeCalls.computeIfAbsent(id, k -> new AtomicInteger()).incrementAndGet();
            if (closeFailsOnce && call == 1) throw new OutOfMemoryError("Java heap space");
            return null;
        }).when(ws).close();
        return new OutboundChannel(ws, task -> { }, pool, 1, 1 << 20, metrics,
                c -> onCloseCalls.computeIfAbsent(c.socketId(), k -> new AtomicInteger()).incrementAndGet());
    }

    private static void overflow(OutboundChannel c) {
        c.enqueue("a", null);
        c.enqueue("b", null);                                        // the bound is 1: this one overflows and closes it
    }

    private static void awaitTornDown(Map<String, AtomicInteger> onCloseCalls, List<OutboundChannel> channels) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 10_000;
        while (channels.stream().anyMatch(c -> !onCloseCalls.containsKey(c.socketId())) && System.currentTimeMillis() < deadline) Thread.sleep(10);
    }

    /**
     * The reviewer's round-4 reproduction, without exhausting the real heap: teardowns fail with OutOfMemoryError
     * (the session close runs out of heap) and REPORTING each failure throws OutOfMemoryError as well. Before, the
     * report escaped the closer's catch: four failures killed all four closer threads, and every teardown queued
     * behind them — and every one handed over later — waited forever, out of the watchdog's reach. Now no closer
     * thread dies, the teardowns behind and after the failures run, and each failed teardown is pending again: the
     * watchdog's retry tears it down. onClose exactly once for every channel.
     */
    @Test
    void aFailureWhileReportingATeardownFailureNeverKillsACloser_andNoAcceptedTeardownIsStranded() throws Exception {
        String prefix = "report-oom-closer-";
        AtomicInteger reports = new AtomicInteger();
        OutboundChannel.TeardownPool pool = new OutboundChannel.TeardownPool(4, prefix, 30_000, failure -> {
            reports.incrementAndGet();
            throw new OutOfMemoryError("Java heap space");                 // building the log line ran out of heap too
        });
        try {
            assertEquals(4, liveThreads(prefix));
            Map<String, AtomicInteger> closeCalls = new ConcurrentHashMap<>(), onCloseCalls = new ConcurrentHashMap<>();
            List<OutboundChannel> failing = new ArrayList<>(), behind = new ArrayList<>(), later = new ArrayList<>();
            for (int i = 0; i < 8; i++) {
                failing.add(teardownChannel("oom-" + i, pool, true, closeCalls, onCloseCalls));
                behind.add(teardownChannel("behind-" + i, pool, false, closeCalls, onCloseCalls));
                later.add(teardownChannel("later-" + i, pool, false, closeCalls, onCloseCalls));
            }
            for (int i = 0; i < 8; i++) {                               // each healthy teardown queued behind a failing one
                overflow(failing.get(i));
                overflow(behind.get(i));
            }
            awaitTornDown(onCloseCalls, behind);
            long deadline = System.currentTimeMillis() + 5_000;
            while (pool.failures() < 8 && System.currentTimeMillis() < deadline) Thread.sleep(10);
            assertEquals(4, liveThreads(prefix), "a closer thread died of a failure or of its report: nothing replaces it");
            assertEquals(8, pool.failures(), "every failed teardown was counted");
            assertEquals(8, reports.get(), "…every failure was offered to the report…");
            assertEquals(8, pool.unreported(), "…and every report that ran out of heap was counted instead");
            for (OutboundChannel c : behind) {
                assertEquals(1, onCloseCalls.get(c.socketId()).get(), "the teardown queued behind the failures ran: " + c.socketId());
                assertEquals(1, closeCalls.get(c.socketId()).get());
            }
            for (OutboundChannel c : failing) {
                assertTrue(c.isClosed(), c.socketId());
                assertTrue(c.teardownPending(), c.socketId() + ": a teardown whose close threw is pending again, not stranded");
                assertFalse(onCloseCalls.containsKey(c.socketId()), "onClose follows a close that returned, never one that threw");
            }
            for (OutboundChannel c : later) overflow(c);                // teardowns handed over after the failures
            awaitTornDown(onCloseCalls, later);
            for (OutboundChannel c : later) assertEquals(1, onCloseCalls.get(c.socketId()).get(), "a later teardown ran: " + c.socketId());
            for (OutboundChannel c : failing) assertTrue(c.retryPendingTeardown(), "the watchdog's tick hands " + c.socketId() + " over again");
            awaitTornDown(onCloseCalls, failing);
            for (OutboundChannel c : failing) {
                assertFalse(c.retryPendingTeardown(), "…once");
                assertEquals(1, onCloseCalls.get(c.socketId()).get(), "onClose exactly once for " + c.socketId());
                assertEquals(2, closeCalls.get(c.socketId()).get(), "the close that threw, then the retry's");
                assertFalse(c.enqueue("late", null), "and it never writes again");
            }
            assertEquals(4, liveThreads(prefix));
            assertEquals(8, pool.failures());
        } finally {
            pool.shutdownNow();
        }
    }

    /** A teardown that throws an Error — any Error — never ends its closer thread: the next teardown runs on it. */
    @Test
    void aTeardownThatThrowsAnErrorNeverEndsItsCloser_andTheNextTeardownRunsOnIt() throws Exception {
        List<Throwable> reported = new CopyOnWriteArrayList<>();
        OutboundChannel.TeardownPool pool = new OutboundChannel.TeardownPool(1, "error-closer-", 30_000, reported::add);
        try {
            CountDownLatch ran = new CountDownLatch(1);
            AtomicReference<String> ranOn = new AtomicReference<>();
            pool.execute(() -> { throw new OutOfMemoryError("Java heap space"); });
            pool.execute(() -> { throw new StackOverflowError(); });
            pool.execute(() -> { throw new AssertionError("a teardown bug"); });
            pool.execute(() -> { ranOn.set(Thread.currentThread().getName()); ran.countDown(); });
            assertTrue(ran.await(5, TimeUnit.SECONDS), "the teardown after three that threw an Error ran");
            assertEquals("error-closer-1", ranOn.get(), "…on the pool's one closer thread, which outlived all three");
            assertEquals(1, liveThreads("error-closer-"));
            assertEquals(3, pool.failures());
            assertEquals(0, pool.unreported());
            assertEquals(List.of(OutOfMemoryError.class, StackOverflowError.class, AssertionError.class),
                    reported.stream().map(Object::getClass).toList(), "each failure was reported, in order");
        } finally {
            pool.shutdownNow();
        }
    }

    /**
     * Reporting a failed hand-over can itself run out of heap — the line is built on the enqueuer, or on the write
     * watchdog. That report must not become the throw the hand-over's catch keeps from the fan-out, nor end the
     * watchdog's scheduled sweep (an Error escaping a scheduleAtFixedRate task cancels every later run).
     * System.out is replaced by one whose println runs out of heap.
     */
    @Test
    void aFailureWhileReportingAFailedHandOverIsThrownNeitherAtTheFanOutNorAtTheWatchdog() throws Exception {
        AtomicBoolean accept = new AtomicBoolean(false);
        List<Runnable> accepted = new CopyOnWriteArrayList<>();
        Executor noNativeThread = task -> {
            if (!accept.get()) throw new OutOfMemoryError("unable to create native thread: possibly out of memory or process/resource limits reached");
            accepted.add(task);
        };
        AtomicInteger onCloseCalls = new AtomicInteger(), printAttempts = new AtomicInteger();
        WebSocketSession wsBad = mock(WebSocketSession.class);
        when(wsBad.getId()).thenReturn("bad");
        when(wsBad.isOpen()).thenReturn(true);
        OutboundChannel bad = new OutboundChannel(wsBad, task -> { }, noNativeThread, 2, 1 << 20, metrics, c -> onCloseCalls.incrementAndGet());
        List<String> got = new CopyOnWriteArrayList<>();
        OutboundChannel healthy = new OutboundChannel(recordingSession("h", got), writers, 1000, 1 << 20, metrics, c -> { });
        int frames = 5;
        Throwable thrownAtFanOut = null, thrownAtWatchdog = null;
        java.io.PrintStream out = System.out;
        System.setOut(new java.io.PrintStream(java.io.OutputStream.nullOutputStream()) {
            @Override public void println(String line) {
                printAttempts.incrementAndGet();
                throw new OutOfMemoryError("Java heap space");
            }
        });
        try {
            try {
                for (int f = 0; f < frames; f++) {
                    bad.enqueue("{\"f\":" + f + "}", null);
                    healthy.enqueue("{\"f\":" + f + "}", null);
                }
            } catch (Throwable t) {
                thrownAtFanOut = t;
            }
            try {
                bad.retryPendingTeardown();                                // a watchdog tick while the closers still refuse
            } catch (Throwable t) {
                thrownAtWatchdog = t;
            }
        } finally {
            System.setOut(out);
        }
        assertNull(thrownAtFanOut, "the failed hand-over's report was thrown at the fan-out: " + thrownAtFanOut);
        assertNull(thrownAtWatchdog, "the failed hand-over's report was thrown at the watchdog: " + thrownAtWatchdog);
        assertEquals(2, printAttempts.get(), "both failed hand-overs tried to report, and both reports ran out of heap");
        awaitSize(got, frames);
        List<String> want = new ArrayList<>();
        for (int f = 0; f < frames; f++) want.add("{\"f\":" + f + "}");
        assertEquals(want, got, "the channel after the failed hand-over received every frame");
        assertTrue(bad.isClosed() && bad.teardownPending(), "closed, its teardown pending");
        accept.set(true);
        assertTrue(bad.retryPendingTeardown(), "the next tick hands it over");
        assertEquals(1, accepted.size());
        accepted.get(0).run();
        assertEquals(1, onCloseCalls.get(), "onClose exactly once");
        org.mockito.Mockito.verify(wsBad, org.mockito.Mockito.times(1)).close();
    }

    // ---- strike re-review round 5: a hand-over never overwrites a closer's pending restore -------------------

    /**
     * A closers executor that hands each teardown to {@code closers} and returns only once a closer thread has run
     * it to its end — for a teardown whose session close throws, past its restore to PENDING. That makes the
     * reviewer's round-5 interleaving certain instead of rare: the whole teardown, the failure and the restore
     * included, happens between the hand-over's publish and the hand-over's next step. Shared with
     * {@code FootprintStrikeDeliveryTest}, which runs the same interleaving on the production strike drainer.
     */
    static final class FinishBeforeReturning implements Executor {
        private final Executor closers;
        final AtomicInteger handOvers = new AtomicInteger();
        final AtomicInteger unfinished = new AtomicInteger();

        FinishBeforeReturning(Executor closers) {
            this.closers = closers;
        }

        @Override
        public void execute(Runnable teardown) {
            CountDownLatch finished = new CountDownLatch(1);
            closers.execute(() -> {
                try {
                    teardown.run();
                } finally {
                    finished.countDown();                            // after the teardown's own restore, before the pool records its throw
                }
            });
            handOvers.incrementAndGet();
            try {
                // Never throws: a throw from here is a failed hand-over to the channel, which would hide the ordering.
                if (!finished.await(10, TimeUnit.SECONDS)) unfinished.incrementAndGet();
            } catch (InterruptedException e) {
                unfinished.incrementAndGet();
                Thread.currentThread().interrupt();
            }
        }
    }

    private static void awaitFailures(OutboundChannel.TeardownPool pool, long n) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5_000;
        while (pool.failures() < n && System.currentTimeMillis() < deadline) Thread.sleep(5);
    }

    /**
     * The reviewer's round-5 finding, deterministically: the closer runs the teardown the moment it is published,
     * its session close throws, and it puts the teardown back to PENDING — all before the hand-over's execute()
     * returns. Before, the hand-over then wrote HANDED over that PENDING: the watchdog, which retries only a
     * PENDING teardown, skipped the channel for good, the session was never closed again and onClose never ran.
     * Now nothing is written after the publish (HANDED is claimed before it), so the teardown is still pending, the
     * watchdog's retry hands it over again, and onClose runs exactly once.
     */
    @Test
    void aHandOverNeverOverwritesTheClosersPendingRestore_soAFastFailedCloseIsRetriedNotStranded() throws Exception {
        String prefix = "fast-fail-closer-";
        OutboundChannel.TeardownPool pool = new OutboundChannel.TeardownPool(OutboundChannel.CLOSER_THREADS, prefix,
                OutboundChannel.CLOSE_DEADLINE_MS, failure -> { });
        try {
            FinishBeforeReturning closers = new FinishBeforeReturning(pool);
            AtomicInteger closeCalls = new AtomicInteger(), onCloseCalls = new AtomicInteger();
            WebSocketSession ws = mock(WebSocketSession.class);
            when(ws.getId()).thenReturn("fast-fail");
            when(ws.isOpen()).thenReturn(true);
            org.mockito.Mockito.doAnswer(inv -> {
                if (closeCalls.incrementAndGet() == 1) throw new OutOfMemoryError("Java heap space");
                return null;
            }).when(ws).close();
            OutboundChannel ch = new OutboundChannel(ws, task -> { }, closers, 1, 1 << 20, metrics, c -> onCloseCalls.incrementAndGet());
            overflow(ch);                                                   // the close hands the teardown over
            assertEquals(1, closers.handOvers.get(), "the overflow handed the teardown over once");
            assertEquals(0, closers.unfinished.get(), "…and the closer ran it to its end before the hand-over returned");
            assertEquals(1, closeCalls.get(), "the session close ran on the closer, and threw");
            assertTrue(ch.isClosed());
            assertTrue(ch.teardownPending(), "the closer's restore to PENDING outlived the hand-over: the teardown is pending, not stranded");
            assertEquals(0, onCloseCalls.get(), "onClose follows a close that returned, never one that threw");
            assertTrue(ch.retryPendingTeardown(), "the watchdog's tick hands it over again");
            assertEquals(2, closers.handOvers.get());
            assertEquals(2, closeCalls.get(), "the retry's close returned…");
            assertEquals(1, onCloseCalls.get(), "…and onClose ran");
            assertFalse(ch.teardownPending());
            assertFalse(ch.retryPendingTeardown(), "a spent teardown is never handed over again");
            assertEquals(2, closers.handOvers.get());
            assertEquals(1, onCloseCalls.get(), "onClose exactly once");
            assertFalse(ch.enqueue("late", null), "and it never writes again");
            awaitFailures(pool, 1);
            assertEquals(1, pool.failures(), "the close that threw was counted on the closer");
            assertEquals(OutboundChannel.CLOSER_THREADS, liveThreads(prefix));
        } finally {
            pool.shutdownNow();
        }
    }

    /**
     * The other side of the same race: an executor that publishes a teardown and throws anyway, after a closer has
     * already run it. The hand-over's failure path puts the teardown back to PENDING only if it is still HANDED —
     * a compare-and-set, never a plain write — so a teardown that already finished (DONE) is not resurrected as
     * pending, and one whose close threw is pending exactly once.
     */
    @Test
    void aHandOverThatThrowsAfterItsTeardownRanNeverOverwritesWhatTheCloserDid() throws Exception {
        String prefix = "publish-then-throw-closer-";
        OutboundChannel.TeardownPool pool = new OutboundChannel.TeardownPool(OutboundChannel.CLOSER_THREADS, prefix,
                OutboundChannel.CLOSE_DEADLINE_MS, failure -> { });
        try {
            FinishBeforeReturning runsIt = new FinishBeforeReturning(pool);
            AtomicBoolean throwAfter = new AtomicBoolean(true);
            Executor publishesThenThrows = task -> {
                runsIt.execute(task);
                if (throwAfter.get()) throw new RejectedExecutionException("accepted, then refused");
            };
            AtomicInteger okOnClose = new AtomicInteger(), failOnClose = new AtomicInteger(), failCloses = new AtomicInteger();
            WebSocketSession wsOk = mock(WebSocketSession.class), wsFail = mock(WebSocketSession.class);
            when(wsOk.getId()).thenReturn("ran-and-closed");
            when(wsFail.getId()).thenReturn("ran-and-threw");
            when(wsOk.isOpen()).thenReturn(true);
            when(wsFail.isOpen()).thenReturn(true);
            org.mockito.Mockito.doAnswer(inv -> {
                if (failCloses.incrementAndGet() == 1) throw new OutOfMemoryError("Java heap space");
                return null;
            }).when(wsFail).close();
            OutboundChannel ok = new OutboundChannel(wsOk, task -> { }, publishesThenThrows, 1, 1 << 20, metrics, c -> okOnClose.incrementAndGet());
            OutboundChannel fail = new OutboundChannel(wsFail, task -> { }, publishesThenThrows, 1, 1 << 20, metrics, c -> failOnClose.incrementAndGet());
            overflow(ok);
            overflow(fail);
            assertEquals(1, okOnClose.get(), "the teardown ran before the executor threw: onClose ran");
            assertFalse(ok.teardownPending(), "a teardown that finished is not made pending again by a hand-over that threw afterwards");
            assertFalse(ok.retryPendingTeardown(), "…so the watchdog never hands a finished teardown over again");
            assertEquals(2, runsIt.handOvers.get(), "one hand-over each");
            assertTrue(fail.teardownPending(), "a teardown whose close threw is pending");
            assertEquals(0, failOnClose.get());
            throwAfter.set(false);
            assertTrue(fail.retryPendingTeardown(), "the watchdog's tick hands it over again");
            assertFalse(fail.teardownPending());
            assertEquals(1, failOnClose.get(), "onClose exactly once");
            assertEquals(1, okOnClose.get(), "onClose exactly once");
            org.mockito.Mockito.verify(wsOk, org.mockito.Mockito.times(1)).close();
            assertEquals(2, failCloses.get(), "the close that threw, then the retry's");
        } finally {
            pool.shutdownNow();
        }
    }

    /**
     * The stress test's size: rounds of fail-once closes, 5 x 20,000 = 100,000 by default (overridable with
     * -Doutbound.strandStress.closes=N / -Doutbound.strandStress.rounds=R). With the hand-over's write after the
     * publish put back, single 20,000-close runs stranded between 2 and 231 on this machine, so one round alone
     * could come up empty; five make a green run mean something.
     */
    static final int STRAND_STRESS_CLOSES = Integer.getInteger("outbound.strandStress.closes", 20_000);
    static final int STRAND_STRESS_ROUNDS = Integer.getInteger("outbound.strandStress.rounds", 5);

    /** An Error thrown without building anything, as the JVM throws its own once the heap is gone. */
    private static final OutOfMemoryError PREALLOCATED_OOM = new OutOfMemoryError("Java heap space");

    /**
     * A bare session whose first close throws. Not a mock: a mock's per-call bookkeeping slows the closer down, and
     * the race this exists for is a closer that finishes before the thread that handed it the teardown moves on.
     */
    private static WebSocketSession failsFirstClose(String id, java.util.concurrent.atomic.AtomicIntegerArray closeCalls, int index) {
        return (WebSocketSession) java.lang.reflect.Proxy.newProxyInstance(OutboundChannelTest.class.getClassLoader(),
                new Class<?>[] {WebSocketSession.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "getId" -> id;
                    case "isOpen" -> Boolean.TRUE;
                    case "close" -> {
                        if (closeCalls.incrementAndGet(index) == 1) throw PREALLOCATED_OOM;
                        yield null;
                    }
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    /**
     * The reviewer's round-5 reproduction, in their style: many closes whose first session close throws, on the real
     * four-thread pool, from several enqueuers at once. Before, a closer that ran a teardown, failed and restored
     * PENDING before its hand-over's next step had that PENDING overwritten with HANDED — neither pending for the
     * watchdog nor torn down: stranded (the reviewer saw 47 of 20,000). Now none is: every one is pending after its
     * failed close, and the watchdog's retry tears each down, onClose exactly once. Five rounds of 20,000
     * ({@link #STRAND_STRESS_ROUNDS} x {@link #STRAND_STRESS_CLOSES}) on one pool; the stranded count is summed.
     */
    @Test
    void manyFailOnceClosesOnTheRealFourThreadPool_noneIsStranded() throws Exception {
        int closes = STRAND_STRESS_CLOSES, rounds = STRAND_STRESS_ROUNDS, enqueuers = 4;
        String prefix = "strand-stress-closer-";
        OutboundChannel.TeardownPool pool = new OutboundChannel.TeardownPool(OutboundChannel.CLOSER_THREADS, prefix,
                OutboundChannel.CLOSE_DEADLINE_MS, failure -> { });
        try {
            long t0 = System.nanoTime();
            int totalStranded = 0;
            List<Integer> strandedPerRound = new ArrayList<>();
            for (int round = 0; round < rounds; round++) {
                java.util.concurrent.atomic.AtomicIntegerArray closeCalls = new java.util.concurrent.atomic.AtomicIntegerArray(closes);
                java.util.concurrent.atomic.AtomicIntegerArray onCloseCalls = new java.util.concurrent.atomic.AtomicIntegerArray(closes);
                AtomicInteger tornDown = new AtomicInteger();
                OutboundChannel[] channels = new OutboundChannel[closes];
                for (int i = 0; i < closes; i++) {
                    int index = i;
                    channels[i] = new OutboundChannel(failsFirstClose("stress-" + round + "-" + i, closeCalls, i), task -> { }, pool, 1, 1 << 20, metrics, c -> {
                        onCloseCalls.incrementAndGet(index);
                        tornDown.incrementAndGet();
                    });
                }
                long failuresBefore = pool.failures();
                Thread[] threads = new Thread[enqueuers];
                AtomicReference<Throwable> thrown = new AtomicReference<>();
                for (int t = 0; t < enqueuers; t++) {
                    int first = t;
                    threads[t] = new Thread(() -> {
                        try {
                            for (int i = first; i < closes; i += enqueuers) overflow(channels[i]);
                        } catch (Throwable e) {
                            thrown.set(e);
                        }
                    }, "stress-enqueuer-" + t);
                }
                for (Thread t : threads) t.start();
                for (Thread t : threads) t.join(30_000);
                assertNull(thrown.get(), "nothing was thrown at an enqueuer: " + thrown.get());
                long deadline = System.currentTimeMillis() + 30_000;
                while ((pool.failures() - failuresBefore < closes || pool.busy() > 0 || pool.queued() > 0) && System.currentTimeMillis() < deadline) Thread.sleep(5);
                assertEquals(closes, pool.failures() - failuresBefore, "every first close ran on a closer and threw");
                assertEquals(0, tornDown.get(), "onClose follows a close that returned, never one that threw");
                // Every close has thrown and none has been retried: a teardown that is not pending now is stranded.
                boolean[] wasPending = new boolean[closes];
                int pending = 0;
                for (int i = 0; i < closes; i++) {
                    wasPending[i] = channels[i].teardownPending();
                    if (wasPending[i]) pending++;
                }
                strandedPerRound.add(closes - pending);
                totalStranded += closes - pending;
                for (int i = 0; i < closes; i++) {
                    if (wasPending[i]) assertTrue(channels[i].retryPendingTeardown(), "the watchdog's tick hands " + channels[i].socketId() + " over again");
                }
                deadline = System.currentTimeMillis() + 30_000;
                while (tornDown.get() < pending && System.currentTimeMillis() < deadline) Thread.sleep(5);
                assertEquals(pending, tornDown.get(), "the watchdog's retry tore every pending one down");
                for (int i = 0; i < closes; i++) {
                    if (!wasPending[i]) continue;                           // stranded: counted, and asserted below
                    assertEquals(1, onCloseCalls.get(i), "onClose exactly once for " + channels[i].socketId());
                    assertEquals(2, closeCalls.get(i), "the close that threw, then the retry's, for " + channels[i].socketId());
                    assertFalse(channels[i].teardownPending());
                }
            }
            System.out.println("[strand-stress] " + rounds + " x " + closes + " fail-once closes on a " + OutboundChannel.CLOSER_THREADS
                    + "-thread pool from " + enqueuers + " enqueuers: stranded " + totalStranded + " " + strandedPerRound
                    + " (" + (System.nanoTime() - t0) / 1_000_000 + " ms)");
            assertEquals(0, totalStranded, totalStranded + " of " + rounds * closes
                    + " fail-once closes were stranded, neither pending for the watchdog nor torn down; per round " + strandedPerRound);
            assertEquals(OutboundChannel.CLOSER_THREADS, liveThreads(prefix));
        } finally {
            pool.shutdownNow();
        }
    }

    private void waitForSent(int n) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 3000;
        while (sent.size() < n && System.currentTimeMillis() < deadline) {
            Thread.sleep(5);
        }
        assertTrue(sent.size() >= n, "expected >= " + n + " sent, got " + sent.size());
    }
}

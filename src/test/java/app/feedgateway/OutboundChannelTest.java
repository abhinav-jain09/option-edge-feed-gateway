package app.feedgateway;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.stubbing.Answer;
import org.springframework.web.socket.CloseStatus;
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
        // unblock any blocked writer when the channel force-closes the session (both close() and close(status))
        org.mockito.Mockito.doAnswer(inv -> { release.countDown(); return null; }).when(ws).close();
        org.mockito.Mockito.doAnswer(inv -> { release.countDown(); return null; }).when(ws).close(any(CloseStatus.class));
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
        org.mockito.Mockito.verify(ws).close(CloseStatus.SESSION_NOT_RELIABLE); // slow-client evict
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
        org.mockito.Mockito.verify(ws).close(CloseStatus.SESSION_NOT_RELIABLE);
        assertTrue(closedCb.await(2, TimeUnit.SECONDS));
    }

    private void waitForSent(int n) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 3000;
        while (sent.size() < n && System.currentTimeMillis() < deadline) {
            Thread.sleep(5);
        }
        assertTrue(sent.size() >= n, "expected >= " + n + " sent, got " + sent.size());
    }
}

package app.feedgateway;

import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Per-socket bounded, coalescing, asynchronous outbound channel (P0 — slow-client isolation).
 *
 * <p>The Kafka consumer thread only {@link #enqueue}s — it NEVER performs network I/O. A single dedicated
 * writer task (one at a time per socket, on a shared bounded pool) drains the queue and performs the
 * blocking {@code session.sendMessage}. So a slow, blocked, or adversarial client can never stall Kafka
 * polling or delay any other tenant: it only fills its OWN bounded queue and is deterministically
 * disconnected when a limit is exceeded.
 *
 * <p>Guarantees:
 * <ul>
 *   <li><b>Bounded</b>: at most {@code maxMessages} queued and {@code maxBytes} buffered;</li>
 *   <li><b>Coalescing</b>: a non-null {@code coalesceKey} replaces any still-queued message with the same
 *       key — replaceable market snapshots collapse to latest-wins instead of piling up;</li>
 *   <li><b>Write deadline</b>: a send in flight longer than the deadline is force-closed by an external
 *       watchdog ({@link #enforceWriteDeadline}), freeing the writer-pool thread;</li>
 *   <li><b>Deterministic disconnection</b>: breaching any limit (or a write error/timeout) marks the
 *       channel closed at once — every later enqueue is refused and the queue is dropped — and then closes
 *       the socket and invokes {@code onClose} exactly once, OFF the calling thread (see below);</li>
 *   <li><b>Metrics</b>: every enqueue/coalesce/disconnect/error is reported.</li>
 * </ul>
 *
 * <p><b>Teardown never runs on the thread that detected the breach</b> (ES footprint strike re-review #1).
 * Closing a WebSocket session can block behind a write still in flight on it — the very write that made the
 * client slow. The thread that sees an overflow is an ENQUEUER: a Kafka consumer, or the strike fold's
 * drainer, which holds that stream's delivery order while it fans a frame out to every socket; the thread
 * that sees a deadline is the write watchdog. Blocking either on one slow socket's close stalled both strike
 * consumers and every healthy socket behind it. So {@link #close} only marks, drops and counts, and HANDS the
 * teardown — the session close, then {@code onClose} — to the {@code closers} executor.
 *
 * <p><b>Teardowns share one bounded pool</b> (strike re-review round 3). The default closers are
 * {@link #TEARDOWN}: {@link #CLOSER_THREADS} daemon threads, started once and shared by every channel. A
 * thread per close put no limit on how many existed at once — a mass overflow started one per channel, and a
 * {@code Thread.start()} that could not get a native thread threw {@code OutOfMemoryError} on the drainer,
 * aborting that frame's fan-out and leaving the channel closed with no teardown. Handing a teardown over can
 * still fail with an injected executor; that failure is never thrown at the enqueuer and never runs the
 * teardown inline: the teardown stays PENDING and the write watchdog hands it over again
 * ({@link #retryPendingTeardown}). Until the teardown runs the channel stays registered, closed, so nothing
 * can fall back to writing to its session directly.
 */
final class OutboundChannel {

    /**
     * How many teardowns run at once, process-wide. A closer thread does no work of its own: it waits for one
     * session close, then runs the detach callback. A close that returns promptly holds it for microseconds, so
     * a handful of threads clears a mass overflow; a close that blocks holds ONE closer thread and nothing else.
     * Four lets a few stuck closes leave the rest moving, and caps what a mass overflow can cost at four
     * threads, started once, and no more.
     */
    static final int CLOSER_THREADS = 4;

    /**
     * How long one session close may hold a closer thread before the write watchdog interrupts it
     * ({@link TeardownPool#interruptOverdue}). The container already bounds a close: Tomcat's blocking-send
     * timeout ({@code org.apache.tomcat.websocket.BLOCKING_SEND_TIMEOUT}, 20 s by default) ends the close frame's
     * send even when the write it queues behind never completes. This deadline sits above that, as the backstop
     * for a close that does not honour it.
     */
    static final long CLOSE_DEADLINE_MS = 30_000L;

    /**
     * The teardown executor every channel uses unless one is injected. Its threads start when this class
     * initialises — at the first channel's construction, never on a thread that is closing one.
     */
    static final TeardownPool TEARDOWN = new TeardownPool(CLOSER_THREADS, "options-edge-ws-closer-", CLOSE_DEADLINE_MS);

    /** Slow-client / throughput metrics sink (implemented by the gateway over atomic counters). */
    interface Metrics {
        void enqueued(int bytes);
        void coalesced();
        void sent(int bytes);
        void disconnectedSlow();
        void writeError();
        void droppedOnClose(int messages);
    }

    private enum CloseReason { OVERFLOW, WRITE_ERROR }

    private record Pending(String envelope, int bytes) {
    }

    // The teardown's hand-over to the closers. NONE while the channel is open; PENDING once it is closed and the
    // closers have not accepted its teardown; HANDING while one thread is offering it; HANDED once accepted. Only
    // the PENDING -> HANDING transition may offer it, so the close and the watchdog's retry never hand it twice.
    private static final int TEARDOWN_NONE = 0, TEARDOWN_PENDING = 1, TEARDOWN_HANDING = 2, TEARDOWN_HANDED = 3;

    private final String socketId;
    private final WebSocketSession session;
    private final Executor writers;
    private final Executor closers;
    private final int maxMessages;
    private final long maxBytes;
    private final Metrics metrics;
    private final Consumer<OutboundChannel> onClose;

    private final Object lock = new Object();
    private final LinkedHashMap<String, Pending> queue = new LinkedHashMap<>();
    private long queuedBytes;
    private long seq;
    private boolean draining;
    private long peakDepth;
    private volatile long sendStartedAtMs; // 0 = no send in flight; else the wall-clock the write began
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final AtomicInteger teardownState = new AtomicInteger(TEARDOWN_NONE);
    private final AtomicBoolean tornDown = new AtomicBoolean(false);

    OutboundChannel(WebSocketSession session, Executor writers, int maxMessages, long maxBytes,
                    Metrics metrics, Consumer<OutboundChannel> onClose) {
        this(session, writers, TEARDOWN, maxMessages, maxBytes, metrics, onClose);
    }

    /** {@code closers} runs each teardown (session close, then {@code onClose}); it must not be the caller's thread in production. */
    OutboundChannel(WebSocketSession session, Executor writers, Executor closers, int maxMessages, long maxBytes,
                    Metrics metrics, Consumer<OutboundChannel> onClose) {
        this.socketId = session.getId();
        this.session = session;
        this.writers = writers;
        this.closers = closers;
        this.maxMessages = Math.max(1, maxMessages);
        this.maxBytes = Math.max(1L, maxBytes);
        this.metrics = metrics;
        this.onClose = onClose;
    }

    String socketId() {
        return socketId;
    }

    WebSocketSession session() {
        return session;
    }

    int queueDepth() {
        synchronized (lock) {
            return queue.size();
        }
    }

    long peakDepth() {
        synchronized (lock) {
            return peakDepth;
        }
    }

    /**
     * Enqueue an envelope for asynchronous delivery. Never blocks on network I/O. {@code coalesceKey != null}
     * replaces any pending message with the same key (latest-wins for replaceable snapshots). Returns
     * {@code false} if the channel is closed or the client was disconnected for exceeding limits.
     */
    boolean enqueue(String envelope, String coalesceKey) {
        if (closed.get() || envelope == null) {
            return false;
        }
        int bytes = envelope.length(); // chars ≈ bytes for the ASCII JSON envelopes; a bound, not exact
        boolean overflow;
        boolean startDrain = false;
        synchronized (lock) {
            if (closed.get()) {
                return false;
            }
            // Coalescable messages share a key (latest-wins, position preserved); non-coalescable messages
            // get a unique key so every one is delivered (replay records, control/lifecycle events).
            String key = (coalesceKey != null) ? ("c " + coalesceKey) : ("u " + (seq++));
            Pending prev = queue.put(key, new Pending(envelope, bytes));
            if (prev != null) {
                queuedBytes -= prev.bytes();
                metrics.coalesced();
            }
            queuedBytes += bytes;
            metrics.enqueued(bytes);
            if (queue.size() > peakDepth) {
                peakDepth = queue.size();
            }
            overflow = queue.size() > maxMessages || queuedBytes > maxBytes;
            if (!overflow && !draining) {
                draining = true;
                startDrain = true;
            }
        }
        if (overflow) {
            // The client cannot keep up even after coalescing — disconnect it deterministically. The close
            // itself happens on the closers executor: this thread is an enqueuer, and must not wait on it.
            close(CloseReason.OVERFLOW);
            return false;
        }
        if (startDrain) {
            writers.execute(this::drain);
        }
        return true;
    }

    /** Drains the queue to the socket. Runs on a writer-pool thread; only ONE drain runs per socket. */
    private void drain() {
        while (true) {
            Pending next;
            synchronized (lock) {
                if (closed.get()) {
                    draining = false;
                    return;
                }
                Iterator<Map.Entry<String, Pending>> it = queue.entrySet().iterator();
                if (!it.hasNext()) {
                    draining = false; // go idle; the next enqueue re-arms a drain
                    return;
                }
                next = it.next().getValue();
                it.remove();
                queuedBytes -= next.bytes();
            }
            if (!session.isOpen()) {
                close(CloseReason.WRITE_ERROR);
                return;
            }
            sendStartedAtMs = System.currentTimeMillis(); // arm the watchdog deadline
            try {
                // Blocking write — but NEVER on the Kafka thread, so a slow socket cannot stall polling.
                // If it exceeds the write deadline the watchdog force-closes the session, unblocking us.
                session.sendMessage(new TextMessage(next.envelope()));
                metrics.sent(next.bytes());
            } catch (IOException | RuntimeException e) {
                close(CloseReason.WRITE_ERROR);
                return;
            } finally {
                sendStartedAtMs = 0;
            }
        }
    }

    /**
     * Watchdog hook (called periodically off the writer threads): if a send has been in flight longer than
     * {@code deadlineMs}, force-close the socket — this unblocks the stuck writer and frees its pool thread,
     * so a few stuck clients cannot starve everyone else. Returns true if it disconnected the client (the
     * session close itself runs on the closers executor, so a close that blocks cannot stall the watchdog).
     */
    boolean enforceWriteDeadline(long nowMs, long deadlineMs) {
        long started = sendStartedAtMs;
        if (started != 0L && nowMs - started > deadlineMs && !closed.get()) {
            return close(CloseReason.WRITE_ERROR);
        }
        return false;
    }

    private boolean close(CloseReason reason) {
        if (!closed.compareAndSet(false, true)) {
            return false;
        }
        int dropped;
        synchronized (lock) {
            dropped = queue.size();
            queue.clear();
            queuedBytes = 0;
        }
        if (reason == CloseReason.OVERFLOW) {
            metrics.disconnectedSlow();
        } else {
            metrics.writeError();
        }
        if (dropped > 0) {
            metrics.droppedOnClose(dropped);
        }
        // Session close first, then onClose: until the close returns the channel stays registered (closed),
        // so a broadcast finds it and is refused, rather than finding no channel and writing to the session.
        teardownState.set(TEARDOWN_PENDING);
        handOverTeardown();
        return true;
    }

    /**
     * Offer this channel's teardown to the closers, once. A failure to hand it over — an executor that rejects,
     * or one that throws an {@code Error} (a thread-per-task executor whose {@code Thread.start()} cannot get a
     * native thread) — is NOT thrown at the caller: the caller is an enqueuer in the middle of a fan-out, and a
     * throw would abort that frame's delivery to every socket after this one. Nor is the teardown run here. It
     * stays PENDING — the channel registered and closed — and the write watchdog offers it again
     * ({@link #retryPendingTeardown}). Returns true if the closers accepted it now.
     */
    private boolean handOverTeardown() {
        if (!teardownState.compareAndSet(TEARDOWN_PENDING, TEARDOWN_HANDING)) {
            return false;
        }
        try {
            closers.execute(this::teardown);
            teardownState.set(TEARDOWN_HANDED);
            return true;
        } catch (RuntimeException | Error handOverFailed) {
            teardownState.set(TEARDOWN_PENDING);
            System.out.println("Feed gateway outbound teardown of socket " + socketId + " could not be scheduled ("
                    + handOverFailed.getClass().getSimpleName() + ": " + handOverFailed.getMessage()
                    + "); it stays pending for the write watchdog to retry");
            return false;
        }
    }

    /**
     * Watchdog hook: hand over a teardown an earlier attempt could not. A closed channel stays registered until
     * its teardown runs, so the watchdog's sweep over the registered channels always finds it. Returns true if
     * the closers accepted it on this call; false if there was nothing pending or they refused again.
     */
    boolean retryPendingTeardown() {
        return teardownState.get() == TEARDOWN_PENDING && handOverTeardown();
    }

    /** True while this channel is closed and the closers have not yet accepted its teardown. */
    boolean teardownPending() {
        return teardownState.get() == TEARDOWN_PENDING;
    }

    /** The session close, then {@code onClose} — at most once, on a closer thread. */
    private void teardown() {
        if (!tornDown.compareAndSet(false, true)) {
            return;
        }
        closeSessionQuietly();
        try {
            onClose.accept(this);
        } catch (RuntimeException e) {
            System.out.println("Feed gateway outbound teardown of socket " + socketId + " failed in onClose: "
                    + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    /** Quiet teardown on a NORMAL disconnect/shutdown — drops the queue, fires no slow-client signal. */
    void shutdown() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        synchronized (lock) {
            queue.clear();
            queuedBytes = 0;
        }
    }

    private void closeSessionQuietly() {
        try {
            if (session.isOpen()) {
                session.close();
            }
        } catch (IOException | RuntimeException ignored) {
            // already marked closed and detached from routing
        }
    }

    boolean isClosed() {
        return closed.get();
    }

    /**
     * A fixed set of pre-started daemon threads taking teardowns from one shared queue.
     *
     * <p><b>Bounds.</b> Threads: exactly {@code threads}, started when the pool is built and never more —
     * {@link #execute} only queues, so no thread is ever created on a caller. Queue: unbounded as a structure,
     * bounded by use — a channel hands its teardown over at most once (its close is exactly-once, and a failed
     * hand-over is offered again only while nothing was queued), so it holds at most one entry per channel that
     * is closed and not yet torn down: never more than the sockets the gateway holds. {@link #execute} therefore
     * never blocks, never runs the task on the caller, and rejects only after {@link #shutdownNow} (tests).
     *
     * <p><b>When every closer thread is stuck.</b> Later teardowns wait in the queue; nothing else waits. Their
     * channels are already closed — every enqueue refused, queue dropped, no write ever again — so what waits is
     * only each one's session close and detach callback: the channel stays registered, closed, until a thread
     * frees. A thread frees when its close returns, which the container's blocking-send timeout bounds, or when
     * the write watchdog interrupts a close that has held it past {@code closeDeadlineMs}
     * ({@link #interruptOverdue}); that teardown then finishes — {@code onClose} still runs, once, after the
     * close attempt — and the thread takes the next. A close that ignores both its own timeout and the interrupt
     * keeps its thread for good and the pool goes on with one fewer; the queued teardowns stop only if every
     * thread were lost that way, and even then no sender, writer or watchdog waits on them.
     */
    static final class TeardownPool implements Executor {
        private final LinkedBlockingQueue<Runnable> queue = new LinkedBlockingQueue<>();
        private final Worker[] workers;
        private final long closeDeadlineMs;
        private volatile boolean stopped;

        TeardownPool(int threads, String namePrefix, long closeDeadlineMs) {
            this.closeDeadlineMs = closeDeadlineMs;
            this.workers = new Worker[Math.max(1, threads)];
            for (int i = 0; i < workers.length; i++) {
                Worker w = new Worker();
                Thread t = new Thread(w::run, namePrefix + (i + 1));
                t.setDaemon(true);
                w.thread = t;
                workers[i] = w;
                t.start();
            }
        }

        /** Queues the teardown for the next free closer thread: never blocks, never runs it here, never starts a thread. */
        @Override
        public void execute(Runnable teardown) {
            if (stopped) {
                throw new RejectedExecutionException("teardown pool stopped");
            }
            queue.add(teardown);
        }

        /**
         * Write-watchdog hook: interrupt every close that has held its closer thread longer than the close
         * deadline — once per close, and never the close that thread takes next. Returns how many it interrupted.
         */
        int interruptOverdue(long nowMs) {
            int n = 0;
            for (Worker w : workers) {
                if (w.interruptIfOverdue(nowMs)) {
                    n++;
                }
            }
            return n;
        }

        /** How many closer threads are inside a teardown right now. */
        int busy() {
            int n = 0;
            for (Worker w : workers) {
                if (w.busy()) {
                    n++;
                }
            }
            return n;
        }

        /** How many teardowns wait for a free closer thread. */
        int queued() {
            return queue.size();
        }

        /** Stops the threads once they finish what they are doing (tests: a private pool must not outlive them). */
        void shutdownNow() {
            stopped = true;
            for (Worker w : workers) {
                w.thread.interrupt();
            }
        }

        private final class Worker {
            private Thread thread;
            private Runnable current; // guarded by this
            private long since;       // guarded by this
            private boolean interrupted; // guarded by this: the watchdog interrupts one close at most once

            void run() {
                while (!stopped) {
                    Runnable task;
                    try {
                        task = queue.take();
                    } catch (InterruptedException stray) {
                        continue;                          // shutdownNow, or an interrupt for a close already finished
                    }
                    synchronized (this) {
                        current = task;
                        since = System.currentTimeMillis();
                        interrupted = false;
                    }
                    try {
                        task.run();
                    } catch (Throwable t) {
                        // A teardown must never take its closer thread down with it.
                        System.out.println("Feed gateway outbound teardown failed: " + t.getClass().getSimpleName() + ": " + t.getMessage());
                    } finally {
                        synchronized (this) {
                            current = null;
                        }
                        Thread.interrupted();               // an interrupt meant for the close just finished is not the next one's
                    }
                }
            }

            synchronized boolean interruptIfOverdue(long nowMs) {
                if (current == null || interrupted || nowMs - since <= closeDeadlineMs) {
                    return false;
                }
                interrupted = true;
                thread.interrupt();
                return true;
            }

            synchronized boolean busy() {
                return current != null;
            }
        }
    }
}

package app.feedgateway;

import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
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
 *   <li><b>Deterministic disconnection</b>: breaching any limit (or a write error/timeout) closes the
 *       socket and invokes {@code onClose} exactly once;</li>
 *   <li><b>Metrics</b>: every enqueue/coalesce/disconnect/error is reported.</li>
 * </ul>
 */
final class OutboundChannel {

    /** Slow-client / throughput metrics sink (implemented by the gateway over atomic counters). */
    interface Metrics {
        void enqueued(int bytes);
        void coalesced();
        void sent(int bytes);
        void disconnectedSlow();
        void writeError();
        void droppedOnClose(int messages);
    }

    private enum CloseReason { OVERFLOW, WRITE_ERROR, SEND_TIMEOUT, IDLE }

    /** Throttle interval for the "slow send force-closed" WARN log; one line per socket per 30s. */
    private static final long TIMEOUT_WARN_THROTTLE_MS = 30_000L;

    private record Pending(String envelope, int bytes) {
    }

    private final String socketId;
    private final WebSocketSession session;
    private final Executor writers;
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
    // Wall-clock of the last observed I/O in EITHER direction: successful send OR inbound frame from the
    // client. Seeded to construction time so a brand-new socket does not fire the idle sweep immediately.
    private final AtomicLong lastActivityAtMs = new AtomicLong(System.currentTimeMillis());
    // Wall-clock of the last "send timeout" WARN emitted for this channel; drives per-socket throttling so
    // a wedged send does not spam the log on every watchdog tick.
    private final AtomicLong lastTimeoutWarnAtMs = new AtomicLong(0L);
    private final AtomicBoolean closed = new AtomicBoolean(false);

    OutboundChannel(WebSocketSession session, Executor writers, int maxMessages, long maxBytes,
                    Metrics metrics, Consumer<OutboundChannel> onClose) {
        this.socketId = session.getId();
        this.session = session;
        this.writers = writers;
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
            lastActivityAtMs.set(System.currentTimeMillis()); // outbound activity — keep the idle sweep quiet
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
            // The client cannot keep up even after coalescing — disconnect it deterministically.
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
                lastActivityAtMs.set(System.currentTimeMillis()); // successful outbound I/O
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
     * so a few stuck clients cannot starve everyone else. Returns true if it disconnected the client. The
     * close uses {@link CloseStatus#SESSION_NOT_RELIABLE} so the browser can distinguish a slow-client evict
     * from a normal disconnect, and emits ONE structured WARN per socket per 30s (throttled) — enough to
     * see it in prod without flooding the log if many sockets wedge at once.
     */
    boolean enforceWriteDeadline(long nowMs, long deadlineMs) {
        long started = sendStartedAtMs;
        if (started != 0L && nowMs - started > deadlineMs && !closed.get()) {
            long elapsed = nowMs - started;
            long lastWarn = lastTimeoutWarnAtMs.get();
            if (nowMs - lastWarn >= TIMEOUT_WARN_THROTTLE_MS
                    && lastTimeoutWarnAtMs.compareAndSet(lastWarn, nowMs)) {
                System.out.println("WARN ws-send-timeout socketId=" + socketId + " elapsedMs=" + elapsed
                        + " deadlineMs=" + deadlineMs + " action=close-session-not-reliable");
            }
            return close(CloseReason.SEND_TIMEOUT);
        }
        return false;
    }

    /**
     * Note an inbound frame from the client. Called by the WS handler on every message received, so a
     * chatty control channel (ping/pong, resubscribes, replay control) keeps the socket out of the idle
     * sweep even when no outbound events are currently being written for it.
     */
    void noteInboundActivity() {
        lastActivityAtMs.set(System.currentTimeMillis());
    }

    /**
     * Idle-sweep hook: if the socket has had no I/O in EITHER direction for {@code idleMs}, close it. This
     * catches TCP half-open connections (laptop lid closed, dead NAT, dropped WiFi) that Spring's raw
     * WebSocket does NOT detect on its own — the send never fails because no bytes need to leave, so
     * {@link #enforceWriteDeadline} never fires. Returns true if it disconnected the client.
     */
    boolean enforceIdleTimeout(long nowMs, long idleMs) {
        if (closed.get()) {
            return false;
        }
        long last = lastActivityAtMs.get();
        if (nowMs - last > idleMs) {
            System.out.println("WARN ws-idle-timeout socketId=" + socketId
                    + " idleMs=" + (nowMs - last) + " thresholdMs=" + idleMs + " action=close");
            return close(CloseReason.IDLE);
        }
        return false;
    }

    long lastActivityAtMs() {
        return lastActivityAtMs.get();
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
            // SEND_TIMEOUT / IDLE / WRITE_ERROR all bucket into writeError for now — they all indicate the
            // client is not reliably draining, and the log line above carries the specific reason.
            metrics.writeError();
        }
        if (dropped > 0) {
            metrics.droppedOnClose(dropped);
        }
        closeSessionQuietly(closeStatusFor(reason));
        onClose.accept(this);
        return true;
    }

    private static CloseStatus closeStatusFor(CloseReason reason) {
        // A slow/idle/timeout eviction is not an application protocol violation; the closest existing
        // CloseStatus is SESSION_NOT_RELIABLE, which the task requested explicitly.
        return switch (reason) {
            case SEND_TIMEOUT, IDLE, OVERFLOW, WRITE_ERROR -> CloseStatus.SESSION_NOT_RELIABLE;
        };
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

    private void closeSessionQuietly(CloseStatus status) {
        try {
            if (session.isOpen()) {
                if (status != null) {
                    session.close(status);
                } else {
                    session.close();
                }
            }
        } catch (IOException | RuntimeException ignored) {
            // already marked closed and detached from routing
        }
    }

    boolean isClosed() {
        return closed.get();
    }
}

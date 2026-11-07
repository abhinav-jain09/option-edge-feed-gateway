package app.feedgateway;

import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.CloseStatus;
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
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.BooleanSupplier;

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
 *
 * <p><b>A closer thread outlives every failure</b> (strike re-review round 4). Nothing replaces a closer thread,
 * so nothing a teardown throws — and nothing reporting that failure throws: under heap exhaustion building the
 * log line can itself throw {@code OutOfMemoryError} — may end one; the failure is counted before it is reported.
 * A teardown whose session close throws has not reached {@code onClose}: it is pending again, and the write
 * watchdog hands it over again, so an accepted teardown is never stranded.
 *
 * <p><b>A hand-over never overwrites a closer's pending restore</b> (strike re-review round 5). A closer can run a
 * teardown the instant it is published, fail, and put it back to PENDING before the thread that handed it over
 * takes its next step. So the hand-over claims the teardown (PENDING -> HANDED) before publishing it and writes
 * nothing after; every state write two threads can race is a compare-and-set from the state it leaves.
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

    private enum CloseReason { OVERFLOW, WRITE_ERROR, AUTH_REVOKED }

    private static final class ChartCopy {
        final ChartOutboundBudget.Lease lease;
        final AtomicBoolean released = new AtomicBoolean();
        boolean counted;
        ChartCopy(ChartOutboundBudget.Lease lease) { this.lease = lease; }
    }
    private record Pending(WebSocketMessage<?> message,int bytes,BooleanSupplier authorized,RithmicHistoryInvalidation invalidation,ChartCopy chart) {
        Pending(WebSocketMessage<?> message,int bytes,BooleanSupplier authorized,RithmicHistoryInvalidation invalidation) {
            this(message,bytes,authorized,invalidation,null);
        }
    }
    private record Insert(boolean overflow,boolean startDrain) {}
    enum HistoryAdmission { ENQUEUED, BUSY, CAPACITY, CLOSED }
    static final int HISTORY_CONTROL_RESERVE=4096;

    // The teardown's lifecycle (strike re-review round 5). NONE while the channel is open. PENDING once it is closed
    // and no closer holds its teardown. HANDED from the moment one thread claims it for a hand-over — PENDING ->
    // HANDED, BEFORE the teardown is published to the closers — until a closer's session close either returns
    // (DONE, terminal) or throws (back to PENDING, for the write watchdog's retry). Only PENDING -> HANDED may offer
    // it, so the close and the watchdog's retry never hand it over twice.
    //
    // Every write that another thread can race is a compare-and-set from the state it leaves, and no thread writes
    // the state after publishing the teardown: once published, a closer may already have run it, failed and put it
    // back to PENDING, and a later write would overwrite that. The one plain write is DONE — terminal, written only
    // by the run whose close returned — and since every other write is a CAS from PENDING or HANDED, none leaves it.
    private static final int TEARDOWN_NONE = 0, TEARDOWN_PENDING = 1, TEARDOWN_HANDED = 2, TEARDOWN_DONE = 3;

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
    private long chartOwnedBytes;
    private int chartOwnedFrames;
    private long seq;
    private boolean draining;
    private long peakDepth;
    private volatile long sendStartedAtMs; // 0 = no send in flight; else the wall-clock the write began
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private volatile CloseStatus authorizationClose;
    private final AtomicInteger teardownState = new AtomicInteger(TEARDOWN_NONE);
    private final AtomicBoolean tornDown = new AtomicBoolean(false);
    /**
     * Run each time the writer drains this queue EMPTY — the one moment a producer that paces itself
     * against this socket's bounds knows there is room again. Set only while such a producer is active
     * (the vol-premium session replay); null otherwise, which costs one volatile read per idle.
     */
    private final OutboundIdleDispatcher idleDispatcher;
    private final Map<Object, IdleCompat> idleCompat = new LinkedHashMap<>();
    private final Object legacyIdleOwner = new Object();
    private static final class IdleCompat { OutboundIdleDispatcher.Registration registration; }

    OutboundChannel(WebSocketSession session, Executor writers, int maxMessages, long maxBytes,
                    Metrics metrics, Consumer<OutboundChannel> onClose) {
        this(session, writers, TEARDOWN, maxMessages, maxBytes, metrics, onClose);
    }

    /** {@code closers} runs each teardown (session close, then {@code onClose}); it must not be the caller's thread in production. */
    OutboundChannel(WebSocketSession session, Executor writers, Executor closers, int maxMessages, long maxBytes,
                    Metrics metrics, Consumer<OutboundChannel> onClose) {
        this(session,writers,closers,writers,maxMessages,maxBytes,metrics,onClose);
    }

    OutboundChannel(WebSocketSession session, Executor writers, Executor closers, Executor idlers, int maxMessages, long maxBytes,
                    Metrics metrics, Consumer<OutboundChannel> onClose) {
        this.socketId = session.getId();
        this.session = session;
        this.writers = writers;
        this.closers = closers;
        this.maxMessages = Math.max(1, maxMessages);
        this.maxBytes = Math.max(1L, maxBytes);
        this.metrics = metrics;
        this.onClose = onClose;
        this.idleDispatcher = new OutboundIdleDispatcher(idlers);
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

    /** Exact UTF-8 text plus binary bytes currently queued. */
    long queuedBytes() {
        synchronized (lock) {
            return queuedBytes;
        }
    }

    /**
     * Install (or clear, with null) the hook run each time the writer drains the queue empty. It runs on
     * the writer thread, OUTSIDE this channel's lock, so it may enqueue again — a producer that paces a
     * large replay against this socket's bounds refills here instead of flooding the queue at once.
     */
    void onIdle(Runnable hook) {
        onIdle(legacyIdleOwner,hook);
    }

    /** Compatibility owners cannot clear another producer's hook. New producers retain their registration handle. */
    boolean onIdle(Object owner,Runnable hook) {
        synchronized(lock){
            if(closed.get())return false;
            if(hook==null){IdleCompat old=idleCompat.remove(owner);if(old!=null)old.registration.close();return true;}
            IdleCompat entry=new IdleCompat();
            entry.registration=idleDispatcher.register(owner,()->{if(!closed.get())hook.run();},()->{synchronized(lock){idleCompat.remove(owner,entry);}});
            if(entry.registration==null)return false;
            idleCompat.put(owner,entry);return true;
        }
    }
    OutboundIdleDispatcher.Registration registerIdleOwner(Object owner,Runnable hook,Runnable failed) {
        if(closed.get())return null;
        return idleDispatcher.register(owner,()->{if(!closed.get())hook.run();},failed);
    }
    void retryIdleDispatch(){idleDispatcher.retry();}

    /**
     * Enqueue an envelope for asynchronous delivery. Never blocks on network I/O. {@code coalesceKey != null}
     * replaces any pending message with the same key (latest-wins for replaceable snapshots). Returns
     * {@code false} if the channel is closed or the client was disconnected for exceeding limits.
     */
    boolean enqueue(String envelope, String coalesceKey) {
        if (closed.get() || envelope == null) {
            return false;
        }
        return enqueueText(envelope,coalesceKey,null);
    }

    void revokeAuthorization(){close(CloseReason.AUTH_REVOKED);}

    boolean enqueueAuthorizedText(String envelope,String coalesceKey,BooleanSupplier authorized){
        if(closed.get()||envelope==null)return false;
        if(!authorized(authorized)){close(CloseReason.AUTH_REVOKED);return false;}
        return enqueueText(envelope,coalesceKey,authorized);
    }

    private boolean enqueueText(String envelope,String coalesceKey,BooleanSupplier authorized){
        envelope=utf8Safe(envelope);TextMessage message=new TextMessage(envelope);int wireBytes=utf8Length(envelope);
        Insert inserted;
        synchronized (lock) {
            if (closed.get()) {
                return false;
            }
            inserted=insertLocked(new Pending(message,wireBytes,authorized,null),coalesceKey);
        }
        return finishInsert(inserted);
    }

    /** Preserve Java's former malformed-surrogate replacement without copying valid shared text. */
    static String utf8Safe(String text){
        StringBuilder out=null;for(int i=0;i<text.length();i++){char c=text.charAt(i);
            if(Character.isHighSurrogate(c)&&i+1<text.length()&&Character.isLowSurrogate(text.charAt(i+1))){if(out!=null){out.append(c);out.append(text.charAt(i+1));}i++;continue;}
            if(Character.isSurrogate(c)){if(out==null){out=new StringBuilder(text.length());out.append(text,0,i);}out.append('?');}
            else if(out!=null)out.append(c);
        }return out==null?text:out.toString();
    }

    /** Exact StandardCharsets.UTF_8 length without allocating/decoding per-recipient byte copies. */
    static int utf8Length(String text){
        long count=0;for(int i=0;i<text.length();i++){char c=text.charAt(i);
            if(c<128)count++;else if(c<2048)count+=2;
            else if(Character.isHighSurrogate(c)&&i+1<text.length()&&Character.isLowSurrogate(text.charAt(i+1))){count+=4;i++;}
            else count+=Character.isSurrogate(c)?1:3;
            if(count>Integer.MAX_VALUE)return Integer.MAX_VALUE;
        }return (int)count;
    }

    private Insert insertLocked(Pending pending,String coalesceKey){
        String key=coalesceKey!=null?"c "+coalesceKey:"u "+seq++;
        Pending previous=queue.put(key,pending);if(previous!=null){queuedBytes-=previous.bytes();metrics.coalesced();}
        queuedBytes+=pending.bytes();metrics.enqueued(pending.bytes());peakDepth=Math.max(peakDepth,queue.size());
        boolean overflow=queue.size()>maxMessages||queuedBytes>maxBytes,start=!overflow&&!draining;
        if(start)draining=true;return new Insert(overflow,start);
    }

    private boolean finishInsert(Insert inserted){
        if (inserted.overflow()) {
            // The client cannot keep up even after coalescing — disconnect it deterministically. The close
            // itself happens on the closers executor: this thread is an enqueuer, and must not wait on it.
            close(CloseReason.OVERFLOW);
            return false;
        }
        if (inserted.startDrain()) {
            try{writers.execute(this::drain);}catch(RuntimeException|Error rejected){close(CloseReason.WRITE_ERROR);return false;}
        }
        return true;
    }

    private static boolean authorized(BooleanSupplier check){
        try{return check!=null&&check.getAsBoolean();}catch(RuntimeException|Error unavailable){return false;}
    }

    /** Reserve one small control plus4KiB before making the channel-owned binary copy. */
    HistoryAdmission tryEnqueueHistoryBinary(byte[] payload,BooleanSupplier authorized){
        if(closed.get())return HistoryAdmission.CLOSED;
        if(payload==null||payload.length==0||payload.length>Math.min(524288L,maxBytes/4))return HistoryAdmission.CAPACITY;
        if(!authorized(authorized)){close(CloseReason.AUTH_REVOKED);return HistoryAdmission.CLOSED;}
        Insert inserted=null;boolean saturated=false;
        synchronized(lock){
            if(closed.get())return HistoryAdmission.CLOSED;
            if(queue.size()+1>maxMessages||queuedBytes+HISTORY_CONTROL_RESERVE>maxBytes)saturated=true;
            else if(queue.size()+2>maxMessages||queuedBytes+payload.length+HISTORY_CONTROL_RESERVE>maxBytes)return HistoryAdmission.BUSY;
            else{
                BinaryMessage message=new BinaryMessage(payload.clone());
                inserted=insertLocked(new Pending(message,payload.length,authorized,null),null);
            }
        }
        if(saturated){close(CloseReason.OVERFLOW);return HistoryAdmission.CLOSED;}
        return finishInsert(inserted)?HistoryAdmission.ENQUEUED:HistoryAdmission.CLOSED;
    }

    /** Atomic nonclosing chart admission. Other streams keep their existing overflow policy. */
    HistoryAdmission tryEnqueueChartBinary(byte[] payload, BooleanSupplier authorized, ChartOutboundBudget.Owner owner) {
        if(closed.get())return HistoryAdmission.CLOSED;
        if(payload==null||payload.length==0||payload.length>Math.min(65_536L,maxBytes/4)||owner==null)return HistoryAdmission.CAPACITY;
        if(!authorized(authorized)){close(CloseReason.AUTH_REVOKED);return HistoryAdmission.CLOSED;}
        synchronized(lock){if(closed.get())return HistoryAdmission.CLOSED;if(!chartRoom(payload.length))return HistoryAdmission.BUSY;}
        ChartOutboundBudget.Lease lease=owner.tryAcquire(payload.length);
        if(lease==null)return HistoryAdmission.BUSY;
        ChartCopy copy=null;Pending pending=null;boolean retained=false;
        try {
            copy=new ChartCopy(lease);
            pending=new Pending(new BinaryMessage(payload.clone()),payload.length,authorized,null,copy);
            Insert inserted;
            synchronized(lock){
                if(closed.get())return HistoryAdmission.CLOSED;
                if(!chartRoom(payload.length))return HistoryAdmission.BUSY;
                chartOwnedBytes+=payload.length;chartOwnedFrames++;copy.counted=true;
                inserted=insertLocked(pending,null);retained=true;
            }
            return finishInsert(inserted)?HistoryAdmission.ENQUEUED:HistoryAdmission.CLOSED;
        } catch(RuntimeException|Error failed) {
            if(pending!=null)synchronized(lock){retained=queue.containsValue(pending);}
            close(CloseReason.WRITE_ERROR);
            return HistoryAdmission.CLOSED;
        } finally {
            if(!retained){if(copy==null)lease.close();else releaseChart(copy,payload.length);}
        }
    }

    /** Caller holds lock; includes any chart copy already handed to the blocking writer. */
    private boolean chartRoom(int bytes) {
        return chartOwnedFrames<ChartOutboundBudget.OWNER_FRAMES && chartOwnedBytes+bytes<=ChartOutboundBudget.OWNER_BYTES
                && queue.size()+2<=maxMessages && queuedBytes+bytes+HISTORY_CONTROL_RESERVE<=maxBytes;
    }
    private void releaseChart(ChartCopy copy,int bytes) {
        if(copy==null||!copy.released.compareAndSet(false,true))return;
        synchronized(lock){if(copy.counted){chartOwnedBytes-=bytes;chartOwnedFrames--;copy.counted=false;}}
        copy.lease.close();
    }

    boolean enqueueHistoryInvalidation(RithmicHistoryInvalidation value,BooleanSupplier authorized){
        if(closed.get()||value==null)return false;
        if(!authorized(authorized)){close(CloseReason.AUTH_REVOKED);return false;}
        Insert inserted;
        synchronized(lock){
            if(closed.get())return false;
            Pending previous=queue.get("c "+value.key());
            if(previous!=null&&previous.invalidation()!=null)value=previous.invalidation().merge(value);
            String envelope=utf8Safe(value.envelope());TextMessage message=new TextMessage(envelope);
            inserted=insertLocked(new Pending(message,utf8Length(envelope),authorized,value),value.key());
        }
        return finishInsert(inserted);
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
                    break;            // ...and tell a pacing producer, outside the lock (below)
                }
                next = it.next().getValue();
                it.remove();
                queuedBytes -= next.bytes();
            }
            sendStartedAtMs = System.currentTimeMillis(); // arm the watchdog deadline
            try {
                if(closed.get())return;
                if(!session.isOpen()){close(CloseReason.WRITE_ERROR);return;}
                if(next.authorized()!=null&&!authorized(next.authorized())){close(CloseReason.AUTH_REVOKED);return;}
                // Blocking write — but NEVER on the Kafka thread, so a slow socket cannot stall polling.
                // If it exceeds the write deadline the watchdog force-closes the session, unblocking us.
                session.sendMessage(next.message());
                metrics.sent(next.bytes());
            } catch (IOException | RuntimeException e) {
                close(CloseReason.WRITE_ERROR);
                return;
            } finally {
                sendStartedAtMs = 0;
                releaseChart(next.chart(),next.bytes());
            }
        }
        // Idle, and outside the lock: the hook may enqueue (re-arming a drain) without deadlocking on it.
        // A closed channel never calls it — nothing it enqueued could be delivered. A hook that throws is
        // the hook's fault and must not unwind a writer-pool thread, which serves every other socket.
        if(!closed.get())idleDispatcher.signalIdle();
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
        if(reason==CloseReason.AUTH_REVOKED)authorizationClose=new CloseStatus(4001,"authorization unavailable");
        int dropped=discardPending();
        if (reason == CloseReason.OVERFLOW) {
            metrics.disconnectedSlow();
        } else if(reason==CloseReason.WRITE_ERROR) {
            metrics.writeError();
        }
        if (dropped > 0) {
            metrics.droppedOnClose(dropped);
        }
        // Session close first, then onClose: until the close returns the channel stays registered (closed),
        // so a broadcast finds it and is refused, rather than finding no channel and writing to the session.
        teardownState.compareAndSet(TEARDOWN_NONE, TEARDOWN_PENDING);   // only the close that won `closed` gets here
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
     *
     * <p><b>HANDED is claimed before the teardown is published, never written after</b> (strike re-review round 5).
     * The moment {@code execute} queues it, a closer can run it — and its session close can throw and put it back
     * to PENDING — before this thread takes another step. Writing HANDED after {@code execute} returned overwrote
     * that PENDING: the watchdog, which retries only a PENDING teardown, skipped the channel for good, and the
     * session was never closed again nor {@code onClose} run. So the claim is the CAS PENDING -> HANDED, and after
     * a successful publish nothing is written at all.
     */
    private boolean handOverTeardown() {
        if (!teardownState.compareAndSet(TEARDOWN_PENDING, TEARDOWN_HANDED)) {
            return false;
        }
        try {
            closers.execute(this::teardown);
            return true;
        } catch (RuntimeException | Error handOverFailed) {
            // Not accepted: back to PENDING for the watchdog — but only if it is still HANDED. An executor that
            // published the teardown and threw anyway may have run it already; what that run did (DONE, or its own
            // restore to PENDING) stands.
            teardownState.compareAndSet(TEARDOWN_HANDED, TEARDOWN_PENDING);
            try {
                System.out.println("Feed gateway outbound teardown of socket " + socketId + " could not be scheduled ("
                        + handOverFailed.getClass().getSimpleName() + ": " + handOverFailed.getMessage()
                        + "); it stays pending for the write watchdog to retry");
            } catch (Throwable reportFailed) {
                // The report must not become the throw this catch keeps from the enqueuer — or from the watchdog,
                // whose scheduled sweep an escaping Error would end: under heap exhaustion the line itself can throw.
            }
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

    /**
     * The session close, then {@code onClose} — at most once, on a closer thread. A session close that throws (an
     * {@code Error}: heap exhaustion inside the container) has not reached {@code onClose}, so the teardown is not
     * spent: it goes back to PENDING — the channel still registered and closed — and the write watchdog hands it
     * over again. Once {@code onClose} has been called it is never called again, however it ended.
     */
    private void teardown() {
        if (!tornDown.compareAndSet(false, true)) {
            return;
        }
        boolean closeReturned = false;
        try {
            closeSessionQuietly();
            closeReturned = true;
        } finally {
            if (!closeReturned) {
                // Not torn down: clear tornDown FIRST, then publish PENDING, so the run the watchdog's retry starts
                // finds the teardown runnable from the top. PENDING only from HANDED — the state it was handed over
                // in — so this restore never overwrites a run that has since finished (DONE).
                tornDown.set(false);
                teardownState.compareAndSet(TEARDOWN_HANDED, TEARDOWN_PENDING);
            }
        }
        teardownState.set(TEARDOWN_DONE);                // terminal: the close returned, and tornDown stays set for good
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
        discardPending();
    }

    /** Logical teardown releases queued ownership; the active writer keeps its own lease. */
    private int discardPending() {
        idleDispatcher.close();
        int dropped;
        Pending chartFirst=null,chartSecond=null;
        synchronized (lock) {
            dropped = queue.size();
            idleCompat.clear();
            for(Pending pending:queue.values())if(pending.chart()!=null){if(chartFirst==null)chartFirst=pending;else chartSecond=pending;}
            queue.clear();
            queuedBytes = 0;
        }
        if(chartFirst!=null)releaseChart(chartFirst.chart(),chartFirst.bytes());
        if(chartSecond!=null)releaseChart(chartSecond.chart(),chartSecond.bytes());
        return dropped;
    }

    private void closeSessionQuietly() {
        try {
            if (session.isOpen()) {
                if(authorizationClose==null)session.close();else session.close(authorizationClose);
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
     *
     * <p><b>No failure ends a closer thread</b> (strike re-review round 4). Nothing replaces one, so a thread lost
     * to a throw is capacity lost for good — lose all of them and every accepted teardown waits forever. Each turn
     * of a thread — the take, the bookkeeping, the teardown — is one try, and its handler cannot throw: the failure
     * is counted first ({@link #failures}, an increment that allocates nothing), then reported, and a report that
     * throws — under heap exhaustion building the line can — is swallowed and counted ({@link #unreported}). A
     * thread leaves its loop only when the pool is stopped.
     */
    static final class TeardownPool implements Executor {
        private final LinkedBlockingQueue<Runnable> queue = new LinkedBlockingQueue<>();
        private final Worker[] workers;
        private final long closeDeadlineMs;
        private final Consumer<Throwable> reporter;
        private final AtomicLong failures = new AtomicLong();
        private final AtomicLong unreported = new AtomicLong();
        private volatile boolean stopped;

        TeardownPool(int threads, String namePrefix, long closeDeadlineMs) {
            this(threads, namePrefix, closeDeadlineMs, TeardownPool::printFailure);
        }

        /** {@code reporter} is told of every teardown that threw; it may throw itself (tests: a report that runs out of heap). */
        TeardownPool(int threads, String namePrefix, long closeDeadlineMs, Consumer<Throwable> reporter) {
            this.closeDeadlineMs = closeDeadlineMs;
            this.reporter = reporter;
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

        /** How many teardowns have thrown on a closer thread. */
        long failures() {
            return failures.get();
        }

        /** How many of those failures could not be reported — the report itself threw — so this count is their only trace. */
        long unreported() {
            return unreported.get();
        }

        private static void printFailure(Throwable failure) {
            System.out.println("Feed gateway outbound teardown failed: " + failure.getClass().getSimpleName() + ": " + failure.getMessage());
        }

        /** A teardown threw: count it, then report it. Never throws — nothing would replace the closer thread it ended. */
        private void recordFailure(Throwable failure) {
            failures.incrementAndGet();                    // first: allocates nothing, so it survives what the report may not
            try {
                reporter.accept(failure);
            } catch (Throwable reportFailed) {
                unreported.incrementAndGet();
            }
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
                    // One try around the whole turn, and a handler that cannot throw: nothing a teardown does, and
                    // nothing reporting its failure does, ends this thread while the pool is alive.
                    try {
                        Runnable task;
                        try {
                            task = queue.take();
                        } catch (InterruptedException stray) {
                            continue;                      // shutdownNow, or an interrupt for a close already finished
                        }
                        synchronized (this) {
                            current = task;
                            since = System.currentTimeMillis();
                            interrupted = false;
                        }
                        try {
                            task.run();
                        } finally {
                            synchronized (this) {
                                current = null;
                            }
                            Thread.interrupted();           // an interrupt meant for the close just finished is not the next one's
                        }
                    } catch (Throwable teardownFailed) {
                        recordFailure(teardownFailed);
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

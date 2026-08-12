package app.feedgateway.contexttape;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * The one place that talks to the standalone {@code context-tape-service}. Same shape as the other HTTP
 * upstreams in this gateway ({@link app.feedgateway.stockgex.StockGexUpstream} most directly): a
 * {@link HttpClient} built from configured timeouts, with a package-private constructor as the test seam.
 *
 * <p><b>It deliberately does NOT fail closed.</b> The context-tape service answers with a small status
 * vocabulary the page branches on — most importantly {@code 503 {"error":"WARMING"}} with a
 * {@code Retry-After}, meaning "the backfill is still running, come back in a moment". Folding that into
 * a generic error would destroy the only signal the page has for telling "warming up" apart from
 * "broken", so the status and body are carried through verbatim and only a genuinely unreachable service
 * becomes a gateway-authored error.
 *
 * <p><b>Bodies are carried as BYTES, never as {@code String}.</b> Decoding the upstream body and letting
 * a message converter re-encode it puts two charset guesses in the path, and "verbatim" would then
 * quietly mean "verbatim for ASCII". A {@code byte[]} has no charset to guess.
 *
 * <p><b>Nothing is silently truncated.</b> The session body is read under a documented cap, and
 * EXCEEDING that cap is a protocol failure that becomes a gateway-authored
 * {@code 502 UPSTREAM_PROTOCOL_ERROR} — never the upstream's own status carrying a body that is a
 * prefix of what it sent. A half-delivered snapshot wearing a 200 is worse than an honest error: the
 * page would parse a truncated envelope and render whatever fell out of it.
 *
 * <p><b>The whole-request budget is HARD, and the request thread never blocks past it.</b> Three layers:
 * <ul>
 *   <li>CONNECT — {@code HttpClient.connectTimeout}. Bounds TCP establishment only.</li>
 *   <li>HANDSHAKE — {@code HttpRequest.timeout}. With {@code BodyHandlers.ofInputStream} the JDK stops
 *       enforcing this the moment {@code send()} returns, i.e. once response HEADERS have arrived.</li>
 *   <li>BODY — the read loop runs on a BOUNDED reader pool while the request thread waits on ONE
 *       ABSOLUTE monotonic deadline ({@code startNanos + budget}), recomputed immediately before the
 *       wait and re-checked against the reader's recorded COMPLETION time after it. The acceptance
 *       rule is exactly "the read finished inside the budget": a waiter descheduled past the deadline
 *       still accepts a body that was delivered on time, and still rejects one that completed late —
 *       {@code Future.get}'s check-completion-before-timeout behaviour cannot smuggle a late body
 *       through. No scheduler has to fire on time, and no stream close has to succeed, for the
 *       request thread to escape.</li>
 * </ul>
 *
 * <p><b>Every cleanup resource is bounded, and no close ever runs on a request, reader, or shutdown
 * caller's thread.</b> {@code InputStream.close()} is not contractually non-blocking, so closes run on a
 * FIXED-size closer pool behind a BOUNDED queue. When even that overflows (every closer thread stuck in
 * a pathological close AND the queue full), the disposal is counted and abandoned — and abandonment is
 * itself BOUNDED: after {@link #MAX_ABANDONED_BEFORE_RECYCLE} abandonments the upstream retires its
 * owned {@link HttpClient} and continues on a fresh one; with an injected client it cannot do that, so
 * it FAIL-STOPS instead — refusing new sessions cleanly rather than leaking without limit.
 *
 * <p><b>JDK-contract limitation, stated plainly:</b> {@code HttpClient.shutdownNow()} is BEST EFFORT —
 * the JDK does not guarantee that a shut-down client's operations terminate. No API can force a wedged
 * exchange dead, so "reclaims everything" is not a promise this class can make. What it CAN and does
 * guarantee: every retirement is followed by a bounded {@code awaitTermination}
 * ({@link #RETIRED_TERMINATION_CONFIRM_MS}); a retiree that fails to confirm occupies one of
 * {@link #MAX_UNTERMINATED_RETIRED} registry slots (freed if it terminates later); and when those
 * slots are exhausted the upstream GLOBALLY FAIL-STOPS instead of retiring another possibly-live
 * client. The hard bound is on the COUNT of potentially-unreclaimed clients, which is the strongest
 * bound the JDK contract permits. Stuck reads are likewise bounded: they can strand at most
 * {@link #READER_THREADS} reader threads, after which new session calls are refused cleanly.
 */
public final class ContextTapeUpstream implements AutoCloseable {

    /**
     * Cap on a SESSION body. Same value as the stock-gex board cap and for the same aggregate-exposure
     * reason: the number that matters is cap × concurrent readers, not one request. The contract says a
     * snapshot is ~150 KB at the top end, so 1 MiB keeps ample headroom while anything larger is a
     * producer fault that should be reported as one, not buffered.
     */
    static final int MAX_SESSION_BYTES = 1024 * 1024;

    /**
     * Max CONCURRENT body reads. Matches the controller's session bulkhead (16): the bulkhead admits at
     * most that many in-flight requests, so a healthy system never contends here. The pool exists for
     * the UNHEALTHY case — a read whose deadline expired parks its reader thread until the stream's
     * close lands, and those stranded readers must be bounded. When every slot is stranded, new session
     * calls fail closed with a clean 502 instead of growing anything or queueing behind stuck I/O.
     */
    static final int READER_THREADS = 16;

    /** Fixed closer-pool size. Detection/initiation never runs here — only the blocking closes. */
    static final int CLOSER_THREADS = 2;

    /**
     * Bounded backlog of closes waiting for a closer thread. Closes normally complete in microseconds,
     * so this only fills when closes BLOCK — and then the honest answer is counted abandonment, not an
     * unbounded queue and not an inline close on the caller.
     */
    static final int CLOSER_QUEUE_CAPACITY = 64;

    /** Longest slice of a remote header value allowed into an exception message (and thus any log). */
    static final int MAX_REMOTE_TOKEN_CHARS = 64;

    /**
     * How many abandoned disposals are tolerated on ONE client before the upstream stops creating
     * exchanges on it. An abandonment leaks one unread exchange until the client is shut down, so
     * this is the bound on that leak per generation: past it, an owned client is RECYCLED (its
     * shutdown initiated and then CONFIRMED — see {@link #confirmRetirement}) and an injected one
     * FAIL-STOPS.
     */
    static final int MAX_ABANDONED_BEFORE_RECYCLE = 8;

    /**
     * How long a retirement waits for the retired client to CONFIRM termination. Runs on the
     * lifecycle thread, never a request thread; also serialises recycles, which is desirable — a
     * second recycle should not start while the first retiree is still unconfirmed.
     */
    static final long RETIRED_TERMINATION_CONFIRM_MS = 5_000L;

    /**
     * Hard cap on retired clients whose termination could NOT be confirmed. This is the honest leak
     * bound this class can actually give: {@code HttpClient.shutdownNow()} is BEST EFFORT by JDK
     * contract — it does not guarantee that operations terminate — so "recycle forever" could retire
     * an unbounded procession of still-live clients. Instead, termination is awaited (bounded) and
     * every retiree that fails to confirm occupies one of these slots (freed if it terminates
     * later); when the slots are gone, the upstream GLOBALLY FAIL-STOPS rather than retire another
     * possibly-live client. Worst-case unreclaimed residue is therefore K clients (+ the one
     * current), never "one more per eight abandonments, forever".
     */
    static final int MAX_UNTERMINATED_RETIRED = 2;

    /** Gateway-authored failure codes. Distinct on purpose — they mean different things to an operator. */
    static final String CODE_UNAVAILABLE = "UPSTREAM_UNAVAILABLE";
    static final String CODE_PROTOCOL = "UPSTREAM_PROTOCOL_ERROR";

    /**
     * Disposals abandoned because the closer pool AND its queue were saturated by blocking closes.
     * Exposed on the controller's operator line — an operator seeing this move knows the upstream is
     * wedging closes, not just running slow.
     */
    static final AtomicLong DISPOSALS_ABANDONED = new AtomicLong();

    /** Owned-client recycles performed to reclaim abandoned exchanges. Also on the operator line. */
    static final AtomicLong CLIENT_RECYCLES = new AtomicLong();

    /** A completed session call: the upstream's status, content type, Retry-After and body bytes, untouched. */
    public record SessionResponse(int status, String contentType, String retryAfter, byte[] body) {}

    /**
     * The service could not be reached, or answered something that is not a usable response — distinct
     * from any 4xx/5xx it deliberately authored. {@link #code()} is what the browser is told.
     */
    public static final class UnavailableException extends RuntimeException {
        private final String code;

        UnavailableException(String code, String message, Throwable cause) {
            super(message, cause);
            this.code = code;
        }

        public String code() {
            return code;
        }
    }

    private final String baseUrl;
    private final Duration requestTimeout;
    /** The whole-request budget in nanos, SATURATED so an absurd (but valid) setting cannot overflow. */
    private final long budgetNanos;
    /**
     * How this upstream gets a client. Non-null means the upstream OWNS its clients: it built the
     * current one, may recycle it when abandoned disposals hit the bound, and shuts it down on
     * {@link #close()}. Null means a test injected one client it does not own — no recycle possible,
     * so hitting the bound fail-stops instead.
     */
    private final java.util.function.Supplier<HttpClient> httpFactory;
    /**
     * ONE lock for the whole client lifecycle — the generation counter, the per-generation
     * abandonment count, the saturation/fail-stop flags, the client swap, and {@link #close()}. Every
     * transition is linearized under it, which is what makes the per-generation bound exact, makes a
     * retired generation's stragglers chargeable to nothing, and makes close-vs-recycle a race with
     * two safe outcomes instead of a leak.
     */
    private final Object lifecycleLock = new Object();
    private volatile HttpClient http;
    /** Client generation; bumped on every recycle. Disposals capture it so stragglers stay theirs. */
    private final AtomicLong generation = new AtomicLong();
    /** Abandoned disposals charged against the CURRENT generation. Guarded by {@link #lifecycleLock}. */
    private int abandonedOnGeneration;
    /**
     * Set (under the lock) the moment the current generation hits the abandonment bound; cleared by
     * the swap. While set, NO new exchange is created on the saturated client — sessions refuse —
     * so the per-generation abandonment total is bounded by the bound plus the requests already in
     * flight at the trip (themselves capped by the controller's 16-session bulkhead), every one of
     * which the generation's client shutdown then reclaims (best-effort; see
     * {@link #confirmRetirement}).
     */
    private volatile boolean generationSaturated;
    /**
     * The abandonment bound was hit and no replacement client is possible (no factory, or the
     * factory itself failed), so no further exchanges may be created. Terminal until {@link #close()}.
     */
    private volatile boolean failStopped;
    /**
     * Explicit lifecycle state (set on {@link #close()}, checked at every entry point): a session call
     * racing bean destruction gets a clean {@link UnavailableException} — never an unmapped
     * {@link RejectedExecutionException} escaping as a 500, and never an inline blocking close.
     */
    private volatile boolean closed;

    /**
     * Where recycles run: a dedicated single lifecycle thread. NOT the request thread (a recycle
     * builds a client and shuts one down — none of that belongs in a request's {@code finally}, and
     * a factory failure must become a fail-stop, not an unmapped 500) and NOT the closer pool (which
     * is by definition saturated whenever a recycle is needed).
     */
    private final ExecutorService lifecycle;

    /**
     * Retired clients whose termination has NOT been confirmed — the residue the JDK's best-effort
     * shutdown contract can leave behind. Guarded by {@link #lifecycleLock}; capped at
     * {@link #MAX_UNTERMINATED_RETIRED}, past which the upstream fail-stops (see
     * {@link #confirmRetirement}). Entries that terminate late are pruned when the next retiree
     * arrives.
     */
    private final java.util.List<HttpClient> unterminatedRetired = new java.util.ArrayList<>();

    /**
     * Test seam for the cleanup-health log line: production writes to stdout, a test injects a
     * collector so the LITERAL event can be asserted without racing a global stream swap.
     */
    java.util.function.Consumer<String> cleanupLog = System.out::println;

    /**
     * Test seam: runs between reader submission and the deadline wait, so a test can deterministically
     * deschedule the waiter past the deadline — the exact race in which {@code Future.get} would
     * otherwise return a late-completed body instead of timing out. A no-op in production.
     */
    Runnable betweenSubmitAndWait = () -> { };

    /**
     * Test seam: runs between {@code send()} returning and the disposal being constructed, so a test
     * can deterministically deschedule a request across a recycle in exactly the window where a
     * disposal built from the THEN-CURRENT generation would mis-tag a generation-0 stream as
     * generation 1. A no-op in production.
     */
    Runnable betweenSendAndDisposal = () -> { };

    /**
     * Where body reads run, so the REQUEST thread can wait on them with a hard timeout. Fixed size,
     * synchronous handoff: a task either gets a thread now or is rejected now — a queue here would be
     * a hidden, unbounded wait in front of a deadline that promises not to wait.
     */
    private final ThreadPoolExecutor readers;

    /**
     * Where potentially blocking {@code close()} calls run — FIXED size behind a BOUNDED queue.
     * Neither request threads, nor reader threads, nor the shutdown caller ever run a close; and this
     * pool can never grow past its two threads however many closes wedge. Overflow is counted
     * abandonment (see {@link StreamDisposal#dispose()}).
     */
    private final ThreadPoolExecutor closer;

    public ContextTapeUpstream(String baseUrl, Duration connectTimeout, Duration requestTimeout) {
        this(baseUrl, requestTimeout, null, () -> HttpClient.newBuilder()
                .connectTimeout(connectTimeout)
                // HTTP/1.1 on purpose: the default HTTP/2 policy makes the JDK attempt an h2c upgrade
                // on every cleartext request — extra negotiation for zero benefit on a one-shot GET.
                .version(HttpClient.Version.HTTP_1_1)
                // A redirect is not part of this contract. Following one would let the upstream point
                // this gateway at an arbitrary host; passing it through would leak an internal Location.
                .followRedirects(HttpClient.Redirect.NEVER)
                .build());
    }

    /** Test seam: inject ONE stub {@link HttpClient} the upstream does not own (no recycle possible). */
    ContextTapeUpstream(String baseUrl, Duration requestTimeout, HttpClient http) {
        this(baseUrl, requestTimeout, Objects.requireNonNull(http, "http"), null);
    }

    /** Test seam: an owned client FACTORY, so the recycle path can be driven with stubs. */
    ContextTapeUpstream(String baseUrl, Duration requestTimeout,
                        java.util.function.Supplier<HttpClient> httpFactory) {
        this(baseUrl, requestTimeout, null, Objects.requireNonNull(httpFactory, "httpFactory"));
    }

    private ContextTapeUpstream(String baseUrl, Duration requestTimeout, HttpClient injected,
                                java.util.function.Supplier<HttpClient> httpFactory) {
        // NOT validated here, deliberately. A bad address must not stop this gateway from booting and
        // serving market data (see ContextTapeConfig); it becomes a per-request 502 instead, which is a
        // state the page renders. Boot-time validation would trade a broken tape for a broken gateway.
        this.baseUrl = trimTrailingSlash(baseUrl == null ? "" : baseUrl);
        this.requestTimeout = Objects.requireNonNull(requestTimeout, "requestTimeout");
        this.budgetNanos = saturatedNanos(requestTimeout);
        this.httpFactory = httpFactory;
        this.http = httpFactory != null ? httpFactory.get() : injected;
        this.lifecycle = httpFactory == null ? null
                : java.util.concurrent.Executors.newSingleThreadExecutor(
                        daemonThreads("context-tape-lifecycle"));
        this.readers = new ThreadPoolExecutor(READER_THREADS, READER_THREADS,
                0L, TimeUnit.MILLISECONDS, new SynchronousQueue<>(),
                daemonThreads("context-tape-reader"), new ThreadPoolExecutor.AbortPolicy());
        // A fixed pool keeps idle core threads; that is fine (two parked daemons), and it means a
        // burst of closes can never create threads beyond the two that exist.
        this.closer = new ThreadPoolExecutor(CLOSER_THREADS, CLOSER_THREADS,
                0L, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(CLOSER_QUEUE_CAPACITY),
                daemonThreads("context-tape-closer"), new ThreadPoolExecutor.AbortPolicy());
    }

    private static ThreadFactory daemonThreads(String prefix) {
        return new ThreadFactory() {
            private final AtomicInteger n = new AtomicInteger();

            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, prefix + "-" + n.incrementAndGet());
                t.setDaemon(true);
                return t;
            }
        };
    }

    /** The reader pool; package-visible so a shutdown test can observe its terminal state. */
    ExecutorService readers() {
        return readers;
    }

    /** The blocking-close pool; package-visible for the same reason. */
    ExecutorService closer() {
        return closer;
    }

    /** The client currently carrying exchanges; package-visible so tests can observe a recycle. */
    HttpClient currentClient() {
        return http;
    }

    /** For tests: the current client generation. */
    long generation() {
        return generation.get();
    }

    /** For tests: abandonments charged to the CURRENT generation. */
    int abandonedOnCurrentGeneration() {
        synchronized (lifecycleLock) {
            return abandonedOnGeneration;
        }
    }

    /** For tests: retired clients whose termination is still unconfirmed. */
    int unterminatedRetiredClients() {
        synchronized (lifecycleLock) {
            return unterminatedRetired.size();
        }
    }

    @Override
    public void close() {
        // The whole transition happens under the lifecycle lock, so it linearizes against a recycle:
        // either the recycle published its fresh client first (and it is shut down right here), or
        // close() wins and the recycle finds `closed` set and shuts the fresh client down itself.
        // Nothing in this block waits: shutdownNow calls only interrupt and drain queues — the
        // shutdown caller never runs a close, a factory, or anything blocking while holding the lock.
        synchronized (lifecycleLock) {
            closed = true;
            readers.shutdownNow();
            closer.shutdownNow();
            if (httpFactory != null) {
                // shutdownNow, not close(): close() waits for in-flight exchanges, and a hung
                // upstream during context shutdown would then hang the shutdown itself. Abandoning
                // the exchanges is the point — and (best-effort, per the JDK contract) it is also
                // what reclaims any disposals abandoned at overflow.
                http.shutdownNow();
            }
        }
        if (lifecycle != null) {
            lifecycle.shutdownNow();
        }
    }

    /**
     * One-shot, off-thread disposal of ONE response stream. However many owners ask — the deadline
     * path, a protocol rejection, the request path's cleanup — the closer pool runs the close at most
     * once, and NO CALLING THREAD EVER RUNS IT: not a request thread, not a reader, not the shutdown
     * caller. When the closer is saturated or already shut down, the disposal is counted, abandoned,
     * and charged toward {@link #MAX_ABANDONED_BEFORE_RECYCLE} — because every synchronous
     * alternative is exactly a failure mode this class exists to prevent (an unbounded pool, an
     * unbounded queue, or a blocking close on a live thread).
     */
    final class StreamDisposal {
        private final InputStream stream;
        /**
         * The generation whose client produced this stream: its stragglers stay ITS stragglers.
         * PASSED IN, never sampled here: this object is constructed after {@code send()} returns,
         * and a recycle can land in between — sampling the then-current generation at that point
         * would tag a generation-0 stream as generation 1 and charge the fresh client for the old
         * one's failure. The caller captured the generation together with the client, atomically,
         * under the lifecycle lock, before the exchange was created.
         */
        private final long bornGeneration;
        private final AtomicBoolean disposed = new AtomicBoolean(false);

        StreamDisposal(InputStream stream, long bornGeneration) {
            this.stream = stream;
            this.bornGeneration = bornGeneration;
        }

        void dispose() {
            if (stream == null || !disposed.compareAndSet(false, true)) {
                return;
            }
            try {
                closer.execute(() -> closeQuietly(stream));
            } catch (RejectedExecutionException saturatedOrShutDown) {
                noteAbandonedDisposal(bornGeneration);
            }
        }
    }

    /**
     * Charge one abandoned disposal against the generation that created the stream, and act when the
     * CURRENT generation hits the bound: an OWNED client is recycled on the lifecycle thread — a
     * fresh client takes over, the old one's shutdown is initiated and then CONFIRMED under the
     * unconfirmed-retiree cap ({@link #confirmRetirement}), all on no request thread — while an
     * injected client cannot be replaced, so the upstream FAIL-STOPS and refuses new sessions
     * instead.
     *
     * <p>Accounting is linearized under the lifecycle lock: the count is exact (no concurrent
     * overshoot), a straggler from a retired generation charges NOTHING (its client is already shut
     * down and its abandonment handed to that shutdown to reclaim), and the saturation flag raised here is what stops
     * new exchanges being created on the leaking client while the lifecycle thread swaps it.
     */
    private void noteAbandonedDisposal(long disposalGeneration) {
        DISPOSALS_ABANDONED.incrementAndGet();
        boolean firstOnGeneration = false;
        boolean tripped = false;
        synchronized (lifecycleLock) {
            if (closed || disposalGeneration != generation.get()) {
                return;
            }
            abandonedOnGeneration++;
            firstOnGeneration = abandonedOnGeneration == 1;
            if (abandonedOnGeneration >= MAX_ABANDONED_BEFORE_RECYCLE && !generationSaturated) {
                generationSaturated = true;
                tripped = true;
            }
        }
        if (firstOnGeneration) {
            logCleanup("closer overflow, abandoning disposals");
        }
        if (!tripped) {
            return;
        }
        if (httpFactory == null) {
            synchronized (lifecycleLock) {
                failStopped = true;
            }
            logCleanup("abandonment bound hit with no client factory, FAIL-STOPPING");
            return;
        }
        try {
            lifecycle.execute(this::recycleNow);
        } catch (RejectedExecutionException shuttingDown) {
            // close() already ran; its client shutdown takes over (best-effort), nothing left to do.
        }
    }

    /**
     * Swap the leaking client for a fresh one. Runs ONLY on the lifecycle thread. The factory runs
     * OUTSIDE the lock (so {@link #close()} never waits behind a slow factory), and the swap itself
     * is linearized against close(): whichever wins, exactly one owner shuts every client down —
     * a fresh client built after close() is shut down here, never published, never leaked.
     */
    private void recycleNow() {
        // LOCKED PREFLIGHT before the factory is even invoked: a registry
        // already at the cap (after pruning late terminations) means no new
        // client may be created at all — the factory must not run (r9 F1).
        // The identical check after the build remains as the race guard for a
        // confirmation that fills the registry while the factory runs.
        synchronized (lifecycleLock) {
            unterminatedRetired.removeIf(HttpClient::isTerminated);
            if (failStopped || unterminatedRetired.size() >= MAX_UNTERMINATED_RETIRED) {
                failStopped = true;
            }
        }
        if (failStopped) {
            logCleanup("recycle refused pre-factory: unconfirmed retiree cap, FAIL-STOPPED");
            return;
        }
        HttpClient fresh;
        try {
            fresh = httpFactory.get();
        } catch (RuntimeException | Error factoryBroke) {
            // No replacement client can exist, so no further exchanges may be created: fail-stop —
            // a clean per-request 502 — rather than an unmapped error on whatever thread this is.
            synchronized (lifecycleLock) {
                failStopped = true;
            }
            logCleanup("client factory failed, FAIL-STOPPING");
            return;
        }
        HttpClient leaking = null;
        boolean refused = false;
        synchronized (lifecycleLock) {
            if (closed) {
                fresh.shutdownNow(); // close() won the race; the fresh client must not outlive it
                return;
            }
            // the cap gates the RECYCLE itself, not only the retirement that
            // follows it: a full registry (after pruning late terminations)
            // means no further clients may be created — fail-stop instead of
            // retiring yet another possibly-live client (r8 F1)
            unterminatedRetired.removeIf(HttpClient::isTerminated);
            if (failStopped || unterminatedRetired.size() >= MAX_UNTERMINATED_RETIRED) {
                failStopped = true;
                refused = true;            // side effects run OFF the lock (r9 F2)
            } else {
                leaking = http;
                http = fresh;
                generation.incrementAndGet();
                abandonedOnGeneration = 0;
                generationSaturated = false;
                CLIENT_RECYCLES.incrementAndGet();
            }
        }
        if (refused) {
            fresh.shutdownNow();           // never under the lock every request needs
            logCleanup("recycle refused: unconfirmed retiree cap, FAIL-STOPPED");
            return;
        }
        // INITIATES reclamation of everything abandoned on the retired generation.
        // Best effort by JDK contract — hence the bounded termination confirmation
        // below. Both calls deliberately run OFF the lifecycle lock: a wedged
        // shutdown or stdout backpressure must never stall session entry (r9 F2).
        leaking.shutdownNow();
        logCleanup("recycled the leaking client");
        confirmRetirement(leaking);
    }

    /**
     * The honest half of retirement: {@code shutdownNow()} only ASKS. This waits (bounded, on the
     * lifecycle thread) for the retiree to confirm termination; a retiree that does not confirm
     * occupies one of {@link #MAX_UNTERMINATED_RETIRED} registry slots — freed if it terminates
     * later — and when the registry is full the upstream GLOBALLY FAIL-STOPS rather than retire yet
     * another possibly-live client. That cap, not the shutdown call, is the leak bound this class
     * actually guarantees; the JDK contract permits nothing stronger.
     */
    private void confirmRetirement(HttpClient retired) {
        boolean terminated;
        try {
            terminated = retired.awaitTermination(Duration.ofMillis(RETIRED_TERMINATION_CONFIRM_MS));
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt(); // close() is tearing us down; treat as unconfirmed
            terminated = false;
        }
        if (terminated) {
            synchronized (lifecycleLock) {
                // a PREVIOUS retiree may have terminated late — every
                // retirement observation prunes, so the registry and its
                // metric can never stay stale (r8 F2)
                unterminatedRetired.removeIf(HttpClient::isTerminated);
            }
            logCleanup("retired client confirmed terminated");
            return;
        }
        boolean stop = false;
        synchronized (lifecycleLock) {
            unterminatedRetired.removeIf(HttpClient::isTerminated); // late terminations free slots
            unterminatedRetired.add(retired);
            if (unterminatedRetired.size() >= MAX_UNTERMINATED_RETIRED && !closed) {
                failStopped = true;
                stop = true;
            }
        }
        if (stop) {
            logCleanup("unconfirmed retired clients hit the cap, FAIL-STOPPING");
        } else {
            logCleanup("retired client did not confirm termination within "
                    + RETIRED_TERMINATION_CONFIRM_MS + "ms");
        }
    }

    /**
     * Cleanup health is logged AT the events themselves — abandonment onset, fail-stop, recycle —
     * not only on the 502 path: a gateway that recycles and keeps serving 200s would otherwise never
     * emit these counters anywhere. Each event class fires at most once per generation, so this is
     * bounded without throttling.
     */
    private void logCleanup(String what) {
        cleanupLog.accept("context-tape upstream " + what
                + ": abandonedDisposals=" + DISPOSALS_ABANDONED.get()
                + " clientRecycles=" + CLIENT_RECYCLES.get()
                + " generation=" + generation.get()
                + " unterminatedRetired=" + unterminatedRetiredClients());
    }

    /** {@code GET <base>/api/context-tape/session} — one snapshot, one request/response. */
    public SessionResponse session() {
        // Gate BEFORE creating an exchange, and capture the client TOGETHER WITH ITS GENERATION,
        // atomically, under the lifecycle lock: the disposal built later must be tagged with the
        // generation whose client actually produced the stream, and a recycle can land anywhere
        // between this capture and that construction. A caller that passed the gate before a flag
        // flipped can still complete its one send — that in-flight remainder is bounded by the
        // controller's 16-session bulkhead and handed to the generation's client shutdown to
        // reclaim (best-effort, confirmed and capped — see confirmRetirement), which is the
        // documented bound.
        HttpClient client;
        long bornGeneration;
        synchronized (lifecycleLock) {
            if (closed) {
                // The graceful-shutdown race, answered deliberately: a clean 502 the page renders,
                // never an unmapped executor rejection escaping as a 500.
                throw unavailable("context-tape upstream is shut down", null);
            }
            if (failStopped) {
                // The abandonment bound was hit on a client this upstream cannot replace. Creating
                // more exchanges on it would leak without limit, so none are created.
                throw unavailable("context-tape upstream is fail-stopped: cleanup is wedged and its "
                        + "client cannot be recycled", null);
            }
            if (generationSaturated) {
                // The current client hit its abandonment bound and the lifecycle thread is swapping
                // it. No exchange may be created on the leaking client in the meantime.
                throw unavailable("context-tape upstream is recycling its client after cleanup "
                        + "overflow", null);
            }
            client = http;
            bornGeneration = generation.get();
        }
        long startedNanos = System.nanoTime();
        long deadlineNanos = startedNanos + budgetNanos;
        HttpResponse<InputStream> resp;
        try {
            // Request construction is INSIDE the mapped block: newBuilder/uri/header/build can all throw
            // IllegalArgumentException for a bad address or header, and an escape here is a 500 with a
            // stack trace on the browser's side.
            HttpRequest req = HttpRequest.newBuilder(uri("/api/context-tape/session"))
                    .timeout(requestTimeout)
                    .header("Accept", "application/json")
                    // Ask for no CONTENT coding. This is only a request, though — see
                    // requireIdentityEncoding: what arrives still has to be checked, because a body
                    // forwarded compressed without its Content-Encoding header is garbage that still
                    // claims to be JSON.
                    .header("Accept-Encoding", "identity")
                    .GET()
                    .build();
            resp = client.send(req, HttpResponse.BodyHandlers.ofInputStream());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw unavailable("interrupted while calling context-tape-service", e);
        } catch (IOException transport) {
            throw unavailable("context-tape-service is unreachable", transport);
        } catch (IllegalArgumentException | SecurityException misconfigured) {
            throw unavailable("context-tape-service address is not usable", misconfigured);
        }
        // From here the stream exists, and EVERY path out of this method — success, protocol
        // rejection, deadline, reader saturation — disposes it through the one-shot off-thread
        // handle. The finally is what guarantees no path forgets; the one-shot flag is what makes
        // multiple owners never close twice.
        betweenSendAndDisposal.run();
        StreamDisposal disposal = new StreamDisposal(resp.body(), bornGeneration);
        try {
            requireIdentityEncoding(resp);
            // Signed DIFFERENCE, never a raw relational: nanoTime values may wrap, and only the
            // difference of two readings is guaranteed meaningful.
            if (System.nanoTime() - deadlineNanos >= 0) {
                // The configured budget was spent before the body could even be read. Failing NOW is
                // the honest answer — flooring the deadline to a fresh grace period would let a
                // request exceed its documented bound, by more than 100% at the minimum setting.
                throw new UnavailableException(CODE_PROTOCOL,
                        "context-tape session request budget was exhausted before the body was read", null);
            }
            byte[] body = readOnDeadline(resp.body(), MAX_SESSION_BYTES, deadlineNanos);
            return new SessionResponse(resp.statusCode(), header(resp, "content-type"),
                    header(resp, "retry-after"), body);
        } finally {
            disposal.dispose();
        }
    }

    /**
     * Run the bounded drain on a reader thread and wait for it on THIS thread against ONE ABSOLUTE
     * monotonic deadline.
     *
     * <p>This is what makes the whole-request budget real, and the deadline is absolute in both
     * directions of the race:
     * <ul>
     *   <li>The wait's remainder is recomputed from the absolute deadline IMMEDIATELY before waiting,
     *       in nanoseconds — descheduling between submission and the wait shrinks the wait, and
     *       millisecond flooring cannot grant time past the budget.</li>
     *   <li>The reader records its COMPLETION time, and acceptance requires
     *       {@code completed <= deadline}. {@code Future.get} checks completion before timeout, so a
     *       waiter descheduled past the deadline can be handed a late-completed body — the completion
     *       check rejects it. Conversely a body that WAS delivered in time is accepted even when the
     *       waiter itself resumed late: the rule is about when the read finished, not when the waiter
     *       woke.</li>
     * </ul>
     * No scheduler has to fire on time, and no stream close has to succeed, for the request thread to
     * leave. A reader left parked on a dead stream is stranded at worst until the disposal's close
     * lands, and stranded readers are bounded by the pool: when every slot is held, the next call is
     * REFUSED cleanly rather than queued behind stuck I/O.
     */
    private byte[] readOnDeadline(InputStream in, int maxBytes, long deadlineNanos) {
        // Sentinel on the REJECT side of the signed-difference comparison: an (unreachable) unset
        // value must read as "late", never wrap into "on time".
        AtomicLong completedNanos = new AtomicLong(Long.MIN_VALUE);
        Future<byte[]> pending;
        try {
            pending = readers.submit(() -> {
                byte[] body = drainBounded(in, maxBytes);
                completedNanos.set(System.nanoTime());
                return body;
            });
        } catch (RejectedExecutionException saturated) {
            throw closed
                    ? unavailable("context-tape upstream is shut down", saturated)
                    : unavailable("every context-tape reader slot is held by a stuck upstream stream",
                            saturated);
        }
        betweenSubmitAndWait.run();
        try {
            long waitNanos = Math.max(0L, deadlineNanos - System.nanoTime());
            byte[] body = pending.get(waitNanos, TimeUnit.NANOSECONDS);
            // Signed difference again (wrap-safe); get() returning guarantees the completion time
            // was recorded, with the write ordered before it by the future's own happens-before.
            if (completedNanos.get() - deadlineNanos > 0) {
                // The future was already complete when the (descheduled) waiter got here, so get()
                // returned it instead of timing out — but the READ finished after the deadline, and
                // a late body is a late body however it was handed over.
                throw new UnavailableException(CODE_PROTOCOL,
                        "context-tape session body was not delivered within the request budget", null);
            }
            return body;
        } catch (TimeoutException late) {
            pending.cancel(true);
            throw new UnavailableException(CODE_PROTOCOL,
                    "context-tape session body was not delivered within the request budget", null);
        } catch (ExecutionException failed) {
            Throwable cause = failed.getCause();
            if (cause instanceof UnavailableException refused) {
                throw refused;
            }
            // Includes a disposal's close surfacing as an IOException under the reader. Either way we
            // do NOT have the upstream's body, so we must not answer with the upstream's status and a
            // body we invented (an empty or partial one).
            throw new UnavailableException(CODE_PROTOCOL,
                    "context-tape session body could not be read", cause);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            pending.cancel(true);
            throw unavailable("interrupted while reading the context-tape session body", interrupted);
        }
    }

    /** The read loop itself, run on a reader thread: byte cap enforced inline, no timing concerns. */
    private static byte[] drainBounded(InputStream in, int maxBytes) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[8192];
        int n;
        while ((n = in.read(chunk)) != -1) {
            if (buffer.size() + n > maxBytes) {
                throw new UnavailableException(CODE_PROTOCOL,
                        "context-tape session body exceeded " + maxBytes + " bytes", null);
            }
            buffer.write(chunk, 0, n);
        }
        return buffer.toByteArray();
    }

    /**
     * Refuse a body that arrived under a content coding this proxy does not decode.
     *
     * <p>{@code Accept-Encoding: identity} is a REQUEST header — a wish, not a guarantee. If an
     * upstream or an intermediary compresses anyway, forwarding those bytes while dropping the
     * {@code Content-Encoding} header hands the browser a gzip stream labelled as JSON: it is not
     * corrupt in a way anything reports, it is simply unreadable. Since the whole contract here is
     * byte-for-byte passthrough, the honest answer is to refuse rather than to half-decode.
     */
    private static void requireIdentityEncoding(HttpResponse<InputStream> resp) {
        // EVERY field and EVERY comma-separated coding, not firstValue(): a response carrying
        // `Content-Encoding: identity` followed by `Content-Encoding: gzip` (or a single
        // `identity, gzip`) is compressed, and judging only the first value would forward gzip bytes
        // labelled as JSON. The acceptable set is exactly "no codings at all" or "identity only".
        // The stream itself is disposed by the caller's finally — never closed here, on this thread.
        for (String field : resp.headers().allValues("content-encoding")) {
            if (field == null || field.isBlank()) {
                continue;
            }
            for (String coding : field.split(",")) {
                String c = coding.trim();
                if (!c.isEmpty() && !"identity".equalsIgnoreCase(c)) {
                    throw new UnavailableException(CODE_PROTOCOL,
                            "context-tape session arrived with content-encoding '" + boundedToken(c)
                            + "', which this proxy does not decode", null);
                }
            }
        }
    }

    /**
     * Bound remote text AT THE POINT IT ENTERS an exception message, not at the log line: a message
     * is copied around (rethrown, wrapped, printed by frameworks) and every copy that is not the one
     * sanitised log call would otherwise carry the unbounded original.
     */
    static String boundedToken(String raw) {
        String oneLine = raw.replaceAll("[\\r\\n]", " ");
        return oneLine.length() <= MAX_REMOTE_TOKEN_CHARS
                ? oneLine
                : oneLine.substring(0, MAX_REMOTE_TOKEN_CHARS) + "…";
    }

    /**
     * Build the upstream URI, refusing anything that is not an absolute http(s) address.
     *
     * <p>A misconfigured {@code CONTEXT_TAPE_BASE_URL} must not become a 500 with a stack trace on the
     * browser's side, and must not fail the whole gateway at boot either: it becomes the same
     * {@link UnavailableException} an unreachable service produces, i.e. a 502 with the ordinary JSON
     * envelope, which is the state the page already knows how to render.
     */
    private URI uri(String path) {
        URI uri;
        try {
            uri = new URI(baseUrl + path);
        } catch (URISyntaxException malformed) {
            throw unavailable("context-tape base url is not a valid URI", malformed);
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!uri.isAbsolute() || uri.getHost() == null
                || !("http".equals(scheme) || "https".equals(scheme))) {
            throw unavailable("context-tape base url must be an absolute http(s) address", null);
        }
        return uri;
    }

    /**
     * The budget in nanos, SATURATED: {@code Duration.toNanos()} throws on overflow, and a huge but
     * valid {@code CONTEXT_TAPE_REQUEST_TIMEOUT_MS} must configure "effectively forever", not detonate
     * every request as a 500. Half of {@code Long.MAX_VALUE} so {@code now + budget} cannot wrap.
     */
    private static long saturatedNanos(Duration d) {
        try {
            long nanos = d.toNanos();
            return Math.min(nanos, Long.MAX_VALUE / 2);
        } catch (ArithmeticException overflow) {
            return Long.MAX_VALUE / 2;
        }
    }

    private static UnavailableException unavailable(String message, Throwable cause) {
        return new UnavailableException(CODE_UNAVAILABLE, message, cause);
    }

    private static void closeQuietly(InputStream in) {
        if (in == null) {
            return;
        }
        try {
            in.close();
        } catch (IOException | RuntimeException ignored) {
            // Best effort: the exchange is being abandoned either way.
        }
    }

    private static String header(HttpResponse<?> resp, String name) {
        return resp.headers().firstValue(name).orElse(null);
    }

    private static String trimTrailingSlash(String s) {
        String t = s.trim();
        while (t.endsWith("/")) {
            t = t.substring(0, t.length() - 1);
        }
        return t;
    }
}

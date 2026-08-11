package app.feedgateway.stockgex;

import app.feedgateway.liquidityhistory.LiquidityHistoryAuth;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.async.CallableProcessingInterceptor;
import org.springframework.web.context.request.async.WebAsyncUtils;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Callable;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * The web UI's door to the standalone {@code stock-gex-service}:
 *
 * <ul>
 *   <li>{@code GET /api/stock-gex/board?symbol=TSLA} — the current stock GEX board, one request/response.</li>
 *   <li>{@code GET /api/stock-gex/stream?symbol=TSLA} — the live {@code text/event-stream} for that board.</li>
 * </ul>
 *
 * <p><b>Status codes are a contract, not noise.</b> The upstream distinguishes
 * {@code 422 OI_SNAPSHOT_UNAVAILABLE} (this ticker is outside the nightly OI index universe — never going
 * to work today), {@code 400 BAD_SYMBOL}, {@code 409 BOARD_GONE}, and {@code 429/503 MAX_SYMBOLS /
 * WIRE_CAPACITY / BOARD_SUBSCRIBE_FAILED / SHUTTING_DOWN / SSE_CLIENT_LIMIT} (capacity — try again). The
 * page renders a different thing for each, so this proxy forwards the upstream status AND body
 * byte-for-byte. The only statuses this class authors are 401/403 (auth, before anything else happens),
 * 400 for a symbol too long to put on the wire, 503 when this gateway is already carrying as many live
 * streams as it will hold, and 502 when the service cannot be reached or did not answer with a usable
 * response — deliberately 502 rather than 503, so "the gateway could not reach stock-gex" stays
 * distinguishable from the upstream's own 503 capacity answer.
 *
 * <p><b>Every gateway-authored answer on the stream endpoint is written SYNCHRONOUSLY</b>, by throwing
 * {@link StockGexRefusal} and letting the {@link ExceptionHandler} below answer it with a plain
 * {@code byte[]}. This is not a style choice. A {@code StreamingResponseBody} — even one that writes 40
 * bytes and returns — is dispatched through the MVC async executor, so answering "this gateway is out of
 * stream slots" with one would require the very resource whose exhaustion it is reporting: during a
 * burst the refusals would queue behind the streams they are refusing, and a client would get a rejected
 * task (a 500) instead of the promised immediate JSON 503. Only the LIVE stream returns a streaming body.
 *
 * <p>Bearer auth reuses {@link LiquidityHistoryAuth}, exactly as {@code /api/seller-activity} and
 * {@code /api/gamma-migration} do — these are data endpoints and stay behind the same guard as every other
 * one. This gateway deliberately has no servlet-security filter chain (see the pom comment — adding one
 * would lock every endpoint behind a generated password), so "authenticated" here means calling the
 * shared verifier first, which both handlers do before touching the upstream.
 *
 * <p><b>Why there IS a stream cap here and no rate limiter.</b> No request-rate limiter: the upstream owns
 * the request-capacity contract and expresses it in the 429/503 codes above, and a second gateway-authored
 * 429 with a different body would be indistinguishable from {@code MAX_SYMBOLS} to the page. But
 * CONCURRENCY is a gateway-local resource the upstream cannot see: every live stream pins one thread of
 * this JVM's MVC async pool for the life of the session. That pool is finite (see
 * {@link StockGexAsyncConfig}), and an async response that cannot get a thread does not fail — it QUEUES,
 * answering nothing at all. So streams are capped below the pool and the overflow is answered immediately
 * with the upstream's own {@code 503 SSE_CLIENT_LIMIT} vocabulary, which the page already renders.
 */
@RestController
public class StockGexController {

    /**
     * Max CONCURRENT live streams this gateway will carry. Each holds one MVC async thread and one
     * upstream HTTP exchange for the life of the session, so this — not the upstream's own limit — is
     * what bounds this JVM. Kept comfortably below {@link StockGexAsyncConfig#MAX_ASYNC_THREADS} so the
     * other async endpoint on this gateway ({@code /api/seller-activity}) can always get a thread.
     */
    static final int MAX_CONCURRENT_STREAMS = 24;

    /**
     * How long a live stream may deliver NOTHING before this gateway closes it.
     *
     * <p>The upstream heartbeats about every 10 seconds, so six missed heartbeats is a stream that has
     * stopped being a stream. Nothing else catches this: the request timeout stops applying the moment
     * headers arrive, and a heartbeat only proves liveness while the upstream is still sending them — a
     * silent upstream produces no evidence of its own silence. Without this, one half-open peer holds an
     * async thread, a permit and an upstream exchange until the JVM restarts.
     */
    static final long STREAM_IDLE_TIMEOUT_MS = 60_000L;
    /** How often the watchdog looks. Fine enough to bound overshoot, coarse enough to cost nothing. */
    static final long STREAM_IDLE_CHECK_MS = 10_000L;

    /** Never log an unreachable-upstream failure more than this often: an outage is per-request. */
    private static final long UNREACHABLE_LOG_INTERVAL_MS = 30_000L;

    private static final String CLEANUP_KEY = StockGexController.class.getName() + ".streamCleanup";

    /**
     * How a stream's cleanup is bound to the servlet async lifecycle. A seam because the production
     * implementation reaches into {@code WebAsyncUtils}, and the property that actually matters — the
     * lease is released even when the body is never invoked — has to be provable without a container.
     */
    interface AsyncCleanupRegistrar {
        void onAsyncCompletion(HttpServletRequest request, Runnable cleanup);
    }

    /**
     * The real binding. {@code WebAsyncManager} registers the interceptor chain's completion handler
     * BEFORE it submits the task, and the container drives it from the async lifecycle — so
     * {@code afterCompletion} runs whether the body ran to the end, the executor rejected it, the client
     * vanished during async setup, or the dispatch was aborted. That is exactly the set of cases where
     * {@code StreamingResponseBody.writeTo} is never called and its own {@code finally} never fires.
     */
    static final AsyncCleanupRegistrar SERVLET_ASYNC_CLEANUP = (request, cleanup) ->
            WebAsyncUtils.getAsyncManager(request).registerCallableInterceptor(
                    CLEANUP_KEY,
                    new CallableProcessingInterceptor() {
                        @Override
                        public <T> void afterCompletion(NativeWebRequest r, Callable<T> task) {
                            cleanup.run();
                        }
                    });

    private final StockGexUpstream upstream;
    private final LiquidityHistoryAuth auth;
    private final ObjectMapper mapper;
    private final AsyncCleanupRegistrar asyncCleanup;
    /** Package-visible so a test can exhaust it. */
    final Semaphore streamSlots;
    private final AtomicLong lastUnreachableLogMs = new AtomicLong(0L);
    private final long idleCheckMs;
    private final long idleTimeoutMs;

    @org.springframework.beans.factory.annotation.Autowired
    public StockGexController(StockGexUpstream upstream, LiquidityHistoryAuth auth, ObjectMapper mapper) {
        this(upstream, auth, mapper, MAX_CONCURRENT_STREAMS, SERVLET_ASYNC_CLEANUP);
    }

    /** Test seam: explicit concurrency limit and cleanup binding. */
    StockGexController(StockGexUpstream upstream, LiquidityHistoryAuth auth, ObjectMapper mapper,
                       int maxConcurrentStreams, AsyncCleanupRegistrar asyncCleanup) {
        this(upstream, auth, mapper, maxConcurrentStreams, asyncCleanup,
                STREAM_IDLE_CHECK_MS, STREAM_IDLE_TIMEOUT_MS);
    }

    /** Test seam: a watchdog fast enough to observe, so the REAL one can be exercised. */
    StockGexController(StockGexUpstream upstream, LiquidityHistoryAuth auth, ObjectMapper mapper,
                       int maxConcurrentStreams, AsyncCleanupRegistrar asyncCleanup,
                       long idleCheckMs, long idleTimeoutMs) {
        this.upstream = upstream;
        this.auth = auth;
        this.mapper = mapper == null ? new ObjectMapper() : mapper;
        this.streamSlots = new Semaphore(maxConcurrentStreams);
        this.asyncCleanup = asyncCleanup;
        this.idleCheckMs = idleCheckMs;
        this.idleTimeoutMs = idleTimeoutMs;
    }

    // ------------------------------------------------------------------ board

    /**
     * No {@code produces} condition on purpose (same reason as the stream handler): the body is an
     * upstream passthrough whose content type is whatever the upstream chose, so pinning one media type
     * here would both misdescribe the endpoint and 406 a client whose {@code Accept} did not name it.
     */
    @GetMapping("/api/stock-gex/board")
    public ResponseEntity<byte[]> board(
            @RequestParam(value = "symbol", required = false) String symbol,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        LiquidityHistoryAuth.Result authResult = auth.authenticate(authorization);
        if (authResult.status() != 200) {
            return ResponseEntity.status(authResult.status()).build();
        }
        if (tooLong(symbol)) {
            return json(HttpStatus.BAD_REQUEST, error("BAD_SYMBOL", "symbol is too long"), null);
        }
        StockGexUpstream.BoardResponse response;
        try {
            response = upstream.board(symbol);
        } catch (StockGexUpstream.UnavailableException unreachable) {
            logUnreachable("board", unreachable);
            return json(HttpStatus.BAD_GATEWAY, unreachableError(unreachable), null);
        }
        ResponseEntity.BodyBuilder out = ResponseEntity.status(response.status())
                .contentType(mediaType(response.contentType(), MediaType.APPLICATION_JSON))
                // A board is a live measurement of a moving market. Nothing on the path — browser,
                // ingress, corporate proxy — may ever replay one as if it were current.
                .header(HttpHeaders.CACHE_CONTROL, "no-store");
        if (response.retryAfter() != null) {
            // The upstream's own backoff advice for its 429/503s. Dropping it would leave the page to
            // guess a retry interval the service already told us.
            out = out.header(HttpHeaders.RETRY_AFTER, response.retryAfter());
        }
        return out.body(response.body() == null ? new byte[0] : response.body());
    }

    // ------------------------------------------------------------------ stream

    /**
     * No {@code produces} condition on purpose: this handler answers {@code text/event-stream}, and every
     * other outcome is answered by the synchronous handler below, so declaring one media type would both
     * misdescribe the endpoint and 406 a client that asked only for the other.
     */
    @GetMapping("/api/stock-gex/stream")
    public ResponseEntity<StreamingResponseBody> stream(
            HttpServletRequest request,
            @RequestParam(value = "symbol", required = false) String symbol,
            @RequestHeader(value = "Last-Event-ID", required = false) String lastEventId,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        LiquidityHistoryAuth.Result authResult = auth.authenticate(authorization);
        if (authResult.status() != 200) {
            throw new StockGexRefusal(authResult.status(), null, new byte[0], null);
        }
        if (tooLong(symbol)) {
            throw refusal(HttpStatus.BAD_REQUEST, error("BAD_SYMBOL", "symbol is too long"), null);
        }
        // Refuse BEFORE opening anything upstream. Answering now with a status the page understands is
        // strictly better than letting the request queue on an exhausted async pool, where it would
        // answer nothing at all.
        if (!streamSlots.tryAcquire()) {
            throw refusal(HttpStatus.SERVICE_UNAVAILABLE,
                    error("SSE_CLIENT_LIMIT",
                          "this gateway is already carrying its maximum number of live stock-gex streams"),
                    "5");
        }
        StreamLease lease = null;
        try {
            StockGexUpstream.StreamResponse opened;
            try {
                opened = upstream.stream(symbol, lastEventId);
            } catch (StockGexUpstream.UnavailableException unreachable) {
                logUnreachable("stream", unreachable);
                throw refusal(HttpStatus.BAD_GATEWAY, unreachableError(unreachable), null);
            }
            if (!opened.live()) {
                // Anything that is not a 200 — 4xx, 5xx, a redirect, even another 2xx — is an ordinary
                // status+body response, carried through with the upstream's own status. A Location is
                // deliberately NOT forwarded: it would name an internal host to the browser, and this
                // endpoint's contract is a board or an error, never a redirect to somewhere else.
                throw new StockGexRefusal(opened.status(), opened.contentType(),
                        opened.errorBody() == null ? new byte[0] : opened.errorBody(),
                        opened.retryAfter());
            }
            if (!isEventStream(opened.contentType())) {
                // A 200 is not enough. An upstream answering `200 application/json`, or an HTML error
                // page from something in between, would otherwise be presented to the browser as a
                // live event stream that can never carry an event — holding a slot and an async
                // thread until the idle watchdog eventually noticed.
                closeQuietly(opened.body());
                throw refusal(HttpStatus.BAD_GATEWAY,
                        error(StockGexUpstream.CODE_PROTOCOL,
                              "stock-gex-service answered 200 but not with an event stream"), null);
            }
            // The lease owns BOTH the upstream stream and the permit from here on, and releases them
            // exactly once however this request ends.
            lease = new StreamLease(opened.body(), streamSlots);
            lease.armIdleWatchdog(upstream.timers(), idleCheckMs, idleTimeoutMs);
            // Bind to the servlet async lifecycle BEFORE returning: if the executor rejects the body, or
            // the container discards the dispatch, writeTo never runs and this is the only thing left.
            asyncCleanup.onAsyncCompletion(request, lease::release);
            StreamingResponseBody pump = pump(lease);
            ResponseEntity<StreamingResponseBody> response = ResponseEntity.status(opened.status())
                    .contentType(mediaType(opened.contentType(), MediaType.TEXT_EVENT_STREAM))
                    .header(HttpHeaders.CACHE_CONTROL, "no-cache, no-transform, no-store")
                    // Any buffering proxy in front of this (nginx/ingress) would hold events back until
                    // its buffer filled, which for a 200-byte-per-second stream reads exactly like a
                    // dead feed.
                    .header("X-Accel-Buffering", "no")
                    .body(pump);
            lease = null; // handed over; the finally below must not undo it
            return response;
        } catch (RuntimeException | Error failed) {
            // Exactly one owner at any moment: before the lease exists the permit is loose and goes
            // straight back; once the lease exists it owns the permit AND the upstream stream, so
            // releasing it is what returns both. On the success path `lease` is nulled after the
            // response is built, so this never undoes a handover.
            if (lease != null) {
                lease.release();
            } else {
                streamSlots.release();
            }
            throw failed;
        }
    }

    /**
     * The one place a gateway-authored (or passed-through non-stream) answer is written, and it is
     * written SYNCHRONOUSLY as a {@code byte[]}. Spring resolves handler exceptions on the request
     * thread, before any async dispatch begins, so this path never touches the MVC async executor —
     * which is exactly what makes "503, this gateway is out of stream slots" answerable while that
     * executor is saturated.
     */
    @ExceptionHandler(StockGexRefusal.class)
    public ResponseEntity<byte[]> refused(StockGexRefusal refusal) {
        ResponseEntity.BodyBuilder out = ResponseEntity.status(refusal.status)
                .contentType(mediaType(refusal.contentType, MediaType.APPLICATION_JSON))
                .header(HttpHeaders.CACHE_CONTROL, "no-store");
        if (refusal.retryAfter != null) {
            out = out.header(HttpHeaders.RETRY_AFTER, refusal.retryAfter);
        }
        return out.body(refusal.body);
    }

    /** Any non-stream outcome of {@code /api/stock-gex/stream}: authored here, or passed through. */
    static final class StockGexRefusal extends RuntimeException {
        final int status;
        final String contentType;
        final byte[] body;
        final String retryAfter;

        StockGexRefusal(int status, String contentType, byte[] body, String retryAfter) {
            super(null, null, false, false); // no stack trace: this is control flow, not a defect
            this.status = status;
            this.contentType = contentType;
            this.body = body;
            this.retryAfter = retryAfter;
        }
    }

    /**
     * Everything a live stream owns, released exactly once no matter who gets there first.
     *
     * <p>Three independent things can end a stream — the pump finishing or failing, the servlet async
     * lifecycle completing, and the idle watchdog firing — and any of them can happen without the
     * others. A permit released twice would hand out capacity this gateway does not have; a permit
     * never released is capacity it never gets back. Hence one idempotent lease rather than a
     * {@code finally} in each of the three places.
     */
    static final class StreamLease {
        private final InputStream upstream;
        private final Semaphore slots;
        private final AtomicBoolean released = new AtomicBoolean(false);
        /**
         * NANOTIME, not wall clock. This is the only mechanism bounding a silent upstream, and a
         * backward clock adjustment (NTP step, a VM resuming) would postpone it by exactly the size
         * of the jump — the one moment it must not be postponed.
         */
        private final AtomicLong lastActivityNanos = new AtomicLong(System.nanoTime());
        private volatile ScheduledFuture<?> watchdog;

        StreamLease(InputStream upstream, Semaphore slots) {
            this.upstream = upstream;
            this.slots = slots;
        }

        InputStream stream() {
            return upstream;
        }

        void markActivity() {
            lastActivityNanos.set(System.nanoTime());
        }

        boolean isIdleFor(long millis) {
            return System.nanoTime() - lastActivityNanos.get() >= TimeUnit.MILLISECONDS.toNanos(millis);
        }

        boolean isReleased() {
            return released.get();
        }

        void armIdleWatchdog(java.util.concurrent.ScheduledExecutorService timers,
                             long checkEveryMs, long idleTimeoutMs) {
            this.watchdog = timers.scheduleWithFixedDelay(() -> {
                if (isIdleFor(idleTimeoutMs)) {
                    release();
                }
            }, checkEveryMs, checkEveryMs, TimeUnit.MILLISECONDS);
        }

        /**
         * Closing the upstream stream is what unblocks a pump parked on a read that will never return —
         * the JDK's stream has no read deadline of its own — so the close must happen before the permit
         * goes back, or the slot would be reusable while the old exchange is still open.
         */
        void release() {
            if (!released.compareAndSet(false, true)) {
                return;
            }
            ScheduledFuture<?> alarm = watchdog;
            if (alarm != null) {
                alarm.cancel(false);
            }
            try {
                if (upstream != null) {
                    upstream.close();
                }
            } catch (IOException | RuntimeException ignored) {
                // Best effort. The exchange is being abandoned either way, and a failure to close must
                // never be the reason the permit is not returned.
            } finally {
                slots.release();
            }
        }
    }

    /**
     * Copy upstream bytes to the client verbatim, flushing each chunk, then release the lease.
     *
     * <p>Byte-level copying is the point: the upstream implements a strict replay/resume contract in its
     * {@code id:} lines, and the client's next {@code Last-Event-ID} must be an id the upstream itself
     * issued. Parsing and re-emitting events here would put a second author on that sequence — one
     * dropped or renumbered id and the upstream sees a gap or a future id and answers the reconnect with
     * a full reset snapshot. So nothing is parsed and nothing is buffered beyond one 8 KiB chunk.
     *
     * <p>A client that goes away makes the write (or its flush) throw; a client that vanishes without an
     * RST is noticed on the next heartbeat, because the heartbeat is what gives us a write to fail on.
     * An upstream that goes silent produces neither, which is what the idle watchdog is for.
     */
    private static StreamingResponseBody pump(StreamLease lease) {
        return out -> {
            try {
                InputStream in = lease.stream();
                byte[] chunk = new byte[8192];
                int n;
                while ((n = in.read(chunk)) != -1) {
                    if (n > 0) {
                        lease.markActivity();
                        out.write(chunk, 0, n);
                        out.flush();
                    }
                }
            } catch (IOException | UncheckedIOException clientOrUpstreamGone) {
                // Normal termination for SSE: the client navigated away, the upstream ended the stream,
                // or the watchdog closed a stream that had gone silent. None of those is a server error.
            } finally {
                lease.release();
            }
        };
    }

    // ------------------------------------------------------------------ helpers

    /**
     * Is this really an event stream? A MISSING content type is accepted — the endpoint's contract
     * says the 200 is a stream and some intermediaries strip it — but a type that is present and
     * says something else is a contradiction, not an omission.
     */
    private static boolean isEventStream(String raw) {
        if (raw == null || raw.isBlank()) {
            return true;
        }
        MediaType parsed = mediaType(raw, null);
        return parsed == null || MediaType.TEXT_EVENT_STREAM.isCompatibleWith(parsed);
    }

    private static void closeQuietly(InputStream in) {
        if (in == null) {
            return;
        }
        try {
            in.close();
        } catch (IOException ignored) {
            // The exchange is being abandoned either way.
        }
    }

    /** Measures exactly what goes on the wire: the symbol is not trimmed anywhere on this path. */
    private boolean tooLong(String symbol) {
        return symbol != null && symbol.length() > StockGexUpstream.MAX_SYMBOL_LENGTH;
    }

    private String unreachableError(StockGexUpstream.UnavailableException cause) {
        return StockGexUpstream.CODE_PROTOCOL.equals(cause.code())
                ? error(StockGexUpstream.CODE_PROTOCOL,
                        "stock-gex-service did not return a usable response")
                : error(StockGexUpstream.CODE_UNAVAILABLE, "stock-gex-service is unreachable");
    }

    private StockGexRefusal refusal(HttpStatus status, String json, String retryAfter) {
        return new StockGexRefusal(status.value(), MediaType.APPLICATION_JSON_VALUE,
                json.getBytes(StandardCharsets.UTF_8), retryAfter);
    }

    /**
     * One line per outage window, not one per request. A proxy that logs nothing gives an operator no
     * way to tell "the board page is broken" from "the board service is down", but a 502 is per-request
     * and a down upstream would otherwise write a line for every poll of every open tab.
     */
    private void logUnreachable(String endpoint, StockGexUpstream.UnavailableException cause) {
        long now = System.currentTimeMillis();
        long previous = lastUnreachableLogMs.get();
        if (now - previous < UNREACHABLE_LOG_INTERVAL_MS
                || !lastUnreachableLogMs.compareAndSet(previous, now)) {
            return;
        }
        Throwable root = cause.getCause() == null ? cause : cause.getCause();
        // Message only, no stack trace: this is an expected operational state, not a defect.
        System.out.println("stock-gex " + endpoint + " 502 " + cause.code() + ": "
                + cause.getMessage() + " (" + root.getClass().getSimpleName() + ")");
    }

    /**
     * Build the gateway's own error envelope with Jackson rather than string concatenation, and never
     * include the upstream exception: a stack trace or a raw connect error names internal hosts and ports
     * to whoever opened the page.
     *
     * <p>The SHAPE matches what the stock-gex service itself emits — {@code error} carries the CODE and
     * {@code detail} the human sentence — because the page has exactly one error reader and a
     * gateway-authored envelope in a different shape would be read as an unrecognised failure. {@code
     * code} is emitted as well, redundantly, so a reader written against either convention finds the
     * token rather than a sentence.
     */
    private String error(String code, String message) {
        ObjectNode node = mapper.createObjectNode();
        node.put("error", code);
        node.put("code", code);
        node.put("detail", message);
        try {
            return mapper.writeValueAsString(node);
        } catch (JsonProcessingException impossible) {
            // Cannot happen for a flat node of three strings, but an unparseable body must never be the
            // failure mode of an error path — the client would then fail to read WHY it failed.
            return "{\"error\":\"" + code + "\",\"code\":\"" + code
                    + "\",\"detail\":\"stock-gex request failed\"}";
        }
    }

    private static ResponseEntity<byte[]> json(HttpStatus status, String body, String retryAfter) {
        ResponseEntity.BodyBuilder out = ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.CACHE_CONTROL, "no-store");
        if (retryAfter != null) {
            out = out.header(HttpHeaders.RETRY_AFTER, retryAfter);
        }
        return out.body(body.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Honour the upstream's content type when it is a CONCRETE one, else the caller's expectation.
     *
     * <p>"Parseable" is not the same as "usable". A wildcard type ({@code &#42;/&#42;}) or a wildcard
     * subtype ({@code application/&#42;}) both parse happily and are then rejected by
     * {@link ResponseEntity.BodyBuilder#contentType} with an
     * {@link IllegalArgumentException} — which, after the upstream response has already been read
     * successfully, escapes as a 500 with a stack trace. Worse, it can throw from inside
     * {@link #refused}, replacing an upstream 422 or 429 with a 500 while HANDLING it. A wildcard is
     * not a description of a body anyway, so it is treated exactly like a missing one.
     */
    private static MediaType mediaType(String raw, MediaType fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            MediaType parsed = MediaType.parseMediaType(raw);
            if (parsed.isWildcardType() || parsed.isWildcardSubtype()) {
                return fallback;
            }
            return parsed;
        } catch (IllegalArgumentException malformed) {
            // InvalidMediaTypeException extends IllegalArgumentException, so this covers both the
            // unparseable case and anything else the parser rejects outright.
            return fallback;
        }
    }
}

package app.feedgateway.contexttape;

import app.feedgateway.liquidityhistory.LiquidityHistoryAuth;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicLong;

/**
 * {@code GET /api/context-tape/session} — the web UI's door to the standalone
 * {@code context-tape-service}: one plain JSON snapshot (~150 KB), one request/response. No SSE, no
 * streaming — the page polls.
 *
 * <p><b>Status codes are a contract, not noise.</b> The upstream's {@code 503 {"error":"WARMING"}}
 * (with its own {@code Retry-After}) means "the backfill is still running, come back in a moment", and
 * the page renders a warming state for it. So this proxy forwards the upstream status AND body
 * byte-for-byte. The only statuses this class authors are 401/403 (auth, before anything else
 * happens), {@code 429 RATE_LIMITED} for a caller over its per-principal budget,
 * {@code 503 GATEWAY_BUSY} when the in-flight bulkhead is full, and 502 when the service cannot be
 * reached or did not answer with a usable response — deliberately 502 rather than 503, so "the
 * gateway could not reach context-tape" stays distinguishable from the upstream's own WARMING 503
 * (and GATEWAY_BUSY carries its own error token for the same reason).
 *
 * <p>Bearer auth reuses {@link LiquidityHistoryAuth}, exactly as {@code /api/stock-gex} and
 * {@code /api/gamma-migration} do — this is a data endpoint and stays behind the same guard as every
 * other one. This gateway deliberately has no servlet-security filter chain (see the pom comment), so
 * "authenticated" here means calling the shared verifier first, which the handler does before touching
 * the upstream. And like {@code /api/pin-flow}, this route FAILS CLOSED: when neither auth mode is
 * enabled the shared verifier serves an authenticated "anonymous" principal (local-dev fallback), so
 * {@link LiquidityHistoryAuth#enforcing()} is checked first and the route answers 401 rather than
 * serving session data unauthenticated.
 *
 * <p><b>Why there IS a rate limiter here</b> (unlike {@code /api/stock-gex/board}): the stock-gex
 * upstream owns its own request-capacity vocabulary (429 MAX_SYMBOLS etc.), so a second gateway 429
 * would be indistinguishable from it. The context-tape service has no such vocabulary — its only
 * non-200 is WARMING — so the gateway-side per-principal budget is the only thing standing between a
 * misbehaving poller and a ~150 KB body being re-read from the upstream in a tight loop. Same
 * fixed-window shape as {@code /api/gamma-migration}, at half the budget because the body is ~150 KB
 * rather than a one-line cache read. Both gateway-authored refusals are SELF-DESCRIBING JSON so the
 * page never has to guess at a bare status: {@code 429 {"error":"RATE_LIMITED"}} with a
 * {@code Retry-After}, and {@code 503 {"error":"GATEWAY_BUSY"}} (distinguishable from the upstream's
 * own 503 by its {@code WARMING} body) when the concurrency bulkhead below is full.
 *
 * <p><b>And why there is a CONCURRENCY bulkhead as well.</b> The rate limiter counts requests per
 * principal per window; it says nothing about how many are IN FLIGHT at once. Each session call
 * occupies a Tomcat worker thread synchronously for up to the whole request budget (10s), and those
 * workers are shared with every other endpoint on this gateway — a handful of principals each opening
 * their full window budget simultaneously against a slow upstream would take unrelated market-data
 * endpoints down with them. Concurrency is a gateway-local resource the upstream cannot see (the same
 * argument as the stock-gex stream cap), so in-flight calls are capped and the overflow is answered
 * immediately rather than queued on a saturating pool.
 */
@RestController
public class ContextTapeController {

    /** One snapshot a second with headroom; the page polls far less often than this. */
    private static final int RATE_LIMIT_PER_MIN = 60;

    /**
     * Max CONCURRENT in-flight session calls this gateway will carry. Each holds one Tomcat worker
     * thread for up to the whole request budget, and those workers are shared with every other
     * endpoint here — this cap, not the per-principal window, is what bounds the JVM. Sized well
     * below Tomcat's default 200 workers so a slow upstream degrades this page, never the gateway.
     */
    static final int MAX_CONCURRENT_SESSIONS = 16;

    /**
     * Cardinality cap on the rate limiter's per-principal state. Real deployments have a handful of
     * principals; this exists so an auth tier minting many distinct subjects can never turn the
     * limiter into an unbounded memory leak.
     */
    static final int MAX_TRACKED_PRINCIPALS = 10_000;

    /** Never log an unreachable-upstream failure more than this often: an outage is per-request. */
    private static final long UNREACHABLE_LOG_INTERVAL_MS = 30_000L;

    /**
     * Counters, each incremented ONCE at the transition that authorises it, exposed on the same
     * throttled operator log line as the stock-gex proxy's (this gateway's established path for
     * proxy counters). No principal or upstream text appears in any of these — a per-caller counter
     * is an unbounded label set.
     *
     * <p>{@code SESSIONS_SERVED} counts ONLY forwarded 200s — an actual snapshot in a browser.
     * Forwarded non-200s are counted separately: WARMING (the upstream's one contracted 503) apart
     * from any other upstream status, so an operator can tell "the service is warming" from "the
     * service is answering errors" at a glance.
     */
    static final AtomicLong SESSIONS_SERVED = new AtomicLong();
    static final AtomicLong WARMING_FORWARDED = new AtomicLong();
    static final AtomicLong UPSTREAM_ERRORS_FORWARDED = new AtomicLong();
    static final AtomicLong RATE_LIMITED = new AtomicLong();
    static final AtomicLong REFUSED_AT_CAP = new AtomicLong();
    static final AtomicLong UPSTREAM_UNREACHABLE = new AtomicLong();
    static final AtomicLong UPSTREAM_PROTOCOL_FAULTS = new AtomicLong();

    private final ContextTapeUpstream upstream;
    private final LiquidityHistoryAuth auth;
    private final ObjectMapper mapper;
    private final RateLimiter rateLimiter;
    /** Package-visible so a test can exhaust it. */
    final java.util.concurrent.Semaphore sessionSlots;
    private final AtomicLong lastUnreachableLogMs = new AtomicLong(0L);

    @org.springframework.beans.factory.annotation.Autowired
    public ContextTapeController(ContextTapeUpstream upstream, LiquidityHistoryAuth auth,
                                 ObjectMapper mapper) {
        this(upstream, auth, mapper, RATE_LIMIT_PER_MIN, MAX_CONCURRENT_SESSIONS);
    }

    /** Test seam: an explicit budget, so the 429 path can be exercised without 60 warm-up calls. */
    ContextTapeController(ContextTapeUpstream upstream, LiquidityHistoryAuth auth, ObjectMapper mapper,
                          int rateLimitPerMinute) {
        this(upstream, auth, mapper, rateLimitPerMinute, MAX_CONCURRENT_SESSIONS);
    }

    /** Test seam: explicit budget AND concurrency cap, so the bulkhead can be saturated cheaply. */
    ContextTapeController(ContextTapeUpstream upstream, LiquidityHistoryAuth auth, ObjectMapper mapper,
                          int rateLimitPerMinute, int maxConcurrentSessions) {
        this.upstream = upstream;
        this.auth = auth;
        this.mapper = mapper == null ? new ObjectMapper() : mapper;
        this.rateLimiter = new RateLimiter(rateLimitPerMinute, 60_000L, MAX_TRACKED_PRINCIPALS);
        this.sessionSlots = new java.util.concurrent.Semaphore(maxConcurrentSessions);
    }

    /**
     * One line an operator can read — including the upstream's cleanup-health counters, because this
     * throttled log line is where this gateway's proxy counters are exposed (same convention as the
     * stock-gex proxy) and an abandonment or a client recycle is exactly what an operator must see.
     */
    String counters() {
        return "context-tape gateway: sessionsServed=" + SESSIONS_SERVED.get()
                + " warmingForwarded=" + WARMING_FORWARDED.get()
                + " upstreamErrorsForwarded=" + UPSTREAM_ERRORS_FORWARDED.get()
                + " rateLimited=" + RATE_LIMITED.get()
                + " refusedAtCap=" + REFUSED_AT_CAP.get()
                + " sessionSlotsFree=" + sessionSlots.availablePermits()
                + " upstreamUnreachable=" + UPSTREAM_UNREACHABLE.get()
                + " upstreamProtocolFaults=" + UPSTREAM_PROTOCOL_FAULTS.get()
                + " abandonedDisposals=" + ContextTapeUpstream.DISPOSALS_ABANDONED.get()
                + " clientRecycles=" + ContextTapeUpstream.CLIENT_RECYCLES.get();
    }

    /**
     * No {@code produces} condition on purpose (same reason as the stock-gex board handler): the body
     * is an upstream passthrough whose content type is whatever the upstream chose, so pinning one
     * media type here would both misdescribe the endpoint and 406 a client whose {@code Accept} did not
     * name it.
     */
    @GetMapping("/api/context-tape/session")
    public ResponseEntity<byte[]> session(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        // ---- Fail closed (mirrors /api/pin-flow): the shared LiquidityHistoryAuth serves an
        // authenticated "anonymous" principal when WS auth is globally disabled (local dev) — that
        // fallback would leave this endpoint serving session data UNauthenticated. This route must be
        // authenticated regardless of the global switch, so if auth is not actually enforcing a real
        // verified token we reject with 401 before invoking the verifier or doing any work. ----
        if (!auth.enforcing()) {
            return unauthorized("authentication required");
        }
        LiquidityHistoryAuth.Result authResult = auth.authenticate(authorization);
        if (authResult.status() != 200) {
            return ResponseEntity.status(authResult.status()).build();
        }
        long retryAfterSeconds = rateLimiter.tryAcquire(authResult.principal(), System.currentTimeMillis());
        if (retryAfterSeconds > 0) {
            RATE_LIMITED.incrementAndGet();
            // Self-describing, not a bare status: the page reads the error token, and Retry-After
            // says when to come back.
            return json(HttpStatus.TOO_MANY_REQUESTS, error("RATE_LIMITED",
                    "this caller is over its per-minute session budget"),
                    Long.toString(retryAfterSeconds));
        }
        // Refuse BEFORE calling upstream: an in-flight session call holds a Tomcat worker for up to
        // the whole request budget, and those workers are shared with every other endpoint here.
        // Answering the overflow immediately is strictly better than queueing it on a saturating pool.
        if (!sessionSlots.tryAcquire()) {
            REFUSED_AT_CAP.incrementAndGet();
            return json(HttpStatus.SERVICE_UNAVAILABLE, error("GATEWAY_BUSY",
                    "this gateway is already carrying its maximum number of in-flight session calls"),
                    "1");
        }
        ContextTapeUpstream.SessionResponse response;
        try {
            response = upstream.session();
        } catch (ContextTapeUpstream.UnavailableException unreachable) {
            logUnreachable(unreachable);
            // Retry-After on the gateway's own 502s too — the contract puts it on EVERY gateway
            // addition, and "the service was unreachable just now" is exactly a retry-in-a-moment.
            return json(HttpStatus.BAD_GATEWAY, unreachableError(unreachable), "5");
        } finally {
            sessionSlots.release();
        }
        ResponseEntity.BodyBuilder out = ResponseEntity.status(response.status())
                .contentType(mediaType(response.contentType(), MediaType.APPLICATION_JSON))
                // A snapshot is a live measurement of a moving market. Nothing on the path — browser,
                // ingress, corporate proxy — may ever replay one as if it were current.
                .header(HttpHeaders.CACHE_CONTROL, "no-store");
        if (response.retryAfter() != null) {
            // The upstream's own backoff advice for its WARMING 503. Dropping it would leave the page
            // to guess a retry interval the service already told us.
            out = out.header(HttpHeaders.RETRY_AFTER, response.retryAfter());
        }
        // Counted at the forward, by WHAT is being forwarded: only a 200 is a session served, and
        // only the contracted {"error":"WARMING"} body is warming — a 503 carrying anything else is
        // a failure the UI renders as one, and the operator counter must not disagree with the UI.
        if (response.status() == 200) {
            SESSIONS_SERVED.incrementAndGet();
        } else if (response.status() == 503 && isWarmingBody(response.body())) {
            WARMING_FORWARDED.incrementAndGet();
        } else {
            UPSTREAM_ERRORS_FORWARDED.incrementAndGet();
        }
        return out.body(response.body() == null ? new byte[0] : response.body());
    }

    /** True only for the upstream's contracted warming envelope: JSON with {@code error == "WARMING"}. */
    private boolean isWarmingBody(byte[] body) {
        if (body == null || body.length == 0) {
            return false;
        }
        try {
            return "WARMING".equals(mapper.readTree(body).path("error").asText());
        } catch (java.io.IOException notJson) {
            return false;
        }
    }

    // ------------------------------------------------------------------ helpers

    /** Pin-flow's fail-closed refusal shape: 401 with a short JSON reason, no envelope codes. */
    private ResponseEntity<byte[]> unauthorized(String message) {
        ObjectNode node = mapper.createObjectNode();
        node.put("error", message); // short, no stack/config detail
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .contentType(MediaType.APPLICATION_JSON)
                .body(node.toString().getBytes(StandardCharsets.UTF_8));
    }

    private String unreachableError(ContextTapeUpstream.UnavailableException cause) {
        return ContextTapeUpstream.CODE_PROTOCOL.equals(cause.code())
                ? error(ContextTapeUpstream.CODE_PROTOCOL,
                        "context-tape-service did not return a usable response")
                : error(ContextTapeUpstream.CODE_UNAVAILABLE, "context-tape-service is unreachable");
    }

    /**
     * One line per outage window, not one per request. A proxy that logs nothing gives an operator no
     * way to tell "the tape page is broken" from "the tape service is down", but a 502 is per-request
     * and a down upstream would otherwise write a line for every poll of every open tab.
     */
    private void logUnreachable(ContextTapeUpstream.UnavailableException cause) {
        if (ContextTapeUpstream.CODE_PROTOCOL.equals(cause.code())) {
            UPSTREAM_PROTOCOL_FAULTS.incrementAndGet();
        } else {
            UPSTREAM_UNREACHABLE.incrementAndGet();
        }
        long now = System.currentTimeMillis();
        long previous = lastUnreachableLogMs.get();
        if (now - previous < UNREACHABLE_LOG_INTERVAL_MS
                || !lastUnreachableLogMs.compareAndSet(previous, now)) {
            return;
        }
        Throwable root = cause.getCause() == null ? cause : cause.getCause();
        // Message only, no stack trace: this is an expected operational state, not a defect. The
        // root message can originate remotely, so it is truncated and flattened to one line — a log
        // line is not a place to paste unbounded input from another service.
        System.out.println("context-tape session 502 " + cause.code() + ": "
                + cause.getMessage() + " (" + root.getClass().getSimpleName() + ": "
                + shortForLog(root.getMessage()) + ") " + counters());
    }

    /** Remote text on a log line is unbounded input; this bounds it. */
    static String shortForLog(String raw) {
        if (raw == null) {
            return "";
        }
        String oneLine = raw.replaceAll("[\\r\\n]", " ");
        return oneLine.length() <= 120 ? oneLine : oneLine.substring(0, 120) + "…";
    }

    /**
     * Build the gateway's own error envelope with Jackson rather than string concatenation, and never
     * include the upstream exception: a stack trace or a raw connect error names internal hosts and
     * ports to whoever opened the page.
     *
     * <p>The SHAPE matches the stock-gex proxy's envelope — {@code error} carries the CODE and
     * {@code detail} the human sentence, with {@code code} emitted redundantly — so a reader written
     * against either convention finds the token rather than a sentence.
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
                    + "\",\"detail\":\"context-tape request failed\"}";
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
     * {@link ResponseEntity.BodyBuilder#contentType} with an {@link IllegalArgumentException} — which,
     * after the upstream response has already been read successfully, escapes as a 500 with a stack
     * trace. A wildcard is not a description of a body anyway, so it is treated exactly like a missing
     * one.
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

    /**
     * Fixed-window per-principal budget, mirroring the gamma-migration limiter — plus the properties
     * a long-lived proxy needs and a page-scoped endpoint could ignore:
     *
     * <ul>
     *   <li><b>Bounded cardinality.</b> Every authenticated subject creates one entry, and an auth
     *       tier minting many distinct subjects must not be able to grow this map for the JVM's
     *       lifetime. When the cap is reached, expired entries are evicted; a NEW principal that
     *       still does not fit is refused (fail closed, briefly) rather than tracked unboundedly or
     *       waved through untracked.</li>
     *   <li><b>Expiry eviction.</b> An entry idle for two full windows carries no budget information
     *       any more and is reclaimable.</li>
     *   <li><b>Clock regression.</b> {@code System.currentTimeMillis()} can step backwards (NTP, a
     *       resumed VM). A window that starts in the future would otherwise make the elapsed time
     *       negative — never rolling, and computing a Retry-After larger than the window itself. The
     *       window start is clamped to "now", which extends the current window by the size of the
     *       step: the fail-SAFE direction for a limiter (never a fresh budget out of a clock jump).</li>
     * </ul>
     */
    static final class RateLimiter {
        private final int limit;
        private final long windowMs;
        private final int maxPrincipals;
        /**
         * ONE lock for everything — lookup, window math, admission, eviction. A split design
         * (concurrent map + per-state locks) was measurably not linearizable: at the cap, racing
         * newcomers could each pass the size check and overshoot it, and a caller could keep using
         * an entry eviction had just removed while a replacement was created — one principal, two
         * live budgets. The critical section here is a hash lookup and four longs of arithmetic;
         * serialising that is nanoseconds, and correctness of a CAP is exactly what a single owner
         * buys. Guarded by the intrinsic lock on {@code this}.
         */
        private final java.util.HashMap<String, long[]> counters = new java.util.HashMap<>();
        /** Earliest time the next full eviction sweep may run; bounds the sweep to once per window. */
        private long nextSweepMs = Long.MIN_VALUE;

        RateLimiter(int limit, long windowMs) {
            this(limit, windowMs, MAX_TRACKED_PRINCIPALS);
        }

        RateLimiter(int limit, long windowMs, int maxPrincipals) {
            this.limit = limit;
            this.windowMs = windowMs;
            this.maxPrincipals = maxPrincipals;
        }

        /** 0 when allowed, else the Retry-After the caller should be given. */
        synchronized long tryAcquire(String principal, long nowMs) {
            String key = principal == null ? "anonymous" : principal;
            long[] state = counters.get(key);
            if (state == null) {
                if (counters.size() >= maxPrincipals) {
                    // At most ONE full sweep per window, however many newcomers arrive at the cap:
                    // an O(n) pass per refused caller would be a CPU amplification path that sits
                    // in front of the bulkhead.
                    if (nowMs < nextSweepMs - windowMs) {
                // wall-clock regression: the scheduled sweep deadline is now in
                // OUR future by more than a window — re-arm it so eviction and
                // future-start clamping are not suppressed for the regression
                // interval (r9 F3)
                nextSweepMs = nowMs;
            }
            if (nowMs >= nextSweepMs) {
                        counters.values().removeIf(s -> {
                            if (s[0] > nowMs) {
                                // A window start in the future means the CLOCK regressed, not that
                                // the entry is stale. Evicting it would erase a saturated
                                // principal's count and hand it a fresh budget on reinsertion —
                                // the exact thing the clamp in the admission path promises never
                                // happens. Clamp the start, KEEP the count.
                                s[0] = nowMs;
                                return false;
                            }
                            return nowMs - s[0] >= 2 * windowMs;
                        });
                        nextSweepMs = nowMs + windowMs;
                    }
                    if (counters.size() >= maxPrincipals) {
                        // Every tracked principal is LIVE and the map is full. Refusing the
                        // newcomer briefly is the only answer that neither grows the map nor
                        // exempts the newcomer from limiting.
                        return 1L;
                    }
                }
                state = new long[]{nowMs, 0};
                counters.put(key, state);
            }
            if (nowMs < state[0]) {
                state[0] = nowMs; // clock stepped backwards: clamp, never a window in the future
            }
            if (nowMs - state[0] >= windowMs) {
                state[0] = nowMs;
                state[1] = 0;
            }
            if (state[1] >= limit) {
                return Math.max(1L, (windowMs - (nowMs - state[0]) + 999) / 1000);
            }
            state[1]++;
            return 0L;
        }

        /** For tests: the current tracked-principal cardinality. */
        synchronized int trackedPrincipals() {
            return counters.size();
        }
    }
}

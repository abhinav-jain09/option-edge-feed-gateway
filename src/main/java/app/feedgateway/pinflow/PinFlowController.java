package app.feedgateway.pinflow;

import app.feedgateway.GatewaySettings;
import app.feedgateway.liquidityhistory.LiquidityHistoryAuth;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.async.DeferredResult;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.List;
import java.util.concurrent.RejectedExecutionException;

/**
 * {@code GET /api/pin-flow?date=YYYY-MM-DD&lo=<int>&hi=<int>} — the §5 Flow-Explorer session endpoint.
 *
 * <p><b>Auth (§4):</b> enforced by the SAME shared {@link LiquidityHistoryAuth} bean that protects
 * {@code /api/liquidity-history} — NOT a controller-local re-implementation. The gateway has no Spring
 * Security filter chain (see the pom note: adding servlet security would lock down every endpoint), so
 * "authenticated" means this handler invokes the shared token verifier before doing any work, and
 * "public" would mean skipping it. This endpoint is never public and is in no allowlist.
 *
 * <p><b>Bulkhead (§6.1):</b> all DB work runs on the dedicated {@link PinFlowExecutor} via
 * {@link DeferredResult}; the shared MVC/WS worker returns immediately. Executor saturation → fast 503.
 *
 * <p><b>Status codes (§5.3):</b> 400 bad params / band / cell-cap / impossible date; 401/403 auth;
 * 503 DB disabled/unavailable/timeout/pool-exhausted/bulkhead-saturated/rate-limited; 500 reserved
 * for unexpected defects only. Error bodies are short JSON with no stack/creds/JDBC metadata.
 */
@RestController
public class PinFlowController {

    // §5.2 correctness + resource guards.
    private static final int MIN_LO = 100;
    private static final int MAX_HI = 100_000;
    private static final int MAX_BAND_WIDTH = 1_000;   // ≤ 201 strikes on the inclusive 5-pt grid
    private static final long MAX_CELLS = 400L * 250L;  // frames × strikes hard cap
    private static final DateTimeFormatter STRICT_ISO =
            DateTimeFormatter.ofPattern("uuuu-MM-dd").withResolverStyle(ResolverStyle.STRICT);

    private final PinFlowStore store;
    private final PinFlowExecutor bulkhead;
    private final LiquidityHistoryAuth auth;
    private final ObjectMapper mapper;
    private final Clock clock;
    private final int defaultLo;
    private final int defaultHi;
    private final long deadlineMs;
    private final int bandMargin;
    private final PinFlowController.RateLimiter rateLimiter;

    @org.springframework.beans.factory.annotation.Autowired
    public PinFlowController(GatewaySettings settings, PinFlowStore store, PinFlowExecutor bulkhead,
                             LiquidityHistoryAuth auth, ObjectMapper mapper) {
        this(store, bulkhead, auth, mapper, Clock.systemUTC(),
                settings.pinFlowStrikeLo(), settings.pinFlowStrikeHi(),
                settings.pinFlowRequestDeadlineMs(), settings.pinFlowRateLimitPerMin(),
                settings.pinFlowBandMargin());
    }

    /** Test seam: injectable clock, band defaults, deadline, rate limit. */
    PinFlowController(PinFlowStore store, PinFlowExecutor bulkhead, LiquidityHistoryAuth auth,
                      ObjectMapper mapper, Clock clock, int defaultLo, int defaultHi,
                      long deadlineMs, int ratePerMinute) {
        this(store, bulkhead, auth, mapper, clock, defaultLo, defaultHi, deadlineMs, ratePerMinute, 0);
    }

    /** Test seam with spot-derived band margin (0 = keep the fixed default band). */
    PinFlowController(PinFlowStore store, PinFlowExecutor bulkhead, LiquidityHistoryAuth auth,
                      ObjectMapper mapper, Clock clock, int defaultLo, int defaultHi,
                      long deadlineMs, int ratePerMinute, int bandMargin) {
        this.bandMargin = bandMargin;
        this.store = store;
        this.bulkhead = bulkhead;
        this.auth = auth;
        this.mapper = mapper;
        this.clock = clock;
        this.defaultLo = defaultLo;
        this.defaultHi = defaultHi;
        this.deadlineMs = deadlineMs;
        this.rateLimiter = new RateLimiter(ratePerMinute, 60_000L);
    }

    @GetMapping(value = "/api/pin-flow")
    public DeferredResult<ResponseEntity<byte[]>> pinFlow(
            @RequestParam(value = "date", required = false) String dateRaw,
            @RequestParam(value = "lo", required = false) String loRaw,
            @RequestParam(value = "hi", required = false) String hiRaw,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        DeferredResult<ResponseEntity<byte[]>> deferred = new DeferredResult<>(deadlineMs, timeout503());

        // ---- Auth (§4): pin-flow FAILS CLOSED. The shared LiquidityHistoryAuth serves an authenticated
        // "anonymous" principal when WS auth is globally disabled (local dev) — that fallback would leave
        // /api/pin-flow serving DB data UNauthenticated. The spec mandates this endpoint be authenticated
        // regardless of the global switch, so if auth is not actually enforcing a real verified token we
        // reject with 401 before invoking the verifier or doing any work. ----
        if (!auth.enforcing()) {
            deferred.setResult(unauthorized("authentication required"));
            return deferred;
        }

        // ---- Shared verifier, before any work. ----
        LiquidityHistoryAuth.Result authResult = auth.authenticate(authorization);
        if (authResult.status() != 200) {
            deferred.setResult(ResponseEntity.status(authResult.status()).build());
            return deferred;
        }

        // ---- §6.5 per-principal rate limit → 503 when exceeded. ----
        if (rateLimiter.exceeded(authResult.principal(), clock.millis())) {
            deferred.setResult(serviceUnavailable("rate limited"));
            return deferred;
        }

        // ---- §5.2/§5.3 param validation (400), synchronously, off the bulkhead. ----
        LocalDate date;
        try {
            date = dateRaw == null || dateRaw.isBlank()
                    ? currentSessionDate()
                    : LocalDate.parse(dateRaw, STRICT_ISO); // strict → impossible dates throw
        } catch (DateTimeParseException bad) {
            deferred.setResult(badRequest("date must be a real yyyy-MM-dd"));
            return deferred;
        }

        Integer lo = parseIntOrNull(loRaw);
        Integer hi = parseIntOrNull(hiRaw);
        if ((loRaw != null && !loRaw.isBlank() && lo == null)
                || (hiRaw != null && !hiRaw.isBlank() && hi == null)) {
            deferred.setResult(badRequest("lo/hi must be integers"));
            return deferred;
        }
        // Band: an explicit lo/hi always wins. When BOTH are omitted we DERIVE the band from the session's
        // actual spot travel (the fixed default does not follow the market: prod SPX ran 7435..7497 against
        // a 7490..7590 default, hiding nearly the whole session; ES hid every put below spot). The
        // derivation is a DB read, so it happens on the bulkhead thread below — never here on the MVC
        // thread — and its result is CLAMPED rather than 400'd, since a server-side default must never be
        // reported as a caller error.
        final boolean deriveBand = lo == null && hi == null && bandMargin > 0;
        int loVal = lo == null ? defaultLo : lo;
        int hiVal = hi == null ? defaultHi : hi;
        if (loVal > hiVal) { // §5.1 rule 8: swap if reversed
            int tmp = loVal;
            loVal = hiVal;
            hiVal = tmp;
        }
        if (loVal < MIN_LO || hiVal > MAX_HI || (hiVal - loVal) > MAX_BAND_WIDTH) {
            deferred.setResult(badRequest("band out of range"));
            return deferred;
        }

        // ---- DB not configured → 503 immediately (§5.3), no bulkhead work. ----
        if (!store.dbConfigured()) {
            deferred.setResult(serviceUnavailable("pin-flow database not configured"));
            return deferred;
        }

        // ---- §6.1 dispatch onto the dedicated bulkhead; saturation → fast 503. ----
        final int fLo = loVal;
        final int fHi = hiVal;
        final boolean fDerive = deriveBand;
        final LocalDate fDate = date;
        try {
            // §6.1 retain the Future so the deadline / client-disconnect can CANCEL the in-flight JDBC
            // work (interrupt → PinFlowStore watcher cancels the statement and frees the connection),
            // rather than leaving it to run for the full statement timeout and starve the bulkhead.
            final java.util.concurrent.Future<?> task = bulkhead.executor().submit(() -> {
                if (deferred.isSetOrExpired()) {
                    return; // client already gone / deadline elapsed
                }
                try {
                    int qLo = fLo;
                    int qHi = fHi;
                    if (fDerive) {
                        int[] derived = store.resolveBandFromSpot(fDate, bandMargin, clock.millis());
                        if (derived != null) {
                            // CLAMP (never 400): these bounds are ours, not the caller's.
                            int dLo = Math.max(MIN_LO, Math.min(derived[0], MAX_HI));
                            int dHi = Math.max(MIN_LO, Math.min(derived[1], MAX_HI));
                            if (dLo > dHi) {
                                int t = dLo;
                                dLo = dHi;
                                dHi = t;
                            }
                            if (dHi - dLo > MAX_BAND_WIDTH) {
                                // Keep it centred on the session's midpoint so we lose the edges, not the money.
                                int mid = dLo + (dHi - dLo) / 2;
                                dLo = Math.max(MIN_LO, mid - MAX_BAND_WIDTH / 2);
                                dHi = Math.min(MAX_HI, dLo + MAX_BAND_WIDTH);
                            }
                            qLo = dLo;
                            qHi = dHi;
                        }
                    }
                    PinFlowResponse response = store.query(fDate, qLo, qHi, clock.millis());
                    // §5.2 defensive cell cap (unreachable given the band cap, but enforced).
                    long cells = (long) response.t().size() * (long) response.k().size();
                    if (cells > MAX_CELLS) {
                        deferred.setResult(badRequest("response too large"));
                        return;
                    }
                    deferred.setResult(ResponseEntity.ok()
                            .contentType(MediaType.APPLICATION_JSON)
                            .body(serialize(response)));
                } catch (PinFlowStore.DbUnavailableException dbDown) {
                    deferred.setResult(serviceUnavailable("pin-flow data unavailable"));
                } catch (RuntimeException unexpected) {
                    // §5.3: 500 reserved for genuine defects only.
                    deferred.setErrorResult(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .contentType(MediaType.APPLICATION_JSON)
                            .body(errorJson("internal error")));
                }
            });
            // Deadline elapsed (503 already written) OR client disconnected → cancel(true) interrupts the
            // worker; the store's watcher then cancels the statement and closes the connection promptly.
            deferred.onTimeout(() -> task.cancel(true));
            deferred.onCompletion(() -> task.cancel(true));
        } catch (RejectedExecutionException saturated) {
            deferred.setResult(serviceUnavailable("pin-flow busy"));
        }
        return deferred;
    }

    private byte[] serialize(PinFlowResponse r) {
        ObjectNode root = mapper.createObjectNode();
        putStringArray(root.putArray("t"), r.t());
        putIntArray(root.putArray("sp"), r.sp());
        putIntArray(root.putArray("k"), r.k());
        ArrayNode cc = root.putArray("cc");
        for (List<Integer> row : r.cc()) {
            putIntArray(cc.addArray(), row);
        }
        ArrayNode cp = root.putArray("cp");
        for (List<Integer> row : r.cp()) {
            putIntArray(cp.addArray(), row);
        }
        putIntArray(root.putArray("lead"), r.lead());
        return root.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static void putStringArray(ArrayNode node, List<String> values) {
        for (String v : values) {
            node.add(v);
        }
    }

    private static void putIntArray(ArrayNode node, List<Integer> values) {
        for (Integer v : values) {
            node.add(v.intValue());
        }
    }

    /**
     * The date whose SESSION is currently in progress. For a same-day session (SPX 09:30 → 16:01) that
     * is simply today. For a MIDNIGHT-SPANNING session (ES 18:00 → 17:00 next day) the live session
     * started YESTERDAY whenever the clock is still before the start time — without this the default
     * date would point at a session that has not begun yet and the endpoint returned an empty payload.
     */
    private LocalDate currentSessionDate() {
        java.time.ZonedDateTime now = clock.instant().atZone(store.zone());
        LocalDate today = now.toLocalDate();
        if (store.sessionSpansMidnight() && now.toLocalTime().isBefore(store.sessionStart())) {
            return today.minusDays(1);
        }
        return today;
    }

    private static Integer parseIntOrNull(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException bad) {
            return null;
        }
    }

    private ResponseEntity<byte[]> badRequest(String message) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_JSON)
                .body(errorJson(message));
    }

    private ResponseEntity<byte[]> unauthorized(String message) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .contentType(MediaType.APPLICATION_JSON)
                .body(errorJson(message));
    }

    private ResponseEntity<byte[]> serviceUnavailable(String message) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .contentType(MediaType.APPLICATION_JSON)
                .body(errorJson(message));
    }

    private ResponseEntity<byte[]> timeout503() {
        return serviceUnavailable("pin-flow timed out");
    }

    private byte[] errorJson(String message) {
        ObjectNode body = mapper.createObjectNode();
        body.put("error", message); // short, no stack/creds/JDBC metadata (§5.2)
        return body.toString().getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Minimal per-principal sliding-window rate limiter (§6.5 → 503 on breach). In-memory / single-node,
     * valid under the same single-replica deployment invariant as the WS gateway.
     */
    static final class RateLimiter {
        private final int limit;
        private final long windowMs;
        private final java.util.Map<String, java.util.ArrayDeque<Long>> byPrincipal = new java.util.HashMap<>();

        RateLimiter(int limit, long windowMs) {
            this.limit = limit;
            this.windowMs = windowMs;
        }

        synchronized boolean exceeded(String principal, long nowMs) {
            java.util.ArrayDeque<Long> window =
                    byPrincipal.computeIfAbsent(principal, p -> new java.util.ArrayDeque<>());
            while (!window.isEmpty() && nowMs - window.peekFirst() >= windowMs) {
                window.pollFirst();
            }
            if (window.size() >= limit) {
                return true;
            }
            window.addLast(nowMs);
            if (byPrincipal.size() > 10_000) {
                byPrincipal.entrySet().removeIf(e -> e.getValue().isEmpty()
                        || nowMs - e.getValue().peekLast() >= windowMs);
            }
            return false;
        }
    }
}

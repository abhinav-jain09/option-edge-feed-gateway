package app.feedgateway.gammamigration;

import app.feedgateway.FeedGatewayService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Locale;

/**
 * {@code GET /api/gamma-migration?symbol&expiry} — the latest gamma-migration reading for one chain.
 *
 * <p>Exists so the standalone Gamma Migration page can be REST-driven like Risk Reversal and
 * Seller Activity rather than opening a second gateway websocket. The option chain still receives
 * the same data as the live {@code gamma-migration} event; both read ONE cache, so the page and
 * the chain can never disagree about which strike is hot.
 *
 * <p>The cached record is served <b>verbatim</b>. Reshaping it here would create a second,
 * silently-drifting view of a contract that already has one owner (the Avro schema), and every
 * field the page needs — regime, dwell, mass balance, hot strike, flips — is already on it.
 *
 * <p>Bearer auth reuses {@link LiquidityHistoryAuth}, exactly as {@code /api/seller-activity} does:
 * invalid/expired → 401, valid-but-unentitled → 403. No concurrency semaphore: this is a single
 * map lookup returning an already-serialised string, not a parse-and-aggregate.
 */
@RestController
public class GammaMigrationController {

    /** Matches the seller-activity budget: same class of cheap authenticated read. */
    private static final int RATE_LIMIT_PER_MIN = 120;

    private final FeedGatewayService service;
    private final LiquidityHistoryAuth auth;
    private final ObjectMapper mapper;
    private final RateLimiter rateLimiter = new RateLimiter(RATE_LIMIT_PER_MIN, 60_000L);

    public GammaMigrationController(FeedGatewayService service, LiquidityHistoryAuth auth,
                                    ObjectMapper mapper) {
        this.service = service;
        this.auth = auth;
        this.mapper = mapper == null ? new ObjectMapper() : mapper;
    }

    @GetMapping(value = "/api/gamma-migration", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> gammaMigration(
            @RequestParam(value = "symbol", required = false) String symbol,
            @RequestParam(value = "expiry", required = false) String expiry,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        LiquidityHistoryAuth.Result authResult = auth.authenticate(authorization);
        if (authResult.status() != 200) {
            return ResponseEntity.status(authResult.status()).build();
        }
        long retryAfterSeconds = rateLimiter.tryAcquire(authResult.principal(), System.currentTimeMillis());
        if (retryAfterSeconds > 0) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .header(HttpHeaders.RETRY_AFTER, Long.toString(retryAfterSeconds)).build();
        }
        // Default to the chain the app is already on. Requiring both params meant the page only
        // worked when the user hand-typed a query string, and opening /gamma-migration plainly
        // answered 400 — the page is about "the current board", so the current board is the
        // sensible default rather than an error.
        if (symbol == null || symbol.isBlank() || expiry == null || expiry.isBlank()) {
            String[] active = service.activeSymbolExpiry();
            if (symbol == null || symbol.isBlank()) {
                symbol = active[0];
            }
            if (expiry == null || expiry.isBlank()) {
                expiry = active[1];
            }
        }
        if (symbol == null || symbol.isBlank() || expiry == null || expiry.isBlank()) {
            // Only when the gateway itself has no selection yet — a genuine "nothing to show".
            return badRequest("no symbol/expiry given and the gateway has no active selection yet");
        }

        String cached = service.cachedGammaMigration(symbol, expiry);
        if (cached == null || cached.isBlank()) {
            // 200 with an explicit absence, not 404. "The service has not published for this chain
            // yet" is a normal state on a cold start or a thin board, and a page that renders
            // "warming up" for it must be able to tell that apart from a routing mistake.
            ObjectNode absent = mapper.createObjectNode();
            absent.put("present", false);
            absent.put("symbol", symbol.trim().toUpperCase(Locale.ROOT));
            absent.put("expiry", expiry.trim());
            return ResponseEntity.ok(write(absent));
        }
        return ResponseEntity.ok(cached);
    }

    private ResponseEntity<String> badRequest(String message) {
        ObjectNode error = mapper.createObjectNode();
        error.put("error", message);
        return ResponseEntity.badRequest().body(write(error));
    }

    /**
     * Serialise with Jackson rather than concatenating JSON.
     *
     * <p>These envelopes echo caller-supplied symbol/expiry. Hand-escaping only quotes and
     * backslashes leaves every control character (a percent-decoded newline, tab, or U+0001)
     * to pass through raw, producing a malformed body on an otherwise valid 200 — the client
     * then fails to parse a response that says "no reading yet".
     */
    private String write(ObjectNode node) {
        try {
            return mapper.writeValueAsString(node);
        } catch (JsonProcessingException e) {
            // Cannot happen for a flat ObjectNode of strings and a boolean, but an unparseable body
            // must never be the failure mode: fall back to a constant that is valid JSON.
            return "{\"present\":false}";
        }
    }

    /** Fixed-window per-principal budget, mirroring the seller-activity limiter. */
    static final class RateLimiter {
        private final int limit;
        private final long windowMs;
        private final java.util.Map<String, long[]> counters = new java.util.concurrent.ConcurrentHashMap<>();

        RateLimiter(int limit, long windowMs) {
            this.limit = limit;
            this.windowMs = windowMs;
        }

        /** 0 when allowed, else the Retry-After the caller should be given. */
        long tryAcquire(String principal, long nowMs) {
            long[] state = counters.computeIfAbsent(principal == null ? "anonymous" : principal,
                    k -> new long[]{nowMs, 0});
            synchronized (state) {
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
        }
    }
}

package app.feedgateway;

import app.feedgateway.liquidityhistory.LiquidityHistoryAuth;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code GET /api/es-liquidity-history[?tradeDate=yyyy-MM-dd]}: the 60-second session projection of the ES liquidity-wall dashboard (ES Book Liquidity Walls
 * Requirements rev 11 section 8 "Gateway and page"), the ADVANCE tab of the futures Liquidity Wall page. Served from {@link EsLiquidityWallHistory}, the
 * in-memory fold of the records the relay has seen (the current trade date only, as {@code /api/liquidity-history} serves only today); a {@code tradeDate}
 * other than the one held answers 404. Bearer auth is {@link LiquidityHistoryAuth}, exactly as {@code /api/gamma-migration}: invalid or expired token 401,
 * valid but unentitled 403. A disabled relay answers 404 so the page falls to live-only at once.
 */
@RestController
public class EsLiquidityWallHistoryController {
    private static final int RATE_LIMIT_PER_MIN = 120;

    private final BooleanSupplier enabled;
    private final EsLiquidityWallHistory history;
    private final LiquidityHistoryAuth auth;
    private final Map<String, long[]> counters = new ConcurrentHashMap<>();

    @org.springframework.beans.factory.annotation.Autowired
    public EsLiquidityWallHistoryController(GatewaySettings settings, EsLiquidityWallRelay relay, LiquidityHistoryAuth auth) {
        this(settings::esLiquidityWallRelayEnabled, relay.history(), auth);
    }

    /** Test seam. */
    EsLiquidityWallHistoryController(BooleanSupplier enabled, EsLiquidityWallHistory history, LiquidityHistoryAuth auth) {
        this.enabled = enabled;
        this.history = history;
        this.auth = auth;
    }

    @GetMapping(value = "/api/es-liquidity-history", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> esLiquidityHistory(
            @RequestParam(value = "tradeDate", required = false) String tradeDate,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return serve(tradeDate, authorization, System.currentTimeMillis());
    }

    ResponseEntity<String> serve(String tradeDate, String authorization, long now) {
        // every response path is no-store: a cached 404/401/403/429 would keep the page unavailable after the relay, the token or the budget recovers
        if (!enabled.getAsBoolean()) {
            return noStore(ResponseEntity.status(HttpStatus.NOT_FOUND)).build();
        }
        LiquidityHistoryAuth.Result authResult = auth.authenticate(authorization);
        if (authResult.status() != 200) {
            return noStore(ResponseEntity.status(authResult.status())).build();
        }
        long retryAfterSeconds = tryAcquire(authResult.principal(), now);
        if (retryAfterSeconds > 0) {
            return noStore(ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)).header(HttpHeaders.RETRY_AFTER, Long.toString(retryAfterSeconds)).build();
        }
        if (tradeDate != null && !tradeDate.isBlank() && (history.tradeDate() == null || !tradeDate.trim().equals(history.tradeDate().toString()))) {
            return noStore(ResponseEntity.status(HttpStatus.NOT_FOUND)).contentType(MediaType.APPLICATION_JSON)
                    .body("{\"error\":\"only the current trade date is held\",\"tradeDate\":" + (history.tradeDate() == null ? "null" : "\"" + history.tradeDate() + "\"") + "}");
        }
        return noStore(ResponseEntity.ok()).contentType(MediaType.APPLICATION_JSON).body(history.render(now));
    }

    private static ResponseEntity.BodyBuilder noStore(ResponseEntity.BodyBuilder b) {
        return b.header(HttpHeaders.CACHE_CONTROL, "no-store");
    }

    /** 0 when allowed, else the Retry-After seconds: the per-principal minute budget of the other authenticated reads. */
    private long tryAcquire(String principal, long nowMs) {
        long[] state = counters.computeIfAbsent(principal == null ? "anonymous" : principal, k -> new long[] {nowMs, 0});
        synchronized (state) {
            if (nowMs - state[0] >= 60_000L) {
                state[0] = nowMs;
                state[1] = 0;
            }
            if (state[1] >= RATE_LIMIT_PER_MIN) {
                return Math.max(1L, (60_000L - (nowMs - state[0]) + 999) / 1000);
            }
            state[1]++;
            return 0L;
        }
    }
}

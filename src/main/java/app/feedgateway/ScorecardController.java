package app.feedgateway;

import app.feedgateway.liquidityhistory.LiquidityHistoryAuth;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code GET /api/scorecard[?fromTradeDate=yyyy-MM-dd]} (liq-V design section 10): a read-only HTTP projection of the compacted scoreboard topics the services
 * publish, held by {@link ScorecardBoard}. The gateway computes nothing. Bearer auth is {@link LiquidityHistoryAuth} exactly as {@code /api/es-liquidity-history}
 * (invalid or expired token 401, unentitled 403, per-principal minute budget 429). A reader with no topic configured, a missing topic or an undecodable record is
 * reported inside the 200 body ({@code sources[].status}, {@code message}), never as an empty table presented as data.
 */
@RestController
public class ScorecardController {
    private static final int RATE_LIMIT_PER_MIN = 120;

    private final ScorecardReader reader;
    private final LiquidityHistoryAuth auth;
    private final Map<String, long[]> counters = new ConcurrentHashMap<>();

    @org.springframework.beans.factory.annotation.Autowired
    ScorecardController(ScorecardReader reader, LiquidityHistoryAuth auth) {
        this.reader = reader;
        this.auth = auth;
    }

    @GetMapping(value = "/api/scorecard", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> scorecard(
            @RequestParam(value = "fromTradeDate", required = false) String fromTradeDate,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return serve(fromTradeDate, authorization, System.currentTimeMillis());
    }

    ResponseEntity<String> serve(String fromTradeDate, String authorization, long now) {
        LiquidityHistoryAuth.Result authResult = auth.authenticate(authorization);
        if (authResult.status() != 200) {
            return noStore(ResponseEntity.status(authResult.status())).build();
        }
        long retryAfterSeconds = tryAcquire(authResult.principal(), now);
        if (retryAfterSeconds > 0) {
            return noStore(ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)).header(HttpHeaders.RETRY_AFTER, Long.toString(retryAfterSeconds)).build();
        }
        String from = null;
        if (fromTradeDate != null && !fromTradeDate.isBlank()) {
            try {
                from = LocalDate.parse(fromTradeDate.trim()).toString();
            } catch (DateTimeParseException bad) {
                return noStore(ResponseEntity.status(HttpStatus.BAD_REQUEST)).contentType(MediaType.APPLICATION_JSON).body("{\"error\":\"fromTradeDate must be yyyy-MM-dd\"}");
            }
        }
        return noStore(ResponseEntity.ok()).contentType(MediaType.APPLICATION_JSON).body(reader.board().render(from, reader.off()));
    }

    private static ResponseEntity.BodyBuilder noStore(ResponseEntity.BodyBuilder b) {
        return b.header(HttpHeaders.CACHE_CONTROL, "no-store");
    }

    private long tryAcquire(String principal, long nowMs) {
        if (counters.size() > 1024) {
            counters.values().removeIf(w -> nowMs - w[0] >= 60_000L);   // bounded: expired minute windows leave
        }
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

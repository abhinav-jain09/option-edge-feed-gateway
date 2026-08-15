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
 * {@code GET /api/gamma-rotation?symbol&expiry} — where one chain's gamma peak has been moving.
 *
 * <p>A SEPARATE endpoint from {@code /api/gamma-migration}, mirroring the separate topic behind it.
 * The migration record answers "where is the peak now", which is instantaneous: the migrating-peak
 * regime is true for one reading and gone, so "was mass rotating down at 09:35?" was unanswerable
 * from it. This answers that — every move consolidated over 1m / 5m / 15m / 30m / 4h / session,
 * plus the raw log of the individual hops behind those totals.
 *
 * <p>The two are deliberately not merged into one response. They are produced independently and
 * at-least-once with no transaction, so one can be a beat ahead of the other; a combined envelope
 * would present that skew as a single consistent reading. Two endpoints let the page show each
 * with its own staleness.
 *
 * <p>Everything else follows {@link GammaMigrationController} exactly: the cached record is served
 * VERBATIM, absence is a 200 with {@code present:false} rather than a 404 (a cold start and a
 * routing mistake must be distinguishable), symbol/expiry default to the chain the app is on, and
 * bearer auth reuses {@link LiquidityHistoryAuth} — invalid/expired 401, valid-but-unentitled 403.
 */
@RestController
public class GammaRotationController {

    /** The same cheap-authenticated-read budget the sibling endpoint uses. */
    private static final int RATE_LIMIT_PER_MIN = 120;

    private final FeedGatewayService service;
    private final LiquidityHistoryAuth auth;
    private final ObjectMapper mapper;
    private final GammaMigrationController.RateLimiter rateLimiter =
            new GammaMigrationController.RateLimiter(RATE_LIMIT_PER_MIN, 60_000L);

    public GammaRotationController(FeedGatewayService service, LiquidityHistoryAuth auth,
                                   ObjectMapper mapper) {
        this.service = service;
        this.auth = auth;
        this.mapper = mapper == null ? new ObjectMapper() : mapper;
    }

    @GetMapping(value = "/api/gamma-rotation", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> gammaRotation(
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
        // The page is about the CURRENT board, so the current board is the default rather than a
        // 400 — the same reasoning that fixed /api/gamma-migration.
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
            return badRequest("no symbol/expiry given and the gateway has no active selection yet");
        }

        String cached = service.cachedGammaRotation(symbol, expiry);
        if (cached == null || cached.isBlank()) {
            // Absence is NORMAL here in a way it is not for the snapshot: this topic only speaks
            // when the peak has actually moved, so a chain whose peak has sat still all session
            // has legitimately published nothing. The page must render "no moves yet", not an
            // error, and it can only do that if absence arrives as a 200.
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

    /** Jackson, not string concatenation: these envelopes echo caller-supplied symbol/expiry. */
    private String write(ObjectNode node) {
        try {
            return mapper.writeValueAsString(node);
        } catch (JsonProcessingException e) {
            return "{\"present\":false}";
        }
    }
}

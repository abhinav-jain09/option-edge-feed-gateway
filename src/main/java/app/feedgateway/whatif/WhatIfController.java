package app.feedgateway.whatif;

import app.feedgateway.FeedGatewayService;
import app.feedgateway.liquidityhistory.LiquidityHistoryAuth;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Locale;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code GET /api/what-if?symbol&expiry} — the What-If board for one chain, on a route of its own.
 *
 * <p><b>Why this route exists, and it is not a convenience.</b> The What-If board was reachable
 * only as the additive {@code whatIf} sibling on {@code /api/gamma-migration}, and
 * {@code GammaMigrationController} returns {@code {"present": false}} <b>before</b> it ever calls
 * {@link app.feedgateway.gammamigration.WhatIfSibling#merge} when the gateway holds no
 * gamma-migration record for the chain. {@code merge} itself then returns the gamma payload
 * unchanged when that payload is null or blank. So with no migration active there was no
 * {@code whatIf} at all: the board was strapped to a reading it has nothing to do with, and the
 * owner saw "no active destination" and no columns as ONE event rather than two.
 *
 * <p>The board depends on the map record and on nothing else, so it is served from the map record
 * and nothing else. The migration's presence cannot make it appear or disappear.
 *
 * <p><b>Served verbatim.</b> The cached record is the one the map service published, byte for byte:
 * every figure the page draws — the strikes with their net gamma, reading, strength, decay and band
 * edges, the spot, the cycle time — is already on it, and the page computes nothing (R-PIV-9's
 * principle, applied to this screen: this service publishes the data, the page draws it). Reshaping
 * here would create a second view of a contract that already has one owner, the Avro schema.
 *
 * <p><b>All strikes travel</b> (C-43). A strike with no delta band carries no band key rather than
 * a zero or a dash: a zero reads as a level and sends the owner to a price that is not in the
 * chain, and the page shows a dash for an absent key.
 *
 * <p>Bearer auth, the rate limit and the absent-is-200 shape are {@code /api/gamma-migration}'s,
 * deliberately: one class of cheap authenticated read, one behaviour, so a page author who has
 * read one route has read this one. An absence is <b>200 with {@code present: false}</b> and never
 * 404 — "the service has not published for this chain yet" is a normal cold-start state and a page
 * must be able to tell it from a routing mistake.
 */
@RestController
public class WhatIfController {

    /** Matches gamma-migration's budget: the same class of cheap authenticated read. */
    private static final int RATE_LIMIT_PER_MIN = 120;

    private final FeedGatewayService service;
    private final LiquidityHistoryAuth auth;
    private final ObjectMapper mapper;
    private final RateLimiter rateLimiter = new RateLimiter(RATE_LIMIT_PER_MIN, 60_000L);

    public WhatIfController(FeedGatewayService service, LiquidityHistoryAuth auth,
                            ObjectMapper mapper) {
        this.service = service;
        this.auth = auth;
        this.mapper = mapper == null ? new ObjectMapper() : mapper;
    }

    @GetMapping(value = "/api/what-if", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> whatIf(
            @RequestParam(value = "symbol", required = false) String symbol,
            @RequestParam(value = "expiry", required = false) String expiry,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        LiquidityHistoryAuth.Result authResult = auth.authenticate(authorization);
        if (authResult.status() != 200) {
            return ResponseEntity.status(authResult.status()).build();
        }
        long retryAfterSeconds =
                rateLimiter.tryAcquire(authResult.principal(), System.currentTimeMillis());
        if (retryAfterSeconds > 0) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .header(HttpHeaders.RETRY_AFTER, Long.toString(retryAfterSeconds)).build();
        }
        // The page is about "the current board", so the current board is the default rather than a
        // 400 — the same decision gamma-migration already made for the same reason.
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

        String cached = service.cachedWhatIfMap(symbol, expiry);
        if (cached == null || cached.isBlank()) {
            ObjectNode absent = mapper.createObjectNode();
            absent.put("present", false);
            absent.put("symbol", symbol.trim().toUpperCase(Locale.ROOT));
            absent.put("expiry", expiry.trim());
            return ResponseEntity.ok(write(absent));
        }
        // VERBATIM. No merge, no reshaping, no second owner of the contract.
        //
        // C-125: the per-strike deltas travel BESIDE it, spliced in before the record's final
        // brace, so the record's own bytes are a PREFIX of what is served -- not re-serialised,
        // not reordered. The figures are the gateway's own tap on the gamma strike topic (C-120)
        // and are not on this record; the block names its source, its producer and the expiry it
        // was read for, which is this BOARD's expiry and not the newest the topic carries.
        return ResponseEntity.ok(withStrikeDeltas(cached));
    }

    /**
     * The cached record with C-125's {@code strikeDeltas} block beside it, or the record unchanged.
     *
     * <p>Unchanged wherever anything is missing or unexpected: no store, no board expiry, no
     * strike carrying a published delta, or a record this cannot extend without reshaping. A body
     * that is not the record is worse than a body without the deltas.
     */
    private String withStrikeDeltas(String cached) {
        try {
            JsonNode record = mapper.readTree(cached);
            if (record == null || !record.isObject()) {
                return cached;
            }
            String boardExpiry = record.path("expiry").asText(null);
            ObjectNode block = StrikeDeltasSibling.build(mapper,
                    service.readableBoard().strikeDeltas(), boardExpiry,
                    StrikeDeltasSibling.strikeCentsOf(record));
            return StrikeDeltasSibling.attach(mapper, cached, block);
        } catch (RuntimeException | com.fasterxml.jackson.core.JsonProcessingException e) {
            // The route's job is to serve the board. A sibling that cannot be built is a cell the
            // page prints a dash in, never a 500 on the screen the owner is reading.
            return cached;
        }
    }

    private ResponseEntity<String> badRequest(String message) {
        ObjectNode error = mapper.createObjectNode();
        error.put("error", message);
        return ResponseEntity.badRequest().body(write(error));
    }

    /**
     * Serialise with Jackson rather than concatenating JSON: these envelopes echo caller-supplied
     * symbol and expiry, and hand-escaping leaves every control character to pass through raw and
     * produce a malformed body on an otherwise valid 200.
     */
    private String write(ObjectNode node) {
        try {
            return mapper.writeValueAsString(node);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("could not serialise the what-if envelope", e);
        }
    }

    /**
     * The same per-principal window gamma-migration, seller-activity and context-tape each keep.
     *
     * <p><b>A fourth copy, and that is the estate's existing pattern rather than my choice</b>:
     * {@code RateLimiter} is a package-private nested class in each of those three controllers.
     * Hoisting it to one shared class is the right change and is NOT made here, because this
     * pull request is on the critical path to a screen the owner is waiting for and a refactor
     * touching three live routes is not something to land in the same hour. Worth a finding of
     * its own.
     */
    static final class RateLimiter {
        private final int limit;
        private final long windowMs;
        private final java.util.Map<String, long[]> counters =
                new java.util.concurrent.ConcurrentHashMap<>();

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

package app.feedgateway.liquidityhistory;

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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.zip.GZIPOutputStream;

/**
 * {@code GET /api/liquidity-history?symbol&expiry[&tradeDate]} — the spec §3 session-history
 * endpoint. Auth mirrors the WS handshake ({@link LiquidityHistoryAuth}); the §3 RESPONSE
 * PRECEDENCE ladder is resolved by {@link LiquidityHistoryStore#query}; this controller owns
 * request validation, the §4 trade-date default (TODAY in ET, and ONLY today), the no-session /
 * pre-open zero sentinels, the response-size truncation budget, and explicit gzip encoding
 * (deterministic {@link GZIPOutputStream}, never container-negotiated compression).
 */
@RestController
public class LiquidityHistoryController {

    private static final Pattern ISO_DATE = Pattern.compile("\\d{4}-\\d{2}-\\d{2}");
    /** Envelope overhead reserve inside the raw-size budget (fields + headers-ish slack). */
    private static final long ENVELOPE_RESERVE_BYTES = 512L;

    private final LiquidityHistoryStore store;
    private final LiquidityHistoryAuth auth;
    private final ObjectMapper mapper;
    private final Clock clock;
    private final long maxResponseBytes;
    private final RateLimiter rateLimiter;

    // @Autowired disambiguates the Spring constructor from the test-seam one below (same pattern as
    // WsJwtHandshakeInterceptor: with two constructors and no marker the context fails to start).
    @org.springframework.beans.factory.annotation.Autowired
    public LiquidityHistoryController(app.feedgateway.GatewaySettings settings,
                                      LiquidityHistoryStore store, LiquidityHistoryAuth auth,
                                      ObjectMapper mapper) {
        this(store, auth, mapper, Clock.systemUTC(),
                settings.heatmapHistoryMaxResponseBytes(), settings.heatmapHistoryRateLimitPerMin());
    }

    /** Test seam: injectable clock + explicit budgets. */
    LiquidityHistoryController(LiquidityHistoryStore store, LiquidityHistoryAuth auth,
                               ObjectMapper mapper, Clock clock, long maxResponseBytes,
                               int rateLimitPerMinute) {
        this.store = store;
        this.auth = auth;
        this.mapper = mapper;
        this.clock = clock;
        this.maxResponseBytes = maxResponseBytes;
        this.rateLimiter = new RateLimiter(rateLimitPerMinute, 60_000L);
    }

    @GetMapping(value = "/api/liquidity-history")
    public ResponseEntity<byte[]> liquidityHistory(
            @RequestParam(value = "symbol", required = false) String symbol,
            @RequestParam(value = "expiry", required = false) String expiry,
            @RequestParam(value = "tradeDate", required = false) String tradeDateRaw,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        long startNs = System.nanoTime();
        if (!store.enabled()) {
            // Feature off: a definite 404 (not a 503) so the UI falls straight to live-only instead
            // of burning its 3-minute retry budget against a permanently absent endpoint.
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        LiquidityHistoryAuth.Result authResult = auth.authenticate(authorization);
        if (authResult.status() != 200) {
            return finish(ResponseEntity.status(authResult.status()).<byte[]>build(),
                    authResult.status(), startNs);
        }
        long retryAfterSeconds = rateLimiter.tryAcquire(authResult.principal(), clock.millis());
        if (retryAfterSeconds > 0) {
            return finish(ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .header(HttpHeaders.RETRY_AFTER, Long.toString(retryAfterSeconds))
                    .<byte[]>build(), 429, startNs);
        }
        if (symbol == null || symbol.isBlank()) {
            return finish(badRequest("symbol is required"), 400, startNs);
        }
        // Canonical ISO expiry required (spec §3): strict yyyy-MM-dd shape AND a real calendar date.
        if (expiry == null || !ISO_DATE.matcher(expiry).matches() || !parseable(expiry)) {
            return finish(badRequest("expiry must be a canonical ISO date (yyyy-MM-dd)"), 400, startNs);
        }
        LocalDate tradeDate;
        if (tradeDateRaw == null) {
            // §4: defaults to TODAY (America/New_York calendar date) and ONLY today — never a
            // walked-back "most recent trading day". Prior sessions need an explicit tradeDate.
            tradeDate = clock.instant().atZone(store.calendar().zone()).toLocalDate();
        } else if (ISO_DATE.matcher(tradeDateRaw).matches() && parseable(tradeDateRaw)) {
            tradeDate = LocalDate.parse(tradeDateRaw);
        } else {
            return finish(badRequest("tradeDate must be yyyy-MM-dd"), 400, startNs);
        }
        String normalizedSymbol = symbol.trim().toUpperCase();

        // Ladder step 1 (before session resolution — the alarm 503s ALL chains, weekends included).
        if (store.topologyAlarm()) {
            return finish(serviceUnavailable(60), 503, startNs);
        }
        Instant sessionOpen = store.calendar().sessionOpen(tradeDate);
        Instant now = clock.instant();
        if (sessionOpen == null || now.isBefore(sessionOpen)) {
            // No session (weekend/holiday) or pre-open (spec §3): 200 with zero sentinels — NEVER an
            // adjacent session's metadata, and never a 503 for a chain that cannot have data yet.
            return finish(envelope(normalizedSymbol, expiry, tradeDate, 0L, 0L,
                    ChainSnapshot.empty(normalizedSymbol, expiry, tradeDate, false), false), 200, startNs);
        }
        Instant sessionClose = store.calendar().sessionClose(tradeDate);

        store.beginServe(normalizedSymbol, expiry, tradeDate);
        try {
            LiquidityHistoryStore.ServeDecision decision = store.query(normalizedSymbol, expiry, tradeDate);
            return switch (decision.status()) {
                case TOPOLOGY_ALARM -> finish(serviceUnavailable(60), 503, startNs);
                case UNAVAILABLE, LAGGING ->
                        finish(serviceUnavailable(decision.retryAfterSeconds()), 503, startNs);
                case OK -> finish(envelope(normalizedSymbol, expiry, tradeDate,
                        sessionOpen.toEpochMilli(), sessionClose.toEpochMilli(),
                        decision.snapshot(), decision.epochRebuilding()), 200, startNs);
            };
        } finally {
            store.endServe(normalizedSymbol, expiry, tradeDate);
        }
    }

    private ResponseEntity<byte[]> finish(ResponseEntity<byte[]> response, int status, long startNs) {
        store.recordRequest(status, (System.nanoTime() - startNs) / 1_000_000L);
        return response;
    }

    private static boolean parseable(String isoDate) {
        try {
            LocalDate.parse(isoDate);
            return true;
        } catch (DateTimeParseException e) {
            return false;
        }
    }

    private ResponseEntity<byte[]> badRequest(String message) {
        ObjectNode body = mapper.createObjectNode();
        body.put("error", message);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private static ResponseEntity<byte[]> serviceUnavailable(int retryAfterSeconds) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .header(HttpHeaders.RETRY_AFTER, Integer.toString(retryAfterSeconds))
                .build();
    }

    /**
     * Builds the gzipped §3 envelope. The raw (pre-gzip) size budget drops OLDEST buckets first
     * (spec §3: never 500); {@code truncated} = size-dropped OR the snapshot's own truncated-start.
     */
    private ResponseEntity<byte[]> envelope(String symbol, String expiry, LocalDate tradeDate,
                                            long sessionOpenMs, long sessionCloseMs,
                                            ChainSnapshot snapshot, boolean epochRebuilding) {
        List<ObjectNode> bucketNodes = new ArrayList<>(snapshot.buckets().size());
        for (ChainSnapshot.BucketProjection bucket : snapshot.buckets()) {
            bucketNodes.add(bucketNode(bucket));
        }
        // Newest-first size accumulation so a budget breach drops the OLDEST buckets.
        long budget = Math.max(ENVELOPE_RESERVE_BYTES, maxResponseBytes) - ENVELOPE_RESERVE_BYTES;
        int keepFrom = bucketNodes.size();
        long used = 0L;
        for (int i = bucketNodes.size() - 1; i >= 0; i--) {
            long size = bucketNodes.get(i).toString().length() + 1L;
            if (used + size > budget) {
                break;
            }
            used += size;
            keepFrom = i;
        }
        boolean sizeTruncated = keepFrom > 0;
        boolean truncated = sizeTruncated || snapshot.truncatedStart();

        ObjectNode root = mapper.createObjectNode();
        root.put("historySchemaVersion", 1);
        root.put("symbol", symbol);
        root.put("expiry", expiry);
        root.put("tradeDate", tradeDate.toString());
        root.put("sessionOpenMs", sessionOpenMs);
        root.put("sessionCloseMs", sessionCloseMs);
        root.put("watermarkBucketStartMs", snapshot.watermarkBucketStartMs());
        root.put("truncated", truncated);
        ArrayNode buckets = root.putArray("buckets");
        for (int i = keepFrom; i < bucketNodes.size(); i++) {
            buckets.add(bucketNodes.get(i));
        }
        byte[] gzipped = gzip(root);
        if (truncated) {
            store.recordTruncatedResponse();
        }
        ResponseEntity.BodyBuilder builder = ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.CONTENT_ENCODING, "gzip")
                .header("X-History-Watermark", Long.toString(snapshot.watermarkBucketStartMs()))
                .header("X-History-Session-Open", Long.toString(sessionOpenMs))
                .header("X-History-Session-Close", Long.toString(sessionCloseMs))
                .header("X-History-Truncated", Boolean.toString(truncated));
        if (epochRebuilding) {
            builder.header("X-History-Epoch-Rebuilding", "true");
        }
        return builder.body(gzipped);
    }

    private ObjectNode bucketNode(ChainSnapshot.BucketProjection bucket) {
        ObjectNode node = mapper.createObjectNode();
        node.put("bucketStartMs", bucket.bucketStartMs());
        node.put("bucketEndMs", bucket.bucketEndMs());
        node.put("freshness", bucket.freshness());
        node.put("inputQuality", bucket.inputQuality());
        node.put("rawP99", bucket.rawP99());
        ArrayNode strikes = node.putArray("visibleStrikes");
        for (Double strike : bucket.visibleStrikes()) {
            strikes.add(strike);
        }
        ArrayNode cells = node.putArray("cells");
        // DEFAULT ELISION (part of the historySchemaVersion=1 contract): a projection field at its
        // neutral default — 0 for sizes/contracts/premiums, NONE for tradeDotSide, NEUTRAL for
        // states — is OMITTED; the client folds absent as that default (the same self-healing
        // Jackson-primitive-default rule the frozen contracts already rely on). This is what makes
        // the §3 budget math (~90B/cell) real: most cells of a session are quiet, and a quiet cell
        // serializes to little more than its identity.
        for (ChainSnapshot.CellProjection cell : bucket.cells()) {
            ObjectNode c = cells.addObject();
            c.put("strike", cell.strike());
            c.put("optionSide", cell.optionSide());
            putNonZero(c, "lastBidSize", cell.lastBidSize());
            putNonZero(c, "lastAskSize", cell.lastAskSize());
            putNonZero(c, "maxBidSize", cell.maxBidSize());
            putNonZero(c, "maxAskSize", cell.maxAskSize());
            putNonZero(c, "buyContracts", cell.buyContracts());
            putNonZero(c, "sellContracts", cell.sellContracts());
            putNonZero(c, "buyPremium", cell.buyPremium());
            putNonZero(c, "sellPremium", cell.sellPremium());
            if (!"NONE".equals(cell.tradeDotSide())) {
                c.put("tradeDotSide", cell.tradeDotSide());
            }
            if (!"NEUTRAL".equals(cell.bidState())) {
                c.put("bidState", cell.bidState());
            }
            if (!"NEUTRAL".equals(cell.askState())) {
                c.put("askState", cell.askState());
            }
        }
        return node;
    }

    private static void putNonZero(ObjectNode node, String field, long value) {
        if (value != 0L) {
            node.put(field, value);
        }
    }

    private static void putNonZero(ObjectNode node, String field, double value) {
        if (value != 0.0) {
            node.put(field, value);
        }
    }

    private byte[] gzip(ObjectNode root) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream(64 * 1024);
            try (GZIPOutputStream gz = new GZIPOutputStream(out)) {
                mapper.writeValue(gz, root);
            }
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Minimal per-principal sliding-window rate limiter (spec §3: default 6/min → 429). In-memory
     * and single-node — valid under the same single-replica deployment invariant as the store.
     */
    static final class RateLimiter {
        private final int limit;
        private final long windowMs;
        private final Map<String, ArrayDeque<Long>> byPrincipal = new HashMap<>();

        RateLimiter(int limit, long windowMs) {
            this.limit = limit;
            this.windowMs = windowMs;
        }

        /** 0 when admitted; else seconds until the oldest in-window request expires (Retry-After). */
        synchronized long tryAcquire(String principal, long nowMs) {
            ArrayDeque<Long> window = byPrincipal.computeIfAbsent(principal, p -> new ArrayDeque<>());
            while (!window.isEmpty() && nowMs - window.peekFirst() >= windowMs) {
                window.pollFirst();
            }
            if (window.size() >= limit) {
                long oldest = window.peekFirst();
                return Math.max(1L, (oldest + windowMs - nowMs + 999L) / 1_000L);
            }
            window.addLast(nowMs);
            if (byPrincipal.size() > 10_000) {
                byPrincipal.entrySet().removeIf(e -> e.getValue().isEmpty()
                        || nowMs - e.getValue().peekLast() >= windowMs);
            }
            return 0L;
        }
    }
}

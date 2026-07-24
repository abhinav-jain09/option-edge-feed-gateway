package app.feedgateway.selleractivity;

import app.feedgateway.FeedGatewayService;
import app.feedgateway.liquidityhistory.LiquidityHistoryAuth;
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

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * {@code GET /api/seller-activity?symbol&expiry[&sample][&mode]} — the server-side, single-source-of-truth
 * seller-activity aggregation for the option-chain seller-activity chart. Replaces the former client-side
 * aggregation so every client (web + future mobile) shares ONE implementation, and no client flattens the
 * raw 1-minute buckets (the {@code RangeError} that blanked the panel, option-chain PR #362).
 *
 * <p>Bearer auth reuses {@link LiquidityHistoryAuth} (same token validation as the WS handshake and the
 * {@code /api/liquidity-history} endpoint): invalid/expired → 401, valid-but-unentitled → 403. The
 * aggregation is {@link SellerActivityAggregator}; the data source is the gateway's in-memory strike-flow
 * cache ({@link FeedGatewayService#cachedStrikeFlowSnapshot}) — a bounded, cheap read (no Kafka), so no
 * rate limiter is needed the way {@code /api/liquidity-history} needs one for its Kafka fold.
 */
@RestController
public class SellerActivityController {

    private static final Pattern ISO_DATE = Pattern.compile("\\d{4}-\\d{2}-\\d{2}");
    private static final int DEFAULT_SAMPLE = 30;
    private static final String DEFAULT_MODE = "combined";

    private final FeedGatewayService service;
    private final LiquidityHistoryAuth auth;
    private final SellerActivityAggregator aggregator;
    private final ObjectMapper mapper;

    public SellerActivityController(FeedGatewayService service, LiquidityHistoryAuth auth, ObjectMapper mapper) {
        this.service = service;
        this.auth = auth;
        this.mapper = mapper;
        this.aggregator = new SellerActivityAggregator(mapper);
    }

    @GetMapping(value = "/api/seller-activity", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ObjectNode> sellerActivity(
            @RequestParam(value = "symbol", required = false) String symbol,
            @RequestParam(value = "expiry", required = false) String expiry,
            @RequestParam(value = "sample", required = false) String sampleRaw,
            @RequestParam(value = "mode", required = false) String modeRaw,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        LiquidityHistoryAuth.Result authResult = auth.authenticate(authorization);
        if (authResult.status() != 200) {
            return ResponseEntity.status(authResult.status()).build();
        }
        if (symbol == null || symbol.isBlank()) {
            return badRequest("symbol is required");
        }
        String canonicalExpiry = canonicalExpiry(expiry);
        if (canonicalExpiry == null) {
            return badRequest("expiry must be a canonical ISO date (yyyy-MM-dd)");
        }
        int sample = resolveSample(sampleRaw);
        if (sample <= 0) {
            return badRequest("sample must be one of " + SellerActivityAggregator.SAMPLE_MINUTES);
        }
        String mode = (modeRaw == null || modeRaw.isBlank())
                ? DEFAULT_MODE : modeRaw.trim().toLowerCase(Locale.ROOT);
        if (!SellerActivityAggregator.MODES.contains(mode)) {
            return badRequest("mode must be one of " + SellerActivityAggregator.MODES);
        }
        String normalizedSymbol = symbol.trim().toUpperCase(Locale.ROOT);
        String snapshot = service.cachedStrikeFlowSnapshot(normalizedSymbol, canonicalExpiry);
        ObjectNode envelope = aggregator.aggregate(snapshot, normalizedSymbol, canonicalExpiry, sample, mode);
        return ResponseEntity.ok(envelope);
    }

    /**
     * Strict ISO-date validation: shape-check then {@link LocalDate#parse} so impossible dates
     * ({@code 2026-99-99}, {@code 2026-02-30}) are rejected, returning the canonical {@code yyyy-MM-dd}.
     */
    private static String canonicalExpiry(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        if (!ISO_DATE.matcher(trimmed).matches()) {
            return null;
        }
        try {
            return LocalDate.parse(trimmed).toString();
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    /** Resolve the sample-minutes parameter: default when absent, {@code -1} when not an allowed value. */
    private int resolveSample(String raw) {
        if (raw == null || raw.isBlank()) {
            return DEFAULT_SAMPLE;
        }
        try {
            int value = Integer.parseInt(raw.trim());
            return SellerActivityAggregator.SAMPLE_MINUTES.contains(value) ? value : -1;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private ResponseEntity<ObjectNode> badRequest(String message) {
        ObjectNode body = mapper.createObjectNode();
        body.put("error", message);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }
}

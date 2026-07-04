package app.feedgateway.liquidityhistory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;

/**
 * One raw 1s {@code StrikeLiquidityHeatmapFrame} as read from the dashboard topic, reduced to the
 * fields the session-history fold consumes (spec §5 aggregation table inputs).
 *
 * <p>The gateway deliberately does NOT deserialize into the frozen contracts classes here: this is a
 * read-only mirror parsed via {@code readTree} with STRICT required-field checks, so a malformed or
 * future-shaped record can never throw out of the poll loop — it is rejected (counted) and skipped
 * (fail-closed parsing, same posture as FeedGatewayService's JSON reads). Unknown fields are ignored
 * by construction; absent OPTIONAL numeric fields default to 0 exactly like Jackson primitive
 * defaults on the contract record, so a one-version-behind producer still folds.
 */
record HistoryFrame(
        String symbol,
        String expiry,
        long bucketStartMs,
        long bucketEndMs,
        List<Cell> cells,
        List<Double> visibleStrikes,
        String freshness,
        String inputQuality) {

    /** Per-cell input fields for the §5 fold (full field set; only the projection serializes). */
    record Cell(
            double strike,
            String optionSide,
            long eventTimeMs,
            double bid,
            double ask,
            long openBidSize,
            long openAskSize,
            long lastBidSize,
            long lastAskSize,
            long maxBidSize,
            long maxAskSize,
            double avgBidSize,
            double avgAskSize,
            long bidSizeDeltaSum,
            long askSizeDeltaSum,
            int bidPullCount,
            int askPullCount,
            int bidRefillCount,
            int askRefillCount,
            int quoteUpdateCount,
            String dominantBidAction,
            String dominantAskAction,
            String bidState,
            String askState,
            long buyContracts,
            long sellContracts,
            double buyPremium,
            double sellPremium,
            int printsAtAsk,
            int printsAtBid,
            long contractsAtAsk,
            long contractsAtBid,
            boolean lockedOrCrossed,
            boolean stale,
            String diagnostics) {
    }

    /** Core measured-size fields a present cell MUST carry; absence is malformed, not zero. */
    private static final String[] REQUIRED_SIZE_FIELDS =
            {"lastBidSize", "lastAskSize", "maxBidSize", "maxAskSize"};

    /**
     * Parses one topic record value. Returns {@code null} on ANY malformed input — missing/blank
     * required fields ({@code symbol}, {@code expiry}, {@code bucketStartMs}, {@code bucketEndMs},
     * {@code cells} array), a non-object root, a malformed cell (missing strike, CALL/PUT side, or
     * a required size field), or invalid JSON. Absent/unknown {@code freshness}/{@code inputQuality}
     * fail closed to STALE/DEGRADED. The caller counts the reject and skips; parsing NEVER throws.
     */
    static HistoryFrame parse(String json, ObjectMapper mapper) {
        try {
            JsonNode root = mapper.readTree(json);
            if (root == null || !root.isObject()) {
                return null;
            }
            String symbol = root.path("symbol").asText("");
            String expiry = root.path("expiry").asText("");
            long bucketStartMs = root.path("bucketStartMs").asLong(0L);
            long bucketEndMs = root.path("bucketEndMs").asLong(0L);
            JsonNode cellsNode = root.get("cells");
            if (symbol.isBlank() || expiry.isBlank() || bucketStartMs <= 0L || bucketEndMs <= bucketStartMs
                    || cellsNode == null || !cellsNode.isArray()) {
                return null;
            }
            List<Cell> cells = new ArrayList<>(cellsNode.size());
            for (JsonNode c : cellsNode) {
                Cell cell = parseCell(c);
                if (cell == null) {
                    return null; // one malformed cell rejects the whole frame — fail closed, never partial
                }
                cells.add(cell);
            }
            List<Double> visibleStrikes = new ArrayList<>();
            JsonNode strikes = root.get("visibleStrikes");
            if (strikes != null && strikes.isArray()) {
                for (JsonNode s : strikes) {
                    if (s.isNumber()) {
                        visibleStrikes.add(s.asDouble());
                    }
                }
            }
            // FAIL-CLOSED freshness/quality: the producer always writes these, so an ABSENT or
            // UNKNOWN value is malformed/schema-drift — never assume the benign LIVE/FULL, which
            // would let stale/degraded history render as active liquidity. Map anything outside the
            // known set to the WORST severity (STALE / DEGRADED). This is also forward-compatible:
            // a future freshness value the gateway does not recognize is treated conservatively as
            // STALE rather than dropped, and worst-of folding stays well-defined.
            String freshness = failClosedFreshness(root.path("freshness").asText(""));
            String inputQuality = failClosedQuality(root.path("inputQuality").asText(""));
            return new HistoryFrame(symbol, expiry, bucketStartMs, bucketEndMs,
                    List.copyOf(cells), List.copyOf(visibleStrikes), freshness, inputQuality);
        } catch (Exception e) {
            return null;
        }
    }

    /** LIVE/GAP/STALE pass through; absent or any unknown value fails closed to STALE. */
    private static String failClosedFreshness(String v) {
        return "LIVE".equals(v) || "GAP".equals(v) || "STALE".equals(v) ? v : "STALE";
    }

    /** FULL/DEGRADED pass through; absent or any unknown value fails closed to DEGRADED. */
    private static String failClosedQuality(String v) {
        return "FULL".equals(v) || "DEGRADED".equals(v) ? v : "DEGRADED";
    }

    private static Cell parseCell(JsonNode c) {
        if (c == null || !c.isObject()) {
            return null;
        }
        double strike = c.path("strike").asDouble(Double.NaN);
        String side = c.path("optionSide").asText("");
        if (!Double.isFinite(strike) || (!"CALL".equals(side) && !"PUT".equals(side))) {
            return null;
        }
        // A PRESENT cell must carry its core measured sizes — a missing size key is malformed
        // (schema-drift / producer bug), NOT "zero liquidity". Reject the cell (→ whole frame,
        // same fail-closed-never-partial rule) rather than silently folding a phantom zero wall.
        for (String required : REQUIRED_SIZE_FIELDS) {
            JsonNode f = c.get(required);
            if (f == null || !f.isNumber()) {
                return null;
            }
        }
        return new Cell(
                strike, side,
                c.path("eventTimeMs").asLong(0L),
                c.path("bid").asDouble(0.0),
                c.path("ask").asDouble(0.0),
                c.path("openBidSize").asLong(0L),
                c.path("openAskSize").asLong(0L),
                c.path("lastBidSize").asLong(0L),
                c.path("lastAskSize").asLong(0L),
                c.path("maxBidSize").asLong(0L),
                c.path("maxAskSize").asLong(0L),
                c.path("avgBidSize").asDouble(0.0),
                c.path("avgAskSize").asDouble(0.0),
                c.path("bidSizeDeltaSum").asLong(0L),
                c.path("askSizeDeltaSum").asLong(0L),
                c.path("bidPullCount").asInt(0),
                c.path("askPullCount").asInt(0),
                c.path("bidRefillCount").asInt(0),
                c.path("askRefillCount").asInt(0),
                c.path("quoteUpdateCount").asInt(0),
                c.path("dominantBidAction").asText("INSUFFICIENT_DATA"),
                c.path("dominantAskAction").asText("INSUFFICIENT_DATA"),
                c.path("bidState").asText("NEUTRAL"),
                c.path("askState").asText("NEUTRAL"),
                c.path("buyContracts").asLong(0L),
                c.path("sellContracts").asLong(0L),
                c.path("buyPremium").asDouble(0.0),
                c.path("sellPremium").asDouble(0.0),
                c.path("printsAtAsk").asInt(0),
                c.path("printsAtBid").asInt(0),
                c.path("contractsAtAsk").asLong(0L),
                c.path("contractsAtBid").asLong(0L),
                c.path("lockedOrCrossed").asBoolean(false),
                c.path("stale").asBoolean(false),
                c.path("diagnostics").asText(""));
    }
}

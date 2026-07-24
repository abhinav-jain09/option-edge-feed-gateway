package app.feedgateway.selleractivity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;

/**
 * Server-side seller-activity aggregation — the single source of truth for the seller-activity chart,
 * consumed by the web today and any future client (mobile). It ports the former client-side
 * option-chain.js logic (normalizeSellerActivity / aggregateSellerActivity / sellerActivityValue /
 * sellerActivityLeader) EXACTLY (golden-master tested against the JS outputs), so no numbers shift.
 *
 * <p>It counts SELLER-AGGRESSOR PRINTS (trades) — size- and money-blind, one classified sell print = 1,
 * matching the classifier's {@code SellerActivityHistory}. The output is bounded (aggregated to the
 * requested sample window), so no client ever flattens the raw ~1440 one-minute buckets/strike — the
 * {@code RangeError} that blanked the panel (option-chain PR #362) is structurally impossible here.
 */
public final class SellerActivityAggregator {

    /** Session sample = the whole Globex session as a cumulative curve (mirrors SELLER_SESSION_MINUTES). */
    public static final int SESSION_MINUTES = 1440;
    public static final Set<Integer> SAMPLE_MINUTES = Set.of(1, 5, 10, 15, 30, 60, 240, SESSION_MINUTES);
    public static final Set<String> MODES = Set.of("call", "put", "combined");

    private final ObjectMapper mapper;

    public SellerActivityAggregator(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * Build the {@code /api/seller-activity} envelope from a cached strike-flow snapshot. A
     * null/blank/invalid snapshot yields a valid EMPTY envelope (the pre-open / no-data case),
     * never an error.
     */
    public ObjectNode aggregate(String snapshotJson, String symbol, String expiry,
                                int sampleMinutes, String mode) {
        ObjectNode out = mapper.createObjectNode();
        out.put("symbol", symbol);
        out.put("expiry", expiry);
        out.put("sampleMinutes", sampleMinutes);
        out.put("mode", mode);
        long asOfMs = 0L;
        Double leaderStrike = null;
        List<StrikeSeries> series = new ArrayList<>();

        JsonNode root = readTree(snapshotJson);
        if (root != null) {
            asOfMs = root.path("timestampMs").asLong(0L);
            for (JsonNode strikeNode : root.path("strikes")) {
                double strike = strikeNode.path("strike").asDouble(Double.NaN);
                if (!Double.isFinite(strike)) {
                    continue;
                }
                List<long[]> points = aggregatePoints(strikeNode.path("sellerActivity"), sampleMinutes, mode);
                if (!points.isEmpty()) {
                    series.add(new StrikeSeries(strike, points));
                }
            }
            series.sort(Comparator.comparingDouble(StrikeSeries::strike));
            leaderStrike = leader(series);
        }

        ArrayNode seriesOut = out.putArray("series");
        for (StrikeSeries s : series) {
            ObjectNode row = seriesOut.addObject();
            row.put("strike", s.strike());
            ArrayNode ptsOut = row.putArray("points");
            for (long[] p : s.points()) {
                ObjectNode pt = ptsOut.addObject();
                pt.put("t", p[0]);
                pt.put("count", p[1]);
            }
        }
        out.put("asOfMs", asOfMs);
        if (leaderStrike == null) {
            out.putNull("leaderStrike");
        } else {
            out.put("leaderStrike", leaderStrike);
        }
        return out;
    }

    /**
     * Port of aggregateSellerActivity(activity, sampleMinutes, mode) → sorted {@code [t, count]} buckets.
     * Session (1440) is a cumulative running total over the raw points; every other sample groups into
     * {@code max(bucketMinutes, sampleMinutes)}-wide event-time buckets. Points are pre-filtered to
     * {@code sellTradeCount > 0} (mirrors normalizeSellerActivity), independent of {@code mode}.
     */
    List<long[]> aggregatePoints(JsonNode activity, int sampleMinutes, String mode) {
        List<long[]> result = new ArrayList<>();
        if (activity == null || !activity.path("points").isArray()) {
            return result;
        }
        List<JsonNode> valid = new ArrayList<>();
        for (JsonNode p : activity.get("points")) {
            long tsMs = p.path("timestampMs").asLong(Long.MIN_VALUE);
            if (tsMs != Long.MIN_VALUE && Math.max(0L, p.path("sellTradeCount").asLong(0L)) > 0L) {
                valid.add(p);
            }
        }
        valid.sort(Comparator.comparingLong(p -> p.path("timestampMs").asLong()));

        if (sampleMinutes == SESSION_MINUTES) {
            long cumulative = 0L;
            for (JsonNode p : valid) {
                cumulative += value(p, mode);
                if (cumulative > 0L) {
                    result.add(new long[]{p.path("timestampMs").asLong(), cumulative});
                }
            }
            return result;
        }
        long bucketMinutes = Math.max(1L, activity.path("bucketMinutes").asLong(1L));
        long widthMs = Math.max(1L, Math.max(bucketMinutes, (long) sampleMinutes)) * 60_000L;
        TreeMap<Long, Long> grouped = new TreeMap<>();
        for (JsonNode p : valid) {
            long bucket = Math.floorDiv(p.path("timestampMs").asLong(), widthMs) * widthMs;
            grouped.merge(bucket, value(p, mode), Long::sum);
        }
        for (var entry : grouped.entrySet()) {
            if (entry.getValue() > 0L) {
                result.add(new long[]{entry.getKey(), entry.getValue()});
            }
        }
        return result;
    }

    /** Port of sellerActivityValue(point, mode). */
    static long value(JsonNode point, String mode) {
        return switch (mode) {
            case "call" -> Math.max(0L, point.path("callSellTradeCount").asLong(0L));
            case "put" -> Math.max(0L, point.path("putSellTradeCount").asLong(0L));
            default -> Math.max(0L, point.path("sellTradeCount").asLong(0L));
        };
    }

    /**
     * Port of sellerActivityLeader: the strike with the largest count in the LATEST visible bucket
     * (not the historical total); ties resolve to the lower strike. {@code null} when there is no data.
     */
    static Double leader(List<StrikeSeries> series) {
        long latest = Long.MIN_VALUE;
        for (StrikeSeries s : series) {
            for (long[] p : s.points()) {
                latest = Math.max(latest, p[0]);
            }
        }
        if (latest == Long.MIN_VALUE) {
            return null;
        }
        Double best = null;
        long bestValue = Long.MIN_VALUE;
        for (StrikeSeries s : series) {
            long value = 0L;
            for (long[] p : s.points()) {
                if (p[0] == latest) {
                    value = p[1];
                    break;
                }
            }
            if (best == null || value > bestValue || (value == bestValue && s.strike() < best)) {
                best = s.strike();
                bestValue = value;
            }
        }
        return best;
    }

    private JsonNode readTree(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return mapper.readTree(json);
        } catch (Exception e) {
            return null;
        }
    }

    /** One strike's aggregated series; {@code points} are {@code [timestampMs, count]} pairs. */
    record StrikeSeries(double strike, List<long[]> points) {
    }
}

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
 * matching the classifier's {@code SellerActivityHistory}. Output is EXPLICITLY bounded by
 * {@link #MAX_STRIKES} and {@link #MAX_POINTS_PER_STRIKE} (defensive ceilings above the upstream
 * invariant), so no client ever flattens the raw ~1440 one-minute buckets/strike (the {@code RangeError}
 * that blanked the panel, option-chain PR #362) and the response can never grow without limit.
 */
public final class SellerActivityAggregator {

    /** Session sample = the whole Globex session as a cumulative curve (mirrors SELLER_SESSION_MINUTES). */
    public static final int SESSION_MINUTES = 1440;
    public static final Set<Integer> SAMPLE_MINUTES = Set.of(1, 5, 10, 15, 30, 60, 240, SESSION_MINUTES);
    public static final Set<String> MODES = Set.of("call", "put", "combined");

    /**
     * Defensive response ceilings ABOVE the upstream invariant (SellerActivityHistory keeps ≤ 24h of
     * 1-minute buckets = 1440 points/strike; an option chain has at most a few hundred strikes). They cap
     * the response even against a malformed/oversized snapshot — keeping the MOST RECENT data — so real
     * data (well under both) is never affected.
     */
    static final int MAX_STRIKES = 1024;
    static final int MAX_POINTS_PER_STRIKE = 1500;
    /**
     * Hard ceiling on TOTAL points across the whole response, so the serialized envelope (and the heap it
     * occupies while the controller serializes it under the concurrency semaphore) is bounded regardless of
     * how the strikes/points distribute. ~200k points ≈ a few MB of JSON — far above a real chain, well
     * below the {@code MAX_STRIKES × MAX_POINTS_PER_STRIKE} theoretical max.
     */
    static final int MAX_TOTAL_POINTS = 200_000;
    /**
     * Parse-cost ceiling enforced BEFORE JSON materialization: reject a snapshot larger than this so a
     * malformed/oversized cache entry cannot force unbounded parse time/heap on this synchronous,
     * rate-limited endpoint. Far above a real strike-flow snapshot (a few MB); an over-cap snapshot
     * yields an empty envelope. Bounding total bytes bounds total strikes and points, so no separate
     * per-strike raw ceiling is needed — and capPoints then keeps the MOST RECENT output per strike.
     */
    static final int MAX_SNAPSHOT_BYTES = 32 * 1024 * 1024;

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
            int totalPoints = 0;
            for (JsonNode strikeNode : root.path("strikes")) {
                double strike = strikeNode.path("strike").asDouble(Double.NaN);
                if (!Double.isFinite(strike)) {
                    continue;
                }
                List<long[]> points = aggregatePoints(strikeNode.path("sellerActivity"), sampleMinutes, mode);
                if (!points.isEmpty()) {
                    if (totalPoints + points.size() > MAX_TOTAL_POINTS) {
                        break; // adding this strike would exceed the total-output cap -> stop (strictly bounded)
                    }
                    series.add(new StrikeSeries(strike, points));
                    totalPoints += points.size();
                    if (series.size() >= MAX_STRIKES) {
                        break; // defensive strike ceiling — real chains never approach this
                    }
                }
            }
            series.sort(Comparator.comparingDouble(StrikeSeries::strike));
            leaderStrike = leader(series, sampleMinutes == SESSION_MINUTES);
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
            return capPoints(result);
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
        return capPoints(result);
    }

    /** Keep at most {@link #MAX_POINTS_PER_STRIKE} points, retaining the MOST RECENT (points are ascending). */
    private static List<long[]> capPoints(List<long[]> points) {
        if (points.size() <= MAX_POINTS_PER_STRIKE) {
            return points;
        }
        return new ArrayList<>(points.subList(points.size() - MAX_POINTS_PER_STRIKE, points.size()));
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
     * Port of sellerActivityLeader: the strike with the largest count at the LATEST visible timestamp;
     * ties resolve to the lower strike. {@code null} when there is no data.
     *
     * <p>What "at the latest timestamp" means depends on the sample. Bucketed samples are independent
     * counts, so a strike absent from the latest bucket scores 0 — it genuinely traded nothing there.
     * Session ({@code cumulative}) points are running totals emitted only when a strike prints, so an
     * absent timestamp means UNCHANGED: a strike holding a cumulative 100 whose last print was at 09:40
     * still leads a strike that printed 2 a minute ago. Scoring it 0 would hand the lead to whoever
     * printed most recently, however small their session total. Mirrors {@code sellerValueAt} in
     * option-chain.js, which the web panel uses for exactly this reason.
     */
    static Double leader(List<StrikeSeries> series, boolean cumulative) {
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
            long valueAt = Long.MIN_VALUE;
            for (long[] p : s.points()) {
                if (cumulative) {
                    // Last value at or before `latest`. Points arrive ascending, but this tolerates any
                    // order and resolves duplicate timestamps to the later element, as the web does.
                    if (p[0] <= latest && p[0] >= valueAt) {
                        value = p[1];
                        valueAt = p[0];
                    }
                } else if (p[0] == latest) {
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
        if (json == null || json.isBlank() || json.length() > MAX_SNAPSHOT_BYTES) {
            return null; // bound parse cost BEFORE materialization; over-cap -> empty envelope
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

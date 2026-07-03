package app.feedgateway.liquidityhistory;

import java.time.LocalDate;
import java.util.List;

/**
 * Immutable, serve-ready projection of one {@code (chain, tradeDate)} session aggregate — the
 * spec §5 HISTORY CELL PROJECTION, taken atomically under the store lock so a response can never
 * observe a torn/mid-fold aggregate (spec §2 / AC17 snapshot isolation). Once built it is fully
 * detached from the store: eviction or epoch swaps after the snapshot cannot affect the response.
 *
 * @param truncatedStart true when the session's earliest data is known/suspected unavailable
 *                       server-side (retention hole at the epoch seek, or a retained-but-never-
 *                       folded previous trading day) — folded into the envelope's {@code truncated}.
 */
public record ChainSnapshot(
        String symbol,
        String expiry,
        LocalDate tradeDate,
        long watermarkBucketStartMs,
        boolean truncatedStart,
        List<BucketProjection> buckets) {

    /** One 60s aggregated bucket; only the §5 bucket-level projection fields serialize. */
    public record BucketProjection(
            long bucketStartMs,
            long bucketEndMs,
            String freshness,
            String inputQuality,
            long rawP99,
            List<Double> visibleStrikes,
            List<CellProjection> cells) {
    }

    /** The §5 HISTORY CELL PROJECTION — the ONLY cell fields that serialize. */
    public record CellProjection(
            double strike,
            String optionSide,
            long lastBidSize,
            long lastAskSize,
            long maxBidSize,
            long maxAskSize,
            long buyContracts,
            long sellContracts,
            double buyPremium,
            double sellPremium,
            String tradeDotSide,
            String bidState,
            String askState) {
    }

    static ChainSnapshot empty(String symbol, String expiry, LocalDate tradeDate, boolean truncatedStart) {
        return new ChainSnapshot(symbol, expiry, tradeDate, 0L, truncatedStart, List.of());
    }
}

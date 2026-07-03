package app.feedgateway.liquidityhistory;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * The 60s session aggregate for one {@code (chain, tradeDate)} — the spec §5 aggregation,
 * implemented ONCE here and mirrored by the client's live fold. Mutated only under the store lock;
 * {@link #snapshot} copies out immutable projections under the same lock (AC17).
 *
 * <p>Constituents (the ≤60 raw 1s frames of a minute) are ordered by their {@code bucketStartMs}
 * (payload time). Per §1 that order equals the per-chain offset order (strictly increasing), so
 * every rule below folds ASSOCIATIVELY over any split of the ordered constituent sequence — the
 * §5 continuability invariant. The ONE exception is {@code rawP99} (a percentile is not mergeable):
 * closed buckets finalize it and drop their sample pool; the newest (boundary) bucket keeps its pool
 * and computes rawP99 final-at-serve.
 */
final class SessionAggregate {

    /**
     * Approximate-byte accounting constants (spec §2 allows "cells*constant"): CELL_BYTES estimates
     * one retained {@link CellFold} (≈30 primitive fields + 5 short strings + map-entry overhead);
     * BUCKET_BYTES estimates the per-bucket fixed state incl. the visibleStrikes list. The rawP99
     * sample pool of the ONE open boundary bucket is counted exactly (8 bytes/sample).
     */
    static final long CELL_BYTES = 400L;
    static final long BUCKET_BYTES = 1_024L;
    static final long BUCKET_MS = 60_000L;

    final String symbol;
    final String expiry;
    final LocalDate tradeDate;
    /** True when this aggregate's epoch seek landed on a retention-truncated log start (spec §3 truncated). */
    final boolean retentionSuspect;

    private final TreeMap<Long, BucketFold> buckets = new TreeMap<>();
    private long watermarkBucketStartMs;
    /**
     * bucketStartMs of the EARLIEST raw 1s frame folded — 1-second granularity, deliberately NOT
     * the first 60s bucket key: retention can delete the opening seconds while the first surviving
     * raw frame still lands inside the 09:30 minute bucket, and the bucket-key test would then
     * report truncated=false with data missing (Codex Gate-2 finding 2).
     */
    private long earliestRawBucketStartMs = Long.MAX_VALUE;
    private long lastFoldAtMs;
    private long approxBytes = BUCKET_BYTES; // base object overhead, same constant reused

    SessionAggregate(String symbol, String expiry, LocalDate tradeDate, boolean retentionSuspect) {
        this.symbol = symbol;
        this.expiry = expiry;
        this.tradeDate = tradeDate;
        this.retentionSuspect = retentionSuspect;
    }

    /** Folds one raw 1s frame. Caller guarantees the frame routes to this (chain, tradeDate). */
    void fold(HistoryFrame frame, long nowMs) {
        long minuteStart = Math.floorDiv(frame.bucketStartMs(), BUCKET_MS) * BUCKET_MS;
        Map.Entry<Long, BucketFold> last = buckets.lastEntry();
        if (last != null && minuteStart > last.getKey()) {
            // The previous boundary bucket is now CLOSED: finalize rawP99 and drop its sample pool
            // (the percentile is the one non-mergeable rule — §5 continuability invariant).
            approxBytes -= last.getValue().finalizeRawP99();
        }
        BucketFold bucket = buckets.get(minuteStart);
        if (bucket == null) {
            bucket = new BucketFold(minuteStart);
            buckets.put(minuteStart, bucket);
            approxBytes += BUCKET_BYTES;
        }
        approxBytes += bucket.fold(frame);
        watermarkBucketStartMs = Math.max(watermarkBucketStartMs, frame.bucketStartMs());
        earliestRawBucketStartMs = Math.min(earliestRawBucketStartMs, frame.bucketStartMs());
        lastFoldAtMs = nowMs;
    }

    /** bucketStartMs of the newest raw frame folded — the client's complete merge boundary (spec §3). */
    long watermarkBucketStartMs() {
        return watermarkBucketStartMs;
    }

    /** Wall-clock of the last fold; the eviction tiebreaker ("oldest last-fold time first", spec §2). */
    long lastFoldAtMs() {
        return lastFoldAtMs;
    }

    long approxBytes() {
        return approxBytes;
    }

    /**
     * Immutable projection of the whole session. {@code sessionOpenMs} is only used to decide
     * {@code truncatedStart}: on a retention-suspect partition, an earliest RAW frame (1s
     * granularity) strictly after the session open means the session's head — possibly only its
     * opening seconds — was already deleted (spec §3 / AC12; Codex Gate-2 finding 2).
     */
    ChainSnapshot snapshot(long sessionOpenMs) {
        List<ChainSnapshot.BucketProjection> projected = new ArrayList<>(buckets.size());
        for (BucketFold bucket : buckets.values()) {
            projected.add(bucket.project());
        }
        boolean truncatedStart = retentionSuspect && earliestRawBucketStartMs != Long.MAX_VALUE
                && earliestRawBucketStartMs > sessionOpenMs;
        return new ChainSnapshot(symbol, expiry, tradeDate, watermarkBucketStartMs, truncatedStart,
                List.copyOf(projected));
    }

    // ------------------------------------------------------------------ 60s bucket fold (§5 table)

    static final class BucketFold {
        private final long bucketStartMs;
        private final long bucketEndMs;

        // frame-level worst-of / newest-of state
        private int freshnessRank; // LIVE=1 < GAP=2 < STALE=3; fold keeps the worst (max)
        private boolean degraded;  // any constituent DEGRADED → whole bucket DEGRADED (collapse at project)
        private List<Double> visibleStrikes = List.of();
        private long newestConstituentMs = Long.MIN_VALUE;

        // rawP99 sample pool: lastBidSize+lastAskSize of EVERY cell of EVERY constituent frame (§5).
        // Kept only while this is the open boundary bucket; finalized+dropped when the bucket closes.
        private long[] pool = new long[64];
        private int poolSize;
        private long finalizedRawP99 = -1L; // -1 = not finalized (boundary bucket → final-at-serve)

        private final Map<String, CellFold> cells = new LinkedHashMap<>();

        BucketFold(long bucketStartMs) {
            this.bucketStartMs = bucketStartMs;
            this.bucketEndMs = bucketStartMs + BUCKET_MS;
        }

        /** Folds one constituent frame; returns the approximate-byte delta this fold added. */
        long fold(HistoryFrame frame) {
            long constituentMs = frame.bucketStartMs();
            long byteDelta = 0L;
            freshnessRank = Math.max(freshnessRank, freshnessRank(frame.freshness()));
            degraded |= "DEGRADED".equals(frame.inputQuality());
            if (constituentMs >= newestConstituentMs) {
                newestConstituentMs = constituentMs;
                visibleStrikes = frame.visibleStrikes(); // newest constituent's, verbatim
            }
            for (HistoryFrame.Cell cell : frame.cells()) {
                if (finalizedRawP99 < 0) {
                    byteDelta += poolAdd(cell.lastBidSize() + cell.lastAskSize());
                }
                String key = cell.strike() + "|" + cell.optionSide();
                CellFold fold = cells.get(key);
                if (fold == null) {
                    fold = new CellFold(cell.strike(), cell.optionSide());
                    cells.put(key, fold);
                    byteDelta += CELL_BYTES;
                }
                fold.fold(cell, constituentMs);
            }
            return byteDelta;
        }

        /** Closes the pool: computes+stores rawP99 and frees the samples; returns bytes released. */
        long finalizeRawP99() {
            if (finalizedRawP99 >= 0) {
                return 0L;
            }
            finalizedRawP99 = nearestRankP99(pool, poolSize);
            long released = 8L * poolSize;
            pool = null;
            poolSize = 0;
            return released;
        }

        ChainSnapshot.BucketProjection project() {
            long rawP99 = finalizedRawP99 >= 0 ? finalizedRawP99 : nearestRankP99(pool, poolSize);
            List<ChainSnapshot.CellProjection> projectedCells = new ArrayList<>(cells.size());
            for (CellFold cell : cells.values()) {
                projectedCells.add(cell.project(degraded));
            }
            return new ChainSnapshot.BucketProjection(bucketStartMs, bucketEndMs,
                    freshnessName(freshnessRank), degraded ? "DEGRADED" : "FULL", rawP99,
                    List.copyOf(visibleStrikes), List.copyOf(projectedCells));
        }

        private long poolAdd(long sample) {
            if (poolSize == pool.length) {
                pool = Arrays.copyOf(pool, pool.length * 2);
            }
            pool[poolSize++] = sample;
            return 8L;
        }

        /**
         * §5 EXACT formula: nearest-rank 99th percentile of the pooled per-cell
         * {@code lastBidSize+lastAskSize} samples — rank = ceil(0.99*N), value = sorted[rank-1];
         * empty pool → 0 (identical sample basis to the live adaptive scaler).
         */
        static long nearestRankP99(long[] pool, int size) {
            if (size <= 0) {
                return 0L;
            }
            long[] sorted = Arrays.copyOf(pool, size);
            Arrays.sort(sorted);
            int rank = (int) Math.ceil(0.99d * size);
            return sorted[Math.max(0, rank - 1)];
        }

        private static int freshnessRank(String freshness) {
            // Severity worst-of (STALE > GAP > LIVE); anything unrecognized is treated as LIVE — an
            // unknown future value must not fabricate severity the producer did not assert.
            return switch (freshness) {
                case "STALE" -> 3;
                case "GAP" -> 2;
                default -> 1;
            };
        }

        private static String freshnessName(int rank) {
            return switch (rank) {
                case 3 -> "STALE";
                case 2 -> "GAP";
                default -> "LIVE";
            };
        }
    }

    // -------------------------------------------------------------------- per-cell fold (§5 table)

    /**
     * Full-field cell fold per the §5 table (aggregate the FULL fields, then project). Fields not in
     * the HISTORY CELL PROJECTION (bid/ask, opens, avgs, delta sums, pull/refill counts, dominant
     * actions, prints, lockedOrCrossed, stale, diagnostics) are still folded exactly — they are what
     * the recompute rules derive from and what the golden/associativity tests pin. The producer's
     * derived display scores are deliberately NOT recomputed: they are excluded from the projection
     * and {@code consumptionScore} needs the producer-side meaningfulFlowContracts config the
     * gateway does not have.
     */
    static final class CellFold {
        final double strike;
        final String optionSide;

        private long oldestMs = Long.MAX_VALUE;
        private long newestMs = Long.MIN_VALUE;
        long eventTimeMs;                       // max
        double bid;                             // newest
        double ask;                             // newest
        long openBidSize;                       // OLDEST constituent (true minute open)
        long openAskSize;
        long lastBidSize;                       // newest
        long lastAskSize;
        long maxBidSize;                        // max of maxima
        long maxAskSize;
        private double weightedBidSizeSum;      // Σ avg*count → quoteUpdateCount-weighted mean
        private double weightedAskSizeSum;
        long bidSizeDeltaSum;                   // sum
        long askSizeDeltaSum;
        long bidPullCount;                      // sum
        long askPullCount;
        long bidRefillCount;
        long askRefillCount;
        long quoteUpdateCount;                  // sum
        private String newestDominantBidAction = "INSUFFICIENT_DATA"; // both-sums-zero fallback (§5)
        private String newestDominantAskAction = "INSUFFICIENT_DATA";
        private String meaningfulBidState;      // newest non-(NEUTRAL/INSUFFICIENT_DATA), else NEUTRAL
        private String meaningfulAskState;
        private long meaningfulBidStateMs = Long.MIN_VALUE;
        private long meaningfulAskStateMs = Long.MIN_VALUE;
        long buyContracts;                      // sum
        long sellContracts;
        double buyPremium;
        double sellPremium;
        long printsAtAsk;
        long printsAtBid;
        long contractsAtAsk;
        long contractsAtBid;
        boolean lockedOrCrossed;                // OR
        boolean stale;                          // newest
        String diagnostics = "";                // newest verbatim (empty stays empty — no stale leak)

        CellFold(double strike, String optionSide) {
            this.strike = strike;
            this.optionSide = optionSide;
        }

        void fold(HistoryFrame.Cell cell, long constituentMs) {
            if (constituentMs < oldestMs) {
                oldestMs = constituentMs;
                openBidSize = cell.openBidSize();
                openAskSize = cell.openAskSize();
            }
            if (constituentMs >= newestMs) {
                newestMs = constituentMs;
                bid = cell.bid();
                ask = cell.ask();
                lastBidSize = cell.lastBidSize();
                lastAskSize = cell.lastAskSize();
                newestDominantBidAction = cell.dominantBidAction();
                newestDominantAskAction = cell.dominantAskAction();
                stale = cell.stale();
                diagnostics = cell.diagnostics();
            }
            if (isMeaningfulState(cell.bidState()) && constituentMs >= meaningfulBidStateMs) {
                meaningfulBidStateMs = constituentMs;
                meaningfulBidState = cell.bidState();
            }
            if (isMeaningfulState(cell.askState()) && constituentMs >= meaningfulAskStateMs) {
                meaningfulAskStateMs = constituentMs;
                meaningfulAskState = cell.askState();
            }
            eventTimeMs = Math.max(eventTimeMs, cell.eventTimeMs());
            maxBidSize = Math.max(maxBidSize, cell.maxBidSize());
            maxAskSize = Math.max(maxAskSize, cell.maxAskSize());
            weightedBidSizeSum += cell.avgBidSize() * cell.quoteUpdateCount();
            weightedAskSizeSum += cell.avgAskSize() * cell.quoteUpdateCount();
            bidSizeDeltaSum += cell.bidSizeDeltaSum();
            askSizeDeltaSum += cell.askSizeDeltaSum();
            bidPullCount += cell.bidPullCount();
            askPullCount += cell.askPullCount();
            bidRefillCount += cell.bidRefillCount();
            askRefillCount += cell.askRefillCount();
            quoteUpdateCount += cell.quoteUpdateCount();
            buyContracts += cell.buyContracts();
            sellContracts += cell.sellContracts();
            buyPremium += cell.buyPremium();
            sellPremium += cell.sellPremium();
            printsAtAsk += cell.printsAtAsk();
            printsAtBid += cell.printsAtBid();
            contractsAtAsk += cell.contractsAtAsk();
            contractsAtBid += cell.contractsAtBid();
            lockedOrCrossed |= cell.lockedOrCrossed();
        }

        /** RECOMPUTED: newest.last − oldest.open (§5 table). */
        long bidSizeDelta() {
            return lastBidSize - openBidSize;
        }

        long askSizeDelta() {
            return lastAskSize - openAskSize;
        }

        /** quoteUpdateCount-weighted mean; total count 0 → 0.0 (producer's own zero rule). */
        double avgBidSize() {
            return quoteUpdateCount == 0 ? 0.0 : weightedBidSizeSum / quoteUpdateCount;
        }

        double avgAskSize() {
            return quoteUpdateCount == 0 ? 0.0 : weightedAskSizeSum / quoteUpdateCount;
        }

        /** RECOMPUTED from summed counts (producer's rule: pull ≥ refill → PULL); both 0 → newest's. */
        String dominantBidAction() {
            if (bidPullCount == 0 && bidRefillCount == 0) {
                return newestDominantBidAction;
            }
            return bidPullCount >= bidRefillCount ? "BID_PULL" : "BID_REFILL";
        }

        String dominantAskAction() {
            if (askPullCount == 0 && askRefillCount == 0) {
                return newestDominantAskAction;
            }
            return askPullCount >= askRefillCount ? "ASK_PULL" : "ASK_REFILL";
        }

        /** Newest constituent's non-(NEUTRAL/INSUFFICIENT_DATA) state, else NEUTRAL (§5 table). */
        String bidState() {
            return meaningfulBidState == null ? "NEUTRAL" : meaningfulBidState;
        }

        String askState() {
            return meaningfulAskState == null ? "NEUTRAL" : meaningfulAskState;
        }

        /**
         * RECOMPUTED from summed contracts — mirrors BucketAggregate.tradeDotSide in the producer:
         * buy&sell present → equal ? MIXED : larger side; one side → that side; none → NONE.
         */
        String tradeDotSide() {
            boolean buy = buyContracts > 0;
            boolean sell = sellContracts > 0;
            if (buy && sell) {
                return buyContracts == sellContracts ? "MIXED" : (buyContracts > sellContracts ? "BUY" : "SELL");
            }
            if (buy) {
                return "BUY";
            }
            if (sell) {
                return "SELL";
            }
            return "NONE";
        }

        /**
         * DEGRADED collapse (§5): when ANY constituent of the bucket was DEGRADED, the cell's
         * bid/askState collapse to INSUFFICIENT_DATA in the projection (dominant actions collapse
         * too, but they are not part of the projection). Sizes/counters still report.
         */
        ChainSnapshot.CellProjection project(boolean degraded) {
            return new ChainSnapshot.CellProjection(strike, optionSide,
                    lastBidSize, lastAskSize, maxBidSize, maxAskSize,
                    buyContracts, sellContracts, buyPremium, sellPremium,
                    tradeDotSide(),
                    degraded ? "INSUFFICIENT_DATA" : bidState(),
                    degraded ? "INSUFFICIENT_DATA" : askState());
        }

        private static boolean isMeaningfulState(String state) {
            return state != null && !state.isEmpty()
                    && !"NEUTRAL".equals(state) && !"INSUFFICIENT_DATA".equals(state);
        }
    }
}

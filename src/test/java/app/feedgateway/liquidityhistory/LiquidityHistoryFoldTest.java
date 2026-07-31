package app.feedgateway.liquidityhistory;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static app.feedgateway.liquidityhistory.LiquidityHistoryTestSupport.MAPPER;
import static app.feedgateway.liquidityhistory.LiquidityHistoryTestSupport.PREVIOUS_TRADING_DAY;
import static app.feedgateway.liquidityhistory.LiquidityHistoryTestSupport.TRADING_DAY;
import static app.feedgateway.liquidityhistory.LiquidityHistoryTestSupport.cell;
import static app.feedgateway.liquidityhistory.LiquidityHistoryTestSupport.config;
import static app.feedgateway.liquidityhistory.LiquidityHistoryTestSupport.et;
import static app.feedgateway.liquidityhistory.LiquidityHistoryTestSupport.frame;
import static app.feedgateway.liquidityhistory.LiquidityHistoryTestSupport.seamStore;
import static app.feedgateway.liquidityhistory.LiquidityHistoryTestSupport.sessionOpenMs;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** §5 aggregation + §4 trade-date routing + strict frame parsing (ACs 4, 7b, 7c, 8 and rawP99). */
class LiquidityHistoryFoldTest {

    private static final String SYMBOL = "SPX";
    private static final String EXPIRY = "2026-06-23";
    private static final String CHAIN = SYMBOL + "|" + EXPIRY;
    private static final long M0 = sessionOpenMs(TRADING_DAY); // 09:30 ET, minute-aligned

    private static LiquidityHistoryStore publishedStore(String... frames) {
        LiquidityHistoryTestSupport.MutableClock clock =
                new LiquidityHistoryTestSupport.MutableClock(et(TRADING_DAY, 10, 5));
        LiquidityHistoryStore store = seamStore(clock, config(4, 200L * 1024 * 1024, 300L), Set::of);
        store.beginEpochState();
        for (String f : frames) {
            store.foldForTest(f, 0);
        }
        store.completeEpochState();
        return store;
    }

    private static ChainSnapshot snapshot(LiquidityHistoryStore store, LocalDate tradeDate) {
        LiquidityHistoryStore.ServeDecision decision = store.query(SYMBOL, EXPIRY, tradeDate);
        assertEquals(LiquidityHistoryStore.ServeStatus.OK, decision.status());
        return decision.snapshot();
    }

    // ---- golden constituents shared by the AC7b/AC7c tests ----

    private static ObjectNode c1() {
        return cell(6000.0, "CALL",
                "eventTimeMs", M0 + 500L, "bid", 1.0, "ask", 1.2,
                "openBidSize", 10L, "openAskSize", 4L, "lastBidSize", 12L, "lastAskSize", 3L,
                "maxBidSize", 15L, "maxAskSize", 3L, "avgBidSize", 11.0, "quoteUpdateCount", 2,
                "bidPullCount", 1, "bidSizeDeltaSum", 2L,
                "buyContracts", 5L, "buyPremium", 100.0,
                "dominantBidAction", "BID_PULL", "dominantAskAction", "STABLE");
    }

    private static ObjectNode c2() {
        return cell(6000.0, "CALL",
                "eventTimeMs", M0 + 1_500L, "bid", 1.1, "ask", 1.3,
                "openBidSize", 12L, "lastBidSize", 8L, "maxBidSize", 30L, "avgBidSize", 9.0,
                "quoteUpdateCount", 1, "bidRefillCount", 2, "bidSizeDeltaSum", -4L,
                "sellContracts", 7L, "sellPremium", 70.0, "bidState", "PULLED",
                "dominantBidAction", "BID_REFILL", "dominantAskAction", "STABLE");
    }

    private static ObjectNode c3() {
        return cell(6000.0, "CALL",
                "eventTimeMs", M0 + 2_500L, "bid", 1.2, "ask", 1.4,
                "openBidSize", 8L, "lastBidSize", 20L, "maxBidSize", 25L, "avgBidSize", 20.0,
                "quoteUpdateCount", 1, "bidPullCount", 2, "bidRefillCount", 1, "bidSizeDeltaSum", 12L,
                "buyContracts", 3L, "sellContracts", 3L, "buyPremium", 30.0, "sellPremium", 20.0,
                "askState", "EATEN", "stale", true, "diagnostics", "d3",
                "dominantBidAction", "BID_PULL", "dominantAskAction", "STABLE");
    }

    // AC7b: golden aggregation vector — open/last/max/sum/recompute rules over 3 constituents.
    @Test
    void goldenVectorAggregatesPerSpecTable() {
        LiquidityHistoryStore store = publishedStore(
                frame(SYMBOL, EXPIRY, M0, "LIVE", "FULL", List.of(6000.0), c1()),
                frame(SYMBOL, EXPIRY, M0 + 1_000L, "LIVE", "FULL", List.of(6000.0), c2()),
                frame(SYMBOL, EXPIRY, M0 + 2_000L, "LIVE", "FULL", List.of(6000.0, 6005.0), c3()));

        ChainSnapshot snapshot = snapshot(store, TRADING_DAY);
        assertEquals(M0 + 2_000L, snapshot.watermarkBucketStartMs(), "watermark = newest raw frame folded");
        assertEquals(1, snapshot.buckets().size());
        ChainSnapshot.BucketProjection bucket = snapshot.buckets().get(0);
        assertEquals(M0, bucket.bucketStartMs());
        assertEquals(M0 + 60_000L, bucket.bucketEndMs());
        assertEquals("LIVE", bucket.freshness());
        assertEquals("FULL", bucket.inputQuality());
        assertEquals(List.of(6000.0, 6005.0), bucket.visibleStrikes(), "visibleStrikes = newest constituent's");
        // pool = lastBid+lastAsk per cell per constituent = {15, 8, 20}; rank ceil(0.99*3)=3 -> 20.
        assertEquals(20L, bucket.rawP99());

        assertEquals(1, bucket.cells().size());
        ChainSnapshot.CellProjection cell = bucket.cells().get(0);
        assertEquals(20L, cell.lastBidSize(), "last from NEWEST constituent");
        assertEquals(0L, cell.lastAskSize());
        assertEquals(30L, cell.maxBidSize(), "max of maxima (c2's 30 survives c3)");
        assertEquals(8L, cell.buyContracts(), "sum");
        assertEquals(10L, cell.sellContracts(), "sum");
        assertEquals(130.0, cell.buyPremium());
        assertEquals(90.0, cell.sellPremium());
        assertEquals("SELL", cell.tradeDotSide(), "recomputed from summed contracts (10 > 8)");
        assertEquals("PULLED", cell.bidState(), "newest MEANINGFUL state (c3's NEUTRAL does not overwrite)");
        assertEquals("EATEN", cell.askState());
    }

    // AC7b (internal fields not in the projection): open-from-oldest, weighted avg, delta recompute,
    // dominant-action recompute — the §5 table is folded over the FULL cell fields.
    @Test
    void goldenVectorInternalFieldsFollowTheTable() {
        SessionAggregate.CellFold fold = foldCells(List.of(parsedCell(c1()), parsedCell(c2()), parsedCell(c3())),
                new long[]{M0, M0 + 1_000L, M0 + 2_000L});
        assertEquals(10L, fold.openBidSize, "open from OLDEST constituent (true minute open)");
        assertEquals(4L, fold.openAskSize);
        assertEquals(10L, fold.bidSizeDelta(), "RECOMPUTED newest.last - oldest.open = 20 - 10");
        assertEquals(10L, fold.bidSizeDeltaSum, "delta sums add: 2 - 4 + 12");
        assertEquals(12.75, fold.avgBidSize(), 1e-9, "quoteUpdateCount-weighted mean (11*2+9+20)/4");
        assertEquals("BID_PULL", fold.dominantBidAction(), "recomputed: pull 3 >= refill 3 -> PULL");
        assertEquals(M0 + 2_500L, fold.eventTimeMs, "eventTimeMs = max");
        assertTrue(fold.stale, "stale = newest constituent");
        assertEquals("d3", fold.diagnostics, "diagnostics = newest verbatim");
    }

    @Test
    void dominantActionFallsBackToNewestConstituentWhenBothSumsZero() {
        SessionAggregate.CellFold fold = foldCells(List.of(
                        parsedCell(cell(6000.0, "CALL", "dominantBidAction", "STABLE", "quoteUpdateCount", 1)),
                        parsedCell(cell(6000.0, "CALL", "dominantBidAction", "LOCKED_OR_CROSSED", "quoteUpdateCount", 1))),
                new long[]{M0, M0 + 1_000L});
        assertEquals("LOCKED_OR_CROSSED", fold.dominantBidAction(),
                "both pull/refill sums 0 -> newest constituent's action");
    }

    /**
     * AC7c: the §5 CONTINUABILITY INVARIANT — every projected field folds associatively, so a fold
     * split at ANY point, continued FROM THE PROJECTION with the client's rules (max from max, sums
     * add, last-by-payload-time replaces), equals the fold of the whole. rawP99 excluded per §5.
     */
    @Test
    void projectionContinuesAssociativelyFromAnySplit() {
        List<HistoryFrame.Cell> constituents = List.of(parsedCell(c1()), parsedCell(c2()), parsedCell(c3()));
        long[] times = {M0, M0 + 1_000L, M0 + 2_000L};
        ChainSnapshot.CellProjection whole =
                foldCells(constituents, times).project(false);
        for (int split = 1; split < constituents.size(); split++) {
            ChainSnapshot.CellProjection prefix =
                    foldCells(constituents.subList(0, split), java.util.Arrays.copyOfRange(times, 0, split))
                            .project(false);
            ChainSnapshot.CellProjection continued = continueFromProjection(prefix,
                    constituents.subList(split, constituents.size()),
                    java.util.Arrays.copyOfRange(times, split, times.length));
            assertEquals(whole, continued, "split at " + split + " must equal the whole fold");
        }
    }

    /** The client-side continuation rules (§5): applied to the projection alone, no raw prefix state. */
    private static ChainSnapshot.CellProjection continueFromProjection(
            ChainSnapshot.CellProjection base, List<HistoryFrame.Cell> suffix, long[] times) {
        long lastBid = base.lastBidSize();
        long lastAsk = base.lastAskSize();
        long maxBid = base.maxBidSize();
        long maxAsk = base.maxAskSize();
        long buy = base.buyContracts();
        long sell = base.sellContracts();
        double buyPrem = base.buyPremium();
        double sellPrem = base.sellPremium();
        String bidState = base.bidState();
        String askState = base.askState();
        for (int i = 0; i < suffix.size(); i++) {
            HistoryFrame.Cell c = suffix.get(i);
            lastBid = c.lastBidSize();                 // last-by-payload-time replaces
            lastAsk = c.lastAskSize();
            maxBid = Math.max(maxBid, c.maxBidSize()); // max continues from max
            maxAsk = Math.max(maxAsk, c.maxAskSize());
            buy += c.buyContracts();                   // sum continues from sum
            sell += c.sellContracts();
            buyPrem += c.buyPremium();
            sellPrem += c.sellPremium();
            if (!"NEUTRAL".equals(c.bidState()) && !"INSUFFICIENT_DATA".equals(c.bidState())) {
                bidState = c.bidState();
            }
            if (!"NEUTRAL".equals(c.askState()) && !"INSUFFICIENT_DATA".equals(c.askState())) {
                askState = c.askState();
            }
        }
        String dot; // tradeDotSide recomputed from the sums
        if (buy > 0 && sell > 0) {
            dot = buy == sell ? "MIXED" : (buy > sell ? "BUY" : "SELL");
        } else if (buy > 0) {
            dot = "BUY";
        } else if (sell > 0) {
            dot = "SELL";
        } else {
            dot = "NONE";
        }
        return new ChainSnapshot.CellProjection(base.strike(), base.optionSide(), lastBid, lastAsk,
                maxBid, maxAsk, buy, sell, buyPrem, sellPrem, dot, bidState, askState);
    }

    // AC8: DEGRADED collapse + freshness worst-of + a 1-second wall inside a quiet minute survives in max.
    @Test
    void degradedCollapseAndFreshnessWorstOf() {
        LiquidityHistoryStore store = publishedStore(
                frame(SYMBOL, EXPIRY, M0, "LIVE", "FULL", List.of(6000.0),
                        cell(6000.0, "CALL", "maxBidSize", 500L, "lastBidSize", 5L,
                                "bidState", "STABLE_WALL", "askState", "EATEN")),
                frame(SYMBOL, EXPIRY, M0 + 1_000L, "GAP", "DEGRADED", List.of(6000.0),
                        cell(6000.0, "CALL", "maxBidSize", 6L, "lastBidSize", 6L)),
                frame(SYMBOL, EXPIRY, M0 + 2_000L, "STALE", "FULL", List.of(6000.0),
                        cell(6000.0, "CALL", "maxBidSize", 4L, "lastBidSize", 4L)));

        ChainSnapshot.BucketProjection bucket = snapshot(store, TRADING_DAY).buckets().get(0);
        assertEquals("DEGRADED", bucket.inputQuality(), "ANY DEGRADED constituent degrades the bucket");
        assertEquals("STALE", bucket.freshness(), "worst-of: STALE > GAP > LIVE");
        ChainSnapshot.CellProjection cell = bucket.cells().get(0);
        assertEquals("INSUFFICIENT_DATA", cell.bidState(), "DEGRADED collapse of interpretation states");
        assertEquals("INSUFFICIENT_DATA", cell.askState());
        assertEquals(500L, cell.maxBidSize(), "the 1-second wall survives in maxBidSize");
        assertEquals(4L, cell.lastBidSize(), "sizes/counters still reported under collapse");
    }

    // rawP99 nearest-rank formula over the pooled per-cell lastBid+lastAsk samples.
    @Test
    void rawP99UsesNearestRankOverThePooledSamples() {
        ObjectNode[] cells = new ObjectNode[100];
        List<Double> strikes = new ArrayList<>();
        for (int i = 1; i <= 100; i++) {
            cells[i - 1] = cell(6000.0 + i, "CALL", "lastBidSize", (long) i);
            strikes.add(6000.0 + i);
        }
        LiquidityHistoryStore store = publishedStore(frame(SYMBOL, EXPIRY, M0, "LIVE", "FULL", strikes, cells));
        // N=100 -> rank = ceil(0.95*100) = 95 -> sorted[94] = 95 (NOT 100: nearest-rank, not max).
        // The percentile moved 0.99 -> 0.95 with the 2026-07-30 colour calibration; the wire
        // field keeps its frozen `rawP99` name.
        assertEquals(95L, snapshot(store, TRADING_DAY).buckets().get(0).rawP99());
    }

    /**
     * Pins THIS repo's default only. It cannot see liquidity-heatmap.js, so it does NOT prove the
     * UI agrees — an earlier version of this test claimed it did, which was false: changing the UI
     * back to 0.99 would leave it green.
     *
     * <p>The actual anti-drift mechanism is deployment-level: web and gateway both read the SAME
     * {@code HEATMAP_COLOR_SATURATION_PCT} key from the shared options-edge-config ConfigMap, so
     * one value feeds both. These in-code defaults only apply when that key is absent. The
     * response's {@code saturationPct} field makes any residual divergence observable at runtime.
     */
    @Test
    void saturationPercentileDefaultIsPinned() {
        assertEquals(0.95d, SessionAggregate.BucketFold.SATURATION_PCT, 1e-9,
                "gateway default; the UI default lives in liquidity-heatmap.js and is NOT checked here");
    }

    /** A malformed or out-of-range override must fall back, never zero or invert the scale. */
    @Test
    void malformedSaturationOverrideFallsBackToTheDefault() {
        for (String bad : new String[] {"", "abc", "0", "-0.5", "1.5", "NaN"}) {
            assertEquals(0.95d, SessionAggregate.BucketFold.resolveSaturationPct(bad), 1e-9,
                    "rejected override: '" + bad + "'");
        }
        assertEquals(0.99d, SessionAggregate.BucketFold.resolveSaturationPct("0.99"), 1e-9);
        assertEquals(1.0d, SessionAggregate.BucketFold.resolveSaturationPct("1.0"), 1e-9);
    }

    @Test
    void rawP99FinalizesWhenTheBucketClosesAndStaysLiveOnTheBoundaryBucket() {
        LiquidityHistoryStore store = publishedStore(
                frame(SYMBOL, EXPIRY, M0, "LIVE", "FULL", List.of(6000.0),
                        cell(6000.0, "CALL", "lastBidSize", 10L)),
                frame(SYMBOL, EXPIRY, M0 + 60_000L, "LIVE", "FULL", List.of(6000.0),
                        cell(6000.0, "CALL", "lastBidSize", 99L)),
                frame(SYMBOL, EXPIRY, M0 + 61_000L, "LIVE", "FULL", List.of(6000.0),
                        cell(6000.0, "CALL", "lastBidSize", 1L)));
        ChainSnapshot snapshot = snapshot(store, TRADING_DAY);
        assertEquals(2, snapshot.buckets().size());
        assertEquals(10L, snapshot.buckets().get(0).rawP99(), "closed bucket: finalized from its own pool");
        // boundary bucket final-at-serve: pool {99, 1}, rank ceil(1.98)=2 -> sorted[1] = 99.
        assertEquals(99L, snapshot.buckets().get(1).rawP99());
    }

    // AC4: tradeDate routing — late-yesterday folds to yesterday, out-of-session dropped + counted,
    // older-than-previous-trading-day dropped + counted, in-session boundary honored.
    @Test
    void tradeDateRoutingIsolatesDatesAndDropsOutOfSessionFrames() {
        LiquidityHistoryTestSupport.MutableClock clock =
                new LiquidityHistoryTestSupport.MutableClock(et(TRADING_DAY, 10, 5));
        LiquidityHistoryStore store = seamStore(clock, config(4, 200L * 1024 * 1024, 300L), Set::of);
        store.beginEpochState();
        // today's frame
        store.foldForTest(frame(SYMBOL, EXPIRY, M0, "LIVE", "FULL", List.of(6000.0),
                cell(6000.0, "CALL", "buyContracts", 1L)), 0);
        // LATE frame for YESTERDAY (previous trading day, retained) — folds into ITS date, never today's
        long yesterdayLate = et(PREVIOUS_TRADING_DAY, 15, 59).toEpochMilli();
        store.foldForTest(frame(SYMBOL, EXPIRY, yesterdayLate, "LIVE", "FULL", List.of(6000.0),
                cell(6000.0, "CALL", "buyContracts", 7L)), 0);
        // out-of-session: pre-open today
        store.foldForTest(frame(SYMBOL, EXPIRY, et(TRADING_DAY, 8, 0).toEpochMilli(), "LIVE", "FULL",
                List.of(6000.0), cell(6000.0, "CALL")), 0);
        // out-of-session: Saturday (no session window at all)
        store.foldForTest(frame(SYMBOL, EXPIRY, et(LocalDate.of(2026, 6, 20), 12, 0).toEpochMilli(),
                "LIVE", "FULL", List.of(6000.0), cell(6000.0, "CALL")), 0);
        // older than the previous trading day (Friday 06-19, inside a session window) -> late-dropped
        store.foldForTest(frame(SYMBOL, EXPIRY, et(LocalDate.of(2026, 6, 19), 10, 0).toEpochMilli(),
                "LIVE", "FULL", List.of(6000.0), cell(6000.0, "CALL")), 0);
        store.completeEpochState();

        assertEquals(2L, store.outOfSessionDroppedCount());
        assertEquals(1L, store.lateFramesDroppedCount());
        assertTrue(store.publishedContains(CHAIN, TRADING_DAY));
        assertTrue(store.publishedContains(CHAIN, PREVIOUS_TRADING_DAY));

        ChainSnapshot today = snapshot(store, TRADING_DAY);
        assertEquals(1, today.buckets().size());
        assertEquals(1L, today.buckets().get(0).cells().get(0).buyContracts(),
                "yesterday's late frame must not leak into today");
        ChainSnapshot yesterday = snapshot(store, PREVIOUS_TRADING_DAY);
        assertEquals(7L, yesterday.buckets().get(0).cells().get(0).buyContracts());
        assertEquals(yesterdayLate, yesterday.watermarkBucketStartMs());
    }

    @Test
    void sessionWindowIsTheClosedIntervalOpenToClose() {
        LiquidityHistoryTestSupport.MutableClock clock =
                new LiquidityHistoryTestSupport.MutableClock(et(TRADING_DAY, 16, 30));
        LiquidityHistoryStore store = seamStore(clock, config(4, 200L * 1024 * 1024, 300L), Set::of);
        assertEquals(TRADING_DAY, store.tradeDateOf(sessionOpenMs(TRADING_DAY)), "open inclusive");
        // §4 says the [open, close] window CONTAINS the instant — close is inclusive as written.
        assertEquals(TRADING_DAY, store.tradeDateOf(et(TRADING_DAY, 16, 0).toEpochMilli()), "close inclusive");
        assertNull(store.tradeDateOf(et(TRADING_DAY, 16, 0).toEpochMilli() + 1L), "past close");
        assertNull(store.tradeDateOf(sessionOpenMs(TRADING_DAY) - 1L), "pre-open");
    }

    // Malformed frames: rejected + counted, never thrown, never partially folded.
    @Test
    void malformedFramesAreRejectedAndCounted() {
        LiquidityHistoryTestSupport.MutableClock clock =
                new LiquidityHistoryTestSupport.MutableClock(et(TRADING_DAY, 10, 5));
        LiquidityHistoryStore store = seamStore(clock, config(4, 200L * 1024 * 1024, 300L), Set::of);
        store.beginEpochState();
        store.foldForTest("not json at all", 0);
        store.foldForTest("[1,2,3]", 0);
        store.foldForTest(frame("", EXPIRY, M0, "LIVE", "FULL", List.of(), cell(6000.0, "CALL")), 0);   // blank symbol
        store.foldForTest(frame(SYMBOL, "", M0, "LIVE", "FULL", List.of(), cell(6000.0, "CALL")), 0);    // blank expiry
        store.foldForTest("{\"symbol\":\"SPX\",\"expiry\":\"2026-06-23\",\"bucketStartMs\":" + M0
                + ",\"bucketEndMs\":" + M0 + "}", 0);                                                    // end<=start + no cells
        store.foldForTest(frame(SYMBOL, EXPIRY, M0, "LIVE", "FULL", List.of(),
                cell(6000.0, "NEITHER")), 0);                                                            // bad side
        store.completeEpochState();
        assertEquals(6L, store.framesRejectedCount());
        assertFalse(store.publishedContains(CHAIN, TRADING_DAY), "nothing may fold from rejected frames");
    }

    // Codex Gate-2 finding 2 (AC12): retention-hole detection is RAW-frame (1s) granular — the
    // opening seconds can be deleted while the first surviving frame still lands inside the 09:30
    // minute bucket, and the response must still say truncated=true.
    @Test
    void retentionSuspectTruncationIsRawFrameGranular() {
        // First surviving raw frame at open+5s, SAME minute bucket as the open -> truncated.
        SessionAggregate suspectMissingSeconds = new SessionAggregate(SYMBOL, EXPIRY, TRADING_DAY, true);
        suspectMissingSeconds.fold(parsedFrame(M0 + 5_000L), 0L);
        assertTrue(suspectMissingSeconds.snapshot(M0).truncatedStart(),
                "opening seconds deleted inside the first minute bucket must report truncated");

        // First surviving raw frame EXACTLY at the open -> nothing can be missing -> not truncated.
        SessionAggregate suspectComplete = new SessionAggregate(SYMBOL, EXPIRY, TRADING_DAY, true);
        suspectComplete.fold(parsedFrame(M0), 0L);
        assertFalse(suspectComplete.snapshot(M0).truncatedStart());

        // No retention suspicion on the partition -> a late-starting producer is NOT a hole.
        SessionAggregate notSuspect = new SessionAggregate(SYMBOL, EXPIRY, TRADING_DAY, false);
        notSuspect.fold(parsedFrame(M0 + 5_000L), 0L);
        assertFalse(notSuspect.snapshot(M0).truncatedStart());
    }

    // ---- helpers ----

    private static HistoryFrame parsedFrame(long bucketStartMs) {
        HistoryFrame parsed = HistoryFrame.parse(frame(SYMBOL, EXPIRY, bucketStartMs, "LIVE", "FULL",
                List.of(6000.0), cell(6000.0, "CALL")), MAPPER);
        assertNotNull(parsed);
        return parsed;
    }

    private static HistoryFrame.Cell parsedCell(ObjectNode cellNode) {
        HistoryFrame frame = HistoryFrame.parse(
                frame(SYMBOL, EXPIRY, M0, "LIVE", "FULL", List.of(), cellNode), MAPPER);
        assertNotNull(frame);
        return frame.cells().get(0);
    }

    private static SessionAggregate.CellFold foldCells(List<HistoryFrame.Cell> cells, long[] constituentMs) {
        SessionAggregate.CellFold fold = new SessionAggregate.CellFold(cells.get(0).strike(),
                cells.get(0).optionSide());
        for (int i = 0; i < cells.size(); i++) {
            fold.fold(cells.get(i), constituentMs[i]);
        }
        return fold;
    }
}

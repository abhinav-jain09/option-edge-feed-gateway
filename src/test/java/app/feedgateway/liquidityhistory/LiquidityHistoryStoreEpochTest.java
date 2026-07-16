package app.feedgateway.liquidityhistory;

import app.feedgateway.GatewaySettings;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.MockConsumer;
import org.apache.kafka.clients.consumer.OffsetAndTimestamp;
import org.apache.kafka.common.KafkaException;
import org.apache.kafka.common.PartitionInfo;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

import static app.feedgateway.liquidityhistory.LiquidityHistoryTestSupport.PREVIOUS_TRADING_DAY;
import static app.feedgateway.liquidityhistory.LiquidityHistoryTestSupport.TRADING_DAY;
import static app.feedgateway.liquidityhistory.LiquidityHistoryTestSupport.await;
import static app.feedgateway.liquidityhistory.LiquidityHistoryTestSupport.cell;
import static app.feedgateway.liquidityhistory.LiquidityHistoryTestSupport.config;
import static app.feedgateway.liquidityhistory.LiquidityHistoryTestSupport.et;
import static app.feedgateway.liquidityhistory.LiquidityHistoryTestSupport.frame;
import static app.feedgateway.liquidityhistory.LiquidityHistoryTestSupport.seamStore;
import static app.feedgateway.liquidityhistory.LiquidityHistoryTestSupport.sessionOpenMs;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Epoch readiness/swap/guards (AC6, AC17, AC16) and deterministic eviction (AC15). */
class LiquidityHistoryStoreEpochTest {

    private static final String TOPIC = "strike-liquidity-heatmap-dashboard";
    private static final TopicPartition TP0 = new TopicPartition(TOPIC, 0);
    private static final TopicPartition TP1 = new TopicPartition(TOPIC, 1);
    private static final String SYMBOL = "SPX";
    private static final String EXPIRY = "2026-06-23";
    private static final String CHAIN = SYMBOL + "|" + EXPIRY;
    private static final long M0 = sessionOpenMs(TRADING_DAY);

    @Test
    void expectedPartitionCountComesFromSettings() {
        String key = "HEATMAP_HISTORY_EXPECTED_PARTITIONS";
        String previous = System.getProperty(key);
        try {
            System.setProperty(key, "32");
            assertEquals(32, LiquidityHistoryStore.Config.fromSettings(new GatewaySettings())
                    .expectedPartitions());
        } finally {
            if (previous == null) {
                System.clearProperty(key);
            } else {
                System.setProperty(key, previous);
            }
        }
    }

    private static String frameWithBuy(long bucketStartMs, long buyContracts) {
        return frame(SYMBOL, EXPIRY, bucketStartMs, "LIVE", "FULL", List.of(6000.0),
                cell(6000.0, "CALL", "buyContracts", buyContracts));
    }

    private static MockConsumer<String, String> mockConsumer(int partitions, Map<TopicPartition, Long> end) {
        MockConsumer<String, String> mc = new MockConsumer<>("earliest");
        List<PartitionInfo> infos = new java.util.ArrayList<>();
        for (int p = 0; p < partitions; p++) {
            infos.add(new PartitionInfo(TOPIC, p, null, null, null));
        }
        mc.updatePartitions(TOPIC, infos);
        Map<TopicPartition, Long> beginning = new HashMap<>();
        for (TopicPartition tp : end.keySet()) {
            beginning.put(tp, 0L);
        }
        mc.updateBeginningOffsets(beginning);
        mc.updateEndOffsets(end);
        return mc;
    }

    /** offsetsForTimes seam: partition 0 has session data at offset 0; partition 1 has none (null). */
    private static LiquidityHistoryStore.TimeSeek seekP0ToZero() {
        return (consumer, timestamps) -> {
            Map<TopicPartition, OffsetAndTimestamp> result = new HashMap<>();
            result.put(TP0, new OffsetAndTimestamp(0L, timestamps.get(TP0)));
            result.put(TP1, null);
            return result;
        };
    }

    private static LiquidityHistoryStore threadedStore(Queue<Consumer<String, String>> consumers,
                                                       LiquidityHistoryStore.TimeSeek timeSeek) {
        LiquidityHistoryTestSupport.MutableClock clock =
                new LiquidityHistoryTestSupport.MutableClock(et(TRADING_DAY, 10, 5));
        LiquidityHistoryStore store = new LiquidityHistoryStore(config(4, 200L * 1024 * 1024, 300L),
                LiquidityHistoryTestSupport.calendar(),
                () -> {
                    Consumer<String, String> next = consumers.poll();
                    if (next == null) {
                        throw new IllegalStateException("consumer factory exhausted");
                    }
                    return next;
                },
                timeSeek, Set::of, clock);
        store.start();
        return store;
    }

    // AC6: serve begins only at position >= boot endOffsets on ALL partitions; frames landing after
    // the boot snapshot keep folding continuously (catch-up and live are the same loop).
    @Test
    void readinessGatesOnBootEndOffsetsThenFoldsContinuously() throws Exception {
        MockConsumer<String, String> mc = mockConsumer(2, Map.of(TP0, 3L, TP1, 0L));
        // Two of the three pre-boot records arrive first; the store must NOT serve at position 2 < 3.
        mc.schedulePollTask(() -> {
            mc.addRecord(new ConsumerRecord<>(TOPIC, 0, 0L, CHAIN, frameWithBuy(M0, 1L)));
            mc.addRecord(new ConsumerRecord<>(TOPIC, 0, 1L, CHAIN, frameWithBuy(M0 + 1_000L, 1L)));
        });
        Queue<Consumer<String, String>> consumers = new ArrayDeque<>(List.of(mc));
        LiquidityHistoryStore store = threadedStore(consumers, seekP0ToZero());
        try {
            // Deterministically NOT ready: the third pre-boot record has not been polled yet.
            Thread.sleep(150L);
            assertEquals(LiquidityHistoryStore.ServeStatus.UNAVAILABLE,
                    store.query(SYMBOL, EXPIRY, TRADING_DAY).status(),
                    "must 503 until position >= endOffsets for ALL partitions");
            mc.schedulePollTask(() ->
                    mc.addRecord(new ConsumerRecord<>(TOPIC, 0, 2L, CHAIN, frameWithBuy(M0 + 2_000L, 1L))));
            await("epoch swap after full catch-up", () ->
                    store.query(SYMBOL, EXPIRY, TRADING_DAY).status() == LiquidityHistoryStore.ServeStatus.OK);
            ChainSnapshot snapshot = store.query(SYMBOL, EXPIRY, TRADING_DAY).snapshot();
            assertEquals(3L, snapshot.buckets().get(0).cells().get(0).buyContracts(),
                    "every pre-boot frame folded exactly once");
            // Post-snapshot records fold continuously on the SAME consumer — no handoff.
            mc.schedulePollTask(() ->
                    mc.addRecord(new ConsumerRecord<>(TOPIC, 0, 3L, CHAIN, frameWithBuy(M0 + 3_000L, 1L))));
            await("live fold advances the watermark", () ->
                    store.query(SYMBOL, EXPIRY, TRADING_DAY).snapshot().watermarkBucketStartMs() == M0 + 3_000L);
            assertEquals(4L, store.query(SYMBOL, EXPIRY, TRADING_DAY).snapshot()
                    .buckets().get(0).cells().get(0).buyContracts());
        } finally {
            store.close();
        }
    }

    // AC6: a mid-catch-up consumer exception + reseek folds every frame EXACTLY once — the failed
    // epoch's partial shadow is discarded and the new epoch starts empty from the session open.
    @Test
    void midCatchupExceptionAndReseekNeverDoubleFolds() {
        MockConsumer<String, String> failing = mockConsumer(2, Map.of(TP0, 3L, TP1, 0L));
        failing.schedulePollTask(() -> {
            failing.addRecord(new ConsumerRecord<>(TOPIC, 0, 0L, CHAIN, frameWithBuy(M0, 1L)));
            failing.addRecord(new ConsumerRecord<>(TOPIC, 0, 1L, CHAIN, frameWithBuy(M0 + 1_000L, 1L)));
        });
        failing.schedulePollTask(() -> failing.setPollException(new KafkaException("mid-catch-up failure")));
        MockConsumer<String, String> recovered = mockConsumer(2, Map.of(TP0, 3L, TP1, 0L));
        recovered.schedulePollTask(() -> {
            recovered.addRecord(new ConsumerRecord<>(TOPIC, 0, 0L, CHAIN, frameWithBuy(M0, 1L)));
            recovered.addRecord(new ConsumerRecord<>(TOPIC, 0, 1L, CHAIN, frameWithBuy(M0 + 1_000L, 1L)));
            recovered.addRecord(new ConsumerRecord<>(TOPIC, 0, 2L, CHAIN, frameWithBuy(M0 + 2_000L, 1L)));
        });
        LiquidityHistoryStore store = threadedStore(
                new ArrayDeque<>(List.of(failing, recovered)), seekP0ToZero());
        try {
            await("recovered epoch swaps", () ->
                    store.query(SYMBOL, EXPIRY, TRADING_DAY).status() == LiquidityHistoryStore.ServeStatus.OK);
            assertEquals(3L, store.query(SYMBOL, EXPIRY, TRADING_DAY).snapshot()
                            .buckets().get(0).cells().get(0).buyContracts(),
                    "offsets 0/1 were read by BOTH epochs but folded once (fresh shadow per epoch)");
        } finally {
            store.close();
        }
    }

    // AC16 (Codex Gate-2 finding 1): partition-count change -> STICKY alarm + 503 all chains. A
    // reverted count must NOT clear it — records written while the count differed may already sit
    // on the wrong partition; only operator action (restart) clears the alarm.
    @Test
    void partitionTopologyAlarmIsStickyEvenAfterRevert() throws Exception {
        MockConsumer<String, String> boot = mockConsumer(2, Map.of(TP0, 0L, TP1, 0L));
        MockConsumer<String, String> changed = mockConsumer(3,
                Map.of(TP0, 0L, TP1, 0L, new TopicPartition(TOPIC, 2), 0L));
        MockConsumer<String, String> reverted = mockConsumer(2, Map.of(TP0, 0L, TP1, 0L));
        LiquidityHistoryStore store = threadedStore(
                new ArrayDeque<>(List.of(boot, changed, reverted)), seekP0ToZero());
        try {
            await("boot epoch swaps", () ->
                    store.query(SYMBOL, EXPIRY, TRADING_DAY).status() == LiquidityHistoryStore.ServeStatus.OK);
            store.requestRebuild(); // next epoch sees 3 partitions
            await("topology alarm raised", store::topologyAlarm);
            assertEquals(LiquidityHistoryStore.ServeStatus.TOPOLOGY_ALARM,
                    store.query(SYMBOL, EXPIRY, TRADING_DAY).status(), "all chains 503 under the alarm");
            // The backoff retry consumes the REVERTED 2-partition consumer (~1s in). The alarm must
            // survive it: sleep past that retry, then pin that nothing cleared.
            Thread.sleep(2_500L);
            assertTrue(store.topologyAlarm(), "sticky for the process lifetime — revert proves nothing");
            assertEquals(LiquidityHistoryStore.ServeStatus.TOPOLOGY_ALARM,
                    store.query(SYMBOL, EXPIRY, TRADING_DAY).status(),
                    "requests keep answering 503 until operator restart");
        } finally {
            store.close();
        }
    }

    // Codex Gate-2 finding 4 / AC15: a cache miss for the RETAINED previous trading day widens the
    // rebuild epoch's seek to THAT day's session open; tradeDateOf routing then rebuilds the
    // prior-day aggregate transparently and the request succeeds after the swap.
    @Test
    void retainedPriorDayCacheMissRebuildsWithWidenedSeek() {
        long yesterdayLate = et(PREVIOUS_TRADING_DAY, 15, 59).toEpochMilli();
        long yesterdayOpenMs = sessionOpenMs(PREVIOUS_TRADING_DAY);
        // Timestamp-sensitive seek seam: yesterday's open (or earlier) -> offset 0; today's -> offset 1.
        LiquidityHistoryStore.TimeSeek seek = (consumer, timestamps) -> {
            Map<TopicPartition, OffsetAndTimestamp> result = new HashMap<>();
            long requested = timestamps.get(TP0);
            result.put(TP0, new OffsetAndTimestamp(requested <= yesterdayOpenMs ? 0L : 1L, requested));
            result.put(TP1, null);
            return result;
        };
        String yesterdayFrame = frame(SYMBOL, EXPIRY, yesterdayLate, "LIVE", "FULL", List.of(6000.0),
                cell(6000.0, "CALL", "buyContracts", 7L));
        // Boot epoch seeks TODAY's open (offset 1) and never reads yesterday's record.
        MockConsumer<String, String> bootConsumer = mockConsumer(2, Map.of(TP0, 2L, TP1, 0L));
        bootConsumer.schedulePollTask(() ->
                bootConsumer.addRecord(new ConsumerRecord<>(TOPIC, 0, 1L, CHAIN, frameWithBuy(M0, 2L))));
        // The widened rebuild epoch seeks YESTERDAY's open (offset 0) and reads both sessions.
        MockConsumer<String, String> widened = mockConsumer(2, Map.of(TP0, 2L, TP1, 0L));
        widened.schedulePollTask(() -> {
            widened.addRecord(new ConsumerRecord<>(TOPIC, 0, 0L, CHAIN, yesterdayFrame));
            widened.addRecord(new ConsumerRecord<>(TOPIC, 0, 1L, CHAIN, frameWithBuy(M0, 2L)));
        });
        LiquidityHistoryStore store = threadedStore(
                new ArrayDeque<>(List.of(bootConsumer, widened)), seek);
        try {
            await("boot epoch swaps (today only)", () ->
                    store.query(SYMBOL, EXPIRY, TRADING_DAY).status() == LiquidityHistoryStore.ServeStatus.OK);
            // Cache miss for the retained prior session: 503 + widened rebuild trigger, NOT a
            // permanent empty+truncated answer.
            assertEquals(LiquidityHistoryStore.ServeStatus.UNAVAILABLE,
                    store.query(SYMBOL, EXPIRY, PREVIOUS_TRADING_DAY).status());
            await("widened rebuild serves the prior day", () ->
                    store.query(SYMBOL, EXPIRY, PREVIOUS_TRADING_DAY).status() == LiquidityHistoryStore.ServeStatus.OK
                            && !store.query(SYMBOL, EXPIRY, PREVIOUS_TRADING_DAY).snapshot().buckets().isEmpty());
            assertEquals(7L, store.query(SYMBOL, EXPIRY, PREVIOUS_TRADING_DAY).snapshot()
                    .buckets().get(0).cells().get(0).buyContracts());
            assertEquals(2L, store.query(SYMBOL, EXPIRY, TRADING_DAY).snapshot()
                            .buckets().get(0).cells().get(0).buyContracts(),
                    "today's aggregate survives the widened rebuild (normal tradeDate routing)");
        } finally {
            store.close();
        }
    }

    // AC17: during a rebuild epoch, non-evicted chains keep serving the FROZEN published store; the
    // shadow replaces it atomically at catch-up — no torn/mixed reads.
    @Test
    void epochSwapServesFrozenPublishedUntilAtomicReplace() {
        LiquidityHistoryTestSupport.MutableClock clock =
                new LiquidityHistoryTestSupport.MutableClock(et(TRADING_DAY, 10, 5));
        LiquidityHistoryStore store = seamStore(clock, config(4, 200L * 1024 * 1024, 300L), Set::of);
        store.beginEpochState();
        store.foldForTest(frameWithBuy(M0, 1L), 0);
        store.completeEpochState();
        LiquidityHistoryStore.ServeDecision steady = store.query(SYMBOL, EXPIRY, TRADING_DAY);
        assertEquals(LiquidityHistoryStore.ServeStatus.OK, steady.status());
        assertFalse(steady.epochRebuilding());
        assertEquals(1L, steady.snapshot().buckets().get(0).cells().get(0).buyContracts());

        store.beginEpochState(); // post-exception rebuild starts
        store.foldForTest(frameWithBuy(M0, 5L), 0);         // folds ONLY into the shadow
        store.foldForTest(frameWithBuy(M0 + 1_000L, 5L), 0);
        LiquidityHistoryStore.ServeDecision duringRebuild = store.query(SYMBOL, EXPIRY, TRADING_DAY);
        assertEquals(LiquidityHistoryStore.ServeStatus.OK, duringRebuild.status());
        assertTrue(duringRebuild.epochRebuilding(), "spec §3 step 4: frozen store + rebuilding header");
        assertEquals(1L, duringRebuild.snapshot().buckets().get(0).cells().get(0).buyContracts(),
                "the shadow's folds must be invisible until the swap (snapshot isolation)");
        assertEquals(M0, duringRebuild.snapshot().watermarkBucketStartMs(), "frozen watermark is honest");

        store.completeEpochState(); // atomic replace
        LiquidityHistoryStore.ServeDecision afterSwap = store.query(SYMBOL, EXPIRY, TRADING_DAY);
        assertFalse(afterSwap.epochRebuilding());
        assertEquals(10L, afterSwap.snapshot().buckets().get(0).cells().get(0).buyContracts());
        assertEquals(M0 + 1_000L, afterSwap.snapshot().watermarkBucketStartMs());
    }

    // AC16: steady-state lag guard -> 503 with the clamped Retry-After (never a silent hole).
    @Test
    void lagGuardGatesServingWithClampedRetryAfter() {
        LiquidityHistoryTestSupport.MutableClock clock =
                new LiquidityHistoryTestSupport.MutableClock(et(TRADING_DAY, 10, 5));
        LiquidityHistoryStore store = seamStore(clock, config(4, 200L * 1024 * 1024, 300L), Set::of);
        store.beginEpochState();
        store.foldForTest(frameWithBuy(M0, 1L), 0);
        store.completeEpochState();

        store.setLagRecordsForTest(301L);
        LiquidityHistoryStore.ServeDecision lagging = store.query(SYMBOL, EXPIRY, TRADING_DAY);
        assertEquals(LiquidityHistoryStore.ServeStatus.LAGGING, lagging.status());
        assertEquals(5, lagging.retryAfterSeconds(), "Retry-After clamps up to the [5,60] floor");
        store.setLagRecordsForTest(10_000_000L);
        assertEquals(60, store.query(SYMBOL, EXPIRY, TRADING_DAY).retryAfterSeconds(),
                "Retry-After clamps down to the [5,60] ceiling");
        store.setLagRecordsForTest(0L);
        assertEquals(LiquidityHistoryStore.ServeStatus.OK, store.query(SYMBOL, EXPIRY, TRADING_DAY).status());
    }

    @Test
    void rebuildFailedAnswers503WithRetryAfter60() {
        LiquidityHistoryTestSupport.MutableClock clock =
                new LiquidityHistoryTestSupport.MutableClock(et(TRADING_DAY, 10, 5));
        LiquidityHistoryStore store = seamStore(clock, config(4, 200L * 1024 * 1024, 300L), Set::of);
        store.beginEpochState();
        store.abortEpochState(); // REBUILD_FAILED (catch-up abort)
        LiquidityHistoryStore.ServeDecision decision = store.query(SYMBOL, EXPIRY, TRADING_DAY);
        assertEquals(LiquidityHistoryStore.ServeStatus.UNAVAILABLE, decision.status());
        assertEquals(60, decision.retryAfterSeconds(), "spec §2: REBUILD_FAILED -> Retry-After 60");
    }

    // ---- AC15: deterministic eviction ----

    private static String chainFrame(String symbol, long bucketStartMs) {
        return frame(symbol, EXPIRY, bucketStartMs, "LIVE", "FULL", List.of(6000.0),
                cell(6000.0, "CALL", "buyContracts", 1L));
    }

    @Test
    void chainCapEvictsOldestLastFoldFirstAndEvictedChainRebuildsOnRequest() {
        LiquidityHistoryTestSupport.MutableClock clock =
                new LiquidityHistoryTestSupport.MutableClock(et(TRADING_DAY, 10, 0));
        LiquidityHistoryStore store = seamStore(clock, config(2, 200L * 1024 * 1024, 300L), Set::of);
        store.beginEpochState();
        store.foldForTest(chainFrame("AAA", M0), 0);
        clock.set(et(TRADING_DAY, 10, 1));
        store.foldForTest(chainFrame("BBB", M0), 0);
        clock.set(et(TRADING_DAY, 10, 2));
        store.foldForTest(chainFrame("CCC", M0), 0); // 3rd chain breaches maxChains=2
        store.completeEpochState();

        assertEquals(1L, store.evictionsCount("chain_cap"));
        assertFalse(store.publishedContains("AAA|" + EXPIRY, TRADING_DAY), "oldest last-fold evicts first");
        assertTrue(store.publishedContains("BBB|" + EXPIRY, TRADING_DAY));
        assertTrue(store.publishedContains("CCC|" + EXPIRY, TRADING_DAY));
        assertTrue(store.isEvicted("AAA|" + EXPIRY, TRADING_DAY));

        // Eviction is a cache miss, not data loss: the evicted chain 503s AND triggers a rebuild.
        LiquidityHistoryStore.ServeDecision evicted = store.query("AAA", EXPIRY, TRADING_DAY);
        assertEquals(LiquidityHistoryStore.ServeStatus.UNAVAILABLE, evicted.status());
        assertTrue(store.rebuildPending(), "request for an evicted chain triggers the single-flighted rebuild");
    }

    @Test
    void evictionPrefersChainsWithoutActiveWsSubscribers() {
        LiquidityHistoryTestSupport.MutableClock clock =
                new LiquidityHistoryTestSupport.MutableClock(et(TRADING_DAY, 10, 0));
        // AAA is the oldest but has an active WS subscriber — BBB must evict instead.
        LiquidityHistoryStore store = seamStore(clock, config(2, 200L * 1024 * 1024, 300L),
                () -> Set.of("AAA|" + EXPIRY));
        store.beginEpochState();
        store.foldForTest(chainFrame("AAA", M0), 0);
        clock.set(et(TRADING_DAY, 10, 1));
        store.foldForTest(chainFrame("BBB", M0), 0);
        clock.set(et(TRADING_DAY, 10, 2));
        store.foldForTest(chainFrame("CCC", M0), 0);
        store.completeEpochState();

        assertTrue(store.publishedContains("AAA|" + EXPIRY, TRADING_DAY), "WS-subscribed chain survives");
        assertFalse(store.publishedContains("BBB|" + EXPIRY, TRADING_DAY));
        assertTrue(store.publishedContains("CCC|" + EXPIRY, TRADING_DAY));
    }

    // Codex Gate-2 finding 3 / AC15: MULTIPLE subscribed chains (per-session routing: one per live
    // user session) are ALL protected — the unsubscribed chain evicts even when it is the newest.
    @Test
    void evictionProtectsEveryChainWithAnActiveSubscriber() {
        LiquidityHistoryTestSupport.MutableClock clock =
                new LiquidityHistoryTestSupport.MutableClock(et(TRADING_DAY, 10, 0));
        LiquidityHistoryStore store = seamStore(clock, config(2, 200L * 1024 * 1024, 300L),
                () -> Set.of("AAA|" + EXPIRY, "BBB|" + EXPIRY));
        store.beginEpochState();
        store.foldForTest(chainFrame("AAA", M0), 0);
        clock.set(et(TRADING_DAY, 10, 1));
        store.foldForTest(chainFrame("BBB", M0), 0);
        clock.set(et(TRADING_DAY, 10, 2));
        store.foldForTest(chainFrame("CCC", M0), 0); // newest, but the only unsubscribed one
        store.completeEpochState();

        assertTrue(store.publishedContains("AAA|" + EXPIRY, TRADING_DAY));
        assertTrue(store.publishedContains("BBB|" + EXPIRY, TRADING_DAY));
        assertFalse(store.publishedContains("CCC|" + EXPIRY, TRADING_DAY),
                "the chain no session watches evicts first, regardless of recency");
    }

    @Test
    void evictionNeverPicksAChainWithAnInFlightRequest() {
        LiquidityHistoryTestSupport.MutableClock clock =
                new LiquidityHistoryTestSupport.MutableClock(et(TRADING_DAY, 10, 0));
        LiquidityHistoryStore store = seamStore(clock, config(2, 200L * 1024 * 1024, 300L), Set::of);
        store.beginServe("AAA", EXPIRY, TRADING_DAY); // AAA (the would-be victim) is in flight
        store.beginEpochState();
        store.foldForTest(chainFrame("AAA", M0), 0);
        clock.set(et(TRADING_DAY, 10, 1));
        store.foldForTest(chainFrame("BBB", M0), 0);
        clock.set(et(TRADING_DAY, 10, 2));
        store.foldForTest(chainFrame("CCC", M0), 0);
        store.completeEpochState();

        assertTrue(store.publishedContains("AAA|" + EXPIRY, TRADING_DAY), "in-flight chain is never evicted");
        assertFalse(store.publishedContains("BBB|" + EXPIRY, TRADING_DAY), "next preference evicts instead");
        store.endServe("AAA", EXPIRY, TRADING_DAY);
    }

    @Test
    void byteCapEvictsUntilUnderBudget() {
        LiquidityHistoryTestSupport.MutableClock clock =
                new LiquidityHistoryTestSupport.MutableClock(et(TRADING_DAY, 10, 0));
        // Each single-bucket/single-cell aggregate ≈ 2.5KB by the documented constants; 3KB fits one.
        LiquidityHistoryStore store = seamStore(clock, config(4, 3_000L, 300L), Set::of);
        store.beginEpochState();
        store.foldForTest(chainFrame("AAA", M0), 0);
        clock.set(et(TRADING_DAY, 10, 1));
        store.foldForTest(chainFrame("BBB", M0), 0); // breaches 3KB -> AAA (oldest) evicts
        store.completeEpochState();
        assertEquals(1L, store.evictionsCount("byte_cap"));
        assertFalse(store.publishedContains("AAA|" + EXPIRY, TRADING_DAY));
        assertTrue(store.publishedContains("BBB|" + EXPIRY, TRADING_DAY));
    }

    @Test
    void dateRollDropsAggregatesOlderThanThePreviousTradingDay() {
        LiquidityHistoryTestSupport.MutableClock clock =
                new LiquidityHistoryTestSupport.MutableClock(et(TRADING_DAY, 10, 5));
        LiquidityHistoryStore store = seamStore(clock, config(4, 200L * 1024 * 1024, 300L), Set::of);
        store.beginEpochState();
        store.foldForTest(frameWithBuy(M0, 1L), 0);
        store.foldForTest(frame(SYMBOL, EXPIRY, et(PREVIOUS_TRADING_DAY, 15, 59).toEpochMilli(),
                "LIVE", "FULL", List.of(6000.0), cell(6000.0, "CALL")), 0);
        store.completeEpochState();
        assertTrue(store.publishedContains(CHAIN, PREVIOUS_TRADING_DAY));

        // Next trading day (2026-06-24): the retained floor moves to 06-23; 06-22 ages out.
        clock.set(et(LocalDate.of(2026, 6, 24), 10, 0));
        store.dateRollForTest();
        assertEquals(1L, store.evictionsCount("date_roll"));
        assertFalse(store.publishedContains(CHAIN, PREVIOUS_TRADING_DAY));
        assertTrue(store.publishedContains(CHAIN, TRADING_DAY), "yesterday (now within retention) survives");
        assertFalse(store.isEvicted(CHAIN, PREVIOUS_TRADING_DAY),
                "date-roll leaves no rebuild claim — the data is unbuildable, not a cache miss");
    }
}

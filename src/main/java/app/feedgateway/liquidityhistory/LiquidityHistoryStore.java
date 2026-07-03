package app.feedgateway.liquidityhistory;

import app.feedgateway.GatewayMarketCalendar;
import app.feedgateway.GatewaySettings;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.consumer.OffsetAndTimestamp;
import org.apache.kafka.common.PartitionInfo;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.errors.WakeupException;
import org.apache.kafka.common.serialization.StringDeserializer;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * Session-history store for the strike-liquidity heatmap (HEATMAP-SESSION-HISTORY-BACKFILL v8 §2/§5):
 * ONE dedicated, GROUP-LESS Kafka consumer (manual assign, no group, no commits, read_committed
 * against the producer's EOS writes) reads the dashboard topic forward from the current trading
 * date's session open and folds every raw 1s frame exactly once into per-{@code (chain, tradeDate)}
 * 60s aggregates.
 *
 * <p>WHY epochs: every (re)seek — boot, consumer-exception recovery, post-eviction rebuild — starts
 * a fresh EPOCH with a fresh shadow store and a fresh readiness gate ({@code endOffsets} snapshot at
 * epoch start). Folds target only the shadow; the previously published store keeps serving frozen
 * (evicted chains excepted — those 503) until {@code position >= epochEndOffsets} on ALL partitions,
 * then the shadow atomically replaces the published store. One reader reads each offset once within
 * an epoch and the shadow starts empty, so no path can double-fold (AC6/AC17).
 *
 * <p>DEPLOYMENT INVARIANT: feed-gateway is a SINGLE replica; per-pod memory is authoritative.
 * Logged at boot; a multi-replica gateway reopens Gate-1.
 */
public final class LiquidityHistoryStore implements AutoCloseable {

    /** Store configuration (extracted so tests construct without env plumbing). */
    record Config(String topic, int expectedPartitions, int maxChains, long maxBytes,
                  long maxLagRecords, long catchupAbortMs, int pollMs) {

        static Config fromSettings(GatewaySettings settings) {
            // 4 partitions is the platform-wide Kafka partition policy the one-chain-one-partition
            // ordering contract is calibrated against (spec §2 topology guard).
            return new Config(settings.strikeLiquidityTopic(), 4,
                    settings.heatmapHistoryMaxChains(), settings.heatmapHistoryMaxBytes(),
                    settings.heatmapHistoryMaxLagRecords(), settings.heatmapHistoryCatchupAbortMs(),
                    settings.pollMs());
        }
    }

    /** Seam over {@link Consumer#offsetsForTimes} — MockConsumer does not implement it (tests inject). */
    interface TimeSeek {
        Map<TopicPartition, OffsetAndTimestamp> offsetsForTimes(Consumer<String, String> consumer,
                                                                Map<TopicPartition, Long> timestamps);
    }

    /** Aggregate identity (spec §2): the chain key {@code symbol|expiry} plus the trading date. */
    record AggKey(String chain, LocalDate tradeDate) {
    }

    /** The §3 RESPONSE PRECEDENCE outcome for one request. */
    public enum ServeStatus { TOPOLOGY_ALARM, UNAVAILABLE, LAGGING, OK }

    public record ServeDecision(ServeStatus status, int retryAfterSeconds, boolean epochRebuilding,
                                ChainSnapshot snapshot) {
    }

    private final Config config;
    private final GatewayMarketCalendar calendar;
    private final Supplier<Consumer<String, String>> consumerFactory;
    private final TimeSeek timeSeek;
    private final Supplier<Set<String>> wsSubscribedChains;
    private final Clock clock;
    private final ObjectMapper mapper = new ObjectMapper();
    private final boolean enabled;

    private final Object lock = new Object();
    // ---- guarded by lock ----
    private Map<AggKey, SessionAggregate> published;          // null until the first epoch swap
    private Map<AggKey, SessionAggregate> shadow;             // non-null only while an epoch catches up
    /** Keys whose published data is knowingly incomplete (evicted); they 503 until an epoch refolds them. */
    private final Set<AggKey> evictedKeys = new HashSet<>();
    private boolean publishedFrozen = true;                   // true whenever no consumer folds published
    private boolean rebuildFailed;
    // ---- end guarded by lock ----

    /** Chains with an in-flight request are never evicted (spec §2); marked by the controller. */
    private final Set<AggKey> inFlight = ConcurrentHashMap.newKeySet();
    private final Object rebuildSignal = new Object();
    private final AtomicBoolean rebuildRequested = new AtomicBoolean();
    private final AtomicBoolean running = new AtomicBoolean();
    private volatile boolean topologyAlarm;
    private volatile long lagRecords;
    private volatile int bootPartitionCount = -1;
    private volatile Thread readThread;
    private volatile Consumer<String, String> activeConsumer;

    // ---- §7 observability (plain counters/gauges, rendered into /metrics text) ----
    private final Map<Integer, AtomicLong> requestsByStatus = new ConcurrentHashMap<>();
    private final AtomicLong serveMsLast = new AtomicLong();
    private final AtomicLong catchupOk = new AtomicLong();
    private final AtomicLong catchupAborted = new AtomicLong();
    private final AtomicLong catchupFailed = new AtomicLong();
    private final AtomicLong catchupMsLast = new AtomicLong();
    private final AtomicLong bytesRead = new AtomicLong();
    private final AtomicLong evictionsDateRoll = new AtomicLong();
    private final AtomicLong evictionsByteCap = new AtomicLong();
    private final AtomicLong evictionsChainCap = new AtomicLong();
    private final AtomicLong lateFramesDropped = new AtomicLong();
    private final AtomicLong outOfSessionFramesDropped = new AtomicLong();
    private final AtomicLong responsesTruncated = new AtomicLong();
    private final AtomicLong epochSwaps = new AtomicLong();
    private final AtomicLong framesRejected = new AtomicLong();

    /** Production constructor: real KafkaConsumer factory, wall clock, real offsetsForTimes. */
    public LiquidityHistoryStore(GatewaySettings settings, GatewayMarketCalendar calendar,
                                 Supplier<Set<String>> wsSubscribedChains) {
        this(Config.fromSettings(settings), calendar, kafkaConsumerFactory(settings),
                Consumer::offsetsForTimes, wsSubscribedChains, Clock.systemUTC(),
                settings.heatmapHistoryEnabled() && settings.enabled());
    }

    /** Test seam: injectable consumer factory, time-seek and clock; always "enabled". */
    LiquidityHistoryStore(Config config, GatewayMarketCalendar calendar,
                          Supplier<Consumer<String, String>> consumerFactory, TimeSeek timeSeek,
                          Supplier<Set<String>> wsSubscribedChains, Clock clock) {
        this(config, calendar, consumerFactory, timeSeek, wsSubscribedChains, clock, true);
    }

    private LiquidityHistoryStore(Config config, GatewayMarketCalendar calendar,
                                  Supplier<Consumer<String, String>> consumerFactory, TimeSeek timeSeek,
                                  Supplier<Set<String>> wsSubscribedChains, Clock clock, boolean enabled) {
        this.config = config;
        this.calendar = calendar;
        this.consumerFactory = consumerFactory;
        this.timeSeek = timeSeek;
        this.wsSubscribedChains = wsSubscribedChains;
        this.clock = clock;
        this.enabled = enabled;
    }

    private static Supplier<Consumer<String, String>> kafkaConsumerFactory(GatewaySettings settings) {
        return () -> {
            Properties props = new Properties();
            props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, settings.bootstrapServers());
            props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
            props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
            // GROUP-LESS by design (spec §2): no group.id, no membership, no rebalance, no commits —
            // position exists only in-process; every epoch re-seeks by session time.
            props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
            // The producer is exactly-once; read_committed keeps aborted transactions out of the fold.
            props.put(ConsumerConfig.ISOLATION_LEVEL_CONFIG, "read_committed");
            props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, "2000");
            props.put(ConsumerConfig.FETCH_MAX_BYTES_CONFIG, Integer.toString(16 * 1024 * 1024));
            props.put(ConsumerConfig.MAX_PARTITION_FETCH_BYTES_CONFIG, Integer.toString(8 * 1024 * 1024));
            props.put(ConsumerConfig.CLIENT_ID_CONFIG, "options-edge-feed-gateway-liquidity-history");
            settings.applyKafkaSecurity(props);
            return new KafkaConsumer<>(props);
        };
    }

    public boolean enabled() {
        return enabled;
    }

    public GatewayMarketCalendar calendar() {
        return calendar;
    }

    public boolean topologyAlarm() {
        return topologyAlarm;
    }

    /** Starts the dedicated read thread (no-op when the feature or Kafka is disabled). */
    public void start() {
        if (!enabled) {
            System.out.println("LiquidityHistoryStore disabled (HEATMAP_HISTORY_ENABLED/KAFKA_ENABLED off) — "
                    + "/api/liquidity-history will answer 404");
            return;
        }
        if (!running.compareAndSet(false, true)) {
            return;
        }
        // Spec §2 deployment invariant, logged loudly: this per-pod store is only authoritative
        // because feed-gateway runs as a SINGLE replica. Multi-replica reopens Gate-1.
        System.out.println("LiquidityHistoryStore starting (SINGLE-REPLICA INVARIANT: per-pod session "
                + "aggregate is authoritative) topic=" + config.topic()
                + " maxChains=" + config.maxChains() + " maxBytes=" + config.maxBytes()
                + " maxLagRecords=" + config.maxLagRecords() + " catchupAbortMs=" + config.catchupAbortMs());
        Thread thread = new Thread(this::runLoop, "liquidity-history-consumer");
        thread.setDaemon(true);
        readThread = thread;
        thread.start();
    }

    @Override
    public void close() {
        running.set(false);
        Consumer<String, String> consumer = activeConsumer;
        if (consumer != null) {
            try {
                consumer.wakeup();
            } catch (RuntimeException ignored) {
                // best-effort wakeup
            }
        }
        synchronized (rebuildSignal) {
            rebuildSignal.notifyAll();
        }
        Thread thread = readThread;
        if (thread != null) {
            try {
                thread.join(3_000L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * Triggers a rebuild epoch (post-eviction / post-REBUILD_FAILED). Single-flighted: the flag is
     * consumed once by the read loop; concurrent requests coalesce into the same epoch.
     */
    void requestRebuild() {
        rebuildRequested.set(true);
        synchronized (rebuildSignal) {
            rebuildSignal.notifyAll();
        }
    }

    // ------------------------------------------------------------------------------- read loop

    private void runLoop() {
        long backoffMs = 1_000L;
        while (running.get()) {
            boolean cleanExit = false;
            Consumer<String, String> consumer = null;
            try {
                consumer = consumerFactory.get();
                activeConsumer = consumer;
                cleanExit = runEpoch(consumer);
            } catch (WakeupException e) {
                // close() — loop condition exits below
            } catch (RuntimeException e) {
                System.out.println("ERROR liquidity-history consumer epoch failed: " + e);
                noteEpochFailure();
            } finally {
                activeConsumer = null;
                if (consumer != null) {
                    try {
                        consumer.close();
                    } catch (RuntimeException ignored) {
                        // already failing; the next epoch gets a fresh consumer
                    }
                }
            }
            if (!running.get()) {
                return;
            }
            if (cleanExit) {
                backoffMs = 1_000L; // controlled epoch end (rebuild request) — no backoff
                continue;
            }
            // Exponential backoff 1s,2s,4s… cap 30s; an explicit rebuild request wakes it early
            // ("retry on next request trigger or next backoff cycle").
            waitForRebuildOrTimeout(backoffMs);
            backoffMs = Math.min(backoffMs * 2, 30_000L);
        }
    }

    private void waitForRebuildOrTimeout(long timeoutMs) {
        synchronized (rebuildSignal) {
            if (rebuildRequested.get() || !running.get()) {
                return;
            }
            try {
                rebuildSignal.wait(timeoutMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * One epoch: seek by session time, catch up into the shadow, swap, then fold live continuously.
     * Returns true on a CONTROLLED exit (shutdown or an explicit rebuild request → immediate new
     * epoch); false when the epoch must back off (catch-up abort, topology alarm). Consumer
     * exceptions propagate to {@link #runLoop()}.
     */
    private boolean runEpoch(Consumer<String, String> consumer) {
        rebuildRequested.set(false); // this epoch satisfies any pending rebuild request
        List<PartitionInfo> partitions = consumer.partitionsFor(config.topic());
        if (partitions == null || partitions.isEmpty()) {
            throw new IllegalStateException("no partition metadata for topic " + config.topic());
        }
        if (!checkTopology(partitions.size())) {
            return false;
        }
        List<TopicPartition> tps = new ArrayList<>(partitions.size());
        for (PartitionInfo p : partitions) {
            tps.add(new TopicPartition(p.topic(), p.partition()));
        }
        consumer.assign(tps);

        LocalDate tradingDate = calendar.currentTradingDate(clock.instant());
        Instant sessionOpen = calendar.sessionOpen(tradingDate); // non-null: currentTradingDate is a trading day
        Map<TopicPartition, Long> epochEnd = consumer.endOffsets(tps);
        Map<TopicPartition, Long> beginning = consumer.beginningOffsets(tps);
        Map<TopicPartition, Long> timestamps = new HashMap<>();
        for (TopicPartition tp : tps) {
            timestamps.put(tp, sessionOpen.toEpochMilli());
        }
        Map<TopicPartition, OffsetAndTimestamp> byTime = timeSeek.offsetsForTimes(consumer, timestamps);
        Set<Integer> retentionSuspect = new HashSet<>();
        for (TopicPartition tp : tps) {
            OffsetAndTimestamp target = byTime == null ? null : byTime.get(tp);
            if (target == null) {
                // No record at/after sessionOpen on this partition → no session data → start at the end.
                consumer.seek(tp, epochEnd.getOrDefault(tp, 0L));
            } else {
                consumer.seek(tp, target.offset());
                Long begin = beginning.get(tp);
                if (begin != null && begin > 0 && target.offset() <= begin) {
                    // The seek landed on a retention-truncated log start: earlier session data may
                    // already be deleted → chains on this partition report truncated=true (spec §3)
                    // when their earliest folded bucket starts after the open.
                    retentionSuspect.add(tp.partition());
                }
            }
        }

        long catchupStartMs = clock.millis();
        beginEpochState();
        boolean caughtUp = false;
        long lastLagCheckMs = 0L;
        long lastTopologyCheckMs = clock.millis();
        LocalDate lastRollCheckDate = tradingDate;

        while (running.get()) {
            ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(config.pollMs()));
            long nowMs = clock.millis();
            if (!records.isEmpty()) {
                synchronized (lock) {
                    Map<AggKey, SessionAggregate> target = caughtUp ? published : shadow;
                    for (ConsumerRecord<String, String> record : records) {
                        if (record.value() != null) {
                            bytesRead.addAndGet(record.value().length());
                        }
                        foldRecordLocked(target, record, retentionSuspect);
                    }
                    enforceCapsLocked(target);
                }
            }
            if (!caughtUp) {
                if (positionsAtOrPast(consumer, tps, epochEnd)) {
                    completeEpochState();
                    caughtUp = true;
                    epochSwaps.incrementAndGet();
                    catchupOk.incrementAndGet();
                    catchupMsLast.set(nowMs - catchupStartMs);
                    lagRecords = 0L;
                } else if (nowMs - catchupStartMs >= config.catchupAbortMs()) {
                    abortEpochState();
                    catchupAborted.incrementAndGet();
                    System.out.println("ERROR liquidity-history REBUILD_FAILED: catch-up exceeded "
                            + config.catchupAbortMs() + "ms — serving 503 until a rebuild succeeds");
                    return false;
                } else if (nowMs - lastLagCheckMs >= 10_000L) {
                    // During catch-up the gauge feeds the Retry-After estimate only — the lag GUARD
                    // is a steady-state gate (spec §2 "LAG GUARD (steady state)").
                    refreshLag(consumer, tps);
                    lastLagCheckMs = nowMs;
                }
                continue;
            }
            // ---- steady state ----
            if (rebuildRequested.compareAndSet(true, false)) {
                synchronized (lock) {
                    publishedFrozen = true; // the new epoch serves the frozen published until it swaps
                }
                return true;
            }
            if (nowMs - lastLagCheckMs >= 10_000L) {
                refreshLag(consumer, tps);
                lastLagCheckMs = nowMs;
            }
            if (nowMs - lastTopologyCheckMs >= 30_000L) {
                List<PartitionInfo> current = consumer.partitionsFor(config.topic());
                if (current != null && !current.isEmpty() && !checkTopology(current.size())) {
                    synchronized (lock) {
                        publishedFrozen = true;
                    }
                    return false;
                }
                lastTopologyCheckMs = nowMs;
            }
            LocalDate currentTradingDate = calendar.currentTradingDate(clock.instant());
            if (!currentTradingDate.equals(lastRollCheckDate)) {
                lastRollCheckDate = currentTradingDate;
                synchronized (lock) {
                    dateRollEvictLocked(published, currentTradingDate);
                }
            }
        }
        return true;
    }

    /** True while the partition count matches the boot snapshot; a change raises the sticky alarm. */
    private boolean checkTopology(int count) {
        if (bootPartitionCount < 0) {
            bootPartitionCount = count;
            if (count != config.expectedPartitions()) {
                System.out.println("WARN liquidity-history topic " + config.topic() + " has " + count
                        + " partitions (platform policy expects " + config.expectedPartitions() + ")");
            }
            return true;
        }
        if (count != bootPartitionCount) {
            // Fail closed and LOUD (spec §2): a partition-count change reshuffles key hashes and
            // silently breaks the one-chain-one-partition ordering contract. All requests 503 until
            // the topology matches the boot snapshot again (operator action).
            topologyAlarm = true;
            System.out.println("ERROR liquidity-history PARTITION TOPOLOGY CHANGED: " + bootPartitionCount
                    + " -> " + count + " — all /api/liquidity-history requests answer 503");
            return false;
        }
        if (topologyAlarm) {
            topologyAlarm = false;
            System.out.println("liquidity-history partition topology restored to " + count);
        }
        return true;
    }

    private static boolean positionsAtOrPast(Consumer<String, String> consumer,
                                             Collection<TopicPartition> tps, Map<TopicPartition, Long> end) {
        for (TopicPartition tp : tps) {
            if (consumer.position(tp) < end.getOrDefault(tp, 0L)) {
                return false;
            }
        }
        return true;
    }

    private void refreshLag(Consumer<String, String> consumer, Collection<TopicPartition> tps) {
        Map<TopicPartition, Long> end = consumer.endOffsets(new ArrayList<>(tps));
        long lag = 0L;
        for (TopicPartition tp : tps) {
            lag += Math.max(0L, end.getOrDefault(tp, 0L) - consumer.position(tp));
        }
        lagRecords = lag;
    }

    // -------------------------------------------------------------- epoch state (also test seams)

    /** New epoch: fresh shadow, published (if any) keeps serving frozen. */
    void beginEpochState() {
        synchronized (lock) {
            shadow = new HashMap<>();
            publishedFrozen = true;
        }
    }

    /** Catch-up complete: the shadow ATOMICALLY replaces the published store (spec §2). */
    void completeEpochState() {
        synchronized (lock) {
            published = shadow;
            shadow = null;
            // Every pre-swap evicted key was refolded from sessionOpen by this epoch; only keys the
            // SHADOW itself evicted (cap breach during catch-up) remain knowingly incomplete.
            evictedKeys.clear();
            evictedKeys.addAll(shadowEvictedMarks);
            shadowEvictedMarks.clear();
            publishedFrozen = false;
            rebuildFailed = false;
            dateRollEvictLocked(published, calendar.currentTradingDate(clock.instant()));
            enforceCapsLocked(published);
        }
    }

    /** Catch-up aborted (REBUILD_FAILED): drop the shadow; published (if any) stays frozen. */
    void abortEpochState() {
        synchronized (lock) {
            shadow = null;
            shadowEvictedMarks.clear();
            rebuildFailed = true;
        }
    }

    private void noteEpochFailure() {
        synchronized (lock) {
            if (shadow != null) {
                catchupFailed.incrementAndGet();
                shadow = null;
                shadowEvictedMarks.clear();
            }
            publishedFrozen = true;
        }
    }

    /** Keys evicted OUT OF THE SHADOW during catch-up (cap breach); they stay 503 after the swap. */
    private final Set<AggKey> shadowEvictedMarks = new HashSet<>();

    // ------------------------------------------------------------------------------------- fold

    /** Test seam: folds one record into the current epoch target (shadow if catching up, else published). */
    void foldForTest(String value, int partition) {
        synchronized (lock) {
            Map<AggKey, SessionAggregate> target = shadow != null ? shadow : published;
            if (target == null) {
                throw new IllegalStateException("no fold target — call beginEpochState() first");
            }
            foldRecordLocked(target,
                    new ConsumerRecord<>(config.topic(), partition, 0L, null, value), Set.of());
            enforceCapsLocked(target);
        }
    }

    private void foldRecordLocked(Map<AggKey, SessionAggregate> target, ConsumerRecord<String, String> record,
                                  Set<Integer> retentionSuspect) {
        HistoryFrame frame = HistoryFrame.parse(record.value(), mapper);
        if (frame == null) {
            framesRejected.incrementAndGet();
            return;
        }
        LocalDate frameTradeDate = tradeDateOf(frame.bucketStartMs());
        if (frameTradeDate == null) {
            outOfSessionFramesDropped.incrementAndGet();
            return;
        }
        LocalDate currentTradingDate = calendar.currentTradingDate(clock.instant());
        if (frameTradeDate.isBefore(previousTradingDay(currentTradingDate))) {
            // Older than the retained window (today + previous trading day): a late frame for a
            // dropped aggregate can never resurrect it — 24h retention made it unbuildable anyway.
            lateFramesDropped.incrementAndGet();
            return;
        }
        AggKey key = new AggKey(frame.symbol() + "|" + frame.expiry(), frameTradeDate);
        if (target == published && evictedKeys.contains(key)) {
            // A post-eviction partial refold would silently serve an incomplete session; the chain
            // stays 503 until a full epoch refolds it from sessionOpen (spec §2 cache-miss semantics).
            return;
        }
        SessionAggregate aggregate = target.get(key);
        if (aggregate == null) {
            aggregate = new SessionAggregate(frame.symbol(), frame.expiry(), frameTradeDate,
                    retentionSuspect.contains(record.partition()));
            target.put(key, aggregate);
        }
        aggregate.fold(frame, clock.millis());
    }

    /**
     * §4: the trading day whose session window contains the instant, else null (dropped+counted).
     * The window is the CLOSED interval [open, close] exactly as written in the spec; sessions never
     * span midnight ET, so the instant's ET date is the only candidate.
     */
    LocalDate tradeDateOf(long epochMs) {
        Instant instant = Instant.ofEpochMilli(epochMs);
        LocalDate day = instant.atZone(calendar.zone()).toLocalDate();
        Instant open = calendar.sessionOpen(day);
        if (open == null) {
            return null;
        }
        Instant close = calendar.sessionClose(day);
        return !instant.isBefore(open) && !instant.isAfter(close) ? day : null;
    }

    private LocalDate previousTradingDay(LocalDate tradingDate) {
        LocalDate day = tradingDate.minusDays(1);
        while (!calendar.isTradingDay(day)) {
            day = day.minusDays(1);
        }
        return day;
    }

    // -------------------------------------------------------------------------- memory bounds §2

    private void dateRollEvictLocked(Map<AggKey, SessionAggregate> target, LocalDate currentTradingDate) {
        if (target == null) {
            return;
        }
        LocalDate floor = previousTradingDay(currentTradingDate);
        Iterator<Map.Entry<AggKey, SessionAggregate>> it = target.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<AggKey, SessionAggregate> entry = it.next();
            if (entry.getKey().tradeDate().isBefore(floor)) {
                it.remove();
                evictionsDateRoll.incrementAndGet();
                evictedKeys.remove(entry.getKey()); // aged out entirely — no rebuild claim remains
            }
        }
    }

    private void enforceCapsLocked(Map<AggKey, SessionAggregate> target) {
        if (target == null) {
            return;
        }
        while (distinctChains(target) > config.maxChains()) {
            if (!evictVictimLocked(target, evictionsChainCap)) {
                return;
            }
        }
        while (totalBytes(target) > config.maxBytes()) {
            if (!evictVictimLocked(target, evictionsByteCap)) {
                return;
            }
        }
    }

    private static int distinctChains(Map<AggKey, SessionAggregate> target) {
        Set<String> chains = new HashSet<>();
        for (AggKey key : target.keySet()) {
            chains.add(key.chain());
        }
        return chains.size();
    }

    private static long totalBytes(Map<AggKey, SessionAggregate> target) {
        long total = 0L;
        for (SessionAggregate aggregate : target.values()) {
            total += aggregate.approxBytes();
        }
        return total;
    }

    /**
     * Deterministic victim selection (spec §2): chains with no active WS subscriber first, then the
     * oldest last-fold time; NEVER a chain with an in-flight request; ties break on the key text so
     * two identical stores evict identically. Returns false when every candidate is in flight.
     */
    private boolean evictVictimLocked(Map<AggKey, SessionAggregate> target, AtomicLong reasonCounter) {
        Set<String> subscribed;
        try {
            Set<String> fromHook = wsSubscribedChains.get();
            subscribed = fromHook == null ? Set.of() : fromHook;
        } catch (RuntimeException e) {
            subscribed = Set.of(); // the hook must never block eviction
        }
        AggKey victim = null;
        SessionAggregate victimAggregate = null;
        boolean victimSubscribed = true;
        for (Map.Entry<AggKey, SessionAggregate> entry : target.entrySet()) {
            AggKey key = entry.getKey();
            if (inFlight.contains(key)) {
                continue;
            }
            boolean entrySubscribed = subscribed.contains(key.chain());
            SessionAggregate aggregate = entry.getValue();
            boolean better;
            if (victim == null) {
                better = true;
            } else if (entrySubscribed != victimSubscribed) {
                better = !entrySubscribed; // unsubscribed evicts before subscribed
            } else if (aggregate.lastFoldAtMs() != victimAggregate.lastFoldAtMs()) {
                better = aggregate.lastFoldAtMs() < victimAggregate.lastFoldAtMs();
            } else {
                better = compareKeys(key, victim) < 0;
            }
            if (better) {
                victim = key;
                victimAggregate = aggregate;
                victimSubscribed = entrySubscribed;
            }
        }
        if (victim == null) {
            return false;
        }
        target.remove(victim);
        reasonCounter.incrementAndGet();
        // Eviction is a cache miss, not data loss: the chain 503s + transparently rebuilds on the
        // next request. Only within-retention keys make that claim. A shadow-side eviction marks
        // the SHADOW set only (it becomes the evicted set at swap) — it must not 503 a chain the
        // frozen published store is still serving completely.
        if (!victim.tradeDate().isBefore(previousTradingDay(calendar.currentTradingDate(clock.instant())))) {
            if (target == shadow) {
                shadowEvictedMarks.add(victim);
            } else {
                evictedKeys.add(victim);
            }
        }
        return true;
    }

    private static int compareKeys(AggKey a, AggKey b) {
        int byChain = a.chain().compareTo(b.chain());
        return byChain != 0 ? byChain : a.tradeDate().compareTo(b.tradeDate());
    }

    // ------------------------------------------------------------------------------------ serve

    /** Marks a (chain, tradeDate) in-flight so eviction can never race a serve (spec §2). */
    public void beginServe(String symbol, String expiry, LocalDate tradeDate) {
        inFlight.add(new AggKey(symbol + "|" + expiry, tradeDate));
    }

    public void endServe(String symbol, String expiry, LocalDate tradeDate) {
        inFlight.remove(new AggKey(symbol + "|" + expiry, tradeDate));
    }

    /**
     * Resolves the §3 RESPONSE PRECEDENCE ladder for one (chain, tradeDate) and snapshots the
     * aggregate atomically. Steps: (1) topology alarm → 503 all chains; (2) no published aggregate
     * (initial catch-up, evicted, REBUILD_FAILED) → 503 + single-flighted rebuild trigger;
     * (3) steady-state lag breach → 503; (4) published exists while an epoch rebuild runs → 200 from
     * the frozen store + rebuilding header; (5) 200. A chain genuinely absent from a caught-up,
     * actively-folding store is NOT step 2 — every offset was folded, so the honest answer is an
     * empty 200 (no frames exist for it), never a rebuild loop.
     */
    public ServeDecision query(String symbol, String expiry, LocalDate tradeDate) {
        if (topologyAlarm) {
            return new ServeDecision(ServeStatus.TOPOLOGY_ALARM, 60, false, null);
        }
        String chain = symbol + "|" + expiry;
        AggKey key = new AggKey(chain, tradeDate);
        LocalDate currentTradingDate = calendar.currentTradingDate(clock.instant());
        synchronized (lock) {
            if (published == null) {
                if (rebuildFailed) {
                    requestRebuild(); // "retry on next request trigger or next backoff cycle" (spec §2)
                }
                return unavailableLocked();
            }
            if (evictedKeys.contains(key)) {
                requestRebuildUnlessCatchingUpLocked();
                return unavailableLocked();
            }
            SessionAggregate aggregate = published.get(key);
            boolean frozen = publishedFrozen;
            if (aggregate == null) {
                if (tradeDate.equals(currentTradingDate)) {
                    if (frozen || rebuildFailed) {
                        // The frozen store predates this chain's possible first frame; completeness
                        // cannot be claimed, so this is precedence step 2, not an empty 200.
                        requestRebuildUnlessCatchingUpLocked();
                        return unavailableLocked();
                    }
                    if (lagRecords > config.maxLagRecords()) {
                        return new ServeDecision(ServeStatus.LAGGING, retryAfterSeconds(), false, null);
                    }
                    return new ServeDecision(ServeStatus.OK, 0, false,
                            ChainSnapshot.empty(symbol, expiry, tradeDate, false));
                }
                // A retained previous trading day this process never folded (epochs seek from TODAY's
                // open) is unbuildable — served honestly as empty + truncated, never a rebuild loop.
                return new ServeDecision(ServeStatus.OK, 0, frozen,
                        ChainSnapshot.empty(symbol, expiry, tradeDate, true));
            }
            if (!frozen && lagRecords > config.maxLagRecords()) {
                return new ServeDecision(ServeStatus.LAGGING, retryAfterSeconds(), false, null);
            }
            Instant open = calendar.sessionOpen(tradeDate);
            long sessionOpenMs = open == null ? 0L : open.toEpochMilli();
            return new ServeDecision(ServeStatus.OK, 0, frozen, aggregate.snapshot(sessionOpenMs));
        }
    }

    private ServeDecision unavailableLocked() {
        return new ServeDecision(ServeStatus.UNAVAILABLE, rebuildFailed ? 60 : retryAfterSeconds(), false, null);
    }

    /**
     * Single-flight coalescing: while an epoch is ACTIVELY catching up (shadow present) it will
     * already refold the requested chain, so a further trigger would only queue a redundant
     * back-to-back epoch. Only an idle/failed loop needs the wake-up.
     */
    private void requestRebuildUnlessCatchingUpLocked() {
        if (shadow == null) {
            requestRebuild();
        }
    }

    /**
     * Bounded remaining-catch-up estimate clamped [5, 60] (spec §3): the lag gauge over an assumed
     * worst-case fold rate of ~1000 records/s (the catch-up budget math in §2 supports far more).
     */
    private int retryAfterSeconds() {
        long estimate = lagRecords / 1_000L;
        return (int) Math.max(5L, Math.min(60L, estimate));
    }

    // ---------------------------------------------------------------------------------- metrics

    public void recordRequest(int status, long serveMs) {
        requestsByStatus.computeIfAbsent(status, s -> new AtomicLong()).incrementAndGet();
        serveMsLast.set(serveMs);
    }

    public void recordTruncatedResponse() {
        responsesTruncated.incrementAndGet();
    }

    long lagRecords() {
        return lagRecords;
    }

    /** §7 metric set, Prometheus text format matching the rest of the gateway's /metrics. */
    public String metricsText() {
        StringBuilder b = new StringBuilder(2_048);
        b.append("# HELP liquidity_history_requests_total /api/liquidity-history requests by HTTP status.\n")
                .append("# TYPE liquidity_history_requests_total counter\n");
        requestsByStatus.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(e -> b.append("liquidity_history_requests_total{status=\"").append(e.getKey())
                        .append("\"} ").append(e.getValue().get()).append('\n'));
        long chains;
        long bytes;
        synchronized (lock) {
            chains = published == null ? 0 : distinctChains(published);
            bytes = published == null ? 0 : totalBytes(published);
        }
        b.append("# HELP liquidity_history_serve_ms Duration of the most recent history serve.\n")
                .append("# TYPE liquidity_history_serve_ms gauge\n")
                .append("liquidity_history_serve_ms ").append(serveMsLast.get()).append('\n')
                .append("# HELP liquidity_history_catchup_total Epoch catch-up outcomes.\n")
                .append("# TYPE liquidity_history_catchup_total counter\n")
                .append("liquidity_history_catchup_total{result=\"ok\"} ").append(catchupOk.get()).append('\n')
                .append("liquidity_history_catchup_total{result=\"aborted\"} ").append(catchupAborted.get()).append('\n')
                .append("liquidity_history_catchup_total{result=\"failed\"} ").append(catchupFailed.get()).append('\n')
                .append("# HELP liquidity_history_catchup_ms Duration of the most recent successful catch-up.\n")
                .append("# TYPE liquidity_history_catchup_ms gauge\n")
                .append("liquidity_history_catchup_ms ").append(catchupMsLast.get()).append('\n')
                .append("# HELP liquidity_history_bytes_read Raw record bytes read by the history consumer.\n")
                .append("# TYPE liquidity_history_bytes_read counter\n")
                .append("liquidity_history_bytes_read ").append(bytesRead.get()).append('\n')
                .append("# HELP liquidity_history_session_aggregate_chains Distinct chains in the published store.\n")
                .append("# TYPE liquidity_history_session_aggregate_chains gauge\n")
                .append("liquidity_history_session_aggregate_chains ").append(chains).append('\n')
                .append("# HELP liquidity_history_session_aggregate_bytes Approximate bytes held by the published store.\n")
                .append("# TYPE liquidity_history_session_aggregate_bytes gauge\n")
                .append("liquidity_history_session_aggregate_bytes ").append(bytes).append('\n')
                .append("# HELP liquidity_history_evictions_total Aggregate evictions by reason.\n")
                .append("# TYPE liquidity_history_evictions_total counter\n")
                .append("liquidity_history_evictions_total{reason=\"date_roll\"} ").append(evictionsDateRoll.get()).append('\n')
                .append("liquidity_history_evictions_total{reason=\"byte_cap\"} ").append(evictionsByteCap.get()).append('\n')
                .append("liquidity_history_evictions_total{reason=\"chain_cap\"} ").append(evictionsChainCap.get()).append('\n')
                .append("# HELP liquidity_history_late_frames_dropped_total Frames older than the retained trade dates.\n")
                .append("# TYPE liquidity_history_late_frames_dropped_total counter\n")
                .append("liquidity_history_late_frames_dropped_total ").append(lateFramesDropped.get()).append('\n')
                .append("# HELP liquidity_history_out_of_session_frames_dropped_total Frames whose bucketStart is in no session window.\n")
                .append("# TYPE liquidity_history_out_of_session_frames_dropped_total counter\n")
                .append("liquidity_history_out_of_session_frames_dropped_total ").append(outOfSessionFramesDropped.get()).append('\n')
                .append("# HELP liquidity_history_responses_truncated_total Responses served with truncated=true.\n")
                .append("# TYPE liquidity_history_responses_truncated_total counter\n")
                .append("liquidity_history_responses_truncated_total ").append(responsesTruncated.get()).append('\n')
                .append("# HELP liquidity_history_consumer_lag_records History consumer total record lag.\n")
                .append("# TYPE liquidity_history_consumer_lag_records gauge\n")
                .append("liquidity_history_consumer_lag_records ").append(lagRecords).append('\n')
                .append("# HELP liquidity_history_epoch_swaps_total Shadow→published epoch swaps.\n")
                .append("# TYPE liquidity_history_epoch_swaps_total counter\n")
                .append("liquidity_history_epoch_swaps_total ").append(epochSwaps.get()).append('\n')
                .append("# HELP liquidity_history_partition_topology_changed 1 while the partition-count alarm is active.\n")
                .append("# TYPE liquidity_history_partition_topology_changed gauge\n")
                .append("liquidity_history_partition_topology_changed ").append(topologyAlarm ? 1 : 0).append('\n')
                .append("# HELP liquidity_history_frames_rejected_total Malformed frames rejected by strict parsing.\n")
                .append("# TYPE liquidity_history_frames_rejected_total counter\n")
                .append("liquidity_history_frames_rejected_total ").append(framesRejected.get()).append('\n');
        return b.toString();
    }

    // ----------------------------------------------------------------------------- test hooks

    long framesRejectedCount() {
        return framesRejected.get();
    }

    long lateFramesDroppedCount() {
        return lateFramesDropped.get();
    }

    long outOfSessionDroppedCount() {
        return outOfSessionFramesDropped.get();
    }

    long evictionsCount(String reason) {
        return switch (reason) {
            case "date_roll" -> evictionsDateRoll.get();
            case "byte_cap" -> evictionsByteCap.get();
            case "chain_cap" -> evictionsChainCap.get();
            default -> throw new IllegalArgumentException(reason);
        };
    }

    void setLagRecordsForTest(long lag) {
        lagRecords = lag;
    }

    void setTopologyAlarmForTest(boolean alarm) {
        topologyAlarm = alarm;
    }

    boolean publishedContains(String chain, LocalDate tradeDate) {
        synchronized (lock) {
            return published != null && published.containsKey(new AggKey(chain, tradeDate));
        }
    }

    boolean isEvicted(String chain, LocalDate tradeDate) {
        synchronized (lock) {
            return evictedKeys.contains(new AggKey(chain, tradeDate));
        }
    }

    boolean rebuildPending() {
        return rebuildRequested.get();
    }

    void dateRollForTest() {
        synchronized (lock) {
            dateRollEvictLocked(shadow != null ? shadow : published,
                    calendar.currentTradingDate(clock.instant()));
        }
    }

    void runLoopForTest() {
        runLoop();
    }

    void markRunningForTest() {
        running.set(true);
    }

    void stopRunningForTest() {
        running.set(false);
    }
}

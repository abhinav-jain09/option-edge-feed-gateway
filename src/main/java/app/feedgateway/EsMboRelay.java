package app.feedgateway;

import com.optionsedge.contracts.esmbo.EsMboSchemas;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;
import org.apache.avro.generic.GenericRecord;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.consumer.OffsetAndTimestamp;
import org.apache.kafka.common.PartitionInfo;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.errors.WakeupException;
import org.apache.kafka.common.serialization.Deserializer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * The ES MBO relay (es-liquidity-engine-service), modelled on {@link EsLiquidityWallRelay}: one read-only tail of {@code es.mbo.level-flow},
 * {@code es.mbo.health} and {@code es.mbo.liquidity-events} per gateway replica (read_committed, partition 0 each, no consumer group, nothing committed),
 * fanned out as the global events {@link #FRAME_EVENT}, {@link #EVENT_EVENT} and {@link #STATUS_EVENT} and replayed to a connecting socket as ONE
 * {@link #HISTORY_EVENT}. The gateway reshapes and forwards: no signal, no threshold.
 * <p>
 * The boundary, in order, per record: the value's Confluent frame must carry the topic's FIXED schema id (3502 level-flow, 3509 health (its prior version's id too),
 * 3503 events; {@link EsMboSchemas#assertFramedAs}) BEFORE any decoding, a mismatch being refused and counted; then the bytes are decoded by the registry
 * through {@link GatewayAvroDeserializer} (tolerant: unknown id / corrupt body is counted, not relayed); then {@link EsMboJson}'s record checks.
 * <ul>
 * <li>ONE contract at a time: the contract of the newest health record. Rows and events of another contract are dropped and counted
 * ({@code gateway_es_mbo_other_contract_total}); a change of contract resets the folding state completely and is shown as {@code contract} in the status.</li>
 * <li>Level-flow rows are folded by bucket end in a bounded buffer. The health record of a bucket CLOSES it: one {@code es-mbo-frame} is emitted and every
 * buffered bucket at or before it is dropped. The two topics are separate partitions, so a health record is held (bounded) until level-flow has reached its
 * bucket (or {@link #HEALTH_HOLD_MS} passes: a quiet bucket has no rows) so a catch-up read never closes a bucket before its rows are read.</li>
 * <li>A frame is broadcast live only while its bucket end is within {@link GatewaySettings#esMboFreshMs()}; an older one (the 15-minute catch-up) is kept for
 * the connect replay only.</li>
 * <li>Events: the ring keeps the last {@link #EVENT_RING} by eventId, latest revision, in arrival order; a record is broadcast live only within
 * {@link GatewaySettings#esMboEventLiveMs()} of its bucket end.</li>
 * </ul>
 * Reads start at offsetsForTimes(now - 15 min) for level-flow and health, now - 6 h for events. After a consumer failure each partition resumes at its next
 * unread offset. A missing topic is looked for again every {@link #TOPIC_RETRY_MS} and never stops the other lanes. Disabled
 * ({@code ES_MBO_RELAY_ENABLED=false}, the default): no thread, no consumer, nothing cached.
 */
@Component
final class EsMboRelay {
    static final String FRAME_EVENT = "es-mbo-frame";
    static final String EVENT_EVENT = "es-mbo-event";
    static final String STATUS_EVENT = "es-mbo-status";
    static final String HISTORY_EVENT = "es-mbo-history";
    static final int FRAME_RING = 900;
    static final int EVENT_RING = 300;
    static final long TOPIC_RETRY_MS = 30_000L;
    static final long STATUS_PERIOD_MS = 5_000L;
    static final long FRAMES_LOOKBACK_MS = 15 * 60_000L;
    static final long EVENTS_LOOKBACK_MS = 6 * 3_600_000L;
    /** A health record whose bucket level-flow has not reached waits this long (wall clock) AND only while the level-flow lane is caught up and quiet; never while it is behind. */
    static final long HEALTH_HOLD_MS = 1_000L;
    /** Open level-flow buckets kept, rows kept per bucket, health records held: an unmatched bucket never leaks memory. */
    static final int MAX_OPEN_BUCKETS = 120;
    static final int MAX_ROWS_PER_BUCKET = 8_192;
    static final int MAX_PENDING_HEALTH = 2_000;

    private record Frame(String base, long bucketEnd, int bytes) {
    }

    private record Event(String base, int bytes, String contract, long bucketEnd) {
    }

    private record PendingHealth(GenericRecord record, long arrivedAt) {
    }

    private final GatewaySettings settings;
    private final int historyMaxBytes;
    private final BiConsumer<String, String> sink;
    private final AtomicBoolean running = new AtomicBoolean();
    private final AtomicLong levelFlowRefused = new AtomicLong(), healthRefused = new AtomicLong(), eventRefused = new AtomicLong();
    private final AtomicLong undecodable = new AtomicLong(), drops = new AtomicLong(), consumerFailures = new AtomicLong();
    private final AtomicLong otherContract = new AtomicLong(), eventsOutsideWindow = new AtomicLong(), eventsFuture = new AtomicLong(), ringEvicted = new AtomicLong();
    private final AtomicLong duplicateEvents = new AtomicLong();
    private final AtomicLong framesSeen = new AtomicLong(), eventsSeen = new AtomicLong(), lateRows = new AtomicLong(), rowsDropped = new AtomicLong();
    /**
     * ONE contract at a time: the contract of the newest health record (the engine publishes one contract; a roll is a contract-table change). Before the
     * first health record the contract of the first level-flow row is provisional. Rows and events of any other contract are dropped and counted; a change of
     * the current contract resets the folding state completely.
     */
    private String current, provisional;
    private final TreeMap<Long, List<EsMboJson.Row>> open = new TreeMap<>();
    private final TreeMap<Long, PendingHealth> pending = new TreeMap<>();
    private long closedThrough;
    /** eventId -> {highest revision accepted, its bucketEnd}: separate from the replay ring, pruned by age to the events read window, so evicting from the ring never forgets a revision. */
    private final Map<String, long[]> ledger = new HashMap<>();
    private long lastLedgerPrune;
    static final long LEDGER_PRUNE_EVERY_MS = 60_000L;
    /** Wall-clock instant the level-flow lane was last seen caught up (position at its end offset) continuously, or null while it is behind. */
    private Long levelFlowCaughtUpSince;
    private boolean levelFlowAbsent;
    private final ArrayDeque<Frame> frames = new ArrayDeque<>();
    private final LinkedHashMap<String, Event> events = new LinkedHashMap<>();
    private long levelFlowWatermark, lastFrameBucketEnd, lastEventBucketEnd;
    private boolean anyFrame, anyEvent;
    private String lastEpochStatus;
    private volatile Deserializer<Object> decoder;
    private volatile KafkaConsumer<String, byte[]> consumer;
    private Thread thread;
    private ScheduledExecutorService heartbeat;
    private final Map<TopicPartition, Long> resumeAt = new HashMap<>();

    @org.springframework.beans.factory.annotation.Autowired
    EsMboRelay(GatewaySettings settings, ObjectProvider<FeedGatewayService> gateway) {
        this(settings, (event, json) -> gateway.getObject().streamEsMbo(event, json), null);
    }

    /** Test seam: the frames go to {@code sink} instead of the sockets, the bytes are decoded by {@code decoder}. */
    EsMboRelay(GatewaySettings settings, BiConsumer<String, String> sink, Deserializer<Object> decoder) {
        this(settings, sink, decoder, settings.esMboHistoryMaxBytes());
    }

    /** Test seam with an explicit history byte bound. */
    EsMboRelay(GatewaySettings settings, BiConsumer<String, String> sink, Deserializer<Object> decoder, int historyMaxBytes) {
        this.historyMaxBytes = historyMaxBytes;
        this.settings = settings;
        this.sink = sink;
        this.decoder = decoder;
    }

    @PostConstruct
    void start() {
        if (!settings.esMboRelayEnabled()) {
            return;
        }
        running.set(true);
        thread = new Thread(this::run, "es-mbo-relay");
        thread.setDaemon(true);
        thread.start();
        heartbeat = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "es-mbo-relay-status");
            t.setDaemon(true);
            return t;
        });
        heartbeat.scheduleAtFixedRate(() -> {
            try {
                tick(System.currentTimeMillis());
                sink.accept(STATUS_EVENT, statusJson());
            } catch (RuntimeException ignored) {
                // a status beat never stops the next
            }
        }, STATUS_PERIOD_MS, STATUS_PERIOD_MS, TimeUnit.MILLISECONDS);
    }

    /** True while the consumer thread exists: false for a disabled relay, which consumes nothing. */
    boolean consuming() {
        return running.get();
    }

    private void run() {
        while (running.get()) {
            String[] topics = {settings.esMboLevelFlowTopic(), settings.esMboHealthTopic(), settings.esMboEventTopic()};
            Properties p = new Properties();
            p.put("bootstrap.servers", settings.bootstrapServers());
            p.put("client.id", "es-mbo-relay");
            p.put("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
            p.put("value.deserializer", "org.apache.kafka.common.serialization.ByteArrayDeserializer");
            p.put("enable.auto.commit", false);
            p.put("isolation.level", "read_committed");
            p.put("max.poll.records", 500);
            p.put("fetch.max.bytes", 4 * 1048576);
            p.put("max.partition.fetch.bytes", 2 * 1048576);
            p.put("request.timeout.ms", 10000);
            p.put("default.api.timeout.ms", 10000);
            settings.applyKafkaSecurity(p);
            Map<String, Object> decoderConfig = new HashMap<>();
            decoderConfig.put("schema.registry.url", settings.schemaRegistryUrl());
            decoderConfig.put("specific.avro.reader", false);
            decoderConfig.put("http.connect.timeout.ms", 1000);
            decoderConfig.put("http.read.timeout.ms", 1000);
            decoderConfig.put("max.schemas.per.subject", 64);
            decoderConfig.put(GatewayAvroDeserializer.SPX_PRICE_TOPIC, settings.underlyingSpxPriceTopic());
            decoderConfig.put(GatewayAvroDeserializer.TOLERANT_TOPICS, String.join(",", topics));
            try (var c = new KafkaConsumer<String, byte[]>(p); GatewayAvroDeserializer d = new GatewayAvroDeserializer()) {
                d.configure(decoderConfig, false);
                decoder = d;
                consumer = c;
                List<TopicPartition> partitions = new ArrayList<>();
                boolean missing = false;
                for (String topic : topics) {
                    TopicPartition tp = optionalPartitionZero(c, topic);   // absent until the engine creates it; never stops the other lanes
                    if (tp == null) {
                        missing = true;
                    } else {
                        partitions.add(tp);
                    }
                }
                long nextLook = System.currentTimeMillis() + TOPIC_RETRY_MS;
                TopicPartition flowPartition = partitions.stream().filter(tp -> tp.topic().equals(topics[0])).findFirst().orElse(null);
                levelFlowLaneAbsent(flowPartition == null);
                levelFlowCaughtUp(false, System.currentTimeMillis());
                c.assign(partitions);
                long now = System.currentTimeMillis();
                for (TopicPartition tp : partitions) {
                    Long resume = resumeAt.get(tp);
                    if (resume != null) {
                        c.seek(tp, resume);
                    } else {
                        seekFrom(c, tp, now - (tp.topic().equals(topics[2]) ? EVENTS_LOOKBACK_MS : FRAMES_LOOKBACK_MS));
                    }
                }
                while (running.get()) {
                    if (missing && System.currentTimeMillis() >= nextLook) {
                        break;   // look again: the loop rebuilds the consumer, resuming each lane at its next unread offset
                    }
                    for (var record : c.poll(Duration.ofMillis(250))) {
                        accept(record.topic(), record.key(), record.value(), System.currentTimeMillis());
                        resumeAt.put(new TopicPartition(record.topic(), record.partition()), record.offset() + 1);
                    }
                    // the deterministic barrier: is the level-flow lane at its end offset NOW (after this poll's records were accepted)? A MISSING lane is NOT caught up: rows may still arrive.
                    boolean caughtUp = flowPartition != null && laneCaughtUp(true, c.position(flowPartition), c.endOffsets(List.of(flowPartition)).get(flowPartition));
                    levelFlowCaughtUp(caughtUp, System.currentTimeMillis());
                }
            } catch (WakeupException stopped) {
                // stop() woke the poll
            } catch (RuntimeException unavailable) {
                consumerFailures.incrementAndGet();
                System.out.println("WARN es-mbo relay consumer failed, retrying in 2s: " + unavailable);
            } finally {
                consumer = null;
                decoder = null;
            }
            if (running.get()) {
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
    }

    /** Position at the first record at or after {@code from}, or at the end when there is none. */
    static void seekFrom(Consumer<String, byte[]> c, TopicPartition tp, long from) {
        Map<TopicPartition, OffsetAndTimestamp> found = c.offsetsForTimes(Map.of(tp, from), Duration.ofSeconds(10));
        OffsetAndTimestamp at = found == null ? null : found.get(tp);
        if (at != null) {
            c.seek(tp, at.offset());
        } else {
            c.seekToEnd(List.of(tp));
        }
    }

    private static TopicPartition optionalPartitionZero(Consumer<String, byte[]> c, String topic) {
        List<PartitionInfo> partitions;
        try {
            partitions = c.partitionsFor(topic, Duration.ofSeconds(10));
        } catch (org.apache.kafka.common.errors.TimeoutException | org.apache.kafka.common.errors.AuthorizationException absent) {
            return null;
        }
        if (partitions == null || partitions.isEmpty()) {
            return null;
        }
        if (partitions.size() != 1) {
            throw new IllegalStateException(topic + " must have exactly one partition, found " + partitions.size());
        }
        return new TopicPartition(topic, 0);
    }

    private EsMboSchemas.Subject subjectOf(String topic) {
        if (topic.equals(settings.esMboLevelFlowTopic())) {
            return EsMboSchemas.LEVEL_FLOW;
        }
        if (topic.equals(settings.esMboHealthTopic())) {
            return EsMboSchemas.HEALTH;
        }
        if (topic.equals(settings.esMboEventTopic())) {
            return EsMboSchemas.EVENT;
        }
        return null;
    }

    private AtomicLong refusedOf(EsMboSchemas.Subject subject) {
        return subject == EsMboSchemas.LEVEL_FLOW ? levelFlowRefused : subject == EsMboSchemas.HEALTH ? healthRefused : eventRefused;
    }

    /** One raw record from one of the three topics: id check, registry decode, record boundary, fold, fan-out. A registry outage propagates (re-read after retry). */
    synchronized void accept(String topic, String key, byte[] value, long now) {
        EsMboSchemas.Subject subject = subjectOf(topic);
        if (subject == null || value == null || key == null) {
            drops.incrementAndGet();   // a foreign topic, a tombstone or an unkeyed record
            return;
        }
        try {
            if (subject == EsMboSchemas.HEALTH) {
                EsMboSchemas.assertFramedAsAnyVersion(value, subject);   // 3509 (version 2) or its prior id
            } else {
                EsMboSchemas.assertFramedAs(value, subject);
            }
        } catch (IllegalStateException wrongId) {
            refusedOf(subject).incrementAndGet();
            return;
        }
        Deserializer<Object> d = decoder;
        Object decoded = d == null ? GatewayAvroDeserializer.UNDECODABLE : d.deserialize(topic, value);
        if (decoded == GatewayAvroDeserializer.UNDECODABLE) {
            undecodable.incrementAndGet();
            return;
        }
        if (subject == EsMboSchemas.LEVEL_FLOW) {
            foldRow(key, EsMboJson.checked(subject, decoded, EsMboJson.ONLY_VERSION_1));
        } else if (subject == EsMboSchemas.HEALTH) {
            GenericRecord r = EsMboJson.checked(subject, decoded, EsMboJson.HEALTH_VERSIONS);
            if (r == null || !EsMboJson.healthKey(key, r)) {
                drops.incrementAndGet();
                return;
            }
            long bucketEnd = (Long) r.get("bucketEnd");
            adopt(EsMboJson.text(r.get("contract")));   // the newest health record names the contract served
            if (bucketEnd <= closedThrough) {
                drops.incrementAndGet();   // a bucket already closed (a re-read): never a second frame
                return;
            }
            pending.put(bucketEnd, new PendingHealth(r, now));
            while (pending.size() > MAX_PENDING_HEALTH) {
                pending.pollFirstEntry();
                drops.incrementAndGet();
            }
        } else {
            acceptEvent(key, EsMboJson.checked(subject, decoded, EsMboJson.ONLY_VERSION_1), now);
        }
        tick(now);
    }

    /** The newest health record's contract becomes the one served; a change resets the folding state completely (open buckets, held health, watermark, frames, the other contract's events). */
    private void adopt(String contract) {
        if (contract.equals(current)) {
            return;
        }
        String previous = current;
        boolean firstAndMatchesRows = current == null && contract.equals(provisional);   // rows read before the first health are this contract's: keep them
        current = contract;
        provisional = null;
        if (!firstAndMatchesRows) {
            long lost = 0;
            for (List<EsMboJson.Row> rows : open.values()) {
                lost += rows.size();
            }
            if (previous == null) {
                otherContract.addAndGet(lost);   // rows read before the first health that belong to another contract: counted, not silent
            } else {
                rowsDropped.addAndGet(lost);
            }
            drops.addAndGet(pending.size());
            open.clear();
            pending.clear();
            levelFlowWatermark = 0;
            closedThrough = 0;
        }
        frames.clear();
        anyFrame = false;
        lastFrameBucketEnd = 0;
        lastEpochStatus = null;
        long otherEvents = events.values().stream().filter(e -> !e.contract().equals(contract)).count();
        otherContract.addAndGet(otherEvents);   // the previous contract's ringed events leave the replay: counted
        events.values().removeIf(e -> !e.contract().equals(contract));
    }

    /** The level-flow lane is caught up only if it EXISTS and its position has reached its end offset: an absent lane may still receive rows. */
    static boolean laneCaughtUp(boolean exists, long position, long endOffset) {
        return exists && position >= endOffset;
    }

    /** The consumer loop reports whether the level-flow topic exists; while it does not, health is held and nothing is emitted. */
    synchronized void levelFlowLaneAbsent(boolean absent) {
        levelFlowAbsent = absent;
    }

    /** Called by the consumer loop after each poll: the level-flow lane is (or is not) at its end offset. A seam for tests with an injected clock. */
    synchronized void levelFlowCaughtUp(boolean caughtUp, long now) {
        if (!caughtUp) {
            levelFlowCaughtUpSince = null;
        } else if (levelFlowCaughtUpSince == null) {
            levelFlowCaughtUpSince = now;
        }
        tick(now);
    }

    private void foldRow(String key, GenericRecord r) {
        EsMboJson.Row row = r == null ? null : EsMboJson.levelFlow(key, r);
        if (row == null) {
            drops.incrementAndGet();
            return;
        }
        long bucketEnd = (Long) r.get("bucketEnd");
        String contract = EsMboJson.text(r.get("contract"));
        if (current == null && provisional == null) {
            provisional = contract;
        }
        if (!contract.equals(current != null ? current : provisional)) {
            otherContract.incrementAndGet();   // not the served contract: dropped, and it never advances the served contract's watermark
            return;
        }
        if (bucketEnd <= closedThrough) {
            lateRows.incrementAndGet();   // its bucket was closed by the health record: never a second frame
            return;
        }
        levelFlowWatermark = Math.max(levelFlowWatermark, bucketEnd);
        List<EsMboJson.Row> rows = open.computeIfAbsent(bucketEnd, k -> new ArrayList<>());
        if (rows.size() >= MAX_ROWS_PER_BUCKET) {
            rowsDropped.incrementAndGet();
        } else {
            rows.add(row);
        }
        while (open.size() > MAX_OPEN_BUCKETS) {
            rowsDropped.addAndGet(open.pollFirstEntry().getValue().size());
        }
    }

    /**
     * Closes every held bucket that is PROVEN complete: level-flow has delivered a record at or after its bucket end (the lane is in bucket order), or the
     * level-flow lane is at its end offset and has been quiet for {@link #HEALTH_HOLD_MS} since both the health record and the catch-up (so no row can be
     * pending). Wall time alone never closes a bucket while level-flow is behind.
     */
    synchronized void tick(long now) {
        while (!pending.isEmpty()) {
            var first = pending.firstEntry();
            boolean reached = first.getKey() <= levelFlowWatermark;
            boolean quiet = levelFlowCaughtUpSince != null && now - Math.max(first.getValue().arrivedAt(), levelFlowCaughtUpSince) >= HEALTH_HOLD_MS;
            if (!reached && !quiet) {
                break;
            }
            pending.pollFirstEntry();
            close(first.getValue().record(), first.getKey(), now);
        }
    }

    private void close(GenericRecord health, long bucketEnd, long now) {
        List<EsMboJson.Row> rows = open.getOrDefault(bucketEnd, List.of());
        String epochStatus = EsMboJson.text(health.get("epochStatus"));
        String base = EsMboJson.frameBase(EsMboJson.text(health.get("contract")), bucketEnd, epochStatus, (Long) health.get("bestBidTick"), (Long) health.get("bestAskTick"), rows);
        var earlier = open.headMap(bucketEnd, false);   // buckets that never got a health: their rows are discarded, and counted
        for (List<EsMboJson.Row> lost : earlier.values()) {
            rowsDropped.addAndGet(lost.size());
        }
        open.headMap(bucketEnd, true).clear();
        closedThrough = Math.max(closedThrough, bucketEnd);
        framesSeen.incrementAndGet();
        anyFrame = true;
        lastFrameBucketEnd = bucketEnd;
        lastEpochStatus = epochStatus;
        frames.addLast(new Frame(base, bucketEnd, base.getBytes(StandardCharsets.UTF_8).length + 16));
        while (frames.size() > FRAME_RING) {
            frames.removeFirst();
            ringEvicted.incrementAndGet();
        }
        if (fresh(bucketEnd, now, settings.esMboFreshMs())) {
            sink.accept(FRAME_EVENT, EsMboJson.close(base, false));
        }
    }

    private void pruneLedger(long now) {
        if (now - lastLedgerPrune < LEDGER_PRUNE_EVERY_MS) {
            return;
        }
        lastLedgerPrune = now;
        long floor = now - EVENTS_LOOKBACK_MS;
        ledger.values().removeIf(v -> v[1] < floor);
        ringEvicted.addAndGet(events.values().stream().filter(e -> e.bucketEnd() < floor).count());
        events.values().removeIf(e -> e.bucketEnd() < floor);   // the replay ring covers the same window, so a pruned ledger entry is also gone from the replay
    }

    synchronized int ledgerSizeForTest() {
        return ledger.size();
    }

    synchronized String currentContractForTest() {
        return current;
    }

    long eventsFutureForTest() {
        return eventsFuture.get();
    }

    long rowsDroppedForTest() {
        return rowsDropped.get();
    }

    long otherContractForTest() {
        return otherContract.get();
    }

    private void acceptEvent(String key, GenericRecord r, long now) {
        String base = r == null ? null : EsMboJson.eventBase(key, r);
        if (base == null) {
            drops.incrementAndGet();
            return;
        }
        long bucketEnd = (Long) r.get("bucketEnd");
        int revision = (Integer) r.get("revision");
        String contract = EsMboJson.text(r.get("contract"));
        if (current != null && !contract.equals(current)) {
            otherContract.incrementAndGet();
            return;
        }
        if (bucketEnd < now - EVENTS_LOOKBACK_MS) {
            eventsOutsideWindow.incrementAndGet();   // outside the window the ledger covers: never accepted, so a pruned ledger entry can never let it in
            return;
        }
        if (bucketEnd > now + EsLiquidityWallRelay.FUTURE_SKEW_MS) {
            eventsFuture.incrementAndGet();   // ahead of the clock beyond the skew allowance: never ledgered or ringed, so it can neither grow them nor outlive the window
            return;
        }
        pruneLedger(now);
        long[] known = ledger.get(key);
        if (known != null && revision <= known[0]) {
            duplicateEvents.incrementAndGet();   // equal = a duplicate, lower = a delayed older revision: neither is stored or forwarded
            return;
        }
        ledger.put(key, new long[] {revision, bucketEnd});
        eventsSeen.incrementAndGet();
        anyEvent = true;
        lastEventBucketEnd = Math.max(lastEventBucketEnd, bucketEnd);
        events.remove(key);   // the latest revision of an eventId replaces the earlier and moves to the end
        events.put(key, new Event(base, base.getBytes(StandardCharsets.UTF_8).length + 16, contract, bucketEnd));
        while (events.size() > EVENT_RING) {
            events.remove(events.keySet().iterator().next());
            ringEvicted.incrementAndGet();
        }
        if (fresh(bucketEnd, now, settings.esMboEventLiveMs())) {
            sink.accept(EVENT_EVENT, EsMboJson.close(base, false));
        }
    }

    /** Within the bound of its bucket end and not ahead of the clock by more than the skew allowance. */
    static boolean fresh(long bucketEnd, long now, long freshMs) {
        return now - bucketEnd <= freshMs && bucketEnd - now <= EsLiquidityWallRelay.FUTURE_SKEW_MS;
    }

    /** The heartbeat body, so the page can say "engine not publishing" honestly. */
    synchronized String statusJson() {
        return "{\"enabled\":true,\"contract\":" + (current == null ? "null" : EsMboJson.quote(current)) + ",\"levelFlowLane\":\"" + (levelFlowAbsent ? "absent" : "present") + "\",\"lastFrameBucketEnd\":" + (anyFrame ? lastFrameBucketEnd : null) + ",\"lastEventBucketEnd\":" + (anyEvent ? lastEventBucketEnd : null)
                + ",\"epochStatus\":" + (lastEpochStatus == null ? "null" : EsMboJson.quote(lastEpochStatus)) + ",\"framesSeen\":" + framesSeen.get() + ",\"eventsSeen\":" + eventsSeen.get()
                + ",\"schemaIdRefused\":" + (levelFlowRefused.get() + healthRefused.get() + eventRefused.get()) + ",\"undecodable\":" + undecodable.get()
                + ",\"consumerFailures\":" + consumerFailures.get() + "}";
    }

    /**
     * The connect replay: ONE {@code es-mbo-history} = the last frames (oldest first) and the event ring, every member {@code cached:true}. Over
     * {@code ES_MBO_HISTORY_MAX_BYTES} the oldest frames are dropped (then the oldest events) until it fits. The status follows so the page knows at once.
     */
    synchronized void replay(BiConsumer<String, String> send) {
        send.accept(HISTORY_EVENT, historyJson());
        send.accept(STATUS_EVENT, statusJson());
    }

    synchronized String historyJson() {
        long budget = historyMaxBytes - 64;
        long total = 0;
        for (Event e : events.values()) {
            total += e.bytes();
        }
        int firstEvent = 0;
        List<Event> evs = new ArrayList<>(events.values());
        while (total > budget / 2 && firstEvent < evs.size()) {   // events never take more than half: the frames are the page's body
            total -= evs.get(firstEvent++).bytes();
        }
        long eventBytes = total;
        List<Frame> fs = new ArrayList<>(frames);
        long frameBytes = 0;
        for (Frame f : fs) {
            frameBytes += f.bytes();
        }
        int firstFrame = 0;
        while (frameBytes + eventBytes > budget && firstFrame < fs.size()) {
            frameBytes -= fs.get(firstFrame++).bytes();
        }
        StringBuilder sb = new StringBuilder((int) Math.min(budget + 64, Integer.MAX_VALUE - 16)).append("{\"frames\":[");
        for (int i = firstFrame; i < fs.size(); i++) {
            sb.append(i > firstFrame ? "," : "").append(EsMboJson.close(fs.get(i).base(), true));
        }
        sb.append("],\"events\":[");
        for (int i = firstEvent; i < evs.size(); i++) {
            sb.append(i > firstEvent ? "," : "").append(EsMboJson.close(evs.get(i).base(), true));
        }
        return sb.append("],\"cached\":true}").toString();
    }

    String metrics() {
        String p = "gateway_es_mbo_";
        StringBuilder sb = new StringBuilder();
        sb.append("# HELP ").append(p).append("enabled Whether the ES MBO relay is enabled.\n# TYPE ").append(p).append("enabled gauge\n").append(p).append("enabled ")
                .append(settings.esMboRelayEnabled() ? 1 : 0).append('\n');
        counter(sb, p + "frames_total", "Frames closed by a health record.", framesSeen.get());
        counter(sb, p + "events_total", "Event records accepted.", eventsSeen.get());
        counter(sb, p + "level_flow_schema_id_refused_total", "Level-flow values refused BEFORE decoding (wrong schema id).", levelFlowRefused.get());
        counter(sb, p + "health_schema_id_refused_total", "Health values refused BEFORE decoding (wrong schema id).", healthRefused.get());
        counter(sb, p + "event_schema_id_refused_total", "Event values refused BEFORE decoding (wrong schema id).", eventRefused.get());
        counter(sb, p + "undecodable_total", "Framed values the registry could not decode.", undecodable.get());
        counter(sb, p + "drops_total", "Records refused at the gateway boundary.", drops.get());
        counter(sb, p + "duplicate_events_total", "Event records ignored because their revision was not newer than the stored one.", duplicateEvents.get());
        counter(sb, p + "other_contract_total", "Rows and events of a contract other than the one served (the newest health record's).", otherContract.get());
        counter(sb, p + "events_outside_window_total", "Event records whose bucket end is older than the events read window.", eventsOutsideWindow.get());
        counter(sb, p + "events_future_total", "Event records whose bucket end is ahead of the clock by more than the skew allowance.", eventsFuture.get());
        counter(sb, p + "ring_evicted_total", "Frames and events that left their replay ring (capacity or the events window).", ringEvicted.get());
        counter(sb, p + "late_rows_total", "Level-flow rows for a bucket already closed.", lateRows.get());
        counter(sb, p + "rows_dropped_total", "Level-flow rows dropped by the buffer bounds.", rowsDropped.get());
        counter(sb, p + "consumer_failures_total", "Consumer loop failures (each followed by a 2 s retry).", consumerFailures.get());
        return sb.toString();
    }

    private static void counter(StringBuilder sb, String name, String help, long v) {
        sb.append("# HELP ").append(name).append(' ').append(help).append("\n# TYPE ").append(name).append(" counter\n").append(name).append(' ').append(v).append('\n');
    }

    synchronized int openBucketsForTest() {
        int n = open.size();
        return n;
    }

    long duplicateEventsForTest() {
        return duplicateEvents.get();
    }

    synchronized int framesHeldForTest() {
        return frames.size();
    }

    synchronized int eventsHeldForTest() {
        return events.size();
    }

    long schemaIdRefusedForTest(EsMboSchemas.Subject subject) {
        return refusedOf(subject).get();
    }

    long dropsForTest() {
        return drops.get();
    }

    long lateRowsForTest() {
        return lateRows.get();
    }

    @PreDestroy
    void stop() {
        running.set(false);
        if (heartbeat != null) {
            heartbeat.shutdownNow();
        }
        var c = consumer;
        if (c != null) {
            c.wakeup();
        }
        if (thread != null) {
            try {
                thread.join(5000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}

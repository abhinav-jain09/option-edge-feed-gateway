package app.feedgateway;

import com.optionsedge.contracts.liquiditywall.LiquidityWallSchemas;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;
import org.apache.avro.generic.GenericRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.consumer.OffsetAndTimestamp;
import org.apache.kafka.common.PartitionInfo;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.errors.WakeupException;
import org.apache.kafka.common.serialization.Deserializer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * The ES liquidity-wall relay (ES Book Liquidity Walls Requirements rev 11 section 8 "Gateway and page"; es-liquidity-wall-service), modelled on
 * {@link RithmicDepthRelay}: one read-only tail of the dashboard, events, voice and buckets topics per gateway replica (read_committed, partition 0 of each
 * assigned, no consumer group, nothing committed), fanned out through the existing authenticated sockets as the global events {@link #HEATMAP_EVENT},
 * {@link #WALL_EVENT} and {@link #VOICE_EVENT}, and folded into the 60-second session projection of {@link EsLiquidityWallHistory}.
 * <p>
 * The boundary, in order, per record: the value's Confluent frame must carry the topic's FIXED schema id (3403 dashboard, 3404 events, 3407 voice, 3402
 * buckets; {@link LiquidityWallSchemas#assertFramedAs}) BEFORE any decoding, a mismatch being refused and counted per topic; then the bytes are decoded by the
 * registry through {@link GatewayAvroDeserializer} (tolerant: an unknown id or a corrupt body is {@link GatewayAvroDeserializer#UNDECODABLE}, counted; a
 * registry outage fails the poll and the record is re-read after the retry); then {@link EsLiquidityWallJson}'s record checks.
 * <ul>
 * <li>Dashboard: the latest frame is cached and broadcast while it is within {@link GatewaySettings#esLiquidityWallFreshMs()} of its bucket end; an older
 * frame (a boot read from the session open, a backlog) is folded into the projection and never shown as live, like {@code liquidity-heatmap}.</li>
 * <li>Events: read from the session open (the retained audit trail, which reconstructs the projection's walls); a record is forwarded live only while its
 * bucket end is within {@link GatewaySettings#esLiquidityWallEventReplayMs()} of now (a boot catch-up is folded and ringed, never broadcast as live); the
 * last {@link GatewaySettings#esLiquidityWallEventRing()} are kept and a connecting socket receives those within the same window, in log order.</li>
 * <li>Voice: forwarded and cached as the last value, each delivery carrying {@code stale} = older than {@link GatewaySettings#esLiquidityWallVoiceFreshMs()}
 * from the bucket end (section 7b: the page speaks only a fresh record that arrived live).</li>
 * <li>Buckets: never forwarded; read from the session open, the retained source of the projection's per-level sizes, states, bucket counts and
 * classification-window quantities.</li>
 * </ul>
 * Every frame a connect replay sends carries {@code "cached":true} and every live forward {@code "cached":false}, so the page tells the two apart without a
 * timer. The dashboard topic is COMPACTED under one key, so a read from the session open could only ever recover the surviving latest frame: it is read from
 * the latest record, always, and the projection reports its dashboard-derived coverage as SINCE_START from the instant the read began. The buckets and events
 * reads start at the current trade date's session open (18:00 ET of the previous day, {@code offsetsForTimes}) when
 * {@link GatewaySettings#esLiquidityWallHistoryFromSessionOpen()}, else at the latest record; the voice read starts at the latest record. After a consumer
 * failure the read resumes at the next unread offset of each partition, so no record is folded twice within one gateway life. Disabled
 * ({@code ES_LIQUIDITY_WALL_RELAY_ENABLED=false}, the default): no thread, no consumer, nothing cached, the history endpoint answers 404.
 */
@Component
final class EsLiquidityWallRelay {
    static final String HEATMAP_EVENT = "es-liquidity-heatmap";
    static final String WALL_EVENT = "es-liquidity-wall";
    static final String VOICE_EVENT = "es-liquidity-voice";
    /** A frame more than this far ahead of the gateway clock is not fresh (clock skew), as the depth relay rules. */
    static final long FUTURE_SKEW_MS = 5_000L;

    private record Cached(String json, long bucketEnd) {
    }

    /** One topic's counters. */
    private static final class Lane {
        final AtomicLong accepted = new AtomicLong();
        final AtomicLong schemaIdRefused = new AtomicLong();
    }

    private final GatewaySettings settings;
    private final BiConsumer<String, String> sink;
    private final EsLiquidityWallHistory history = new EsLiquidityWallHistory();
    private final AtomicBoolean running = new AtomicBoolean();
    private final Lane dashboardLane = new Lane(), eventsLane = new Lane(), voiceLane = new Lane(), bucketsLane = new Lane();
    private final AtomicLong drops = new AtomicLong();
    private final AtomicLong undecodable = new AtomicLong();
    private final AtomicLong staleFrames = new AtomicLong();
    private final AtomicLong staleEvents = new AtomicLong();
    private final AtomicLong consumerFailures = new AtomicLong();
    private final ArrayDeque<Cached> wallRing = new ArrayDeque<>();
    private Cached dashboard;
    private Cached voice;
    /** The registry decoder of the current consumer life (set before the first poll), or the test seam's. */
    private volatile Deserializer<Object> decoder;
    private volatile KafkaConsumer<String, byte[]> consumer;
    private Thread thread;
    /** The next unread offset per partition: a reconnect resumes here rather than re-reading the session. Consumer thread only. */
    private final Map<TopicPartition, Long> resumeAt = new HashMap<>();

    // @Autowired marks the Spring constructor: with the test seam below and no marker the context fails to start (the LiquidityHistoryController pattern).
    @org.springframework.beans.factory.annotation.Autowired
    EsLiquidityWallRelay(GatewaySettings settings, ObjectProvider<FeedGatewayService> gateway) {
        this(settings, (event, json) -> gateway.getObject().streamEsLiquidityWall(event, json), null);
    }

    /** Test seam: the frames go to {@code sink} instead of the sockets, the bytes are decoded by {@code decoder}. */
    EsLiquidityWallRelay(GatewaySettings settings, BiConsumer<String, String> sink, Deserializer<Object> decoder) {
        this.settings = settings;
        this.sink = sink;
        this.decoder = decoder;
    }

    @PostConstruct
    void start() {
        if (!settings.esLiquidityWallRelayEnabled()) {
            return;
        }
        running.set(true);
        thread = new Thread(this::run, "es-liquidity-wall-relay");
        thread.setDaemon(true);
        thread.start();
    }

    /** True while the consumer thread exists: false for a disabled relay, which consumes nothing. */
    boolean consuming() {
        return running.get();
    }

    EsLiquidityWallHistory history() {
        return history;
    }

    private void run() {
        while (running.get()) {
            String dashboardTopic = settings.esLiquidityWallDashboardTopic(), eventsTopic = settings.esLiquidityWallEventsTopic();
            String voiceTopic = settings.esLiquidityWallVoiceTopic(), bucketsTopic = settings.esLiquidityWallBucketsTopic();
            Properties p = new Properties();
            p.put("bootstrap.servers", settings.bootstrapServers());
            p.put("client.id", "es-liquidity-wall-relay");
            p.put("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
            p.put("value.deserializer", "org.apache.kafka.common.serialization.ByteArrayDeserializer");   // the id check reads the raw frame first
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
            decoderConfig.put(GatewayAvroDeserializer.TOLERANT_TOPICS, dashboardTopic + "," + eventsTopic + "," + voiceTopic + "," + bucketsTopic);
            try (var c = new KafkaConsumer<String, byte[]>(p); GatewayAvroDeserializer d = new GatewayAvroDeserializer()) {
                d.configure(decoderConfig, false);
                decoder = d;
                consumer = c;
                TopicPartition dashboardPartition = partitionZero(c, dashboardTopic), eventsPartition = partitionZero(c, eventsTopic);
                TopicPartition voicePartition = partitionZero(c, voiceTopic), bucketsPartition = partitionZero(c, bucketsTopic);
                List<TopicPartition> partitions = List.of(dashboardPartition, eventsPartition, voicePartition, bucketsPartition);
                c.assign(partitions);
                for (TopicPartition tp : partitions) {
                    Long resume = resumeAt.get(tp);
                    if (resume != null) {
                        c.seek(tp, resume);
                    } else if (tp.equals(bucketsPartition) || tp.equals(eventsPartition)) {
                        seekFromSessionOpen(c, tp, tp.equals(bucketsPartition));
                    } else {
                        c.seekToEnd(List.of(tp));   // the compacted dashboard (its history is one surviving frame) and the voice: latest only
                        if (tp.equals(dashboardPartition)) {
                            history.dashboardCoverageFrom(System.currentTimeMillis());
                        }
                    }
                }
                while (running.get()) {
                    for (var record : c.poll(Duration.ofMillis(250))) {
                        accept(record.topic(), record.key(), record.value(), System.currentTimeMillis());
                        resumeAt.put(new TopicPartition(record.topic(), record.partition()), record.offset() + 1);
                    }
                }
            } catch (WakeupException stopped) {
                // stop() woke the poll
            } catch (RuntimeException unavailable) {
                consumerFailures.incrementAndGet();
                System.out.println("WARN es-liquidity-wall relay consumer failed, retrying in 2s: " + unavailable);
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

    /** Section 8: one partition each. A topic with another count is refused loud (retried every 2 s) rather than read in part. */
    private static TopicPartition partitionZero(KafkaConsumer<String, byte[]> c, String topic) {
        List<PartitionInfo> partitions = c.partitionsFor(topic, Duration.ofSeconds(10));
        if (partitions == null || partitions.size() != 1) {
            throw new IllegalStateException(topic + " must have exactly one partition, found " + (partitions == null ? "none" : partitions.size()));
        }
        return new TopicPartition(topic, 0);
    }

    /** The two retained reads (buckets, events): from the session open when the setting says so and the broker finds an offset for it, else from the latest record. */
    private void seekFromSessionOpen(KafkaConsumer<String, byte[]> c, TopicPartition tp, boolean buckets) {
        long now = System.currentTimeMillis();
        if (settings.esLiquidityWallHistoryFromSessionOpen()) {
            LocalDate tradeDate = EsLiquidityWallHistory.tradeDate(now);
            long from = EsLiquidityWallHistory.sessionStartMs(tradeDate);
            Map<TopicPartition, OffsetAndTimestamp> found = c.offsetsForTimes(Map.of(tp, from), Duration.ofSeconds(10));
            OffsetAndTimestamp at = found == null ? null : found.get(tp);
            if (at != null) {
                c.seek(tp, at.offset());
                origin(buckets, "SESSION_OPEN", from);
                return;
            }
        }
        c.seekToEnd(List.of(tp));
        origin(buckets, "LATEST", now);
    }

    private void origin(boolean buckets, String origin, long from) {
        if (buckets) {
            history.bucketsSeekOrigin(origin, from);
        } else {
            history.eventsSeekOrigin(origin, from);
        }
    }

    /** The subject whose fixed id a topic's values must carry, or null for a topic this relay does not read. */
    private LiquidityWallSchemas.Subject subjectOf(String topic) {
        if (topic.equals(settings.esLiquidityWallDashboardTopic())) {
            return LiquidityWallSchemas.DASHBOARD;
        }
        if (topic.equals(settings.esLiquidityWallEventsTopic())) {
            return LiquidityWallSchemas.EVENT;
        }
        if (topic.equals(settings.esLiquidityWallVoiceTopic())) {
            return LiquidityWallSchemas.VOICE;
        }
        if (topic.equals(settings.esLiquidityWallBucketsTopic())) {
            return LiquidityWallSchemas.BUCKET;
        }
        return null;
    }

    private Lane lane(LiquidityWallSchemas.Subject subject) {
        return subject == LiquidityWallSchemas.DASHBOARD ? dashboardLane : subject == LiquidityWallSchemas.EVENT ? eventsLane : subject == LiquidityWallSchemas.VOICE ? voiceLane : bucketsLane;
    }

    /**
     * One raw record from one of the four topics: the frame's schema id against the topic's fixed id, the registry decode, the record boundary, the history
     * fold, the cache and the fan-out. A registry infrastructure failure propagates (the poll fails, the record is re-read after the retry); everything else
     * is counted and never reaches a socket.
     */
    synchronized void accept(String topic, String key, byte[] value, long now) {
        LiquidityWallSchemas.Subject subject = subjectOf(topic);
        if (subject == null || value == null) {
            drops.incrementAndGet();   // a foreign topic, or a tombstone
            return;
        }
        Lane lane = lane(subject);
        try {
            LiquidityWallSchemas.assertFramedAs(value, subject);
        } catch (IllegalStateException wrongId) {
            lane.schemaIdRefused.incrementAndGet();
            return;
        }
        Deserializer<Object> d = decoder;
        Object decoded = d == null ? GatewayAvroDeserializer.UNDECODABLE : d.deserialize(topic, value);
        if (decoded == GatewayAvroDeserializer.UNDECODABLE) {
            undecodable.incrementAndGet();
            return;
        }
        if (subject == LiquidityWallSchemas.DASHBOARD) {
            String json = EsLiquidityWallJson.dashboard(key, decoded);
            if (json == null) {
                drops.incrementAndGet();
                return;
            }
            GenericRecord r = (GenericRecord) decoded;
            long bucketEnd = (Long) r.get("bucketEnd");
            history.foldDashboard(r);
            lane.accepted.incrementAndGet();
            if (!fresh(bucketEnd, now, settings.esLiquidityWallFreshMs())) {
                staleFrames.incrementAndGet();   // folded, never shown as live
                return;
            }
            dashboard = new Cached(json, bucketEnd);
            sink.accept(HEATMAP_EVENT, EsLiquidityWallJson.withCached(json, false));
        } else if (subject == LiquidityWallSchemas.EVENT) {
            String json = EsLiquidityWallJson.event(key, decoded);
            if (json == null) {
                drops.incrementAndGet();
                return;
            }
            GenericRecord r = (GenericRecord) decoded;
            long bucketEnd = (Long) r.get("bucketEnd");
            history.foldEvent(r);
            lane.accepted.incrementAndGet();
            wallRing.addLast(new Cached(json, bucketEnd));
            while (wallRing.size() > settings.esLiquidityWallEventRing()) {
                wallRing.removeFirst();
            }
            if (!fresh(bucketEnd, now, settings.esLiquidityWallEventReplayMs())) {
                // the session-open catch-up (older than the replay window) or a bucket end ahead of the clock beyond the skew allowance: folded and ringed,
                // never broadcast as if it had just happened; the same two-sided gate as the dashboard frames
                staleEvents.incrementAndGet();
                return;
            }
            sink.accept(WALL_EVENT, EsLiquidityWallJson.withCached(json, false));
        } else if (subject == LiquidityWallSchemas.VOICE) {
            String json = EsLiquidityWallJson.voice(key, decoded);
            if (json == null) {
                drops.incrementAndGet();
                return;
            }
            long bucketEnd = (Long) ((GenericRecord) decoded).get("bucketEnd");
            lane.accepted.incrementAndGet();
            voice = new Cached(json, bucketEnd);
            sink.accept(VOICE_EVENT, voiceFrame(voice, now, false));
        } else {
            GenericRecord r = EsLiquidityWallJson.bucket(key, decoded);
            if (r == null) {
                drops.incrementAndGet();
                return;
            }
            history.foldBucket(r);
            lane.accepted.incrementAndGet();
        }
    }

    /** Within the bound of its bucket end and not ahead of the clock by more than the skew allowance. */
    static boolean fresh(long bucketEnd, long now, long freshMs) {
        return now - bucketEnd <= freshMs && bucketEnd - now <= FUTURE_SKEW_MS;
    }

    private String voiceFrame(Cached v, long now, boolean cached) {
        return EsLiquidityWallJson.withCached(EsLiquidityWallJson.withStale(v.json(), now - v.bucketEnd() > settings.esLiquidityWallVoiceFreshMs()), cached);
    }

    /** The connect replay, every frame marked {@code cached}: the fresh dashboard frame, the ring's recent events in log order, the last voice record marked by its age now. */
    synchronized void replay(BiConsumer<String, String> send) {
        replay(send, System.currentTimeMillis());
    }

    synchronized void replay(BiConsumer<String, String> send, long now) {
        Cached d = dashboard;
        if (d != null && fresh(d.bucketEnd(), now, settings.esLiquidityWallFreshMs())) {
            send.accept(HEATMAP_EVENT, EsLiquidityWallJson.withCached(d.json(), true));
        }
        long window = settings.esLiquidityWallEventReplayMs();
        for (Cached e : wallRing) {
            if (fresh(e.bucketEnd(), now, window)) {   // two-sided: neither older than the window nor ahead of the clock beyond the skew allowance
                send.accept(WALL_EVENT, EsLiquidityWallJson.withCached(e.json(), true));
            }
        }
        Cached v = voice;
        if (v != null) {
            send.accept(VOICE_EVENT, voiceFrame(v, now, true));
        }
    }

    String metrics() {
        String p = "gateway_es_liquidity_wall_";
        StringBuilder sb = new StringBuilder();
        sb.append("# HELP ").append(p).append("enabled Whether the ES liquidity-wall relay is enabled.\n# TYPE ").append(p).append("enabled gauge\n").append(p).append("enabled ")
                .append(settings.esLiquidityWallRelayEnabled() ? 1 : 0).append('\n');
        String[] names = {"dashboard", "events", "voice", "buckets"};
        Lane[] lanes = {dashboardLane, eventsLane, voiceLane, bucketsLane};
        for (int i = 0; i < names.length; i++) {
            sb.append("# HELP ").append(p).append(names[i]).append("_accepted_total ").append(names[i]).append(" records that passed the gateway boundary.\n# TYPE ").append(p).append(names[i])
                    .append("_accepted_total counter\n").append(p).append(names[i]).append("_accepted_total ").append(lanes[i].accepted.get()).append('\n');
            sb.append("# HELP ").append(p).append(names[i]).append("_schema_id_refused_total ").append(names[i])
                    .append(" values refused BEFORE decoding because the Confluent frame did not carry the topic's fixed schema id.\n# TYPE ").append(p).append(names[i])
                    .append("_schema_id_refused_total counter\n").append(p).append(names[i]).append("_schema_id_refused_total ").append(lanes[i].schemaIdRefused.get()).append('\n');
        }
        sb.append("# HELP ").append(p).append("stale_frames_total Dashboard records folded but not broadcast because their bucket end was outside the freshness bound.\n# TYPE ").append(p)
                .append("stale_frames_total counter\n").append(p).append("stale_frames_total ").append(staleFrames.get()).append('\n');
        sb.append("# HELP ").append(p).append("stale_events_total Event records folded and ringed but not broadcast because their bucket end was outside the replay window (the boot catch-up).\n# TYPE ").append(p)
                .append("stale_events_total counter\n").append(p).append("stale_events_total ").append(staleEvents.get()).append('\n');
        sb.append("# HELP ").append(p).append("drops_total Records refused at the gateway boundary (foreign topic, tombstone, wrong record, key, version, oversize).\n# TYPE ").append(p)
                .append("drops_total counter\n").append(p).append("drops_total ").append(drops.get()).append('\n');
        sb.append("# HELP ").append(p).append("undecodable_total Framed values the registry could not decode (unknown id, corrupt body).\n# TYPE ").append(p)
                .append("undecodable_total counter\n").append(p).append("undecodable_total ").append(undecodable.get()).append('\n');
        sb.append("# HELP ").append(p).append("consumer_failures_total Consumer loop failures (each followed by a 2 s retry).\n# TYPE ").append(p)
                .append("consumer_failures_total counter\n").append(p).append("consumer_failures_total ").append(consumerFailures.get()).append('\n');
        sb.append("# HELP ").append(p).append("history_minutes Minutes held by the 60-second session projection.\n# TYPE ").append(p).append("history_minutes gauge\n").append(p)
                .append("history_minutes ").append(history.minuteCount()).append('\n');
        return sb.toString();
    }

    long dropsForTest() {
        return drops.get();
    }

    long staleFramesForTest() {
        return staleFrames.get();
    }

    long staleEventsForTest() {
        return staleEvents.get();
    }

    long schemaIdRefusedForTest(LiquidityWallSchemas.Subject subject) {
        return lane(subject).schemaIdRefused.get();
    }

    @PreDestroy
    void stop() {
        running.set(false);
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

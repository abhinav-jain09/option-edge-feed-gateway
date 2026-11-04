package app.feedgateway;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayDeque;
import java.util.ArrayList;
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
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * The ES liquidity-wall relay (ES Book Liquidity Walls Requirements rev 11 section 8 "Gateway and page"; es-liquidity-wall-service), modelled on
 * {@link RithmicDepthRelay}: one read-only tail of the dashboard, events and voice topics per gateway replica, decoded by the registered schema id through
 * {@link GatewayAvroDeserializer} (read_committed, partition 0 assigned, no consumer group, nothing committed), fanned out through the existing
 * authenticated sockets as the global events {@link #HEATMAP_EVENT}, {@link #WALL_EVENT} and {@link #VOICE_EVENT}, and folded into the 60-second
 * session projection of {@link EsLiquidityWallHistory}.
 * <ul>
 * <li>Dashboard: the latest frame is cached and broadcast while it is within {@link GatewaySettings#esLiquidityWallFreshMs()} of its bucket end; an older
 * frame (a boot read from the session open, a backlog) is folded into the projection and never shown as live, like {@code liquidity-heatmap}.</li>
 * <li>Events: every record is forwarded; the last {@link GatewaySettings#esLiquidityWallEventRing()} are replayed to a connecting socket in log order.</li>
 * <li>Voice: forwarded and cached as the last value, each delivery carrying {@code stale} = older than {@link GatewaySettings#esLiquidityWallVoiceFreshMs()}
 * from the bucket end (section 7b: the page speaks only a fresh record that arrived live; a connect replay is rendered silently).</li>
 * </ul>
 * The dashboard read starts at the current trade date's session open (18:00 ET of the previous day, {@code offsetsForTimes}) when
 * {@link GatewaySettings#esLiquidityWallHistoryFromSessionOpen()}, else at the latest record; events and voice start at the latest record. After a consumer
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

    private final GatewaySettings settings;
    private final BiConsumer<String, String> sink;
    private final EsLiquidityWallHistory history = new EsLiquidityWallHistory();
    private final AtomicBoolean running = new AtomicBoolean();
    private final AtomicLong dashboardAccepted = new AtomicLong();
    private final AtomicLong eventsAccepted = new AtomicLong();
    private final AtomicLong voiceAccepted = new AtomicLong();
    private final AtomicLong drops = new AtomicLong();
    private final AtomicLong undecodable = new AtomicLong();
    private final AtomicLong staleFrames = new AtomicLong();
    private final AtomicLong consumerFailures = new AtomicLong();
    private final ArrayDeque<String> wallRing = new ArrayDeque<>();
    private Cached dashboard;
    private Cached voice;
    private volatile KafkaConsumer<String, Object> consumer;
    private Thread thread;
    /** The next unread offset per partition: a reconnect resumes here rather than re-reading the session. */
    private final Map<TopicPartition, Long> resumeAt = new HashMap<>();

    // @Autowired marks the Spring constructor: with the test seam below and no marker the context fails to start (the LiquidityHistoryController pattern).
    @org.springframework.beans.factory.annotation.Autowired
    EsLiquidityWallRelay(GatewaySettings settings, ObjectProvider<FeedGatewayService> gateway) {
        this(settings, (event, json) -> gateway.getObject().streamEsLiquidityWall(event, json));
    }

    /** Test seam: the frames go to {@code sink} instead of the sockets. */
    EsLiquidityWallRelay(GatewaySettings settings, BiConsumer<String, String> sink) {
        this.settings = settings;
        this.sink = sink;
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
            String dashboardTopic = settings.esLiquidityWallDashboardTopic(), eventsTopic = settings.esLiquidityWallEventsTopic(), voiceTopic = settings.esLiquidityWallVoiceTopic();
            Properties p = new Properties();
            p.put("bootstrap.servers", settings.bootstrapServers());
            p.put("client.id", "es-liquidity-wall-relay");
            p.put("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
            p.put("value.deserializer", GatewayAvroDeserializer.class.getName());
            p.put("schema.registry.url", settings.schemaRegistryUrl());
            p.put("specific.avro.reader", false);
            p.put(GatewayAvroDeserializer.SPX_PRICE_TOPIC, settings.underlyingSpxPriceTopic());
            p.put(GatewayAvroDeserializer.TOLERANT_TOPICS, dashboardTopic + "," + eventsTopic + "," + voiceTopic);
            p.put("enable.auto.commit", false);
            p.put("isolation.level", "read_committed");
            p.put("max.poll.records", 500);
            p.put("fetch.max.bytes", 4 * 1048576);
            p.put("max.partition.fetch.bytes", 2 * 1048576);
            p.put("request.timeout.ms", 10000);
            p.put("default.api.timeout.ms", 10000);
            p.put("http.connect.timeout.ms", 1000);
            p.put("http.read.timeout.ms", 1000);
            p.put("max.schemas.per.subject", 64);
            settings.applyKafkaSecurity(p);
            try (var c = new KafkaConsumer<String, Object>(p)) {
                consumer = c;
                TopicPartition dashboardPartition = partitionZero(c, dashboardTopic), eventsPartition = partitionZero(c, eventsTopic), voicePartition = partitionZero(c, voiceTopic);
                List<TopicPartition> partitions = List.of(dashboardPartition, eventsPartition, voicePartition);
                c.assign(partitions);
                for (TopicPartition tp : partitions) {
                    Long resume = resumeAt.get(tp);
                    if (resume != null) {
                        c.seek(tp, resume);
                    } else if (tp.equals(dashboardPartition)) {
                        seekDashboard(c, tp);
                    } else {
                        c.seekToEnd(List.of(tp));
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
    private static TopicPartition partitionZero(KafkaConsumer<String, Object> c, String topic) {
        List<PartitionInfo> partitions = c.partitionsFor(topic, Duration.ofSeconds(10));
        if (partitions == null || partitions.size() != 1) {
            throw new IllegalStateException(topic + " must have exactly one partition, found " + (partitions == null ? "none" : partitions.size()));
        }
        return new TopicPartition(topic, 0);
    }

    private void seekDashboard(KafkaConsumer<String, Object> c, TopicPartition tp) {
        long now = System.currentTimeMillis();
        if (settings.esLiquidityWallHistoryFromSessionOpen()) {
            LocalDate tradeDate = EsLiquidityWallHistory.tradeDate(now);
            long from = EsLiquidityWallHistory.sessionStartMs(tradeDate);
            Map<TopicPartition, OffsetAndTimestamp> found = c.offsetsForTimes(Map.of(tp, from), Duration.ofSeconds(10));
            OffsetAndTimestamp at = found == null ? null : found.get(tp);
            if (at != null) {
                c.seek(tp, at.offset());
                history.seekOrigin("SESSION_OPEN", from);
                return;
            }
        }
        c.seekToEnd(List.of(tp));
        history.seekOrigin("LATEST", now);
    }

    /** One record from one of the three topics (the boundary of {@link EsLiquidityWallJson}); the history fold, the cache and the fan-out. */
    synchronized void accept(String topic, String key, Object value, long now) {
        if (value == GatewayAvroDeserializer.UNDECODABLE) {
            undecodable.incrementAndGet();
            return;
        }
        if (topic.equals(settings.esLiquidityWallDashboardTopic())) {
            String json = EsLiquidityWallJson.dashboard(key, value);
            if (json == null) {
                drops.incrementAndGet();
                return;
            }
            GenericRecord r = (GenericRecord) value;
            long bucketEnd = (Long) r.get("bucketEnd");
            history.foldDashboard(r);
            dashboardAccepted.incrementAndGet();
            if (!fresh(bucketEnd, now, settings.esLiquidityWallFreshMs())) {
                staleFrames.incrementAndGet();   // folded, never shown as live
                return;
            }
            dashboard = new Cached(json, bucketEnd);
            sink.accept(HEATMAP_EVENT, json);
        } else if (topic.equals(settings.esLiquidityWallEventsTopic())) {
            String json = EsLiquidityWallJson.event(key, value);
            if (json == null) {
                drops.incrementAndGet();
                return;
            }
            history.foldEvent((GenericRecord) value);
            eventsAccepted.incrementAndGet();
            wallRing.addLast(json);
            while (wallRing.size() > settings.esLiquidityWallEventRing()) {
                wallRing.removeFirst();
            }
            sink.accept(WALL_EVENT, json);
        } else if (topic.equals(settings.esLiquidityWallVoiceTopic())) {
            String json = EsLiquidityWallJson.voice(key, value);
            if (json == null) {
                drops.incrementAndGet();
                return;
            }
            long bucketEnd = (Long) ((GenericRecord) value).get("bucketEnd");
            voiceAccepted.incrementAndGet();
            voice = new Cached(json, bucketEnd);
            sink.accept(VOICE_EVENT, EsLiquidityWallJson.withStale(json, voiceStale(bucketEnd, now)));
        } else {
            drops.incrementAndGet();
        }
    }

    /** Within the bound of its bucket end and not ahead of the clock by more than the skew allowance. */
    static boolean fresh(long bucketEnd, long now, long freshMs) {
        return now - bucketEnd <= freshMs && bucketEnd - now <= FUTURE_SKEW_MS;
    }

    private boolean voiceStale(long bucketEnd, long now) {
        return now - bucketEnd > settings.esLiquidityWallVoiceFreshMs();
    }

    /** The connect replay: the fresh dashboard frame, the event ring in log order, the last voice record marked by its age now. */
    synchronized void replay(BiConsumer<String, String> send) {
        long now = System.currentTimeMillis();
        replay(send, now);
    }

    synchronized void replay(BiConsumer<String, String> send, long now) {
        Cached d = dashboard;
        if (d != null && fresh(d.bucketEnd(), now, settings.esLiquidityWallFreshMs())) {
            send.accept(HEATMAP_EVENT, d.json());
        }
        for (String json : wallRing) {
            send.accept(WALL_EVENT, json);
        }
        Cached v = voice;
        if (v != null) {
            send.accept(VOICE_EVENT, EsLiquidityWallJson.withStale(v.json(), voiceStale(v.bucketEnd(), now)));
        }
    }

    String metrics() {
        String p = "gateway_es_liquidity_wall_";
        return "# HELP " + p + "enabled Whether the ES liquidity-wall relay is enabled.\n# TYPE " + p + "enabled gauge\n" + p + "enabled " + (settings.esLiquidityWallRelayEnabled() ? 1 : 0) + "\n"
                + "# HELP " + p + "dashboard_accepted_total Dashboard records that passed the gateway boundary (folded into the projection).\n# TYPE " + p + "dashboard_accepted_total counter\n" + p + "dashboard_accepted_total " + dashboardAccepted.get() + "\n"
                + "# HELP " + p + "events_accepted_total Event records forwarded.\n# TYPE " + p + "events_accepted_total counter\n" + p + "events_accepted_total " + eventsAccepted.get() + "\n"
                + "# HELP " + p + "voice_accepted_total Voice records forwarded.\n# TYPE " + p + "voice_accepted_total counter\n" + p + "voice_accepted_total " + voiceAccepted.get() + "\n"
                + "# HELP " + p + "stale_frames_total Dashboard records folded but not broadcast because their bucket end was outside the freshness bound.\n# TYPE " + p + "stale_frames_total counter\n" + p + "stale_frames_total " + staleFrames.get() + "\n"
                + "# HELP " + p + "drops_total Records refused at the gateway boundary (wrong record, key, version, oversize, tombstone).\n# TYPE " + p + "drops_total counter\n" + p + "drops_total " + drops.get() + "\n"
                + "# HELP " + p + "undecodable_total Values the registry could not decode (not Confluent-framed, unknown schema id).\n# TYPE " + p + "undecodable_total counter\n" + p + "undecodable_total " + undecodable.get() + "\n"
                + "# HELP " + p + "consumer_failures_total Consumer loop failures (each followed by a 2 s retry).\n# TYPE " + p + "consumer_failures_total counter\n" + p + "consumer_failures_total " + consumerFailures.get() + "\n"
                + "# HELP " + p + "history_minutes Minutes held by the 60-second session projection.\n# TYPE " + p + "history_minutes gauge\n" + p + "history_minutes " + history.minuteCount() + "\n";
    }

    long dropsForTest() {
        return drops.get();
    }

    long staleFramesForTest() {
        return staleFrames.get();
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

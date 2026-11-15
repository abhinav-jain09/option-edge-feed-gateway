package app.feedgateway;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.avro.generic.GenericRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.PartitionInfo;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.errors.WakeupException;
import org.apache.kafka.common.serialization.Deserializer;
import org.springframework.stereotype.Component;

/**
 * The reader of the compacted scoreboard topics (liq-V design section 10): a read-only consumer without a group (assign, nothing committed) that reads each
 * topic from the BEGINNING and then tails it, into {@link ScorecardBoard}. A topic that does not exist yet (the emitting service has not rolled) is not an
 * error: the gateway starts, reports TOPIC_MISSING for it and looks again every {@link GatewaySettings#scorecardTopicRetryMs()}, bounded. No websocket frame,
 * no relay: the only consumer of the board is {@link ScorecardController}. Empty topic list = no thread, no consumer.
 */
@Component
class ScorecardReader {
    /** Fixed registry id of LevelAbsorptionScoreboard (liq-V design section 6). */
    static final int SCOREBOARD_ID = 2814;
    /** The fixed scoreboard schema id of each family's topic (liq-V design section 6): a frame carrying another id is not a scoreboard row of that topic. */
    static final Map<String, Integer> ALLOWED_ID = Map.of(
            "futures.level-absorption.scoreboard", SCOREBOARD_ID,
            "futures.liquidity-wall.scoreboard", 3410,
            "futures.line-health.scoreboard", 3712);

    private final GatewaySettings settings;
    private final List<String> topics;
    private final ScorecardBoard board = new ScorecardBoard();
    private final AtomicBoolean running = new AtomicBoolean();
    private volatile Deserializer<Object> decoder;
    private volatile KafkaConsumer<String, byte[]> consumer;
    private Thread thread;
    private final Map<TopicPartition, Long> resumeAt = new HashMap<>();

    @org.springframework.beans.factory.annotation.Autowired
    ScorecardReader(GatewaySettings settings) {
        this(settings, null);
    }

    /** Test seam: bytes are decoded by {@code decoder}. */
    ScorecardReader(GatewaySettings settings, Deserializer<Object> decoder) {
        this.settings = settings;
        this.decoder = decoder;
        this.topics = settings.scorecardScoreboardTopics();
        board.configure(topics);
    }

    ScorecardBoard board() {
        return board;
    }

    boolean off() {
        return topics.isEmpty();
    }

    @PostConstruct
    void start() {
        if (off()) {
            return;
        }
        running.set(true);
        thread = new Thread(this::run, "scorecard-reader");
        thread.setDaemon(true);
        thread.start();
    }

    @PreDestroy
    void stop() {
        running.set(false);
        KafkaConsumer<String, byte[]> c = consumer;
        if (c != null) {
            c.wakeup();
        }
    }

    private void run() {
        while (running.get()) {
            Properties p = new Properties();
            p.put("bootstrap.servers", settings.bootstrapServers());
            p.put("client.id", "scorecard-reader");
            p.put("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
            p.put("value.deserializer", "org.apache.kafka.common.serialization.ByteArrayDeserializer");
            p.put("enable.auto.commit", false);
            p.put("isolation.level", "read_committed");
            p.put("max.poll.records", 500);
            p.put("request.timeout.ms", 10000);
            p.put("default.api.timeout.ms", 10000);
            settings.applyKafkaSecurity(p);
            Map<String, Object> cfg = new HashMap<>();
            cfg.put("schema.registry.url", settings.schemaRegistryUrl());
            cfg.put("specific.avro.reader", false);
            cfg.put("http.connect.timeout.ms", 1000);
            cfg.put("http.read.timeout.ms", 1000);
            cfg.put("max.schemas.per.subject", 64);
            cfg.put(GatewayAvroDeserializer.SPX_PRICE_TOPIC, settings.underlyingSpxPriceTopic());
            cfg.put(GatewayAvroDeserializer.TOLERANT_TOPICS, String.join(",", topics));
            try (var c = new KafkaConsumer<String, byte[]>(p); GatewayAvroDeserializer d = new GatewayAvroDeserializer()) {
                d.configure(cfg, false);
                decoder = d;
                consumer = c;
                // a new consumer life rebuilds every topic from its earliest offset: a topic deleted and recreated while Kafka was unreachable
                // can have kept a valid-looking offset, so nothing read before is trusted (the rows come back within one read of the compacted topics)
                resumeAt.clear();
                board.resetAll("CONNECTING");
                loop(c);
            } catch (WakeupException stopped) {
                // stop() woke the poll
            } catch (RuntimeException unavailable) {
                board.setAllStates("RECOVERING");   // the rows held are stale until the next life has read the topics again
                System.out.println("WARN scorecard reader consumer failed, retrying in 5s: " + unavailable);
            } finally {
                consumer = null;
                decoder = null;
            }
            if (running.get()) {
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
    }

    private void loop(KafkaConsumer<String, byte[]> c) {
        Set<String> assigned = new HashSet<>();
        Map<String, Map<TopicPartition, Long>> fence = new HashMap<>();
        long nextLook = 0;
        while (running.get()) {
            long now = System.currentTimeMillis();
            if (now >= nextLook) {
                nextLook = now + settings.scorecardTopicRetryMs();
                Map<String, List<PartitionInfo>> existing = c.listTopics(Duration.ofSeconds(10));
                boolean changed = false;
                for (String t : topics) {
                    List<PartitionInfo> parts = existing.get(t);
                    if (parts != null && parts.size() > 1) {
                        if (assigned.remove(t)) {
                            changed = true;
                            fence.remove(t);
                        }
                        board.reset(t);
                        resumeAt.keySet().removeIf(tp -> tp.topic().equals(t));
                        board.setState(t, "PARTITIONS_INVALID");   // latest-per-key is only defined on one partition
                    } else if (parts == null || parts.isEmpty()) {
                        if (assigned.remove(t)) {
                            changed = true;
                            fence.remove(t);
                            board.reset(t);   // its old rows are not current data any more
                            resumeAt.keySet().removeIf(tp -> tp.topic().equals(t));   // a recreated topic starts again from its beginning
                        }
                        board.setState(t, "TOPIC_MISSING");
                    } else if (assigned.add(t)) {
                        changed = true;
                    }
                }
                if (changed) {
                    List<TopicPartition> all = new ArrayList<>();
                    for (String t : assigned) {
                        for (PartitionInfo pi : existing.get(t)) {
                            all.add(new TopicPartition(t, pi.partition()));
                        }
                    }
                    c.assign(all);
                    fence.keySet().removeIf(t -> !assigned.contains(t));
                    Map<TopicPartition, Long> ends = c.endOffsets(all, Duration.ofSeconds(10));
                    for (TopicPartition tp : all) {
                        Long resume = resumeAt.get(tp);
                        if (resume != null) {
                            c.seek(tp, resume);
                        } else {
                            c.seekToBeginning(List.of(tp));
                            fence.computeIfAbsent(tp.topic(), k -> new HashMap<>()).put(tp, ends.get(tp));
                            board.setState(tp.topic(), "LOADING");
                        }
                    }
                }
            }
            if (assigned.isEmpty()) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                continue;
            }
            org.apache.kafka.clients.consumer.ConsumerRecords<String, byte[]> polled;
            try {
                polled = c.poll(Duration.ofMillis(250));
            } catch (org.apache.kafka.clients.consumer.OffsetOutOfRangeException moved) {
                // the retained log no longer holds our position: forget it and rebuild the topic from its earliest offset (the consumer is recreated by run())
                for (TopicPartition tp : moved.partitions()) {
                    resumeAt.keySet().removeIf(x -> x.topic().equals(tp.topic()));
                    board.reset(tp.topic());
                    board.setState(tp.topic(), "LOADING");
                }
                throw moved;
            }
            for (var record : polled) {
                accept(record.topic(), record.key(), record.value());
                resumeAt.put(new TopicPartition(record.topic(), record.partition()), record.offset() + 1);
            }
            for (Map.Entry<String, Map<TopicPartition, Long>> e : fence.entrySet()) {
                boolean done = true;
                for (Map.Entry<TopicPartition, Long> f : e.getValue().entrySet()) {
                    if (c.position(f.getKey()) < f.getValue()) {
                        done = false;
                    }
                }
                if (done && assigned.contains(e.getKey())) {
                    board.setState(e.getKey(), "LIVE");
                }
            }
        }
    }

    /** One raw record: tombstone removes the key; a frame that is not id 2814 or does not decode is counted and never shown. */
    synchronized void accept(String topic, String key, byte[] value) {
        if (value == null) {
            board.remove(topic, key);
            return;
        }
        if (value.length < 5 || value[0] != 0 || ByteBuffer.wrap(value, 1, 4).getInt() != ALLOWED_ID.getOrDefault(topic, -1)) {
            board.countUndecodable(topic);
            return;
        }
        Deserializer<Object> d = decoder;
        Object decoded = d == null ? GatewayAvroDeserializer.UNDECODABLE : d.deserialize(topic, value);
        if (!(decoded instanceof GenericRecord r)) {
            board.countUndecodable(topic);
            return;
        }
        board.put(topic, key, r);
    }
}

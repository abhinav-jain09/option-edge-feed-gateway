package app.feedgateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.optionsedge.contracts.liquidityvalidator.LiquidityValidatorSchemas;
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
 * The liquidity-validator label relay (design/geometry-trade-rules/VALIDATOR-LABEL-ON-ORDER-PAGE-REQUIREMENT.md; liquidity-validator-service), modelled on
 * {@link EsLiquidityWallRelay}: one read-only tail of {@code futures.liquidity-validator.evaluations} per gateway replica (read_committed, partition 0, no consumer
 * group, nothing committed), fanned out through the existing authenticated sockets as the global event {@link #EVENT}. DISPLAY ONLY: the page shows a label next
 * to the geometry signal and speaks the sentence; nothing is hidden, filtered or ordered.
 * <p>
 * Boundary, in order, per record: the value's Confluent frame must carry the fixed schema id of the evaluation subject (3604,
 * {@link LiquidityValidatorSchemas#assertFramedAs}) BEFORE any decoding; the registry decodes (an unknown id or a corrupt body is
 * {@link GatewayAvroDeserializer#UNDECODABLE}, counted); then {@link #frame}'s record checks. The result is mapped to the label, the sentence and the tone HERE
 * (the page composes no text). A record is forwarded live only while its wall evaluation time is within the replay window of now; the session-open catch-up is
 * ringed and replayed to a connecting socket with {@code cached:true} and never spoken. {@code speak} is true only for a live record inside the voice window.
 * Disabled ({@code LIQUIDITY_VALIDATOR_RELAY_ENABLED=false}, the default): no thread, no consumer.
 */
@Component
final class LiquidityValidatorRelay {
    static final String EVENT = "liquidity-validator";
    static final long FUTURE_SKEW_MS = 5_000L;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private record Cached(String json, long evaluatedAtMs) {
    }

    private final GatewaySettings settings;
    private final BiConsumer<String, String> sink;
    private final AtomicBoolean running = new AtomicBoolean();
    private final AtomicLong accepted = new AtomicLong(), schemaIdRefused = new AtomicLong(), drops = new AtomicLong(), undecodable = new AtomicLong(), staleCatchUp = new AtomicLong(),
            consumerFailures = new AtomicLong();
    private final ArrayDeque<Cached> ring = new ArrayDeque<>();
    private volatile Deserializer<Object> decoder;
    private volatile KafkaConsumer<String, byte[]> consumer;
    private Thread thread;
    private long resumeAt = -1L;

    @org.springframework.beans.factory.annotation.Autowired
    LiquidityValidatorRelay(GatewaySettings settings, ObjectProvider<FeedGatewayService> gateway) {
        this(settings, (event, json) -> gateway.getObject().streamLiquidityValidator(event, json), null);
    }

    /** Test seam: the frames go to {@code sink}, the bytes are decoded by {@code decoder}. */
    LiquidityValidatorRelay(GatewaySettings settings, BiConsumer<String, String> sink, Deserializer<Object> decoder) {
        this.settings = settings;
        this.sink = sink;
        this.decoder = decoder;
    }

    @PostConstruct
    void start() {
        if (!settings.liquidityValidatorRelayEnabled()) {
            return;
        }
        running.set(true);
        thread = new Thread(this::run, "liquidity-validator-relay");
        thread.setDaemon(true);
        thread.start();
    }

    boolean consuming() {
        return running.get();
    }

    private void run() {
        while (running.get()) {
            String topic = settings.liquidityValidatorTopic();
            Properties p = new Properties();
            p.put("bootstrap.servers", settings.bootstrapServers());
            p.put("client.id", "liquidity-validator-relay");
            p.put("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
            p.put("value.deserializer", "org.apache.kafka.common.serialization.ByteArrayDeserializer");
            p.put("enable.auto.commit", false);
            p.put("isolation.level", "read_committed");
            p.put("max.poll.records", 500);
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
            decoderConfig.put(GatewayAvroDeserializer.TOLERANT_TOPICS, topic);
            try (var c = new KafkaConsumer<String, byte[]>(p); GatewayAvroDeserializer d = new GatewayAvroDeserializer()) {
                d.configure(decoderConfig, false);
                decoder = d;
                consumer = c;
                List<PartitionInfo> parts = c.partitionsFor(topic, Duration.ofSeconds(10));
                if (parts == null || parts.size() != 1) {
                    throw new IllegalStateException(topic + " must have exactly one partition, found " + (parts == null ? "none" : parts.size()));
                }
                TopicPartition tp = new TopicPartition(topic, 0);
                c.assign(List.of(tp));
                if (resumeAt >= 0) {
                    c.seek(tp, resumeAt);
                } else {
                    seekFromSessionOpen(c, tp);
                }
                while (running.get()) {
                    for (var record : c.poll(Duration.ofMillis(250))) {
                        accept(record.topic(), record.key(), record.value(), System.currentTimeMillis());
                        resumeAt = record.offset() + 1;
                    }
                }
            } catch (WakeupException stopped) {
                // stop() woke the poll
            } catch (RuntimeException unavailable) {
                consumerFailures.incrementAndGet();
                System.out.println("WARN liquidity-validator relay consumer failed, retrying in 2s: " + unavailable);
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

    /** From the current trade date's session open (18:00 ET of the previous day) so a restart refills the ring; else the end. */
    private void seekFromSessionOpen(Consumer<String, byte[]> c, TopicPartition tp) {
        long now = System.currentTimeMillis();
        LocalDate tradeDate = EsLiquidityWallHistory.tradeDate(now);
        long from = EsLiquidityWallHistory.sessionStartMs(tradeDate);
        Map<TopicPartition, OffsetAndTimestamp> found = c.offsetsForTimes(Map.of(tp, from), Duration.ofSeconds(10));
        OffsetAndTimestamp at = found == null ? null : found.get(tp);
        if (at != null) {
            c.seek(tp, at.offset());
        } else {
            c.seekToEnd(List.of(tp));
        }
    }

    /** One raw record: the schema id, the registry decode, the record boundary, the ring and the fan-out. A registry infrastructure failure propagates (re-read after the retry). */
    synchronized void accept(String topic, String key, byte[] value, long now) {
        if (!topic.equals(settings.liquidityValidatorTopic()) || value == null) {
            drops.incrementAndGet();   // a foreign topic or a tombstone
            return;
        }
        try {
            LiquidityValidatorSchemas.assertFramedAs(value, LiquidityValidatorSchemas.EVALUATION);
        } catch (IllegalStateException wrongId) {
            schemaIdRefused.incrementAndGet();
            return;
        }
        Deserializer<Object> d = decoder;
        Object decoded = d == null ? GatewayAvroDeserializer.UNDECODABLE : d.deserialize(topic, value);
        if (decoded == GatewayAvroDeserializer.UNDECODABLE) {
            undecodable.incrementAndGet();
            return;
        }
        long[] at = new long[1];
        ObjectNode node = frame(decoded, at);
        if (node == null) {
            drops.incrementAndGet();
            return;
        }
        accepted.incrementAndGet();
        boolean live = fresh(at[0], now, settings.liquidityValidatorReplayMs());
        boolean speak = live && fresh(at[0], now, settings.liquidityValidatorVoiceFreshMs());
        node.put("speak", speak);
        node.put("cached", false);
        String json = node.toString();
        ring.addLast(new Cached(json, at[0]));
        while (ring.size() > settings.liquidityValidatorRing()) {
            ring.removeFirst();
        }
        if (!live) {
            staleCatchUp.incrementAndGet();   // ringed for the connect replay, never broadcast as if it had just happened
            return;
        }
        sink.accept(EVENT, json);
    }

    static boolean fresh(long at, long now, long windowMs) {
        return now - at <= windowMs && at - now <= FUTURE_SKEW_MS;
    }

    /** The label table, the ONE place the words live (the page composes no text). {@code tone}: good = rising chime, warn = falling, neutral = one soft note. */
    record Wording(String label, String speech, String tone) {
    }

    static Wording wording(String result) {
        switch (result) {
            case "STRONG_CONTINUATION":
                return new Wording("Liquidity: strong continuation", "Liquidity confirms, strong continuation", "good");
            case "CONTINUATION":
                return new Wording("Liquidity: continuation", "Liquidity supports the break", "good");
            case "WEAKENING":
                return new Wording("Liquidity: weakening", "Liquidity weakening", "warn");
            case "FAILURE_LIKELY":
                return new Wording("Liquidity: failure likely", "Liquidity says failure likely", "warn");
            case "LATE_SIGNAL":
                return new Wording("Liquidity: late signal", "Late signal, the move is already extended", "warn");
            case "UNCERTAIN":
                return new Wording("Liquidity: uncertain", "Liquidity uncertain", "neutral");
            default:
                return null;   // an unknown result is refused, not guessed
        }
    }

    /** The page frame of a decoded evaluation, or null when the record fails the boundary. {@code at[0]} receives the wall evaluation time. */
    static ObjectNode frame(Object decoded, long[] at) {
        if (!(decoded instanceof GenericRecord r)) {
            return null;
        }
        String annotationId = text(r, "annotationId"), result = text(r, "result"), tf = text(r, "timeframe"), evaluationId = text(r, "evaluationId");
        Wording w = wording(result);
        Object evaluated = r.get("evaluatedAtWallMs"), released = r.get("releaseVisibleAtMs");
        if (w == null || annotationId.isBlank() || evaluationId.isBlank() || tf.isBlank() || !(evaluated instanceof Long) || !(released instanceof Long)) {
            return null;
        }
        at[0] = (Long) evaluated;
        ObjectNode n = MAPPER.createObjectNode();
        n.put("type", EVENT);
        n.put("evaluationId", evaluationId);
        n.put("tradeDate", text(r, "tradeDate"));
        n.put("annotationId", annotationId);
        n.put("patternId", text(r, "patternId"));
        n.put("tf", tf);
        n.put("direction", text(r, "direction"));
        n.put("releaseVisibleAtMs", (Long) released);
        n.put("evaluatedAtWallMs", at[0]);
        n.put("result", result);
        ArrayNode reasons = n.putArray("resultReasons");
        if (r.get("resultReasons") instanceof List<?> rs) {
            rs.forEach(x -> reasons.add(String.valueOf(x)));
        }
        n.put("label", w.label());
        n.put("speech", w.speech());
        n.put("tone", w.tone());
        return n;
    }

    private static String text(GenericRecord r, String field) {
        Object v = r.getSchema().getField(field) == null ? null : r.get(field);
        return v == null ? "" : v.toString();
    }

    /** The connect replay: the ring's records inside the replay window, in log order, every one marked cached and never spoken. */
    synchronized void replay(BiConsumer<String, String> send) {
        replay(send, System.currentTimeMillis());
    }

    synchronized void replay(BiConsumer<String, String> send, long now) {
        for (Cached e : ring) {
            try {
                ObjectNode n = (ObjectNode) MAPPER.readTree(e.json());
                n.put("cached", true);
                n.put("speak", false);
                send.accept(EVENT, n.toString());
            } catch (java.io.IOException unreadable) {
                drops.incrementAndGet();
            }
        }
    }

    String metrics() {
        String p = "gateway_liquidity_validator_";
        StringBuilder sb = new StringBuilder();
        sb.append("# TYPE ").append(p).append("enabled gauge\n").append(p).append("enabled ").append(settings.liquidityValidatorRelayEnabled() ? 1 : 0).append('\n');
        sb.append("# TYPE ").append(p).append("accepted_total counter\n").append(p).append("accepted_total ").append(accepted.get()).append('\n');
        sb.append("# TYPE ").append(p).append("schema_id_refused_total counter\n").append(p).append("schema_id_refused_total ").append(schemaIdRefused.get()).append('\n');
        sb.append("# TYPE ").append(p).append("drops_total counter\n").append(p).append("drops_total ").append(drops.get()).append('\n');
        sb.append("# TYPE ").append(p).append("undecodable_total counter\n").append(p).append("undecodable_total ").append(undecodable.get()).append('\n');
        sb.append("# TYPE ").append(p).append("catch_up_total counter\n").append(p).append("catch_up_total ").append(staleCatchUp.get()).append('\n');
        sb.append("# TYPE ").append(p).append("consumer_failures_total counter\n").append(p).append("consumer_failures_total ").append(consumerFailures.get()).append('\n');
        return sb.toString();
    }

    long dropsForTest() {
        return drops.get();
    }

    long schemaIdRefusedForTest() {
        return schemaIdRefused.get();
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

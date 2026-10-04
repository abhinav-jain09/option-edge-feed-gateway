package app.feedgateway.contexttape;

import app.feedgateway.GatewaySettings;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;

/**
 * Materializes the ES-machine challenger's mirrored current topic in dev/prod.
 * Classification never runs here: this component is a strict, read-only projection relay.
 */
public final class MirroredEsCompressionStore implements EsCompressionSource, AutoCloseable {
    static final String ARTIFACT_SHA =
            "9b59499f2db8651262992cea9107d3333eeede3af46e5920c4e6e283a9a6e3f3";
    private static final int MAX_BYTES = 1 << 20;
    private static final long MAX_FUTURE_MS = 60_000L;

    private record Snapshot(byte[] body, long generatedAtMs, boolean ready) { }

    private final GatewaySettings settings;
    private final ObjectMapper mapper;
    private final AtomicLong accepted = new AtomicLong();
    private final AtomicLong rejected = new AtomicLong();
    private volatile Snapshot snapshot;
    private volatile boolean running;
    private volatile KafkaConsumer<String, String> activeConsumer;
    private Thread thread;

    public MirroredEsCompressionStore(GatewaySettings settings, ObjectMapper mapper) {
        this.settings = settings;
        this.mapper = mapper == null ? new ObjectMapper() : mapper;
        if (settings.enabled() && settings.zeroDteEsChallengerMirrorEnabled()) start();
    }

    private void start() {
        running = true;
        thread = new Thread(this::run, "zero-dte-es-mirror-store");
        thread.setDaemon(true);
        thread.start();
    }

    private void run() {
        long backoffMs = 1_000L;
        while (running) {
            try (KafkaConsumer<String, String> consumer = consumer()) {
                activeConsumer = consumer;
                var infos = consumer.partitionsFor(settings.zeroDteEsChallengerMirrorTopic(),
                        Duration.ofSeconds(10));
                if (infos == null || infos.size() != 1) {
                    throw new IllegalStateException("ES challenger mirror topic must have exactly one partition");
                }
                TopicPartition partition = new TopicPartition(
                        settings.zeroDteEsChallengerMirrorTopic(), infos.getFirst().partition());
                consumer.assign(List.of(partition));
                consumer.seekToBeginning(List.of(partition));
                backoffMs = 1_000L;
                while (running) {
                    for (ConsumerRecord<String, String> record : consumer.poll(Duration.ofMillis(500))) {
                        accept(record, System.currentTimeMillis());
                    }
                }
            } catch (org.apache.kafka.common.errors.WakeupException stopped) {
                if (running) rejected.incrementAndGet();
            } catch (RuntimeException failure) {
                rejected.incrementAndGet();
                if (!running) break;
                try {
                    Thread.sleep(backoffMs);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    break;
                }
                backoffMs = Math.min(30_000L, backoffMs * 2);
            } finally {
                activeConsumer = null;
            }
        }
    }

    void accept(ConsumerRecord<String, String> record, long nowMs) {
        if (record == null
                || !settings.zeroDteEsChallengerMirrorTopic().equals(record.topic())
                || record.partition() != 0
                || !"SPX|ES_ANALYSIS".equals(record.key())) {
            rejected.incrementAndGet();
            return;
        }
        if (record.value() == null) {
            snapshot = null;
            rejected.incrementAndGet();
            return;
        }
        byte[] body = record.value().getBytes(StandardCharsets.UTF_8);
        if (body.length == 0 || body.length > MAX_BYTES) {
            rejected.incrementAndGet();
            return;
        }
        try {
            JsonNode node = mapper.readTree(body);
            long generatedAtMs = node.path("generatedAtMs").asLong(0L);
            boolean valid = node.isObject()
                    && "zdce.es-challenger-view.1".equals(node.path("schemaVersion").asText())
                    && "zero-dte-es-challenger-service".equals(node.path("service").asText())
                    && "zdce-es-challenger-v1".equals(node.path("modelVersion").asText())
                    && "SHADOW_NOT_FOR_TRADING".equals(node.path("authority").asText())
                    && "ES_ANALYSIS".equals(node.path("source").asText())
                    && "PRICE_VOLUME_ONLY".equals(node.path("capability").asText())
                    && ARTIFACT_SHA.equals(node.path("artifactSha256").asText())
                    && node.path("points").isArray()
                    && generatedAtMs > 0 && generatedAtMs <= nowMs + MAX_FUTURE_MS;
            if (!valid) {
                rejected.incrementAndGet();
                return;
            }
            Snapshot prior = snapshot;
            if (prior != null && generatedAtMs < prior.generatedAtMs()) {
                rejected.incrementAndGet();
                return;
            }
            snapshot = new Snapshot(body, generatedAtMs, node.path("ready").asBoolean(false));
            accepted.incrementAndGet();
        } catch (Exception malformed) {
            rejected.incrementAndGet();
        }
    }

    @Override public ContextTapeUpstream.SessionResponse esCompression() {
        Snapshot current = snapshot;
        long now = System.currentTimeMillis();
        if (!settings.zeroDteEsChallengerMirrorEnabled() || current == null) {
            return unavailable("MIRROR_WARMING");
        }
        if (!current.ready()) return unavailable("SOURCE_WARMING");
        if (now - current.generatedAtMs() > settings.zeroDteEsChallengerMirrorMaxAgeMs()) {
            return unavailable("MIRROR_STALE");
        }
        return new ContextTapeUpstream.SessionResponse(200, "application/json", null,
                current.body().clone());
    }

    private static ContextTapeUpstream.SessionResponse unavailable(String state) {
        byte[] body = ("{\"error\":\"WARMING\",\"state\":\"" + state + "\"}")
                .getBytes(StandardCharsets.UTF_8);
        return new ContextTapeUpstream.SessionResponse(503, "application/json", "5", body);
    }

    private KafkaConsumer<String, String> consumer() {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, settings.bootstrapServers());
        props.put(ConsumerConfig.CLIENT_ID_CONFIG, "zero-dte-es-mirror-store-" + UUID.randomUUID());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "zero-dte-es-mirror-store");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ISOLATION_LEVEL_CONFIG, "read_committed");
        props.put(ConsumerConfig.MAX_PARTITION_FETCH_BYTES_CONFIG, Integer.toString(MAX_BYTES));
        settings.applyKafkaSecurity(props);
        return new KafkaConsumer<>(props);
    }

    @Override public long acceptedCount() { return accepted.get(); }
    @Override public long rejectedCount() { return rejected.get(); }

    @Override public void close() {
        running = false;
        KafkaConsumer<String, String> consumer = activeConsumer;
        if (consumer != null) consumer.wakeup();
        if (thread != null) {
            thread.interrupt();
            try {
                thread.join(5_000L);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }
}

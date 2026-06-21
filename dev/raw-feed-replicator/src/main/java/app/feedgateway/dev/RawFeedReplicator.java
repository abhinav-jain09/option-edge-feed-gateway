package app.feedgateway.dev;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.PartitionInfo;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.ByteArraySerializer;

/**
 * Read-only raw-feed replicator (OE multi-tenant local test harness).
 *
 * <p>Copies a configurable set of topics from a SOURCE (production) Kafka into a DEST (local) Kafka,
 * byte-for-byte (key + value + headers + timestamp). It is <b>provably read-only against the
 * source</b>:
 * <ul>
 *   <li>uses manual {@code assign()} — never {@code subscribe()} — so it joins no consumer group;</li>
 *   <li>{@code enable.auto.commit=false} and it never commits — nothing is written to the source's
 *       {@code __consumer_offsets};</li>
 *   <li>it only ever produces to the DEST cluster.</li>
 * </ul>
 *
 * <p>Config via env: SOURCE_BOOTSTRAP, DEST_BOOTSTRAP, TOPICS (comma-sep), BACKFILL (recent msgs per
 * partition to seed, default 10), RUN_SECONDS (0 = run forever).
 */
public final class RawFeedReplicator {

    public static void main(String[] args) throws Exception {
        String source = env("SOURCE_BOOTSTRAP", "192.168.100.252:9092,192.168.100.252:9094,192.168.100.252:9096");
        String dest = env("DEST_BOOTSTRAP", "localhost:19092");
        List<String> topics = List.of(env("TOPICS", "dev.options.databento.raw").split(","));
        int backfill = Integer.parseInt(env("BACKFILL", "10"));
        long runSeconds = Long.parseLong(env("RUN_SECONDS", "0"));

        log("source=" + source + " dest=" + dest + " topics=" + topics + " backfill=" + backfill
                + " runSeconds=" + (runSeconds == 0 ? "forever" : runSeconds));

        ensureDestTopics(source, dest, topics);

        try (KafkaConsumer<byte[], byte[]> consumer = new KafkaConsumer<>(consumerProps(source));
             KafkaProducer<byte[], byte[]> producer = new KafkaProducer<>(producerProps(dest))) {

            // Manual assignment of all partitions of all topics — NO group, NO commits.
            List<TopicPartition> assignment = new ArrayList<>();
            for (String topic : topics) {
                List<PartitionInfo> parts = consumer.partitionsFor(topic);
                if (parts == null || parts.isEmpty()) {
                    log("WARN: source topic not found or has no partitions: " + topic);
                    continue;
                }
                for (PartitionInfo p : parts) {
                    assignment.add(new TopicPartition(topic, p.partition()));
                }
            }
            consumer.assign(assignment);

            // Seed from (end - backfill) per partition so we get recent data immediately, then tail.
            Map<TopicPartition, Long> end = consumer.endOffsets(assignment);
            Map<TopicPartition, Long> begin = consumer.beginningOffsets(assignment);
            for (TopicPartition tp : assignment) {
                long start = Math.max(begin.get(tp), end.get(tp) - backfill);
                consumer.seek(tp, start);
            }
            log("assigned " + assignment.size() + " partitions; tailing for new records...");

            long deadline = runSeconds == 0 ? Long.MAX_VALUE : System.currentTimeMillis() + runSeconds * 1000L;
            long copied = 0;
            while (System.currentTimeMillis() < deadline) {
                ConsumerRecords<byte[], byte[]> records = consumer.poll(Duration.ofMillis(500));
                for (ConsumerRecord<byte[], byte[]> r : records) {
                    ProducerRecord<byte[], byte[]> out = new ProducerRecord<>(
                            r.topic(), null, r.timestamp(), r.key(), r.value(), r.headers());
                    producer.send(out);
                    copied++;
                }
                if (!records.isEmpty()) {
                    producer.flush();
                    log("copied so far: " + copied);
                }
            }
            producer.flush();
            log("DONE. total copied=" + copied);
        }
    }

    private static void ensureDestTopics(String source, String dest, List<String> topics) throws Exception {
        Map<String, Integer> partitionCounts = new HashMap<>();
        try (Admin srcAdmin = Admin.create(adminProps(source))) {
            var described = srcAdmin.describeTopics(topics).allTopicNames().get();
            described.forEach((t, d) -> partitionCounts.put(t, d.partitions().size()));
        }
        try (Admin destAdmin = Admin.create(adminProps(dest))) {
            Set<String> existing = destAdmin.listTopics().names().get();
            List<NewTopic> toCreate = new ArrayList<>();
            for (String t : topics) {
                if (existing.contains(t)) {
                    continue;
                }
                int parts = partitionCounts.getOrDefault(t, 1);
                toCreate.add(new NewTopic(t, parts, (short) 1));
            }
            if (!toCreate.isEmpty()) {
                destAdmin.createTopics(toCreate).all().get();
                toCreate.forEach(nt -> log("created dest topic " + nt.name() + " (" + nt.numPartitions() + "p)"));
            }
        }
    }

    private static Properties consumerProps(String bootstrap) {
        Properties p = new Properties();
        p.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap);
        p.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class.getName());
        p.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class.getName());
        p.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false"); // read-only: never commit
        p.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, "2000");
        p.put(ConsumerConfig.CLIENT_ID_CONFIG, "raw-feed-replicator");
        return p;
    }

    private static Properties producerProps(String bootstrap) {
        Properties p = new Properties();
        p.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap);
        p.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class.getName());
        p.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class.getName());
        p.put(ProducerConfig.ACKS_CONFIG, "1");
        p.put(ProducerConfig.LINGER_MS_CONFIG, "20");
        p.put(ProducerConfig.CLIENT_ID_CONFIG, "raw-feed-replicator");
        return p;
    }

    private static Properties adminProps(String bootstrap) {
        Properties p = new Properties();
        p.put("bootstrap.servers", bootstrap);
        p.put("default.api.timeout.ms", "15000");
        p.put("request.timeout.ms", "15000");
        return p;
    }

    private static String env(String key, String fallback) {
        String v = System.getenv(key);
        return v == null || v.isBlank() ? fallback : v;
    }

    private static void log(String msg) {
        System.out.println("[raw-feed-replicator] " + msg);
    }

    private RawFeedReplicator() {
    }
}

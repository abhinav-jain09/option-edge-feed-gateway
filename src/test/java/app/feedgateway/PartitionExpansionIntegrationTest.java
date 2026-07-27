package app.feedgateway;

import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewPartitions;
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
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Proves against a REAL broker the Kafka-client behaviours the partition-growth mechanism is built on.
 *
 * <p>Every unit test around this feature exercises our own helpers with mocked consumers, which can only
 * confirm that our code does what we wrote — never that Kafka does what we assumed. The 4→32 incident was
 * caused by exactly such an assumption (that a manually assigned consumer would notice new partitions).
 * The four assumptions below are load-bearing; if any is false in a future client version, this test fails
 * instead of a trading day.
 *
 * <ol>
 *   <li>A manually assigned consumer NEVER picks up new partitions on its own — the premise of the feature.</li>
 *   <li>{@code partitionsFor()} on a KNOWN topic answers from a cache bounded by {@code metadata.max.age.ms},
 *       so pinning that config is what makes the refresh interval mean anything.</li>
 *   <li>{@code assign()} PRESERVES the fetch position of partitions already assigned — the union-assignment
 *       fix would silently rewind every healthy partition if this were false.</li>
 *   <li>A partition created by expanding a topic starts EMPTY, so {@code seekToBeginning} on it recovers
 *       exactly the records written since creation — the offset-based recovery that replaced a
 *       clock-dependent {@code offsetsForTimes} seek.</li>
 * </ol>
 */
@Testcontainers
class PartitionExpansionIntegrationTest {

    @Container
    private static final KafkaContainer KAFKA =
            new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.1"));

    private static final String TOPIC = "partition-expansion-probe";
    private static final Duration POLL = Duration.ofMillis(250);

    @Test
    void manualAssignmentIgnoresGrowthUntilMetadataIsRefreshedAndNewPartitionsStartEmpty() throws Exception {
        String bootstrap = KAFKA.getBootstrapServers();
        long refreshMs = 2_000L;

        try (Admin admin = Admin.create(Map.of(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap))) {
            admin.createTopics(List.of(new NewTopic(TOPIC, 2, (short) 1))).all().get();

            try (KafkaProducer<String, String> producer = producer(bootstrap);
                 KafkaConsumer<String, String> consumer = consumer(bootstrap, refreshMs)) {

                // --- bootstrap exactly as the gateway does: resolve metadata, assign manually ---
                List<TopicPartition> assigned = partitionsOf(consumer);
                assertEquals(2, assigned.size(), "topic starts with 2 partitions");
                consumer.assign(assigned);
                consumer.seekToBeginning(assigned);

                producer.send(new ProducerRecord<>(TOPIC, 0, "k0", "before-expansion-p0")).get();
                producer.send(new ProducerRecord<>(TOPIC, 1, "k1", "before-expansion-p1")).get();
                Map<TopicPartition, List<String>> before = drain(consumer, 2);
                assertEquals(2, before.values().stream().mapToInt(List::size).sum(),
                        "both pre-expansion records are consumed");

                long p0PositionBeforeGrowth = consumer.position(new TopicPartition(TOPIC, 0));

                // --- the incident: the producer expands the topic underneath a manually assigned consumer ---
                admin.createPartitions(Map.of(TOPIC, NewPartitions.increaseTo(4))).all().get();
                producer.send(new ProducerRecord<>(TOPIC, 2, "k2", "after-expansion-p2")).get();
                producer.send(new ProducerRecord<>(TOPIC, 3, "k3", "after-expansion-p3")).get();

                // (1) Manual assignment does NOT self-heal. This is the whole reason the feature exists;
                // if this ever stops being true, the periodic refresh becomes dead code.
                assertEquals(Set.copyOf(assigned), consumer.assignment(),
                        "a manually assigned consumer must never absorb new partitions on its own");
                ConsumerRecords<String, String> blind = consumer.poll(POLL);
                assertTrue(blind.isEmpty(),
                        "records on partitions 2-3 are invisible while the assignment is stale — the incident");

                // (2) partitionsFor() eventually reflects growth, bounded by metadata.max.age.ms.
                List<TopicPartition> discovered = awaitDiscovery(consumer, 4, refreshMs * 5);
                assertEquals(4, discovered.size(),
                        "metadata.max.age.ms bounds how soon a known topic's growth becomes visible");

                // --- apply the union assignment exactly as PartitionRefresh does ---
                List<TopicPartition> added = FeedGatewayService.addedPartitions(assigned, discovered);
                assertEquals(List.of(new TopicPartition(TOPIC, 2), new TopicPartition(TOPIC, 3)), added,
                        "only the genuinely new partitions are added");
                List<TopicPartition> merged = FeedGatewayService.mergedAssignment(assigned, added);
                consumer.assign(merged);

                // (3) assign() preserves positions of partitions already in the assignment. If Kafka reset
                // them, the union fix would silently rewind (or skip) every healthy partition on every
                // expansion.
                assertEquals(p0PositionBeforeGrowth, consumer.position(new TopicPartition(TOPIC, 0)),
                        "re-assigning the union must not disturb an existing partition's fetch position");

                // (4) A grown partition starts EMPTY, so beginning == the exact set of missed records.
                // This is what let the clock-dependent offsetsForTimes recovery be deleted.
                Map<TopicPartition, Long> beginnings = consumer.beginningOffsets(added);
                for (TopicPartition partition : added) {
                    assertEquals(0L, beginnings.get(partition),
                            "a partition created by expansion begins at offset 0 — nothing predates it");
                }
                consumer.seekToBeginning(added);

                Map<TopicPartition, List<String>> recovered = drain(consumer, 2);
                assertEquals(List.of("after-expansion-p2"), recovered.get(new TopicPartition(TOPIC, 2)),
                        "the record written to the new partition before discovery is recovered, not lost");
                assertEquals(List.of("after-expansion-p3"), recovered.get(new TopicPartition(TOPIC, 3)),
                        "the record written to the new partition before discovery is recovered, not lost");
                assertFalse(recovered.containsKey(new TopicPartition(TOPIC, 0)),
                        "recovery must not replay the pre-existing partitions");
            }
        }
    }

    /** Poll until {@code expected} records have been read or the budget expires; group them by partition. */
    private static Map<TopicPartition, List<String>> drain(KafkaConsumer<String, String> consumer, int expected) {
        Map<TopicPartition, List<String>> byPartition = new HashMap<>();
        long deadline = System.currentTimeMillis() + 30_000L;
        int seen = 0;
        while (seen < expected && System.currentTimeMillis() < deadline) {
            for (ConsumerRecord<String, String> record : consumer.poll(POLL)) {
                byPartition.computeIfAbsent(new TopicPartition(record.topic(), record.partition()),
                        k -> new ArrayList<>()).add(record.value());
                seen++;
            }
        }
        assertEquals(expected, seen, "expected exactly " + expected + " records within the budget");
        return byPartition;
    }

    /** Wait for the client's metadata to reflect {@code expected} partitions, mirroring PartitionRefresh. */
    private static List<TopicPartition> awaitDiscovery(KafkaConsumer<String, String> consumer,
                                                       int expected, long budgetMs) {
        long deadline = System.currentTimeMillis() + budgetMs;
        List<TopicPartition> latest = List.of();
        while (System.currentTimeMillis() < deadline) {
            latest = partitionsOf(consumer);
            if (latest.size() >= expected) {
                return latest;
            }
            consumer.poll(POLL); // keep the client alive so it refreshes metadata on its own schedule
        }
        return latest;
    }

    private static List<TopicPartition> partitionsOf(KafkaConsumer<String, String> consumer) {
        List<PartitionInfo> info = consumer.partitionsFor(TOPIC, Duration.ofSeconds(10));
        assertNotNull(info, "topic metadata must resolve");
        return info.stream()
                .map(p -> new TopicPartition(p.topic(), p.partition()))
                .sorted(Comparator.comparingInt(TopicPartition::partition))
                .toList();
    }

    private static KafkaConsumer<String, String> consumer(String bootstrap, long metadataMaxAgeMs) {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "partition-expansion-probe");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");
        // The production setting under test: without this the client would answer partitionsFor() from a
        // cache up to 5 minutes stale, and the gateway's refresh interval would bound nothing.
        props.put(ConsumerConfig.METADATA_MAX_AGE_CONFIG, Long.toString(metadataMaxAgeMs));
        return new KafkaConsumer<>(props);
    }

    private static KafkaProducer<String, String> producer(String bootstrap) {
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        return new KafkaProducer<>(props);
    }
}

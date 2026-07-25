package app.feedgateway.systemstatus;

import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.ListOffsetsResult;
import org.apache.kafka.clients.admin.OffsetSpec;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

/**
 * Per-service consumer lag for the System Status page (design §3.5).
 *
 * <p>Lag is computed against an ALLOWLISTED registry of {@code service → consumer group → input topics}
 * — not "whatever groups exist" — and every EXPECTED partition of those topics is classified, because a
 * group that has committed on only some partitions must not read as healthy:
 *
 * <ul>
 *   <li>{@code OK} — valid, non-negative {@code end − committed};</li>
 *   <li>{@code NO_COMMIT} — the partition exists but the group has no committed offset;</li>
 *   <li>{@code OFFSET_ANOMALY} — committed beyond the end offset, or negative;</li>
 *   <li>{@code UNKNOWN} — the fetch itself failed.</li>
 * </ul>
 *
 * Aggregates (sum/max) come from {@code OK} partitions ONLY, and the service-level status degrades to
 * {@code PARTIAL_NO_COMMITS} / {@code OFFSET_ANOMALY} / {@code UNKNOWN} so a number is never presented as
 * more complete than it is. Results are cached briefly: the page polls every 2 minutes and this must never
 * become a per-request load on the broker.
 */
public class ConsumerLagReader implements AutoCloseable {

    /** service → (consumer group, input topics). Config-driven; see GatewaySettings#systemStatusLagRegistry. */
    public record Entry(String service, String group, List<String> topics) { }

    private final String bootstrapServers;
    private final List<Entry> registry;
    private final long cacheMillis;
    private final int timeoutMs;

    private volatile long cachedAt;
    private volatile List<Map<String, Object>> cached = List.of();
    private volatile String cachedError;
    private volatile String lastSuccessAt;

    public ConsumerLagReader(String bootstrapServers, List<Entry> registry, long cacheMillis, int timeoutMs) {
        this.bootstrapServers = bootstrapServers;
        this.registry = registry == null ? List.of() : registry;
        this.cacheMillis = Math.max(1_000L, cacheMillis);
        this.timeoutMs = Math.max(1_000, timeoutMs);
    }

    public boolean configured() {
        return bootstrapServers != null && !bootstrapServers.isBlank() && !registry.isEmpty();
    }

    /** Cached snapshot; a refresh failure keeps last-good numbers but marks every row non-OK. */
    public synchronized List<Map<String, Object>> services() {
        long now = System.currentTimeMillis();
        if (now - cachedAt < cacheMillis && !cached.isEmpty()) {
            return cached;
        }
        try {
            cached = compute();
            cachedError = null;
            lastSuccessAt = java.time.Instant.now().toString();
        } catch (Exception e) {
            cachedError = e.getClass().getSimpleName() + ": " + trim(e.getMessage());
            // Never serve stale numbers as current: mark them UNKNOWN but keep the values visible.
            List<Map<String, Object>> degraded = new ArrayList<>();
            for (Map<String, Object> row : cached) {
                Map<String, Object> copy = new LinkedHashMap<>(row);
                copy.put("lagStatus", "UNKNOWN");
                copy.put("errorCode", cachedError);
                degraded.add(copy);
            }
            if (degraded.isEmpty()) {
                for (Entry e2 : registry) {
                    degraded.add(unknownRow(e2, cachedError));
                }
            }
            cached = degraded;
        }
        cachedAt = now;
        return cached;
    }

    public String error() {
        return cachedError;
    }

    private Map<String, Object> unknownRow(Entry e, String error) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("service", e.service());
        row.put("consumerGroup", e.group());
        row.put("lagStatus", "UNKNOWN");
        row.put("lagRecordsSum", null);
        row.put("lagRecordsMax", null);
        row.put("okPartitions", 0);
        row.put("expectedPartitions", null);
        row.put("computedAt", java.time.Instant.now().toString());
        row.put("lastSuccessfulAt", lastSuccessAt);
        row.put("errorCode", error);
        return row;
    }

    private List<Map<String, Object>> compute() throws Exception {
        Properties props = new Properties();
        props.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, timeoutMs);
        props.put(AdminClientConfig.DEFAULT_API_TIMEOUT_MS_CONFIG, timeoutMs);
        props.put(AdminClientConfig.CLIENT_ID_CONFIG, "feed-gateway-system-status");
        List<Map<String, Object>> rows = new ArrayList<>();
        try (Admin admin = Admin.create(props)) {
            Map<String, org.apache.kafka.clients.admin.TopicDescription> described = admin
                    .describeTopics(registry.stream().flatMap(e -> e.topics().stream()).distinct().toList())
                    .allTopicNames().get(timeoutMs, TimeUnit.MILLISECONDS);
            for (Entry entry : registry) {
                rows.add(lagFor(admin, entry, described));
            }
        }
        return rows;
    }

    private Map<String, Object> lagFor(Admin admin, Entry entry,
                                       Map<String, org.apache.kafka.clients.admin.TopicDescription> described)
            throws Exception {
        // Every CURRENT partition of the registered input topics is expected — not merely those the
        // group happens to have committed (that is how a half-committed group reads as healthy).
        List<TopicPartition> expected = new ArrayList<>();
        for (String topic : entry.topics()) {
            var desc = described.get(topic);
            if (desc == null) {
                continue;
            }
            desc.partitions().forEach(p -> expected.add(new TopicPartition(topic, p.partition())));
        }
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("service", entry.service());
        row.put("consumerGroup", entry.group());
        row.put("expectedPartitions", expected.size());
        row.put("computedAt", java.time.Instant.now().toString());
        if (expected.isEmpty()) {
            row.put("lagStatus", "UNKNOWN");
            row.put("lagRecordsSum", null);
            row.put("lagRecordsMax", null);
            row.put("okPartitions", 0);
            row.put("errorCode", "no partitions for registered input topics");
            row.put("lastSuccessfulAt", lastSuccessAt);
            return row;
        }

        Map<TopicPartition, OffsetAndMetadata> committed = admin
                .listConsumerGroupOffsets(entry.group()).partitionsToOffsetAndMetadata()
                .get(timeoutMs, TimeUnit.MILLISECONDS);
        Map<TopicPartition, OffsetSpec> specs = new HashMap<>();
        expected.forEach(tp -> specs.put(tp, OffsetSpec.latest()));
        Map<TopicPartition, ListOffsetsResult.ListOffsetsResultInfo> ends = admin
                .listOffsets(specs).all().get(timeoutMs, TimeUnit.MILLISECONDS);

        long sum = 0L;
        long max = 0L;
        int ok = 0;
        int noCommit = 0;
        int anomaly = 0;
        for (TopicPartition tp : expected) {
            OffsetAndMetadata c = committed == null ? null : committed.get(tp);
            var end = ends.get(tp);
            if (c == null) {
                noCommit++;
                continue;
            }
            if (end == null) {
                continue;
            }
            long lag = end.offset() - c.offset();
            if (lag < 0 || c.offset() < 0) {
                anomaly++;
                continue;
            }
            ok++;
            sum += lag;
            max = Math.max(max, lag);
        }
        String status;
        if (anomaly > 0) {
            status = "OFFSET_ANOMALY";
        } else if (ok == 0) {
            status = "NO_COMMITS";
        } else if (noCommit > 0) {
            status = "PARTIAL_NO_COMMITS";
        } else {
            status = "OK";
        }
        row.put("lagStatus", status);
        // Aggregates over OK partitions ONLY; null when there is nothing valid to aggregate.
        row.put("lagRecordsSum", ok == 0 ? null : sum);
        row.put("lagRecordsMax", ok == 0 ? null : max);
        row.put("okPartitions", ok);
        row.put("noCommitPartitions", noCommit);
        row.put("anomalyPartitions", anomaly);
        row.put("lastSuccessfulAt", lastSuccessAt);
        return row;
    }

    private static String trim(String s) {
        if (s == null) {
            return "";
        }
        return s.length() > 200 ? s.substring(0, 200) : s;
    }

    @Override
    public void close() {
        // Admin clients are created per refresh and closed in compute(); nothing long-lived to release.
    }

    /** Parse {@code service:group:topic1,topic2;service2:group2:topicA} into registry entries. */
    public static List<Entry> parseRegistry(String raw) {
        List<Entry> out = new ArrayList<>();
        if (raw == null || raw.isBlank()) {
            return out;
        }
        for (String chunk : raw.split(";")) {
            String[] parts = chunk.trim().split(":");
            if (parts.length != 3 || parts[0].isBlank() || parts[1].isBlank()) {
                continue;
            }
            List<String> topics = new ArrayList<>();
            for (String t : parts[2].split(",")) {
                if (!t.isBlank()) {
                    topics.add(t.trim());
                }
            }
            if (!topics.isEmpty()) {
                out.add(new Entry(parts[0].trim(), parts[1].trim(), topics));
            }
        }
        return out;
    }

    static Duration timeout(int ms) {
        return Duration.ofMillis(ms);
    }
}

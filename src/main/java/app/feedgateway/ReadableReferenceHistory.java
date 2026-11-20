package app.feedgateway;

import app.feedgateway.readable.ReferencePrice;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.OffsetAndTimestamp;
import org.apache.kafka.common.TopicPartition;

/**
 * <b>C-74</b>: the two read-backs the reference price needs, as the gateway performs them.
 *
 * <p><b>No session calendar is involved, deliberately.</b> The SPX index only prints during the
 * cash session, so the newest index print at or before now <i>is</i> the previous cash close —
 * there is nothing to look up and no holiday table to get wrong. That is the same rule the live
 * page already uses; this class only lets the gateway apply it to history instead of waiting for
 * the next print, which overnight is eleven hours away.
 *
 * <p>Both seeks are bounded in time and in records, and both are <b>one-shot at start-up</b>: this
 * is not a consumer, it opens a client, assigns, seeks, drains a bounded window and closes. A
 * failure anywhere returns an empty list, because {@link ReferencePrice} already states an
 * explained absence and a page that says why it has no reference is better than a gateway that
 * will not start.
 */
final class ReadableReferenceHistory implements ReferencePrice.History {

    /** How far back to look for the index print. Comfortably past a weekend plus a holiday. */
    static final long PRICE_LOOKBACK_MS = 96L * 3_600_000L;

    /** How far back to look for the session's bars. One session plus the evening before it. */
    static final long BAR_LOOKBACK_MS = 30L * 3_600_000L;

    /** One session plus the evening before it: enough to rebuild the path, not a week of it. */
    static final long SNAPSHOT_LOOKBACK_MS = 30L * 3_600_000L;

    /** A bound on the drain, so a busy topic cannot turn start-up into a replay. */
    static final int MAX_RECORDS = 20_000;

    private final Supplier<Consumer<String, Object>> consumers;
    private final Function<Object, String> toJson;
    private final String priceTopic;
    private final String barTopic;

    /**
     * @param consumers  opens a client configured for these topics; closed by this class
     * @param toJson     the gateway's own record-to-JSON conversion, so history decodes exactly
     *                   as the live tap does -- two decoders would be two ways to be wrong
     */
    ReadableReferenceHistory(Supplier<Consumer<String, Object>> consumers,
            Function<Object, String> toJson, String priceTopic, String barTopic) {
        this.consumers = consumers;
        this.toJson = toJson;
        this.priceTopic = priceTopic;
        this.barTopic = barTopic;
    }

    @Override
    public List<String> pricesBefore(long beforeMs, int limit) {
        return drain(priceTopic, beforeMs - PRICE_LOOKBACK_MS, beforeMs, limit);
    }

    @Override
    public List<String> cashSessionBars(long beforeMs) {
        return drain(barTopic, beforeMs - BAR_LOOKBACK_MS, beforeMs, MAX_RECORDS);
    }

    /**
     * <b>C-71</b>: the ES indicators snapshots of this session, for the side-path rebuild.
     *
     * <p>The same bounded, by-time seek: the topic holds seven days and reading it from offset
     * zero at every start-up would be a replay of a week for one session's worth of records.
     */
    List<String> sessionSnapshots(long beforeMs, String snapshotTopic) {
        return drain(snapshotTopic, beforeMs - SNAPSHOT_LOOKBACK_MS, beforeMs, MAX_RECORDS);
    }

    /**
     * Every record of a topic in a time window, as JSON.
     *
     * <p>Seeks by TIMESTAMP on each partition rather than reading from the beginning: these topics
     * are retained unlimited, and reading one from offset zero at every start-up would be minutes
     * of replay for a handful of records that matter.
     */
    private List<String> drain(String topic, long fromMs, long toMs, int limit) {
        if (topic == null || topic.isBlank() || limit <= 0) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        try (Consumer<String, Object> consumer = consumers.get()) {
            if (consumer == null) {
                return List.of();
            }
            List<TopicPartition> parts = new ArrayList<>();
            var infos = consumer.partitionsFor(topic, Duration.ofSeconds(10));
            if (infos == null || infos.isEmpty()) {
                return List.of();
            }
            for (var info : infos) {
                parts.add(new TopicPartition(topic, info.partition()));
            }
            consumer.assign(parts);

            Map<TopicPartition, Long> wanted = new java.util.HashMap<>();
            for (TopicPartition tp : parts) {
                wanted.put(tp, fromMs);
            }
            Map<TopicPartition, OffsetAndTimestamp> found =
                    consumer.offsetsForTimes(wanted, Duration.ofSeconds(10));
            Map<TopicPartition, Long> ends = consumer.endOffsets(parts, Duration.ofSeconds(10));
            boolean any = false;
            for (TopicPartition tp : parts) {
                OffsetAndTimestamp at = found == null ? null : found.get(tp);
                if (at == null) {
                    // Nothing at or after fromMs on this partition: every record it holds is older
                    // than the window, so there is nothing here to read forward to.
                    consumer.seek(tp, ends.getOrDefault(tp, 0L));
                    continue;
                }
                consumer.seek(tp, at.offset());
                any = true;
            }
            if (!any) {
                return List.of();
            }

            int empties = 0;
            while (out.size() < limit && empties < 3) {
                ConsumerRecords<String, Object> polled = consumer.poll(Duration.ofSeconds(2));
                if (polled == null || polled.isEmpty()) {
                    empties++;
                    continue;
                }
                empties = 0;
                for (ConsumerRecord<String, Object> record : polled) {
                    if (record.timestamp() > toMs) {
                        continue;             // past the window; a later partition may still be in it
                    }
                    String json = toJson.apply(record.value());
                    if (json != null && !json.isBlank()) {
                        out.add(json);
                    }
                    if (out.size() >= limit) {
                        break;
                    }
                }
                if (caughtUp(consumer, parts, ends)) {
                    break;
                }
            }
        } catch (RuntimeException unreachable) {
            // A broker that cannot be read at start-up is not a reason to refuse to start: the
            // page states the absence and its reason, which is the honest outcome.
            return List.of();
        }
        return out;
    }

    private static boolean caughtUp(Consumer<String, Object> consumer, List<TopicPartition> parts,
            Map<TopicPartition, Long> ends) {
        for (TopicPartition tp : parts) {
            if (consumer.position(tp) < ends.getOrDefault(tp, 0L)) {
                return false;
            }
        }
        return true;
    }
}

package app.feedgateway;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.MockConsumer;
import org.apache.kafka.clients.consumer.OffsetAndTimestamp;
import org.apache.kafka.clients.consumer.OffsetResetStrategy;
import org.apache.kafka.common.PartitionInfo;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The C-74 seeks themselves: the window, the bound, and what happens when the broker will not talk.
 *
 * <p>{@link MockConsumer} does not implement {@code offsetsForTimes}, so it is overridden here to
 * answer from the records it holds — which is the behaviour under test anyway: this class must seek
 * by TIME rather than read a retained-forever topic from offset zero at every start-up.
 */
class ReadableReferenceHistoryTest {

    private static final String PRICES = "underlying.spx.price";
    private static final String BARS = "es.futures.footprint.bars";
    private static final long NOW = 1_795_140_000_000L;

    /** A MockConsumer that can answer offsetsForTimes, and records what it was asked to seek. */
    private static final class Seeking extends MockConsumer<String, Object> {
        private final Map<TopicPartition, List<ConsumerRecord<String, Object>>> held =
                new HashMap<>();
        long seekedTo = -1;

        Seeking() {
            super(OffsetResetStrategy.NONE);
        }

        void hold(TopicPartition tp, List<ConsumerRecord<String, Object>> records) {
            held.put(tp, records);
            updateBeginningOffsets(Map.of(tp, 0L));
            updateEndOffsets(Map.of(tp, (long) records.size()));
        }

        @Override
        public Map<TopicPartition, OffsetAndTimestamp> offsetsForTimes(
                Map<TopicPartition, Long> timestampsToSearch, Duration timeout) {
            Map<TopicPartition, OffsetAndTimestamp> out = new HashMap<>();
            for (var e : timestampsToSearch.entrySet()) {
                List<ConsumerRecord<String, Object>> records =
                        held.getOrDefault(e.getKey(), List.of());
                OffsetAndTimestamp found = null;
                for (ConsumerRecord<String, Object> r : records) {
                    if (r.timestamp() >= e.getValue()) {
                        found = new OffsetAndTimestamp(r.offset(), r.timestamp());
                        break;
                    }
                }
                out.put(e.getKey(), found);
            }
            return out;
        }

        @Override
        public synchronized void seek(TopicPartition partition, long offset) {
            seekedTo = offset;
            super.seek(partition, offset);
            for (ConsumerRecord<String, Object> r : held.getOrDefault(partition, List.of())) {
                if (r.offset() >= offset) {
                    addRecord(r);
                }
            }
        }

        @Override
        public synchronized List<PartitionInfo> partitionsFor(String topic, Duration timeout) {
            return List.of(new PartitionInfo(topic, 0, null, new org.apache.kafka.common.Node[0],
                    new org.apache.kafka.common.Node[0]));
        }
    }

    private static ConsumerRecord<String, Object> record(String topic, long offset, long ts,
            String value) {
        return new ConsumerRecord<String, Object>(topic, 0, offset, ts,
                org.apache.kafka.common.record.TimestampType.CREATE_TIME,
                0, 0, null, value, new org.apache.kafka.common.header.internals.RecordHeaders(),
                java.util.Optional.empty());
    }

    @Test
    @DisplayName("it seeks by time and returns only records inside the window")
    void itSeeksByTimeAndWindowsTheResult() {
        TopicPartition tp = new TopicPartition(PRICES, 0);
        Seeking consumer = new Seeking();
        consumer.hold(tp, List.of(
                record(PRICES, 0L, NOW - 200L * 3_600_000L, "far-too-old"),
                record(PRICES, 1L, NOW - 10L * 3_600_000L, "in-window-a"),
                record(PRICES, 2L, NOW - 1L * 3_600_000L, "in-window-b"),
                record(PRICES, 3L, NOW + 3_600_000L, "after-the-window")));

        ReadableReferenceHistory history = new ReadableReferenceHistory(
                () -> consumer, v -> (String) v, PRICES, BARS);
        List<String> got = history.pricesBefore(NOW, 500);

        assertTrue(got.contains("in-window-a") && got.contains("in-window-b"), got.toString());
        assertTrue(!got.contains("far-too-old"),
                "a record older than the look-back must not be read: seeking by time is the whole "
                        + "point, because these topics are retained forever");
        assertTrue(!got.contains("after-the-window"),
                "and a record after the instant asked for is not this session's: " + got);
        assertTrue(consumer.seekedTo > 0, "it seeked rather than reading from the beginning");
    }

    @Test
    @DisplayName("the drain is bounded by the limit it was given")
    void theDrainIsBounded() {
        TopicPartition tp = new TopicPartition(PRICES, 0);
        Seeking consumer = new Seeking();
        List<ConsumerRecord<String, Object>> many = new java.util.ArrayList<>();
        for (int i = 0; i < 50; i++) {
            many.add(record(PRICES, i, NOW - (50 - i) * 60_000L, "r" + i));
        }
        consumer.hold(tp, many);

        List<String> got = new ReadableReferenceHistory(() -> consumer, v -> (String) v,
                PRICES, BARS).pricesBefore(NOW, 10);
        assertEquals(10, got.size(), "a busy topic must not turn start-up into a replay");
    }

    @Test
    @DisplayName("a broker that cannot be read gives an empty history, never an exception")
    void anUnreachableBrokerIsEmptyNotFatal() {
        ReadableReferenceHistory history = new ReadableReferenceHistory(
                () -> {
                    throw new org.apache.kafka.common.KafkaException("no broker");
                }, v -> (String) v, PRICES, BARS);
        assertEquals(List.of(), history.pricesBefore(NOW, 100),
                "the page states the absence and its reason; the gateway still starts");
        assertEquals(List.of(), history.cashSessionBars(NOW));
    }

    @Test
    @DisplayName("a blank topic name reads nothing rather than guessing one")
    void aBlankTopicIsRefused() {
        assertEquals(List.of(), new ReadableReferenceHistory(() -> new Seeking(),
                v -> (String) v, "  ", BARS).pricesBefore(NOW, 100));
    }
}

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
        List<String> got = history.pricesBetween(NOW - 96L * 3_600_000L, NOW, 500);

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
                PRICES, BARS).pricesBetween(NOW - 96L * 3_600_000L, NOW, 10);
        assertEquals(10, got.size(), "a busy topic must not turn start-up into a replay");
    }

    @Test
    @DisplayName("a broker that cannot be read gives an empty history, never an exception")
    void anUnreachableBrokerIsEmptyNotFatal() {
        ReadableReferenceHistory history = new ReadableReferenceHistory(
                () -> {
                    throw new org.apache.kafka.common.KafkaException("no broker");
                }, v -> (String) v, PRICES, BARS);
        assertEquals(List.of(), history.pricesBetween(NOW - 96L * 3_600_000L, NOW, 100),
                "the page states the absence and its reason; the gateway still starts");
        assertEquals(List.of(), history.barsBetween(NOW - 30L * 3_600_000L, NOW));
    }

    @Test
    @DisplayName("a blank topic name reads nothing rather than guessing one")
    void aBlankTopicIsRefused() {
        assertEquals(List.of(), new ReadableReferenceHistory(() -> new Seeking(),
                v -> (String) v, "  ", BARS).pricesBetween(NOW - 96L * 3_600_000L, NOW, 100));
    }

    @Test
    @DisplayName("a converter that reads NONE of the records is reported, not returned as empty")
    void aConverterThatReadsNothingIsReported() {
        TopicPartition tp = new TopicPartition(PRICES, 0);
        Seeking consumer = new Seeking();
        consumer.hold(tp, List.of(
                record(PRICES, 0L, NOW - 2L * 3_600_000L, "a"),
                record(PRICES, 1L, NOW - 1L * 3_600_000L, "b")));

        // The C-74 defect in miniature: the records are fine and the converter cannot read them.
        ReadableReferenceHistory history = new ReadableReferenceHistory(
                () -> consumer, v -> null, PRICES, BARS);
        assertEquals(List.of(), history.pricesBetween(NOW - 96L * 3_600_000L, NOW, 500));

        var drains = history.drains();
        assertEquals(1, drains.size(), "one seek, one report");
        var d = drains.get(0);
        assertEquals(2, d.polled(), "it polled both records");
        assertEquals(0, d.converted(), "and converted neither");
        assertEquals(2, d.unconvertible());
        assertTrue(d.polledButConvertedNothing(),
                "which is a wrong converter, NOT an empty window -- the distinction the old "
                        + "catch-and-return-empty threw away");
        assertTrue(d.line().contains("CONVERTED NONE"),
                "and it says so where anyone reading the log will see it: " + d.line());
        assertTrue(d.line().contains(PRICES), d.line());
    }

    @Test
    @DisplayName("an empty window is reported as an empty window, not as a broken converter")
    void anEmptyWindowIsNotABrokenConverter() {
        TopicPartition tp = new TopicPartition(PRICES, 0);
        Seeking consumer = new Seeking();
        consumer.hold(tp, List.of(record(PRICES, 0L, NOW - 500L * 3_600_000L, "ancient")));

        ReadableReferenceHistory history = new ReadableReferenceHistory(
                () -> consumer, v -> (String) v, PRICES, BARS);
        history.pricesBetween(NOW - 2L * 3_600_000L, NOW, 500);

        var d = history.drains().get(0);
        assertTrue(!d.polledButConvertedNothing(),
                "nothing was polled, so nothing failed to convert: " + d.line());
        assertEquals(0, d.polled());
    }

    @Test
    @DisplayName("an unreachable broker's report names the exception class and its message")
    void anUnreachableBrokerNamesItsException() {
        ReadableReferenceHistory history = new ReadableReferenceHistory(
                () -> {
                    throw new org.apache.kafka.common.KafkaException("no broker");
                }, v -> (String) v, PRICES, BARS);
        history.pricesBetween(NOW - 96L * 3_600_000L, NOW, 100);

        var d = history.drains().get(0);
        assertTrue(d.failure() != null && d.failure().contains("KafkaException"),
                "the reason a start-up read failed must not be swallowed: " + d.failure());
        assertTrue(d.failure().contains("no broker"), d.failure());
        assertTrue(d.line().contains("failed=KafkaException"), d.line());
    }

    @Test
    @DisplayName("a successful drain reports what it read, so 'rebuilt from 0' is never the whole story")
    void aSuccessfulDrainReportsItsCounts() {
        TopicPartition tp = new TopicPartition(PRICES, 0);
        Seeking consumer = new Seeking();
        consumer.hold(tp, List.of(
                record(PRICES, 0L, NOW - 3L * 3_600_000L, "a"),
                record(PRICES, 1L, NOW - 2L * 3_600_000L, "b"),
                record(PRICES, 2L, NOW + 3_600_000L, "after")));

        ReadableReferenceHistory history = new ReadableReferenceHistory(
                () -> consumer, v -> (String) v, PRICES, BARS);
        history.pricesBetween(NOW - 96L * 3_600_000L, NOW, 500);

        var d = history.drains().get(0);
        assertEquals(3, d.polled());
        assertEquals(2, d.converted());
        assertEquals(1, d.outsideWindow());
        assertEquals(0, d.unconvertible());
        assertTrue(d.failure() == null, d.line());
    }

    @Test
    @DisplayName("the price topic and the bar topic each convert through their OWN boundary")
    void eachTopicConvertsThroughItsOwnBoundary() {
        // A FRESH consumer per seek, because drain() closes the one it was handed -- a single
        // shared mock would make the second seek read nothing for a reason that is not the
        // converter's.
        java.util.function.Supplier<org.apache.kafka.clients.consumer.Consumer<String, Object>>
                consumers = () -> {
                    Seeking c = new Seeking();
                    c.hold(new TopicPartition(PRICES, 0),
                            List.of(record(PRICES, 0L, NOW - 3_600_000L, "typed-price")));
                    c.hold(new TopicPartition(BARS, 0),
                            List.of(record(BARS, 0L, NOW - 3_600_000L, "generic-bar")));
                    return c;
                };

        // Two converters, and each must be used on its own topic. The single shared converter this
        // replaces returned null for every typed UnderlyingPriceEvent on the price topic.
        ReadableReferenceHistory history = new ReadableReferenceHistory(
                consumers,
                v -> "price:" + v,
                v -> "bar:" + v,
                PRICES, BARS);

        assertEquals(List.of("price:typed-price"),
                history.pricesBetween(NOW - 96L * 3_600_000L, NOW, 10));
        assertEquals(List.of("bar:generic-bar"),
                history.barsBetween(NOW - 30L * 3_600_000L, NOW));
    }

    @Test
    @DisplayName("barPublishTimes pairs each bar's own minute with the BROKER's stamp")
    void barPublishTimesUsesTheBrokerStamp() {
        long barStart = NOW - 6L * 3_600_000L;
        long publishedLate = barStart + 3L * 3_600_000L;      // backfilled three hours later
        long punctual = barStart + 65_000L;

        String late = bar(barStart);
        String onTime = bar(barStart + 60_000L);

        java.util.function.Supplier<org.apache.kafka.clients.consumer.Consumer<String, Object>>
                consumers = () -> {
                    Seeking c = new Seeking();
                    c.hold(new TopicPartition(BARS, 0), List.of(
                            record(BARS, 0L, publishedLate, late),
                            record(BARS, 1L, punctual + 60_000L, onTime)));
                    return c;
                };

        ReadableReferenceHistory history = new ReadableReferenceHistory(
                consumers, v -> (String) v, v -> (String) v, PRICES, BARS);

        var pairs = history.barPublishTimes(NOW - 30L * 3_600_000L, NOW);
        assertEquals(2, pairs.size(), "both bars read: " + pairs);

        var first = pairs.get(0);
        assertEquals(barStart, first.barStartMs(),
                "the bar's own minute comes from its own observations block");
        assertEquals(barStart + 60_000L, first.barEndMs(),
                "the bar's own close, read from the record and not derived");
        assertEquals(publishedLate, first.publishedMs(),
                "and the publish instant is the RECORD's timestamp, which is what survives a "
                        + "restart -- not anything taken from our own clock");

        app.feedgateway.readable.FeedGap gap = new app.feedgateway.readable.FeedGap();
        gap.measure(pairs);
        assertEquals(1, gap.lateBars(), "exactly the backfilled one is late");
    }

    @Test
    @DisplayName("a bar with no readable minute is counted unconvertible, not paired with a guess")
    void aBarWithNoMinuteIsRefused() {
        java.util.function.Supplier<org.apache.kafka.clients.consumer.Consumer<String, Object>>
                consumers = () -> {
                    Seeking c = new Seeking();
                    c.hold(new TopicPartition(BARS, 0), List.of(
                            record(BARS, 0L, NOW - 3_600_000L, "{\"observations\":{}}")));
                    return c;
                };
        ReadableReferenceHistory history = new ReadableReferenceHistory(
                consumers, v -> (String) v, v -> (String) v, PRICES, BARS);

        assertTrue(history.barPublishTimes(NOW - 30L * 3_600_000L, NOW).isEmpty());
        var d = history.drains().get(0);
        assertEquals(1, d.polled());
        assertEquals(1, d.unconvertible(),
                "a bar whose minute cannot be read is reported, never paired with a guessed one");
        assertTrue(d.polledButConvertedNothing(), d.line());
    }

    /**
     * A footprint bar carrying BOTH instants the publish-time reader needs.
     *
     * <p>{@code barEndMs} as well as {@code barStartMs}: lateness is the lag after the bar CLOSED,
     * because the topic carries five timeframes and a 30m bar is 1,800 s after its own start by
     * construction. A fixture with only the start made this test pass while the page named the
     * whole session as a gap.
     */
    private static String bar(long startMs) {
        return bar(startMs, 60_000L);
    }

    private static String bar(long startMs, long widthMs) {
        return "{\"schemaVersion\":6,\"symbol\":\"ES.v.0\",\"timeframe\":\"1m\","
                + "\"widthMs\":" + widthMs + ",\"observations\":{\"barStartMs\":" + startMs
                + ",\"barEndMs\":" + (startMs + widthMs) + ",\"volume\":1200}}";
    }

    @Test
    @DisplayName("the night grid takes every bar whose START is in the night, backfilled or not")
    void aBackfilledBarIsInTheWindowByItsOwnMinute() {
        long nightFrom = NOW - 12L * 3_600_000L;
        long barStart = NOW - 6L * 3_600_000L;          // its minute is squarely in the night
        long publishedLate = NOW + 2L * 3_600_000L;     // written two hours AFTER the window ends

        TopicPartition tp = new TopicPartition(BARS, 0);
        Seeking consumer = new Seeking();
        consumer.hold(tp, List.of(
                record(BARS, 0L, barStart + 65_000L, bar(barStart - 3_600_000L)),
                record(BARS, 1L, publishedLate, bar(barStart)),
                record(BARS, 2L, publishedLate, bar(NOW + 3L * 3_600_000L))));

        ReadableReferenceHistory history = new ReadableReferenceHistory(
                () -> consumer, v -> (String) v, PRICES, BARS);
        List<String> bars = history.barsBetween(nightFrom, NOW);

        assertEquals(2, bars.size(),
                "THE DEFECT: the backfilled bar's minute is in the night and its broker stamp is "
                        + "not, and the drain dropped it for the stamp. The 2026-11-20 night bars "
                        + "were written up to five and a half hours after the minutes they "
                        + "describe, so the grid asked for the night and got the punctual subset");
        assertTrue(bars.get(1).contains("\"barStartMs\":" + barStart), bars.get(1));
        assertEquals(1, history.lastLateButInWindow(),
                "and the one it took on its own minute against the broker's stamp is counted, so "
                        + "this is a figure rather than a belief");

        var d = history.drains().get(0);
        assertEquals(3, d.polled());
        assertEquals(2, d.converted());
        assertEquals(1, d.outsideWindow(),
                "only the bar whose own minute is past the window is outside it");
    }

    @Test
    @DisplayName("a bar with no close instant is refused, not given a derived one")
    void aBarWithNoEndIsRefused() {
        String noEnd = "{\"schemaVersion\":6,\"timeframe\":\"1m\","
                + "\"observations\":{\"barStartMs\":" + (NOW - 3_600_000L) + "}}";
        java.util.function.Supplier<org.apache.kafka.clients.consumer.Consumer<String, Object>>
                consumers = () -> {
                    Seeking c = new Seeking();
                    c.hold(new TopicPartition(BARS, 0),
                            List.of(record(BARS, 0L, NOW - 3_500_000L, noEnd)));
                    return c;
                };
        ReadableReferenceHistory history = new ReadableReferenceHistory(
                consumers, v -> (String) v, v -> (String) v, PRICES, BARS);

        assertTrue(history.barPublishTimes(NOW - 30L * 3_600_000L, NOW).isEmpty(),
                "a bar that cannot say which minutes it covers cannot be judged late or punctual; "
                        + "deriving its close from a width would be a second opinion about a figure "
                        + "the producer already states");
        assertEquals(1, history.drains().get(0).unconvertible());
    }
}

package app.feedgateway.mtsession.gateway;

import static org.junit.jupiter.api.Assertions.assertEquals;

import app.feedgateway.mtsession.gateway.ReplayChronology.Cursor;
import java.util.List;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

/**
 * Replay must reproduce the real market chronology: records from every topic/partition merged by event
 * time ({@code eventTimestamp → topic → partition → offset}), never topic-by-topic, and the cap must bound
 * the merged timeline rather than being drained by whichever topic is read first.
 */
class ReplayChronologyTest {

    private static final Function<Cursor, Cursor> ID = Function.identity();

    private static long ts(Cursor c) {
        return c.eventTimestamp();
    }

    @Test
    void mergeInterleavesTwoTopicsByEventTimeNotTopicByTopic() {
        // snapshots at even-ish times, strike-flow interleaved — the buggy reader would emit all of one
        // topic then all of the other; the merge must interleave by timestamp.
        List<Cursor> snapshots = List.of(
                new Cursor(10, "options.databento.display", 0, 0),
                new Cursor(30, "options.databento.display", 0, 1),
                new Cursor(50, "options.databento.display", 0, 2));
        List<Cursor> strikeFlow = List.of(
                new Cursor(20, "options.databento.strike-flow", 0, 0),
                new Cursor(40, "options.databento.strike-flow", 0, 1),
                new Cursor(60, "options.databento.strike-flow", 0, 2));

        List<Cursor> merged = ReplayChronology.merge(List.of(snapshots, strikeFlow), ID, 0);

        assertEquals(List.of(10L, 20L, 30L, 40L, 50L, 60L), merged.stream().map(ReplayChronologyTest::ts).toList());
    }

    @Test
    void capTakesTheEarliestAcrossAllTopicsNotAllOfTheFirstPhase() {
        // The avro topic is "read first" but the cap must still take the chronologically earliest 3 events
        // across BOTH topics — so the string topic's early record (ts=20) is NOT starved.
        List<Cursor> avro = List.of(
                new Cursor(10, "a.display", 0, 0),
                new Cursor(30, "a.display", 0, 1),
                new Cursor(50, "a.display", 0, 2));
        List<Cursor> string = List.of(
                new Cursor(20, "z.strike-flow", 0, 0),
                new Cursor(40, "z.strike-flow", 0, 1));

        List<Cursor> merged = ReplayChronology.merge(List.of(avro, string), ID, 3);

        assertEquals(List.of(10L, 20L, 30L), merged.stream().map(ReplayChronologyTest::ts).toList());
    }

    @Test
    void tieBreakerIsTopicThenPartitionThenOffset() {
        // All identical timestamp — deterministic order falls to topic, then partition, then offset.
        List<Cursor> a = List.of(
                new Cursor(100, "alpha", 1, 9),
                new Cursor(100, "alpha", 1, 10));
        List<Cursor> b = List.of(
                new Cursor(100, "alpha", 0, 5),
                new Cursor(100, "beta", 0, 0));

        List<Cursor> merged = ReplayChronology.merge(List.of(a, b), ID, 0);

        assertEquals(List.of(
                new Cursor(100, "alpha", 0, 5),   // alpha p0
                new Cursor(100, "alpha", 1, 9),    // alpha p1 off9
                new Cursor(100, "alpha", 1, 10),   // alpha p1 off10
                new Cursor(100, "beta", 0, 0)      // beta last
        ), merged);
    }

    @Test
    void mergeAcrossManyPartitionsIsGloballyOrdered() {
        List<Cursor> p0 = List.of(new Cursor(1, "t", 0, 0), new Cursor(5, "t", 0, 1));
        List<Cursor> p1 = List.of(new Cursor(2, "t", 1, 0), new Cursor(3, "t", 1, 1));
        List<Cursor> p2 = List.of(new Cursor(4, "t", 2, 0), new Cursor(6, "t", 2, 1));

        List<Cursor> merged = ReplayChronology.merge(List.of(p0, p1, p2), ID, 0);

        assertEquals(List.of(1L, 2L, 3L, 4L, 5L, 6L), merged.stream().map(ReplayChronologyTest::ts).toList());
    }

    @Test
    void emptySourcesYieldEmptyAndNonPositiveCapMeansUnlimited() {
        assertEquals(List.of(), ReplayChronology.merge(List.of(List.<Cursor>of(), List.of()), ID, 0));
        List<Cursor> all = List.of(new Cursor(1, "t", 0, 0), new Cursor(2, "t", 0, 1));
        assertEquals(all, ReplayChronology.merge(List.of(all), ID, 0));   // cap 0 = no cap
        assertEquals(all, ReplayChronology.merge(List.of(all), ID, -1));  // negative = no cap
    }
}

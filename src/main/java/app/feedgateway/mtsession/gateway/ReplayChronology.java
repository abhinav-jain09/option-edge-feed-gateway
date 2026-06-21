package app.feedgateway.mtsession.gateway;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;
import java.util.function.Function;

/**
 * Canonical chronological ordering for historical replay (P0 — replay chronology). Replay must reproduce
 * the real market sequence, so records from EVERY topic and partition are merged by event time, not read
 * topic-by-topic (which would surface "all snapshots, then all strike-flow"). The deterministic order is:
 *
 * <pre>eventTimestamp → topic → partition → offset</pre>
 *
 * <p>{@link #merge} is a stable k-way merge over per-source streams that are each already sorted by this
 * order (a Kafka partition is read in offset order, and these append-only feeds carry non-decreasing event
 * timestamps within a partition). It is pure (no Kafka) so the ordering + record cap are unit-testable; the
 * live reader uses the same {@link #ORDER} comparator over per-partition buffers.
 */
public final class ReplayChronology {

    private ReplayChronology() {
    }

    /** The merge key carried by every outbound replay event; retained on the wire for auditability. */
    public record Cursor(long eventTimestamp, String topic, int partition, long offset) {
    }

    /** eventTimestamp → topic → partition → offset. Total and deterministic (no equal distinct records). */
    public static final Comparator<Cursor> ORDER = Comparator
            .comparingLong(Cursor::eventTimestamp)
            .thenComparing(Cursor::topic)
            .thenComparingInt(Cursor::partition)
            .thenComparingLong(Cursor::offset);

    /**
     * Deterministic k-way merge of per-source streams, each already in {@link #ORDER}. Returns at most
     * {@code cap} elements (the EARLIEST {@code cap} across all sources — the cap is applied to the merged
     * timeline, never exhausted by whichever source happens to be read first). {@code cap <= 0} means no cap.
     *
     * @param sources per-source lists, each sorted by {@code keyFn} in {@link #ORDER}
     * @param keyFn   extracts the merge {@link Cursor} from an element
     */
    public static <T> List<T> merge(List<List<T>> sources, Function<T, Cursor> keyFn, int cap) {
        // Heap of (sourceIndex, positionInSource); ordered by the element's cursor.
        PriorityQueue<int[]> heap = new PriorityQueue<>((a, b) ->
                ORDER.compare(keyFn.apply(sources.get(a[0]).get(a[1])),
                              keyFn.apply(sources.get(b[0]).get(b[1]))));
        for (int s = 0; s < sources.size(); s++) {
            if (!sources.get(s).isEmpty()) {
                heap.add(new int[]{s, 0});
            }
        }
        List<T> out = new ArrayList<>();
        while (!heap.isEmpty()) {
            if (cap > 0 && out.size() >= cap) {
                break;
            }
            int[] head = heap.poll();
            List<T> src = sources.get(head[0]);
            out.add(src.get(head[1]));
            int next = head[1] + 1;
            if (next < src.size()) {
                heap.add(new int[]{head[0], next});
            }
        }
        return out;
    }
}

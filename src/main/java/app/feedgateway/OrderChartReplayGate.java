package app.feedgateway;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;
import java.util.function.ToLongFunction;
import org.apache.kafka.common.TopicPartition;

/** Attempt-owned replay barrier. A capability announcement never implies a hydrated history view. */
final class OrderChartReplayGate {
    private Object owner;
    private final Map<TopicPartition, Long> barriers = new HashMap<>();
    private boolean ready;

    synchronized void begin(Object attempt, Collection<TopicPartition> partitions) {
        if (owner != attempt) { owner = attempt; barriers.clear(); }
        ready = false;
        for (TopicPartition partition : partitions) barriers.put(partition, Long.MAX_VALUE);
    }

    synchronized void capture(Object attempt, Map<TopicPartition, Long> ends) {
        if (owner != attempt) return;
        ends.forEach((partition, end) -> {
            if (barriers.containsKey(partition) && end >= 0) barriers.put(partition, end);
        });
    }

    synchronized void advance(Object attempt, ToLongFunction<TopicPartition> appliedPosition) {
        if (owner != attempt || barriers.isEmpty()) return;
        ready = false;
        for (var entry : barriers.entrySet()) {
            if (entry.getValue() == Long.MAX_VALUE) return;
            try { if (appliedPosition.applyAsLong(entry.getKey()) < entry.getValue()) return; }
            catch (RuntimeException unassigned) { return; }
        }
        ready = true;
    }

    synchronized void invalidate() { owner = null; ready = false; barriers.clear(); }

    synchronized <T> T read(Supplier<T> snapshot, Supplier<T> loading) {
        return ready ? snapshot.get() : loading.get();
    }
}

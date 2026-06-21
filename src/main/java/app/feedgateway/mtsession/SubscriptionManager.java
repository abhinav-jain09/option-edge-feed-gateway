package app.feedgateway.mtsession;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Ref-counted union of distinct Databento subscriptions, with idempotent, never-negative
 * accounting (OE-DDD-001 §9.2, DR-9).
 *
 * <p>This is the in-memory reference implementation of the Redis-Lua owner-set semantics: the
 * ref-count is derived from a <em>set of owners</em> (AppSession ids), not a raw counter. Therefore:
 * <ul>
 *   <li>acquiring twice for the same owner is a no-op on the count (idempotent);</li>
 *   <li>releasing an unknown owner, or releasing twice, can never drive the count negative;</li>
 *   <li>the subscribed strike range is the union of all current owners' windows, and it narrows
 *       correctly when owners leave (because it is recomputed from the surviving owners).</li>
 * </ul>
 *
 * <p>Not thread-safe by itself; the gateway serialises mutations per Redis key. Tests exercise the
 * invariants directly.
 */
public final class SubscriptionManager {

    /** Result of an acquire/release, exposing the resulting ref-count and union range. */
    public record RefState(int refCount, Optional<StrikeWindow> range, boolean changed) {
    }

    private final Map<SubscriptionKey, Map<String, StrikeWindow>> owners = new LinkedHashMap<>();

    /**
     * Record that {@code ownerId} depends on {@code key} with the given strike window.
     * Idempotent per owner; updating an existing owner's window re-computes the union.
     */
    public RefState acquire(SubscriptionKey key, String ownerId, StrikeWindow window) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(ownerId, "ownerId");
        Objects.requireNonNull(window, "window");
        Map<String, StrikeWindow> set = owners.computeIfAbsent(key, k -> new LinkedHashMap<>());
        boolean changed = !window.equals(set.put(ownerId, window));
        return new RefState(set.size(), unionOf(set), changed);
    }

    /**
     * Remove {@code ownerId}'s dependency on {@code key}. Safe under duplicate/unknown release —
     * the count floors at zero and the entry is dropped when the last owner leaves.
     */
    public RefState release(SubscriptionKey key, String ownerId) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(ownerId, "ownerId");
        Map<String, StrikeWindow> set = owners.get(key);
        if (set == null) {
            return new RefState(0, Optional.empty(), false);
        }
        boolean changed = set.remove(ownerId) != null;
        if (set.isEmpty()) {
            owners.remove(key);
            return new RefState(0, Optional.empty(), changed);
        }
        return new RefState(set.size(), unionOf(set), changed);
    }

    public int refCount(SubscriptionKey key) {
        Map<String, StrikeWindow> set = owners.get(key);
        return set == null ? 0 : set.size();
    }

    public Optional<StrikeWindow> subscribedRange(SubscriptionKey key) {
        Map<String, StrikeWindow> set = owners.get(key);
        return set == null ? Optional.empty() : unionOf(set);
    }

    /** Immutable snapshot of the desired upstream subscription set: key → unioned strike range. */
    public Map<SubscriptionKey, StrikeWindow> desiredSet() {
        Map<SubscriptionKey, StrikeWindow> out = new LinkedHashMap<>();
        for (var e : owners.entrySet()) {
            unionOf(e.getValue()).ifPresent(range -> out.put(e.getKey(), range));
        }
        return Collections.unmodifiableMap(out);
    }

    public int distinctSubscriptions() {
        return owners.size();
    }

    private static Optional<StrikeWindow> unionOf(Map<String, StrikeWindow> set) {
        StrikeWindow acc = null;
        for (StrikeWindow w : set.values()) {
            acc = acc == null ? w : acc.union(w);
        }
        return Optional.ofNullable(acc);
    }
}

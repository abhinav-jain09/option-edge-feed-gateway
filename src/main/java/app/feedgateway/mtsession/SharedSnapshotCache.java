package app.feedgateway.mtsession;

import java.util.concurrent.ConcurrentHashMap;

/**
 * The shared, per-contract snapshot cache (OE-DDD-001 §8.1, NFR-3): exactly one entry per contract
 * regardless of how many sessions subscribe. Modelled minimally here to assert the
 * O(distinct contracts) invariant in tests; the live gateway stores richer snapshot state.
 */
public final class SharedSnapshotCache {

    private final ConcurrentHashMap<SubscriptionKey, Object> byContract = new ConcurrentHashMap<>();

    public void put(SubscriptionKey key, Object snapshot) {
        byContract.put(key, snapshot);
    }

    public Object get(SubscriptionKey key) {
        return byContract.get(key);
    }

    public int size() {
        return byContract.size();
    }
}

package app.feedgateway.mtsession;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Persistent per-user application session (OE-DDD-001 §6.1). Selection, epoch, and attached sockets
 * are mutated only by {@link SessionRoutingEngine}, which owns all consistency between an
 * AppSession and the routing indexes.
 */
public final class AppSession {

    private final String id;
    private final String userId;
    private final Set<String> entitlements;
    private final Set<String> socketIds = new LinkedHashSet<>();
    private Selection selection;
    private long epoch;

    AppSession(String id, String userId, Set<String> entitlements, Selection selection, long epoch) {
        this.id = id;
        this.userId = userId;
        this.entitlements = Set.copyOf(entitlements);
        this.selection = selection;
        this.epoch = epoch;
    }

    public String id() {
        return id;
    }

    public String userId() {
        return userId;
    }

    public Set<String> entitlements() {
        return entitlements;
    }

    public Selection selection() {
        return selection;
    }

    public MarketDataSource mode() {
        return selection.source();
    }

    public long epoch() {
        return epoch;
    }

    public Set<String> socketIds() {
        return Collections.unmodifiableSet(socketIds);
    }

    public boolean hasSockets() {
        return !socketIds.isEmpty();
    }

    // ---- package-private mutation, driven by the engine ----

    void setSelection(Selection selection) {
        this.selection = selection;
    }

    void bumpEpoch() {
        this.epoch++;
    }

    boolean addSocket(String socketId) {
        return socketIds.add(socketId);
    }

    boolean removeSocket(String socketId) {
        return socketIds.remove(socketId);
    }
}

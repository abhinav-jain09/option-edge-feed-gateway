package app.feedgateway.mtsession;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * The per-socket routing core (OE-DDD-001 §8.7). Owns the AppSession registry, the dual reverse
 * indexes (contract / underlying), the shared cache, and the Databento {@link SubscriptionManager}.
 *
 * <p>Isolation invariants (verified by tests):
 * <ul>
 *   <li><b>I1</b> — a socket only ever receives a record whose derived key is in its AppSession's
 *       index set, after the per-session epoch barrier and per-user strike filter (no leakage);</li>
 *   <li><b>I2</b> — the shared cache holds one entry per contract irrespective of subscriber count;</li>
 *   <li><b>I3</b> — all IBKR sessions resolve to the same contract key and receive identical fan-out.</li>
 * </ul>
 *
 * <p>Not internally synchronised; the gateway serialises mutation/routing on its event loop. Tests
 * drive it single-threaded.
 */
public final class SessionRoutingEngine {

    private static final String VIX = "VIX";

    private final ConcurrencyLimits limits;
    private final SubscriptionManager subscriptions;
    private final Clock clock;
    private final SharedSnapshotCache cache = new SharedSnapshotCache();

    private final Map<String, AppSession> appSessions = new LinkedHashMap<>();
    private final Map<String, Set<String>> userToApps = new LinkedHashMap<>();
    private final Map<String, String> socketToApp = new LinkedHashMap<>();
    private final Map<SubscriptionKey, Set<String>> appByContract = new LinkedHashMap<>();
    private final Map<UnderlyingKey, Set<String>> appByUnderlying = new LinkedHashMap<>();

    public SessionRoutingEngine(ConcurrencyLimits limits, SubscriptionManager subscriptions) {
        this(limits, subscriptions, Clock.systemUTC());
    }

    public SessionRoutingEngine(ConcurrencyLimits limits, SubscriptionManager subscriptions, Clock clock) {
        this.limits = Objects.requireNonNull(limits, "limits");
        this.subscriptions = Objects.requireNonNull(subscriptions, "subscriptions");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    // ---------------------------------------------------------------------
    // Registration & lifecycle
    // ---------------------------------------------------------------------

    /**
     * Register a new AppSession. Enforces global and per-user concurrency limits (FR-27) and the
     * source entitlement (FR-12), and acquires the Databento subscription where applicable.
     */
    public AppSession registerAppSession(String appSessionId, String userId, Selection selection,
                                         Set<String> entitlements) {
        return registerAppSession(appSessionId, userId, selection, entitlements,
                ApprovalState.APPROVED, UserSessionPolicy.systemDefault());
    }

    /**
     * Register a new AppSession with explicit approval state and session policy. Rejects
     * unapproved users (FR-15), enforces concurrency limits (FR-27) and source entitlement (FR-12),
     * stamps activity/deadline timestamps from the engine clock, and acquires the Databento
     * subscription where applicable.
     */
    public AppSession registerAppSession(String appSessionId, String userId, Selection selection,
                                         Set<String> entitlements, ApprovalState approvalState,
                                         UserSessionPolicy policy) {
        Objects.requireNonNull(appSessionId, "appSessionId");
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(selection, "selection");
        Objects.requireNonNull(approvalState, "approvalState");
        Objects.requireNonNull(policy, "policy");
        if (appSessions.containsKey(appSessionId)) {
            throw new IllegalStateException("AppSession already registered: " + appSessionId);
        }
        if (!approvalState.grantsAccess()) {
            throw new NotApprovedException("User " + userId + " is not approved (" + approvalState + ")");
        }
        if (!EntitlementPolicy.canSelect(selection.source(), entitlements)) {
            throw new NotEntitledException("User " + userId + " not entitled to source " + selection.source());
        }
        if (appSessions.size() >= limits.maxTotalAppSessions()) {
            throw new CapacityException(CapacityException.Limit.TOTAL_APP_SESSIONS,
                    "Global AppSession limit reached (" + limits.maxTotalAppSessions() + ")");
        }
        Set<String> userApps = userToApps.computeIfAbsent(userId, u -> new LinkedHashSet<>());
        if (userApps.size() >= limits.maxAppSessionsPerUser()) {
            throw new CapacityException(CapacityException.Limit.APP_SESSIONS_PER_USER,
                    "Per-user AppSession limit reached for " + userId + " (" + limits.maxAppSessionsPerUser() + ")");
        }

        AppSession app = new AppSession(appSessionId, userId, entitlements == null ? Set.of() : entitlements,
                selection, 1L, policy, clock.millis());
        appSessions.put(appSessionId, app);
        userApps.add(appSessionId);
        addToIndexes(app, selection);
        if (selection.source() == MarketDataSource.DATABENTO) {
            subscriptions.acquire(selection.subscriptionKey(), appSessionId, selection.strikeWindow());
        }
        return app;
    }

    /** Attach a WebSocket to an AppSession, enforcing the per-AppSession socket limit (FR-27). */
    public void attachSocket(String appSessionId, String socketId) {
        AppSession app = require(appSessionId);
        if (socketToApp.containsKey(socketId)) {
            throw new IllegalStateException("Socket already attached: " + socketId);
        }
        if (app.socketIds().size() >= limits.maxSocketsPerAppSession()) {
            throw new CapacityException(CapacityException.Limit.SOCKETS_PER_APP_SESSION,
                    "Per-AppSession socket limit reached for " + appSessionId + " (" + limits.maxSocketsPerAppSession() + ")");
        }
        socketToApp.put(socketId, appSessionId);
        app.addSocket(socketId);
        app.touch(clock.millis());
    }

    /** Detach a socket; the AppSession persists (grace window is the caller's policy, FR-11/§6.2). */
    public void detachSocket(String socketId) {
        String appId = socketToApp.remove(socketId);
        if (appId != null) {
            AppSession app = appSessions.get(appId);
            if (app != null) {
                app.removeSocket(socketId);
            }
        }
    }

    /**
     * Change a session's selection: reindex, release/acquire Databento subscriptions, and raise the
     * per-session epoch so stale pre-switch records are dropped for this session only (§8.3).
     */
    public void changeSelection(String appSessionId, Selection newSelection) {
        AppSession app = require(appSessionId);
        Objects.requireNonNull(newSelection, "newSelection");
        if (!EntitlementPolicy.canSelect(newSelection.source(), app.entitlements())) {
            throw new NotEntitledException("AppSession " + appSessionId + " not entitled to source " + newSelection.source());
        }
        Selection old = app.selection();
        removeFromIndexes(app, old);
        if (old.source() == MarketDataSource.DATABENTO) {
            subscriptions.release(old.subscriptionKey(), appSessionId);
        }
        app.setSelection(newSelection);
        addToIndexes(app, newSelection);
        if (newSelection.source() == MarketDataSource.DATABENTO) {
            subscriptions.acquire(newSelection.subscriptionKey(), appSessionId, newSelection.strikeWindow());
        }
        app.bumpEpoch();
        app.touch(clock.millis());
    }

    /** Refresh the activity timestamp for an AppSession (heartbeat / data ack), deferring idle timeout. */
    public void touchActivity(String appSessionId) {
        AppSession app = appSessions.get(appSessionId);
        if (app != null) {
            app.touch(clock.millis());
        }
    }

    /**
     * Fully tear down an AppSession (idle/max/logout/suspend): unindex, release subs, drop sockets.
     *
     * @return the ids of the sockets that were attached and must now be closed by the caller.
     */
    public Set<String> teardownAppSession(String appSessionId) {
        AppSession app = appSessions.remove(appSessionId);
        if (app == null) {
            return Set.of();
        }
        Set<String> closedSockets = new LinkedHashSet<>(app.socketIds());
        removeFromIndexes(app, app.selection());
        if (app.selection().source() == MarketDataSource.DATABENTO) {
            subscriptions.release(app.selection().subscriptionKey(), appSessionId);
        }
        for (String socketId : closedSockets) {
            socketToApp.remove(socketId);
        }
        Set<String> userApps = userToApps.get(app.userId());
        if (userApps != null) {
            userApps.remove(appSessionId);
            if (userApps.isEmpty()) {
                userToApps.remove(app.userId());
            }
        }
        return closedSockets;
    }

    /** Outcome of an expiry sweep: which AppSessions ended and which sockets must be closed. */
    public record SweepResult(Set<String> expiredAppSessionIds, Set<String> closedSocketIds) {
    }

    /**
     * Tear down every AppSession whose idle timeout or max-session deadline has passed (FR-18).
     * Returns the affected AppSession ids and the sockets the caller must close.
     */
    public SweepResult sweepExpired() {
        long now = clock.millis();
        Set<String> expired = new LinkedHashSet<>();
        Set<String> closed = new LinkedHashSet<>();
        for (AppSession app : new ArrayList<>(appSessions.values())) {
            if (app.isIdleExpired(now) || app.isMaxExpired(now)) {
                expired.add(app.id());
                closed.addAll(teardownAppSession(app.id()));
            }
        }
        return new SweepResult(expired, closed);
    }

    /**
     * Force-logout / suspend a user: tear down all of their AppSessions immediately (FR-22/FR-23).
     *
     * @return the sockets the caller must close.
     */
    public Set<String> forceLogoutUser(String userId) {
        Set<String> userApps = userToApps.get(userId);
        if (userApps == null) {
            return Set.of();
        }
        Set<String> closed = new LinkedHashSet<>();
        for (String appId : new ArrayList<>(userApps)) {
            closed.addAll(teardownAppSession(appId));
        }
        return closed;
    }

    // ---------------------------------------------------------------------
    // Routing
    // ---------------------------------------------------------------------

    /**
     * Resolve the set of socket ids that should receive {@code record}, applying source membership,
     * the per-session epoch barrier, the entitlement double-check, and the per-user strike filter.
     * The shared cache is updated for contract records regardless of subscriber count (NFR-3).
     */
    public Set<String> route(RoutableRecord record) {
        Optional<RoutingTarget> targetOpt = RoutingKeyDeriver.derive(record);
        if (targetOpt.isEmpty()) {
            return Set.of();
        }
        RoutingTarget target = targetOpt.get();

        List<String> candidates;
        boolean contractScoped;
        SubscriptionKey contractKey = null;
        switch (target) {
            case RoutingTarget.Contract c -> {
                contractScoped = true;
                contractKey = c.key();
                cache.put(contractKey, Boolean.TRUE); // one entry per contract (I2)
                candidates = snapshot(appByContract.get(contractKey));
            }
            case RoutingTarget.Underlying u -> {
                contractScoped = false;
                candidates = snapshot(appByUnderlying.get(u.key()));
            }
        }
        if (candidates.isEmpty()) {
            return Set.of();
        }

        Set<String> sockets = new LinkedHashSet<>();
        for (String appId : candidates) {
            AppSession app = appSessions.get(appId);
            if (app == null) {
                continue;
            }
            if (!passesBarrier(app, record.selectionEpoch())) {
                continue;
            }
            if (!EntitlementPolicy.canReceive(target.source(), app.entitlements())) {
                continue;
            }
            if (contractScoped && record.strike().isPresent()
                    && !app.selection().strikeWindow().contains(record.strike().getAsDouble())) {
                continue; // per-user strike delivery filter
            }
            sockets.addAll(app.socketIds());
        }
        return sockets;
    }

    private static boolean passesBarrier(AppSession app, long recordEpoch) {
        return recordEpoch <= 0L || app.epoch() <= 0L || recordEpoch >= app.epoch();
    }

    /**
     * True if this specific socket should receive {@code record} — same filter as live routing
     * ({@link #route}). Used for per-session filtered cached-state replay on connect (FR-11).
     */
    public boolean shouldDeliverToSocket(RoutableRecord record, String socketId) {
        return route(record).contains(socketId);
    }

    // ---------------------------------------------------------------------
    // Index maintenance
    // ---------------------------------------------------------------------

    private void addToIndexes(AppSession app, Selection selection) {
        appByContract.computeIfAbsent(selection.subscriptionKey(), k -> new LinkedHashSet<>()).add(app.id());
        for (UnderlyingKey uk : underlyingKeys(selection)) {
            appByUnderlying.computeIfAbsent(uk, k -> new LinkedHashSet<>()).add(app.id());
        }
    }

    private void removeFromIndexes(AppSession app, Selection selection) {
        removeFromIndex(appByContract, selection.subscriptionKey(), app.id());
        for (UnderlyingKey uk : underlyingKeys(selection)) {
            removeFromIndex(appByUnderlying, uk, app.id());
        }
    }

    private static <K> void removeFromIndex(Map<K, Set<String>> index, K key, String appId) {
        Set<String> set = index.get(key);
        if (set != null) {
            set.remove(appId);
            if (set.isEmpty()) {
                index.remove(key);
            }
        }
    }

    /** A session is interested in its symbol's underlying plus the always-relevant VIX (§8.6). */
    private static List<UnderlyingKey> underlyingKeys(Selection selection) {
        List<UnderlyingKey> keys = new ArrayList<>(2);
        keys.add(new UnderlyingKey(selection.source(), selection.underlying()));
        if (!VIX.equalsIgnoreCase(selection.underlying())) {
            keys.add(new UnderlyingKey(selection.source(), VIX));
        }
        return keys;
    }

    private static List<String> snapshot(Set<String> set) {
        return set == null ? List.of() : new ArrayList<>(set);
    }

    private AppSession require(String appSessionId) {
        AppSession app = appSessions.get(appSessionId);
        if (app == null) {
            throw new IllegalStateException("Unknown AppSession: " + appSessionId);
        }
        return app;
    }

    // ---------------------------------------------------------------------
    // Observability (used by tests / metrics)
    // ---------------------------------------------------------------------

    public int appSessionCount() {
        return appSessions.size();
    }

    public int appSessionCountForUser(String userId) {
        Set<String> apps = userToApps.get(userId);
        return apps == null ? 0 : apps.size();
    }

    public Optional<AppSession> appSession(String appSessionId) {
        return Optional.ofNullable(appSessions.get(appSessionId));
    }

    public SubscriptionManager subscriptions() {
        return subscriptions;
    }

    public SharedSnapshotCache cache() {
        return cache;
    }

    public int distinctContractIndexSize() {
        return appByContract.size();
    }

    public Map<SubscriptionKey, Integer> contractFanoutSizes() {
        Map<SubscriptionKey, Integer> out = new LinkedHashMap<>();
        appByContract.forEach((k, v) -> out.put(k, v.size()));
        return Collections.unmodifiableMap(out);
    }
}

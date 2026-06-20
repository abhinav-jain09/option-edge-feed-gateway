package app.feedgateway.mtsession;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Persistent per-user application session (OE-DDD-001 §6.1). Carries the user's approval state,
 * resolved session policy, and the activity/deadline timestamps that drive idle-timeout and
 * max-session enforcement (§6.3/§6.6, FR-17/FR-18). Selection, epoch, sockets, and activity are
 * mutated only by {@link SessionRoutingEngine}.
 */
public final class AppSession {

    private final String id;
    private final String userId;
    // Refreshed atomically from every verified token (role revocation must not persist for the session's
    // life). Mutated only under the engine write lock; volatile so the read in route() always sees the latest.
    private volatile Set<String> entitlements;
    private final Set<String> socketIds = new LinkedHashSet<>();
    private final UserSessionPolicy policy;
    private final long createdAtMillis;
    private final long maxDeadlineMillis; // 0 ⇒ unlimited

    private Selection selection;
    private long epoch;
    private long lastActivityAtMillis;
    private volatile boolean replaying; // per-session Live↔Replay mode (live delivery suppressed while true)

    AppSession(String id, String userId, Set<String> entitlements, Selection selection, long epoch,
               UserSessionPolicy policy, long nowMillis) {
        this.id = id;
        this.userId = userId;
        this.entitlements = Set.copyOf(entitlements);
        this.selection = selection;
        this.epoch = epoch;
        this.policy = Objects.requireNonNull(policy, "policy");
        this.createdAtMillis = nowMillis;
        this.lastActivityAtMillis = nowMillis;
        this.maxDeadlineMillis = policy.isUnlimited()
                ? 0L
                : nowMillis + (long) policy.maxSessionMinutes() * 60_000L;
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

    public UserSessionPolicy policy() {
        return policy;
    }

    public long createdAtMillis() {
        return createdAtMillis;
    }

    public long lastActivityAtMillis() {
        return lastActivityAtMillis;
    }

    public long maxDeadlineMillis() {
        return maxDeadlineMillis;
    }

    public Set<String> socketIds() {
        return Collections.unmodifiableSet(socketIds);
    }

    public boolean hasSockets() {
        return !socketIds.isEmpty();
    }

    /** True while this session is in historical-replay mode; live records are not delivered to it. */
    public boolean isReplaying() {
        return replaying;
    }

    /** True if idle longer than the policy's idle timeout (FR-18). */
    public boolean isIdleExpired(long nowMillis) {
        long idleMillis = (long) policy.idleTimeoutMinutes() * 60_000L;
        return nowMillis - lastActivityAtMillis >= idleMillis;
    }

    /** True if the absolute max-session deadline has passed (FR-18); never for unlimited sessions. */
    public boolean isMaxExpired(long nowMillis) {
        return maxDeadlineMillis > 0L && nowMillis >= maxDeadlineMillis;
    }

    // ---- package-private mutation, driven by the engine ----

    void setSelection(Selection selection) {
        this.selection = selection;
    }

    /** Replace entitlements with the roles from a freshly-verified token (FR-12 role revocation). */
    void setEntitlements(Set<String> entitlements) {
        this.entitlements = entitlements == null ? Set.of() : Set.copyOf(entitlements);
    }

    void bumpEpoch() {
        this.epoch++;
    }

    void setReplaying(boolean replaying) {
        this.replaying = replaying;
    }

    void touch(long nowMillis) {
        this.lastActivityAtMillis = nowMillis;
    }

    boolean addSocket(String socketId) {
        return socketIds.add(socketId);
    }

    boolean removeSocket(String socketId) {
        return socketIds.remove(socketId);
    }
}

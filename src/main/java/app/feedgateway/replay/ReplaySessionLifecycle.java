package app.feedgateway.replay;

import org.springframework.stereotype.Component;

/**
 * Binds a session's replay lifecycle to the WebSocket connection lifecycle. On connect the session's
 * owner is recorded; on disconnect (clean close OR transport error) ALL replay state for that socket id
 * is torn down — ownership, the data-plane binding, and the registry's state-machine entry — so a closed
 * (or reused) session id can never retain a replay attachment or stale state.
 */
@Component
public final class ReplaySessionLifecycle {

    private final ReplaySessionOwnership ownership;
    private final ReplaySessionBindings bindings;
    private final ReplaySessionRegistry registry;

    public ReplaySessionLifecycle(ReplaySessionOwnership ownership, ReplaySessionBindings bindings,
                                  ReplaySessionRegistry registry) {
        this.ownership = ownership;
        this.bindings = bindings;
        this.registry = registry;
    }

    /** Record the session's authenticated owner (no-op when {@code principal} is null, e.g. auth off). */
    public void onConnect(String sessionId, String principal) {
        if (principal != null) {
            ownership.bind(sessionId, principal);
        }
    }

    /** Tear down EVERYTHING for the socket so no replay state leaks past the connection. */
    public void onDisconnect(String sessionId) {
        ownership.unbind(sessionId);
        bindings.clear(sessionId);
        registry.remove(sessionId);
    }
}

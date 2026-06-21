package app.feedgateway.replay;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class ReplaySessionLifecycleTest {

    private final ReplaySessionOwnership ownership = new ReplaySessionOwnership();
    private final ReplaySessionBindings bindings = new ReplaySessionBindings();
    private final ReplaySessionRegistry registry = new ReplaySessionRegistry();
    private final ReplaySessionLifecycle lifecycle = new ReplaySessionLifecycle(ownership, bindings, registry);

    @Test
    void disconnectTearsDownAllReplayStateForTheSocket() {
        lifecycle.onConnect("s1", "iss|alice");
        assertTrue(ownership.owns("s1", "iss|alice"));
        // simulate an active replay binding + registry state
        registry.attach("s1", "r-1", true);
        bindings.bind("s1", "r-1");
        assertTrue(registry.get("s1") != null);
        assertTrue(bindings.runFor("s1").isPresent());

        lifecycle.onDisconnect("s1");

        assertFalse(ownership.owns("s1", "iss|alice"), "ownership cleared");
        assertEquals(Optional.empty(), bindings.runFor("s1"), "binding cleared");
        assertNull(registry.get("s1"), "registry state cleared");
    }

    @Test
    void connectWithNoPrincipalDoesNotBindOwnership() {
        lifecycle.onConnect("s1", null); // auth disabled → no 'sub'
        assertFalse(ownership.owns("s1", "iss|alice"));
    }
}

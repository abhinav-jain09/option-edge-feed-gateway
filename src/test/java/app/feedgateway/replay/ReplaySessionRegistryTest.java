package app.feedgateway.replay;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ReplaySessionRegistryTest {

    @Test
    void registersLiveAndDrivesTheLifecycleAtomically() {
        ReplaySessionRegistry reg = new ReplaySessionRegistry();
        assertEquals(ReplayMode.LIVE, reg.register("s1").mode());

        assertTrue(reg.attach("s1", "r-1", true).accepted());
        assertEquals(ReplayMode.REPLAY_ATTACHING, reg.get("s1").mode());
        assertTrue(reg.streaming("s1").accepted());
        assertEquals(ReplayMode.REPLAY_STREAMING, reg.get("s1").mode());
        assertEquals("r-1", reg.get("s1").runId());
        assertTrue(reg.terminal("s1").accepted());
        assertTrue(reg.returnToLive("s1").accepted());
        assertTrue(reg.liveResumed("s1").accepted());
        assertEquals(ReplayMode.LIVE, reg.get("s1").mode());
    }

    @Test
    void rejectedTransitionLeavesStoredStateUnchanged() {
        ReplaySessionRegistry reg = new ReplaySessionRegistry();
        reg.register("s1");
        assertFalse(reg.attach("s1", "r-1", false).accepted()); // unauthorized
        assertEquals(ReplayMode.LIVE, reg.get("s1").mode(), "registry must not store a rejected transition");
    }

    @Test
    void twoSessionsAreIsolated() {
        ReplaySessionRegistry reg = new ReplaySessionRegistry();
        reg.attach("alice", "r-1", true);
        reg.streaming("alice");
        reg.attach("bob", "r-2", true);

        assertEquals("r-1", reg.get("alice").runId());
        assertEquals(ReplayMode.REPLAY_STREAMING, reg.get("alice").mode());
        assertEquals("r-2", reg.get("bob").runId());
        assertEquals(ReplayMode.REPLAY_ATTACHING, reg.get("bob").mode());

        // returning alice to live does not touch bob
        reg.returnToLive("alice");
        assertEquals(ReplayMode.RETURN_TO_LIVE, reg.get("alice").mode());
        assertEquals("r-2", reg.get("bob").runId());
    }

    @Test
    void removeDropsTheSession() {
        ReplaySessionRegistry reg = new ReplaySessionRegistry();
        reg.register("s1");
        reg.remove("s1");
        assertNull(reg.get("s1"));
    }
}

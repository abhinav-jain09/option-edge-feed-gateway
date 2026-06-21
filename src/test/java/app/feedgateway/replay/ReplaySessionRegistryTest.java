package app.feedgateway.replay;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
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

    @Test
    void rejectedTransitionOnAnUnknownSessionCreatesNoState() {
        ReplaySessionRegistry reg = new ReplaySessionRegistry();
        // an unauthorized attach (or any illegal transition) on a never-seen session must NOT insert state
        assertFalse(reg.attach("ghost", "r-1", false).accepted());
        assertNull(reg.get("ghost"), "a refused transition must not materialise a session entry");
        assertFalse(reg.returnToLive("ghost2").accepted());
        assertNull(reg.get("ghost2"));
    }

    @Test
    void concurrentTransitionsAreAtomicAndIsolatedPerSession() throws Exception {
        ReplaySessionRegistry reg = new ReplaySessionRegistry();
        int sessions = 8, iterations = 500;
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(16);
        java.util.concurrent.CountDownLatch start = new java.util.concurrent.CountDownLatch(1);
        List<java.util.concurrent.Future<?>> futures = new ArrayList<>();
        for (int s = 0; s < sessions; s++) {
            String sid = "s" + s;
            // two threads per session racing the same lifecycle; transitions must stay coherent.
            for (int t = 0; t < 2; t++) {
                futures.add(pool.submit(() -> {
                    try { start.await(); } catch (InterruptedException ignored) { }
                    for (int i = 0; i < iterations; i++) {
                        reg.attach(sid, "r-" + (i % 3), true);
                        reg.streaming(sid);
                        reg.terminal(sid);
                        reg.returnToLive(sid);
                        reg.liveResumed(sid);
                        // every observed state must belong to THIS session and be a valid mode
                        ReplaySessionState st = reg.get(sid);
                        org.junit.jupiter.api.Assertions.assertEquals(sid, st.sessionId());
                        org.junit.jupiter.api.Assertions.assertNotNull(st.mode());
                    }
                }));
            }
        }
        start.countDown();
        for (var f : futures) {
            f.get(30, java.util.concurrent.TimeUnit.SECONDS); // any thread assertion failure surfaces here
        }
        pool.shutdownNow();
        // each session ended on a coherent state keyed to itself (no cross-session corruption)
        for (int s = 0; s < sessions; s++) {
            assertEquals("s" + s, reg.get("s" + s).sessionId());
        }
    }
}

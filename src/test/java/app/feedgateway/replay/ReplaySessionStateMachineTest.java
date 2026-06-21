package app.feedgateway.replay;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.feedgateway.replay.ReplaySessionStateMachine.Transition;
import org.junit.jupiter.api.Test;

class ReplaySessionStateMachineTest {

    @Test
    void fullLifecycleLiveToReplayToTerminalToLive() {
        ReplaySessionState s = ReplaySessionState.live("sess-1");
        assertEquals(ReplayMode.LIVE, s.mode());

        Transition t = ReplaySessionStateMachine.attach(s, "r-1", true);
        assertTrue(t.accepted());
        assertEquals(ReplayMode.REPLAY_ATTACHING, t.state().mode());
        assertEquals("r-1", t.state().runId());

        t = ReplaySessionStateMachine.streaming(t.state());
        assertEquals(ReplayMode.REPLAY_STREAMING, t.state().mode());

        t = ReplaySessionStateMachine.terminal(t.state());
        assertEquals(ReplayMode.REPLAY_TERMINAL, t.state().mode());

        t = ReplaySessionStateMachine.returnToLive(t.state());
        assertEquals(ReplayMode.RETURN_TO_LIVE, t.state().mode());
        assertNull(t.state().runId(), "runId is cleared when leaving replay");

        t = ReplaySessionStateMachine.liveResumed(t.state());
        assertEquals(ReplayMode.LIVE, t.state().mode());
    }

    @Test
    void attachRequiresAuthorizationEveryTime() {
        ReplaySessionState s = ReplaySessionState.live("sess-1");
        Transition denied = ReplaySessionStateMachine.attach(s, "r-1", false);
        assertFalse(denied.accepted(), "an unauthorized attach must be refused");
        assertEquals("attach not authorized", denied.rejection().orElseThrow());
        assertEquals(ReplayMode.LIVE, denied.state().mode(), "state unchanged on refusal");
        assertFalse(denied.cacheCleared());
    }

    @Test
    void everyTransitionClearsCachesAndBumpsGeneration() {
        ReplaySessionState s = ReplaySessionState.live("sess-1");
        long g0 = s.generation();
        Transition a = ReplaySessionStateMachine.attach(s, "r-1", true);
        assertTrue(a.cacheCleared());
        assertEquals(g0 + 1, a.state().generation());
        Transition b = ReplaySessionStateMachine.streaming(a.state());
        assertTrue(b.cacheCleared());
        assertEquals(g0 + 2, b.state().generation());
        Transition c = ReplaySessionStateMachine.returnToLive(b.state());
        assertTrue(c.cacheCleared());
        assertEquals(g0 + 3, c.state().generation());
    }

    @Test
    void reAttachingToADifferentRunReauthorizesAndClears_noCrossRunBleed() {
        ReplaySessionState s = ReplaySessionStateMachine.streaming(
                ReplaySessionStateMachine.attach(ReplaySessionState.live("sess-1"), "r-1", true).state()).state();
        assertEquals("r-1", s.runId());

        // attaching to run r-2 while streaming r-1 must re-authorize (fail closed if not) ...
        assertFalse(ReplaySessionStateMachine.attach(s, "r-2", false).accepted());

        // ... and on a fresh authorization it switches run, clears caches, bumps the generation.
        Transition t = ReplaySessionStateMachine.attach(s, "r-2", true);
        assertTrue(t.accepted());
        assertEquals("r-2", t.state().runId());
        assertTrue(t.cacheCleared());
        assertTrue(t.state().generation() > s.generation());
    }

    @Test
    void illegalTransitionsAreRejectedAndStateIsUnchanged() {
        ReplaySessionState live = ReplaySessionState.live("sess-1");
        assertFalse(ReplaySessionStateMachine.streaming(live).accepted());     // no streaming from LIVE
        assertFalse(ReplaySessionStateMachine.terminal(live).accepted());      // no terminal from LIVE
        assertFalse(ReplaySessionStateMachine.returnToLive(live).accepted());  // already LIVE
        assertFalse(ReplaySessionStateMachine.liveResumed(live).accepted());   // not returning
        assertFalse(ReplaySessionStateMachine.attach(live, "  ", true).accepted()); // blank runId
    }
}

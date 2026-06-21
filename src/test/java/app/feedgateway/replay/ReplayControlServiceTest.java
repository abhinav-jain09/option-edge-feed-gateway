package app.feedgateway.replay;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.feedgateway.replay.ReplayControlService.ReplayControlException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ReplayControlServiceTest {

    private final List<String> transitions = new ArrayList<>();
    private final List<String> authChecks = new ArrayList<>();

    private ReplayControlService service(boolean authorize) {
        ReplaySessionRegistry reg = new ReplaySessionRegistry();
        return new ReplayControlService(reg,
                (sessionId, runId, principal) -> { authChecks.add(sessionId + ":" + runId + ":" + principal); return authorize; },
                (sessionId, t) -> transitions.add(sessionId + "->" + t.state().mode() + " clear=" + t.cacheCleared()));
    }

    @Test
    void attachReauthorizesAndFiresCacheClearingTransition() {
        ReplayMode mode = service(true).attach("s1", "r-1", "iss|alice");
        assertEquals(ReplayMode.REPLAY_ATTACHING, mode);
        assertEquals(List.of("s1:r-1:iss|alice"), authChecks, "must re-authorize on attach");
        assertTrue(transitions.contains("s1->REPLAY_ATTACHING clear=true"), "must fire a cache-clearing transition");
    }

    @Test
    void unauthorizedAttachIsRefusedAndFiresNoTransition() {
        ReplayControlService svc = service(false);
        assertThrows(ReplayControlException.class, () -> svc.attach("s1", "r-1", "iss|mallory"));
        assertEquals(1, authChecks.size(), "authorization was checked");
        assertTrue(transitions.isEmpty(), "a refused attach must not fire a data-plane transition");
    }

    @Test
    void everyAttachReauthorizes_noTrustOfPriorDecision() {
        ReplayControlService svc = service(true);
        svc.attach("s1", "r-1", "iss|alice");
        svc.markStreaming("s1");
        svc.attach("s1", "r-2", "iss|alice"); // switching runs re-authorizes again
        assertEquals(2, authChecks.size(), "each attach re-authorizes; prior authorization is never reused");
    }

    @Test
    void fullControlLifecycleReturnsCorrectModes() {
        ReplayControlService svc = service(true);
        assertEquals(ReplayMode.REPLAY_ATTACHING, svc.attach("s1", "r-1", "p"));
        assertEquals(ReplayMode.REPLAY_STREAMING, svc.markStreaming("s1"));
        assertEquals(ReplayMode.REPLAY_TERMINAL, svc.markTerminal("s1"));
        assertEquals(ReplayMode.RETURN_TO_LIVE, svc.returnToLive("s1"));
        assertEquals(ReplayMode.LIVE, svc.markLiveResumed("s1"));
        // a cache-clearing transition fired on every edge
        assertEquals(5, transitions.size());
        assertTrue(transitions.stream().allMatch(t -> t.endsWith("clear=true")));
    }

    @Test
    void illegalControlCallIsRejected() {
        ReplayControlService svc = service(true);
        // returnToLive on a session that is still LIVE is an illegal transition → refused.
        assertThrows(ReplayControlException.class, () -> svc.returnToLive("s1"));
    }
}

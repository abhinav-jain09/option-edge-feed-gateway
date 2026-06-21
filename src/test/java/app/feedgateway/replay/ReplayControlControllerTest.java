package app.feedgateway.replay;

import static org.junit.jupiter.api.Assertions.assertEquals;

import app.feedgateway.GatewaySettings;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class ReplayControlControllerTest {

    private final ReplaySessionRegistry registry = new ReplaySessionRegistry();
    private final ReplaySessionOwnership ownership = new ReplaySessionOwnership();
    private final ReplaySessionBindings bindings = new ReplaySessionBindings();

    private ReplayControlController controller(boolean enabled, ReplayPrincipalResolver principals) {
        if (enabled) {
            System.setProperty("GATEWAY_REPLAY_ENABLED", "true");
        } else {
            System.clearProperty("GATEWAY_REPLAY_ENABLED");
        }
        ReplayControlService control = new ReplayConfig().replayControlService(registry, ownership, bindings);
        return new ReplayControlController(new GatewaySettings(), control, ownership, registry, principals);
    }

    @AfterEach
    void clearProp() {
        System.clearProperty("GATEWAY_REPLAY_ENABLED");
    }

    private static final ReplayPrincipalResolver ALICE =
            auth -> "Bearer good".equals(auth) ? Optional.of("iss|alice") : Optional.empty();

    @Test
    void disabledReplayReturns404() {
        var r = controller(false, ALICE).attach("Bearer good",
                new ReplayControlController.AttachRequest("s1", "r-1"));
        assertEquals(HttpStatus.NOT_FOUND, r.getStatusCode());
    }

    @Test
    void missingOrInvalidTokenReturns401() {
        ownership.bind("s1", "iss|alice");
        var r = controller(true, ALICE).attach(null, new ReplayControlController.AttachRequest("s1", "r-1"));
        assertEquals(HttpStatus.UNAUTHORIZED, r.getStatusCode());
    }

    @Test
    void nonOwnerReturns403() {
        ownership.bind("s1", "iss|bob"); // session owned by bob, caller is alice
        var r = controller(true, ALICE).attach("Bearer good", new ReplayControlController.AttachRequest("s1", "r-1"));
        assertEquals(HttpStatus.FORBIDDEN, r.getStatusCode());
    }

    @Test
    void ownerAttachReturns200_setsModeAndBinding() {
        ownership.bind("s1", "iss|alice");
        ResponseEntity<?> r = controller(true, ALICE).attach("Bearer good",
                new ReplayControlController.AttachRequest("s1", "r-1"));
        assertEquals(HttpStatus.OK, r.getStatusCode());
        assertEquals("REPLAY_ATTACHING", ((java.util.Map<?, ?>) r.getBody()).get("mode"));
        assertEquals(Optional.of("r-1"), bindings.runFor("s1"), "the data-plane binding was set");
        assertEquals(ReplayMode.REPLAY_ATTACHING, registry.get("s1").mode());
    }

    @Test
    void returnToLiveClearsTheBinding() {
        ownership.bind("s1", "iss|alice");
        ReplayControlController c = controller(true, ALICE);
        c.attach("Bearer good", new ReplayControlController.AttachRequest("s1", "r-1"));
        var r = c.returnToLive("Bearer good", new ReplayControlController.SessionRequest("s1"));
        assertEquals(HttpStatus.OK, r.getStatusCode());
        assertEquals(Optional.empty(), bindings.runFor("s1"), "binding cleared on return-to-live");
    }

    @Test
    void illegalTransitionReturns409() {
        ownership.bind("s1", "iss|alice");
        // return-to-live on a session still LIVE is illegal
        var r = controller(true, ALICE).returnToLive("Bearer good",
                new ReplayControlController.SessionRequest("s1"));
        assertEquals(HttpStatus.CONFLICT, r.getStatusCode());
    }
}

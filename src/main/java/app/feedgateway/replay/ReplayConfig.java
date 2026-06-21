package app.feedgateway.replay;

import app.feedgateway.GatewaySettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the per-session replay control plane. The {@link ReplayControlService} is built with:
 * <ul>
 *   <li>an {@link ReplayControlService.Authorizer} = "the principal owns the session"
 *       ({@link ReplaySessionOwnership}) — re-checked on every attach;</li>
 *   <li>a {@link ReplayControlService.TransitionListener} that keeps the control→data-plane
 *       {@link ReplaySessionBindings} in sync on every edge (bind the run when replaying, clear on the
 *       return to live), so the data-plane reader streams the right run to the right socket.</li>
 * </ul>
 */
@Configuration
public class ReplayConfig {

    @Bean
    public ReplaySessionRegistry replaySessionRegistry() {
        return new ReplaySessionRegistry();
    }

    @Bean
    public ReplayPrincipalResolver replayPrincipalResolver(GatewaySettings settings) {
        return new JwtReplayPrincipalResolver(settings);
    }

    @Bean
    public ReplayControlService replayControlService(ReplaySessionRegistry registry,
                                                     ReplaySessionOwnership ownership,
                                                     ReplaySessionBindings bindings) {
        ReplayControlService.Authorizer authorizer =
                (sessionId, runId, principal) -> ownership.owns(sessionId, principal);
        ReplayControlService.TransitionListener listener = (sessionId, transition) -> {
            switch (transition.state().mode()) {
                case REPLAY_ATTACHING -> bindings.bind(sessionId, transition.state().runId());
                case LIVE, RETURN_TO_LIVE -> bindings.clear(sessionId);
                default -> { /* STREAMING / TERMINAL keep the existing binding */ }
            }
        };
        return new ReplayControlService(registry, authorizer, listener);
    }
}

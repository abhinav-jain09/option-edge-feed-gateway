package app.feedgateway;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * P0 (FR-18, operationally-evidenced expiry): the domain models idle-timeout and maximum-session deadlines,
 * but nothing was invoking the sweep. This scheduler runs it: every tick it tears down each AppSession past
 * its idle timeout or absolute max-session deadline and force-closes that session's sockets, so a long-lived
 * or idle session cannot keep streaming forever.
 *
 * <p>Only wired when {@code gateway.auth.enabled=true} (the same condition as the rest of the secure mode).
 */
@Component
@ConditionalOnProperty(name = "gateway.auth.enabled", havingValue = "true")
public class SessionExpiryReaper {

    private static final Logger LOG = LoggerFactory.getLogger(SessionExpiryReaper.class);

    private final FeedGatewayService gatewayService;

    public SessionExpiryReaper(FeedGatewayService gatewayService) {
        this.gatewayService = gatewayService;
    }

    @Scheduled(
            initialDelayString = "${GATEWAY_SESSION_EXPIRY_SWEEP_MS:30000}",
            fixedDelayString = "${GATEWAY_SESSION_EXPIRY_SWEEP_MS:30000}")
    public void sweep() {
        int expired = gatewayService.sweepExpiredSessions();
        if (expired > 0) {
            LOG.info("Expired {} idle/max-session AppSession(s) and closed their sockets", expired);
        }
    }
}

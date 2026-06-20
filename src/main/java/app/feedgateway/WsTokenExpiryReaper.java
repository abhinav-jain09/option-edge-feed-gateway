package app.feedgateway;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Periodically closes WebSocket sessions whose Keycloak token has expired. The token is validated only at
 * the handshake (see {@link WsJwtHandshakeInterceptor}), so a socket opened with a near-expiry token would
 * otherwise keep streaming live quotes indefinitely. This is the mid-session enforcement the handshake
 * cannot provide. No-op when WS auth is disabled.
 */
@Component
public class WsTokenExpiryReaper {

    private static final Logger LOG = LoggerFactory.getLogger(WsTokenExpiryReaper.class);

    private final FeedGatewayService gatewayService;
    private final GatewaySettings settings;

    public WsTokenExpiryReaper(FeedGatewayService gatewayService, GatewaySettings settings) {
        this.gatewayService = gatewayService;
        this.settings = settings;
    }

    @Scheduled(
            initialDelayString = "${WS_AUTH_EXPIRY_CHECK_MS:15000}",
            fixedDelayString = "${WS_AUTH_EXPIRY_CHECK_MS:15000}")
    public void sweep() {
        if (!settings.wsAuthEnabled()) {
            return;
        }
        int closed = gatewayService.closeExpiredAuthSessions(System.currentTimeMillis());
        if (closed > 0) {
            LOG.info("Closed {} WebSocket session(s) with expired tokens", closed);
        }
    }
}

package app.feedgateway;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Periodically closes WebSocket sessions whose Keycloak token has expired. The token is validated only at
 * the handshake (see {@link WsJwtHandshakeInterceptor} / the ticket handshake), so a socket opened with a
 * near-expiry token would otherwise keep streaming live quotes indefinitely. This is the mid-session
 * enforcement the handshake cannot provide. Active whenever EITHER legacy {@code WS_AUTH_ENABLED} or
 * multi-tenant {@code GATEWAY_AUTH_ENABLED} is on — both flows stamp {@code AUTH_EXPIRES_AT_ATTR} on the
 * session, so the reaper has the same enforcement contract in both modes. No-op when all auth is off.
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
        // Active whenever EITHER auth model enforces tokens: the legacy single-tenant `oc.bearer` flow
        // (WS_AUTH_ENABLED) or the multi-tenant flow (GATEWAY_AUTH_ENABLED — covers BOTH the ws-ticket
        // and the dual-auth bearer paths that share AUTH_EXPIRES_AT_ATTR).
        if (!settings.wsAuthEnabled() && !settings.authEnabled()) {
            return;
        }
        int closed = gatewayService.closeExpiredAuthSessions(System.currentTimeMillis());
        if (closed > 0) {
            LOG.info("Closed {} WebSocket session(s) with expired tokens", closed);
        }
    }
}

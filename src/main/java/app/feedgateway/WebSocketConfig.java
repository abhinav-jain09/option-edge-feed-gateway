package app.feedgateway;

import app.feedgateway.mtsession.gateway.TicketHandshakeInterceptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistration;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import java.util.Arrays;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {
    private final FeedWebSocketHandler handler;
    private final WsJwtHandshakeInterceptor jwtInterceptor;
    private final GatewaySettings settings;
    // Present ONLY when GATEWAY_AUTH_ENABLED=true (MtSessionAuthConfig). Its presence selects the
    // multi-tenant ticket handshake instead of the default oc.bearer JWT handshake.
    private final ObjectProvider<TicketHandshakeInterceptor> ticketInterceptorProvider;

    public WebSocketConfig(FeedWebSocketHandler handler,
                           WsJwtHandshakeInterceptor jwtInterceptor,
                           GatewaySettings settings,
                           ObjectProvider<TicketHandshakeInterceptor> ticketInterceptorProvider) {
        this.handler = handler;
        this.jwtInterceptor = jwtInterceptor;
        this.settings = settings;
        this.ticketInterceptorProvider = ticketInterceptorProvider;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        TicketHandshakeInterceptor ticketInterceptor = ticketInterceptorProvider.getIfAvailable();
        boolean ticketMode = ticketInterceptor != null;

        // Fail-closed origins: a wildcard is only acceptable while ALL auth is off (dev). If EITHER the
        // multi-tenant ticket auth OR the oc.bearer JWT auth is on, a '*' would let any site open an
        // authenticated socket — so demand an explicit allow-list at startup.
        String[] origins =
                resolveAllowedOrigins(ticketMode || settings.wsAuthEnabled(), settings.wsAllowedOrigins());

        WebSocketHandlerRegistration registration =
                registry.addHandler(handler, "/ws/events").setAllowedOrigins(origins);

        // DUAL-AUTH (GATEWAY_AUTH_ENABLED=true): BOTH the multi-tenant ticket handshake AND the legacy
        // {@code oc.bearer} bearer handshake are wired so the same gateway serves both UIs:
        //   - the existing option-chain web app, which offers {@code ["oc.bearer", <accessToken>]}
        //   - the multi-tenant replay UI, which offers {@code oe.ticket.<id>}
        // Order matters: ticket interceptor runs first; it passes through to the JWT interceptor when no
        // {@code oe.ticket.*} subprotocol is offered. The JWT interceptor then either binds via
        // WsTicketService.ensureAppSession() (multi-tenant) or validates standalone (legacy).
        // Legacy single-tenant mode (no GATEWAY_AUTH_ENABLED) wires only the JWT interceptor — the
        // WsTicketService bean does not exist, so this interceptor has no AppSession to bind.
        if (ticketMode) {
            registration.setHandshakeHandler(new app.feedgateway.mtsession.gateway.TicketSubProtocolHandshakeHandler());
            registration.addInterceptors(ticketInterceptor, jwtInterceptor);
        } else {
            registration.addInterceptors(jwtInterceptor);
        }
    }

    /**
     * Locked-down origins from WS_ALLOWED_ORIGINS. A wildcard is only sane while auth is disabled (dev);
     * once auth is on, a wildcard would let any site open an authenticated socket, so we fail closed at
     * startup and demand an explicit allow-list (mirrors the web app's issuer invariant).
     */
    static String[] resolveAllowedOrigins(boolean authEnabled, String csv) {
        String[] origins = Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .toArray(String[]::new);
        if (authEnabled && Arrays.stream(origins).anyMatch(o -> o.equals("*"))) {
            throw new IllegalStateException(
                    "WS_ALLOWED_ORIGINS must be an explicit allow-list (not '*') when auth is enabled "
                            + "(WS_AUTH_ENABLED or GATEWAY_AUTH_ENABLED)");
        }
        return origins;
    }
}

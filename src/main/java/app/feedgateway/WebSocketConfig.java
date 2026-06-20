package app.feedgateway;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import java.util.Arrays;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {
    private final FeedWebSocketHandler handler;
    private final WsJwtHandshakeInterceptor jwtInterceptor;
    private final GatewaySettings settings;

    public WebSocketConfig(FeedWebSocketHandler handler,
                           WsJwtHandshakeInterceptor jwtInterceptor,
                           GatewaySettings settings) {
        this.handler = handler;
        this.jwtInterceptor = jwtInterceptor;
        this.settings = settings;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, "/ws/events")
                .addInterceptors(jwtInterceptor)
                .setAllowedOrigins(resolveAllowedOrigins(settings.wsAuthEnabled(), settings.wsAllowedOrigins()));
    }

    /**
     * Locked-down origins from WS_ALLOWED_ORIGINS. A wildcard is only sane while auth is disabled (dev);
     * once WS_AUTH_ENABLED is on, a wildcard would let any site open an authenticated socket, so we fail
     * closed at startup and demand an explicit allow-list (mirrors the web app's issuer invariant).
     */
    static String[] resolveAllowedOrigins(boolean authEnabled, String csv) {
        String[] origins = Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .toArray(String[]::new);
        if (authEnabled && Arrays.stream(origins).anyMatch(o -> o.equals("*"))) {
            throw new IllegalStateException(
                    "WS_ALLOWED_ORIGINS must be an explicit allow-list (not '*') when WS_AUTH_ENABLED=true");
        }
        return origins;
    }
}

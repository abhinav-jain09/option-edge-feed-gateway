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
                .setAllowedOrigins(allowedOrigins());
    }

    /**
     * Locked-down origins from WS_ALLOWED_ORIGINS. A wildcard is only sane while auth is disabled (dev);
     * once WS_AUTH_ENABLED is on, an explicit allow-list must be configured or the handshake is wide open.
     */
    private String[] allowedOrigins() {
        return Arrays.stream(settings.wsAllowedOrigins().split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .toArray(String[]::new);
    }
}

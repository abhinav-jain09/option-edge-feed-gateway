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
    private final GatewaySettings settings;
    private final ObjectProvider<TicketHandshakeInterceptor> interceptorProvider;

    public WebSocketConfig(FeedWebSocketHandler handler, GatewaySettings settings,
                           ObjectProvider<TicketHandshakeInterceptor> interceptorProvider) {
        this.handler = handler;
        this.settings = settings;
        this.interceptorProvider = interceptorProvider;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        String[] origins = Arrays.stream(settings.wsAllowedOrigins().split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toArray(String[]::new);
        WebSocketHandlerRegistration registration =
                registry.addHandler(handler, "/ws/events").setAllowedOrigins(origins);

        // Present only when GATEWAY_AUTH_ENABLED=true (MtSessionAuthConfig); otherwise legacy behaviour.
        TicketHandshakeInterceptor interceptor = interceptorProvider.getIfAvailable();
        if (interceptor != null) {
            registration.addInterceptors(interceptor);
        }
    }
}

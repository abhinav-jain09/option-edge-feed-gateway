package app.feedgateway;

import app.feedgateway.mtsession.SessionRoutingEngine;
import app.feedgateway.mtsession.gateway.TicketHandshakeInterceptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Component
public class FeedWebSocketHandler extends TextWebSocketHandler {
    private final FeedGatewayService gatewayService;
    private final ObjectProvider<SessionRoutingEngine> engineProvider;

    public FeedWebSocketHandler(FeedGatewayService gatewayService,
                                ObjectProvider<SessionRoutingEngine> engineProvider) {
        this.gatewayService = gatewayService;
        this.engineProvider = engineProvider;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        // When auth is enabled, the handshake bound an AppSession id; attach this socket to it.
        SessionRoutingEngine engine = engineProvider.getIfAvailable();
        Object appSessionId = session.getAttributes().get(TicketHandshakeInterceptor.ATTR_APP_SESSION_ID);
        if (engine != null && appSessionId instanceof String id) {
            try {
                engine.attachSocket(id, session.getId());
            } catch (RuntimeException ignored) {
                // AppSession may have expired between ticket mint and connect; fall through to legacy add.
            }
        }
        gatewayService.addClient(session);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        detach(session);
        gatewayService.removeClient(session);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        detach(session);
        gatewayService.removeClient(session);
    }

    private void detach(WebSocketSession session) {
        SessionRoutingEngine engine = engineProvider.getIfAvailable();
        if (engine != null) {
            engine.detachSocket(session.getId());
        }
    }
}

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
        SessionRoutingEngine engine = engineProvider.getIfAvailable();
        // Auth on (the session router bean exists ⟺ gateway.auth.enabled=true): the handshake has
        // already redeemed the single-use ticket and bound an AppSession id. This socket MUST attach
        // to that session — if it cannot, REJECT it. A redeemed ticket must never leave an
        // unattached live client (no AppSession to route or tear it down), so addClient is skipped.
        if (engine != null) {
            Object attr = session.getAttributes().get(TicketHandshakeInterceptor.ATTR_APP_SESSION_ID);
            if (!(attr instanceof String id) || id.isBlank()) {
                reject(session, "no session bound");
                return;
            }
            try {
                // attachSocket validates all limits before mutating, so a throw leaves no partial state.
                engine.attachSocket(id, session.getId());
            } catch (RuntimeException attachFailed) {
                // AppSession expired between ticket mint and connect, or the per-AppSession socket
                // cap was reached. Close the socket; never register it as a live client.
                reject(session, "attach rejected");
                return;
            }
        }
        gatewayService.addClient(session);
    }

    /** Best-effort close of a socket we refuse to admit, so no unattached client lingers. */
    private static void reject(WebSocketSession session, String reason) {
        try {
            session.close(CloseStatus.POLICY_VIOLATION.withReason(reason));
        } catch (Exception ignored) {
            // best-effort; the socket is already being torn down
        }
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

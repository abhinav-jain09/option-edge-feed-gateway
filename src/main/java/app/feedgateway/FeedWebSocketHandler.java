package app.feedgateway;

import app.feedgateway.mtsession.SessionRoutingEngine;
import app.feedgateway.mtsession.gateway.TicketHandshakeInterceptor;
import app.feedgateway.replay.ReplaySessionLifecycle;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.SubProtocolCapable;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.List;

@Component
public class FeedWebSocketHandler extends TextWebSocketHandler implements SubProtocolCapable {
    private final FeedGatewayService gatewayService;
    private final ObjectProvider<SessionRoutingEngine> engineProvider;
    private final ReplaySessionLifecycle replayLifecycle;

    public FeedWebSocketHandler(FeedGatewayService gatewayService,
                                ObjectProvider<SessionRoutingEngine> engineProvider,
                                ReplaySessionLifecycle replayLifecycle) {
        this.gatewayService = gatewayService;
        this.engineProvider = engineProvider;
        this.replayLifecycle = replayLifecycle;
    }

    /**
     * Advertises the {@code oc.bearer} subprotocol so the negotiated handshake echoes it back when the
     * browser connects with {@code new WebSocket(url, ['oc.bearer', accessToken])} (see
     * {@link WsJwtHandshakeInterceptor}). Without this the server would reject the offered subprotocol.
     */
    @Override
    public List<String> getSubProtocols() {
        return List.of(WsJwtHandshakeInterceptor.BEARER_SUBPROTOCOL);
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
        // Bind the session to its authenticated owner (the JWT 'sub' set at handshake) so replay control
        // requests can be authorized to the owning user only. No 'sub' (auth off) leaves it unbound.
        Object sub = session.getAttributes().get("sub");
        replayLifecycle.onConnect(session.getId(), sub == null ? null : sub.toString());
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
        replayLifecycle.onDisconnect(session.getId()); // tear down ALL replay state for the socket
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        detach(session);
        gatewayService.removeClient(session);
        replayLifecycle.onDisconnect(session.getId());
    }

    private void detach(WebSocketSession session) {
        SessionRoutingEngine engine = engineProvider.getIfAvailable();
        if (engine != null) {
            engine.detachSocket(session.getId());
        }
    }
}

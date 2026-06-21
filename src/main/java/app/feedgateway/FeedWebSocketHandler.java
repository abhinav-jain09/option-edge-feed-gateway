package app.feedgateway;

import app.feedgateway.replay.ReplaySessionLifecycle;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.SubProtocolCapable;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.List;

@Component
public class FeedWebSocketHandler extends TextWebSocketHandler implements SubProtocolCapable {
    private final FeedGatewayService gatewayService;
    private final ReplaySessionLifecycle replayLifecycle;

    public FeedWebSocketHandler(FeedGatewayService gatewayService, ReplaySessionLifecycle replayLifecycle) {
        this.gatewayService = gatewayService;
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
        gatewayService.addClient(session);
        // Bind the session to its authenticated owner (the JWT 'sub' set at handshake) so replay control
        // requests can be authorized to the owning user only. No 'sub' (auth off) leaves it unbound.
        Object sub = session.getAttributes().get("sub");
        replayLifecycle.onConnect(session.getId(), sub == null ? null : sub.toString());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        gatewayService.removeClient(session);
        replayLifecycle.onDisconnect(session.getId()); // tear down ALL replay state for the socket
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        gatewayService.removeClient(session);
        replayLifecycle.onDisconnect(session.getId());
    }
}

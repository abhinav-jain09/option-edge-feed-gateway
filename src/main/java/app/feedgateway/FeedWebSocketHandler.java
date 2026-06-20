package app.feedgateway;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.SubProtocolCapable;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.List;

@Component
public class FeedWebSocketHandler extends TextWebSocketHandler implements SubProtocolCapable {
    private final FeedGatewayService gatewayService;

    public FeedWebSocketHandler(FeedGatewayService gatewayService) {
        this.gatewayService = gatewayService;
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
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        gatewayService.removeClient(session);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        gatewayService.removeClient(session);
    }
}

package app.feedgateway.mtsession.gateway;

import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

/**
 * Spring adapter that enforces ticket auth on the {@code /ws/events} handshake (OE-DDD-001 §5.3).
 * Thin: it extracts the ticket from the request and delegates the decision to
 * {@link HandshakeTicketAuthenticator}. On accept it binds the AppSession id into the WS session
 * attributes; on reject it fails the upgrade with 401.
 *
 * <p>Bound attribute keys are read by {@code FeedWebSocketHandler} to attach the socket to the
 * session router.
 */
public final class TicketHandshakeInterceptor implements HandshakeInterceptor {

    public static final String ATTR_APP_SESSION_ID = "mt.appSessionId";
    public static final String ATTR_USER_ID = "mt.userId";
    private static final String SUBPROTOCOL_PREFIX = "oe.ticket.";

    private final HandshakeTicketAuthenticator authenticator;
    private final boolean authEnabled;

    public TicketHandshakeInterceptor(HandshakeTicketAuthenticator authenticator, boolean authEnabled) {
        this.authenticator = authenticator;
        this.authEnabled = authEnabled;
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        String ticket = extractTicket(request);
        HandshakeTicketAuthenticator.Decision d = authenticator.authenticate(authEnabled, ticket);
        if (!d.accept()) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
        if (!d.passthrough()) {
            attributes.put(ATTR_APP_SESSION_ID, d.appSessionId());
            attributes.put(ATTR_USER_ID, d.userId());
        }
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                              WebSocketHandler wsHandler, Exception exception) {
        // no-op
    }

    /** Ticket may arrive as the {@code oe.ticket.<id>} subprotocol (preferred) or {@code ?ticket=<id>}. */
    private static String extractTicket(ServerHttpRequest request) {
        for (String proto : request.getHeaders().getOrEmpty("Sec-WebSocket-Protocol")) {
            for (String token : proto.split(",")) {
                String t = token.trim();
                if (t.startsWith(SUBPROTOCOL_PREFIX)) {
                    return t.substring(SUBPROTOCOL_PREFIX.length());
                }
            }
        }
        String query = request.getURI().getQuery();
        if (query != null) {
            for (String pair : query.split("&")) {
                int eq = pair.indexOf('=');
                if (eq > 0 && "ticket".equals(pair.substring(0, eq))) {
                    return pair.substring(eq + 1);
                }
            }
        }
        return null;
    }
}

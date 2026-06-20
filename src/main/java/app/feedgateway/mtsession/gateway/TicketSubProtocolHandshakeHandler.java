package app.feedgateway.mtsession.gateway;

import java.util.List;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;

/**
 * P1 (ticket transport): the browser carries the single-use ticket as the {@code oe.ticket.<id>}
 * subprotocol (NOT a query parameter — a ticket in the URL leaks into proxy/access logs and could be
 * replayed). A browser fails the upgrade unless the server echoes one of the offered subprotocols, so this
 * handler selects and echoes the {@code oe.ticket.*} value. The id itself is opaque and is not logged.
 *
 * <p>Falls back to the default negotiation (e.g. {@code oc.bearer}) when no ticket subprotocol is offered.
 */
public final class TicketSubProtocolHandshakeHandler extends DefaultHandshakeHandler {

    @Override
    protected String selectProtocol(List<String> requestedProtocols, WebSocketHandler webSocketHandler) {
        if (requestedProtocols != null) {
            for (String protocol : requestedProtocols) {
                if (protocol != null && protocol.startsWith(TicketHandshakeInterceptor.SUBPROTOCOL_PREFIX)) {
                    return protocol; // echo the ticket subprotocol so the browser completes the upgrade
                }
            }
        }
        return super.selectProtocol(requestedProtocols, webSocketHandler);
    }
}

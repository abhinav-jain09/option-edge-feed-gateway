package app.feedgateway.mtsession.gateway;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import app.feedgateway.mtsession.InMemoryTicketStore;
import app.feedgateway.mtsession.WsTicket;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;

/**
 * The handshake interceptor: passes through when auth is off, accepts a valid ticket (binding the
 * AppSession into the WS attributes), and fails the upgrade with 401 on a missing/invalid/used ticket.
 */
class TicketHandshakeInterceptorTest {

    private final AtomicInteger seq = new AtomicInteger();
    private final InMemoryTicketStore store =
            new InMemoryTicketStore(Clock.systemUTC(), () -> "tkt-" + seq.incrementAndGet());
    private final HandshakeTicketAuthenticator authenticator = new HandshakeTicketAuthenticator(store);

    private ServerHttpRequest requestWithTicket(String ticketId) {
        ServerHttpRequest req = mock(ServerHttpRequest.class);
        HttpHeaders headers = new HttpHeaders();
        if (ticketId != null) {
            headers.add("Sec-WebSocket-Protocol", "oe.ticket." + ticketId);
        }
        when(req.getURI()).thenReturn(URI.create("http://gw/ws/events"));
        when(req.getHeaders()).thenReturn(headers);
        return req;
    }

    @Test
    void authDisabledPassesThroughWithoutBindingSession() {
        TicketHandshakeInterceptor interceptor = new TicketHandshakeInterceptor(authenticator, false);
        Map<String, Object> attrs = new HashMap<>();

        boolean ok = interceptor.beforeHandshake(requestWithTicket(null), mock(ServerHttpResponse.class),
                mock(WebSocketHandler.class), attrs);

        assertTrue(ok);
        assertFalse(attrs.containsKey(TicketHandshakeInterceptor.ATTR_APP_SESSION_ID));
    }

    @Test
    void validTicketIsAcceptedAndBindsAppSession() {
        WsTicket ticket = store.mint("u1", "app:u1", Duration.ofSeconds(10));
        TicketHandshakeInterceptor interceptor = new TicketHandshakeInterceptor(authenticator, true);
        Map<String, Object> attrs = new HashMap<>();

        boolean ok = interceptor.beforeHandshake(requestWithTicket(ticket.ticketId()), mock(ServerHttpResponse.class),
                mock(WebSocketHandler.class), attrs);

        assertTrue(ok);
        assertEquals("app:u1", attrs.get(TicketHandshakeInterceptor.ATTR_APP_SESSION_ID));
        assertEquals("u1", attrs.get(TicketHandshakeInterceptor.ATTR_USER_ID));
    }

    @Test
    void missingTicketIsRejectedWith401() {
        TicketHandshakeInterceptor interceptor = new TicketHandshakeInterceptor(authenticator, true);
        ServerHttpResponse resp = mock(ServerHttpResponse.class);
        Map<String, Object> attrs = new HashMap<>();

        boolean ok = interceptor.beforeHandshake(requestWithTicket(null), resp, mock(WebSocketHandler.class), attrs);

        assertFalse(ok);
        verify(resp).setStatusCode(HttpStatus.UNAUTHORIZED);
        assertTrue(attrs.isEmpty());
    }

    @Test
    void alreadyRedeemedTicketIsRejected() {
        WsTicket ticket = store.mint("u1", "app:u1", Duration.ofSeconds(10));
        TicketHandshakeInterceptor interceptor = new TicketHandshakeInterceptor(authenticator, true);

        // First handshake consumes the single-use ticket.
        assertTrue(interceptor.beforeHandshake(requestWithTicket(ticket.ticketId()), mock(ServerHttpResponse.class),
                mock(WebSocketHandler.class), new HashMap<>()));

        // Replay of the same ticket is rejected.
        ServerHttpResponse resp = mock(ServerHttpResponse.class);
        boolean ok = interceptor.beforeHandshake(requestWithTicket(ticket.ticketId()), resp,
                mock(WebSocketHandler.class), new HashMap<>());
        assertFalse(ok);
        verify(resp).setStatusCode(HttpStatus.UNAUTHORIZED);
    }
}

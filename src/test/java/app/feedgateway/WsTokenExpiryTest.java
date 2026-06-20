package app.feedgateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WsTokenExpiryTest {

    private static final long NOW = 1_700_000_000_000L;

    private FeedGatewayService newService() {
        return new FeedGatewayService(new GatewaySettings(), new ObjectMapper(), new HpsfGatewayViewMapper(), null);
    }

    private static WebSocketSession sessionWithExpiry(Long expiresAtMs) {
        WebSocketSession session = mock(WebSocketSession.class);
        Map<String, Object> attrs = new HashMap<>();
        if (expiresAtMs != null) {
            attrs.put(WsJwtHandshakeInterceptor.AUTH_EXPIRES_AT_ATTR, expiresAtMs);
        }
        when(session.getAttributes()).thenReturn(attrs);
        when(session.isOpen()).thenReturn(true);
        when(session.getId()).thenReturn(java.util.UUID.randomUUID().toString());
        return session;
    }

    @Test
    void closesSessionWhoseTokenHasExpired() throws Exception {
        FeedGatewayService service = newService();
        WebSocketSession expired = sessionWithExpiry(NOW - 1);
        service.addClient(expired);

        int closed = service.closeExpiredAuthSessions(NOW);

        assertEquals(1, closed);
        verify(expired).close(any(CloseStatus.class));
    }

    @Test
    void keepsSessionWhoseTokenIsStillValid() throws Exception {
        FeedGatewayService service = newService();
        WebSocketSession valid = sessionWithExpiry(NOW + 60_000);
        service.addClient(valid);

        int closed = service.closeExpiredAuthSessions(NOW);

        assertEquals(0, closed);
        verify(valid, never()).close(any(CloseStatus.class));
    }

    @Test
    void ignoresUnauthenticatedSessionsWithNoExpiryAttribute() throws Exception {
        FeedGatewayService service = newService();
        WebSocketSession noAuth = sessionWithExpiry(null);
        service.addClient(noAuth);

        int closed = service.closeExpiredAuthSessions(NOW);

        assertEquals(0, closed);
        verify(noAuth, never()).close(any(CloseStatus.class));
    }

    @Test
    void closesOnlyTheExpiredSessionAmongMany() throws Exception {
        FeedGatewayService service = newService();
        WebSocketSession expired = sessionWithExpiry(NOW - 5_000);
        WebSocketSession valid = sessionWithExpiry(NOW + 5_000);
        WebSocketSession noAuth = sessionWithExpiry(null);
        service.addClient(expired);
        service.addClient(valid);
        service.addClient(noAuth);

        int closed = service.closeExpiredAuthSessions(NOW);

        assertEquals(1, closed);
        verify(expired).close(any(CloseStatus.class));
        verify(valid, never()).close(any(CloseStatus.class));
        verify(noAuth, never()).close(any(CloseStatus.class));
    }
}

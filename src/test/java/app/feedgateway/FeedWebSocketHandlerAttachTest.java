package app.feedgateway;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import app.feedgateway.mtsession.ConcurrencyLimits;
import app.feedgateway.mtsession.MarketDataSource;
import app.feedgateway.mtsession.Selection;
import app.feedgateway.mtsession.SessionRoutingEngine;
import app.feedgateway.mtsession.StrikeWindow;
import app.feedgateway.mtsession.SubscriptionManager;
import app.feedgateway.mtsession.gateway.TicketHandshakeInterceptor;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

/**
 * On the auth path a redeemed ticket must never leave an unattached live client: if the socket
 * cannot attach to its handshake-bound AppSession, the handler closes it and does NOT call
 * {@code addClient} (req. 3).
 */
class FeedWebSocketHandlerAttachTest {

    private static final Selection SPX =
            new Selection(MarketDataSource.DATABENTO, "SPX", "20260617", StrikeWindow.ALL);

    private FeedGatewayService gateway() {
        FeedGatewayService svc =
                new FeedGatewayService(new GatewaySettings(), new ObjectMapper(), new HpsfGatewayViewMapper(), null);
        svc.runOutboundWritesInline();
        return svc;
    }

    @SuppressWarnings("unchecked")
    private ObjectProvider<SessionRoutingEngine> provider(SessionRoutingEngine engine) {
        ObjectProvider<SessionRoutingEngine> p = mock(ObjectProvider.class);
        when(p.getIfAvailable()).thenReturn(engine);
        return p;
    }

    private WebSocketSession socket(String id, String boundAppSessionId) throws Exception {
        WebSocketSession ws = mock(WebSocketSession.class);
        when(ws.getId()).thenReturn(id);
        when(ws.isOpen()).thenReturn(true);
        Map<String, Object> attrs = new HashMap<>();
        if (boundAppSessionId != null) {
            attrs.put(TicketHandshakeInterceptor.ATTR_APP_SESSION_ID, boundAppSessionId);
        }
        when(ws.getAttributes()).thenReturn(attrs);
        return ws;
    }

    @Test
    void successfulAttachAdmitsTheClient() throws Exception {
        SessionRoutingEngine engine = new SessionRoutingEngine(new ConcurrencyLimits(5, 5, 100), new SubscriptionManager());
        engine.registerAppSession("app:u1", "u1", SPX, Set.of());
        FeedWebSocketHandler handler = new FeedWebSocketHandler(gateway(), provider(engine));
        WebSocketSession ws = socket("s1", "app:u1");

        handler.afterConnectionEstablished(ws);

        verify(ws, never()).close(any());                 // not rejected
        verify(ws).sendMessage(any());                    // addClient sent the initial status frame
        assertTrue(engine.appSession("app:u1").orElseThrow().socketIds().contains("s1"));
    }

    @Test
    void expiredAppSessionIsRejectedNotAdmitted() throws Exception {
        // No AppSession registered for the bound id (mint→connect gap / idle teardown).
        SessionRoutingEngine engine = new SessionRoutingEngine(new ConcurrencyLimits(5, 5, 100), new SubscriptionManager());
        FeedWebSocketHandler handler = new FeedWebSocketHandler(gateway(), provider(engine));
        WebSocketSession ws = socket("s1", "app:ghost");

        handler.afterConnectionEstablished(ws);

        verify(ws).close(any(CloseStatus.class));          // socket closed
        verify(ws, never()).sendMessage(any());            // addClient never ran (no status frame)
    }

    @Test
    void socketCapBreachIsRejectedNotAdmitted() throws Exception {
        SessionRoutingEngine engine = new SessionRoutingEngine(new ConcurrencyLimits(5, 1, 100), new SubscriptionManager());
        engine.registerAppSession("app:u1", "u1", SPX, Set.of());
        engine.attachSocket("app:u1", "existing");         // fill the single-socket cap
        FeedWebSocketHandler handler = new FeedWebSocketHandler(gateway(), provider(engine));
        WebSocketSession ws = socket("s2", "app:u1");

        handler.afterConnectionEstablished(ws);

        verify(ws).close(any(CloseStatus.class));
        verify(ws, never()).sendMessage(any());
        assertFalse(engine.appSession("app:u1").orElseThrow().socketIds().contains("s2"));
    }

    @Test
    void authOnButNoBoundSessionIsRejected() throws Exception {
        SessionRoutingEngine engine = new SessionRoutingEngine(new ConcurrencyLimits(5, 5, 100), new SubscriptionManager());
        FeedWebSocketHandler handler = new FeedWebSocketHandler(gateway(), provider(engine));
        WebSocketSession ws = socket("s1", null);          // engine present (auth on) but no bound id

        handler.afterConnectionEstablished(ws);

        verify(ws).close(any(CloseStatus.class));
        verify(ws, never()).sendMessage(any());
    }

    @Test
    void legacyModeWithoutEngineStillAdmits() throws Exception {
        FeedWebSocketHandler handler = new FeedWebSocketHandler(gateway(), provider(null)); // auth off
        WebSocketSession ws = socket("s1", null);

        handler.afterConnectionEstablished(ws);

        verify(ws, never()).close(any());
        verify(ws).sendMessage(any());                     // admitted as before
    }
}

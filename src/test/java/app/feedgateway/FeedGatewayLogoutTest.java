package app.feedgateway;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import app.feedgateway.mtsession.ConcurrencyLimits;
import app.feedgateway.mtsession.MarketDataSource;
import app.feedgateway.mtsession.Selection;
import app.feedgateway.mtsession.SessionRoutingEngine;
import app.feedgateway.mtsession.StrikeWindow;
import app.feedgateway.mtsession.SubscriptionManager;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.WebSocketSession;

/**
 * P0 (real sign-out): logout must tear down the SERVER-side session — remove the AppSession from routing
 * and force-close every socket attached to it — so a still-open socket or leaked ticket cannot keep
 * streaming after the user signs out.
 */
class FeedGatewayLogoutTest {

    private SessionRoutingEngine engineWithSession() {
        SessionRoutingEngine engine =
                new SessionRoutingEngine(new ConcurrencyLimits(5, 5, 100), new SubscriptionManager());
        engine.registerAppSession("app:u1", "u1",
                new Selection(MarketDataSource.DATABENTO, "SPX", "20260612", StrikeWindow.ALL), Set.of());
        engine.attachSocket("app:u1", "s1");
        return engine;
    }

    private WebSocketSession socket(String id) throws Exception {
        WebSocketSession ws = mock(WebSocketSession.class);
        when(ws.getId()).thenReturn(id);
        when(ws.isOpen()).thenReturn(true);
        return ws;
    }

    @Test
    void logoutTearsDownTheAppSessionAndClosesItsSocket() throws Exception {
        SessionRoutingEngine engine = engineWithSession();
        FeedGatewayService svc =
                new FeedGatewayService(new GatewaySettings(), new ObjectMapper(), new HpsfGatewayViewMapper(), engine);
        svc.runOutboundWritesInline();
        WebSocketSession ws = socket("s1");
        svc.addClient(ws);

        int closed = svc.logout("app:u1");

        assertEquals(1, closed, "the user's one socket is closed");
        assertTrue(engine.appSession("app:u1").isEmpty(), "the AppSession is removed from routing");
        verify(ws).close();                                  // socket force-closed server-side
        assertTrue(svc.healthJson().contains("\"clients\":0"), "no client sockets remain");
    }

    @Test
    void logoutIsIdempotentAndSafeWhenNothingIsAttached() throws Exception {
        SessionRoutingEngine engine = engineWithSession();
        FeedGatewayService svc =
                new FeedGatewayService(new GatewaySettings(), new ObjectMapper(), new HpsfGatewayViewMapper(), engine);
        svc.runOutboundWritesInline();
        svc.addClient(socket("s1"));

        assertEquals(1, svc.logout("app:u1"));
        assertEquals(0, svc.logout("app:u1"), "second logout closes nothing");
        assertEquals(0, svc.logout("app:unknown"), "unknown session is a no-op");
    }
}

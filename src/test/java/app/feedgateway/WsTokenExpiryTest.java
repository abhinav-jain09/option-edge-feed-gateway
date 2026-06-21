package app.feedgateway;

import app.feedgateway.mtsession.ConcurrencyLimits;
import app.feedgateway.mtsession.MarketDataSource;
import app.feedgateway.mtsession.Selection;
import app.feedgateway.mtsession.SessionRoutingEngine;
import app.feedgateway.mtsession.StrikeWindow;
import app.feedgateway.mtsession.SubscriptionManager;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WsTokenExpiryTest {

    private static final long NOW = 1_700_000_000_000L;

    private FeedGatewayService newService() {
        FeedGatewayService svc =
                new FeedGatewayService(new GatewaySettings(), new ObjectMapper(), new HpsfGatewayViewMapper(), null);
        svc.runOutboundWritesInline();
        return svc;
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

    /**
     * P0 (replay resource safety, token-expiry path): when the token-expiry reaper closes a per-session
     * socket, it must detach it from routing AND cancel replay if it was its AppSession's last socket —
     * exactly like removeClient/onSlowDisconnect. Otherwise an in-flight reader is orphaned and, worse, the
     * still-"attached" dead socket makes a later cancel decline. (The other tests use a null engine and
     * cannot observe this.)
     */
    @Test
    void expiredTokenSweepCancelsInFlightReplayForItsLastSocket() throws Exception {
        SessionRoutingEngine engine =
                new SessionRoutingEngine(new ConcurrencyLimits(5, 5, 100), new SubscriptionManager());
        engine.registerAppSession("app:u1", "u1",
                new Selection(MarketDataSource.DATABENTO, "SPX", "20260612", StrikeWindow.ALL), Set.of());
        engine.attachSocket("app:u1", "s1");
        FeedGatewayService svc =
                new FeedGatewayService(new GatewaySettings(), new ObjectMapper(), new HpsfGatewayViewMapper(), engine);
        svc.runOutboundWritesInline();

        WebSocketSession session = mock(WebSocketSession.class);
        Map<String, Object> attrs = new HashMap<>();
        attrs.put(WsJwtHandshakeInterceptor.AUTH_EXPIRES_AT_ATTR, NOW - 1); // already expired
        attrs.put(app.feedgateway.mtsession.gateway.TicketHandshakeInterceptor.ATTR_APP_SESSION_ID, "app:u1");
        when(session.getAttributes()).thenReturn(attrs);
        when(session.isOpen()).thenReturn(true);
        when(session.getId()).thenReturn("s1");
        svc.addClient(session);

        // An in-flight replay for the session (the state startReplay installs before its reader runs).
        FeedGatewayService.ReplayHandle handle = svc.registerOwnerHandleForTest("app:u1");
        replayHandlesOf(svc).put("app:u1", handle);
        engine.setReplayMode("app:u1", true);

        int closed = svc.closeExpiredAuthSessions(NOW);

        assertEquals(1, closed);
        assertTrue(engine.socketsForAppSession("app:u1").isEmpty(), "expired socket detached from routing");
        assertFalse(handle.active.get(), "expired-token sweep cancels the in-flight reader");
        assertFalse(replayHandlesOf(svc).containsKey("app:u1"), "in-flight replay handle removed");
        assertFalse(engine.isReplaying("app:u1"), "session left replay mode");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, FeedGatewayService.ReplayHandle> replayHandlesOf(FeedGatewayService svc)
            throws Exception {
        Field f = FeedGatewayService.class.getDeclaredField("replayHandles");
        f.setAccessible(true);
        return (Map<String, FeedGatewayService.ReplayHandle>) f.get(svc);
    }
}

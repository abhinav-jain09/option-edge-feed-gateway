package app.feedgateway;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
import app.feedgateway.mtsession.approval.ApprovalAuthority;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.WebSocketSession;

/**
 * P0 (approval rechecked during sessions): an approval that is revoked/suspended/expired after a ticket was
 * issued must tear down the ALREADY-CONNECTED session — not wait for the next ticket. The reaper re-consults
 * the authority for every active session and force-closes those no longer approved (fail-closed on error).
 */
class ApprovalReaperTest {

    private SessionRoutingEngine engineWithSession() {
        SessionRoutingEngine engine =
                new SessionRoutingEngine(new ConcurrencyLimits(5, 5, 100), new SubscriptionManager());
        engine.registerAppSession("app:u1", "u1",
                new Selection(MarketDataSource.DATABENTO, "SPX", "20260612", StrikeWindow.ALL), Set.of("user"));
        engine.attachSocket("app:u1", "s1");
        return engine;
    }

    private FeedGatewayService gateway(SessionRoutingEngine engine, WebSocketSession ws) {
        FeedGatewayService svc =
                new FeedGatewayService(new GatewaySettings(), new ObjectMapper(), new HpsfGatewayViewMapper(), engine);
        svc.runOutboundWritesInline();
        svc.addClient(ws);
        return svc;
    }

    private WebSocketSession socket() throws Exception {
        WebSocketSession ws = mock(WebSocketSession.class);
        when(ws.getId()).thenReturn("s1");
        when(ws.isOpen()).thenReturn(true);
        return ws;
    }

    private ApprovalReaper reaper(SessionRoutingEngine engine, FeedGatewayService gateway, ApprovalAuthority authz) {
        return new ApprovalReaper(engine, authz, gateway, new GatewaySettings());
    }

    @Test
    void revokedApprovalTearsDownTheActiveSession() throws Exception {
        SessionRoutingEngine engine = engineWithSession();
        WebSocketSession ws = socket();
        FeedGatewayService gateway = gateway(engine, ws);
        // Approval was valid at issuance; now the authority denies (suspended / expired / unavailable).
        ApprovalReaper reaper = reaper(engine, gateway, new ApprovalAuthority.DenyAll());

        int revoked = reaper.recheckOnce(System.currentTimeMillis());

        assertEquals(1, revoked);
        assertTrue(engine.appSession("app:u1").isEmpty(), "the revoked session is removed from routing");
        verify(ws).close(); // its socket is force-closed mid-session
    }

    @Test
    void stillApprovedSessionIsLeftAlone() throws Exception {
        SessionRoutingEngine engine = engineWithSession();
        WebSocketSession ws = socket();
        FeedGatewayService gateway = gateway(engine, ws);
        ApprovalReaper reaper = reaper(engine, gateway, query -> ApprovalAuthority.ApprovalDecision.approved(0L));

        assertEquals(0, reaper.recheckOnce(System.currentTimeMillis()));
        assertFalse(engine.appSession("app:u1").isEmpty(), "an approved session keeps streaming");
    }

    @Test
    void revokedRoleTearsDownASessionWhoseSelectionIsNoLongerEntitled() throws Exception {
        SessionRoutingEngine engine =
                new SessionRoutingEngine(new ConcurrencyLimits(5, 5, 100), new SubscriptionManager());
        engine.registerAppSession("app:u1", "u1",
                new Selection(MarketDataSource.IBKR, "SPX", "20260617", StrikeWindow.ALL), Set.of("user", "ibkr-user"));
        engine.attachSocket("app:u1", "s1");
        WebSocketSession ws = socket();
        FeedGatewayService gateway = gateway(engine, ws);
        // Approval is fine, but the ibkr-user role was revoked — the IBKR selection is no longer entitled.
        engine.refreshEntitlements("app:u1", Set.of("user"));
        ApprovalReaper reaper = reaper(engine, gateway, query -> ApprovalAuthority.ApprovalDecision.approved(0L));

        assertEquals(1, reaper.recheckOnce(System.currentTimeMillis()));
        assertTrue(engine.appSession("app:u1").isEmpty(), "a session whose role was revoked is torn down");
        verify(ws).close();
    }

    @Test
    void authorityErrorFailsClosedAndRevokes() throws Exception {
        SessionRoutingEngine engine = engineWithSession();
        WebSocketSession ws = socket();
        FeedGatewayService gateway = gateway(engine, ws);
        ApprovalReaper reaper = reaper(engine, gateway, query -> { throw new RuntimeException("platform down"); });

        assertEquals(1, reaper.recheckOnce(System.currentTimeMillis()), "an authority error revokes (fail closed)");
        assertTrue(engine.appSession("app:u1").isEmpty());
    }
}

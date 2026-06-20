package app.feedgateway.mtsession.gateway;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import app.feedgateway.mtsession.ConcurrencyLimits;
import app.feedgateway.mtsession.InMemoryTicketStore;
import app.feedgateway.mtsession.MarketDataSource;
import app.feedgateway.mtsession.NotApprovedException;
import app.feedgateway.mtsession.NotEntitledException;
import app.feedgateway.mtsession.Selection;
import app.feedgateway.mtsession.approval.ApprovalAuthority;
import app.feedgateway.mtsession.SessionRoutingEngine;
import app.feedgateway.mtsession.StrikeWindow;
import app.feedgateway.mtsession.SubscriptionManager;
import app.feedgateway.mtsession.auth.JwtVerificationException;
import app.feedgateway.mtsession.auth.TokenVerifier;
import app.feedgateway.mtsession.auth.VerifiedPrincipal;
import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class WsTicketServiceTest {

    /** Fake verifier: maps known tokens to principals, throws otherwise. */
    private static final class FakeVerifier implements TokenVerifier {
        private final Map<String, VerifiedPrincipal> principals;
        FakeVerifier(Map<String, VerifiedPrincipal> principals) { this.principals = principals; }
        @Override public VerifiedPrincipal verify(String token) throws JwtVerificationException {
            VerifiedPrincipal p = principals.get(token);
            if (p == null) {
                throw new JwtVerificationException("bad token");
            }
            return p;
        }
    }

    private static final ApprovalAuthority APPROVE_ALL =
            query -> ApprovalAuthority.ApprovalDecision.approved(0L);

    private WsTicketService service(TokenVerifier verifier) {
        return service(verifier, APPROVE_ALL);
    }

    private WsTicketService service(TokenVerifier verifier, ApprovalAuthority approvalAuthority) {
        SessionRoutingEngine engine = new SessionRoutingEngine(
                new ConcurrencyLimits(1, 4, 100), new SubscriptionManager());
        AtomicInteger seq = new AtomicInteger();
        InMemoryTicketStore store = new InMemoryTicketStore(Clock.systemUTC(), () -> "tk-" + seq.incrementAndGet());
        return new WsTicketService(verifier, store, engine, approvalAuthority,
                "https://kc.test/realms/optionsedge", Duration.ofSeconds(10));
    }

    private static Selection dbnto(String symbol, String expiry) {
        return new Selection(MarketDataSource.DATABENTO, symbol, expiry, StrikeWindow.ALL);
    }

    @Test
    void issueRegistersAppSessionAndMintsTicket() throws Exception {
        VerifiedPrincipal trader = new VerifiedPrincipal("u1", "testtrader",
                Set.of("user", "trader", "ibkr-user"), "options-edge-web");
        WsTicketService svc = service(new FakeVerifier(Map.of("good", trader)));

        WsTicketService.TicketIssued issued = svc.issue("good", dbnto("SPX", "20260612"));
        assertNotNull(issued.ticketId());
        assertEquals("app:u1", issued.appSessionId());
        assertEquals(10_000L, issued.expiresInMs());
        assertEquals("SPX", svc.appSessionForUser("u1").selection().symbol());
    }

    @Test
    void secondIssueChangesSelectionNotDuplicateSession() throws Exception {
        VerifiedPrincipal trader = new VerifiedPrincipal("u1", "t", Set.of("user", "trader", "ibkr-user"), "c");
        WsTicketService svc = service(new FakeVerifier(Map.of("good", trader)));
        svc.issue("good", dbnto("SPX", "20260612"));
        svc.issue("good", dbnto("NDX", "20260620"));
        assertEquals("NDX", svc.appSessionForUser("u1").selection().symbol());
    }

    @Test
    void invalidTokenRejected() {
        WsTicketService svc = service(new FakeVerifier(Map.of()));
        assertThrows(JwtVerificationException.class, () -> svc.issue("nope", dbnto("SPX", "20260612")));
    }

    @Test
    void unapprovedUserIsRejectedEvenWithAValidToken() {
        // P0: a valid token is NOT approval. With the authority denying (default-deny), no ticket is issued
        // and no AppSession is created — the self-registered user gets no Databento data.
        VerifiedPrincipal validUser = new VerifiedPrincipal("u9", "selfsignup", Set.of("user"), "c");
        WsTicketService svc = service(new FakeVerifier(Map.of("good", validUser)), new ApprovalAuthority.DenyAll());
        assertThrows(NotApprovedException.class, () -> svc.issue("good", dbnto("SPX", "20260612")));
        org.junit.jupiter.api.Assertions.assertNull(svc.appSessionForUser("u9"), "no session for an unapproved user");
    }

    @Test
    void expiredApprovalIsRejected() {
        VerifiedPrincipal validUser = new VerifiedPrincipal("u8", "lapsed", Set.of("user"), "c");
        // Authority returns APPROVED but with an expiry in the past → grantsAccess(now) is false → denied.
        ApprovalAuthority expired = query -> new ApprovalAuthority.ApprovalDecision(
                app.feedgateway.mtsession.ApprovalState.APPROVED, 1L);
        WsTicketService svc = service(new FakeVerifier(Map.of("good", validUser)), expired);
        assertThrows(NotApprovedException.class, () -> svc.issue("good", dbnto("SPX", "20260612")));
    }

    @Test
    void ibkrSelectionWithoutEntitlementRejected() {
        VerifiedPrincipal basic = new VerifiedPrincipal("u2", "basic", Set.of("user"), "c");
        WsTicketService svc = service(new FakeVerifier(Map.of("basic", basic)));
        Selection ibkr = new Selection(MarketDataSource.IBKR, "SPX", "20260617", StrikeWindow.ALL);
        assertThrows(NotEntitledException.class, () -> svc.issue("basic", ibkr));
    }
}

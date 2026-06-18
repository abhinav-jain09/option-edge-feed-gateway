package app.feedgateway.mtsession.gateway;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import app.feedgateway.mtsession.NotApprovedException;
import app.feedgateway.mtsession.NotEntitledException;
import app.feedgateway.mtsession.auth.JwtVerificationException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/** Maps the ticket-mint outcomes to HTTP: 401 (no/invalid token), 403 (not approved/entitled), 400 (bad request), 200 (minted). */
class WsTicketControllerTest {

    private static final WsTicketController.TicketRequest REQ =
            new WsTicketController.TicketRequest("DATABENTO", "SPX", "20260617", 0.0, 1_000_000.0);

    @Test
    void missingOrMalformedBearerIsUnauthorizedAndNeverHitsTheService() {
        WsTicketService svc = mock(WsTicketService.class);
        WsTicketController controller = new WsTicketController(svc);

        assertEquals(HttpStatus.UNAUTHORIZED, controller.issue(null, REQ).getStatusCode());
        assertEquals(HttpStatus.UNAUTHORIZED, controller.issue("Token abc", REQ).getStatusCode());
        verifyNoInteractions(svc);
    }

    @Test
    void validTokenMintsTicket() throws Exception {
        WsTicketService svc = mock(WsTicketService.class);
        when(svc.issue(eq("tok"), any())).thenReturn(new WsTicketService.TicketIssued("TKT-1", "app:u1", 10_000));
        ResponseEntity<String> resp = new WsTicketController(svc).issue("Bearer tok", REQ);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertTrue(resp.getBody().contains("\"ticket\":\"TKT-1\""));
        assertTrue(resp.getBody().contains("\"appSessionId\":\"app:u1\""));
        assertTrue(resp.getBody().contains("\"expiresInMs\":10000"));
    }

    @Test
    void invalidTokenIsUnauthorized() throws Exception {
        WsTicketService svc = mock(WsTicketService.class);
        when(svc.issue(any(), any())).thenThrow(new JwtVerificationException("bad"));
        assertEquals(HttpStatus.UNAUTHORIZED, new WsTicketController(svc).issue("Bearer tok", REQ).getStatusCode());
    }

    @Test
    void notApprovedAndNotEntitledAreForbidden() throws Exception {
        WsTicketService approval = mock(WsTicketService.class);
        when(approval.issue(any(), any())).thenThrow(new NotApprovedException("nope"));
        assertEquals(HttpStatus.FORBIDDEN, new WsTicketController(approval).issue("Bearer tok", REQ).getStatusCode());

        WsTicketService entitlement = mock(WsTicketService.class);
        when(entitlement.issue(any(), any())).thenThrow(new NotEntitledException("nope"));
        assertEquals(HttpStatus.FORBIDDEN, new WsTicketController(entitlement).issue("Bearer tok", REQ).getStatusCode());
    }

    @Test
    void invalidSelectionSourceIsBadRequest() {
        WsTicketService svc = mock(WsTicketService.class);
        WsTicketController.TicketRequest bad =
                new WsTicketController.TicketRequest("NASDAQ", "SPX", "20260617", 0.0, 1.0);
        // toSelection() rejects the unknown source before the service is reached.
        assertEquals(HttpStatus.BAD_REQUEST, new WsTicketController(svc).issue("Bearer tok", bad).getStatusCode());
        verifyNoInteractions(svc);
    }
}

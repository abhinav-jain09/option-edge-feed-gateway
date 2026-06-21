package app.feedgateway.mtsession.gateway;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import app.feedgateway.mtsession.auth.JwtVerificationException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/** Replay control endpoints map service outcomes to HTTP: 401, 403 (disabled), 400, 409, 200. */
class ReplayControllerTest {

    private static final ReplayService.ReplayRequest REQ = new ReplayService.ReplayRequest(
            "app:u1", "SPX", "20260612", "2026-06-12T14:00:00Z", "2026-06-12T14:20:00Z", 1000, null);
    private static final ReplayParams PARAMS =
            new ReplayParams("app:u1", "SPX", "20260612", 1_000L, 2_000L, 1000, null);

    @Test
    void missingBearerIsUnauthorizedOnEveryEndpoint() {
        ReplayService svc = mock(ReplayService.class);
        ReplayController c = new ReplayController(svc);
        assertEquals(HttpStatus.UNAUTHORIZED, c.start(null, REQ).getStatusCode());
        assertEquals(HttpStatus.UNAUTHORIZED, c.stop(null, new ReplayController.ModeRequest("app:u1")).getStatusCode());
        assertEquals(HttpStatus.UNAUTHORIZED, c.resume(null, new ReplayController.ModeRequest("app:u1")).getStatusCode());
        assertEquals(HttpStatus.UNAUTHORIZED, c.runs(null).getStatusCode());
        verifyNoInteractions(svc);
    }

    @Test
    void runsReturnsProjectedListWithNoStoreHeaders() throws Exception {
        ReplayService svc = mock(ReplayService.class);
        when(svc.listRuns(eq("tok"))).thenReturn(java.util.List.of(
                new ReplayRunView("r-1", "RUNNING", "2026-06-12", "14:00", "14:20"),
                new ReplayRunView("r-2", "COMPLETE", "2026-06-11", "09:30", "10:00")));
        ResponseEntity<String> resp = new ReplayController(svc).runs("Bearer tok");

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        String body = resp.getBody();
        assertTrue(body.contains("\"runId\":\"r-1\""));
        assertTrue(body.contains("\"state\":\"RUNNING\""));
        assertTrue(body.contains("\"runId\":\"r-2\""));
        assertTrue(body.startsWith("[") && body.endsWith("]"));
        assertEquals(false, body.contains("ownerId")); // projection drops ownership/internals
        assertTrue(resp.getHeaders().getFirst("Cache-Control").contains("no-store"));
    }

    @Test
    void runsEmptyListSerializesAsEmptyArray() throws Exception {
        ReplayService svc = mock(ReplayService.class);
        when(svc.listRuns(eq("tok"))).thenReturn(java.util.List.of());
        ResponseEntity<String> resp = new ReplayController(svc).runs("Bearer tok");
        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertEquals("[]", resp.getBody());
    }

    @Test
    void runsMapsDisabledTo403() throws Exception {
        ReplayService svc = mock(ReplayService.class);
        when(svc.listRuns(any())).thenThrow(new ReplayService.ReplayDisabledException("disabled"));
        assertEquals(HttpStatus.FORBIDDEN, new ReplayController(svc).runs("Bearer tok").getStatusCode());
    }

    @Test
    void startReturnsModeAndParams() throws Exception {
        ReplayService svc = mock(ReplayService.class);
        when(svc.start(eq("tok"), any())).thenReturn(new ReplayService.ReplayAck(ReplayRunner.Mode.REPLAY_RUNNING, PARAMS));
        ResponseEntity<String> resp = new ReplayController(svc).start("Bearer tok", REQ);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertTrue(resp.getBody().contains("\"mode\":\"REPLAY_RUNNING\""));
        assertTrue(resp.getBody().contains("\"sessionId\":\"app:u1\""));
        assertTrue(resp.getBody().contains("\"maxRecords\":1000"));
    }

    @Test
    void stopAndResumeReturnMode() throws Exception {
        ReplayService svc = mock(ReplayService.class);
        when(svc.stop(eq("tok"), eq("app:u1"))).thenReturn(ReplayRunner.Mode.REPLAY_COMPLETE);
        when(svc.resume(eq("tok"), eq("app:u1"))).thenReturn(ReplayRunner.Mode.LIVE);
        ReplayController c = new ReplayController(svc);

        assertTrue(c.stop("Bearer tok", new ReplayController.ModeRequest("app:u1")).getBody().contains("REPLAY_COMPLETE"));
        assertTrue(c.resume("Bearer tok", new ReplayController.ModeRequest("app:u1")).getBody().contains("LIVE"));
    }

    @Test
    void mapsServiceErrorsToStatusCodes() throws Exception {
        assertEquals(HttpStatus.UNAUTHORIZED, startThrowing(new JwtVerificationException("bad")));
        assertEquals(HttpStatus.FORBIDDEN, startThrowing(new ReplayService.ReplayDisabledException("disabled")));
        assertEquals(HttpStatus.BAD_REQUEST, startThrowing(new IllegalArgumentException("bad window")));
        assertEquals(HttpStatus.CONFLICT, startThrowing(new IllegalStateException("no session")));
    }

    private static HttpStatus startThrowing(Throwable t) throws Exception {
        ReplayService svc = mock(ReplayService.class);
        when(svc.start(any(), any())).thenThrow(t);
        return (HttpStatus) new ReplayController(svc).start("Bearer tok", REQ).getStatusCode();
    }
}

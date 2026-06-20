package app.feedgateway.mtsession.gateway;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * The orchestrator is the source of truth for run ownership. The gateway authorizer must treat ONLY a
 * 2xx as "owns the run", deny on every other status, and deny (fail closed) on any transport failure.
 */
class OrchestratorReplayRunAuthorizerTest {

    private static final Duration T = Duration.ofSeconds(2);

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static HttpClient clientReturning(int status) throws Exception {
        HttpClient http = mock(HttpClient.class);
        HttpResponse resp = mock(HttpResponse.class);
        when(resp.statusCode()).thenReturn(status);
        when(http.send(any(HttpRequest.class), any())).thenReturn(resp);
        return http;
    }

    @Test
    void ownerResponseIsAuthorizedAndCallsTheOwnershipEndpointWithTheBearer() throws Exception {
        HttpClient http = clientReturning(200);
        OrchestratorReplayRunAuthorizer authz =
                new OrchestratorReplayRunAuthorizer("http://orchestrator:8080/", T, http);

        assertDoesNotThrow(() -> authz.authorizeRun("the-token", "r-123"));

        ArgumentCaptor<HttpRequest> req = ArgumentCaptor.forClass(HttpRequest.class);
        org.mockito.Mockito.verify(http).send(req.capture(), any());
        assertEquals("http://orchestrator:8080/api/hpsf/replay-runs/r-123",
                req.getValue().uri().toString());
        assertEquals("Bearer the-token",
                req.getValue().headers().firstValue("Authorization").orElse(""));
    }

    @Test
    void forbiddenIsDenied() throws Exception {
        OrchestratorReplayRunAuthorizer authz =
                new OrchestratorReplayRunAuthorizer("http://orchestrator:8080", T, clientReturning(403));
        assertThrows(ReplayRunAuthorizer.ReplayRunAuthorizationException.class,
                () -> authz.authorizeRun("tok", "r-123"));
    }

    @Test
    void notFoundIsDenied() throws Exception {
        OrchestratorReplayRunAuthorizer authz =
                new OrchestratorReplayRunAuthorizer("http://orchestrator:8080", T, clientReturning(404));
        assertThrows(ReplayRunAuthorizer.ReplayRunAuthorizationException.class,
                () -> authz.authorizeRun("tok", "r-123"));
    }

    @Test
    void transportFailureFailsClosed() throws Exception {
        HttpClient http = mock(HttpClient.class);
        when(http.send(any(HttpRequest.class), any())).thenThrow(new IOException("orchestrator down"));
        OrchestratorReplayRunAuthorizer authz =
                new OrchestratorReplayRunAuthorizer("http://orchestrator:8080", T, http);
        assertThrows(ReplayRunAuthorizer.ReplayRunAuthorizationException.class,
                () -> authz.authorizeRun("tok", "r-123"));
    }

    @Test
    void blankRunIdAndBlankTokenAreRejected() throws Exception {
        OrchestratorReplayRunAuthorizer authz =
                new OrchestratorReplayRunAuthorizer("http://orchestrator:8080", T, clientReturning(200));
        assertThrows(ReplayRunAuthorizer.ReplayRunAuthorizationException.class,
                () -> authz.authorizeRun("tok", "  "));
        assertThrows(ReplayRunAuthorizer.ReplayRunAuthorizationException.class,
                () -> authz.authorizeRun("", "r-123"));
    }
}

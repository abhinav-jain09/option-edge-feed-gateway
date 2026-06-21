package app.feedgateway.mtsession.gateway;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * The run-picker lister forwards the bearer to the orchestrator (which filters by ownership), projects each
 * run to an ownership-safe view, and is FAIL-CLOSED: any non-2xx, transport failure, or unparseable body
 * yields an EMPTY list — never a raw orchestrator error and never a leaked ownerId/internal field.
 */
class OrchestratorReplayRunListerTest {

    private static final Duration T = Duration.ofSeconds(2);

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static HttpClient clientReturning(int status, String body) throws Exception {
        HttpClient http = mock(HttpClient.class);
        HttpResponse resp = mock(HttpResponse.class);
        when(resp.statusCode()).thenReturn(status);
        when(resp.body()).thenReturn(body);
        when(http.send(any(HttpRequest.class), any())).thenReturn(resp);
        return http;
    }

    @Test
    void projectsOwnedRunsAndCallsTheListEndpointWithTheBearer() throws Exception {
        String body = "[" // includes ownerId + internal fields the projection must drop
                + "{\"runId\":\"r-1\",\"ownerId\":\"iss|sub\",\"state\":\"RUNNING\",\"replayDate\":\"2026-06-12\","
                + "\"startTime\":\"14:00\",\"endTime\":\"14:20\",\"topicPrefix\":\"x\",\"progress\":0.5},"
                + "{\"runId\":\"r-2\",\"ownerId\":\"iss|sub\",\"state\":\"COMPLETE\",\"replayDate\":\"2026-06-11\","
                + "\"startTime\":\"09:30\",\"endTime\":\"10:00\"}]";
        HttpClient http = clientReturning(200, body);
        OrchestratorReplayRunLister lister =
                new OrchestratorReplayRunLister("http://orchestrator:8080/", T, http);

        List<ReplayRunView> runs = lister.listRuns("the-token");

        assertEquals(2, runs.size());
        assertEquals(new ReplayRunView("r-1", "RUNNING", "2026-06-12", "14:00", "14:20"), runs.get(0));
        assertEquals("r-2", runs.get(1).runId());
        assertEquals("COMPLETE", runs.get(1).state());

        ArgumentCaptor<HttpRequest> req = ArgumentCaptor.forClass(HttpRequest.class);
        org.mockito.Mockito.verify(http).send(req.capture(), any());
        assertEquals("http://orchestrator:8080/api/hpsf/replay-runs", req.getValue().uri().toString());
        assertEquals("Bearer the-token", req.getValue().headers().firstValue("Authorization").orElse(""));
    }

    @Test
    void runWithNoRunIdIsSkipped() throws Exception {
        String body = "[{\"state\":\"RUNNING\"},{\"runId\":\"r-9\",\"state\":\"COMPLETE\"}]";
        OrchestratorReplayRunLister lister =
                new OrchestratorReplayRunLister("http://orchestrator:8080", T, clientReturning(200, body));
        List<ReplayRunView> runs = lister.listRuns("tok");
        assertEquals(1, runs.size());
        assertEquals("r-9", runs.get(0).runId());
    }

    @Test
    void nonSuccessStatusFailsClosedToEmpty() throws Exception {
        for (int status : new int[] {401, 403, 404, 500, 503}) {
            OrchestratorReplayRunLister lister = new OrchestratorReplayRunLister(
                    "http://orchestrator:8080", T, clientReturning(status, "[{\"runId\":\"r-1\"}]"));
            assertTrue(lister.listRuns("tok").isEmpty(), "status " + status + " must yield empty");
        }
    }

    @Test
    void transportFailureFailsClosedToEmpty() throws Exception {
        HttpClient http = mock(HttpClient.class);
        when(http.send(any(HttpRequest.class), any())).thenThrow(new IOException("orchestrator down"));
        OrchestratorReplayRunLister lister = new OrchestratorReplayRunLister("http://orchestrator:8080", T, http);
        assertTrue(lister.listRuns("tok").isEmpty());
    }

    @Test
    void malformedOrNonArrayBodyFailsClosedToEmpty() throws Exception {
        OrchestratorReplayRunLister bad =
                new OrchestratorReplayRunLister("http://orchestrator:8080", T, clientReturning(200, "not json{"));
        assertTrue(bad.listRuns("tok").isEmpty());
        OrchestratorReplayRunLister obj =
                new OrchestratorReplayRunLister("http://orchestrator:8080", T, clientReturning(200, "{\"runId\":\"r-1\"}"));
        assertTrue(obj.listRuns("tok").isEmpty());
    }

    @Test
    void blankTokenReturnsEmptyWithoutCallingOrchestrator() throws Exception {
        HttpClient http = mock(HttpClient.class);
        OrchestratorReplayRunLister lister = new OrchestratorReplayRunLister("http://orchestrator:8080", T, http);
        assertTrue(lister.listRuns("  ").isEmpty());
        org.mockito.Mockito.verifyNoInteractions(http);
    }
}

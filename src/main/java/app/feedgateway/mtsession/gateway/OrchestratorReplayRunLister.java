package app.feedgateway.mtsession.gateway;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * {@link ReplayRunLister} backed by the orchestrator's ownership-filtered read endpoint
 * {@code GET <base>/api/hpsf/replay-runs}. Mirrors {@link OrchestratorReplayRunAuthorizer}: forwards the
 * SAME bearer (the orchestrator returns ONLY the caller's own runs) and is FAIL-CLOSED — a timeout,
 * transport failure, non-2xx, or unparseable body all yield an EMPTY list rather than surfacing the
 * orchestrator's raw response. Each run is projected to a {@link ReplayRunView} that drops {@code ownerId}
 * and all internal fields.
 */
public final class OrchestratorReplayRunLister implements ReplayRunLister {

    private final String baseUrl;
    private final Duration timeout;
    private final HttpClient http;
    private final ObjectMapper mapper = new ObjectMapper();

    public OrchestratorReplayRunLister(String baseUrl, Duration timeout) {
        this(baseUrl, timeout, HttpClient.newBuilder().connectTimeout(timeout).build());
    }

    /** Test seam: inject a stub {@link HttpClient}. */
    OrchestratorReplayRunLister(String baseUrl, Duration timeout, HttpClient http) {
        this.baseUrl = trimTrailingSlash(Objects.requireNonNull(baseUrl, "baseUrl"));
        this.timeout = Objects.requireNonNull(timeout, "timeout");
        this.http = Objects.requireNonNull(http, "http");
        if (this.baseUrl.isBlank()) {
            throw new IllegalArgumentException("orchestrator base url is required");
        }
    }

    @Override
    public List<ReplayRunView> listRuns(String bearerToken) {
        if (bearerToken == null || bearerToken.isBlank()) {
            return List.of();
        }
        HttpRequest req = HttpRequest.newBuilder(URI.create(baseUrl + "/api/hpsf/replay-runs"))
                .timeout(timeout)
                .header("Authorization", "Bearer " + bearerToken)
                .header("Accept", "application/json")
                .GET()
                .build();
        HttpResponse<String> resp;
        try {
            resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return List.of(); // fail closed
        } catch (IOException e) {
            return List.of(); // fail closed — never surface the orchestrator/transport error to the browser
        }
        if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
            return List.of(); // fail closed (401/403/404/5xx all map to "no runs", not an error body)
        }
        try {
            JsonNode arr = mapper.readTree(resp.body());
            if (arr == null || !arr.isArray()) {
                return List.of();
            }
            List<ReplayRunView> out = new ArrayList<>(arr.size());
            for (JsonNode n : arr) {
                String runId = text(n, "runId");
                if (runId == null || runId.isBlank()) {
                    continue; // a run with no id is unusable in the picker
                }
                out.add(new ReplayRunView(runId, text(n, "state"), text(n, "replayDate"),
                        text(n, "startTime"), text(n, "endTime")));
            }
            return List.copyOf(out);
        } catch (Exception parseFail) {
            return List.of(); // fail closed on any malformed/unexpected body
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return v == null || v.isNull() ? null : v.asText();
    }

    private static String trimTrailingSlash(String s) {
        String t = s.trim();
        while (t.endsWith("/")) {
            t = t.substring(0, t.length() - 1);
        }
        return t;
    }
}

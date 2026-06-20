package app.feedgateway.mtsession.gateway;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Objects;

/**
 * {@link ReplayRunAuthorizer} backed by the replay orchestrator's ownership-checked read endpoint
 * {@code GET <base>/api/hpsf/replay-runs/<runId>}. The orchestrator independently verifies the SAME
 * bearer and returns {@code 2xx} only when the token's {@code (issuer|subject)} owns the run (or is an
 * admin); any other outcome — {@code 401/403/404}, a transport failure, or a timeout — is treated as
 * DENY (fail closed). No topic is read until this returns normally.
 */
public final class OrchestratorReplayRunAuthorizer implements ReplayRunAuthorizer {

    private final String baseUrl;
    private final Duration timeout;
    private final HttpClient http;

    public OrchestratorReplayRunAuthorizer(String baseUrl, Duration timeout) {
        this(baseUrl, timeout, HttpClient.newBuilder().connectTimeout(timeout).build());
    }

    /** Test seam: inject a stub {@link HttpClient}. */
    OrchestratorReplayRunAuthorizer(String baseUrl, Duration timeout, HttpClient http) {
        this.baseUrl = trimTrailingSlash(Objects.requireNonNull(baseUrl, "baseUrl"));
        this.timeout = Objects.requireNonNull(timeout, "timeout");
        this.http = Objects.requireNonNull(http, "http");
        if (this.baseUrl.isBlank()) {
            throw new IllegalArgumentException("orchestrator base url is required");
        }
    }

    @Override
    public void authorizeRun(String bearerToken, String runId) {
        if (runId == null || runId.isBlank()) {
            throw new ReplayRunAuthorizationException("runId is required for run authorization");
        }
        if (bearerToken == null || bearerToken.isBlank()) {
            throw new ReplayRunAuthorizationException("bearer token is required for run authorization");
        }
        // runId is pre-validated to [A-Za-z0-9_-]{1,128} by ReplayParams, so encoding is a no-op here;
        // encode anyway as defense in depth against any future relaxation of that charset.
        URI uri = URI.create(baseUrl + "/api/hpsf/replay-runs/"
                + URLEncoder.encode(runId.trim(), StandardCharsets.UTF_8));
        HttpRequest req = HttpRequest.newBuilder(uri)
                .timeout(timeout)
                .header("Authorization", "Bearer " + bearerToken)
                .header("Accept", "application/json")
                .GET()
                .build();
        HttpResponse<Void> resp;
        try {
            resp = http.send(req, HttpResponse.BodyHandlers.discarding());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ReplayRunAuthorizationException("interrupted authorizing replay run " + runId, e);
        } catch (IOException e) {
            // Fail closed: ownership could not be confirmed.
            throw new ReplayRunAuthorizationException(
                    "could not reach orchestrator to authorize replay run " + runId, e);
        }
        int status = resp.statusCode();
        if (status >= 200 && status < 300) {
            return; // owner (or admin) — authorized
        }
        throw new ReplayRunAuthorizationException(
                "not authorized for replay run " + runId + " (orchestrator status " + status + ")");
    }

    private static String trimTrailingSlash(String s) {
        String t = s.trim();
        while (t.endsWith("/")) {
            t = t.substring(0, t.length() - 1);
        }
        return t;
    }
}

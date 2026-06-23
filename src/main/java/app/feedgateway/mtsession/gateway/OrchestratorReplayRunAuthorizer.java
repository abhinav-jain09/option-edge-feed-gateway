package app.feedgateway.mtsession.gateway;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final java.util.regex.Pattern ISO_DATE =
            java.util.regex.Pattern.compile("\\d{4}-\\d{2}-\\d{2}");

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
    public AuthorizedRun authorizeRun(String bearerToken, String runId) {
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
        HttpResponse<String> resp;
        try {
            resp = http.send(req, HttpResponse.BodyHandlers.ofString());
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
            // Owner (or admin) — authorized. Parse the run's replayDate best-effort: it pins the run-backed
            // replay to the authoritative chain expiry, but a missing/unparseable body NEVER fails the
            // already-confirmed authorization (UNKNOWN just leaves the client-supplied expiry in place).
            return new AuthorizedRun(parseReplayDate(resp.body()));
        }
        throw new ReplayRunAuthorizationException(
                "not authorized for replay run " + runId + " (orchestrator status " + status + ")");
    }

    /**
     * Extract {@code replayDate} from the orchestrator run JSON. Returns the value ONLY when it is a valid
     * ISO {@code yyyy-MM-dd} calendar date; null on any absence, parse error, or malformed/invalid date.
     * A null result means "unknown" — the caller keeps the client-supplied expiry — so a bad orchestrator
     * field can never pin the replay to an impossible expiry (which would recreate the zero-strikes failure).
     */
    private static String parseReplayDate(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        try {
            JsonNode node = MAPPER.readTree(body).path("replayDate");
            String date = node.isTextual() ? node.asText().trim() : "";
            if (!ISO_DATE.matcher(date).matches()) {
                return null;
            }
            java.time.LocalDate.parse(date); // reject impossible dates (e.g. 2026-13-40) — DateTimeParseException
            return date;
        } catch (RuntimeException | com.fasterxml.jackson.core.JsonProcessingException e) {
            return null;
        }
    }

    private static String trimTrailingSlash(String s) {
        String t = s.trim();
        while (t.endsWith("/")) {
            t = t.substring(0, t.length() - 1);
        }
        return t;
    }
}

package app.feedgateway.mtsession.approval;

import app.feedgateway.mtsession.ApprovalState;
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
 * Consults the authoritative approval platform (OE-DDD-001 §5.4 Config-Control) over HTTP:
 * {@code GET <base>/api/approvals/{userId}}. The platform is the source of truth for live approval —
 * the reaper re-queries it during active sessions so a SUSPEND/EXPIRE takes effect mid-session.
 *
 * <p>Fail-closed in every uncertain case: a non-2xx response (incl. 404 = no record), a transport error, a
 * timeout, or an unparseable body all DENY. A 2xx grants access only when the body's {@code state} is
 * {@code APPROVED} (and any {@code expiresAt} is in the future) — every other state denies.
 */
public final class HttpApprovalAuthority implements ApprovalAuthority {

    private final String baseUrl;
    private final String apiKey;       // optional shared secret header; null/blank → omitted
    private final Duration timeout;
    private final HttpClient http;
    private final ObjectMapper mapper = new ObjectMapper();

    public HttpApprovalAuthority(String baseUrl, String apiKey, Duration timeout) {
        this(baseUrl, apiKey, timeout, HttpClient.newBuilder().connectTimeout(timeout).build());
    }

    /** Test seam: inject a stub {@link HttpClient}. */
    HttpApprovalAuthority(String baseUrl, String apiKey, Duration timeout, HttpClient http) {
        this.baseUrl = trimTrailingSlash(Objects.requireNonNull(baseUrl, "baseUrl"));
        this.apiKey = apiKey;
        this.timeout = Objects.requireNonNull(timeout, "timeout");
        this.http = Objects.requireNonNull(http, "http");
        if (this.baseUrl.isBlank()) {
            throw new IllegalArgumentException("approval base url is required");
        }
    }

    @Override
    public ApprovalDecision decide(ApprovalQuery query) {
        if (query == null || query.userId() == null || query.userId().isBlank()) {
            return ApprovalDecision.DENY;
        }
        URI uri = URI.create(baseUrl + "/api/approvals/"
                + URLEncoder.encode(query.userId().trim(), StandardCharsets.UTF_8));
        HttpRequest.Builder b = HttpRequest.newBuilder(uri).timeout(timeout)
                .header("Accept", "application/json").GET();
        if (apiKey != null && !apiKey.isBlank()) {
            b.header("X-Approval-Api-Key", apiKey);
        }
        HttpResponse<String> resp;
        try {
            resp = http.send(b.build(), HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return ApprovalDecision.DENY;
        } catch (IOException e) {
            return ApprovalDecision.DENY; // platform unreachable → deny
        }
        int status = resp.statusCode();
        if (status < 200 || status >= 300) {
            return ApprovalDecision.DENY; // incl. 404 (no approval record) and 5xx
        }
        try {
            JsonNode root = mapper.readTree(resp.body());
            ApprovalState state = parseState(root);
            if (state != ApprovalState.APPROVED) {
                return ApprovalDecision.DENY;
            }
            long expiresAtMs = root.hasNonNull("expiresAtMs") ? root.get("expiresAtMs").asLong(0L)
                    : (root.hasNonNull("expiresAt") ? root.get("expiresAt").asLong(0L) : 0L);
            return ApprovalDecision.approved(expiresAtMs);
        } catch (RuntimeException | com.fasterxml.jackson.core.JsonProcessingException malformed) {
            return ApprovalDecision.DENY; // unparseable → deny
        }
    }

    private static ApprovalState parseState(JsonNode root) {
        // Accept {"state":"APPROVED"} or {"approved":true}; anything else is treated as not-approved.
        if (root.hasNonNull("state")) {
            try {
                return ApprovalState.valueOf(root.get("state").asText("").trim().toUpperCase());
            } catch (IllegalArgumentException unknown) {
                return ApprovalState.REJECTED;
            }
        }
        if (root.hasNonNull("approved")) {
            return root.get("approved").asBoolean(false) ? ApprovalState.APPROVED : ApprovalState.REJECTED;
        }
        return ApprovalState.REJECTED;
    }

    private static String trimTrailingSlash(String s) {
        String t = s.trim();
        while (t.endsWith("/")) {
            t = t.substring(0, t.length() - 1);
        }
        return t;
    }
}

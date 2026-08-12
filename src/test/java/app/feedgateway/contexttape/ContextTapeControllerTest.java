package app.feedgateway.contexttape;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import app.feedgateway.liquidityhistory.LiquidityHistoryAuth;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/**
 * Behavioural tests for the context-tape proxy.
 *
 * <p>What actually needs defending here is the PASSTHROUGH. The upstream's {@code 503
 * {"error":"WARMING"}} (with its own {@code Retry-After}) is the only way the page can tell "the
 * backfill is still running" apart from "the service is broken"; a proxy that helpfully normalises it
 * into a generic error silently destroys the feature. The rest is the same edge set as the other
 * proxies: auth before anything else, a per-principal budget, and transport failure as a clean 502
 * that never leaks an internal host.
 *
 * <p>Tests drive the real {@link ContextTapeUpstream} over a stubbed {@link HttpClient} rather than
 * mocking the upstream, so URL construction, header forwarding and timeout shape are covered too.
 */
class ContextTapeControllerTest {

    private static final String SESSION_JSON =
            "{\"schemaVersion\":1,\"service\":\"context-tape\",\"state\":\"live\","
            + "\"sessionDate\":\"2026-08-12\",\"generatedAtMs\":1786500000000,"
            + "\"spot\":{\"lastPrice\":7727.79,\"present\":true},"
            + "\"candles\":[{\"tMs\":1786455000000,\"o\":7763.1,\"h\":7764.0,\"l\":7761.2,"
            + "\"c\":7763.6,\"buyVol\":1234,\"sellVol\":987}]}";

    private static final String WARMING_JSON = "{\"error\":\"WARMING\"}";

    // ---------------------------------------------------------------- helpers

    private static LiquidityHistoryAuth authReturning(int status) {
        LiquidityHistoryAuth auth = mock(LiquidityHistoryAuth.class);
        // enforcing() must be stubbed TRUE: the route fails closed on it, so a bare mock (false)
        // would 401 every test before the behaviour under test was ever reached.
        when(auth.enforcing()).thenReturn(true);
        when(auth.authenticate(any())).thenReturn(new LiquidityHistoryAuth.Result(status, "tester"));
        return auth;
    }

    private static HttpHeaders headers(Map<String, List<String>> map) {
        return HttpHeaders.of(map, (k, v) -> true);
    }

    private static HttpHeaders contentType(String value) {
        return headers(value == null ? Map.of() : Map.of("content-type", List.of(value)));
    }

    /** A fresh body stream per call, so the rate-limit test can make several successful requests. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static HttpClient clientReturning(int status, HttpHeaders headers, byte[] body) throws Exception {
        HttpClient http = mock(HttpClient.class);
        HttpResponse resp = mock(HttpResponse.class);
        when(resp.statusCode()).thenReturn(status);
        when(resp.headers()).thenReturn(headers);
        when(resp.body()).thenAnswer(inv -> new ByteArrayInputStream(body));
        when(http.send(any(HttpRequest.class), any())).thenReturn(resp);
        return http;
    }

    private static HttpClient clientReturning(int status, String contentType, String body) throws Exception {
        return clientReturning(status, contentType(contentType), body.getBytes(StandardCharsets.UTF_8));
    }

    /** Every {@link ContextTapeUpstream} built here owns reader and closer pools; close them all. */
    private final List<ContextTapeUpstream> upstreams = new ArrayList<>();

    @AfterEach
    void closeEverythingThisTestOpened() {
        upstreams.forEach(ContextTapeUpstream::close);
        upstreams.clear();
    }

    private ContextTapeUpstream upstream(HttpClient http) {
        ContextTapeUpstream created =
                new ContextTapeUpstream("http://context-tape-service:8134/", Duration.ofSeconds(5), http);
        upstreams.add(created);
        return created;
    }

    private ContextTapeController controller(HttpClient http, int authStatus) {
        return controller(http, authStatus, Integer.MAX_VALUE);
    }

    private ContextTapeController controller(HttpClient http, int authStatus, int rateLimitPerMinute) {
        return new ContextTapeController(upstream(http), authReturning(authStatus), new ObjectMapper(),
                rateLimitPerMinute);
    }

    private static String bodyText(ResponseEntity<byte[]> res) {
        return new String(res.getBody(), StandardCharsets.UTF_8);
    }

    private static HttpRequest capturedRequest(HttpClient http) throws Exception {
        ArgumentCaptor<HttpRequest> req = ArgumentCaptor.forClass(HttpRequest.class);
        verify(http).send(req.capture(), any());
        return req.getValue();
    }

    /**
     * Closes are one-shot and OFF-THREAD now, so tests await them rather than assume them. Measured
     * with nanoTime: a wall-clock step during a test must not stretch or shrink this wait.
     */
    private static boolean waitFor(java.util.function.BooleanSupplier condition, long timeoutMs)
            throws InterruptedException {
        long deadlineNanos = System.nanoTime() + timeoutMs * 1_000_000L;
        while (System.nanoTime() < deadlineNanos) {
            if (condition.getAsBoolean()) {
                return true;
            }
            Thread.sleep(5L);
        }
        return condition.getAsBoolean();
    }

    // ---------------------------------------------------------------- passthrough

    @Test
    void theSessionSnapshotIsServedVerbatim() throws Exception {
        HttpClient http = clientReturning(200, "application/json", SESSION_JSON);
        ResponseEntity<byte[]> res = controller(http, 200).session("Bearer t");

        assertEquals(200, res.getStatusCode().value());
        assertEquals(SESSION_JSON, bodyText(res), "the snapshot must not be reshaped in transit");
        assertEquals(MediaType.APPLICATION_JSON, res.getHeaders().getContentType());
        assertEquals("http://context-tape-service:8134/api/context-tape/session",
                capturedRequest(http).uri().toString());
    }

    @Test
    void theWarming503KeepsItsStatusBodyAndRetryAfter() throws Exception {
        // The whole point of the endpoint's error contract: WARMING is a meaningful state the page
        // renders ("backfill in progress"), not a failure to collapse into a generic error.
        HttpClient http = clientReturning(503,
                headers(Map.of("content-type", List.of("application/json"), "retry-after", List.of("5"))),
                WARMING_JSON.getBytes(StandardCharsets.UTF_8));

        ResponseEntity<byte[]> res = controller(http, 200).session("Bearer t");

        assertEquals(503, res.getStatusCode().value(), "the upstream's WARMING status must survive");
        assertEquals(WARMING_JSON, bodyText(res), "and its body byte-for-byte");
        assertEquals("5", res.getHeaders().getFirst("Retry-After"),
                "the service already said how long to wait; the page should not have to invent it");
    }

    @Test
    void anUpstream500KeepsItsStatusAndBodyVerbatim() throws Exception {
        // e.g. SNAPSHOT_TOO_LARGE: the service's own 5xx vocabulary is part of the page's error
        // contract exactly as WARMING is, and must not be collapsed into a gateway-authored error.
        String body = "{\"error\":\"SNAPSHOT_TOO_LARGE\",\"detail\":\"session exceeded the publish cap\"}";
        HttpClient http = clientReturning(500, "application/json", body);

        ResponseEntity<byte[]> res = controller(http, 200).session("Bearer t");

        assertEquals(500, res.getStatusCode().value(), "the upstream's own 500 must survive");
        assertEquals(body, bodyText(res), "and its body byte-for-byte");
    }

    @Test
    void aLiveSnapshotIsNeverCacheable() throws Exception {
        // A snapshot is a measurement of a moving market. Any intermediary replaying one would be
        // showing a state that was true at a moment it does not name.
        ResponseEntity<byte[]> res =
                controller(clientReturning(200, "application/json", SESSION_JSON), 200).session("Bearer t");
        assertTrue(res.getHeaders().getCacheControl().contains("no-store"), res.getHeaders().toString());
    }

    @Test
    void arbitraryBytesSurviveByteForByte() throws Exception {
        // "Verbatim" has to mean verbatim for BYTES. Carrying the body as a String makes the response
        // charset a guess by a message converter, and neither of these payloads survives a guess:
        // one is not valid UTF-8 at all, and the other contains a NUL.
        byte[] hostile = {0x7B, (byte) 0xFF, (byte) 0xFE, 0x00, 0x41, (byte) 0xC3, 0x28, 0x7D};
        HttpClient http = clientReturning(200, contentType("application/octet-stream"), hostile);

        ResponseEntity<byte[]> res = controller(http, 200).session("Bearer t");

        assertArrayEquals(hostile, res.getBody(),
                "no charset may be applied to a body this proxy promised to carry unchanged");
    }

    @Test
    void aMissingOrMalformedUpstreamContentTypeFallsBackRatherThanFailing() throws Exception {
        for (String bad : new String[]{null, "", "not/a/media/type", "*/*", "application/*"}) {
            HttpClient http = clientReturning(200, contentType(bad), SESSION_JSON.getBytes(StandardCharsets.UTF_8));
            ResponseEntity<byte[]> res = controller(http, 200).session("Bearer t");
            assertEquals(200, res.getStatusCode().value(), "content-type: " + bad);
            assertEquals(MediaType.APPLICATION_JSON, res.getHeaders().getContentType(), "content-type: " + bad);
            assertEquals(SESSION_JSON, bodyText(res));
        }
    }

    // ---------------------------------------------------------------- auth

    @Test
    void authIsEnforcedBeforeTheUpstreamIsEvenCalled() {
        for (int status : new int[]{401, 403}) {
            HttpClient http = mock(HttpClient.class);
            assertEquals(status, controller(http, status).session(null).getStatusCode().value());
            verifyNoInteractions(http);
        }
    }

    @Test
    void theRouteFailsClosedWhenAuthIsNotEnforcing() throws Exception {
        // Mirrors /api/pin-flow: with both auth switches off (local dev) the shared verifier serves an
        // authenticated "anonymous" principal. That fallback must NOT open this route — it answers 401
        // before the verifier is even invoked, and the upstream is never touched.
        LiquidityHistoryAuth openAuth = mock(LiquidityHistoryAuth.class);
        when(openAuth.enforcing()).thenReturn(false);
        when(openAuth.authenticate(any())).thenReturn(new LiquidityHistoryAuth.Result(200, "anonymous"));
        HttpClient http = mock(HttpClient.class);
        ContextTapeController controller = new ContextTapeController(
                upstream(http), openAuth, new ObjectMapper(), Integer.MAX_VALUE);

        ResponseEntity<byte[]> res = controller.session("Bearer t");

        assertEquals(401, res.getStatusCode().value(),
                "an un-enforcing gateway must refuse, not serve session data unauthenticated");
        assertNotNull(new ObjectMapper().readTree(res.getBody()).get("error"),
                "and say why, in the pin-flow refusal shape");
        org.mockito.Mockito.verify(openAuth, org.mockito.Mockito.never()).authenticate(any());
        verifyNoInteractions(http);
    }

    // ---------------------------------------------------------------- rate limit

    @Test
    void requestsBeyondTheBudgetAre429WithRetryAfterAndNeverReachTheUpstream() throws Exception {
        HttpClient http = clientReturning(200, "application/json", SESSION_JSON);
        ContextTapeController controller = controller(http, 200, 2);

        assertEquals(200, controller.session("Bearer t").getStatusCode().value());
        assertEquals(200, controller.session("Bearer t").getStatusCode().value());

        ResponseEntity<byte[]> refused = controller.session("Bearer t");
        assertEquals(429, refused.getStatusCode().value(), "the burst must be refused, not proxied");
        assertEquals("RATE_LIMITED",
                new ObjectMapper().readTree(refused.getBody()).get("error").asText(),
                "the refusal must be self-describing, not a bare status");
        assertNotNull(refused.getHeaders().getFirst("Retry-After"),
                "a refused caller must be told when to come back");
        verify(http, times(2)).send(any(HttpRequest.class), any());
    }

    @Test
    void aCallerBeyondTheConcurrencyCapIsRefusedImmediatelyWithACodeOfItsOwn() throws Exception {
        // The rate limiter counts requests per window; it says nothing about how many are IN FLIGHT.
        // Each in-flight call holds a Tomcat worker for up to the whole request budget, so the
        // overflow must be answered NOW, with a token distinguishable from the upstream's WARMING 503.
        java.util.concurrent.CountDownLatch entered = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch release = new java.util.concurrent.CountDownLatch(1);
        @SuppressWarnings({"unchecked", "rawtypes"})
        HttpClient http = mock(HttpClient.class);
        @SuppressWarnings({"unchecked", "rawtypes"})
        HttpResponse resp = mock(HttpResponse.class);
        when(resp.statusCode()).thenReturn(200);
        when(resp.headers()).thenReturn(contentType("application/json"));
        when(resp.body()).thenAnswer(inv -> new ByteArrayInputStream(
                SESSION_JSON.getBytes(StandardCharsets.UTF_8)));
        when(http.send(any(HttpRequest.class), any())).thenAnswer(inv -> {
            entered.countDown();
            release.await(5, java.util.concurrent.TimeUnit.SECONDS);
            return resp;
        });
        ContextTapeController controller = new ContextTapeController(
                upstream(http), authReturning(200), new ObjectMapper(), Integer.MAX_VALUE, 1);

        Thread first = new Thread(() -> controller.session("Bearer t"));
        first.setDaemon(true);
        first.start();
        assertTrue(entered.await(5, java.util.concurrent.TimeUnit.SECONDS),
                "the first call must be in flight before the overflow is attempted");

        ResponseEntity<byte[]> refused = controller.session("Bearer t");
        assertEquals(503, refused.getStatusCode().value());
        assertEquals("GATEWAY_BUSY",
                new ObjectMapper().readTree(refused.getBody()).get("error").asText());
        assertEquals("1", refused.getHeaders().getFirst("Retry-After"));

        release.countDown();
        first.join(5_000L);
        assertEquals(1, controller.sessionSlots.availablePermits(),
                "the in-flight call must give its slot back however it ends");
    }

    @Test
    void theRateLimiterCountsPerPrincipalAndReturnsRetryAfter() {
        ContextTapeController.RateLimiter limiter = new ContextTapeController.RateLimiter(2, 60_000L);
        assertEquals(0L, limiter.tryAcquire("a", 0));
        assertEquals(0L, limiter.tryAcquire("a", 0));
        assertTrue(limiter.tryAcquire("a", 0) > 0, "third call in the window is refused");
        assertEquals(0L, limiter.tryAcquire("b", 0), "a different principal has its own budget");
        assertEquals(0L, limiter.tryAcquire("a", 60_000L), "the window rolls");
    }

    @Test
    void theLimiterEvictsExpiredPrincipalsInsteadOfGrowingForever() {
        ContextTapeController.RateLimiter limiter = new ContextTapeController.RateLimiter(1, 60_000L, 2);
        assertEquals(0L, limiter.tryAcquire("a", 0));
        assertEquals(0L, limiter.tryAcquire("b", 0));
        assertEquals(2, limiter.trackedPrincipals());

        // Both entries are two windows stale by now: a new principal triggers eviction and fits.
        assertEquals(0L, limiter.tryAcquire("c", 200_000L),
                "expired entries must be reclaimed, not counted against the cap");
        assertTrue(limiter.trackedPrincipals() <= 2, "cardinality must stay bounded");

        // And when every tracked principal is LIVE, a newcomer is refused — briefly — rather than
        // tracked unboundedly or waved through untracked.
        assertEquals(0L, limiter.tryAcquire("d", 200_000L));
        assertTrue(limiter.tryAcquire("e", 200_000L) > 0,
                "a full map of live principals must fail closed for the newcomer");
    }

    @Test
    void aBackwardsClockStepNeverMintsAFreshBudget() {
        // System.currentTimeMillis() can step backwards (NTP, a resumed VM). The window start is
        // clamped rather than compared raw: raw, the elapsed time goes negative, the window never
        // rolls, and the computed Retry-After exceeds the window itself.
        ContextTapeController.RateLimiter limiter = new ContextTapeController.RateLimiter(2, 60_000L);
        assertEquals(0L, limiter.tryAcquire("a", 10_000L));
        assertEquals(0L, limiter.tryAcquire("a", 10_000L));

        long retryAfter = limiter.tryAcquire("a", 4_000L); // the clock stepped back 6s
        assertTrue(retryAfter > 0, "a clock step must not mint a fresh budget");
        assertTrue(retryAfter <= 60L, "and the Retry-After must never exceed the window: " + retryAfter);

        assertEquals(0L, limiter.tryAcquire("a", 4_000L + 60_000L), "the clamped window still rolls");
    }

    // ---------------------------------------------------------------- upstream failure

    @Test
    void anUnreachableUpstreamIsACleanJsonErrorNotAStackTrace() throws Exception {
        HttpClient http = mock(HttpClient.class);
        when(http.send(any(HttpRequest.class), any()))
                .thenThrow(new IOException("Connection refused to context-tape-service/10.42.0.9:8134"));

        ResponseEntity<byte[]> res = controller(http, 200).session("Bearer t");

        // 502, not 503: the upstream authors its OWN 503 (WARMING) and the page must be able to tell
        // "gateway could not reach it" apart from that.
        assertEquals(502, res.getStatusCode().value());
        JsonNode body = new ObjectMapper().readTree(res.getBody());
        assertEquals("UPSTREAM_UNAVAILABLE", body.get("error").asText());
        assertEquals("UPSTREAM_UNAVAILABLE", body.get("code").asText());
        assertNotNull(body.get("detail"));
        assertEquals("5", res.getHeaders().getFirst("Retry-After"),
                "the contract puts Retry-After on every gateway-authored addition, the 502s included");
        assertFalse(bodyText(res).contains("10.42.0.9"), "internal host/port must not leak to the browser");
        assertFalse(bodyText(res).contains("Exception"), "no stack trace or exception class in the body");
    }

    @Test
    void aSnapshotBiggerThanTheCapIsAnHonest502NotATruncatedSnapshot() throws Exception {
        // A prefix of a snapshot delivered under the upstream's own 200 is the worst possible answer:
        // the page would parse it and render whatever survived the cut as if it were the whole session.
        byte[] huge = new byte[ContextTapeUpstream.MAX_SESSION_BYTES + 1];
        java.util.Arrays.fill(huge, (byte) 'x');
        HttpClient http = clientReturning(200, contentType("application/json"), huge);

        ResponseEntity<byte[]> res = controller(http, 200).session("Bearer t");

        assertEquals(502, res.getStatusCode().value());
        assertEquals("UPSTREAM_PROTOCOL_ERROR",
                new ObjectMapper().readTree(res.getBody()).get("error").asText());
        assertEquals("5", res.getHeaders().getFirst("Retry-After"),
                "both gateway-authored 502 codes carry Retry-After per the contract");
    }

    @Test
    void aBodyThatFailsMidReadIsA502NotTheUpstreamStatusWithAnEmptyBody() throws Exception {
        InputStream broken = new InputStream() {
            private int served = 0;

            @Override
            public int read() throws IOException {
                throw new IOException("connection reset");
            }

            @Override
            public int read(byte[] b, int off, int len) throws IOException {
                if (served++ == 0) {
                    b[off] = '{';
                    return 1;
                }
                throw new IOException("connection reset");
            }
        };
        @SuppressWarnings({"unchecked", "rawtypes"})
        HttpClient http = mock(HttpClient.class);
        @SuppressWarnings({"unchecked", "rawtypes"})
        HttpResponse resp = mock(HttpResponse.class);
        when(resp.statusCode()).thenReturn(200);
        when(resp.headers()).thenReturn(contentType("application/json"));
        when(resp.body()).thenReturn(broken);
        when(http.send(any(HttpRequest.class), any())).thenReturn(resp);

        ResponseEntity<byte[]> res = controller(http, 200).session("Bearer t");

        // NOT 200-with-a-partial-body: that would be this gateway inventing an answer and signing it
        // with the upstream's status.
        assertEquals(502, res.getStatusCode().value());
        assertEquals("UPSTREAM_PROTOCOL_ERROR",
                new ObjectMapper().readTree(res.getBody()).get("error").asText());
    }

    @Test
    void anExceptionWhileBUILDINGTheRequestIsA502NotA500() throws Exception {
        // A base URL with no scheme fails inside request construction, which is NOT an IOException.
        // Outside the mapped block it reaches the browser as a 500 with a stack trace.
        HttpClient http = mock(HttpClient.class);
        ContextTapeUpstream misconfigured =
                new ContextTapeUpstream("context-tape-service:8134", Duration.ofSeconds(5), http);
        upstreams.add(misconfigured);
        ContextTapeController controller = new ContextTapeController(
                misconfigured, authReturning(200), new ObjectMapper(), Integer.MAX_VALUE);

        ResponseEntity<byte[]> res = controller.session("Bearer t");
        assertEquals(502, res.getStatusCode().value());
        assertEquals("UPSTREAM_UNAVAILABLE",
                new ObjectMapper().readTree(res.getBody()).get("error").asText());
        verifyNoInteractions(http);
    }

    @Test
    void aCompressedBodyIsRefusedRatherThanForwardedAsUnreadableBytes() throws Exception {
        // Accept-Encoding: identity is a REQUEST header — a wish. If something compresses anyway,
        // forwarding those bytes while dropping Content-Encoding hands the browser a gzip stream
        // labelled as JSON: not corrupt in any way that reports itself, simply unreadable.
        HttpClient http = clientReturning(200,
                headers(Map.of("content-type", List.of("application/json"),
                               "content-encoding", List.of("gzip"))),
                new byte[]{0x1f, (byte) 0x8b, 0x08, 0x00});

        ResponseEntity<byte[]> res = controller(http, 200).session("Bearer t");

        assertEquals(502, res.getStatusCode().value());
        assertEquals("UPSTREAM_PROTOCOL_ERROR",
                new ObjectMapper().readTree(res.getBody()).get("error").asText());
    }

    @Test
    void duplicateOrCommaSeparatedContentEncodingCannotSneakCompressionPast() throws Exception {
        // `Content-Encoding: identity` followed by a second field carrying gzip — or one field
        // saying "identity, gzip" — is a compressed body. Judging only the first value would forward
        // gzip bytes labelled as JSON after the header was dropped.
        HttpHeaders duplicated = headers(Map.of(
                "content-type", List.of("application/json"),
                "content-encoding", List.of("identity", "gzip")));
        HttpHeaders commaJoined = headers(Map.of(
                "content-type", List.of("application/json"),
                "content-encoding", List.of("identity, gzip")));
        for (HttpHeaders sneaky : new HttpHeaders[]{duplicated, commaJoined}) {
            HttpClient http = clientReturning(200, sneaky, new byte[]{0x1f, (byte) 0x8b, 0x08, 0x00});
            ResponseEntity<byte[]> res = controller(http, 200).session("Bearer t");
            assertEquals(502, res.getStatusCode().value(), sneaky.map().toString());
            assertEquals("UPSTREAM_PROTOCOL_ERROR",
                    new ObjectMapper().readTree(res.getBody()).get("error").asText());
        }

        // But identity repeated is still just identity.
        HttpClient http = clientReturning(200, headers(Map.of(
                "content-type", List.of("application/json"),
                "content-encoding", List.of("identity", "identity"))),
                SESSION_JSON.getBytes(StandardCharsets.UTF_8));
        assertEquals(200, controller(http, 200).session("Bearer t").getStatusCode().value());
    }

    @Test
    void identityEncodingIsRequestedOnTheWire() throws Exception {
        HttpClient http = clientReturning(200, "application/json", SESSION_JSON);
        controller(http, 200).session("Bearer t");
        assertEquals("identity", capturedRequest(http).headers().firstValue("Accept-Encoding").orElse(""));
    }

    // ---------------------------------------------------------------- timeout shape

    @Test
    void theConfiguredBudgetIsPlacedOnTheWireAndTheBodyWaitGetsOnlyTheRemainder() throws Exception {
        // The wire timeout bounds the handshake; the body wait then gets what REMAINS of the SAME
        // budget — not a fresh copy, which would have made a 400ms setting mean up to 800ms. Proven
        // by construction: a 250ms handshake against a 400ms budget must fail the never-delivering
        // body at ~400ms total, well before the ~650ms a fresh-copy bug would take.
        java.util.concurrent.CountDownLatch closed = new java.util.concurrent.CountDownLatch(1);
        InputStream neverDelivers = new InputStream() {
            @Override
            public int read() {
                return read(new byte[1], 0, 1);
            }

            @Override
            public int read(byte[] b, int off, int len) {
                try {
                    closed.await(10, java.util.concurrent.TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return -1;
            }

            @Override
            public void close() {
                closed.countDown();
            }
        };
        @SuppressWarnings({"unchecked", "rawtypes"})
        HttpClient http = mock(HttpClient.class);
        @SuppressWarnings({"unchecked", "rawtypes"})
        HttpResponse resp = mock(HttpResponse.class);
        when(resp.statusCode()).thenReturn(200);
        when(resp.headers()).thenReturn(contentType("application/json"));
        when(resp.body()).thenReturn(neverDelivers);
        when(http.send(any(HttpRequest.class), any())).thenAnswer(inv -> {
            Thread.sleep(1_500L); // the handshake consumes most of the budget
            return resp;
        });
        ContextTapeUpstream up = new ContextTapeUpstream("http://context-tape-service:8134",
                Duration.ofMillis(2_000), http);
        upstreams.add(up);

        long startedNanos = System.nanoTime();
        ResponseEntity<byte[]> res = new ContextTapeController(
                up, authReturning(200), new ObjectMapper(), Integer.MAX_VALUE).session("Bearer t");
        long elapsedMs = (System.nanoTime() - startedNanos) / 1_000_000L;

        assertEquals(502, res.getStatusCode().value());
        assertEquals(Duration.ofMillis(2_000), capturedRequest(http).timeout().orElse(null),
                "the configured budget must bound the handshake on the wire");
        // Genuinely timing-bound — the property IS a duration (remainder ≈2000ms total vs a
        // fresh-copy bug's ≈3500ms) — so the scenario is scaled to give ~1s of margin in BOTH
        // directions against CI descheduling rather than asserting a tight wall-clock figure.
        assertTrue(elapsedMs < 3_000L,
                "the body wait must get the REMAINDER of the budget, not a fresh copy; took "
                + elapsedMs + "ms against a 2000ms budget");
    }

    @Test
    void anExhaustedBudgetIsAnImmediate502NotAGracePeriod() throws Exception {
        // The handshake consumed the whole configured budget. Granting the body read a floor of
        // "just a little more" would let a request exceed its documented bound — at the minimum
        // setting, by more than 100%.
        @SuppressWarnings({"unchecked", "rawtypes"})
        HttpClient http = mock(HttpClient.class);
        @SuppressWarnings({"unchecked", "rawtypes"})
        HttpResponse resp = mock(HttpResponse.class);
        java.util.concurrent.atomic.AtomicBoolean bodyClosed = new java.util.concurrent.atomic.AtomicBoolean();
        when(resp.statusCode()).thenReturn(200);
        when(resp.headers()).thenReturn(contentType("application/json"));
        when(resp.body()).thenReturn(new ByteArrayInputStream(new byte[0]) {
            @Override
            public void close() {
                bodyClosed.set(true);
            }
        });
        when(http.send(any(HttpRequest.class), any())).thenAnswer(inv -> {
            Thread.sleep(20L); // longer than the whole 1ms budget below
            return resp;
        });
        ContextTapeUpstream up = new ContextTapeUpstream("http://context-tape-service:8134",
                Duration.ofMillis(1), http);
        upstreams.add(up);
        ResponseEntity<byte[]> res = new ContextTapeController(
                up, authReturning(200), new ObjectMapper(), Integer.MAX_VALUE).session("Bearer t");

        assertEquals(502, res.getStatusCode().value());
        assertEquals("UPSTREAM_PROTOCOL_ERROR",
                new ObjectMapper().readTree(res.getBody()).get("error").asText());
        assertTrue(waitFor(bodyClosed::get, 5_000L),
                "the abandoned exchange must be closed (off-thread), not leaked");
    }

    @Test
    void aDeadlineSurfacingAsCleanEofIsA502NotAPartialSnapshotAndClosesOnTheCloserPool() throws Exception {
        // Two invariants in one scenario, because they share the mechanism. (1) close() is not
        // guaranteed to make a parked read throw — some streams surface it as a clean -1. Accepting
        // that would return the bytes accumulated so far under the upstream's 200: a PARTIAL
        // snapshot wearing a trustworthy status. (2) The blocking close itself must run on the
        // closer pool, never on a request or reader thread — a stuck close anywhere else would pin
        // exactly the resource the deadline exists to free.
        java.util.concurrent.CountDownLatch closed = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.atomic.AtomicReference<String> closeThread =
                new java.util.concurrent.atomic.AtomicReference<>();
        InputStream partialThenEof = new InputStream() {
            private boolean served;

            @Override
            public int read() {
                return -1;
            }

            @Override
            public int read(byte[] b, int off, int len) {
                if (!served) {
                    served = true;
                    b[off] = '{'; // a real prefix of a real body — the bytes a lax proxy would serve
                    return 1;
                }
                try {
                    closed.await(5, java.util.concurrent.TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return -1; // the close surfaces as CLEAN EOF, not an IOException
            }

            @Override
            public void close() {
                closeThread.compareAndSet(null, Thread.currentThread().getName());
                closed.countDown();
            }
        };
        @SuppressWarnings({"unchecked", "rawtypes"})
        HttpClient http = mock(HttpClient.class);
        @SuppressWarnings({"unchecked", "rawtypes"})
        HttpResponse resp = mock(HttpResponse.class);
        when(resp.statusCode()).thenReturn(200);
        when(resp.headers()).thenReturn(contentType("application/json"));
        when(resp.body()).thenReturn(partialThenEof);
        when(http.send(any(HttpRequest.class), any())).thenReturn(resp);
        ContextTapeUpstream up = new ContextTapeUpstream("http://context-tape-service:8134",
                Duration.ofMillis(300), http);
        upstreams.add(up);

        ResponseEntity<byte[]> res = new ContextTapeController(
                up, authReturning(200), new ObjectMapper(), Integer.MAX_VALUE).session("Bearer t");

        assertEquals(502, res.getStatusCode().value(),
                "a body the deadline cut short must NEVER be forwarded as the upstream's 200");
        assertEquals("UPSTREAM_PROTOCOL_ERROR",
                new ObjectMapper().readTree(res.getBody()).get("error").asText());
        assertTrue(waitFor(() -> closeThread.get() != null, 5_000L),
                "the deadline path must have the stream closed (off-thread, so it is awaited)");
        assertTrue(closeThread.get().startsWith("context-tape-closer"),
                "the potentially blocking close must run on the closer pool, not the request thread; ran on "
                + closeThread.get());
    }

    @Test
    void aBodyCompletingAfterTheDeadlineIsRejectedByTheWaitersOwnClock() throws Exception {
        // The hard-deadline property: nothing has to happen ON TIME anywhere else — no timer
        // firing, no close succeeding — for a late body to be rejected. The stream here delivers a
        // clean EOF entirely on its own, 500ms late, with the close playing no part; the request
        // thread's own timed wait is what expires.
        InputStream lateEof = new InputStream() {
            private boolean served;

            @Override
            public int read() {
                return -1;
            }

            @Override
            public int read(byte[] b, int off, int len) {
                if (!served) {
                    served = true;
                    b[off] = '{';
                    return 1;
                }
                try {
                    Thread.sleep(2_500L); // then a clean, un-prompted EOF — but late
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return -1;
            }
        };
        @SuppressWarnings({"unchecked", "rawtypes"})
        HttpClient http = mock(HttpClient.class);
        @SuppressWarnings({"unchecked", "rawtypes"})
        HttpResponse resp = mock(HttpResponse.class);
        when(resp.statusCode()).thenReturn(200);
        when(resp.headers()).thenReturn(contentType("application/json"));
        when(resp.body()).thenReturn(lateEof);
        when(http.send(any(HttpRequest.class), any())).thenReturn(resp);
        ContextTapeUpstream up = new ContextTapeUpstream("http://context-tape-service:8134",
                Duration.ofMillis(300), http);
        upstreams.add(up);

        long startedNanos = System.nanoTime();
        ResponseEntity<byte[]> res = new ContextTapeController(
                up, authReturning(200), new ObjectMapper(), Integer.MAX_VALUE).session("Bearer t");
        long elapsedMs = (System.nanoTime() - startedNanos) / 1_000_000L;

        assertEquals(502, res.getStatusCode().value(),
                "a body that completed after the deadline must be discarded, not accepted");
        // Genuinely timing-bound — the property IS "the waiter escapes at ~300ms rather than the
        // stream's own 2500ms" — so the scenario is scaled to give well over a second of margin in
        // both directions against CI descheduling. (The interrupt from cancel() may shorten the
        // stream's sleep; that only widens the margin on the failing side's 502, which the
        // completion-time check still produces.)
        assertTrue(elapsedMs < 2_000L,
                "and the request thread must escape ON the deadline, not when the stream ends; took "
                + elapsedMs + "ms against a 300ms budget");
    }

    @Test
    void everyPathClosesTheStreamOffTheRequestThreadExactlyOnce() throws Exception {
        // A blocking close() ANYWHERE on the request thread would let a hostile stream defeat the
        // whole-request deadline and pin its bulkhead slot — including the mundane paths: the
        // ordinary success cleanup and a protocol rejection. Both must dispose through the one-shot
        // handle: on the closer pool, never on the caller, never twice.
        String requestThread = Thread.currentThread().getName();

        // SUCCESS path.
        java.util.concurrent.atomic.AtomicInteger successCloses = new java.util.concurrent.atomic.AtomicInteger();
        java.util.concurrent.atomic.AtomicReference<String> successThread =
                new java.util.concurrent.atomic.AtomicReference<>();
        InputStream happy = new ByteArrayInputStream(SESSION_JSON.getBytes(StandardCharsets.UTF_8)) {
            @Override
            public void close() {
                successCloses.incrementAndGet();
                successThread.compareAndSet(null, Thread.currentThread().getName());
            }
        };
        @SuppressWarnings({"unchecked", "rawtypes"})
        HttpClient okHttp = mock(HttpClient.class);
        @SuppressWarnings({"unchecked", "rawtypes"})
        HttpResponse okResp = mock(HttpResponse.class);
        when(okResp.statusCode()).thenReturn(200);
        when(okResp.headers()).thenReturn(contentType("application/json"));
        when(okResp.body()).thenReturn(happy);
        when(okHttp.send(any(HttpRequest.class), any())).thenReturn(okResp);
        assertEquals(200, controller(okHttp, 200).session("Bearer t").getStatusCode().value());
        assertTrue(waitFor(() -> successCloses.get() >= 1, 5_000L), "the exchange must be closed");
        Thread.sleep(50L); // give a hypothetical second close its chance to happen
        assertEquals(1, successCloses.get(), "one-shot means ONCE, however many owners disposed");
        assertTrue(successThread.get().startsWith("context-tape-closer"),
                "the close ran on " + successThread.get() + ", not the closer pool");
        assertFalse(successThread.get().equals(requestThread), "and never on the request thread");

        // PROTOCOL-REJECTION path (compressed body refused before the read).
        java.util.concurrent.atomic.AtomicInteger rejectCloses = new java.util.concurrent.atomic.AtomicInteger();
        java.util.concurrent.atomic.AtomicReference<String> rejectThread =
                new java.util.concurrent.atomic.AtomicReference<>();
        InputStream compressed = new ByteArrayInputStream(new byte[]{0x1f, (byte) 0x8b}) {
            @Override
            public void close() {
                rejectCloses.incrementAndGet();
                rejectThread.compareAndSet(null, Thread.currentThread().getName());
            }
        };
        @SuppressWarnings({"unchecked", "rawtypes"})
        HttpClient gzHttp = mock(HttpClient.class);
        @SuppressWarnings({"unchecked", "rawtypes"})
        HttpResponse gzResp = mock(HttpResponse.class);
        when(gzResp.statusCode()).thenReturn(200);
        when(gzResp.headers()).thenReturn(headers(Map.of(
                "content-type", List.of("application/json"),
                "content-encoding", List.of("gzip"))));
        when(gzResp.body()).thenReturn(compressed);
        when(gzHttp.send(any(HttpRequest.class), any())).thenReturn(gzResp);
        assertEquals(502, controller(gzHttp, 200).session("Bearer t").getStatusCode().value());
        assertTrue(waitFor(() -> rejectCloses.get() >= 1, 5_000L), "the rejected exchange must be closed");
        Thread.sleep(50L);
        assertEquals(1, rejectCloses.get());
        assertTrue(rejectThread.get().startsWith("context-tape-closer"),
                "the rejection close ran on " + rejectThread.get() + ", not the closer pool");
    }

    @Test
    void aHugeContentEncodingIsBoundedBeforeItEntersTheExceptionMessage() throws Exception {
        // The exception message is copied around — rethrown, wrapped, printed by frameworks — and
        // every copy that is not the one sanitised log call would carry the unbounded original. So
        // the remote text is bounded where it ENTERS the message, not where one log line prints it.
        String huge = "x".repeat(5_000);
        HttpClient http = clientReturning(200, headers(Map.of(
                "content-type", List.of("application/json"),
                "content-encoding", List.of(huge))),
                SESSION_JSON.getBytes(StandardCharsets.UTF_8));

        ContextTapeUpstream.UnavailableException thrown = assertThrows(
                ContextTapeUpstream.UnavailableException.class, () -> upstream(http).session());

        assertTrue(thrown.getMessage().length() < 200,
                "remote header text must be bounded at the exception, was "
                + thrown.getMessage().length() + " chars");
        assertFalse(thrown.getMessage().contains(huge), "and never carried whole");
    }

    @Test
    void theCardinalityCapHoldsStrictlyUnderAConcurrentHammer() throws Exception {
        // The cap is only a cap if racing newcomers cannot each pass the size check: 8 threads
        // pushing 200 distinct LIVE principals each against a cap of 50 must end at <= 50 tracked.
        ContextTapeController.RateLimiter limiter =
                new ContextTapeController.RateLimiter(1_000, 60_000L, 50);
        int threads = 8;
        java.util.concurrent.ExecutorService pool =
                java.util.concurrent.Executors.newFixedThreadPool(threads);
        try {
            java.util.concurrent.CountDownLatch go = new java.util.concurrent.CountDownLatch(1);
            List<java.util.concurrent.Future<?>> workers = new ArrayList<>();
            for (int t = 0; t < threads; t++) {
                int worker = t;
                workers.add(pool.submit(() -> {
                    go.await();
                    for (int i = 0; i < 200; i++) {
                        limiter.tryAcquire("w" + worker + "-p" + i, 0L);
                    }
                    return null;
                }));
            }
            go.countDown();
            for (java.util.concurrent.Future<?> w : workers) {
                w.get(10, java.util.concurrent.TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }
        assertTrue(limiter.trackedPrincipals() <= 50,
                "the cap must be strict under concurrency, was " + limiter.trackedPrincipals());
    }

    @Test
    void permanentlyBlockingClosesStayBoundedInThreadsQueueAndAreCountedWhenAbandoned() throws Exception {
        // The exhaustion path the unbounded pool had: every request's close blocks forever. The
        // contract now is FIXED threads (2), a BOUNDED queue (64), and counted abandonment beyond
        // that — never more threads, never an unbounded queue, never an inline close.
        java.util.concurrent.CountDownLatch releaseCloses = new java.util.concurrent.CountDownLatch(1);
        @SuppressWarnings({"unchecked", "rawtypes"})
        HttpClient http = mock(HttpClient.class);
        when(http.send(any(HttpRequest.class), any())).thenAnswer(inv -> {
            @SuppressWarnings({"unchecked", "rawtypes"})
            HttpResponse resp = mock(HttpResponse.class);
            when(resp.statusCode()).thenReturn(200);
            when(resp.headers()).thenReturn(contentType("application/json"));
            when(resp.body()).thenReturn(
                    new ByteArrayInputStream(SESSION_JSON.getBytes(StandardCharsets.UTF_8)) {
                        @Override
                        public void close() {
                            try {
                                releaseCloses.await(30, java.util.concurrent.TimeUnit.SECONDS);
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                            }
                        }
                    });
            return resp;
        });
        ContextTapeUpstream up = upstream(http);
        ContextTapeController controller = new ContextTapeController(
                up, authReturning(200), new ObjectMapper(), Integer.MAX_VALUE);

        long abandonedBefore = ContextTapeUpstream.DISPOSALS_ABANDONED.get();
        int wedged = ContextTapeUpstream.CLOSER_THREADS + ContextTapeUpstream.CLOSER_QUEUE_CAPACITY;
        int total = wedged + ContextTapeUpstream.MAX_ABANDONED_BEFORE_RECYCLE;
        try {
            for (int i = 0; i < total; i++) {
                assertEquals(200, controller.session("Bearer t").getStatusCode().value(),
                        "the REQUESTS must keep succeeding while cleanup wedges, up to the bound");
            }
            java.util.concurrent.ThreadPoolExecutor closer =
                    (java.util.concurrent.ThreadPoolExecutor) up.closer();
            assertTrue(closer.getPoolSize() <= ContextTapeUpstream.CLOSER_THREADS,
                    "blocked closes must NEVER grow the pool; size=" + closer.getPoolSize());
            assertTrue(closer.getQueue().size() <= ContextTapeUpstream.CLOSER_QUEUE_CAPACITY,
                    "nor the queue; depth=" + closer.getQueue().size());
            assertEquals(abandonedBefore + ContextTapeUpstream.MAX_ABANDONED_BEFORE_RECYCLE,
                    ContextTapeUpstream.DISPOSALS_ABANDONED.get(),
                    "the overflow must be COUNTED abandonment, not silent anything");

            // The abandonment bound was hit on a client this upstream cannot replace (injected):
            // it FAIL-STOPS. No further exchanges may be created on the leaking client — that is
            // the proof that abandoned live exchanges are bounded, not merely counted.
            ResponseEntity<byte[]> refused = controller.session("Bearer t");
            assertEquals(502, refused.getStatusCode().value(),
                    "past the abandonment bound the upstream must refuse, not leak on");
            verify(http, times(total)).send(any(HttpRequest.class), any());
        } finally {
            releaseCloses.countDown(); // let the wedged closes finish so teardown is clean
        }
    }

    /** A factory whose every client serves 200s with permanently blocking closes, recording creations. */
    private static java.util.function.Supplier<HttpClient> wedgingFactory(
            List<HttpClient> created, List<String> factoryThreads,
            java.util.concurrent.CountDownLatch releaseCloses) {
        return () -> {
            factoryThreads.add(Thread.currentThread().getName());
            HttpClient client = mock(HttpClient.class);
            try {
                // Retirees CONFIRM termination by default, so recycling in these tests keeps its
                // registry empty; the unconfirmable-retiree cap has its own dedicated test.
                when(client.awaitTermination(any())).thenReturn(true);
                when(client.isTerminated()).thenReturn(true);
                when(client.send(any(HttpRequest.class), any())).thenAnswer(inv -> {
                    @SuppressWarnings({"unchecked", "rawtypes"})
                    HttpResponse resp = mock(HttpResponse.class);
                    when(resp.statusCode()).thenReturn(200);
                    when(resp.headers()).thenReturn(contentType("application/json"));
                    when(resp.body()).thenReturn(
                            new ByteArrayInputStream(SESSION_JSON.getBytes(StandardCharsets.UTF_8)) {
                                @Override
                                public void close() {
                                    try {
                                        releaseCloses.await(30, java.util.concurrent.TimeUnit.SECONDS);
                                    } catch (InterruptedException e) {
                                        Thread.currentThread().interrupt();
                                    }
                                }
                            });
                    return resp;
                });
            } catch (Exception impossible) {
                throw new IllegalStateException(impossible);
            }
            created.add(client);
            return client;
        };
    }

    private static final int SESSIONS_TO_TRIP =
            ContextTapeUpstream.CLOSER_THREADS + ContextTapeUpstream.CLOSER_QUEUE_CAPACITY
                    + ContextTapeUpstream.MAX_ABANDONED_BEFORE_RECYCLE;

    @Test
    void closerOverflowRecyclesTheOwnedClientOffTheRequestThreadAndLogsIt() throws Exception {
        // The OWNED-client half of the abandonment bound: when the counted abandonments hit the
        // bound, the LIFECYCLE thread — never the request thread — swaps in a fresh client from the
        // factory and shuts down the leaking one, reclaiming every exchange abandoned on it. And the
        // event is LOGGED with its counters at the moment it happens, because a gateway that
        // recycles and keeps serving 200s would otherwise never emit them anywhere.
        java.util.concurrent.CountDownLatch releaseCloses = new java.util.concurrent.CountDownLatch(1);
        List<HttpClient> created = java.util.Collections.synchronizedList(new ArrayList<>());
        List<String> factoryThreads = java.util.Collections.synchronizedList(new ArrayList<>());
        ContextTapeUpstream up = new ContextTapeUpstream("http://context-tape-service:8134",
                Duration.ofSeconds(5), wedgingFactory(created, factoryThreads, releaseCloses));
        upstreams.add(up);
        ContextTapeController controller = new ContextTapeController(
                up, authReturning(200), new ObjectMapper(), Integer.MAX_VALUE);

        long recyclesBefore = ContextTapeUpstream.CLIENT_RECYCLES.get();
        // CopyOnWriteArrayList: compound traversals (stream/join) below run while
        // the lifecycle thread may still append — snapshot semantics, no CME
        List<String> cleanupLog = new java.util.concurrent.CopyOnWriteArrayList<>();
        up.cleanupLog = cleanupLog::add; // injected logger: assert the LITERAL event, race-free
        try {
            for (int i = 0; i < SESSIONS_TO_TRIP; i++) {
                assertEquals(200, controller.session("Bearer t").getStatusCode().value(),
                        "requests must keep succeeding while cleanup wedges, up to the bound");
            }
            assertTrue(waitFor(() ->
                    ContextTapeUpstream.CLIENT_RECYCLES.get() == recyclesBefore + 1, 5_000L),
                    "hitting the bound must recycle the client");

            assertEquals(2, created.size(),
                    "the recycle must have pulled a FRESH client from the factory");
            assertTrue(factoryThreads.get(1).startsWith("context-tape-lifecycle"),
                    "the recycle (factory included) must run on the lifecycle thread, not the "
                    + "request thread; ran on " + factoryThreads.get(1));
            verify(created.get(0)).shutdownNow();
            assertTrue(waitFor(() -> created.get(1) == up.currentClient(), 5_000L),
                    "and the fresh client must be the one now carrying exchanges");

            // The RECYCLE EVENT itself must be logged — not merely counter labels that an earlier
            // overflow line already carried. Awaited, because the log lands after the swap.
            assertTrue(waitFor(() -> cleanupLog.stream()
                            .anyMatch(line -> line.contains("recycled the leaking client")), 5_000L),
                    "the recycle must be logged as its own event; got:\n"
                    + String.join("\n", cleanupLog));
            assertTrue(cleanupLog.stream().anyMatch(line ->
                            line.contains("closer overflow") && line.contains("abandonedDisposals=")),
                    "and the abandonment onset with its counters; got:\n"
                    + String.join("\n", cleanupLog));

            // Unlike the injected-client case, an OWNED upstream keeps serving after the recycle.
            assertTrue(waitFor(() -> controller.session("Bearer t").getStatusCode().value() == 200,
                    5_000L), "a recycled upstream serves on, on the fresh client");
        } finally {
            releaseCloses.countDown();
        }
    }

    @Test
    void closeRacingARecycleNeverLeaksTheFreshClient() throws Exception {
        // The round-5 lifecycle race, made deterministic: the recycle is held INSIDE the factory
        // while close() runs. Whoever wins, exactly one owner shuts every client down — a fresh
        // client built after close() must be shut down and never published.
        java.util.concurrent.CountDownLatch releaseCloses = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch factoryEntered = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch factoryProceed = new java.util.concurrent.CountDownLatch(1);
        List<HttpClient> created = java.util.Collections.synchronizedList(new ArrayList<>());
        List<String> factoryThreads = java.util.Collections.synchronizedList(new ArrayList<>());
        java.util.function.Supplier<HttpClient> inner =
                wedgingFactory(created, factoryThreads, releaseCloses);
        java.util.function.Supplier<HttpClient> gated = () -> {
            if (!created.isEmpty()) { // only the RECYCLE's factory call is held, not the first build
                factoryEntered.countDown();
                try {
                    factoryProceed.await(30, java.util.concurrent.TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            return inner.get();
        };
        ContextTapeUpstream up = new ContextTapeUpstream("http://context-tape-service:8134",
                Duration.ofSeconds(5), gated);
        upstreams.add(up);
        ContextTapeController controller = new ContextTapeController(
                up, authReturning(200), new ObjectMapper(), Integer.MAX_VALUE);

        long recyclesBefore = ContextTapeUpstream.CLIENT_RECYCLES.get();
        try {
            for (int i = 0; i < SESSIONS_TO_TRIP; i++) {
                assertEquals(200, controller.session("Bearer t").getStatusCode().value());
            }
            assertTrue(factoryEntered.await(5, java.util.concurrent.TimeUnit.SECONDS),
                    "the lifecycle thread must be inside the factory before close() races it");

            up.close(); // close wins while the recycler is still building the fresh client
            factoryProceed.countDown();

            assertTrue(waitFor(() -> created.size() == 2, 5_000L));
            org.mockito.Mockito.verify(created.get(1),
                    org.mockito.Mockito.timeout(5_000L)).shutdownNow();
            assertEquals(created.get(0), up.currentClient(),
                    "a fresh client built after close() must never be published");
            assertEquals(recyclesBefore, ContextTapeUpstream.CLIENT_RECYCLES.get(),
                    "and the aborted swap is not a recycle");
        } finally {
            releaseCloses.countDown();
            factoryProceed.countDown();
        }
    }

    @Test
    void aStragglerFromARetiredGenerationNeverChargesTheFreshClient() throws Exception {
        // Generation tagging: a disposal born under client generation 0, abandoned AFTER the recycle
        // to generation 1, charges nothing — its client is already shut down, its abandonment is
        // already reclaimed. Charging it forward would burn the fresh client's budget with the old
        // client's failures.
        java.util.concurrent.CountDownLatch releaseCloses = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch heldReached = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch heldProceed = new java.util.concurrent.CountDownLatch(1);
        List<HttpClient> created = java.util.Collections.synchronizedList(new ArrayList<>());
        List<String> factoryThreads = java.util.Collections.synchronizedList(new ArrayList<>());
        ContextTapeUpstream up = new ContextTapeUpstream("http://context-tape-service:8134",
                Duration.ofSeconds(30), wedgingFactory(created, factoryThreads, releaseCloses));
        upstreams.add(up);
        java.util.concurrent.atomic.AtomicBoolean firstSession = new java.util.concurrent.atomic.AtomicBoolean(true);
        up.betweenSubmitAndWait = () -> {
            if (firstSession.compareAndSet(true, false)) { // hold ONLY the generation-0 straggler
                heldReached.countDown();
                try {
                    heldProceed.await(30, java.util.concurrent.TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        };
        ContextTapeController controller = new ContextTapeController(
                up, authReturning(200), new ObjectMapper(), Integer.MAX_VALUE);

        long recyclesBefore = ContextTapeUpstream.CLIENT_RECYCLES.get();
        try {
            Thread held = new Thread(() -> controller.session("Bearer t"));
            held.setDaemon(true);
            held.start();
            assertTrue(heldReached.await(5, java.util.concurrent.TimeUnit.SECONDS),
                    "the straggler session must be in flight (generation 0) before the recycle");

            for (int i = 0; i < SESSIONS_TO_TRIP; i++) {
                assertEquals(200, controller.session("Bearer t").getStatusCode().value());
            }
            assertTrue(waitFor(() -> up.generation() == 1, 5_000L), "the recycle must have happened");

            heldProceed.countDown(); // the straggler now disposes a generation-0 stream
            held.join(5_000L);

            assertTrue(waitFor(() -> up.abandonedOnCurrentGeneration() == 0, 5_000L),
                    "generation 1 must not be charged for generation 0's straggler; charged "
                    + up.abandonedOnCurrentGeneration());
            assertEquals(recyclesBefore + 1, ContextTapeUpstream.CLIENT_RECYCLES.get(),
                    "and the straggler must not trigger a second recycle");
        } finally {
            releaseCloses.countDown();
            heldProceed.countDown();
        }
    }

    @Test
    void aBodyOfExactlyTheCapIsServedWhole() throws Exception {
        // The cap is a bound, not an off-by-one: MAX bytes is a legal snapshot, MAX+1 is the fault.
        byte[] exactlyMax = new byte[ContextTapeUpstream.MAX_SESSION_BYTES];
        java.util.Arrays.fill(exactlyMax, (byte) 'x');
        HttpClient http = clientReturning(200, contentType("application/json"), exactlyMax);

        ResponseEntity<byte[]> res = controller(http, 200).session("Bearer t");

        assertEquals(200, res.getStatusCode().value());
        assertEquals(ContextTapeUpstream.MAX_SESSION_BYTES, res.getBody().length,
                "a body of exactly the cap must arrive whole");
    }

    @Test
    void aClockRegressionAtFullCapacityClampsSaturatedPrincipalsInsteadOfErasingThem() {
        // The round-3 scenario: the map is at capacity, the clock steps back far enough that every
        // window start is "in the future". Evicting those entries would erase saturated principals'
        // counts and hand them fresh budgets on reinsertion. They must be CLAMPED — start moved to
        // now, count kept — so saturation survives the clock step.
        ContextTapeController.RateLimiter limiter = new ContextTapeController.RateLimiter(1, 60_000L, 2);
        assertEquals(0L, limiter.tryAcquire("a", 200_000L)); // a is now saturated (limit 1)
        assertEquals(0L, limiter.tryAcquire("b", 200_000L)); // map full

        // The clock regresses by more than two windows; a newcomer arrives and triggers the sweep.
        assertTrue(limiter.tryAcquire("c", 1_000L) > 0,
                "the map is still full of LIVE principals, so the newcomer is refused");
        assertEquals(2, limiter.trackedPrincipals(),
                "the sweep must clamp future-dated entries, not evict them");
        assertTrue(limiter.tryAcquire("a", 1_000L) > 0,
                "and a saturated principal stays saturated across the clock step — never a fresh budget");
        assertEquals(0L, limiter.tryAcquire("a", 1_000L + 60_000L),
                "until its (clamped) window genuinely rolls");
    }

    @Test
    void unconfirmableRetiredClientsAreCappedByAGlobalFailStop() throws Exception {
        // The JDK contract half of the leak bound: shutdownNow() only ASKS, so a retiree may never
        // terminate. Retirement therefore awaits confirmation, and retirees that cannot confirm are
        // capped — at the cap the upstream FAIL-STOPS instead of retiring an unbounded procession of
        // possibly-live clients. Here every retiree refuses to confirm.
        java.util.concurrent.CountDownLatch releaseCloses = new java.util.concurrent.CountDownLatch(1);
        List<HttpClient> created = java.util.Collections.synchronizedList(new ArrayList<>());
        List<String> factoryThreads = java.util.Collections.synchronizedList(new ArrayList<>());
        java.util.function.Supplier<HttpClient> inner =
                wedgingFactory(created, factoryThreads, releaseCloses);
        java.util.function.Supplier<HttpClient> unconfirmable = () -> {
            HttpClient client = inner.get();
            try {
                when(client.awaitTermination(any())).thenReturn(false);
                when(client.isTerminated()).thenReturn(false);
            } catch (InterruptedException impossible) {
                throw new IllegalStateException(impossible);
            }
            return client;
        };
        ContextTapeUpstream up = new ContextTapeUpstream("http://context-tape-service:8134",
                Duration.ofSeconds(5), unconfirmable);
        upstreams.add(up);
        ContextTapeController controller = new ContextTapeController(
                up, authReturning(200), new ObjectMapper(), Integer.MAX_VALUE);

        long recyclesBefore = ContextTapeUpstream.CLIENT_RECYCLES.get();
        try {
            // Generation 0: trip the abandonment bound → recycle 1 → retiree 1 cannot confirm.
            for (int i = 0; i < SESSIONS_TO_TRIP; i++) {
                assertEquals(200, controller.session("Bearer t").getStatusCode().value());
            }
            assertTrue(waitFor(() ->
                    ContextTapeUpstream.CLIENT_RECYCLES.get() == recyclesBefore + 1, 5_000L));
            assertTrue(waitFor(() -> up.unterminatedRetiredClients() == 1, 5_000L),
                    "an unconfirmable retiree must occupy a registry slot");

            // Generation 1: the closer is still wedged, so eight more sessions trip it again →
            // recycle 2 → retiree 2 cannot confirm → the registry is full → GLOBAL fail-stop.
            for (int i = 0; i < ContextTapeUpstream.MAX_ABANDONED_BEFORE_RECYCLE; i++) {
                assertEquals(200, controller.session("Bearer t").getStatusCode().value());
            }
            assertTrue(waitFor(() ->
                    ContextTapeUpstream.CLIENT_RECYCLES.get() == recyclesBefore + 2, 5_000L));
            assertTrue(waitFor(() -> up.unterminatedRetiredClients()
                    >= ContextTapeUpstream.MAX_UNTERMINATED_RETIRED, 5_000L));

            assertTrue(waitFor(() ->
                    controller.session("Bearer t").getStatusCode().value() == 502, 5_000L),
                    "at the unconfirmed-retiree cap the upstream must fail-stop, not recycle on");
            Thread.sleep(100L);
            assertEquals(502, controller.session("Bearer t").getStatusCode().value(),
                    "and the fail-stop is terminal — no third client is ever created");
            assertEquals(recyclesBefore + 2, ContextTapeUpstream.CLIENT_RECYCLES.get());
            assertEquals(3, created.size(), "two retirees plus the live client, and NEVER more");
        } finally {
            releaseCloses.countDown();
        }
    }

    @Test
    void confirmedTerminationsFreeTheirSlotsSoRecyclingCanContinueIndefinitely() throws Exception {
        // The healthy half: a retiree that CONFIRMS termination (as wedgingFactory's clients do)
        // occupies no registry slot, so repeated recycles never approach the fail-stop cap.
        java.util.concurrent.CountDownLatch releaseCloses = new java.util.concurrent.CountDownLatch(1);
        List<HttpClient> created = java.util.Collections.synchronizedList(new ArrayList<>());
        List<String> factoryThreads = java.util.Collections.synchronizedList(new ArrayList<>());
        ContextTapeUpstream up = new ContextTapeUpstream("http://context-tape-service:8134",
                Duration.ofSeconds(5), wedgingFactory(created, factoryThreads, releaseCloses));
        upstreams.add(up);
        ContextTapeController controller = new ContextTapeController(
                up, authReturning(200), new ObjectMapper(), Integer.MAX_VALUE);

        long recyclesBefore = ContextTapeUpstream.CLIENT_RECYCLES.get();
        try {
            for (int i = 0; i < SESSIONS_TO_TRIP; i++) {
                assertEquals(200, controller.session("Bearer t").getStatusCode().value());
            }
            for (int cycle = 1; cycle <= 3; cycle++) {
                int expected = cycle;
                assertTrue(waitFor(() -> ContextTapeUpstream.CLIENT_RECYCLES.get()
                        == recyclesBefore + expected, 5_000L), "recycle #" + cycle);
                assertEquals(0, up.unterminatedRetiredClients(),
                        "a confirmed retiree must occupy no registry slot");
                if (cycle < 3) {
                    for (int i = 0; i < ContextTapeUpstream.MAX_ABANDONED_BEFORE_RECYCLE; i++) {
                        assertEquals(200, controller.session("Bearer t").getStatusCode().value());
                    }
                }
            }
            assertEquals(200, controller.session("Bearer t").getStatusCode().value(),
                    "three confirmed recycles later, the upstream still serves");
        } finally {
            releaseCloses.countDown();
        }
    }

    @Test
    void aRecycleLandingBetweenSendAndDisposalStillChargesTheBornGeneration() throws Exception {
        // THE round-6 interleaving, exactly: request H's send() returns on the generation-0 client,
        // H is descheduled BEFORE constructing its disposal, a recycle installs generation 1, and H
        // resumes. The disposal must carry the generation captured with the client at H's entry —
        // generation 0 — so its abandonment charges a retired generation (i.e. nothing) instead of
        // prematurely saturating the fresh one.
        java.util.concurrent.CountDownLatch releaseCloses = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch heldReached = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch heldProceed = new java.util.concurrent.CountDownLatch(1);
        List<HttpClient> created = java.util.Collections.synchronizedList(new ArrayList<>());
        List<String> factoryThreads = java.util.Collections.synchronizedList(new ArrayList<>());
        ContextTapeUpstream up = new ContextTapeUpstream("http://context-tape-service:8134",
                Duration.ofSeconds(30), wedgingFactory(created, factoryThreads, releaseCloses));
        upstreams.add(up);
        java.util.concurrent.atomic.AtomicBoolean firstSession =
                new java.util.concurrent.atomic.AtomicBoolean(true);
        up.betweenSendAndDisposal = () -> {
            if (firstSession.compareAndSet(true, false)) { // hold ONLY request H, post-send
                heldReached.countDown();
                try {
                    heldProceed.await(30, java.util.concurrent.TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        };
        ContextTapeController controller = new ContextTapeController(
                up, authReturning(200), new ObjectMapper(), Integer.MAX_VALUE);

        long recyclesBefore = ContextTapeUpstream.CLIENT_RECYCLES.get();
        try {
            Thread held = new Thread(() -> controller.session("Bearer t"));
            held.setDaemon(true);
            held.start();
            assertTrue(heldReached.await(5, java.util.concurrent.TimeUnit.SECONDS),
                    "H must be past send() but before its disposal exists");

            for (int i = 0; i < SESSIONS_TO_TRIP; i++) {
                assertEquals(200, controller.session("Bearer t").getStatusCode().value());
            }
            assertTrue(waitFor(() -> up.generation() == 1, 5_000L), "the recycle must have happened");

            heldProceed.countDown(); // H now builds and (on its finally) abandons its disposal
            held.join(5_000L);

            assertTrue(waitFor(() -> up.abandonedOnCurrentGeneration() == 0, 5_000L),
                    "H's stream was BORN under generation 0; generation 1 must not be charged, was "
                    + up.abandonedOnCurrentGeneration());
            assertEquals(recyclesBefore + 1, ContextTapeUpstream.CLIENT_RECYCLES.get(),
                    "and the mis-tag must not have triggered a premature second recycle");
        } finally {
            releaseCloses.countDown();
            heldProceed.countDown();
        }
    }

    @Test
    void aFactoryFailureDuringRecycleFailStopsCleanlyOnTheLifecycleThread() throws Exception {
        // The recycle's own failure mode: the replacement client cannot be built. That must become a
        // FAIL-STOP — every subsequent session a clean 502 — raised on the lifecycle thread, never
        // an exception escaping a request's finally as a 500.
        java.util.concurrent.CountDownLatch releaseCloses = new java.util.concurrent.CountDownLatch(1);
        List<HttpClient> created = java.util.Collections.synchronizedList(new ArrayList<>());
        List<String> factoryThreads = java.util.Collections.synchronizedList(new ArrayList<>());
        java.util.function.Supplier<HttpClient> inner =
                wedgingFactory(created, factoryThreads, releaseCloses);
        java.util.function.Supplier<HttpClient> failsOnRecycle = () -> {
            if (!created.isEmpty()) {
                factoryThreads.add(Thread.currentThread().getName());
                throw new IllegalStateException("no more clients today");
            }
            return inner.get();
        };
        ContextTapeUpstream up = new ContextTapeUpstream("http://context-tape-service:8134",
                Duration.ofSeconds(5), failsOnRecycle);
        upstreams.add(up);
        ContextTapeController controller = new ContextTapeController(
                up, authReturning(200), new ObjectMapper(), Integer.MAX_VALUE);

        long recyclesBefore = ContextTapeUpstream.CLIENT_RECYCLES.get();
        try {
            for (int i = 0; i < SESSIONS_TO_TRIP; i++) {
                assertEquals(200, controller.session("Bearer t").getStatusCode().value(),
                        "requests succeed up to the bound; the factory only runs on the recycle");
            }
            assertTrue(waitFor(() -> factoryThreads.size() == 2, 5_000L),
                    "the recycle must have attempted the factory");
            assertTrue(factoryThreads.get(1).startsWith("context-tape-lifecycle"),
                    "the failing factory ran on " + factoryThreads.get(1)
                    + ", not the lifecycle thread");

            ResponseEntity<byte[]> refused = controller.session("Bearer t");
            assertEquals(502, refused.getStatusCode().value(),
                    "a fail-stopped upstream answers the ordinary 502 envelope, not a 500");
            assertEquals("UPSTREAM_UNAVAILABLE",
                    new ObjectMapper().readTree(refused.getBody()).get("error").asText());
            assertEquals(recyclesBefore, ContextTapeUpstream.CLIENT_RECYCLES.get(),
                    "a failed swap is not a recycle");
            assertEquals(created.get(0), up.currentClient(),
                    "and no phantom client was ever published");
        } finally {
            releaseCloses.countDown();
        }
    }

    /**
     * Spin until this upstream's single reader task has COMPLETED — bounded, so a regression fails
     * the test rather than hanging the suite. The bound is generous (the awaited task is trivial);
     * it is a failure detector, not a timing assertion.
     */
    private static void awaitReaderDone(ContextTapeUpstream up) {
        java.util.concurrent.ThreadPoolExecutor readers =
                (java.util.concurrent.ThreadPoolExecutor) up.readers();
        long deadlineNanos = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(10);
        while (readers.getCompletedTaskCount() < 1) {
            if (System.nanoTime() - deadlineNanos >= 0) {
                throw new AssertionError("reader task did not complete within 10s");
            }
            try {
                Thread.sleep(1L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    @Test
    void aLateCompletedFutureHandedToADelayedWaiterIsStillRejected() throws Exception {
        // THE completion-race, proven deterministically: the body is RELEASED only after the
        // deadline has certainly passed (a lower-bound sleep, safe in any scheduler), and the waiter
        // then does not proceed until the reader task has provably COMPLETED — so get() must return
        // the late body rather than time out, and only the recorded completion time can reject it.
        java.util.concurrent.CountDownLatch allowBody = new java.util.concurrent.CountDownLatch(1);
        InputStream lateBody = new InputStream() {
            private boolean served;

            @Override
            public int read() {
                return -1;
            }

            @Override
            public int read(byte[] b, int off, int len) {
                if (!served) {
                    served = true;
                    try {
                        allowBody.await(30, java.util.concurrent.TimeUnit.SECONDS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    b[off] = '{';
                    return 1;
                }
                return -1;
            }
        };
        @SuppressWarnings({"unchecked", "rawtypes"})
        HttpClient http = mock(HttpClient.class);
        @SuppressWarnings({"unchecked", "rawtypes"})
        HttpResponse resp = mock(HttpResponse.class);
        when(resp.statusCode()).thenReturn(200);
        when(resp.headers()).thenReturn(contentType("application/json"));
        when(resp.body()).thenReturn(lateBody);
        when(http.send(any(HttpRequest.class), any())).thenReturn(resp);
        ContextTapeUpstream up = new ContextTapeUpstream("http://context-tape-service:8134",
                Duration.ofMillis(100), http);
        upstreams.add(up);
        up.betweenSubmitAndWait = () -> {
            try {
                Thread.sleep(150L); // a LOWER bound: the 100ms deadline has certainly passed…
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            allowBody.countDown(); // …now the body completes — late by construction
            awaitReaderDone(up);   // and the waiter proceeds only once the future is provably DONE
        };

        ResponseEntity<byte[]> res = new ContextTapeController(
                up, authReturning(200), new ObjectMapper(), Integer.MAX_VALUE).session("Bearer t");

        assertEquals(502, res.getStatusCode().value(),
                "a body whose READ finished after the deadline must be rejected even when get() "
                + "returns it to a descheduled waiter");
        assertEquals("UPSTREAM_PROTOCOL_ERROR",
                new ObjectMapper().readTree(res.getBody()).get("error").asText());
    }

    @Test
    void aBodyDeliveredInTimeIsAcceptedEvenWhenTheWaiterItselfResumesLate() throws Exception {
        // The other half of the completion-time rule: the deadline is about when the READ finished,
        // not when the waiter woke. A GC pause on the request thread must not turn an on-time body
        // into a 502. Deterministic: the waiter first waits for the reader to provably COMPLETE
        // (instant body, comfortably inside the 1s budget), and only then oversleeps the deadline.
        HttpClient http = clientReturning(200, "application/json", SESSION_JSON);
        ContextTapeUpstream up = new ContextTapeUpstream("http://context-tape-service:8134",
                Duration.ofSeconds(1), http);
        upstreams.add(up);
        up.betweenSubmitAndWait = () -> {
            awaitReaderDone(up); // the body is READ (and timestamped) well inside the budget…
            try {
                Thread.sleep(1_300L); // …then the waiter resumes only after the deadline passed
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        };

        ResponseEntity<byte[]> res = new ContextTapeController(
                up, authReturning(200), new ObjectMapper(), Integer.MAX_VALUE).session("Bearer t");

        assertEquals(200, res.getStatusCode().value(),
                "the body was READ inside the budget, so it is the truth and it is served");
        assertEquals(SESSION_JSON, bodyText(res));
    }

    @Test
    void anAbsurdlyLargeConfiguredBudgetIsForeverNotA500() throws Exception {
        // CONTEXT_TAPE_REQUEST_TIMEOUT_MS has no upper bound, and Duration.toNanos() throws on
        // overflow. A huge but valid setting must mean "effectively no deadline", not detonate
        // every request as an ArithmeticException 500.
        HttpClient http = clientReturning(200, "application/json", SESSION_JSON);
        ContextTapeUpstream up = new ContextTapeUpstream("http://context-tape-service:8134",
                Duration.ofMillis(Long.MAX_VALUE), http);
        upstreams.add(up);

        ResponseEntity<byte[]> res = new ContextTapeController(
                up, authReturning(200), new ObjectMapper(), Integer.MAX_VALUE).session("Bearer t");

        assertEquals(200, res.getStatusCode().value());
        assertEquals(SESSION_JSON, bodyText(res));
    }

    // ---------------------------------------------------------------- lifecycle & counters

    @Test
    void closeShutsDownTheOwnedPoolsAndIsIdempotent() throws Exception {
        // Injected (non-owned) client on purpose here: this test is about the pools. The OWNED
        // HttpClient's shutdown is exercised in aSessionCallAfterCloseIsACleanGateway502NotA500,
        // which builds through the public constructor.
        ContextTapeUpstream up = new ContextTapeUpstream("http://context-tape-service:8134",
                Duration.ofSeconds(5), mock(HttpClient.class));

        up.close();
        assertTrue(up.readers().isShutdown(), "the reader pool must not outlive the upstream");
        assertTrue(up.closer().isShutdown(), "nor the closer pool");
        up.close(); // second close must be a no-op, not an error
    }

    @Test
    void aSessionCallAfterCloseIsACleanGateway502AndTheOwnedClientIsObservablyShutDown() {
        // The graceful-shutdown race: Spring destroys the bean while a request is arriving. The call
        // must get the ordinary 502 envelope — never an unmapped RejectedExecutionException as a 500,
        // and never an inline blocking close on anyone's thread. Built through the OWNING factory
        // seam so the owned client's shutdownNow() is an ASSERTION, not a hope: delete the
        // http.shutdownNow() call in close() and the verify below fails.
        HttpClient ownedClient = mock(HttpClient.class);
        ContextTapeUpstream owned = new ContextTapeUpstream("http://context-tape-service:8134",
                Duration.ofMillis(300), () -> ownedClient);
        owned.close();

        org.mockito.Mockito.verify(ownedClient).shutdownNow();

        ContextTapeUpstream.UnavailableException refused = assertThrows(
                ContextTapeUpstream.UnavailableException.class, owned::session);
        assertEquals("UPSTREAM_UNAVAILABLE", refused.code());

        ResponseEntity<byte[]> res = new ContextTapeController(
                owned, authReturning(200), new ObjectMapper(), Integer.MAX_VALUE).session("Bearer t");
        assertEquals(502, res.getStatusCode().value(),
                "a shut-down upstream is an ordinary unreachable-service answer, not a server error");
        owned.close(); // idempotent for the owned client too
    }

    @Test
    void countersCountWhatWasActuallyForwarded() throws Exception {
        // SESSIONS_SERVED means a snapshot in a browser — a forwarded 200 and nothing else. And
        // "warming" is exclusively the contracted {"error":"WARMING"} body: a 503 carrying anything
        // else is a failure the UI renders as one, so the operator counter must agree with the UI.
        long served = ContextTapeController.SESSIONS_SERVED.get();
        long warming = ContextTapeController.WARMING_FORWARDED.get();
        long errors = ContextTapeController.UPSTREAM_ERRORS_FORWARDED.get();

        controller(clientReturning(200, "application/json", SESSION_JSON), 200).session("Bearer t");
        controller(clientReturning(503, "application/json", WARMING_JSON), 200).session("Bearer t");
        controller(clientReturning(503, "application/json", "{\"error\":\"DB_DOWN\"}"), 200)
                .session("Bearer t");
        controller(clientReturning(500, "application/json", "{\"error\":\"SNAPSHOT_TOO_LARGE\"}"), 200)
                .session("Bearer t");

        assertEquals(served + 1, ContextTapeController.SESSIONS_SERVED.get(),
                "only the 200 is a session served");
        assertEquals(warming + 1, ContextTapeController.WARMING_FORWARDED.get(),
                "only the contracted WARMING body counts as warming");
        assertEquals(errors + 2, ContextTapeController.UPSTREAM_ERRORS_FORWARDED.get(),
                "a non-WARMING 503 and a 500 are both forwarded errors");

        // The cleanup-health counters must be ON the operator line, not merely declared somewhere
        // package-visible: this string is the established exposure path for this gateway's proxies.
        String line = controller(clientReturning(200, "application/json", SESSION_JSON), 200).counters();
        assertTrue(line.contains("abandonedDisposals="), line);
        assertTrue(line.contains("clientRecycles="), line);
    }

    @Test
    void aBlankBaseUrlDoesNotStopTheGatewayBootingAndAnswersA502PerRequest() {
        // A gateway that refuses to start because ONE feature is misconfigured takes market data down
        // with it. The session endpoint answering 502 is a state the page renders.
        ContextTapeUpstream blank =
                new ContextTapeUpstream("   ", Duration.ofSeconds(5), mock(HttpClient.class));
        upstreams.add(blank);
        ContextTapeController controller = new ContextTapeController(
                blank, authReturning(200), new ObjectMapper(), Integer.MAX_VALUE);

        assertEquals(502, controller.session("Bearer t").getStatusCode().value());
    }
}

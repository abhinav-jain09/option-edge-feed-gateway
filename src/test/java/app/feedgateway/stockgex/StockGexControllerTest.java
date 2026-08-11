package app.feedgateway.stockgex;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import app.feedgateway.liquidityhistory.LiquidityHistoryAuth;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

/**
 * Behavioural tests for the stock-gex proxy.
 *
 * <p>What actually needs defending here is the PASSTHROUGH. The upstream's status vocabulary is the only
 * way the page can tell "this ticker is outside the OI index universe, stop asking" (422) from "we are at
 * capacity, retry" (429/503) from "you typed nonsense" (400); a proxy that helpfully normalises any of
 * those into a 500 silently destroys the feature. The stream half has the same property one level down:
 * the upstream's {@code id:} lines drive a strict resume contract, so the bytes must arrive unedited and
 * the client's {@code Last-Event-ID} must actually reach the upstream.
 *
 * <p>The other half is RESOURCE OWNERSHIP. A live stream owns an MVC async thread, a semaphore permit and
 * an upstream exchange for hours, and three unrelated things can end it — the pump, the servlet async
 * lifecycle, and the idle watchdog. These tests drive each of those independently, including the case
 * where the streaming body is returned and then NEVER INVOKED, which is what happens when the executor
 * rejects the task or the container abandons the dispatch.
 *
 * <p>Tests drive the real {@link StockGexUpstream} over a stubbed {@link HttpClient} rather than mocking
 * the upstream, so URL construction, header forwarding and timeout shape are covered too.
 */
class StockGexControllerTest {

    private static final String BOARD_JSON =
            "{\"symbol\":\"TSLA\",\"asOf\":\"2026-08-11T14:31:02Z\",\"spot\":312.45,"
            + "\"strikes\":[{\"strike\":310.0,\"gex\":-1.24e8},{\"strike\":315.0,\"gex\":8.1e7}]}";

    // ---------------------------------------------------------------- helpers

    /** Captures the cleanup the controller binds to the servlet async lifecycle, so a test can fire it. */
    private static final class CapturingRegistrar implements StockGexController.AsyncCleanupRegistrar {
        private final List<Runnable> cleanups = new ArrayList<>();

        @Override
        public void onAsyncCompletion(jakarta.servlet.http.HttpServletRequest request, Runnable cleanup) {
            cleanups.add(cleanup);
        }

        void fireAll() {
            cleanups.forEach(Runnable::run);
        }
    }

    private static LiquidityHistoryAuth authReturning(int status) {
        LiquidityHistoryAuth auth = mock(LiquidityHistoryAuth.class);
        when(auth.authenticate(any())).thenReturn(new LiquidityHistoryAuth.Result(status, "tester"));
        return auth;
    }

    private static HttpHeaders headers(Map<String, List<String>> map) {
        return HttpHeaders.of(map, (k, v) -> true);
    }

    private static HttpHeaders contentType(String value) {
        return headers(value == null ? Map.of() : Map.of("content-type", List.of(value)));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static HttpClient clientReturning(int status, HttpHeaders headers, byte[] body) throws Exception {
        HttpClient http = mock(HttpClient.class);
        HttpResponse resp = mock(HttpResponse.class);
        when(resp.statusCode()).thenReturn(status);
        when(resp.headers()).thenReturn(headers);
        when(resp.body()).thenReturn(new ByteArrayInputStream(body));
        when(http.send(any(HttpRequest.class), any())).thenReturn(resp);
        return http;
    }

    private static HttpClient clientReturning(int status, String contentType, String body) throws Exception {
        return clientReturning(status, contentType(contentType), body.getBytes(StandardCharsets.UTF_8));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static HttpClient clientStreaming(int status, String contentType, InputStream body) throws Exception {
        HttpClient http = mock(HttpClient.class);
        HttpResponse resp = mock(HttpResponse.class);
        when(resp.statusCode()).thenReturn(status);
        when(resp.headers()).thenReturn(contentType(contentType));
        when(resp.body()).thenReturn(body);
        when(http.send(any(HttpRequest.class), any())).thenReturn(resp);
        return http;
    }

    /**
     * Every {@link StockGexUpstream} built here owns a watchdog scheduler, and every live response
     * holds a lease. Left behind they accumulate across the suite — daemon threads do not stop a JVM
     * exiting, but they do interfere with the timing the watchdog tests measure.
     */
    private final List<StockGexUpstream> upstreams = new ArrayList<>();
    private final List<ResponseEntity<StreamingResponseBody>> liveResponses = new ArrayList<>();

    @AfterEach
    void closeEverythingThisTestOpened() {
        for (ResponseEntity<StreamingResponseBody> live : liveResponses) {
            try {
                live.getBody().writeTo(OutputStream.nullOutputStream());
            } catch (Exception ignored) {
                // the point is only to run the pump's finally
            }
        }
        liveResponses.clear();
        upstreams.forEach(StockGexUpstream::close);
        upstreams.clear();
    }

    private StockGexUpstream upstream(HttpClient http) {
        StockGexUpstream created =
                new StockGexUpstream("http://stock-gex-service:8021/", Duration.ofSeconds(5), http);
        upstreams.add(created);
        return created;
    }

    private StockGexController controller(HttpClient http, int authStatus) {
        return controller(http, authStatus, StockGexController.MAX_CONCURRENT_STREAMS, new CapturingRegistrar());
    }

    private StockGexController controller(HttpClient http, int authStatus, int maxStreams,
                                          StockGexController.AsyncCleanupRegistrar registrar) {
        return new StockGexController(upstream(http), authReturning(authStatus), new ObjectMapper(),
                maxStreams, registrar);
    }

    private static String bodyText(ResponseEntity<byte[]> res) {
        return new String(res.getBody(), StandardCharsets.UTF_8);
    }

    private static byte[] drain(ResponseEntity<StreamingResponseBody> res) throws IOException {
        ByteArrayOutputStream sink = new ByteArrayOutputStream();
        res.getBody().writeTo(sink);
        return sink.toByteArray();
    }

    private static HttpRequest capturedRequest(HttpClient http) throws Exception {
        ArgumentCaptor<HttpRequest> req = ArgumentCaptor.forClass(HttpRequest.class);
        verify(http).send(req.capture(), any());
        return req.getValue();
    }

    /** Run the stream handler and return whatever it answered — a stream, or a refusal. */
    /**
     * Drives the handler the way MVC does, including the raw header the resume id is now read from —
     * binding a String would have let duplicate fields be joined into a token nobody issued.
     */
    private Object streamOutcome(StockGexController controller, String symbol, String lastEventId,
                                 String authorization) {
        return streamOutcomeWithIds(controller, symbol,
                lastEventId == null ? List.of() : List.of(lastEventId), authorization);
    }

    private Object streamOutcomeWithIds(StockGexController controller, String symbol,
                                        List<String> lastEventIds, String authorization) {
        org.springframework.mock.web.MockHttpServletRequest request =
                new org.springframework.mock.web.MockHttpServletRequest();
        for (String id : lastEventIds) {
            request.addHeader("Last-Event-ID", id);
        }
        try {
            return controller.stream(request, symbol, authorization);
        } catch (StockGexController.StockGexRefusal refusal) {
            return controller.refused(refusal);
        }
    }

    @SuppressWarnings("unchecked")
    private static ResponseEntity<byte[]> refusalOf(Object outcome) {
        assertTrue(outcome instanceof ResponseEntity, "expected a response");
        ResponseEntity<?> res = (ResponseEntity<?>) outcome;
        assertTrue(res.getBody() == null || res.getBody() instanceof byte[],
                "a refusal must be a SYNCHRONOUS byte[] body — a StreamingResponseBody would need the "
                + "very async executor whose exhaustion it is reporting");
        return (ResponseEntity<byte[]>) res;
    }

    @SuppressWarnings("unchecked")
    private ResponseEntity<StreamingResponseBody> liveStream(Object outcome) {
        ResponseEntity<?> res = (ResponseEntity<?>) outcome;
        assertTrue(res.getBody() instanceof StreamingResponseBody, "expected a live stream");
        ResponseEntity<StreamingResponseBody> live = (ResponseEntity<StreamingResponseBody>) res;
        liveResponses.add(live);
        return live;
    }

    // ---------------------------------------------------------------- board

    @Test
    void boardServesTheUpstreamBodyVerbatim() throws Exception {
        HttpClient http = clientReturning(200, "application/json", BOARD_JSON);
        ResponseEntity<byte[]> res = controller(http, 200).board("TSLA", "Bearer t");

        assertEquals(200, res.getStatusCode().value());
        assertEquals(BOARD_JSON, bodyText(res), "the board must not be reshaped in transit");
        assertEquals(MediaType.APPLICATION_JSON, res.getHeaders().getContentType());
        assertEquals("http://stock-gex-service:8021/api/stock-gex/board?symbol=TSLA",
                capturedRequest(http).uri().toString());
    }

    @Test
    void aLiveBoardIsNeverCacheable() throws Exception {
        // A board is a measurement of a moving market. Any intermediary replaying one would be showing
        // a number that was true at a moment it does not name.
        ResponseEntity<byte[]> res =
                controller(clientReturning(200, "application/json", BOARD_JSON), 200).board("TSLA", "Bearer t");
        assertTrue(res.getHeaders().getCacheControl().contains("no-store"), res.getHeaders().toString());
    }

    @Test
    void arbitraryBytesSurviveByteForByte() throws Exception {
        // "Verbatim" has to mean verbatim for BYTES. Carrying the body as a String makes the response
        // charset a guess by a message converter, and neither of these payloads survives a guess:
        // one is not valid UTF-8 at all, and the other contains a NUL.
        byte[] hostile = {0x7B, (byte) 0xFF, (byte) 0xFE, 0x00, 0x41, (byte) 0xC3, 0x28, 0x7D};
        HttpClient http = clientReturning(200, contentType("application/octet-stream"), hostile);

        ResponseEntity<byte[]> res = controller(http, 200).board("TSLA", "Bearer t");

        assertArrayEquals(hostile, res.getBody(),
                "no charset may be applied to a body this proxy promised to carry unchanged");
    }

    @Test
    void aNonAsciiBodySurvivesByteForByte() throws Exception {
        String body = "{\"error\":\"BAD_SYMBOL\",\"detail\":\"unknown root — try TSLA · 「日本」\"}";
        HttpClient http = clientReturning(400, "text/plain; charset=utf-8", body);

        ResponseEntity<byte[]> res = controller(http, 200).board("ZZZZ", "Bearer t");

        assertEquals(400, res.getStatusCode().value());
        assertArrayEquals(body.getBytes(StandardCharsets.UTF_8), res.getBody());
    }

    @Test
    void everyUpstreamErrorStatusAndBodySurvivesVerbatim() throws Exception {
        // The whole point of the endpoint. 422 means "this symbol is outside the nightly OI index
        // universe" — permanent for today — while 429/503 mean "retry"; the page renders a different
        // thing for each, so collapsing any of them into a 500 would silently break it.
        String[][] cases = {
            {"422", "{\"error\":\"OI_SNAPSHOT_UNAVAILABLE\",\"symbol\":\"ZZZZ\"}"},
            {"400", "{\"error\":\"BAD_SYMBOL\",\"symbol\":\"!!\"}"},
            {"429", "{\"error\":\"MAX_SYMBOLS\",\"limit\":25}"},
            {"409", "{\"error\":\"BOARD_GONE\"}"},
            {"503", "{\"error\":\"WIRE_CAPACITY\"}"},
            {"503", "{\"error\":\"BOARD_SUBSCRIBE_FAILED\"}"},
            {"503", "{\"error\":\"SHUTTING_DOWN\"}"},
            {"503", "{\"error\":\"SSE_CLIENT_LIMIT\"}"},
            {"302", "{\"error\":\"MOVED\"}"},
            {"204", ""},
        };
        for (String[] c : cases) {
            int status = Integer.parseInt(c[0]);
            ResponseEntity<byte[]> res =
                    controller(clientReturning(status, "application/json", c[1]), 200).board("ZZZZ", "Bearer t");
            assertEquals(status, res.getStatusCode().value(), "status must pass through: " + c[1]);
            assertEquals(c[1], bodyText(res), "body must pass through byte-for-byte");
        }
    }

    @Test
    void theUpstreamsOwnRetryAfterIsCarriedThroughRatherThanGuessedAt() throws Exception {
        HttpClient http = clientReturning(429,
                headers(Map.of("content-type", List.of("application/json"), "retry-after", List.of("30"))),
                "{\"error\":\"MAX_SYMBOLS\"}".getBytes(StandardCharsets.UTF_8));

        ResponseEntity<byte[]> res = controller(http, 200).board("TSLA", "Bearer t");

        assertEquals("30", res.getHeaders().getFirst("Retry-After"),
                "the service already said how long to wait; the page should not have to invent it");
    }

    @Test
    void aMissingOrMalformedUpstreamContentTypeFallsBackRatherThanFailing() throws Exception {
        for (String bad : new String[]{null, "", "not/a/media/type", "///"}) {
            HttpClient http = clientReturning(200, contentType(bad), BOARD_JSON.getBytes(StandardCharsets.UTF_8));
            ResponseEntity<byte[]> res = controller(http, 200).board("TSLA", "Bearer t");
            assertEquals(200, res.getStatusCode().value(), "content-type: " + bad);
            assertEquals(MediaType.APPLICATION_JSON, res.getHeaders().getContentType(), "content-type: " + bad);
            assertEquals(BOARD_JSON, bodyText(res));
        }
    }

    @Test
    void aBoardBiggerThanTheCapIsAnHonest502NotATruncatedBoard() throws Exception {
        // A prefix of a board delivered under the upstream's own 200 is the worst possible answer: the
        // page would parse it and render whatever survived the cut as if it were the whole market.
        byte[] huge = new byte[StockGexUpstream.MAX_BOARD_BYTES + 1];
        java.util.Arrays.fill(huge, (byte) 'x');
        HttpClient http = clientReturning(200, contentType("application/json"), huge);

        ResponseEntity<byte[]> res = controller(http, 200).board("TSLA", "Bearer t");

        assertEquals(502, res.getStatusCode().value());
        assertEquals("UPSTREAM_PROTOCOL_ERROR",
                new ObjectMapper().readTree(res.getBody()).get("error").asText());
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
        when(resp.statusCode()).thenReturn(422);
        when(resp.headers()).thenReturn(contentType("application/json"));
        when(resp.body()).thenReturn(broken);
        when(http.send(any(HttpRequest.class), any())).thenReturn(resp);

        ResponseEntity<byte[]> res = controller(http, 200).board("ZZZZ", "Bearer t");

        // NOT 422-with-an-empty-body: that would be this gateway inventing an answer and signing it with
        // the upstream's status. The page would read "outside coverage" from a body we never received.
        assertEquals(502, res.getStatusCode().value());
        assertEquals("UPSTREAM_PROTOCOL_ERROR",
                new ObjectMapper().readTree(res.getBody()).get("error").asText());
    }

    @Test
    void anUnreachableUpstreamIsACleanJsonErrorNotAStackTrace() throws Exception {
        HttpClient http = mock(HttpClient.class);
        when(http.send(any(HttpRequest.class), any()))
                .thenThrow(new IOException("Connection refused to stock-gex-service/10.42.0.7:8021"));

        ResponseEntity<byte[]> res = controller(http, 200).board("TSLA", "Bearer t");

        // 502, not 503: the upstream authors its OWN 503s (WIRE_CAPACITY, SHUTTING_DOWN) and the page
        // must be able to tell "gateway could not reach it" apart from those.
        assertEquals(502, res.getStatusCode().value());
        JsonNode body = new ObjectMapper().readTree(res.getBody());
        // Shape matches the stock-gex service's own envelope: `error` is the CODE, `detail` the sentence.
        assertEquals("UPSTREAM_UNAVAILABLE", body.get("error").asText());
        assertEquals("UPSTREAM_UNAVAILABLE", body.get("code").asText());
        assertNotNull(body.get("detail"));
        assertFalse(bodyText(res).contains("10.42.0.7"), "internal host/port must not leak to the browser");
        assertFalse(bodyText(res).contains("Exception"), "no stack trace or exception class in the body");
    }

    @Test
    void anExceptionWhileBUILDINGTheRequestIsA502NotA500() throws Exception {
        // Request construction — newBuilder, header validation, build — throws IllegalArgumentException,
        // which is NOT an IOException. Outside the mapped block it reaches the browser as a 500 with a
        // stack trace, and the page has no entry for that.
        HttpClient http = mock(HttpClient.class);
        StockGexUpstream misconfigured =
                new StockGexUpstream("stock-gex-service:8021", Duration.ofSeconds(5), http);
        StockGexController controller = new StockGexController(
                misconfigured, authReturning(200), new ObjectMapper(), 4, new CapturingRegistrar());

        ResponseEntity<byte[]> board = controller.board("TSLA", "Bearer t");
        assertEquals(502, board.getStatusCode().value());
        assertEquals("UPSTREAM_UNAVAILABLE",
                new ObjectMapper().readTree(board.getBody()).get("error").asText());

        ResponseEntity<byte[]> stream = refusalOf(streamOutcome(controller, "TSLA", null, "Bearer t"));
        assertEquals(502, stream.getStatusCode().value());
        verifyNoInteractions(http);
    }

    @Test
    void anIllegalArgumentFromSENDIsAlsoA502NotA500() throws Exception {
        // The JDK client itself throws IllegalArgumentException for some addresses it will not accept.
        HttpClient http = mock(HttpClient.class);
        when(http.send(any(HttpRequest.class), any()))
                .thenThrow(new IllegalArgumentException("URI with undefined scheme"));

        ResponseEntity<byte[]> res = controller(http, 200).board("TSLA", "Bearer t");

        assertEquals(502, res.getStatusCode().value());
        assertFalse(bodyText(res).contains("IllegalArgument"), "no exception class in the body");
    }

    @Test
    void aBlankBaseUrlDoesNotStopTheGatewayBootingAndAnswersA502PerRequest() {
        // A gateway that refuses to start because ONE feature is misconfigured takes market data down
        // with it. The board endpoint answering 502 is a state the page renders.
        StockGexUpstream blank = new StockGexUpstream("   ", Duration.ofSeconds(5), mock(HttpClient.class));
        StockGexController controller =
                new StockGexController(blank, authReturning(200), new ObjectMapper(), 4, new CapturingRegistrar());

        assertEquals(502, controller.board("TSLA", "Bearer t").getStatusCode().value());
    }

    @Test
    void authIsEnforcedBeforeTheUpstreamIsEvenCalled() throws Exception {
        for (int status : new int[]{401, 403}) {
            HttpClient http = mock(HttpClient.class);
            assertEquals(status, controller(http, status).board("TSLA", null).getStatusCode().value());
            assertEquals(status,
                    refusalOf(streamOutcome(controller(http, status), "TSLA", null, null))
                            .getStatusCode().value());
            verifyNoInteractions(http);
        }
    }

    @Test
    void theSymbolIsUrlEncodedAndOTHERWISEUNTOUCHED() throws Exception {
        HttpClient http = clientReturning(400, "application/json", "{\"error\":\"BAD_SYMBOL\"}");
        controller(http, 200).board(" tsla ", "Bearer t");
        // Not trimmed, not upper-cased. The upstream owns BAD_SYMBOL, and a gateway that quietly
        // rewrites the symbol makes the service judge something the user never typed.
        assertEquals("http://stock-gex-service:8021/api/stock-gex/board?symbol=+tsla+",
                capturedRequest(http).uri().toString());
    }

    @Test
    void theSymbolIsUrlEncodedRatherThanTrustedIntoTheQueryString() throws Exception {
        HttpClient http = clientReturning(400, "application/json", "{\"error\":\"BAD_SYMBOL\"}");
        controller(http, 200).board("A B&x=1", "Bearer t");
        assertEquals("http://stock-gex-service:8021/api/stock-gex/board?symbol=A+B%26x%3D1",
                capturedRequest(http).uri().toString());
    }

    @Test
    void anAbsurdlyLongSymbolIsRefusedOnTheGatewaySideWithTheUpstreamsOwnCode() throws Exception {
        HttpClient http = mock(HttpClient.class);
        ResponseEntity<byte[]> res = controller(http, 200).board("T".repeat(64), "Bearer t");
        assertEquals(400, res.getStatusCode().value());
        // Reuse BAD_SYMBOL rather than invent a gateway-only code: the page already branches on it.
        assertTrue(bodyText(res).contains("BAD_SYMBOL"), bodyText(res));
        verifyNoInteractions(http);
    }

    @Test
    void bothCallsRefuseTransferEncodingSoNothingArrivesCompressedAndUnlabelled() throws Exception {
        HttpClient board = clientReturning(200, "application/json", BOARD_JSON);
        controller(board, 200).board("TSLA", "Bearer t");
        assertEquals("identity", capturedRequest(board).headers().firstValue("Accept-Encoding").orElse(""));

        HttpClient stream = clientStreaming(200, "text/event-stream", new ByteArrayInputStream(new byte[0]));
        streamOutcome(controller(stream, 200), "TSLA", null, "Bearer t");
        assertEquals("identity", capturedRequest(stream).headers().firstValue("Accept-Encoding").orElse(""));
    }

    // ---------------------------------------------------------------- stream

    @Test
    void streamAnnouncesEventStreamAndDisablesProxyBuffering() throws Exception {
        HttpClient http = clientStreaming(200, "text/event-stream",
                new ByteArrayInputStream("id: 7\ndata: {}\n\n".getBytes(StandardCharsets.UTF_8)));

        ResponseEntity<StreamingResponseBody> res =
                liveStream(streamOutcome(controller(http, 200), "TSLA", null, "Bearer t"));

        assertEquals(200, res.getStatusCode().value());
        assertEquals(MediaType.TEXT_EVENT_STREAM, res.getHeaders().getContentType());
        assertTrue(res.getHeaders().getFirst("Cache-Control").contains("no-cache"));
        // Without this an intermediary buffers a low-rate stream and the page sees a dead feed.
        assertEquals("no", res.getHeaders().getFirst("X-Accel-Buffering"));
    }

    @Test
    void upstreamIdLinesReachTheClientUnchanged() throws Exception {
        // The upstream's replay contract is keyed on ITS ids: a dropped or rewritten id makes the next
        // reconnect look like a gap (or a future id) and costs a full reset snapshot.
        String sse = "id: 41\nevent: board\ndata: {\"strike\":310}\n\n"
                + ": heartbeat\n\n"
                + "id: 42\nevent: board\ndata: {\"strike\":315}\n\n";
        HttpClient http = clientStreaming(200, "text/event-stream",
                new ByteArrayInputStream(sse.getBytes(StandardCharsets.UTF_8)));

        ResponseEntity<StreamingResponseBody> res =
                liveStream(streamOutcome(controller(http, 200), "TSLA", null, "Bearer t"));

        assertEquals(sse, new String(drain(res), StandardCharsets.UTF_8));
    }

    @Test
    void lastEventIdIsForwardedUpstreamSoAReconnectResumes() throws Exception {
        HttpClient http = clientStreaming(200, "text/event-stream",
                new ByteArrayInputStream(new byte[0]));

        streamOutcome(controller(http, 200), "TSLA", "42", "Bearer t");

        HttpRequest req = capturedRequest(http);
        assertEquals("42", req.headers().firstValue("Last-Event-ID").orElse(""));
        assertEquals("text/event-stream", req.headers().firstValue("Accept").orElse(""));
    }

    @Test
    void anEventIdIsForwardedEXACTLYOrNotAtAll() throws Exception {
        // The id is an opaque token the upstream minted and keys its replay buffer on. Trimming " 42 "
        // into "42" would resume from an id the upstream may never have issued; dropping it costs one
        // full reset snapshot, which is always a correct answer.
        for (String hostile : new String[]{"42\r\nX-Injected: 1", "  ", " ", "9".repeat(300), " 42", "42 ", ""}) {
            HttpClient http = clientStreaming(200, "text/event-stream", new ByteArrayInputStream(new byte[0]));
            Object outcome = streamOutcome(controller(http, 200), "TSLA", hostile, "Bearer t");
            assertEquals(200, ((ResponseEntity<?>) outcome).getStatusCode().value(),
                    "must not fail the request: [" + hostile + "]");
            assertTrue(capturedRequest(http).headers().firstValue("Last-Event-ID").isEmpty(),
                    "an id that is not exactly forwardable must be dropped, not rewritten: [" + hostile + "]");
        }

        HttpClient good = clientStreaming(200, "text/event-stream", new ByteArrayInputStream(new byte[0]));
        streamOutcome(controller(good, 200), "TSLA", "sess-7:41", "Bearer t");
        assertEquals("sess-7:41", capturedRequest(good).headers().firstValue("Last-Event-ID").orElse(""));
    }

    @Test
    void everyNon200IncludingOther2xxIsPassedThroughWithItsOwnStatus() throws Exception {
        // ok() used to mean "any 2xx", which rewrote a 204 or 206 to 200 AND presented it to the browser
        // as a live event stream that would never emit an event.
        int[] statuses = {204, 206, 302, 400, 409, 422, 429, 500, 503};
        for (int status : statuses) {
            String body = "{\"error\":\"CODE_" + status + "\"}";
            HttpClient http = clientStreaming(status, "application/json",
                    new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8)));

            ResponseEntity<byte[]> res =
                    refusalOf(streamOutcome(controller(http, 200), "TSLA", null, "Bearer t"));

            assertEquals(status, res.getStatusCode().value(), "status must survive: " + status);
            assertEquals(body, new String(res.getBody(), StandardCharsets.UTF_8));
        }
    }

    @Test
    void aRedirectIsPassedThroughWithoutItsLocation() throws Exception {
        HttpClient http = mock(HttpClient.class);
        @SuppressWarnings({"unchecked", "rawtypes"})
        HttpResponse resp = mock(HttpResponse.class);
        when(resp.statusCode()).thenReturn(302);
        when(resp.headers()).thenReturn(headers(Map.of(
                "content-type", List.of("text/html"),
                "location", List.of("http://stock-gex-service.internal:8021/elsewhere"))));
        when(resp.body()).thenReturn(new ByteArrayInputStream("moved".getBytes(StandardCharsets.UTF_8)));
        when(http.send(any(HttpRequest.class), any())).thenReturn(resp);

        ResponseEntity<byte[]> res = refusalOf(streamOutcome(controller(http, 200), "TSLA", null, "Bearer t"));

        assertEquals(302, res.getStatusCode().value());
        // Forwarding it would name an internal host to the browser, and this endpoint's contract is a
        // board or an error — never a redirect somewhere else.
        assertNull(res.getHeaders().getFirst("Location"),
                "an internal Location must not be handed to the browser");
        assertFalse(new String(res.getBody(), StandardCharsets.UTF_8).contains("internal")
                        || res.getHeaders().toString().contains("internal"),
                "no internal hostname anywhere in the response");
    }

    @Test
    void aStreamErrorBodyOverTheCapIsAnHonest502NotATruncatedEnvelope() throws Exception {
        byte[] huge = new byte[StockGexUpstream.MAX_ERROR_BYTES + 1];
        java.util.Arrays.fill(huge, (byte) 'x');
        HttpClient http = clientStreaming(503, "application/json", new ByteArrayInputStream(huge));

        ResponseEntity<byte[]> res = refusalOf(streamOutcome(controller(http, 200), "TSLA", null, "Bearer t"));

        assertEquals(502, res.getStatusCode().value());
        assertEquals("UPSTREAM_PROTOCOL_ERROR",
                new ObjectMapper().readTree(res.getBody()).get("error").asText());
    }

    @Test
    void aRejectedSubscribeKeepsTheUpstreamStatusBodyAndRetryAfter() throws Exception {
        String body = "{\"error\":\"MAX_SYMBOLS\",\"limit\":25}";
        HttpClient http = mock(HttpClient.class);
        @SuppressWarnings({"unchecked", "rawtypes"})
        HttpResponse resp = mock(HttpResponse.class);
        when(resp.statusCode()).thenReturn(429);
        when(resp.headers()).thenReturn(headers(Map.of(
                "content-type", List.of("application/json"), "retry-after", List.of("12"))));
        when(resp.body()).thenReturn(new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8)));
        when(http.send(any(HttpRequest.class), any())).thenReturn(resp);

        ResponseEntity<byte[]> res = refusalOf(streamOutcome(controller(http, 200), "TSLA", null, "Bearer t"));

        assertEquals(429, res.getStatusCode().value());
        assertEquals(MediaType.APPLICATION_JSON, res.getHeaders().getContentType());
        assertEquals(body, new String(res.getBody(), StandardCharsets.UTF_8));
        assertEquals("12", res.getHeaders().getFirst("Retry-After"));
    }

    @Test
    void anUnreachableUpstreamOnTheStreamIsAlsoACleanJsonError() throws Exception {
        HttpClient http = mock(HttpClient.class);
        when(http.send(any(HttpRequest.class), any())).thenThrow(new IOException("connect timed out"));

        ResponseEntity<byte[]> res = refusalOf(streamOutcome(controller(http, 200), "TSLA", null, "Bearer t"));

        assertEquals(502, res.getStatusCode().value());
        assertEquals(MediaType.APPLICATION_JSON, res.getHeaders().getContentType());
        assertEquals("UPSTREAM_UNAVAILABLE",
                new ObjectMapper().readTree(res.getBody()).get("error").asText());
    }

    // ---------------------------------------------------------------- ownership

    @Test
    void aRefusedRequestNeverConsumesAStreamSlot() throws Exception {
        HttpClient http = clientStreaming(200, "text/event-stream", new ByteArrayInputStream(new byte[0]));
        StockGexController denied = controller(http, 401, 1, new CapturingRegistrar());
        streamOutcome(denied, "TSLA", null, null);
        assertEquals(1, denied.streamSlots.availablePermits(), "a 401 must not consume a slot");

        StockGexController tooLong = controller(http, 200, 1, new CapturingRegistrar());
        streamOutcome(tooLong, "T".repeat(64), null, "Bearer t");
        assertEquals(1, tooLong.streamSlots.availablePermits(), "a rejected symbol must not consume a slot");
    }

    @Test
    void aRejectedSubscribeAndAnUnreachableUpstreamBothReturnTheSlot() throws Exception {
        StockGexController rejected = controller(
                clientStreaming(429, "application/json",
                        new ByteArrayInputStream("{\"error\":\"MAX_SYMBOLS\"}".getBytes(StandardCharsets.UTF_8))),
                200, 1, new CapturingRegistrar());
        streamOutcome(rejected, "TSLA", null, "Bearer t");
        assertEquals(1, rejected.streamSlots.availablePermits(),
                "an upstream refusal is not a live stream, so its slot must come straight back");

        HttpClient dead = mock(HttpClient.class);
        when(dead.send(any(HttpRequest.class), any())).thenThrow(new IOException("connect timed out"));
        StockGexController unreachable = controller(dead, 200, 1, new CapturingRegistrar());
        streamOutcome(unreachable, "TSLA", null, "Bearer t");
        assertEquals(1, unreachable.streamSlots.availablePermits());
    }

    @Test
    void aStreamingBodyThatIsNEVERINVOKEDStillReleasesTheSlotAndClosesTheUpstream() throws Exception {
        // THE case that a `finally` inside writeTo cannot cover. If the MVC async executor rejects the
        // task, or the container abandons the dispatch, or the client vanishes during async setup, the
        // body is never called at all — and without a lifecycle binding the permit and the upstream
        // exchange are gone until the JVM restarts.
        AtomicBoolean closed = new AtomicBoolean(false);
        InputStream upstreamBody = closeRecording(closed, "id: 1\ndata: {}\n\n");
        CapturingRegistrar registrar = new CapturingRegistrar();
        StockGexController controller =
                controller(clientStreaming(200, "text/event-stream", upstreamBody), 200, 1, registrar);

        Object outcome = streamOutcome(controller, "TSLA", null, "Bearer t");
        liveStream(outcome); // returned, and then deliberately never written to
        assertEquals(0, controller.streamSlots.availablePermits(), "the slot is held while the stream lives");

        registrar.fireAll(); // the servlet async lifecycle completing without the body ever running

        assertTrue(closed.get(), "the upstream exchange must be closed");
        assertEquals(1, controller.streamSlots.availablePermits(), "and the slot must come back");
    }

    @Test
    void theLeaseIsReleasedExactlyOnceHoweverManyThingsEndTheStream() throws Exception {
        // The pump, the async lifecycle and the idle watchdog can all fire, in any order. A permit
        // released twice hands out capacity this gateway does not have.
        AtomicBoolean closed = new AtomicBoolean(false);
        InputStream upstreamBody = closeRecording(closed, "data: x\n\n");
        CapturingRegistrar registrar = new CapturingRegistrar();
        StockGexController controller =
                controller(clientStreaming(200, "text/event-stream", upstreamBody), 200, 1, registrar);

        ResponseEntity<StreamingResponseBody> res =
                liveStream(streamOutcome(controller, "TSLA", null, "Bearer t"));
        drain(res);            // pump completes -> release
        registrar.fireAll();   // lifecycle completes -> release again
        registrar.fireAll();   // and again, for good measure

        assertEquals(1, controller.streamSlots.availablePermits(),
                "the permit count must be exactly the cap, not more");
        assertTrue(closed.get());
    }

    @Test
    void aClientDisconnectClosesTheUpstreamStreamSoItsListenerSlotIsReleased() throws Exception {
        // The upstream caps concurrent SSE clients. A browser tab closing must release that slot, or the
        // service runs out of listeners after N navigations and answers MAX_SYMBOLS to everyone.
        AtomicBoolean closed = new AtomicBoolean(false);
        InputStream upstreamBody = closeRecording(closed, "id: 1\ndata: {}\n\n");
        StockGexController controller = controller(
                clientStreaming(200, "text/event-stream", upstreamBody), 200, 1, new CapturingRegistrar());
        ResponseEntity<StreamingResponseBody> res =
                liveStream(streamOutcome(controller, "TSLA", null, "Bearer t"));

        OutputStream deadClient = new OutputStream() {
            @Override
            public void write(int b) throws IOException {
                throw new IOException("Broken pipe");
            }

            @Override
            public void write(byte[] b, int off, int len) throws IOException {
                throw new IOException("Broken pipe");
            }
        };
        res.getBody().writeTo(deadClient); // must not propagate: a gone client is not a server error

        assertTrue(closed.get(), "the upstream stream must be closed when the client disappears");
        assertEquals(1, controller.streamSlots.availablePermits(),
                "a disconnected client must also give this gateway its stream slot back");
    }

    @Test
    void aStreamThatBlowsUpMidPumpStillReturnsItsSlot() throws Exception {
        AtomicBoolean closed = new AtomicBoolean(false);
        StockGexController controller = controller(
                clientStreaming(200, "text/event-stream", closeRecording(closed, "data: x\n\n")),
                200, 1, new CapturingRegistrar());
        ResponseEntity<StreamingResponseBody> res =
                liveStream(streamOutcome(controller, "TSLA", null, "Bearer t"));
        assertEquals(0, controller.streamSlots.availablePermits());

        OutputStream exploding = new OutputStream() {
            @Override
            public void write(int b) {
                throw new IllegalStateException("container went away");
            }

            @Override
            public void write(byte[] b, int off, int len) {
                throw new IllegalStateException("container went away");
            }
        };
        assertThrows(RuntimeException.class, () -> res.getBody().writeTo(exploding));
        assertEquals(1, controller.streamSlots.availablePermits());
        assertTrue(closed.get());
    }

    @Test
    void aSilentUpstreamIsClosedByTheREALIdleWatchdogRatherThanHeldForever() throws Exception {
        // Nothing else can see this failure. The request timeout stopped applying when headers
        // arrived, and a heartbeat only proves liveness while the upstream is still sending one —
        // silence produces no evidence of itself. Without the watchdog, one half-open peer holds an
        // async thread, a permit and an upstream exchange until the JVM restarts.
        //
        // The PRODUCTION watchdog is exercised, on the controller's own lease, via the timing seam.
        // An earlier version of this test built a second lease over the same semaphore, which
        // released a permit it had never acquired and left the count wrong in the direction that
        // hides a leak.
        CountDownLatch closed = new CountDownLatch(1);
        InputStream silent = new InputStream() {
            private final CountDownLatch stop = new CountDownLatch(1);

            @Override
            public int read() throws IOException {
                try {
                    stop.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                throw new IOException("closed under the reader");
            }

            @Override
            public int read(byte[] b, int off, int len) throws IOException {
                return read();
            }

            @Override
            public void close() {
                stop.countDown();
                closed.countDown();
            }
        };

        StockGexController controller = new StockGexController(
                upstream(clientStreaming(200, "text/event-stream", silent)),
                authReturning(200), new ObjectMapper(), 1, new CapturingRegistrar(),
                20L, 60L);   // check every 20ms, idle after 60ms
        ResponseEntity<StreamingResponseBody> res =
                liveStream(streamOutcome(controller, "TSLA", null, "Bearer t"));
        assertEquals(0, controller.streamSlots.availablePermits());

        CountDownLatch pumpFinished = new CountDownLatch(1);
        Thread pump = new Thread(() -> {
            try {
                res.getBody().writeTo(OutputStream.nullOutputStream());
            } catch (IOException ignored) {
                // the watchdog's close surfaces here
            } finally {
                pumpFinished.countDown();
            }
        });
        pump.setDaemon(true);
        pump.start();

        assertTrue(closed.await(5, TimeUnit.SECONDS),
                "the watchdog must close a stream that has delivered nothing");
        assertTrue(pumpFinished.await(5, TimeUnit.SECONDS),
                "and closing it is what unblocks the pump parked on the read");
        assertEquals(1, controller.streamSlots.availablePermits(),
                "exactly one permit back — not two, however many things ended the stream");
        liveResponses.remove(res);
    }

    @Test
    void aStreamThatKEEPSDELIVERINGIsNeverClosedByItsOwnWatchdog() throws Exception {
        // The other half of the contract: the watchdog must not be a timer on healthy sessions.
        AtomicBoolean stop = new AtomicBoolean(false);
        InputStream chatty = new InputStream() {
            @Override
            public int read() {
                return stop.get() ? -1 : ':';
            }

            @Override
            public int read(byte[] b, int off, int len) {
                if (stop.get()) {
                    return -1;
                }
                b[off] = ':';
                try {
                    Thread.sleep(5L);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return 1;
            }
        };
        StockGexController controller = new StockGexController(
                upstream(clientStreaming(200, "text/event-stream", chatty)),
                authReturning(200), new ObjectMapper(), 1, new CapturingRegistrar(),
                20L, 200L);
        ResponseEntity<StreamingResponseBody> res =
                liveStream(streamOutcome(controller, "TSLA", null, "Bearer t"));

        Thread pump = new Thread(() -> {
            try {
                res.getBody().writeTo(OutputStream.nullOutputStream());
            } catch (IOException ignored) {
                // only reached if the watchdog wrongly fired
            }
        });
        pump.setDaemon(true);
        pump.start();

        Thread.sleep(600L);   // three idle windows, with bytes arriving throughout
        assertEquals(0, controller.streamSlots.availablePermits(),
                "a stream that is delivering must still be alive after several idle windows");
        stop.set(true);
        pump.join(5000);
        liveResponses.remove(res);
    }

    @Test
    void theIdleClockIsMonotonicAndMovedOnlyByBytesArriving() {
        // MONOTONIC on purpose: this is the only mechanism bounding a silent upstream, and a
        // backward wall-clock step (NTP, a resumed VM) would postpone it by exactly the size of the
        // jump — at the one moment it must not be postponed.
        Semaphore slots = new Semaphore(1);
        slots.tryAcquire();
        StockGexController.StreamLease lease =
                new StockGexController.StreamLease(new ByteArrayInputStream(new byte[0]), slots, false);

        assertFalse(lease.isIdleFor(60_000L), "a stream that just started is not idle");
        assertTrue(lease.isIdleFor(0L), "and one measured against a zero budget always is");
        lease.markActivity();
        assertFalse(lease.isIdleFor(60_000L), "a byte arriving resets the clock");
        assertFalse(lease.isReleased());
        lease.release();
        lease.release();
        assertTrue(lease.isReleased());
        assertEquals(1, slots.availablePermits(), "released twice, returned once");
    }

    @Test
    void streamsBeyondTheCapAreRefusedIMMEDIATELYWithACodeThePageKnows() throws Exception {
        // The failure this prevents is NOT "one user gets a slow board": an async response that cannot
        // get a thread does not fail, it queues, and with spring.mvc.async.request-timeout=-1 it queues
        // forever, having answered nothing at all. And the refusal itself must not be a streaming body,
        // or it would need the very executor whose exhaustion it reports.
        HttpClient http = clientStreaming(200, "text/event-stream",
                new ByteArrayInputStream("id: 1\ndata: {}\n\n".getBytes(StandardCharsets.UTF_8)));
        StockGexController controller = controller(http, 200, 1, new CapturingRegistrar());

        ResponseEntity<StreamingResponseBody> first =
                liveStream(streamOutcome(controller, "TSLA", null, "Bearer t"));
        assertEquals(200, first.getStatusCode().value());

        ResponseEntity<byte[]> refused = refusalOf(streamOutcome(controller, "AAPL", null, "Bearer t"));
        assertEquals(503, refused.getStatusCode().value());
        assertEquals("5", refused.getHeaders().getFirst("Retry-After"));
        JsonNode body = new ObjectMapper().readTree(refused.getBody());
        assertEquals("SSE_CLIENT_LIMIT", body.get("error").asText(),
                "reuse the upstream's own vocabulary — the page already renders this one");

        // Draining the first stream ends it, and the slot must come back for the next client.
        drain(first);
        assertEquals(1, controller.streamSlots.availablePermits());
    }

    // ---------------------------------------------------------------- content-type / encoding

    @Test
    void aWildcardContentTypeFallsBackInsteadOfBecomingA500() throws Exception {
        // "Parseable" is not "usable": */* and application/* both parse, and are then REJECTED by
        // ResponseEntity.contentType() with an IllegalArgumentException — after the upstream body has
        // already been read successfully. Unhandled, that is a 500 with a stack trace where a
        // perfectly good 422 or 200 should have been.
        for (String wildcard : new String[]{"*/*", "application/*", "*/*; charset=utf-8"}) {
            ResponseEntity<byte[]> board = controller(
                    clientReturning(200, contentType(wildcard), BOARD_JSON.getBytes(StandardCharsets.UTF_8)),
                    200).board("TSLA", "Bearer t");
            assertEquals(200, board.getStatusCode().value(), wildcard);
            assertEquals(MediaType.APPLICATION_JSON, board.getHeaders().getContentType(), wildcard);
            assertEquals(BOARD_JSON, bodyText(board), wildcard);
        }
    }

    @Test
    void aWildcardContentTypeOnAREFUSALDoesNotReplaceItWithA500() throws Exception {
        // The nastiest version: throwing from inside the exception handler while HANDLING a refusal
        // would swap the upstream's 429 for a 500 and take the whole error contract with it.
        String body = "{\"error\":\"MAX_SYMBOLS\"}";
        HttpClient http = clientStreaming(429, "*/*",
                new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8)));

        ResponseEntity<byte[]> res = refusalOf(streamOutcome(controller(http, 200), "TSLA", null, "Bearer t"));

        assertEquals(429, res.getStatusCode().value());
        assertEquals(body, new String(res.getBody(), StandardCharsets.UTF_8));
    }

    @Test
    void a200ThatIsNotAnEventStreamIsA502NotAFakeLiveStream() throws Exception {
        // A gateway answering `200 application/json`, or an HTML error page from an intermediary,
        // would otherwise be handed to the browser as a live stream that can never carry an event —
        // holding a slot and an async thread until the idle watchdog eventually noticed.
        AtomicBoolean closed = new AtomicBoolean(false);
        StockGexController controller = controller(
                clientStreaming(200, "application/json", closeRecording(closed, "{\"not\":\"a stream\"}")),
                200, 1, new CapturingRegistrar());

        ResponseEntity<byte[]> res = refusalOf(streamOutcome(controller, "TSLA", null, "Bearer t"));

        assertEquals(502, res.getStatusCode().value());
        assertEquals("UPSTREAM_PROTOCOL_ERROR",
                new ObjectMapper().readTree(res.getBody()).get("error").asText());
        assertTrue(closed.get(), "and the body must be closed rather than left open");
        assertEquals(1, controller.streamSlots.availablePermits(), "and the slot handed straight back");
    }

    @Test
    void a200WithNoContentTypeAtAllIsStillTreatedAsTheStreamTheContractPromises() throws Exception {
        // Absence is not contradiction: some intermediaries strip the header, and the endpoint's
        // contract says a 200 here IS the stream.
        ResponseEntity<StreamingResponseBody> res = liveStream(streamOutcome(
                controller(clientStreaming(200, null,
                        new ByteArrayInputStream("id: 1\ndata: {}\n\n".getBytes(StandardCharsets.UTF_8))), 200),
                "TSLA", null, "Bearer t"));

        assertEquals(200, res.getStatusCode().value());
        assertEquals(MediaType.TEXT_EVENT_STREAM, res.getHeaders().getContentType());
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

        ResponseEntity<byte[]> res = controller(http, 200).board("TSLA", "Bearer t");

        assertEquals(502, res.getStatusCode().value());
        assertEquals("UPSTREAM_PROTOCOL_ERROR",
                new ObjectMapper().readTree(res.getBody()).get("error").asText());
    }

    @Test
    void anIdentityEncodingHeaderIsFine() throws Exception {
        ResponseEntity<byte[]> res = controller(clientReturning(200,
                headers(Map.of("content-type", List.of("application/json"),
                               "content-encoding", List.of("identity"))),
                BOARD_JSON.getBytes(StandardCharsets.UTF_8)), 200).board("TSLA", "Bearer t");

        assertEquals(200, res.getStatusCode().value());
        assertEquals(BOARD_JSON, bodyText(res));
    }

    @Test
    void aBoardRedirectAlsoLosesItsInternalLocation() throws Exception {
        // Pinned for the board as well as the stream: an internal Location handed to a browser names
        // a host that only exists inside the cluster.
        HttpClient http = clientReturning(302,
                headers(Map.of("content-type", List.of("text/html"),
                               "location", List.of("http://stock-gex-service.internal:8021/elsewhere"))),
                "moved".getBytes(StandardCharsets.UTF_8));

        ResponseEntity<byte[]> res = controller(http, 200).board("TSLA", "Bearer t");

        assertEquals(302, res.getStatusCode().value());
        assertEquals("moved", bodyText(res));
        assertNull(res.getHeaders().getFirst("Location"));
        assertFalse(res.getHeaders().toString().contains("internal"));
    }

    @Test
    void aStreamREFUSALBodyAlsoSurvivesByteForByte() throws Exception {
        // The board path is covered above; the refusal path goes through a different response
        // builder and the exception handler, so it gets its own byte-hostile case.
        byte[] hostile = {0x7B, (byte) 0xFF, (byte) 0xFE, 0x00, 0x41, (byte) 0xC3, 0x28, 0x7D};
        HttpClient http = clientStreaming(422, "application/json", new ByteArrayInputStream(hostile));

        ResponseEntity<byte[]> res = refusalOf(streamOutcome(controller(http, 200), "TSLA", null, "Bearer t"));

        assertEquals(422, res.getStatusCode().value());
        assertArrayEquals(hostile, res.getBody(),
                "a refusal body is as much a passthrough as a board body");
    }

    @Test
    void aWildcardOnALIVESTREAMIsJudgedOnItsOWNTermsNotByTheHeaderBuilder() throws Exception {
        // The trap: mediaType() is deliberately forgiving because its job is to produce a header
        // Spring will accept, so it turns every wildcard into the fallback. Reusing it to DECIDE
        // whether a 200 is a stream made `application/*` mean "no opinion" — and an
        // application/*-labelled response was relabelled text/event-stream and served as live market
        // data. application/* is not an absence; it is a statement that excludes event streams.
        String[][] cases = {
            {"application/*", "reject"},
            {"application/json", "reject"},
            {"text/html", "reject"},
            {"not a media type at all", "reject"},
            {"text/*", "accept"},
            {"*/*", "accept"},
            {"text/event-stream", "accept"},
            {"text/event-stream;charset=utf-8", "accept"},
        };
        for (String[] c : cases) {
            AtomicBoolean closed = new AtomicBoolean(false);
            StockGexController controller = controller(
                    clientStreaming(200, c[0], closeRecording(closed, "id: 1\ndata: {}\n\n")),
                    200, 1, new CapturingRegistrar());
            Object outcome = streamOutcome(controller, "TSLA", null, "Bearer t");

            if ("accept".equals(c[1])) {
                assertEquals(200, ((ResponseEntity<?>) outcome).getStatusCode().value(), c[0]);
                assertTrue(((ResponseEntity<?>) outcome).getBody() instanceof StreamingResponseBody, c[0]);
                liveResponses.add((ResponseEntity<StreamingResponseBody>) outcome);
            } else {
                ResponseEntity<byte[]> refused = refusalOf(outcome);
                assertEquals(502, refused.getStatusCode().value(), c[0]);
                assertEquals("UPSTREAM_PROTOCOL_ERROR",
                        new ObjectMapper().readTree(refused.getBody()).get("error").asText(), c[0]);
                assertTrue(closed.get(), "the body must be closed on rejection: " + c[0]);
                assertEquals(1, controller.streamSlots.availablePermits(),
                        "and the slot handed straight back: " + c[0]);
            }
        }
    }

    @Test
    void aDuplicatedLastEventIdIsDroppedRatherThanJoinedIntoATokenNobodyIssued() throws Exception {
        // MVC joins duplicate header fields with a comma, and "41,7" passes every character check
        // while being an id the upstream never minted. The contract is exactly one opaque token, or
        // nothing — and nothing costs one reset snapshot, which is always correct.
        HttpClient http = clientStreaming(200, "text/event-stream", new ByteArrayInputStream(new byte[0]));
        streamOutcomeWithIds(controller(http, 200), "TSLA", List.of("41", "7"), "Bearer t");

        assertTrue(capturedRequest(http).headers().firstValue("Last-Event-ID").isEmpty(),
                "two fields is not one token");

        HttpClient single = clientStreaming(200, "text/event-stream", new ByteArrayInputStream(new byte[0]));
        streamOutcomeWithIds(controller(single, 200), "TSLA", List.of("41"), "Bearer t");
        assertEquals("41", capturedRequest(single).headers().firstValue("Last-Event-ID").orElse(""));
    }

    @Test
    void theBoardBudgetIsAWHOLEREQUESTBudgetNotOnePerPhase() throws Exception {
        // The request timeout covers the handshake; the body read then got a FRESH copy of the same
        // budget, so a 5s setting could take almost 10s. GatewaySettings calls it a whole-request
        // budget, so it has to be one.
        long budgetMs = 600L;
        // Behaves like a real socket in the one respect this test depends on: close() ENDS the read.
        // The deadline is enforced by closing the stream from the watchdog thread, so a stub that
        // ignored close would hang forever rather than fail — and a hanging test proves nothing.
        InputStream slow = new InputStream() {
            private final java.util.concurrent.atomic.AtomicBoolean closed =
                    new java.util.concurrent.atomic.AtomicBoolean(false);

            @Override
            public int read() throws IOException {
                if (closed.get()) {
                    throw new IOException("closed under the reader");
                }
                try {
                    Thread.sleep(25L);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                if (closed.get()) {
                    throw new IOException("closed under the reader");
                }
                return 'x';
            }

            @Override
            public int read(byte[] b, int off, int len) throws IOException {
                b[off] = (byte) read();
                return 1;
            }

            @Override
            public void close() {
                closed.set(true);
            }
        };
        StockGexUpstream upstream = new StockGexUpstream("http://stock-gex-service:8021",
                Duration.ofMillis(budgetMs), clientStreaming(200, "application/json", slow));
        upstreams.add(upstream);
        StockGexController controller = new StockGexController(
                upstream, authReturning(200), new ObjectMapper(), 4, new CapturingRegistrar());

        long start = System.nanoTime();
        ResponseEntity<byte[]> res = controller.board("TSLA", "Bearer t");
        long elapsedMs = (System.nanoTime() - start) / 1_000_000L;

        assertEquals(502, res.getStatusCode().value(), "a body that never ends must be cut off");
        assertTrue(elapsedMs < budgetMs * 2,
                "the whole request took " + elapsedMs + "ms against a " + budgetMs + "ms budget");
    }

    // ---------------------------------------------------------------- metrics

    @Test
    void everyTransitionIsCountedExactlyOnceAtTheCommitThatAuthorisesIt() throws Exception {
        // A counter incremented in a caller, a retry, or an exception handler eventually disagrees
        // with the state it claims to describe. Each of these is incremented at the one place the
        // transition is decided, and the GAUGE is read from the semaphore rather than reconstructed.
        long admittedBefore = StockGexController.STREAMS_ADMITTED.get();
        long endedBefore = StockGexController.STREAMS_ENDED.get();
        long refusedBefore = StockGexController.STREAMS_REFUSED_AT_CAP.get();
        long boardsBefore = StockGexController.BOARDS_SERVED.get();

        StockGexController controller = controller(
                clientStreaming(200, "text/event-stream",
                        new ByteArrayInputStream("id: 1\ndata: {}\n\n".getBytes(StandardCharsets.UTF_8))),
                200, 1, new CapturingRegistrar());
        ResponseEntity<StreamingResponseBody> live =
                liveStream(streamOutcome(controller, "TSLA", null, "Bearer t"));
        assertEquals(admittedBefore + 1, StockGexController.STREAMS_ADMITTED.get());
        assertEquals(endedBefore, StockGexController.STREAMS_ENDED.get(), "not ended yet");

        // Over the cap while that one is live.
        refusalOf(streamOutcome(controller, "AAPL", null, "Bearer t"));
        assertEquals(refusedBefore + 1, StockGexController.STREAMS_REFUSED_AT_CAP.get());

        // End it three different ways at once; it must still be counted once.
        drain(live);
        liveResponses.remove(live);
        assertEquals(endedBefore + 1, StockGexController.STREAMS_ENDED.get(),
                "a stream ended by pump AND lifecycle AND watchdog is still one ending");
        assertEquals(1, controller.streamSlots.availablePermits());

        controller(clientReturning(200, "application/json", BOARD_JSON), 200).board("TSLA", "Bearer t");
        assertEquals(boardsBefore + 1, StockGexController.BOARDS_SERVED.get());
    }

    @Test
    void theOperatorLineCarriesNoUnboundedOrPerSymbolLabel() throws Exception {
        StockGexController controller = controller(
                clientReturning(200, "application/json", BOARD_JSON), 200);
        controller.board("TSLA", "Bearer t");

        String line = controller.counters();

        assertTrue(line.contains("streamSlotsFree="), line);
        assertFalse(line.contains("TSLA"),
                "a per-ticker label is an unbounded label set, and an arbitrary ticker must not be "
                + "able to create per-ticker anything");
        assertFalse(line.contains("Bearer"), "and a credential must never reach a log line");
    }

    @Test
    void remoteTextOnALogLineIsBounded() {
        String hostile = "x".repeat(5000) + "\r\ninjected: line";
        String logged = StockGexController.shortForLog(hostile);

        assertTrue(logged.length() < 200, "unbounded remote text must not be pasted into a log");
        assertFalse(logged.contains("\n"), "and must not be able to forge a second log line");
        assertEquals("", StockGexController.shortForLog(null));
    }

    // ---------------------------------------------------------------- timeout shape

    @Test
    void bothCallsBoundTheHANDSHAKEAndNeitherBoundsAHealthyStream() throws Exception {
        // Verified against the JDK client: with BodyHandlers.ofInputStream the request timeout stops
        // being enforced the moment send() returns with headers. So it bounds "accepted the socket, sent
        // no headers" — which would otherwise pin a Tomcat request thread — without putting any lifetime
        // limit on an hours-long stream. What bounds the BODY is the idle watchdog, not this.
        HttpClient boardHttp = clientReturning(200, "application/json", BOARD_JSON);
        controller(boardHttp, 200).board("TSLA", "Bearer t");
        assertTrue(capturedRequest(boardHttp).timeout().isPresent(),
                "a request/response call must be bounded");

        HttpClient streamHttp = clientStreaming(200, "text/event-stream", new ByteArrayInputStream(new byte[0]));
        streamOutcome(controller(streamHttp, 200), "TSLA", null, "Bearer t");
        assertTrue(capturedRequest(streamHttp).timeout().isPresent(),
                "an unbounded handshake lets a silent upstream pin a request thread forever");
    }

    private static InputStream closeRecording(AtomicBoolean closed, String payload) {
        return new ByteArrayInputStream(payload.getBytes(StandardCharsets.UTF_8)) {
            @Override
            public void close() throws IOException {
                closed.set(true);
                super.close();
            }
        };
    }
}

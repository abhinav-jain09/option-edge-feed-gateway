package app.feedgateway.stockgex;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

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
 * <p>Tests drive the real {@link StockGexUpstream} over a stubbed {@link HttpClient} rather than mocking
 * the upstream, so URL construction, header forwarding and timeout shape are covered too.
 */
class StockGexControllerTest {

    private static final String BOARD_JSON =
            "{\"symbol\":\"TSLA\",\"asOf\":\"2026-08-11T14:31:02Z\",\"spot\":312.45,"
            + "\"strikes\":[{\"strike\":310.0,\"gex\":-1.24e8},{\"strike\":315.0,\"gex\":8.1e7}]}";

    // ---------------------------------------------------------------- helpers

    private static LiquidityHistoryAuth authReturning(int status) {
        LiquidityHistoryAuth auth = mock(LiquidityHistoryAuth.class);
        when(auth.authenticate(any())).thenReturn(new LiquidityHistoryAuth.Result(status, "tester"));
        return auth;
    }

    private static HttpHeaders headers(String contentType) {
        Map<String, List<String>> map = contentType == null
                ? Map.of() : Map.of("content-type", List.of(contentType));
        return HttpHeaders.of(map, (k, v) -> true);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static HttpClient clientReturning(int status, String contentType, String body) throws Exception {
        HttpClient http = mock(HttpClient.class);
        HttpResponse resp = mock(HttpResponse.class);
        when(resp.statusCode()).thenReturn(status);
        when(resp.headers()).thenReturn(headers(contentType));
        when(resp.body()).thenReturn(body.getBytes(StandardCharsets.UTF_8));
        when(http.send(any(HttpRequest.class), any())).thenReturn(resp);
        return http;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static HttpClient clientStreaming(int status, String contentType, InputStream body) throws Exception {
        HttpClient http = mock(HttpClient.class);
        HttpResponse resp = mock(HttpResponse.class);
        when(resp.statusCode()).thenReturn(status);
        when(resp.headers()).thenReturn(headers(contentType));
        when(resp.body()).thenReturn(body);
        when(http.send(any(HttpRequest.class), any())).thenReturn(resp);
        return http;
    }

    private static StockGexController controller(HttpClient http, int authStatus) {
        return controller(http, authStatus, StockGexController.MAX_CONCURRENT_STREAMS);
    }

    private static StockGexController controller(HttpClient http, int authStatus, int maxStreams) {
        StockGexUpstream upstream =
                new StockGexUpstream("http://stock-gex-service:8021/", Duration.ofSeconds(5), http);
        return new StockGexController(upstream, authReturning(authStatus), new ObjectMapper(), maxStreams);
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
    void aNonAsciiBodySurvivesByteForByte() throws Exception {
        // "Verbatim" has to mean verbatim for BYTES, not for ASCII. Carrying the body as a String makes
        // the response charset a guess by a message converter (ISO-8859-1 for anything it does not
        // recognise as JSON), and a stateReason with a non-ASCII character would arrive mangled.
        String body = "{\"error\":\"BAD_SYMBOL\",\"detail\":\"unknown root — try TSLA · 「日本」\"}";
        HttpClient http = clientReturning(400, "text/plain; charset=utf-8", body);

        ResponseEntity<byte[]> res = controller(http, 200).board("ZZZZ", "Bearer t");

        assertEquals(400, res.getStatusCode().value());
        assertArrayEqualsUtf8(body, res.getBody());
    }

    private static void assertArrayEqualsUtf8(String expected, byte[] actual) {
        assertEquals(expected, new String(actual, StandardCharsets.UTF_8));
        assertEquals(expected.getBytes(StandardCharsets.UTF_8).length, actual.length,
                "byte length must be identical — no charset was allowed to re-encode the body");
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
        // The page has one error reader; a differently-shaped envelope reads as an unknown failure.
        assertEquals("UPSTREAM_UNAVAILABLE", body.get("error").asText());
        assertEquals("UPSTREAM_UNAVAILABLE", body.get("code").asText());
        assertNotNull(body.get("detail"));
        assertFalse(bodyText(res).contains("10.42.0.7"), "internal host/port must not leak to the browser");
        assertFalse(bodyText(res).contains("Exception"), "no stack trace or exception class in the body");
    }

    @Test
    void aMisconfiguredBaseUrlIsA502NotA500() throws Exception {
        // A base URL with no scheme makes the JDK client throw IllegalArgumentException, which is NOT an
        // IOException. Unhandled it becomes a 500 with a stack trace on the browser's side; the page has
        // no entry for that. It is a configuration fault, and "cannot reach the board service" is the
        // honest, renderable answer.
        HttpClient http = mock(HttpClient.class);
        StockGexUpstream upstream =
                new StockGexUpstream("stock-gex-service:8021", Duration.ofSeconds(5), http);
        StockGexController controller =
                new StockGexController(upstream, authReturning(200), new ObjectMapper());

        ResponseEntity<byte[]> board = controller.board("TSLA", "Bearer t");
        assertEquals(502, board.getStatusCode().value());
        assertEquals("UPSTREAM_UNAVAILABLE",
                new ObjectMapper().readTree(board.getBody()).get("error").asText());

        ResponseEntity<StreamingResponseBody> stream = controller.stream("TSLA", null, "Bearer t");
        assertEquals(502, stream.getStatusCode().value());
        verifyNoInteractions(http);
    }

    @Test
    void authIsEnforcedBeforeTheUpstreamIsEvenCalled() throws Exception {
        for (int status : new int[]{401, 403}) {
            HttpClient http = mock(HttpClient.class);
            assertEquals(status, controller(http, status).board("TSLA", null).getStatusCode().value());
            assertEquals(status, controller(http, status).stream("TSLA", null, null).getStatusCode().value());
            verifyNoInteractions(http);
        }
    }

    @Test
    void aRefusedRequestNeverConsumesAStreamSlot() throws Exception {
        // Auth failures and oversized symbols are answered before the permit is taken, and every early
        // exit after it is taken releases it. A leaked permit is a slot this gateway never gets back.
        HttpClient http = clientStreaming(200, "text/event-stream", new ByteArrayInputStream(new byte[0]));
        StockGexController denied = controller(http, 401, 1);
        denied.stream("TSLA", null, null);
        assertEquals(1, denied.streamSlots.availablePermits(), "a 401 must not consume a slot");

        StockGexController tooLong = controller(http, 200, 1);
        tooLong.stream("T".repeat(64), null, "Bearer t");
        assertEquals(1, tooLong.streamSlots.availablePermits(), "a rejected symbol must not consume a slot");
    }

    @Test
    void aRejectedSubscribeAndAnUnreachableUpstreamBothReturnTheSlot() throws Exception {
        StockGexController rejected = controller(
                clientStreaming(429, "application/json",
                        new ByteArrayInputStream("{\"error\":\"MAX_SYMBOLS\"}".getBytes(StandardCharsets.UTF_8))),
                200, 1);
        rejected.stream("TSLA", null, "Bearer t");
        assertEquals(1, rejected.streamSlots.availablePermits(),
                "an upstream refusal is not a live stream, so its slot must come straight back");

        HttpClient dead = mock(HttpClient.class);
        when(dead.send(any(HttpRequest.class), any())).thenThrow(new IOException("connect timed out"));
        StockGexController unreachable = controller(dead, 200, 1);
        unreachable.stream("TSLA", null, "Bearer t");
        assertEquals(1, unreachable.streamSlots.availablePermits());
    }

    @Test
    void streamsBeyondTheCapAreRefusedImmediatelyWithACodeThePageKnows() throws Exception {
        // The failure this prevents is NOT "one user gets a slow board": an async response that cannot
        // get a thread does not fail, it queues, and with spring.mvc.async.request-timeout=-1 it queues
        // forever, having answered nothing at all. Refusing at the door is the only loud option.
        HttpClient http = clientStreaming(200, "text/event-stream",
                new ByteArrayInputStream("id: 1\ndata: {}\n\n".getBytes(StandardCharsets.UTF_8)));
        StockGexController controller = controller(http, 200, 1);

        ResponseEntity<StreamingResponseBody> first = controller.stream("TSLA", null, "Bearer t");
        assertEquals(200, first.getStatusCode().value());

        ResponseEntity<StreamingResponseBody> refused = controller.stream("AAPL", null, "Bearer t");
        assertEquals(503, refused.getStatusCode().value());
        assertEquals("5", refused.getHeaders().getFirst("Retry-After"));
        JsonNode body = new ObjectMapper().readTree(drain(refused));
        assertEquals("SSE_CLIENT_LIMIT", body.get("error").asText(),
                "reuse the upstream's own vocabulary — the page already renders this one");

        // Draining the first stream ends it, and the slot must come back for the next client.
        drain(first);
        assertEquals(1, controller.streamSlots.availablePermits());
    }

    @Test
    void aStreamThatBlowsUpMidPumpStillReturnsItsSlot() throws Exception {
        // A RuntimeException out of the servlet output stream is not the expected failure mode, which is
        // exactly why the release must not be written only for the expected one.
        StockGexController controller = controller(
                clientStreaming(200, "text/event-stream",
                        new ByteArrayInputStream("data: x\n\n".getBytes(StandardCharsets.UTF_8))),
                200, 1);
        ResponseEntity<StreamingResponseBody> res = controller.stream("TSLA", null, "Bearer t");
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
        try {
            res.getBody().writeTo(exploding);
        } catch (RuntimeException expected) {
            // propagated on purpose: an unexpected fault is not something to swallow
        }
        assertEquals(1, controller.streamSlots.availablePermits());
    }

    @Test
    void theSymbolIsUrlEncodedRatherThanTrustedIntoTheQueryString() throws Exception {
        HttpClient http = clientReturning(400, "application/json", "{\"error\":\"BAD_SYMBOL\"}");
        controller(http, 200).board("A B&x=1", "Bearer t");
        // The upstream owns BAD_SYMBOL; our job is only to make sure what it judges is what was asked
        // for, not a second parameter we accidentally injected.
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

    // ---------------------------------------------------------------- stream

    @Test
    void streamAnnouncesEventStreamAndDisablesProxyBuffering() throws Exception {
        HttpClient http = clientStreaming(200, "text/event-stream",
                new ByteArrayInputStream("id: 7\ndata: {}\n\n".getBytes(StandardCharsets.UTF_8)));

        ResponseEntity<StreamingResponseBody> res = controller(http, 200).stream("TSLA", null, "Bearer t");

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

        ResponseEntity<StreamingResponseBody> res = controller(http, 200).stream("TSLA", null, "Bearer t");

        assertEquals(sse, new String(drain(res), StandardCharsets.UTF_8));
    }

    @Test
    void lastEventIdIsForwardedUpstreamSoAReconnectResumes() throws Exception {
        HttpClient http = clientStreaming(200, "text/event-stream",
                new ByteArrayInputStream(new byte[0]));

        controller(http, 200).stream("TSLA", "42", "Bearer t");

        HttpRequest req = capturedRequest(http);
        assertEquals("42", req.headers().firstValue("Last-Event-ID").orElse(""));
        assertEquals("text/event-stream", req.headers().firstValue("Accept").orElse(""));
    }

    @Test
    void aMalformedLastEventIdIsDroppedRatherThanForwardedOrRejected() throws Exception {
        // Forwarding a header with a newline throws on header validation, turning a resumable reconnect
        // into an error page. Dropping it degrades to a full reset snapshot, which is always correct.
        for (String hostile : new String[]{"42\r\nX-Injected: 1", "  ", " ", "9".repeat(300)}) {
            HttpClient http = clientStreaming(200, "text/event-stream", new ByteArrayInputStream(new byte[0]));
            ResponseEntity<StreamingResponseBody> res = controller(http, 200).stream("TSLA", hostile, "Bearer t");
            assertEquals(200, res.getStatusCode().value(), "must not fail the request: " + hostile);
            assertTrue(capturedRequest(http).headers().firstValue("Last-Event-ID").isEmpty(),
                    "hostile id must not reach the upstream: " + hostile);
        }
    }

    @Test
    void aRejectedSubscribeKeepsTheUpstreamStatusAndBodyToo() throws Exception {
        // The stream endpoint has the same contract as the board: capacity and universe errors are
        // answered on the subscribe, and the page branches on them identically.
        String body = "{\"error\":\"MAX_SYMBOLS\",\"limit\":25}";
        HttpClient http = clientStreaming(429, "application/json",
                new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8)));

        ResponseEntity<StreamingResponseBody> res = controller(http, 200).stream("TSLA", null, "Bearer t");

        assertEquals(429, res.getStatusCode().value());
        assertEquals(MediaType.APPLICATION_JSON, res.getHeaders().getContentType());
        assertEquals(body, new String(drain(res), StandardCharsets.UTF_8));
    }

    @Test
    void anUnreachableUpstreamOnTheStreamIsAlsoACleanJsonError() throws Exception {
        HttpClient http = mock(HttpClient.class);
        when(http.send(any(HttpRequest.class), any())).thenThrow(new IOException("connect timed out"));

        ResponseEntity<StreamingResponseBody> res = controller(http, 200).stream("TSLA", null, "Bearer t");

        assertEquals(502, res.getStatusCode().value());
        assertEquals(MediaType.APPLICATION_JSON, res.getHeaders().getContentType());
        JsonNode parsed = new ObjectMapper().readTree(drain(res));
        assertEquals("UPSTREAM_UNAVAILABLE", parsed.get("error").asText());
    }

    @Test
    void aClientDisconnectClosesTheUpstreamStreamSoItsListenerSlotIsReleased() throws Exception {
        // The upstream caps concurrent SSE clients. A browser tab closing must release that slot, or the
        // service runs out of listeners after N navigations and answers MAX_SYMBOLS to everyone.
        AtomicBoolean closed = new AtomicBoolean(false);
        InputStream upstreamBody = new ByteArrayInputStream(
                "id: 1\ndata: {}\n\n".getBytes(StandardCharsets.UTF_8)) {
            @Override
            public void close() throws IOException {
                closed.set(true);
                super.close();
            }
        };
        HttpClient http = clientStreaming(200, "text/event-stream", upstreamBody);
        StockGexController controller = controller(http, 200, 1);
        ResponseEntity<StreamingResponseBody> res = controller.stream("TSLA", null, "Bearer t");

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
    void aNormallyEndedStreamAlsoClosesTheUpstream() throws Exception {
        AtomicBoolean closed = new AtomicBoolean(false);
        InputStream upstreamBody = new ByteArrayInputStream("data: bye\n\n".getBytes(StandardCharsets.UTF_8)) {
            @Override
            public void close() throws IOException {
                closed.set(true);
                super.close();
            }
        };
        ResponseEntity<StreamingResponseBody> res =
                controller(clientStreaming(200, "text/event-stream", upstreamBody), 200)
                        .stream("TSLA", null, "Bearer t");
        drain(res);
        assertTrue(closed.get());
    }

    // ---------------------------------------------------------------- timeout shape

    @Test
    void theBoardHasARequestDeadlineAndTheStreamDeliberatelyHasNone() throws Exception {
        HttpClient boardHttp = clientReturning(200, "application/json", BOARD_JSON);
        controller(boardHttp, 200).board("TSLA", "Bearer t");
        assertTrue(capturedRequest(boardHttp).timeout().isPresent(),
                "a request/response call must be bounded");

        HttpClient streamHttp = clientStreaming(200, "text/event-stream", new ByteArrayInputStream(new byte[0]));
        controller(streamHttp, 200).stream("TSLA", null, "Bearer t");
        assertTrue(capturedRequest(streamHttp).timeout().isEmpty(),
                "a read deadline on an SSE request kills healthy streams on a timer — heartbeats are ~10s "
                + "but a session lasts hours");
    }
}

package app.feedgateway.stockgex;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import app.feedgateway.liquidityhistory.LiquidityHistoryAuth;

/**
 * The frozen-board endpoints.
 *
 * <p>What is pinned here is what makes them USEFUL AFTER HOURS rather than merely present: the same
 * bearer gate as every other data endpoint, verbatim passthrough of a vocabulary the page branches on
 * ({@code 503 CLOSE_BOARD_UNAVAILABLE} means "not deployed", {@code 404 NO_CLOSE_BOARD} means "that
 * session or symbol was never published"), {@code no-store} so a frozen day cannot be replayed
 * tomorrow as the latest one, and the fact that a frozen read is NOT counted as a board served.
 */
class StockGexCloseBoardTest {

    private static final String FROZEN_JSON =
            "{\"symbol\":\"TSLA\",\"state\":\"closed\",\"closingSession\":\"2026-08-11\"}";
    private static final String BASE = "http://stock-gex-service:8021";

    // ------------------------------------------------------------------ helpers

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static HttpClient clientReturning(int status, String contentType, String body,
                                              String retryAfter) throws Exception {
        HttpClient http = mock(HttpClient.class);
        HttpResponse resp = mock(HttpResponse.class);
        when(resp.statusCode()).thenReturn(status);
        Map<String, List<String>> raw = retryAfter == null
                ? Map.of("content-type", List.of(contentType))
                : Map.of("content-type", List.of(contentType), "retry-after", List.of(retryAfter));
        when(resp.headers()).thenReturn(java.net.http.HttpHeaders.of(raw, (k, v) -> true));
        when(resp.body()).thenReturn(new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8)));
        when(http.send(any(HttpRequest.class), any())).thenReturn(resp);
        return http;
    }

    private static HttpClient clientReturning(int status, String body) throws Exception {
        return clientReturning(status, "application/json", body, null);
    }

    private static HttpRequest capturedRequest(HttpClient http) throws Exception {
        ArgumentCaptor<HttpRequest> req = ArgumentCaptor.forClass(HttpRequest.class);
        verify(http).send(req.capture(), any());
        return req.getValue();
    }

    private static String bodyText(ResponseEntity<byte[]> res) {
        return new String(res.getBody(), StandardCharsets.UTF_8);
    }

    private static LiquidityHistoryAuth authReturning(int status) {
        LiquidityHistoryAuth auth = mock(LiquidityHistoryAuth.class);
        when(auth.authenticate(any())).thenReturn(new LiquidityHistoryAuth.Result(status, null));
        return auth;
    }

    private StockGexController controller(HttpClient http, int authStatus) {
        StockGexUpstream upstream = new StockGexUpstream(BASE, Duration.ofSeconds(5), http);
        return new StockGexController(upstream, authReturning(authStatus), new ObjectMapper(),
                StockGexController.MAX_CONCURRENT_STREAMS,
                (request, listener) -> { /* no async work on these endpoints */ });
    }

    // ------------------------------------------------------------------ close-board

    @Test
    void theFrozenBoardIsServedVerbatim() throws Exception {
        HttpClient http = clientReturning(200, FROZEN_JSON);
        ResponseEntity<byte[]> res = controller(http, 200).closeBoard("TSLA", null, null, "Bearer t");

        assertEquals(200, res.getStatusCode().value());
        assertEquals(FROZEN_JSON, bodyText(res));
        assertEquals(MediaType.APPLICATION_JSON, res.getHeaders().getContentType());
        assertEquals(BASE + "/api/stock-gex/close-board?symbol=TSLA",
                capturedRequest(http).uri().toString());
    }

    @Test
    void byExpiryAndSessionAreForwardedVerbatimAndOnlyWhenPresent() throws Exception {
        HttpClient both = clientReturning(200, FROZEN_JSON);
        controller(both, 200).closeBoard("TSLA", "true", "2026-08-11", "Bearer t");
        assertEquals(BASE + "/api/stock-gex/close-board?symbol=TSLA&byExpiry=true"
                        + "&session=2026-08-11",
                capturedRequest(both).uri().toString());

        // The service owns both contracts. An odd value is encoded and passed on, never judged here
        // — a gateway that re-judged it would be a second, silently disagreeing authority.
        HttpClient odd = clientReturning(200, FROZEN_JSON);
        controller(odd, 200).closeBoard("TSLA", "yes", "last friday", "Bearer t");
        assertEquals(BASE + "/api/stock-gex/close-board?symbol=TSLA&byExpiry=yes"
                        + "&session=last+friday",
                capturedRequest(odd).uri().toString());

        HttpClient blank = clientReturning(200, FROZEN_JSON);
        controller(blank, 200).closeBoard("TSLA", "  ", "  ", "Bearer t");
        assertEquals(BASE + "/api/stock-gex/close-board?symbol=TSLA",
                capturedRequest(blank).uri().toString(), "blank values are not sent at all");
    }

    @Test
    void theSymbolIsEncodedAndOtherwiseUntouched() throws Exception {
        HttpClient http = clientReturning(400, "{\"error\":\"BAD_SYMBOL\"}");
        controller(http, 200).closeBoard(" ts la&x=1 ", null, null, "Bearer t");
        assertEquals(BASE + "/api/stock-gex/close-board?symbol=+ts+la%26x%3D1+",
                capturedRequest(http).uri().toString());
    }

    @Test
    void theUpstreamVocabularyReachesThePageIntact() throws Exception {
        // These three mean different things to the page: not deployed, nothing for that
        // symbol/session, and a corrupt file. Folding any of them into a 500 would leave the page
        // unable to tell "this will never work" from "try another day".
        for (Object[] c : new Object[][] {
                {503, "{\"error\":\"CLOSE_BOARD_UNAVAILABLE\"}"},
                {404, "{\"error\":\"NO_CLOSE_BOARD\"}"},
                {500, "{\"error\":\"CLOSE_BOARD_CORRUPT\"}"}}) {
            HttpClient http = clientReturning((int) c[0], (String) c[1]);
            ResponseEntity<byte[]> res =
                    controller(http, 200).closeBoard("TSLA", null, null, "Bearer t");
            assertEquals(c[0], res.getStatusCode().value());
            assertEquals(c[1], bodyText(res));
        }
    }

    @Test
    void aFrozenBoardIsNeverCached() throws Exception {
        // It names its own session in the body, but nothing on the path reads that. A cached copy
        // would be handed back tomorrow as though it were the newest published session.
        HttpClient http = clientReturning(200, FROZEN_JSON);
        ResponseEntity<byte[]> res = controller(http, 200).closeBoard("TSLA", null, null, "Bearer t");
        assertEquals("no-store", res.getHeaders().getFirst(HttpHeaders.CACHE_CONTROL));
    }

    @Test
    void retryAfterIsCarriedThrough() throws Exception {
        HttpClient http = clientReturning(503, "application/json",
                "{\"error\":\"CLOSE_BOARD_UNAVAILABLE\"}", "30");
        ResponseEntity<byte[]> res = controller(http, 200).closeBoard("TSLA", null, null, "Bearer t");
        assertEquals("30", res.getHeaders().getFirst(HttpHeaders.RETRY_AFTER));
    }

    @Test
    void anUnreachableServiceBecomesAGatewayAuthored502() throws Exception {
        HttpClient http = mock(HttpClient.class);
        when(http.send(any(HttpRequest.class), any())).thenThrow(new IOException("no route"));
        ResponseEntity<byte[]> res = controller(http, 200).closeBoard("TSLA", null, null, "Bearer t");
        assertEquals(502, res.getStatusCode().value());
        assertTrue(bodyText(res).contains(StockGexUpstream.CODE_UNAVAILABLE));
    }

    @Test
    void aFrozenReadIsNotCountedAsABoardServed() throws Exception {
        // BOARDS_SERVED is an existing operational number meaning "live boards handed to a page".
        // Counting after-hours reads into it would quietly change what an operator is reading.
        long before = StockGexController.BOARDS_SERVED.get();
        controller(clientReturning(200, FROZEN_JSON), 200)
                .closeBoard("TSLA", null, null, "Bearer t");
        assertEquals(before, StockGexController.BOARDS_SERVED.get());
    }

    // ------------------------------------------------------------------ auth

    @Test
    void bothEndpointsAreBehindTheSameBearerGateAndCallNothingWhenRefused() throws Exception {
        for (int status : new int[] {401, 403}) {
            HttpClient http = mock(HttpClient.class);
            assertEquals(status, controller(http, status)
                    .closeBoard("TSLA", null, null, null).getStatusCode().value());
            assertEquals(status, controller(http, status)
                    .closeSessions(null).getStatusCode().value());
            verifyNoInteractions(http);
        }
    }

    @Test
    void anAbsurdlyLongSymbolIsRefusedBeforeTheServiceIsCalled() throws Exception {
        HttpClient http = mock(HttpClient.class);
        String tooLong = "T".repeat(StockGexUpstream.MAX_SYMBOL_LENGTH + 1);
        ResponseEntity<byte[]> res =
                controller(http, 200).closeBoard(tooLong, null, null, "Bearer t");
        assertEquals(400, res.getStatusCode().value());
        assertTrue(bodyText(res).contains("BAD_SYMBOL"));
        verifyNoInteractions(http);
    }

    // ------------------------------------------------------------------ close-sessions

    @Test
    void theSessionListIsFetchedWithNoQueryStringAtAll() throws Exception {
        HttpClient http = clientReturning(200, "{\"sessions\":[\"2026-08-11\"]}");
        ResponseEntity<byte[]> res = controller(http, 200).closeSessions("Bearer t");

        assertEquals(200, res.getStatusCode().value());
        assertEquals("{\"sessions\":[\"2026-08-11\"]}", bodyText(res));
        // Not "?symbol=" — this endpoint takes no parameters, and sending an empty one would ask
        // the service a question it does not have.
        assertEquals(BASE + "/api/stock-gex/close-sessions",
                capturedRequest(http).uri().toString());
        assertNull(capturedRequest(http).uri().getQuery());
    }

    @Test
    void theSessionListIsAlsoNeverCached() throws Exception {
        HttpClient http = clientReturning(200, "{\"sessions\":[]}");
        ResponseEntity<byte[]> res = controller(http, 200).closeSessions("Bearer t");
        assertEquals("no-store", res.getHeaders().getFirst(HttpHeaders.CACHE_CONTROL));
    }

    // ------------------------------------------------------------------ regression

    @Test
    void theLiveBoardUrlIsUnchangedByTheSharedUriBuilder() throws Exception {
        // The three endpoints now share one query builder. This is the guard that the shared helper
        // did not narrow what the LIVE board sends: symbol always present, byExpiry only when set.
        HttpClient plain = clientReturning(200, "{}");
        controller(plain, 200).board("TSLA", null, "Bearer t");
        assertEquals(BASE + "/api/stock-gex/board?symbol=TSLA",
                capturedRequest(plain).uri().toString());

        HttpClient empty = clientReturning(400, "{\"error\":\"BAD_SYMBOL\"}");
        controller(empty, 200).board(null, null, "Bearer t");
        assertEquals(BASE + "/api/stock-gex/board?symbol=",
                capturedRequest(empty).uri().toString(),
                "an absent symbol is still SENT empty: the service owns BAD_SYMBOL");
    }

    @Test
    void aFrozenBoardOverTheByteCapIsAProtocolErrorNotATruncatedBody() throws Exception {
        String huge = "{\"pad\":\"" + "x".repeat(StockGexUpstream.MAX_BOARD_BYTES + 1) + "\"}";
        HttpClient http = clientReturning(200, huge);
        ResponseEntity<byte[]> res = controller(http, 200).closeBoard("TSLA", null, null, "Bearer t");
        assertEquals(502, res.getStatusCode().value());
        assertTrue(bodyText(res).contains(StockGexUpstream.CODE_PROTOCOL),
                "a prefix of a body wearing the upstream's 200 is worse than an honest error");
    }

    @Test
    void optionalHeadersThatAreAbsentAreNotInvented() throws Exception {
        HttpClient http = clientReturning(200, FROZEN_JSON);
        ResponseEntity<byte[]> res = controller(http, 200).closeBoard("TSLA", null, null, "Bearer t");
        assertNull(res.getHeaders().getFirst(HttpHeaders.RETRY_AFTER));
        assertEquals(Optional.empty(), Optional.ofNullable(
                res.getHeaders().getFirst(HttpHeaders.CONTENT_ENCODING)));
    }
}

package app.feedgateway.stockgex;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.feedgateway.liquidityhistory.LiquidityHistoryAuth;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.ByteArrayInputStream;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * The endpoints driven through the real MVC dispatch — argument binding, the {@code @ExceptionHandler},
 * status, headers and body — rather than by calling the handler methods directly.
 *
 * <p>This exists because {@code StockGexAuthAllowlistRegressionTest} asserts the auth invariant by
 * READING THE SOURCE, and a source-text assertion stays green through any refactor that keeps the words
 * and loses the behaviour (and can fail on a comment). The invariant that matters is behavioural: an
 * unauthenticated request is answered 401/403 and the upstream is never called. That is asserted here,
 * over a dispatch that also proves the handler is reachable, its parameters resolve, and the refusal
 * path produces a real response instead of an exception escaping to a 500 page.
 */
class StockGexMvcTest {

    private static LiquidityHistoryAuth authReturning(int status) {
        LiquidityHistoryAuth auth = mock(LiquidityHistoryAuth.class);
        when(auth.authenticate(any())).thenReturn(new LiquidityHistoryAuth.Result(status, "tester"));
        return auth;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static HttpClient clientReturning(int status, String contentType, String body) throws Exception {
        HttpClient http = mock(HttpClient.class);
        HttpResponse resp = mock(HttpResponse.class);
        when(resp.statusCode()).thenReturn(status);
        when(resp.headers()).thenReturn(HttpHeaders.of(
                Map.of("content-type", List.of(contentType)), (k, v) -> true));
        when(resp.body()).thenReturn(new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8)));
        when(http.send(any(HttpRequest.class), any())).thenReturn(resp);
        return http;
    }

    private static MockMvc mvc(HttpClient http, int authStatus, int maxStreams) {
        StockGexController controller = new StockGexController(
                new StockGexUpstream("http://stock-gex-service:8021", Duration.ofSeconds(5), http),
                authReturning(authStatus), new ObjectMapper(), maxStreams,
                (request, cleanup) -> { /* no async lifecycle under MockMvc's synchronous dispatch */ });
        return MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void anUnauthenticatedRequestIsRefusedOnBOTHENDPOINTSBeforeTheUpstreamIsTouched() throws Exception {
        for (int status : new int[]{401, 403}) {
            HttpClient http = mock(HttpClient.class);
            MockMvc mvc = mvc(http, status, 4);

            mvc.perform(get("/api/stock-gex/board").param("symbol", "TSLA"))
                    .andExpect(status().is(status));
            mvc.perform(get("/api/stock-gex/stream").param("symbol", "TSLA"))
                    .andExpect(status().is(status));

            verifyNoInteractions(http);
        }
    }

    @Test
    void anAuthenticatedBoardRequestIsDispatchedAndCarriedThroughUnchanged() throws Exception {
        String body = "{\"symbol\":\"TSLA\",\"state\":\"live\"}";
        mvc(clientReturning(200, "application/json", body), 200, 4)
                .perform(get("/api/stock-gex/board").param("symbol", "TSLA")
                        .header("Authorization", "Bearer t"))
                .andExpect(status().isOk())
                .andExpect(content().string(body))
                .andExpect(header().string("Cache-Control", "no-store"));
    }

    @Test
    void a422IsDeliveredToTheBrowserAsA422WithItsOwnBody() throws Exception {
        // The single most important status on this path: it is the only thing that tells the page the
        // ticker is outside the OI universe rather than temporarily unavailable.
        String body = "{\"error\":\"OI_SNAPSHOT_UNAVAILABLE\",\"oiAsOfTradeDate\":\"2026-08-08\"}";
        mvc(clientReturning(422, "application/json", body), 200, 4)
                .perform(get("/api/stock-gex/board").param("symbol", "ZZZZ")
                        .header("Authorization", "Bearer t"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value("OI_SNAPSHOT_UNAVAILABLE"))
                .andExpect(jsonPath("$.oiAsOfTradeDate").value("2026-08-08"));
    }

    @Test
    void aStreamRefusedByTheUpstreamComesBackThroughTheExceptionHandlerNotAsA500() throws Exception {
        // The refusal path is an exception in this controller. If it were not handled it would surface
        // as a 500 error page and the page would lose the whole error contract in one step.
        mvc(clientReturning(429, "application/json", "{\"error\":\"MAX_SYMBOLS\",\"limit\":25}"), 200, 4)
                .perform(get("/api/stock-gex/stream").param("symbol", "TSLA")
                        .header("Authorization", "Bearer t"))
                .andExpect(status().isTooManyRequests())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error").value("MAX_SYMBOLS"));
    }

    @Test
    void aStreamBeyondTheCapIsRefusedWithReadableJsonThroughTheSameDispatch() throws Exception {
        HttpClient http = clientReturning(200, "text/event-stream", "id: 1\ndata: {}\n\n");
        MockMvc mvc = mvc(http, 200, 0); // no slots at all: every request is overflow

        mvc.perform(get("/api/stock-gex/stream").param("symbol", "TSLA")
                        .header("Authorization", "Bearer t"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string("Retry-After", "5"))
                .andExpect(jsonPath("$.error").value("SSE_CLIENT_LIMIT"))
                .andExpect(jsonPath("$.detail").exists());

        verifyNoInteractions(http);
    }

    @Test
    void aMissingSymbolIsTheUpstreamsCallToMakeNotAFrameworkErrorPage() throws Exception {
        // The parameter is optional on purpose: the upstream owns BAD_SYMBOL, and a framework-generated
        // 400 with an HTML body would give the page nothing it can read.
        mvc(clientReturning(400, "application/json", "{\"error\":\"BAD_SYMBOL\"}"), 200, 4)
                .perform(get("/api/stock-gex/board").header("Authorization", "Bearer t"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("BAD_SYMBOL"));
    }
}

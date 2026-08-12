package app.feedgateway.contexttape;

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
import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * The endpoint driven through the real MVC dispatch — argument binding, status, headers and body —
 * rather than by calling the handler method directly.
 *
 * <p>This exists because {@code ContextTapeAuthAllowlistRegressionTest} asserts the auth invariant by
 * READING THE SOURCE, and a source-text assertion stays green through any refactor that keeps the
 * words and loses the behaviour (and can fail on a comment). The invariant that matters is
 * behavioural: an unauthenticated request is answered 401/403 and the upstream is never called. That
 * is asserted here, over a dispatch that also proves the handler is reachable, its parameters resolve,
 * and the failure paths produce real responses instead of exceptions escaping to a 500 page.
 */
class ContextTapeMvcTest {

    private static LiquidityHistoryAuth authReturning(int status) {
        LiquidityHistoryAuth auth = mock(LiquidityHistoryAuth.class);
        when(auth.enforcing()).thenReturn(true);
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

    /** Every upstream built here owns reader and closer pools; a green suite must not retain threads. */
    private final java.util.List<ContextTapeUpstream> upstreams = new java.util.ArrayList<>();

    @org.junit.jupiter.api.AfterEach
    void closeEverythingThisTestOpened() {
        upstreams.forEach(ContextTapeUpstream::close);
        upstreams.clear();
    }

    private MockMvc mvc(HttpClient http, LiquidityHistoryAuth auth) {
        ContextTapeUpstream upstream =
                new ContextTapeUpstream("http://context-tape-service:8134", Duration.ofSeconds(5), http);
        upstreams.add(upstream);
        ContextTapeController controller = new ContextTapeController(
                upstream, auth, new ObjectMapper(), Integer.MAX_VALUE);
        return MockMvcBuilders.standaloneSetup(controller).build();
    }

    private MockMvc mvc(HttpClient http, int authStatus) {
        return mvc(http, authReturning(authStatus));
    }

    @Test
    void anUnauthenticatedRequestIsRefusedBeforeTheUpstreamIsTouched() throws Exception {
        for (int status : new int[]{401, 403}) {
            HttpClient http = mock(HttpClient.class);
            mvc(http, status).perform(get("/api/context-tape/session"))
                    .andExpect(status().is(status));
            verifyNoInteractions(http);
        }
    }

    @Test
    void anUnEnforcingGatewayAnswers401ThroughTheSameDispatch() throws Exception {
        // The fail-closed gate (mirrors /api/pin-flow), proven over the real dispatch: with both auth
        // switches off the shared verifier would serve an authenticated "anonymous" — the route must
        // answer 401 instead of serving session data unauthenticated.
        LiquidityHistoryAuth openAuth = mock(LiquidityHistoryAuth.class);
        when(openAuth.enforcing()).thenReturn(false);
        when(openAuth.authenticate(any())).thenReturn(new LiquidityHistoryAuth.Result(200, "anonymous"));
        HttpClient http = mock(HttpClient.class);

        mvc(http, openAuth).perform(get("/api/context-tape/session"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").exists());

        verifyNoInteractions(http);
    }

    @Test
    void anAuthenticatedRequestIsDispatchedAndCarriedThroughUnchanged() throws Exception {
        String body = "{\"schemaVersion\":1,\"service\":\"context-tape\",\"state\":\"live\"}";
        mvc(clientReturning(200, "application/json", body), 200)
                .perform(get("/api/context-tape/session").header("Authorization", "Bearer t"))
                .andExpect(status().isOk())
                .andExpect(content().string(body))
                .andExpect(header().string("Cache-Control", "no-store"));
    }

    @Test
    void theWarming503IsDeliveredToTheBrowserAsA503WithItsOwnBody() throws Exception {
        // The single most important status on this path: it is the only thing that tells the page the
        // backfill is still running rather than the service being broken.
        mvc(clientReturning(503, "application/json", "{\"error\":\"WARMING\"}"), 200)
                .perform(get("/api/context-tape/session").header("Authorization", "Bearer t"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value("WARMING"));
    }

    @Test
    void anUnreachableUpstreamIsA502WithReadableJsonNotA500Page() throws Exception {
        HttpClient http = mock(HttpClient.class);
        when(http.send(any(HttpRequest.class), any()))
                .thenThrow(new IOException("Connection refused to context-tape-service/10.42.0.9:8134"));

        mvc(http, 200).perform(get("/api/context-tape/session").header("Authorization", "Bearer t"))
                .andExpect(status().isBadGateway())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("Retry-After", "5"))
                .andExpect(jsonPath("$.error").value("UPSTREAM_UNAVAILABLE"))
                .andExpect(jsonPath("$.detail").exists());
    }
}

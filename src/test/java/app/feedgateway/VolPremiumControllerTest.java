package app.feedgateway;

import static app.feedgateway.VolPremiumFixtures.FIXTURE_NOW_MS;
import static app.feedgateway.VolPremiumFixtures.Row;
import static app.feedgateway.VolPremiumFixtures.readings;
import static app.feedgateway.VolPremiumFixtures.warnings;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import app.feedgateway.liquidityhistory.LiquidityHistoryAuth;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.bind.annotation.GetMapping;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * {@code GET /api/vol-premium/ivrv} (VP-346): the current session as a machine reads it — the same
 * records, bytes and order a WebSocket replay delivers, behind the same bearer auth as the gateway's
 * other read-only data routes.
 */
class VolPremiumControllerTest {

    /** A real gateway holding a real session, offered in REVERSE so the order served is the store's own. */
    private static FeedGatewayService gatewayHolding(List<Row> observations, List<Row> transitions) {
        FeedGatewayService service = new FeedGatewayService(new GatewaySettings(), new ObjectMapper(),
                new HpsfGatewayViewMapper(), null);
        service.volPremiumClockForTest(() -> FIXTURE_NOW_MS);
        VolPremiumSessionStore store = service.volPremiumStoreForTest();
        List<Row> reversed = new ArrayList<>(observations);
        Collections.reverse(reversed);
        long offset = 0;
        for (Row row : reversed) {
            assertTrue(store.acceptObservation(FeedGatewayService.VOL_PREMIUM_SOURCE, row.key(), 0, offset++,
                    row.json(), FIXTURE_NOW_MS).admitted(), row.key());
        }
        for (Row row : transitions) {
            assertTrue(store.acceptWarning(FeedGatewayService.VOL_PREMIUM_SOURCE, row.key(), 0, offset++,
                    row.json(), FIXTURE_NOW_MS).admitted(), row.key());
        }
        return service;
    }

    private static LiquidityHistoryAuth auth(int status) {
        LiquidityHistoryAuth auth = mock(LiquidityHistoryAuth.class);
        when(auth.authenticate(any())).thenReturn(new LiquidityHistoryAuth.Result(status, "tester"));
        return auth;
    }

    private static MockHttpServletResponse get(VolPremiumController controller, String symbol,
                                               String authorization) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        controller.ivrv(symbol, authorization, response);
        return response;
    }

    private static List<String> json(List<Row> rows) {
        return rows.stream().map(Row::json).toList();
    }

    @Test
    void servesTheCurrentSessionVerbatimInReplayOrder() throws Exception {
        List<Row> observations = readings().subList(295, 305);
        List<Row> transitions = warnings().subList(0, 3);
        VolPremiumController controller = new VolPremiumController(gatewayHolding(observations, transitions),
                auth(200));

        MockHttpServletResponse response = get(controller, "SPX", "Bearer t");

        assertEquals(200, response.getStatus());
        assertTrue(response.getContentType().startsWith("application/json"), response.getContentType());
        List<Row> byEpisode = new ArrayList<>(transitions);   // all three open at ordinal 7141
        byEpisode.sort(Comparator.comparing(Row::key));
        // EXACT bytes: the producer's records, unreshaped, in (frameSeq, epoch) then (frameSeq, episodeId)
        // order — nothing coalesced, nothing recomputed.
        String expected = "{\"symbol\":\"SPX\",\"sessionDate\":\"2026-08-27\",\"observations\":["
                + String.join(",", json(observations)) + "],\"warnings\":["
                + String.join(",", json(byEpisode)) + "]}";
        assertEquals(expected, response.getContentAsString(StandardCharsets.UTF_8));
        JsonNode parsed = new ObjectMapper().readTree(response.getContentAsString(StandardCharsets.UTF_8));
        assertEquals(10, parsed.get("observations").size(), "and it is JSON a machine can read");
        assertEquals(3, parsed.get("warnings").size());
    }

    @Test
    void anUnauthenticatedCallerIs401AndNothingIsRead() throws Exception {
        FeedGatewayService service = mock(FeedGatewayService.class);
        MockHttpServletResponse response = get(new VolPremiumController(service, auth(401)), "SPX", null);
        assertEquals(401, response.getStatus());
        assertEquals("", response.getContentAsString());
        verify(service, never()).volPremiumSession(any());
    }

    @Test
    void anUnentitledCallerIs403AndNothingIsRead() throws Exception {
        FeedGatewayService service = mock(FeedGatewayService.class);
        assertEquals(403, get(new VolPremiumController(service, auth(403)), "SPX", "Bearer t").getStatus());
        verify(service, never()).volPremiumSession(any());
    }

    @Test
    void aSymbolWithNoSessionIsA200WithANullSessionAndEmptyArrays() throws Exception {
        // A cold start must read as "nothing yet", distinguishable from a routing mistake or an error.
        VolPremiumController controller = new VolPremiumController(
                gatewayHolding(readings().subList(0, 2), List.of()), auth(200));
        MockHttpServletResponse response = get(controller, "NDX", "Bearer t");
        assertEquals(200, response.getStatus());
        assertEquals("{\"symbol\":\"NDX\",\"sessionDate\":null,\"observations\":[],\"warnings\":[]}",
                response.getContentAsString(StandardCharsets.UTF_8));
    }

    @Test
    void aSymbolThatCannotNameASeriesIs400AndIsNeverLookedUp() throws Exception {
        // The symbol is echoed into the response, so anything outside the contract's bound and a safe
        // character set is refused rather than escaped — it could not name a held series anyway.
        FeedGatewayService service = mock(FeedGatewayService.class);
        VolPremiumController controller = new VolPremiumController(service, auth(200));
        for (String bad : new String[] {"SPX\"}", "", "S".repeat(17), "SP X", "SPX\n"}) {
            MockHttpServletResponse response = get(controller, bad, "Bearer t");
            assertEquals(400, response.getStatus(), "symbol '" + bad + "'");
            assertTrue(response.getContentAsString().contains("\"error\""));
        }
        verify(service, never()).volPremiumSession(any());
    }

    @Test
    void atMostFourResponsesStreamAtOnceAndTheRestAre503WithRetryAfter() throws Exception {
        FeedGatewayService service = mock(FeedGatewayService.class);
        CountDownLatch entered = new CountDownLatch(VolPremiumController.MAX_CONCURRENT_RESPONSES);
        CountDownLatch release = new CountDownLatch(1);
        when(service.volPremiumSession(any())).thenAnswer(inv -> {
            entered.countDown();
            release.await(5, TimeUnit.SECONDS);
            return new VolPremiumSessionStore.Snapshot(null, List.of(), List.of());
        });
        VolPremiumController controller = new VolPremiumController(service, auth(200));
        ExecutorService pool = Executors.newFixedThreadPool(VolPremiumController.MAX_CONCURRENT_RESPONSES);
        try {
            List<Future<MockHttpServletResponse>> held = new ArrayList<>();
            for (int i = 0; i < VolPremiumController.MAX_CONCURRENT_RESPONSES; i++) {
                held.add(pool.submit(() -> get(controller, "SPX", "Bearer t")));
            }
            assertTrue(entered.await(5, TimeUnit.SECONDS), "four responses are being written");

            MockHttpServletResponse fifth = get(controller, "SPX", "Bearer t");
            assertEquals(503, fifth.getStatus());
            assertEquals("1", fifth.getHeader("Retry-After"));

            release.countDown();
            for (Future<MockHttpServletResponse> f : held) {
                assertEquals(200, f.get(5, TimeUnit.SECONDS).getStatus());
            }
            assertEquals(200, get(controller, "SPX", "Bearer t").getStatus(), "the permits came back");
        } finally {
            release.countDown();
            pool.shutdownNow();
        }
    }

    @Test
    void theRouteIsTheOneTheWebRepoCalls() throws Exception {
        GetMapping mapping = VolPremiumController.class.getMethod("ivrv", String.class, String.class,
                jakarta.servlet.http.HttpServletResponse.class).getAnnotation(GetMapping.class);
        assertArrayEquals(new String[] {"/api/vol-premium/ivrv"}, mapping.value());
    }
}

package app.feedgateway;

import static app.feedgateway.VolPremiumFixtures.FIXTURE_NOW_MS;
import static app.feedgateway.VolPremiumFixtures.Row;
import static app.feedgateway.VolPremiumFixtures.readings;
import static app.feedgateway.VolPremiumFixtures.warnings;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
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

    private static String retention(boolean complete, long refusedForBudget, long refusedForDisk, long retained,
                                    long budget) {
        return "\"retention\":{\"complete\":" + complete + ",\"refusedForBudget\":" + refusedForBudget
                + ",\"refusedForDisk\":" + refusedForDisk + ",\"retainedBytes\":" + retained + ",\"budgetBytes\":"
                + budget + "}}";
    }

    @Test
    void servesTheCurrentSessionVerbatimInReplayOrder() throws Exception {
        List<Row> observations = readings().subList(295, 305);
        List<Row> transitions = warnings().subList(0, 3);
        FeedGatewayService controllerService = gatewayHolding(observations, transitions);
        VolPremiumController controller = new VolPremiumController(controllerService, auth(200));

        MockHttpServletResponse response = get(controller, "SPX", "Bearer t");

        assertEquals(200, response.getStatus());
        assertTrue(response.getContentType().startsWith("application/json"), response.getContentType());
        List<Row> byEpisode = new ArrayList<>(transitions);   // all three open at ordinal 7141
        byEpisode.sort(Comparator.comparing(Row::key));
        // EXACT bytes: the producer's records, read back from the store's disk log unreshaped, in (frameSeq, epoch)
        // then (frameSeq, episodeId) order — nothing coalesced, nothing recomputed.
        long retained = controllerService.volPremiumSession("SPX").retainedBytes();
        long expectedBytes = 0;
        for (Row row : observations) {
            expectedBytes += row.json().getBytes(StandardCharsets.UTF_8).length;
        }
        for (Row row : transitions) {
            expectedBytes += row.json().getBytes(StandardCharsets.UTF_8).length;
        }
        assertEquals(expectedBytes, retained, "the live bytes are the records' own bytes");
        String expected = "{\"symbol\":\"SPX\",\"sessionDate\":\"2026-08-27\",\"observations\":["
                + String.join(",", json(observations)) + "],\"warnings\":["
                + String.join(",", json(byEpisode)) + "],"
                + retention(true, 0, 0, retained, VolPremiumSessionStore.SERIES_DISK_BUDGET_BYTES);
        assertEquals(expected, response.getContentAsString(StandardCharsets.UTF_8));
        JsonNode parsed = new ObjectMapper().readTree(response.getContentAsString(StandardCharsets.UTF_8));
        assertEquals(10, parsed.get("observations").size(), "and it is JSON a machine can read");
        assertEquals(3, parsed.get("warnings").size());
    }

    @Test
    void aSessionLargerThanOnePageChunkIsStreamedWholeAndInOrder() throws Exception {
        // A whole regular session is ~26 MB, a hundred of the store's read chunks: the page is every record, in
        // order, verbatim, however many chunks it takes.
        List<Row> session = VolPremiumFixtures.referenceSession();
        long nowMs = VolPremiumFixtures.longField(session.get(session.size() - 1).json(), "eventTimeMs") + 1_000L;
        FeedGatewayService service = new FeedGatewayService(new GatewaySettings(), new ObjectMapper(),
                new HpsfGatewayViewMapper(), null);
        service.volPremiumClockForTest(() -> nowMs);
        VolPremiumSessionStore store = service.volPremiumStoreForTest();
        long offset = 0;
        long bytes = 0;
        for (Row row : session) {
            assertTrue(store.acceptObservation(FeedGatewayService.VOL_PREMIUM_SOURCE, row.key(), 0, offset++,
                    row.json(), nowMs).admitted(), row.key());
            bytes += row.json().getBytes(StandardCharsets.UTF_8).length;
        }
        assertTrue(bytes > 20L * VolPremiumSessionStore.PAGE_CHUNK_BYTES, "precondition: many chunks, " + bytes);
        try {
            String body = get(new VolPremiumController(service, auth(200)), "SPX", "Bearer t")
                    .getContentAsString(StandardCharsets.UTF_8);
            assertEquals("{\"symbol\":\"SPX\",\"sessionDate\":\"2026-08-27\",\"observations\":["
                    + String.join(",", json(session)) + "],\"warnings\":[],"
                    + retention(true, 0, 0, bytes, VolPremiumSessionStore.SERIES_DISK_BUDGET_BYTES), body);
        } finally {
            store.close();
        }
    }

    @Test
    void servesAV1OnlyAndAMixedSessionVerbatimInReplayOrder() throws Exception {
        // Through the rollout the page may hold either wire version, each record exactly as its producer wrote it.
        List<Row> v1 = new ArrayList<>();
        for (long seq = 7141; seq < 7146; seq++) {
            v1.add(VolPremiumFixtures.v1At(seq));
        }
        FeedGatewayService v1Only = gatewayHolding(v1, List.of());
        MockHttpServletResponse response = get(new VolPremiumController(v1Only, auth(200)), "SPX", "Bearer t");
        assertEquals(200, response.getStatus());
        assertEquals("{\"symbol\":\"SPX\",\"sessionDate\":\"2026-08-27\",\"observations\":["
                + String.join(",", json(v1)) + "],\"warnings\":[],"
                + retention(true, 0, 0, v1Only.volPremiumSession("SPX").retainedBytes(),
                        VolPremiumSessionStore.SERIES_DISK_BUDGET_BYTES), response.getContentAsString(StandardCharsets.UTF_8));

        // Mixed: the v1 run, then the engine from an ordinal the v1 run also holds — both points, v1's epoch first.
        List<Row> mixed = new ArrayList<>(v1);
        for (long seq = 7145; seq < 7148; seq++) {
            mixed.add(VolPremiumFixtures.readingAt(seq));
        }
        Row warning = warnings().get(0);
        FeedGatewayService service = gatewayHolding(mixed, List.of(warning));
        String body = get(new VolPremiumController(service, auth(200)), "SPX", "Bearer t")
                .getContentAsString(StandardCharsets.UTF_8);
        assertEquals("{\"symbol\":\"SPX\",\"sessionDate\":\"2026-08-27\",\"observations\":["
                + String.join(",", json(mixed)) + "],\"warnings\":[" + warning.json() + "],"
                + retention(true, 0, 0, service.volPremiumSession("SPX").retainedBytes(),
                        VolPremiumSessionStore.SERIES_DISK_BUDGET_BYTES), body);
        JsonNode page = new ObjectMapper().readTree(body);
        List<Integer> versions = new ArrayList<>();
        page.get("observations").forEach(o -> versions.add(o.get("schemaVersion").intValue()));
        assertEquals(List.of(1, 1, 1, 1, 1, 2, 2, 2), versions, "a reader tells them apart by each record's own version");
    }

    @Test
    void anUnauthenticatedCallerIs401AndNothingIsRead() throws Exception {
        FeedGatewayService service = mock(FeedGatewayService.class);
        MockHttpServletResponse response = get(new VolPremiumController(service, auth(401)), "SPX", null);
        assertEquals(401, response.getStatus());
        assertEquals("", response.getContentAsString());
        verify(service, never()).volPremiumPage(any());
    }

    @Test
    void anUnentitledCallerIs403AndNothingIsRead() throws Exception {
        FeedGatewayService service = mock(FeedGatewayService.class);
        assertEquals(403, get(new VolPremiumController(service, auth(403)), "SPX", "Bearer t").getStatus());
        verify(service, never()).volPremiumPage(any());
    }

    @Test
    void aSymbolWithNoSessionIsA200WithANullSessionAndEmptyArrays() throws Exception {
        // A cold start must read as "nothing yet", distinguishable from a routing mistake or an error.
        VolPremiumController controller = new VolPremiumController(
                gatewayHolding(readings().subList(0, 2), List.of()), auth(200));
        MockHttpServletResponse response = get(controller, "NDX", "Bearer t");
        assertEquals(200, response.getStatus());
        assertEquals("{\"symbol\":\"NDX\",\"sessionDate\":null,\"observations\":[],\"warnings\":[],"
                        + retention(true, 0, 0, 0, VolPremiumSessionStore.SERIES_DISK_BUDGET_BYTES),
                response.getContentAsString(StandardCharsets.UTF_8));
    }

    @Test
    void aSessionPastItsBudgetSaysSoOnThePageItServes() throws Exception {
        // A machine must never read a held prefix as the whole session (VolPremiumSessionStore's
        // SERIES_DISK_BUDGET_BYTES): the page carries the store's own verdict.
        FeedGatewayService service = mock(FeedGatewayService.class);
        String reading = readings().get(0).json();
        when(service.volPremiumPage(any())).thenReturn(VolPremiumSessionStore.Page.of(new VolPremiumSessionStore.Snapshot(
                "2026-08-27", List.of(reading), List.of(), false, 3L, 0L, 7_000L, 8_000L)));
        MockHttpServletResponse response = get(new VolPremiumController(service, auth(200)), "SPX", "Bearer t");
        assertEquals(200, response.getStatus());
        String body = response.getContentAsString(StandardCharsets.UTF_8);
        assertTrue(body.endsWith("],\"warnings\":[]," + retention(false, 3, 0, 7_000, 8_000)), body);
        assertEquals(false, new ObjectMapper().readTree(body).get("retention").get("complete").asBoolean(true));
    }

    @Test
    void aSessionWhoseDiskLogFailedSaysSoOnThePageItServes() throws Exception {
        // A failed log (the store's DISK_FAILURE) is the other way a session stops being whole: the page says how
        // many records it refused for it, and that the session is incomplete.
        FeedGatewayService service = mock(FeedGatewayService.class);
        when(service.volPremiumPage(any())).thenReturn(VolPremiumSessionStore.Page.of(new VolPremiumSessionStore.Snapshot(
                "2026-08-27", List.of(), List.of(), false, 0L, 2L, 0L, 8_000L)));
        String body = get(new VolPremiumController(service, auth(200)), "SPX", "Bearer t")
                .getContentAsString(StandardCharsets.UTF_8);
        assertTrue(body.endsWith("\"warnings\":[]," + retention(false, 0, 2, 0, 8_000)), body);
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
        verify(service, never()).volPremiumPage(any());
    }

    @Test
    void atMostFourResponsesStreamAtOnceAndTheRestAre503WithRetryAfter() throws Exception {
        FeedGatewayService service = mock(FeedGatewayService.class);
        CountDownLatch entered = new CountDownLatch(VolPremiumController.MAX_CONCURRENT_RESPONSES);
        CountDownLatch release = new CountDownLatch(1);
        when(service.volPremiumPage(any())).thenAnswer(inv -> {
            entered.countDown();
            release.await(5, TimeUnit.SECONDS);
            return VolPremiumSessionStore.Page.of(new VolPremiumSessionStore.Snapshot(null, List.of(), List.of()));
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
    void aResponseWriteFailureStillReleasesItsPermit() throws Exception {
        // A client that disconnects mid-page makes the write throw. The permit must come back anyway:
        // four such failures would otherwise exhaust the endpoint for good, and every later caller would
        // be told 503 with nothing actually being written.
        FeedGatewayService service = mock(FeedGatewayService.class);
        when(service.volPremiumPage(any())).thenAnswer(inv -> VolPremiumSessionStore.Page.of(
                new VolPremiumSessionStore.Snapshot("2026-08-27", List.of(readings().get(0).json()), List.of())));
        VolPremiumController controller = new VolPremiumController(service, auth(200));
        for (int i = 0; i < VolPremiumController.MAX_CONCURRENT_RESPONSES + 2; i++) {
            MockHttpServletResponse broken = new MockHttpServletResponse() {
                @Override
                public jakarta.servlet.ServletOutputStream getOutputStream() {
                    return new jakarta.servlet.ServletOutputStream() {
                        @Override
                        public boolean isReady() {
                            return true;
                        }

                        @Override
                        public void setWriteListener(jakarta.servlet.WriteListener listener) {
                        }

                        @Override
                        public void write(int b) throws java.io.IOException {
                            throw new java.io.IOException("client went away");
                        }

                        @Override
                        public void write(byte[] b, int off, int len) throws java.io.IOException {
                            throw new java.io.IOException("client went away");
                        }
                    };
                }
            };
            assertThrows(java.io.IOException.class, () -> controller.ivrv("SPX", "Bearer t", broken),
                    "the write failure propagates to the container");
        }
        MockHttpServletResponse healthy = get(controller, "SPX", "Bearer t");
        assertEquals(200, healthy.getStatus(), "every permit came back from the failed writes");
        verify(service, org.mockito.Mockito.times(VolPremiumController.MAX_CONCURRENT_RESPONSES + 3))
                .volPremiumPage(any());
    }

    @Test
    void theRouteIsTheOneTheWebRepoCalls() throws Exception {
        GetMapping mapping = VolPremiumController.class.getMethod("ivrv", String.class, String.class,
                jakarta.servlet.http.HttpServletResponse.class).getAnnotation(GetMapping.class);
        assertArrayEquals(new String[] {"/api/vol-premium/ivrv"}, mapping.value());
    }
}

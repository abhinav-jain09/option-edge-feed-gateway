package app.feedgateway;

import static org.junit.jupiter.api.Assertions.*;

import app.feedgateway.liquidityhistory.LiquidityHistoryAuth;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

/** ES-FOOTPRINT-GATEWAY-DESIGN.md G-R7/G-R9/G-R11 (8)(9)(13)(16) — the two backfill routes, without a server. */
class FootprintBackfillControllerTest {

    /** Scripted authenticator: a fixed status. */
    private static java.util.function.Function<String, LiquidityHistoryAuth.Result> auth(int status) {
        return header -> new LiquidityHistoryAuth.Result(status, status == 200 ? "tester" : null);
    }

    private static GatewayController controller(FeedGatewayService service, int authStatus) {
        return new GatewayController(service, null, auth(authStatus));
    }

    private static String body(MockHttpServletResponse r) {
        try { return r.getContentAsString(); } catch (java.io.UnsupportedEncodingException e) { throw new IllegalStateException(e); }
    }

    @Test void flagOffAnswers404WithTheEnabledFalseBodyAndCountsNothing() throws Exception {
        FeedGatewayService s = new FeedGatewayService(new GatewaySettings(), new com.fasterxml.jackson.databind.ObjectMapper(), new HpsfGatewayViewMapper(), null);
        MockHttpServletResponse r = new MockHttpServletResponse();
        controller(s, 401).footprintBars("1m", Long.MAX_VALUE, -1, 100, "", null, r);
        assertEquals(404, r.getStatus()); assertEquals("{\"enabled\":false}", body(r));
        MockHttpServletResponse r2 = new MockHttpServletResponse();
        controller(s, 200).footprintOutcomes("1m", Long.MAX_VALUE, "garbage", 100, "", null, r2);
        assertEquals(404, r2.getStatus(), "the flag precedes authentication and the cursor check");
        assertFalse(s.metrics().contains("gateway_footprint_backfill_requests_total"), "no footprint series flag-off");
    }

    @Test void authenticationFailureIsTheAuthOutcomeCountedAsARequestNotARejection() throws Exception {
        FeedGatewayService s = FootprintWiringTest.on();
        MockHttpServletResponse r = new MockHttpServletResponse();
        controller(s, 401).footprintOutcomes("1m", Long.MAX_VALUE, "bad cursor", 100, "2000-01-01", null, r);
        assertEquals(401, r.getStatus());
        String m = s.footprintMetricsText();
        assertTrue(m.contains("gateway_footprint_backfill_requests_total{route=\"outcomes\"} 1\n"));
        assertTrue(m.contains("gateway_footprint_backfill_rejected_total{route=\"outcomes\",reason=\"bad_cursor\"} 0\n"));
        assertTrue(m.contains("gateway_footprint_backfill_rejected_total{route=\"outcomes\",reason=\"session_mismatch\"} 0\n"));
        MockHttpServletResponse r403 = new MockHttpServletResponse();
        controller(s, 403).footprintBars("1m", 1, -1, 1, "", null, r403);
        assertEquals(403, r403.getStatus());
    }

    @Test void barsPageIsStreamedWithTheEnvelopeAndClampedLimit() throws Exception {
        FeedGatewayService s = FootprintWiringTest.on();
        for (long start = 1000; start <= 5000; start += 1000) s.admitFootprintRecord("es-footprint-bar", FootprintViewsTest.bar("2026-08-14", "30s", start), "cache");
        MockHttpServletResponse r = new MockHttpServletResponse();
        controller(s, 200).footprintBars("30s", 5000, -1, 2, "", "Bearer x", r);
        assertEquals(200, r.getStatus());
        assertEquals("application/json", r.getContentType());
        String b = body(r);
        assertTrue(b.startsWith("{\"sessionDate\":\"2026-08-14\",\"bars\":[{"), b);
        assertTrue(b.endsWith("],\"nextCursor\":2000}"), b);
        assertEquals(2, b.split("\"barStartMs\"").length - 1);
        MockHttpServletResponse big = new MockHttpServletResponse();
        controller(s, 200).footprintBars("30s", 5000, 2000, 5000, "", "Bearer x", big);
        assertTrue(body(big).endsWith("],\"nextCursor\":null}"), "a short page ends pagination; limit 5000 was clamped to 100");
        assertEquals(3, body(big).split("\"barStartMs\"").length - 1);
        assertEquals(100, GatewayController.clamp(5000)); assertEquals(1, GatewayController.clamp(0)); assertEquals(63, GatewayController.clamp(63));
        assertEquals(4, s.footprintBackfillPermits().availablePermits(), "permits are released after the write");
        assertTrue(s.footprintMetricsText().contains("gateway_footprint_backfill_requests_total{route=\"bars\"} 2\n"));
    }

    @Test void sessionMismatchIsA200WithTheFlagAndCountsOnce() throws Exception {
        FeedGatewayService s = FootprintWiringTest.on();
        s.admitFootprintRecord("es-footprint-bar", FootprintViewsTest.bar("2026-08-17", "30s", 9000), "cache");
        MockHttpServletResponse r = new MockHttpServletResponse();
        controller(s, 200).footprintBars("30s", Long.MAX_VALUE, -1, 100, "2026-08-14", null, r);
        assertEquals(200, r.getStatus());
        assertEquals("{\"sessionDate\":\"2026-08-17\",\"sessionMismatch\":true,\"bars\":[],\"nextCursor\":null}", body(r));
        assertTrue(s.footprintMetricsText().contains("gateway_footprint_backfill_rejected_total{route=\"bars\",reason=\"session_mismatch\"} 1\n"));
    }

    @Test void outcomesRouteValidatesTheCursorBeforeTheSessionCheck() throws Exception {
        FeedGatewayService s = FootprintWiringTest.on();
        s.admitFootprintRecord("es-footprint-outcome", FootprintViewsTest.outcome("2026-08-17", "1m", 10, "ES.v.0|1m|1|BUYERS|LEVEL|1|1"), "cache");
        MockHttpServletResponse r = new MockHttpServletResponse();
        controller(s, 200).footprintOutcomes("1m", Long.MAX_VALUE, "5m|0000000000000000010|x", 100, "2026-08-14", null, r);
        assertEquals(400, r.getStatus()); assertEquals("{\"error\":\"bad cursor\"}", body(r));
        String m = s.footprintMetricsText();
        assertTrue(m.contains("gateway_footprint_backfill_rejected_total{route=\"outcomes\",reason=\"bad_cursor\"} 1\n"));
        assertTrue(m.contains("gateway_footprint_backfill_rejected_total{route=\"outcomes\",reason=\"session_mismatch\"} 0\n"), "bad cursor wins over session mismatch");
        MockHttpServletResponse ok = new MockHttpServletResponse();
        controller(s, 200).footprintOutcomes("1m", Long.MAX_VALUE, "", 100, "2026-08-17", null, ok);
        assertEquals(200, ok.getStatus());
        assertTrue(body(ok).startsWith("{\"sessionDate\":\"2026-08-17\",\"outcomes\":[{"));
        assertTrue(body(ok).endsWith("],\"nextCursor\":null}"));
        MockHttpServletResponse full = new MockHttpServletResponse();
        controller(s, 200).footprintOutcomes("1m", Long.MAX_VALUE, "", 1, "", null, full);
        assertTrue(body(full).endsWith("\"nextCursor\":\"1m|0000000000000000010|ES.v.0|1m|1|BUYERS|LEVEL|1|1\"}"), body(full));
    }

    @Test void busyWinsOverEveryLaterRejectionAndReleasesNothingItDidNotTake() throws Exception {
        FeedGatewayService s = FootprintWiringTest.on();
        assertTrue(s.footprintBackfillPermits().tryAcquire(4));                  // exhaust the permits
        MockHttpServletResponse r = new MockHttpServletResponse();
        controller(s, 200).footprintOutcomes("1m", Long.MAX_VALUE, "bad", 100, "2000-01-01", null, r);
        assertEquals(503, r.getStatus()); assertEquals("1", r.getHeader("Retry-After")); assertEquals("{\"error\":\"busy\"}", body(r));
        String m = s.footprintMetricsText();
        assertTrue(m.contains("gateway_footprint_backfill_rejected_total{route=\"outcomes\",reason=\"busy\"} 1\n"));
        assertTrue(m.contains("gateway_footprint_backfill_rejected_total{route=\"outcomes\",reason=\"bad_cursor\"} 0\n"));
        assertEquals(0, s.footprintBackfillPermits().availablePermits(), "a rejected request never releases a permit it did not take");
        s.footprintBackfillPermits().release(4);
    }

    @Test void thePermitIsReleasedEvenWhenTheClientDisconnectsMidWrite() {
        FeedGatewayService s = FootprintWiringTest.on();
        s.admitFootprintRecord("es-footprint-bar", FootprintViewsTest.bar("2026-08-14", "30s", 1000), "cache");
        MockHttpServletResponse broken = new MockHttpServletResponse() {
            @Override public jakarta.servlet.ServletOutputStream getOutputStream() {
                return new jakarta.servlet.ServletOutputStream() {
                    @Override public boolean isReady() { return true; }
                    @Override public void setWriteListener(jakarta.servlet.WriteListener l) { }
                    @Override public void write(int b) throws java.io.IOException { throw new java.io.IOException("client went away"); }
                };
            }
        };
        assertThrows(java.io.IOException.class, () -> controller(s, 200).footprintBars("30s", 5000, -1, 100, "", null, broken));
        assertEquals(4, s.footprintBackfillPermits().availablePermits());
    }

    @Test void theResponseBufferBoundIsVerifiedNotAssumed() throws Exception {
        // G-R8/CODE round-1 #5: the ONLY buffer between the page bytes and the socket is the
        // container's response buffer. The route requests 64 KiB and then VERIFIES it; a container
        // that reports more refuses the page rather than streaming behind an unbounded buffer.
        FeedGatewayService s = FootprintWiringTest.on();
        s.admitFootprintRecord("es-footprint-bar", FootprintViewsTest.bar("2026-08-14", "30s", 1000), "cache");
        MockHttpServletResponse ok = new MockHttpServletResponse();
        controller(s, 200).footprintBars("30s", 5000, -1, 100, "", null, ok);
        assertEquals(200, ok.getStatus());
        assertEquals(GatewayController.FOOTPRINT_WRITE_BUFFER, ok.getBufferSize(), "the route sets the buffer it proves");
        MockHttpServletResponse unbounded = new MockHttpServletResponse() {
            @Override public void setBufferSize(int size) { /* a container that ignores the request */ }
            @Override public int getBufferSize() { return GatewayController.FOOTPRINT_WRITE_BUFFER * 4; }
        };
        controller(s, 200).footprintBars("30s", 5000, -1, 100, "", null, unbounded);
        assertEquals(503, unbounded.getStatus(), "an unbounded buffer refuses the page");
        assertTrue(body(unbounded).contains("response buffer 262144 exceeds 65536"), body(unbounded));
        assertEquals(4, s.footprintBackfillPermits().availablePermits(), "and still releases its permit");
    }

    @Test void recordsAreWrittenAsTheirOwnAsciiBytesVerbatim() throws Exception {
        FeedGatewayService s = FootprintWiringTest.on();
        String rec = FootprintViewsTest.bar("2026-08-14", "4h", 42, "verbatim-tag_1.0:|-");
        s.admitFootprintRecord("es-footprint-bar", rec, "live");
        MockHttpServletResponse r = new MockHttpServletResponse();
        controller(s, 200).footprintBars("4h", 42, -1, 100, "", null, r);
        byte[] bytes = r.getContentAsByteArray();
        assertEquals(new String(bytes, StandardCharsets.US_ASCII), body(r));
        assertTrue(body(r).contains(rec), "the producer's bytes, untouched");
    }
}

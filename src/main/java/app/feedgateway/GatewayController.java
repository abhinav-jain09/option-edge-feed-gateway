package app.feedgateway;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
public class GatewayController {
    private final FeedGatewayService service;
    private final app.feedgateway.liquidityhistory.LiquidityHistoryStore historyStore;

    /** ES Footprint (G-R7): explicit in-handler authentication, the LiquidityHistoryController convention (a function seam over the final auth bean). */
    private final java.util.function.Function<String, app.feedgateway.liquidityhistory.LiquidityHistoryAuth.Result> footprintAuth;

    // EXPLICIT: the test seam below is a second constructor, and two constructors with neither
    // annotated leave Spring nothing to choose from — it falls back to a no-arg default that does
    // not exist, and the context never refreshes. Named so another seam cannot repeat it.
    @org.springframework.beans.factory.annotation.Autowired
    public GatewayController(FeedGatewayService service,
                             org.springframework.beans.factory.ObjectProvider<
                                     app.feedgateway.liquidityhistory.LiquidityHistoryStore> historyStore,
                             org.springframework.beans.factory.ObjectProvider<
                                     app.feedgateway.liquidityhistory.LiquidityHistoryAuth> auth) {
        this.service = service;
        this.historyStore = historyStore.getIfAvailable();
        app.feedgateway.liquidityhistory.LiquidityHistoryAuth bean = auth.getIfAvailable();
        this.footprintAuth = bean == null ? null : bean::authenticate;
    }

    /** Test seam. */
    GatewayController(FeedGatewayService service, app.feedgateway.liquidityhistory.LiquidityHistoryStore historyStore,
                      java.util.function.Function<String, app.feedgateway.liquidityhistory.LiquidityHistoryAuth.Result> auth) {
        this.service = service;
        this.historyStore = historyStore;
        this.footprintAuth = auth;
    }

    // ---- ES Footprint backfill (ES-FOOTPRINT-GATEWAY-DESIGN.md G-R7) ------------------------------

    /** G-R7: both routes clamp the page to [1, 100]. */
    static final int FOOTPRINT_LIMIT_MAX = 100;
    /** G-R7/G-R8: the ONLY buffer between the page bytes and the socket; VERIFIED, not assumed (CODE round-1 #5). */
    static final int FOOTPRINT_WRITE_BUFFER = 64 * 1024;

    /**
     * {@code GET /api/footprint/bars?tf&toMs&afterMs=-1&limit=100&sessionDate=} — processing order
     * (exactly one outcome per request): Spring typed binding (400 before this handler runs) →
     * flag (404) → explicit authentication → permit (503 busy) → snapshot under the coordinator
     * lock → STREAMED write through one fixed buffer, one record at a time. The lock is never held
     * while writing; the permit is released after the flush.
     */
    @GetMapping(value = "/api/footprint/bars", produces = MediaType.APPLICATION_JSON_VALUE)
    public void footprintBars(@org.springframework.web.bind.annotation.RequestParam("tf") String tf,
                              @org.springframework.web.bind.annotation.RequestParam("toMs") long toMs,
                              @org.springframework.web.bind.annotation.RequestParam(value = "afterMs", defaultValue = "-1") long afterMs,
                              @org.springframework.web.bind.annotation.RequestParam(value = "limit", defaultValue = "100") int limit,
                              @org.springframework.web.bind.annotation.RequestParam(value = "sessionDate", defaultValue = "") String sessionDate,
                              @org.springframework.web.bind.annotation.RequestHeader(value = "Authorization", required = false) String authorization,
                              jakarta.servlet.http.HttpServletResponse response) throws java.io.IOException {
        if (!footprintGate(response, "bars", authorization)) return;
        java.util.concurrent.Semaphore permits = service.footprintBackfillPermits();
        if (!permits.tryAcquire()) { reject(response, "bars", "busy", 503, "{\"error\":\"busy\"}", true); return; }
        try {
            FootprintViews.BarsPage page = service.footprintViews().barsPage(tf, toMs, afterMs, clamp(limit), sessionDate);
            if (page.sessionMismatch()) service.footprintBackfillRejected("bars", "session_mismatch");
            writePage(response, page.sessionDate(), page.sessionMismatch(), "bars", page.records(),
                    page.nextCursor() == null ? "null" : Long.toString(page.nextCursor()));
        } finally {
            permits.release();
        }
    }

    /**
     * {@code GET /api/footprint/outcomes?tf&toMs&after=&limit=100&sessionDate=} — same order, plus the
     * cursor grammar check (400 {@code bad cursor}) between permit and snapshot.
     */
    @GetMapping(value = "/api/footprint/outcomes", produces = MediaType.APPLICATION_JSON_VALUE)
    public void footprintOutcomes(@org.springframework.web.bind.annotation.RequestParam("tf") String tf,
                                  @org.springframework.web.bind.annotation.RequestParam("toMs") long toMs,
                                  @org.springframework.web.bind.annotation.RequestParam(value = "after", defaultValue = "") String after,
                                  @org.springframework.web.bind.annotation.RequestParam(value = "limit", defaultValue = "100") int limit,
                                  @org.springframework.web.bind.annotation.RequestParam(value = "sessionDate", defaultValue = "") String sessionDate,
                                  @org.springframework.web.bind.annotation.RequestHeader(value = "Authorization", required = false) String authorization,
                                  jakarta.servlet.http.HttpServletResponse response) throws java.io.IOException {
        if (!footprintGate(response, "outcomes", authorization)) return;
        java.util.concurrent.Semaphore permits = service.footprintBackfillPermits();
        if (!permits.tryAcquire()) { reject(response, "outcomes", "busy", 503, "{\"error\":\"busy\"}", true); return; }
        try {
            if (!after.isEmpty() && !FootprintViews.validOutcomeCursor(tf, after)) {
                reject(response, "outcomes", "bad_cursor", 400, "{\"error\":\"bad cursor\"}", false);
                return;
            }
            FootprintViews.OutcomesPage page = service.footprintViews().outcomesPage(tf, toMs, after, clamp(limit), sessionDate);
            if (page.sessionMismatch()) service.footprintBackfillRejected("outcomes", "session_mismatch");
            writePage(response, page.sessionDate(), page.sessionMismatch(), "outcomes", page.records(),
                    page.nextCursor() == null ? "null" : "\"" + page.nextCursor() + "\"");
        } finally {
            permits.release();
        }
    }

    /** Steps (2) flag and (3) authentication; counts the request at the flag check (G-R9). */
    private boolean footprintGate(jakarta.servlet.http.HttpServletResponse response, String route, String authorization) throws java.io.IOException {
        if (!service.footprintEnabled()) {
            response.setStatus(404);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getOutputStream().write("{\"enabled\":false}".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
            response.flushBuffer();
            return false;
        }
        service.footprintBackfillRequested(route);
        if (footprintAuth == null) {                      // no authenticator wired: fail closed
            response.setStatus(401);
            response.flushBuffer();
            return false;
        }
        app.feedgateway.liquidityhistory.LiquidityHistoryAuth.Result auth = footprintAuth.apply(authorization);
        if (auth.status() != 200) {
            response.setStatus(auth.status());
            response.flushBuffer();
            return false;
        }
        return true;
    }

    static int clamp(int limit) { return Math.max(1, Math.min(limit, FOOTPRINT_LIMIT_MAX)); }

    private void reject(jakarta.servlet.http.HttpServletResponse response, String route, String reason, int status,
                        String body, boolean retryAfter) throws java.io.IOException {
        service.footprintBackfillRejected(route, reason);
        response.setStatus(status);
        if (retryAfter) response.setHeader("Retry-After", "1");
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getOutputStream().write(body.getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        response.flushBuffer();
    }

    /**
     * G-R7 step (7): {@code {"sessionDate":..,["sessionMismatch":true,]"<field>":[..],"nextCursor":..}}
     * streamed straight into the servlet response stream. There is NO page-side buffer; the only
     * buffer between these bytes and the socket is the container's response buffer, which this method
     * REQUESTS at {@link #FOOTPRINT_WRITE_BUFFER} (64 KiB) and then VERIFIES with
     * {@code getBufferSize()} — a container that reports more refuses the page with 503 rather than
     * streaming behind an unbounded buffer, so the transient bound (≤ one record + 64 KiB) is enforced
     * rather than assumed (CODE round-1 #5). Each record is written as its own ASCII byte array (F-E8
     * alphabet), so no copy of the page as a whole ever exists.
     */
    private static void writePage(jakarta.servlet.http.HttpServletResponse response, String sessionDate, boolean mismatch,
                                  String field, java.util.List<String> records, String cursorJson) throws java.io.IOException {
        try { response.setBufferSize(FOOTPRINT_WRITE_BUFFER); } catch (IllegalStateException alreadyCommitted) { /* verified below */ }
        int buffer = response.getBufferSize();
        if (buffer > FOOTPRINT_WRITE_BUFFER) {
            response.setStatus(503);
            response.setHeader("Retry-After", "5");
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getOutputStream().write(("{\"error\":\"response buffer " + buffer + " exceeds " + FOOTPRINT_WRITE_BUFFER + "\"}")
                    .getBytes(java.nio.charset.StandardCharsets.US_ASCII));
            response.flushBuffer();
            return;
        }
        response.setStatus(200);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        java.io.OutputStream out = response.getOutputStream();
        StringBuilder head = new StringBuilder("{\"sessionDate\":");
        head.append(sessionDate == null ? "null" : "\"" + sessionDate + "\"");
        if (mismatch) head.append(",\"sessionMismatch\":true");
        head.append(",\"").append(field).append("\":[");
        out.write(head.toString().getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        for (int i = 0; i < records.size(); i++) {
            if (i > 0) out.write(',');
            out.write(records.get(i).getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        }
        out.write(("],\"nextCursor\":" + cursorJson + "}").getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        out.flush();
        response.flushBuffer();
    }

    @GetMapping(value = "/")
    public ResponseEntity<Void> index() {
        // Serve the sign-in / application UI (static/index.html).
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create("/index.html")).build();
    }

    /**
     * P1 (environment portability): the bundled sign-in pages fetch their Keycloak issuer + client id from
     * here instead of hardcoding {@code localhost} — so a remote browser talks to the DEPLOYED Keycloak, not
     * the user's workstation. Public (no auth): it carries no secrets and is needed to start authentication.
     */
    @GetMapping(value = "/auth-config", produces = MediaType.APPLICATION_JSON_VALUE)
    public String authConfig() {
        return service.authConfigJson();
    }

    @GetMapping(value = "/health", produces = MediaType.APPLICATION_JSON_VALUE)
    public String health() {
        return service.healthJson();
    }

    /**
     * R46 backfill (ES-CVD-DESIGN.md): the current session's CVD bar-close records for one
     * timeframe, ascending by barStartMs, paginated by afterMs (exclusive cursor) up to toMs
     * (inclusive bound = the hello frame's high-water mark). Auth: same JWT gate as every /api
     * route. Response: {"sessionDate":..., "bars":[<record>...], "nextCursor": <long or null>}.
     */
    @GetMapping(value = "/api/cvd/bars", produces = MediaType.APPLICATION_JSON_VALUE)
    public String cvdBars(@org.springframework.web.bind.annotation.RequestParam("tf") String tf,
                          @org.springframework.web.bind.annotation.RequestParam("toMs") long toMs,
                          @org.springframework.web.bind.annotation.RequestParam(value = "afterMs", defaultValue = "-1") long afterMs,
                          @org.springframework.web.bind.annotation.RequestParam(value = "limit", defaultValue = "500") int limit,
                          @org.springframework.web.bind.annotation.RequestParam(value = "sessionDate", defaultValue = "") String sessionDate) {
        int capped = Math.max(1, Math.min(limit, 1000));
        // R46: session check, rows, cursor and session stamp are ONE atomic snapshot (finding 3).
        FeedGatewayService.CvdBarsPage page = service.cvdBarsPage(tf, toMs, afterMs, capped, sessionDate);
        StringBuilder sb = new StringBuilder("{\"sessionDate\":");
        sb.append(page.sessionDate() == null ? "null" : "\"" + page.sessionDate() + "\"");
        if (page.sessionMismatch()) {
            return sb.append(",\"sessionMismatch\":true,\"bars\":[],\"nextCursor\":null}").toString();
        }
        sb.append(",\"bars\":[");
        for (int i = 0; i < page.bars().size(); i++) {
            if (i > 0) sb.append(',');
            sb.append(page.bars().get(i));
        }
        sb.append("],\"nextCursor\":").append(page.nextCursor() == null ? "null" : page.nextCursor());
        sb.append('}');
        return sb.toString();
    }

    @GetMapping(value = "/metrics", produces = MediaType.TEXT_PLAIN_VALUE)
    public String metrics() {
        // Liquidity-history §7 metrics are appended to the same text endpoint the rest of the
        // gateway exports on (one scrape target per pod).
        return service.metrics() + (historyStore == null ? "" : historyStore.metricsText());
    }
}

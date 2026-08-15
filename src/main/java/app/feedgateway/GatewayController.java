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

    public GatewayController(FeedGatewayService service,
                             org.springframework.beans.factory.ObjectProvider<
                                     app.feedgateway.liquidityhistory.LiquidityHistoryStore> historyStore) {
        this.service = service;
        this.historyStore = historyStore.getIfAvailable();
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
                          @org.springframework.web.bind.annotation.RequestParam(value = "limit", defaultValue = "500") int limit) {
        int capped = Math.max(1, Math.min(limit, 1000));
        java.util.List<String> bars = service.cvdBarsSnapshot(tf, toMs, afterMs, capped);
        StringBuilder sb = new StringBuilder("{\"sessionDate\":");
        String sd = service.cvdBarsSessionDate();
        sb.append(sd == null ? "null" : "\"" + sd + "\"").append(",\"bars\":[");
        for (int i = 0; i < bars.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append(bars.get(i));
        }
        // nextCursor = the last returned bar's start when the page filled; null otherwise.
        sb.append("],\"nextCursor\":");
        if (bars.size() == capped) {
            com.fasterxml.jackson.databind.ObjectMapper m = new com.fasterxml.jackson.databind.ObjectMapper();
            try {
                sb.append(m.readTree(bars.get(bars.size() - 1)).path("bar").path("barStartMs").asLong());
            } catch (Exception e) { sb.append("null"); }
        } else {
            sb.append("null");
        }
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

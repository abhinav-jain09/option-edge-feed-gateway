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

    @GetMapping(value = "/metrics", produces = MediaType.TEXT_PLAIN_VALUE)
    public String metrics() {
        // Liquidity-history §7 metrics are appended to the same text endpoint the rest of the
        // gateway exports on (one scrape target per pod).
        return service.metrics() + (historyStore == null ? "" : historyStore.metricsText());
    }
}

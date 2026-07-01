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

    public GatewayController(FeedGatewayService service) {
        this.service = service;
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
     * Forward-path watchdog readiness probe. 503 when the broadcast path is silently stalled during
     * market hours with at least one active WebSocket session — so k8s stops routing to a wedged pod
     * and restarts it. Liveness (/health) is unaffected. See
     * {@link FeedGatewayService#readinessStatus()} for the full contract.
     */
    @GetMapping(value = "/readyz", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> readyz() {
        int status = service.readinessStatus();
        String body = status == 200 ? "{\"ready\":true}" : "{\"ready\":false,\"reason\":\"forward-stalled\"}";
        return ResponseEntity.status(status).body(body);
    }

    @GetMapping(value = "/metrics", produces = MediaType.TEXT_PLAIN_VALUE)
    public String metrics() {
        return service.metrics();
    }
}

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

    @GetMapping(value = "/health", produces = MediaType.APPLICATION_JSON_VALUE)
    public String health() {
        return service.healthJson();
    }

    @GetMapping(value = "/metrics", produces = MediaType.TEXT_PLAIN_VALUE)
    public String metrics() {
        return service.metrics();
    }
}

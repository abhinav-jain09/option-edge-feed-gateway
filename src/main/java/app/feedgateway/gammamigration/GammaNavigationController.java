package app.feedgateway.gammamigration;

import app.feedgateway.FeedGatewayService;
import app.feedgateway.liquidityhistory.LiquidityHistoryAuth;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Locale;

/** Serves the latest bounded Gamma Navigator shadow record for one chain. */
@RestController
public class GammaNavigationController {
    private static final int RATE_LIMIT_PER_MIN = 120;
    private final FeedGatewayService service;
    private final LiquidityHistoryAuth auth;
    private final ObjectMapper mapper;
    private final GammaMigrationController.RateLimiter rateLimiter =
            new GammaMigrationController.RateLimiter(RATE_LIMIT_PER_MIN, 60_000L);

    public GammaNavigationController(FeedGatewayService service, LiquidityHistoryAuth auth,
                                     ObjectMapper mapper) {
        this.service = service;
        this.auth = auth;
        this.mapper = mapper == null ? new ObjectMapper() : mapper;
    }

    @GetMapping(value = "/api/gamma-navigation", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> gammaNavigation(
            @RequestParam(value = "symbol", required = false) String symbol,
            @RequestParam(value = "expiry", required = false) String expiry,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        LiquidityHistoryAuth.Result authResult = auth.authenticate(authorization);
        if (authResult.status() != 200) return ResponseEntity.status(authResult.status()).build();
        long retry = rateLimiter.tryAcquire(authResult.principal(), System.currentTimeMillis());
        if (retry > 0) return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, Long.toString(retry)).build();
        if (symbol == null || symbol.isBlank() || expiry == null || expiry.isBlank()) {
            String[] active = service.activeSymbolExpiry();
            if (symbol == null || symbol.isBlank()) symbol = active[0];
            if (expiry == null || expiry.isBlank()) expiry = active[1];
        }
        if (symbol == null || symbol.isBlank() || expiry == null || expiry.isBlank()) {
            ObjectNode error = mapper.createObjectNode();
            error.put("error", "no symbol/expiry given and the gateway has no active selection yet");
            return ResponseEntity.badRequest().body(write(error));
        }
        String cached = service.cachedGammaNavigation(symbol, expiry);
        if (cached != null && !cached.isBlank()) return ResponseEntity.ok(cached);
        ObjectNode absent = mapper.createObjectNode();
        absent.put("present", false);
        absent.put("symbol", symbol.trim().toUpperCase(Locale.ROOT));
        absent.put("expiry", expiry.trim());
        return ResponseEntity.ok(write(absent));
    }

    private String write(ObjectNode node) {
        try {
            return mapper.writeValueAsString(node);
        } catch (JsonProcessingException e) {
            return "{\"present\":false}";
        }
    }
}

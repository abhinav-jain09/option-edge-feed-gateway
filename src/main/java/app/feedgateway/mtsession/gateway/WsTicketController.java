package app.feedgateway.mtsession.gateway;

import app.feedgateway.mtsession.MarketDataSource;
import app.feedgateway.mtsession.NotApprovedException;
import app.feedgateway.mtsession.NotEntitledException;
import app.feedgateway.mtsession.Selection;
import app.feedgateway.mtsession.StrikeWindow;
import app.feedgateway.mtsession.auth.JwtVerificationException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * Mints a single-use WS ticket after verifying the caller's bearer JWT (OE-DDD-001 §5.3 / §11.1).
 * Only registered when {@code gateway.auth.enabled=true} (flag-gated migration, DDD §12).
 */
@RestController
@ConditionalOnProperty(name = "gateway.auth.enabled", havingValue = "true")
public final class WsTicketController {

    private final WsTicketService ticketService;

    public WsTicketController(WsTicketService ticketService) {
        this.ticketService = ticketService;
    }

    public record TicketRequest(String source, String symbol, String expiry, Double strikeLo, Double strikeHi) {
        Selection toSelection() {
            MarketDataSource src = MarketDataSource.parse(source)
                    .orElseThrow(() -> new IllegalArgumentException("invalid source: " + source));
            StrikeWindow window = (strikeLo == null || strikeHi == null)
                    ? StrikeWindow.ALL : StrikeWindow.of(strikeLo, strikeHi);
            return new Selection(src, symbol, expiry, window);
        }
    }

    @PostMapping(value = "/api/ws-ticket", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> issue(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody TicketRequest request) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return error(HttpStatus.UNAUTHORIZED, "missing bearer token");
        }
        String token = authorization.substring("Bearer ".length()).trim();
        try {
            WsTicketService.TicketIssued issued = ticketService.issue(token, request.toSelection());
            String body = "{\"ticket\":\"" + issued.ticketId() + "\","
                    + "\"appSessionId\":\"" + issued.appSessionId() + "\","
                    + "\"expiresInMs\":" + issued.expiresInMs() + "}";
            return ResponseEntity.ok(body);
        } catch (JwtVerificationException e) {
            return error(HttpStatus.UNAUTHORIZED, "invalid token");
        } catch (NotApprovedException e) {
            return error(HttpStatus.FORBIDDEN, "not approved");
        } catch (NotEntitledException e) {
            return error(HttpStatus.FORBIDDEN, "not entitled");
        } catch (IllegalArgumentException e) {
            return error(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    private static ResponseEntity<String> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON)
                .body("{\"error\":\"" + message + "\"}");
    }
}

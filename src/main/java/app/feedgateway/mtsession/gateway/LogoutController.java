package app.feedgateway.mtsession.gateway;

import app.feedgateway.FeedGatewayService;
import app.feedgateway.mtsession.auth.JwtVerificationException;
import app.feedgateway.mtsession.auth.TokenVerifier;
import app.feedgateway.mtsession.auth.VerifiedPrincipal;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

/**
 * P0 (logout completeness): server-side sign-out. The browser clears its own credentials and ends the
 * Keycloak session; this endpoint tears down the matching SERVER-side {@code AppSession} — cancelling any
 * replay and force-closing every WebSocket attached to it — so a leaked ticket or a still-open socket
 * cannot keep streaming after the user signs out.
 *
 * <p>Authenticated by {@code Authorization: Bearer} only (no ambient cookie authority), so {@code @CrossOrigin}
 * is safe: a caller can only tear down the session of the subject whose token it presents.
 */
@RestController
@CrossOrigin(origins = "*", allowedHeaders = "*", methods = {RequestMethod.POST, RequestMethod.OPTIONS})
@ConditionalOnProperty(name = "gateway.auth.enabled", havingValue = "true")
public final class LogoutController {

    private final TokenVerifier verifier;
    private final FeedGatewayService gatewayService;

    public LogoutController(TokenVerifier verifier, FeedGatewayService gatewayService) {
        this.verifier = verifier;
        this.gatewayService = gatewayService;
    }

    @PostMapping(value = "/api/logout", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> logout(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return error(HttpStatus.UNAUTHORIZED, "missing bearer token");
        }
        String token = authorization.substring("Bearer ".length()).trim();
        try {
            VerifiedPrincipal principal = verifier.verify(token);
            String userId = principal.userId();
            if (userId == null || userId.isBlank()) {
                return error(HttpStatus.UNAUTHORIZED, "token has no subject");
            }
            int closed = gatewayService.logout("app:" + userId);
            return ResponseEntity.ok("{\"loggedOut\":true,\"socketsClosed\":" + closed + "}");
        } catch (JwtVerificationException e) {
            return error(HttpStatus.UNAUTHORIZED, "invalid token");
        }
    }

    private static ResponseEntity<String> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON)
                .body("{\"error\":\"" + message.replace("\"", "'") + "\"}");
    }
}

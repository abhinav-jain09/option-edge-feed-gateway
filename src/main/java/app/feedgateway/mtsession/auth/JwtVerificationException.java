package app.feedgateway.mtsession.auth;

/**
 * Raised when an access token fails verification (bad signature, wrong issuer, expired, malformed,
 * or an unexpected authorized party). The WS handshake / REST filter rejects on this (OE-DDD-001 §5.3).
 */
public class JwtVerificationException extends Exception {

    public JwtVerificationException(String message) {
        super(message);
    }

    public JwtVerificationException(String message, Throwable cause) {
        super(message, cause);
    }
}

package app.feedgateway;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * P1 (environment portability + XSS hardening): emit a restrictive, DEPLOYMENT-AWARE Content-Security-Policy
 * (and other security headers) on every response. Because the Keycloak origin is injected per environment
 * (see {@code /auth-config}), the CSP's {@code connect-src} is derived from {@code GATEWAY_KEYCLOAK_ISSUER}
 * at runtime — a static meta-CSP could not allow the right origin across dev/prod. Scripts are confined to
 * {@code 'self'} (self-hosted only, no inline JS, no CDN), so reflected data cannot execute.
 */
@Component
public class SecurityHeadersFilter extends OncePerRequestFilter {

    private final GatewaySettings settings;

    public SecurityHeadersFilter(GatewaySettings settings) {
        this.settings = settings;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        response.setHeader("Content-Security-Policy", contentSecurityPolicy());
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("Referrer-Policy", "no-referrer");
        response.setHeader("X-Frame-Options", "DENY");
        response.setHeader("Cross-Origin-Opener-Policy", "same-origin");
        chain.doFilter(request, response);
    }

    String contentSecurityPolicy() {
        String kc = originOf(settings.keycloakIssuer());
        String connectSrc = "connect-src 'self'" + (kc.isEmpty() ? "" : " " + kc);
        // script-src 'self': no inline JS, no third-party CDN (React et al. are self-hosted). style 'unsafe-
        // inline' is acceptable (style attributes cannot execute JS). connect-src allows the deployment's
        // Keycloak so the PKCE token/refresh/revoke calls work, plus same-origin (/auth-config, /api, ws).
        return "default-src 'none'; "
                + "script-src 'self'; "
                + "style-src 'self' 'unsafe-inline'; "
                + "img-src 'self' data:; "
                + "font-src 'self' data:; "
                + connectSrc + "; "
                + "base-uri 'none'; "
                + "frame-ancestors 'none'; "
                + "form-action 'none'";
    }

    /** {@code scheme://host[:port]} of a URL, or "" if it cannot be parsed. */
    static String originOf(String url) {
        if (url == null || url.isBlank()) {
            return "";
        }
        try {
            URI u = URI.create(url.trim());
            if (u.getScheme() == null || u.getHost() == null) {
                return "";
            }
            return u.getScheme() + "://" + u.getHost() + (u.getPort() == -1 ? "" : ":" + u.getPort());
        } catch (RuntimeException e) {
            return "";
        }
    }
}

package app.feedgateway;

import app.feedgateway.mtsession.MarketDataSource;
import app.feedgateway.mtsession.NotApprovedException;
import app.feedgateway.mtsession.NotEntitledException;
import app.feedgateway.mtsession.Selection;
import app.feedgateway.mtsession.StrikeWindow;
import app.feedgateway.mtsession.auth.JwtVerificationException;
import app.feedgateway.mtsession.auth.VerifiedPrincipal;
import app.feedgateway.mtsession.gateway.TicketHandshakeInterceptor;
import app.feedgateway.mtsession.gateway.WsTicketService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.function.LongSupplier;
import java.util.regex.Pattern;

/**
 * Authenticates the WebSocket handshake with a Keycloak JWT. The browser {@code WebSocket(url, protocols)}
 * API can set ONLY the {@code Sec-WebSocket-Protocol} header (no Authorization header; bearer-in-query is
 * prohibited), so the client offers {@code ["oc.bearer", <accessToken>]}; this interceptor extracts and
 * validates the token (signature + iss + aud + exp) before the socket is established. A missing/invalid
 * token is rejected with 401. The handler echoes the {@code oc.bearer} subprotocol (see FeedWebSocketHandler).
 *
 * <p>This interceptor coexists with the {@link TicketHandshakeInterceptor} so the gateway can serve BOTH
 * the legacy option-chain web app (offers {@code oc.bearer}) AND the multi-tenant replay UI (offers
 * {@code oe.ticket.<id>}) in the same {@code GATEWAY_AUTH_ENABLED=true} mode. Wiring order (ticket first,
 * JWT second) is set by {@link WebSocketConfig}:
 * <ul>
 *   <li>A request offering {@code oe.ticket.*} is fully handled by the ticket interceptor (which sets
 *       {@link TicketHandshakeInterceptor#ATTR_APP_SESSION_ID}); this interceptor sees the attr already
 *       set and SKIPS so it cannot reject the ticket flow for missing {@code oc.bearer}.</li>
 *   <li>A request offering {@code oc.bearer} is passed through by the ticket interceptor (no ticket
 *       subprotocol present) and lands here; this interceptor validates the JWT and — when the
 *       multi-tenant routing engine is active ({@link WsTicketService} bean present) — also
 *       registers/refreshes the user's AppSession via the same shared
 *       {@link WsTicketService#ensureAppSession} the ws-ticket flow uses, so approval, app-session-id,
 *       entitlement-refresh, and selection-revalidation semantics are identical on both paths.</li>
 * </ul>
 */
@Component
public class WsJwtHandshakeInterceptor implements HandshakeInterceptor {

    private static final Logger LOG = LoggerFactory.getLogger(WsJwtHandshakeInterceptor.class);
    private static final long LOG_INTERVAL_MS = 10_000L;
    private static final int MAX_LOG_KEYS = 256;
    private static final int MAX_DETAIL = 200;
    private final RefusalLog refusalLog = new RefusalLog();
    /** Clock for the log rate limit; replaceable so a test does not sleep. */
    private volatile LongSupplier nowMs = System::currentTimeMillis;

    void setClockForTest(LongSupplier clock) {
        this.nowMs = clock;
    }

    static final String BEARER_SUBPROTOCOL = "oc.bearer";
    /** Session attribute holding the token's expiry (epoch ms), so the reaper can close it when it lapses. */
    public static final String AUTH_EXPIRES_AT_ATTR = "authExpiresAtEpochMs";
    private static final String SEC_WEBSOCKET_PROTOCOL = "Sec-WebSocket-Protocol";

    private final GatewaySettings settings;
    /**
     * Present ONLY when {@code GATEWAY_AUTH_ENABLED=true}: gives us the shared AppSession-registration
     * helper + the multi-tenant {@link app.feedgateway.mtsession.auth.TokenVerifier}. When absent (legacy
     * single-tenant {@code WS_AUTH_ENABLED}-only mode) we fall back to the local {@link JwtDecoder} and do
     * NOT bind an AppSession (the legacy path has no routing engine).
     */
    private final ObjectProvider<WsTicketService> ticketServiceProvider;
    private volatile JwtDecoder decoder; // lazily built from settings; injectable for tests

    // @Autowired disambiguates this from the test-only two-arg constructor: with two constructors and no
    // marker, Spring looks for a no-arg default (which doesn't exist) and the whole context fails to start.
    @Autowired
    public WsJwtHandshakeInterceptor(GatewaySettings settings,
                                     ObjectProvider<WsTicketService> ticketServiceProvider) {
        this.settings = settings;
        this.ticketServiceProvider = ticketServiceProvider;
    }

    /** Test-only: legacy single-tenant mode (no routing engine). */
    WsJwtHandshakeInterceptor(GatewaySettings settings, JwtDecoder decoder) {
        this.settings = settings;
        this.ticketServiceProvider = noTicketService();
        this.decoder = decoder;
    }

    /** Test-only: multi-tenant mode with an explicit ticket service (bearer binds AppSession). */
    WsJwtHandshakeInterceptor(GatewaySettings settings, JwtDecoder decoder, WsTicketService ticketService) {
        this.settings = settings;
        this.ticketServiceProvider = singletonProvider(ticketService);
        this.decoder = decoder;
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        // SKIP if the ticket interceptor already bound an AppSession on this handshake. Without this skip
        // a valid ticket connection (which offers `oe.ticket.<id>` instead of `oc.bearer`) would be rejected
        // here for missing the bearer subprotocol — even though the ticket interceptor already accepted it.
        if (attributes.get(TicketHandshakeInterceptor.ATTR_APP_SESSION_ID) instanceof String existing
                && !existing.isBlank()) {
            return true;
        }

        WsTicketService ticketService = ticketServiceProvider.getIfAvailable();
        boolean multiTenant = ticketService != null;

        // Auth-off short-circuit (legacy single-tenant dev only — multi-tenant mode always enforces auth).
        if (!multiTenant && !settings.wsAuthEnabled()) {
            return true;
        }

        String token = extractToken(request.getHeaders());
        if (token == null) {
            return reject(request, response, "no oc.bearer token offered", null, null);
        }

        if (multiTenant) {
            // Multi-tenant mode: use the SAME TokenVerifier (issuer/audience/azp/typ) the ws-ticket flow
            // uses, then register/refresh the AppSession via the shared helper so both auth paths enforce
            // identical approval + entitlement semantics. NotApprovedException MUST close the handshake.
            try {
                VerifiedPrincipal principal = ticketService.verify(token);
                Selection selection = defaultSelection();
                String userId = ticketService.ensureAppSession(principal, selection);
                attributes.put(TicketHandshakeInterceptor.ATTR_APP_SESSION_ID, "app:" + userId);
                attributes.put(TicketHandshakeInterceptor.ATTR_USER_ID, userId);
                attributes.put("sub", userId);
                if (principal.expiresAt() != null) {
                    attributes.put(AUTH_EXPIRES_AT_ATTR, principal.expiresAt().toEpochMilli());
                }
                return true;
            } catch (JwtVerificationException denied) {
                return reject(request, response, "token not accepted", denied, token);
            } catch (NotApprovedException denied) {
                return reject(request, response, "not approved", denied, token);
            } catch (NotEntitledException notEntitled) {
                // Codex round-2 L-2: the user is approved but not entitled to the operator-configured
                // {@link MarketDataSource} (e.g. {@code APP_MARKET_DATA_SOURCE=IBKR} for a Databento-only
                // user, or the parseable-but-never-primary {@code SHARED} source). Without this catch,
                // {@link SessionRoutingEngine#changeSelection}/{@code registerAppSession} would let the
                // RuntimeException escape the interceptor and surface as an uncaught handshake error
                // (HTTP 500-ish), not a controlled 401. We close the handshake with 401 — defence in depth
                // alongside the operator-side check.
                return reject(request, response, "not entitled", notEntitled, token);
            } catch (IllegalArgumentException badSelection) {
                // The default Selection requires non-blank symbol+expiry (Selection record). If the gateway
                // is misconfigured (no initialSymbol/initialExpiry, or an unparseable
                // APP_MARKET_DATA_SOURCE — see {@link #defaultSelection()}), fail closed with 401 — better
                // than a half-attached socket. Operator must set IB_SYMBOL + IB_EXPIRY (and a parseable
                // source) when GATEWAY_AUTH_ENABLED=true.
                return reject(request, response, "gateway selection misconfigured", badSelection, token);
            }
        }

        // Legacy single-tenant path: just validate the JWT against the WS_AUTH_* decoder. No routing engine,
        // no AppSession binding (FeedWebSocketHandler also skips the attach when engine is absent).
        try {
            Jwt jwt = decoder().decode(token);
            attributes.put("sub", jwt.getSubject());
            if (jwt.getExpiresAt() != null) {
                attributes.put(AUTH_EXPIRES_AT_ATTR, jwt.getExpiresAt().toEpochMilli());
            }
            return true;
        } catch (JwtException e) {
            return reject(request, response, "token not accepted", e, token);
        }
    }

    /**
     * Refuses the handshake with 401 and logs WHY. The browser only sees a failed upgrade (close 1006) and a 401
     * has no body worth reading, so without this line a refused login is invisible: an unapproved account was
     * mistaken for a socket-cap problem for hours (2026-11-10). Logs the reason and the exception message
     * (token problem such as expired / wrong audience, or the user id for a not-approved account); never the
     * token itself.
     */
    private boolean reject(ServerHttpRequest request, ServerHttpResponse response, String reason, Exception cause, String offeredToken) {
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        String detail = cause == null ? "" : sanitize(cause.getMessage(), offeredToken);
        // One line per reason per interval (per user for a not-approved account, whose id comes from a signed
        // token so the key set is bounded by real users): a client that retries every few seconds, or a caller
        // sending garbage on purpose, must not turn the log into noise.
        String key = "not approved".equals(reason) ? reason + ':' + detail : reason;
        String suffix = refusalLog.admit(key, nowMs.getAsLong());
        if (suffix == null) {
            return false;
        }
        LOG.warn("ws handshake refused (401): {}{} origin={}{}", reason, detail.isEmpty() ? "" : ": " + detail,
                sanitize(request.getHeaders().getOrigin(), null), suffix);
        return false;
    }

    private static final Pattern JWT_SHAPE = Pattern.compile("[A-Za-z0-9_-]{8,}\\.[A-Za-z0-9_-]{8,}(\\.[A-Za-z0-9_-]*)?");

    /**
     * Makes a verifier message safe for one log line: the offered token and anything shaped like a JWT is
     * replaced, line breaks and other control characters become spaces (no forged log lines), and the
     * text is cut to {@value #MAX_DETAIL}. The message is useful (expired, wrong audience, unexpected azp,
     * the user id of an unapproved account) but comes from libraries whose wording is not guaranteed.
     */
    static String sanitize(String message, String offeredToken) {
        if (message == null) {
            return "";
        }
        String out = message;
        if (offeredToken != null && !offeredToken.isBlank()) {
            out = out.replace(offeredToken, "[token]");
        }
        out = JWT_SHAPE.matcher(out).replaceAll("[token]");
        out = out.replaceAll("[\\p{Cntrl}]+", " ").trim();
        return out.length() > MAX_DETAIL ? out.substring(0, MAX_DETAIL - 3) + "..." : out;
    }

    /**
     * Decides, per key, whether a refusal is logged now. All state sits behind one lock, so concurrent
     * handshakes cannot all see an expired timestamp and each log; the table holds at most
     * {@link #MAX_LOG_KEYS} keys (the oldest is dropped, never the whole table) and each key counts the
     * refusals it did not log, so a line never reports another reason's suppressed refusals.
     */
    private static final class RefusalLog {
        private static final class State {
            long lastLoggedMs;
            long suppressed;
        }

        private final Map<String, State> states = new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, State> eldest) {
                return size() > MAX_LOG_KEYS;
            }
        };

        /** The suffix to append to the log line ("" or the suppressed count), or null when this one is not logged. */
        synchronized String admit(String key, long nowMs) {
            State st = states.get(key);
            if (st == null) {
                st = new State();
                st.lastLoggedMs = nowMs;
                states.put(key, st);
                return "";
            }
            if (nowMs - st.lastLoggedMs < LOG_INTERVAL_MS) {
                st.suppressed++;
                return null;
            }
            String suffix = st.suppressed > 0 ? " (" + st.suppressed + " similar refusals not logged)" : "";
            st.lastLoggedMs = nowMs;
            st.suppressed = 0;
            return suffix;
        }
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                              WebSocketHandler wsHandler, Exception exception) {
        // no-op
    }

    /**
     * The gateway's default {@link Selection} for a bearer-flow connection: source / symbol / expiry from
     * {@link GatewaySettings} (the same fields that drive {@code FeedGatewayService.ActiveSelection}).
     * StrikeWindow is open (ALL) by default — the legacy {@code oc.bearer} clients filter strikes client-
     * side; multi-tenant strike narrowing only applies to the ws-ticket flow that explicitly carries a
     * window. Throws {@link IllegalArgumentException} (caught above → 401) if symbol/expiry are blank.
     *
     * <p>Source resolution is FAIL-CLOSED in multi-tenant mode (review finding M-1): a blank
     * {@code APP_MARKET_DATA_SOURCE} defaults to {@code DATABENTO} (the production live source), but a
     * non-blank-but-unparseable value (e.g. typo {@code "IBKRR"}) throws — silently re-routing an approved
     * user to a different feed than the operator intended is a security-relevant misconfiguration, not
     * something the gateway should paper over.
     */
    private Selection defaultSelection() {
        return defaultSelection(settings);
    }

    /**
     * Static form of {@link #defaultSelection()} so the REST liquidity-history endpoint
     * ({@code app.feedgateway.liquidityhistory.LiquidityHistoryAuth}) enforces the EXACT same
     * fail-closed source/symbol/expiry resolution when it registers/refreshes an AppSession through
     * the shared {@link WsTicketService#ensureAppSession} helper — a divergence here would let the
     * REST path accept a configuration the WS handshake rejects (or vice versa).
     */
    public static Selection defaultSelection(GatewaySettings settings) {
        String configured = settings.initialMarketDataSource();
        MarketDataSource source;
        if (configured == null || configured.isBlank()) {
            source = MarketDataSource.DATABENTO;
        } else {
            source = MarketDataSource.parse(configured)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "APP_MARKET_DATA_SOURCE is set but not parseable; refusing to default the bearer-"
                                    + "flow Selection to an unintended source"));
        }
        // SHARED is parseable but never a primary {@link MarketDataSource}; it indexes broadcast underlying
        // streams (VIX) for sessions of either source. Default-routing a bearer-flow user to SHARED is a
        // configuration error and would otherwise surface deeper in routing as NotEntitledException — fail
        // closed at the source. Note: this mirrors {@code EntitlementPolicy.canSelect(SHARED) == false}.
        if (source == MarketDataSource.SHARED) {
            throw new IllegalArgumentException(
                    "APP_MARKET_DATA_SOURCE=SHARED is not a valid primary source for the bearer-flow default");
        }
        return new Selection(source, settings.initialSymbol(),
                GatewaySettings.normalizeExpiry(settings.initialExpiry()), StrikeWindow.ALL);
    }

    /** Token from the offered subprotocols {@code ["oc.bearer", "<token>"]} — the value after the marker. */
    static String extractToken(HttpHeaders headers) {
        List<String> offered = new ArrayList<>();
        for (String header : headers.getOrEmpty(SEC_WEBSOCKET_PROTOCOL)) {
            for (String part : header.split(",")) {
                String trimmed = part.trim();
                if (!trimmed.isEmpty()) {
                    offered.add(trimmed);
                }
            }
        }
        int marker = offered.indexOf(BEARER_SUBPROTOCOL);
        if (marker >= 0 && marker + 1 < offered.size()) {
            return offered.get(marker + 1);
        }
        return null;
    }

    private JwtDecoder decoder() {
        JwtDecoder current = decoder;
        if (current == null) {
            synchronized (this) {
                current = decoder;
                if (current == null) {
                    current = buildDecoder();
                    decoder = current;
                }
            }
        }
        return current;
    }

    private JwtDecoder buildDecoder() {
        return buildWsDecoder(settings);
    }

    /**
     * Builds the legacy single-tenant WS JWT decoder (issuer + audience validation from
     * {@code WS_AUTH_ISSUER_URI}/{@code WS_AUTH_AUDIENCE}). Public+static so the REST
     * liquidity-history endpoint validates bearer tokens with the SAME decoder rules as the WS
     * handshake in legacy mode (one token-validation policy per mode, never two).
     */
    public static JwtDecoder buildWsDecoder(GatewaySettings settings) {
        String issuer = settings.wsAuthIssuer();
        if (issuer == null || issuer.isBlank()) {
            throw new IllegalStateException("WS_AUTH_ISSUER_URI is required when WS_AUTH_ENABLED=true");
        }
        // Split-horizon deployments may reach Keycloak directly while tokens retain their
        // public issuer. This changes key retrieval only; issuer and audience stay mandatory.
        String jwksUrl = settings.keycloakJwksUrl();
        NimbusJwtDecoder nimbus = jwksUrl == null || jwksUrl.isBlank()
                ? NimbusJwtDecoder.withIssuerLocation(issuer).build()
                : NimbusJwtDecoder.withJwkSetUri(jwksUrl.trim()).build();
        OAuth2TokenValidator<Jwt> withIssuer = JwtValidators.createDefaultWithIssuer(issuer);
        OAuth2TokenValidator<Jwt> audience = new AudienceValidator(settings.wsAuthAudience());
        nimbus.setJwtValidator(new DelegatingOAuth2TokenValidator<>(withIssuer, audience));
        return nimbus;
    }

    private static ObjectProvider<WsTicketService> noTicketService() {
        return new ObjectProvider<>() {
            @Override public WsTicketService getObject(Object... args) { return null; }
            @Override public WsTicketService getObject() { return null; }
            @Override public WsTicketService getIfAvailable() { return null; }
            @Override public WsTicketService getIfUnique() { return null; }
        };
    }

    private static ObjectProvider<WsTicketService> singletonProvider(WsTicketService svc) {
        return new ObjectProvider<>() {
            @Override public WsTicketService getObject(Object... args) { return svc; }
            @Override public WsTicketService getObject() { return svc; }
            @Override public WsTicketService getIfAvailable() { return svc; }
            @Override public WsTicketService getIfUnique() { return svc; }
        };
    }
}

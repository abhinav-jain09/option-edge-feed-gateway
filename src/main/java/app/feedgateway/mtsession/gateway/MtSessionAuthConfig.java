package app.feedgateway.mtsession.gateway;

import app.feedgateway.GatewaySettings;
import app.feedgateway.mtsession.ConcurrencyLimits;
import app.feedgateway.mtsession.InMemoryTicketStore;
import app.feedgateway.mtsession.RedisTicketStore;
import app.feedgateway.mtsession.SessionRoutingEngine;
import app.feedgateway.mtsession.SubscriptionManager;
import app.feedgateway.mtsession.TicketStore;
import app.feedgateway.mtsession.approval.ApprovalAuthority;
import app.feedgateway.mtsession.approval.HttpApprovalAuthority;
import app.feedgateway.mtsession.approval.RoleClaimApprovalAuthority;
import app.feedgateway.mtsession.auth.JwtVerificationException;
import app.feedgateway.mtsession.auth.KeycloakJwtVerifier;
import app.feedgateway.mtsession.auth.TokenVerifier;
import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import java.time.Clock;
import java.time.Duration;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the multi-tenant session/auth components into the gateway, but ONLY when
 * {@code gateway.auth.enabled=true} (env {@code GATEWAY_AUTH_ENABLED}). With the flag off this
 * configuration is not loaded, so the gateway starts and behaves exactly as before (DDD §12).
 */
@Configuration
@ConditionalOnProperty(name = "gateway.auth.enabled", havingValue = "true")
public class MtSessionAuthConfig {

    @Bean
    public SessionRoutingEngine sessionRoutingEngine() {
        return new SessionRoutingEngine(ConcurrencyLimits.defaults(), new SubscriptionManager());
    }

    @Bean
    public TicketStore ticketStore(GatewaySettings settings) {
        String redisUri = settings.redisUri();
        boolean hasRedis = redisUri != null && !redisUri.isBlank();
        // Auth is enabled (this config loads only when gateway.auth.enabled=true). Outside dev/test a
        // durable, shared ticket store is mandatory — single-use ticket redemption must hold across
        // instances — so fail startup rather than silently fall back to the in-memory store.
        requireDurableTicketStore(hasRedis, settings);
        // P1 (multi-replica): stamp the minting replica into the ticket id (<instance>~<random>) so the
        // handshake can reject a ticket that reached the wrong replica before redeeming it.
        String instanceId = settings.instanceId();
        java.util.function.Supplier<String> ids = () -> instanceId + "~" + UUID.randomUUID();
        if (!hasRedis) {
            return new InMemoryTicketStore(Clock.systemUTC(), ids);
        }
        RedisClient client = RedisClient.create(redisUri);
        StatefulRedisConnection<String, String> conn = client.connect();
        return new RedisTicketStore(conn.sync(), Clock.systemUTC(), ids, "oe:ticket:");
    }

    /**
     * Enforce the durable ticket-store policy (req. 6, review finding #7). When auth is on, the in-memory
     * ticket store is allowed ONLY via an explicit, deliberate opt-out ({@code GATEWAY_ALLOW_INMEMORY_TICKETS=true});
     * otherwise {@code GATEWAY_REDIS_URI} is mandatory. This is fail-closed: it no longer keys off the
     * {@code APP_PROFILE} *default* ("dev"), so a real deploy that enables auth but forgets to set
     * {@code APP_PROFILE=prod} can never silently fall back to a non-durable, non-shared store.
     *
     * @throws IllegalStateException (failing startup) if Redis is required but not configured
     */
    static void requireDurableTicketStore(boolean hasRedis, GatewaySettings settings) {
        if (!hasRedis && !settings.allowInMemoryTickets()) {
            throw new IllegalStateException(
                    "GATEWAY_REDIS_URI is required when GATEWAY_AUTH_ENABLED=true. The in-memory ticket store "
                            + "is not durable or shared across instances and must be opted into explicitly with "
                            + "GATEWAY_ALLOW_INMEMORY_TICKETS=true (dev/test only); otherwise set GATEWAY_REDIS_URI "
                            + "(APP_PROFILE=" + settings.appProfile() + ").");
        }
    }

    @Bean
    public TokenVerifier tokenVerifier(GatewaySettings settings) {
        String issuer = settings.keycloakIssuer();
        if (issuer == null || issuer.isBlank()) {
            // misconfigured-but-safe: fail closed at verify time rather than at startup
            return token -> {
                throw new JwtVerificationException("GATEWAY_KEYCLOAK_ISSUER is not configured");
            };
        }
        return new KeycloakJwtVerifier(issuer, settings.keycloakJwksUrl(), settings.keycloakClientId(),
                settings.keycloakAudience());
    }

    @Bean
    public HandshakeTicketAuthenticator handshakeTicketAuthenticator(TicketStore ticketStore,
                                                                     GatewaySettings settings) {
        return new HandshakeTicketAuthenticator(ticketStore, settings.instanceId());
    }

    @Bean
    public WsTicketService wsTicketService(TokenVerifier tokenVerifier, TicketStore ticketStore,
                                           SessionRoutingEngine engine, ApprovalAuthority approvalAuthority,
                                           GatewaySettings settings) {
        return new WsTicketService(tokenVerifier, ticketStore, engine, approvalAuthority,
                settings.keycloakIssuer(), Duration.ofSeconds(settings.wsTicketTtlSeconds()));
    }

    /**
     * The authoritative approval gate (P0 — approval enforcement). Prefer the Config-Control platform
     * (GATEWAY_APPROVAL_URL); else fall back to an admin-granted realm role (GATEWAY_APPROVAL_ROLE, explicit
     * opt-in); else DENY EVERYONE (fail closed). {@link MtSessionSecurityInvariant} refuses startup if
     * neither is configured, so an auth-enabled gateway can never silently approve every user.
     */
    @Bean
    public ApprovalAuthority approvalAuthority(GatewaySettings settings) {
        String url = settings.approvalUrl();
        if (url != null && !url.isBlank()) {
            return new HttpApprovalAuthority(url, settings.approvalApiKey(),
                    Duration.ofMillis(settings.approvalTimeoutMs()));
        }
        String role = settings.approvalRole();
        if (role != null && !role.isBlank()) {
            return new RoleClaimApprovalAuthority(role);
        }
        return new ApprovalAuthority.DenyAll();
    }

    @Bean
    public TicketHandshakeInterceptor ticketHandshakeInterceptor(HandshakeTicketAuthenticator authenticator,
                                                                 GatewaySettings settings) {
        return new TicketHandshakeInterceptor(authenticator, settings.authEnabled());
    }

    /**
     * Control plane for per-session Live↔Replay switching. The data plane ({@link ReplayRunner}) is
     * the {@link app.feedgateway.FeedGatewayService} bean. Replay is administratively gated by
     * {@code DATABENTO_REPLAY_UI_ENABLED} and disabled in prod unless explicitly allowed (req. 11).
     */
    @Bean
    public ReplayService replayService(TokenVerifier tokenVerifier, SessionRoutingEngine engine,
                                       ReplayRunner replayRunner, ReplayRunAuthorizer replayRunAuthorizer,
                                       GatewaySettings settings) {
        boolean prodBlocked = settings.isProd() && !settings.replayAllowInProd();
        return new ReplayService(tokenVerifier, engine, replayRunner, replayRunAuthorizer,
                settings.replayUiEnabled(), prodBlocked,
                settings.replayMaxWindowMs(), settings.replayMaxRecords());
    }

    /**
     * Authorizes runId ownership against the orchestrator before any replay topic is read (P0 — runId
     * authz). With no {@code GATEWAY_REPLAY_ORCHESTRATOR_URL} configured we cannot confirm ownership, so
     * the authorizer denies every runId-backed replay (fail closed); {@link MtSessionSecurityInvariant}
     * additionally refuses startup in that posture when replay is enabled.
     */
    @Bean
    public ReplayRunAuthorizer replayRunAuthorizer(GatewaySettings settings) {
        String base = settings.replayOrchestratorBaseUrl();
        if (base == null || base.isBlank()) {
            return (token, runId) -> {
                throw new ReplayRunAuthorizer.ReplayRunAuthorizationException(
                        "replay run authorization unavailable: GATEWAY_REPLAY_ORCHESTRATOR_URL is not configured");
            };
        }
        return new OrchestratorReplayRunAuthorizer(base,
                Duration.ofMillis(settings.replayOrchestratorTimeoutMs()));
    }
}

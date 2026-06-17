package app.feedgateway.mtsession.gateway;

import app.feedgateway.GatewaySettings;
import app.feedgateway.mtsession.ConcurrencyLimits;
import app.feedgateway.mtsession.InMemoryTicketStore;
import app.feedgateway.mtsession.RedisTicketStore;
import app.feedgateway.mtsession.SessionRoutingEngine;
import app.feedgateway.mtsession.SubscriptionManager;
import app.feedgateway.mtsession.TicketStore;
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
        if (!hasRedis) {
            return new InMemoryTicketStore(Clock.systemUTC(), () -> UUID.randomUUID().toString());
        }
        RedisClient client = RedisClient.create(redisUri);
        StatefulRedisConnection<String, String> conn = client.connect();
        return new RedisTicketStore(conn.sync(), Clock.systemUTC(),
                () -> UUID.randomUUID().toString(), "oe:ticket:");
    }

    /**
     * Enforce the production ticket-store policy (req. 6): when auth is on and the profile is not
     * dev/test, {@code GATEWAY_REDIS_URI} must be set. The in-memory store is allowed only for dev/test.
     *
     * @throws IllegalStateException (failing startup) if Redis is required but not configured
     */
    static void requireDurableTicketStore(boolean hasRedis, GatewaySettings settings) {
        if (!hasRedis && !settings.isDevOrTest()) {
            throw new IllegalStateException(
                    "GATEWAY_REDIS_URI is required when GATEWAY_AUTH_ENABLED=true and APP_PROFILE is not "
                            + "dev/test (APP_PROFILE=" + settings.appProfile() + "). The in-memory ticket store "
                            + "is permitted only for dev/test; set GATEWAY_REDIS_URI for a durable shared store.");
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
        return new KeycloakJwtVerifier(issuer, settings.keycloakJwksUrl(), settings.keycloakClientId());
    }

    @Bean
    public HandshakeTicketAuthenticator handshakeTicketAuthenticator(TicketStore ticketStore) {
        return new HandshakeTicketAuthenticator(ticketStore);
    }

    @Bean
    public WsTicketService wsTicketService(TokenVerifier tokenVerifier, TicketStore ticketStore,
                                           SessionRoutingEngine engine, GatewaySettings settings) {
        return new WsTicketService(tokenVerifier, ticketStore, engine,
                Duration.ofSeconds(settings.wsTicketTtlSeconds()));
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
                                       ReplayRunner replayRunner, GatewaySettings settings) {
        boolean prodBlocked = settings.isProd() && !settings.replayAllowInProd();
        return new ReplayService(tokenVerifier, engine, replayRunner,
                settings.replayUiEnabled(), prodBlocked,
                settings.replayMaxWindowMs(), settings.replayMaxRecords());
    }
}

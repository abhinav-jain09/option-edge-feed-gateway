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
        if (redisUri == null || redisUri.isBlank()) {
            return new InMemoryTicketStore(Clock.systemUTC(), () -> UUID.randomUUID().toString());
        }
        RedisClient client = RedisClient.create(redisUri);
        StatefulRedisConnection<String, String> conn = client.connect();
        return new RedisTicketStore(conn.sync(), Clock.systemUTC(),
                () -> UUID.randomUUID().toString(), "oe:ticket:");
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
        return new KeycloakJwtVerifier(issuer, settings.keycloakClientId());
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
}

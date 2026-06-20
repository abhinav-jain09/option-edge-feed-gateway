package app.feedgateway.mtsession.gateway;

import app.feedgateway.GatewaySettings;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * P0 production-mode invariant: enabling authentication (GATEWAY_AUTH_ENABLED=true) puts the gateway into
 * one INSEPARABLE secure mode — there is no half-on configuration. This bean exists only when auth is on
 * (the same condition that wires the session engine + ticket auth) and fails startup LOUDLY if any pillar
 * of the secure posture is missing, rather than silently degrading to a fail-open default.
 *
 * <p>Per-session tenant isolation is intrinsic (the SessionRoutingEngine bean is created under the very
 * same condition, so an authenticated socket is always routed per-session — no separate flag to leave off).
 * This invariant additionally requires, with explicit/logged dev opt-outs only:
 * <ul>
 *   <li>a configured token issuer (GATEWAY_KEYCLOAK_ISSUER) — fail at startup, not first request;</li>
 *   <li>an exact WS origin allow-list (no {@code *});</li>
 *   <li>a durable, shared ticket store (GATEWAY_REDIS_URI) unless GATEWAY_ALLOW_INMEMORY_TICKETS=true;</li>
 *   <li>encrypted/authenticated Kafka (GATEWAY_KAFKA_SECURITY_PROTOCOL=SSL/SASL_SSL) unless
 *       GATEWAY_ALLOW_INSECURE_KAFKA=true.</li>
 * </ul>
 * Each opt-out is a deliberate dev/test switch; with no switches set, the default for an auth-enabled
 * gateway is fully fail-closed.
 */
@Component
@ConditionalOnProperty(name = "gateway.auth.enabled", havingValue = "true")
public class MtSessionSecurityInvariant implements InitializingBean {

    private final GatewaySettings settings;

    public MtSessionSecurityInvariant(GatewaySettings settings) {
        this.settings = settings;
    }

    @Override
    public void afterPropertiesSet() {
        List<String> errors = new ArrayList<>();

        if (settings.keycloakIssuer() == null || settings.keycloakIssuer().isBlank()) {
            errors.add("GATEWAY_KEYCLOAK_ISSUER must be set");
        }
        boolean wildcardOrigin = Arrays.stream(settings.wsAllowedOrigins().split(","))
                .map(String::trim).anyMatch("*"::equals);
        if (wildcardOrigin) {
            errors.add("WS_ALLOWED_ORIGINS must be an explicit allow-list (no '*')");
        }
        boolean hasRedis = settings.redisUri() != null && !settings.redisUri().isBlank();
        if (!hasRedis && !settings.allowInMemoryTickets()) {
            errors.add("GATEWAY_REDIS_URI required for a durable ticket store"
                    + " (or GATEWAY_ALLOW_INMEMORY_TICKETS=true for dev/test)");
        }
        if (!settings.kafkaSecure() && !settings.allowInsecureKafka()) {
            errors.add("GATEWAY_KAFKA_SECURITY_PROTOCOL must be SSL/SASL_SSL"
                    + " (or GATEWAY_ALLOW_INSECURE_KAFKA=true for dev)");
        }

        if (!errors.isEmpty()) {
            throw new IllegalStateException(
                    "GATEWAY_AUTH_ENABLED=true is the inseparable secure production mode, but the security "
                            + "posture is incomplete — the gateway refuses to start. Missing: "
                            + String.join("; ", errors) + ".");
        }
    }
}

package app.feedgateway.mtsession.gateway;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import app.feedgateway.mtsession.SessionRoutingEngine;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

/**
 * Boots the FULL Spring context to prove the flag-gated auth wiring is valid at runtime — not just
 * at compile time. Named {@code *IT} so the default {@code mvn test} (surefire) does NOT run it;
 * it is opt-in to avoid the gateway's @PostConstruct Kafka consumers connecting to remote brokers.
 *
 * <p>Run with the Kafka loop disabled and auth enabled against the local infra:
 * <pre>
 * MT_BOOT_TEST=1 KAFKA_ENABLED=false GATEWAY_AUTH_ENABLED=true \
 *   GATEWAY_KEYCLOAK_ISSUER=http://localhost:8099/realms/optionsedge \
 *   GATEWAY_REDIS_URI=redis://localhost:6380 \
 *   mvn test -Dtest=GatewayContextBootIT
 * </pre>
 */
@SpringBootTest
class GatewayContextBootIT {

    @Autowired
    ApplicationContext ctx;

    @BeforeAll
    static void gate() {
        assumeTrue(System.getenv("MT_BOOT_TEST") != null,
                "opt-in boot test; set MT_BOOT_TEST=1 + KAFKA_ENABLED=false + GATEWAY_AUTH_ENABLED=true + local Keycloak/Redis");
    }

    @Test
    @DisplayName("Full context boots and wires the flag-gated auth beans")
    void contextBootsAndWiresAuthBeans() {
        assertNotNull(ctx);
        // the conditional auth wiring is present and injectable end-to-end
        assertNotNull(ctx.getBean(SessionRoutingEngine.class));
        assertNotNull(ctx.getBean(WsTicketService.class));
        assertNotNull(ctx.getBean(HandshakeTicketAuthenticator.class));
        assertNotNull(ctx.getBean(TicketHandshakeInterceptor.class));
        assertNotNull(ctx.getBean(WsTicketController.class));
        // the live WebSocket config bean co-exists with the interceptor
        assertNotNull(ctx.getBean(app.feedgateway.WebSocketConfig.class));
    }
}

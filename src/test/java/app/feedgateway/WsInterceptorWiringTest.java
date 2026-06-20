package app.feedgateway;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Guards against the constructor-ambiguity that kept the whole Spring context from starting: with two
 * constructors and no {@code @Autowired} marker, Spring looked for a no-arg default and failed to boot.
 * A plain bean-factory refresh reproduces that without needing Kafka or the full app context.
 */
class WsInterceptorWiringTest {

    @Test
    void interceptorIsConstructableBySpring() {
        try (AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext()) {
            ctx.register(GatewaySettings.class, WsJwtHandshakeInterceptor.class);
            ctx.refresh();
            assertNotNull(ctx.getBean(WsJwtHandshakeInterceptor.class));
        }
    }
}

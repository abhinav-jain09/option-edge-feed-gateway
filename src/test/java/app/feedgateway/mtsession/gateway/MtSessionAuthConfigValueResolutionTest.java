package app.feedgateway.mtsession.gateway;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.reflect.Method;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.TypeMismatchException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.BeanExpressionContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;

/**
 * Resolves the REAL {@code @Value} expressions of {@link MtSessionAuthConfig#sessionRoutingEngine} the way the
 * container does (placeholder, then SpEL, then conversion to Integer), against a given environment: an unset or
 * empty variable is "not set" (null), a number is that number, a non-number fails instead of defaulting.
 */
class MtSessionAuthConfigValueResolutionTest {

    private static Integer resolve(int parameter, Map<String, Object> env) throws Exception {
        Method m = null;
        for (Method c : MtSessionAuthConfig.class.getDeclaredMethods()) {
            if (c.getName().equals("sessionRoutingEngine")) {
                m = c;
            }
        }
        String expression = m.getParameters()[parameter].getAnnotation(Value.class).value();
        try (AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext()) {
            ctx.getEnvironment().getPropertySources().addFirst(new MapPropertySource("t", env));
            ctx.refresh();
            var bf = ctx.getBeanFactory();
            Object v = bf.getBeanExpressionResolver()
                    .evaluate(bf.resolveEmbeddedValue(expression), new BeanExpressionContext(bf, null));
            return bf.getTypeConverter().convertIfNecessary(v, Integer.class);
        }
    }

    @Test
    void theThreeVariablesAreRead() throws Exception {
        assertEquals(7, resolve(0, Map.of("GATEWAY_MAX_APP_SESSIONS_PER_USER", "7")));
        assertEquals(32, resolve(1, Map.of("GATEWAY_MAX_SOCKETS_PER_APP_SESSION", "32")));
        assertEquals(500, resolve(2, Map.of("GATEWAY_MAX_TOTAL_APP_SESSIONS", "500")));
    }

    @Test
    void unsetOrEmptyMeansNotSet() throws Exception {
        assertNull(resolve(1, Map.of()));
        assertNull(resolve(1, Map.of("GATEWAY_MAX_SOCKETS_PER_APP_SESSION", "")));
    }

    @Test
    void aNonNumberFailsStartupInsteadOfDefaulting() {
        assertThrows(TypeMismatchException.class,
                () -> resolve(1, Map.of("GATEWAY_MAX_SOCKETS_PER_APP_SESSION", "lots")));
    }
}

package app.feedgateway.mtsession.gateway;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.feedgateway.GatewaySettings;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * P0: with GATEWAY_AUTH_ENABLED=true the gateway must refuse to start unless the WHOLE secure posture is
 * present (issuer, exact origins, durable ticket store, encrypted Kafka), with explicit dev opt-outs only.
 */
class MtSessionSecurityInvariantTest {

    private static void check() {
        new MtSessionSecurityInvariant(new GatewaySettings()).afterPropertiesSet();
    }

    /** A fully-configured production posture (no opt-outs needed). */
    private static Map<String, String> prodProps() {
        Map<String, String> m = new HashMap<>();
        m.put("GATEWAY_KEYCLOAK_ISSUER", "https://kc.prod/realms/optionsedge");
        m.put("WS_ALLOWED_ORIGINS", "https://optionsedge.prod");
        m.put("GATEWAY_REDIS_URI", "rediss://redis.prod:6379");
        m.put("GATEWAY_KAFKA_SECURITY_PROTOCOL", "SASL_SSL");
        return m;
    }

    @Test
    void fullySecureConfigStarts() {
        withProps(prodProps(), () -> assertDoesNotThrow(MtSessionSecurityInvariantTest::check));
    }

    @Test
    void missingIssuerFailsStartup() {
        Map<String, String> m = prodProps();
        m.remove("GATEWAY_KEYCLOAK_ISSUER");
        withProps(m, () -> assertTrue(msg(assertThrows(IllegalStateException.class,
                MtSessionSecurityInvariantTest::check)).contains("GATEWAY_KEYCLOAK_ISSUER")));
    }

    @Test
    void wildcardOriginFailsStartup() {
        Map<String, String> m = prodProps();
        m.put("WS_ALLOWED_ORIGINS", "https://optionsedge.prod,*");
        withProps(m, () -> assertTrue(msg(assertThrows(IllegalStateException.class,
                MtSessionSecurityInvariantTest::check)).contains("WS_ALLOWED_ORIGINS")));
    }

    @Test
    void missingRedisWithoutOptInFailsStartup() {
        Map<String, String> m = prodProps();
        m.remove("GATEWAY_REDIS_URI");
        withProps(m, () -> assertTrue(msg(assertThrows(IllegalStateException.class,
                MtSessionSecurityInvariantTest::check)).contains("GATEWAY_REDIS_URI")));
    }

    @Test
    void insecureKafkaWithoutOptInFailsStartup() {
        Map<String, String> m = prodProps();
        m.put("GATEWAY_KAFKA_SECURITY_PROTOCOL", "PLAINTEXT");
        withProps(m, () -> assertTrue(msg(assertThrows(IllegalStateException.class,
                MtSessionSecurityInvariantTest::check)).contains("GATEWAY_KAFKA_SECURITY_PROTOCOL")));
    }

    @Test
    void devOptOutsAllowInsecureLocalSetup() {
        // dev: in-memory tickets + plaintext kafka, BUT issuer + exact origins are still mandatory.
        Map<String, String> m = new HashMap<>();
        m.put("GATEWAY_KEYCLOAK_ISSUER", "http://192.168.100.102:8089/realms/optionsedge");
        m.put("WS_ALLOWED_ORIGINS", "http://localhost:8090");
        m.put("GATEWAY_ALLOW_INMEMORY_TICKETS", "true");
        m.put("GATEWAY_ALLOW_INSECURE_KAFKA", "true");
        withProps(m, () -> assertDoesNotThrow(MtSessionSecurityInvariantTest::check));
    }

    private static String msg(Throwable t) {
        return String.valueOf(t.getMessage());
    }

    private static void withProps(Map<String, String> props, Runnable body) {
        Map<String, String> prev = new HashMap<>();
        for (String k : props.keySet()) {
            prev.put(k, System.getProperty(k));
        }
        try {
            props.forEach(System::setProperty);
            body.run();
        } finally {
            prev.forEach((k, v) -> {
                if (v == null) {
                    System.clearProperty(k);
                } else {
                    System.setProperty(k, v);
                }
            });
        }
    }
}

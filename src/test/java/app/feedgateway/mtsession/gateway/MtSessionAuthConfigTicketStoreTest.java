package app.feedgateway.mtsession.gateway;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.feedgateway.GatewaySettings;
import app.feedgateway.mtsession.InMemoryTicketStore;
import org.junit.jupiter.api.Test;

/**
 * Ticket-store policy (req. 6): with auth enabled, the in-memory store is allowed only for dev/test;
 * any other profile must supply GATEWAY_REDIS_URI or startup fails.
 *
 * <p>{@link GatewaySettings} reads env/system properties lazily, so every settings-dependent call is
 * made inside the {@code withProps} scope where the profile/redis properties are in effect.
 */
class MtSessionAuthConfigTicketStoreTest {

    @Test
    void redisRequiredWhenProfileNotDevAndRedisMissing() {
        withProps("prod", "", () -> assertThrows(IllegalStateException.class,
                () -> MtSessionAuthConfig.requireDurableTicketStore(false, new GatewaySettings())));
        withProps("staging", "", () -> assertThrows(IllegalStateException.class,
                () -> MtSessionAuthConfig.requireDurableTicketStore(false, new GatewaySettings())));
    }

    @Test
    void inMemoryAllowedForDevAndTest() {
        withProps("dev", "", () -> assertDoesNotThrow(
                () -> MtSessionAuthConfig.requireDurableTicketStore(false, new GatewaySettings())));
        withProps("test", "", () -> assertDoesNotThrow(
                () -> MtSessionAuthConfig.requireDurableTicketStore(false, new GatewaySettings())));
    }

    @Test
    void redisPresentSatisfiesAnyProfile() {
        withProps("prod", "redis://localhost:6379", () -> assertDoesNotThrow(
                () -> MtSessionAuthConfig.requireDurableTicketStore(true, new GatewaySettings())));
    }

    @Test
    void ticketStoreBeanIsInMemoryForDevWithoutRedis() {
        withProps("dev", "", () ->
                assertInstanceOf(InMemoryTicketStore.class, new MtSessionAuthConfig().ticketStore(new GatewaySettings())));
    }

    @Test
    void ticketStoreBeanFailsStartupForProdWithoutRedis() {
        withProps("prod", "", () -> {
            IllegalStateException ex = assertThrows(IllegalStateException.class,
                    () -> new MtSessionAuthConfig().ticketStore(new GatewaySettings()));
            assertTrue(ex.getMessage().contains("GATEWAY_REDIS_URI"));
        });
    }

    private static void withProps(String profile, String redisUri, Runnable body) {
        String prevProfile = System.getProperty("APP_PROFILE");
        String prevRedis = System.getProperty("GATEWAY_REDIS_URI");
        System.setProperty("APP_PROFILE", profile);
        if (redisUri == null || redisUri.isBlank()) {
            System.clearProperty("GATEWAY_REDIS_URI");
        } else {
            System.setProperty("GATEWAY_REDIS_URI", redisUri);
        }
        try {
            body.run();
        } finally {
            restore("APP_PROFILE", prevProfile);
            restore("GATEWAY_REDIS_URI", prevRedis);
        }
    }

    private static void restore(String key, String previous) {
        if (previous == null) {
            System.clearProperty(key);
        } else {
            System.setProperty(key, previous);
        }
    }
}

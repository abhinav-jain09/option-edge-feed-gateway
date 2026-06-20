package app.feedgateway.mtsession.gateway;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.feedgateway.GatewaySettings;
import app.feedgateway.mtsession.InMemoryTicketStore;
import org.junit.jupiter.api.Test;

/**
 * Ticket-store policy (req. 6, review finding #7): with auth enabled, the in-memory store is allowed ONLY
 * via the explicit GATEWAY_ALLOW_INMEMORY_TICKETS opt-in — independent of APP_PROFILE — so a real deploy
 * that forgets APP_PROFILE=prod cannot silently use a non-durable store. Otherwise GATEWAY_REDIS_URI is
 * mandatory and startup fails closed.
 *
 * <p>{@link GatewaySettings} reads env/system properties lazily, so every settings-dependent call is made
 * inside the {@code withProps} scope where the properties are in effect.
 */
class MtSessionAuthConfigTicketStoreTest {

    @Test
    void inMemoryRejectedWithoutOptInRegardlessOfProfile() {
        // The dev default profile must NOT silently permit in-memory — that was the footgun.
        for (String profile : new String[]{"prod", "staging", "dev", "test"}) {
            withProps(profile, "", false, () -> assertThrows(IllegalStateException.class,
                    () -> MtSessionAuthConfig.requireDurableTicketStore(false, new GatewaySettings())));
        }
    }

    @Test
    void inMemoryAllowedOnlyWithExplicitOptIn() {
        // The explicit opt-out is honored in any profile (dev/test use).
        withProps("dev", "", true, () -> assertDoesNotThrow(
                () -> MtSessionAuthConfig.requireDurableTicketStore(false, new GatewaySettings())));
        withProps("prod", "", true, () -> assertDoesNotThrow(
                () -> MtSessionAuthConfig.requireDurableTicketStore(false, new GatewaySettings())));
    }

    @Test
    void redisPresentSatisfiesAnyProfile() {
        withProps("prod", "redis://localhost:6379", false, () -> assertDoesNotThrow(
                () -> MtSessionAuthConfig.requireDurableTicketStore(true, new GatewaySettings())));
    }

    @Test
    void ticketStoreBeanIsInMemoryOnlyWithExplicitOptIn() {
        withProps("dev", "", true, () ->
                assertInstanceOf(InMemoryTicketStore.class, new MtSessionAuthConfig().ticketStore(new GatewaySettings())));
    }

    @Test
    void ticketStoreBeanFailsStartupWithoutRedisOrOptIn() {
        withProps("prod", "", false, () -> {
            IllegalStateException ex = assertThrows(IllegalStateException.class,
                    () -> new MtSessionAuthConfig().ticketStore(new GatewaySettings()));
            assertTrue(ex.getMessage().contains("GATEWAY_REDIS_URI"));
        });
    }

    private static void withProps(String profile, String redisUri, boolean allowInMemory, Runnable body) {
        String prevProfile = System.getProperty("APP_PROFILE");
        String prevRedis = System.getProperty("GATEWAY_REDIS_URI");
        String prevAllow = System.getProperty("GATEWAY_ALLOW_INMEMORY_TICKETS");
        System.setProperty("APP_PROFILE", profile);
        if (redisUri == null || redisUri.isBlank()) {
            System.clearProperty("GATEWAY_REDIS_URI");
        } else {
            System.setProperty("GATEWAY_REDIS_URI", redisUri);
        }
        if (allowInMemory) {
            System.setProperty("GATEWAY_ALLOW_INMEMORY_TICKETS", "true");
        } else {
            System.clearProperty("GATEWAY_ALLOW_INMEMORY_TICKETS");
        }
        try {
            body.run();
        } finally {
            restore("APP_PROFILE", prevProfile);
            restore("GATEWAY_REDIS_URI", prevRedis);
            restore("GATEWAY_ALLOW_INMEMORY_TICKETS", prevAllow);
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

package app.feedgateway;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WebSocketConfigOriginsTest {

    @Test
    void wildcardIsAllowedWhenAuthDisabled() {
        assertArrayEquals(new String[]{"*"},
                WebSocketConfig.resolveAllowedOrigins(false, "*"));
    }

    @Test
    void wildcardIsRejectedWhenAuthEnabled() {
        assertThrows(IllegalStateException.class,
                () -> WebSocketConfig.resolveAllowedOrigins(true, "*"));
    }

    @Test
    void wildcardMixedIntoListIsAlsoRejectedWhenAuthEnabled() {
        assertThrows(IllegalStateException.class,
                () -> WebSocketConfig.resolveAllowedOrigins(true, "https://app.example, *"));
    }

    @Test
    void explicitAllowListIsParsedAndTrimmedWhenAuthEnabled() {
        assertArrayEquals(new String[]{"https://app.example", "https://admin.example"},
                WebSocketConfig.resolveAllowedOrigins(true, " https://app.example , https://admin.example "));
    }
}

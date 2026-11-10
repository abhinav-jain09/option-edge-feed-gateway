package app.feedgateway.mtsession;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class ConcurrencyLimitsTest {

    @Test
    void nothingSetKeepsTheDocumentedDefaults() {
        assertEquals(ConcurrencyLimits.defaults(), ConcurrencyLimits.of(null, null, null));
        assertEquals(4, ConcurrencyLimits.defaults().maxSocketsPerAppSession());
    }

    @Test
    void anOperatorLimitReplacesOnlyItsOwnField() {
        ConcurrencyLimits l = ConcurrencyLimits.of(null, 32, null);
        assertEquals(1, l.maxAppSessionsPerUser());
        assertEquals(32, l.maxSocketsPerAppSession());
        assertEquals(200, l.maxTotalAppSessions());
    }

    @Test
    void aLimitBelowOneIsRefusedNotReplacedByTheDefault() {
        assertThrows(IllegalArgumentException.class, () -> ConcurrencyLimits.of(null, 0, null));
        assertThrows(IllegalArgumentException.class, () -> ConcurrencyLimits.of(-1, null, null));
    }
}

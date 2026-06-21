package app.feedgateway.mtsession;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class NormalizationTest {

    @Test
    void symbolUpperCasesAndTrims() {
        assertEquals("SPX", Normalization.symbol("  spx "));
        assertEquals("NDX", Normalization.symbol("ndx"));
        assertEquals("", Normalization.symbol(null));
        assertEquals("", Normalization.symbol("   "));
    }

    @Test
    void expiryStripsDashes() {
        assertEquals("20260612", Normalization.expiry("2026-06-12"));
        assertEquals("20260612", Normalization.expiry(" 20260612 "));
        assertEquals("", Normalization.expiry(null));
    }

    @Test
    void expiryValidity() {
        assertTrue(Normalization.isValidExpiry("2026-06-12"));
        assertTrue(Normalization.isValidExpiry("20260612"));
        assertFalse(Normalization.isValidExpiry("2026-6-12"));
        assertFalse(Normalization.isValidExpiry("notadate"));
        assertFalse(Normalization.isValidExpiry(null));
    }
}

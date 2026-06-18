package app.feedgateway.mtsession.gateway;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import org.junit.jupiter.api.Test;

/**
 * Replay window/calendar validation (reqs 6 & 11): the window must be a positive span no longer than
 * the configured maximum (30 min), instants must be ISO-8601 UTC, required fields non-blank, and the
 * record count is clamped to the hard cap.
 */
class ReplayParamsTest {

    private static final long MAX_WINDOW_MS = 30 * 60 * 1000L;
    private static final int MAX_RECORDS = 200_000;

    private static ReplayParams of(String start, String end, Integer maxRecords) {
        return ReplayParams.of("app:u1", "spx", "2026-06-12", start, end, maxRecords, MAX_WINDOW_MS, MAX_RECORDS);
    }

    @Test
    void validWindowNormalisesSymbolExpiryAndClampsRecords() {
        ReplayParams p = of("2026-06-12T14:00:00Z", "2026-06-12T14:20:00Z", 500);
        assertEquals("app:u1", p.sessionId());
        assertEquals("SPX", p.symbol());                 // upper-cased
        assertEquals("20260612", p.expiry());            // dashes stripped
        assertEquals(Instant.parse("2026-06-12T14:00:00Z").toEpochMilli(), p.startUtcMs());
        assertEquals(Instant.parse("2026-06-12T14:20:00Z").toEpochMilli(), p.endUtcMs());
        assertEquals(500, p.maxRecords());
    }

    @Test
    void recordCountDefaultsAndClampsToCap() {
        assertEquals(1000, of("2026-06-12T14:00:00Z", "2026-06-12T14:10:00Z", null).maxRecords());
        assertEquals(MAX_RECORDS, of("2026-06-12T14:00:00Z", "2026-06-12T14:10:00Z", 10_000_000).maxRecords());
        assertEquals(1, of("2026-06-12T14:00:00Z", "2026-06-12T14:10:00Z", -5).maxRecords());
    }

    @Test
    void rejectsNonPositiveWindow() {
        assertThrows(IllegalArgumentException.class, () -> of("2026-06-12T14:00:00Z", "2026-06-12T14:00:00Z", 100));
        assertThrows(IllegalArgumentException.class, () -> of("2026-06-12T14:20:00Z", "2026-06-12T14:00:00Z", 100));
    }

    @Test
    void rejectsWindowOverThirtyMinutes() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> of("2026-06-12T14:00:00Z", "2026-06-12T14:30:01Z", 100));
        assertEquals("replay window exceeds maximum of 30 minutes", ex.getMessage());
    }

    @Test
    void allowsExactlyThirtyMinutes() {
        ReplayParams p = of("2026-06-12T14:00:00Z", "2026-06-12T14:30:00Z", 100);
        assertEquals(MAX_WINDOW_MS, p.endUtcMs() - p.startUtcMs());
    }

    @Test
    void rejectsUnparseableInstant() {
        assertThrows(IllegalArgumentException.class, () -> of("not-a-date", "2026-06-12T14:10:00Z", 100));
        assertThrows(IllegalArgumentException.class, () -> of("2026-06-12T14:00:00Z", "14:10", 100));
    }

    @Test
    void rejectsBlankRequiredFields() {
        assertThrows(IllegalArgumentException.class,
                () -> ReplayParams.of("  ", "SPX", "20260612", "2026-06-12T14:00:00Z", "2026-06-12T14:10:00Z", 1, MAX_WINDOW_MS, MAX_RECORDS));
        assertThrows(IllegalArgumentException.class,
                () -> ReplayParams.of("app:u1", "", "20260612", "2026-06-12T14:00:00Z", "2026-06-12T14:10:00Z", 1, MAX_WINDOW_MS, MAX_RECORDS));
        assertThrows(IllegalArgumentException.class,
                () -> ReplayParams.of("app:u1", "SPX", "  ", "2026-06-12T14:00:00Z", "2026-06-12T14:10:00Z", 1, MAX_WINDOW_MS, MAX_RECORDS));
    }
}

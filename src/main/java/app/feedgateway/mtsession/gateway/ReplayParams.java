package app.feedgateway.mtsession.gateway;

import java.time.Instant;
import java.util.Objects;

/**
 * Validated parameters for one per-session historical replay (req. 6). The window is bounded to
 * {@code maxWindowMs} (req. 11: ≤ 30 minutes) and {@code maxRecords} is clamped to a hard cap.
 *
 * <p>{@code sessionId} is the caller's AppSession id; the {@link ReplayService} additionally checks
 * it matches the authenticated principal so a user can only drive their own session.
 */
public record ReplayParams(
        String sessionId,
        String symbol,
        String expiry,
        long startUtcMs,
        long endUtcMs,
        int maxRecords) {

    public ReplayParams {
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(symbol, "symbol");
        Objects.requireNonNull(expiry, "expiry");
    }

    /**
     * Parse and validate a replay request. {@code startUtc}/{@code endUtc} are ISO-8601 instants
     * (as produced by the UI's {@code MarketCalendar.validateReplayWindow}).
     *
     * @throws IllegalArgumentException on any blank field, unparseable instant, non-positive or
     *                                  over-long window, or non-positive record cap.
     */
    public static ReplayParams of(String sessionId, String symbol, String expiry,
                                  String startUtc, String endUtc, Integer maxRecords,
                                  long maxWindowMs, int maxRecordsCap) {
        String sid = requireText(sessionId, "sessionId");
        String sym = requireText(symbol, "symbol").toUpperCase();
        String exp = requireText(expiry, "expiry").replace("-", "");
        long start = parseInstant(startUtc, "startUtc");
        long end = parseInstant(endUtc, "endUtc");
        if (end <= start) {
            throw new IllegalArgumentException("endUtc must be after startUtc");
        }
        if (end - start > maxWindowMs) {
            throw new IllegalArgumentException("replay window exceeds maximum of " + (maxWindowMs / 60_000L) + " minutes");
        }
        int requested = maxRecords == null ? 1000 : maxRecords;
        int bounded = Math.max(1, Math.min(maxRecordsCap, requested));
        return new ReplayParams(sid, sym, exp, start, end, bounded);
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.trim();
    }

    private static long parseInstant(String value, String field) {
        try {
            return Instant.parse(requireText(value, field)).toEpochMilli();
        } catch (RuntimeException e) {
            throw new IllegalArgumentException(field + " is not a valid ISO-8601 UTC instant");
        }
    }
}

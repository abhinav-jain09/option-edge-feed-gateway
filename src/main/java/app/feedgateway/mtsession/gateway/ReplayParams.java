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
        int maxRecords,
        String runId) {

    // Orchestrator run ids look like r-20260612-093000-160000-7aceb330. Pin the charset (no dots — dots are
    // Kafka topic separators) so a crafted runId can never inject extra topic segments via ReplayTopicResolver
    // (defense-in-depth on top of the resolver's namespace assertion; review finding #6).
    private static final java.util.regex.Pattern RUN_ID = java.util.regex.Pattern.compile("[A-Za-z0-9_-]{1,128}");

    public ReplayParams {
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(symbol, "symbol");
        Objects.requireNonNull(expiry, "expiry");
        runId = (runId == null || runId.isBlank()) ? null : runId.trim();
        if (runId != null && !RUN_ID.matcher(runId).matches()) {
            throw new IllegalArgumentException("runId has an invalid format");
        }
    }

    /**
     * True when this replay is backed by an orchestrated run: the gateway reads the run's local
     * {@code *.replay.<runId>.*} topics (full topic, already windowed) instead of slicing the live
     * topics by timestamp.
     */
    public boolean hasRun() {
        return runId != null;
    }

    /**
     * Parse and validate a replay request. {@code startUtc}/{@code endUtc} are ISO-8601 instants supplied
     * by the caller (the UI's replay bar derives them from the chosen exchange-local window via
     * {@code OptionChainMarketCalendar.exchangeLocalToUtcInstant}); this method performs the authoritative
     * server-side validation — parse, ordering, and maximum-window enforcement — independently of the UI.
     *
     * @throws IllegalArgumentException on any blank field, unparseable instant, non-positive or
     *                                  over-long window, or non-positive record cap.
     */
    public static ReplayParams of(String sessionId, String symbol, String expiry,
                                  String startUtc, String endUtc, Integer maxRecords,
                                  long maxWindowMs, int maxRecordsCap) {
        return of(sessionId, symbol, expiry, startUtc, endUtc, maxRecords, maxWindowMs, maxRecordsCap, null);
    }

    public static ReplayParams of(String sessionId, String symbol, String expiry,
                                  String startUtc, String endUtc, Integer maxRecords,
                                  long maxWindowMs, int maxRecordsCap, String runId) {
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
        return new ReplayParams(sid, sym, exp, start, end, bounded, runId);
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

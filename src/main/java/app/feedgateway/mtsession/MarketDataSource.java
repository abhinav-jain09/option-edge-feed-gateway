package app.feedgateway.mtsession;

import java.util.Locale;
import java.util.Optional;

/**
 * Market-data origin for a selection or record.
 *
 * <p>Per OE-DDD-001 §4.1 the platform runs two modes: {@link #IBKR} (a single fixed shared
 * subscription, SPX/0DTE/45) and {@link #DATABENTO} (the multi-tenant engine).
 */
public enum MarketDataSource {
    IBKR,
    DATABENTO,
    /**
     * A shared underlying feed (e.g. VIX) that is NOT owned by either tenant mode: it is delivered to every
     * session regardless of the session's selected source, and can never be selected as a primary source
     * (OE-DDD-001 §8.6 — shared underlyings). Routing keys for shared underlyings use this source so a
     * DATABENTO session and an IBKR session both index/receive them under the same {@code SHARED|<sym>} key.
     */
    SHARED;

    /**
     * Parse a source token, tolerating the historical aliases used across the feeds
     * ({@code IB}, {@code DB}). Blank/unknown tokens yield {@link Optional#empty()} rather than
     * throwing, so callers can treat "absent in payload" distinctly from "present but mismatched".
     */
    public static Optional<MarketDataSource> parse(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        String v = raw.trim().toUpperCase(Locale.ROOT);
        return switch (v) {
            case "IBKR", "IB" -> Optional.of(IBKR);
            case "DATABENTO", "DB" -> Optional.of(DATABENTO);
            case "SHARED" -> Optional.of(SHARED);
            default -> Optional.empty();
        };
    }
}

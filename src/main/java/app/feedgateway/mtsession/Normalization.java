package app.feedgateway.mtsession;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Canonical normalization of symbol/expiry tokens, mirroring the gateway's existing match logic
 * (OE-DDD-001 §8.6: symbol upper-cased, expiry stripped of {@code '-'} to {@code YYYYMMDD}).
 *
 * <p>Centralising this prevents the classic isolation bug where two code paths normalize
 * differently and a record is delivered to (or withheld from) the wrong session.
 */
public final class Normalization {

    private static final Pattern YYYYMMDD = Pattern.compile("\\d{8}");

    private Normalization() {
    }

    /** Upper-cases and trims a symbol; {@code null} becomes {@code ""}. */
    public static String symbol(String raw) {
        return raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT);
    }

    /** Trims and removes {@code '-'} from an expiry, yielding {@code YYYYMMDD}; {@code null} → {@code ""}. */
    public static String expiry(String raw) {
        return raw == null ? "" : raw.trim().replace("-", "");
    }

    /** True if the value is a well-formed {@code YYYYMMDD} expiry after normalization. */
    public static boolean isValidExpiry(String raw) {
        return YYYYMMDD.matcher(expiry(raw)).matches();
    }
}

package app.feedgateway;

import java.util.Set;
import org.apache.avro.generic.GenericRecord;

/**
 * W0 (ta-books-catalog W0-FRAME-EQUIVALENCE.md FE-1/FE-2): the market-structure {@code ui} and {@code patterns} topics are Confluent Avro.
 * A decoded value is accepted only as one of the two records of its topic and is rendered by {@link AvroFrameJson} to the exact text the
 * JSON producer used to write, which then flows through the unchanged validation, retention, recovery and hello paths. Anything else (a
 * tombstone, an undecodable value, another record, an {@code UNKNOWN} enum symbol, an oversize frame) yields null: counted by the caller,
 * never parsed as JSON, never retained.
 */
final class MarketStructureAvro {
    private static final String NAMESPACE = "com.optionsedge.contracts.marketstructure.";
    private static final Set<String> UI = Set.of(NAMESPACE + "ui.UiProjection", NAMESPACE + "ui.UiUnavailable");
    private static final Set<String> PATTERNS = Set.of(NAMESPACE + "patterns.PatternAnnotationSet", NAMESPACE + "patterns.PatternUnavailable");

    private MarketStructureAvro() {
    }

    /** The frame text of a {@code ui} value, or null. */
    static String uiFrame(Object value) {
        return frame(value, UI, FeedGatewayService.MARKET_STRUCTURE_MAX_BYTES);
    }

    /** The frame text of a {@code patterns} value, or null. */
    static String patternsFrame(Object value) {
        return frame(value, PATTERNS, FeedGatewayService.MARKET_STRUCTURE_PATTERNS_MAX_BYTES);
    }

    private static String frame(Object value, Set<String> records, int maxBytes) {
        return value instanceof GenericRecord record && records.contains(record.getSchema().getFullName())
                ? AvroFrameJson.render(record, maxBytes, true) : null;
    }
}

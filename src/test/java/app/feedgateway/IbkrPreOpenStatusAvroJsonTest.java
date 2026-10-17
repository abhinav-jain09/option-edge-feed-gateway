package app.feedgateway;

import app.kafka.IbkrPreOpenStatusAvro;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The JSON the browser receives for each of the nine pre-open status shapes.
 *
 * <p>THIS IS THE PROPERTY WITH NO RUNTIME SAFETY NET. The records are published as Avro and
 * converted here by {@link AvroJson}; {@code option-chain.js} then reads the payload BY FIELD NAME.
 * If a field it reads were renamed, retyped, or replaced by null, nothing would fail — no exception,
 * no log line, no failed deploy. The pre-open panel would simply stop updating, and the cause would
 * be a field name. So the conversion is asserted here, shape by shape, rather than reasoned about.
 *
 * <p>Each assertion names WHICH browser read it protects, so a future edit can see the cost.
 */
class IbkrPreOpenStatusAvroJsonTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static JsonNode json(GenericRecord record) {
        return AvroJson.toJsonNode(MAPPER, record);
    }

    private static GenericRecord record(Schema schema, Object... nameThenValue) {
        GenericRecord r = new GenericData.Record(schema);
        for (int i = 0; i < nameThenValue.length; i += 2) {
            r.put((String) nameThenValue[i], nameThenValue[i + 1]);
        }
        return r;
    }

    /** The browser's server-clock anchor reads the FIRST present of these, in this order. */
    private static void assertCarriesAClockField(JsonNode node, String expected) {
        for (String candidate : List.of("serverTimeMs", "eventTimeMs", "atMs", "effectiveTimeMs")) {
            if (node.has(candidate) && !node.get(candidate).isNull()) {
                assertEquals(expected, candidate, "the clock anchor would read " + candidate);
                return;
            }
        }
        assertEquals(expected, "(none)", "no clock field present; the anchor would read 0");
    }

    @Test
    void strikeStatusRendersTheFieldsTheBoardReads() {
        JsonNode n = json(record(IbkrPreOpenStatusAvro.STRIKE_STATUS,
                "state", "FRESH", "reasonCodes", List.of("A", "B"), "sessionId", "S",
                "outputGeneration", 3L, "baselineEpoch", 4L, "recordRevision", 5L,
                "eventTimeMs", 1700000000000L));
        assertEquals("FRESH", n.get("state").asText());
        // reasonCodes must be a JSON ARRAY: the browser joins it, and a string would render wrongly.
        assertTrue(n.get("reasonCodes").isArray(), "reasonCodes must stay an array");
        assertEquals(List.of("A", "B"),
                List.of(n.get("reasonCodes").get(0).asText(), n.get("reasonCodes").get(1).asText()));
        assertEquals(4L, n.get("baselineEpoch").asLong(), "the epoch-0 numberless-status check reads this");
        assertEquals(5L, n.get("recordRevision").asLong(), "per-strike staleness reads this");
        assertCarriesAClockField(n, "eventTimeMs");
    }

    @Test
    void pathStatusRendersStateAndItsClockField() {
        JsonNode n = json(record(IbkrPreOpenStatusAvro.PATH_STATUS,
                "state", "ARMED", "reasonCodes", List.of(), "sessionId", "S",
                "outputGeneration", 3L, "eventTimeMs", 1700000000000L));
        assertEquals("ARMED", n.get("state").asText(), "the path plane gates rendering on this");
        assertTrue(n.get("reasonCodes").isArray());
        assertTrue(n.get("reasonCodes").isEmpty(), "an unqualified path carries [], never null");
        assertCarriesAClockField(n, "eventTimeMs");
    }

    /**
     * The FREEZE shape: {@code effectiveAtMs} arms the per-strike switching chips, and its publish
     * time is {@code atMs}, NOT eventTimeMs. Both names are read by the browser.
     */
    @Test
    void pathFreezeRendersEffectiveAtMsAndAtMs() {
        JsonNode n = json(record(IbkrPreOpenStatusAvro.PATH_FREEZE,
                "state", "FREEZE", "effectiveAtMs", 1700000009000L, "sessionId", "S",
                "outputGeneration", 3L, "atMs", 1700000000000L));
        assertEquals("FREEZE", n.get("state").asText());
        assertEquals(1700000009000L, n.get("effectiveAtMs").asLong(),
                "the chips arm when the server clock passes this");
        assertFalse(n.has("eventTimeMs"), "the freeze shape never carried eventTimeMs");
        assertCarriesAClockField(n, "atMs");
    }

    @Test
    void manifestRendersItsStrikesArrayAsTheBrowserWalksIt() {
        GenericRecord one = record(IbkrPreOpenStatusAvro.MANIFEST_STRIKE,
                "strike", "7500", "callConId", 101L, "putConId", 102L);
        GenericRecord two = record(IbkrPreOpenStatusAvro.MANIFEST_STRIKE,
                "strike", "7505", "callConId", 201L, "putConId", 202L);
        GenericData.Array<GenericRecord> strikes = new GenericData.Array<>(
                2, IbkrPreOpenStatusAvro.MANIFEST.getField("strikes").schema());
        strikes.add(one);
        strikes.add(two);
        JsonNode n = json(record(IbkrPreOpenStatusAvro.MANIFEST,
                "manifestEpoch", 1L, "expectedCount", 2, "armSpot", 6300.0,
                "digest", "d", "feedGeneration", 7L, "strikes", strikes));
        assertEquals(2, n.get("expectedCount").asInt());
        // armSpot must stay a NUMBER. A decimal logical type would reach the browser as base64.
        assertTrue(n.get("armSpot").isNumber(), "armSpot must render as a number");
        assertEquals(6300.0, n.get("armSpot").asDouble());
        assertTrue(n.get("strikes").isArray());
        // ORDER is preserved: the predecessor iterated a sorted TreeMap.
        assertEquals("7500", n.get("strikes").get(0).get("strike").asText());
        assertEquals("7505", n.get("strikes").get(1).get("strike").asText());
        // strike stays the canonical TEXT — "7500", never "7500.0" — and the browser coerces it
        // with Number(), so a numeric render would also work but a different TEXT would not.
        assertTrue(n.get("strikes").get(0).get("strike").isTextual());
        assertEquals(101L, n.get("strikes").get(0).get("callConId").asLong());
    }

    /**
     * The generation control's optional fields render as explicit nulls, which is the ONE place the
     * payload differs from the predecessor JSON (which omitted them). Acceptable because the browser
     * ignores __generation records beyond their clock field — asserted here so that reasoning is
     * executable rather than a comment.
     */
    @Test
    void generationRendersOptionalFieldsAsNullsAndKeepsItsClockField() {
        JsonNode open = json(record(IbkrPreOpenStatusAvro.GENERATION,
                "state", "OPEN", "atMs", 1700000000000L, "reason", null, "feedGeneration", null));
        assertEquals("OPEN", open.get("state").asText());
        assertTrue(open.get("reason").isNull(), "absent reason renders null, not a string");
        assertTrue(open.get("feedGeneration").isNull());
        assertCarriesAClockField(open, "atMs");

        JsonNode closed = json(record(IbkrPreOpenStatusAvro.GENERATION,
                "state", "CLOSED", "atMs", 1700000000000L,
                "reason", "FEED_GENERATION_ADOPTED", "feedGeneration", null));
        assertEquals("FEED_GENERATION_ADOPTED", closed.get("reason").asText());

        JsonNode adopted = json(record(IbkrPreOpenStatusAvro.GENERATION,
                "state", "OPEN", "atMs", 1700000000000L, "reason", null, "feedGeneration", 9L));
        assertEquals(9L, adopted.get("feedGeneration").asLong());
    }

    @Test
    void baselineRendersItsContentHash() {
        JsonNode n = json(record(IbkrPreOpenStatusAvro.BASELINE,
                "vendorSession", "20261018", "contentHash", "abc123"));
        // The recovery reader took this by substring search over the JSON; the browser ignores it.
        assertEquals("abc123", n.get("contentHash").asText());
        assertEquals("20261018", n.get("vendorSession").asText());
        assertCarriesAClockField(n, "(none)");
    }

    @Test
    void baselineRevocationRendersReasonAndAtMs() {
        JsonNode n = json(record(IbkrPreOpenStatusAvro.BASELINE_REVOCATION,
                "reason", "BASELINE_INCOMPATIBLE_WITH_REARMED_MANIFEST", "atMs", 1700000000000L));
        assertEquals("BASELINE_INCOMPATIBLE_WITH_REARMED_MANIFEST", n.get("reason").asText());
        assertCarriesAClockField(n, "atMs");
    }

    @Test
    void ownershipRendersItsEffectiveTime() {
        JsonNode n = json(record(IbkrPreOpenStatusAvro.OWNERSHIP,
                "ownerMode", "PREOPEN", "outputGeneration", 3L, "effectiveTimeMs", 1700000000000L));
        assertEquals("PREOPEN", n.get("ownerMode").asText());
        assertCarriesAClockField(n, "effectiveTimeMs");
    }

    /**
     * The heartbeat is the one control record the browser reads in detail: it FAIL-CLOSES the
     * window when beats stop past {@code heartbeatDeadlineMs}. Renaming either field read here
     * would silently disable that fail-close.
     */
    @Test
    void heartbeatRendersTheTwoFieldsTheFailCloseTimerReads() {
        JsonNode n = json(record(IbkrPreOpenStatusAvro.HEARTBEAT,
                "emitterEpoch", 3L, "seq", 11L, "state", "ARMED",
                "effectiveTimeMs", 1700000000000L, "heartbeatDeadlineMs", 1700000060000L,
                "tradeDate", "20261018", "fenceMs", 1700000009000L, "sessionId", "S"));
        assertEquals(1700000060000L, n.get("heartbeatDeadlineMs").asLong(),
                "the browser's fail-close deadline reads this by name");
        assertEquals(1700000000000L, n.get("effectiveTimeMs").asLong(),
                "the browser's beat-receipt reads this by name");
        assertEquals("ARMED", n.get("state").asText());
        assertCarriesAClockField(n, "effectiveTimeMs");
    }

    /** No shape may render a field as base64: that is what an unhandled bytes/decimal field does. */
    @Test
    void noShapeRendersBase64() {
        for (Schema schema : IbkrPreOpenStatusAvro.schemas().values()) {
            for (Schema.Field f : schema.getFields()) {
                Schema type = f.schema();
                assertFalse(type.getType() == Schema.Type.BYTES,
                        schema.getFullName() + "." + f.name() + " is a bytes field — AvroJson would "
                                + "render it as base64 unless it carries the decimal logical type");
            }
        }
    }
}

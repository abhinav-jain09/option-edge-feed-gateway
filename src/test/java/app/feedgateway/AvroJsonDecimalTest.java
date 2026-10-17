package app.feedgateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A decimal logical type must reach the browser as a NUMBER.
 *
 * <p>The Kafka Serialization Rule requires every price on a Kafka value to be a decimal logical type
 * or exact integer ticks, never a double. A decimal is carried as Avro {@code bytes}, and this
 * converter rendered any bytes field as a Jackson binary node — which serializes to base64. So
 * before this, converting ANY priced topic from JSON to Avro would have silently turned
 * {@code "strike":7800} into {@code "strike":"BKYvgA=="} for every UI reading it.
 */
class AvroJsonDecimalTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final Schema SCHEMA = new Schema.Parser().parse("""
            {"type":"record","name":"Priced","namespace":"test",
             "fields":[
               {"name":"strike","type":{"type":"bytes","logicalType":"decimal","precision":18,"scale":4}},
               {"name":"nullableStrike","type":["null",{"type":"bytes","logicalType":"decimal","precision":18,"scale":4}],"default":null},
               {"name":"opaque","type":"bytes"},
               {"name":"status","type":"string"}
             ]}
            """);

    private static GenericRecord record(String strike, String nullableStrike) {
        GenericRecord r = new GenericData.Record(SCHEMA);
        r.put("strike", decimal(strike));
        r.put("nullableStrike", nullableStrike == null ? null : decimal(nullableStrike));
        r.put("opaque", ByteBuffer.wrap(new byte[] {1, 60, 112}));
        r.put("status", "OI_OK");
        return r;
    }

    private static ByteBuffer decimal(String value) {
        return ByteBuffer.wrap(new BigDecimal(value).setScale(4).unscaledValue().toByteArray());
    }

    @Test
    void anIntegralDecimalRendersLikeTheJsonItReplaced() throws Exception {
        String json = MAPPER.writeValueAsString(AvroJson.toJsonNode(MAPPER, record("7800", null), SCHEMA));
        // Not "7800.0000", and emphatically not base64: the hand-built JSON wrote a bare 7800.
        assertTrue(json.contains("\"strike\":7800"), json);
        assertTrue(!json.contains("\"strike\":\""), "a decimal must not render as a string: " + json);
    }

    @Test
    void aFractionalDecimalKeepsItsFraction() throws Exception {
        String json = MAPPER.writeValueAsString(AvroJson.toJsonNode(MAPPER, record("7802.5", null), SCHEMA));
        assertTrue(json.contains("\"strike\":7802.5"), json);
    }

    @Test
    void aDecimalInsideANullableUnionAlsoRendersAsANumber() throws Exception {
        String json = MAPPER.writeValueAsString(
                AvroJson.toJsonNode(MAPPER, record("7800", "6000.25"), SCHEMA));
        assertTrue(json.contains("\"nullableStrike\":6000.25"), json);
    }

    @Test
    void genuinelyOpaqueBytesStillRenderAsBase64() throws Exception {
        // The change is scoped to the decimal logical type. A bytes field with no logical type is
        // still opaque and must keep its existing representation.
        String json = MAPPER.writeValueAsString(AvroJson.toJsonNode(MAPPER, record("7800", null), SCHEMA));
        assertTrue(json.contains("\"opaque\":\""), "opaque bytes must stay base64: " + json);
    }

    @Test
    void withoutTheSchemaADecimalIsStillOpaqueWhichIsWhyTheSchemaIsPassed() throws Exception {
        // The one-argument entry point cannot tell a decimal from opaque bytes — this documents the
        // reason the record walk threads field schemas through rather than dropping them.
        String json = MAPPER.writeValueAsString(AvroJson.toJsonNode(MAPPER, decimal("7800")));
        assertEquals("\"BKYvgA==\"", json, "7800 at scale 4 is 78000000, base64 BKYvgA==");
    }
}

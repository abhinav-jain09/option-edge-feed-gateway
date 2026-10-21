package app.feedgateway;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;
import org.junit.jupiter.api.Test;

/** A logical decimal is rendered at its schema's scale and refused when its value exceeds the schema's precision. */
class AvroDecimalPrecisionTest {
    private static final Schema SCHEMA = new Schema.Parser().parse(
            "{\"type\":\"record\",\"name\":\"D\",\"namespace\":\"t\",\"fields\":[{\"name\":\"x\",\"type\":{\"type\":\"bytes\",\"logicalType\":\"decimal\",\"precision\":5,\"scale\":2}}]}");

    private static GenericRecord of(Object value) {
        GenericRecord r = new GenericData.Record(SCHEMA);
        r.put("x", value);
        return r;
    }

    private static ByteBuffer unscaled(long v) {
        return ByteBuffer.wrap(BigInteger.valueOf(v).toByteArray());
    }

    @Test
    void theScaleOfTheSchemaAndItsPrecisionBoundTheRenderedValue() {
        assertEquals("{\"x\":123.45}", AvroFrameJson.render(of(unscaled(12345)), 1000, true), "5 digits at scale 2");
        assertNull(AvroFrameJson.render(of(unscaled(123456)), 1000, true), "6 digits exceed precision 5");
        assertEquals("{\"x\":60.50}", AvroFrameJson.render(of(new BigDecimal("60.5")), 1000, true), "a reader-converted value keeps the schema scale 2");
        assertNull(AvroFrameJson.render(of(new BigDecimal("60.555")), 1000, true), "a value that does not fit the scale is refused, never rounded");
        assertNull(AvroFrameJson.render(of(new BigDecimal("12345.67")), 1000, true), "precision 7");
    }

    @Test
    void aMapBeyondTheContractIsRefusedBeforeAnythingIsCopied() {
        Schema ms = new Schema.Parser().parse("{\"type\":\"record\",\"name\":\"M\",\"namespace\":\"t\",\"fields\":[{\"name\":\"m\",\"type\":{\"type\":\"map\",\"values\":\"long\"}}]}");
        java.util.Map<String, Long> small = new java.util.HashMap<>(), huge = new java.util.HashMap<>();
        small.put("a", 1L);
        for (int i = 0; i <= AvroFrameJson.MAX_MAP_ENTRIES; i++) {
            huge.put("k" + i, (long) i);
        }
        GenericRecord ok = new GenericData.Record(ms);
        ok.put("m", small);
        GenericRecord big = new GenericData.Record(ms);
        big.put("m", huge);
        assertEquals("{\"m\":{\"a\":1}}", AvroFrameJson.render(ok, 100000, true));
        assertNull(AvroFrameJson.render(big, 10_000_000, true));
    }

    @Test
    void aKeyedCollectionWithARepeatedKeyIsRefused() {
        Schema ks = new Schema.Parser().parse("{\"type\":\"record\",\"name\":\"K\",\"namespace\":\"t\",\"fields\":[{\"name\":\"m\",\"jsonKeyed\":true,\"type\":{\"type\":\"array\",\"items\":{\"type\":\"record\",\"name\":\"E\",\"fields\":[{\"name\":\"key\",\"type\":\"string\"},{\"name\":\"value\",\"type\":\"long\"}]}}}]}");
        Schema es = ks.getField("m").schema().getElementType();
        java.util.function.BiFunction<String, Long, GenericRecord> entry = (k, v) -> { GenericRecord e = new GenericData.Record(es); e.put("key", k); e.put("value", v); return e; };
        GenericRecord ok = new GenericData.Record(ks);
        ok.put("m", java.util.List.of(entry.apply("x", 1L), entry.apply("y", 2L)));
        GenericRecord dup = new GenericData.Record(ks);
        dup.put("m", java.util.List.of(entry.apply("x", 1L), entry.apply("x", 2L)));
        assertEquals("{\"m\":{\"x\":1,\"y\":2}}", AvroFrameJson.render(ok, 1000, true));
        assertNull(AvroFrameJson.render(dup, 1000, true));
    }

    @Test
    void theFrameBoundIsInUtf8BytesNotCharacters() {
        Schema ss = new Schema.Parser().parse("{\"type\":\"record\",\"name\":\"S\",\"namespace\":\"t\",\"fields\":[{\"name\":\"s\",\"type\":\"string\"}]}");
        GenericRecord r = new GenericData.Record(ss);
        r.put("s", "\u20ac".repeat(400));   // 400 characters, 1200 bytes
        assertNull(AvroFrameJson.render(r, 1000, true), "400 characters but 1200 bytes: over a 1000-byte bound");
        assertEquals(true, AvroFrameJson.render(r, 1300, true) != null);
    }
}

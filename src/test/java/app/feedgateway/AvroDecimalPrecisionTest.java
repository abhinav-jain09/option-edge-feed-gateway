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
}

package app.feedgateway;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;
import org.junit.jupiter.api.Test;

/** The rendering rules R1-R9 of W0-FRAME-EQUIVALENCE.md plus the contract rules of 05/06/07, one rule per test, on a crafted schema. */
class AvroFrameJsonTest {
    private static final Schema SCHEMA = new Schema.Parser().parse("""
            {"type":"record","name":"R","namespace":"t","fields":[
              {"name":"b","type":"long"},
              {"name":"m1","type":"int","jsonName":"1m"},
              {"name":"omit","type":["null","string"],"default":null,"jsonOmitNull":true},
              {"name":"keep","type":["null","string"],"default":null},
              {"name":"kind","type":{"type":"enum","name":"K","symbols":["UNKNOWN","UP","DOWN"],"default":"UNKNOWN"}},
              {"name":"price","type":{"type":"bytes","logicalType":"decimal","precision":12,"scale":2}},
              {"name":"ratio","type":["null","long",{"type":"bytes","logicalType":"decimal","precision":18,"scale":4}],"default":null},
              {"name":"measures","type":{"type":"array","items":{"type":"record","name":"E","fields":[
                  {"name":"key","type":"K"},{"name":"value","type":["null","long"],"default":null}]}},"jsonKeyed":true},
              {"name":"params","type":{"type":"map","values":"string"}},
              {"name":"flag","type":"boolean"},
              {"name":"list","type":{"type":"array","items":"string"}}
            ]}""");

    private static GenericRecord record(String kind) {
        Schema k = SCHEMA.getField("kind").schema();
        Schema e = SCHEMA.getField("measures").schema().getElementType();
        GenericRecord r = new GenericData.Record(SCHEMA);
        r.put("b", 9_007_199_254_740_991L);
        r.put("m1", 3);
        r.put("omit", null);
        r.put("keep", null);
        r.put("kind", new GenericData.EnumSymbol(k, kind));
        r.put("price", ByteBuffer.wrap(BigInteger.valueOf(600050).toByteArray()));
        r.put("ratio", ByteBuffer.wrap(BigInteger.valueOf(5000).toByteArray()));
        GenericRecord m1 = new GenericData.Record(e), m2 = new GenericData.Record(e), m3 = new GenericData.Record(e);
        m1.put("key", new GenericData.EnumSymbol(k, "UP"));
        m1.put("value", null);
        m2.put("key", new GenericData.EnumSymbol(k, "DOWN"));
        m2.put("value", -7L);
        m3.put("key", new GenericData.EnumSymbol(k, "UNKNOWN"));
        m3.put("value", 0L);
        r.put("measures", List.of(m1, m2, m3));
        r.put("params", Map.of("zeta", "z", "alpha", "a"));
        r.put("flag", true);
        r.put("list", List.of("a\"b", "é"));
        return r;
    }

    @Test
    void fieldsAreInSchemaOrderUnderTheirJsonNameAndANullOmitFieldIsAbsentButOtherNullsAreWritten() {
        String json = AvroFrameJson.render(record("UP"), 10_000, false);
        assertEquals("{\"b\":9007199254740991,\"1m\":3,\"keep\":null,\"kind\":\"UP\",\"price\":6000.50,\"ratio\":0.5000,"
                + "\"measures\":{\"UP\":null,\"DOWN\":-7,\"UNKNOWN\":0},\"params\":{\"alpha\":\"a\",\"zeta\":\"z\"},\"flag\":true,"
                + "\"list\":[\"a\\\"b\",\"é\"]}", json);
    }

    @Test
    void aDecimalKeepsItsScaleAndIsNeverADouble() {
        GenericRecord r = record("UP");
        r.put("price", ByteBuffer.wrap(BigInteger.valueOf(600000).toByteArray()));
        String json = AvroFrameJson.render(r, 10_000, false);
        assertEquals(true, json.contains("\"price\":6000.00,"), json);
        r.put("price", new BigDecimal("12.30"));   // a deserializer that applied the logical type hands back the BigDecimal
        assertEquals(true, AvroFrameJson.render(r, 10_000, false).contains("\"price\":12.30,"));
    }

    @Test
    void unknownIsRefusedOnlyWhenAskedAndAnOversizeFrameStopsTheRendering() {
        assertNull(AvroFrameJson.render(record("UNKNOWN"), 10_000, true), "R5: a converted topic never carries UNKNOWN");
        assertEquals(true, AvroFrameJson.render(record("UNKNOWN"), 10_000, false).contains("\"kind\":\"UNKNOWN\""), "the chart contracts pass UNKNOWN through");
        assertNull(AvroFrameJson.render(record("UP"), 50, false), "bounded while rendered");
    }

    @Test
    void floatDoubleFixedAndNonDecimalBytesHaveNoContractRendering() {
        for (String type : new String[] {"\"float\"", "\"double\"", "{\"type\":\"fixed\",\"name\":\"F\",\"size\":2}", "\"bytes\""}) {
            Schema s = new Schema.Parser().parse("{\"type\":\"record\",\"name\":\"X\",\"fields\":[{\"name\":\"v\",\"type\":" + type + "}]}");
            GenericRecord r = new GenericData.Record(s);
            r.put("v", type.equals("\"float\"") ? (Object) 1.5f : type.equals("\"double\"") ? (Object) 1.5d
                    : type.equals("\"bytes\"") ? ByteBuffer.wrap(new byte[] {1}) : new GenericData.Fixed(s.getField("v").schema(), new byte[] {1, 2}));
            assertNull(AvroFrameJson.render(r, 1000, false), type);
        }
    }
}

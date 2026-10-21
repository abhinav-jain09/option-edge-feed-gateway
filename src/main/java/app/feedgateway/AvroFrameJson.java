package app.feedgateway;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import java.io.IOException;
import java.io.Writer;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.TreeMap;
import org.apache.avro.LogicalTypes;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;

/**
 * The browser frame of a decoded Avro record, by the rules of ta-books-catalog/W0-FRAME-EQUIVALENCE.md section 2 (R1-R9; reference
 * renderer {@code FrameJson} of market-structure-service) and of the geometry, trade-tool and wave frame contracts (05/06/07 section 1):
 * fields in schema order under their {@code jsonName}, a {@code jsonOmitNull} field omitted when null, a {@code jsonKeyed} array of
 * {@code {key, value}} entries as one object, an enum as its symbol, int and long as JSON integers, a decimal as a plain number at the
 * schema's scale, a union as {@code null} or its bare value, a map as an object (keys sorted: the bytes are a function of the record),
 * no whitespace. The rules are read from the WRITER schema of the record, so a later FULL-compatible field or symbol needs no code change.
 * {@code float}, {@code double}, {@code fixed} and non-decimal {@code bytes} have no contract rendering: the record is refused.
 */
final class AvroFrameJson {
    private static final JsonFactory FACTORY = new JsonFactory().enable(JsonGenerator.Feature.WRITE_BIGDECIMAL_AS_PLAIN);
    private static final String UNKNOWN = "UNKNOWN";

    private AvroFrameJson() {
    }

    /**
     * The frame text, or null when the record cannot be rendered under the contract: an unsupported type, an {@code UNKNOWN} enum symbol
     * when {@code rejectUnknown}, or more than {@code maxChars} UTF-16 units (a lower bound of the UTF-8 size, so a record that fits is
     * never refused here; the rendering stops at the bound instead of allocating the oversize text). The caller measures the UTF-8 size.
     */
    static String render(GenericRecord record, int maxChars, boolean rejectUnknown) {
        BoundedWriter out = new BoundedWriter(maxChars);
        try (JsonGenerator g = FACTORY.createGenerator(out)) {
            write(g, record.getSchema(), record, rejectUnknown);
        } catch (IOException | RuntimeException e) {
            return null;
        }
        return out.toString();
    }

    private static void write(JsonGenerator g, Schema s, Object v, boolean rejectUnknown) throws IOException {
        switch (s.getType()) {
            case RECORD -> {
                GenericRecord r = (GenericRecord) v;
                g.writeStartObject();
                for (Schema.Field f : s.getFields()) {
                    Object x = r.get(f.pos());
                    if (x == null && Boolean.TRUE.equals(f.getObjectProp("jsonOmitNull"))) {
                        continue;
                    }
                    String name = f.getProp("jsonName");
                    g.writeFieldName(name == null ? f.name() : name);
                    if (Boolean.TRUE.equals(f.getObjectProp("jsonKeyed"))) {
                        writeKeyed(g, f.schema().getElementType(), (java.util.List<?>) x, rejectUnknown);
                    } else {
                        write(g, f.schema(), x, rejectUnknown);
                    }
                }
                g.writeEndObject();
            }
            case UNION -> {
                if (v == null) {
                    g.writeNull();
                } else {
                    write(g, s.getTypes().get(GenericData.get().resolveUnion(s, v)), v, rejectUnknown);
                }
            }
            case ARRAY -> {
                g.writeStartArray();
                for (Object item : (java.util.List<?>) v) {
                    write(g, s.getElementType(), item, rejectUnknown);
                }
                g.writeEndArray();
            }
            case MAP -> {
                g.writeStartObject();
                for (Map.Entry<String, Object> e : sorted((Map<?, ?>) v).entrySet()) {
                    g.writeFieldName(e.getKey());
                    write(g, s.getValueType(), e.getValue(), rejectUnknown);
                }
                g.writeEndObject();
            }
            case ENUM -> {
                String symbol = v.toString();
                if (rejectUnknown && UNKNOWN.equals(symbol)) {
                    throw new IOException("enum symbol UNKNOWN");
                }
                g.writeString(symbol);
            }
            case STRING -> g.writeString(v.toString());
            case INT -> g.writeNumber((Integer) v);
            case LONG -> g.writeNumber((Long) v);
            case BOOLEAN -> g.writeBoolean((Boolean) v);
            case BYTES -> g.writeNumber(decimal(s, v));
            default -> throw new IOException("no contract rendering for " + s.getType());
        }
    }

    /** The entries of a keyed array as one object: each entry's key is the member name, its value the member value, in entry order. */
    private static void writeKeyed(JsonGenerator g, Schema entry, java.util.List<?> entries, boolean rejectUnknown) throws IOException {
        g.writeStartObject();
        Schema value = entry.getField("value").schema();
        for (Object e : entries) {
            GenericRecord r = (GenericRecord) e;
            g.writeFieldName(r.get("key").toString());
            write(g, value, r.get("value"), rejectUnknown);
        }
        g.writeEndObject();
    }

    /** {@code new BigDecimal(new BigInteger(bytes), scale)}: never through a double, so 6000.50 keeps its trailing zero (R7). */
    private static BigDecimal decimal(Schema s, Object v) throws IOException {
        if (!(s.getLogicalType() instanceof LogicalTypes.Decimal d)) {
            throw new IOException("bytes without a decimal logical type");
        }
        BigDecimal out;
        if (v instanceof BigDecimal given) {   // already converted by the reader: still rendered at the SCHEMA's scale (6000.5 at scale 2 is 6000.50)
            try {
                out = given.scale() == d.getScale() ? given : given.setScale(d.getScale(), java.math.RoundingMode.UNNECESSARY);
            } catch (ArithmeticException e) {
                throw new IOException("a decimal that does not fit its schema scale " + d.getScale());
            }
        } else {
            out = unscaled(d, v);
        }
        if (out.precision() > d.getPrecision()) {
            throw new IOException("a decimal of precision " + out.precision() + " exceeds its schema precision " + d.getPrecision());
        }
        return out;
    }

    private static BigDecimal unscaled(LogicalTypes.Decimal d, Object v) throws IOException {
        ByteBuffer b = ((ByteBuffer) v).duplicate();
        if (!b.hasRemaining()) {
            throw new IOException("a decimal with no bytes");
        }
        byte[] raw = new byte[b.remaining()];
        b.get(raw);
        return new BigDecimal(new BigInteger(raw), d.getScale());
    }

    private static Map<String, Object> sorted(Map<?, ?> map) {
        Map<String, Object> out = new TreeMap<>();
        for (Map.Entry<?, ?> e : map.entrySet()) {
            out.put(e.getKey().toString(), e.getValue());
        }
        return out;
    }

    /** Collects the text and refuses to grow past the bound. */
    private static final class BoundedWriter extends Writer {
        private final StringBuilder text = new StringBuilder(2048);
        private final int maxChars;

        BoundedWriter(int maxChars) {
            this.maxChars = maxChars;
        }

        @Override
        public void write(char[] buffer, int offset, int length) throws IOException {
            if (text.length() + length > maxChars) {
                throw new IOException("frame exceeds " + maxChars + " characters");
            }
            text.append(buffer, offset, length);
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }

        @Override
        public String toString() {
            return text.toString();
        }
    }
}

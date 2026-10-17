package app.feedgateway;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.avro.LogicalTypes;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericRecord;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.List;
import java.util.Map;

final class AvroJson {
    private AvroJson() {
    }

    static JsonNode toJsonNode(ObjectMapper mapper, Object value) {
        return toJsonNode(mapper, value, null);
    }

    /**
     * @param schema the schema of {@code value} when the caller knows it, else null. It exists for
     *               ONE reason: a {@code bytes} field carrying a {@code decimal} logical type is
     *               indistinguishable from opaque bytes at the value level, and rendering it as
     *               base64 would put {@code "strike":"BKYvgA=="} on the wire to a browser that expects a
     *               number. The Kafka Serialization Rule requires every PRICE to be a decimal logical
     *               type, so without this every converted price would reach the UI as base64.
     */
    static JsonNode toJsonNode(ObjectMapper mapper, Object value, Schema schema) {
        if (value == null) {
            return mapper.nullNode();
        }
        if (value instanceof GenericRecord record) {
            ObjectNode node = mapper.createObjectNode();
            for (Schema.Field field : record.getSchema().getFields()) {
                node.set(field.name(), toJsonNode(mapper, record.get(field.name()), field.schema()));
            }
            return node;
        }
        if (value instanceof BigDecimal decimal) {
            // Some deserializer configurations hand back a decoded BigDecimal rather than bytes.
            return numberNodeFor(mapper, decimal);
        }
        if (value instanceof CharSequence sequence) {
            return mapper.getNodeFactory().textNode(sequence.toString());
        }
        if (value instanceof Integer number) {
            return mapper.getNodeFactory().numberNode(number);
        }
        if (value instanceof Long number) {
            return mapper.getNodeFactory().numberNode(number);
        }
        if (value instanceof Float number) {
            return mapper.getNodeFactory().numberNode(number);
        }
        if (value instanceof Double number) {
            return mapper.getNodeFactory().numberNode(number);
        }
        if (value instanceof Number number) {
            return mapper.getNodeFactory().numberNode(number.doubleValue());
        }
        if (value instanceof Boolean bool) {
            return mapper.getNodeFactory().booleanNode(bool);
        }
        if (value instanceof ByteBuffer buffer) {
            ByteBuffer copy = buffer.asReadOnlyBuffer();
            byte[] bytes = new byte[copy.remaining()];
            copy.get(bytes);
            LogicalTypes.Decimal decimal = decimalOf(schema);
            if (decimal != null) {
                return numberNodeFor(mapper,
                        new BigDecimal(new BigInteger(bytes), decimal.getScale()));
            }
            return mapper.getNodeFactory().binaryNode(bytes);
        }
        if (value instanceof Collection<?> collection) {
            Schema element = schema != null && unwrapNull(schema).getType() == Schema.Type.ARRAY
                    ? unwrapNull(schema).getElementType() : null;
            ArrayNode array = mapper.createArrayNode();
            for (Object item : collection) {
                array.add(toJsonNode(mapper, item, element));
            }
            return array;
        }
        if (value instanceof Map<?, ?> map) {
            Schema valueSchema = schema != null && unwrapNull(schema).getType() == Schema.Type.MAP
                    ? unwrapNull(schema).getValueType() : null;
            ObjectNode node = mapper.createObjectNode();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                node.set(String.valueOf(entry.getKey()), toJsonNode(mapper, entry.getValue(), valueSchema));
            }
            return node;
        }
        return mapper.getNodeFactory().textNode(value.toString());
    }

    /**
     * A decimal as the MINIMAL exact number, so a converted price reads the way the hand-built JSON
     * it replaces read: 7800 rather than 7800.0000, and 7802.5 rather than 7802.5000. Trailing-zero
     * stripping is what makes a serialization change invisible to a browser.
     */
    private static JsonNode numberNodeFor(ObjectMapper mapper, BigDecimal decimal) {
        BigDecimal trimmed = decimal.stripTrailingZeros();
        if (trimmed.scale() <= 0) {
            return mapper.getNodeFactory().numberNode(trimmed.toBigIntegerExact());
        }
        return mapper.getNodeFactory().numberNode(trimmed);
    }

    /** The decimal logical type of a schema, through a nullable union; null when it is not a decimal. */
    private static LogicalTypes.Decimal decimalOf(Schema schema) {
        if (schema == null) {
            return null;
        }
        Schema resolved = unwrapNull(schema);
        return resolved.getLogicalType() instanceof LogicalTypes.Decimal decimal ? decimal : null;
    }

    /** The non-null branch of a nullable union, else the schema itself. */
    private static Schema unwrapNull(Schema schema) {
        if (schema.getType() != Schema.Type.UNION) {
            return schema;
        }
        List<Schema> branches = schema.getTypes();
        for (Schema branch : branches) {
            if (branch.getType() != Schema.Type.NULL) {
                return branch;
            }
        }
        return schema;
    }
}

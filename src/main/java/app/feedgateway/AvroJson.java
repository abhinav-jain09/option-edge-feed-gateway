package app.feedgateway;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericRecord;

import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.Map;

final class AvroJson {
    private AvroJson() {
    }

    static JsonNode toJsonNode(ObjectMapper mapper, Object value) {
        if (value == null) {
            return mapper.nullNode();
        }
        if (value instanceof GenericRecord record) {
            ObjectNode node = mapper.createObjectNode();
            for (Schema.Field field : record.getSchema().getFields()) {
                node.set(field.name(), toJsonNode(mapper, record.get(field.name())));
            }
            return node;
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
            return mapper.getNodeFactory().binaryNode(bytes);
        }
        if (value instanceof Collection<?> collection) {
            ArrayNode array = mapper.createArrayNode();
            for (Object item : collection) {
                array.add(toJsonNode(mapper, item));
            }
            return array;
        }
        if (value instanceof Map<?, ?> map) {
            ObjectNode node = mapper.createObjectNode();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                node.set(String.valueOf(entry.getKey()), toJsonNode(mapper, entry.getValue()));
            }
            return node;
        }
        return mapper.getNodeFactory().textNode(value.toString());
    }
}

package app.feedgateway;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.confluent.kafka.schemaregistry.avro.AvroSchema;
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericRecord;

/**
 * Phase F fixtures: the producer's own schemas (src/test/resources/break, byte-identical copies of break-classifier-service's registered files, ids 2601-2603), the records the
 * producer's real RecordFactory/Engine wrote (producer-records.jsonl: the Avro datum and the contract JSON of each), and synthetic records built from the same schemas.
 */
final class BreakFixtures {
    static final String TOPIC = "t.break";
    static final int MARK_ID = 2601, OUTCOME_ID = 2602, STATUS_ID = 2603;
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String NAMESPACE = "com.optionsedge.contracts.breakclass.";

    private BreakFixtures() {
    }

    /** One producer record: its type, the raw Avro datum and the JSON the contract expects for it. */
    record Real(String set, String type, byte[] datum, JsonNode json) {
        int schemaId() {
            return switch (type) {
                case "BreakMark" -> MARK_ID;
                case "BreakOutcome" -> OUTCOME_ID;
                default -> STATUS_ID;
            };
        }

        byte[] framed() {
            return frame(schemaId(), datum);
        }

        String frameType() {
            return switch (type) {
                case "BreakMark" -> BreakClassificationRelay.MARK_FRAME;
                case "BreakOutcome" -> BreakClassificationRelay.OUTCOME_FRAME;
                default -> BreakClassificationRelay.STATUS_FRAME;
            };
        }
    }

    static String text(String resource) {
        try (InputStream in = BreakFixtures.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("test resource " + resource + " is missing");
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    static Schema schema(String name) {
        return new Schema.Parser().parse(text("/break/" + name + ".avsc"));
    }

    static List<Real> real() throws IOException {
        List<Real> out = new ArrayList<>();
        for (String line : text("/break/producer-records.jsonl").split("\n")) {
            JsonNode r = JSON.readTree(line);
            out.add(new Real(r.get("set").asText(), r.get("type").asText(), Base64.getDecoder().decode(r.get("avro").asText()), r.get("json")));
        }
        return out;
    }

    /** The Confluent wire frame: magic byte 0, the 4-byte schema id, the datum. */
    static byte[] frame(int id, byte[] datum) {
        return ByteBuffer.allocate(5 + datum.length).put((byte) 0).putInt(id).put(datum).array();
    }

    /** The production deserializer against a mock registry holding the three producer schemas under their registered ids, the topic tolerant. */
    static GatewayAvroDeserializer deserializer(String scope) throws Exception {
        SchemaRegistryClient registry = io.confluent.kafka.schemaregistry.testutil.MockSchemaRegistry.getClientForScope(scope);
        registry.register(NAMESPACE + "BreakMark", new AvroSchema(schema("BreakMark")), 1, MARK_ID);
        registry.register(NAMESPACE + "BreakOutcome", new AvroSchema(schema("BreakOutcome")), 1, OUTCOME_ID);
        registry.register(NAMESPACE + "BreakStatus", new AvroSchema(schema("BreakStatus")), 1, STATUS_ID);
        return deserializer(scope, TOPIC);
    }

    static GatewayAvroDeserializer deserializer(String scope, String topic) {
        Map<String, Object> config = new HashMap<>();
        config.put("schema.registry.url", "mock://" + scope);
        config.put("auto.register.schemas", false);
        config.put("value.subject.name.strategy", "io.confluent.kafka.serializers.subject.RecordNameStrategy");
        config.put("specific.avro.reader", "false");
        config.put(GatewayAvroDeserializer.SPX_PRICE_TOPIC, "underlying.spx.price");
        config.put(GatewayAvroDeserializer.TOLERANT_TOPICS, topic);
        GatewayAvroDeserializer d = new GatewayAvroDeserializer();
        d.configure(config, false);
        return d;
    }

    static String hex(long seed) {
        return String.format("%064x", seed);
    }

    /** A mark that passes the boundary: M1..M15 as given, an UNDECIDED S0 mark of {@code sessionDate} with the id {@code hex(seed)}. */
    static GenericRecord mark(String timeframe, String sessionDate, long seed) {
        GenericRecord r = TaFixtures.minimal(schema("BreakMark"));
        TaFixtures.put(r, "schemaVersion", 1);
        TaFixtures.put(r, "classificationId", hex(seed));
        TaFixtures.put(r, "sessionDate", sessionDate);
        TaFixtures.put(r, "timeframe", timeframe);
        TaFixtures.put(r, "state", "UNDECIDED");
        TaFixtures.put(r, "reason", "NO_MEASURED_EDGE");
        TaFixtures.put(r, "vendoredKitHash", hex(7));
        TaFixtures.put(r, "parameterSetHash", hex(8));
        return r;
    }

    static GenericRecord outcome(long markSeed, String label) {
        GenericRecord r = TaFixtures.minimal(schema("BreakOutcome"));
        TaFixtures.put(r, "schemaVersion", 1);
        TaFixtures.put(r, "classificationId", hex(markSeed));
        TaFixtures.put(r, "label", label);
        TaFixtures.put(r, "parameterSetHash", hex(8));
        return r;
    }

    static GenericRecord status(String kind) {
        GenericRecord r = TaFixtures.minimal(schema("BreakStatus"));
        TaFixtures.put(r, "schemaVersion", 1);
        TaFixtures.put(r, "statusKind", kind);
        TaFixtures.put(r, "detail", "");
        TaFixtures.put(r, "parameterSetHash", hex(8));
        return r;
    }
}

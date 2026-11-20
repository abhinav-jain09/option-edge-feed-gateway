package app.feedgateway;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.confluent.kafka.serializers.KafkaAvroSerializer;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * <b>C-80 condition 2, the gateway half: a REAL serialized what-if map record carrying
 * &sect;12f's staircase, decoded by the code that is running on Dev right now.</b>
 *
 * <p>The structural argument is that this is safe — the gateway consumes
 * {@code options.spx.whatif.map} on its Avro consumer with no typed reader, so the reader schema is
 * the writer's, fetched by id, and a field it has never heard of is simply carried through. That
 * argument is correct and it is not a proof, and the reviewer asked for the proof.
 *
 * <p>So this test takes the <b>published schema itself</b> —
 * {@code src/test/resources/whatif-map-record.avsc}, the exact schema
 * {@code WhatIfGridAvro.schemaFor(WhatIfMapRecord.class)} generates, SHA-256
 * {@code f1722ef9c9b4d6249a89ee32a85dbb97b73362b887009e284072b50e2dc3084a}, the one the registry
 * answered {@code is_compatible: true} for against the deployed version [1] — writes a record
 * against it with a populated staircase block, serializes it through a real
 * {@link KafkaAvroSerializer}, and reads it back through {@link GatewayAvroDeserializer} and
 * {@link AvroJson}: the gateway's own decode and its own JSON writer, which is what the browser
 * receives.
 *
 * <p><b>Why this counts as "the deployed code".</b> Build #899 is the image serving Dev, from
 * gateway commit {@code a6c707d}. {@code git diff a6c707d..HEAD} over
 * {@code GatewayAvroDeserializer.java} and {@code AvroJson.java} is <b>empty</b> — the two files
 * this test drives are byte-identical in the running image and in this tree. Nothing about the
 * blocked gateway build affects that: the decode path has not been touched since 2026-11-06.
 */
final class WhatIfStaircaseDecodeTest {

    private static final String TOPIC = "options.spx.whatif.map";
    private static final String REGISTRY = "mock://whatif-staircase-decode";

    @Test
    @DisplayName("C-80(2): the staircase block survives the gateway's decode and reaches its JSON")
    void theStaircaseReachesTheBrowsersJson() throws Exception {
        Schema map = publishedSchema();
        assertEquals(21, map.getFields().size(),
                "this resource must be the 21-field schema §12f produces, or the test is proving "
                        + "something about a shape nobody publishes");

        GenericRecord record = (GenericRecord) minimal(map);
        record.put("symbol", "SPX");
        record.put("expiry", "20261120");
        record.put("staircases", List.of(staircase(map)));

        // ONE mock scope, shared by writer and reader through the URL, exactly as the real pair
        // share the live registry: Confluent resolves `mock://<scope>` to the same in-memory
        // client for both, so the id the serializer registers is the id the gateway looks up.
        // GatewayAvroDeserializer has no constructor taking a client -- it builds its own
        // KafkaAvroDeserializer and is configured by URL -- and that is the point: the test drives
        // it the only way the service does.
        byte[] bytes;
        try (KafkaAvroSerializer serializer = new KafkaAvroSerializer()) {
            Map<String, Object> config = new HashMap<>();
            config.put("schema.registry.url", REGISTRY);
            config.put("auto.register.schemas", true);
            config.put("value.subject.name.strategy",
                    "io.confluent.kafka.serializers.subject.RecordNameStrategy");
            serializer.configure(config, false);
            bytes = serializer.serialize(TOPIC, record);
        }
        assertNotNull(bytes);
        assertTrue(bytes.length > 0, "a record that cannot be written cannot be read");

        Object decoded;
        try (GatewayAvroDeserializer deserializer = new GatewayAvroDeserializer()) {
            Map<String, Object> config = new HashMap<>();
            config.put("schema.registry.url", REGISTRY);
            config.put(GatewayAvroDeserializer.SPX_PRICE_TOPIC, "underlying.spx.price");
            config.put(GatewayAvroDeserializer.VIX_PRICE_TOPIC, "underlying.vix.price");
            deserializer.configure(config, false);
            decoded = deserializer.deserialize(TOPIC, bytes);
        }
        assertNotNull(decoded, "the gateway decoded the record");
        assertFalse(decoded == GatewayAvroDeserializer.UNDECODABLE,
                "the gateway read the record as poison, which is the failure this test exists to "
                        + "rule out");
        assertTrue(decoded instanceof GenericRecord,
                "generic, as the gateway's own comment says it is for every topic but three: "
                        + decoded.getClass());

        // and the JSON a browser is handed, through the gateway's own writer
        JsonNode json = AvroJson.toJsonNode(new ObjectMapper(), decoded);
        assertNotNull(json);
        JsonNode stairs = json.get("staircases");
        assertNotNull(stairs, "the field reached the browser payload at all");
        assertTrue(stairs.isArray() && stairs.size() == 1, "one block, as written: " + stairs);
        JsonNode block = stairs.get(0);
        assertEquals("DOWN", block.get("orientation").asText());
        assertEquals("ARMED", block.get("stairState").asText());
        assertEquals(3, block.get("rungCount").asInt());
        assertEquals(7360.0, block.get("topStep").get("strike").asDouble(), 1e-9);
        assertEquals(77.87, block.get("topStep").get("brake").asDouble(), 1e-9,
                "brake arrives in index delta units per point, the value §12f now publishes");
        assertEquals("BALANCE", block.get("pinTarget").get("stoppedBy").asText());
        assertFalse(block.get("phrase").get("arbitrated").asBoolean(),
                "C-79: the browser can see that nothing has arbitrated this line");
        assertTrue(block.get("opposed").isNull(),
                "RD-19's flag is absent and not false: nothing has compared the two sections yet");
    }

    @Test
    @DisplayName("C-80(2), the web half: the block's own bytes against the proxy's size cap")
    void theBlockIsSmallAgainstTheProxysCap() throws Exception {
        // The web's /api/what-if does NOT parse this JSON. It is `proxyChainRead(...)` returning
        // ResponseEntity<byte[]> -- raw bytes, same-origin, no deserialization -- so there is
        // nothing on the web side that can fail to read a field it has never heard of. What the
        // web side CAN do is refuse the response for being too large: that route carries its own
        // cap, WHAT_IF_PROXY_MAX_BYTES, defaulting to 1 MiB, and its own comment records the
        // measurement it was chosen against -- 97,182 bytes at 71 strikes, so about 180 KB at the
        // 146 strikes the service publishes mid-session. The failure mode there is a 502 with the
        // whole board lost, not a partial render, so "the staircase is small" is worth a NUMBER
        // rather than a word.
        Schema map = publishedSchema();
        GenericRecord block = staircase(map);
        ObjectMapper mapper = new ObjectMapper();
        byte[] blockJson = mapper.writeValueAsBytes(AvroJson.toJsonNode(mapper, block));
        int measured = blockJson.length;

        // Two instances at most, one per orientation (RD-6), and §12b's two ladder blocks are of
        // the same order. Measured here so the margin is a fact and not a reassurance.
        // Measured 2026-11-20: 447 bytes for a fully populated block -- every optional present,
        // the phrase included. The bound is 1 KiB so a future field has room without a false red,
        // and the real statement is the ratio two lines below.
        assertTrue(measured < 1_024,
                "one staircase block's JSON, measured: " + measured + " bytes");
        int worstCase = 2 * measured;
        int capFloor = 262_144;
        assertTrue(worstCase < capFloor / 100,
                "both orientations together are " + worstCase + " bytes against the route's "
                        + "LOWEST permitted cap of " + capFloor + " -- §12f cannot be what pushes "
                        + "this response over");
    }

    // ---- the fixture ---------------------------------------------------------------------------

    private static Schema publishedSchema() throws Exception {
        try (InputStream in = WhatIfStaircaseDecodeTest.class.getClassLoader()
                .getResourceAsStream("whatif-map-record.avsc")) {
            assertNotNull(in, "src/test/resources/whatif-map-record.avsc must be on the classpath");
            return new Schema.Parser().parse(in);
        }
    }

    private static GenericRecord staircase(Schema map) {
        Schema element = map.getField("staircases").schema().getElementType();
        GenericRecord block = new GenericData.Record(element);
        block.put("orientation", "DOWN");
        block.put("topRung", 7360.0);
        block.put("lowRung", 7340.0);
        block.put("rungCount", 3);
        block.put("totalAbsGamma", 2.5e9);
        GenericRecord step = new GenericData.Record(
                nonNull(element.getField("topStep").schema()));
        step.put("strike", 7360.0);
        step.put("absGamma", 8.0e8);
        step.put("brake", 77.87);
        block.put("topStep", step);
        block.put("netPullAtSpot", -29.5);
        block.put("netPullBelowTopStep", -12.25);
        GenericRecord pin = new GenericData.Record(
                nonNull(element.getField("pinTarget").schema()));
        pin.put("strike", 7350.0);
        pin.put("stoppedBy", "BALANCE");
        block.put("pinTarget", pin);
        block.put("touch", true);
        block.put("hold", true);
        block.put("stepsTaken", 1);
        block.put("stairState", "ARMED");
        block.put("opposed", null);
        GenericRecord phrase = new GenericData.Record(
                nonNull(element.getField("phrase").schema()));
        phrase.put("text", "Stair down. Dealer selling with the clock. "
                + "Price going down to 7350. No puts.");
        phrase.put("arbitrated", false);
        block.put("phrase", phrase);
        return block;
    }

    /**
     * The smallest legal value for a schema: null where the union allows it, an empty list for an
     * array, a recursion for a record, and a neutral value for a primitive.
     *
     * <p>Built generically from the schema rather than written out, so this fixture does not have
     * to be edited every time the map record gains a field — and so the test decodes the WHOLE
     * published shape instead of a trimmed one that would not prove anything about the real record.
     */
    private static Object minimal(Schema s) {
        switch (s.getType()) {
            case UNION:
                for (Schema branch : s.getTypes()) {
                    if (branch.getType() == Schema.Type.NULL) {
                        return null;
                    }
                }
                return minimal(s.getTypes().get(0));
            case RECORD:
                GenericRecord r = new GenericData.Record(s);
                for (Schema.Field f : s.getFields()) {
                    r.put(f.name(), minimal(f.schema()));
                }
                return r;
            case ARRAY:
                return new ArrayList<>();
            case MAP:
                return new HashMap<String, Object>();
            case STRING:
                return "";
            case ENUM:
                return new GenericData.EnumSymbol(s, s.getEnumSymbols().get(0));
            case DOUBLE:
                return 0.0;
            case FLOAT:
                return 0.0f;
            case LONG:
                return 0L;
            case INT:
                return 0;
            case BOOLEAN:
                return false;
            default:
                throw new IllegalStateException("no minimal value for " + s.getType());
        }
    }

    private static Schema nonNull(Schema s) {
        if (s.getType() != Schema.Type.UNION) {
            return s;
        }
        for (Schema branch : s.getTypes()) {
            if (branch.getType() != Schema.Type.NULL) {
                return branch;
            }
        }
        throw new IllegalStateException("a union of nothing but null");
    }
}

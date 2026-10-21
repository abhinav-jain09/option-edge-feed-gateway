package app.feedgateway;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.confluent.kafka.serializers.KafkaAvroSerializer;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericDatumReader;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.io.DecoderFactory;
import org.junit.jupiter.api.Test;

/**
 * W0 acceptance (MARKET-STRUCTURE-FRAME-EQUIVALENCE.md FE-5): for every golden fixture the committed Avro datum, decoded under the schema file the
 * manifest names, renders to the committed frame BYTE FOR BYTE and by SHA-256; the same holds through the production wire path
 * (Confluent framing, a registry, {@link GatewayAvroDeserializer}); the hello and tombstone vectors hold through the retention paths.
 * The fixtures are a copy of market-structure-service/src/test/resources/avro-golden at processing commit 3447c51e9.
 */
class W0GoldenFramesTest {
    private static final Path GOLDEN = Path.of("src", "test", "resources", "avro-golden");
    private static final ObjectMapper JSON = new ObjectMapper();

    private static JsonNode manifest() throws IOException {
        return JSON.readTree(Files.readAllBytes(GOLDEN.resolve("MANIFEST.json")));
    }

    private static String sha256(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    private static String simpleName(JsonNode fixture) {
        String full = fixture.get("record").asText();
        return full.substring(full.lastIndexOf('.') + 1);
    }

    private static GenericRecord decode(JsonNode fixture) throws Exception {
        Schema schema = TaFixtures.schema(simpleName(fixture));
        return new GenericDatumReader<GenericRecord>(schema).read(null,
                DecoderFactory.get().binaryDecoder(Files.readAllBytes(GOLDEN.resolve(fixture.get("avroFile").asText())), null));
    }

    @Test
    void everyFixtureRendersToItsCommittedFrameByteForByte() throws Exception {
        JsonNode fixtures = manifest().get("fixtures");
        assertEquals(16, fixtures.size());
        for (JsonNode fixture : fixtures) {
            String name = fixture.get("name").asText();
            byte[] schemaBytes = Files.readAllBytes(Path.of("src", "test", "resources", "ta", simpleName(fixture) + ".avsc"));
            assertEquals(fixture.get("schemaSha256").asText(), sha256(schemaBytes), name + ": the schema copy is the one the fixtures were written under");
            assertEquals(fixture.get("avroSha256").asText(), sha256(Files.readAllBytes(GOLDEN.resolve(fixture.get("avroFile").asText()))), name);
            byte[] frame = Files.readAllBytes(GOLDEN.resolve(fixture.get("frameFile").asText()));
            assertEquals(fixture.get("frameSha256").asText(), sha256(frame), name);
            String rendered = AvroFrameJson.render(decode(fixture), Integer.MAX_VALUE, true);
            assertNotNull(rendered, name);
            assertEquals(new String(frame, StandardCharsets.UTF_8), rendered, name);
            assertEquals(frame.length, StudiesRelay.utf8Length(rendered), name + ": the producer's measured frame length");
        }
    }

    @Test
    void theGatewayAcceptsOnlyTheRecordsOfItsTopicAndMatchesTheFramesThroughTheProductionWirePath() throws Exception {
        Map<String, Object> config = new HashMap<>();
        config.put("schema.registry.url", "mock://w0-golden");
        config.put("auto.register.schemas", true);
        config.put("value.subject.name.strategy", "io.confluent.kafka.serializers.subject.RecordNameStrategy");
        Map<String, Object> deserializerConfig = new HashMap<>(config);
        deserializerConfig.put("specific.avro.reader", "false");
        deserializerConfig.put(GatewayAvroDeserializer.SPX_PRICE_TOPIC, "underlying.spx.price");
        deserializerConfig.put(GatewayAvroDeserializer.TOLERANT_TOPICS, "ui.topic,patterns.topic");
        try (KafkaAvroSerializer serializer = new KafkaAvroSerializer(); GatewayAvroDeserializer deserializer = new GatewayAvroDeserializer()) {
            serializer.configure(config, false);
            deserializer.configure(deserializerConfig, false);
            for (JsonNode fixture : manifest().get("fixtures")) {
                String name = fixture.get("name").asText();
                String topic = fixture.get("topic").asText();
                boolean ui = topic.endsWith(".ui"), patterns = topic.endsWith(".patterns");
                String tolerant = ui ? "ui.topic" : patterns ? "patterns.topic" : "other.topic";
                byte[] wire = serializer.serialize(tolerant, decode(fixture));
                Object decoded = deserializer.deserialize(tolerant, wire);
                String expected = new String(Files.readAllBytes(GOLDEN.resolve(fixture.get("frameFile").asText())), StandardCharsets.UTF_8);
                String frame = ui ? MarketStructureAvro.uiFrame(decoded) : patterns ? MarketStructureAvro.patternsFrame(decoded) : null;
                assertEquals(ui || patterns ? expected : null, frame, name);
                // a record of the OTHER market-structure topic is refused, never rendered
                assertNull(ui ? MarketStructureAvro.patternsFrame(decoded) : MarketStructureAvro.uiFrame(decoded), name);
            }
        }
    }

    @Test
    void anUndecodableValueIsAMarkerNotAFailureAndRetainedJsonIsNeverParsed() throws Exception {
        Map<String, Object> config = new HashMap<>();
        config.put("schema.registry.url", "mock://w0-undecodable");
        config.put("specific.avro.reader", "false");
        config.put(GatewayAvroDeserializer.SPX_PRICE_TOPIC, "underlying.spx.price");
        config.put(GatewayAvroDeserializer.TOLERANT_TOPICS, "ui.topic");
        try (GatewayAvroDeserializer deserializer = new GatewayAvroDeserializer()) {
            deserializer.configure(config, false);
            assertSame(GatewayAvroDeserializer.UNDECODABLE, deserializer.deserialize("ui.topic", "{\"schemaVersion\":1}".getBytes(StandardCharsets.UTF_8)));
            assertSame(GatewayAvroDeserializer.UNDECODABLE, deserializer.deserialize("ui.topic", new byte[] {0, 0, 0}));
            assertSame(GatewayAvroDeserializer.UNDECODABLE, deserializer.deserialize("ui.topic", new byte[] {0, 0, 0, 0, 99, 1, 2}));   // an id the registry does not know
            assertSame(GatewayAvroDeserializer.UNDECODABLE, deserializer.deserialize("ui.topic", new byte[] {0, 0, 0, 0, 99, 1, 2}));   // …remembered, not asked again
            assertNull(deserializer.deserialize("ui.topic", null), "a tombstone stays null");
            assertNull(MarketStructureAvro.uiFrame(GatewayAvroDeserializer.UNDECODABLE));
            assertNull(MarketStructureAvro.uiFrame("{\"schemaVersion\":1,\"service\":\"market-structure\"}"), "JSON text is never accepted as a frame");
        }
    }

    @Test
    void anUnknownEnumSymbolAndAnOversizeFrameAreRefused() throws Exception {
        GenericRecord ui = TaFixtures.uiProjection(1L);
        assertNotNull(MarketStructureAvro.uiFrame(ui));
        TaFixtures.put(ui, "state", "UNKNOWN");
        assertNull(MarketStructureAvro.uiFrame(ui), "R5: UNKNOWN is never rendered on a converted topic");
        GenericRecord big = TaFixtures.uiProjection(1L);
        TaFixtures.put(big, "warning", "x".repeat(FeedGatewayService.MARKET_STRUCTURE_MAX_BYTES));
        assertNull(MarketStructureAvro.uiFrame(big), "the frame is bounded while it is rendered, not after");
        assertNull(AvroFrameJson.render(TaFixtures.minimal(TaFixtures.schema("SequenceRecord")), 10, true));
    }

    @Test
    void theHelloVectorsHoldThroughTheRetentionPath() throws Exception {
        System.setProperty("GATEWAY_MARKET_STRUCTURE_ENABLED", "true");
        System.setProperty("GATEWAY_MARKET_STRUCTURE_PATTERNS_ENABLED", "true");
        try {
            var none = new FeedGatewayService(new GatewaySettings(), JSON, new HpsfGatewayViewMapper(), null);
            String structureNone = Files.readString(GOLDEN.resolve("hello-structure-none.hello.json"));
            assertTrue(none.cvdHelloJson().contains(structureNone), "no ui record retained: the field is present and null");
            String latest = Files.readString(GOLDEN.resolve("hello-structure-latest.hello.json"));
            String frame = Files.readString(GOLDEN.resolve("ui-session-ended.frame.json"));
            assertEquals(",\"structure\":" + frame, latest);
            var s = new FeedGatewayService(new GatewaySettings(), JSON, new HpsfGatewayViewMapper(), null);
            Long asOf = s.validateMarketStructure(frame);
            assertNotNull(asOf, "the golden ui frame passes the unchanged boundary");
            assertTrue(s.retainMarketStructure(asOf, frame));
            assertTrue(s.cvdHelloJson().contains(latest));
            for (String vector : new String[] {"hello-patterns-latest-per-timeframe", "hello-patterns-partial"}) {
                String fragment = Files.readString(GOLDEN.resolve(vector + ".hello.json"));
                var p = new FeedGatewayService(new GatewaySettings(), JSON, new HpsfGatewayViewMapper(), null);
                JsonNode members = JSON.readTree("{" + fragment.substring(1) + "}").get("patterns");
                for (String tf : new String[] {"1m", "5m", "15m"}) {
                    if (members.get(tf).isNull()) {
                        continue;
                    }
                    String text = rawMember(fragment, tf);
                    var boundary = p.validateMarketStructurePatterns(text);
                    assertNotNull(boundary, vector + " " + tf + ": the golden set passes the unchanged boundary");
                    assertEquals(tf, boundary.timeframe());
                    assertTrue(p.retainMarketStructurePatterns(tf, boundary.asOfMs(), text));
                }
                assertTrue(p.cvdHelloJson().contains(fragment), vector);
            }
            // The UNAVAILABLE member is retained and relayed like any other set, under the key of its own timeframe (this set is 15m; the
            // vector once put it under "1m", corrected in processing 3447c51e9).
            String unavailable = Files.readString(GOLDEN.resolve("patterns-set-unavailable.frame.json"));
            var u = new FeedGatewayService(new GatewaySettings(), JSON, new HpsfGatewayViewMapper(), null);
            var boundary = u.validateMarketStructurePatterns(unavailable);
            assertNotNull(boundary);
            assertTrue(u.retainMarketStructurePatterns(boundary.timeframe(), boundary.asOfMs(), unavailable));
            assertTrue(u.cvdHelloJson().contains(",\"patterns\":{\"1m\":null,\"5m\":null,\"15m\":" + unavailable + "}"));
            assertEquals(",\"patterns\":{\"1m\":null,\"5m\":null,\"15m\":" + unavailable + "}", Files.readString(GOLDEN.resolve("hello-patterns-unavailable-member.hello.json")),
                    "the committed vector is exactly the fragment the gateway builds");
            assertTrue(u.cvdHelloJson().contains(Files.readString(GOLDEN.resolve("hello-patterns-unavailable-member.hello.json"))));
        } finally {
            System.clearProperty("GATEWAY_MARKET_STRUCTURE_ENABLED");
            System.clearProperty("GATEWAY_MARKET_STRUCTURE_PATTERNS_ENABLED");
        }
    }

    /** The raw text of one timeframe member of a {@code ,"patterns":{...}} fragment, cut out by the parser's own token offsets. */
    private static String rawMember(String fragment, String tf) throws IOException {
        String object = "{" + fragment.substring(1) + "}";
        try (var parser = JSON.getFactory().createParser(object)) {
            parser.nextToken();
            parser.nextToken();   // the "patterns" field name
            parser.nextToken();   // its object
            while (parser.nextToken() == com.fasterxml.jackson.core.JsonToken.FIELD_NAME) {
                String name = parser.currentName();
                parser.nextToken();
                int from = (int) parser.currentTokenLocation().getCharOffset();
                parser.skipChildren();
                if (name.equals(tf)) {
                    int to = parser.currentToken().isScalarValue() ? (int) parser.currentLocation().getCharOffset() : (int) parser.currentLocation().getCharOffset();
                    return object.substring(from, to);
                }
            }
        }
        throw new IllegalStateException(tf);
    }

    @Test
    void theTombstoneVectorsKeepTheirMeaning() throws Exception {
        JsonNode tombstones = manifest().get("tombstones");
        assertEquals(3, tombstones.size());
        assertEquals("5m", FeedGatewayService.marketStructurePatternsKeyTimeframe(tombstones.get(1).get("key").asText()), "scheduleId|tf withdraws that timeframe");
        assertNull(FeedGatewayService.marketStructurePatternsKeyTimeframe(tombstones.get(2).get("key").asText()), "no timeframe derives from malformed-key");
        assertFalse(tombstones.get(0).get("value").asText().startsWith("{"), "never Avro, never decoded");
    }
}

package app.feedgateway;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.optionsedge.contracts.zerodte.ZeroDteIntelligenceAvro;
import io.confluent.kafka.schemaregistry.testutil.MockSchemaRegistry;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.avro.generic.GenericRecord;
import org.junit.jupiter.api.Test;

/**
 * options.spx.vix-option-inteligence-service.current is Avro. The browser must keep receiving the JSON snapshot it has always received: the gateway's own AvroJson, applied to
 * the decoded record, yields the identical document.
 */
class ZeroDteIntelligenceAvroRelayTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String TOPIC = "options.spx.vix-option-inteligence-service.current";

    private static JsonNode fixture(String name) throws Exception {
        return MAPPER.readTree(Files.readString(Path.of("src/test/resources/zero-dte-intelligence", name + ".json")));
    }

    private static GatewayAvroDeserializer deserializer() {
        MockSchemaRegistry.dropScope("zero-dte-relay");
        ZeroDteIntelligenceAvro.register(MockSchemaRegistry.getClientForScope("zero-dte-relay"));
        Map<String, Object> config = new HashMap<>(ZeroDteIntelligenceAvro.serdeConfig("mock://zero-dte-relay"));
        config.put(GatewayAvroDeserializer.SPX_PRICE_TOPIC, "underlying.spx.price");
        config.put(GatewayAvroDeserializer.TOLERANT_TOPICS, TOPIC);
        GatewayAvroDeserializer d = new GatewayAvroDeserializer();
        d.configure(config, false);
        return d;
    }

    private static byte[] avroBytes(JsonNode snapshot) {
        var serializer = new ZeroDteIntelligenceAvro.Serializer();
        serializer.configure(ZeroDteIntelligenceAvro.serdeConfig("mock://zero-dte-relay"), false);
        return serializer.serialize(TOPIC, snapshot);
    }

    @Test
    void theBrowserJsonIsTheIdenticalDocument() throws Exception {
        try (GatewayAvroDeserializer d = deserializer()) {
            for (String name : List.of("no-data", "good")) {
                JsonNode legacy = fixture(name);
                Object decoded = d.deserialize(TOPIC, avroBytes(legacy));
                GenericRecord record = assertInstanceOf(GenericRecord.class, decoded);
                String rendered = MAPPER.writeValueAsString(AvroJson.toJsonNode(MAPPER, record));
                assertEquals(legacy, MAPPER.readTree(rendered), name + ": AvroJson renders the document the JSON era published");
                assertEquals(legacy.path("atmIv").isNull(), MAPPER.readTree(rendered).path("atmIv").isNull());
                assertEquals(legacy.path("prediction").isNull(), MAPPER.readTree(rendered).path("prediction").isNull());
            }
        }
    }

    @Test
    void retainedJsonIsDroppedAsUndecodableNotParsed() throws Exception {
        try (GatewayAvroDeserializer d = deserializer()) {
            assertSame(GatewayAvroDeserializer.UNDECODABLE, d.deserialize(TOPIC, fixture("good").toString().getBytes(StandardCharsets.UTF_8)));
        }
    }

    @Test
    void validAvroOfAnotherRecordIsNotRelayed() throws Exception {
        String source = Files.readString(Path.of("src/main/java/app/feedgateway/FeedGatewayService.java"));
        String k = body(source, "private String kafkaJson(");
        assertTrue(k.contains("ZeroDteIntelligenceAvro.schema().equals(r.getSchema())") && k.contains(": null;"));
    }

    @Test
    void theBindingLivesOnTheAvroConsumersOnly() throws Exception {
        String source = Files.readString(Path.of("src/main/java/app/feedgateway/FeedGatewayService.java"));
        String json1 = body(source, "private void runJsonStateCacheConsumer()");
        String json2 = body(source, "private void runJsonStateLiveConsumer()");
        String avro1 = body(source, "private void runAvroCacheConsumer()");
        String avro2 = body(source, "private void runAvroLiveConsumer()");
        assertFalse(json1.contains("vixOptionInteligenceTopic") || json1.contains("addZeroDteIntelligenceTopic"), "no JSON cache consumer may bind the topic");
        assertFalse(json2.contains("vixOptionInteligenceTopic") || json2.contains("addZeroDteIntelligenceTopic"), "no JSON live consumer may bind the topic");
        assertTrue(avro1.contains("addZeroDteIntelligenceTopic(topicEvents)"));
        assertTrue(avro2.contains("addZeroDteIntelligenceTopic(topicEvents)"));
        assertTrue(body(source, "private List<String> tolerantTopics()").contains("vixOptionInteligenceTopic()"), "a stray value must not stall the shared Avro consumer");
    }

    private static String body(String source, String signature) {
        int start = source.indexOf(signature);
        assertTrue(start >= 0, signature);
        int next = source.indexOf("\n    private ", start + signature.length());
        return source.substring(start, next < 0 ? source.length() : next);
    }
}

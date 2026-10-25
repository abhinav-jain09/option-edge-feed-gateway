package app.feedgateway;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.optionsedge.contracts.esflow.EsAggressorFlowAvro;
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
 * es.futures.aggressor-flow is Avro. The browser must keep receiving the JSON snapshot it has always received: the gateway's own AvroJson, applied to
 * the decoded record, yields the identical document (key order inside {@code windows} aside — a map — which the page reads by name).
 */
class EsAggressorFlowAvroRelayTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String TOPIC = "es.futures.aggressor-flow";

    private static JsonNode fixture(String name) throws Exception {
        return MAPPER.readTree(Files.readString(Path.of("src/test/resources/es-aggressor-flow", name + ".json")));
    }

    private static GatewayAvroDeserializer deserializer() {
        MockSchemaRegistry.dropScope("es-aggressor-relay");
        EsAggressorFlowAvro.register(MockSchemaRegistry.getClientForScope("es-aggressor-relay"));
        Map<String, Object> config = new HashMap<>(EsAggressorFlowAvro.serdeConfig("mock://es-aggressor-relay"));
        config.put(GatewayAvroDeserializer.SPX_PRICE_TOPIC, "underlying.spx.price");
        config.put(GatewayAvroDeserializer.TOLERANT_TOPICS, TOPIC);
        GatewayAvroDeserializer d = new GatewayAvroDeserializer();
        d.configure(config, false);
        return d;
    }

    private static byte[] avroBytes(JsonNode snapshot) {
        var serializer = new EsAggressorFlowAvro.Serializer();
        serializer.configure(EsAggressorFlowAvro.serdeConfig("mock://es-aggressor-relay"), false);
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
                assertTrue(MAPPER.readTree(rendered).path("lastPrice").isNull() == legacy.path("lastPrice").isNull());
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
        assertTrue(k.contains("EsAggressorFlowAvro.SUBJECT.equals(r.getSchema().getFullName())") && k.contains(": null;"));
    }

    @Test
    void theBindingLivesOnTheAvroConsumersOnly() throws Exception {
        String source = Files.readString(Path.of("src/main/java/app/feedgateway/FeedGatewayService.java"));
        String json1 = body(source, "private void runJsonStateCacheConsumer()");
        String json2 = body(source, "private void runJsonStateLiveConsumer()");
        String avro1 = body(source, "private void runAvroCacheConsumer()");
        String avro2 = body(source, "private void runAvroLiveConsumer()");
        assertFalse(json1.contains("esAggressorFlow") || json1.contains("addEsAggressorFlowTopic"), "no JSON cache consumer may bind the topic");
        assertFalse(json2.contains("esAggressorFlow") || json2.contains("addEsAggressorFlowTopic"), "no JSON live consumer may bind the topic");
        assertTrue(avro1.contains("addEsAggressorFlowTopic(topicEvents)"));
        assertTrue(avro2.contains("addEsAggressorFlowTopic(topicEvents)"));
        assertTrue(body(source, "private List<String> tolerantTopics()").contains("esAggressorFlowTopic()"), "a stray value must not stall the shared Avro consumer");
    }

    private static String body(String source, String signature) {
        int start = source.indexOf(signature);
        assertTrue(start >= 0, signature);
        int next = source.indexOf("\n    private ", start + signature.length());
        return source.substring(start, next < 0 ? source.length() : next);
    }
}

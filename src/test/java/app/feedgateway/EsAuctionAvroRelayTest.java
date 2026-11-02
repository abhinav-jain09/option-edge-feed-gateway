package app.feedgateway;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.optionsedge.contracts.esamt.EsAuctionAvro;
import io.confluent.kafka.schemaregistry.testutil.MockSchemaRegistry;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.avro.generic.GenericRecord;
import org.junit.jupiter.api.Test;

/**
 * es.futures.auction is Avro (contracts EsAuctionMinute, fixed id 3201). The browser, the keyed minute view, the backfill and the duplicate/correction
 * rules all see the JSON text the JSON era published: real dev records (an overnight minute, a mid-session minute, the 16:00 close) are encoded with the
 * contract serializer, read back through the gateway's own Avro deserializer and rendered by the gateway, and must be the identical JSON document (the codec's member order may differ from the engine's at the top level; no consumer depends on member order).
 */
class EsAuctionAvroRelayTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String TOPIC = "es.futures.auction";
    private static final String SRC = "src/main/java/app/feedgateway/FeedGatewayService.java";

    private record Real(String key, String payload) { }

    private static List<Real> real() throws Exception {
        List<Real> out = new ArrayList<>();
        for (String line : Files.readAllLines(Path.of("src/test/resources/es-auction/dev-2026-11-02.jsonl"))) {
            if (line.isBlank()) {
                continue;
            }
            JsonNode n = MAPPER.readTree(line);
            out.add(new Real(n.get("key").asText(), n.get("payload").asText()));
        }
        assertEquals(3, out.size());
        return out;
    }

    private static GatewayAvroDeserializer deserializer() {
        MockSchemaRegistry.dropScope("es-auction-relay");
        EsAuctionAvro.register(MockSchemaRegistry.getClientForScope("es-auction-relay"));
        Map<String, Object> config = new HashMap<>(EsAuctionAvro.serdeConfig("mock://es-auction-relay"));
        config.put(GatewayAvroDeserializer.SPX_PRICE_TOPIC, "underlying.spx.price");
        config.put(GatewayAvroDeserializer.TOLERANT_TOPICS, TOPIC);
        GatewayAvroDeserializer d = new GatewayAvroDeserializer();
        d.configure(config, false);
        return d;
    }

    private static byte[] avroBytes(String payload) throws Exception {
        var serializer = new EsAuctionAvro.Serializer();
        serializer.configure(EsAuctionAvro.serdeConfig("mock://es-auction-relay"), false);
        return serializer.serializeJson(TOPIC, payload);
    }

    private static FeedGatewayService service() {
        return new FeedGatewayService(new GatewaySettings(), new ObjectMapper(), new HpsfGatewayViewMapper(), null);
    }

    @Test
    void theRenderedJsonIsTheIdenticalDocumentTheJsonEraPublished() throws Exception {
        FeedGatewayService s = service();
        try (GatewayAvroDeserializer d = deserializer()) {
            for (Real r : real()) {
                byte[] wire = avroBytes(r.payload());
                assertEquals(0, wire[0], "Confluent magic byte");
                Object decoded = d.deserialize(TOPIC, wire);
                assertInstanceOf(GenericRecord.class, decoded);
                assertEquals(MAPPER.readTree(r.payload()), MAPPER.readTree(s.esAuctionJson(decoded)), r.key() + ": the identical JSON document the JSON era published (member order of the top level may differ)");
            }
        }
    }

    @Test
    void theKeyedMinuteViewAndTheBroadcastSeeTheSameDocumentAsTheJsonEra() throws Exception {
        FeedGatewayService viaAvro = service();
        FeedGatewayService viaJson = service();
        try (GatewayAvroDeserializer d = deserializer()) {
            for (Real r : real()) {
                viaJson.onEsAuctionCacheRecord(r.key(), r.payload());
                viaAvro.onEsAuctionCacheRecord(r.key(), viaAvro.esAuctionJson(d.deserialize(TOPIC, avroBytes(r.payload()))));
            }
        }
        assertEquals(viaJson.esAuctionMinutesCached(), viaAvro.esAuctionMinutesCached());
        assertTrue(viaAvro.esAuctionMinutesCached() > 0);
    }

    @Test
    void retainedJsonAndForeignValuesAreRefusedCountedAndNeverRelayed() throws Exception {
        FeedGatewayService s = service();
        try (GatewayAvroDeserializer d = deserializer()) {
            String json = real().get(0).payload();
            assertSame(GatewayAvroDeserializer.UNDECODABLE, d.deserialize(TOPIC, json.getBytes(StandardCharsets.UTF_8)));
            assertNull(s.esAuctionJson(GatewayAvroDeserializer.UNDECODABLE));
            assertNull(s.esAuctionJson(null));
            assertNull(s.esAuctionJson("{\"a\":1}"));
            assertTrue(s.metrics().contains("gateway_es_auction_refused_total 3"), "refusals are counted on the metric");
        }
    }

    @Test
    void theBindingLivesOnTheAvroConsumersAndTheRefusalIsExposed() throws Exception {
        String source = Files.readString(Path.of(SRC));
        assertTrue(source.contains("EsAuctionAvro.schema().equals(r.getSchema())"), "ONE fixed contract: any other schema is dropped, never relayed");
        assertTrue(source.contains("json = esAuctionJson(record.value());"));
        assertFalse(source.contains("json = stringJson(record.value());   // es-auction"));
    }

    @Test
    void bothAvroConsumersRefuseAMisroutingBindingOfTheAuctionTopic() throws Exception {
        String source = Files.readString(Path.of(SRC));
        for (String sig : List.of("private void runAvroCacheConsumer() {", "private void runAvroLiveConsumer() {")) {
            int at = source.indexOf(sig);
            assertTrue(at >= 0, sig);
            assertTrue(source.startsWith("\n        Map<String, TopicBinding> topicEvents = new UniqueBindings();", at + sig.length()), sig + " must use UniqueBindings");
        }
        Map<String, FeedGatewayService.TopicBinding> m = new FeedGatewayService.UniqueBindings();
        m.put("es.futures.auction", new FeedGatewayService.TopicBinding("DATABENTO", "es-auction"));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
                () -> m.put("es.futures.auction", new FeedGatewayService.TopicBinding("DATABENTO", "es-cvd")));
    }
}

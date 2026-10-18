package app.feedgateway;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.optionsedge.contracts.hpsf.UnderlyingPriceAvro;
import com.optionsedge.contracts.hpsf.UnderlyingPriceEvent;
import io.confluent.kafka.schemaregistry.avro.AvroSchema;
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient;
import io.confluent.kafka.schemaregistry.testutil.MockSchemaRegistry;
import io.confluent.kafka.serializers.KafkaAvroSerializer;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Properties;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.kafka.common.errors.SerializationException;
import org.apache.kafka.common.serialization.Deserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SpxPriceJsonTest {
    private static final String SCOPE = "gw-spx-price";
    private static final String URL = "mock://" + SCOPE;
    private final ObjectMapper mapper = new ObjectMapper();
    private final GatewaySettings settings = new GatewaySettings();
    private final String topic = settings.underlyingSpxPriceTopic();
    private FeedGatewayService service;
    private UnderlyingPriceAvro.EventSerializer writer;
    private SchemaRegistryClient registry;

    @BeforeEach
    void setUp() throws Exception {
        registry = MockSchemaRegistry.getClientForScope(SCOPE);
        registry.register(UnderlyingPriceAvro.SUBJECT, new AvroSchema(UnderlyingPriceAvro.SCHEMA));
        writer = new UnderlyingPriceAvro.EventSerializer(registry);
        writer.configure(UnderlyingPriceAvro.serializerConfig(URL), false);
        service = new FeedGatewayService(settings, mapper, new HpsfGatewayViewMapper(), null);
    }

    @AfterEach
    void tearDown() {
        MockSchemaRegistry.dropScope(SCOPE);
    }

    /** The value deserializer exactly as the service configures it, built the way KafkaConsumer builds it. */
    private Deserializer<?> consumerValueDeserializer(Properties props) throws Exception {
        props.put("schema.registry.url", URL);
        Deserializer<?> d = (Deserializer<?>) Class.forName((String) props.get("value.deserializer")).getDeclaredConstructor().newInstance();
        d.configure(new HashMap<>((java.util.Map) props), false);
        return d;
    }

    private Deserializer<?> liveOrCache() throws Exception {
        var m = FeedGatewayService.class.getDeclaredMethod("avroConsumerProperties", String.class);
        m.setAccessible(true);
        return consumerValueDeserializer((Properties) m.invoke(service, "t"));
    }

    private Deserializer<?> replay(String runId) throws Exception {
        var params = new app.feedgateway.mtsession.gateway.ReplayParams("app:x", "SPX", "20261019", 1000L, 2000L, 10, runId);
        return consumerValueDeserializer(service.replayConsumerProps("app:x", true, params, java.util.Map.of()));
    }

    private static UnderlyingPriceEvent event(double px, Long size) {
        return new UnderlyingPriceEvent(1, "SPX", "2026-10-16T14:30:00.123Z", px, size, "NATIVE_SPX_INDEX",
                "LIVE_INDEX", px, 1_760_625_000_123_000_000L, "epoch-1", 42L, "LIVE_INDEX");
    }

    /** The JSON era's text: field order of the record, compact, doubles as Jackson/Python repr print them. */
    private static String oldJson(double px, Long size) {
        return "{\"schemaVersion\":1,\"symbol\":\"SPX\",\"eventTime\":\"2026-10-16T14:30:00.123Z\",\"price\":" + px
                + ",\"size\":" + size + ",\"source\":\"NATIVE_SPX_INDEX\",\"quality\":\"LIVE_INDEX\",\"canonicalSpot\":" + px
                + ",\"spotEventTimeNs\":1760625000123000000,\"feedEpochId\":\"epoch-1\",\"epochSeq\":42,\"spotQuality\":\"LIVE_INDEX\"}";
    }

    @Test
    void wireBytesThroughTheConfiguredConsumerRenderByteIdenticalJsonForAGridOfPrices() throws Exception {
        double[] grid = {7335.0, 6012.5, 7499.873412, 5000.01, 4999.999999999999, 6000.0, 7000.25, 6543.21, 123456.789012345,
                Math.nextUp(7000.0), Math.nextDown(7000.0), 0.1 + 0.2, 6899.999, 7100.1};
        Deserializer<?> live = liveOrCache();
        Deserializer<?> replay = replay("run-1");
        String replayTopic = app.feedgateway.mtsession.gateway.ReplayTopicResolver.toReplayTopic(topic, "run-1");
        for (double px : grid) {
            for (Long size : new Long[] {null, 5L}) {
                byte[] wire = writer.serialize(topic, event(px, size));
                String old = oldJson(px, size);
                Object typed = live.deserialize(topic, wire);
                assertEquals(event(px, size), typed, "the typed event reaches the code, price " + px);
                assertEquals(old, SpxPriceJson.render(mapper, typed), "price " + px);
                assertEquals(mapper.writeValueAsString(mapper.readTree(old)), SpxPriceJson.render(mapper, typed));
                assertEquals(typed, replay.deserialize(replayTopic, wire), "run-scoped replay binding");
            }
        }
    }

    @Test
    void jsonForeignSchemaAndNonConfluentBytesAreRejectedOnLiveCacheAndReplayBindings() throws Exception {
        Schema other = new Schema.Parser().parse("{\"type\":\"record\",\"name\":\"Other\",\"namespace\":\"x\",\"fields\":[{\"name\":\"a\",\"type\":\"long\"}]}");
        registry.register("x.Other", new AvroSchema(other));
        GenericData.Record foreign = new GenericData.Record(other);
        foreign.put("a", 1L);
        KafkaAvroSerializer foreignWriter = new KafkaAvroSerializer(registry);
        foreignWriter.configure(java.util.Map.of("schema.registry.url", URL, "auto.register.schemas", false,
                "use.latest.version", true, "value.subject.name.strategy",
                "io.confluent.kafka.serializers.subject.RecordNameStrategy"), false);
        byte[] foreignFrame = foreignWriter.serialize(topic, foreign);
        byte[] json = oldJson(6012.5, null).getBytes(StandardCharsets.UTF_8);
        byte[] garbage = {1, 2, 3, 4, 5, 6, 7};
        String replayTopic = app.feedgateway.mtsession.gateway.ReplayTopicResolver.toReplayTopic(topic, "run-1");
        for (Deserializer<?> d : new Deserializer<?>[] {liveOrCache(), replay("run-1")}) {
            for (String t : new String[] {topic, replayTopic}) {
                assertThrows(SerializationException.class, () -> d.deserialize(t, json));
                assertThrows(SerializationException.class, () -> d.deserialize(t, foreignFrame));
                assertThrows(SerializationException.class, () -> d.deserialize(t, garbage));
            }
        }
    }

    @Test
    void otherAvroTopicsStillDecodeGenericRecords() throws Exception {
        Schema other = new Schema.Parser().parse("{\"type\":\"record\",\"name\":\"Other\",\"namespace\":\"x\",\"fields\":[{\"name\":\"a\",\"type\":\"long\"}]}");
        GenericData.Record rec = new GenericData.Record(other);
        rec.put("a", 7L);
        KafkaAvroSerializer w = new KafkaAvroSerializer(registry);
        w.configure(java.util.Map.of("schema.registry.url", URL, "value.subject.name.strategy",
                "io.confluent.kafka.serializers.subject.RecordNameStrategy"), false);
        Object back = liveOrCache().deserialize(settings.databentoGexTopic(), w.serialize(settings.databentoGexTopic(), rec));
        assertEquals(rec, back);
    }

    @Test
    void tombstoneIsNullAndATypedValueIsRequired() {
        assertNull(SpxPriceJson.render(mapper, null));
        assertThrows(IllegalStateException.class, () -> SpxPriceJson.render(mapper, oldJson(6012.5, null)));
    }
}

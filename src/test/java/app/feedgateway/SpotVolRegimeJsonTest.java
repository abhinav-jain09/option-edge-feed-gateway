package app.feedgateway;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.optionsedge.contracts.spotvolregime.Regime;
import com.optionsedge.contracts.spotvolregime.SpotVolRegimeAvro;
import com.optionsedge.contracts.spotvolregime.SpotVolRegimeSnapshot;
import com.optionsedge.contracts.spotvolregime.SpxDirection;
import com.optionsedge.contracts.spotvolregime.StrikeBand;
import com.optionsedge.contracts.spotvolregime.StrikeMark;
import com.optionsedge.contracts.spotvolregime.VixDirection;
import com.optionsedge.contracts.spotvolregime.WindowStats;
import io.confluent.kafka.schemaregistry.avro.AvroSchema;
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient;
import io.confluent.kafka.schemaregistry.testutil.MockSchemaRegistry;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Properties;
import org.apache.kafka.common.errors.SerializationException;
import org.apache.kafka.common.serialization.Deserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SpotVolRegimeJsonTest {
    private static final String SCOPE = "gw-spot-vol-regime";
    private static final String URL = "mock://" + SCOPE;
    private final ObjectMapper mapper = new ObjectMapper();
    private final GatewaySettings settings = new GatewaySettings();
    private final String topic = settings.spotVolRegimeTopic();
    private FeedGatewayService service;
    private SpotVolRegimeAvro.SnapshotSerializer writer;
    private SchemaRegistryClient registry;

    @BeforeEach
    void setUp() throws Exception {
        registry = MockSchemaRegistry.getClientForScope(SCOPE);
        registry.register(SpotVolRegimeAvro.SUBJECT, new AvroSchema(SpotVolRegimeAvro.SCHEMA));
        writer = new SpotVolRegimeAvro.SnapshotSerializer(registry);
        writer.configure(SpotVolRegimeAvro.serializerConfig(URL), false);
        service = new FeedGatewayService(settings, mapper, new HpsfGatewayViewMapper(), null);
    }

    @AfterEach
    void tearDown() {
        MockSchemaRegistry.dropScope(SCOPE);
    }

    /** The value deserializer exactly as the service configures it for the Avro cache and live consumers. */
    private Deserializer<?> consumerValueDeserializer() throws Exception {
        Method m = FeedGatewayService.class.getDeclaredMethod("avroConsumerProperties", String.class);
        m.setAccessible(true);
        Properties props = (Properties) m.invoke(service, "t");
        props.put("schema.registry.url", URL);
        Deserializer<?> d = (Deserializer<?>) Class.forName((String) props.get("value.deserializer")).getDeclaredConstructor().newInstance();
        d.configure(new HashMap<>((java.util.Map) props), false);
        return d;
    }

    private static final long AS_OF = 1_760_625_000_000L;

    private static SpotVolRegimeSnapshot frame() {
        StrikeBand band = new StrikeBand(StrikeBand.CURRENT_SCHEMA_VERSION, "2026-10-16", 5, List.of(
                new StrikeMark(7405, Regime.DIVERGENT_UP, AS_OF - 60_000L), new StrikeMark(7410, Regime.DIVERGENT_UP, AS_OF)));
        return new SpotVolRegimeSnapshot(SpotVolRegimeSnapshot.CURRENT_SCHEMA_VERSION, "SPX", AS_OF, 7412.25, AS_OF - 1_000L, 16.55,
                AS_OF - 2_000L,
                new WindowStats(60_000L, 0.0123456789012345, 0.30000000000000004, SpxDirection.UP, VixDirection.UP, null),
                new WindowStats(300_000L, -1.0E-5, null, SpxDirection.DOWN, VixDirection.NO_CONFIRMATION, null),
                null, null, Regime.DIVERGENT_UP, AS_OF - 90_000L, List.of("PERSISTING_CONFIRMED_UP", "VIX_STALE"), band, "svr-1",
                AS_OF + 15L, 42L);
    }

    private static SpotVolRegimeSnapshot warmup() {
        return new SpotVolRegimeSnapshot(SpotVolRegimeSnapshot.CURRENT_SCHEMA_VERSION, "SPX", AS_OF, null, null, null, null, null,
                null, null, null, null, null, List.of("WARMUP_SPX_5M"), null, "svr-1", AS_OF + 15L, 1L);
    }

    @Test
    void renderedJsonIsTheLiteralTextTheJsonEraProducerWrote() throws Exception {
        // Hand-written from the record's field order and Jackson's number printing: the JSON era's bytes, not a re-derivation.
        String expected = "{\"schemaVersion\":4,\"symbol\":\"SPX\",\"asOfEventTimeMs\":1760625000000,\"spxLast\":7412.25,"
                + "\"spxEventTimeMs\":1760624999000,\"vixLast\":16.55,\"vixEventTimeMs\":1760624998000,"
                + "\"window1m\":{\"windowMs\":60000,\"spxDeltaPct\":0.0123456789012345,\"vixDeltaPts\":0.30000000000000004,"
                + "\"spxDirection\":\"UP\",\"vixDirection\":\"UP\",\"regime\":\"DIVERGENT_UP\"},"
                + "\"window5m\":{\"windowMs\":300000,\"spxDeltaPct\":-1.0E-5,\"vixDeltaPts\":null,"
                + "\"spxDirection\":\"DOWN\",\"vixDirection\":\"UNKNOWN\",\"regime\":\"UNKNOWN\"},"
                + "\"combinedRegime\":\"UNKNOWN\",\"conviction\":\"NA\",\"declaredRegime\":\"DIVERGENT_UP\","
                + "\"declaredSinceMs\":1760624910000,\"reasonCodes\":[\"PERSISTING_CONFIRMED_UP\",\"VIX_STALE\"],"
                + "\"strikeBand\":{\"schemaVersion\":1,\"sessionDate\":\"2026-10-16\",\"strikeIncrement\":5,\"marks\":["
                + "{\"strike\":7405,\"regime\":\"DIVERGENT_UP\",\"markedAtEventTimeMs\":1760624940000},"
                + "{\"strike\":7410,\"regime\":\"DIVERGENT_UP\",\"markedAtEventTimeMs\":1760625000000}]},"
                + "\"codeVersion\":\"svr-1\",\"producedAtMs\":1760625000015,\"seq\":42}";
        assertEquals(expected, new ObjectMapper().writeValueAsString(frame()), "the fixture itself is the JSON-era text");
        Object typed = consumerValueDeserializer().deserialize(topic, writer.serialize(topic, frame()));
        assertEquals(expected, SpotVolRegimeJson.render(typed));
    }

    @Test
    void wireBytesThroughTheConfiguredConsumerRenderByteIdenticalJsonForEveryShape() throws Exception {
        Deserializer<?> live = consumerValueDeserializer();
        for (SpotVolRegimeSnapshot s : List.of(frame(), warmup())) {
            Object typed = live.deserialize(topic, writer.serialize(topic, s));
            assertEquals(s, typed);
            assertEquals(new ObjectMapper().writeValueAsString(s), SpotVolRegimeJson.render(typed));
        }
    }

    @Test
    void theServiceRendersTheSpotVolRegimeEventThroughKafkaJson() throws Exception {
        Method m = FeedGatewayService.class.getDeclaredMethod("kafkaJson", String.class, boolean.class, Object.class);
        m.setAccessible(true);
        Object typed = consumerValueDeserializer().deserialize(topic, writer.serialize(topic, frame()));
        assertEquals(new ObjectMapper().writeValueAsString(frame()), m.invoke(service, "spot-vol-regime", true, typed));
        assertNull(m.invoke(service, "spot-vol-regime", true, null));
    }

    @Test
    void jsonForeignSchemaAndNonConfluentBytesAreRejected() throws Exception {
        byte[] json = new ObjectMapper().writeValueAsBytes(frame());
        byte[] garbage = {1, 2, 3, 4, 5, 6, 7};
        Deserializer<?> d = consumerValueDeserializer();
        assertThrows(SerializationException.class, () -> d.deserialize(topic, json));
        assertThrows(SerializationException.class, () -> d.deserialize(topic, garbage));
    }

    @Test
    void otherAvroTopicsStillDecodeGenericRecordsAndTheRegimeTopicIsNeverGeneric() throws Exception {
        org.apache.avro.Schema other = new org.apache.avro.Schema.Parser().parse(
                "{\"type\":\"record\",\"name\":\"Other\",\"namespace\":\"x\",\"fields\":[{\"name\":\"a\",\"type\":\"long\"}]}");
        org.apache.avro.generic.GenericData.Record rec = new org.apache.avro.generic.GenericData.Record(other);
        rec.put("a", 7L);
        io.confluent.kafka.serializers.KafkaAvroSerializer w = new io.confluent.kafka.serializers.KafkaAvroSerializer(registry);
        w.configure(java.util.Map.of("schema.registry.url", URL, "value.subject.name.strategy",
                "io.confluent.kafka.serializers.subject.RecordNameStrategy"), false);
        Deserializer<?> d = consumerValueDeserializer();
        assertEquals(rec, d.deserialize(settings.databentoGexTopic(), w.serialize(settings.databentoGexTopic(), rec)));
        // A foreign record on the regime topic is refused rather than rendered generically.
        assertThrows(SerializationException.class, () -> d.deserialize(topic, w.serialize(topic, rec)));
    }

    @Test
    void tombstoneIsNullAndATypedValueIsRequired() {
        assertNull(SpotVolRegimeJson.render(null));
        assertThrows(IllegalStateException.class, () -> SpotVolRegimeJson.render("{\"symbol\":\"SPX\"}"));
    }

    @Test
    void spotVolRegimeIsOnTheAvroConsumersAndOffTheJsonStateOnes() throws Exception {
        // The topic must be consumed ONLY where the Avro value deserializer is configured; on the string consumers the
        // Avro frame would be cached as garbled text and silently dropped on delivery.
        String source = new String(java.nio.file.Files.readAllBytes(java.nio.file.Path.of(
                "src/main/java/app/feedgateway/FeedGatewayService.java")), StandardCharsets.UTF_8);
        for (String consumer : List.of("runAvroCacheConsumer", "runAvroLiveConsumer", "runJsonStateCacheConsumer", "runJsonStateLiveConsumer")) {
            int start = source.indexOf("private void " + consumer + "()");
            int end = source.indexOf("\n    private void ", start + 1);
            String body = source.substring(start, end);
            boolean avro = consumer.startsWith("runAvro");
            assertEquals(avro, body.contains("settings.spotVolRegimeTopic()"), consumer);
        }
        assertTrue(source.contains("GatewayAvroDeserializer.SPOT_VOL_REGIME_TOPIC"));
        assertFalse(source.contains("\"spot-vol-regime\".equals(event) ? SpotVolRegimeJson.render(value) : stringJson"));
    }
}

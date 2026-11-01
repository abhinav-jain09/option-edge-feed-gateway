package app.feedgateway;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.optionsedge.contracts.strikeinvasion.StrikeInvasionAvro;
import com.optionsedge.contracts.strikeinvasion.StrikeInvasionSchemas;
import com.optionsedge.contracts.strikeinvasion.StrikeInvasionSnapshot;
import io.confluent.kafka.schemaregistry.avro.AvroSchema;
import io.confluent.kafka.schemaregistry.testutil.MockSchemaRegistry;
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.kafka.common.errors.SerializationException;
import org.apache.kafka.common.serialization.Serde;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** strike-invasion is Avro on the wire; the browser-bound JSON must stay byte-identical to the JSON era. */
class StrikeInvasionAvroReaderTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String SCOPE = "gateway-strike-invasion-reader";
    private static final String URL = "mock://" + SCOPE;
    private static final String TOPIC = new GatewaySettings().strikeInvasionTopic();

    private Serde<StrikeInvasionSnapshot> producer;
    private GatewayAvroDeserializer consumer;
    private FeedGatewayService service;

    @BeforeEach
    void setUp() throws Exception {
        SchemaRegistryClient registry = MockSchemaRegistry.getClientForScope(SCOPE);
        registry.register(StrikeInvasionAvro.SNAPSHOT_SUBJECT, new AvroSchema(StrikeInvasionSchemas.SNAPSHOT));
        producer = StrikeInvasionAvro.snapshotSerde(URL);
        consumer = new GatewayAvroDeserializer();
        Map<String, Object> cfg = new HashMap<>();
        cfg.put("schema.registry.url", URL);
        cfg.put("specific.avro.reader", "false");
        cfg.put("value.subject.name.strategy", "io.confluent.kafka.serializers.subject.RecordNameStrategy");
        cfg.put(GatewayAvroDeserializer.SPX_PRICE_TOPIC, new GatewaySettings().underlyingSpxPriceTopic());
        consumer.configure(cfg, false);
        service = new FeedGatewayService(new GatewaySettings(), new ObjectMapper(), new HpsfGatewayViewMapper(), null);
    }

    private static StrikeInvasionSnapshot snapshot(String direction, double strike, Double invalidation) {
        return new StrikeInvasionSnapshot(StrikeInvasionSnapshot.SCHEMA_VERSION, "SPX", direction, strike, 1_760_625_000_123L,
                "FAILED_INVASION", 2, "2 (F,F)", 61.3, "CONTESTED", 14.5, 40.1, 55.0, 12.0, 8.8, 70.0, 30.0, 25.5, 66.6,
                50.0, 40.0, 30.0, 20.0, 10.0, 5.0, 0.4, "SHORT_CALL_CANDIDATE", "A", invalidation, List.of("R1", "R2"), "BASE",
                "strike-invasion-1.0.0", "cfg123", 12.5, -3.25, 410, 1500L, true, "OK", "wall-1:abcdef012345", false,
                StrikeInvasionSnapshot.CALIBRATION_GATE1_NO_PASS, 1_760_625_000_123L);
    }

    private static StrikeInvasionSnapshot sparse() {
        return new StrikeInvasionSnapshot(5, "SPX", "DOWN", 7500.0, 5L, "DORMANT", 0, "0 ()", null, "NONE", null, null, null,
                null, null, null, null, null, null, null, null, null, null, null, null, null, "NONE", "-", null, List.of(),
                "RELAXED", "m", "h", null, null, null, null, null, null, null, false, "ref", 5L);
    }

    private String browserJson(byte[] bytes) throws Exception {
        Object decoded = consumer.deserialize(TOPIC, bytes);
        Method m = FeedGatewayService.class.getDeclaredMethod("kafkaJson", String.class, boolean.class, Object.class);
        m.setAccessible(true);
        try {
            return (String) m.invoke(service, "strike-invasion", true, decoded);
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof RuntimeException r) throw r;
            throw e;
        }
    }

    @Test
    void avroSnapshotsReachTheBrowserAsTheByteIdenticalJacksonJson() throws Exception {
        for (StrikeInvasionSnapshot s : List.of(snapshot("UP", 7570.0, 7575.0), snapshot("DOWN", 7572.5, null),
                snapshot("UP", 0.1 + 0.2, 1.0E7 + 0.5), sparse())) {
            String json = browserJson(producer.serializer().serialize(TOPIC, s));
            assertEquals(JSON.writeValueAsString(s), json);
        }
        assertTrue(browserJson(producer.serializer().serialize(TOPIC, snapshot("UP", 7570.0, 7575.0)))
                .contains("\"strike\":7570.0,"));
    }

    @Test
    void jsonBytesAreRejectedLoudly() {
        byte[] json = "{\"symbol\":\"SPX\",\"strike\":7570.0,\"direction\":\"UP\"}".getBytes(StandardCharsets.UTF_8);
        assertThrows(SerializationException.class, () -> consumer.deserialize(TOPIC, json));
    }

    @Test
    void aTombstoneRendersNoJsonAndAStringOrForeignValueFailsLoud() throws Exception {
        assertNull(browserJson(null));
        Method m = FeedGatewayService.class.getDeclaredMethod("kafkaJson", String.class, boolean.class, Object.class);
        m.setAccessible(true);
        InvocationTargetException e = assertThrows(InvocationTargetException.class,
                () -> m.invoke(service, "strike-invasion", true, "{\"symbol\":\"SPX\"}"));
        assertTrue(e.getCause() instanceof IllegalStateException, String.valueOf(e.getCause()));
    }
}

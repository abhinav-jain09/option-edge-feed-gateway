package app.feedgateway;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.optionsedge.contracts.hpsf.VixUnderlyingPriceAvro;
import com.optionsedge.contracts.hpsf.VixUnderlyingPriceEvent;
import io.confluent.kafka.schemaregistry.avro.AvroSchema;
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient;
import io.confluent.kafka.schemaregistry.testutil.MockSchemaRegistry;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Properties;
import org.apache.kafka.common.errors.SerializationException;
import org.apache.kafka.common.serialization.Deserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** underlying.vix.price is Avro: the gateway decodes the registered record and re-emits the JSON text of the JSON era. */
class VixPriceJsonTest {
    private static final String SCOPE = "gw-vix-price";
    private static final String URL = "mock://" + SCOPE;
    private final ObjectMapper mapper = new ObjectMapper();
    private final GatewaySettings settings = new GatewaySettings();
    private final String topic = settings.ibkrVixPriceTopic();
    private FeedGatewayService service;
    private VixUnderlyingPriceAvro.EventSerializer writer;

    @BeforeEach
    void setUp() throws Exception {
        SchemaRegistryClient registry = MockSchemaRegistry.getClientForScope(SCOPE);
        registry.register(VixUnderlyingPriceAvro.SUBJECT, new AvroSchema(VixUnderlyingPriceAvro.SCHEMA));
        writer = new VixUnderlyingPriceAvro.EventSerializer(registry);
        writer.configure(VixUnderlyingPriceAvro.serializerConfig(URL), false);
        service = new FeedGatewayService(settings, mapper, new HpsfGatewayViewMapper(), null);
    }

    @AfterEach
    void tearDown() {
        MockSchemaRegistry.dropScope(SCOPE);
    }

    private Deserializer<?> consumerValueDeserializer() throws Exception {
        var m = FeedGatewayService.class.getDeclaredMethod("avroConsumerProperties", String.class);
        m.setAccessible(true);
        Properties props = (Properties) m.invoke(service, "t");
        props.put("schema.registry.url", URL);
        Deserializer<?> d = (Deserializer<?>) Class.forName((String) props.get("value.deserializer")).getDeclaredConstructor().newInstance();
        d.configure(new HashMap<>((java.util.Map) props), false);
        return d;
    }

    private static VixUnderlyingPriceEvent parity() {
        return new VixUnderlyingPriceEvent(1, "VIX", 17.4, "2026-11-06T14:30:00.123Z", 1_762_439_400_123_000_000L, "20261118",
                "SYNTHETIC_OPTION_SPOT", "SYNTHETIC", "VIX_MONTHLY_PARITY_NO_DF", 3L, 0.02, 1500L, 0.015226,
                17.5, 0.825, 0.875, 0.925, 0.975, 0.05, 0.05, 0.1, "epoch-1", 7L);
    }

    private static VixUnderlyingPriceEvent index() {
        return new VixUnderlyingPriceEvent(1, "VIX", 15.99, "2026-11-06T14:30:00.123Z", 1_762_439_400_123_000_000L, "20261118",
                "IBKR_INDEX", "LIVE", "IBKR_VIX_INDEX", null, null, null, null, null, null, null, null, null, null, null, null, "epoch-1", 8L);
    }

    /** The text the Rust feed wrote in the JSON era for these two ticks (the browser contract). */
    private static final String OLD_PARITY = "{\"schemaVersion\":1,\"symbol\":\"VIX\",\"price\":17.4,\"eventTime\":\"2026-11-06T14:30:00.123Z\","
            + "\"sourceEventTimeNs\":1762439400123000000,\"expiry\":\"20261118\",\"source\":\"SYNTHETIC_OPTION_SPOT\",\"quality\":\"SYNTHETIC\","
            + "\"derivation\":\"VIX_MONTHLY_PARITY_NO_DF\",\"pairsUsed\":3,\"dispersion\":0.02,\"oldestLegAgeMs\":1500,"
            + "\"parityDiscountErrorEstVolPts\":0.015226,\"atmStrike\":17.5,\"atmCallBid\":0.825,\"atmCallAsk\":0.875,\"atmPutBid\":0.925,"
            + "\"atmPutAsk\":0.975,\"atmCallSpread\":0.05,\"atmPutSpread\":0.05,\"maxLegSpread\":0.1,\"feedEpochId\":\"epoch-1\",\"epochSeq\":7}";
    private static final String OLD_INDEX = "{\"schemaVersion\":1,\"symbol\":\"VIX\",\"price\":15.99,\"eventTime\":\"2026-11-06T14:30:00.123Z\","
            + "\"sourceEventTimeNs\":1762439400123000000,\"expiry\":\"20261118\",\"source\":\"IBKR_INDEX\",\"quality\":\"LIVE\","
            + "\"derivation\":\"IBKR_VIX_INDEX\",\"atmStrike\":null,\"atmCallBid\":null,\"atmCallAsk\":null,\"atmPutBid\":null,\"atmPutAsk\":null,"
            + "\"atmCallSpread\":null,\"atmPutSpread\":null,\"maxLegSpread\":null,\"feedEpochId\":\"epoch-1\",\"epochSeq\":8}";

    @Test
    void wireBytesThroughTheConfiguredConsumerRenderTheJsonEraText() throws Exception {
        Deserializer<?> live = consumerValueDeserializer();
        String replayTopic = app.feedgateway.mtsession.gateway.ReplayTopicResolver.toReplayTopic(topic, "run-1");
        for (String t : new String[] {topic, replayTopic}) {
            assertEquals(OLD_PARITY, VixPriceJson.render(mapper, live.deserialize(t, writer.serialize(topic, parity()))), t);
            assertEquals(OLD_INDEX, VixPriceJson.render(mapper, live.deserialize(t, writer.serialize(topic, index()))), t);
        }
    }

    @Test
    void jsonBytesAreNeverDataAndATombstoneIsNoJson() throws Exception {
        Deserializer<?> live = consumerValueDeserializer();
        byte[] json = OLD_PARITY.getBytes(StandardCharsets.UTF_8);
        assertThrows(SerializationException.class, () -> live.deserialize(topic, json));
        assertThrows(SerializationException.class, () -> live.deserialize(topic, new byte[] {1, 2, 3, 4, 5, 6}));
        assertNull(VixPriceJson.render(mapper, null));
        assertThrows(IllegalStateException.class, () -> VixPriceJson.render(mapper, "not an event"));
    }
}

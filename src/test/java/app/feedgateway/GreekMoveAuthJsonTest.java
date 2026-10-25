package app.feedgateway;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.optionsedge.contracts.greekmoveauth.AuthClass;
import com.optionsedge.contracts.greekmoveauth.EndgameState;
import com.optionsedge.contracts.greekmoveauth.GreekMoveAuthAvro;
import com.optionsedge.contracts.greekmoveauth.GreekName;
import com.optionsedge.contracts.greekmoveauth.GreekVote;
import com.optionsedge.contracts.greekmoveauth.MoveAuthenticityVerdict;
import com.optionsedge.contracts.greekmoveauth.MoveDirection;
import com.optionsedge.contracts.greekmoveauth.MoveTrackSegment;
import com.optionsedge.contracts.greekmoveauth.MoveTrailSample;
import com.optionsedge.contracts.greekmoveauth.SourceOffset;
import com.optionsedge.contracts.greekmoveauth.ThetaVerdict;
import com.optionsedge.contracts.greekmoveauth.TransitionMarker;
import com.optionsedge.contracts.greekmoveauth.Verdict;
import io.confluent.kafka.schemaregistry.avro.AvroSchema;
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient;
import io.confluent.kafka.schemaregistry.testutil.MockSchemaRegistry;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Properties;
import org.apache.kafka.common.serialization.Deserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** The browser JSON of the Avro greek-move-auth topic is byte-identical to what the JSON-era service wrote. */
class GreekMoveAuthJsonTest {
    private static final String SCOPE = "gw-greek-move-auth";
    private static final String URL = "mock://" + SCOPE;
    private final ObjectMapper jsonEra = new ObjectMapper();
    private final GatewaySettings settings = new GatewaySettings();
    private final String topic = settings.greekMoveAuthCurrentTopic();
    private FeedGatewayService service;
    private GreekMoveAuthAvro.VerdictSerializer writer;

    @BeforeEach
    void setUp() throws Exception {
        SchemaRegistryClient registry = MockSchemaRegistry.getClientForScope(SCOPE);
        registry.register(GreekMoveAuthAvro.SUBJECT, new AvroSchema(GreekMoveAuthAvro.SCHEMA));
        writer = new GreekMoveAuthAvro.VerdictSerializer(registry);
        writer.configure(GreekMoveAuthAvro.serializerConfig(URL), false);
        service = new FeedGatewayService(settings, jsonEra, new HpsfGatewayViewMapper(), null);
    }

    @AfterEach
    void tearDown() {
        MockSchemaRegistry.dropScope(SCOPE);
    }

    /** The value deserializer exactly as the service configures every Avro consumer. */
    private Deserializer<?> consumerValueDeserializer() throws Exception {
        var m = FeedGatewayService.class.getDeclaredMethod("avroConsumerProperties", String.class);
        m.setAccessible(true);
        Properties props = (Properties) m.invoke(service, "t");
        props.put("schema.registry.url", URL);
        Deserializer<?> d = (Deserializer<?>) Class.forName((String) props.get("value.deserializer")).getDeclaredConstructor().newInstance();
        d.configure(new HashMap<>((java.util.Map) props), false);
        return d;
    }

    private static MoveAuthenticityVerdict verdict(double spot, double spotMove) {
        return new MoveAuthenticityVerdict(1, "ab12", "SPX", "2026-10-19", 1_760_880_000_123L, 45_000L,
                spotMove, 1, 2.718281828459045, Verdict.REAL_UP, true, MoveDirection.UP, 0.8730000000000001, "HEURISTIC_UNCALIBRATED", true,
                List.of(new GreekVote(GreekName.DELTA, 0.6, "OK", false), new GreekVote(GreekName.GAMMA, 0.0, "ABSTAIN", true)),
                2, 1, 3, true, ThetaVerdict.SUSTAIN, true, 1.5e-7,
                "2026-10-19", 7335.0, "CALL", "v1", 1_760_879_999_000L, 0.5, -520.25,
                true, EndgameState.ENDGAME_PINNED, 1_760_879_000_000L, null, 1, 1.0E-5, -3.5, null, 0.1 + 0.2, null, Boolean.TRUE,
                "OK", List.of("A"), List.of("MOVE_BASELINE_NOT_READY"),
                List.of(new SourceOffset("options.databento.gex.strike", 3, 99L)),
                List.of(new MoveTrailSample(1_760_879_990_000L, spot, AuthClass.REAL), new MoveTrailSample(1_760_880_000_123L, spot + 0.25, AuthClass.FAKE)),
                List.of(new MoveTrackSegment(spot, spot + 0.25, 1_760_879_990_000L, 1_760_880_000_123L, AuthClass.NEUTRAL)),
                spot, 1_760_879_990_000L, spot + 0.25, 1_760_880_000_123L, 1,
                List.of(new TransitionMarker(spot, 1_760_879_990_000L)), List.of(),
                "gma-1", "1a2b", "et-2026", "greek-move-auth-svc-v1",
                1_760_880_000_500L, 377L, "greek-move-auth-dev", 17L, 1_760_880_000_500L);
    }

    @Test
    void theBrowserJsonIsByteIdenticalToTheJsonEraRecord() throws Exception {
        Deserializer<?> consumer = consumerValueDeserializer();
        for (double spot : new double[] {7335.0, 6012.5, 7499.873412, 0.1 + 0.2, 5000.01, 1e-5, 123456.789012345, 1.0E7 + 0.5}) {
            MoveAuthenticityVerdict v = verdict(spot, spot - 7000.0);
            String old = jsonEra.writeValueAsString(v);
            Object decoded = consumer.deserialize(topic, writer.serialize(topic, v));
            assertEquals(old, GreekMoveAuthJson.render(decoded), "spot " + spot);
        }
    }

    @Test
    void theServiceRoutesTheEventThroughTheRenderer() throws Exception {
        MoveAuthenticityVerdict v = verdict(7335.0, 0.5);
        Object decoded = consumerValueDeserializer().deserialize(topic, writer.serialize(topic, v));
        var m = FeedGatewayService.class.getDeclaredMethod("kafkaJson", String.class, boolean.class, Object.class);
        m.setAccessible(true);
        assertEquals(jsonEra.writeValueAsString(v), m.invoke(service, "greek-move-auth", true, decoded));
    }

    @Test
    void jsonBytesAreNeverReadAndNullIsATombstone() throws Exception {
        Deserializer<?> consumer = consumerValueDeserializer();
        byte[] json = jsonEra.writeValueAsString(verdict(7335.0, 0.5)).getBytes(StandardCharsets.UTF_8);
        assertThrows(RuntimeException.class, () -> consumer.deserialize(topic, json));
        assertNull(GreekMoveAuthJson.render(null));
        assertThrows(IllegalStateException.class, () -> GreekMoveAuthJson.render(jsonEra.writeValueAsString(verdict(7335.0, 0.5))));
    }
}

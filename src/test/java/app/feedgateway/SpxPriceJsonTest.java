package app.feedgateway;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.optionsedge.contracts.hpsf.UnderlyingPriceAvro;
import com.optionsedge.contracts.hpsf.UnderlyingPriceEvent;
import io.confluent.kafka.schemaregistry.avro.AvroSchema;
import io.confluent.kafka.schemaregistry.client.MockSchemaRegistryClient;
import io.confluent.kafka.serializers.KafkaAvroDeserializer;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SpxPriceJsonTest {
    private static final String TOPIC = "underlying.spx.price";
    private final ObjectMapper mapper = new ObjectMapper();
    private UnderlyingPriceAvro.EventSerializer writer;
    private KafkaAvroDeserializer reader;

    @BeforeEach
    void setUp() throws Exception {
        MockSchemaRegistryClient registry = new MockSchemaRegistryClient();
        registry.register(UnderlyingPriceAvro.SUBJECT, new AvroSchema(UnderlyingPriceAvro.SCHEMA));
        writer = new UnderlyingPriceAvro.EventSerializer(registry);
        writer.configure(UnderlyingPriceAvro.serializerConfig("mock://gw"), false);
        reader = new KafkaAvroDeserializer(registry);
        reader.configure(Map.of("schema.registry.url", "mock://gw", "specific.avro.reader", "false",
                "value.subject.name.strategy", "io.confluent.kafka.serializers.subject.RecordNameStrategy"), false);
    }

    private Object wire(UnderlyingPriceEvent e) {
        return reader.deserialize(TOPIC, writer.serialize(TOPIC, e));
    }

    /** The JSON era's text: field order of the record, compact, doubles as Jackson/Python repr print them. */
    private static String oldJson(double px, Long size) {
        return "{\"schemaVersion\":1,\"symbol\":\"SPX\",\"eventTime\":\"2026-10-16T14:30:00.123Z\",\"price\":" + px
                + ",\"size\":" + size + ",\"source\":\"NATIVE_SPX_INDEX\",\"quality\":\"LIVE_INDEX\",\"canonicalSpot\":" + px
                + ",\"spotEventTimeNs\":1760625000123000000,\"feedEpochId\":\"epoch-1\",\"epochSeq\":42,\"spotQuality\":\"LIVE_INDEX\"}";
    }

    @Test
    void renderedJsonIsByteIdenticalToTheJsonEraForAGridOfPrices() throws Exception {
        double[] grid = {7335.0, 6012.5, 7499.873412, 5000.01, 4999.999999999999, 6000.0, 7000.25, 6543.21, 123456.789012345,
                Math.nextUp(7000.0), Math.nextDown(7000.0), 0.1 + 0.2, 6899.999, 7100.1};
        for (double px : grid) {
            for (Long size : new Long[] {null, 5L}) {
                String old = oldJson(px, size);
                UnderlyingPriceEvent e = new UnderlyingPriceEvent(1, "SPX", "2026-10-16T14:30:00.123Z", px, size, "NATIVE_SPX_INDEX",
                        "LIVE_INDEX", px, 1_760_625_000_123_000_000L, "epoch-1", 42L, "LIVE_INDEX");
                String now = SpxPriceJson.render(mapper, wire(e));
                assertEquals(old, now, "price " + px);
                assertEquals(mapper.writeValueAsString(mapper.readTree(old)), now, "reparsed price " + px);
            }
        }
    }

    @Test
    void tombstoneIsNullAndForeignValuesFailLoudly() {
        assertNull(SpxPriceJson.render(mapper, null));
        assertThrows(IllegalStateException.class, () -> SpxPriceJson.render(mapper, oldJson(6012.5, null)));
    }
}

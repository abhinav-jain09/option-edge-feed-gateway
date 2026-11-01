package app.feedgateway;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.HashMap;
import java.util.Map;
import org.apache.kafka.common.errors.SerializationException;
import org.junit.jupiter.api.Test;

/** The tolerant deserializer drops POISON records, never records lost to an unreachable registry. */
class GatewayAvroDeserializerRegistryTest {

    @Test
    void anUnreachableRegistryFailsThePollInsteadOfDroppingTheRecord() {
        Map<String, Object> config = new HashMap<>();
        config.put("schema.registry.url", "http://127.0.0.1:0");   // port 0 is never connectable: a deterministic connection failure with no socket to reclaim
        config.put(GatewayAvroDeserializer.SPX_PRICE_TOPIC, "underlying.spx.price");
        config.put(GatewayAvroDeserializer.TOLERANT_TOPICS, "chart.topic");
        try (GatewayAvroDeserializer d = new GatewayAvroDeserializer()) {
            d.configure(config, false);
            byte[] framed = {0, 0, 0, 0, 7, 1, 2, 3};
            assertThrows(SerializationException.class, () -> d.deserialize("chart.topic", framed), "an infrastructure failure is retried, not swallowed");
            assertSame(GatewayAvroDeserializer.UNDECODABLE, d.deserialize("chart.topic", new byte[] {1, 2, 3, 4, 5, 6}), "a value that is not Confluent-framed is poison");
        }
    }
}

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
        int closedPort;
        try (java.net.ServerSocket probe = new java.net.ServerSocket(0, 1, java.net.InetAddress.getLoopbackAddress())) {
            closedPort = probe.getLocalPort();   // bound, then closed: nothing listens there, a refused connection (deterministic)
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
        config.put("schema.registry.url", "http://127.0.0.1:" + closedPort);
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

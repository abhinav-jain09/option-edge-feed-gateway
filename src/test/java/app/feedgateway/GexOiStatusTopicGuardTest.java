package app.feedgateway;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The gateway resolves the oi-status topic through the contract rather than a private copy of the
 * name — one definition for the producer and every reader.
 *
 * <p>What is pinned here is that the topic carries NO version suffix. The JSON-to-Avro cutover
 * WIPED this topic and recreated it under its own name; a {@code .vN} sibling would point these
 * AVRO consumers at a topic nothing writes, and the failure would look like a dead badge rather
 * than a misconfiguration.
 */
class GexOiStatusTopicGuardTest {

    @Test
    void theDefaultIsTheUnversionedAvroTopic() {
        // Guarded so a set override does not make this assertion vacuous.
        org.junit.jupiter.api.Assumptions.assumeTrue(
                System.getenv("KAFKA_DATABENTO_GEX_OI_STATUS_TOPIC") == null);
        String topic = new GatewaySettings().databentoGexOiStatusTopic();
        assertTrue(topic.endsWith("options.databento.gex.oi-status"), topic);
        assertFalse(topic.matches(".*\\.v[0-9]+$"), topic);
    }

    @Test
    void aPrefixedTopicResolvesAndABlankOneIsRefused() {
        assertEquals("dev.options.databento.gex.oi-status",
                app.kafka.GexOiStatusAvro.requireTopic(
                        "dev.options.databento.gex.oi-status", "gateway-test"));
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> app.kafka.GexOiStatusAvro.requireTopic("  ", "gateway-test"));
        assertTrue(e.getMessage().contains("gateway-test"), e.getMessage());
    }
}

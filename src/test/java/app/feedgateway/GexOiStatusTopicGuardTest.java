package app.feedgateway;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The gateway resolves the oi-status topic through the contract's fail-closed guard.
 *
 * <p>Renaming the topic was only half the JSON-to-Avro cutover while
 * {@code KAFKA_DATABENTO_GEX_OI_STATUS_TOPIC} could still name the predecessor: this topic is read
 * by the AVRO consumers here, so that configuration would hand them a topic full of JSON they cannot
 * read, and the failure would look like a dead badge rather than a misconfiguration.
 */
class GexOiStatusTopicGuardTest {

    @Test
    void theDefaultIsTheAvroTopic() {
        // Guarded so a set override does not make this assertion vacuous.
        org.junit.jupiter.api.Assumptions.assumeTrue(
                System.getenv("KAFKA_DATABENTO_GEX_OI_STATUS_TOPIC") == null);
        assertTrue(new GatewaySettings().databentoGexOiStatusTopic()
                .endsWith("options.databento.gex.oi-status.v2"));
    }

    @Test
    void theRetiredJsonTopicIsRefusedWhicheverPrefixResolves() {
        // The gateway's own resolution path, exercised through the shared contract guard rather than
        // a private copy of the rule — one definition for the producer and every moved reader.
        assertEquals("dev.options.databento.gex.oi-status.v2",
                app.kafka.GexOiStatusAvro.requireAvroTopic(
                        "dev.options.databento.gex.oi-status.v2", "gateway-test"));
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> app.kafka.GexOiStatusAvro.requireAvroTopic(
                        "dev.options.databento.gex.oi-status", "gateway-test"));
        assertTrue(e.getMessage().contains("retired JSON topic"), e.getMessage());
        assertTrue(e.getMessage().contains("gateway-test"), e.getMessage());
    }
}

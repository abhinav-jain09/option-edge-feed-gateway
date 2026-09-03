package app.feedgateway;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Wiring for the {@code delta-flow-accel} stream: the topic knob, the cache/live consumer symmetry,
 * and the standalone dispatch. The settings assertions execute the real methods; the symmetry check
 * is structural because the two topicEvents maps are built inside private wiring — the rule it pins
 * (both consumers carry the binding, or bootstrap and live drift) is the one that has actually
 * broken here before.
 */
class DeltaFlowAccelWiringTest {

    @Test
    void theTopicKnobDefaultsToTheWebTieresStreamTopic() {
        GatewaySettings settings = new GatewaySettings();
        assertEquals("delta-flow.acceleration.current", settings.deltaFlowAccelTopic());
    }

    @Test
    void bothConsumersCarryTheBindingAndTheDispatchIsStandalone() throws Exception {
        String src = Files.readString(Path.of("src/main/java/app/feedgateway/FeedGatewayService.java"));
        String binding = "topicEvents.put(settings.deltaFlowAccelTopic(), new TopicBinding(\"DATABENTO\", \"delta-flow-accel\"));";
        int count = src.split(java.util.regex.Pattern.quote(binding), -1).length - 1;
        assertEquals(2, count, "cache + live consumers must stay symmetric — one binding per consumer");
        assertTrue(src.contains("if (\"delta-flow-accel\".equals(binding.event())) {"),
                "the frame is broadcast STANDALONE, same delivery class as es-cvd");
    }
}

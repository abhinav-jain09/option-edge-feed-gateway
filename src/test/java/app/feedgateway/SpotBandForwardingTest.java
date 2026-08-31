package app.feedgateway;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * Per-strike spot-band flow reaching the browser.
 *
 * <p>The invariant that matters here is SYMMETRY: this gateway registers its topics twice, once for
 * the cache bootstrap and once for the live consumer, and a topic wired into only one of them is a
 * feature that works after a restart and not before it, or the reverse. Seller Activity — the record
 * this one is modelled on — is registered in both, and so is this.
 */
class SpotBandForwardingTest {

    private static final String SERVICE = "src/main/java/app/feedgateway/FeedGatewayService.java";
    private static final String SETTINGS = "src/main/java/app/feedgateway/GatewaySettings.java";

    @Test
    void theBandTopicIsWiredIntoBOTHtheCacheAndTheLiveConsumer() throws Exception {
        String source = Files.readString(Path.of(SERVICE));
        assertEquals(2, occurrences(source,
                "topicEvents.put(settings.databentoSpotBandTopic(), new TopicBinding(\"DATABENTO\", \"spot-band\"));"),
                "registered once = works in only one of bootstrap or live");
        // and it sits alongside the record it is modelled on, which is registered the same way
        assertEquals(2, occurrences(source,
                "topicEvents.put(settings.databentoSellerActivityTopic(), new TopicBinding(\"DATABENTO\", \"seller-activity\"));"));
    }

    @Test
    void theEventNameIsOnTheForwardingAllowList() throws Exception {
        String source = Files.readString(Path.of(SERVICE));
        assertTrue(source.contains("\"seller-activity\", \"spot-band\", \"delta-flow\""),
                "an event the allow-list does not name is consumed and then silently dropped");
    }

    @Test
    void theTopicDefaultsToTheNameTheProducerWritesAndStaysOverridable() throws Exception {
        String settings = Files.readString(Path.of(SETTINGS));
        assertTrue(settings.contains(
                "return value(\"KAFKA_DATABENTO_SPOT_BAND_TOPIC\", \"options.databento.spot-band-flow\");"),
                "the default must match the classifier's DEFAULT_SPOT_BAND_TOPIC exactly");
    }

    private static int occurrences(String text, String needle) {
        int count = 0;
        for (int at = 0; (at = text.indexOf(needle, at)) >= 0; at += needle.length()) count++;
        return count;
    }
}

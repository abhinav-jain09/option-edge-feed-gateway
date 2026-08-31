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

    /**
     * Registration is not delivery. The first cut of this feature registered the topic in both
     * consumers and allow-listed the event — both asserted, both true — and the records still reached
     * nobody, because the gateway delivers per-strike records through the ui-batch and a new event
     * needs a cache map, a cache key, a store branch and a batch field before any of that happens.
     * The page showed an empty column for hours behind two green tests.
     */
    @Test
    void theRecordIsCACHEDandRIDESTHEBATCH_notMerelyRegistered() throws Exception {
        String source = Files.readString(Path.of(SERVICE));
        assertTrue(source.contains("private final Map<String, String> spotBands = new ConcurrentHashMap<>()"),
                "consumed with nowhere to put it is the same as not consumed");
        assertTrue(source.contains("case \"spot-band\" -> {"), "updateCache must have a store branch");
        assertTrue(source.contains("spotBands.put(key, json);"));
        assertTrue(source.contains("key = strikeFlowCacheKey(json, key);"),
                "a per-strike event needs a per-strike cache key or every strike overwrites the last");
        assertTrue(source.contains("case \"spot-band\" -> spotBandJsons.add(cachedEvent.json());"),
                "collected into the batch");
        assertTrue(source.contains("\"spotBands\\\":\" + jsonArray(spotBandJsons)"),
                "and emitted as a batch field the page can read");
    }

    /** The live coalescing path is a SECOND batch route; a field on one and not the other delivers
     *  only on replay, or only live, and looks intermittently broken. */
    @Test
    void theCoalescedLivePathCarriesItToo() throws Exception {
        String source = Files.readString(Path.of(SERVICE));
        assertTrue(source.contains("private final Map<String, String> pendingSpotBands = new LinkedHashMap<>()"));
        assertTrue(source.contains("case \"spot-band\" -> pendingSpotBands;"));
        assertTrue(source.contains("new ArrayList<>(pendingSpotBands.values()),"));
        assertTrue(source.contains("pendingSpotBands.clear();"), "a batch that never clears repeats itself");
    }

    /** One record per strike, republished on change — broadcasting each would fill the chain's
     *  outbound queue, which is exactly why seller-activity does not either. */
    @Test
    void individualRecordsAreNOTbroadcastOverTheSocket() throws Exception {
        String source = Files.readString(Path.of(SERVICE));
        int at = source.indexOf("if (\"spot-band\".equals(binding.event())) {");
        assertTrue(at > 0, "there must be an explicit skip, not an accident of ordering");
        String after = source.substring(at, at + 500);
        assertTrue(after.contains("continue;"), "the skip must actually skip");
    }

    /** Replay after a source switch must include it, or switching source silently empties the column. */
    @Test
    void itSurvivesASourceSwitchAndIsEvictedWithItsVersionKey() throws Exception {
        String source = Files.readString(Path.of(SERVICE));
        assertTrue(source.contains("\"strike-flow\", \"spot-band\", \"seller-activity\""),
                "absent from the source-switch replay list, the column empties on a switch");
        assertTrue(source.contains("replayCacheMap(session, \"spot-band\", spotBands);"));
        assertTrue(source.contains("spotBands.remove(versionKey.substring(\"spot-band:\".length()));"),
                "a cache with no eviction outlives the session it describes");
    }
}

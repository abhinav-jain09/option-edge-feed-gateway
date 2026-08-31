package app.feedgateway;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;

/**
 * Per-strike spot-band flow reaching the browser.
 *
 * <p>The first version of this file asserted only that certain STRINGS appeared in
 * FeedGatewayService.java. Every test passed while the cache held one entry for the whole board,
 * because a source scan cannot see what a record does — it can only see what the code is spelled
 * like. Worse, one of those scans demanded {@code strikeFlowCacheKey} by name, so the suite
 * actively pinned the bug in place. The tests that matter here therefore DRIVE the cache with a
 * real payload and assert what comes out of it.
 */
class SpotBandForwardingTest {

    private static final String SERVICE = "src/main/java/app/feedgateway/FeedGatewayService.java";
    private static final String SETTINGS = "src/main/java/app/feedgateway/GatewaySettings.java";

    /** Exactly the shape SpotBandSnapshotJson puts on the wire. */
    private static String bandJson(double strike) {
        return "{\"eventType\":\"SPOT_BAND_FLOW\",\"marketDataSource\":\"DATABENTO\",\"source\":\"DATABENTO\","
                + "\"symbol\":\"SPX\",\"expiry\":\"2026-08-31\",\"sessionDate\":\"2026-08-31\","
                + "\"strike\":" + strike + ",\"timestampMs\":1788198368524,\"binPoints\":5.0,"
                + "\"unplacedNotional\":0.0,\"refusedBins\":0,"
                + "\"bands\":[{\"spotLow\":6400.0,\"spotHigh\":6405.0,\"callBuy\":1000.0,\"callSell\":250.0,"
                + "\"putBuy\":75.0,\"putSell\":10.0}]}";
    }

    private static FeedGatewayService service() {
        return new FeedGatewayService(new GatewaySettings(), new ObjectMapper(), new HpsfGatewayViewMapper(), null);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, String> cacheOf(FeedGatewayService service, String field) throws Exception {
        Field f = FeedGatewayService.class.getDeclaredField(field);
        f.setAccessible(true);
        return (Map<String, String>) f.get(service);
    }

    /** Feed one record through the real ingestion path and return the cache key it produced. */
    private static String ingest(FeedGatewayService service, String event, String kafkaKey, String json)
            throws Exception {
        Class<?> bindingClass = Class.forName("app.feedgateway.FeedGatewayService$TopicBinding");
        Constructor<?> ctor = bindingClass.getDeclaredConstructor(String.class, String.class);
        ctor.setAccessible(true);
        Object binding = ctor.newInstance("DATABENTO", event);
        Method updateCache = FeedGatewayService.class.getDeclaredMethod(
                "updateCache", bindingClass, ConsumerRecord.class, String.class);
        updateCache.setAccessible(true);
        ConsumerRecord<String, Object> record =
                new ConsumerRecord<>("options.databento.spot-band-flow", 0, 1L, kafkaKey, null);
        return (String) updateCache.invoke(service, binding, record, json);
    }

    /**
     * THE regression. A band record is ONE STRIKE; a strike-flow record is chain-wide (one record
     * carrying strikes[]). Keying bands with the board-level keyer collapsed every strike into a
     * single entry, last writer winning, and the page could show a band for exactly one strike.
     * Three distinct strikes must therefore produce three entries — this fails on the old keyer.
     */
    @Test
    void eachStrikeGetsItsOwnCacheEntry() throws Exception {
        FeedGatewayService service = service();
        ingest(service, "spot-band", "k1", bandJson(6400));
        ingest(service, "spot-band", "k2", bandJson(6405));
        ingest(service, "spot-band", "k3", bandJson(6410));

        Map<String, String> bands = cacheOf(service, "spotBands");
        assertEquals(3, bands.size(),
                "one entry per strike — a board-level key collapses all of them into one");
        assertEquals(3, bands.keySet().stream().distinct().count());
    }

    /**
     * The key must come from the PAYLOAD, not the Kafka record key. deltaFlowCacheKey falls back to
     * the record key silently when symbol/expiry/strike are missing or renamed, so a producer-side
     * field rename would still yield distinct entries and pass the test above while breaking the
     * page's symbol|expiry|strike contract. Nothing here may carry the record key.
     */
    @Test
    void theKeyIsDerivedFromThePayloadNotTheKafkaRecordKey() throws Exception {
        FeedGatewayService service = service();
        String key = ingest(service, "spot-band", "RECORD-KEY-MUST-NOT-BE-USED", bandJson(6400));

        assertFalse(key.contains("RECORD-KEY-MUST-NOT-BE-USED"),
                "fell back to the Kafka key — the payload's symbol/expiry/strike did not parse");
        assertTrue(key.endsWith("SPX|20260831|6400"), "expected source|symbol|expiry|strike, got " + key);
    }

    /** A cache with no eviction outlives the session it describes. */
    @Test
    void theEntryIsEvictedByItsVersionKey() throws Exception {
        FeedGatewayService service = service();
        String key = ingest(service, "spot-band", "k1", bandJson(6400));
        assertEquals(1, cacheOf(service, "spotBands").size());

        Method remove = FeedGatewayService.class.getDeclaredMethod("removeCacheEntry", String.class);
        remove.setAccessible(true);
        remove.invoke(service, "spot-band:" + key);

        assertTrue(cacheOf(service, "spotBands").isEmpty(), "evicting the version key must clear the entry");
    }

    /**
     * One record per strike, republished on change. Broadcasting each would fill the chain's outbound
     * queue — and there is no client handler for a "spot-band" message in the first place, so any such
     * message is discarded by the browser. This must hold on BOTH socket routes, which is where the
     * first cut was wrong: the live consumer skipped it and replayCachedToSocket replayed it anyway,
     * one message per cache entry.
     */
    @Test
    void noRouteBroadcastsIndividualRecords() throws Exception {
        String source = Files.readString(Path.of(SERVICE));

        int at = source.indexOf("if (\"spot-band\".equals(binding.event())) {");
        assertTrue(at > 0, "there must be an explicit live-path skip, not an accident of ordering");
        assertTrue(source.substring(at, at + 500).contains("continue;"), "the skip must actually skip");

        assertFalse(source.contains("replayCacheMap(session, \"spot-band\", spotBands);"),
                "replayCacheMap sends one socket message per entry and no client reads a spot-band message");
    }

    /** The topic name is a cross-repo contract with the classifier; no behaviour here can check it. */
    @Test
    void theTopicDefaultMatchesTheProducer() throws Exception {
        String settings = Files.readString(Path.of(SETTINGS));
        assertTrue(settings.contains(
                "return value(\"KAFKA_DATABENTO_SPOT_BAND_TOPIC\", \"options.databento.spot-band-flow\");"),
                "the default must match the classifier's DEFAULT_SPOT_BAND_TOPIC exactly");
    }

    /**
     * The gateway registers topics twice — cache bootstrap and live consumer. A topic wired into only
     * one is a feature that works after a restart and not before it, or the reverse.
     */
    @Test
    void theBandTopicIsWiredIntoBothConsumers() throws Exception {
        String source = Files.readString(Path.of(SERVICE));
        assertEquals(2, occurrences(source,
                "topicEvents.put(settings.databentoSpotBandTopic(), new TopicBinding(\"DATABENTO\", \"spot-band\"));"),
                "registered once = works in only one of bootstrap or live");
    }

    /**
     * Every delivery route must actually reach a browser. enqueuePending and broadcastCachedState both
     * DROP when perSessionRouting() is true, so on an authenticated gateway the coalesced batch and the
     * source-switch batch are dead — and bands have no per-message client handler, so the per-entry
     * replay reached nobody either. That combination delivered nothing at all on any route. The
     * per-session connect must therefore send bands as their own ui-batch.
     */
    @Test
    void theAuthenticatedRouteStillHasABandDeliveryPath() throws Exception {
        String source = Files.readString(Path.of(SERVICE));

        // It must sit in the COMMON per-socket replay path, not only in addClient: connect, source
        // switch and return-to-live (replayLiveCacheToAppSession) all go through replayCachedToSocket,
        // and in authenticated mode broadcastCachedState drops the source-switch batch.
        int at = source.indexOf("private void replayCachedToSocket(WebSocketSession session) {");
        assertTrue(at > 0, "expected the common per-socket replay path");
        int end = source.indexOf("\n    }", at);
        assertTrue(source.substring(at, end).contains("replaySpotBandBatchToSocket(session);"),
                "bands must replay on every per-socket route, not just on connect");
        assertFalse(source.contains("replaySpotBandBatchToSocket(session);\n            replayShortPremiumCached"),
                "must not be duplicated into addClient — the common path already covers connect");

        assertTrue(source.contains("sendEnvelope(session, uiBatchEnvelopeJson(asBatch));"),
                "and it must be ONE batch, not one message per strike");
        assertTrue(source.contains("deliverableCacheEntries(session, \"spot-band\", spotBands)"),
                "the batch must use the same per-socket routing filter as the per-entry path");
    }

    /**
     * A source switch is NOT one of replayCachedToSocket's triggers — it has exactly two, addClient and
     * resumeLive. In per-session mode broadcastCachedState drops the switch batch, and bands cannot
     * refill from live data either because the live consumer skips them by design. Both switch call
     * sites must therefore replay the band batch, or the column empties on every roll and stays empty.
     */
    @Test
    void theSourceSwitchReplaysTheBandBatchExactlyOnce() throws Exception {
        String source = Files.readString(Path.of(SERVICE));

        assertEquals(1, occurrences(source, "replaySpotBandBatchAfterSourceSwitch();"),
                "applySelection calls markSelectionReady inline when the switch is serviceable at once, "
                        + "so a second call site sends every socket the same batch twice");

        // and the one call site is the READINESS one: before readiness the new selection's cache is
        // knowably incomplete, which is the whole point of the gate.
        int ready = source.indexOf("broadcast(\"source-ready\", activeSelectionJson(selection, \"source-ready\"));");
        assertTrue(ready > 0, "expected the readiness announcement");
        assertTrue(source.substring(ready, ready + 400).contains("replaySpotBandBatchAfterSourceSwitch();"),
                "the band replay belongs on the readiness convergence path");

        int at = source.indexOf("private void replaySpotBandBatchAfterSourceSwitch() {");
        assertTrue(at > 0, "expected the switch-time helper");
        assertTrue(source.substring(at, at + 400).contains("if (!perSessionRouting()) {"),
                "legacy mode already gets the switch batch from broadcastCachedState — do not double-send");
    }

    /**
     * Readiness is ONE-SHOT per selection key and its replay iterates `clients` weakly. A socket that
     * connects between a switch and its readiness can therefore be served a still-filling cache and
     * then be missed by the readiness replay, with nothing left to repair it. The connect path brackets
     * its replay with the readiness key and re-takes the band board if it moved underneath.
     */
    @Test
    void aSocketJoiningDuringReadinessStillGetsTheCompleteBoard() throws Exception {
        String source = Files.readString(Path.of(SERVICE));
        int at = source.indexOf("String keyBeforeReplay = readySelectionKey.get();");
        assertTrue(at > 0, "connect must capture the readiness key before replaying");
        String window = source.substring(at, at + 500);
        assertTrue(window.contains("replayCachedToSocket(session);"), "…around the replay itself");
        assertTrue(window.contains("!java.util.Objects.equals(keyBeforeReplay, readySelectionKey.get())"),
                "…and compare it afterwards");
        assertTrue(window.contains("replaySpotBandBatchOncePerReadiness(session);"),
                "…re-taking the band board when readiness landed mid-replay");
    }

    /**
     * Readiness and the connect bracket race to serve the same completed board, and either iterator
     * ordering can win. Exactly-once must therefore be a property of the code, not of the interleaving:
     * both go through a per-socket ledger keyed by the readiness key, and the loser no-ops.
     */
    @Test
    void theSwitchBoardIsSentAtMostOncePerSocketPerReadiness() throws Exception {
        String source = Files.readString(Path.of(SERVICE));

        int at = source.indexOf("private void replaySpotBandBatchOncePerReadiness(WebSocketSession session) {");
        assertTrue(at > 0, "expected the deduplicating variant");
        String body = source.substring(at, at + 600);
        assertTrue(body.contains("spotBandSwitchDelivered.put(session.getId(), key)"),
                "claim the key atomically — two racing threads must not both send");
        assertTrue(body.contains("java.util.Objects.equals(previous, key)"), "…and the loser returns");

        // Both racing call sites must use the deduplicating variant, never the raw one. Counted by their
        // exact argument so the method DECLARATION is not mistaken for a third call site.
        assertEquals(1, occurrences(source, "replaySpotBandBatchOncePerReadiness(client);"),
                "the readiness replay must dedupe");
        assertEquals(1, occurrences(source, "replaySpotBandBatchOncePerReadiness(session);"),
                "the connect bracket must dedupe");
        // The raw (undeduplicated) variant has exactly two callers, neither of them a racing site:
        // replayCachedToSocket, whose connect/return-to-live replay always owes a fresh board, and the
        // deduplicating wrapper itself once it has claimed the key.
        assertEquals(2, occurrences(source, "replaySpotBandBatchToSocket(session);\n"),
                "a third raw call site would bypass the exactly-once ledger");
        assertTrue(source.contains("spotBandSwitchDelivered.remove(id);"),
                "a per-socket ledger that is never cleaned leaks an entry per disconnect");
    }

    /** The legacy (unauthenticated) connect bootstrap listed every cached surface except this one, so a
     *  fresh page showed empty bands until the next coalesced flush happened to carry one. */
    @Test
    void theLegacyConnectBootstrapIncludesBands() throws Exception {
        String source = Files.readString(Path.of(SERVICE));
        assertTrue(source.contains("\"strike-flow\", \"spot-band\", \"delta-flow\", \"strike-intel\""),
                "sendCachedState on connect must list spot-band");
    }

    /** Absent from the source-switch replay list, the column empties on a switch and never refills. */
    @Test
    void itSurvivesASourceSwitch() throws Exception {
        String source = Files.readString(Path.of(SERVICE));
        assertTrue(source.contains("\"strike-flow\", \"spot-band\", \"seller-activity\""),
                "the source-switch replay list feeds the ui-batch; absent from it, the column empties");
    }

    /** The live coalescing path is a SECOND batch route; a field on one and not the other looks
     *  intermittently broken. */
    @Test
    void theCoalescedLivePathCarriesItToo() throws Exception {
        String source = Files.readString(Path.of(SERVICE));
        assertTrue(source.contains("case \"spot-band\" -> pendingSpotBands;"));
        assertTrue(source.contains("new ArrayList<>(pendingSpotBands.values()),"));
        assertTrue(source.contains("pendingSpotBands.clear();"), "a batch that never clears repeats itself");
        assertTrue(source.contains("\"spotBands\\\":\" + jsonArray(spotBandJsons)"),
                "and emitted as a batch field the page can read");
    }

    private static int occurrences(String text, String needle) {
        int count = 0;
        for (int at = 0; (at = text.indexOf(needle, at)) >= 0; at += needle.length()) count++;
        return count;
    }
}

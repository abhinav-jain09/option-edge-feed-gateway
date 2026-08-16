package app.feedgateway;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;

/**
 * U16 (ES-CVD-SPX-LEVELS-DESIGN.md CL-R7): the es-cvd-spx-levels gateway event — opt-in flag,
 * boundary acceptance, tombstone withdrawal, connect replay retention, and the wiring-shape pins
 * (live-consumer binding, raw pass-through, auth-mode allowlist) whose absence was exactly the
 * defect found on the es-cvd siblings.
 */
class CvdSpxLevelsWiringTest {

    private static FeedGatewayService service() {
        return new FeedGatewayService(new GatewaySettings(), new ObjectMapper(), new HpsfGatewayViewMapper(), null);
    }

    private static String levels(String state) {
        return "{\"schemaVersion\":\"1.0.0\",\"symbol\":\"SPX\",\"state\":\"" + state
                + "\",\"sessionDate\":\"20260817\",\"alignedAtMs\":1786900000000}";
    }

    @Test void isOptInAndUsesTheAlignedOutputTopic() {
        GatewaySettings off = new GatewaySettings();
        assertFalse(off.esCvdSpxLevelsEnabled(), "the flag is the LAST rollout step — default OFF");
        assertEquals("options.es-cvd-spx-levels", off.esCvdSpxLevelsTopic());
    }

    @Test void authModeAllowlistCoversTheWholeCvdFamily() {
        // The U16 event plus the sibling defect fix: without allowlisting, per-session (auth)
        // mode drops every frame as non-routable.
        assertTrue(FeedGatewayService.isGlobalBroadcastEvent("es-cvd-spx-levels"));
        assertTrue(FeedGatewayService.isGlobalBroadcastEvent("es-cvd"));
        assertTrue(FeedGatewayService.isGlobalBroadcastEvent("es-cvd-bar"));
    }

    @Test void boundaryAcceptsOnlyMajorOneOkOrUnavailable() {
        var s = service();
        assertTrue(s.acceptCvdSpxLevels(levels("OK")));
        assertTrue(s.acceptCvdSpxLevels(levels("UNAVAILABLE")));
        assertTrue(s.acceptCvdSpxLevels(levels("OK").replace("1.0.0", "1.7.3")),
                "minor/patch revisions of major 1 must pass");
        assertFalse(s.acceptCvdSpxLevels(levels("OVERFLOW")), "producer-side state never reaches clients");
        assertFalse(s.acceptCvdSpxLevels(levels("OK").replace("1.0.0", "2.0.0")), "future schema epoch");
        assertFalse(s.acceptCvdSpxLevels(levels("OK").replace("1.0.0", "10.0")), "malformed version");
        assertFalse(s.acceptCvdSpxLevels("[1,2,3]"), "non-object");
        assertFalse(s.acceptCvdSpxLevels("not json"));
        assertFalse(s.acceptCvdSpxLevels("{\"schemaVersion\":\"1.0.0\"}"), "missing state");
    }

    @Test void boundaryRefusesOversizeRecords() {
        var s = service();
        String pad = "x".repeat(FeedGatewayService.CVD_SPX_LEVELS_MAX_BYTES);
        assertFalse(s.acceptCvdSpxLevels(
                levels("OK").replace("\"SPX\"", "\"" + pad + "\"")));
    }

    @Test void tombstoneWithdrawsTheConnectReplayAndCounts() {
        var s = service();
        s.cvdSpxLevelsLatestForTest().set(levels("OK"));
        ConsumerRecord<String, String> tombstone =
                new ConsumerRecord<>("options.es-cvd-spx-levels", 0, 5L, "SPX", null);
        s.evictCvdSpxLevelsTombstone("es-cvd-spx-levels", tombstone);
        assertNull(s.cvdSpxLevelsLatestForTest().get(), "a withdrawn record must never replay");
        assertEquals(1L, s.cvdSpxLevelsDropsForTest());
    }

    @Test void otherEventsTombstonesAreIgnored() {
        var s = service();
        s.cvdSpxLevelsLatestForTest().set(levels("OK"));
        ConsumerRecord<String, String> tombstone =
                new ConsumerRecord<>("es.tape-zones.board", 0, 5L, "ES", null);
        s.evictCvdSpxLevelsTombstone("tapeZones", tombstone);
        s.evictCvdSpxLevelsTombstone(null, tombstone);
        assertEquals(levels("OK"), s.cvdSpxLevelsLatestForTest().get());
        assertEquals(0L, s.cvdSpxLevelsDropsForTest());
    }

    @Test void metricsCarryTheAlertGateNames() {
        // The paging-rule conjunction (deploy repo cvd-spx-levels-alerts.yaml) references these
        // EXACT series names; renaming either silently disarms every U16 alert.
        String metrics = service().metrics();
        assertTrue(metrics.contains("\ngateway_cvd_spx_levels_enabled 0"), "flag off by default");
        assertTrue(metrics.contains("\ngateway_cvd_spx_levels_drops_total 0"));
    }

    // ---- wiring-shape pins on the source (the es-cvd defect class: right branch, wrong loop) ----

    @Test void liveConsumerBindsTheTopicBehindTheFlagAndCacheConsumerDoesNot() throws Exception {
        String source = Files.readString(Path.of("src/main/java/app/feedgateway/FeedGatewayService.java"));
        int liveBuilder = source.indexOf("private void runJsonStateLiveConsumer()");
        int liveRun = source.indexOf("runLiveConsumer(\"state-live\"", liveBuilder);
        assertTrue(liveBuilder >= 0 && liveRun > liveBuilder);
        String builder = source.substring(liveBuilder, liveRun);
        assertTrue(builder.contains("esCvdSpxLevelsEnabled()")
                        && builder.contains("\"es-cvd-spx-levels\""),
                "the delivery branch lives in the LIVE loop, so the LIVE consumer must subscribe");
        assertTrue(builder.contains("settings.esCvdTopic()") && builder.contains("settings.esCvdBarsTopic()"),
                "defect fix: es-cvd/es-cvd-bar must be live-bound too, not only cache-bound");
        int cacheBuilder = source.indexOf("private void runJsonStateCacheConsumer()");
        int cacheRun = source.indexOf("runAssignedCacheConsumer(\"state\"", cacheBuilder);
        assertFalse(source.substring(cacheBuilder, cacheRun).contains("es-cvd-spx-levels"),
                "updateCache has no case for the levels event; cache subscription would only churn");
    }

    @Test void deliveryBroadcastsVerbatimBeforeTheGenericCacheKeyGate() throws Exception {
        String source = Files.readString(Path.of("src/main/java/app/feedgateway/FeedGatewayService.java"));
        int direct = source.indexOf("if (\"es-cvd-spx-levels\".equals(binding.event()))");
        int generic = source.indexOf("String cacheKey = updateCache(binding, record, json);", direct);
        assertTrue(direct >= 0 && generic > direct);
        String branch = source.substring(direct, generic);
        assertTrue(branch.contains("acceptCvdSpxLevels(json)"), "boundary gate before broadcast");
        assertTrue(branch.contains("broadcast(binding.event(), json);"));
        assertTrue(FeedGatewayServiceSourcePins.isRawPassThrough(source),
                "producer-authored attestation must reach the browser byte-untouched");
    }

    @Test void addClientReplaysTheRetainedRecordBehindTheFlag() throws Exception {
        String source = Files.readString(Path.of("src/main/java/app/feedgateway/FeedGatewayService.java"));
        int addClient = source.indexOf("public void addClient(WebSocketSession session)");
        int replayEnd = source.indexOf("if (perSessionRouting())", addClient);
        String head = source.substring(addClient, replayEnd);
        assertTrue(head.contains("esCvdSpxLevelsEnabled()")
                        && head.contains("send(session, \"es-cvd-spx-levels\", levels)"),
                "connect replay happens for BOTH routing modes, before the mode branch");
    }

    /** Tiny helper so the pin reads as one assertion. */
    private static final class FeedGatewayServiceSourcePins {
        static boolean isRawPassThrough(String source) {
            int m = source.indexOf("private static boolean isRawPassThroughEvent(String event)");
            int end = source.indexOf('}', m);
            return m >= 0 && source.substring(m, end).contains("\"es-cvd-spx-levels\"");
        }
    }
}

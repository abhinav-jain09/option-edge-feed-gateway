package app.feedgateway;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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

    private static final long FLOW = 1786899999000L;

    /** A full OK record: causal provenance inline, neither matrix flag, complete structure. */
    private static String ok(String sessionDate, long positionMs, long prints) {
        return ok(sessionDate, positionMs, prints, "[]", "[]", "null", "null");
    }

    private static String ok(String sessionDate, long positionMs, long prints,
                             String buyLevels, String sellLevels, String flip, String balance) {
        return "{\"schemaVersion\":\"1.0.0\",\"symbol\":\"ES.v.0\",\"state\":\"OK\",\"sessionDate\":\""
                + sessionDate + "\",\"sessionComplete\":false,\"alignedAtMs\":1786900000000,"
                + "\"basisCents\":2575,\"basisState\":\"MEASURED\",\"basisMeasuredAtMs\":" + FLOW + ","
                + "\"sourcePublishedAtMs\":" + FLOW + ",\"flowEventTimeMs\":" + FLOW + ","
                + "\"foldPositionMs\":" + positionMs + ",\"foldPrints\":" + prints
                + ",\"buyLevels\":" + buyLevels + ",\"sellLevels\":" + sellLevels
                + ",\"flip\":" + flip + ",\"balancePriceCents\":" + balance + "}";
    }

    private static String level(long priceCents, long deltaSum) {
        return "{\"priceCents\":" + priceCents + ",\"deltaSum\":" + deltaSum
                + ",\"tradeCount\":7,\"lastTouchMs\":" + (FLOW - 1000) + "}";
    }

    /** An UNAVAILABLE record with an explicit matrix combination. */
    private static String unavailable(String reason, String provenance, boolean retained, boolean reset) {
        return "{\"schemaVersion\":\"1.0.0\",\"symbol\":\"ES.v.0\",\"state\":\"UNAVAILABLE\",\"reason\":\""
                + reason + "\",\"alignedAtMs\":1786900000000,\"sourceProvenance\":" + provenance
                + ",\"provenanceRetained\":" + retained + ",\"baselineReset\":" + reset + "}";
    }

    private static String prov(String sessionDate, long positionMs, long prints) {
        return "{\"sessionDate\":\"" + sessionDate + "\",\"foldPositionMs\":" + positionMs
                + ",\"foldPrints\":" + prints + "}";
    }

    private static String levels(String state) {
        return "OK".equals(state) ? ok("20260817", 1000, 5)
                : unavailable("source_stale", "null", false, false).replace("\"UNAVAILABLE\"",
                        "\"" + state + "\"");
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
        assertNotNull(s.validateCvdSpxLevels(levels("OK")));
        assertNotNull(s.validateCvdSpxLevels(levels("UNAVAILABLE")));
        assertNotNull(s.validateCvdSpxLevels(levels("OK").replace("1.0.0", "1.7.3")),
                "minor/patch revisions of major 1 must pass");
        assertNull(s.validateCvdSpxLevels(levels("OK").replace("\"OK\"", "\"OVERFLOW\"")),
                "producer-side state never reaches clients");
        assertNull(s.validateCvdSpxLevels(levels("OK").replace("1.0.0", "2.0.0")), "future schema epoch");
        assertNull(s.validateCvdSpxLevels(levels("OK").replace("1.0.0", "10.0")), "malformed version");
        assertNull(s.validateCvdSpxLevels("[1,2,3]"), "non-object");
        assertNull(s.validateCvdSpxLevels("not json"));
        assertNull(s.validateCvdSpxLevels("{\"schemaVersion\":\"1.0.0\"}"), "missing state");
        assertNull(s.validateCvdSpxLevels(ok("2026081", 1, 1)), "sessionDate must be 8 digits");
        assertNull(s.validateCvdSpxLevels(ok("20260817", 1, 1).replace("\"foldPrints\":1", "\"foldPrints\":\"1\"")),
                "provenance components are integral, not strings");
    }

    @Test void unavailableMustMatchTheProvenanceCombinationMatrix() {
        var s = service();
        // The four legal combinations.
        assertNotNull(s.validateCvdSpxLevels(unavailable("source_absent", "null", false, true)),
                "tombstone-derived absence");
        assertNotNull(s.validateCvdSpxLevels(unavailable("source_absent", "null", false, false)),
                "pre-first-source startup absence");
        assertNotNull(s.validateCvdSpxLevels(
                        unavailable("source_malformed", prov("20260817", 10, 2), true, false)),
                "provenance-unrecoverable malformed carries the RETAINED provenance, labeled");
        assertNotNull(s.validateCvdSpxLevels(
                        unavailable("source_stale", prov("20260817", 10, 2), false, false)),
                "causally validated");
        // Everything else is malformed — most importantly, a reset that claims provenance, which
        // must never be able to erase the gateway's baseline.
        assertNull(s.validateCvdSpxLevels(unavailable("source_absent", prov("20260817", 10, 2), false, true)));
        assertNull(s.validateCvdSpxLevels(unavailable("source_absent", "null", true, true)));
        assertNull(s.validateCvdSpxLevels(unavailable("source_absent", "null", true, false)));
        assertNull(s.validateCvdSpxLevels(unavailable("source_stale", prov("20260817", 10, 2), true, true)));
        assertNull(s.validateCvdSpxLevels(
                unavailable("source_stale", prov("20260817", 10, 2), false, false)
                        .replace(",\"provenanceRetained\":false", "")), "both flags are mandatory");
        assertNull(s.validateCvdSpxLevels(
                unavailable("source_stale", prov("20260817", 10, 2), false, false)
                        .replace(",\"sourceProvenance\":" + prov("20260817", 10, 2), "")),
                "an absent key is not the same statement as an explicit null");
        assertNull(s.validateCvdSpxLevels(unavailable("source_stale", "{\"sessionDate\":\"20260817\"}", false, false)),
                "a partial provenance object is malformed, not provenance-less");
        // An OK record may never claim either flag.
        assertNull(s.validateCvdSpxLevels(ok("20260817", 1, 1).replace("\"state\":\"OK\"",
                "\"state\":\"OK\",\"baselineReset\":true")));
    }

    @Test void retentionRefusesRegressionsInTheCalendarAwareTotalOrder() {
        var s = service();
        assertTrue(s.retainCvdSpxLevels(s.validateCvdSpxLevels(ok("20260817", 1000, 5))));
        assertEquals(0L, s.cvdSpxLevelsRegressionsForTest());

        assertFalse(s.retainCvdSpxLevels(s.validateCvdSpxLevels(ok("20260817", 900, 4))), "lower position");
        assertFalse(s.retainCvdSpxLevels(s.validateCvdSpxLevels(ok("20260814", 9999, 99))), "older session");
        assertFalse(s.retainCvdSpxLevels(s.validateCvdSpxLevels(ok("20260817", 1100, 4))),
                "crossed: position up, prints down — impossible from one monotone fold");
        assertFalse(s.retainCvdSpxLevels(s.validateCvdSpxLevels(ok("20260817", 900, 6))), "crossed the other way");
        assertEquals(4L, s.cvdSpxLevelsRegressionsForTest());
        assertTrue(s.cvdSpxLevelsLatestForTest().get().contains("\"foldPositionMs\":1000"),
                "the retained record is untouched by every refusal");

        assertTrue(s.retainCvdSpxLevels(s.validateCvdSpxLevels(ok("20260817", 1000, 5))), "equal is idempotent");
        assertTrue(s.retainCvdSpxLevels(s.validateCvdSpxLevels(ok("20260817", 1200, 6))), "advance");
        assertTrue(s.retainCvdSpxLevels(s.validateCvdSpxLevels(ok("20260818", 1, 1))), "newer session wins");
    }

    @Test void onlyTheTombstoneCombinationResetsTheBaseline() {
        var s = service();
        assertTrue(s.retainCvdSpxLevels(s.validateCvdSpxLevels(ok("20260817", 1000, 5))));
        // A startup-absence record must NOT regress a live baseline to "nothing known".
        assertFalse(s.retainCvdSpxLevels(s.validateCvdSpxLevels(unavailable("source_absent", "null", false, false))));
        assertEquals(1L, s.cvdSpxLevelsRegressionsForTest());
        // The tombstone-derived absence DOES reset it, and a lower-provenance record is then accepted.
        assertTrue(s.retainCvdSpxLevels(s.validateCvdSpxLevels(unavailable("source_absent", "null", false, true))));
        assertTrue(s.retainCvdSpxLevels(s.validateCvdSpxLevels(ok("20260817", 1, 1))),
                "after an operator wipe the baseline starts over");
    }

    @Test void boundaryRefusesOversizeRecords() {
        var s = service();
        String pad = "x".repeat(FeedGatewayService.CVD_SPX_LEVELS_MAX_BYTES);
        assertNull(s.validateCvdSpxLevels(levels("OK").replace("\"MEASURED\"", "\"" + pad + "\"")),
                "the size cap is PRE-parse: an oversize record is refused whatever it says");
    }

    @Test void theContractualKeyIsRequiredAndTheSymbolMustAgree() {
        var s = service();
        assertNotNull(s.validateCvdSpxLevels("ES.v.0", levels("OK")));
        assertNull(s.validateCvdSpxLevels("NQ.v.0", levels("OK")), "a foreign key never governs");
        assertNull(s.validateCvdSpxLevels(null, levels("OK")), "nor a null key");
        assertNull(s.validateCvdSpxLevels("ES.v.0", levels("OK").replace("\"symbol\":\"ES.v.0\"", "\"symbol\":\"NQ.v.0\"")),
                "key and payload symbol must AGREE, not merely each be present");
    }

    @Test void theFullOkStructureContractIsEnforced() {
        var s = service();
        String full = ok("20260817", 1000, 5,
                "[" + level(637525, 900) + "]", "[" + level(638000, -900) + "]",
                "{\"priceCents\":637600,\"atMs\":" + (FLOW - 500) + ",\"direction\":\"UP\",\"cvdAtCross\":42}",
                "637750");
        assertNotNull(s.validateCvdSpxLevels(full), "a complete record passes");

        // envelope fields the browser needs
        assertNull(s.validateCvdSpxLevels(full.replace(",\"alignedAtMs\":1786900000000", "")));
        assertNull(s.validateCvdSpxLevels(full.replace(",\"sessionComplete\":false", "")));
        assertNull(s.validateCvdSpxLevels(full.replace("\"basisState\":\"MEASURED\"", "\"basisState\":\"QUARANTINED\"")),
                "an OK record cannot carry an unusable basis state");
        assertNull(s.validateCvdSpxLevels(full.replace(",\"basisCents\":2575", "")));
        assertNull(s.validateCvdSpxLevels(full.replace("\"sessionDate\":\"20260817\"", "\"sessionDate\":\"99999999\"")),
                "eight digits is not a date");

        // structure invariants
        assertNull(s.validateCvdSpxLevels(ok("20260817", 1, 1, "[" + level(637525, -900) + "]", "[]", "null", "null")),
                "a buy level with negative delta breaks the side-sign rule");
        assertNull(s.validateCvdSpxLevels(ok("20260817", 1, 1,
                        "[" + level(637525, 900) + "," + level(637525, 300) + "]", "[]", "null", "null")),
                "duplicate prices within a side");
        assertNull(s.validateCvdSpxLevels(ok("20260817", 1, 1,
                        "[{\"priceCents\":637525,\"deltaSum\":900,\"tradeCount\":7,\"lastTouchMs\":" + (FLOW + 1) + "}]",
                        "[]", "null", "null")),
                "a touch after the record's own flow time");
        assertNull(s.validateCvdSpxLevels(ok("20260817", 1, 1, "[]", "[]",
                        "{\"priceCents\":637600,\"atMs\":" + (FLOW + 1) + ",\"direction\":\"UP\",\"cvdAtCross\":1}",
                        "null")),
                "a flip crossing after the flow time");
        assertNull(s.validateCvdSpxLevels(ok("20260817", 1, 1, "[]", "[]",
                        "{\"priceCents\":637600,\"atMs\":" + (FLOW - 1) + ",\"direction\":\"SIDEWAYS\",\"cvdAtCross\":1}",
                        "null")),
                "an unknown flip direction");
        assertNull(s.validateCvdSpxLevels(ok("20260817", 1, 1, "[]", "[]", "null", "0")),
                "a non-positive balance price");
        assertNull(s.validateCvdSpxLevels(full.replace(",\"flip\":", ",\"flipX\":")), "flip must be present");
    }

    @Test void oversizedIntegersAreRefusedNotTruncated() {
        var s = service();
        String huge = new java.math.BigInteger("2").pow(70).toString();
        assertNull(s.validateCvdSpxLevels(ok("20260817", 1000, 5).replace("\"foldPositionMs\":1000",
                "\"foldPositionMs\":" + huge)), "asLong() would have wrapped this into a plausible position");
        assertNull(s.validateCvdSpxLevels(ok("20260817", 1000, 5).replace("\"foldPrints\":5",
                "\"foldPrints\":" + ((1L << 53))), "past JS-exact"));
        assertNotNull(s.validateCvdSpxLevels(ok("20260817", 1000, (1L << 53) - 1)), "the boundary is legal");
        assertNull(s.validateCvdSpxLevels(ok("20260817", -1, 5)), "a negative fold position");
        assertNull(s.validateCvdSpxLevels(
                unavailable("source_stale", prov("20260817", -5, 2), false, false)),
                "nor inside sourceProvenance");
        assertNull(s.validateCvdSpxLevels(
                unavailable("source_stale", prov("99999999", 5, 2), false, false)),
                "nor an impossible date");
    }

    @Test void unavailableRecordsCarryAReasonAndNoStructure() {
        var s = service();
        assertNull(s.validateCvdSpxLevels(unavailable("source_stale", "null", false, false)
                        .replace("\"reason\":\"source_stale\"", "\"reason\":\"whatever\"")),
                "an unknown reason is a schema violation");
        assertNull(s.validateCvdSpxLevels(unavailable("source_stale", "null", false, false)
                        .replace(",\"sourceProvenance\"", ",\"buyLevels\":[],\"sourceProvenance\"")),
                "an UNAVAILABLE record has no structure to report");
    }

    @Test void tombstoneWithdrawsTheConnectReplayAndCounts() {
        var s = service();
        s.cvdSpxLevelsLatestForTest().set(levels("OK"));
        ConsumerRecord<String, String> tombstone =
                new ConsumerRecord<>("options.es-cvd-spx-levels", 0, 5L, "ES.v.0", null);
        s.evictCvdSpxLevelsTombstone("es-cvd-spx-levels", tombstone);
        assertNull(s.cvdSpxLevelsLatestForTest().get(), "a withdrawn record must never replay");
        assertEquals(1L, s.cvdSpxLevelsDropsForTest());
        // The baseline goes with it: the next record must not be judged against an erased history.
        assertTrue(s.retainCvdSpxLevels(s.validateCvdSpxLevels(ok("20260101", 1, 1))));
        assertEquals(0L, s.cvdSpxLevelsRegressionsForTest());
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
        assertTrue(metrics.contains("\ngateway_cvd_spx_levels_position_regressions_total 0"));
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
        assertTrue(branch.contains("validateCvdSpxLevels(") && branch.contains("record.key()")
                        && branch.contains("retainCvdSpxLevels(accepted)"),
                "the schema gate (fed the KAFKA KEY) and the regression gate both precede the broadcast");
        assertTrue(branch.contains("broadcast(binding.event(), json);"));
        assertTrue(FeedGatewayServiceSourcePins.isRawPassThrough(source),
                "producer-authored attestation must reach the browser byte-untouched");
    }

    @Test void connectReplayRidesInsideTheHelloContract() throws Exception {
        // CL-R8/G19: the levels record replays INSIDE cvd-hello, so "hello carried no levels
        // record" (levels: null) is distinguishable from "replay still pending" — that is what
        // lets the page choose `no_data` instead of staying blank forever.
        String source = Files.readString(Path.of("src/main/java/app/feedgateway/FeedGatewayService.java"));
        int addClient = source.indexOf("public void addClient(WebSocketSession session)");
        int replayEnd = source.indexOf("if (perSessionRouting())", addClient);
        String head = source.substring(addClient, replayEnd);
        assertTrue(head.contains("esCvdSpxLevelsEnabled()") && head.contains("send(session, \"cvd-hello\""),
                "the hello is sent when EITHER CVD flag is on, for both routing modes");
        assertFalse(head.contains("send(session, \"es-cvd-spx-levels\""),
                "a separate connect frame would reintroduce the ambiguity G19 forbids");
    }

    @Test void helloCarriesTheLevelsFieldOnlyWhenEnabled() {
        var off = service();
        assertFalse(off.cvdHelloJson().contains("levels"), "field absent while the flag is off");

        System.setProperty("GATEWAY_ES_CVD_SPX_LEVELS_ENABLED", "true");
        try {
            var on = service();
            assertTrue(on.cvdHelloJson().contains("\"levels\":null"),
                    "an explicit null IS the completion signal: hello arrived, no record retained");
            on.retainCvdSpxLevels(on.validateCvdSpxLevels(ok("20260817", 1000, 5)));
            assertTrue(on.cvdHelloJson().contains("\"foldPositionMs\":1000"), "retained record rides verbatim");
        } finally {
            System.clearProperty("GATEWAY_ES_CVD_SPX_LEVELS_ENABLED");
        }
    }

    /** Tiny helper so the pin reads as one assertion. */
    private static final class FeedGatewayServiceSourcePins {
        static boolean isRawPassThrough(String source) {
            int m = source.indexOf("private static boolean isRawPassThroughEvent(String event)");
            int end = source.indexOf('}', m);
            return m >= 0 && source.substring(m, end).contains("\"es-cvd-spx-levels\"");
        }
    }

    @Test void startupHydrationIsWiredBehindTheFlag() throws Exception {
        // Without it the only subscriber is the live consumer, which starts at seekToEnd: a
        // restarted gateway would answer every hello with levels:null until the next heartbeat —
        // and if the aligner is down after committing, forever.
        String source = Files.readString(Path.of("src/main/java/app/feedgateway/FeedGatewayService.java"));
        int start = source.indexOf("public void start()");
        int end = source.indexOf("runSelectionConsumer);", start);
        String head = source.substring(start, end);
        assertTrue(head.contains("esCvdSpxLevelsEnabled()") && head.contains("hydrateCvdSpxLevels"),
                "hydration runs at startup, behind the flag, before clients can connect");

        int hydrate = source.indexOf("void hydrateCvdSpxLevels()");
        int hydrateEnd = source.indexOf("CVD_SPX_LEVELS_BASIS_STATES", hydrate);
        assertTrue(hydrate >= 0 && hydrateEnd > hydrate);
        String body = source.substring(hydrate, hydrateEnd);
        assertTrue(body.contains("infos.size() != 1"), "the single-partition contract is re-asserted");
        assertTrue(body.contains("validateCvdSpxLevels(latestKey, latestValue)"),
                "the hydrated record goes through the SAME gate as a live one, key included");
        assertTrue(body.contains("retainCvdSpxLevels(accepted)"), "and through the same retention");
    }

    @Test void theStateFieldMatrixIsEnforcedInBothDirections() {
        var s = service();
        // UNAVAILABLE-only fields on an OK record — malformed WHATEVER their value, because the
        // record reaches the browser verbatim and would read as a state it is not in.
        for (String field : new String[] { "reason", "sourceProvenance", "provenanceRetained", "baselineReset" }) {
            String value = "provenanceRetained".equals(field) || "baselineReset".equals(field) ? "false"
                    : ("reason".equals(field) ? "\"source_stale\"" : "null");
            assertNull(s.validateCvdSpxLevels(ok("20260817", 1000, 5)
                            .replace("\"state\":\"OK\"", "\"state\":\"OK\",\"" + field + "\":" + value)),
                    field + " belongs to UNAVAILABLE only");
        }
        // OK-only fields on an UNAVAILABLE record, provenance included.
        for (String injected : new String[] { "\"sessionDate\":\"20260817\"", "\"foldPositionMs\":10",
                "\"foldPrints\":2", "\"flowEventTimeMs\":1", "\"basisState\":\"MEASURED\"" }) {
            assertNull(s.validateCvdSpxLevels(unavailable("source_stale", "null", false, false)
                            .replace("\"state\":\"UNAVAILABLE\"", "\"state\":\"UNAVAILABLE\"," + injected)),
                    injected + " is OK-only");
        }
    }

    @Test void everyTranslatedPriceObeysItsDeclaredRange() {
        var s = service();
        long max = FeedGatewayService.CVD_SPX_LEVELS_MAX_PRICE_CENTS;
        assertNotNull(s.validateCvdSpxLevels(ok("20260817", 1, 1, "[]", "[]", "null", String.valueOf(max))),
                "the top of the range is legal");
        assertNull(s.validateCvdSpxLevels(ok("20260817", 1, 1, "[]", "[]", "null", String.valueOf(max + 1))),
                "a balance above 10,000,000 cents is not an SPX price");
        assertNull(s.validateCvdSpxLevels(ok("20260817", 1, 1,
                        "[" + level(max + 1, 900) + "]", "[]", "null", "null")));
        assertNull(s.validateCvdSpxLevels(ok("20260817", 1, 1, "[]", "[]",
                        "{\"priceCents\":" + (max + 1) + ",\"atMs\":" + (FLOW - 1)
                                + ",\"direction\":\"UP\",\"cvdAtCross\":1}", "null")));
    }

    @Test void theBasisStateSetMatchesItsNamedAuthority() {
        // SOURCE OF TRUTH: CvdSpxLevelsAligner.OK_BASIS_STATES in options-edge-processing, itself
        // parity-tested against BasisSnapshot.isValid. This repo cannot import it, so the set is
        // pinned here against the same authority: a drift on either side fails a test instead of
        // silently changing what the boundary accepts.
        assertEquals(java.util.Set.of("ANCHORED", "MEASURED", "PROJECTED"),
                FeedGatewayService.CVD_SPX_LEVELS_BASIS_STATES);
    }

    @Test void hydrationIsAStartupBarrierNotABackgroundTask() throws Exception {
        // Submitting it to the executor returned from start() immediately: a client connecting in
        // the next second still got levels:null, and a live tombstone could clear retention only
        // for the late hydration to resurrect the withdrawn record.
        String source = Files.readString(Path.of("src/main/java/app/feedgateway/FeedGatewayService.java"));
        int start = source.indexOf("public void start()");
        int end = source.indexOf("runSelectionConsumer);", start);
        String head = source.substring(start, end);
        assertTrue(head.contains("hydrateCvdSpxLevels();"),
                "hydration is CALLED on the startup path, before any consumer is submitted");
        assertFalse(head.contains("submit(this::hydrateCvdSpxLevels)"),
                "not submitted asynchronously");
    }

    @Test void aForeignKeyTombstoneNeverErasesTheBaseline() {
        // A withdrawal is as governing as a value, so it passes the same key gate. Letting any
        // null-valued record on the topic clear retention would hand a foreign producer an erase.
        var s = service();
        s.retainCvdSpxLevels(s.validateCvdSpxLevels(ok("20260817", 1000, 5)));
        ConsumerRecord<String, String> foreign =
                new ConsumerRecord<>("options.es-cvd-spx-levels", 0, 6L, "SPX", null);
        s.evictCvdSpxLevelsTombstone("es-cvd-spx-levels", foreign);
        assertNotNull(s.cvdSpxLevelsLatestForTest().get(), "the governing record survives");
        assertEquals(1L, s.cvdSpxLevelsDropsForTest(), "and the foreign record is counted");
        // the retention baseline survives too: a lower record is still a regression
        assertFalse(s.retainCvdSpxLevels(s.validateCvdSpxLevels(ok("20260817", 900, 4))));

        ConsumerRecord<String, String> real =
                new ConsumerRecord<>("options.es-cvd-spx-levels", 0, 7L, "ES.v.0", null);
        s.evictCvdSpxLevelsTombstone("es-cvd-spx-levels", real);
        assertNull(s.cvdSpxLevelsLatestForTest().get(), "the contractual tombstone does withdraw");
    }

    @Test void hydrationHandsItsEndOffsetToTheLiveConsumer() throws Exception {
        // Hydration reads to E and closes; the live consumer would otherwise seekToEnd to a later
        // E', skipping everything committed in between — and with the aligner then stopped, the
        // gateway would replay the older hydrated record indefinitely.
        String source = Files.readString(Path.of("src/main/java/app/feedgateway/FeedGatewayService.java"));
        assertTrue(source.contains("cvdSpxLevelsHandoffOffset.set(end)"),
                "hydration records the offset it read to");
        int live = source.indexOf("private void runLiveConsumerOnce(");
        int seek = source.indexOf("seekCvdSpxLevelsToHandoff(consumer, partitions)", live);
        int poll = source.indexOf("consumer.poll(", live);
        assertTrue(seek > live && seek < poll, "and the live consumer starts there, before its first poll");
        assertTrue(source.contains("cvdSpxLevelsHandoffOffset.getAndSet(-1L)"),
                "consumed ONCE, so a reconnect cannot rewind past a live tombstone");
    }

    @Test void theHydrationBudgetIsOneAbsoluteDeadline() throws Exception {
        // Per-call 10s timeouts do not bound a startup step: three metadata calls, a position()
        // and a bounded close can each pay their own, and "15 seconds" becomes a minute.
        String source = Files.readString(Path.of("src/main/java/app/feedgateway/FeedGatewayService.java"));
        int hydrate = source.indexOf("void hydrateCvdSpxLevels()");
        int end = source.indexOf("private void seekCvdSpxLevelsToHandoff", hydrate);
        String body = source.substring(hydrate, end);
        assertFalse(body.contains("Duration.ofSeconds(10)"), "no per-call constant timeouts remain");
        assertTrue(body.indexOf("deadlineNanos = System.nanoTime()") < body.indexOf("partitionsFor("),
                "the deadline is taken BEFORE the first Kafka call");
        assertTrue(body.contains("consumer.close(hydrateRemaining(deadlineNanos))"),
                "and the close spends from the same budget");
    }

    @Test void onlyTheOwningConsumerConsumesTheHandoffOffset() throws Exception {
        // Both live consumers run the helper. Consuming the offset before finding the partition
        // let avro-live — which never holds this topic — destroy it, leaving state-live at its own
        // later seekToEnd and reopening the loss gap.
        String source = Files.readString(Path.of("src/main/java/app/feedgateway/FeedGatewayService.java"));
        int helper = source.indexOf("private void seekCvdSpxLevelsToHandoff");
        int end = source.indexOf("hydrateRemaining(long deadlineNanos)", helper);
        String body = source.substring(helper, end);
        assertTrue(body.indexOf("owned == null") < body.indexOf("getAndSet(-1L)"),
                "ownership is established BEFORE the offset is consumed");
        assertTrue(body.indexOf("getAndSet(-1L)") < body.indexOf("consumer.seek(owned"),
                "and the consuming consumer is the one that seeks");
    }

    @Test void aRetainedTombstoneIsCountedAtStartupToo() throws Exception {
        // Otherwise the counter depends on whether the gateway happened to restart after an
        // operator wipe: the same action, two different histories.
        String source = Files.readString(Path.of("src/main/java/app/feedgateway/FeedGatewayService.java"));
        int hydrate = source.indexOf("void hydrateCvdSpxLevels()");
        int end = source.indexOf("private void seekCvdSpxLevelsToHandoff", hydrate);
        String body = source.substring(hydrate, end);
        int nullCheck = body.indexOf("if (latestValue == null)");
        int count = body.indexOf("cvdSpxLevelsDrops.incrementAndGet()", nullCheck);
        assertTrue(nullCheck >= 0 && count > nullCheck && count - nullCheck < 400,
                "the retained-tombstone branch counts the drop before returning");
    }

    @Test void sessionDateMustBeTextualNotMerelyDigitLike() {
        // asText() coerces the INTEGER 20260817 into "20260817", so a regex-on-asText check looks
        // like type enforcement while accepting a strict-type violation.
        var s = service();
        assertNull(s.validateCvdSpxLevels(ok("20260817", 1000, 5)
                        .replace("\"sessionDate\":\"20260817\"", "\"sessionDate\":20260817")),
                "a numeric top-level sessionDate is malformed");
        assertNull(s.validateCvdSpxLevels(unavailable("source_stale", prov("20260817", 10, 2), false, false)
                        .replace("\"sessionDate\":\"20260817\"", "\"sessionDate\":20260817")),
                "and so is a numeric one inside sourceProvenance");
    }

    @Test void anEmptyCommittedScanIsNotAWithdrawal() throws Exception {
        // begin < end says the log has offsets, not that read_committed will return a user record:
        // the range can hold only aborted batches or control records. Counting that as a tombstone
        // would invent withdrawals that never happened.
        String source = Files.readString(Path.of("src/main/java/app/feedgateway/FeedGatewayService.java"));
        int hydrate = source.indexOf("void hydrateCvdSpxLevels()");
        int end = source.indexOf("private void seekCvdSpxLevelsToHandoff", hydrate);
        String body = source.substring(hydrate, end);
        assertTrue(body.contains("boolean sawRecord = false"), "the two states are tracked apart");
        assertTrue(body.indexOf("if (!sawRecord) return;") < body.indexOf("if (latestValue == null)"),
                "an empty scan returns BEFORE the tombstone branch can count a drop");
    }
}

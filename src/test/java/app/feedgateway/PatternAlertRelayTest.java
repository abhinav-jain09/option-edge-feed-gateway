package app.feedgateway;

import static app.feedgateway.PatternAlertFixtures.DATE;
import static app.feedgateway.PatternAlertFixtures.WINDOW_MS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.avro.generic.GenericRecord;
import org.junit.jupiter.api.Test;

/**
 * The alert relay without a socket: the gateway boundary, the per-profile retained state, the frames and the hello part (design section 6.2), with the records of the live session of
 * 2026-11-03 built from the contracts 0.66.0 schemas.
 */
class PatternAlertRelayTest {
    static final ObjectMapper JSON = new ObjectMapper();
    static final String REGULAR = PatternAlertRelay.REGULAR_EVENT;
    static final String ESTIMATE = PatternAlertRelay.ESTIMATE_EVENT;

    /** The test clock sits just after the wedge's accepting bar (13:41), inside every window. */
    final AtomicLong now = new AtomicLong(PatternAlertFixtures.barEnd(250) + 5_000L);

    PatternAlertRelay relay() {
        return relay(256);
    }

    PatternAlertRelay relay(int retained) {
        PatternAlertRelay r = new PatternAlertRelay(WINDOW_MS, retained, 16L * 1024 * 1024, now::get);
        r.beginRecovery();
        r.promote();   // ready: the empty history of a topic without records
        return r;
    }

    static JsonNode json(String text) {
        try {
            return JSON.readTree(text);
        } catch (Exception e) {
            throw new IllegalStateException(text, e);
        }
    }

    static int bytes(String s) {
        return StudiesRelay.utf8Length(s);
    }

    @Test
    void aRaisedLiveBreakIsOneDisplayedAndSpokenFrameCarryingTheFullRecordAndTheServersSentence() {
        PatternAlertRelay r = relay();
        now.set(PatternAlertFixtures.barEnd(160) + 5_000L);
        String[] frame = r.onLive(REGULAR, PatternAlertFixtures.triangleBreak(1, "RAISED"));
        assertEquals("market-structure-alerts", frame[0]);
        JsonNode f = json(frame[1]);
        assertEquals("event", f.get("type").asText());
        assertEquals("regular", f.get("profile").asText());
        assertTrue(f.get("display").asBoolean());
        assertTrue(f.get("speak").asBoolean());
        assertFalse(f.has("compact"));
        JsonNode e = f.get("event");
        assertEquals(1, e.get("eventSeq").asLong());
        assertEquals("BREAK", e.get("kind").asText());
        assertEquals("RAISED", e.get("disposition").asText());
        assertEquals("TF_1M", e.get("timeframe").asText());
        assertEquals("1m", e.get("tf").asText(), "the page's spelling of the timeframe rides every full event");
        assertEquals(757_821L, e.get("priceTicks").asLong());
        assertEquals("Bullish symmetrical triangle, one minute, broke up at 7,578.21", e.get("speech").asText());
        assertEquals(PatternAlertFixtures.barEnd(160), e.get("availableMs").asLong());
        assertEquals(PatternAlertFixtures.barEnd(160) + WINDOW_MS, e.get("speakUntilMs").asLong());
        assertEquals(PatternAlertFixtures.OPEN_MS, e.get("nav").get("rthOpenMs").asLong());
        assertTrue(e.get("display").asBoolean() && e.get("speak").asBoolean(), "the decisions ride the event object too, so the page's shouldRender reads one object");
        assertTrue(bytes(e.toString()) < PatternAlertRelay.RECORD_MAX_BYTES);
    }

    @Test
    void speechIsOffOnceTheGatewayClockIsPastTheProducersSpeakUntilButTheEventIsStillDisplayed() {
        PatternAlertRelay r = relay();
        GenericRecord e = PatternAlertFixtures.triangleBreak(1, "RAISED");
        now.set(PatternAlertFixtures.barEnd(160) + WINDOW_MS);   // the boundary: still inside (<=)
        assertTrue(json(r.onLive(REGULAR, e)[1]).get("speak").asBoolean());
        now.set(PatternAlertFixtures.barEnd(160) + WINDOW_MS + 1);
        JsonNode late = json(r.onLive(REGULAR, PatternAlertFixtures.wedgeBreak(2, "RAISED"))[1]);
        assertTrue(late.get("display").asBoolean());
        assertTrue(late.get("speak").asBoolean(), "the clock is past the triangle's window but inside the wedge's: each frame is compared with ITS OWN speakUntilMs");
        now.set(PatternAlertFixtures.barEnd(254) + WINDOW_MS + 1);
        JsonNode later = json(r.onLive(REGULAR, PatternAlertFixtures.wedgeReturn(3, "RAISED"))[1]);
        assertTrue(later.get("display").asBoolean());
        assertFalse(later.get("speak").asBoolean());
        assertFalse(later.get("event").get("speak").asBoolean());
    }

    @Test
    void aSuppressedEventTravelsOnlyInCompactFormForItsMarkAndIsNeverDisplayedOrSpoken() {
        PatternAlertRelay r = relay();
        for (String disposition : List.of("SUPPRESSED_STALE", "SUPPRESSED_REPLAY")) {
            JsonNode f = json(r.onLive(REGULAR, PatternAlertFixtures.numbered(DATE, "SUPPRESSED_STALE".equals(disposition) ? 1 : 2, disposition, 100))[1]);
            assertFalse(f.get("display").asBoolean(), disposition);
            assertFalse(f.get("speak").asBoolean(), disposition);
            assertFalse(f.has("event"), disposition);
            JsonNode c = f.get("compact");
            assertEquals(disposition, c.get("disposition").asText());
            assertEquals("5m", c.get("tf").asText());
            for (String key : List.of("annotationId", "kind", "stance", "eventSeq", "patternId", "patternFamily", "breakOrdinal", "direction")) {
                assertTrue(c.has(key), key);
            }
            assertFalse(c.has("speech") || c.has("line"), "no text travels with a suppressed event");
            assertFalse(c.get("display").asBoolean());
            assertFalse(c.get("speak").asBoolean());
        }
    }

    @Test
    void aSelfCheckSuppressionIsCountedAndNotForwardedYetItConsumesItsSequenceNumber() {
        PatternAlertRelay r = relay();
        assertNull(r.onLive(REGULAR, PatternAlertFixtures.numbered(DATE, 1, "SUPPRESSED_SELF_CHECK", 90)), "AL-16: not forwarded");
        assertEquals(1, r.highForTest(0), "the sequence it consumed is accounted");
        assertEquals(0, r.retainedForTest(0), "and it is not retained");
        assertNotNull(r.onLive(REGULAR, PatternAlertFixtures.numbered(DATE, 2, "RAISED", 91)));
        assertEquals(1, r.retainedForTest(0));
        assertTrue(r.metrics().contains("gateway_pattern_alerts_self_check_not_forwarded_total 1\n"), r.metrics());
        assertFalse(r.helloField(Long.MAX_VALUE).contains("SUPPRESSED_SELF_CHECK"));
    }

    @Test
    void anEventAtOrBelowTheHighWaterIsACountedRegressionWithNoFrameAndNoStateChange() {
        PatternAlertRelay r = relay();
        assertNotNull(r.onLive(REGULAR, PatternAlertFixtures.triangleBreak(1, "RAISED")));
        assertNotNull(r.onLive(REGULAR, PatternAlertFixtures.wedgeBreak(2, "RAISED")));
        String before = r.helloField(Long.MAX_VALUE);
        assertNull(r.onLive(REGULAR, PatternAlertFixtures.wedgeBreak(2, "RAISED")), "a duplicate");
        assertNull(r.onLive(REGULAR, PatternAlertFixtures.triangleBreak(1, "RAISED")), "an older record");
        assertEquals(2, r.regressionsForTest());
        assertEquals(before, r.helloField(Long.MAX_VALUE));
        assertEquals(2, r.acceptedForTest());
    }

    @Test
    void theProfileIsNamedByTheBindingEventAndEachProfileNumbersItsOwnEventsFromOne() {
        PatternAlertRelay r = relay();
        JsonNode regular = json(r.onLive(REGULAR, PatternAlertFixtures.triangleBreak(1, "RAISED"))[1]);
        String[] estimate = r.onLive(ESTIMATE, PatternAlertFixtures.wedgeBreak(1, "RAISED"));
        assertNotNull(estimate, "the same session date and the same eventSeq in the other profile is not a regression");
        assertEquals("market-structure-alerts-estimate", estimate[0]);
        assertEquals("estimate", json(estimate[1]).get("profile").asText());
        assertEquals("regular", regular.get("profile").asText());
        assertEquals(0L, r.regressionsForTest());
        JsonNode hello = json("{" + r.helloField(Long.MAX_VALUE).substring(1) + "}").get("alerts").get("profiles");
        assertEquals("TRIANGLE_SYMMETRICAL", hello.get("regular").get("events").get(0).get("patternId").asText());
        assertEquals("WEDGE_RISING", hello.get("estimate").get("events").get(0).get("patternId").asText());
        assertEquals(1, hello.get("regular").get("eventSeqHigh").asLong());
        assertEquals(1, hello.get("estimate").get("eventSeqHigh").asLong());
        assertTrue(r.handles(REGULAR) && r.handles(ESTIMATE) && !r.handles("market-structure-geometry"));
    }

    @Test
    void theGatewayBoundaryRefusesWhatItCannotVouchForAndCountsEachRefusal() {
        PatternAlertRelay r = relay();
        List<Object> bad = new ArrayList<>();
        bad.add(PatternAlertFixtures.with(PatternAlertFixtures.triangleBreak(1, "RAISED"), "schemaVersion", 2));
        bad.add(PatternAlertFixtures.with(PatternAlertFixtures.triangleBreak(1, "RAISED"), "service", "somebody-else"));
        bad.add(PatternAlertFixtures.with(PatternAlertFixtures.triangleBreak(1, "RAISED"), "sessionDate", "03-11-2026"));
        bad.add(PatternAlertFixtures.with(PatternAlertFixtures.triangleBreak(1, "RAISED"), "eventSeq", 0L));
        bad.add(PatternAlertFixtures.with(PatternAlertFixtures.triangleBreak(1, "RAISED"), "kind", "UNKNOWN"));
        bad.add(PatternAlertFixtures.withUnknownKindSymbol(PatternAlertFixtures.triangleBreak(1, "RAISED"), "SQUEEZE"));   // a symbol of a later writer schema is not relayed until this relay knows it
        bad.add(PatternAlertFixtures.with(PatternAlertFixtures.triangleBreak(1, "RAISED"), "disposition", "UNKNOWN"));
        bad.add(PatternAlertFixtures.with(PatternAlertFixtures.triangleBreak(1, "RAISED"), "timeframe", "UNKNOWN"));
        bad.add(PatternAlertFixtures.with(PatternAlertFixtures.triangleBreak(1, "RAISED"), "tip", "x".repeat(PatternAlertRelay.RECORD_MAX_BYTES)));
        bad.add(GatewayAvroDeserializer.UNDECODABLE);
        bad.add("not a record");
        bad.add(TaFixtures.tools("M1", 1, "OK"));
        for (Object o : bad) {
            assertNull(r.onLive(REGULAR, o), String.valueOf(o).substring(0, Math.min(40, String.valueOf(o).length())));
        }
        assertEquals(bad.size(), r.dropsForTest());
        assertNull(r.onLive("some-other-event", PatternAlertFixtures.triangleBreak(1, "RAISED")));
        assertEquals(0, r.acceptedForTest());
        assertEquals(0, r.retainedForTest(0));
    }

    @Test
    void aWatermarkIsIgnoredAndCountedBecauseOnlyTheAuditReadUsesIt() {
        PatternAlertRelay r = relay();
        assertNull(r.onLive(REGULAR, PatternAlertFixtures.watermark(DATE, "TF_1M", 5, 4, true, 0)));
        assertEquals(0, r.dropsForTest());
        assertEquals(0, r.highForTest(0));
        assertTrue(r.metrics().contains("gateway_pattern_alerts_watermarks_ignored_total 1\n"));
    }

    @Test
    void aNewerSessionDateReplacesTheProfilesStateAndAnEarlierOneIsARegression() {
        PatternAlertRelay r = relay();
        r.onLive(REGULAR, PatternAlertFixtures.numbered("2026-11-03", 1, "RAISED", 10));
        r.onLive(REGULAR, PatternAlertFixtures.numbered("2026-11-03", 2, "RAISED", 11));
        assertNotNull(r.onLive(REGULAR, PatternAlertFixtures.numbered("2026-11-04", 1, "RAISED", 12)), "a new session starts a new sequence space at 1");
        assertEquals(1, r.highForTest(0));
        assertEquals(1, r.retainedForTest(0));
        assertNull(r.onLive(REGULAR, PatternAlertFixtures.numbered("2026-11-03", 3, "RAISED", 13)));
        assertEquals(1, r.regressionsForTest());
        assertTrue(r.helloField(Long.MAX_VALUE).contains("\"sessionDate\":\"2026-11-04\""));
    }

    @Test
    void theNewestEventsAreRetainedWithinTheBoundAndTheOldestIsEvicted() {
        PatternAlertRelay r = relay(8);
        for (int i = 1; i <= 12; i++) {
            r.onLive(REGULAR, PatternAlertFixtures.numbered(DATE, i, "SUPPRESSED_STALE", 100 + i));
        }
        assertEquals(8, r.retainedForTest(0));
        assertEquals(12, r.highForTest(0), "the high-water is advanced by every valid event including those not retained");
        JsonNode p = json("{" + r.helloField(Long.MAX_VALUE).substring(1) + "}").get("alerts").get("profiles").get("regular");
        assertEquals(5, p.get("eventSeqLow").asLong());
        assertEquals(12, p.get("eventSeqHigh").asLong());
        assertTrue(p.get("truncated").asBoolean(), "something was evicted: the hello says so");
        assertEquals(8, p.get("events").size());
    }

    @Test
    void onlyTheNewestFortyEightRaisedEventsKeepTheirFullRecord() {
        PatternAlertRelay r = relay(256);
        now.set(PatternAlertFixtures.barEnd(102) + 5_000L);   // inside every event's window
        for (int i = 1; i <= 60; i++) {
            r.onLive(REGULAR, PatternAlertFixtures.numbered(DATE, i, "RAISED", 100));
        }
        assertEquals(60, r.retainedForTest(0));
        assertEquals(PatternAlertRelay.FULL_RAISED_MAX, r.fullsForTest(0));
        JsonNode events = json("{" + r.helloField(Long.MAX_VALUE).substring(1) + "}").get("alerts").get("profiles").get("regular").get("events");
        assertFalse(events.get(0).has("speech"), "the oldest are compact: a mark, not a toast");
        assertEquals(12, events.get(11).get("eventSeq").asLong());
        assertTrue(events.get(12).has("speech"), "eventSeq 13 is the 48th from the newest");
        assertTrue(events.get(59).has("speech"));
    }

    @Test
    void aHelloEventIsFullWithDisplayOffOnlyWhileTheResumeCouldStillDisplayItAndCompactOtherwise() {
        PatternAlertRelay r = relay();
        r.onLive(REGULAR, PatternAlertFixtures.triangleBreak(1, "RAISED"));      // available 12:11
        r.onLive(REGULAR, PatternAlertFixtures.wedgeBreak(2, "RAISED"));         // available 13:41
        r.onLive(REGULAR, PatternAlertFixtures.numbered(DATE, 3, "SUPPRESSED_STALE", 249));
        now.set(PatternAlertFixtures.barEnd(250) + 5_000L);   // the triangle's window (to 12:41) is over, the wedge's is not
        JsonNode events = json("{" + r.helloField(Long.MAX_VALUE).substring(1) + "}").get("alerts").get("profiles").get("regular").get("events");
        assertFalse(events.get(0).has("speech"), "outside the window: compact");
        assertEquals("compact", events.get(0).has("speech") ? "full" : "compact");
        assertTrue(events.get(1).has("speech"));
        assertFalse(events.get(1).get("display").asBoolean(), "a hello never displays: the resume does");
        assertFalse(events.get(1).get("speak").asBoolean());
        assertFalse(events.get(2).has("speech"));
        assertEquals("SUPPRESSED_STALE", events.get(2).get("disposition").asText());
    }

    @Test
    void theHelloPartIsHeldUnderNinetySixKilobytesWhateverTheBudgetOffersAndItsSkeletonIsAlwaysThere() {
        PatternAlertRelay r = relay(256);
        for (int i = 1; i <= 200; i++) {
            GenericRecord e = PatternAlertFixtures.numbered(DATE, i, i <= 100 ? "SUPPRESSED_STALE" : "RAISED", 100 + i);
            PatternAlertFixtures.with(e, "tip", "t".repeat(1_500));   // a realistic evidence sentence makes the full records large
            r.onLive(REGULAR, e);
            r.onLive(ESTIMATE, e);
        }
        String huge = r.helloField(Long.MAX_VALUE);
        assertTrue(bytes(huge) <= PatternAlertRelay.HELLO_PART_BYTES + 512, "bytes " + bytes(huge));
        assertTrue(bytes(huge) > PatternAlertRelay.HELLO_PART_BYTES / 2, "and it really uses its share: " + bytes(huge));
        JsonNode p = json("{" + huge.substring(1) + "}").get("alerts").get("profiles");
        assertTrue(p.get("regular").get("truncated").asBoolean());
        assertEquals(200, p.get("regular").get("eventSeqHigh").asLong(), "trimming never changes the high-water");
        // newer events win: the newest event is still there, the oldest compact ones went first
        JsonNode events = p.get("regular").get("events");
        assertEquals(200, events.get(events.size() - 1).get("eventSeq").asLong());
        String tight = r.helloField(4_000);
        assertTrue(bytes(tight) <= 4_000 + 512, "a tight budget is honoured: " + bytes(tight));
        JsonNode t = json("{" + tight.substring(1) + "}").get("alerts");
        assertEquals(WINDOW_MS, t.get("windowMs").asLong());
        assertTrue(t.get("profiles").get("regular").get("truncated").asBoolean());
        String none = r.helloField(0);
        assertTrue(none.contains("\"events\":[]"), "the skeleton is sent with every entry empty: the field's presence is the flag");
    }

    @Test
    void theCompactEventsGiveWayBeforeTheFullOnesWhenTheHelloIsTrimmed() {
        PatternAlertRelay r = relay(256);
        for (int i = 1; i <= 40; i++) {
            r.onLive(REGULAR, PatternAlertFixtures.numbered(DATE, i, "SUPPRESSED_STALE", 100 + i));
        }
        r.onLive(REGULAR, PatternAlertFixtures.wedgeBreak(41, "RAISED"));
        String cap = r.helloField(1_800);
        JsonNode events = json("{" + cap.substring(1) + "}").get("alerts").get("profiles").get("regular").get("events");
        assertTrue(events.size() >= 1);
        assertTrue(events.get(events.size() - 1).has("speech"), "the one full RAISED event survives the trimming of the compact ones");
    }

    @Test
    void aProfileThatHasNotPromotedItsRecoveryHelloesNotReadyWithNoEventsAndNoHighWater() {
        PatternAlertRelay r = new PatternAlertRelay(WINDOW_MS, 256, 16L * 1024 * 1024, now::get);
        r.beginRecovery();
        r.onLive(REGULAR, PatternAlertFixtures.triangleBreak(7, "RAISED"));   // a live record that arrived during the recovery
        JsonNode p = json("{" + r.helloField(Long.MAX_VALUE).substring(1) + "}").get("alerts").get("profiles").get("regular");
        assertFalse(p.get("ready").asBoolean());
        assertEquals(0, p.get("events").size());
        assertEquals(0, p.get("eventSeqHigh").asLong());
        assertTrue(p.get("sessionDate").isNull());
        r.promote();
        JsonNode ready = json("{" + r.helloField(Long.MAX_VALUE).substring(1) + "}").get("alerts").get("profiles").get("regular");
        assertTrue(ready.get("ready").asBoolean());
        assertEquals(7, ready.get("eventSeqHigh").asLong());
        assertEquals(1, ready.get("events").size());
    }

    @Test
    void theHistoryOfTheRecoveryIsMergedUnderTheLiveRecordsAtPromotionAndBroadcastsNothing() {
        PatternAlertRelay r = new PatternAlertRelay(WINDOW_MS, 256, 16L * 1024 * 1024, now::get);
        r.beginRecovery();
        r.onStaged(REGULAR, PatternAlertFixtures.triangleBreak(1, "SUPPRESSED_STALE"));
        r.onStaged(REGULAR, PatternAlertFixtures.wedgeBreak(2, "RAISED"));
        r.onStaged(REGULAR, PatternAlertFixtures.watermark(DATE, "TF_1M", 5, 4, true, 2));
        r.onStaged(ESTIMATE, PatternAlertFixtures.numbered(DATE, 1, "RAISED", 40));
        assertNotNull(r.onLive(REGULAR, PatternAlertFixtures.wedgeReturn(3, "RAISED")), "the live record that follows the captured end offset");
        assertTrue(r.promote().isEmpty(), "history is never broadcast");
        assertTrue(r.readyForTest(0) && r.readyForTest(1));
        assertEquals(3, r.highForTest(0));
        assertEquals(3, r.retainedForTest(0));
        assertEquals(1, r.retainedForTest(1));
        JsonNode events = json("{" + r.helloField(Long.MAX_VALUE).substring(1) + "}").get("alerts").get("profiles").get("regular").get("events");
        assertEquals(List.of(1L, 2L, 3L), List.of(events.get(0).get("eventSeq").asLong(), events.get(1).get("eventSeq").asLong(), events.get(2).get("eventSeq").asLong()));
        assertEquals(8_192, r.tailRecords(REGULAR));
    }

    @Test
    void aRecoveryAfterTheRelayIsLiveAddsOlderHistoryWithoutDuplicatingOrRegressing() {
        PatternAlertRelay r = relay();
        r.onLive(REGULAR, PatternAlertFixtures.wedgeReturn(3, "RAISED"));
        r.beginRecovery();
        r.onStaged(REGULAR, PatternAlertFixtures.triangleBreak(1, "RAISED"));
        r.onStaged(REGULAR, PatternAlertFixtures.wedgeBreak(2, "RAISED"));
        r.onStaged(REGULAR, PatternAlertFixtures.wedgeReturn(3, "RAISED"));
        r.promote();
        assertEquals(3, r.retainedForTest(0));
        assertEquals(3, r.highForTest(0));
    }

    @Test
    void theFieldAndTheFramesAreStableValidJson() {
        PatternAlertRelay r = relay();
        r.onLive(REGULAR, PatternAlertFixtures.triangleBreak(1, "RAISED"));
        JsonNode hello = json("{" + r.helloField(Long.MAX_VALUE).substring(1) + "}");
        assertEquals(List.of("windowMs", "profiles"), names(hello.get("alerts")));
        assertEquals(List.of("regular", "estimate"), names(hello.get("alerts").get("profiles")));
        assertEquals(List.of("sessionDate", "ready", "eventSeqLow", "eventSeqHigh", "truncated", "events"), names(hello.get("alerts").get("profiles").get("regular")));
        assertEquals(r.helloField(Long.MAX_VALUE), r.helloField(Long.MAX_VALUE), "deterministic");
        assertEquals(0, hello.get("alerts").get("profiles").get("estimate").get("events").size());
    }

    @Test
    void aReplyEventInsideTheGatewayWindowButPastTheProducersSpeakUntilIsDisplayedWithoutSpeech() {
        PatternAlertRelay r = new PatternAlertRelay(3_600_000L, 256, 16L * 1024 * 1024, now::get);   // a gateway window longer than the producer's: the producer's speakUntilMs is the authority for speech
        r.beginRecovery();
        r.promote();
        now.set(PatternAlertFixtures.barEnd(160) + 5_000L);
        r.onLive(REGULAR, PatternAlertFixtures.triangleBreak(1, "RAISED"));
        PatternAlertRelay.clearHelloSnapshot();
        r.helloField(Long.MAX_VALUE);
        r.bindHello("s");
        now.set(PatternAlertFixtures.barEnd(160) + WINDOW_MS + 60_000L);
        JsonNode d = json(r.resume("s", "{\"type\":\"alerts-resume\",\"reason\":\"initial\",\"profile\":\"regular\",\"sessionDate\":\"" + DATE + "\",\"cursor\":0}")[1]);
        assertEquals(1, d.get("events").size());
        assertTrue(d.get("events").get(0).get("display").asBoolean());
        assertFalse(d.get("events").get(0).get("speak").asBoolean());
    }

    static List<String> names(JsonNode node) {
        List<String> names = new ArrayList<>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }
}

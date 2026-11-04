package app.feedgateway;

import static app.feedgateway.PatternAlertFixtures.WINDOW_MS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.avro.generic.GenericRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

/**
 * The alert relay inside the gateway: the hello placement, the per-socket snapshot map, the resume message and its reply, the frames' delivery rules and the flag (design sections 6.1 to 6.3).
 */
class PatternAlertServiceTest {
    static final ObjectMapper JSON = new ObjectMapper();
    final AtomicLong now = new AtomicLong(PatternAlertFixtures.barEnd(250) + 5_000L);

    @BeforeEach
    void clock() {
        PatternAlertRelay.clockForTest = now::get;
    }

    @AfterEach
    void unclock() {
        PatternAlertRelay.clockForTest = null;
    }

    /** One connected socket: every envelope sent to it, parsed. */
    static final class Wire {
        final WebSocketSession socket = mock(WebSocketSession.class);
        final List<JsonNode> sent = new CopyOnWriteArrayList<>();

        Wire(String id) throws Exception {
            when(socket.getId()).thenReturn(id);
            when(socket.isOpen()).thenReturn(true);
            doAnswer(call -> {
                sent.add(JSON.readTree(((TextMessage) call.getArgument(0)).getPayload()));
                return null;
            }).when(socket).sendMessage(any());
        }

        List<JsonNode> ofType(String type) {
            return sent.stream().filter(n -> type.equals(n.path("type").asText())).toList();
        }

        /** The data of the alert frames (both binding events) whose inner type is {@code inner}. */
        List<JsonNode> alerts(String inner) {
            return sent.stream().filter(n -> n.path("type").asText().startsWith("market-structure-alerts") && inner.equals(n.path("data").path("type").asText())).map(n -> n.get("data")).toList();
        }

        JsonNode last(String inner) {
            List<JsonNode> all = alerts(inner);
            return all.get(all.size() - 1);
        }

        JsonNode hello() {
            return ofType("cvd-hello").get(0).get("data");
        }
    }

    static FeedGatewayService service(boolean geometry) {
        GatewaySettings settings = spy(new GatewaySettings());
        doReturn(true).when(settings).patternAlertsEnabled();
        if (geometry) {
            doReturn(true).when(settings).geometryEnabled();
        }
        FeedGatewayService s = new FeedGatewayService(settings, JSON, new HpsfGatewayViewMapper(), null);
        s.runOutboundWritesInline();
        return s;
    }

    static PatternAlertRelay relay(FeedGatewayService s) {
        return (PatternAlertRelay) s.taRelaysForTest().stream().filter(r -> r instanceof PatternAlertRelay).findFirst().orElseThrow();
    }

    static void ready(PatternAlertRelay r) {
        r.beginRecovery();
        r.promote();
    }

    static String resume(String reason, String profile, String date, Object cursor) {
        return "{\"type\":\"alerts-resume\",\"reason\":\"" + reason + "\",\"profile\":\"" + profile + "\",\"sessionDate\":\"" + date + "\",\"cursor\":" + cursor + "}";
    }

    /** The single reply a resume message produced after {@code mark} frames. */
    static JsonNode reply(Wire w, int mark) {
        assertEquals(mark + 1, w.sent.size(), "a resume message is ALWAYS answered, with exactly one frame");
        return w.sent.get(mark).get("data");
    }

    static void feedSession(PatternAlertRelay r) {
        r.onLive(PatternAlertRelay.REGULAR_EVENT, PatternAlertFixtures.triangleBreak(1, "RAISED"));
        r.onLive(PatternAlertRelay.REGULAR_EVENT, PatternAlertFixtures.wedgeBreak(2, "RAISED"));
        r.onLive(PatternAlertRelay.REGULAR_EVENT, PatternAlertFixtures.wedgeReturn(3, "RAISED"));
    }

    // ── the flag and the hello ────────────────────────────────────────────────────────────────────

    @Test
    void withTheFlagOffThereIsNoRelayNoAlertsFieldNoAuditAndNoBinding() throws Exception {
        FeedGatewayService s = new FeedGatewayService(new GatewaySettings(), JSON, new HpsfGatewayViewMapper(), null);
        assertFalse(new GatewaySettings().patternAlertsEnabled());
        assertTrue(s.taRelaysForTest().stream().noneMatch(r -> r instanceof PatternAlertRelay));
        assertNull(s.patternAlertAudit());
        assertFalse(s.cvdHelloJson().contains("alerts"));
        assertFalse(s.metrics().contains("gateway_pattern_alerts_"));
        s.runOutboundWritesInline();
        Wire w = new Wire("off");
        s.addClient(w.socket);
        s.handleFootprintBasicMessage(w.socket, resume("initial", "regular", PatternAlertFixtures.DATE, 0));
        assertTrue(w.alerts("resume-error").isEmpty() && w.alerts("display").isEmpty(), "with the relay off an alert message gets the footprint path, which ignores it");
        Method bind = FeedGatewayService.class.getDeclaredMethod("runAvroLiveConsumer");
        assertNotNull(bind);
    }

    @Test
    void theSettingsNameTheProducersTopicsAndTheBoundsAndTheFlagIsOffByDefault() {
        GatewaySettings s = new GatewaySettings();
        assertEquals("options.spx.market-structure.pattern-alerts", s.patternAlertsTopic());
        assertEquals("options.spx.market-structure.estimate.pattern-alerts", s.patternAlertsEstimateTopic());
        assertEquals(1_800_000L, s.patternAlertsWindowMs());
        assertEquals(256, s.patternAlertsRetained());
        assertEquals(604_800_000L, s.patternAlertsRetentionMs());
        assertEquals(10_000L, s.patternAlertsAuditTimeoutMs());
        assertFalse(s.patternAlertsEnabled());
        assertFalse(s.patternAlertsActive());
        assertNull(s.patternAlertsRefusal());
    }

    @Test
    void aQueueBelowTheFloorMakesTheRelayRefuseToEnableLoudlyAndNotRunWithABudgetItCannotHonour() {
        System.setProperty("GATEWAY_PATTERN_ALERTS_ENABLED", "true");
        System.setProperty("GATEWAY_WS_MAX_QUEUED_BYTES", String.valueOf(PatternAlertRelay.MIN_WS_QUEUED_BYTES - 1));
        try {
            GatewaySettings low = new GatewaySettings();
            assertNotNull(low.patternAlertsRefusal());
            assertFalse(low.patternAlertsActive());
            FeedGatewayService s = new FeedGatewayService(low, JSON, new HpsfGatewayViewMapper(), null);
            assertTrue(s.taRelaysForTest().isEmpty());
            assertNull(s.patternAlertAudit());
            assertTrue(s.metrics().contains("gateway_pattern_alerts_refused 1\n"), "the refusal is visible in the metrics");
            System.setProperty("GATEWAY_WS_MAX_QUEUED_BYTES", String.valueOf(PatternAlertRelay.MIN_WS_QUEUED_BYTES));
            assertTrue(new GatewaySettings().patternAlertsActive(), "exactly the floor is enough");
        } finally {
            System.clearProperty("GATEWAY_PATTERN_ALERTS_ENABLED");
            System.clearProperty("GATEWAY_WS_MAX_QUEUED_BYTES");
        }
    }

    @Test
    void theAlertsPartIsTheFirstPartOfTheHelloAndEveryOtherPartIsStillThere() throws Exception {
        FeedGatewayService s = service(true);
        assertTrue(s.taRelaysForTest().get(0) instanceof PatternAlertRelay, "placed FIRST: the part is small and decisive");
        ready(relay(s));
        String hello = s.cvdHelloJson();
        assertTrue(hello.indexOf("\"alerts\":") < hello.indexOf("\"geometry\":"), hello.substring(0, Math.min(300, hello.length())));
        JsonNode node = JSON.readTree(hello);
        assertEquals(WINDOW_MS, node.get("alerts").get("windowMs").asLong());
        assertTrue(node.get("alerts").get("profiles").get("regular").get("ready").asBoolean());
        assertTrue(node.has("geometry"));
    }

    @Test
    void theAlertFramesAreNeverCoalescedAndAreAllowedToReachEverySocket() throws Exception {
        java.lang.reflect.Field f = FeedGatewayService.class.getDeclaredField("COALESCABLE_EVENTS");
        f.setAccessible(true);
        Set<?> coalescable = (Set<?>) f.get(null);
        Method key = FeedGatewayService.class.getDeclaredMethod("coalesceKeyFor", String.class, String.class);
        key.setAccessible(true);
        FeedGatewayService s = service(false);
        for (String event : List.of(PatternAlertRelay.REGULAR_EVENT, PatternAlertRelay.ESTIMATE_EVENT)) {
            assertFalse(coalescable.contains(event), event);
            assertNull(key.invoke(s, event, "{\"type\":\"event\",\"profile\":\"regular\"}"), "every alert frame must be delivered");
            assertTrue(FeedGatewayService.GLOBAL_BROADCAST_EVENTS.contains(event), "and in per-session mode too");
        }
    }

    @Test
    void theHelloOfTheFootprintStrikePathIsBoundToItsSocketToo() throws Exception {
        FootprintTopicGateTest.FakeReader reader = new FootprintTopicGateTest.FakeReader();
        for (String t : List.of("futures.footprint", "futures.footprint.evidence", "futures.footprint.bars", "futures.footprint.outcomes", "futures.footprint.strike")) {
            reader.valid(t);
        }
        GatewaySettings settings = spy(new GatewaySettings());
        doReturn(true).when(settings).patternAlertsEnabled();
        FeedGatewayService s = new FeedGatewayService(settings, JSON, new HpsfGatewayViewMapper(), null, reader);
        s.runOutboundWritesInline();
        PatternAlertRelay r = relay(s);
        ready(r);
        now.set(PatternAlertFixtures.barEnd(160) + 5_000L);
        feedSession(r);
        Wire w = new Wire("strike");
        s.addClient(w.socket);   // the hello is composed and delivered by the strike view's drainer, not by this method
        assertEquals(1, w.ofType("cvd-hello").size());
        assertTrue(w.hello().has("alerts"));
        assertEquals(1, r.socketsForTest(), "bound exactly once");
        int mark = w.sent.size();
        s.handleFootprintBasicMessage(w.socket, resume("initial", "regular", PatternAlertFixtures.DATE, 0));
        assertEquals(3, reply(w, mark).get("events").size());
    }

    // ── resume ────────────────────────────────────────────────────────────────────────────────────

    @Test
    void anInitialResumeIsAnsweredWithTheRaisedEventsAboveTheCursorEachMarkedForDisplayAndSpeech() throws Exception {
        FeedGatewayService s = service(false);
        PatternAlertRelay r = relay(s);
        ready(r);
        now.set(PatternAlertFixtures.barEnd(250) + 5_000L);   // the triangle's window (to 12:41) is over; the wedge's and its return's are not
        feedSession(r);
        Wire w = new Wire("page-1");
        s.addClient(w.socket);
        assertTrue(w.hello().get("alerts").get("profiles").get("regular").get("ready").asBoolean());
        int mark = w.sent.size();
        s.handleFootprintBasicMessage(w.socket, resume("initial", "regular", PatternAlertFixtures.DATE, 0));
        JsonNode d = reply(w, mark);
        assertEquals("market-structure-alerts", w.sent.get(mark).get("type").asText());
        assertEquals("display", d.get("type").asText());
        assertEquals("regular", d.get("profile").asText());
        assertEquals(PatternAlertFixtures.DATE, d.get("sessionDate").asText());
        assertFalse(d.get("reset").asBoolean());
        assertEquals(3, d.get("eventSeqHigh").asLong());
        assertEquals(2, d.get("eventSeqLow").asLong(), "the triangle is outside the window: not displayed");
        assertFalse(d.get("truncated").asBoolean());
        assertEquals(2, d.get("events").size());
        for (JsonNode e : d.get("events")) {
            assertTrue(e.get("display").asBoolean());
            assertTrue(e.get("speak").asBoolean());
            assertEquals("RAISED", e.get("disposition").asText());
            assertTrue(e.has("speech") && e.has("line") && e.has("nav"));
        }
        assertEquals(List.of(2L, 3L), List.of(d.get("events").get(0).get("eventSeq").asLong(), d.get("events").get(1).get("eventSeq").asLong()));
        assertTrue(s.metrics().contains("gateway_pattern_alerts_cursor_resumes_total 1\n"));
    }

    @Test
    void theCursorIsStrict_aCursorEqualToAnEventSeqDoesNotReturnThatEventAndAHigherOneReturnsLess() throws Exception {
        FeedGatewayService s = service(false);
        PatternAlertRelay r = relay(s);
        ready(r);
        now.set(PatternAlertFixtures.barEnd(160) + 5_000L);
        feedSession(r);
        for (int cursor = 0; cursor <= 3; cursor++) {
            Wire w = new Wire("c" + cursor);
            s.addClient(w.socket);
            int mark = w.sent.size();
            s.handleFootprintBasicMessage(w.socket, resume("initial", "regular", PatternAlertFixtures.DATE, cursor));
            JsonNode d = reply(w, mark);
            assertEquals(3 - cursor, d.get("events").size(), "cursor " + cursor);
            assertFalse(d.get("reset").asBoolean(), "a cursor equal to the high-water is not a reset");
        }
    }

    @Test
    void onlyEventsUpToTheSocketsHelloHighWaterAreInTheReplyBecauseTheRestWasOrWillBeDeliveredLive() throws Exception {
        FeedGatewayService s = service(false);
        PatternAlertRelay r = relay(s);
        ready(r);
        feedSession(r);
        Wire w = new Wire("late");
        s.addClient(w.socket);
        r.onLive(PatternAlertRelay.REGULAR_EVENT, PatternAlertFixtures.numbered(PatternAlertFixtures.DATE, 4, "RAISED", 250));   // after the hello
        int mark = w.sent.size();
        s.handleFootprintBasicMessage(w.socket, resume("initial", "regular", PatternAlertFixtures.DATE, 0));
        JsonNode d = reply(w, mark);
        assertEquals(3, d.get("eventSeqHigh").asLong());
        for (JsonNode e : d.get("events")) {
            assertTrue(e.get("eventSeq").asLong() <= 3, "event 4 arrives live, never in the reply");
        }
    }

    @Test
    void aCursorAboveTheHelloHighWaterIsNotAnErrorButAResetReplyForTheWholeWindow() throws Exception {
        FeedGatewayService s = service(false);
        PatternAlertRelay r = relay(s);
        ready(r);
        now.set(PatternAlertFixtures.barEnd(160) + 5_000L);
        feedSession(r);
        Wire w = new Wire("reset");
        s.addClient(w.socket);
        int mark = w.sent.size();
        s.handleFootprintBasicMessage(w.socket, resume("initial", "regular", PatternAlertFixtures.DATE, 99));
        JsonNode d = reply(w, mark);
        assertEquals("display", d.get("type").asText());
        assertTrue(d.get("reset").asBoolean());
        assertEquals(3, d.get("events").size(), "answered as for cursor 0");
        assertEquals(3, d.get("eventSeqHigh").asLong(), "the page sets its cursor to this after processing");
    }

    @Test
    void aResumeThatIsNotValidIsAnsweredWithOneResumeErrorAndNothingElse() throws Exception {
        FeedGatewayService s = service(false);
        PatternAlertRelay r = relay(s);
        ready(r);
        feedSession(r);
        String d = PatternAlertFixtures.DATE;
        List<String> bad = List.of(
                resume("initial", "regular", "2026-11-02", 0),                    // another session date than the hello's
                resume("initial", "regular", "not-a-date", 0),
                resume("initial", "premarket", d, 0),                              // unknown profile
                resume("later", "regular", d, 0),                                  // unknown reason
                resume("initial", "regular", d, -1),                               // negative cursor
                resume("initial", "regular", d, "\"3\""),                          // not a number
                resume("initial", "regular", d, "1.5"),
                resume("initial", "regular", d, "12345678901234567890123"),
                "{\"type\":\"alerts-resume\"}",
                "{\"type\":\"alerts-resume\",\"reason\":\"initial\",\"profile\":\"regular\",\"sessionDate\":\"" + d + "\",\"cursor\":0,\"pad\":\"" + "x".repeat(600) + "\"}",
                "x".repeat(3_000) + "alerts-resume");
        for (String message : bad) {
            Wire w = new Wire("bad-" + Math.abs(message.hashCode()));
            s.addClient(w.socket);
            int mark = w.sent.size();
            s.handleFootprintBasicMessage(w.socket, message);
            JsonNode reply = reply(w, mark);
            assertEquals("resume-error", reply.get("type").asText(), message.substring(0, Math.min(80, message.length())));
            assertTrue(reply.has("error"));
        }
        assertTrue(s.metrics().contains("gateway_pattern_alerts_resume_errors_total " + bad.size() + "\n"), s.metrics());
        assertTrue(s.metrics().contains("gateway_pattern_alerts_cursor_resumes_total 0\n"));
    }

    @Test
    void aSocketThatIsNotRegisteredIsNotAnswered() throws Exception {
        FeedGatewayService s = service(false);
        ready(relay(s));
        Wire stranger = new Wire("stranger");
        s.handleFootprintBasicMessage(stranger.socket, resume("initial", "regular", PatternAlertFixtures.DATE, 0));
        assertTrue(stranger.sent.isEmpty());
    }

    @Test
    void theResumeAllowanceIsOneInitialPerHelloPlusEightOverflowRetriesAfterIt() throws Exception {
        FeedGatewayService s = service(false);
        PatternAlertRelay r = relay(s);
        ready(r);
        now.set(PatternAlertFixtures.barEnd(160) + 5_000L);
        feedSession(r);
        Wire w = new Wire("rate");
        s.addClient(w.socket);
        int mark = w.sent.size();
        s.handleFootprintBasicMessage(w.socket, resume("overflow", "regular", PatternAlertFixtures.DATE, 0));
        assertEquals("resume-error", reply(w, mark).get("type").asText(), "a retry without an initial resume");
        mark++;
        s.handleFootprintBasicMessage(w.socket, resume("initial", "regular", PatternAlertFixtures.DATE, 0));
        assertEquals("display", reply(w, mark).get("type").asText());
        mark++;
        s.handleFootprintBasicMessage(w.socket, resume("initial", "regular", PatternAlertFixtures.DATE, 0));
        assertEquals("resume-error", reply(w, mark).get("type").asText(), "a second initial resume for the same hello");
        mark++;
        for (int i = 1; i <= PatternAlertRelay.OVERFLOW_RETRIES_MAX; i++) {
            s.handleFootprintBasicMessage(w.socket, resume("overflow", "regular", PatternAlertFixtures.DATE, 0));
            assertEquals("display", reply(w, mark).get("type").asText(), "retry " + i);
            mark++;
        }
        s.handleFootprintBasicMessage(w.socket, resume("overflow", "regular", PatternAlertFixtures.DATE, 0));
        assertEquals("resume-error", reply(w, mark).get("type").asText(), "the ninth retry");
    }

    @Test
    void anOverflowRetryTakesAFreshSnapshotSoItCoversTheLiveFramesThePageDropped() throws Exception {
        FeedGatewayService s = service(false);
        PatternAlertRelay r = relay(s);
        ready(r);
        now.set(PatternAlertFixtures.barEnd(160) + 5_000L);
        feedSession(r);
        Wire w = new Wire("overflow");
        s.addClient(w.socket);
        int mark = w.sent.size();
        s.handleFootprintBasicMessage(w.socket, resume("initial", "regular", PatternAlertFixtures.DATE, 0));
        assertEquals(3, reply(w, mark).get("eventSeqHigh").asLong());
        mark++;
        r.onLive(PatternAlertRelay.REGULAR_EVENT, PatternAlertFixtures.numbered(PatternAlertFixtures.DATE, 4, "RAISED", 158));   // live frames the page then dropped
        r.onLive(PatternAlertRelay.REGULAR_EVENT, PatternAlertFixtures.numbered(PatternAlertFixtures.DATE, 5, "RAISED", 158));
        mark = w.sent.size();
        s.handleFootprintBasicMessage(w.socket, resume("overflow", "regular", PatternAlertFixtures.DATE, 3));
        JsonNode d = reply(w, mark);
        assertEquals(5, d.get("eventSeqHigh").asLong(), "the high-water was refreshed to the relay's");
        assertEquals(List.of(4L, 5L), List.of(d.get("events").get(0).get("eventSeq").asLong(), d.get("events").get(1).get("eventSeq").asLong()));
    }

    @Test
    void aProfileThatIsNotReadyGetsNoResumeAndThenOneSnapshotPerSocketAtPromotionWithItsOwnHighWater() throws Exception {
        FeedGatewayService s = service(false);
        PatternAlertRelay r = relay(s);
        Wire early = new Wire("early");
        s.addClient(early.socket);   // before the recovery was promoted
        JsonNode p = early.hello().get("alerts").get("profiles").get("regular");
        assertFalse(p.get("ready").asBoolean());
        assertEquals(0, p.get("events").size());
        int mark = early.sent.size();
        s.handleFootprintBasicMessage(early.socket, resume("initial", "regular", PatternAlertFixtures.DATE, 0));
        assertEquals("not-ready", reply(early, mark).get("error").asText(), "not ready: no resume");
        // the recovery reads history and the relay becomes ready; the promotion's per-socket step runs in finishTaRecovery (see PatternAlertLoopTest)
        now.set(PatternAlertFixtures.barEnd(160) + 5_000L);
        r.beginRecovery();
        r.onStaged(PatternAlertRelay.REGULAR_EVENT, PatternAlertFixtures.triangleBreak(1, "RAISED"));
        r.onStaged(PatternAlertRelay.REGULAR_EVENT, PatternAlertFixtures.wedgeBreak(2, "RAISED"));
        r.promote();
        Wire late = new Wire("late");
        s.addClient(late.socket);
        assertTrue(late.hello().get("alerts").get("profiles").get("regular").get("ready").asBoolean(), "a socket that connects after the promotion is ready at once");
        r.afterPromote((id, frame) -> {
            if ("early".equals(id)) {
                early.sent.add(readTree("{\"type\":\"" + frame[0] + "\",\"data\":" + frame[1] + "}"));
            } else {
                late.sent.add(readTree("{\"type\":\"" + frame[0] + "\",\"data\":" + frame[1] + "}"));
            }
        });
        assertEquals(2, early.alerts("snapshot").size(), "one snapshot per profile for the socket whose hello showed them not ready");
        assertEquals(0, late.alerts("snapshot").size(), "none for the socket that was ready");
        JsonNode snap = early.alerts("snapshot").get(0);
        assertEquals("regular", snap.get("profile").asText());
        assertTrue(snap.get("ready").asBoolean());
        assertEquals(2, snap.get("eventSeqHigh").asLong());
        assertEquals(2, snap.get("events").size());
        // the stored high-water matches the snapshot: a resume right after it works and is bounded by the snapshot
        mark = early.sent.size();
        s.handleFootprintBasicMessage(early.socket, resume("initial", "regular", PatternAlertFixtures.DATE, 0));
        JsonNode d = reply(early, mark);
        assertEquals("display", d.get("type").asText());
        assertEquals(2, d.get("eventSeqHigh").asLong());
        assertEquals(2, d.get("events").size());
        // and nothing is sent twice
        r.afterPromote((id, frame) -> {
            throw new AssertionError("a ready socket is not owed another snapshot");
        });
    }

    @Test
    void theSnapshotIsOwedWhenThePromotionRunsBetweenTheHelloCompositionAndTheBinding() {
        FeedGatewayService s = service(false);
        PatternAlertRelay r = relay(s);
        PatternAlertRelay.clearHelloSnapshot();
        r.helloField(Long.MAX_VALUE);   // composed while not ready
        r.beginRecovery();
        r.onStaged(PatternAlertRelay.REGULAR_EVENT, PatternAlertFixtures.triangleBreak(1, "RAISED"));
        r.promote();                    // the promotion lands before the socket is bound
        r.bindHello("racer");
        List<String[]> owed = r.takeOwed("racer");
        assertEquals(2, owed.size(), "a snapshot for each profile the hello showed as not ready");
        assertEquals("market-structure-alerts", owed.get(0)[0]);
        assertEquals("snapshot", readTree(owed.get(0)[1]).get("type").asText());
        assertTrue(r.takeOwed("racer").isEmpty(), "owed once");
    }

    @Test
    void theSocketsHelloSnapshotIsForgottenWhenItCloses() throws Exception {
        FeedGatewayService s = service(false);
        PatternAlertRelay r = relay(s);
        ready(r);
        Wire w = new Wire("closing");
        s.addClient(w.socket);
        assertEquals(1, r.socketsForTest());
        s.removeClient(w.socket);
        assertEquals(0, r.socketsForTest());
    }

    // ── the display reply's bounds ────────────────────────────────────────────────────────────────

    /** An event whose rendered record is within a few bytes of the 4 KiB record bound. */
    static GenericRecord big(long seq, String disposition) {
        GenericRecord e = PatternAlertFixtures.numbered(PatternAlertFixtures.DATE, seq, disposition, 100);
        int base = StudiesRelay.utf8Length(AvroFrameJson.render(e, Integer.MAX_VALUE, false));
        return PatternAlertFixtures.with(e, "tip", "t".repeat(PatternAlertRelay.RECORD_MAX_BYTES - base - 4));
    }

    @Test
    void theReplyOfTheWorstCaseStaysUnderItsBudgetAndSaysItIsTruncatedWhenMoreThanFortyEightQualify() throws Exception {
        FeedGatewayService s = service(false);
        PatternAlertRelay r = relay(s);
        ready(r);
        now.set(PatternAlertFixtures.barEnd(102) + 5_000L);
        for (int i = 1; i <= 256; i++) {
            assertNotNull(r.onLive(PatternAlertRelay.REGULAR_EVENT, big(i, "RAISED")), "an event of about 4 KiB is still inside the record bound: " + i);
        }
        assertEquals(PatternAlertRelay.FULL_RAISED_MAX, r.fullsForTest(0));
        Wire w = new Wire("worst");
        s.addClient(w.socket);
        int mark = w.sent.size();
        s.handleFootprintBasicMessage(w.socket, resume("initial", "regular", PatternAlertFixtures.DATE, 0));
        String envelope = JSON.writeValueAsString(w.sent.get(mark));
        JsonNode d = reply(w, mark);
        long budget = Math.min(PatternAlertRelay.REPLY_MAX_BYTES, new GatewaySettings().wsMaxQueuedBytes() / 2);
        assertTrue(StudiesRelay.utf8Length(envelope) <= budget, "reply " + StudiesRelay.utf8Length(envelope) + " bytes against " + budget);
        assertEquals(48, d.get("events").size());
        assertTrue(d.get("truncated").asBoolean(), "256 qualify, 48 can be displayed in full: the reply says so");
        assertEquals(209, d.get("eventSeqLow").asLong(), "the newest 48 are the ones kept");
        assertEquals(256, d.get("events").get(47).get("eventSeq").asLong());
    }

    @Test
    void theReplyIsExactlyCompleteWhenAtMostFortyEightQualifyAndAnEvictedOneIsReportedAsTruncated() throws Exception {
        FeedGatewayService s = service(false);
        PatternAlertRelay r = relay(s);
        ready(r);
        now.set(PatternAlertFixtures.barEnd(102) + 5_000L);
        for (int i = 1; i <= 48; i++) {
            r.onLive(PatternAlertRelay.REGULAR_EVENT, PatternAlertFixtures.numbered(PatternAlertFixtures.DATE, i, "RAISED", 100));
        }
        Wire w = new Wire("exact");
        s.addClient(w.socket);
        int mark = w.sent.size();
        s.handleFootprintBasicMessage(w.socket, resume("initial", "regular", PatternAlertFixtures.DATE, 0));
        JsonNode d = reply(w, mark);
        assertEquals(48, d.get("events").size());
        assertFalse(d.get("truncated").asBoolean());
        assertEquals(1, d.get("eventSeqLow").asLong());
    }

    @Test
    void aCursorBelowWhatTheRetentionEvictedIsReportedTruncatedWhileSuppressedGapsAreNot() throws Exception {
        GatewaySettings settings = spy(new GatewaySettings());
        doReturn(true).when(settings).patternAlertsEnabled();
        doReturn(8).when(settings).patternAlertsRetained();
        FeedGatewayService s = new FeedGatewayService(settings, JSON, new HpsfGatewayViewMapper(), null);
        s.runOutboundWritesInline();
        PatternAlertRelay r = relay(s);
        ready(r);
        now.set(PatternAlertFixtures.barEnd(102) + 5_000L);
        r.onLive(PatternAlertRelay.REGULAR_EVENT, PatternAlertFixtures.numbered(PatternAlertFixtures.DATE, 1, "SUPPRESSED_SELF_CHECK", 100));   // a gap that is not a loss
        for (int i = 2; i <= 13; i++) {
            r.onLive(PatternAlertRelay.REGULAR_EVENT, PatternAlertFixtures.numbered(PatternAlertFixtures.DATE, i, "RAISED", 100));
        }
        Wire w = new Wire("evicted");
        s.addClient(w.socket);
        int mark = w.sent.size();
        s.handleFootprintBasicMessage(w.socket, resume("initial", "regular", PatternAlertFixtures.DATE, 0));
        JsonNode d = reply(w, mark);
        assertEquals(8, d.get("events").size());
        assertEquals(6, d.get("eventSeqLow").asLong());
        assertTrue(d.get("truncated").asBoolean(), "events 2..5 were RAISED, inside the window and evicted");
        Wire ok = new Wire("evicted-ok");
        s.addClient(ok.socket);
        mark = ok.sent.size();
        s.handleFootprintBasicMessage(ok.socket, resume("initial", "regular", PatternAlertFixtures.DATE, 5));
        assertFalse(reply(ok, mark).get("truncated").asBoolean(), "a cursor at the eviction point misses nothing");
    }

    @Test
    void aReplyIsBuiltAgainstTheActualBudgetWhateverTheQueueSettingAndStaysWithinIt() {
        PatternAlertRelay r = new PatternAlertRelay(WINDOW_MS, 256, 40_000, now::get);   // the refusal floor is not applied to a relay built directly: the reply still never exceeds ITS budget
        r.beginRecovery();
        r.promote();
        now.set(PatternAlertFixtures.barEnd(102) + 5_000L);
        for (int i = 1; i <= 60; i++) {
            r.onLive(PatternAlertRelay.REGULAR_EVENT, PatternAlertFixtures.numbered(PatternAlertFixtures.DATE, i, "RAISED", 100));
        }
        PatternAlertRelay.clearHelloSnapshot();
        r.helloField(Long.MAX_VALUE);
        r.bindHello("tiny");
        String[] reply = r.resume("tiny", resume("initial", "regular", PatternAlertFixtures.DATE, 0));
        String envelope = "{\"type\":\"" + reply[0] + "\",\"data\":" + reply[1] + "}";
        assertTrue(StudiesRelay.utf8Length(envelope) <= 20_000, "budget is half the queue: " + StudiesRelay.utf8Length(envelope));
        JsonNode d = readTree(reply[1]);
        assertTrue(d.get("truncated").asBoolean());
        assertTrue(d.get("events").size() < 48 && d.get("events").size() > 0);
        assertEquals(60, d.get("events").get(d.get("events").size() - 1).get("eventSeq").asLong(), "the newest are kept");
    }

    static JsonNode readTree(String text) {
        try {
            return JSON.readTree(text);
        } catch (Exception e) {
            throw new IllegalStateException(text, e);
        }
    }

    @Test
    void theEstimateProfileResumesAgainstItsOwnEventsAndItsOwnEventName() throws Exception {
        FeedGatewayService s = service(false);
        PatternAlertRelay r = relay(s);
        ready(r);
        now.set(PatternAlertFixtures.barEnd(160) + 5_000L);
        r.onLive(PatternAlertRelay.REGULAR_EVENT, PatternAlertFixtures.triangleBreak(1, "RAISED"));
        r.onLive(PatternAlertRelay.ESTIMATE_EVENT, PatternAlertFixtures.numbered(PatternAlertFixtures.DATE, 1, "RAISED", 158));
        r.onLive(PatternAlertRelay.ESTIMATE_EVENT, PatternAlertFixtures.numbered(PatternAlertFixtures.DATE, 2, "RAISED", 158));
        Wire w = new Wire("both");
        s.addClient(w.socket);
        int mark = w.sent.size();
        s.handleFootprintBasicMessage(w.socket, resume("initial", "estimate", PatternAlertFixtures.DATE, 0));
        assertEquals("market-structure-alerts-estimate", w.sent.get(mark).get("type").asText());
        JsonNode d = reply(w, mark);
        assertEquals("estimate", d.get("profile").asText());
        assertEquals(2, d.get("events").size());
        mark++;
        s.handleFootprintBasicMessage(w.socket, resume("initial", "regular", PatternAlertFixtures.DATE, 0));
        assertEquals(1, reply(w, mark).get("events").size(), "each profile has its own initial allowance and its own events");
    }
}

package app.feedgateway;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.Node;
import org.apache.kafka.common.PartitionInfo;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

/**
 * ES-FOOTPRINT-GATEWAY-DESIGN.md G-R11 — the EXECUTED seams (round-1 #4): the admission predicate
 * inside {@code PartitionRefresh.apply()} with a rejecting-then-accepting gate on a mocked consumer,
 * the live consumer's END seek for footprint partitions, real socket fan-out of the four events,
 * hello atomicity under concurrent admission, Spring typed binding ahead of the handler, and the
 * start-up preflight leaving no lifecycle state behind.
 */
class FootprintSeamTest {

    private static final Set<String> TOPICS = Set.of("futures.cvd.bars", "futures.footprint.bars", "futures.footprint.outcomes");

    private static PartitionInfo pi(String topic) { return new PartitionInfo(topic, 0, new Node(1, "b", 9092), new Node[0], new Node[0]); }
    private static TopicPartition tp(String topic) { return new TopicPartition(topic, 0); }

    @SuppressWarnings("unchecked")
    private static KafkaConsumer<String, Object> consumerWith(List<String> present) {
        KafkaConsumer<String, Object> c = mock(KafkaConsumer.class);
        Map<String, List<PartitionInfo>> cluster = new HashMap<>();
        for (String t : present) cluster.put(t, List.of(pi(t)));
        when(c.listTopics(any(Duration.class))).thenReturn(cluster);
        return c;
    }

    // ---- G-R8a: the predicate seam inside apply() ----------------------------------------------------

    @Test @SuppressWarnings("unchecked")
    void applyWithholdsUnadmittedFootprintPartitionsAndAdmitsThemOnceTheGateAccepts() {
        FootprintTopicGateTest.FakeReader reader = new FootprintTopicGateTest.FakeReader();
        reader.valid("futures.footprint").valid("futures.footprint.evidence").valid("futures.footprint.bars").unknown("futures.footprint.outcomes");
        FeedGatewayService s = new FeedGatewayService(new GatewaySettings(), new ObjectMapper(), new HpsfGatewayViewMapper(), null, reader);
        s.setRunningForTest(true);
        KafkaConsumer<String, Object> consumer = consumerWith(List.of("futures.cvd.bars", "futures.footprint.bars", "futures.footprint.outcomes"));
        List<TopicPartition> assigned = List.of(tp("futures.cvd.bars"));
        Object refresh = s.partitionRefreshForTest("test-state", TOPICS, s.footprintGate()::admit);

        List<TopicPartition>[] r1 = s.applyRefreshForTest(refresh, consumer, assigned);
        assertEquals(List.of(tp("futures.footprint.bars")), r1[1], "the validated topic is added; the unvalidated one is WITHHELD from added()");
        assertEquals(List.of(tp("futures.cvd.bars"), tp("futures.footprint.bars")), r1[0], "…and from the merged assignment");
        assertEquals(List.of(tp("futures.footprint.bars")), r1[2], "the added partition is a NEW-topic adoption for the caller's seek");
        verify(consumer).assign(List.of(tp("futures.cvd.bars"), tp("futures.footprint.bars")));

        List<TopicPartition>[] r2 = s.applyRefreshForTest(refresh, consumer, r1[0]);
        assertTrue(r2[1].isEmpty(), "still withheld while the gate rejects: re-discovered, re-evaluated, not assigned");

        reader.valid("futures.footprint.outcomes");                                   // the producer created it
        List<TopicPartition>[] r3 = s.applyRefreshForTest(refresh, consumer, r2[0]);
        assertEquals(List.of(tp("futures.footprint.outcomes")), r3[1], "admitted on the next refresh once valid");
        assertEquals(3, r3[0].size());
        verify(consumer).assign(List.of(tp("futures.cvd.bars"), tp("futures.footprint.bars"), tp("futures.footprint.outcomes")));
        assertTrue(s.footprintGate().validated("futures.footprint.outcomes"));
    }

    @Test @SuppressWarnings("unchecked")
    void theBootstrapFilterAndTheRefreshSeamAgreeForBothConsumerFlows() {
        FootprintTopicGateTest.FakeReader reader = new FootprintTopicGateTest.FakeReader();
        reader.valid("futures.footprint").valid("futures.footprint.evidence").unknown("futures.footprint.bars").unknown("futures.footprint.outcomes");
        FeedGatewayService s = new FeedGatewayService(new GatewaySettings(), new ObjectMapper(), new HpsfGatewayViewMapper(), null, reader);
        s.setRunningForTest(true);
        List<TopicPartition> resolved = List.of(tp("futures.cvd.bars"), tp("futures.footprint.bars"), tp("futures.footprint.outcomes"));
        assertEquals(List.of(tp("futures.cvd.bars")), s.footprintAdmitted(resolved), "bootstrap: both unvalidated footprint partitions withheld");
        // The PRODUCTION bootstrap seam both state consumers call, executed once per flow.
        Map<String, FeedGatewayService.TopicBinding> events = new HashMap<>();
        events.put("futures.cvd.bars", new FeedGatewayService.TopicBinding("DATABENTO", "es-cvd-bar"));
        events.put("futures.footprint.bars", new FeedGatewayService.TopicBinding("DATABENTO", "es-footprint-bar"));
        events.put("futures.footprint.outcomes", new FeedGatewayService.TopicBinding("DATABENTO", "es-footprint-outcome"));
        for (String name : new String[]{"state", "state-live"}) {
            KafkaConsumer<String, Object> consumer = consumerWith(List.of("futures.cvd.bars", "futures.footprint.bars", "futures.footprint.outcomes"));
            List<TopicPartition> assigned = s.bootstrapAssign(name, consumer, events);
            assertEquals(List.of(tp("futures.cvd.bars")), assigned, name + ": unvalidated footprint partitions never reach the assignment");
            verify(consumer).assign(List.of(tp("futures.cvd.bars")));
            Object refresh = s.partitionRefreshForTest(name, TOPICS, s.footprintGate()::admit);
            assertTrue(s.applyRefreshForTest(refresh, consumer, assigned)[1].isEmpty(), name + ": nor the refresh's added()");
        }
        reader.valid("futures.footprint.bars").valid("futures.footprint.outcomes");
        assertEquals(resolved, s.footprintAdmitted(resolved));
        KafkaConsumer<String, Object> after = consumerWith(List.of("futures.cvd.bars", "futures.footprint.bars", "futures.footprint.outcomes"));
        assertEquals(3, s.bootstrapAssign("state", after, events).size(), "…and the production assign takes all three once valid");
    }

    // ---- round-1 #1: the live consumer never replays footprint history -------------------------------

    @Test @SuppressWarnings("unchecked")
    void theLiveConsumerSeeksFootprintPartitionsToEndAndOnlyThose() {
        FeedGatewayService s = FootprintWiringTest.on();
        KafkaConsumer<String, Object> consumer = mock(KafkaConsumer.class);
        List<TopicPartition> partitions = List.of(tp("futures.cvd.bars"), tp("futures.footprint.bars"), tp("futures.footprint"), tp("futures.footprint.outcomes"));
        s.seekFootprintToEnd(consumer, partitions);
        verify(consumer).seekToEnd(List.of(tp("futures.footprint.bars"), tp("futures.footprint"), tp("futures.footprint.outcomes")));
        FeedGatewayService off = new FeedGatewayService(new GatewaySettings(), new ObjectMapper(), new HpsfGatewayViewMapper(), null);
        KafkaConsumer<String, Object> untouched = mock(KafkaConsumer.class);
        off.seekFootprintToEnd(untouched, partitions);
        verify(untouched, org.mockito.Mockito.never()).seekToEnd(anyCollection());
    }

    @Test @SuppressWarnings("unchecked")
    void theLiveBootstrapSeekLeavesFootprintPartitionsAtEndOnEveryRetry() {
        // The PRODUCTION seam runLiveConsumerOnce calls, executed: a retry replays the cache window for
        // the ordinary topics and then forces the footprint partitions to END.
        FeedGatewayService s = FootprintWiringTest.on();
        KafkaConsumer<String, Object> consumer = mock(KafkaConsumer.class);
        when(consumer.offsetsForTimes(any(Map.class))).thenReturn(Collections.emptyMap());
        Map<String, FeedGatewayService.TopicBinding> events = new HashMap<>();
        events.put("futures.cvd.bars", new FeedGatewayService.TopicBinding("DATABENTO", "es-cvd-bar"));
        events.put("futures.footprint.bars", new FeedGatewayService.TopicBinding("DATABENTO", "es-footprint-bar"));
        events.put("futures.footprint", new FeedGatewayService.TopicBinding("DATABENTO", "es-footprint"));
        List<TopicPartition> partitions = List.of(tp("futures.cvd.bars"), tp("futures.footprint.bars"), tp("futures.footprint"));
        List<TopicPartition> footprint = List.of(tp("futures.footprint.bars"), tp("futures.footprint"));

        s.liveBootstrapSeek(consumer, partitions, events, true);
        verify(consumer).seekToEnd(footprint);                      // the LAST word on the footprint partitions is END
        org.mockito.InOrder order = org.mockito.Mockito.inOrder(consumer);
        order.verify(consumer).offsetsForTimes(any(Map.class));     // the cache-window seek ran first…
        order.verify(consumer).seekToEnd(footprint);                // …and the footprint override after it

        KafkaConsumer<String, Object> first = mock(KafkaConsumer.class);
        s.liveBootstrapSeek(first, partitions, events, false);
        verify(first).seekToEnd(partitions);                        // first attempt: everything at END anyway
        verify(first, org.mockito.Mockito.never()).offsetsForTimes(any(Map.class));
    }

    @Test @SuppressWarnings("unchecked")
    void aLateAdoptedFootprintPartitionStartsAtEndOnTheLiveConsumer() {
        FeedGatewayService s = FootprintWiringTest.on();
        KafkaConsumer<String, Object> consumer = mock(KafkaConsumer.class);
        Map<String, FeedGatewayService.TopicBinding> events = new HashMap<>();
        events.put("futures.footprint.outcomes", new FeedGatewayService.TopicBinding("DATABENTO", "es-footprint-outcome"));
        List<TopicPartition> added = List.of(tp("futures.footprint.outcomes"));
        s.liveAdoptionSeek(consumer, s.refreshForTest(added, added, List.of()), events);
        verify(consumer, org.mockito.Mockito.atLeastOnce()).seekToEnd(added);
        verify(consumer, org.mockito.Mockito.never()).seekToBeginning(anyCollection());
    }

    // ---- G-R3/D5: real socket fan-out through the live branch -----------------------------------------

    private static WebSocketSession recordingSession(String id, List<String> sink) {
        return (WebSocketSession) Proxy.newProxyInstance(WebSocketSession.class.getClassLoader(), new Class<?>[]{WebSocketSession.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "isOpen" -> Boolean.TRUE;
                    case "getId" -> id;
                    case "sendMessage" -> { if (args[0] instanceof TextMessage tm) sink.add(tm.getPayload()); yield null; }
                    case "toString" -> "RecordingSession-" + id;
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> { Class<?> rt = method.getReturnType(); yield rt == boolean.class ? Boolean.FALSE : rt == int.class ? 0 : rt == long.class ? 0L : null; }
                });
    }

    @Test void everyAuthenticatedSocketReceivesAllFourEventsAndAnOversizeRecordReachesNone() throws Exception {
        FeedGatewayService s = FootprintWiringTest.on();
        List<String> a = Collections.synchronizedList(new ArrayList<>()), b = Collections.synchronizedList(new ArrayList<>());
        s.addClient(recordingSession("a", a)); s.addClient(recordingSession("b", b));
        assertTrue(s.onFootprintLiveRecord("es-footprint", "{\"schemaVersion\":6,\"live\":true}"));
        assertTrue(s.onFootprintLiveRecord("es-footprint-evidence", "{\"schemaVersion\":6,\"ev\":true}"));
        assertTrue(s.onFootprintLiveRecord("es-footprint-bar", FootprintViewsTest.bar("2026-08-14", "1m", 1)));
        assertTrue(s.onFootprintLiveRecord("es-footprint-outcome", FootprintViewsTest.outcome("2026-08-14", "1m", 1, "x")));
        assertTrue(s.onFootprintLiveRecord("es-footprint-bar", FootprintViewsTest.bar("2026-08-13", "1m", 1)), "stale: still broadcast");
        assertFalse(s.onFootprintLiveRecord("es-footprint-bar", "{\"pad\":\"" + "y".repeat(300_000) + "\"}"), "oversize: dropped entirely");
        // Wait for the quantity this test ASSERTS — five footprint frames on each socket — not for a
        // total frame count that also includes whatever else the session was sent. A two-second
        // budget on a loaded machine expired with four of the five delivered, and the assertion
        // then reported a delivery defect that did not exist.
        // Collections.synchronizedList guards each METHOD, not an iteration: streaming it while the
        // delivery threads append is undefined. Count under the list's own monitor, which is the
        // lock those methods take.
        java.util.function.Function<List<String>, Long> footprintFrames = sink -> {
            synchronized (sink) {
                long n = 0;
                for (String m : sink) if (m.contains("es-footprint")) n++;
                return n;
            }
        };
        long deadline = System.currentTimeMillis() + 30_000;
        while ((footprintFrames.apply(a) < 5 || footprintFrames.apply(b) < 5)
                && System.currentTimeMillis() < deadline) Thread.sleep(5);
        for (List<String> sink : List.of(a, b)) {
            List<String> snapshot;
            synchronized (sink) { snapshot = List.copyOf(sink); }
            List<String> footprint = snapshot.stream().filter(m -> m.contains("es-footprint")).toList();
            assertEquals(5, footprint.size(), "four events + the stale bar, never the oversize one: " + footprint);
            assertTrue(footprint.stream().anyMatch(m -> m.contains("\"es-footprint\"") && m.contains("\"live\":true")));
            assertTrue(footprint.stream().anyMatch(m -> m.contains("es-footprint-evidence")));
            assertTrue(footprint.stream().anyMatch(m -> m.contains("es-footprint-outcome")));
            assertTrue(footprint.stream().noneMatch(m -> m.contains("yyyyyyyy")));
        }
        String m = s.footprintMetricsText();
        assertTrue(m.contains("gateway_footprint_broadcast_total{event=\"es-footprint-bar\"} 2\n"), m);
        assertTrue(m.contains("gateway_footprint_records_total{event=\"es-footprint-bar\",consumer=\"live\"} 3\n"));
        for (String e : new String[]{"es-footprint", "es-footprint-evidence", "es-footprint-bar", "es-footprint-outcome"}) {
            assertTrue(FeedGatewayService.isGlobalBroadcastEvent(e), e + " fans out in per-session (auth) mode too (D5)");
        }
        assertFalse(FeedGatewayService.isGlobalBroadcastEvent("es-footprint-private"), "an unknown event stays non-routable");
    }

    // ---- G-R6: hello atomicity under concurrent admission --------------------------------------------

    @Test void theHelloIsOneConsistentSnapshotWhileTwoThreadsRollTheSession() throws Exception {
        FeedGatewayService s = FootprintWiringTest.on();
        FootprintViews v = s.footprintViews();
        CountDownLatch go = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Runnable writer = () -> { try { go.await(); for (int i = 0; i < 2000; i++) { String d = i % 2 == 0 ? "2026-08-14" : "2026-08-15";
            v.admitBar(FootprintViewsTest.bar(d, "1m", i)); v.admitOutcome(FootprintViewsTest.outcome(d, "5m", i, "id" + i)); } } catch (Throwable t) { failure.set(t); } };
        Runnable reader = () -> { try { go.await(); ObjectMapper m = new ObjectMapper(); for (int i = 0; i < 4000; i++) {
            var node = m.readTree(v.helloField());
            String sd = node.get("sessionDate").isNull() ? null : node.get("sessionDate").asText();
            assertTrue(sd == null || sd.equals("2026-08-14") || sd.equals("2026-08-15"));
            node.get("hwm").fieldNames().forEachRemaining(tf -> assertTrue(FootprintViews.TIMEFRAMES.contains(tf)));
            if (sd == null) { assertTrue(node.get("hwm").isEmpty() && node.get("outcomeHwm").isEmpty(), "no date ⇒ no marks"); }
        } } catch (Throwable t) { failure.set(t); } };
        Thread w1 = new Thread(writer), w2 = new Thread(writer), r = new Thread(reader);
        w1.start(); w2.start(); r.start(); go.countDown(); w1.join(); w2.join(); r.join();
        assertNull(failure.get());
        assertEquals("2026-08-15", v.sessionDate(), "monotonic: the newer date wins whatever the interleaving");
    }

    // ---- G-R7: Spring typed binding precedes the handler --------------------------------------------

    @Test void malformedTypedParametersAre400BeforeAnyFootprintCodeRuns() throws Exception {
        FeedGatewayService s = FootprintWiringTest.on();
        GatewayController c = new GatewayController(s, null, h -> new app.feedgateway.liquidityhistory.LiquidityHistoryAuth.Result(200, "t"));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(c).build();
        int bars = mvc.perform(MockMvcRequestBuilders.get("/api/footprint/bars").param("tf", "1m").param("toMs", "abc")).andReturn().getResponse().getStatus();
        int outcomes = mvc.perform(MockMvcRequestBuilders.get("/api/footprint/outcomes").param("tf", "1m").param("toMs", "1").param("limit", "x")).andReturn().getResponse().getStatus();
        assertEquals(400, bars); assertEquals(400, outcomes);
        assertTrue(s.footprintMetricsText().contains("gateway_footprint_backfill_requests_total{route=\"bars\"} 0\n"), "never counted");
        assertTrue(s.footprintMetricsText().contains("gateway_footprint_backfill_requests_total{route=\"outcomes\"} 0\n"));
        FeedGatewayService off = new FeedGatewayService(new GatewaySettings(), new ObjectMapper(), new HpsfGatewayViewMapper(), null);
        MockMvc mvcOff = MockMvcBuilders.standaloneSetup(new GatewayController(off, null, h -> null)).build();
        assertEquals(400, mvcOff.perform(MockMvcRequestBuilders.get("/api/footprint/bars").param("tf", "1m").param("toMs", "abc")).andReturn().getResponse().getStatus(), "binding failure even with the flag off");
        assertEquals(404, mvcOff.perform(MockMvcRequestBuilders.get("/api/footprint/bars").param("tf", "1m").param("toMs", "1")).andReturn().getResponse().getStatus(), "well-formed + flag off ⇒ 404");
    }

    // ---- round-1 #3: preflight refusal leaves no lifecycle state --------------------------------------

    @Test void aPreflightRefusalLeavesRunningFalse() {
        FootprintTopicGateTest.FakeReader reader = new FootprintTopicGateTest.FakeReader();
        reader.valid("futures.footprint").valid("futures.footprint.evidence").valid("futures.footprint.outcomes").cfg("futures.footprint.bars", 1_048_589L, "producer");
        FeedGatewayService s = new FeedGatewayService(new GatewaySettings(), new ObjectMapper(), new HpsfGatewayViewMapper(), null, reader);
        assertThrows(IllegalStateException.class, s::start, "an existing invalid topic refuses start-up");
        assertFalse(s.runningForTest(), "no lifecycle state moved before the refusal");
        assertThrows(IllegalStateException.class, s::footprintPreflight);
        String src;
        try { src = java.nio.file.Files.readString(java.nio.file.Path.of("src/main/java/app/feedgateway/FeedGatewayService.java")); } catch (java.io.IOException e) { throw new IllegalStateException(e); }
        int start = src.indexOf("public void start() {");
        assertTrue(src.indexOf("footprintPreflight();", start) < src.indexOf("running.compareAndSet(false, true)", start), "preflight precedes the running CAS");
        assertTrue(src.indexOf("running.compareAndSet(false, true)", start) < src.indexOf("executor = Executors.newFixedThreadPool", start));
    }
}

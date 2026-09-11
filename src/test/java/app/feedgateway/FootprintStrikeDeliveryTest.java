package app.feedgateway;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import app.feedgateway.liquidityhistory.LiquidityHistoryAuth;
import app.feedgateway.mtsession.ConcurrencyLimits;
import app.feedgateway.mtsession.SessionRoutingEngine;
import app.feedgateway.mtsession.SubscriptionManager;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.IntConsumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.consumer.OffsetAndTimestamp;
import org.apache.kafka.common.Node;
import org.apache.kafka.common.PartitionInfo;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

/**
 * ES-FOOTPRINT-STRIKE gateway final review — the PRODUCTION paths: the strike fold's sequenced frames
 * reaching real socket channels in order across two consumer threads (#1), the replay lifecycle driven
 * through the production cache-consumer loop against a scripted broker (#2), fifth-event fan-out in
 * authenticated mode with rejection suppression, and a producer-shaped record carried byte-exactly
 * through live, latest and history (#4).
 */
class FootprintStrikeDeliveryTest {

    private static final ObjectMapper M = new ObjectMapper();
    private static final String BARS = "futures.footprint.bars", STRIKE = "futures.footprint.strike";

    private static WebSocketSession socket(String id, List<String> sink) throws Exception {
        WebSocketSession ws = mock(WebSocketSession.class);
        when(ws.getId()).thenReturn(id);
        when(ws.isOpen()).thenReturn(true);
        doAnswer(inv -> { sink.add(((TextMessage) inv.getArgument(0)).getPayload()); return null; }).when(ws).sendMessage(any());
        return ws;
    }

    private static List<String> sink() { return Collections.synchronizedList(new ArrayList<>()); }

    /** The strike frames a socket received, in order, parsed. */
    private static List<JsonNode> strikeFrames(List<String> sink) throws Exception {
        List<String> copy;
        synchronized (sink) { copy = List.copyOf(sink); }
        List<JsonNode> out = new ArrayList<>();
        for (String m : copy) {
            JsonNode n = M.readTree(m);
            if (n.path("type").asText().startsWith("es-footprint-strike")) out.add(n);
        }
        return out;
    }

    private static List<String> types(List<JsonNode> frames) { return frames.stream().map(n -> n.get("type").asText()).toList(); }

    private static void awaitQuietly(CountDownLatch l) {
        try { l.await(10, TimeUnit.SECONDS); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    private static GatewayController controller(FeedGatewayService s) {
        return new GatewayController(s, null, h -> new LiquidityHistoryAuth.Result(200, "tester"));
    }

    private static <T> T withProperty(String key, String value, java.util.function.Supplier<T> body) {
        String prior = System.getProperty(key);
        System.setProperty(key, value);
        try { return body.get(); } finally { if (prior == null) System.clearProperty(key); else System.setProperty(key, prior); }
    }

    // ---- final review #1: ordered delivery through the real socket channels ----------------------------

    /**
     * The reviewer's interleaving, on the production path: the live consumer admits A and is preempted
     * before delivering it; the cache consumer refuses A's identity; REST completes; the live consumer
     * resumes. Every socket must see A BEFORE the refusal's control, and nothing of A after it.
     */
    @Test void aRecordAdmittedBeforeARefusalReachesEverySocketBeforeTheRefusalsControl() throws Exception {
        FeedGatewayService s = FootprintWiringTest.on();
        s.runOutboundWritesInline();
        List<String> a = sink(), b = sink();
        s.addClient(socket("a", a)); s.addClient(socket("b", b));
        String recA = FootprintStrikeViewTest.e(100, 0, "A"), recB = FootprintStrikeViewTest.e(100, 0, "B");
        CountDownLatch paused = new CountDownLatch(1), resume = new CountDownLatch(1);
        AtomicBoolean forwarded = new AtomicBoolean();
        Thread live = new Thread(() -> forwarded.set(s.onFootprintLiveRecord("es-footprint-strike", recA)), "state-live");
        s.footprintStrikeView().afterDecisionForTest = () -> { if (Thread.currentThread() == live) { paused.countDown(); awaitQuietly(resume); } };
        live.start();
        assertTrue(paused.await(10, TimeUnit.SECONDS));
        assertTrue(strikeFrames(a).isEmpty(), "A is decided and queued, not yet delivered");
        assertFalse(s.admitFootprintRecord("es-footprint-strike", recB, "cache"), "the cache consumer refuses the identity");
        for (List<String> sink : List.of(a, b)) {
            assertEquals(List.of("es-footprint-strike", "es-footprint-strike-control"), types(strikeFrames(sink)),
                    "the refusal's drain delivered the queue in order: A, then the control");
        }
        // REST completes NOW — between the control and the moment the live thread resumes
        MockHttpServletResponse rest = new MockHttpServletResponse();
        controller(s).footprintStrikeLatest("1m", "2026-09-10", "", -1, 200, "Bearer x", rest);
        JsonNode page = M.readTree(rest.getContentAsByteArray());
        assertEquals(0, page.get("episodes").size(), "REST has withdrawn the identity");
        assertEquals(1, page.get("authority").asLong());
        assertEquals(s.footprintStrikeView().incarnation(), page.get("incarnation").asText());
        assertEquals(680_000, page.get("tombstones").get(0).get("strikeCents").asLong());
        assertEquals(100, page.get("tombstones").get(0).get("openBarStartMs").asLong());
        resume.countDown();
        live.join(10_000);
        assertTrue(forwarded.get(), "the live consumer's A WAS admitted, before the refusal");
        for (List<String> sink : List.of(a, b)) {
            List<JsonNode> frames = strikeFrames(sink);
            assertEquals(List.of("es-footprint-strike", "es-footprint-strike-control"), types(frames),
                    "no evidence of A arrives after the control that withdrew it");
            assertEquals(recA, frames.get(0).get("data").asText(), "the evidence is A, byte for byte");
            assertEquals(1, frames.get(1).get("data").get("authority").asLong());
            assertEquals(1, frames.get(1).get("data").get("refused").asLong());
        }
        String m = s.footprintMetricsText();
        assertTrue(m.contains("gateway_footprint_broadcast_total{event=\"es-footprint-strike\"} 1\n"), m);
        assertTrue(m.contains("gateway_footprint_broadcast_total{event=\"es-footprint-strike-control\"} 1\n"), m);
        assertTrue(m.contains("gateway_footprint_drops_total{event=\"es-footprint-strike\",consumer=\"cache\",reason=\"collision\"} 1\n"), m);
    }

    @Test void aRecordAdmittedBeforeAnEvictionReachesEverySocketBeforeTheEvictionsControl() throws Exception {
        FeedGatewayService s = withProperty("GATEWAY_ES_FOOTPRINT_STRIKE_MAX_EPISODES", "1", FootprintWiringTest::on);
        s.runOutboundWritesInline();
        List<String> a = sink();
        s.addClient(socket("a", a));
        String recA = FootprintStrikeViewTest.e(100, 0, "A");
        CountDownLatch paused = new CountDownLatch(1), resume = new CountDownLatch(1);
        Thread live = new Thread(() -> s.onFootprintLiveRecord("es-footprint-strike", recA), "state-live");
        s.footprintStrikeView().afterDecisionForTest = () -> { if (Thread.currentThread() == live) { paused.countDown(); awaitQuietly(resume); } };
        live.start();
        assertTrue(paused.await(10, TimeUnit.SECONDS));
        assertTrue(s.admitFootprintRecord("es-footprint-strike", FootprintStrikeViewTest.e(200, 0, "B"), "cache"), "a newer opening, admitted, evicts A");
        MockHttpServletResponse rest = new MockHttpServletResponse();
        controller(s).footprintStrikeHistory("1m", 680_000, "", "", 100, "Bearer x", rest);
        JsonNode page = M.readTree(rest.getContentAsByteArray());
        assertEquals(1, page.get("episodes").size());
        assertTrue(page.get("episodes").get(0).asText().contains("\"tag\":\"B\""), "REST holds B only");
        assertEquals(200, page.get("historyBeginsAtMs").asLong());
        resume.countDown();
        live.join(10_000);
        List<JsonNode> frames = strikeFrames(a);
        assertEquals(List.of("es-footprint-strike", "es-footprint-strike-control"), types(frames));
        assertEquals(recA, frames.get(0).get("data").asText());
        assertEquals(200, frames.get(1).get("data").get("historyBeginsAtMs").asLong(), "the control names the new boundary");
    }

    /** addClient's hello is captured and queued in sequence with the controls, so no socket can hold one older than a control it already has. */
    @Test void aSocketNeverReceivesAHelloOlderThanAControlItAlreadyHolds() throws Exception {
        FeedGatewayService s = FootprintWiringTest.on();
        s.runOutboundWritesInline();
        CountDownLatch go = new CountDownLatch(1);
        Thread mutator = new Thread(() -> {
            awaitQuietly(go);
            for (int i = 0; i < 300; i++) {
                s.admitFootprintRecord("es-footprint-strike", FootprintStrikeViewTest.e(1_000 + i, 0, "a"), "cache");
                s.admitFootprintRecord("es-footprint-strike", FootprintStrikeViewTest.e(1_000 + i, 0, "b"), "cache");
            }
        });
        mutator.start();
        List<List<String>> sinks = new ArrayList<>();
        go.countDown();
        for (int i = 0; i < 60; i++) { List<String> sk = sink(); sinks.add(sk); s.addClient(socket("s" + i, sk)); }
        mutator.join();
        int controlsSeenBeforeAHello = 0;
        for (List<String> sk : sinks) {
            List<String> copy;
            synchronized (sk) { copy = List.copyOf(sk); }
            long maxBefore = -1, hello = -1, last = -1;
            for (String msg : copy) {
                JsonNode n = M.readTree(msg);
                String type = n.path("type").asText();
                if ("cvd-hello".equals(type)) {
                    hello = n.get("data").get("footprintStrike").get("authority").asLong();
                    assertTrue(hello >= maxBefore, "hello at authority " + hello + " after a control at " + maxBefore);
                    assertEquals(s.footprintStrikeView().incarnation(), n.get("data").get("footprintStrike").get("incarnation").asText());
                    last = hello;
                } else if ("es-footprint-strike-control".equals(type)) {
                    long c = n.get("data").get("authority").asLong();
                    if (hello < 0) { maxBefore = Math.max(maxBefore, c); controlsSeenBeforeAHello++; }
                    assertTrue(c >= last, "a control at " + c + " after authority " + last);
                    last = c;
                }
            }
            assertTrue(hello >= 0, "every socket got its hello");
        }
        assertEquals(300, s.footprintStrikeView().authority());
        System.out.println("hello-ordering: " + controlsSeenBeforeAHello + " controls reached a socket before its hello");
    }

    // ---- final review #4: the fifth event fans out in authenticated mode; a rejected record reaches nobody --

    @Test void inAuthModeTheFifthEventAndItsControlReachEverySocket_andARejectedRecordReachesNone() throws Exception {
        FootprintTopicGateTest.FakeReader r = new FootprintTopicGateTest.FakeReader();
        for (String t : List.of("futures.footprint", "futures.footprint.evidence", BARS, "futures.footprint.outcomes", STRIKE)) r.valid(t);
        FeedGatewayService s = new FeedGatewayService(new GatewaySettings(), new ObjectMapper(), new HpsfGatewayViewMapper(),
                new SessionRoutingEngine(new ConcurrencyLimits(5, 5, 100), new SubscriptionManager()), r);
        s.runOutboundWritesInline();
        List<String> a = sink(), b = sink();
        s.addClient(socket("a", a)); s.addClient(socket("b", b));
        String recA = FootprintStrikeViewTest.e(100, 0, "A"), checkpoint = FootprintStrikeViewTest.checkpoint("2026-09-10", "1m", 500);
        assertTrue(s.onFootprintLiveRecord("es-footprint", "{\"schemaVersion\":6,\"live\":true}"));
        assertTrue(s.onFootprintLiveRecord("es-footprint-bar", FootprintViewsTest.bar("2026-08-14", "1m", 1)));
        assertTrue(s.onFootprintLiveRecord("es-footprint-strike", recA));
        assertFalse(s.onFootprintLiveRecord("es-footprint-strike", FootprintStrikeViewTest.e(100, 0, "B")), "a collision is not forwarded");
        assertFalse(s.onFootprintLiveRecord("es-footprint-strike", FootprintStrikeViewTest.e(100, 1, "C")), "nor a later revision of the refused identity");
        assertFalse(s.onFootprintLiveRecord("es-footprint-strike", "{\"kind\":\"OPEN\"}"), "nor a shape drop");
        assertTrue(s.onFootprintLiveRecord("es-footprint-strike", checkpoint), "an admitted CHECKPOINT is evidence too");
        for (List<String> sk : List.of(a, b)) {
            List<JsonNode> frames = strikeFrames(sk);
            assertEquals(List.of("es-footprint-strike", "es-footprint-strike-control", "es-footprint-strike"), types(frames), "auth mode fans the fifth event out: " + frames);
            assertEquals(recA, frames.get(0).get("data").asText());
            assertEquals(checkpoint, frames.get(2).get("data").asText());
            String all;
            synchronized (sk) { all = String.join("\n", sk); }
            assertFalse(all.contains("\\\"tag\\\":\\\"B\\\"") || all.contains("\\\"tag\\\":\\\"C\\\""), "a rejected record reaches no socket");
            assertTrue(all.contains("{\"type\":\"es-footprint\",") && all.contains("{\"type\":\"es-footprint-bar\","), "…and the other footprint events fan out beside it");
        }
        String m = s.footprintMetricsText();
        assertTrue(m.contains("gateway_footprint_broadcast_total{event=\"es-footprint-strike\"} 2\n"), m);
        assertTrue(m.contains("gateway_footprint_broadcast_total{event=\"es-footprint-strike-control\"} 1\n"), m);
        for (String reason : List.of("collision", "refused", "shape"))
            assertTrue(m.contains("gateway_footprint_drops_total{event=\"es-footprint-strike\",consumer=\"live\",reason=\"" + reason + "\"} 1\n"), reason + "\n" + m);
    }

    // ---- final review #4: a producer-shaped record, byte for byte, through live, latest and history --------

    /**
     * The producer's own key order (StrikeRecords.EPISODE_KEYS, compact Jackson output), with JSON nulls, a
     * non-Latin-1 character and a surrogate pair, escaped control characters, and fields this gateway has
     * never heard of — nested and at the top level.
     */
    static String producerRecord() {
        return "{\"kind\":\"UPDATE\",\"symbol\":\"ES.v.0\",\"sessionDate\":\"2026-09-10\",\"timeframe\":\"1m\",\"strikeCents\":680000,"
                + "\"openBarStartMs\":1757511000000,\"revision\":3,\"final\":false,\"closeReason\":null,\"approach\":\"FROM_BELOW\","
                + "\"suspended\":false,\"unreadableBars\":0,\"barsObserved\":4,\"seenMaxBarStartMs\":1757511180000,"
                + "\"prevAdmittedCloseCents\":null,\"prevAdmittedBarStartMs\":null,\"firstBarStartMs\":1757511000000,\"lastBarStartMs\":1757511180000,"
                + "\"seriesTruncated\":false,\"seriesOmittedCount\":0,\"bandCents\":250,"
                + "\"series\":[{\"barStartMs\":1757511000000,\"note\":\"6800 € \\u0001 tab\\t quote\\\" back\\\\ 😀\",\"delta\":-12,\"x\":null},"
                + "{\"barStartMs\":1757511060000,\"unknownNested\":{\"a\":[1,null,true,1.5e3]}}],"
                + "\"futureField\":{\"v\":2}}";
    }

    @Test void aProducerShapedRecordIsCarriedByteExactlyThroughLiveLatestAndHistory() throws Exception {
        FeedGatewayService s = FootprintWiringTest.on();
        s.runOutboundWritesInline();
        List<String> a = sink();
        s.addClient(socket("a", a));
        String rec = producerRecord();
        assertNotNull(M.readTree(rec), "the fixture is valid JSON");
        assertTrue(s.onFootprintLiveRecord("es-footprint-strike", rec));
        String quoted = FootprintStrikeView.quoted(rec);
        List<String> live;
        synchronized (a) { live = a.stream().filter(x -> x.startsWith("{\"type\":\"es-footprint-strike\",")).toList(); }
        assertEquals(List.of("{\"type\":\"es-footprint-strike\",\"data\":" + quoted + "}"), live, "live: the record as one JSON string literal");
        assertEquals(rec, M.readTree(live.get(0)).get("data").asText());
        byte[] wanted = quoted.getBytes(StandardCharsets.UTF_8);

        MockHttpServletResponse latest = new MockHttpServletResponse();
        controller(s).footprintStrikeLatest("1m", "2026-09-10", "", -1, 200, "Bearer x", latest);
        byte[] latestBytes = latest.getContentAsByteArray();
        assertTrue(indexOf(latestBytes, wanted) > 0, "latest: the same quoted bytes, written from the retained UTF-8");
        assertEquals(rec, M.readTree(latestBytes).get("episodes").get(0).asText());

        MockHttpServletResponse history = new MockHttpServletResponse();
        controller(s).footprintStrikeHistory("1m", 680_000, "", "", 100, "Bearer x", history);
        byte[] historyBytes = history.getContentAsByteArray();
        assertTrue(indexOf(historyBytes, wanted) > 0, "history: the same quoted bytes");
        assertEquals(rec, M.readTree(historyBytes).get("episodes").get(0).asText());
        assertEquals(rec.getBytes(StandardCharsets.UTF_8).length, s.footprintStrikeView().bytesInView());
    }

    private static int indexOf(byte[] hay, byte[] needle) {
        outer:
        for (int i = 0; i + needle.length <= hay.length; i++) {
            for (int j = 0; j < needle.length; j++) if (hay[i + j] != needle[j]) continue outer;
            return i;
        }
        return -1;
    }

    // ---- final review #2: the replay lifecycle through the PRODUCTION cache-consumer loop ------------------

    /** One partition per topic behind a mocked KafkaConsumer: what runAssignedCacheConsumerOnce talks to. */
    static final class FakeBroker {
        final Map<String, List<String>> logs = new ConcurrentHashMap<>();
        final Set<String> present = ConcurrentHashMap.newKeySet();
        /** Topics whose records poll never hands over: their partitions never catch up. */
        final Set<String> withheld = ConcurrentHashMap.newKeySet();
        final Map<TopicPartition, Long> positions = new ConcurrentHashMap<>();
        /** Every timestamp map the consumer was asked to seek to, in order. */
        final List<Map<TopicPartition, Long>> cutoffsSought = new CopyOnWriteArrayList<>();
        volatile List<TopicPartition> assigned = List.of();
        final AtomicInteger polls = new AtomicInteger();
        volatile IntConsumer onPoll = n -> {};

        FakeBroker topic(String t, String... records) { logs.put(t, new CopyOnWriteArrayList<>(List.of(records))); present.add(t); return this; }
        long end(TopicPartition p) { List<String> l = logs.get(p.topic()); return l == null ? 0 : l.size(); }
        Long cutoffFor(String topic) {
            Long found = null;
            for (Map<TopicPartition, Long> m : cutoffsSought) { Long c = m.get(new TopicPartition(topic, 0)); if (c != null) found = c; }
            return found;
        }

        @SuppressWarnings("unchecked")
        KafkaConsumer<String, Object> consumer() {
            KafkaConsumer<String, Object> c = mock(KafkaConsumer.class);
            when(c.listTopics(any(Duration.class))).thenAnswer(inv -> {
                Map<String, List<PartitionInfo>> m = new HashMap<>();
                for (String t : present) m.put(t, List.of(new PartitionInfo(t, 0, new Node(1, "b", 9092), new Node[0], new Node[0])));
                return m;
            });
            doAnswer(inv -> { assigned = List.copyOf((Collection<TopicPartition>) inv.getArgument(0)); return null; }).when(c).assign(anyCollection());
            when(c.offsetsForTimes(anyMap())).thenAnswer(inv -> {
                Map<TopicPartition, Long> ts = (Map<TopicPartition, Long>) inv.getArgument(0);
                cutoffsSought.add(Map.copyOf(ts));
                Thread.sleep(3);                                             // a broker round trip: a cutoff recomputed after it would differ
                Map<TopicPartition, OffsetAndTimestamp> out = new HashMap<>();
                for (Map.Entry<TopicPartition, Long> e : ts.entrySet()) if (end(e.getKey()) > 0) out.put(e.getKey(), new OffsetAndTimestamp(0L, e.getValue()));
                return out;
            });
            doAnswer(inv -> { positions.put(inv.getArgument(0), inv.getArgument(1)); return null; }).when(c).seek(any(TopicPartition.class), anyLong());
            doAnswer(inv -> { for (TopicPartition p : (Collection<TopicPartition>) inv.getArgument(0)) positions.put(p, end(p)); return null; }).when(c).seekToEnd(anyCollection());
            when(c.endOffsets(anyCollection(), any(Duration.class))).thenAnswer(inv -> {
                Thread.sleep(3);
                Map<TopicPartition, Long> out = new HashMap<>();
                for (TopicPartition p : (Collection<TopicPartition>) inv.getArgument(0)) out.put(p, end(p));
                return out;
            });
            when(c.position(any(TopicPartition.class))).thenAnswer(inv -> {
                TopicPartition p = inv.getArgument(0);
                if (!assigned.contains(p)) throw new IllegalStateException("not assigned: " + p);
                return positions.getOrDefault(p, 0L);
            });
            when(c.poll(any(Duration.class))).thenAnswer(inv -> {
                onPoll.accept(polls.incrementAndGet());
                Map<TopicPartition, List<ConsumerRecord<String, Object>>> batch = new HashMap<>();
                for (TopicPartition p : assigned) {
                    if (withheld.contains(p.topic())) continue;
                    long at = positions.getOrDefault(p, 0L);
                    List<String> log = logs.getOrDefault(p.topic(), List.of());
                    if (at < log.size()) {                                   // ONE record per poll: completion can be watched step by step
                        batch.put(p, List.of(new ConsumerRecord<>(p.topic(), 0, at, null, log.get((int) at))));
                        positions.put(p, at + 1);
                    }
                }
                return new ConsumerRecords<>(batch, Map.of());
            });
            return c;
        }
    }

    private static Map<String, FeedGatewayService.TopicBinding> events() {
        Map<String, FeedGatewayService.TopicBinding> m = new HashMap<>();
        m.put(BARS, new FeedGatewayService.TopicBinding("DATABENTO", "es-footprint-bar"));
        m.put(STRIKE, new FeedGatewayService.TopicBinding("DATABENTO", "es-footprint-strike"));
        return m;
    }

    private static List<JsonNode> controlFrames(List<String> sink) throws Exception {
        return strikeFrames(sink).stream().filter(n -> "es-footprint-strike-control".equals(n.get("type").asText())).map(n -> n.get("data")).toList();
    }

    /**
     * The strike topic is ABSENT at bootstrap and appears later. Through the production loop: the adoption
     * seeks it, the replay opens with the cutoff that seek ACTUALLY used, the fold stays LOADING until the
     * adopted partition's records are applied up to the end offset captured at adoption — one record per
     * poll, watched — and completion is declared although the shared readiness never arrives (the bars
     * partition never catches up).
     */
    @Test void anAbsentStrikeTopicAdoptedLaterJoinsTheReplayWithItsSeeksCutoff_andCompletesWithoutSharedReadiness() throws Exception {
        FeedGatewayService s = FootprintWiringTest.on();
        s.runOutboundWritesInline();
        List<String> a = sink();
        s.addClient(socket("a", a));
        FootprintStrikeView v = s.footprintStrikeView();
        // what the fold holds at the very moment completion reaches a socket: all three records, or completion was early
        List<Integer> foldedAtCompletion = new CopyOnWriteArrayList<>();
        WebSocketSession watcher = mock(WebSocketSession.class);
        when(watcher.getId()).thenReturn("watcher");
        when(watcher.isOpen()).thenReturn(true);
        doAnswer(inv -> {
            String p = ((TextMessage) inv.getArgument(0)).getPayload();
            if (p.startsWith("{\"type\":\"es-footprint-strike-control\"") && p.contains("\"loading\":false"))
                foldedAtCompletion.add(v.history("ES.v.0", "1m", 680_000, "", 100).records().size());
            return null;
        }).when(watcher).sendMessage(any());
        s.addClient(watcher);
        FakeBroker broker = new FakeBroker().topic(BARS, "{}", "{}");
        broker.withheld.add(BARS);
        List<Boolean> loadingAtPoll = new CopyOnWriteArrayList<>();
        List<Long> beginsAtPoll = new CopyOnWriteArrayList<>();
        broker.onPoll = n -> {
            loadingAtPoll.add(v.loading());
            beginsAtPoll.add(v.replayBeginsAtMs());
            if (n == 2) {                                                          // the producer creates the topic
                broker.topic(STRIKE, FootprintStrikeViewTest.e(100, 0, "r0"), FootprintStrikeViewTest.e(200, 0, "r1"), FootprintStrikeViewTest.e(300, 0, "r2"));
                s.expirePartitionRefreshForTest("state");
            }
            if (n == 8) s.setRunningForTest(false);
        };
        AtomicBoolean sharedReadiness = new AtomicBoolean();
        long before = System.currentTimeMillis();
        s.setRunningForTest(true);
        s.runCacheConsumerAttemptForTest("state", events(), sharedReadiness, broker.consumer());
        long after = System.currentTimeMillis();

        Long cutoff = broker.cutoffFor(STRIKE);
        assertNotNull(cutoff, "the adopted strike partition was sought by timestamp");
        long seekBack = new GatewaySettings().esFootprintStrikeSeekBackMs();
        assertTrue(cutoff >= before - seekBack && cutoff <= after - seekBack, "a week back from the adoption: " + cutoff);
        assertEquals(cutoff, v.replayBeginsAtMs(), "replayBeginsAtMs IS the cutoff the seek used — captured once, not recomputed");
        assertEquals(List.of(true, true, true, true, true, false, false, false), loadingAtPoll,
                "LOADING through the three adopted records, complete on the poll after the third was APPLIED");
        assertNull(beginsAtPoll.get(1), "no strike partition at bootstrap: nothing was sought, nothing claimed");
        assertEquals(cutoff, beginsAtPoll.get(2), "open from the adoption on");
        assertFalse(v.loading());
        assertFalse(sharedReadiness.get(), "the shared readiness never came — the strike authority does not wait for it");
        assertEquals(3, v.history("ES.v.0", "1m", 680_000, "", 100).records().size(), "every adopted record was folded");
        assertEquals(List.of(3), foldedAtCompletion, "completion was announced only AFTER the last adopted record was applied");
        List<JsonNode> controls = controlFrames(a);
        assertEquals(1, controls.size(), "one authority change: the completion (it was loading all along)");
        assertFalse(controls.get(0).get("loading").asBoolean());
        assertEquals(cutoff.longValue(), controls.get(0).get("replayBeginsAtMs").asLong());
    }

    /**
     * A retry is a new attempt: its bootstrap seek reopens the completed replay with ITS cutoff (an
     * authority change readers are told about) and completes again only when its own end offsets are
     * crossed. An empty strike partition completes at bootstrap.
     */
    @Test void aRetryReopensTheReplayWithItsOwnCutoffAndCompletesAgain() throws Exception {
        FeedGatewayService s = FootprintWiringTest.on();
        s.runOutboundWritesInline();
        List<String> a = sink();
        s.addClient(socket("a", a));
        FootprintStrikeView v = s.footprintStrikeView();

        FakeBroker first = new FakeBroker().topic(STRIKE, FootprintStrikeViewTest.e(100, 0, "r0"), FootprintStrikeViewTest.e(200, 0, "r1"));
        first.onPoll = n -> { if (n == 4) s.setRunningForTest(false); };
        s.setRunningForTest(true);
        s.runCacheConsumerAttemptForTest("state", events(), new AtomicBoolean(), first.consumer());
        long c1 = first.cutoffFor(STRIKE);
        assertFalse(v.loading());
        assertEquals(c1, v.replayBeginsAtMs());
        Thread.sleep(5);

        FakeBroker retry = new FakeBroker().topic(STRIKE, FootprintStrikeViewTest.e(100, 0, "r0"), FootprintStrikeViewTest.e(200, 0, "r1"), FootprintStrikeViewTest.e(300, 0, "r2"));
        List<Boolean> loadingAtPoll = new CopyOnWriteArrayList<>();
        retry.onPoll = n -> { loadingAtPoll.add(v.loading()); if (n == 5) s.setRunningForTest(false); };
        s.setRunningForTest(true);
        s.runCacheConsumerAttemptForTest("state", events(), new AtomicBoolean(), retry.consumer());
        long c2 = retry.cutoffFor(STRIKE);
        assertTrue(c2 > c1, "the retry sought from its own clock");
        assertEquals(c2, v.replayBeginsAtMs());
        assertEquals(List.of(true, true, true, false, false), loadingAtPoll, "reopened at the retry's bootstrap, complete once its three records are applied");
        List<JsonNode> controls = controlFrames(a);
        assertEquals(3, controls.size(), "complete, reopened, complete again: " + controls);
        assertFalse(controls.get(0).get("loading").asBoolean());
        assertEquals(c1, controls.get(0).get("replayBeginsAtMs").asLong());
        assertTrue(controls.get(1).get("loading").asBoolean(), "readers are told the replay reopened");
        assertEquals(c2, controls.get(1).get("replayBeginsAtMs").asLong());
        assertFalse(controls.get(2).get("loading").asBoolean());
        assertEquals(List.of(1L, 2L, 3L), controls.stream().map(n -> n.get("authority").asLong()).toList());

        FakeBroker empty = new FakeBroker().topic(STRIKE);
        List<Boolean> emptyLoading = new CopyOnWriteArrayList<>();
        empty.onPoll = n -> { emptyLoading.add(v.loading()); if (n == 1) s.setRunningForTest(false); };
        s.setRunningForTest(true);
        s.runCacheConsumerAttemptForTest("state", events(), new AtomicBoolean(), empty.consumer());
        assertEquals(List.of(false), emptyLoading, "an empty strike partition is complete at bootstrap, before any poll");
    }

    // ---- strike re-review (round 2) -----------------------------------------------------------------------

    /** Waits until the socket's strike frames are exactly {@code want} (by type), or fails with what it has. */
    private static List<JsonNode> awaitStrikeTypes(List<String> sink, List<String> want) throws Exception {
        long deadline = System.currentTimeMillis() + 10_000;
        List<JsonNode> frames = strikeFrames(sink);
        while (!types(frames).equals(want) && System.currentTimeMillis() < deadline) { Thread.sleep(10); frames = strikeFrames(sink); }
        assertEquals(want, types(frames));
        return frames;
    }

    /**
     * The reviewer's reproduction of #1, on the production path with REAL asynchronous writers: the strike
     * fold's drainer fans each frame out while it holds the stream's delivery order; one socket stops
     * reading, overflows on that drainer, and closing it BLOCKS — behind its own outstanding write. Before:
     * the live consumer waited inside that close, the cache consumer waited for the drainer, its refusal
     * control stayed queued and every healthy socket waited with it (the watchdog skipped the channel: it
     * was already marked closed). Now the channel is marked closed at once and its session is closed on a
     * shared closer thread, never on the drainer.
     */
    @Test void aSlowSocketWhoseCloseBlocksStallsNeitherConsumerNorAnyHealthySocket() throws Exception {
        FeedGatewayService s = FootprintWiringTest.on();                        // no runOutboundWritesInline: the production writer pool
        List<String> a = sink(), b = sink(), slowGot = sink();
        s.addClient(socket("a", a)); s.addClient(socket("b", b));
        CountDownLatch sendRelease = new CountDownLatch(1), closeEntered = new CountDownLatch(1), closeRelease = new CountDownLatch(1), closeReturned = new CountDownLatch(1);
        AtomicReference<String> closedOn = new AtomicReference<>();
        WebSocketSession slow = mock(WebSocketSession.class);
        when(slow.getId()).thenReturn("slow");
        when(slow.isOpen()).thenReturn(true);
        doAnswer(inv -> {
            String p = ((TextMessage) inv.getArgument(0)).getPayload();
            slowGot.add(p);
            if (p.startsWith("{\"type\":\"es-footprint-strike")) sendRelease.await(30, TimeUnit.SECONDS);   // its reader stops reading
            return null;
        }).when(slow).sendMessage(any());
        doAnswer(inv -> {                                                            // …and closing it waits behind that write
            closedOn.set(Thread.currentThread().getName());
            closeEntered.countDown();
            closeRelease.await(30, TimeUnit.SECONDS);
            closeReturned.countDown();
            return null;
        }).when(slow).close();
        withProperty("GATEWAY_WS_MAX_QUEUED_MESSAGES", "50", () -> { s.addClient(slow); return null; });
        int n = 80;
        List<String> records = new ArrayList<>();
        for (int i = 0; i < n; i++) records.add(FootprintStrikeViewTest.episode("OPEN", "2026-09-10", "1m", 680_000 + i * 500L, 100, 0, 100, "s" + i));
        String conflict = FootprintStrikeViewTest.episode("OPEN", "2026-09-10", "1m", 680_000, 100, 0, 100, "conflict");
        Thread live = new Thread(() -> { for (String r : records) s.onFootprintLiveRecord("es-footprint-strike", r); }, "state-live");
        Thread cache = new Thread(() -> s.admitFootprintRecord("es-footprint-strike", conflict, "cache"), "state-cache");
        try {
            live.start();
            assertTrue(closeEntered.await(10, TimeUnit.SECONDS), "the slow socket overflowed and its close began");
            live.join(5_000);
            cache.start();                                                       // the cache consumer's refusal, while that close is still blocked
            cache.join(5_000);
            assertFalse(live.isAlive(), "the live consumer is not waiting inside the slow socket's close");
            assertFalse(cache.isAlive(), "the cache consumer is not waiting for a drainer stuck in that close");
            assertEquals(1, closeReturned.getCount(), "…and the close is STILL blocked: nothing above waited for it");
            assertTrue(closedOn.get().startsWith("options-edge-ws-closer-"), "the close runs on a shared closer thread: " + closedOn.get());
            List<String> want = new ArrayList<>(Collections.nCopies(n, "es-footprint-strike"));
            want.add("es-footprint-strike-control");
            for (List<String> healthy : List.of(a, b)) {
                List<JsonNode> frames = awaitStrikeTypes(healthy, want);
                assertEquals(records.get(n - 1), frames.get(n - 1).get("data").asText(), "every record, in order, then the refusal's control");
                assertEquals(1, frames.get(n).get("data").get("refused").asLong());
            }
            assertEquals(0, s.footprintStrikeView().queuedFrames());
        } finally {
            closeRelease.countDown();
            sendRelease.countDown();
            live.join(10_000);
            cache.join(10_000);
        }
        assertTrue(closeReturned.await(10, TimeUnit.SECONDS), "the teardown finished once the close could");
        verify(slow, times(1)).close();
        assertTrue(slowGot.stream().filter(p -> p.startsWith("{\"type\":\"es-footprint-strike")).count() <= 1,
                "the slow socket got at most the frame it was stuck on: its queue was dropped when it was marked closed");
    }

    // ---- strike re-review (round 3): teardowns share one bounded pool -----------------------------------------

    /** A slow socket: its strike sends block until {@code sendRelease}; its close runs {@code onClose}. */
    private static WebSocketSession slowSocket(String id, CountDownLatch sendRelease, org.mockito.stubbing.Answer<Void> onClose) throws Exception {
        WebSocketSession ws = mock(WebSocketSession.class);
        when(ws.getId()).thenReturn(id);
        when(ws.isOpen()).thenReturn(true);
        doAnswer(inv -> {
            if (((TextMessage) inv.getArgument(0)).getPayload().startsWith("{\"type\":\"es-footprint-strike")) sendRelease.await(30, TimeUnit.SECONDS);
            return null;
        }).when(ws).sendMessage(any());
        doAnswer(onClose).when(ws).close();
        return ws;
    }

    private static List<String> strikeRecords(int n) {
        List<String> records = new ArrayList<>();
        for (int i = 0; i < n; i++) records.add(FootprintStrikeViewTest.episode("OPEN", "2026-09-10", "1m", 680_000 + i * 500L, 100, 0, 100, "s" + i));
        return records;
    }

    /**
     * The reviewer's round-3 finding on the production path: 64 sockets stop reading at once, overflow on the
     * strike fold's drainer, and each one's close blocks. With a thread per close that was 64 live closer
     * threads at once — and in a real mass overflow a Thread.start() that cannot get a native thread throws
     * OutOfMemoryError on the drainer. Now at most CLOSER_THREADS closer threads ever exist, exactly that many
     * closes are in progress while the rest wait, the live consumer and both healthy sockets are never held up,
     * and once the closes can return every slow socket is torn down — detached — exactly once.
     */
    @Test void sixtyFourSlowSocketsOverflowingAtOnceNeverHoldMoreCloserThreadsThanTheBound() throws Exception {
        OutboundChannelTest.awaitSharedClosersIdle();
        FeedGatewayService s = FootprintWiringTest.on();                        // no runOutboundWritesInline: the production writer pool
        List<String> a = sink(), b = sink();
        WebSocketSession sa = socket("a", a), sb = socket("b", b);
        // A writer thread for every socket, so 64 stuck SENDS cannot starve the healthy sockets' writes: this
        // test is about the closers. (The pool is sized at the first addClient.)
        withProperty("GATEWAY_WS_WRITER_THREADS", "96", () -> { s.addClient(sa); return null; });
        s.addClient(sb);
        int slowCount = 64, n = 80;
        CountDownLatch sendRelease = new CountDownLatch(1), closeRelease = new CountDownLatch(1);
        AtomicInteger closesInProgress = new AtomicInteger(), closesReturned = new AtomicInteger();
        Map<String, AtomicInteger> closeCalls = new ConcurrentHashMap<>();
        List<WebSocketSession> slow = new ArrayList<>();
        for (int i = 0; i < slowCount; i++) {
            String id = "slow-" + i;
            slow.add(slowSocket(id, sendRelease, inv -> {
                closeCalls.computeIfAbsent(id, k -> new AtomicInteger()).incrementAndGet();
                closesInProgress.incrementAndGet();
                try { closeRelease.await(30, TimeUnit.SECONDS); } finally { closesInProgress.decrementAndGet(); closesReturned.incrementAndGet(); }
                return null;
            }));
        }
        withProperty("GATEWAY_WS_MAX_QUEUED_MESSAGES", "50", () -> { slow.forEach(s::addClient); return null; });
        List<String> records = strikeRecords(n);
        AtomicReference<Throwable> thrown = new AtomicReference<>();
        Thread live = new Thread(() -> {
            try { for (String r : records) s.onFootprintLiveRecord("es-footprint-strike", r); } catch (Throwable t) { thrown.set(t); }
        }, "state-live");
        AtomicInteger peak = new AtomicInteger();
        AtomicBoolean sampling = new AtomicBoolean(true);
        Thread sampler = new Thread(() -> {
            while (sampling.get()) {
                peak.accumulateAndGet(OutboundChannelTest.liveCloserThreads(), Math::max);
                try { Thread.sleep(2); } catch (InterruptedException e) { return; }
            }
        }, "closer-sampler");
        try {
            sampler.start();
            live.start();
            live.join(10_000);
            assertFalse(live.isAlive(), "the live consumer fanned every record out while the slow sockets' closes are blocked");
            assertNull(thrown.get(), "nothing was thrown at the drainer");
            long deadline = System.currentTimeMillis() + 10_000;
            while (closesInProgress.get() < OutboundChannel.CLOSER_THREADS && System.currentTimeMillis() < deadline) Thread.sleep(5);
            Thread.sleep(300);                                                  // room for any closer thread beyond the bound to appear
            sampling.set(false);
            sampler.join(5_000);
            System.out.println("[closer-bound] FootprintStrikeDeliveryTest: 64 overflowing sockets, peak live closer threads "
                    + peak.get() + " (bound " + OutboundChannel.CLOSER_THREADS + "), closes in progress " + closesInProgress.get()
                    + ", queued " + OutboundChannel.TEARDOWN.queued());
            assertTrue(peak.get() <= OutboundChannel.CLOSER_THREADS,
                    "64 overflowing sockets held " + peak.get() + " live closer threads at once; the bound is " + OutboundChannel.CLOSER_THREADS);
            assertEquals(OutboundChannel.CLOSER_THREADS, closesInProgress.get(), "exactly the bound's closes are in progress; the others wait");
            for (WebSocketSession ws : slow) {
                OutboundChannel ch = s.outboundChannelForTest(ws.getId());
                assertNotNull(ch, ws.getId() + " stays registered, closed, until its teardown runs");
                assertTrue(ch.isClosed(), ws.getId() + " overflowed");
            }
            List<String> want = Collections.nCopies(n, "es-footprint-strike");
            for (List<String> healthy : List.of(a, b)) {
                List<JsonNode> frames = awaitStrikeTypes(healthy, want);
                assertEquals(records.get(n - 1), frames.get(n - 1).get("data").asText(), "every record, in order");
            }
            assertEquals(0, s.footprintStrikeView().queuedFrames());
        } finally {
            sampling.set(false);
            closeRelease.countDown();
            sendRelease.countDown();
            live.join(10_000);
        }
        long deadline = System.currentTimeMillis() + 10_000;
        while ((closesReturned.get() < slowCount || slow.stream().anyMatch(ws -> s.outboundChannelForTest(ws.getId()) != null))
                && System.currentTimeMillis() < deadline) Thread.sleep(10);
        assertEquals(slowCount, closesReturned.get(), "every close ran once it could return");
        for (WebSocketSession ws : slow) {
            assertEquals(1, closeCalls.get(ws.getId()).get(), "one session close for " + ws.getId());
            assertNull(s.outboundChannelForTest(ws.getId()), "…then its detach (onClose) ran for " + ws.getId());
        }
    }

    /**
     * A slow socket whose teardown cannot be handed over — its executor throws OutOfMemoryError, as a
     * Thread.start() does with no native thread left — overflows on the strike drainer. Before, the Error
     * escaped the RuntimeException fallback: the drainer's fan-out aborted with the frame already taken from the
     * view's outbox (every socket after it lost that record), and the channel, already marked closed, was never
     * torn down (the watchdog skipped it). Now the fan-out completes for every other socket and the write
     * watchdog's next tick hands the teardown over: the socket is closed and detached exactly once.
     */
    @Test void aSlowSocketsTeardownThatCannotBeScheduledNeitherAbortsTheFanOutNorIsLost() throws Exception {
        FeedGatewayService s = FootprintWiringTest.on();
        List<String> a = sink(), b = sink(), c = sink();
        s.addClient(socket("a", a));
        s.addClient(socket("b", b));
        AtomicBoolean accept = new AtomicBoolean(false);
        AtomicInteger refusals = new AtomicInteger(), closeCalls = new AtomicInteger();
        CountDownLatch sendRelease = new CountDownLatch(1);
        WebSocketSession slow = slowSocket("slow", sendRelease, inv -> { closeCalls.incrementAndGet(); return null; });
        s.outboundClosersForTest(task -> {
            if (!accept.get()) {
                refusals.incrementAndGet();
                throw new OutOfMemoryError("unable to create native thread: possibly out of memory or process/resource limits reached");
            }
            OutboundChannel.TEARDOWN.execute(task);
        });
        withProperty("GATEWAY_WS_MAX_QUEUED_MESSAGES", "50", () -> { s.addClient(slow); return null; });
        s.outboundClosersForTest(null);
        s.addClient(socket("c", c));
        int n = 80;
        List<String> records = strikeRecords(n);
        AtomicReference<Throwable> thrown = new AtomicReference<>();
        Thread live = new Thread(() -> {
            try { for (String r : records) s.onFootprintLiveRecord("es-footprint-strike", r); } catch (Throwable t) { thrown.set(t); }
        }, "state-live");
        try {
            live.start();
            live.join(10_000);
            assertFalse(live.isAlive());
            assertNull(thrown.get(), "a teardown that could not be scheduled was thrown at the drainer: " + thrown.get());
            List<String> want = Collections.nCopies(n, "es-footprint-strike");
            for (List<String> healthy : List.of(a, b, c)) {
                List<JsonNode> frames = awaitStrikeTypes(healthy, want);
                assertEquals(records.get(n - 1), frames.get(n - 1).get("data").asText(), "every record reached every other socket, in order");
            }
            assertEquals(0, s.footprintStrikeView().queuedFrames());
            assertEquals(1, refusals.get(), "the overflow tried to hand the teardown over once");
            OutboundChannel ch = s.outboundChannelForTest("slow");
            assertNotNull(ch, "not torn down yet: still registered");
            assertTrue(ch.isClosed() && ch.teardownPending(), "closed, with its teardown pending rather than lost");
            assertEquals(0, closeCalls.get());
            s.enforceOutboundWriteDeadlines();                                    // a watchdog tick while the closers still refuse
            assertEquals(2, refusals.get(), "the watchdog retried it");
            assertTrue(ch.teardownPending());
            accept.set(true);
            s.enforceOutboundWriteDeadlines();                                    // the next tick hands it over
            long deadline = System.currentTimeMillis() + 5_000;
            while ((closeCalls.get() < 1 || s.outboundChannelForTest("slow") != null) && System.currentTimeMillis() < deadline) Thread.sleep(10);
            assertEquals(1, closeCalls.get(), "the watchdog's retry closed the session");
            assertNull(s.outboundChannelForTest("slow"), "…and detached it");
            s.enforceOutboundWriteDeadlines();
            Thread.sleep(50);
            assertEquals(1, closeCalls.get(), "exactly once");
            assertEquals(2, refusals.get());
        } finally {
            sendRelease.countDown();
            live.join(10_000);
        }
    }

    // ---- strike re-review (round 4): no failure ends a closer thread ------------------------------------------

    /**
     * The reviewer's round-4 finding on the production path, without exhausting the real heap: eight slow sockets
     * overflow on the strike drainer and each one's session close runs out of heap — and so does REPORTING each
     * failure. Before, the report escaped the closer's catch: four failures killed all four closer threads, and
     * every teardown behind them waited forever, out of the watchdog's reach (it retries only a teardown that was
     * never accepted). Now no closer thread dies, the eight slow sockets queued among the failures are closed and
     * detached, each failed teardown is pending again, and the watchdog's next tick closes and detaches it, once.
     */
    @Test void aSlowSocketsTeardownThatFailsUnderHeapExhaustionKillsNoCloserAndIsNeverStranded() throws Exception {
        String prefix = "strike-oom-closer-";
        AtomicInteger reports = new AtomicInteger();
        OutboundChannel.TeardownPool closers = new OutboundChannel.TeardownPool(OutboundChannel.CLOSER_THREADS, prefix,
                OutboundChannel.CLOSE_DEADLINE_MS, failure -> {
                    reports.incrementAndGet();
                    throw new OutOfMemoryError("Java heap space");            // the report runs out of heap as well
                });
        try {
            FeedGatewayService s = FootprintWiringTest.on();
            List<String> a = sink(), b = sink();
            WebSocketSession sa = socket("a", a), sb = socket("b", b);
            withProperty("GATEWAY_WS_WRITER_THREADS", "32", () -> { s.addClient(sa); return null; });
            s.addClient(sb);
            int pairs = 8, n = 80;
            CountDownLatch sendRelease = new CountDownLatch(1);
            Map<String, AtomicInteger> closeCalls = new ConcurrentHashMap<>();
            Set<String> closed = ConcurrentHashMap.newKeySet();
            List<String> failingIds = new ArrayList<>(), fineIds = new ArrayList<>();
            List<WebSocketSession> slow = new ArrayList<>();
            for (int i = 0; i < 2 * pairs; i++) {
                boolean fails = i % 2 == 0;
                String id = (fails ? "oom-" : "fine-") + i;
                (fails ? failingIds : fineIds).add(id);
                WebSocketSession ws = slowSocket(id, sendRelease, inv -> {
                    int call = closeCalls.computeIfAbsent(id, k -> new AtomicInteger()).incrementAndGet();
                    if (fails && call == 1) throw new OutOfMemoryError("Java heap space");   // the container's close ran out of heap
                    closed.add(id);
                    return null;
                });
                // A session a close RETURNED from is closed, as a container's is: these sockets are detached while
                // the fan-out is still running, and a detached socket's frames reach only a session still open.
                when(ws.isOpen()).thenAnswer(inv -> !closed.contains(id));
                slow.add(ws);
            }
            s.outboundClosersForTest(closers);
            withProperty("GATEWAY_WS_MAX_QUEUED_MESSAGES", "50", () -> { slow.forEach(s::addClient); return null; });
            s.outboundClosersForTest(null);
            List<String> records = strikeRecords(n);
            AtomicReference<Throwable> thrown = new AtomicReference<>();
            Thread live = new Thread(() -> {
                try { for (String r : records) s.onFootprintLiveRecord("es-footprint-strike", r); } catch (Throwable t) { thrown.set(t); }
            }, "state-live");
            try {
                live.start();
                live.join(10_000);
                assertFalse(live.isAlive());
                assertNull(thrown.get(), "nothing was thrown at the drainer: " + thrown.get());
                long deadline = System.currentTimeMillis() + 10_000;
                while ((closers.failures() < pairs || fineIds.stream().anyMatch(id -> s.outboundChannelForTest(id) != null))
                        && System.currentTimeMillis() < deadline) Thread.sleep(10);
                assertEquals(OutboundChannel.CLOSER_THREADS, OutboundChannelTest.liveThreads(prefix),
                        "a closer thread died of a failure or of its report: nothing replaces it");
                assertEquals(pairs, closers.failures(), "every failed teardown was counted");
                assertEquals(pairs, closers.unreported(), "…and every report that ran out of heap");
                for (String id : fineIds) {
                    assertNull(s.outboundChannelForTest(id), id + ", queued among the failures, was closed and detached");
                    assertEquals(1, closeCalls.get(id).get(), "one session close for " + id);
                }
                for (String id : failingIds) {
                    OutboundChannel ch = s.outboundChannelForTest(id);
                    assertNotNull(ch, id + ": a close that threw never reaches the detach");
                    assertTrue(ch.isClosed() && ch.teardownPending(), id + " is closed, its teardown pending again rather than stranded");
                }
                List<String> want = Collections.nCopies(n, "es-footprint-strike");
                for (List<String> healthy : List.of(a, b)) {
                    List<JsonNode> frames = awaitStrikeTypes(healthy, want);
                    assertEquals(records.get(n - 1), frames.get(n - 1).get("data").asText(), "every record reached every healthy socket, in order");
                }
                s.enforceOutboundWriteDeadlines();                                // the watchdog's tick hands each failed teardown over again
                deadline = System.currentTimeMillis() + 10_000;
                while (failingIds.stream().anyMatch(id -> s.outboundChannelForTest(id) != null) && System.currentTimeMillis() < deadline) Thread.sleep(10);
                for (String id : failingIds) {
                    assertNull(s.outboundChannelForTest(id), id + " was closed and detached by the watchdog's retry");
                    assertEquals(2, closeCalls.get(id).get(), "the close that threw, then the retry's, for " + id);
                }
                s.enforceOutboundWriteDeadlines();
                Thread.sleep(50);
                for (String id : failingIds) assertEquals(2, closeCalls.get(id).get(), "exactly once more, for " + id);
                assertEquals(OutboundChannel.CLOSER_THREADS, OutboundChannelTest.liveThreads(prefix));
                assertEquals(pairs, closers.failures());
                assertEquals(pairs, reports.get());
            } finally {
                sendRelease.countDown();
                live.join(10_000);
            }
        } finally {
            closers.shutdownNow();
        }
    }

    /**
     * The reviewer's production scenario for #2: the live consumer misses an update (it re-sought END on a
     * reconnect), and the continuing cache consumer folds it after the replay completed. Before, every
     * connected browser kept the superseded revision until some unrelated authority change. Now the change
     * reaches every socket as the admitted record, in order; the live consumer's later copy adds nothing.
     */
    @Test void anUpdateOnlyTheCacheConsumerFoldedAfterTheReplayCompletedReachesEverySocket() throws Exception {
        FeedGatewayService s = FootprintWiringTest.on();
        s.runOutboundWritesInline();
        List<String> a = sink(), b = sink();
        s.addClient(socket("a", a)); s.addClient(socket("b", b));
        String r0 = FootprintStrikeViewTest.e(100, 0, "r0"), r1 = FootprintStrikeViewTest.e(100, 1, "r1");
        assertTrue(s.admitFootprintRecord("es-footprint-strike", r0, "cache"), "replayed");
        s.footprintStrikeView().replayCompleted();
        assertTrue(s.admitFootprintRecord("es-footprint-strike", r1, "cache"), "revision 1: the live consumer never saw it");
        for (List<String> sk : List.of(a, b)) {
            List<JsonNode> frames = strikeFrames(sk);
            assertEquals(List.of("es-footprint-strike-control", "es-footprint-strike"), types(frames), "the completion, then the change");
            assertFalse(frames.get(0).get("data").get("loading").asBoolean());
            assertEquals(r1, frames.get(1).get("data").asText(), "revision 1, byte for byte");
        }
        MockHttpServletResponse rest = new MockHttpServletResponse();
        controller(s).footprintStrikeLatest("1m", "2026-09-10", "", -1, 200, "Bearer x", rest);
        JsonNode page = M.readTree(rest.getContentAsByteArray());
        assertEquals(r1, page.get("episodes").get(0).asText(), "REST and the sockets agree");
        assertEquals(1, page.get("authority").asLong(), "an additive change: readers fold it, nothing is invalidated");
        assertTrue(s.onFootprintLiveRecord("es-footprint-strike", r1), "the live consumer's copy is admitted…");
        assertEquals(2, strikeFrames(a).size(), "…and changes nothing, so it is not sent twice");
        String m = s.footprintMetricsText();
        assertTrue(m.contains("gateway_footprint_broadcast_total{event=\"es-footprint-strike\"} 1\n"), m);
        assertTrue(m.contains("gateway_footprint_broadcast_total{event=\"es-footprint-strike-control\"} 1\n"), m);
    }

    /** The reviewer's reproduction of #3 on the wire: maxEpisodes = 1, 680000/100 then 681000/200. */
    @Test void latestNamesAnEvictedNewestEpisodeOnTheWire() throws Exception {
        FeedGatewayService s = withProperty("GATEWAY_ES_FOOTPRINT_STRIKE_MAX_EPISODES", "1", FootprintWiringTest::on);
        s.footprintStrikeView().replayCompleted();
        String second = FootprintStrikeViewTest.episode("OPEN", "2026-09-10", "1m", 681_000, 200, 0, 200, "second");
        assertTrue(s.admitFootprintRecord("es-footprint-strike", FootprintStrikeViewTest.episode("OPEN", "2026-09-10", "1m", 680_000, 100, 0, 100, "first"), "cache"));
        assertTrue(s.admitFootprintRecord("es-footprint-strike", second, "cache"));
        MockHttpServletResponse rest = new MockHttpServletResponse();
        controller(s).footprintStrikeLatest("1m", "2026-09-10", "", -1, 200, "Bearer x", rest);
        JsonNode page = M.readTree(rest.getContentAsByteArray());
        assertEquals(1, page.get("episodes").size());
        assertEquals(second, page.get("episodes").get(0).asText());
        assertEquals(200, page.get("historyBeginsAtMs").asLong());
        assertEquals(M.readTree("[{\"strikeCents\":680000,\"openBarStartMs\":100}]"), page.get("tombstones"), "the evicted newest episode is named");
        assertTrue(s.footprintMetricsText().contains("gateway_footprint_strike_eviction_markers 1\n"));
    }
}

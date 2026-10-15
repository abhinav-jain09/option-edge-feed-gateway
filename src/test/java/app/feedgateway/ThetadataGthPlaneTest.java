package app.feedgateway;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Overnight ThetaData GTH board plane — GATE-1-OVERNIGHT-THETADATA.md §4.2 / §4.3 (consumer side).
 * Mirrors the pre-open gateway tests in {@link FeedGatewayServiceTest}: the status plane is the slice-1
 * machinery instantiated a second time, the value plane is the compacted per-key cache the board is
 * reconstructed from after any restart. Every test drives the production seams (updateCache /
 * ingestThetadataGex / replayCachedToSocket / addClient), never a copy of them.
 */
class ThetadataGthPlaneTest {

    private static final String STATUS_EVENT = FeedGatewayService.THETADATA_GTH_STATUS_EVENT;
    private static final String VALUE_EVENT = FeedGatewayService.THETADATA_GEX_BY_STRIKE_EVENT;
    private static final String SESSION = "20260921";
    private static final String STRIKE_KEY = "SPX|" + SESSION + "|6600";

    @AfterEach
    void clearFlag() {
        System.clearProperty("GATEWAY_THETADATA_GTH_ENABLED");
    }

    // ---- wire shapes -------------------------------------------------------------------------------

    @Test
    void valueWrapCarriesKeyPartitionOffsetTimestampAndUntouchedPayloadWithFullEscaping() throws Exception {
        String wrapped = FeedGatewayService.wrapThetadataGex(STRIKE_KEY, 7, 41L, 1758400000000L,
                "{\"netGex\":1.5,\"baselineEpoch\":1}");
        assertEquals("{\"recordKey\":\"" + STRIKE_KEY + "\",\"partition\":7,\"offset\":41,"
                + "\"timestampMs\":1758400000000,\"gex\":{\"netGex\":1.5,\"baselineEpoch\":1}}", wrapped);
        assertEquals("{\"recordKey\":\"" + STRIKE_KEY + "\",\"partition\":3,\"offset\":9,\"timestampMs\":5,"
                + "\"phase\":\"EVICTED\",\"reason\":\"TOMBSTONE\"}",
                FeedGatewayService.wrapThetadataGexTombstone(STRIKE_KEY, 3, 9L, 5L));
        // EVERY control character stays valid JSON (parse-verified, the status-wrap discipline).
        ObjectMapper jackson = new ObjectMapper();
        String nasty = "a\"b\\c\nd\re\tfg";
        JsonNode parsed = jackson.readTree(FeedGatewayService.wrapThetadataGex(nasty, 1, 7L, 3L, "{}"));
        assertEquals(nasty, parsed.get("recordKey").asText());
        assertEquals(1, parsed.get("partition").asInt());
        assertEquals(7L, parsed.get("offset").asLong());
        assertEquals(3L, parsed.get("timestampMs").asLong());
        JsonNode tomb = jackson.readTree(FeedGatewayService.wrapThetadataGexTombstone(nasty, 1, 7L, 3L));
        assertEquals(nasty, tomb.get("recordKey").asText());
        assertEquals("EVICTED", tomb.get("phase").asText());
    }

    @Test
    void bothPlanesAreGlobalBroadcastEventsInPerSessionMode() {
        assertTrue(FeedGatewayService.GLOBAL_BROADCAST_EVENTS.contains(STATUS_EVENT),
                "auth-mode sockets must receive the standalone overnight status broadcasts");
        assertTrue(FeedGatewayService.GLOBAL_BROADCAST_EVENTS.contains(VALUE_EVENT),
                "auth-mode sockets must receive the standalone overnight value broadcasts");
    }

    @Test
    void statusPlaneBroadcastGateIsExactlyOncePerOffsetAndIndependentOfThePreOpenGate() {
        FeedGatewayService service = service();
        assertTrue(service.shouldBroadcastThetadataGth(5L));
        assertFalse(service.shouldBroadcastThetadataGth(5L), "sibling consumer's duplicate is suppressed");
        assertFalse(service.shouldBroadcastThetadataGth(4L), "a regressed offset never fires");
        assertTrue(service.shouldBroadcastThetadataGth(6L));
        // The two status planes are separate offset spaces: the pre-open gate is untouched.
        assertTrue(service.shouldBroadcastIbkrPreOpen(5L));
    }

    @Test
    void valuePlaneBroadcastGateIsPerPartitionExactlyOnceInOrder() {
        FeedGatewayService service = service();
        assertTrue(service.shouldBroadcastThetadataGex(0, 5L));
        assertFalse(service.shouldBroadcastThetadataGex(0, 5L));
        assertFalse(service.shouldBroadcastThetadataGex(0, 4L));
        assertTrue(service.shouldBroadcastThetadataGex(0, 6L));
        assertTrue(service.shouldBroadcastThetadataGex(1, 3L), "partitions are independent offset spaces");
        assertFalse(service.shouldBroadcastThetadataGex(1, 3L));
    }

    // ---- status plane: offset-ordered last-value-wins per raw key, wrapped, own cache --------------

    @Test
    @SuppressWarnings("unchecked")
    void statusCacheIsOffsetOrderedWrapsTheRawKafkaKeyAndNeverTouchesThePreOpenCache() throws Exception {
        FeedGatewayService service = service();
        String topic = new GatewaySettings().thetadataGthStatusTopic();
        assertEquals("options.thetadata.gex.status", topic);
        Object binding = topicBinding("THETADATA", STATUS_EVENT);
        String status = "{\"state\":\"FRESH\",\"recordRevision\":7,\"sessionId\":\"THETADATA_GTH:" + SESSION + "\"}";
        long now = System.currentTimeMillis();
        assertNotNull(updateCache(service, binding, recordAt(topic, 0, 5L, STRIKE_KEY, status, now), status));
        String newer = "{\"state\":\"FRESH\",\"recordRevision\":8}";
        // EQUAL Kafka timestamp but HIGHER offset: accepted (offset-ordered, not timestamp).
        assertNotNull(updateCache(service, binding, recordAt(topic, 0, 6L, STRIKE_KEY, newer, now), newer));
        // LATER timestamp but LOWER offset: rejected — a stale duplicate can never overwrite.
        assertNull(updateCache(service, binding, recordAt(topic, 0, 4L, STRIKE_KEY, status, now + 1_000L), status));
        // The SAME offset replayed by the sibling consumer: rejected (strictly higher only).
        assertNull(updateCache(service, binding, recordAt(topic, 0, 6L, STRIKE_KEY, newer, now), newer));
        // Controls cache under their own raw key (the barrier the web renders on, §6.4).
        String complete = "{\"epoch\":1,\"eventTimeMs\":" + now + "}";
        assertNotNull(updateCache(service, binding,
                recordAt(topic, 0, 9L, "__baseline-complete|" + SESSION + "|1", complete, now), complete));
        Map<String, String> cache = planeMap(service, "thetadataGthStatus");
        assertEquals("{\"recordKey\":\"" + STRIKE_KEY + "\",\"offset\":6,\"timestampMs\":" + now
                + ",\"status\":" + newer + "}", cache.get("THETADATA|" + STRIKE_KEY));
        assertTrue(cache.containsKey("THETADATA|__baseline-complete|" + SESSION + "|1"));
        assertTrue(planeMap(service, "ibkrPreOpenStatus").isEmpty(), "the pre-open plane is a separate cache");
    }

    // ---- value plane: per-key LWW, tombstone eviction, TTL ------------------------------------------

    @Test
    void valueCacheIsOffsetOrderedAndATombstoneEvictsForGood() throws Exception {
        FeedGatewayService service = service();
        String topic = new GatewaySettings().thetadataGthStrikeTopic();
        assertEquals("options.thetadata.gex.strike", topic);
        long now = System.currentTimeMillis();
        String v1 = gexJson(1, 7, now);
        assertEquals("THETADATA|" + STRIKE_KEY,
                service.ingestThetadataGex(recordAt(topic, 3, 5L, STRIKE_KEY, v1, now), v1, false));
        Map<String, String> cache = planeMap(service, "thetadataGexByStrike");
        assertEquals(FeedGatewayService.wrapThetadataGex(STRIKE_KEY, 3, 5L, now, v1), cache.get("THETADATA|" + STRIKE_KEY));
        // A lower offset (compacted redelivery / sibling consumer) never overwrites.
        String v0 = gexJson(1, 6, now);
        assertNull(service.ingestThetadataGex(recordAt(topic, 3, 4L, STRIKE_KEY, v0, now + 500L), v0, false));
        assertNull(service.ingestThetadataGex(recordAt(topic, 3, 5L, STRIKE_KEY, v1, now), v1, false),
                "the same offset replayed is rejected");
        // A different partition for the same key is fail-closed (the status-plane rule).
        assertNull(service.ingestThetadataGex(recordAt(topic, 4, 50L, STRIKE_KEY, v1, now), v1, false));
        // The GTH_CLOSED transaction's tombstone: the value is gone…
        assertNotNull(service.ingestThetadataGex(recordAt(topic, 3, 7L, STRIKE_KEY, null, now), null, false));
        assertTrue(cache.isEmpty(), "tombstone must evict the cached value");
        // …and a late value below the tombstone's offset cannot resurrect the strike.
        assertNull(service.ingestThetadataGex(recordAt(topic, 3, 6L, STRIKE_KEY, v1, now), v1, false));
        assertTrue(cache.isEmpty());
        // A tombstone for a key never cached is a harmless no-op that still advances the position.
        assertNotNull(service.ingestThetadataGex(recordAt(topic, 2, 1L, "SPX|" + SESSION + "|6605", null, now), null, false));
    }

    @Test
    void valueCacheHonoursTheSessionLengthTtlAtIngestAndOnPurge() throws Exception {
        FeedGatewayService service = service();
        String topic = new GatewaySettings().thetadataGthStrikeTopic();
        long now = System.currentTimeMillis();
        long ttl = new GatewaySettings().thetadataGthStrikeTtlMs();
        assertEquals(12 * 3_600_000L, ttl, "default value TTL is one GTH session (12 h)");
        assertEquals(12 * 3_600_000L, new GatewaySettings().thetadataGthStatusTtlMs(), "default status TTL is 12 h");
        // A previous night's value (older than the window) is never cached.
        String old = gexJson(1, 3, now - ttl - 60_000L);
        assertNull(service.ingestThetadataGex(recordAt(topic, 0, 1L, STRIKE_KEY, old, now - ttl - 60_000L), old, false));
        assertTrue(planeMap(service, "thetadataGexByStrike").isEmpty());
        // A fresh value is cached, then ages out on the periodic purge.
        String fresh = gexJson(1, 4, now);
        assertNotNull(service.ingestThetadataGex(recordAt(topic, 0, 2L, STRIKE_KEY, fresh, now), fresh, false));
        assertEquals(1, planeMap(service, "thetadataGexByStrike").size());
        purgeExpiredCache(service, now + ttl + 1L);
        assertTrue(planeMap(service, "thetadataGexByStrike").isEmpty(), "the purge must evict an aged value");
        // The status plane ages the same way.
        Object binding = topicBinding("THETADATA", STATUS_EVENT);
        String status = "{\"state\":\"FRESH\"}";
        assertNotNull(updateCache(service, binding,
                recordAt(new GatewaySettings().thetadataGthStatusTopic(), 0, 3L, STRIKE_KEY, status, now), status));
        assertEquals(1, planeMap(service, "thetadataGthStatus").size());
        purgeExpiredCache(service, now + ttl + 1L);
        assertTrue(planeMap(service, "thetadataGthStatus").isEmpty());
    }

    // ---- live delivery ------------------------------------------------------------------------------

    @Test
    void liveValueDeliveryRidesTheCaughtUpFlagAndTheOffsetGateAndBroadcastsTombstones() throws Exception {
        FeedGatewayService service = service();
        List<String> sink = new ArrayList<>();
        addRecordingClient(service, sink);
        String topic = new GatewaySettings().thetadataGthStrikeTopic();
        long now = System.currentTimeMillis();
        String v1 = gexJson(1, 7, now);
        // Not caught up: cached, not broadcast.
        assertNotNull(service.ingestThetadataGex(recordAt(topic, 0, 5L, STRIKE_KEY, v1, now), v1, false));
        assertTrue(sink.isEmpty());
        // Caught up: the live consumer broadcasts the wrapped value exactly once.
        String v2 = gexJson(1, 8, now);
        assertNotNull(service.ingestThetadataGex(recordAt(topic, 0, 6L, STRIKE_KEY, v2, now), v2, true));
        assertEquals(1, sink.size());
        assertTrue(sink.get(0).contains("\"type\":\"" + VALUE_EVENT + "\""), sink.get(0));
        assertTrue(sink.get(0).contains("\"recordRevision\":8"), sink.get(0));
        // The sibling consumer's duplicate of offset 6 is rejected by the cache and never broadcast.
        assertNull(service.ingestThetadataGex(recordAt(topic, 0, 6L, STRIKE_KEY, v2, now), v2, true));
        assertEquals(1, sink.size());
        // The tombstone is broadcast so a connected client drops the strike.
        assertNotNull(service.ingestThetadataGex(recordAt(topic, 0, 7L, STRIKE_KEY, null, now), null, true));
        assertEquals(2, sink.size());
        assertTrue(sink.get(1).contains("\"phase\":\"EVICTED\"") && sink.get(1).contains("\"reason\":\"TOMBSTONE\""),
                sink.get(1));
    }

    // ---- replay on connect: status plane first, then the value cache, gated on BOTH barriers -------

    @Test
    void connectReplaysTheStatusPlaneBeforeTheValueCacheOnBothConnectPaths() throws Exception {
        System.setProperty("GATEWAY_THETADATA_GTH_ENABLED", "true");
        FeedGatewayService service = service();
        long now = System.currentTimeMillis();
        seedPlane(service, now);

        // Per-session (auth) connect path + return-to-live: replayCachedToSocket.
        Method replay = FeedGatewayService.class.getDeclaredMethod("replayCachedToSocket", WebSocketSession.class);
        replay.setAccessible(true);
        List<String> sink = new ArrayList<>();
        WebSocketSession session = recordingSession(sink);
        setBarrier(service, "avroCaughtUp", true);
        setBarrier(service, "stateCaughtUp", false);
        replay.invoke(service, session);
        assertTrue(sink.stream().noneMatch(m -> m.contains(STATUS_EVENT) || m.contains(VALUE_EVENT)),
                "nothing of the plane is served before the control stream is caught up: " + sink);
        setBarrier(service, "stateCaughtUp", true);
        setBarrier(service, "avroCaughtUp", false);
        replay.invoke(service, session);
        assertTrue(sink.stream().anyMatch(m -> m.contains(STATUS_EVENT)), "statuses replay on the state barrier");
        assertTrue(sink.stream().noneMatch(m -> m.contains(VALUE_EVENT)),
                "values wait for the avro barrier too: " + sink);
        sink.clear();
        setBarrier(service, "avroCaughtUp", true);
        replay.invoke(service, session);
        assertOrdered(sink);

        // Legacy connect path: addClient through a REAL outbound channel.
        service.runOutboundWritesInline();
        service.markStateCaughtUpWithoutHandoffForTest();
        List<String> joined = new CopyOnWriteArrayList<>();
        service.addClient(recordingSession(joined));
        assertOrdered(joined);
    }

    private static void assertOrdered(List<String> frames) {
        int statusAt = -1;
        int completeAt = -1;
        int valueAt = -1;
        for (int i = 0; i < frames.size(); i++) {
            String frame = frames.get(i);
            if (frame.contains("\"type\":\"" + STATUS_EVENT + "\"")) {
                if (frame.contains("__path|")) statusAt = statusAt < 0 ? i : statusAt;
                if (frame.contains("__baseline-complete|")) completeAt = i;
            }
            if (frame.contains("\"type\":\"" + VALUE_EVENT + "\"")) valueAt = valueAt < 0 ? i : valueAt;
        }
        assertTrue(statusAt >= 0, "the __path control must replay: " + frames);
        assertTrue(completeAt >= 0, "the __baseline-complete control must replay: " + frames);
        assertTrue(valueAt >= 0, "the cached value must replay: " + frames);
        assertTrue(statusAt < valueAt && completeAt < valueAt,
                "bootstrap protocol: status plane -> value cache -> live (" + statusAt + "," + completeAt + "," + valueAt + ")");
    }

    @Test
    void aGatewayRestartedAfterTheCloseReplaysStatusesAndNoValues() throws Exception {
        // The GTH_CLOSED transaction tombstones every value key: a cache rebuilt from the compacted
        // topics holds the __path = GTH_CLOSED control and no value (§4.2 durable value replay).
        System.setProperty("GATEWAY_THETADATA_GTH_ENABLED", "true");
        FeedGatewayService service = service();
        long now = System.currentTimeMillis();
        seedPlane(service, now);
        String strikeTopic = new GatewaySettings().thetadataGthStrikeTopic();
        String statusTopic = new GatewaySettings().thetadataGthStatusTopic();
        assertNotNull(service.ingestThetadataGex(recordAt(strikeTopic, 0, 12L, STRIKE_KEY, null, now), null, false));
        String closed = "{\"state\":\"GTH_CLOSED\",\"sessionId\":\"THETADATA_GTH:" + SESSION + "\",\"eventTimeMs\":" + now + "}";
        assertNotNull(updateCache(service, topicBinding("THETADATA", STATUS_EVENT),
                recordAt(statusTopic, 0, 20L, "__path|" + SESSION, closed, now), closed));
        Method replay = FeedGatewayService.class.getDeclaredMethod("replayCachedToSocket", WebSocketSession.class);
        replay.setAccessible(true);
        setBarrier(service, "avroCaughtUp", true);
        setBarrier(service, "stateCaughtUp", true);
        List<String> sink = new ArrayList<>();
        replay.invoke(service, recordingSession(sink));
        assertTrue(sink.stream().anyMatch(m -> m.contains(STATUS_EVENT) && m.contains("GTH_CLOSED")), sink.toString());
        assertTrue(sink.stream().noneMatch(m -> m.contains(VALUE_EVENT)), "no value survives the close: " + sink);
    }

    // ---- feature flag: OFF binds nothing and reports nothing ---------------------------------------

    @Test
    void flagOffBindsNoTopicAndKeepsTheHealthPayloadDark() throws Exception {
        String source = Files.readString(Path.of("src/main/java/app/feedgateway/FeedGatewayService.java"));
        String statusBinding = "topicEvents.put(settings.thetadataGthStatusTopic(), new TopicBinding(\"THETADATA\", THETADATA_GTH_STATUS_EVENT));";
        String valueBinding = "topicEvents.put(settings.thetadataGthStrikeTopic(), new TopicBinding(\"THETADATA\", THETADATA_GEX_BY_STRIKE_EVENT));";
        for (String method : List.of("runJsonStateCacheConsumer", "runJsonStateLiveConsumer")) {
            String body = methodBody(source, method);
            assertTrue(body.contains(statusBinding), method + " must bind the GTH status topic (JSON)");
            assertFalse(body.contains(valueBinding), method + " must NOT bind the Avro value topic");
            assertTrue(guardedByFlag(body, statusBinding), method + " status binding must sit under the flag");
        }
        for (String method : List.of("runAvroCacheConsumer", "runAvroLiveConsumer")) {
            String body = methodBody(source, method);
            assertTrue(body.contains(valueBinding), method + " must bind the GTH value topic (Avro)");
            assertFalse(body.contains(statusBinding), method + " must NOT bind the JSON status topic");
            assertTrue(guardedByFlag(body, valueBinding), method + " value binding must sit under the flag");
        }
        // Behavioural: OFF is byte-equivalent to a build without the feature in /health…
        System.clearProperty("GATEWAY_THETADATA_GTH_ENABLED");
        assertFalse(new GatewaySettings().thetadataGthEnabled(), "default OFF");
        FeedGatewayService dark = service();
        assertFalse(dark.healthJson().contains("thetadataGthStatus"));
        assertFalse(dark.metrics().contains("thetadata_gth_status"));
        // …and ON reports the (initially empty) plane.
        System.setProperty("GATEWAY_THETADATA_GTH_ENABLED", "true");
        FeedGatewayService lit = service();
        assertTrue(lit.healthJson().contains("\"thetadataGthStatus\":0"));
        assertTrue(lit.healthJson().contains("\"thetadataGexByStrike\":0"));
        assertTrue(lit.metrics().contains("options_edge_feed_gateway_thetadata_gex_by_strike 0"));
    }

    private static boolean guardedByFlag(String body, String binding) {
        int at = body.indexOf(binding);
        int guard = body.lastIndexOf("if (settings.thetadataGthEnabled()) {", at);
        return at >= 0 && guard >= 0 && body.indexOf('}', guard) > at;
    }

    // ---- consumer contract: read_committed + seekToBeginning on (re)start ----------------------------

    @Test
    void bothPlanesAreConsumedReadCommitted() throws Exception {
        FeedGatewayService service = service();
        for (String factory : List.of("avroConsumerProperties", "stringObjectConsumerProperties")) {
            Method m = FeedGatewayService.class.getDeclaredMethod(factory, String.class);
            m.setAccessible(true);
            Properties props = (Properties) m.invoke(service, "gth");
            assertEquals("read_committed", props.getProperty("isolation.level"),
                    factory + " must read committed so an aborted overnight transaction never surfaces");
        }
    }

    @Test
    void cacheHydrationSeeksBothOvernightTopicsToTheBeginningNotToATimestampWindow() throws Exception {
        System.setProperty("GATEWAY_THETADATA_GTH_ENABLED", "true");
        FeedGatewayService service = service();
        GatewaySettings settings = new GatewaySettings();
        TopicPartition status = new TopicPartition(settings.thetadataGthStatusTopic(), 0);
        TopicPartition strike = new TopicPartition(settings.thetadataGthStrikeTopic(), 4);
        TopicPartition ordinary = new TopicPartition(settings.databentoGexTopic(), 0);
        Map<String, Object> topicEvents = new LinkedHashMap<>();
        topicEvents.put(status.topic(), topicBinding("THETADATA", STATUS_EVENT));
        topicEvents.put(strike.topic(), topicBinding("THETADATA", VALUE_EVENT));
        topicEvents.put(ordinary.topic(), topicBinding("DATABENTO", "gex-by-strike"));
        KafkaConsumer<?, ?> consumer = org.mockito.Mockito.mock(KafkaConsumer.class);
        org.mockito.Mockito.when(consumer.offsetsForTimes(org.mockito.ArgumentMatchers.anyMap()))
                .thenReturn(Map.of());
        Method seek = FeedGatewayService.class.getDeclaredMethod("seekToCacheWindow",
                KafkaConsumer.class, List.class, Map.class);
        seek.setAccessible(true);
        seek.invoke(service, consumer, List.of(status, strike, ordinary), topicEvents);
        org.mockito.Mockito.verify(consumer).seekToBeginning(List.of(status, strike));
        org.mockito.Mockito.verify(consumer, org.mockito.Mockito.never())
                .seekToBeginning(org.mockito.ArgumentMatchers.argThat(
                        (java.util.Collection<TopicPartition> c) -> c != null && c.contains(ordinary)));
    }

    // ---- helpers (the FeedGatewayServiceTest idioms, local so this class stands alone) -------------

    private static String gexJson(long epoch, long revision, long updatedAtMs) {
        return "{\"symbol\":\"SPX\",\"expiry\":\"" + SESSION + "\",\"strike\":6600.0,\"netGex\":1.23E9,"
                + "\"source\":\"THETADATA\",\"timeframe\":\"GTH_OVERNIGHT\",\"sessionId\":\"THETADATA_GTH:" + SESSION + "\","
                + "\"outputGeneration\":1,\"baselineEpoch\":" + epoch + ",\"recordRevision\":" + revision
                + ",\"updatedAtMs\":" + updatedAtMs + ",\"oiQuality\":\"PRIOR_SESSION\"}";
    }

    private static void seedPlane(FeedGatewayService service, long now) throws Exception {
        GatewaySettings settings = new GatewaySettings();
        Object binding = topicBinding("THETADATA", STATUS_EVENT);
        String path = "{\"state\":\"GTH_ARMED\",\"sessionId\":\"THETADATA_GTH:" + SESSION + "\",\"eventTimeMs\":" + now + "}";
        assertNotNull(updateCache(service, binding,
                recordAt(settings.thetadataGthStatusTopic(), 0, 1L, "__path|" + SESSION, path, now), path));
        String manifest = "{\"manifestEpoch\":1,\"strikes\":[{\"strike\":6600}]}";
        assertNotNull(updateCache(service, binding,
                recordAt(settings.thetadataGthStatusTopic(), 0, 2L, "__manifest|" + SESSION, manifest, now), manifest));
        String fresh = "{\"state\":\"FRESH\",\"outputGeneration\":1,\"baselineEpoch\":1,\"recordRevision\":7}";
        assertNotNull(updateCache(service, binding,
                recordAt(settings.thetadataGthStatusTopic(), 0, 3L, STRIKE_KEY, fresh, now), fresh));
        String complete = "{\"epoch\":1}";
        assertNotNull(updateCache(service, binding,
                recordAt(settings.thetadataGthStatusTopic(), 0, 4L, "__baseline-complete|" + SESSION + "|1", complete, now), complete));
        String value = gexJson(1, 7, now);
        assertNotNull(service.ingestThetadataGex(
                recordAt(settings.thetadataGthStrikeTopic(), 0, 10L, STRIKE_KEY, value, now), value, false));
    }

    private static FeedGatewayService service() {
        return new FeedGatewayService(new GatewaySettings(), new ObjectMapper(), new HpsfGatewayViewMapper(), null);
    }

    private static Object topicBinding(String source, String event) throws Exception {
        Class<?> type = Class.forName("app.feedgateway.FeedGatewayService$TopicBinding");
        Constructor<?> constructor = type.getDeclaredConstructor(String.class, String.class);
        constructor.setAccessible(true);
        return constructor.newInstance(source, event);
    }

    private static String updateCache(FeedGatewayService service, Object binding,
                                      ConsumerRecord<String, String> record, String json) throws Exception {
        Class<?> bindingType = Class.forName("app.feedgateway.FeedGatewayService$TopicBinding");
        Method method = FeedGatewayService.class.getDeclaredMethod("updateCache", bindingType, ConsumerRecord.class, String.class);
        method.setAccessible(true);
        return (String) method.invoke(service, binding, record, json);
    }

    private static void purgeExpiredCache(FeedGatewayService service, long nowMs) throws Exception {
        Method method = FeedGatewayService.class.getDeclaredMethod("purgeExpiredCache", long.class);
        method.setAccessible(true);
        method.invoke(service, nowMs);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, String> planeMap(FeedGatewayService service, String field) throws Exception {
        Field f = FeedGatewayService.class.getDeclaredField(field);
        f.setAccessible(true);
        return (Map<String, String>) f.get(service);
    }

    private static void setBarrier(FeedGatewayService service, String field, boolean value) throws Exception {
        Field barrier = FeedGatewayService.class.getDeclaredField(field);
        barrier.setAccessible(true);
        ((AtomicBoolean) barrier.get(service)).set(value);
    }

    private static ConsumerRecord<String, String> recordAt(
            String topic, int partition, long offset, String key, String value, long timestampMs) {
        return new ConsumerRecord<>(topic, partition, offset, timestampMs,
                org.apache.kafka.common.record.TimestampType.CREATE_TIME, -1, -1, key, value,
                new org.apache.kafka.common.header.internals.RecordHeaders(), java.util.Optional.empty());
    }

    private static String methodBody(String source, String methodName) {
        int start = source.indexOf("private void " + methodName + "()");
        if (start < 0) {
            throw new IllegalArgumentException("method not found: " + methodName);
        }
        int next = source.indexOf("\n    private ", start + 1);
        return next < 0 ? source.substring(start) : source.substring(start, next);
    }

    private static WebSocketSession recordingSession(List<String> sink) {
        return (WebSocketSession) Proxy.newProxyInstance(
                WebSocketSession.class.getClassLoader(),
                new Class<?>[]{WebSocketSession.class},
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "isOpen": return Boolean.TRUE;
                        case "getId": return "gth-session-" + System.identityHashCode(sink);
                        case "sendMessage":
                            if (args[0] instanceof TextMessage tm) {
                                sink.add(tm.getPayload());
                            }
                            return null;
                        case "toString": return "RecordingSession";
                        case "hashCode": return System.identityHashCode(proxy);
                        case "equals": return proxy == args[0];
                        default:
                            Class<?> rt = method.getReturnType();
                            if (rt == boolean.class) return Boolean.FALSE;
                            if (rt == int.class) return 0;
                            if (rt == long.class) return 0L;
                            return null;
                    }
                });
    }

    private static void addRecordingClient(FeedGatewayService service, List<String> sink) throws Exception {
        WebSocketSession session = recordingSession(sink);
        Field clients = FeedGatewayService.class.getDeclaredField("clients");
        clients.setAccessible(true);
        @SuppressWarnings("unchecked")
        java.util.Collection<WebSocketSession> set = (java.util.Collection<WebSocketSession>) clients.get(service);
        set.add(session);
    }
}

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
 * PATTERNS relay (the Context Tape candlestick-pattern layer): the `market-structure-patterns` frame is the sibling
 * of the `market-structure` path — opt-in, live-consumer only, raw pass-through, latest-record retention PER
 * TIMEFRAME replayed inside the hello as {@code patterns}, allowlisted for per-session mode.
 */
class MarketStructurePatternsWiringTest {

    private static FeedGatewayService service() {
        return new FeedGatewayService(new GatewaySettings(), new ObjectMapper(), new HpsfGatewayViewMapper(), null);
    }

    private static String ok(String tf, long asOfMs) {
        return "{\"schemaVersion\":1,\"service\":\"market-structure\",\"state\":\"OK\",\"sessionDate\":\"2026-10-16\","
                + "\"asOfMs\":" + asOfMs + ",\"scheduleId\":\"2026-10-16.r0000000000\",\"timeframe\":\"" + tf + "\","
                + "\"decisionOrdinal\":7,\"parameterSetHash\":\"" + "a".repeat(64) + "\","
                + "\"effectiveLimits\":{\"maxPatternBars\":24,\"maxAnnotations\":64},\"annotations\":[]}";
    }

    private static String source() throws Exception {
        return Files.readString(Path.of("src/main/java/app/feedgateway/FeedGatewayService.java"));
    }

    @Test
    void isOptInAndNamesItsOwnTopic() {
        GatewaySettings s = new GatewaySettings();
        assertFalse(s.marketStructurePatternsEnabled(), "OFF by default — the flag is the last rollout step");
        assertEquals("options.spx.market-structure.patterns", s.marketStructurePatternsTopic());
    }

    @Test
    void authModeAllowlistCarriesTheFrame() {
        assertTrue(FeedGatewayService.GLOBAL_BROADCAST_EVENTS.contains("market-structure-patterns"));
    }

    @Test
    void boundaryAcceptsOnlyAMajorOneSetOfThisServiceWithAClockAndATimeframe() {
        var s = service();
        var b = s.validateMarketStructurePatterns(ok("5m", 1786900000000L));
        assertNotNull(b);
        assertEquals("5m", b.timeframe());
        assertEquals(1786900000000L, b.asOfMs());
        assertNotNull(s.validateMarketStructurePatterns(
                "{\"schemaVersion\":1,\"service\":\"market-structure\",\"state\":\"UNAVAILABLE\",\"reason\":\"NOT_READY\",\"sessionDate\":\"2026-10-16\",\"asOfMs\":5,\"timeframe\":\"1m\"}"));
        assertNull(s.validateMarketStructurePatterns(null));
        assertNull(s.validateMarketStructurePatterns("not json"));
        assertNull(s.validateMarketStructurePatterns("[1,2]"));
        assertNull(s.validateMarketStructurePatterns(ok("5m", 1L).replace("\"service\":\"market-structure\"", "\"service\":\"other\"")));
        assertNull(s.validateMarketStructurePatterns(ok("5m", 1L).replace("\"schemaVersion\":1", "\"schemaVersion\":2")));
        assertNull(s.validateMarketStructurePatterns(ok("5m", 1L).replace("\"state\":\"OK\"", "\"state\":\"HALF\"")));
        assertNull(s.validateMarketStructurePatterns(ok("30m", 1L)), "a timeframe outside {1m,5m,15m} is refused");
        assertNull(s.validateMarketStructurePatterns(ok("5m", 0L)), "a non-positive asOfMs is refused");
        assertNull(s.validateMarketStructurePatterns(ok("5m", 9L) + "null"), "trailing tokens are refused");
    }

    @Test
    void boundaryRefusesSetsOverTheRequirementsByteBound() {
        var s = service();
        String big = ok("1m", 1L).replace("\"annotations\":[]", "\"annotations\":[\"" + "x".repeat(FeedGatewayService.MARKET_STRUCTURE_PATTERNS_MAX_BYTES) + "\"]");
        assertNull(s.validateMarketStructurePatterns(big));
    }

    @Test
    void retentionIsPerTimeframeAndRefusesARegressionWithinOne() {
        var s = service();
        assertTrue(s.retainMarketStructurePatterns("1m", 100L, ok("1m", 100L)));
        assertTrue(s.retainMarketStructurePatterns("5m", 100L, ok("5m", 100L)));
        assertTrue(s.retainMarketStructurePatterns("1m", 200L, ok("1m", 200L)));
        assertFalse(s.retainMarketStructurePatterns("1m", 150L, ok("1m", 150L)), "an older fold never displaces a newer one");
        assertTrue(s.retainMarketStructurePatterns("5m", 150L, ok("5m", 150L)), "another timeframe has its own baseline");
        assertEquals(ok("1m", 200L), s.marketStructurePatternsLatestForTest("1m"));
        assertEquals(1L, s.marketStructurePatternsRegressionsForTest());
        assertTrue(s.retainMarketStructurePatterns("1m", 200L, ok("1m", 200L)), "an equal asOfMs is an idempotent republish");
    }

    @Test
    void boundaryRefusesEveryClockAndVersionShape() {
        var s = service();
        assertNull(s.validateMarketStructurePatterns(ok("5m", 1L).replace("\"asOfMs\":1,", "")), "a missing asOfMs is refused");
        assertNull(s.validateMarketStructurePatterns(ok("5m", 1L).replace("\"asOfMs\":1,", "\"asOfMs\":1.5,")), "a fractional asOfMs is refused");
        assertNull(s.validateMarketStructurePatterns(ok("5m", 1L).replace("\"asOfMs\":1,", "\"asOfMs\":\"1\",")), "a textual asOfMs is refused");
        assertNull(s.validateMarketStructurePatterns(ok("5m", (1L << 53))), "a JS-unsafe asOfMs is refused");
        assertNotNull(s.validateMarketStructurePatterns(ok("5m", (1L << 53) - 1)), "the JS-exact bound itself is accepted");
        assertNull(s.validateMarketStructurePatterns(ok("5m", 1L).replace("\"schemaVersion\":1", "\"schemaVersion\":1.0")), "a non-integral schema version is refused");
        assertNull(s.validateMarketStructurePatterns(ok("5m", 1L).replace("\"schemaVersion\":1", "\"schemaVersion\":\"1\"")), "a textual schema version is refused");
        assertNull(s.validateMarketStructurePatterns(ok("5m", 1L).replace("\"timeframe\":\"5m\",", "")), "a missing timeframe is refused");
        assertNull(s.validateMarketStructurePatterns(""), "an empty body is refused");
        assertNull(s.validateMarketStructurePatterns("{}"), "an empty object is refused");
    }

    @Test
    void aTombstoneWithdrawsExactlyTheTimeframeItsKeyNames() {
        var s = service();
        assertTrue(s.retainMarketStructurePatterns("1m", 100L, ok("1m", 100L)));
        assertTrue(s.retainMarketStructurePatterns("5m", 100L, ok("5m", 100L)));
        assertTrue(s.retainMarketStructurePatterns("15m", 100L, ok("15m", 100L)));
        s.evictMarketStructurePatternsTombstone("market-structure",
                new ConsumerRecord<>("options.market-structure.levels", 0, 1L, "k", null));
        assertNotNull(s.marketStructurePatternsLatestForTest("1m"), "another event's tombstone never touches this retention");
        s.evictMarketStructurePatternsTombstone("market-structure-patterns",
                new ConsumerRecord<>("options.spx.market-structure.patterns", 0, 2L, "2026-10-16.r0000000000|5m", null));
        assertNull(s.marketStructurePatternsLatestForTest("5m"), "the keyed timeframe is withdrawn");
        assertNotNull(s.marketStructurePatternsLatestForTest("1m"), "the other timeframes keep their sets");
        assertNotNull(s.marketStructurePatternsLatestForTest("15m"));
        assertEquals(1L, s.marketStructurePatternsDropsForTest());
        assertTrue(s.retainMarketStructurePatterns("5m", 50L, ok("5m", 50L)), "after a withdrawal that timeframe's baseline is erased");
        assertFalse(s.retainMarketStructurePatterns("1m", 50L, ok("1m", 50L)), "…and the others' baselines are not");
        s.evictMarketStructurePatternsTombstone("market-structure-patterns",
                new ConsumerRecord<>("options.spx.market-structure.patterns", 0, 3L, "whatever-key", null));
        assertNotNull(s.marketStructurePatternsLatestForTest("1m"), "a tombstone whose key names no timeframe withdraws nothing");
        assertEquals(2L, s.marketStructurePatternsDropsForTest(), "…and is counted");
        s.evictMarketStructurePatternsTombstone("market-structure-patterns",
                new ConsumerRecord<>("options.spx.market-structure.patterns", 0, 4L, null, null));
        assertNotNull(s.marketStructurePatternsLatestForTest("1m"), "a null key withdraws nothing");
        assertNull(FeedGatewayService.marketStructurePatternsKeyTimeframe("2026-10-16.r0000000000|30m"));
        assertNull(FeedGatewayService.marketStructurePatternsKeyTimeframe("|1m"));
        assertNull(FeedGatewayService.marketStructurePatternsKeyTimeframe("2026-10-16|"));
        assertEquals("15m", FeedGatewayService.marketStructurePatternsKeyTimeframe("2026-10-16.r0000000000|15m"));
    }

    @Test
    void helloReadsOneSnapshotSoAWithdrawalNeverShowsBesideItsPredecessor() throws Exception {
        System.setProperty("GATEWAY_MARKET_STRUCTURE_PATTERNS_ENABLED", "true");
        try {
            var on = service();
            assertTrue(on.retainMarketStructurePatterns("1m", 100L, ok("1m", 100L)));
            assertTrue(on.retainMarketStructurePatterns("5m", 100L, ok("5m", 100L)));
            on.evictMarketStructurePatternsTombstone("market-structure-patterns",
                    new ConsumerRecord<>("options.spx.market-structure.patterns", 0, 2L, "2026-10-16.r0000000000|5m", null));
            String hello = on.cvdHelloJson();
            assertTrue(hello.contains("\"1m\":" + ok("1m", 100L)), "1m survives a 5m withdrawal");
            assertTrue(hello.contains("\"5m\":null"), "the withdrawn timeframe is an explicit null");
            assertTrue(hello.contains("\"15m\":null"));
            // the snapshot is immutable: a writer replaces it, never mutates what a hello may be reading
            String src = source();
            assertTrue(src.contains("marketStructurePatternsSnapshot.set(java.util.Map.copyOf(next))"), "copy-on-write under the retain lock");
            assertFalse(src.contains("marketStructurePatternsLatest.put("), "no mutable per-timeframe map remains");
            int hello0 = src.indexOf("java.util.Map<String, PatternEntry> snap = marketStructurePatternsSnapshot.get();   // ONE snapshot for all three");
            assertTrue(hello0 > 0, "the hello captures the snapshot once");
        } finally {
            System.clearProperty("GATEWAY_MARKET_STRUCTURE_PATTERNS_ENABLED");
        }
    }

    @Test
    void helloCarriesThePatternsFieldOnlyWhenEnabledWithOneEntryPerTimeframe() throws Exception {
        var off = service();
        assertFalse(off.cvdHelloJson().contains("\"patterns\""), "flag off: the field is ABSENT");
        System.setProperty("GATEWAY_MARKET_STRUCTURE_PATTERNS_ENABLED", "true");
        try {
            var on = service();
            assertTrue(on.sendsCvdHello(), "the hello is sent for the patterns stream alone");
            assertTrue(on.cvdHelloJson().contains(",\"patterns\":{\"1m\":null,\"5m\":null,\"15m\":null}"), "flag on, nothing retained: explicit nulls");
            assertTrue(on.retainMarketStructurePatterns("5m", 100L, ok("5m", 100L)));
            assertTrue(on.cvdHelloJson().contains("\"5m\":" + ok("5m", 100L)), "the retained set rides VERBATIM");
            new ObjectMapper().readerFor(com.fasterxml.jackson.databind.JsonNode.class)
                    .with(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                    .readValue(on.cvdHelloJson());
        } finally {
            System.clearProperty("GATEWAY_MARKET_STRUCTURE_PATTERNS_ENABLED");
        }
    }

    @Test
    void liveConsumerBindsTheTopicBehindTheFlagAndCacheConsumerDoesNot() throws Exception {
        String src = source();
        int live = src.indexOf("private void runJsonStateLiveConsumer()");
        int cache = src.indexOf("private void runJsonStateCacheConsumer()");
        assertTrue(live > 0 && cache > 0);
        String binding = "topicEvents.put(settings.marketStructurePatternsTopic(), new TopicBinding(\"DATABENTO\", \"market-structure-patterns\"));";
        int at = src.indexOf(binding);
        assertTrue(at > 0, "the live consumer binds the topic");
        assertEquals(at, src.lastIndexOf(binding), "bound exactly once");
        int liveEnd = src.indexOf("\n    }\n", live);
        assertTrue(at > live && at < liveEnd, "the binding lives in the LIVE consumer");
        assertTrue(src.lastIndexOf("if (settings.marketStructurePatternsEnabled()) {", at) > live, "…behind the flag");
        int cacheEnd = src.indexOf("\n    }\n", cache);
        assertFalse(src.substring(cache, cacheEnd).contains("marketStructurePatternsTopic"), "the cache consumer never subscribes");
    }

    @Test
    void deliveryIsRawPassThroughValidatedAndRetainedBeforeBroadcast() throws Exception {
        String src = source();
        int raw = src.indexOf("private static boolean isRawPassThroughEvent(String event)");
        int rawEnd = src.indexOf("\n    }\n", raw);
        assertTrue(src.substring(raw, rawEnd).contains("\"market-structure-patterns\".equals(event)"), "never enriched");
        int branch = src.indexOf("if (\"market-structure-patterns\".equals(binding.event())) {");
        assertTrue(branch > 0);
        String body = src.substring(branch, src.indexOf("continue;", branch));
        int validate = body.indexOf("validateMarketStructurePatterns(json)");
        int retain = body.indexOf("retainMarketStructurePatterns(");
        int bcast = body.indexOf("broadcast(binding.event(), json)");
        assertTrue(validate > 0 && retain > validate && bcast > retain, "validate → retain → broadcast, in that order");
        assertTrue(body.contains("marketStructurePatternsDrops.incrementAndGet()"), "a rejected record is counted");
        assertTrue(branch < src.indexOf("String cacheKey = updateCache("), "the branch precedes the generic cache path");
        assertTrue(src.contains("evictMarketStructurePatternsTombstone(binding == null ? null : binding.event(), record);"),
                "the tombstone path is wired beside the structure one");
    }

    @Test
    void metricsCarryOnlyTheEnabledGaugeWhileOffAndTheCountersWhenOn() {
        var off = service();
        String m = off.metrics();
        assertTrue(m.contains("gateway_market_structure_patterns_enabled 0"));
        assertFalse(m.contains("gateway_market_structure_patterns_drops_total"), "OFF: no counter line, the metrics text is what it was");
        assertFalse(m.contains("gateway_market_structure_patterns_regressions_total"));
        System.setProperty("GATEWAY_MARKET_STRUCTURE_PATTERNS_ENABLED", "true");
        try {
            String on = service().metrics();
            assertTrue(on.contains("gateway_market_structure_patterns_enabled 1"));
            assertTrue(on.contains("gateway_market_structure_patterns_drops_total 0"));
            assertTrue(on.contains("gateway_market_structure_patterns_regressions_total 0"));
        } finally {
            System.clearProperty("GATEWAY_MARKET_STRUCTURE_PATTERNS_ENABLED");
        }
    }

    @Test
    void recoveryIsWiredLikeTheStructureRecordInTheLiveLoop() throws Exception {
        String src = source();
        int loop = src.indexOf("private void runLiveConsumerOnce(");
        int loopEnd = src.indexOf("\n    }\n", src.indexOf("finishMarketStructurePatternsRecovery(consumer, patternsRecovery);", loop));
        String body = src.substring(loop, loopEnd);
        assertTrue(body.contains("MarketStructurePatternsRecovery patternsRecovery = newMarketStructurePatternsRecovery();"), "end offsets captured on start");
        assertTrue(body.contains("patternsRecovery.end.putAll(marketStructurePatternsEndOffsets(consumer, partitions, topicEvents));"));
        assertTrue(body.contains("patternsRecovery.end.putAll(addedPatternsEnd);"), "…and on late adoption");
        int stage = body.indexOf("stageMarketStructurePatterns(patternsRecovery, record, json);");
        int evict = body.indexOf("evictMarketStructurePatternsTombstone(binding == null ? null : binding.event(), record);");
        assertTrue(stage > 0 && evict > stage, "while recovering, every record of the event (tombstones included) is STAGED before the live eviction path");
        assertTrue(body.contains("finishMarketStructurePatternsRecovery(consumer, patternsRecovery);"), "promotion runs every poll");
        int seek = src.indexOf("void seekMarketStructureSnapshot(");
        String seekBody = src.substring(seek, src.indexOf("\n    }\n", seek));
        assertTrue(seekBody.contains("\"market-structure-patterns\".equals(binding.event())"), "the seek-back covers the pattern topic");
    }
}

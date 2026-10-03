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
 * STRUCTURE relay (the Context Tape STRUCTURE layer): the `market-structure` frame is the sibling of
 * the U16 es-cvd-spx-levels path — opt-in, live-consumer only, raw pass-through, latest-record
 * retention replayed inside the hello as {@code structure}, allowlisted for per-session mode.
 */
class MarketStructureWiringTest {

    private static FeedGatewayService service() {
        return new FeedGatewayService(new GatewaySettings(), new ObjectMapper(), new HpsfGatewayViewMapper(), null);
    }

    private static String ok(long asOfMs) {
        return "{\"schemaVersion\":1,\"service\":\"market-structure\",\"state\":\"OK\",\"sessionDate\":\"2026-10-05\","
                + "\"asOfMs\":" + asOfMs + ",\"scheduleId\":\"2026-10-05.r0000000000\",\"lastClose\":6018.5,"
                + "\"levels\":[{\"levelId\":\"p5l\",\"kind\":\"SWING_LOW\",\"role\":\"SUPPORT\",\"price\":6011.2,"
                + "\"timeframe\":\"5m\",\"state\":\"ACTIVE\",\"sinceMs\":" + (asOfMs - 600000) + "}],"
                + "\"trend\":{\"1m\":{\"state\":\"UP\"},\"5m\":{\"state\":\"UP\"},\"15m\":{\"state\":\"NONE\"}},"
                + "\"selection\":{\"anchorBelow\":\"p5l\",\"obstacleAbove\":null},\"warning\":null,\"events\":[]}";
    }

    private static String source() throws Exception {
        return Files.readString(Path.of("src/main/java/app/feedgateway/FeedGatewayService.java"));
    }

    @Test
    void isOptInAndNamesItsOwnTopic() {
        GatewaySettings s = new GatewaySettings();
        assertFalse(s.marketStructureEnabled(), "OFF by default — the flag is the last rollout step");
        assertEquals("options.market-structure.levels", s.marketStructureTopic());
    }

    @Test
    void authModeAllowlistCarriesTheFrame() {
        assertTrue(FeedGatewayService.GLOBAL_BROADCAST_EVENTS.contains("market-structure"),
                "per-session (auth) mode would silently drop the frame without the allowlist entry");
    }

    @Test
    void boundaryAcceptsOnlyAMajorOneRecordOfThisServiceWithAnAsOfClock() {
        var s = service();
        assertEquals(1786900000000L, s.validateMarketStructure(ok(1786900000000L)));
        assertEquals(5L, s.validateMarketStructure(
                "{\"schemaVersion\":1,\"service\":\"market-structure\",\"state\":\"UNAVAILABLE\",\"reason\":\"WARMING\",\"asOfMs\":5}"));
        assertNull(s.validateMarketStructure(null));
        assertNull(s.validateMarketStructure("not json"));
        assertNull(s.validateMarketStructure("[1,2]"));
        assertNull(s.validateMarketStructure(ok(1).replace("\"schemaVersion\":1", "\"schemaVersion\":2")));
        assertNull(s.validateMarketStructure(ok(1).replace("\"schemaVersion\":1", "\"schemaVersion\":\"1\"")),
                "a textual version is not the integer the contract names");
        assertNull(s.validateMarketStructure(ok(1).replace("market-structure", "context-tape")));
        assertNull(s.validateMarketStructure(ok(1).replace("\"OK\"", "\"LIVE\"")));
        assertNull(s.validateMarketStructure(ok(1).replace("\"asOfMs\":1,", "\"asOfMs\":0,")));
        assertNull(s.validateMarketStructure(ok(1).replace("\"asOfMs\":1,", "\"asOfMs\":1.5,")));
        assertNull(s.validateMarketStructure(ok(1).replace("\"asOfMs\":1,", "")), "asOfMs is required");
        assertNull(s.validateMarketStructure(ok(1).replace("\"asOfMs\":1,", "\"asOfMs\":99999999999999999999,")),
                "an oversized integer is refused, never wrapped");
    }

    @Test
    void boundaryRefusesOversizeRecords() {
        var s = service();
        String padded = ok(7).replace("\"events\":[]", "\"events\":[],\"pad\":\"" + "x".repeat(FeedGatewayService.MARKET_STRUCTURE_MAX_BYTES) + "\"");
        assertNull(s.validateMarketStructure(padded));
    }

    @Test
    void retentionKeepsTheNewestAsOfAndRefusesARegression() {
        var s = service();
        assertTrue(s.retainMarketStructure(100L, ok(100L)));
        assertTrue(s.retainMarketStructure(200L, ok(200L)));
        assertFalse(s.retainMarketStructure(150L, ok(150L)), "an older fold never displaces a newer one");
        assertEquals(ok(200L), s.marketStructureLatestForTest().get());
        assertEquals(1L, s.marketStructureRegressionsForTest());
        assertTrue(s.retainMarketStructure(200L, ok(200L)), "an equal asOfMs is an idempotent republish");
    }

    @Test
    void onlyAKeyedTombstoneWithdrawsTheReplay() {
        var s = service();
        assertTrue(s.retainMarketStructure(100L, ok(100L)));
        s.evictMarketStructureTombstone("market-structure",
                new ConsumerRecord<>("options.market-structure.levels", 0, 1L, "OTHER", null));
        assertNotNull(s.marketStructureLatestForTest().get(), "a foreign-key tombstone is counted, not an erase");
        assertEquals(1L, s.marketStructureDropsForTest());
        s.evictMarketStructureTombstone("es-cvd-spx-levels",
                new ConsumerRecord<>("options.es-cvd-spx-levels", 0, 1L, FeedGatewayService.MARKET_STRUCTURE_KEY, null));
        assertNotNull(s.marketStructureLatestForTest().get(), "another event's tombstone never touches this retention");
        s.evictMarketStructureTombstone("market-structure",
                new ConsumerRecord<>("options.market-structure.levels", 0, 2L, FeedGatewayService.MARKET_STRUCTURE_KEY, null));
        assertNull(s.marketStructureLatestForTest().get());
        assertEquals(2L, s.marketStructureDropsForTest());
        assertTrue(s.retainMarketStructure(50L, ok(50L)), "after a withdrawal the baseline is erased: an older fold is accepted again");
    }

    @Test
    void helloCarriesTheStructureFieldOnlyWhenEnabled() throws Exception {
        var off = service();
        assertFalse(off.cvdHelloJson().contains("\"structure\""), "flag off: the field is ABSENT");
        assertFalse(off.sendsCvdHello());

        System.setProperty("GATEWAY_MARKET_STRUCTURE_ENABLED", "true");
        try {
            var on = service();
            assertTrue(on.sendsCvdHello(), "the hello is sent for the structure stream alone");
            assertTrue(on.cvdHelloJson().contains(",\"structure\":null"), "flag on, nothing retained: an explicit null");
            assertTrue(on.retainMarketStructure(100L, ok(100L)));
            assertTrue(on.cvdHelloJson().contains(",\"structure\":" + ok(100L)), "the retained record rides VERBATIM");
            new ObjectMapper().readTree(on.cvdHelloJson());   // still one well-formed hello object
        } finally {
            System.clearProperty("GATEWAY_MARKET_STRUCTURE_ENABLED");
        }
    }

    @Test
    void liveConsumerBindsTheTopicBehindTheFlagAndCacheConsumerDoesNot() throws Exception {
        String src = source();
        int live = src.indexOf("private void runJsonStateLiveConsumer()");
        int cache = src.indexOf("private void runJsonStateCacheConsumer()");
        assertTrue(live > 0 && cache > 0);
        String binding = "topicEvents.put(settings.marketStructureTopic(), new TopicBinding(\"DATABENTO\", \"market-structure\"));";
        int at = src.indexOf(binding);
        assertTrue(at > 0, "the live consumer binds the topic");
        assertEquals(at, src.lastIndexOf(binding), "bound exactly once");
        int liveEnd = src.indexOf("\n    }\n", live);
        assertTrue(at > live && at < liveEnd, "the binding lives in the LIVE consumer");
        assertTrue(src.lastIndexOf("if (settings.marketStructureEnabled()) {", at) > live, "…behind the flag");
        int cacheEnd = src.indexOf("\n    }\n", cache);
        assertFalse(src.substring(cache, cacheEnd).contains("marketStructureTopic"), "the cache consumer never subscribes");
    }

    @Test
    void deliveryIsRawPassThroughValidatedAndRetainedBeforeBroadcast() throws Exception {
        String src = source();
        int raw = src.indexOf("private static boolean isRawPassThroughEvent(String event)");
        int rawEnd = src.indexOf("\n    }\n", raw);
        assertTrue(src.substring(raw, rawEnd).contains("\"market-structure\".equals(event)"), "never enriched");
        int branch = src.indexOf("if (\"market-structure\".equals(binding.event())) {");
        assertTrue(branch > 0);
        String body = src.substring(branch, src.indexOf("continue;", branch));
        int validate = body.indexOf("validateMarketStructure(json)");
        int retain = body.indexOf("retainMarketStructure(asOf, json)");
        int bcast = body.indexOf("broadcast(binding.event(), json)");
        assertTrue(validate > 0 && retain > validate && bcast > retain, "validate → retain → broadcast, in that order");
        assertTrue(body.contains("marketStructureDrops.incrementAndGet()"), "a rejected record is counted");
        assertTrue(branch < src.indexOf("String cacheKey = updateCache("), "the branch precedes the generic cache path");
        assertTrue(src.contains("evictMarketStructureTombstone(binding == null ? null : binding.event(), record);"),
                "the tombstone path is wired beside the levels one");
    }

    @Test
    void metricsCarryTheStreamNames() {
        var s = service();
        String m = s.metrics();
        assertTrue(m.contains("gateway_market_structure_enabled 0"));
        assertTrue(m.contains("gateway_market_structure_drops_total 0"));
        assertTrue(m.contains("gateway_market_structure_regressions_total 0"));
    }
}

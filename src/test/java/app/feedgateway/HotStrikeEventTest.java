package app.feedgateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * §4.4 hot-strike forward contract: bound on BOTH consumer registrations, cached
 * per symbol from the RAW record value (verbatim — never the enrichJson
 * reserialization), last-value-wins, 12h session cache window, and replayed on
 * connect via the unconditional strike-cluster idiom.
 */
class HotStrikeEventTest {

    private static FeedGatewayService service() {
        return new FeedGatewayService(new GatewaySettings(), new ObjectMapper(),
                new HpsfGatewayViewMapper(), null);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, String> hotStrikes(FeedGatewayService service) throws Exception {
        Field f = FeedGatewayService.class.getDeclaredField("hotStrikes");
        f.setAccessible(true);
        return (Map<String, String>) f.get(service);
    }

    @Test
    void cacheConsumerStoresTheRawEnvelopeVerbatimKeyedBySymbol() throws Exception {
        FeedGatewayService service = service();
        Method updateCache = FeedGatewayService.class.getDeclaredMethod("updateCache",
                Class.forName("app.feedgateway.FeedGatewayService$TopicBinding"),
                ConsumerRecord.class, String.class);
        updateCache.setAccessible(true);
        var bindingCtor = Class.forName("app.feedgateway.FeedGatewayService$TopicBinding")
                .getDeclaredConstructors()[0];
        bindingCtor.setAccessible(true);
        Object binding = bindingCtor.newInstance("DATABENTO", "hot-strike");
        String raw = "{\"schemaVersion\":1,\"row\":{\"symbol\":\"SPX\",\"strike\":7465.0}}";
        // json argument deliberately DIFFERS from the record value: the verbatim
        // contract requires the RAW value to win, not the enriched reserialization.
        ConsumerRecord<String, Object> record =
                new ConsumerRecord<>("signal-follower.hot-strike", 0, 0, "SPX", raw);
        updateCache.invoke(service, binding, record, "{\"enriched\":true}");
        assertEquals(raw, hotStrikes(service).get("SPX"),
                "cache path must store the RAW record value keyed by symbol");
        // last-value-wins per symbol
        String newer = "{\"schemaVersion\":1,\"row\":{\"symbol\":\"SPX\",\"strike\":7470.0}}";
        updateCache.invoke(service, binding, new ConsumerRecord<>(
                "signal-follower.hot-strike", 0, 1, "SPX", newer), "{\"enriched\":true}");
        assertEquals(newer, hotStrikes(service).get("SPX"));
        assertEquals(1, hotStrikes(service).size());
    }

    @Test
    void expiredHotStrikeIsPurgedByTheSessionWindow() throws Exception {
        FeedGatewayService service = service();
        Field cacheTimes = FeedGatewayService.class.getDeclaredField("cacheEventTimes");
        cacheTimes.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, Long> times = (Map<String, Long>) cacheTimes.get(service);
        hotStrikes(service).put("SPX", "{\"schemaVersion\":1,\"row\":{}}");
        // yesterday's row: older than the 12h session window
        times.put("hot-strike:SPX", System.currentTimeMillis() - 24L * 3600_000);
        Method purge = FeedGatewayService.class.getDeclaredMethod("purgeExpiredCache",
                long.class);
        purge.setAccessible(true);
        purge.invoke(service, System.currentTimeMillis());
        assertTrue(hotStrikes(service).isEmpty(),
                "a stale hot-strike must be evicted, never replayed indefinitely");
    }

    @Test
    void sourceContractsArePinned() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/app/feedgateway/FeedGatewayService.java"));
        // bound on BOTH consumer registrations
        int bindings = source.split(
                "topicEvents\\.put\\(settings\\.hotStrikeTopic\\(\\), new TopicBinding\\(\"DATABENTO\", \"hot-strike\"\\)\\);", -1).length - 1;
        assertEquals(2, bindings, "hot-strike must bind on BOTH consumer registrations");
        // live branch forwards the RAW value, never the enriched json
        assertTrue(source.contains(
                "String hotRaw = avro ? avroJson(record.value()) : stringJson(record.value());"),
                "live branch must broadcast the raw record value (verbatim, §4.4)");
        // freshness-gated replay-on-connect (12h session window)
        assertTrue(source.contains("isCacheFresh(\"hot-strike:\" + hotEntry.getKey(), hotNowMs)"),
                "hot-strike replay must be gated by the session freshness window");
        // auth-mode delivery: broadcast() drops events outside the global allowlist
        assertTrue(source.split("GLOBAL_BROADCAST_EVENTS = Set.of\\(")[1]
                        .substring(0, 2000).contains("\"hot-strike\","),
                "hot-strike must be allowlisted for per-session (auth) broadcast");
        // delivered in BOTH connect modes: per-session AND legacy
        assertEquals(2, source.split("replayHotStrikeCached\\(session\\);", -1).length - 1,
                "hot-strike must replay on BOTH the legacy and per-session connect paths");
        // session-length cache window + seek-back
        assertTrue(source.contains(
                "return CachePolicy.expiring(settings.hotStrikeTtlMs());"),
                "hot-strike needs the 12h session cache window, not the 15-min TTL");
    }
}

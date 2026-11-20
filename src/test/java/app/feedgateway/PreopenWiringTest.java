package app.feedgateway;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.feedgateway.readable.PreopenSource;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The pre-open tap, DRIVEN rather than asserted about.
 *
 * <p>The lesson this file is built on is written in {@code FeedGatewayService} beside the
 * {@code gamma-effort} cache case: that event had four source pins asserting the relay was wired,
 * all four passed, and every record was silently dropped, because {@code updateCache} ends in
 * {@code default -> { return null; }} and the broadcast guard it needed could never pass. A source
 * scan cannot see what a record does.
 *
 * <p>So the event name is READ OUT of the registration line and then used to drive the real
 * {@code updateCache}. Renaming the event in the registration, or in the cache case, or in either
 * alone, fails this test — which a literal spelled twice in a test would not catch.
 */
class PreopenWiringTest {

    private static final String SERVICE = "src/main/java/app/feedgateway/FeedGatewayService.java";

    /** A record exactly as published on options.databento.gex.strike.preopen. */
    private static final String PREOPEN_JSON =
            "{\"symbol\":\"SPX\",\"expiry\":\"20261119\",\"strike\":7405.0,"
                    + "\"netGex\":2.6703191198E8,\"callGex\":3.4447187646E8,\"putGex\":-7.743996448E7,"
                    + "\"gammaSign\":\"POSITIVE\",\"timeframe\":\"IBKR_PREOPEN\",\"source\":\"IBKR\","
                    + "\"updatedAt\":\"2026-11-19T14:24:59.906Z\",\"staleAfterMs\":300000,"
                    + "\"canonicalSpot\":7398.75341796875,"
                    + "\"historyUpdatedAt\":\"2026-11-19T14:24:59.906Z\"}";

    /** The event name as the REGISTRATION spells it, and how many sites register it. */
    private static String[] registeredEvent() throws Exception {
        String src = Files.readString(Path.of(SERVICE));
        Matcher m = Pattern.compile(
                "topicEvents\\.put\\(settings\\.gexPreopenTopic\\(\\),\\s*"
                        + "new TopicBinding\\(\"[A-Z]+\",\\s*\"([a-z-]+)\"\\)\\)")
                .matcher(src);
        String event = null;
        int sites = 0;
        while (m.find()) {
            if (event == null) {
                event = m.group(1);
            } else {
                assertEquals(event, m.group(1),
                        "both consumers must register the pre-open topic under the SAME event name");
            }
            sites++;
        }
        assertNotNull(event, "the pre-open topic is not registered at all");
        return new String[] {event, String.valueOf(sites)};
    }

    private static PreopenSource preopenOf(FeedGatewayService service) throws Exception {
        Field f = FeedGatewayService.class.getDeclaredField("readableBoard");
        f.setAccessible(true);
        Object renderer = f.get(service);
        Method m = renderer.getClass().getMethod("preopenSource");
        return (PreopenSource) m.invoke(renderer);
    }

    /** One record through the REAL ingestion path; returns the cache key it produced. */
    private static String ingest(FeedGatewayService service, String event, String json)
            throws Exception {
        Class<?> bindingClass = Class.forName("app.feedgateway.FeedGatewayService$TopicBinding");
        Constructor<?> ctor = bindingClass.getDeclaredConstructor(String.class, String.class);
        ctor.setAccessible(true);
        Object binding = ctor.newInstance("DATABENTO", event);
        Method updateCache = FeedGatewayService.class.getDeclaredMethod(
                "updateCache", bindingClass, ConsumerRecord.class, String.class);
        updateCache.setAccessible(true);
        ConsumerRecord<String, Object> record = new ConsumerRecord<>(
                "options.databento.gex.strike.preopen", 0, 1L, "SPX|20261119|7405", null);
        return (String) updateCache.invoke(service, binding, record, json);
    }

    private static FeedGatewayService service() {
        return new FeedGatewayService(new GatewaySettings(), new ObjectMapper(),
                new HpsfGatewayViewMapper(), null);
    }

    @Test
    @DisplayName("a pre-open record driven through updateCache reaches the readable page's store")
    void theRecordActuallyReachesTheStore() throws Exception {
        String event = registeredEvent()[0];
        FeedGatewayService service = service();
        PreopenSource store = preopenOf(service);
        assertEquals(0, store.observed(), "nothing before the record");

        ingest(service, event, PREOPEN_JSON);

        assertEquals(1, store.observed(),
                "the record must reach the store through the real path, under the event name the "
                        + "registration itself uses");
        assertEquals(1, store.strikesHeld());
        assertNotNull(store.board(1_795_088_699_906L),
                "and the store must be able to build a board from it");
    }

    @Test
    @DisplayName("the tap caches nothing, so the record can never satisfy a broadcast guard")
    void theRecordIsNeverCachedAndSoNeverBroadcast() throws Exception {
        String event = registeredEvent()[0];
        assertNull(ingest(service(), event, PREOPEN_JSON),
                "updateCache must return null for this event: every standalone broadcast branch is "
                        + "guarded by cacheKey != null, so a null here is what keeps the pre-open "
                        + "board off every websocket, ui-batch row and /api cache");
    }

    @Test
    @DisplayName("both consumers register it, so a restart on either path still feeds the page")
    void bothConsumersRegisterIt() throws Exception {
        assertEquals("2", registeredEvent()[1],
                "the cache consumer and the live consumer must both bind the pre-open topic; the "
                        + "tap is idempotent, so a double delivery costs nothing and a single "
                        + "binding would leave the page blind whenever that consumer was behind");
    }

    @Test
    @DisplayName("the topic is the one the platform actually publishes")
    void theTopicIsTheRealOne() {
        assertEquals("options.databento.gex.strike.preopen",
                new GatewaySettings().gexPreopenTopic(),
                "databento-gex-history projects the pre-open records onto this topic and the "
                        + "context tape already consumes it");
    }

    @Test
    @DisplayName("an unreadable pre-open record is counted, and never breaks the ingestion path")
    void anUnreadableRecordIsCountedNotFatal() throws Exception {
        String event = registeredEvent()[0];
        FeedGatewayService service = service();
        PreopenSource store = preopenOf(service);

        assertNull(ingest(service, event, "not json at all"));
        assertEquals(0, store.observed());
        assertEquals(1, store.unreadable(), "the refusal is counted rather than thrown");

        ingest(service, event, PREOPEN_JSON);
        assertEquals(1, store.observed(), "and the path still works afterwards");
    }
}

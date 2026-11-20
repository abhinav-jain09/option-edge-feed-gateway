package app.feedgateway;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.feedgateway.readable.StrikeDeltaSource;
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
 * <b>C-120's tap, DRIVEN rather than asserted about</b> &mdash; the same discipline as
 * {@code PreopenWiringTest}, for the same reason written beside the {@code gamma-effort} cache
 * case: that event had four source pins saying the relay was wired, all four passed, and every
 * record was dropped. A source scan cannot see what a record does.
 *
 * <p><b>And the gate this tap needs.</b> The gateway binds the event {@code gex-by-strike} to FOUR
 * topics: the Databento live one, the Databento history one, and the IBKR live and history ones. A
 * tap that took the event rather than the topic would put a history row, or another producer's
 * row, on a live strike the owner is trading, with nothing on the page to say where it came from.
 * So the three non-live topics are driven through the same path and must leave the store empty.
 */
class C120DeltaWiringTest {

    private static final String SERVICE = "src/main/java/app/feedgateway/FeedGatewayService.java";

    /** A real record off options.databento.gex.strike, trimmed to what the tap reads. */
    private static final String GEX_JSON = "{\"schemaVersion\":10,"
            + "\"messageType\":\"GEX_STRIKE_SNAPSHOT\",\"source\":\"DATABENTO\","
            + "\"symbol\":\"SPX\",\"expiry\":\"20261120\",\"strike\":7275.0,"
            + "\"callDelta\":0.05545327388772792,\"putDelta\":-0.9702893751050274,"
            + "\"callInvalidReason\":\"\",\"putInvalidReason\":\"\","
            + "\"updatedAtMs\":1795188021183}";

    /**
     * The event name as the REGISTRATION spells it for the live Databento strike topic.
     *
     * <p>Read out of the source rather than spelled twice here, so renaming the event in the
     * registration or in the cache case alone fails this test.
     */
    private static String registeredEvent() throws Exception {
        String src = Files.readString(Path.of(SERVICE));
        Matcher m = Pattern.compile(
                "topicEvents\\.put\\(settings\\.databentoGexTopic\\(\\),\\s*"
                        + "new TopicBinding\\(\"[A-Z]+\",\\s*\"([a-z-]+)\"\\)\\)")
                .matcher(src);
        String event = null;
        int sites = 0;
        while (m.find()) {
            if (event == null) {
                event = m.group(1);
            } else {
                assertEquals(event, m.group(1),
                        "every consumer must register the live strike topic under the SAME event");
            }
            sites++;
        }
        assertNotNull(event, "the live Databento strike topic is not registered at all");
        assertTrue(sites >= 2, "both consumers register it; found " + sites);
        return event;
    }

    private static StrikeDeltaSource storeOf(FeedGatewayService service) throws Exception {
        Field f = FeedGatewayService.class.getDeclaredField("readableBoard");
        f.setAccessible(true);
        Object renderer = f.get(service);
        Method m = renderer.getClass().getMethod("strikeDeltas");
        return (StrikeDeltaSource) m.invoke(renderer);
    }

    /** One record through the REAL ingestion path, on the topic named. */
    private static String ingest(FeedGatewayService service, String event, String topic,
            String json) throws Exception {
        Class<?> bindingClass = Class.forName("app.feedgateway.FeedGatewayService$TopicBinding");
        Constructor<?> ctor = bindingClass.getDeclaredConstructor(String.class, String.class);
        ctor.setAccessible(true);
        Object binding = ctor.newInstance("DATABENTO", event);
        Method updateCache = FeedGatewayService.class.getDeclaredMethod(
                "updateCache", bindingClass, ConsumerRecord.class, String.class);
        updateCache.setAccessible(true);
        ConsumerRecord<String, Object> record =
                new ConsumerRecord<>(topic, 0, 1L, "SPX|20261120|7275", null);
        return (String) updateCache.invoke(service, binding, record, json);
    }

    private static FeedGatewayService service() {
        return new FeedGatewayService(new GatewaySettings(), new ObjectMapper(),
                new HpsfGatewayViewMapper(), null);
    }

    @Test
    @DisplayName("C-120: a live strike record driven through updateCache reaches the delta store")
    void theRecordActuallyReachesTheStore() throws Exception {
        FeedGatewayService service = service();
        StrikeDeltaSource store = storeOf(service);
        assertEquals(0, store.strikesHeld(), "nothing before the record");

        GatewaySettings settings = new GatewaySettings();
        String key = ingest(service, registeredEvent(), settings.databentoGexTopic(), GEX_JSON);

        assertNotNull(key, "the cache case must still return its key");
        assertEquals(1, store.strikesHeld(),
                "THE POINT: the record reached the readable page's delta store through the real "
                        + "ingestion path, not through a method a test called directly");
        assertEquals(0.05545327388772792, store.at("20261120", 727_500L).call(), 0.0,
                "and unchanged, to the last digit (C-103)");
    }

    @Test
    @DisplayName("C-120: the three OTHER topics on the same event reach the store with nothing")
    void onlyTheLiveDatabentoTopicFeedsTheStore() throws Exception {
        GatewaySettings settings = new GatewaySettings();
        String event = registeredEvent();
        for (String topic : new String[] {
                settings.databentoGexHistoryTopic(),
                settings.ibkrUnusualWhalesGexTopic(),
                settings.ibkrUnusualWhalesGexHistoryTopic()}) {
            FeedGatewayService service = service();
            StrikeDeltaSource store = storeOf(service);
            ingest(service, event, topic, GEX_JSON);
            assertEquals(0, store.strikesHeld(),
                    "a record on " + topic + " must not put a delta on a LIVE strike the owner is "
                            + "trading: one event, four topics, and only one of them is today's "
                            + "live chain");
            assertNull(store.at("20261120", 727_500L));
        }
    }
}

package app.feedgateway;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

/**
 * The gamma-effort relay: one dev-only JSON topic forwarded verbatim to the browser.
 *
 * <p>options.spx.gamma-effort.current carries SPX dealer gamma measured against ES contracts
 * traded -- the forced hedge a strike implies against the aggressive flow pushing into it -- and
 * the evidence that weakens it: the gamma map's age, the refusal tally, and the calibration every
 * figure was computed under.
 *
 * <p><b>What these assertions are worth.</b> The settings checks RUN the real code. The wiring
 * checks are source pins, which is this module's established pattern and is also the weakest kind
 * of evidence there is: a pin proves a branch is written, never that a record reaches a socket.
 * Nothing here can prove delivery. That is established by connecting to the running gateway on dev
 * and watching the event arrive, and it is the only proof that counts.
 *
 * <p>They are matched by what the expressions CONTAIN rather than by their exact tails. The
 * gamma-zone equivalents pinned their closing tokens and broke the moment this event was appended
 * to the same branches, which said nothing about whether gamma-zone was still wired.
 *
 * <p><b>And the warning above came true.</b> Every pin in this file passed from the day the relay
 * merged, and not one gamma-effort record ever reached a browser. {@code updateCache} had no
 * {@code case "gamma-effort"}, so it fell to {@code default -> return null}; the standalone
 * broadcast branch those pins assert is guarded by {@code cacheKey != null} and could therefore
 * never run. {@code GLOBAL_BROADCAST_EVENTS} was missing it too, which would have dropped every
 * frame a second time on any authenticated socket -- i.e. dev and prod. The page sat on "Waiting
 * for the first reading" for a day while four tests reported the relay wired.
 *
 * <p>The tests below this line therefore DRIVE the real code instead of reading it. Each one fails
 * on the commit before this one.
 */
class GammaEffortWiringTest {

    private static String source() throws IOException {
        return Files.readString(Path.of("src/main/java/app/feedgateway/FeedGatewayService.java"));
    }

    @Test
    void itIsForwardedVERBATIMAndNotENRICHED() throws IOException {
        // THE BUG THIS PINS. The record carries its OWN sessionDate. Without this, enrichJson
        // stamps the gateway's sessionDate/source over the producer's fields, and a page reading
        // the session off the record is told a session the service never measured. The gamma-zone
        // wiring test caught this omission on the first build of this relay.
        assertTrue(source().contains("|| \"gamma-effort\".equals(event)"),
                "gamma-effort must be in isRawPassThroughEvent");
    }

    @Test
    void itBroadcastsSTANDALONEAndNotThroughTheChainSelection() throws IOException {
        // The active selection names an options chain (source, symbol, expiry). This names a
        // FUTURES session, so matchesActiveSelection would drop every record and the page would
        // stay empty with nothing in the logs to say why.
        String source = source();
        assertTrue(source.contains("|| \"gamma-effort\".equals(binding.event())) {"),
                "the standalone broadcast branch must carry gamma-effort");
        int standalone = source.indexOf("|| \"gamma-effort\".equals(binding.event())) {");
        int selectionGated = source.indexOf("&& shouldForward(binding, forwardJson, record,");
        assertTrue(standalone > 0 && selectionGated > standalone,
                "the standalone branch must come BEFORE the selection-gated one, or it never runs");
    }

    @Test
    void itGatesCatchUpReadinessLikeTheOtherSourceIndependentFeeds() throws IOException {
        // Bound under DATABENTO by convention only. With another source selected it must still gate
        // readiness, or the cache reads caught up over a session it has not read -- and the page is
        // handed a hello built from nothing.
        assertTrue(source().contains("|| \"gamma-effort\".equals(binding.event())\n"),
                "gamma-effort must gate catch-up");
    }

    @Test
    void itIsBoundOnTheJSONConsumersAndNotTheAvroOnes() throws IOException {
        // Binding a JSON topic to the Avro consumers COMPILES, logs a deserialization error per
        // record, and leaves the page empty. That is where the gamma-zone relay went first.
        String source = source();
        assertTrue(source.contains("topicEvents.put(settings.gammaEffortTopic(), "
                        + "new TopicBinding(\"DATABENTO\", \"gamma-effort\"));"),
                "the binding must exist");
        assertEquals(2, source.split("settings\\.gammaEffortTopic\\(\\), new TopicBinding", -1).length - 1,
                "both JSON binding sites must carry it, as they do for gamma-zone");
    }

    @Test
    void theKillSwitchIsDarkSafeOff() {
        // Default off, like every integration added here: a flag that defaults ON ships a
        // subscription nobody asked for to whatever environment picks up the image next.
        assertFalse(new GatewaySettings().gammaEffortEnabled());
    }

    @Test
    void theCacheTtlIsOneSessionsHorizon() {
        // Long enough that an intra-day reconnect is handed the live session; short enough that a
        // reconnect the next morning is not handed yesterday's as though it were current.
        assertEquals(12L * 60L * 60L * 1000L, new GatewaySettings().gammaEffortTtlMs());
    }

    @Test
    void theTopicIsTheDECLAREDOneAndOverridableOnlyByItsOwnKey() {
        assertEquals("options.spx.gamma-effort.current", new GatewaySettings().gammaEffortTopic());
    }

    /* ----------------------------------------------- what the pins above could not see */

    private static final String TOPIC = "options.spx.gamma-effort.current";

    /** The record key the service actually produces: sessionDate|symbol|timeframe. */
    private static final String KEY = "2026-11-13|ES.v.0|1m";

    /**
     * What updateCache stores it under. Every event except pace/pace-rank gets the binding's
     * SOURCE prefixed generically, so the map key is not the record key -- gamma-zone's entries
     * carry the same prefix. Asserted rather than assumed: the first draft of this test expected
     * the bare record key and failed here, which is the whole reason these tests drive the code.
     */
    private static final String CACHE_KEY = "DATABENTO|" + KEY;

    /** Trimmed to the fields the gateway itself reads; the relay forwards the rest verbatim. */
    private static final String RECORD =
            "{\"schema\":\"gamma-effort.v1\",\"sessionDate\":\"2026-11-13\",\"symbol\":\"ES.v.0\","
            + "\"timeframe\":\"1m\",\"asOfMs\":1794572704425,\"spotCents\":761000,"
            + "\"gammaMap\":{\"present\":false,\"asOfMs\":null,\"strikes\":0},"
            + "\"scalp\":{\"hotStrike\":{\"bandLowCents\":759500,\"volumeContracts\":18115}}}";

    private static FeedGatewayService service() {
        return new FeedGatewayService(new GatewaySettings(), new ObjectMapper(), new HpsfGatewayViewMapper(), null);
    }

    /** Feed one record through the REAL ingestion path and return the cache key it produced. */
    private static String ingest(FeedGatewayService service, String json) throws Exception {
        Class<?> bindingClass = Class.forName("app.feedgateway.FeedGatewayService$TopicBinding");
        Constructor<?> ctor = bindingClass.getDeclaredConstructor(String.class, String.class);
        ctor.setAccessible(true);
        Object binding = ctor.newInstance("DATABENTO", "gamma-effort");
        Method updateCache = FeedGatewayService.class.getDeclaredMethod(
                "updateCache", bindingClass, ConsumerRecord.class, String.class);
        updateCache.setAccessible(true);
        ConsumerRecord<String, Object> record = new ConsumerRecord<>(TOPIC, 0, 1L, KEY, null);
        return (String) updateCache.invoke(service, binding, record, json);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, String> cacheOf(FeedGatewayService service) throws Exception {
        Field f = FeedGatewayService.class.getDeclaredField("gammaEfforts");
        f.setAccessible(true);
        return (Map<String, String>) f.get(service);
    }

    @Test
    void aRecordIsCACHEDSoTheBroadcastGuardCanPass() throws Exception {
        // THE bug. The standalone branch runs only `if (cacheKey != null && cacheCaughtUpFlag)`.
        // A null key here is not a cache miss -- it is the whole relay, silently off, with no
        // droppedBy* counter incremented and nothing in the log to say so.
        FeedGatewayService service = service();
        String key = ingest(service, RECORD);
        assertNotNull(key, "updateCache returned null, so the broadcast guard can never pass");
        assertEquals(CACHE_KEY, key, "source-prefixed record key, exactly as gamma-zone is keyed");
        assertEquals(RECORD, cacheOf(service).get(CACHE_KEY), "the record must be cached VERBATIM");
    }

    @Test
    void aSecondRecordREPLACESTheFirstForTheSameSession() throws Exception {
        // One live session is one entry. The service publishes one record per folded bar, ~390 a
        // session; a cache that appended would hold the day.
        FeedGatewayService service = service();
        ingest(service, RECORD);
        String later = RECORD.replace("\"spotCents\":761000", "\"spotCents\":761500");
        ingest(service, later);
        assertEquals(1, cacheOf(service).size(), "last-value-wins, one entry per session");
        assertEquals(later, cacheOf(service).get(CACHE_KEY), "the newer reading must win");
    }

    @Test
    void theAuthenticatedBroadcastAllowlistCarriesIt() {
        // Without this entry broadcast() drops every frame on a per-session (auth) socket while
        // forwardedEvents still increments -- the metric reads "delivered" and no socket saw one.
        // dev and prod are both authenticated, so this alone would have kept the page empty.
        assertTrue(FeedGatewayService.GLOBAL_BROADCAST_EVENTS.contains("gamma-effort"),
                "gamma-effort must be allowlisted for per-session broadcast");
    }

    @Test
    void aPageConnectingMidSessionIsHandedTheLastReading() throws Exception {
        // The service publishes once per folded bar. Without a replay on connect a page that opens
        // at 10:00:05 shows "Waiting for the first reading" until 10:01 -- and shows it for ever if
        // the session has already ended.
        FeedGatewayService service = service();
        ingest(service, RECORD);

        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn("test-session");
        when(session.isOpen()).thenReturn(true);

        Method replay = FeedGatewayService.class.getDeclaredMethod(
                "replayGammaZoneCached", WebSocketSession.class);
        replay.setAccessible(true);
        replay.invoke(service, session);

        ArgumentCaptor<TextMessage> sent = ArgumentCaptor.forClass(TextMessage.class);
        verify(session, atLeastOnce()).sendMessage(sent.capture());
        boolean delivered = sent.getAllValues().stream()
                .map(TextMessage::getPayload)
                .anyMatch(payload -> payload.contains("\"gamma-effort\"") && payload.contains("18115"));
        assertTrue(delivered, "the cached reading must be replayed to a connecting socket");
    }

    @Test
    void anEvictedEntryLeavesItsOWNMapAndNotAnotherGammaMap() throws Exception {
        // removeCacheEntry dispatches on a versionKey PREFIX. A missing arm leaves the record in
        // memory for ever; an arm in the wrong place empties a sibling map instead.
        FeedGatewayService service = service();
        ingest(service, RECORD);
        Method remove = FeedGatewayService.class.getDeclaredMethod("removeCacheEntry", String.class);
        remove.setAccessible(true);
        remove.invoke(service, "gamma-effort:" + CACHE_KEY);
        assertTrue(cacheOf(service).isEmpty(), "the entry must be evicted from gammaEfforts");
    }
}

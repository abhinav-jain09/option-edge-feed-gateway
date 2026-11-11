package app.feedgateway;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * The gamma-zone relay: two dev-only JSON topics forwarded verbatim to the browser.
 *
 * <p>options.spx.gamma-zone.current is the measurement -- the price at which the session's traded
 * volume balances, the stretch, the projected edges, and the evidence that WEAKENS the reading.
 * options.spx.gamma-zone.profile is the session's whole volume profile, which is what answers "how
 * many contracts traded between these two strikes"; the measurement cannot, because it carries the
 * ratio curve for +/-30 points and a level count.
 *
 * <p><b>What these assertions are worth.</b> The settings checks RUN the real code. The wiring
 * checks are source pins, which is this module's established pattern and is also the weakest kind
 * of evidence there is: a pin proves a branch is written, never that a record reaches a socket.
 * Nothing here can prove delivery. That is established by connecting to the running gateway on dev
 * and watching the event arrive, and it is the only proof that counts.
 */
class GammaZoneWiringTest {

    private static String source() throws IOException {
        return Files.readString(Path.of("src/main/java/app/feedgateway/FeedGatewayService.java"));
    }

    @Test
    void theTopicsDefaultToTheNamesTheServiceActuallyPUBLISHESTo() {
        GatewaySettings settings = new GatewaySettings();
        assertEquals("options.spx.gamma-zone.current", settings.gammaZoneTopic());
        assertEquals("options.spx.gamma-zone.profile", settings.gammaZoneProfileTopic());
    }

    @Test
    void theRelayIsOFFUntilSomebodyTurnsItOn() {
        // Dark-safe, like the navigation integration beside it: a gateway rolled with this code and
        // no flag behaves exactly as it did before.
        assertFalse(new GatewaySettings().gammaZoneEnabled());
    }

    @Test
    void bothTopicsAreBoundInBOTHConsumerSetups() throws IOException {
        // The cache consumer fills the per-session state a connecting page is handed; the live one
        // broadcasts. A binding present in only one of them is the defect that looks like a working
        // relay until somebody reloads the page -- or until nobody is connected when the record
        // arrives.
        String source = source();
        assertEquals(2, count(source,
                "topicEvents.put(settings.gammaZoneTopic(), new TopicBinding(\"DATABENTO\", \"gamma-zone\"));"));
        assertEquals(2, count(source,
                "topicEvents.put(settings.gammaZoneProfileTopic(), new TopicBinding(\"DATABENTO\", \"gamma-zone-profile\"));"));
    }

    @Test
    void bothEventsAreForwardedVERBATIM() throws IOException {
        // The service publishes no signal and names no level to sell. The gateway must not be the
        // first thing in the chain that implies one by reshaping the record -- and enriching would
        // re-serialise the 9 KB profile on every broadcast for two fields nobody reads.
        String source = source();
        assertTrue(source.contains("return \"gamma-zone\".equals(event) || \"gamma-zone-profile\".equals(event)"),
                "both events must be in isRawPassThroughEvent");
    }

    @Test
    void theyBroadcastSTANDALONEAndNotThroughTheChainSelection() throws IOException {
        // The active selection names an options chain (source, symbol, expiry). The gamma zone names
        // a FUTURES session, so matchesActiveSelection would drop every record and the page would
        // stay empty with nothing in the logs to say why.
        String source = source();
        assertTrue(source.contains("} else if (\"gamma-zone\".equals(binding.event())\n"
                        + "                            || \"gamma-zone-profile\".equals(binding.event())) {"),
                "the standalone broadcast branch must exist, before the shouldForward branch");
        int standalone = source.indexOf("|| \"gamma-zone-profile\".equals(binding.event())) {");
        int selectionGated = source.indexOf("&& shouldForward(binding, forwardJson, record,");
        assertTrue(standalone > 0 && selectionGated > standalone,
                "the standalone branch must come BEFORE the selection-gated one, or it never runs");
    }

    @Test
    void theyAreBoundOnTheJSONConsumersAndNOTTheAvroOnes() throws IOException {
        // Both records are JSON on the wire. Bound to the Avro consumers -- which is where this
        // first went -- GatewayAvroDeserializer throws on every record and the relay never reaches
        // stringJson, updateCache or a socket. It compiles and the page stays empty.
        String source = source();
        int avroCache = source.indexOf("private void runAvroCacheConsumer()");
        int jsonCache = source.indexOf("private void runJsonStateCacheConsumer()");
        int avroLive = source.indexOf("private void runAvroLiveConsumer()");
        int jsonLive = source.indexOf("private void runJsonStateLiveConsumer()");
        assertTrue(avroCache > 0 && jsonCache > avroCache && avroLive > jsonCache && jsonLive > avroLive,
                "the four consumer methods must be in the order this test assumes");
        for (int at = source.indexOf("settings.gammaZoneTopic()"); at >= 0;
                at = source.indexOf("settings.gammaZoneTopic()", at + 1)) {
            boolean inJsonCache = at > jsonCache && at < avroLive;
            boolean inJsonLive = at > jsonLive;
            assertTrue(inJsonCache || inJsonLive,
                    "a gamma-zone binding sits outside both JSON consumers, at offset " + at);
        }
    }

    @Test
    void bothEventsAreGLOBALLYBroadcastableOrAuthenticatedSocketsGetNothing() throws IOException {
        // In per-session mode broadcast() drops any event missing from GLOBAL_BROADCAST_EVENTS --
        // while forwardedEvents still increments, so the metric reads "delivered" and no socket
        // ever saw a frame.
        assertTrue(source().contains("\"gamma-zone\", \"gamma-zone-profile\","),
                "both events must be in GLOBAL_BROADCAST_EVENTS");
    }

    @Test
    void connectReplayDoesNotGoThroughTheSELECTIONRouter() throws IOException {
        // replayCacheMap resolves a routable record through GatewayRecordMapper, which has no case
        // for either event and returns nothing -- so the replay existed and delivered nothing.
        String source = source();
        assertFalse(source.contains("replayCacheMap(session, \"gamma-zone\""),
                "the selection router has no route for a futures-session record");
        assertTrue(source.contains("replayGammaZoneCached(session);"));
        assertTrue(source.contains("send(session, event, json);"),
                "the bespoke replay must send directly");
    }

    @Test
    void bothCachesAreEVICTEDAndTheProfileArmComesFirst() throws IOException {
        // Nothing evicted either map, so every session date this process saw stayed in memory at
        // ~9 KB a profile and was walked on every connect. And the arms are prefix tests:
        // "gamma-zone-profile:" starts with "gamma-zone:", so the profile arm must come FIRST or
        // every profile eviction routes to the wrong map.
        String source = source();
        int profileArm = source.indexOf("versionKey.startsWith(\"gamma-zone-profile:\")");
        int zoneArm = source.indexOf("versionKey.startsWith(\"gamma-zone:\")");
        assertTrue(profileArm > 0, "the profile cache must be evicted");
        assertTrue(zoneArm > 0, "the measurement cache must be evicted");
        assertTrue(profileArm < zoneArm,
                "the gamma-zone-profile arm must be tested BEFORE gamma-zone, or it never matches");
        assertTrue(source.contains("CachePolicy.expiring(settings.gammaZoneTtlMs())"),
                "both events need an expiring cache policy");
    }

    @Test
    void theCacheTtlIsOneSessionsHorizon() {
        // Long enough that an intra-day reconnect is handed the live session; short enough that a
        // reconnect the next morning is not handed yesterday's as though it were current.
        assertEquals(12L * 60L * 60L * 1000L, new GatewaySettings().gammaZoneTtlMs());
    }

    @Test
    void theyGateCatchUpReadinessLikeTheOtherSourceIndependentFeeds() throws IOException {
        // Bound under DATABENTO by convention only. With another source selected they must still
        // gate readiness, or the cache reads caught up over a session it has not read -- and the
        // page is handed a hello built from nothing.
        assertTrue(source().contains("|| \"gamma-zone\".equals(binding.event())\n"
                        + "                    || \"gamma-zone-profile\".equals(binding.event())\n"
                        + "                    || isVolPremiumEvent(binding.event())"),
                "both events must gate catch-up");
    }

    @Test
    void aConnectingPageIsHandedTheNewestOfEACHTopic() throws IOException {
        // Without this a page that opens mid-day draws nothing until the next folded bar, which on
        // a quiet afternoon is a minute of blank screen and looks exactly like a broken relay.
        // Superseded by connectReplayDoesNotGoThroughTheSELECTIONRouter: the replay is bespoke now,
        // because the selection router has no route for a futures-session record. What still has to
        // hold is that BOTH caches are replayed.
        String source = source();
        assertTrue(source.contains("replayGammaZoneMap(session, \"gamma-zone\", gammaZones, nowMs);"));
        assertTrue(source.contains("replayGammaZoneMap(session, \"gamma-zone-profile\", gammaZoneProfiles, nowMs);"));
    }

    @Test
    void eachEventHasItsOwnCacheKeyedBySession() throws IOException {
        // Keyed by the record's own sessionDate|symbol|timeframe, last-value-wins, exactly as the
        // compacted topics are. One live session is one entry, and the two records are cached
        // SEPARATELY because they are two different records with two different jobs.
        String source = source();
        assertTrue(source.contains("gammaZones.put(key, json);"));
        assertTrue(source.contains("gammaZoneProfiles.put(key, json);"));
        assertFalse(source.contains("gammaZones.put(key, json);\n                gammaZoneProfiles.put(key, json);"),
                "the two caches must not be written from one branch");
    }

    private static int count(String haystack, String needle) {
        int n = 0;
        for (int i = haystack.indexOf(needle); i >= 0; i = haystack.indexOf(needle, i + 1)) {
            n++;
        }
        return n;
    }
}

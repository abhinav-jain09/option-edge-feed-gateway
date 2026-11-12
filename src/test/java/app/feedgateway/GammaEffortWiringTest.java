package app.feedgateway;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

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
}

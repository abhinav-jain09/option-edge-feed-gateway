package app.feedgateway;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The ES MBO events must be on the global allowlist (an authenticated socket drops any other) and wired into the connect replay and the metrics. */
class EsMboWiringTest {
    private static String source() throws IOException {
        return Files.readString(Path.of("src/main/java/app/feedgateway/FeedGatewayService.java"));
    }

    @Test
    void theThreeLiveEventsAreOnTheGlobalAllowlistByTheirWireNames() {
        assertEquals(List.of("es-mbo-frame", "es-mbo-event", "es-mbo-status"), List.of(EsMboRelay.FRAME_EVENT, EsMboRelay.EVENT_EVENT, EsMboRelay.STATUS_EVENT));
        for (String event : List.of("es-mbo-frame", "es-mbo-event", "es-mbo-status")) {
            assertTrue(FeedGatewayService.isGlobalBroadcastEvent(event), event + " must survive per-session routing");
        }
    }

    @Test
    void theRelayIsWiredIntoTheConnectReplayTheFanOutAndTheMetrics() throws IOException {
        String source = source();
        assertTrue(source.contains("esMboRelay.getIfAvailable()"), "relay provider is used");
        assertTrue(source.contains("settings.esMboRelayEnabled()"), "connect replay is behind the flag");
        assertTrue(source.contains("streamEsMbo(String event, String json)"), "the fan-out entry exists");
        assertTrue(source.contains("+ esMboMetrics()"), "metrics are exposed");
    }

    @Test
    void theFanOutRefusesAnyOtherEventName() throws IOException {
        String source = source();
        int at = source.indexOf("void streamEsMbo(");
        String body = source.substring(at, source.indexOf("broadcast(event, json);", at));
        assertTrue(body.contains("FRAME_EVENT") && body.contains("EVENT_EVENT") && body.contains("STATUS_EVENT") && body.contains("return;"));
    }

    @Test
    void theMboReplayGoesRightBehindTheStatus() throws IOException {
        String source = source();
        int status = source.indexOf("send(session, \"status\", statusJson());");
        int replay = source.indexOf("relay.replay((event,json)->send(session,event,json))", source.indexOf("esMboRelay.getIfAvailable()"));
        int hello = source.indexOf("if (sendsCvdHello()) {", status);
        int body = source.indexOf("private void addClientAndReplay(");
        assertTrue(status > body && replay > status && replay < hello, "the mbo status+history are enqueued before the cvd-hello");
        assertFalse(source.contains("replayOlder"), "one history message, from one snapshot");
    }

    @Test
    void theWholeConnectReplayAndTheReturnToLiveReplayAreTaggedAsReplay() throws IOException {
        String source = source();
        int add = source.indexOf("public void addClient(WebSocketSession session) {");
        assertTrue(source.indexOf("asConnectReplay(() -> addClientAndReplay(session));", add) > add, "addClient tags the whole replay");
        assertTrue(source.contains("asConnectReplay(() -> replayCachedToSocket(s));"), "the return-to-live / readiness cache replay is tagged (the connect one runs inside addClient's tag)");
        int send = source.indexOf("private void send(WebSocketSession session, String event, String json) {");
        assertTrue(source.indexOf("CONNECT_REPLAY.get())", send) > send, "send() tags by the thread flag");
        assertFalse(source.contains("awaitRoom"), "nothing waits");
    }
}

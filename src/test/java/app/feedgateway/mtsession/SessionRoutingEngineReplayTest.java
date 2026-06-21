package app.feedgateway.mtsession;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Per-session Live↔Replay isolation in the routing engine (replay reqs 7/12). A session in replay
 * mode receives ONLY its private replay stream (never live), and other sessions keep receiving live
 * data uninterrupted.
 */
class SessionRoutingEngineReplayTest {

    private static final Set<String> DBNTO = Set.of();

    private SessionRoutingEngine engine() {
        return new SessionRoutingEngine(new ConcurrencyLimits(5, 5, 100), new SubscriptionManager());
    }

    private static Selection spx() {
        return new Selection(MarketDataSource.DATABENTO, "SPX", "20260612", StrikeWindow.ALL);
    }

    private static RoutableRecord pace() {
        return RoutableRecord.contract(MarketDataSource.DATABENTO, EventType.PACE, "SPX", "20260612", 7500, 0);
    }

    @Test
    void replayingSessionReceivesNoLiveRecords() {
        SessionRoutingEngine e = engine();
        e.registerAppSession("app:u1", "u1", spx(), DBNTO);
        e.attachSocket("app:u1", "s1");
        assertEquals(Set.of("s1"), e.route(pace()));

        e.setReplayMode("app:u1", true);
        assertTrue(e.isReplaying("app:u1"));
        assertEquals(Set.of(), e.route(pace()));                 // live suppressed for this session
        assertFalse(e.shouldDeliverToSocket(pace(), "s1"));      // and for cache replay on connect
        assertEquals(Set.of("s1"), e.socketsForAppSession("app:u1")); // replay still targets its socket
    }

    @Test
    void returnToLiveRestoresLiveDelivery() {
        SessionRoutingEngine e = engine();
        e.registerAppSession("app:u1", "u1", spx(), DBNTO);
        e.attachSocket("app:u1", "s1");

        e.setReplayMode("app:u1", true);
        assertEquals(Set.of(), e.route(pace()));

        e.setReplayMode("app:u1", false);
        assertFalse(e.isReplaying("app:u1"));
        assertEquals(Set.of("s1"), e.route(pace()));
    }

    @Test
    void oneUserReplaysWhileAnotherStaysLive() {
        SessionRoutingEngine e = engine();
        e.registerAppSession("app:u1", "u1", spx(), DBNTO);
        e.attachSocket("app:u1", "s1");
        e.registerAppSession("app:u2", "u2", spx(), DBNTO);
        e.attachSocket("app:u2", "s2");
        assertEquals(Set.of("s1", "s2"), e.route(pace()));

        e.setReplayMode("app:u1", true); // u1 enters replay; u2 untouched

        assertEquals(Set.of("s2"), e.route(pace()));            // only u2 gets live
        assertEquals(Set.of("s1"), e.socketsForAppSession("app:u1")); // u1's replay stream is isolated
        assertFalse(e.isReplaying("app:u2"));
    }
}

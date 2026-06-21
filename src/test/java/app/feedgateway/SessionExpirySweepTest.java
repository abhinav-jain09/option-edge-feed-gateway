package app.feedgateway;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import app.feedgateway.mtsession.ApprovalState;
import app.feedgateway.mtsession.ConcurrencyLimits;
import app.feedgateway.mtsession.MarketDataSource;
import app.feedgateway.mtsession.Selection;
import app.feedgateway.mtsession.SessionRoutingEngine;
import app.feedgateway.mtsession.StrikeWindow;
import app.feedgateway.mtsession.SubscriptionManager;
import app.feedgateway.mtsession.UserSessionPolicy;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Field;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.WebSocketSession;

/**
 * P0 (FR-18 evidenced): idle/max-session expiry must actually tear sessions down and close their sockets.
 * Driven by {@code SessionExpiryReaper} → {@link FeedGatewayService#sweepExpiredSessions()}.
 */
class SessionExpirySweepTest {

    private static final class MutableClock extends Clock {
        private Instant now;
        MutableClock(Instant start) { this.now = start; }
        void advance(Duration d) { now = now.plus(d); }
        @Override public Instant instant() { return now; }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
    }

    private WebSocketSession socket() throws Exception {
        WebSocketSession ws = mock(WebSocketSession.class);
        when(ws.getId()).thenReturn("s1");
        when(ws.isOpen()).thenReturn(true);
        return ws;
    }

    @Test
    void maxSessionExpiryTearsDownAndClosesSockets() throws Exception {
        MutableClock clock = new MutableClock(Instant.parse("2026-06-12T14:00:00Z"));
        SessionRoutingEngine engine =
                new SessionRoutingEngine(new ConcurrencyLimits(5, 5, 100), new SubscriptionManager(), clock);
        // idle floor is 10 min; cap the session at a 1-minute absolute deadline.
        UserSessionPolicy oneMinute = new UserSessionPolicy(10, 1, false);
        engine.registerAppSession("app:u1", "u1",
                new Selection(MarketDataSource.DATABENTO, "SPX", "20260612", StrikeWindow.ALL),
                Set.of("user"), ApprovalState.APPROVED, oneMinute);
        engine.attachSocket("app:u1", "s1");

        FeedGatewayService svc =
                new FeedGatewayService(new GatewaySettings(), new ObjectMapper(), new HpsfGatewayViewMapper(), engine);
        svc.runOutboundWritesInline();
        WebSocketSession ws = socket();
        svc.addClient(ws);

        assertEquals(0, svc.sweepExpiredSessions(), "not yet expired");

        clock.advance(Duration.ofSeconds(61)); // past the 1-minute max-session deadline

        assertEquals(1, svc.sweepExpiredSessions(), "the over-deadline session is swept");
        assertTrue(engine.appSession("app:u1").isEmpty(), "expired session removed from routing");
        verify(ws).close();                     // its socket is force-closed
        assertTrue(svc.healthJson().contains("\"clients\":0"));
    }

    @Test
    void liveSessionIsNotSwept() throws Exception {
        MutableClock clock = new MutableClock(Instant.parse("2026-06-12T14:00:00Z"));
        SessionRoutingEngine engine =
                new SessionRoutingEngine(new ConcurrencyLimits(5, 5, 100), new SubscriptionManager(), clock);
        engine.registerAppSession("app:u1", "u1",
                new Selection(MarketDataSource.DATABENTO, "SPX", "20260612", StrikeWindow.ALL),
                Set.of("user"), ApprovalState.APPROVED, new UserSessionPolicy(10, 60, false));
        engine.attachSocket("app:u1", "s1");
        FeedGatewayService svc =
                new FeedGatewayService(new GatewaySettings(), new ObjectMapper(), new HpsfGatewayViewMapper(), engine);
        svc.runOutboundWritesInline();
        svc.addClient(socket());

        clock.advance(Duration.ofMinutes(5)); // within idle (10m) and max (60m)
        assertEquals(0, svc.sweepExpiredSessions());
        assertFalse(engine.appSession("app:u1").isEmpty());
    }

    /**
     * P0 (replay resource safety on expiry): when the expiry sweep tears a session down, an in-flight
     * replay must be finalized explicitly — not left for an eventual socket-close callback — so the reader
     * cannot keep consuming Kafka after the session is gone.
     */
    @Test
    void expirySweepCancelsInFlightReplay() throws Exception {
        MutableClock clock = new MutableClock(Instant.parse("2026-06-12T14:00:00Z"));
        SessionRoutingEngine engine =
                new SessionRoutingEngine(new ConcurrencyLimits(5, 5, 100), new SubscriptionManager(), clock);
        UserSessionPolicy oneMinute = new UserSessionPolicy(10, 1, false);
        engine.registerAppSession("app:u1", "u1",
                new Selection(MarketDataSource.DATABENTO, "SPX", "20260612", StrikeWindow.ALL),
                Set.of("user"), ApprovalState.APPROVED, oneMinute);
        engine.attachSocket("app:u1", "s1");
        FeedGatewayService svc =
                new FeedGatewayService(new GatewaySettings(), new ObjectMapper(), new HpsfGatewayViewMapper(), engine);
        svc.runOutboundWritesInline();
        svc.addClient(socket());

        // An in-flight replay for the session (the state startReplay installs before its reader runs).
        FeedGatewayService.ReplayHandle handle = svc.registerOwnerHandleForTest("app:u1");
        replayHandlesOf(svc).put("app:u1", handle);
        engine.setReplayMode("app:u1", true);

        clock.advance(Duration.ofSeconds(61)); // past the 1-minute max-session deadline
        assertEquals(1, svc.sweepExpiredSessions());

        assertFalse(handle.active.get(), "expiry sweep cancels the in-flight reader");
        assertFalse(replayHandlesOf(svc).containsKey("app:u1"), "replay handle removed on expiry");
        assertTrue(engine.appSession("app:u1").isEmpty(), "expired session removed from routing");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, FeedGatewayService.ReplayHandle> replayHandlesOf(FeedGatewayService svc)
            throws Exception {
        Field f = FeedGatewayService.class.getDeclaredField("replayHandles");
        f.setAccessible(true);
        return (Map<String, FeedGatewayService.ReplayHandle>) f.get(svc);
    }
}

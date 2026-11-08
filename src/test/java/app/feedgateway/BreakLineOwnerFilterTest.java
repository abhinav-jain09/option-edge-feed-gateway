package app.feedgateway;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

/** The owner filter at the socket boundary for the break score (BS13-W-008): two users, the handler's tag, the targeted delivery, the per-socket hello and the global heartbeat. */
class BreakLineOwnerFilterTest {
    private static final String ALICE = LineHealthOwnerTag.of("alice"), BOB = LineHealthOwnerTag.of("bob");
    private final List<List<String>> received = new ArrayList<>();

    private WebSocketSession socket(String id, String subject) throws Exception {
        WebSocketSession ws = mock(WebSocketSession.class);
        when(ws.getId()).thenReturn(id);
        when(ws.isOpen()).thenReturn(true);
        Map<String, Object> attributes = new HashMap<>();
        if (subject != null) {
            attributes.put("sub", subject);
        }
        when(ws.getAttributes()).thenReturn(attributes);
        List<String> frames = new ArrayList<>();
        received.add(frames);
        org.mockito.Mockito.doAnswer(call -> {
            frames.add(((TextMessage) call.getArgument(0)).getPayload());
            return null;
        }).when(ws).sendMessage(org.mockito.ArgumentMatchers.any());
        return ws;
    }

    private FeedGatewayService gateway() {
        FeedGatewayService service = new FeedGatewayService(new GatewaySettings(), new ObjectMapper(), new HpsfGatewayViewMapper(), null);
        service.runOutboundWritesInline();
        return service;
    }

    @SuppressWarnings("unchecked")
    private FeedWebSocketHandler handler(FeedGatewayService service) {
        ObjectProvider<app.feedgateway.mtsession.SessionRoutingEngine> none = mock(ObjectProvider.class);
        when(none.getIfAvailable()).thenReturn(null);
        return new FeedWebSocketHandler(service, none);
    }

    @Test
    void twoUsersEachReceiveOnlyTheirOwnBreakLineFramesAndNobodyElseDoes() throws Exception {
        FeedGatewayService service = gateway();
        FeedWebSocketHandler handler = handler(service);
        WebSocketSession alice1 = socket("a1", "alice"), alice2 = socket("a2", "alice"), bob = socket("b", "bob"), nobody = socket("n", null);
        for (WebSocketSession s : List.of(alice1, alice2, bob, nobody)) {
            handler.afterConnectionEstablished(s);
        }
        received.forEach(List::clear);
        service.streamBreakLineTo(ALICE, "break-line-state", "{\"lineId\":\"bl-alice-secret\"}");
        service.streamBreakLineTo(BOB, "break-line-event", "{\"lineId\":\"bl-bob-secret\"}");
        assertTrue(received.get(0).get(0).contains("bl-alice-secret"));
        assertTrue(received.get(1).get(0).contains("bl-alice-secret"));
        assertEquals(1, received.get(0).size());
        assertTrue(received.get(2).get(0).contains("bl-bob-secret"));
        assertEquals(1, received.get(2).size());
        assertFalse(received.get(2).get(0).contains("alice"), "Bob never sees Alice's line");
        assertEquals(0, received.get(3).size(), "a socket with no subject receives nothing");
        service.streamBreakLineTo(null, "break-line-state", "{\"lineId\":\"bl-x\"}");
        service.streamBreakLineTo(" ", "break-line-state", "{\"lineId\":\"bl-x\"}");
        assertFalse(received.stream().anyMatch(frames -> frames.stream().anyMatch(f -> f.contains("bl-x"))));
    }

    @Test
    void aStateFrameSentThroughTheGlobalBroadcastIsDroppedNotDeliveredToAnyone() throws Exception {
        FeedGatewayService service = gateway();
        FeedWebSocketHandler handler = handler(service);
        WebSocketSession alice = socket("a", "alice"), bob = socket("b", "bob");
        handler.afterConnectionEstablished(alice);
        handler.afterConnectionEstablished(bob);
        received.forEach(List::clear);
        service.streamBreakLine("break-line-state", "{\"lineId\":\"bl-alice-secret\"}");
        assertFalse(received.stream().anyMatch(frames -> frames.stream().anyMatch(f -> f.contains("bl-alice-secret"))));
        service.streamBreakLine("break-line-status", "{\"dataLagMs\":5}");
        assertTrue(received.get(0).stream().anyMatch(f -> f.contains("dataLagMs")), "the heartbeat is global");
        assertTrue(received.get(1).stream().anyMatch(f -> f.contains("dataLagMs")));
    }

    @Test
    void theResumeAnswerAndTheCheckpointPartsAreNeverInTheGlobalAllowlist() {
        assertFalse(FeedGatewayService.GLOBAL_BROADCAST_EVENTS.contains(BreakLineRelay.RESUME));
        assertFalse(FeedGatewayService.GLOBAL_BROADCAST_EVENTS.contains(BreakLineRelay.CHECKPOINT_EVENT));
        assertFalse(FeedGatewayService.GLOBAL_BROADCAST_EVENTS.contains(BreakLineRelay.STATE_EVENT));
        assertFalse(FeedGatewayService.GLOBAL_BROADCAST_EVENTS.contains(BreakLineRelay.EVENT_EVENT));
        assertTrue(FeedGatewayService.GLOBAL_BROADCAST_EVENTS.contains(BreakLineRelay.STATUS_EVENT));
    }

    @Test
    void theHelloHasNoBreakLineFieldWhileTheFlagIsOff() {
        FeedGatewayService service = gateway();
        assertFalse(service.cvdHelloJson(null, ALICE).contains("breakLine"));
        assertEquals(service.cvdHelloJson(null), service.cvdHelloJson(null, ALICE));
    }
}

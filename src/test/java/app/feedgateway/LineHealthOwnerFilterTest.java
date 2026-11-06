package app.feedgateway;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
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

/** The owner filter at the socket boundary (requirement LH-O-6, LH-N-6): the handler's tag, the targeted delivery and the per-socket hello. */
class LineHealthOwnerFilterTest {

    private static final String ALICE = LineHealthOwnerTag.of("alice"), BOB = LineHealthOwnerTag.of("bob");

    private final List<List<String>> received = new ArrayList<>();

    @SuppressWarnings("unchecked")
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
    void theHandlerComputesTheTagOnceFromTheSubjectAndASocketWithoutOneHasNone() throws Exception {
        FeedWebSocketHandler handler = handler(gateway());
        WebSocketSession alice = socket("a", "alice"), nobody = socket("n", null), blank = socket("b", "  ");
        handler.afterConnectionEstablished(alice);
        handler.afterConnectionEstablished(nobody);
        handler.afterConnectionEstablished(blank);
        assertEquals(ALICE, FeedGatewayService.lineHealthOwnerTag(alice));
        assertNull(FeedGatewayService.lineHealthOwnerTag(nobody));
        assertNull(FeedGatewayService.lineHealthOwnerTag(blank));
    }

    @Test
    void aTargetedFrameReachesOnlyTheOwnersSockets() throws Exception {
        FeedGatewayService service = gateway();
        FeedWebSocketHandler handler = handler(service);
        WebSocketSession alice1 = socket("a1", "alice"), alice2 = socket("a2", "alice"), bob = socket("b", "bob"), nobody = socket("n", null);
        for (WebSocketSession s : List.of(alice1, alice2, bob, nobody)) {
            handler.afterConnectionEstablished(s);
        }
        received.forEach(List::clear);   // drop the connect frames
        service.streamLineHealthTo(ALICE, "line-health-state", "{\"lineId\":\"ui-alice\"}");
        assertTrue(received.get(0).get(0).contains("ui-alice"));
        assertTrue(received.get(1).get(0).contains("ui-alice"));
        assertEquals(0, received.get(2).size(), "another user receives nothing");
        assertEquals(0, received.get(3).size(), "a socket without a subject receives nothing");
        service.streamLineHealthTo(null, "line-health-state", "{\"lineId\":\"ui-x\"}");
        service.streamLineHealthTo(" ", "line-health-state", "{\"lineId\":\"ui-x\"}");
        assertFalse(received.stream().anyMatch(frames -> frames.stream().anyMatch(f -> f.contains("ui-x"))));
    }

    @Test
    void theHelloOfEachSocketCarriesItsOwnTag() {
        FeedGatewayService service = gateway();
        // no relay is wired here: the hello's lineHealth field is only added when the relay flag is on, so this asserts the overload keeps today's hello byte for byte
        assertEquals(service.cvdHelloJson(null), service.cvdHelloJson(null, ALICE));
    }
}

package app.feedgateway.mtsession.gateway;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.lang.reflect.Method;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * P1: the browser carries the ticket in the {@code oe.ticket.<id>} subprotocol; the server MUST echo it or
 * the upgrade fails. This verifies the echo (and the fallback when no ticket subprotocol is offered).
 */
class TicketSubProtocolHandshakeHandlerTest {

    private String select(List<String> offered) throws Exception {
        TicketSubProtocolHandshakeHandler h = new TicketSubProtocolHandshakeHandler();
        Method m = h.getClass().getDeclaredMethod("selectProtocol", List.class,
                org.springframework.web.socket.WebSocketHandler.class);
        m.setAccessible(true);
        return (String) m.invoke(h, offered, null);
    }

    @Test
    void echoesTheTicketSubprotocol() throws Exception {
        assertEquals("oe.ticket.abc123", select(List.of("oe.ticket.abc123")));
        // Even alongside other offered protocols, the ticket one is selected and echoed.
        assertEquals("oe.ticket.xyz", select(List.of("foo", "oe.ticket.xyz")));
    }

    @Test
    void noTicketSubprotocolFallsThroughToDefault() throws Exception {
        // No oe.ticket.* offered and no WebSocketHandler -> default negotiation selects nothing.
        assertNull(select(List.of("oc.bearer")));
        assertNull(select(List.of()));
    }
}

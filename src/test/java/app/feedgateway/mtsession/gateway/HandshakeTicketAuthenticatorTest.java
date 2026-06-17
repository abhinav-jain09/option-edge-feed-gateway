package app.feedgateway.mtsession.gateway;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.feedgateway.mtsession.InMemoryTicketStore;
import app.feedgateway.mtsession.WsTicket;
import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class HandshakeTicketAuthenticatorTest {

    private InMemoryTicketStore store() {
        AtomicInteger seq = new AtomicInteger();
        return new InMemoryTicketStore(Clock.systemUTC(), () -> "hk-" + seq.incrementAndGet());
    }

    @Test
    void disabledIsPassthrough() {
        HandshakeTicketAuthenticator auth = new HandshakeTicketAuthenticator(store());
        HandshakeTicketAuthenticator.Decision d = auth.authenticate(false, null);
        assertTrue(d.accept());
        assertTrue(d.passthrough());
    }

    @Test
    void enabledMissingTicketRejected() {
        HandshakeTicketAuthenticator auth = new HandshakeTicketAuthenticator(store());
        assertFalse(auth.authenticate(true, null).accept());
        assertFalse(auth.authenticate(true, "  ").accept());
    }

    @Test
    void enabledUnknownTicketRejected() {
        HandshakeTicketAuthenticator auth = new HandshakeTicketAuthenticator(store());
        assertFalse(auth.authenticate(true, "ghost").accept());
    }

    @Test
    void enabledValidTicketAcceptedOnceOnly() {
        InMemoryTicketStore store = store();
        HandshakeTicketAuthenticator auth = new HandshakeTicketAuthenticator(store);
        WsTicket t = store.mint("u1", "app:u1", Duration.ofSeconds(10));

        HandshakeTicketAuthenticator.Decision ok = auth.authenticate(true, t.ticketId());
        assertTrue(ok.accept());
        assertFalse(ok.passthrough());
        assertEquals("app:u1", ok.appSessionId());
        assertEquals("u1", ok.userId());

        // single-use: replay rejected
        assertFalse(auth.authenticate(true, t.ticketId()).accept());
    }
}

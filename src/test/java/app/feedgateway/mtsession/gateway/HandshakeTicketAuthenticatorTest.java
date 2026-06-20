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

    private static final String INSTANCE_A = "inst-a";

    /** A store whose minted ticket ids are prefixed with {@code instance~} (as the real id generator does). */
    private InMemoryTicketStore store(String instanceId) {
        AtomicInteger seq = new AtomicInteger();
        return new InMemoryTicketStore(Clock.systemUTC(), () -> instanceId + "~hk-" + seq.incrementAndGet());
    }

    private HandshakeTicketAuthenticator auth(InMemoryTicketStore store) {
        return new HandshakeTicketAuthenticator(store, INSTANCE_A);
    }

    @Test
    void disabledIsPassthrough() {
        HandshakeTicketAuthenticator.Decision d = auth(store(INSTANCE_A)).authenticate(false, null);
        assertTrue(d.accept());
        assertTrue(d.passthrough());
    }

    @Test
    void enabledMissingTicketRejected() {
        HandshakeTicketAuthenticator auth = auth(store(INSTANCE_A));
        assertFalse(auth.authenticate(true, null).accept());
        assertFalse(auth.authenticate(true, "  ").accept());
    }

    @Test
    void enabledUnknownTicketRejected() {
        assertFalse(auth(store(INSTANCE_A)).authenticate(true, "ghost").accept());
    }

    @Test
    void enabledValidTicketAcceptedOnceOnly() {
        InMemoryTicketStore store = store(INSTANCE_A);
        HandshakeTicketAuthenticator auth = auth(store);
        WsTicket t = store.mint("u1", "app:u1", Duration.ofSeconds(10));

        HandshakeTicketAuthenticator.Decision ok = auth.authenticate(true, t.ticketId());
        assertTrue(ok.accept());
        assertFalse(ok.passthrough());
        assertEquals("app:u1", ok.appSessionId());
        assertEquals("u1", ok.userId());

        // single-use: replay rejected
        assertFalse(auth.authenticate(true, t.ticketId()).accept());
    }

    @Test
    void ticketMintedByAnotherReplicaIsRejectedAndNotConsumed() {
        // P1: the AppSession lives only on the minting replica. A ticket minted on inst-b that reaches inst-a
        // must be rejected BEFORE redemption, and the single-use ticket must survive for the correct replica.
        InMemoryTicketStore sharedStore = store("inst-b");           // ids prefixed with inst-b~
        WsTicket foreign = sharedStore.mint("u1", "app:u1", Duration.ofSeconds(10));

        HandshakeTicketAuthenticator instA = new HandshakeTicketAuthenticator(sharedStore, INSTANCE_A);
        HandshakeTicketAuthenticator.Decision rejected = instA.authenticate(true, foreign.ticketId());
        assertFalse(rejected.accept());
        assertTrue(rejected.reason().contains("different gateway instance"));
        assertEquals(1, instA.foreignInstanceRejections());

        // The ticket was NOT consumed — the correctly sticky-routed replica (inst-b) can still redeem it.
        HandshakeTicketAuthenticator instB = new HandshakeTicketAuthenticator(sharedStore, "inst-b");
        assertTrue(instB.authenticate(true, foreign.ticketId()).accept(), "the bound replica still accepts it");
    }
}

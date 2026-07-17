package app.feedgateway;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class GatewayIsolationLevelTest {

    private static final String KEY = "GATEWAY_KAFKA_ISOLATION_LEVEL";

    @Test
    void defaultsToReadCommittedSoAbortedPreOpenTxnsAreNeverSurfaced() {
        assertEquals("read_committed", new GatewaySettings().consumerIsolationLevel());
    }

    @Test
    void honoursAnExplicitReadUncommittedOverride() {
        String prev = System.getProperty(KEY);
        try {
            System.setProperty(KEY, "read_uncommitted");
            assertEquals("read_uncommitted", new GatewaySettings().consumerIsolationLevel());
        } finally {
            restore(prev);
        }
    }

    @Test
    void anUnrecognisedValueFailsSafeToReadCommitted() {
        String prev = System.getProperty(KEY);
        try {
            System.setProperty(KEY, "garbage");
            assertEquals("read_committed", new GatewaySettings().consumerIsolationLevel());
        } finally {
            restore(prev);
        }
    }

    private static void restore(String prev) {
        if (prev == null) {
            System.clearProperty(KEY);
        } else {
            System.setProperty(KEY, prev);
        }
    }
}

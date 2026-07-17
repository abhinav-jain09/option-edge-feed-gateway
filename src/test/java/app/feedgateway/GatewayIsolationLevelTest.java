package app.feedgateway;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.junit.jupiter.api.Test;

/**
 * Locks the Kafka isolation-level invariant for the pre-open GEX safety property: record-surfacing consumers
 * MUST be read_committed (so an aborted pre-open transaction is never shown) and the endOffsets-only source
 * -switch barrier MUST be read_uncommitted (so it captures the physical high watermark, not the last-stable
 * offset). Both are hard-coded — there is deliberately no env escape hatch that could weaken read_committed.
 */
class GatewayIsolationLevelTest {

    @Test
    void recordConsumersAreReadCommitted() {
        assertEquals("read_committed", FeedGatewayService.RECORD_CONSUMER_ISOLATION);
    }

    @Test
    void barrierConsumerIsReadUncommittedForAPhysicalHighWatermark() {
        assertEquals("read_uncommitted", FeedGatewayService.BARRIER_CONSUMER_ISOLATION);
    }

    @Test
    void theTwoIsolationLevelsDiffer() {
        assertNotEquals(FeedGatewayService.RECORD_CONSUMER_ISOLATION,
                FeedGatewayService.BARRIER_CONSUMER_ISOLATION);
    }
}

package app.feedgateway.mtsession;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SubscriptionManagerTest {

    private final SubscriptionKey key = new SubscriptionKey(MarketDataSource.DATABENTO, "SPX", "20260612");

    @Test
    void acquireSingleOwner() {
        SubscriptionManager m = new SubscriptionManager();
        SubscriptionManager.RefState s = m.acquire(key, "app1", StrikeWindow.of(7000, 8000));
        assertEquals(1, s.refCount());
        assertEquals(StrikeWindow.of(7000, 8000), s.range().orElseThrow());
        assertEquals(1, m.distinctSubscriptions());
    }

    @Test
    void acquireIsIdempotentPerOwner() {
        SubscriptionManager m = new SubscriptionManager();
        m.acquire(key, "app1", StrikeWindow.of(7000, 8000));
        SubscriptionManager.RefState s = m.acquire(key, "app1", StrikeWindow.of(7000, 8000));
        assertEquals(1, s.refCount(), "re-acquire by same owner must not inflate the count");
    }

    @Test
    void twoOwnersUnionRange() {
        SubscriptionManager m = new SubscriptionManager();
        m.acquire(key, "app1", StrikeWindow.of(7000, 7500));
        SubscriptionManager.RefState s = m.acquire(key, "app2", StrikeWindow.of(7400, 8000));
        assertEquals(2, s.refCount());
        assertEquals(StrikeWindow.of(7000, 8000), s.range().orElseThrow());
    }

    @Test
    void rangeNarrowsWhenOwnerLeaves() {
        SubscriptionManager m = new SubscriptionManager();
        m.acquire(key, "app1", StrikeWindow.of(7000, 7500));
        m.acquire(key, "app2", StrikeWindow.of(7400, 8000));
        m.release(key, "app2");
        assertEquals(StrikeWindow.of(7000, 7500), m.subscribedRange(key).orElseThrow());
    }

    @Test
    void releaseNeverGoesNegativeAndIsIdempotent() {
        SubscriptionManager m = new SubscriptionManager();
        m.acquire(key, "app1", StrikeWindow.ALL);
        assertEquals(0, m.release(key, "app1").refCount());
        // duplicate release of an already-removed owner / empty key
        assertEquals(0, m.release(key, "app1").refCount());
        assertEquals(0, m.release(key, "ghost").refCount());
        assertEquals(0, m.refCount(key));
    }

    @Test
    void lastReleaseRemovesEntry() {
        SubscriptionManager m = new SubscriptionManager();
        m.acquire(key, "app1", StrikeWindow.ALL);
        m.acquire(key, "app2", StrikeWindow.ALL);
        assertEquals(1, m.release(key, "app1").refCount());
        SubscriptionManager.RefState last = m.release(key, "app2");
        assertEquals(0, last.refCount());
        assertTrue(last.range().isEmpty());
        assertEquals(0, m.distinctSubscriptions());
        assertTrue(m.desiredSet().isEmpty());
    }

    @Test
    void desiredSetReflectsUnion() {
        SubscriptionManager m = new SubscriptionManager();
        SubscriptionKey ndx = new SubscriptionKey(MarketDataSource.DATABENTO, "NDX", "20260620");
        m.acquire(key, "a", StrikeWindow.of(7000, 8000));
        m.acquire(ndx, "b", StrikeWindow.of(18000, 19000));
        assertEquals(2, m.desiredSet().size());
        assertEquals(StrikeWindow.of(7000, 8000), m.desiredSet().get(key));
        assertFalse(m.desiredSet().isEmpty());
    }
}

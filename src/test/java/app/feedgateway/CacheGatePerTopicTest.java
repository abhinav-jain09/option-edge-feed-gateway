package app.feedgateway;

import app.feedgateway.FeedGatewayService.CacheGate;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The per-topic live-forward gate that replaced the family-atomic catch-up flag.
 *
 * <p>The incident being prevented: after a gateway restart, ONE large or slow auxiliary topic kept
 * the family flag false for minutes (or forever, under CPU pressure), and the live consumers
 * cache-gated EVERY record — ~1.05M of ~1.25M polled records dropped in prod, the entire option
 * chain dark while Kubernetes reported Ready. The known-bad behaviour is pinned by the controls in
 * each test: the family flag alone answers WRONG (false) exactly where the per-topic gate must
 * answer ready.
 */
class CacheGatePerTopicTest {

    private static final String CORE = "options.databento.display";
    private static final String AUX = "options.databento.gex.strike-lifecycle";

    @Test
    void readyTopicForwardsWhileFamilyStillCatchingUp() {
        AtomicBoolean family = new AtomicBoolean(false);
        CacheGate gate = new CacheGate(family);
        gate.applyBarrierStates(Map.of(CORE, true, AUX, false));

        // Known-bad control: the OLD gate was this family flag, and it says nothing may forward.
        assertFalse(family.get(), "control: the family-atomic flag still reads not-caught-up");

        // The fix: the core lane is complete, so core records forward NOW; only the still-replaying
        // auxiliary lane stays dark.
        assertTrue(gate.readyFor(CORE), "a caught-up topic must forward while the family is still catching up");
        assertFalse(gate.readyFor(AUX), "a topic whose own lane is still replaying must stay closed");
    }

    @Test
    void pendingTopicStaysClosedEvenWhenFamilyFlagIsStaleTrue() {
        // A partition added mid-run flips its topic's barrier back to pending; the family flag can
        // lag that transition by a poll. The per-topic answer must fail closed on its own state.
        AtomicBoolean family = new AtomicBoolean(true);
        CacheGate gate = new CacheGate(family);
        gate.applyBarrierStates(Map.of(CORE, false));

        assertFalse(gate.readyFor(CORE), "a pending barrier gates its topic even while the family flag reads true");
    }

    @Test
    void unbarrieredTopicFollowsTheFamilyFlagExactly() {
        // A topic with NO catch-up barrier this attempt (its source is not selected, e.g. vix-price
        // while IBKR is active) kept family-flag semantics before this change and must keep them
        // now — it forwards exactly when the family flag says so, in both directions.
        AtomicBoolean family = new AtomicBoolean(false);
        CacheGate gate = new CacheGate(family);
        gate.applyBarrierStates(Map.of(CORE, true));

        assertFalse(gate.readyFor("options.databento.vix"), "no barrier + family false => closed");
        family.set(true);
        assertTrue(gate.readyFor("options.databento.vix"), "no barrier + family true => open");
    }

    @Test
    void attemptDeathClearsEveryTopicStateAndFailsClosed() {
        AtomicBoolean family = new AtomicBoolean(true);
        CacheGate gate = new CacheGate(family);
        gate.applyBarrierStates(Map.of(CORE, true));
        assertTrue(gate.readyFor(CORE));

        // The consumer attempt dies: markCacheRecovering drops the family flag and the retry hook
        // clears the per-topic states, so NOTHING stays open on a dead attempt's measurements.
        gate.clearTopicStates();
        family.set(false);
        assertFalse(gate.readyFor(CORE), "a dead attempt's ready verdict must not survive it");
        assertTrue(gate.pendingTopics().isEmpty(), "cleared states leave nothing pending-listed");
    }

    @Test
    void sourceSwitchRetiresBarriersThatNoLongerApply() {
        AtomicBoolean family = new AtomicBoolean(false);
        CacheGate gate = new CacheGate(family);
        gate.applyBarrierStates(Map.of(CORE, true, AUX, false));

        // The switch recomputes barriers for the NEW selection; AUX is no longer barriered.
        gate.applyBarrierStates(Map.of(CORE, false));

        assertFalse(gate.readyFor(CORE), "the recomputed barrier is authoritative");
        assertFalse(gate.readyFor(AUX), "a retired barrier falls back to the family flag (false here)");
        family.set(true);
        assertTrue(gate.readyFor(AUX), "a retired barrier follows the family flag, not its stale state");
        assertEquals(List.of(CORE), gate.pendingTopics());
    }

    @Test
    void topicBarrierStatesRequiresEveryPartitionOfATopic() {
        Map<TopicPartition, Long> barriers = new LinkedHashMap<>();
        barriers.put(new TopicPartition(CORE, 0), 10L);
        barriers.put(new TopicPartition(CORE, 1), 20L);
        barriers.put(new TopicPartition(AUX, 0), 5L);

        // CORE partition 1 is still 1 record short; AUX is complete.
        Map<TopicPartition, Long> positions = Map.of(
                new TopicPartition(CORE, 0), 10L,
                new TopicPartition(CORE, 1), 19L,
                new TopicPartition(AUX, 0), 7L);
        Map<String, Boolean> states =
                FeedGatewayService.topicBarrierStates(barriers, positions::get);
        assertEquals(Map.of(CORE, false, AUX, true), states,
                "a topic is ready only when EVERY one of its barrier partitions is reached");

        // The lagging partition reaches its barrier: the whole topic flips ready.
        Map<TopicPartition, Long> caughtUp = Map.of(
                new TopicPartition(CORE, 0), 10L,
                new TopicPartition(CORE, 1), 20L,
                new TopicPartition(AUX, 0), 7L);
        assertEquals(Map.of(CORE, true, AUX, true),
                FeedGatewayService.topicBarrierStates(barriers, caughtUp::get));
    }

    @Test
    void emptyBarrierMapYieldsNoTopicStates() {
        // catchUpEndOffsets can legitimately be empty (nothing assigned). Everything then follows
        // the family flag — which caughtUp({}) certifies true, exactly the pre-existing behaviour.
        assertTrue(FeedGatewayService.topicBarrierStates(Map.of(), p -> 0L).isEmpty());

        AtomicBoolean family = new AtomicBoolean(true);
        CacheGate gate = new CacheGate(family);
        gate.applyBarrierStates(Map.of());
        assertTrue(gate.readyFor(CORE), "no barriers at all => the family flag decides");
    }

    @Test
    void pendingTopicsListsOnlyUnreachedBarriersSorted() {
        CacheGate gate = new CacheGate(new AtomicBoolean(false));
        gate.applyBarrierStates(Map.of(
                "z.topic", false,
                "a.topic", false,
                CORE, true));
        assertEquals(List.of("a.topic", "z.topic"), gate.pendingTopics());
    }
}

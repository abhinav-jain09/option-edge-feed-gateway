package app.feedgateway;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * <b>C-136: the cache sweep resolves ONE policy per event, not one per entry.</b>
 *
 * <p><b>The measurement that brought this.</b> A 90-second JDK Flight Recorder allocation profile of
 * the live dev gateway on 2026-11-20 ranked every allocation site in the process. Three of the top
 * four were one sweep: {@code CachePolicy.expiring} (a record per cache entry per sweep),
 * {@code java.time.LocalTime.create} (reached through {@code cachePolicyFor} &rarr;
 * {@code isRegularTradingHours}, which builds a {@code ZonedDateTime} and a {@code LocalTime} from
 * {@code nowMs}, again per entry) and {@code purgeExpiredCache} itself (a
 * {@code ConcurrentHashMap$MapEntry} per entry from the {@code entrySet()} iterator). Together they
 * were about half of everything the process allocated, and the sweep runs on every poll of both
 * consumer loops.
 *
 * <p>Two class histograms four minutes apart showed the live set growing by under a megabyte while
 * the heap's teeth ran a gigabyte above its floor, so this is <b>churn and not a leak</b> — which is
 * why the fix is an allocation count and not an eviction policy.
 *
 * <p><b>What is asserted here.</b> That the sweep's decisions are unchanged, entry for entry,
 * against a per-entry evaluation; and that the number of policy resolutions is the number of
 * distinct events rather than the number of entries. The second is the fix and the first is the
 * licence for it.
 */
final class C136CachePolicyChurnTest {

    /** Counts policy resolutions, which is the figure the fix is about. */
    private static final class Counting extends FeedGatewayService {
        final AtomicInteger resolutions = new AtomicInteger();
        final List<String> events = new ArrayList<>();

        Counting() {
            super(new GatewaySettings(), new ObjectMapper(), new HpsfGatewayViewMapper(), null);
        }

        @Override
        CachePolicy cachePolicyFor(String event, long nowMs) {
            resolutions.incrementAndGet();
            events.add(event);
            return super.cachePolicyFor(event, nowMs);
        }
    }

    private static Map<String, Long> cacheTimes(long nowMs, int perEvent, String... events) {
        Map<String, Long> times = new LinkedHashMap<>();
        for (String event : events) {
            for (int i = 0; i < perEvent; i++) {
                // half of them far older than any TTL, half of them current
                long at = i % 2 == 0 ? nowMs - 48L * 3_600_000L : nowMs;
                times.put(event + ":SPX:" + i, at);
            }
        }
        return times;
    }

    @Test
    @DisplayName("one policy resolution per EVENT, not per entry — 5 instead of 2,000")
    void onePolicyPerEventNotPerEntry() {
        Counting service = new Counting();
        long now = 1_795_204_918_171L;
        String[] events = {"snapshot", "max-pain", "gex-by-strike", "pace", "option-truth"};
        Map<String, Long> times = cacheTimes(now, 400, events);
        assertEquals(2_000, times.size(), "the fixture is the size the live cache reaches");

        service.expiredCacheKeys(times, now);

        assertEquals(events.length, service.resolutions.get(),
                "the sweep resolved " + service.resolutions.get() + " policies for "
                        + times.size() + " entries across " + events.length + " events; before "
                        + "C-136 it was one per entry");
        assertEquals(events.length, service.events.stream().distinct().count(),
                "and one of them for each event");
    }

    @Test
    @DisplayName("the decisions are unchanged, entry for entry, against a per-entry evaluation")
    void theDecisionsAreUnchanged() {
        Counting service = new Counting();
        long now = 1_795_204_918_171L;
        String[] events = {"snapshot", "max-pain", "gex-by-strike", "pace", "option-truth",
                           "es-footprint", "zero-dte-intelligence"};
        Map<String, Long> times = cacheTimes(now, 30, events);

        List<String> fromTheSweep = service.expiredCacheKeys(times, now);

        // the same judgement, resolved per ENTRY as the code did before
        List<String> perEntry = new ArrayList<>();
        times.forEach((versionKey, eventTime) -> {
            String event = versionKey.substring(0, versionKey.indexOf(':'));
            FeedGatewayService.CachePolicy policy = service.cachePolicyFor(event, now);
            boolean expired = !policy.neverEvict()
                    && (policy.ttlMs() <= 0 || eventTime < now - policy.ttlMs());
            if (expired) {
                perEntry.add(versionKey);
            }
        });

        assertEquals(perEntry, fromTheSweep,
                "the memo must change no decision: it is the same pure function of (event, nowMs)");
        assertTrue(fromTheSweep.size() > 0 && fromTheSweep.size() < times.size(),
                "the fixture must actually separate expired from fresh, or this proves nothing: "
                        + fromTheSweep.size() + " of " + times.size());
    }

    @Test
    @DisplayName("an empty cache resolves nothing at all")
    void anEmptyCacheResolvesNothing() {
        Counting service = new Counting();
        assertEquals(List.of(), service.expiredCacheKeys(Map.of(), 1_795_204_918_171L));
        assertEquals(0, service.resolutions.get(),
                "a sweep of nothing must not build a policy, a ZonedDateTime or a LocalTime");
    }
}

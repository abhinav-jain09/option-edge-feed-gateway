package app.feedgateway.readable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * <b>C-107: the gap line is a LIST of late episodes, not one window.</b>
 *
 * <p><b>Why the single window was wrong.</b> It was the earliest late bar's minute to the latest
 * late bar's minute, so two separate ten-minute outages eight hours apart named the eight hours
 * between them &mdash; most of a session the feed was alive for. The owner reads that cell to know
 * which minutes his VWAP was built without, and the old form answered a question nobody asked.
 *
 * <p><b>The rule, as the reviewer set it.</b> An episode is two or more <b>consecutive</b> late
 * bars of the finest width, and it ends at the first measured bar that is <b>not</b> late. One late
 * bar is counted but is not an episode. An episode lying wholly inside the exchange's own
 * 17:00&ndash;18:00 New York halt is not an episode either. The line shows the <b>two longest</b>
 * and the <b>count</b>.
 */
final class C107LateEpisodesTest {

    private static final long WIDTH = 30_000L;

    /** 2026-11-19 18:00:00 New York, the same anchor {@code FeedGapTest} uses. */
    private static final long EIGHTEEN_NY =
            java.time.Instant.parse("2026-11-19T23:00:00Z").toEpochMilli();

    /** 2026-11-19 17:00:00 New York: the first minute of the daily halt. */
    private static final long SEVENTEEN_NY =
            java.time.Instant.parse("2026-11-19T22:00:00Z").toEpochMilli();

    private static FeedGap.Published punctual(long startMs) {
        return new FeedGap.Published(startMs, startMs + WIDTH, startMs + WIDTH + 5_000L);
    }

    /** Late by six hours, which is the shape a backfilled outage really has. */
    private static FeedGap.Published late(long startMs) {
        return new FeedGap.Published(startMs, startMs + WIDTH,
                startMs + WIDTH + 6L * 3_600_000L);
    }

    /** {@code n} slots from {@code fromMs}, the ones in {@code lateSlots} backfilled. */
    private static List<FeedGap.Published> run(long fromMs, int n, List<Integer> lateSlots) {
        List<FeedGap.Published> out = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            long at = fromMs + i * WIDTH;
            out.add(lateSlots.contains(i) ? late(at) : punctual(at));
        }
        return out;
    }

    @Test
    @DisplayName("C-107: two separate outages are TWO episodes, not one window over the hours between")
    void twoOutagesAreTwoEpisodes() {
        // thirty slots: late at 2-4, punctual through the middle, late again at 24-27
        List<FeedGap.Published> bars = run(EIGHTEEN_NY, 30,
                List.of(2, 3, 4, 24, 25, 26, 27));
        FeedGap g = new FeedGap();
        g.measure(bars);

        assertEquals(7, g.lateBars());
        assertEquals(2, g.episodes().size(),
                "THE DEFECT: one window from the first late bar to the last named every punctual "
                        + "minute between them as a gap");
        FeedGap.Episode first = g.episodes().get(0);
        FeedGap.Episode second = g.episodes().get(1);
        assertEquals(3, first.bars());
        assertEquals(4, second.bars());
        assertEquals(EIGHTEEN_NY + 2 * WIDTH, first.fromStartMs());
        assertEquals(EIGHTEEN_NY + 4 * WIDTH, first.toStartMs());
        assertEquals(EIGHTEEN_NY + 24 * WIDTH, second.fromStartMs());
        assertEquals(EIGHTEEN_NY + 27 * WIDTH, second.toStartMs());
        assertTrue(g.label().startsWith("gap 2 eps "), g.label());
    }

    @Test
    @DisplayName("C-107: an episode ends at the first bar that is not late, not at a clock")
    void anEpisodeEndsWhereLatenessReturnsToNormal() {
        List<FeedGap.Published> bars = run(EIGHTEEN_NY, 10, List.of(1, 2, 3));
        FeedGap g = new FeedGap();
        g.measure(bars);
        assertEquals(1, g.episodes().size());
        assertEquals(EIGHTEEN_NY + 3 * WIDTH, g.episodes().get(0).toStartMs(),
                "slot 4 was punctual, so the episode ended at slot 3 and not at slot 4");
        assertEquals(3, g.episodes().get(0).bars());
    }

    @Test
    @DisplayName("C-107: one late bar between punctual ones is counted, and is no episode")
    void oneLateBarIsNotAnEpisode() {
        FeedGap g = new FeedGap();
        g.measure(run(EIGHTEEN_NY, 10, List.of(5)));
        assertEquals(1, g.lateBars(), "the lateness is real and is counted");
        assertTrue(g.episodes().isEmpty(), "one bar cannot show a window the VWAP missed");
        assertNull(g.label(), "and the owner's line stays quiet rather than crying wolf");
    }

    @Test
    @DisplayName("C-107: a run wholly inside the 17:00 New York halt is NOT an episode")
    void theMaintenanceBreakIsNotAnEpisode() {
        // 17:05 to 17:10 New York: the exchange is shut, and the bars arrive in a burst after
        FeedGap g = new FeedGap();
        g.measure(run(SEVENTEEN_NY + 10 * WIDTH, 12, List.of(0, 1, 2, 3)));
        assertEquals(4, g.lateBars(), "they were late, and the count still says so");
        assertTrue(g.episodes().isEmpty(),
                "but the daily halt is the exchange's own schedule, not a feed fault, and "
                        + "labelling it would teach the owner to ignore the cell");
        assertNull(g.label());
    }

    @Test
    @DisplayName("C-107: an episode that merely OVERLAPS the halt is kept whole")
    void anEpisodeOverlappingTheBreakIsKeptWhole() {
        // starts 17:55 New York and runs past 18:00: the 2026-11-20 outage's own shape
        long from = SEVENTEEN_NY + 110 * WIDTH;             // 17:55:00
        FeedGap g = new FeedGap();
        g.measure(run(from, 20, List.of(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12)));
        assertEquals(1, g.episodes().size());
        assertEquals(13, g.episodes().get(0).bars(),
                "clipping it at the break's edge would shorten the only episode that mattered");
        assertEquals(from, g.episodes().get(0).fromStartMs());
    }

    @Test
    @DisplayName("C-107: with more than two, the line shows the TWO LONGEST and the count")
    void theLineShowsTheTwoLongestAndTheCount() {
        // four episodes of 2, 9, 3 and 6 bars
        List<Integer> lateSlots = new ArrayList<>();
        lateSlots.addAll(List.of(1, 2));                                 // 2 bars
        lateSlots.addAll(List.of(10, 11, 12, 13, 14, 15, 16, 17, 18));   // 9 bars
        lateSlots.addAll(List.of(25, 26, 27));                           // 3 bars
        lateSlots.addAll(List.of(35, 36, 37, 38, 39, 40));               // 6 bars
        FeedGap g = new FeedGap();
        g.measure(run(EIGHTEEN_NY, 50, lateSlots));

        assertEquals(4, g.episodes().size());
        List<FeedGap.Episode> longest = g.twoLongest();
        assertEquals(2, longest.size());
        assertEquals(9, longest.get(0).bars(), "the longest first");
        assertEquals(6, longest.get(1).bars());

        String label = g.label();
        assertTrue(label.startsWith("gap 4 eps "),
                "the COUNT is on the line, so four outages and one cannot read alike: " + label);
        assertTrue(label.contains(longest.get(0).window()), label);
        assertTrue(label.contains(longest.get(1).window()), label);
        assertTrue(label.length() <= 64, "C-75 keeps headline lines short: " + label);
    }

    @Test
    @DisplayName("C-107: the episodes are a pure function of the records, in any order")
    void theEpisodesSurviveAnyArrivalOrder() {
        List<FeedGap.Published> bars = run(EIGHTEEN_NY, 30, List.of(2, 3, 4, 24, 25, 26, 27));
        FeedGap ordered = new FeedGap();
        ordered.measure(bars);

        List<FeedGap.Published> shuffled = new ArrayList<>(bars);
        Collections.shuffle(shuffled, new java.util.Random(20_261_120L));
        FeedGap unordered = new FeedGap();
        unordered.measure(shuffled);

        assertEquals(ordered.episodes(), unordered.episodes(),
                "a run is a property of the SESSION, not of the order the drain handed the "
                        + "records over in. Reading runs off an unordered collection would split "
                        + "one outage into as many episodes as the iteration order had inversions, "
                        + "which is the restart-invariance this class was built for");
        assertEquals(ordered.label(), unordered.label());
    }
}

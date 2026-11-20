package app.feedgateway.readable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * <b>C-96's three defects, each driven by the shape that produced it on 2026-11-20.</b>
 *
 * <p>Not one of these is a test of a sentence in a page. Each one reproduces the mechanism: two
 * threads writing one map, a window built from mixed timeframes, and a page whose block was gated
 * on a board that is absent for most of the day.
 */
final class C96RegressionsTest {

    private static OvernightGrid.Bar bar(String timeframe, long startMs) {
        return OvernightGrid.Bar.spx(startMs, 7100.0, 7105.0, 7095.0, 7102.0, 10L,
                "ANCHORED", timeframe, null, null, null);
    }

    /**
     * <b>Item 2: the night survives two threads writing it while a third reads.</b>
     *
     * <p>This is the defect, not a proxy for it. The live state consumer and the start-up drain both
     * call {@code onBar}; a plain {@code TreeMap} corrupted its red-black structure under that and
     * threw {@code NullPointerException} from {@code rotateLeft}, killing the consumer and shedding
     * entries until the page read {@code slots=0/1}. With a plain {@code TreeMap} this test fails
     * either by exception or by a short count; it must do neither.
     */
    @Test
    @DisplayName("item 2: concurrent onBar keeps every bar, and no reader sees a corrupt map")
    void theNightSurvivesConcurrentWriters() throws Exception {
        OvernightSource source = new OvernightSource();
        long base = 1_795_000_000_000L;
        int perThread = 1_500;
        // the drain replays the EARLIER half, the live consumer appends the LATER half -- which is
        // the real interleaving: a backfill of past minutes while the present keeps arriving
        ExecutorService pool = Executors.newFixedThreadPool(3);
        CountDownLatch start = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Runnable drain = () -> {
            try {
                start.await();
                for (int i = 0; i < perThread; i++) {
                    source.onBar(bar("1m", base + i * 60_000L));
                }
            } catch (Throwable t) {
                failure.compareAndSet(null, t);
            }
        };
        Runnable live = () -> {
            try {
                start.await();
                for (int i = perThread; i < 2 * perThread; i++) {
                    source.onBar(bar("1m", base + i * 60_000L));
                }
            } catch (Throwable t) {
                failure.compareAndSet(null, t);
            }
        };
        Runnable reader = () -> {
            try {
                start.await();
                for (int i = 0; i < 400; i++) {
                    // the page's own access: iterate the collection the renderer is handed
                    long seen = 0;
                    for (OvernightGrid.Bar b : source.bars("1m")) {
                        if (b != null) {
                            seen++;
                        }
                    }
                    assertTrue(seen >= 0);
                }
            } catch (Throwable t) {
                failure.compareAndSet(null, t);
            }
        };
        pool.submit(drain);
        pool.submit(live);
        pool.submit(reader);
        start.countDown();
        pool.shutdown();
        assertTrue(pool.awaitTermination(60, TimeUnit.SECONDS), "the writers did not finish");
        assertNull(failure.get(), "a thread failed: " + failure.get());
        assertEquals(2 * perThread, source.barCount("1m"),
                "bars were lost: this is the count the page's slots are built from");
    }

    @Test
    @DisplayName("item 2: a reader is handed a live view, so it must tolerate a concurrent write")
    void aReaderToleratesAWriteMidIteration() {
        OvernightSource source = new OvernightSource();
        long base = 1_795_000_000_000L;
        for (int i = 0; i < 200; i++) {
            source.onBar(bar("1m", base + i * 60_000L));
        }
        int seen = 0;
        for (OvernightGrid.Bar b : source.bars("1m")) {
            // Write WHILE iterating: a view over an unsynchronised map throws here, and a weakly
            // consistent one does not -- which is the property the heap fix depends on, since
            // bars(String) hands the renderer the live map rather than a copy.
            //
            // The inserted keys are EARLIER than the iteration's start on purpose. Inserting
            // later ones does not fail this test, it never ends it: a weakly consistent iterator
            // does see keys added ahead of it, so each pass would add another and the loop would
            // run until the heap gave out. It did, in the surefire fork, which is how I found out.
            source.onBar(bar("1m", base - (1L + seen) * 60_000L));
            if (b != null) {
                seen++;
            }
        }
        assertEquals(200, seen, "the iteration did not cover the bars present when it started");
        assertEquals(400, source.barCount("1m"),
                "every concurrent insert survived: 200 original plus 200 written mid-iteration");
    }

    /**
     * <b>Item 3: the gap window comes from the smallest timeframe only.</b>
     *
     * <p>The exact shape that printed {@code gap 18:00-23:43} while the feed died at 18:16: a
     * 30-minute bar opening before the outage, closing inside it, published hours late. It is
     * genuinely late, and its start is sixteen minutes before the feed stopped.
     */
    @Test
    @DisplayName("item 3: a wide bar that opened before the outage does not drag the window back")
    void theWindowComesFromTheFinestTimeframe() {
        long eighteen00 = 1_795_000_000_000L;              // the 30m bar's open
        long eighteen16 = eighteen00 + 16 * 60_000L;       // where the feed actually died
        long resumed = eighteen00 + 5 * 3_600_000L;        // five hours later
        long late = resumed + 3_600_000L;                  // everything published an hour after that

        List<FeedGap.Published> bars = new ArrayList<>();
        // the 30-minute bar: opens 18:00, closes 18:30, published hours late -> genuinely late
        bars.add(new FeedGap.Published(eighteen00, eighteen00 + 30 * 60_000L, late));
        // the 30-second bars: the first late one opens at 18:16, which is the truth
        for (long t = eighteen16; t < resumed; t += 30_000L) {
            bars.add(new FeedGap.Published(t, t + 30_000L, late));
        }
        // and some on-time 30-second bars after the resume, so the window has a right-hand edge
        for (long t = resumed; t < resumed + 10 * 30_000L; t += 30_000L) {
            bars.add(new FeedGap.Published(t, t + 30_000L, t + 30_000L + 5_000L));
        }

        FeedGap gap = new FeedGap();
        gap.measure(bars);
        assertEquals(30_000L, gap.finestWidthMs(), "the finest width present is the 30-second bar");
        String label = gap.label();
        assertNotNull(label, "bars were late, so there is a window");
        // the window must START at 18:16 and not at 18:00 -- the whole point of the fix
        String expectedStart = ReadablePage.hms(eighteen16, ReadablePage.NY).substring(0, 5);
        String wrongStart = ReadablePage.hms(eighteen00, ReadablePage.NY).substring(0, 5);
        // C-107 put the episode count on the label, so the window now follows "gap N ep(s) ".
        // The assertion is on the EPISODE's own window rather than the label's prefix, which is
        // what this test was always about and no longer depends on the label's shape.
        assertEquals(1, gap.episodes().size(), "one outage, one episode: " + label);
        assertEquals(expectedStart, gap.episodes().get(0).window().substring(0, 5),
                "window should start at the first LATE 30-second bar (" + expectedStart
                        + "), got: " + label);
        assertTrue(!expectedStart.equals(wrongStart)
                        && !gap.episodes().get(0).window().startsWith(wrongStart),
                "the 30-minute bar's own open must not name the window: " + label);
        // and only the finest bars were counted
        assertEquals(bars.size() - 1 - 10, gap.lateBars(),
                "every late 30-second bar counted, and the 30-minute bar not counted at all");
    }

    @Test
    @DisplayName("item 3: with only wide bars present, that width is the finest and is used")
    void aSingleWidthIsItsOwnFinest() {
        long open = 1_795_000_000_000L;
        List<FeedGap.Published> only30m = List.of(
                new FeedGap.Published(open, open + 1_800_000L, open + 9_000_000L),
                new FeedGap.Published(open + 1_800_000L, open + 3_600_000L, open + 9_000_000L));
        FeedGap gap = new FeedGap();
        gap.measure(only30m);
        // the rule is "smallest PRESENT", not "30 seconds": a drain with no fine bars still
        // measures rather than silently reporting nothing
        assertEquals(1_800_000L, gap.finestWidthMs());
        assertEquals(2, gap.lateBars());
        assertNotNull(gap.label());
    }

    @Test
    @DisplayName("item 3: a drain with none of the finest width leaves the measurement standing")
    void anEmptyFinestDrainDoesNotWipeTheWindow() {
        long open = 1_795_000_000_000L;
        FeedGap gap = new FeedGap();
        // TWO consecutive late bars, because C-107 needs two to make an episode and this test is
        // about the empty drain, not about the episode rule.
        gap.measure(List.of(
                new FeedGap.Published(open, open + 30_000L, open + 9_000_000L),
                new FeedGap.Published(open + 30_000L, open + 60_000L, open + 9_000_000L)));
        String first = gap.label();
        assertNotNull(first);
        gap.measure(new ArrayList<>());          // nothing at all
        assertEquals(first, gap.label(), "an empty drain is not evidence the gap closed");
    }
}

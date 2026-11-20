package app.feedgateway.readable;

import java.util.ArrayList;
import java.util.List;
import java.util.NavigableMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;

/**
 * What the readable page needs from the two overnight inputs, held in memory and <b>bounded</b>.
 *
 * <p><b>Why it keeps the bars rather than a running grid.</b> C-60's backfill rebuilds the whole
 * span at every write, so the page is a pure function of the bars held here. A running grid would
 * have to be corrected when a late bar arrived, and correcting an aggregate is the thing backfill
 * exists to avoid. Keeping the bars costs one night of one-minute bars — about a thousand — which
 * is nothing, and makes the rebuild trivially right.
 *
 * <p><b>The bound is a night, not a count.</b> Bars older than the previous cash close are dropped
 * on every add, so the footprint cannot grow across sessions even if the process runs for days. A
 * count-based cap would silently drop the START of the night, which is the one part of the grid that
 * never changes again and the part a reader checks first.
 *
 * <p><b>The price and the bars are kept apart on purpose.</b> They are different chains: on
 * 2026-11-20 the bars stopped for forty minutes while {@code underlying.spx.price} ran on. Folding
 * them into one "latest state" would have made a stalled bar look like a current price, which is
 * precisely the ten-point error C-60's addendum was written to prevent.
 */
public final class OvernightSource {

    /**
     * Keyed by <b>timeframe then bar start</b>, so a bar re-read after a restart replaces rather
     * than duplicates, and a 30-second bar never collides with the minute that contains it.
     *
     * <p><b>Concurrent, and C-96 item 2 is what a plain {@code TreeMap} here actually cost.</b>
     * On 2026-11-20 {@code /overnight} showed <b>26 of 51</b> slots while the bars for the missing
     * minutes were on the topic the whole time — measured: 331 distinct one-minute bar starts
     * covering 23:16Z to 04:46Z continuously. The gateway was not failing to read them. It was
     * failing to <b>keep</b> them:
     *
     * <pre>
     * Feed gateway state consumer error: Cannot read field "left" because "&lt;local2&gt;" is null
     * java.lang.NullPointerException
     *   at java.util.TreeMap.rotateLeft
     *   at java.util.TreeMap.fixAfterInsertion
     *   at java.util.TreeMap.addEntry
     *   at java.util.TreeMap.put
     *   at app.feedgateway.readable.OvernightSource.onBar(OvernightSource.java:68)
     * </pre>
     *
     * <p>Two threads call {@link #onBar}: the live state consumer, and the start-up drain that
     * replays the night's history. A {@code TreeMap} under concurrent {@code put} does not throw at
     * the collision — it <b>corrupts its own red-black structure</b> and throws later, inside a
     * rotation, from whichever thread gets there next. The exception killed the state consumer, and
     * whatever the drain had not yet inserted was simply lost. The surviving entries were the tail
     * of each series, which is why both the 1m and 30s counts stood at about 45% of what the topic
     * holds and why the holes were exactly the backfilled range.
     *
     * <p>A read-write race was there too, and that half is mine: {@link #bars(String)} returns an
     * unmodifiable <b>view</b> rather than a copy — I made it a view earlier the same day to stop
     * the page's rebuild allocating a list per request, which was part of the heap exhaustion. The
     * view is the right call for the heap and it put HTTP request threads on the same unsynchronised
     * structure. {@code ConcurrentSkipListMap} keeps both properties: it is a sorted
     * {@link NavigableMap}, so {@code headMap(...).clear()} and the grid's ordered iteration are
     * unchanged; its iterators are weakly consistent, so a reader cannot corrupt or be corrupted by
     * a concurrent write; and reads still allocate nothing.
     */
    private final java.util.Map<String, ConcurrentSkipListMap<Long, OvernightGrid.Bar>> bars =
            new ConcurrentHashMap<>();
    private volatile ReadablePage.Price lastPrice;

    /**
     * <b>C-98's rule, holding the previous cash close.</b>
     *
     * <p>It replaces a {@code lastIndexPrice} field that simply kept the newest index print. That
     * field was correct only while the premise behind it held — that the index prints during the
     * cash session and not otherwise — and on 2026-11-20 the index printed once a second through
     * the pre-market, so the newest print was a current price and {@code previousCashClose()}
     * returned it. See {@link IndexSilence} for the measurement and the rule.
     */
    private final IndexSilence silence = new IndexSilence();
    private volatile long nightStartMs;
    private volatile long barsRefused;
    private volatile long pricesRefused;

    /**
     * Index prints seen INSIDE the night, which are today's trading and not the previous close.
     *
     * <p>Published rather than private: on a pre-market day this counts into the thousands, and a
     * reader of the instrument needs to be able to tell "the index is printing and we are correctly
     * ignoring it" from "no index print has been seen".
     */
    private volatile long closeCandidatesInsideTheNight;

    /**
     * Set where the night starts. Everything before it is dropped here and ignored by the grid.
     *
     * <p>Called when the session rolls. It is explicit rather than derived, because the cash close
     * comes from the session calendar and this class must not hold a second copy of that rule.
     */
    public void nightStartsAt(long epochMs) {
        this.nightStartMs = epochMs;
        for (ConcurrentSkipListMap<Long, OvernightGrid.Bar> byStart : bars.values()) {
            byStart.headMap(epochMs, false).clear();
        }
    }

    /** The night's start as this source understands it. */
    public long nightStartMs() {
        return nightStartMs;
    }

    /** One bar. A bar before the night's start is refused and counted, never folded into it. */
    public void onBar(OvernightGrid.Bar bar) {
        if (bar == null) {
            barsRefused++;
            return;
        }
        if (nightStartMs > 0 && bar.startMs() < nightStartMs) {
            barsRefused++;
            return;
        }
        bars.computeIfAbsent(bar.timeframe(), k -> new ConcurrentSkipListMap<>())
                .put(bar.startMs(), bar);
    }

    /**
     * The newest price. An OLDER price never replaces a newer one: the two consumers may deliver
     * out of order after a seek, and a page that went backwards in time would be read as the market
     * going backwards.
     */
    public void onPrice(ReadablePage.Price price) {
        if (price == null) {
            pricesRefused++;
            return;
        }
        if (lastPrice != null && price.epochMs() < lastPrice.epochMs()) {
            pricesRefused++;
            return;
        }
        lastPrice = price;
        // EVERY price advances the feed's clock, index or not. Overnight the topic carries the
        // ES-basis derivation once a second while the index is quiet, and that is precisely when a
        // trailing silence has to become visible -- so the derivation is the clock that closes it.
        // Nothing is derived from its VALUE; only its instant is read.
        silence.observeClock(price.epochMs());
        if (isIndexPrint(price)) {
            silence.observe(price);
            // C-97 item 4: AN INDEX PRINT FROM INSIDE THIS NIGHT IS NOT THE PREVIOUS CLOSE.
            //
            // This single missing condition emptied the owner's page. On 2026-11-20
            // `underlying.spx.price` published source=NATIVE_SPX_INDEX / quality=LIVE_INDEX once
            // per second through the pre-market -- measured, thirteen consecutive records from
            // 10:50:53Z to 10:51:05Z. Every one of them passed isIndexPrint, correctly by its own
            // labels, and each one replaced `lastIndexPrice`. So `previousCashClose()` returned
            // the CURRENT price, and the page printed `close|7160.54|+0.00`: a close equal to now,
            // with a zero difference by construction.
            //
            // The damage went further than the label. ReadableBoardRenderer.onPriceJson treats a
            // moved reference as a session roll and calls nightStartsAt(reference), which clears
            // every bar before the new start -- so the night's start marched forward once a second
            // and the history behind it was deleted. `slots=0/1` and `bars night=0` are that loop,
            // not the map corruption it sat beside.
            //
            // The rule, and why it is this one: the previous close is the last index print from
            // BEFORE the night began. `>` and not `>=` because the print AT the night's start is
            // the close itself. Once a start is known the reference is frozen, which is what makes
            // the loop impossible rather than merely unlikely.
            //
            // WHAT THIS GIVES UP, stated rather than left to be found at 16:00: the reference will
            // not roll to TODAY's close on its own; it holds yesterday's until the night start is
            // set again. Rolling it properly needs a cash-close boundary, which is the session
            // calendar this class deliberately does not hold -- raised in the Discussion tab as its
            // own item. A frozen correct reference beats a reference that marches.
            if (nightStartMs > 0 && price.epochMs() > nightStartMs) {
                // Kept as a COUNTER only. C-97 used this condition to freeze the reference, and
                // C-98 replaced that with the silence rule -- which is stronger, because it also
                // survives the restart that re-resolves from history. The count stays because it
                // is the figure that says "the index is printing inside the night and we are
                // correctly not treating it as a close", which is exactly what was invisible
                // this morning.
                closeCandidatesInsideTheNight++;
            }
        }
    }

    /** The newest price, or null where none has arrived. */
    public ReadablePage.Price lastPrice() {
        return lastPrice;
    }

    /**
     * <b>The previous cash close, MEASURED rather than calculated.</b>
     *
     * <p>C-60 anchors the night on the previous SPX cash close. The obvious way to find it is a
     * session calendar, and this class deliberately does not hold one: a second copy of the
     * calendar's early-close rules here would be a figure that could silently disagree with the
     * platform's own.
     *
     * <p>Instead it is the <b>last print whose source was the index itself</b>. The SPX index stops
     * printing about a minute before the cash close and the topic then carries the ES-basis
     * derivation all night, so the last index print freezes exactly at the close and stays there
     * until the next session opens. That is the same figure the owner reads — on 2026-11-19 it was
     * 7176.24 at 15:58:54 New York, which is a last tick and not an exchange official close, and
     * the page says so.
     *
     * <p>Null before any index print has been seen, in which case the page has no reference price
     * and says that rather than inventing one.
     */
    public ReadablePage.Price previousCashClose() {
        return silence.reference();
    }

    /** Why there is no previous close, or null when there is one (C-98: never a live price). */
    public String closeAbsenceReason() {
        return silence.absenceReason();
    }

    /** The newest index print, reference or not. For the instrument and for /now's source label. */
    public ReadablePage.Price newestIndexPrint() {
        return silence.newest();
    }

    /** How many session-ending silences the index feed has shown this process. */
    public long indexSilencesSeen() {
        return silence.silencesSeen();
    }

    /**
     * <b>C-74</b>: seed the previous cash close from history at start-up.
     *
     * <p>It is taken ONLY when nothing has been learned from the live stream. A real index print
     * observed on the topic is the better fact and must always win over one read back at boot —
     * otherwise a restart would pin the page to a recovered value for the rest of the session even
     * after the index began printing again.
     */
    public void seedPreviousClose(ReadablePage.Price close) {
        // The reference resolved from history by the SAME rule (ReferencePrice runs
        // IndexSilence.resolveFrom over its slices), adopted only where nothing has been learned
        // live. One rule, two entry points -- and the rule itself lives in one class so the two
        // cannot drift.
        silence.adopt(close);
    }

    /**
     * Whether a price came from the index itself rather than from the ES-basis derivation.
     *
     * <p>Matched on the producer's own words. Overnight the topic reads {@code ES_BASIS_DERIVED} /
     * {@code LIVE_ES_BASIS}; during the cash session it reads the index source. Anything this
     * method does not recognise is treated as NOT an index print, so an unknown new source can
     * never be mistaken for a cash close — it would freeze a wrong reference price for a whole
     * night, and a wrong reference is worse than an absent one.
     */
    static boolean isIndexPrint(ReadablePage.Price price) {
        if (price == null) {
            return false;
        }
        String source = price.source() == null ? "" : price.source().toUpperCase(java.util.Locale.ROOT);
        String quality = price.quality() == null ? "" : price.quality().toUpperCase(java.util.Locale.ROOT);
        if (source.contains("BASIS") || quality.contains("BASIS")) {
            return false;                      // the overnight derivation, never a close
        }
        return source.contains("INDEX") || quality.contains("INDEX");
    }

    /**
     * Every bar of ONE timeframe, oldest first.
     *
     * <p>The caller names the timeframe because the two lengths answer different questions: the
     * owner's 30-second rows are built from {@code 30s}, the night's coarser grid from {@code 1m}.
     * There is deliberately no method that returns them all together — that would be the
     * double-count this class is keyed to prevent.
     */
    public java.util.Collection<OvernightGrid.Bar> bars(String timeframe) {
        ConcurrentSkipListMap<Long, OvernightGrid.Bar> byStart = bars.get(timeframe);
        // A VIEW, not a copy. This returned `new ArrayList<>(byStart.values())`, and that copy ran
        // on every render of the long page and on every /now and /overnight request -- three call
        // sites, one of them on the 30-second beat. It is the allocation that was holding the
        // stack when the heap finally ran out on 2026-11-20 (OvernightSource.java:163). Copying it
        // bought nothing: every caller iterates once and keeps nothing, and an unmodifiable view
        // is as safe against a caller that tries to add as a copy is.
        return byStart == null ? List.of()
                : java.util.Collections.unmodifiableCollection(byStart.values());
    }

    /** The timeframes this source is holding bars for. */
    public java.util.Set<String> timeframes() {
        return java.util.Set.copyOf(bars.keySet());
    }

    /** How many bars are held of one timeframe, for a health read. */
    public int barCount(String timeframe) {
        ConcurrentSkipListMap<Long, OvernightGrid.Bar> byStart = bars.get(timeframe);
        return byStart == null ? 0 : byStart.size();
    }

    /** How many bars are held in all, across every timeframe. */
    public int barCount() {
        int n = 0;
        for (ConcurrentSkipListMap<Long, OvernightGrid.Bar> byStart : bars.values()) {
            n += byStart.size();
        }
        return n;
    }

    /**
     * The newest bar's start of one timeframe, or 0 — what says whether the bar chain is behind the
     * price.
     */
    public long newestBarStartMs(String timeframe) {
        ConcurrentSkipListMap<Long, OvernightGrid.Bar> byStart = bars.get(timeframe);
        return byStart == null || byStart.isEmpty() ? 0L : byStart.lastKey();
    }

    /** Records refused because they fell outside the night, counted rather than silent. */
    public long barsRefused() {
        return barsRefused;
    }

    /** Prices refused for going backwards, counted. */
    /** How many index prints were seen inside the night and so not taken as the previous close. */
    public long closeCandidatesInsideTheNight() {
        return closeCandidatesInsideTheNight;
    }

    public long pricesRefused() {
        return pricesRefused;
    }
}

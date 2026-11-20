package app.feedgateway.readable;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

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
     */
    private final java.util.Map<String, TreeMap<Long, OvernightGrid.Bar>> bars =
            new java.util.LinkedHashMap<>();
    private ReadablePage.Price lastPrice;
    private ReadablePage.Price lastIndexPrice;
    private long nightStartMs;
    private long barsRefused;
    private long pricesRefused;

    /**
     * Set where the night starts. Everything before it is dropped here and ignored by the grid.
     *
     * <p>Called when the session rolls. It is explicit rather than derived, because the cash close
     * comes from the session calendar and this class must not hold a second copy of that rule.
     */
    public void nightStartsAt(long epochMs) {
        this.nightStartMs = epochMs;
        for (TreeMap<Long, OvernightGrid.Bar> byStart : bars.values()) {
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
        bars.computeIfAbsent(bar.timeframe(), k -> new TreeMap<>()).put(bar.startMs(), bar);
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
        if (isIndexPrint(price)) {
            lastIndexPrice = price;
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
        return lastIndexPrice;
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
        if (close != null && lastIndexPrice == null) {
            lastIndexPrice = close;
        }
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
        TreeMap<Long, OvernightGrid.Bar> byStart = bars.get(timeframe);
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
        TreeMap<Long, OvernightGrid.Bar> byStart = bars.get(timeframe);
        return byStart == null ? 0 : byStart.size();
    }

    /** How many bars are held in all, across every timeframe. */
    public int barCount() {
        int n = 0;
        for (TreeMap<Long, OvernightGrid.Bar> byStart : bars.values()) {
            n += byStart.size();
        }
        return n;
    }

    /**
     * The newest bar's start of one timeframe, or 0 — what says whether the bar chain is behind the
     * price.
     */
    public long newestBarStartMs(String timeframe) {
        TreeMap<Long, OvernightGrid.Bar> byStart = bars.get(timeframe);
        return byStart == null || byStart.isEmpty() ? 0L : byStart.lastKey();
    }

    /** Records refused because they fell outside the night, counted rather than silent. */
    public long barsRefused() {
        return barsRefused;
    }

    /** Prices refused for going backwards, counted. */
    public long pricesRefused() {
        return pricesRefused;
    }
}

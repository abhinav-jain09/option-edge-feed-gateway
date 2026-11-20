package app.feedgateway.readable;

import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * <b>C-61 and C-63</b>: holds the overnight inputs, renders the readable page on a fixed cadence,
 * and hands out the bytes that were last rendered.
 *
 * <p><b>Rendered on a schedule, not per request.</b> C-61 says the service writes the page every 30
 * seconds, and the first line carries the time it was written. A render per request would make that
 * time meaningless — it would always equal the read time, and a reader could no longer tell a page
 * the service is still writing from one it stopped writing an hour ago. The cadence also bounds the
 * work: one render per interval however many readers there are.
 *
 * <p><b>The page is never empty and never an error.</b> Before the first render it says so; with no
 * price it says so; with no board it says so. A reader at a secret address cannot tell a 500 from a
 * wrong address, so this never answers with one.
 *
 * <p><b>It decides nothing</b> (C-63). The board's gamma, sign and reading come from the map
 * service's published record; the overnight prices come from the platform's own
 * {@code spxEq} fields; the reference price is the last index print. Nothing here computes a
 * reading, and nothing applies a basis.
 */
public final class ReadableBoardRenderer {

    /** C-61's cadence. */
    public static final long RENDER_INTERVAL_MS = 30_000L;

    /**
     * The timeframe the night's coarse grid is built from. Minutes, not 30-second bars: the grid's
     * slots are 15 minutes wide, so minutes are half the records for the same answer. C-64's Recent
     * table uses {@code 30s} instead, and the two never share a slot.
     */
    public static final String NIGHT_TIMEFRAME = "1m";

    /** The timeframe C-64's 30-second rows are built from — the owner's own resolution. */
    public static final String RECENT_TIMEFRAME = "30s";

    private final OvernightSource source = new OvernightSource();
    private final PreopenSource preopen = new PreopenSource();
    /** C-65's path of sides. Fed from the indicators snapshot, rebuilt at start-up by C-71. */
    private final VwapSideTracker sides = new VwapSideTracker();
    private volatile String indicatorsJson;
    /**
     * The instant of the newest indicators snapshot, from the record's OWN {@code asOfEventTime}.
     *
     * <p>Not a wall clock and not the arrival time: the snapshot that froze on 2026-11-19 kept
     * being republished for hours with the same {@code asOfEventTime}, so arrival would have
     * reported it as current. The question the page answers is "how old is the measurement", and
     * only the record's own instant answers it.
     */
    private volatile long indicatorsAtMs;
    private volatile String basisJson;
    private volatile String profileJson;
    private final Supplier<String> boardJson;
    private final LongSupplier clock;

    private volatile String page = "NOT YET RENDERED — the gateway has not written this page yet. "
            + "It is written every 30 seconds; if this line persists, the renderer is not running.\n";
    private volatile long renderedAtMs;
    private long nightStartedAt;
    /** Moments the side path has observed. Counted so a flat zero is visible on /metrics. */
    private volatile long sideObservations;

    /** C-74: where the reference price came from, in words, for the page. */
    private volatile String referenceLabel;
    /** C-133: the resolution's own words, printed rather than discarded. */
    private volatile String referenceReason;

    /** Whether a pre-open board has ever been served, which is what makes a switch a switch. */
    private boolean servedPreopen;
    /** When the map record first replaced it. Recorded once, so the line does not drift. */
    private long switchedAtMs;

    /**
     * @param boardJson the map service's own cached record for the chain being shown, or null
     * @param clock     the wall clock, injected so a test renders a deterministic page
     */
    public ReadableBoardRenderer(Supplier<String> boardJson, LongSupplier clock) {
        this.boardJson = boardJson == null ? () -> null : boardJson;
        this.clock = clock == null ? System::currentTimeMillis : clock;
    }

    /** One footprint bar, as the gateway received it. Unreadable records are counted, not fatal. */
    public void onBarJson(String json) {
        OvernightGrid.Bar bar = OvernightDecoders.bar(json);
        if (bar != null) {
            source.onBar(bar);
        }
    }

    /**
     * One pre-open gamma record, as the gateway received it off
     * {@code options.databento.gex.strike.preopen}.
     *
     * <p>This is what lets the owner read his ladder at 06:30 New York. The map service consumes
     * only {@code options.databento.gex.strike} and so has nothing to publish until the cash path
     * comes round; measured on 2026-11-19 its first record was 09:15:58, while the pre-open board
     * was already complete at 06:43:02.
     */
    /**
     * <b>C-74</b>: take the previous cash close from history at start-up.
     *
     * <p>Called once by the gateway after it has read back the price topic and the cash-session
     * bars. Without it, a gateway that restarts overnight has never seen an index print, so the
     * owner's overnight rows stay empty until the cash open — precisely when he does not need
     * them. A live index print still wins over a recovered one; this only fills a gap.
     */
    public void seedReference(ReferencePrice.Resolved resolved) {
        if (resolved == null) {
            return;
        }
        referenceLabel = resolved.label();
        referenceReason = resolved.reason();
        // C-133: THE RESOLUTION SAYS SO, in the log and on the page.
        //
        // The reason string already existed and was discarded at this line. On 2026-11-20 it held
        // exactly what an hour of investigation had to recover by hand -- which slice answered, and
        // therefore that the answer had come out of a capped read. A figure that cannot say where
        // it came from cannot be checked, so from here it is printed where both the operator and
        // the owner can see it.
        System.out.println("readable reference resolved from=" + resolved.from()
                + " close=" + (resolved.close() == null ? "none"
                        : resolved.close().spx() + "@"
                                + ReadablePage.hms(resolved.close().epochMs(), ReadablePage.NY)
                                + "NY")
                + " reason=" + resolved.reason());
        if (resolved.close() != null) {
            source.seedPreviousClose(resolved.close());
            nightStartedAt = resolved.close().epochMs();
            source.nightStartsAt(nightStartedAt);
        }
    }

    /** Where the reference price came from, or null before anything has been resolved. */
    public String referenceLabel() {
        return referenceLabel;
    }

    /** The resolution's own reason, or null before anything has been resolved. */
    public String referenceReason() {
        return referenceReason;
    }

    /**
     * <b>C-133</b>: one line on {@code /now} saying how the previous close was reached.
     *
     * <p>Empty before anything has been resolved, because an empty line is better than a line that
     * says nothing. It is appended rather than threaded through {@link Pages#now} on purpose: the
     * page's signature is long already, and this is a property of the resolution rather than of
     * anything the page is rendering.
     */
    String referenceLine() {
        if (referenceReason == null || referenceReason.isBlank()) {
            return "";
        }
        return "reference|" + (referenceLabel == null ? "" : referenceLabel + "|")
                + referenceReason + "\n";
    }

    public void onPreopenJson(String json) {
        preopen.observe(json);
    }

    /** Moments the side path has observed, replayed ones included. For /metrics. */
    public long sideObservations() {
        return sideObservations;
    }

    /**
     * Rows the board currently has, for /metrics. Zero where there is no board at all.
     *
     * <p>Read together with the pre-open record count this tells an operator which of two very
     * different situations he is in: no records at all, or records that do not reach the grid.
     */
    public int boardRowsForMetrics() {
        ReadablePage.Board b = currentBoard(clock.getAsLong());
        return b == null ? 0 : b.rowsPresent();
    }

    /** The pre-open store, for the status page and the tests. */
    /**
     * <b>C-120</b>: the published per-strike deltas, so the board rows and the {@code delta|}
     * lines carry the producer's own figures.
     */
    private final StrikeDeltaSource strikeDeltas = new StrikeDeltaSource();

    /**
     * One record off the LIVE Databento strike topic.
     *
     * <p>The caller gates on that topic by name. The gateway binds the same {@code gex-by-strike}
     * event to four topics -- the Databento live and history ones and the IBKR live and history
     * ones -- and a history row or another producer's row in the live board would be a wrong delta
     * on a strike the owner is trading, with nothing on the page to say where it came from.
     */
    public void onGexStrikeJson(String json) {
        strikeDeltas.observe(json);
    }

    /** C-120: the delta store, for the metrics page and a readiness probe. */
    public StrikeDeltaSource strikeDeltas() {
        return strikeDeltas;
    }

    public PreopenSource preopenSource() {
        return preopen;
    }

    /**
     * One ES indicators snapshot, as the gateway received it.
     *
     * <p>Feeds C-69's VWAP cells and C-65's path of sides. The side path is advanced with
     * <b>this snapshot's own price against this snapshot's own VWAP</b> — the only comparison that
     * is ever true. The snapshot carries {@code currentPrice} beside {@code asOfEventTime}, so
     * nothing is borrowed from a neighbouring record.
     */
    public void onIndicatorsJson(String json) {
        if (json == null || json.isBlank()) {
            return;
        }
        if (!VwapRebuild.isForReadablePages(json)) {
            // The OTHER instrument's snapshot. Both indicator CURRENT topics are bound, and they
            // publish at the same instants, so without this the cells below showed whichever
            // record happened to arrive last while the side path (which drops them) kept
            // measuring against ES. See VwapRebuild.READ_SYMBOL.
            otherInstrumentSnapshots++;
            return;
        }
        indicatorsJson = json;
        // The held figures, BEFORE the usability gate below: this record may be the one that
        // carries the absence, and the line still has to say when the last present figure was
        // read. See VwapHeld.
        VwapRead read = VwapRead.of(json);
        Long at = VwapRebuild.instantOf(json);
        if (read != null && at != null) {
            vwapHeld.observe("vwapes", read.session(), at);
            vwapHeld.observe("vwaprth", read.rth(), at);
            // The movement of the two VWAP figures, at the RECORD's own instant, so a latched
            // figure's age survives a restart of this gateway (a clock of ours would restart with
            // it). A LATCHED figure usually keeps moving -- measured, see LatchWatch.
            latches.observe("vwapes", read.session() == null ? null
                    : read.session().vwap() == null ? null : read.session().vwap().value(), at);
            latches.observe("vwaprth", read.rth() == null ? null
                    : read.rth().vwap() == null ? null : read.rth().vwap().value(), at);
        }
        VwapRebuild.Moment m = VwapRebuild.decode(json);
        if (m != null) {
            sides.observe(m.atMs(), m.price(), m.vwap(), m.upper1(), m.lower1(), m.anchor());
            sideObservations++;
            if (m.atMs() > indicatorsAtMs) {
                indicatorsAtMs = m.atMs();
            }
        }
    }

    /** One basis state, as published — plain JSON on {@code spx.basis.state}, never Avro. */
    /**
     * Snapshots refused because they are not {@link VwapRebuild#READ_SYMBOL}'s. Counted rather
     * than dropped silently: on dev this climbs at the same rate as the accepted ones, and a zero
     * here where the SPX topic is bound would mean the filter is not running.
     */
    private long otherInstrumentSnapshots;

    /**
     * <b>When each VWAP figure last changed</b>, so {@code (LATCHED)} can say whether the figure is
     * still moving. See {@link LatchWatch} for the measurement this closes.
     */
    private final LatchWatch latches = new LatchWatch();

    /** The movement store, for a readiness probe and the metrics page. */
    public LatchWatch latches() {
        return latches;
    }

    /** Bars PUBLISHED long after their own minute, so the headline can say so. */
    private final FeedGap feedGap = new FeedGap();

    /**
     * Measure the feed gap from these records' own broker stamps.
     *
     * <p>Not fed from {@link #onBarJson}: that path has no broker timestamp, and the gateway's own
     * arrival clock cannot survive a restart (see {@link FeedGap}). The service measures this from
     * a bounded read of the bars topic, at start-up and on a cadence.
     */
    public void measureFeedGap(java.util.Collection<FeedGap.Published> bars) {
        feedGap.measure(bars);
    }

    /** What the last measurement found. Public so the service can log and publish it. */
    public FeedGap feedGap() {
        return feedGap;
    }

    /** The last present figure of each VWAP line, so the line never loses it. */
    private final VwapHeld vwapHeld = new VwapHeld();

    VwapHeld vwapHeld() {
        return vwapHeld;
    }

    long otherInstrumentSnapshots() {
        return otherInstrumentSnapshots;
    }

    public void onBasisJson(String json) {
        if (json != null && !json.isBlank()) {
            basisJson = json;
        }
    }

    /** One gamma-zone profile, for C-66's footprint-by-price page. */
    public void onProfileJson(String json) {
        if (json != null && !json.isBlank()) {
            profileJson = json;
        }
    }

    /**
     * <b>C-71</b>: rebuild the session's path of sides from snapshot history at start-up.
     *
     * <p>Called once by the gateway with the snapshots of this session. Without it, a restart left
     * the owner's crossings and holding times at zero, which reads as a quiet session rather than
     * a short memory.
     */
    public int rebuildSides(java.util.Collection<String> snapshots) {
        int replayed = VwapRebuild.replay(sides, snapshots);
        sideObservations += replayed;
        return replayed;
    }

    /** One SPX price record, as the gateway received it. */
    public void onPriceJson(String json, long recordTimestampMs) {
        ReadablePage.Price price = OvernightDecoders.price(json, recordTimestampMs);
        if (price != null) {
            source.onPrice(price);
            ReadablePage.Price reference = source.previousCashClose();
            // C-97 item 4, THE SECOND HALF OF THE LOOP, and it is guarded here as well as in
            // OvernightSource on purpose.
            //
            // What the old condition believed: "the reference moved, so the session rolled". What
            // it actually did on 2026-11-20: the pre-market index feed replaced the reference once
            // a second, this branch read every one of those as a session roll, and
            // nightStartsAt(now) cleared every bar before it. The night's start marched forward
            // and the page went from 26 of 51 slots to 0 of 1 in fourteen minutes.
            //
            // OvernightSource now freezes the reference, so this branch cannot fire that way any
            // more -- and that is exactly why the guard belongs here too. A loop this expensive
            // should not be prevented by one condition in one class: the next person to relax the
            // freeze would restore it silently. So the start may be set when there is none, and
            // may be moved EARLIER (a correction learned from history), and is never moved
            // FORWARD into the live session on the strength of a price. Rolling it forward at a
            // real cash close needs a session boundary this class does not hold; that is an open
            // item in the Discussion tab, not something to infer from a tick.
            if (reference != null
                    && (nightStartedAt == 0L || reference.epochMs() < nightStartedAt)) {
                nightStartedAt = reference.epochMs();
                source.nightStartsAt(nightStartedAt);
            }
        }
    }

    /**
     * Render once. Called on the cadence; safe to call more often.
     *
     * @return the page just rendered
     */
    public String render() {
        long now = clock.getAsLong();
        // THE INSTRUMENT, before anything else is attempted, so the line is there even on the
        // render that fails: 2026-11-20's OutOfMemoryError left no figure anywhere saying how the
        // heap had got there, and no histogram could be taken because the image is a JRE.
        System.out.println(HeapGuard.line(source.barCount(NIGHT_TIMEFRAME),
                source.barCount(RECENT_TIMEFRAME)));
        ReadablePage.Price reference = source.previousCashClose();
        ReadablePage.Overnight overnight = null;
        boolean skippedForHeap = false;
        if (reference != null) {
            if (HeapGuard.low()) {
                // The page's own rule, restored: a read that cannot be taken is published ABSENT
                // WITH ITS REASON, and no one block may refuse the whole page. On 2026-11-20 this
                // block threw OutOfMemoryError and took /now with it for seventeen minutes while
                // liveness answered 200 and restartCount stayed 0.
                skippedForHeap = true;
            } else {
                overnight = OvernightGrid.build(now, reference.epochMs(), reference.spx(),
                        source.bars(NIGHT_TIMEFRAME), null, null);
            }
        }
        String json = boardJson.get();
        ReadablePage.Board board = json == null ? null
                : BoardLines.from(json, boardCycleMs(json, now), strikeDeltas, now);
        if (board == null) {
            // The map record is always preferred where there is one: it is the fuller board and it
            // carries the what-if readings. The pre-open board stands in only while there is no map
            // record at all, and it names itself on the page, so the handover is visible rather
            // than silent. It is never merged with the map board -- two sources in one ladder
            // would leave the owner unable to say which rows he was reading.
            board = preopen.board(now);
            if (board != null) {
                servedPreopen = true;
            }
        } else if (servedPreopen) {
            // C-73: the switch is ANNOUNCED, with the time it happened. The owner will have been
            // reading the pre-open ladder for two and a half hours; a silent swap to a different
            // board with different columns -- and a different definition of the same figures --
            // is exactly the kind of change he should not have to notice for himself. The instant
            // is recorded ONCE, so the line keeps saying when it happened rather than "just now".
            if (switchedAtMs == 0L) {
                switchedAtMs = now;
            }
            board = new ReadablePage.Board(board.cycleMs(), board.spot(), board.rowsPresent(),
                    board.rowsExpected(),
                    board.source() + " (switched from " + PreopenBoard.SOURCE + " at "
                            + ReadablePage.hms(switchedAtMs, ReadablePage.NY) + " New York)",
                    board.largestAbove(), board.largestBelow(), board.lines());
        }
        String rendered = ReadablePage.render(now, source.lastPrice(), overnight, board,
                stalledInputs());
        if (skippedForHeap) {
            rendered = rendered + "\nOVERNIGHT ROWS ABSENT — " + HeapGuard.reason() + "\n";
        }
        if (reference == null) {
            rendered = rendered + "\nNO REFERENCE PRICE YET — the previous cash close is taken from "
                    + "the last print of the SPX index itself, and this gateway has not seen one "
                    + "since it started. The overnight rows appear once it has.\n"
                    + (referenceLabel == null ? "" : "  " + referenceLabel + "\n");
        } else if (referenceLabel != null) {
            // C-74: an index print and a futures bar's SPX-equivalent are different facts, so the
            // page names which one this close is rather than leaving the reader to assume.
            // C-133: and HOW it was reached, so a close out of a capped read is visible as one.
            rendered = rendered + "\nREFERENCE — " + referenceLabel
                    + (referenceReason == null ? "" : " (" + referenceReason + ")") + "\n";
        }
        page = rendered;
        renderedAtMs = now;
        return rendered;
    }

    /**
     * The inputs <b>{@code /now}</b> depends on — both of them.
     *
     * <p>This method exists because the long page's list did not do. I wrote into that list's
     * documentation that the VWAP and holding inputs "belong to {@code /now}, which lists them
     * itself" — and then never added them, so {@code /now} named the footprint bars and nothing
     * else. The comment asserted something the code did not do.
     *
     * <p>It mattered in the one place it could matter most: the indicator snapshot is the feed
     * that is actually down, frozen since 23:16Z with the footprint. A reader of {@code /now}
     * would have seen the VWAP cells holding their last figures with a state code and <b>no line
     * anywhere saying that feed had stopped</b> — which is the exact failure the stalled lines
     * exist to prevent.
     */
    private java.util.List<StalledInputs.Input> nowStalledInputs() {
        return java.util.List.of(
                new StalledInputs.Input(
                        "footprint bars", source.newestBarStartMs(RECENT_TIMEFRAME),
                        "The 30-second rows' footprint figures and the footprint-by-price page"),
                new StalledInputs.Input(
                        "ES indicators snapshot", indicatorsAtMs,
                        "The VWAP line with its bands, the side and the holding times"));
    }

    /**
     * The inputs THIS page depends on, with the newest instant the gateway has actually seen for
     * each. Only the footprint bars are listed here: this page's tables are the overnight grid and
     * the gamma board, and the board's own freshness is already on its heading as a cycle time.
     * The VWAP and holding inputs belong to {@code /now}, which lists them itself — a page that
     * warned about an input it does not show would be noise.
     */
    private java.util.List<StalledInputs.Input> stalledInputs() {
        return java.util.List.of(new StalledInputs.Input(
                "footprint bars", source.newestBarStartMs(NIGHT_TIMEFRAME),
                "The overnight slots and their closes, volumes and buyer/seller counts"));
    }

    /**
     * The board's own cycle instant, from the record's fingerprint where it carries one.
     *
     * <p>Falls back to the render time rather than guessing, and the page's heading then shows that
     * — which is visibly wrong rather than quietly wrong, and a board whose own time cannot be
     * read is a board somebody should look at.
     */
    private static long boardCycleMs(String json, long fallback) {
        Long at = BoardCycle.epochMsOf(json);
        return at == null ? fallback : at;
    }

    /**
     * C-66's pages, rendered on demand from what the gateway holds.
     *
     * <p>Unlike the long page these are not cached on the 30-second beat: each is a formatting
     * pass over state that is already in memory, and a reader who asks for {@code /board} twice in
     * a second costs nothing. The first line of each still carries the render instant, so a reader
     * can always tell when what he is looking at was assembled.
     *
     * @param name one of board, now, overnight, footprint, recent-full, index
     * @return the page, or null where that name is not a page
     */
    public String namedPage(String name) {
        return namedPage(name, 1);
    }

    /** As {@link #namedPage(String)}, for one part of a paged page (1-based). */
    public String namedPage(String name, int part) {
        long nowMs = clock.getAsLong();
        ReadablePage.Price last = source.lastPrice();
        ReadablePage.Board board = currentBoard(nowMs);
        switch (name == null ? "" : name) {
            case "board":
                return Pages.board(nowMs, last, board);
            case "now": {
                // the flag is read BEFORE the page is built, because building it moves the heap
                boolean lowHeap = HeapGuard.low();
                return withOvernightAbsence(Pages.now(nowMs, last, overnightNow(nowMs),
                        indicatorsJson == null ? null : VwapRead.of(indicatorsJson),
                        basisJson == null ? null : BasisRead.of(basisJson),
                        sides.result(), recentRows(), board, nowStalledInputs(), vwapHeld,
                        feedGap.label(),
                        // C-98 item 5: the reason the close is absent, where it is. On low heap the
                        // block is skipped and the heap's own reason is appended by
                        // withOvernightAbsence; here the reason is the reference's, which is the
                        // silence rule's: "no silence of three hours or more has been seen yet".
                        lowHeap ? HeapGuard.reason() : source.closeAbsenceReason(),
                        latches, BoardLines.expiryOf(boardJson.get())), lowHeap)
                        + referenceLine();
            }
            case "overnight": {
                boolean lowHeap = HeapGuard.low();
                return withOvernightAbsence(
                        Pages.overnight(nowMs, last, overnightNow(nowMs)), lowHeap);
            }
            case "footprint":
                // C-96 item 1: the CENTRE is the live price, not the board's spot. The board is
                // absent for most of the day the owner reads this page.
                return onePage(Pages.footprint(nowMs, last, footprintCentre(last),
                        footprintLevels(last), basisOrNull()), part);
            case "recent-full":
                return onePage(Pages.recentFull(nowMs, last, recentRows()), part);
            case "index":
                return Pages.index(nowMs, last, pageStats(nowMs));
            default:
                return null;
        }
    }

    /**
     * One part of a paged page, 1-based, or a line saying which parts exist.
     *
     * <p>C-66 splits rather than cuts, so a page that does not fit is several pages and the reader
     * has to be able to ask for the next one. Asking for a part that is not there is answered with
     * the count rather than with nothing, because a blank page cannot be told from a wrong address.
     */
    private static String onePage(java.util.List<String> parts, int part) {
        if (parts == null || parts.isEmpty()) {
            return "empty|no rows for this page yet\n";
        }
        int index = part <= 0 ? 1 : part;
        if (index > parts.size()) {
            return "nosuchpart|asked for " + index + " of " + parts.size()
                    + "|parts are numbered 1 to " + parts.size() + "\n";
        }
        return parts.get(index - 1);
    }

    /**
     * <b>C-96 item 1: the footprint levels near the LIVE price, with no gamma board required.</b>
     *
     * <p>What this page showed before, and why it showed nothing. Both the centre and the levels
     * came from the gamma board — {@code board == null} returned an empty list and
     * {@code board.spot()} was the centre — so overnight, pre-market, and any other hour with no
     * board, {@code /footprint} printed {@code spot=absent|levels=0} while the data it needed was
     * on the broker the whole time. Measured on 2026-11-20 at 10:26:50Z:
     * {@code options.spx.gamma-zone.profile}'s newest record was 47 seconds old. <b>The profile
     * was never the missing part; the board gate was.</b>
     *
     * <p>The centre is now {@link #footprintCentre}, the live SPX-equivalent price — which is the
     * figure the page's own first line already prints, so the levels are now centred on the number
     * the reader is looking at rather than on a board that may be hours stale or absent.
     *
     * <p>An empty list still means something, and it means one thing now instead of two: <b>no
     * profile has been published</b>, or no level of it lies within the radius of the live price.
     * It no longer means "there is no gamma board", which was the case that made the page useless
     * at exactly the hours the owner asked for it.
     */
    private java.util.List<FootprintByPrice.Level> footprintLevels(ReadablePage.Price last) {
        Double centre = footprintCentre(last);
        if (profileJson == null || centre == null) {
            return java.util.List.of();
        }
        java.util.List<FootprintByPrice.Level> all =
                FootprintByPrice.levels(profileJson, basisOrNull());
        return FootprintByPrice.near(all, centre, BoardLines.GRID_RADIUS, basisOrNull());
    }

    /**
     * The price the footprint window is centred on: the live SPX-equivalent print, or null.
     *
     * <p>Null and not a zero, for the reason the page's own comment gives: a primitive would print
     * {@code spot=0.00}, a price the index has never traded at, and an absence that reads as a
     * quotation is worse than an absence that says so.
     */
    private Double footprintCentre(ReadablePage.Price last) {
        return last == null ? null : last.spx();
    }

    private BasisRead basisOrNull() {
        return basisJson == null ? null : BasisRead.of(basisJson);
    }

    /** What /index lists: every page, when it was assembled and how long it is. */
    private java.util.List<Pages.PageStat> pageStats(long nowMs) {
        java.util.List<Pages.PageStat> out = new java.util.ArrayList<>();
        for (String name : PAGES) {
            String body = "index".equals(name) ? null : namedPage(name, 1);
            out.add(new Pages.PageStat("/" + name, nowMs, body == null ? 0 : body.length()));
        }
        return out;
    }

    /** Every page name this renderer answers to, in the order /index lists them. */
    public static final java.util.List<String> PAGES =
            java.util.List.of("board", "now", "overnight", "footprint", "recent-full", "index");

    /** The 30-second rows C-64 asks for, from the bars the gateway already holds. */
    private java.util.List<RecentRows.Row> recentRows() {
        return RecentRows.build(clock.getAsLong(), RecentRows.DEFAULT_ROWS,
                source.bars(RECENT_TIMEFRAME));
    }

    /** The overnight grid as the long page builds it, shared so the two cannot disagree. */
    private ReadablePage.Overnight overnightNow(long nowMs) {
        ReadablePage.Price reference = source.previousCashClose();
        if (reference == null) {
            return null;
        }
        if (HeapGuard.low()) {
            // /now and /overnight are rendered on demand and took the same OutOfMemoryError as
            // the cadence render did. Absent is a page the owner can read; a 500 is not.
            return null;
        }
        return OvernightGrid.build(nowMs, reference.epochMs(), reference.spx(),
                source.bars(NIGHT_TIMEFRAME), null, null);
    }

    /**
     * The heap's reason, appended to an on-demand page, or the page unchanged.
     *
     * <p><b>This closes a defect in my own guard, and the defect was a page telling the owner
     * something false.</b> On low heap {@link #overnightNow} returns null, and
     * {@code Pages.overnight} then falls into its pre-existing branch and prints "no reference
     * price yet: the previous cash close is taken from the last print of the SPX index itself and
     * none has been seen". A reference price exists; the heap is the reason. The cadence page had
     * the truth appended and these two did not, so the one page the owner reads would have named
     * the wrong cause on exactly the failure we are watching for.
     *
     * <p>It is appended rather than threaded through {@code Pages.now} and
     * {@code Pages.overnight} so that the line is the SAME sentence on all three pages: two
     * spellings of one absence is how they come to disagree.
     */
    String withOvernightAbsence(String page, boolean skipped) {
        if (page == null || !skipped) {
            return page;
        }
        return page + "\nOVERNIGHT ROWS ABSENT — " + HeapGuard.reason() + "\n";
    }

    /** The board the pages show: the map record where there is one, else the pre-open board. */
    private ReadablePage.Board currentBoard(long nowMs) {
        String json = boardJson.get();
        ReadablePage.Board board = json == null ? null
                : BoardLines.from(json, boardCycleMs(json, nowMs), strikeDeltas, nowMs);
        return board != null ? board : preopen.board(nowMs);
    }

    /** The bytes to serve: whatever was last rendered. Never null, never empty. */
    public String page() {
        return page;
    }

    /** When the served page was rendered, so a watchdog can see the renderer has stopped. */
    public long renderedAtMs() {
        return renderedAtMs;
    }

    /** How many bars the night is holding, across every timeframe, for a health read. */
    public int barCount() {
        return source.barCount();
    }

    /** The store, for the end-to-end test to assert against. */
    OvernightSource source() {
        return source;
    }
}

package app.feedgateway;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/** ES-FOOTPRINT-STRIKE-INTERACTION.md R14/R18/R20 — the gateway fold, without a broker. */
class FootprintStrikeViewTest {

    static String episode(String kind, String session, String tf, long strike, long open, long revision, long seenMax, String tag) {
        return episode(kind, "ES.v.0", session, tf, strike, open, revision, seenMax, tag);
    }
    static String episode(String kind, String symbol, String session, String tf, long strike, long open, long revision, long seenMax, String tag) {
        return "{\"kind\":\"" + kind + "\",\"symbol\":\"" + symbol + "\",\"sessionDate\":\"" + session + "\",\"timeframe\":\"" + tf + "\",\"strikeCents\":" + strike
                + ",\"openBarStartMs\":" + open + ",\"revision\":" + revision + ",\"final\":" + "CLOSE".equals(kind) + ",\"closeReason\":" + ("CLOSE".equals(kind) ? "\"LEFT_BAND\"" : "null")
                + ",\"approach\":\"UNKNOWN\",\"suspended\":false,\"unreadableBars\":0,\"barsObserved\":1,\"seenMaxBarStartMs\":" + seenMax
                + ",\"prevAdmittedCloseCents\":null,\"prevAdmittedBarStartMs\":null,\"firstBarStartMs\":" + open + ",\"lastBarStartMs\":" + open
                + ",\"seriesTruncated\":false,\"seriesOmittedCount\":0,\"bandCents\":250,\"series\":[{\"tag\":\"" + tag + "\"}]}";
    }
    static String checkpoint(String session, String tf, long seenMax) {
        return "{\"kind\":\"CHECKPOINT\",\"symbol\":\"ES.v.0\",\"sessionDate\":" + (session == null ? "null" : "\"" + session + "\"") + ",\"timeframe\":\"" + tf + "\",\"seenMaxBarStartMs\":" + seenMax
                + ",\"prevAdmittedCloseCents\":null,\"prevAdmittedBarStartMs\":null,\"bandCents\":250}";
    }
    /** An episode at 6800 opening at {@code open}: OPEN for revision 0, UPDATE otherwise. */
    static String e(long open, long revision, String tag) { return episode(revision == 0 ? "OPEN" : "UPDATE", "2026-09-10", "1m", 680_000, open, revision, open + revision, tag); }
    private static FootprintStrikeView view() { return new FootprintStrikeView(new ObjectMapper(), 262_144, 1 << 20, 1000, 1000); }
    private static List<String> tags(FootprintStrikeView.Page p) { return p.records().stream().map(r -> r.replaceAll(".*\"tag\":\"([^\"]+)\".*", "$1")).toList(); }
    private static FootprintStrikeView.Page latest(FootprintStrikeView v, String tf, String session) { return v.latest("ES.v.0", tf, session, -1, 200); }
    private static FootprintStrikeView.Page history(FootprintStrikeView v, long strike) { return v.history("ES.v.0", "1m", strike, "", 100); }

    @Test void theFoldTakesTheGreatestRevisionPerIdentityWhateverTheArrivalOrder() {
        FootprintStrikeView v = view();
        assertEquals(FootprintStrikeView.Reason.ADMITTED, v.admit(e(100, 2, "r2")).reason());
        assertEquals(FootprintStrikeView.Reason.ADMITTED, v.admit(e(100, 0, "r0")).reason());
        assertEquals(List.of("r2"), tags(latest(v, "1m", "2026-09-10")), "revision 2 wins; the late revision 0 does not replace it");
        assertEquals(1, v.episodesInView());
        assertEquals(FootprintViews.utf8Length(e(100, 2, "r2")), v.bytesInView(), "bytes account the head only");
    }

    @Test void aCollisionRefusesTheIdentity_identicalBytesAreNotACollision_andEveryObservedRevisionIsChecked() {
        FootprintStrikeView v = view();
        String a = e(100, 0, "a");
        assertEquals(FootprintStrikeView.Reason.ADMITTED, v.admit(a).reason());
        assertEquals(FootprintStrikeView.Reason.ADMITTED, v.admit(a).reason(), "a bar re-processed after a crash collides with itself");
        assertEquals(0, v.collisions());
        assertEquals(FootprintStrikeView.Reason.COLLISION, v.admit(e(100, 0, "b")).reason());
        assertEquals(1, v.collisions()); assertEquals(1, v.refusedIdentities());
        assertTrue(latest(v, "1m", "2026-09-10").records().isEmpty(), "the chip renders NO DATA, not one of two contradictory records");
        assertEquals(FootprintStrikeView.Reason.REFUSED, v.admit(e(100, 1, "c")).reason(), "refused for the incarnation");
        assertTrue(v.helloField().endsWith(",\"loading\":true,\"refused\":1,\"unavailable\":false}"));
        // superseded revisions are still checked, in BOTH arrival orders
        for (boolean newerFirst : List.of(false, true)) {
            FootprintStrikeView w = view();
            if (newerFirst) w.admit(e(300, 2, "new"));
            w.admit(e(300, 0, "A"));
            if (!newerFirst) w.admit(e(300, 2, "new"));
            assertEquals(FootprintStrikeView.Reason.COLLISION, w.admit(e(300, 0, "B")).reason(), "newerFirst=" + newerFirst);
            assertEquals(1, w.collisions());
            assertEquals(FootprintStrikeView.Reason.REFUSED, w.admit(e(300, 3, "later")).reason());
            assertTrue(history(w, 680_000).records().isEmpty());
        }
    }

    @Test void aRefusedNewestEpisodeNeverFallsBackToAnOlderValue() {
        FootprintStrikeView v = view();
        v.admit(e(100, 0, "older"));
        v.admit(e(200, 0, "A")); v.admit(e(200, 0, "B"));
        assertTrue(latest(v, "1m", "2026-09-10").records().isEmpty(), "the newest episode is refused: NO DATA, not the older one");
        assertEquals(List.of("older"), tags(history(v, 680_000)), "history still lists the older, valid episode");
        v.admit(e(300, 0, "newer"));
        assertEquals(List.of("newer"), tags(latest(v, "1m", "2026-09-10")), "only a genuinely newer opening replaces the tombstone");
    }

    @Test void symbolsHaveIndependentIndexesAndRemoval() {
        FootprintStrikeView v = view();
        String es = e(100, 0, "ES"), other = episode("OPEN", "ESZ6", "2026-09-10", "1m", 680_000, 100, 0, 100, "other");
        v.admit(es); v.admit(other);
        assertEquals(List.of(es), v.history("ES.v.0", "1m", 680_000, "", 100).records());
        assertEquals(List.of(other), v.history("ESZ6", "1m", 680_000, "", 100).records());
        v.admit(e(100, 0, "collision"));
        assertEquals(List.of(other), v.history("ESZ6", "1m", 680_000, "", 100).records(), "refusing one symbol's identity leaves the other's alone");
        assertTrue(v.history("ES.v.0", "1m", 680_000, "", 100).records().isEmpty());
        assertEquals(1, v.episodesInView());
    }

    @Test void latestIsScopedToOneSessionAndOneTimeframe_historyCrossesSessionsNewestFirst() {
        FootprintStrikeView v = view();
        v.admit(episode("CLOSE", "2026-09-09", "1m", 680_000, 10, 3, 20, "yesterday"));
        v.admit(episode("CLOSE", "2026-09-10", "1m", 680_000, 100, 2, 200, "today-first"));
        v.admit(episode("OPEN", "2026-09-10", "1m", 680_000, 300, 0, 300, "today-latest"));
        v.admit(episode("OPEN", "2026-09-10", "5m", 680_000, 300, 0, 300, "five-minute"));
        v.admit(episode("OPEN", "2026-09-10", "1m", 685_000, 300, 0, 300, "other-strike"));
        FootprintStrikeView.Page latest = latest(v, "1m", "2026-09-10");
        assertEquals(List.of("today-latest", "other-strike"), tags(latest), "greatest openBarStartMs of THIS session, ascending by strike");
        assertEquals("2026-09-10", latest.sessionDate());
        FootprintStrikeView.Page yesterday = latest(v, "1m", "2026-09-09");
        assertEquals(List.of("yesterday"), tags(yesterday)); assertEquals("2026-09-09", yesterday.sessionDate(), "the envelope names the REQUESTED session");
        FootprintStrikeView.Page future = latest(v, "1m", "2026-09-11");
        assertTrue(future.records().isEmpty(), "a session with no episode: no row — NO DATA is the reader's word, never yesterday's value");
        assertEquals("2026-09-11", future.sessionDate());
        assertEquals(1, latest(v, "5m", "2026-09-10").records().size(), "timeframes are never merged");
        FootprintStrikeView.Page h = history(v, 680_000);
        assertEquals(List.of("today-latest", "today-first", "yesterday"), tags(h));
        assertNull(h.nextCursor());
        FootprintStrikeView.Page h1 = v.history("ES.v.0", "1m", 680_000, "", 1);
        assertEquals("2026-09-10|" + String.format(Locale.ROOT, "%019d", 300), h1.nextCursor());
        assertEquals(List.of("today-first"), tags(v.history("ES.v.0", "1m", 680_000, h1.nextCursor(), 1)), "the cursor is exclusive");
        assertTrue(FootprintStrikeView.validHistoryCursor(h1.nextCursor()));
        for (String bad : List.of("garbage", "2026-02-30|0000000000000000300", "2026-09-10|9999999999999999999", "2026-09-10|00000000000000003", "2026-9-10|0000000000000000300"))
            assertFalse(FootprintStrikeView.validHistoryCursor(bad), bad);
        FootprintStrikeView.Page l1 = v.latest("ES.v.0", "1m", "2026-09-10", -1, 1);
        assertEquals("680000", l1.nextCursor());
        assertEquals(List.of("other-strike"), tags(v.latest("ES.v.0", "1m", "2026-09-10", 680_000, 1)));
    }

    @Test void checkpointsAdvanceTheHelloHighWaterMarkAndCarryNoEpisode() {
        FootprintStrikeView v = view();
        assertEquals("{\"authority\":0,\"incarnation\":\"" + v.incarnation() + "\",\"symbol\":\"\",\"sessionDate\":null,\"hwm\":{},\"historyBeginsAtMs\":null,\"replayBeginsAtMs\":null,\"loading\":true,\"refused\":0,\"unavailable\":false}", v.helloField());
        assertTrue(v.admit(checkpoint("2026-09-10", "1m", 500)).checkpoint());
        assertTrue(v.admit(checkpoint(null, "30s", 7)).checkpoint(), "a genuine JSON null session date is the producer's 'nothing seen yet'");
        assertEquals(0, v.episodesInView());
        v.admit(episode("OPEN", "2026-09-10", "5m", 680_000, 300, 0, 300, "x"));
        v.admit(checkpoint("2026-09-10", "1m", 400));                          // older watermark: never regresses
        assertEquals("{\"authority\":0,\"incarnation\":\"" + v.incarnation() + "\",\"symbol\":\"\",\"sessionDate\":\"2026-09-10\",\"hwm\":{\"1m\":500,\"30s\":7,\"5m\":300},\"historyBeginsAtMs\":300,\"replayBeginsAtMs\":null,\"loading\":true,\"refused\":0,\"unavailable\":false}", v.helloField());
    }

    @Test void theBudgetsEvictTheOldestIdentitiesAndTheBoundaryIsMonotonic() {
        FootprintStrikeView v = new FootprintStrikeView(new ObjectMapper(), 262_144, 1 << 20, 3, 1000);
        for (int i = 1; i <= 5; i++) v.admit(episode("CLOSE", "2026-09-10", "1m", 680_000 + i * 500, i * 100, 1, i * 100, "e" + i));
        assertEquals(3, v.episodesInView()); assertEquals(2, v.evictions());
        assertEquals(Long.valueOf(300), v.historyBeginsAtMs(), "history begins at the oldest RETAINED episode");
        assertEquals(Long.valueOf(300), latest(v, "1m", "2026-09-10").historyBeginsAtMs());
        assertEquals(FootprintStrikeView.Reason.EVICTED, v.admit(episode("OPEN", "2026-09-10", "1m", 680_500, 100, 0, 100, "late")).reason(), "an evicted identity never returns, at any revision");
        assertEquals(FootprintStrikeView.Reason.EVICTED, v.admit(episode("UPDATE", "2026-09-10", "1m", 690_000, 250, 7, 250, "before-boundary")).reason());
        long boundary = v.historyBeginsAtMs();
        v.admit(episode("OPEN", "2026-09-10", "1m", 682_500, 500, 0, 500, "A")); v.admit(episode("OPEN", "2026-09-10", "1m", 682_500, 500, 0, 500, "B"));   // a refusal frees a slot
        assertTrue(v.historyBeginsAtMs() >= boundary, "the boundary never retreats: " + v.historyBeginsAtMs());
        // the byte budget counts EVERY retained byte — payload, identity and revision ledger (round-2 #1)
        FootprintStrikeView small = new FootprintStrikeView(new ObjectMapper(), 262_144, 2_000, 1000, 1000);
        small.admit(e(100, 0, "one")); small.admit(episode("OPEN", "2026-09-10", "1m", 680_500, 200, 0, 200, "two"));
        assertEquals(1, small.episodesInView(), "the byte budget evicts too");
        assertTrue(small.chargedBytesInView() <= 2_000, "and it is the budget that holds");
        assertEquals(List.of("two"), tags(latest(small, "1m", "2026-09-10")), "the survivor is the newer identity");
    }

    @Test void theRefusalLedgerIsBoundedAndOverflowFailsClosed() {
        FootprintStrikeView v = new FootprintStrikeView(new ObjectMapper(), 262_144, 1 << 20, 1000, 2);
        for (int i = 1; i <= 2; i++) { v.admit(e(i * 100, 0, "A")); v.admit(e(i * 100, 0, "B")); }
        assertFalse(v.unavailable());
        v.admit(e(300, 0, "A"));
        assertEquals(FootprintStrikeView.Reason.UNAVAILABLE, v.admit(e(300, 0, "B")).reason(), "a third refusal would have to be forgotten");
        assertTrue(v.unavailable()); assertTrue(latest(v, "1m", "2026-09-10").unavailable()); assertTrue(history(v, 680_000).unavailable());
        assertTrue(latest(v, "1m", "2026-09-10").records().isEmpty());
        assertEquals(FootprintStrikeView.Reason.UNAVAILABLE, v.admit(e(900, 0, "x")).reason(), "for the incarnation");
        assertTrue(v.helloField().endsWith("\"unavailable\":true}"));
    }

    @Test void shapeAndOversizeAreDropsNotCrashes_andTheHelloCanNeverBeCorrupted() {
        FootprintStrikeView v = view();
        assertEquals(FootprintStrikeView.Reason.SHAPE, v.admit("not json").reason());
        assertEquals(FootprintStrikeView.Reason.SHAPE, v.admit("{\"kind\":\"OPEN\"}").reason());
        assertEquals(FootprintStrikeView.Reason.SHAPE, v.admit("{\"kind\":\"VERDICT\",\"symbol\":\"ES\",\"timeframe\":\"1m\",\"seenMaxBarStartMs\":1}").reason(), "an unknown kind is a shape drop");
        assertEquals(FootprintStrikeView.Reason.OVERSIZE, v.admit("{\"pad\":\"" + "y".repeat(300_000) + "\"}").reason());
        assertEquals(FootprintStrikeView.Reason.SHAPE, v.admit(checkpoint("2026-09-10", "x\"y", 1)).reason(), "the timeframe vocabulary is enforced: nothing unquoted can reach the hello");
        assertEquals(FootprintStrikeView.Reason.SHAPE, v.admit(checkpoint("2026-02-30", "1m", 1)).reason(), "an impossible date never advances the session");
        assertEquals(FootprintStrikeView.Reason.SHAPE, v.admit(e(100, 0, "x").replace("\"sessionDate\":\"2026-09-10\"", "\"sessionDate\":\"2026-09-1\"")).reason());
        assertEquals(FootprintStrikeView.Reason.ADMITTED, v.admit(episode("UPDATE", "2026-09-10", "1m", 680_000, 100, 253_402_300_800_000L, 100, "big")).reason(), "a revision is a count, not an epoch");
        assertEquals(FootprintStrikeView.Reason.SHAPE, v.admit(e(100, 0, "x").replace("\"openBarStartMs\":100", "\"openBarStartMs\":253402300800000")).reason(), "an epoch outside the domain is a shape drop");
        assertEquals("{\"authority\":0,\"incarnation\":\"" + v.incarnation() + "\",\"symbol\":\"\",\"sessionDate\":\"2026-09-10\",\"hwm\":{\"1m\":100},\"historyBeginsAtMs\":100,\"replayBeginsAtMs\":null,\"loading\":true,\"refused\":0,\"unavailable\":false}", v.helloField());
        assertEquals(1, v.episodesInView());
    }

    @Test void keysAndCursorsDoNotDependOnTheDefaultLocale() {
        Locale prior = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("ar-EG"));
            FootprintStrikeView v = view();
            v.admit(e(100, 0, "a")); v.admit(e(200, 0, "b"));
            assertEquals(List.of("b"), tags(latest(v, "1m", "2026-09-10")));
            FootprintStrikeView.Page h1 = v.history("ES.v.0", "1m", 680_000, "", 1);
            assertEquals("2026-09-10|0000000000000000200", h1.nextCursor(), "ASCII digits whatever the locale");
            assertTrue(FootprintStrikeView.validHistoryCursor(h1.nextCursor()));
            assertEquals(List.of("a"), tags(v.history("ES.v.0", "1m", 680_000, h1.nextCursor(), 1)));
        } finally { Locale.setDefault(prior); }
    }

    // ---- code round-2: every retained byte budgeted, the boundary honest, loading distinguishable -----

    @Test void theRevisionLedgerAndRefusedIdentitiesCannotOutgrowTheBudget() {
        // ONE identity, ten thousand revisions: the digests ARE the growth, and they are charged, so the
        // budget acts on them. Before this the ledger grew without limit inside a view reporting 460 bytes.
        long budget = 200_000;
        FootprintStrikeView v = new FootprintStrikeView(new ObjectMapper(), 262_144, budget, 1000, 1000);
        long chargedAtPeak = 0;
        FootprintStrikeView.Reason last = null;
        for (long r = 0; r < 10_000; r++) {
            last = v.admit(e(100, r, "r" + r)).reason();
            chargedAtPeak = Math.max(chargedAtPeak, v.metadataBytesInView());
            assertTrue(v.chargedBytesInView() <= budget, "the budget holds at revision " + r);
        }
        assertTrue(chargedAtPeak > 100L * FootprintStrikeView.REVISION_BYTES, "the revisions are actually charged: " + chargedAtPeak);
        assertEquals(FootprintStrikeView.Reason.EVICTED, last, "the identity leaves through the published boundary, honestly");
        assertNotNull(v.historyBeginsAtMs());
        assertTrue(latest(v, "1m", "2026-09-10").records().isEmpty(), "and the chip reads NO DATA, not a stale head");
        // a LARGE identity is refused before it can charge arbitrary metadata: the symbol is bounded
        FootprintStrikeView w = view();
        String huge = "S".repeat(FootprintStrikeView.MAX_SYMBOL_CHARS + 1);
        assertEquals(FootprintStrikeView.Reason.SHAPE, w.admit(episode("OPEN", huge, "2026-09-10", "1m", 680_000, 100, 0, 100, "x")).reason());
        assertEquals(0, w.episodesInView()); assertEquals(0, w.metadataBytesInView());
        assertEquals(FootprintStrikeView.Reason.ADMITTED, w.admit(episode("OPEN", "S".repeat(FootprintStrikeView.MAX_SYMBOL_CHARS), "2026-09-10", "1m", 680_000, 100, 0, 100, "x")).reason());
        // refusal tombstones are charged too, and their bound still fails closed
        FootprintStrikeView t = new FootprintStrikeView(new ObjectMapper(), 262_144, 1 << 20, 1000, 2);
        for (int i = 0; i < 3; i++) { t.admit(e(1000 + i, 0, "a" + i)); t.admit(e(1000 + i, 0, "b" + i)); }
        assertTrue(t.metadataBytesInView() > 0 && t.unavailable());
    }

    @Test void everyRetainedHeadOpensAtOrAfterTheBoundary_evenWhenManyOpenInTheSameMillisecond() {
        FootprintStrikeView v = new FootprintStrikeView(new ObjectMapper(), 262_144, 1 << 20, 1, 1000);
        // two strikes opening at the SAME instant, with room for one head: the whole bucket must go
        v.admit(episode("OPEN", "2026-09-10", "1m", 680_000, 100, 0, 100, "a"));
        v.admit(episode("OPEN", "2026-09-10", "1m", 680_500, 100, 0, 100, "b"));
        Long boundary = v.historyBeginsAtMs();
        assertNotNull(boundary);
        assertEquals(0, v.episodesInView(), "no survivor may sit behind the published boundary");
        assertEquals(101L, boundary.longValue());
        assertTrue(latest(v, "1m", "2026-09-10").records().isEmpty());
        // and neither identity can come back, at any revision — the boundary is the whole rule
        assertEquals(FootprintStrikeView.Reason.EVICTED, v.admit(episode("UPDATE", "2026-09-10", "1m", 680_500, 100, 1, 101, "b2")).reason());
        assertEquals(FootprintStrikeView.Reason.EVICTED, v.admit(episode("OPEN", "2026-09-10", "1m", 680_500, 100, 0, 101, "conflict")).reason());
        assertEquals(0, v.collisions(), "an evicted identity is not a collision — it is gone");
        // a head that opens AT the boundary or later is admitted and keeps updating
        assertEquals(FootprintStrikeView.Reason.ADMITTED, v.admit(episode("OPEN", "2026-09-10", "1m", 681_000, 101, 0, 101, "c")).reason());
        assertEquals(FootprintStrikeView.Reason.ADMITTED, v.admit(episode("UPDATE", "2026-09-10", "1m", 681_000, 101, 1, 102, "c2")).reason());
        assertEquals(List.of("c2"), tags(latest(v, "1m", "2026-09-10")));
        for (String rec : latest(v, "1m", "2026-09-10").records())
            assertTrue(rec.contains("\"openBarStartMs\":101"), "every returned record opens at or after historyBeginsAtMs");
    }

    @Test void anUnfinishedReplayIsLOADING_neverACompletedEmptyPage_andCompletionReachesTheReaders() {
        FootprintStrikeView v = view();
        List<String> control = controls(v);
        assertTrue(v.loading(), "nothing has replayed yet");
        assertTrue(v.helloField().contains("\"loading\":true") && v.helloField().contains("\"replayBeginsAtMs\":null"));
        assertTrue(latest(v, "1m", "2026-09-10").loading(), "an empty page while loading is NOT a completed NO DATA");
        assertTrue(history(v, 680_000).loading());
        // a live CHECKPOINT can advance the high-water mark long before the cache has finished replaying
        v.admit(checkpoint("2026-09-10", "1m", 5_000));
        assertTrue(v.loading() && latest(v, "1m", "2026-09-10").loading());
        assertTrue(control.isEmpty(), "no authority change yet");
        v.replayRestarted(1_000L);
        assertTrue(control.isEmpty(), "opening a replay while already loading changes no authority");
        v.replayCompleted();
        assertFalse(v.loading());
        assertEquals(1, control.size(), "completing the replay reaches already-connected readers");
        assertTrue(control.get(0).contains("\"loading\":false") && control.get(0).contains("\"replayBeginsAtMs\":1000"));
        assertFalse(latest(v, "1m", "2026-09-10").loading());
        assertEquals(1_000L, latest(v, "1m", "2026-09-10").replayBeginsAtMs().longValue());
        v.replayCompleted();
        assertEquals(1, control.size(), "completion is idempotent and does not re-notify");
    }

    @Test void aRefusalAndFailingClosedBothReachAlreadyConnectedReaders() {
        FootprintStrikeView v = new FootprintStrikeView(new ObjectMapper(), 262_144, 1 << 20, 1000, 1);
        List<String> control = controls(v);
        v.admit(e(100, 0, "a"));
        assertTrue(control.isEmpty(), "an ordinary admission is not an authority change");
        assertEquals(FootprintStrikeView.Reason.COLLISION, v.admit(e(100, 0, "b")).reason());
        assertEquals(1, control.size(), "the readers hold a value this fold has just withdrawn");
        assertTrue(control.get(0).contains("\"refused\":1"));
        v.admit(e(200, 0, "c"));
        assertEquals(FootprintStrikeView.Reason.UNAVAILABLE, v.admit(e(200, 0, "d")).reason(), "the refusal that overflows the ledger reports the state it left behind");
        assertTrue(v.unavailable());
        assertTrue(control.get(control.size() - 1).contains("\"unavailable\":true"));
    }

    // ---- round 3: an authority generation, eviction as an authority change, an honest replay ----------

    @Test void everyAuthorityChangeBumpsAGenerationThatTheHelloAndEveryPageCarry() {
        // A count-only notice could not be ordered against a hello captured before it, so readers could
        // apply an OLDER authority last and stay loading for ever (gateway round-3 #3).
        FootprintStrikeView v = new FootprintStrikeView(new ObjectMapper(), 262_144, 1 << 20, 1000, 1);
        List<Long> seen = new ArrayList<>();
        v.onFrame(f -> { if (f.kind() == FootprintStrikeView.FrameKind.CONTROL) seen.add(authorityOf(f.body())); });
        assertEquals(0, v.authority());
        assertTrue(v.helloField().startsWith("{\"authority\":0,"));
        assertEquals(0, latest(v, "1m", "2026-09-10").authority());
        v.admit(e(100, 0, "a"));
        assertEquals(0, v.authority(), "an ordinary admission is not an authority change");
        v.replayCompleted();
        assertEquals(1, v.authority(), "completing the replay is");
        v.admit(e(100, 0, "b"));                                       // collision
        assertEquals(2, v.authority(), "and so is a refusal");
        assertEquals(List.of(1L, 2L), seen, "each one notified exactly once, with its own generation");
        assertTrue(latest(v, "1m", "2026-09-10").authority() >= 2);
        assertTrue(v.helloField().contains("\"authority\":2,"));
    }

    @Test void evictionIsAnAuthorityChangeAndAdmissionSaysWhetherTheIdentitySurvivedIt() {
        // The boundary moving changes what every reader may hold, and enforcement can evict the very
        // identity just inserted — reporting that as ADMITTED told the caller (and the page) the
        // opposite of the truth (gateway round-3 #4).
        FootprintStrikeView v = new FootprintStrikeView(new ObjectMapper(), 262_144, 1 << 20, 1, 1000);
        List<Long> control = new ArrayList<>();
        v.onFrame(f -> { if (f.kind() == FootprintStrikeView.FrameKind.CONTROL) control.add(authorityOf(f.body())); });
        assertEquals(FootprintStrikeView.Reason.ADMITTED, v.admit(episode("OPEN", "2026-09-10", "1m", 680_000, 100, 0, 100, "a")).reason());
        assertTrue(control.isEmpty());
        assertEquals(FootprintStrikeView.Reason.ADMITTED, v.admit(episode("OPEN", "2026-09-10", "1m", 680_500, 200, 0, 200, "b")).reason());
        assertEquals(1, control.size(), "the eviction moved the boundary: connected readers must be told");
        assertNotNull(v.historyBeginsAtMs());
        // an identity that enforcement evicts on the way in is reported EVICTED, not ADMITTED
        FootprintStrikeView w = new FootprintStrikeView(new ObjectMapper(), 262_144, 1 << 20, 1, 1000);
        w.admit(episode("OPEN", "2026-09-10", "1m", 680_500, 500, 0, 500, "newer"));
        assertEquals(FootprintStrikeView.Reason.EVICTED, w.admit(episode("OPEN", "2026-09-10", "1m", 680_000, 100, 0, 100, "older")).reason(),
                "it opened before the boundary the newer head established");
    }

    @Test void aReplayThatRestartsReopensLOADING_andTheWindowNamesTheSeekNotTheClock() {
        FootprintStrikeView v = view();
        List<Long> control = new ArrayList<>();
        v.onFrame(f -> { if (f.kind() == FootprintStrikeView.FrameKind.CONTROL) control.add(authorityOf(f.body())); });
        v.replayRestarted(1_000L);
        assertTrue(v.loading()); assertEquals(1_000L, latest(v, "1m", "2026-09-10").replayBeginsAtMs().longValue());
        assertTrue(control.isEmpty(), "it was already loading");
        v.replayCompleted();
        assertFalse(v.loading());
        assertEquals(1_000L, latest(v, "1m", "2026-09-10").replayBeginsAtMs().longValue(),
                "the window is the one that was SEEKED, not the clock at completion (round-3 #5)");
        assertEquals(1, control.size());
        v.replayCompleted();
        assertEquals(1_000L, latest(v, "1m", "2026-09-10").replayBeginsAtMs().longValue(), "a repeated caught-up poll moves nothing");
        assertEquals(1, control.size());
        // a new consumer attempt or a late adoption reopens it, and says so
        v.replayRestarted(50_000L);
        assertTrue(v.loading()); assertEquals(2, control.size());
        assertEquals(50_000L, latest(v, "1m", "2026-09-10").replayBeginsAtMs().longValue());
    }

    @Test void theConfiguredSymbolCanNeverCorruptTheSharedHello() throws Exception {
        FootprintStrikeView v = view();
        v.scopeSymbol("ES\"X\\Y");
        assertEquals("ES\"X\\Y", new ObjectMapper().readTree(v.helloField()).get("symbol").asText(),
                "the shared cvd-hello must stay parseable whatever the configuration says (round-3 #6)");
        assertThrows(IllegalArgumentException.class, () -> v.scopeSymbol("S".repeat(FootprintStrikeView.MAX_SYMBOL_CHARS + 1)));
        assertThrows(IllegalArgumentException.class, () -> v.scopeSymbol("ES\nX"));
    }

    @Test void aRefusedNewestEpisodeBehindTheBoundaryIsStillNamed_andAnUnreachableOneLeavesWithItsSessionsNewestHead() {
        // Round-3 #7 guarded a scan of the WHOLE index that pruned every tombstone behind the boundary. Under
        // protocol v2 a strike's refused or evicted newest episode must stay named in latest (strike re-review
        // #3), so nothing walks the index any more: a dead entry is kept exactly while latest can reach it —
        // as its session's newest entry — and leaves, locally, when it cannot.
        FootprintStrikeView v = new FootprintStrikeView(new ObjectMapper(), 262_144, 1 << 20, 2, 1000);
        v.admit(e(100, 0, "a")); v.admit(e(100, 0, "b"));               // collision at open 100 -> 6800's newest, refused
        assertEquals(1, v.indexedTombstones());
        for (int i = 1; i <= 4; i++) v.admit(episode("OPEN", "2026-09-10", "1m", 680_000 + i * 500, 1000L * i, 0, 1000L * i, "x" + i));
        assertTrue(v.historyBeginsAtMs() > 100, "the boundary moved past the refused episode");
        assertEquals(1, v.indexedTombstones(), "…but it is still 6800's newest episode in its session, so latest still names it");
        assertTrue(latest(v, "1m", "2026-09-10").tombstones().contains(new FootprintStrikeView.Tombstone(680_000, 100)));
        assertEquals(1, v.refusedIdentities(), "the refusal itself is still remembered for the incarnation");
        // a refused episode BELOW its session's newest head is unreachable, and leaves with that head
        FootprintStrikeView w = new FootprintStrikeView(new ObjectMapper(), 262_144, 1 << 20, 2, 1000);
        w.admit(e(100, 0, "a")); w.admit(e(100, 0, "b"));
        w.admit(e(200, 0, "newer"));                                    // 6800's newest is live again
        assertEquals(1, w.indexedTombstones());
        w.admit(episode("OPEN", "2026-09-10", "1m", 680_500, 300, 0, 300, "p"));
        w.admit(episode("OPEN", "2026-09-10", "1m", 681_000, 400, 0, 400, "q"));   // evicts "newer": 6800's newest, so it becomes a marker
        assertEquals(0, w.indexedTombstones(), "the refused entry below the marker can never be reached again: gone");
        assertEquals(1, w.evictionMarkers());
        assertEquals(List.of(new FootprintStrikeView.Tombstone(680_000, 200)), latest(w, "1m", "2026-09-10").tombstones(),
                "latest names the evicted newest episode — never the refused older one, and never an older episode as data");
        assertEquals(1, w.refusedIdentities());
    }

    @Test void quotedEscapesEveryControlCharacterAndItsWorstCaseIsWhatTheProxyMustSizeFor() throws Exception {
        StringBuilder all = new StringBuilder();
        for (char c = 0; c < 0x20; c++) all.append(c);
        String json = "{\"a\":\"" + all + "\\u0000\"}";
        String q = FootprintStrikeView.quoted(json);
        assertEquals(json, new ObjectMapper().readTree(q).asText(), "every control character survives the round trip");
        for (char c = 0; c < 0x20; c++) {
            if (c == '\n' || c == '\r' || c == '\t') continue;
            assertTrue(q.contains(String.format(Locale.ROOT, "\\u%04x", (int) c)), "lower-case hex escape for " + (int) c);
        }
        assertTrue(FootprintStrikeView.quoted("\u0000").length() == 8, "one NUL is six escape characters plus two quotes");
        assertEquals(6L * 10 + 3, FootprintStrikeView.quotedBoundBytes(10));
        String worst = "\u0000".repeat(1000);
        assertTrue(FootprintStrikeView.quoted(worst).length() <= FootprintStrikeView.quotedBoundBytes(1000), "the bound holds for the worst case");
    }

    @Test void quotedCarriesTheBytesVerbatim() throws Exception {
        String json = e(100, 0, "quote\"back\\slash\ttab");
        String quoted = FootprintStrikeView.quoted(json);
        assertEquals(json, new ObjectMapper().readTree(quoted).asText(), "a JSON string literal that parses back to the exact record text");
        assertTrue(quoted.startsWith("\"{\\\"kind\\\":\\\"OPEN\\\""));
    }

    // ---- final review: tombstones, retained storage, byte-exact pages, ordered delivery, incarnation ---------

    /** The bodies of the CONTROL frames the view sequences, in delivery order. */
    static List<String> controls(FootprintStrikeView v) {
        List<String> out = java.util.Collections.synchronizedList(new ArrayList<>());
        v.onFrame(f -> { if (f.kind() == FootprintStrikeView.FrameKind.CONTROL) out.add(f.body()); });
        return out;
    }

    static long authorityOf(String body) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\"authority\":(\\d+)").matcher(body);
        assertTrue(m.find(), body);
        return Long.parseLong(m.group(1));
    }

    private static List<FootprintStrikeView.Frame> recorded(FootprintStrikeView v) {
        List<FootprintStrikeView.Frame> out = java.util.Collections.synchronizedList(new ArrayList<>());
        v.onFrame(out::add);
        return out;
    }

    private static List<FootprintStrikeView.FrameKind> kinds(List<FootprintStrikeView.Frame> frames) {
        synchronized (frames) { return frames.stream().map(FootprintStrikeView.Frame::kind).toList(); }
    }

    /**
     * Starts a LIVE admission of {@code record} on its own thread and parks that thread right after its
     * decision was queued and the view lock released, before it drains — where a preempted consumer
     * thread would sit. Only that thread pauses.
     */
    private static Thread pausedLiveAdmission(FootprintStrikeView v, String record, CountDownLatch paused, CountDownLatch resume,
                                              AtomicReference<FootprintStrikeView.Admission> outcome) {
        Thread t = new Thread(() -> outcome.set(v.admit(record, true)), "state-live");
        v.afterDecisionForTest = () -> {
            if (Thread.currentThread() != t) return;
            paused.countDown();
            try { resume.await(10, TimeUnit.SECONDS); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        };
        t.start();
        return t;
    }

    @Test void latestNamesEveryStrikeWhoseNewestEpisodeIsRefused_andTheCursorCountsItAsVisited() {
        FootprintStrikeView v = view();
        v.admit(e(100, 0, "older"));
        v.admit(e(200, 0, "A")); v.admit(e(200, 0, "B"));                                   // 6800's newest episode: refused
        v.admit(episode("OPEN", "2026-09-10", "1m", 685_000, 300, 0, 300, "live"));
        v.admit(episode("OPEN", "2026-09-09", "1m", 690_000, 50, 0, 50, "y1"));
        v.admit(episode("OPEN", "2026-09-09", "1m", 690_000, 50, 0, 50, "y2"));            // refused, in ANOTHER session
        FootprintStrikeView.Page p = latest(v, "1m", "2026-09-10");
        assertEquals(List.of("live"), tags(p), "no row for the refused strike, and never its older episode");
        assertEquals(List.of(new FootprintStrikeView.Tombstone(680_000, 200)), p.tombstones(), "…and the page SAYS which strike is NO DATA");
        FootprintStrikeView.Page yesterday = latest(v, "1m", "2026-09-09");
        assertEquals(List.of(new FootprintStrikeView.Tombstone(690_000, 50)), yesterday.tombstones(), "scoped to the requested session");
        assertTrue(yesterday.records().isEmpty());
        assertTrue(latest(v, "5m", "2026-09-10").tombstones().isEmpty(), "and to the timeframe");
        FootprintStrikeView.Page first = v.latest("ES.v.0", "1m", "2026-09-10", -1, 1);
        assertEquals(List.of("live"), tags(first));
        assertEquals(List.of(new FootprintStrikeView.Tombstone(680_000, 200)), first.tombstones(), "a tombstone rides the page that visited it");
        assertEquals("685000", first.nextCursor(), "cursor semantics unchanged");
        FootprintStrikeView.Page after = v.latest("ES.v.0", "1m", "2026-09-10", 680_000, 200);
        assertTrue(after.tombstones().isEmpty(), "…and is not repeated by a page that starts after it");
        assertEquals(List.of("live"), tags(after));
        FootprintStrikeView.Page onlyDead = v.latest("ES.v.0", "1m", "2026-09-10", -1, 200);
        assertNull(onlyDead.nextCursor(), "a page that ends on the last strike ends pagination, tombstone or not");
        assertNull(history(v, 680_000).tombstones(), "history pages carry no tombstones");
        assertEquals(List.of("older"), tags(history(v, 680_000)), "history still omits the refused identity and lists the older one");
        v.admit(e(300, 0, "newer"));
        assertTrue(latest(v, "1m", "2026-09-10").tombstones().isEmpty(), "a genuinely newer opening replaces the tombstone");
        assertEquals(List.of("newer", "live"), tags(latest(v, "1m", "2026-09-10")));
    }

    @Test void retainedStorageIsWhatTheBudgetCharges_evenWhenOneCharacterIsWiderThanLatin1() {
        // The reviewer's counterexample (final review #3): maxBytes 300,000 and an admitted ~200 KB record whose
        // ONE character above U+00FF made the retained String UTF-16 — a 400,906-byte backing array against a
        // 201,157-byte charge. The view now retains the record's UTF-8 array, and charges that array.
        FootprintStrikeView v = new FootprintStrikeView(new ObjectMapper(), 262_144, 300_000, 1000, 1000);
        String rec = e(100, 0, "x".repeat(200_000) + "€");
        byte[] utf8 = rec.getBytes(StandardCharsets.UTF_8);
        assertTrue(2L * rec.length() > 300_000, "held as a UTF-16 String, this record's array alone would exceed the budget");
        assertEquals(FootprintStrikeView.Reason.ADMITTED, v.admit(rec).reason());
        assertEquals(List.of(utf8.length), v.retainedPayloadLengthsForTest(),
                "retained as UTF-8: the wide character costs its three bytes, not the whole record's width");
        assertEquals(FootprintStrikeView.arrayBytes(utf8.length), v.retainedBytesInView(), "the charge is the array actually held, header and alignment included");
        assertEquals(v.retainedBytesInView() + v.metadataBytesInView(), v.chargedBytesInView());
        assertTrue(v.chargedBytesInView() <= 300_000, "and the budget holds: " + v.chargedBytesInView());
        assertEquals(utf8.length, v.bytesInView(), "the record bytes a reader receives are published apart from storage");
        // a second such record cannot share the budget: the older one leaves through the boundary
        assertEquals(FootprintStrikeView.Reason.ADMITTED, v.admit(e(200, 0, "y".repeat(200_000) + "€")).reason());
        assertEquals(1, v.episodesInView());
        assertTrue(v.chargedBytesInView() <= 300_000);
        // retained STRINGS are charged by their storage too: one wide character makes every string that carries it UTF-16
        FootprintStrikeView narrow = view(), wide = view();
        narrow.admit(episode("OPEN", "E".repeat(64), "2026-09-10", "1m", 680_000, 100, 0, 100, "x"));
        wide.admit(episode("OPEN", "€" + "E".repeat(63), "2026-09-10", "1m", 680_000, 100, 0, 100, "x"));
        assertEquals(1, wide.episodesInView());
        assertTrue(wide.metadataBytesInView() - narrow.metadataBytesInView() >= 3L * 64,
                "the symbol, the identity and the age key each cost two bytes a char: " + narrow.metadataBytesInView() + " vs " + wide.metadataBytesInView());
        assertEquals(24 + 24, FootprintStrikeView.stringBytes("abc"));
        assertEquals(24 + FootprintStrikeView.arrayBytes(2L * 101), FootprintStrikeView.stringBytes("x".repeat(100) + "€"));
        assertEquals(24 + FootprintStrikeView.arrayBytes(101), FootprintStrikeView.stringBytes("x".repeat(100) + "ÿ"), "U+00FF is still Latin-1");
    }

    @Test void theChargeTracksEveryRetainedArrayThroughGrowthShrinkEvictionAndRefusal() {
        FootprintStrikeView v = new FootprintStrikeView(new ObjectMapper(), 262_144, 60_000, 1000, 1000);
        java.util.Random r = new java.util.Random(7);
        for (int i = 0; i < 600; i++) {
            long open = 100 + r.nextInt(40) * 10L + i / 20 * 10L;                 // identities recur, and time moves on
            long rev = r.nextInt(4);
            String tag = (r.nextBoolean() ? "x".repeat(r.nextInt(3000)) : "é€".repeat(r.nextInt(800))) + (i % 3);
            v.admit(e(open, rev, tag));
            List<Integer> held = v.retainedPayloadLengthsForTest();
            assertEquals(held.stream().mapToLong(FootprintStrikeView::arrayBytes).sum(), v.retainedBytesInView(), "retained charge == the arrays held, step " + i);
            assertEquals(held.stream().mapToLong(Integer::longValue).sum(), v.bytesInView(), "record bytes == the heads' bytes, step " + i);
            assertTrue(v.unavailable() || v.chargedBytesInView() <= 60_000, "the budget holds, step " + i);
        }
        assertTrue(v.evictions() > 0 && v.collisions() > 0, "the run exercised eviction and refusal: " + v.evictions() + "/" + v.collisions());
    }

    @Test void theRetainedUtf8IsQuotedByteForByteAsTheStringWouldBe() {
        java.util.Random r = new java.util.Random(11);
        int[] pool = {0, 1, 0x1f, '"', '\\', '\n', '\r', '\t', 'a', '/', 0x7f, 0x80, 0xe9, 0xff, 0x100, 0x20ac, 0xfeff, 0x1F600, 0x10FFFF};
        for (int n = 0; n < 2000; n++) {
            StringBuilder sb = new StringBuilder();
            int len = n == 0 ? 20_000 : r.nextInt(40);                             // one long enough to cross the write buffer
            for (int i = 0; i < len; i++) sb.appendCodePoint(pool[r.nextInt(pool.length)]);
            String s = sb.toString();
            assertArrayEquals(FootprintStrikeView.quoted(s).getBytes(StandardCharsets.UTF_8), FootprintStrikeView.quotedUtf8(s.getBytes(StandardCharsets.UTF_8)), "string " + n);
        }
        byte[] worst = new byte[50_000];
        assertTrue(FootprintStrikeView.quotedUtf8(worst).length <= FootprintStrikeView.quotedBoundBytes(worst.length), "the proxy's bound still holds");
    }

    @Test void aRecordAdmittedBeforeARefusalIsDeliveredBeforeThatRefusalsControl_whicheverThreadDrains() throws Exception {
        FootprintStrikeView v = view();
        List<FootprintStrikeView.Frame> delivered = recorded(v);
        CountDownLatch paused = new CountDownLatch(1), resume = new CountDownLatch(1);
        AtomicReference<FootprintStrikeView.Admission> live = new AtomicReference<>();
        Thread t = pausedLiveAdmission(v, e(100, 0, "A"), paused, resume, live);
        assertTrue(paused.await(10, TimeUnit.SECONDS));
        assertTrue(delivered.isEmpty(), "the live thread has decided and queued A, and not yet delivered it");
        // the cache consumer refuses the same identity now — and its drain carries the queue, A first
        assertEquals(FootprintStrikeView.Reason.COLLISION, v.admit(e(100, 0, "B")).reason());
        assertEquals(List.of(FootprintStrikeView.FrameKind.EVIDENCE, FootprintStrikeView.FrameKind.CONTROL), kinds(delivered));
        assertTrue(delivered.get(0).body().contains("\"tag\":\"A\""));
        assertEquals(1, authorityOf(delivered.get(1).body()));
        // REST completes HERE — after the control, before the live thread resumes: the identity is withdrawn
        FootprintStrikeView.Page rest = latest(v, "1m", "2026-09-10");
        assertTrue(rest.records().isEmpty());
        assertEquals(1, rest.authority());
        assertEquals(List.of(new FootprintStrikeView.Tombstone(680_000, 100)), rest.tombstones());
        resume.countDown();
        t.join(10_000);
        assertEquals(FootprintStrikeView.Reason.ADMITTED, live.get().reason(), "A WAS admitted — before the refusal");
        assertEquals(2, delivered.size(), "nothing reaches the sink after the control: the live thread found its evidence already delivered");
        assertEquals(0, v.queuedFrames());
    }

    @Test void aRecordAdmittedBeforeAnEvictionIsDeliveredBeforeThatEvictionsControl() throws Exception {
        FootprintStrikeView v = new FootprintStrikeView(new ObjectMapper(), 262_144, 1 << 20, 1, 1000);
        List<FootprintStrikeView.Frame> delivered = recorded(v);
        CountDownLatch paused = new CountDownLatch(1), resume = new CountDownLatch(1);
        AtomicReference<FootprintStrikeView.Admission> live = new AtomicReference<>();
        Thread t = pausedLiveAdmission(v, e(100, 0, "A"), paused, resume, live);
        assertTrue(paused.await(10, TimeUnit.SECONDS));
        assertEquals(FootprintStrikeView.Reason.ADMITTED, v.admit(e(200, 0, "B")).reason(), "the cache consumer's newer opening evicts A");
        assertEquals(List.of(FootprintStrikeView.FrameKind.EVIDENCE, FootprintStrikeView.FrameKind.CONTROL), kinds(delivered));
        assertTrue(delivered.get(0).body().contains("\"tag\":\"A\""));
        assertEquals(Long.valueOf(200), v.historyBeginsAtMs(), "the boundary moved past A");
        assertEquals(List.of("B"), tags(history(v, 680_000)), "REST no longer holds A");
        resume.countDown();
        t.join(10_000);
        assertEquals(FootprintStrikeView.Reason.ADMITTED, live.get().reason());
        assertEquals(2, delivered.size(), "and no evidence of A arrives after the control that withdrew it");
    }

    @Test void aHelloIsSequencedWithTheControls_noFrameCarriesAnAuthorityOlderThanOneDeliveredBeforeIt() throws Exception {
        FootprintStrikeView v = new FootprintStrikeView(new ObjectMapper(), 262_144, 1 << 24, 100_000, 100_000);
        List<FootprintStrikeView.Frame> delivered = recorded(v);
        CountDownLatch go = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread mutator = new Thread(() -> { try { go.await(); for (int i = 0; i < 500; i++) { v.admit(e(1_000 + i, 0, "a")); v.admit(e(1_000 + i, 0, "b")); } } catch (Throwable x) { failure.set(x); } });
        Thread connector = new Thread(() -> { try { go.await(); for (int i = 0; i < 500; i++) v.hello("socket-" + i); } catch (Throwable x) { failure.set(x); } });
        mutator.start(); connector.start(); go.countDown(); mutator.join(); connector.join();
        assertNull(failure.get());
        long last = -1;
        int hellos = 0, controlFrames = 0;
        synchronized (delivered) {
            for (FootprintStrikeView.Frame f : delivered) {
                long a = authorityOf(f.body());
                assertTrue(a >= last, "a " + f.kind() + " at authority " + a + " was delivered after authority " + last);
                last = a;
                if (f.kind() == FootprintStrikeView.FrameKind.HELLO) hellos++;
                if (f.kind() == FootprintStrikeView.FrameKind.CONTROL) controlFrames++;
            }
        }
        assertEquals(500, hellos);
        assertEquals(500, controlFrames);
        assertEquals(500, last);
    }

    @Test void aForeignSymbolNeverAdvancesTheNamedHello() throws Exception {
        // round-3 additional finding: the fix existed, the committed regression did not (final review table)
        FootprintStrikeView v = view();
        v.scopeSymbol("ES.v.0");
        v.admit(checkpoint("2026-09-10", "1m", 500));
        String nq = checkpoint("2026-09-11", "1m", 9_000).replace("\"symbol\":\"ES.v.0\"", "\"symbol\":\"NQ.v.0\"");
        assertTrue(v.admit(nq).checkpoint());
        v.admit(episode("OPEN", "NQ.v.0", "2026-09-12", "5m", 1_900_000, 100, 0, 99_999, "nq"));
        com.fasterxml.jackson.databind.JsonNode hello = new ObjectMapper().readTree(v.helloField());
        assertEquals("ES.v.0", hello.get("symbol").asText());
        assertEquals("2026-09-10", hello.get("sessionDate").asText(), "another symbol's newer session never speaks for this hello");
        assertEquals(500, hello.get("hwm").get("1m").asLong(), "nor its high-water mark");
        assertNull(hello.get("hwm").get("5m"), "nor a timeframe only the other symbol reached");
        assertEquals(List.of("nq"), tags(v.history("NQ.v.0", "5m", 1_900_000, "", 100)), "its records still fold and are served by an explicit symbol");
    }

    @Test void theIncarnationIsFixedForTheViewAndRidesTheHelloEveryControlAndEveryPage() throws Exception {
        FootprintStrikeView v = view(), other = view();
        assertNotEquals(v.incarnation(), other.incarnation(), "a new process is a new incarnation");
        assertEquals(v.incarnation(), UUID.fromString(v.incarnation()).toString());
        String inc = v.incarnation();
        List<String> control = controls(v);
        ObjectMapper m = new ObjectMapper();
        assertEquals(inc, m.readTree(v.helloField()).get("incarnation").asText());
        v.admit(e(100, 0, "a")); v.admit(e(100, 0, "b")); v.replayCompleted();
        assertEquals(2, control.size());
        for (String c : control) assertEquals(inc, m.readTree(c).get("incarnation").asText());
        assertEquals(inc, latest(v, "1m", "2026-09-10").incarnation());
        assertEquals(inc, history(v, 680_000).incarnation());
        assertEquals(inc, v.incarnation(), "it never changes within the incarnation, whatever the authority did");
    }

    // ---- strike re-review (round 2): a cache-only change reaches readers; evicted newest episodes are named ----

    /**
     * The reviewer's reproduction of #2: revision 0 → revision 1 folded by the CACHE consumer after the replay
     * completed changed REST's payload and emitted no frame at all. The change must reach every connected
     * reader, in order with the mutations around it — as the admitted record's evidence (a reader folds it by
     * revision, the same rule this view applies), not as an authority change that would make every reader
     * release its fold and re-walk. Invalidations keep advancing the authority, after their evidence.
     */
    @Test void aChangeTheCacheConsumerFoldsAfterTheReplayCompletedReachesTheReaders_inOrder() {
        FootprintStrikeView v = view();
        List<FootprintStrikeView.Frame> delivered = recorded(v);
        String r0 = e(100, 0, "r0"), r1 = e(100, 1, "r1");
        assertEquals(FootprintStrikeView.Reason.ADMITTED, v.admit(r0).reason());
        assertTrue(delivered.isEmpty(), "while the replay is incomplete the cache consumer queues no evidence: its completion covers it");
        v.replayCompleted();
        assertEquals(List.of(FootprintStrikeView.FrameKind.CONTROL), kinds(delivered));
        long authority = v.authority();
        assertEquals(1, authority);
        // revision 0 -> revision 1, from the cache consumer, after completion
        assertEquals(FootprintStrikeView.Reason.ADMITTED, v.admit(r1).reason());
        assertEquals(List.of("r1"), tags(latest(v, "1m", "2026-09-10")), "REST serves revision 1");
        assertEquals(List.of(FootprintStrikeView.FrameKind.CONTROL, FootprintStrikeView.FrameKind.EVIDENCE), kinds(delivered),
                "…and the connected readers are told: the change is queued, as evidence, by the mutation that made it");
        assertEquals(r1, delivered.get(1).body(), "the evidence is the record the fold now holds, byte for byte");
        assertEquals(authority, v.authority(), "a newer revision supersedes by the fold's own rule: not an invalidation");
        // the live consumer meets the same record later (or a redelivery arrives): nothing changed, nothing is queued twice
        assertEquals(FootprintStrikeView.Reason.ADMITTED, v.admit(r1, true).reason());
        assertEquals(FootprintStrikeView.Reason.ADMITTED, v.admit(r0).reason(), "an older revision after a newer one");
        assertEquals(2, delivered.size(), "an admission that changed nothing queues nothing");
        // a change of WHICH episode is latest for the strike, from the cache consumer
        String newer = e(200, 0, "newer");
        v.admit(newer);
        assertEquals(List.of("newer"), tags(latest(v, "1m", "2026-09-10")));
        assertEquals(newer, delivered.get(2).body());
        // an invalidation from the cache consumer still advances the authority — after everything queued before it
        assertEquals(FootprintStrikeView.Reason.COLLISION, v.admit(e(200, 0, "conflict")).reason());
        assertEquals(List.of(FootprintStrikeView.FrameKind.CONTROL, FootprintStrikeView.FrameKind.EVIDENCE, FootprintStrikeView.FrameKind.EVIDENCE,
                FootprintStrikeView.FrameKind.CONTROL), kinds(delivered));
        assertEquals(authority + 1, authorityOf(delivered.get(3).body()));
        // a REOPENED replay stops the cache consumer's evidence again until it completes again
        v.replayRestarted(5L);
        v.admit(e(300, 0, "during-the-retry"));
        v.replayCompleted();
        assertEquals(List.of(FootprintStrikeView.FrameKind.CONTROL, FootprintStrikeView.FrameKind.CONTROL), kinds(delivered).subList(4, 6),
                "reopened, then completed: the readers re-walk after it and find the retry's change");
        assertEquals(List.of("during-the-retry"), tags(latest(v, "1m", "2026-09-10")));
        assertEquals(0, v.queuedFrames());
    }

    private static long headCharge(String symbol, String session, String tf, long strike, long open, int revisions) {
        String identity = symbol + "|" + session + "|" + tf + "|" + strike + "|" + open;
        String openKey = FootprintStrikeView.openKey(session, open), ageKey = String.format(Locale.ROOT, "%019d", open) + "|" + identity;
        return FootprintStrikeView.HEAD_OVERHEAD + FootprintStrikeView.stringBytes(identity) + FootprintStrikeView.stringBytes(symbol)
                + FootprintStrikeView.stringBytes(session) + FootprintStrikeView.stringBytes(tf) + FootprintStrikeView.stringBytes(openKey)
                + FootprintStrikeView.stringBytes(ageKey) + (long) FootprintStrikeView.REVISION_BYTES * revisions;
    }

    private static long markerCharge(String symbol, String session, String tf, long strike, long open) {
        String identity = symbol + "|" + session + "|" + tf + "|" + strike + "|" + open;
        String openKey = FootprintStrikeView.openKey(session, open), ageKey = String.format(Locale.ROOT, "%019d", open) + "|" + identity;
        return FootprintStrikeView.MARKER_OVERHEAD + FootprintStrikeView.stringBytes(identity) + FootprintStrikeView.stringBytes(openKey)
                + FootprintStrikeView.stringBytes(ageKey) + FootprintStrikeView.stringBytes(symbol) + FootprintStrikeView.stringBytes(tf);
    }

    /**
     * The reviewer's reproduction of #3: maxEpisodes = 1; strike 680000 opens at 100, then strike 681000 at 200.
     * latest returned the second record, boundary 200 and NO tombstone — 6800's evicted newest episode simply
     * vanished. It is now kept as a marker, named, charged, and retired by a genuinely newer opening.
     */
    @Test void anEvictedNewestEpisodeIsNamedInTombstones_theReviewersCase() {
        FootprintStrikeView v = new FootprintStrikeView(new ObjectMapper(), 262_144, 1 << 20, 1, 1000);
        List<String> control = controls(v);
        v.admit(episode("OPEN", "2026-09-10", "1m", 680_000, 100, 0, 100, "first"));
        v.admit(episode("OPEN", "2026-09-10", "1m", 681_000, 200, 0, 200, "second"));
        FootprintStrikeView.Page p = latest(v, "1m", "2026-09-10");
        assertEquals(List.of("second"), tags(p));
        assertEquals(200L, p.historyBeginsAtMs().longValue());
        assertEquals(List.of(new FootprintStrikeView.Tombstone(680_000, 100)), p.tombstones(), "6800's evicted newest episode is NAMED, not silently absent");
        assertEquals(1, v.evictionMarkers());
        assertEquals(1, control.size(), "the eviction moved the boundary: one authority change");
        assertEquals(headCharge("ES.v.0", "2026-09-10", "1m", 681_000, 200, 1) + markerCharge("ES.v.0", "2026-09-10", "1m", 680_000, 100),
                v.metadataBytesInView(), "the marker is charged to the byte budget, exactly");
        assertEquals(FootprintStrikeView.Reason.EVICTED, v.admit(episode("UPDATE", "2026-09-10", "1m", 680_000, 100, 1, 101, "late")).reason(),
                "the marked identity can never come back, at any revision");
        assertTrue(history(v, 680_000).records().isEmpty());
        // a genuinely newer opening of 6800 answers for it again, and retires its marker
        v.admit(episode("OPEN", "2026-09-10", "1m", 680_000, 300, 0, 300, "third"));          // evicts 681000/200: now IT is marked
        FootprintStrikeView.Page q = latest(v, "1m", "2026-09-10");
        assertEquals(List.of("third"), tags(q));
        assertEquals(List.of(new FootprintStrikeView.Tombstone(681_000, 200)), q.tombstones());
        assertEquals(1, v.evictionMarkers(), "6800's marker left the moment a newer opening answered for the strike");
        assertEquals(headCharge("ES.v.0", "2026-09-10", "1m", 680_000, 300, 1) + markerCharge("ES.v.0", "2026-09-10", "1m", 681_000, 200), v.metadataBytesInView());
        assertTrue(latest(v, "1m", "2026-09-09").tombstones().isEmpty(), "markers speak for their own session only");
        assertTrue(latest(v, "5m", "2026-09-10").tombstones().isEmpty(), "and their own timeframe");
    }

    /**
     * Markers are bounded (the refusal ledger's bound, and the byte budget). Forgetting one is an AUTHORITY
     * CHANGE — a reader discards what it held under it and re-walks — and it can never let an older episode
     * take the strike's place: every marker opened below the boundary, so nothing older is retained or admitted.
     */
    @Test void theMarkerLedgerIsBounded_forgettingAMarkerIsAnAuthorityChange_andNoOlderEpisodeTakesItsPlace() {
        FootprintStrikeView v = new FootprintStrikeView(new ObjectMapper(), 262_144, 1 << 20, 1, 2);
        List<String> control = controls(v);
        for (int i = 0; i < 4; i++) v.admit(episode("OPEN", "2026-09-10", "1m", 680_000 + i * 500, 100 + i * 100, 0, 100 + i * 100, "s" + i));
        assertEquals(2, v.evictionMarkers(), "three evicted newest episodes, room for two markers");
        assertEquals(1, v.markerDrops(), "the OLDEST marker was forgotten");
        FootprintStrikeView.Page p = latest(v, "1m", "2026-09-10");
        assertEquals(List.of("s3"), tags(p));
        assertEquals(List.of(new FootprintStrikeView.Tombstone(680_500, 200), new FootprintStrikeView.Tombstone(681_000, 300)), p.tombstones());
        assertEquals(3, control.size(), "one authority change per mutation: the drop rode the eviction that forced it");
        assertEquals(FootprintStrikeView.Reason.EVICTED, v.admit(episode("OPEN", "2026-09-10", "1m", 680_000, 100, 0, 100, "s0")).reason());
        assertEquals(FootprintStrikeView.Reason.EVICTED, v.admit(episode("OPEN", "2026-09-10", "1m", 680_000, 150, 0, 150, "older-than-the-boundary")).reason(),
                "no older episode of the forgotten strike can be admitted in its place");
        assertTrue(history(v, 680_000).records().isEmpty());
        assertTrue(latest(v, "1m", "2026-09-10").records().stream().noneMatch(r -> r.contains("\"strikeCents\":680000")),
                "latest has no row for it: NO DATA, never an older episode");

        // under the BYTE budget alone a marker goes before any live head — and forgetting it is an authority change on its own
        String big = "x".repeat(20_000);
        FootprintStrikeView probe = new FootprintStrikeView(new ObjectMapper(), 262_144, 1 << 24, 1, 1000);
        probe.admit(e(100, 0, "s0")); probe.admit(episode("OPEN", "2026-09-10", "1m", 680_500, 200, 0, 200, "s1"));
        probe.admit(episode("UPDATE", "2026-09-10", "1m", 680_500, 200, 1, 201, big));
        assertEquals(1, probe.evictionMarkers());
        long withMarker = probe.chargedBytesInView();
        FootprintStrikeView b = new FootprintStrikeView(new ObjectMapper(), 262_144, withMarker - 1, 1, 1000);
        List<String> bControl = controls(b);
        b.admit(e(100, 0, "s0")); b.admit(episode("OPEN", "2026-09-10", "1m", 680_500, 200, 0, 200, "s1"));
        assertEquals(1, b.evictionMarkers());
        Long boundary = b.historyBeginsAtMs();
        long authority = b.authority();
        long metaBefore = b.metadataBytesInView();
        assertEquals(FootprintStrikeView.Reason.ADMITTED, b.admit(episode("UPDATE", "2026-09-10", "1m", 680_500, 200, 1, 201, big)).reason());
        assertEquals(1, b.episodesInView(), "the live head stays: a marker is worth less than a head");
        assertEquals(0, b.evictionMarkers()); assertEquals(1, b.markerDrops());
        assertEquals(metaBefore + FootprintStrikeView.REVISION_BYTES - markerCharge("ES.v.0", "2026-09-10", "1m", 680_000, 100), b.metadataBytesInView(),
                "the marker's charge left with it, exactly");
        assertEquals(boundary, b.historyBeginsAtMs(), "nothing was evicted: the boundary did not move");
        assertEquals(authority + 1, b.authority(), "forgetting the marker is an authority change by itself");
        assertEquals(authority + 1, authorityOf(bControl.get(bControl.size() - 1)), "…and readers are told");
        assertTrue(b.chargedBytesInView() <= withMarker - 1);
        assertTrue(latest(b, "1m", "2026-09-10").tombstones().isEmpty());
        assertFalse(b.unavailable());
    }
}

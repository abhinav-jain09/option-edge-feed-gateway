package app.feedgateway;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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
        assertEquals("{\"authority\":0,\"symbol\":\"\",\"sessionDate\":null,\"hwm\":{},\"historyBeginsAtMs\":null,\"replayBeginsAtMs\":null,\"loading\":true,\"refused\":0,\"unavailable\":false}", v.helloField());
        assertTrue(v.admit(checkpoint("2026-09-10", "1m", 500)).checkpoint());
        assertTrue(v.admit(checkpoint(null, "30s", 7)).checkpoint(), "a genuine JSON null session date is the producer's 'nothing seen yet'");
        assertEquals(0, v.episodesInView());
        v.admit(episode("OPEN", "2026-09-10", "5m", 680_000, 300, 0, 300, "x"));
        v.admit(checkpoint("2026-09-10", "1m", 400));                          // older watermark: never regresses
        assertEquals("{\"authority\":0,\"symbol\":\"\",\"sessionDate\":\"2026-09-10\",\"hwm\":{\"1m\":500,\"30s\":7,\"5m\":300},\"historyBeginsAtMs\":300,\"replayBeginsAtMs\":null,\"loading\":true,\"refused\":0,\"unavailable\":false}", v.helloField());
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
        assertTrue(small.bytesInView() + small.metadataBytesInView() <= 2_000, "and it is the budget that holds");
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
        assertEquals("{\"authority\":0,\"symbol\":\"\",\"sessionDate\":\"2026-09-10\",\"hwm\":{\"1m\":100},\"historyBeginsAtMs\":100,\"replayBeginsAtMs\":null,\"loading\":true,\"refused\":0,\"unavailable\":false}", v.helloField());
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
            assertTrue(v.bytesInView() + v.metadataBytesInView() <= budget, "the budget holds at revision " + r);
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
        List<String> control = new ArrayList<>();
        v.onAuthorityChange(() -> control.add(v.helloField()));
        assertTrue(v.loading(), "nothing has replayed yet");
        assertTrue(v.helloField().contains("\"loading\":true") && v.helloField().contains("\"replayBeginsAtMs\":null"));
        assertTrue(latest(v, "1m", "2026-09-10").loading(), "an empty page while loading is NOT a completed NO DATA");
        assertTrue(history(v, 680_000).loading());
        // a live CHECKPOINT can advance the high-water mark long before the cache has finished replaying
        v.admit(checkpoint("2026-09-10", "1m", 5_000));
        assertTrue(v.loading() && latest(v, "1m", "2026-09-10").loading());
        assertTrue(control.isEmpty(), "no authority change yet");
        v.replay(1_000, true);
        assertFalse(v.loading());
        assertEquals(1, control.size(), "completing the replay reaches already-connected readers");
        assertTrue(control.get(0).contains("\"loading\":false") && control.get(0).contains("\"replayBeginsAtMs\":1000"));
        assertFalse(latest(v, "1m", "2026-09-10").loading());
        assertEquals(1_000L, latest(v, "1m", "2026-09-10").replayBeginsAtMs().longValue());
        v.replay(1_000, true);
        assertEquals(1, control.size(), "completion is idempotent and does not re-notify");
    }

    @Test void aRefusalAndFailingClosedBothReachAlreadyConnectedReaders() {
        FootprintStrikeView v = new FootprintStrikeView(new ObjectMapper(), 262_144, 1 << 20, 1000, 1);
        List<String> control = new ArrayList<>();
        v.onAuthorityChange(() -> control.add(v.helloField()));
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
        v.onAuthorityChange(() -> seen.add(v.authority()));
        assertEquals(0, v.authority());
        assertTrue(v.helloField().startsWith("{\"authority\":0,"));
        assertEquals(0, latest(v, "1m", "2026-09-10").authority());
        v.admit(e(100, 0, "a"));
        assertEquals(0, v.authority(), "an ordinary admission is not an authority change");
        v.replay(1_000, true);
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
        v.onAuthorityChange(() -> control.add(v.authority()));
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
        v.onAuthorityChange(() -> control.add(v.authority()));
        v.replayRestarted(1_000);
        assertTrue(v.loading()); assertEquals(1_000L, latest(v, "1m", "2026-09-10").replayBeginsAtMs().longValue());
        assertTrue(control.isEmpty(), "it was already loading");
        v.replay(9_999, true);
        assertFalse(v.loading());
        assertEquals(1_000L, latest(v, "1m", "2026-09-10").replayBeginsAtMs().longValue(),
                "the window is the one that was SEEKED, not the clock at completion (round-3 #5)");
        assertEquals(1, control.size());
        v.replay(12_345, true);
        assertEquals(1_000L, latest(v, "1m", "2026-09-10").replayBeginsAtMs().longValue(), "a repeated caught-up poll moves nothing");
        assertEquals(1, control.size());
        // a new consumer attempt or a late adoption reopens it, and says so
        v.replayRestarted(50_000);
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

    @Test void prunedTombstonesStopCountingAsIndexed() {
        // The guard that skips the full index scan meant "a refusal has ever happened", so one historic
        // collision made every future eviction scan the whole index under the lock (round-3 #7).
        FootprintStrikeView v = new FootprintStrikeView(new ObjectMapper(), 262_144, 1 << 20, 2, 1000);
        v.admit(e(100, 0, "a")); v.admit(e(100, 0, "b"));               // collision at open 100 -> tombstone
        assertEquals(1, v.indexedTombstones());
        for (int i = 1; i <= 4; i++) v.admit(episode("OPEN", "2026-09-10", "1m", 680_000 + i * 500, 1000L * i, 0, 1000L * i, "x" + i));
        assertEquals(0, v.indexedTombstones(), "the boundary moved past it: it is no longer in the index");
        assertEquals(1, v.refusedIdentities(), "the refusal itself is still remembered for the incarnation");
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
}

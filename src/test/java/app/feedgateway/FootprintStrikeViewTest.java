package app.feedgateway;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

/** ES-FOOTPRINT-STRIKE-INTERACTION.md R14/R18/R20 — the gateway fold, without a broker. */
class FootprintStrikeViewTest {

    static String episode(String kind, String session, String tf, long strike, long open, long revision, long seenMax, String tag) {
        return "{\"kind\":\"" + kind + "\",\"symbol\":\"ES.v.0\",\"sessionDate\":\"" + session + "\",\"timeframe\":\"" + tf + "\",\"strikeCents\":" + strike
                + ",\"openBarStartMs\":" + open + ",\"revision\":" + revision + ",\"final\":" + "CLOSE".equals(kind) + ",\"closeReason\":" + ("CLOSE".equals(kind) ? "\"LEFT_BAND\"" : "null")
                + ",\"approach\":\"UNKNOWN\",\"suspended\":false,\"unreadableBars\":0,\"barsObserved\":1,\"seenMaxBarStartMs\":" + seenMax
                + ",\"prevAdmittedCloseCents\":null,\"prevAdmittedBarStartMs\":null,\"firstBarStartMs\":" + open + ",\"lastBarStartMs\":" + open
                + ",\"seriesTruncated\":false,\"seriesOmittedCount\":0,\"bandCents\":250,\"series\":[{\"tag\":\"" + tag + "\"}]}";
    }
    static String checkpoint(String session, String tf, long seenMax) {
        return "{\"kind\":\"CHECKPOINT\",\"symbol\":\"ES.v.0\",\"sessionDate\":\"" + session + "\",\"timeframe\":\"" + tf + "\",\"seenMaxBarStartMs\":" + seenMax
                + ",\"prevAdmittedCloseCents\":null,\"prevAdmittedBarStartMs\":null,\"bandCents\":250}";
    }
    private static FootprintStrikeView view() { return new FootprintStrikeView(new ObjectMapper(), 262_144, 1 << 20, 1000); }

    @Test void theFoldTakesTheGreatestRevisionPerIdentityWhateverTheArrivalOrder() {
        FootprintStrikeView v = view();
        assertEquals(FootprintStrikeView.Reason.ADMITTED, v.admit(episode("UPDATE", "2026-09-10", "1m", 680_000, 100, 2, 300, "r2")).reason());
        assertEquals(FootprintStrikeView.Reason.ADMITTED, v.admit(episode("OPEN", "2026-09-10", "1m", 680_000, 100, 0, 100, "r0")).reason());
        FootprintStrikeView.Page p = v.latest("1m", "2026-09-10", -1, 200);
        assertEquals(1, p.records().size()); assertTrue(p.records().get(0).contains("\"r2\""), "revision 2 wins; the late revision 0 does not replace it");
        assertEquals(1, v.episodesInView());
    }

    @Test void aCollisionRefusesTheIdentity_andIdenticalBytesAreNotACollision() {
        FootprintStrikeView v = view();
        String a = episode("OPEN", "2026-09-10", "1m", 680_000, 100, 0, 100, "a");
        assertEquals(FootprintStrikeView.Reason.ADMITTED, v.admit(a).reason());
        assertEquals(FootprintStrikeView.Reason.ADMITTED, v.admit(a).reason(), "a bar re-processed after a crash collides with itself");
        assertEquals(0, v.collisions());
        assertEquals(FootprintStrikeView.Reason.COLLISION, v.admit(episode("OPEN", "2026-09-10", "1m", 680_000, 100, 0, 100, "b")).reason());
        assertEquals(1, v.collisions()); assertEquals(1, v.refusedIdentities());
        assertEquals(0, v.latest("1m", "2026-09-10", -1, 200).records().size(), "the chip renders NO DATA, not one of two contradictory records");
        assertEquals(FootprintStrikeView.Reason.REFUSED, v.admit(episode("UPDATE", "2026-09-10", "1m", 680_000, 100, 1, 200, "c")).reason(), "refused for the incarnation");
        assertTrue(v.helloField().endsWith(",\"refused\":1}"));
        // a DIFFERENT identity at the same strike is unaffected
        assertEquals(FootprintStrikeView.Reason.ADMITTED, v.admit(episode("OPEN", "2026-09-10", "1m", 680_000, 900, 0, 900, "d")).reason());
        assertEquals(1, v.latest("1m", "2026-09-10", -1, 200).records().size());
    }

    @Test void latestIsScopedToOneSessionAndOneTimeframe_historyCrossesSessionsNewestFirst() {
        FootprintStrikeView v = view();
        v.admit(episode("CLOSE", "2026-09-09", "1m", 680_000, 10, 3, 20, "yesterday"));
        v.admit(episode("CLOSE", "2026-09-10", "1m", 680_000, 100, 2, 200, "today-first"));
        v.admit(episode("OPEN", "2026-09-10", "1m", 680_000, 300, 0, 300, "today-latest"));
        v.admit(episode("OPEN", "2026-09-10", "5m", 680_000, 300, 0, 300, "five-minute"));
        v.admit(episode("OPEN", "2026-09-10", "1m", 685_000, 300, 0, 300, "other-strike"));
        FootprintStrikeView.Page latest = v.latest("1m", "2026-09-10", -1, 200);
        assertEquals(2, latest.records().size());
        assertTrue(latest.records().get(0).contains("today-latest"), "greatest openBarStartMs of THIS session");
        assertTrue(latest.records().get(1).contains("other-strike"), "ascending by strike");
        assertTrue(v.latest("1m", "2026-09-11", -1, 200).records().isEmpty(), "a session with no episode: no row — NO DATA is the reader's word, never yesterday's value");
        assertEquals(1, v.latest("5m", "2026-09-10", -1, 200).records().size(), "timeframes are never merged");
        FootprintStrikeView.Page h = v.history("1m", 680_000, "", 100);
        assertEquals(List.of("today-latest", "today-first", "yesterday"), h.records().stream().map(r -> r.replaceAll(".*\"tag\":\"([^\"]+)\".*", "$1")).toList());
        assertNull(h.nextCursor());
        FootprintStrikeView.Page h1 = v.history("1m", 680_000, "", 1);
        assertEquals("2026-09-10|" + String.format("%019d", 300), h1.nextCursor());
        FootprintStrikeView.Page h2 = v.history("1m", 680_000, h1.nextCursor(), 1);
        assertTrue(h2.records().get(0).contains("today-first"), "the cursor is exclusive");
        assertTrue(FootprintStrikeView.validHistoryCursor(h1.nextCursor())); assertFalse(FootprintStrikeView.validHistoryCursor("garbage"));
        FootprintStrikeView.Page l1 = v.latest("1m", "2026-09-10", -1, 1);
        assertEquals("680000", l1.nextCursor());
        assertTrue(v.latest("1m", "2026-09-10", 680_000, 1).records().get(0).contains("other-strike"));
    }

    @Test void checkpointsAdvanceTheHelloHighWaterMarkAndCarryNoEpisode() {
        FootprintStrikeView v = view();
        assertEquals("{\"sessionDate\":null,\"hwm\":{},\"historyBeginsAtMs\":null,\"refused\":0}", v.helloField());
        assertTrue(v.admit(checkpoint("2026-09-10", "1m", 500)).checkpoint());
        assertEquals(0, v.episodesInView());
        v.admit(episode("OPEN", "2026-09-10", "5m", 680_000, 300, 0, 300, "x"));
        v.admit(checkpoint("2026-09-10", "1m", 400));                          // older watermark: never regresses
        assertEquals("{\"sessionDate\":\"2026-09-10\",\"hwm\":{\"1m\":500,\"5m\":300},\"historyBeginsAtMs\":300,\"refused\":0}", v.helloField());
    }

    @Test void theBudgetsEvictTheOldestIdentitiesAndTheBoundaryIsPublished() {
        FootprintStrikeView v = new FootprintStrikeView(new ObjectMapper(), 262_144, 1 << 20, 3);
        for (int i = 1; i <= 5; i++) v.admit(episode("CLOSE", "2026-09-10", "1m", 680_000 + i * 500, i * 100, 1, i * 100, "e" + i));
        assertEquals(3, v.episodesInView()); assertEquals(2, v.evictions());
        assertEquals(Long.valueOf(300), v.historyBeginsAtMs(), "history begins at the oldest RETAINED episode");
        assertEquals(Long.valueOf(300), v.latest("1m", "2026-09-10", -1, 200).historyBeginsAtMs());
        FootprintStrikeView small = new FootprintStrikeView(new ObjectMapper(), 262_144, 600, 1000);
        small.admit(episode("OPEN", "2026-09-10", "1m", 680_000, 100, 0, 100, "one"));
        small.admit(episode("OPEN", "2026-09-10", "1m", 680_500, 200, 0, 200, "two"));
        assertEquals(1, small.episodesInView(), "the byte budget evicts too");
        assertTrue(small.bytesInView() <= 600);
    }

    @Test void shapeAndOversizeAreDropsNotCrashes() {
        FootprintStrikeView v = view();
        assertEquals(FootprintStrikeView.Reason.SHAPE, v.admit("not json").reason());
        assertEquals(FootprintStrikeView.Reason.SHAPE, v.admit("{\"kind\":\"OPEN\"}").reason());
        assertEquals(FootprintStrikeView.Reason.SHAPE, v.admit("{\"kind\":\"VERDICT\",\"symbol\":\"ES\",\"timeframe\":\"1m\",\"seenMaxBarStartMs\":1}").reason(), "an unknown kind is a shape drop");
        assertEquals(FootprintStrikeView.Reason.OVERSIZE, v.admit("{\"pad\":\"" + "y".repeat(300_000) + "\"}").reason());
        assertEquals(0, v.episodesInView());
    }
}

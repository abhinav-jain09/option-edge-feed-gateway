package app.feedgateway;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class MarketCommentaryViewTest {
    static String rec(String date, String minute, int revision, String alignment) {
        return "{\"schemaVersion\":1,\"tradeDate\":\"" + date + "\",\"minute\":\"" + minute + "\",\"revision\":" + revision
                + ",\"finalizedBy\":\"WATERMARK\",\"alignment\":\"" + alignment + "\",\"lean\":\"DOWN\",\"alignmentQuality\":\"PARTIAL\"}";
    }

    @Test void newestRecordIsLatestAndEveryMinuteIsACell() {
        MarketCommentaryView v = new MarketCommentaryView();
        assertTrue(v.accept(rec("2026-10-13", "09:31", 0, "UNAVAILABLE")));
        assertTrue(v.accept(rec("2026-10-13", "09:32", 0, "AUCTION_ONLY")));
        MarketCommentaryView.Page p = v.page(true, "");
        assertEquals("2026-10-13", p.tradeDate());
        assertTrue(p.latestJson().contains("\"09:32\""));
        assertEquals(2, p.cells().size());
        assertEquals("AUCTION_ONLY", p.cells().get(1).alignment());
    }

    @Test void refusesWrongRevisionBadShapeAndOversize() {
        MarketCommentaryView v = new MarketCommentaryView();
        assertFalse(v.accept(rec("2026-10-13", "09:31", 1, "X")));
        assertFalse(v.accept("not json"));
        assertFalse(v.accept(rec("2026-13-45", "09:31", 0, "X")));
        assertFalse(v.accept(rec("2026-10-13", "9:31", 0, "X")));
        assertFalse(v.accept("{\"schemaVersion\":1,\"padding\":\"" + "x".repeat(70_000) + "\"}"));
        assertEquals(5, v.seen());
        assertEquals(5, v.refused());
        assertEquals(0, v.cached());
    }

    @Test void onlyTheTwoNewestTradeDatesAreKeptAndARedeliveryReplaces() {
        MarketCommentaryView v = new MarketCommentaryView();
        v.accept(rec("2026-10-09", "09:31", 0, "A"));
        v.accept(rec("2026-10-12", "09:31", 0, "B"));
        v.accept(rec("2026-10-13", "09:31", 0, "C"));
        v.accept(rec("2026-10-13", "09:31", 0, "D"));
        assertEquals(2, v.cached());
        assertTrue(v.page(true, "2026-10-09").cells().isEmpty());
        assertEquals("D", v.page(true, "2026-10-13").cells().get(0).alignment());
        assertFalse(v.page(false, "").enabled());
    }
}

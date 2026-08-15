package app.feedgateway;

import static org.junit.jupiter.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

/** The keyed CVD bar view: upsert semantics, session rollover, pagination, high-water marks (R37a/R46). */
class CvdBarViewTest {

    private static FeedGatewayService service() {
        return new FeedGatewayService(new GatewaySettings(), new ObjectMapper(), new HpsfGatewayViewMapper(), null);
    }

    private static String bar(String sessionDate, String tf, long startMs, long closeCvd) {
        return "{\"schemaVersion\":\"1.0.0\",\"symbol\":\"ES.v.0\",\"timeframe\":\"" + tf
                + "\",\"widthMs\":30000,\"sessionDate\":\"" + sessionDate
                + "\",\"bar\":{\"barStartMs\":" + startMs + ",\"closeCvd\":" + closeCvd + "}}";
    }

    @Test void atLeastOnceDeliveryIsInvisibleLastRecordPerKeyWins() {
        var s = service();
        s.upsertCvdBar(bar("20260814", "30s", 1000, 5));
        s.upsertCvdBar(bar("20260814", "30s", 1000, 7));      // redelivery/overwrite
        var page = s.cvdBarsSnapshot("30s", Long.MAX_VALUE, -1, 10);
        assertEquals(1, page.size(), "one key, one record");
        assertTrue(page.get(0).contains("\"closeCvd\":7"), "last write wins");
    }

    @Test void paginationIsAscendingExclusiveCursorInclusiveBound() {
        var s = service();
        for (long start = 1000; start <= 5000; start += 1000) s.upsertCvdBar(bar("20260814", "30s", start, start));
        s.upsertCvdBar(bar("20260814", "1m", 1000, 99));       // other timeframe must not leak in
        var first = s.cvdBarsSnapshot("30s", 5000, -1, 2);
        assertEquals(2, first.size());
        assertTrue(first.get(0).contains("\"barStartMs\":1000"));
        var second = s.cvdBarsSnapshot("30s", 5000, 2000, 10);
        assertEquals(3, second.size(), "cursor is exclusive, bound is inclusive");
        assertTrue(second.get(0).contains("\"barStartMs\":3000"));
    }

    @Test void sessionRolloverClearsTheViewAndHwmTracksPerTimeframe() {
        var s = service();
        s.upsertCvdBar(bar("20260814", "30s", 1000, 1));
        s.upsertCvdBar(bar("20260814", "1m", 2000, 2));
        assertEquals(1000L, s.cvdBarsHighWaterMarks().get("30s"));
        assertEquals(2000L, s.cvdBarsHighWaterMarks().get("1m"));
        s.upsertCvdBar(bar("20260817", "30s", 9000, 3));       // Monday session arrives
        assertEquals("20260817", s.cvdBarsSessionDate());
        assertNull(s.cvdBarsHighWaterMarks().get("1m"), "previous session's bars are gone");
        assertEquals(0, s.cvdBarsSnapshot("1m", Long.MAX_VALUE, -1, 10).size());
        assertTrue(s.cvdHelloJson().contains("\"sessionDate\":\"20260817\""));
        assertTrue(s.cvdHelloJson().contains("\"30s\":9000"));
    }

    @Test void foreignShapesNeverPoisonTheView() {
        var s = service();
        s.upsertCvdBar("{\"unrelated\":true}");
        s.upsertCvdBar("not json at all");
        assertEquals(0, s.cvdBarsSnapshot("30s", Long.MAX_VALUE, -1, 10).size());
    }
}

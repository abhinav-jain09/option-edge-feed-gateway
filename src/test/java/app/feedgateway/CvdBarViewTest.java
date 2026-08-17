package app.feedgateway;

import static org.junit.jupiter.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** The keyed CVD bar view: upsert semantics, session rollover, pagination, high-water marks (R37a/R46). */
class CvdBarViewTest {

    @Test void bootstrapAndLiveConsumersShareOneCvdTopicWiringPath() throws Exception {
        String source = Files.readString(Path.of("src/main/java/app/feedgateway/FeedGatewayService.java"));
        assertEquals(2, occurrences(source, "addEsCvdTopics(topicEvents);"));
        assertEquals(1, occurrences(source,
                "topicEvents.put(settings.esCvdTopic(), new TopicBinding(\"DATABENTO\", \"es-cvd\"));"));
        assertEquals(1, occurrences(source,
                "topicEvents.put(settings.esCvdBarsTopic(), new TopicBinding(\"DATABENTO\", \"es-cvd-bar\"));"));
    }

    private static int occurrences(String text, String needle) {
        int count = 0;
        for (int at = 0; (at = text.indexOf(needle, at)) >= 0; at += needle.length()) count++;
        return count;
    }

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
        var page = s.cvdBarsPage("30s", Long.MAX_VALUE, -1, 10, "");
        assertEquals(1, page.bars().size(), "one key, one record");
        assertTrue(page.bars().get(0).contains("\"closeCvd\":7"), "last write wins");
    }

    @Test void paginationIsAscendingExclusiveCursorInclusiveBound() {
        var s = service();
        for (long start = 1000; start <= 5000; start += 1000) s.upsertCvdBar(bar("20260814", "30s", start, start));
        s.upsertCvdBar(bar("20260814", "1m", 1000, 99));       // other timeframe must not leak in
        var first = s.cvdBarsPage("30s", 5000, -1, 2, "");
        assertEquals(2, first.bars().size());
        assertTrue(first.bars().get(0).contains("\"barStartMs\":1000"));
        assertEquals(2000L, first.nextCursor(), "a full page carries the cursor, computed atomically");
        var second = s.cvdBarsPage("30s", 5000, first.nextCursor(), 10, "");
        assertEquals(3, second.bars().size(), "cursor is exclusive, bound is inclusive");
        assertTrue(second.bars().get(0).contains("\"barStartMs\":3000"));
        assertNull(second.nextCursor(), "a short page ends pagination");
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
        assertEquals(0, s.cvdBarsPage("1m", Long.MAX_VALUE, -1, 10, "").bars().size());
        assertTrue(s.cvdHelloJson().contains("\"sessionDate\":\"20260817\""));
        assertTrue(s.cvdHelloJson().contains("\"30s\":9000"));
    }

    @Test void foreignShapesNeverPoisonTheView() {
        var s = service();
        s.upsertCvdBar("{\"unrelated\":true}");
        s.upsertCvdBar("not json at all");
        assertEquals(0, s.cvdBarsPage("30s", Long.MAX_VALUE, -1, 10, "").bars().size());
    }

    @Test void aLateBarFromADeadSessionCanNeverRollTheViewBackwards() {
        var s = service();
        s.upsertCvdBar(bar("20260817", "30s", 9000, 3));       // Monday's view is live
        s.upsertCvdBar(bar("20260814", "30s", 1000, 9));       // late Friday record arrives
        assertEquals("20260817", s.cvdBarsSessionDate(), "rollover is monotonic");
        var page = s.cvdBarsPage("30s", Long.MAX_VALUE, -1, 10, "");
        assertEquals(1, page.bars().size(), "the stale-session record was dropped, not merged");
        assertTrue(page.bars().get(0).contains("\"closeCvd\":3"));
    }

    @Test void aPinnedSessionMismatchIsExplicitAndCarriesTheCurrentSession() {
        var s = service();
        s.upsertCvdBar(bar("20260817", "30s", 9000, 3));
        var page = s.cvdBarsPage("30s", Long.MAX_VALUE, -1, 10, "20260814");
        assertTrue(page.sessionMismatch(), "a rolled view must refuse a page pinned to a dead session");
        assertEquals("20260817", page.sessionDate());
        assertEquals(0, page.bars().size());
        var ok = s.cvdBarsPage("30s", Long.MAX_VALUE, -1, 10, "20260817");
        assertFalse(ok.sessionMismatch());
        assertEquals(1, ok.bars().size());
    }
}

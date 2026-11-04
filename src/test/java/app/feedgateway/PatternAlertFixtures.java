package app.feedgateway;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;

/**
 * Alert records built from the contracts 0.66.0 schemas (src/test/resources/ta, byte-identical copies pinned by TaSchemaPinsTest), with the facts of the live session of 2026-11-03
 * (requirement section 9.1): a 1m symmetrical triangle broke UP (break bar 158, accepted at 160 by TIME, price 757 821 ticks), a 1m rising wedge broke DOWN (break bar 248, accepted at 250,
 * price 758 786 ticks) and returned to its lower boundary (ordinal 254, price 758 937 ticks). Prices are ticks of one cent; the session opens 09:30 EST (the clocks changed on 2026-11-01).
 */
final class PatternAlertFixtures {
    private PatternAlertFixtures() {
    }

    static final Schema EVENT = TaFixtures.schema("PatternAlertEvent");
    static final Schema WATERMARK = TaFixtures.schema("PatternAlertWatermark");
    static final String DATE = "2026-11-03";
    static final long OPEN_MS = ZonedDateTime.of(2026, 11, 3, 9, 30, 0, 0, ZoneId.of("America/New_York")).toInstant().toEpochMilli();
    static final long WINDOW_MS = 1_800_000L;
    static final String TRIANGLE = "1m|TRIANGLE_SYMMETRICAL|ms1:2026-11-03.r0000000000|1m|30|LOW";
    static final String WEDGE = "1m|WEDGE_RISING|ms1:2026-11-03.r0000000000|1m|160|HIGH";

    static long barEnd(int ordinal) {
        return OPEN_MS + (ordinal + 1L) * 60_000L;
    }

    /** A complete event; {@code kind} BREAK, RETURN or FAILED. {@code availableMs} is the end of the accepting bar for a break, else the event bar. */
    static GenericRecord event(String date, long seq, String kind, String disposition, String timeframe, String annotationId, String patternId, String family, String label,
                               String bias, String direction, String stance, int ordinal, int breakOrdinal, int confirmOrdinal, long priceTicks, String line, String speech) {
        GenericRecord r = TaFixtures.minimal(EVENT);
        long eventMs = barEnd(ordinal);
        long availableMs = "BREAK".equals(kind) ? barEnd(confirmOrdinal) : eventMs;
        TaFixtures.put(r, "schemaVersion", 1);
        TaFixtures.put(r, "service", "market-structure");
        TaFixtures.put(r, "sessionDate", date);
        TaFixtures.put(r, "scheduleId", "ms1:" + date + ".r0000000000");
        TaFixtures.put(r, "eventId", date + "|" + annotationId + "|" + kind + "|" + ordinal + ("BREAK".equals(kind) ? "|" + direction : ""));
        TaFixtures.put(r, "eventSeq", seq);
        TaFixtures.put(r, "kind", kind);
        TaFixtures.put(r, "disposition", disposition);
        TaFixtures.put(r, "suppressDetail", disposition.startsWith("SUPPRESSED") ? "LAG_EXCEEDED" : null);
        TaFixtures.put(r, "timeframe", timeframe);
        TaFixtures.put(r, "annotationId", annotationId);
        TaFixtures.put(r, "patternId", patternId);
        TaFixtures.put(r, "patternFamily", family);
        TaFixtures.put(r, "label", label);
        TaFixtures.put(r, "bias", bias);
        TaFixtures.put(r, "direction", direction);
        TaFixtures.put(r, "stance", stance);
        TaFixtures.put(r, "acceptedVia", "BREAK".equals(kind) ? "TIME" : null);
        TaFixtures.put(r, "edge", "NOT_MEASURED");
        TaFixtures.put(r, "ordinal", ordinal);
        TaFixtures.put(r, "breakOrdinal", breakOrdinal);
        TaFixtures.put(r, "confirmOrdinal", confirmOrdinal);
        TaFixtures.put(r, "priceTicks", priceTicks);
        TaFixtures.put(r, "eventMs", eventMs);
        TaFixtures.put(r, "breakBarEndMs", barEnd(breakOrdinal));
        TaFixtures.put(r, "acceptedMs", barEnd(confirmOrdinal));
        TaFixtures.put(r, "availableMs", availableMs);
        TaFixtures.put(r, "speakUntilMs", availableMs + WINDOW_MS);
        TaFixtures.put(r, "asOfMs", barEnd(confirmOrdinal));
        TaFixtures.put(r, "decisionOrdinal", confirmOrdinal);
        TaFixtures.put(r, "publishedAtMs", availableMs + 1_000L);
        TaFixtures.put(r, "lagMs", 1_000L);
        TaFixtures.put(r, "textVersion", 1);
        TaFixtures.put(r, "line", line);
        TaFixtures.put(r, "detail", "Accepted at " + hm(confirmOrdinal) + " after the break bar");
        TaFixtures.put(r, "tip", "");
        TaFixtures.put(r, "speech", speech);
        GenericRecord nav = TaFixtures.child(r, "nav");
        nav.put("firstOrdinal", Math.max(0, breakOrdinal - 60));
        nav.put("lastOrdinal", confirmOrdinal);
        nav.put("rthOpenMs", OPEN_MS);
        nav.put("slotMs", 60_000L);
        nav.put("priceHigh", priceTicks + 500L);
        nav.put("priceLow", priceTicks - 900L);
        return r;
    }

    private static String hm(int ordinal) {
        long minutes = (ordinal + 1L);
        long total = 9 * 60 + 30 + minutes;
        return String.format("%02d:%02d", total / 60, total % 60);
    }

    static GenericRecord triangleBreak(long seq, String disposition) {
        return event(DATE, seq, "BREAK", disposition, "TF_1M", TRIANGLE, "TRIANGLE_SYMMETRICAL", "TRIANGLE", "Symmetrical triangle", "BULLISH", "UP", "WITH", 158, 158, 160, 757_821L,
                "Symmetrical triangle (1m) closed above its upper boundary at 12:09 · bias bullish · price 7,578.21 · edge: none measured",
                "Bullish symmetrical triangle, one minute, broke up at 7,578.21");
    }

    static GenericRecord wedgeBreak(long seq, String disposition) {
        return event(DATE, seq, "BREAK", disposition, "TF_1M", WEDGE, "WEDGE_RISING", "WEDGE", "Rising wedge", "BEARISH", "DOWN", "WITH", 248, 248, 250, 758_786L,
                "Rising wedge (1m) closed below its lower boundary at 13:39 · bias bearish · price 7,587.86 · edge: none measured",
                "Bearish rising wedge, one minute, broke down at 7,587.86");
    }

    static GenericRecord wedgeReturn(long seq, String disposition) {
        return event(DATE, seq, "RETURN", disposition, "TF_1M", WEDGE, "WEDGE_RISING", "WEDGE", "Rising wedge", "BEARISH", "DOWN", "WITH", 254, 248, 250, 758_937L,
                "Rising wedge (1m) came back to its lower boundary at 13:45 · after the close below · bias bearish · price 7,589.37 · edge: none measured",
                "Bearish rising wedge, one minute, came back to its lower boundary at 7,589.37");
    }

    /** A filler event of the given sequence: a distinct 5m annotation so that nothing collides; {@code ordinal} is the break bar. */
    static GenericRecord numbered(String date, long seq, String disposition, int ordinal) {
        String annotation = "5m|WEDGE_FALLING|ms1:" + date + ".r0000000000|5m|" + ordinal + "|LOW";
        return event(date, seq, "BREAK", disposition, "TF_5M", annotation, "WEDGE_FALLING", "WEDGE", "Falling wedge", "BULLISH", "UP", "WITH", ordinal, ordinal, ordinal + 2, 757_000L + ordinal,
                "Falling wedge (5m) closed above its upper boundary · bias bullish · price 7,570.00 · edge: none measured", "Bullish falling wedge, five minute, broke up at 7,570.00");
    }

    static GenericRecord watermark(String date, String timeframe, int decision, int previous, boolean evaluated, long eventsThroughSeq) {
        GenericRecord r = TaFixtures.minimal(WATERMARK);
        TaFixtures.put(r, "schemaVersion", 1);
        TaFixtures.put(r, "service", "market-structure");
        TaFixtures.put(r, "sessionDate", date);
        TaFixtures.put(r, "scheduleId", "ms1:" + date + ".r0000000000");
        TaFixtures.put(r, "timeframe", timeframe);
        TaFixtures.put(r, "decisionOrdinal", decision);
        TaFixtures.put(r, "previousDecisionOrdinal", previous);
        TaFixtures.put(r, "asOfMs", barEnd(decision));
        TaFixtures.put(r, "evaluated", evaluated);
        TaFixtures.put(r, "unavailableReason", evaluated ? null : "FOLD_FAILURE");
        TaFixtures.put(r, "eventsThroughSeq", eventsThroughSeq);
        TaFixtures.put(r, "publishedAtMs", barEnd(decision) + 500L);
        return r;
    }

    /** Sets a string/long/int field and returns the record, for one-off variations. */
    static GenericRecord with(GenericRecord r, String field, Object value) {
        return TaFixtures.put(r, field, value);
    }
}

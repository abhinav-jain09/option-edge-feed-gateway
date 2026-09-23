package app.feedgateway;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Read-only, bounded WebSocket history for Basic. Reuses the existing admitted footprint view. */
final class FootprintBasicHistory {
    static final String REQUEST = "es-footprint-basic-history";
    static final String RESPONSE = "es-footprint-basic-history";
    static final int MAX_REQUEST_BYTES = 1024;
    static final int MAX_PAGE_RECORDS = 4;
    private static final ZoneId NEW_YORK = ZoneId.of("America/New_York");
    /** Basic receives bars already grouped by the selected period; it never rolls up market data in the browser. */
    private static final Map<String, Long> WIDTHS_MS = Map.of(
            "1m", 60_000L, "5m", 300_000L, "15m", 900_000L,
            "30m", 1_800_000L, "1h", 3_600_000L, "4h", 14_400_000L);

    private FootprintBasicHistory() { }

    static String reply(ObjectMapper mapper, FootprintViews view, JsonNode request, int limit) {
        ObjectNode out = mapper.createObjectNode();
        if (view == null) return out.put("error", "unavailable").toString();
        if (limit < 1) return out.put("error", "record_budget").toString();
        LocalDate date = FootprintViews.parseCanonicalDate(request.path("sessionDate").asText());
        String timeframe = request.path("timeframe").asText("1m");
        JsonNode after = request.path("afterMs"), to = request.path("toMs");
        if (date == null || !WIDTHS_MS.containsKey(timeframe) || !after.isIntegralNumber() || !after.canConvertToLong()
                || !to.isIntegralNumber() || !to.canConvertToLong()) {
            return out.put("error", "bad_request").toString();
        }
        long start = date.minusDays(1).atTime(18, 0).atZone(NEW_YORK).toInstant().toEpochMilli();
        long end = date.atTime(16, 0).atZone(NEW_YORK).toInstant().toEpochMilli();
        long cursor = after.longValue(), ceiling = to.longValue();
        if (cursor < start - 1 || cursor >= end || ceiling < start - 1 || ceiling >= end) {
            return out.put("error", "bad_request").toString();
        }
        FootprintViews.BarsPage page = "1h".equals(timeframe)
                ? hourlyPage(mapper, view, date.toString(), start, end, cursor, ceiling, Math.min(limit, MAX_PAGE_RECORDS))
                : view.barsPage(timeframe, ceiling, cursor, Math.min(limit, MAX_PAGE_RECORDS), date.toString());
        if (page.sessionDate() == null) out.putNull("sessionDate"); else out.put("sessionDate", page.sessionDate());
        out.put("sessionMismatch", page.sessionMismatch());
        out.put("timeframe", timeframe);
        out.put("afterMs", cursor);
        out.put("toMs", ceiling);
        out.put("sessionStartMs", start);
        out.put("sessionEndMs", end);
        // The admitted JSON is parsed, never executed or embedded as page code.
        var rows = out.putArray("bars");
        try {
            for (String record : page.records()) rows.add(mapper.readTree(record));
        } catch (Exception invalid) {
            return mapper.createObjectNode().put("error", "invalid_history").toString();
        }
        if (page.nextCursor() == null) out.putNull("nextCursor"); else out.put("nextCursor", page.nextCursor());
        return out.toString();
    }

    /** Build the one unsupported producer period from the admitted 1-minute records, on the gateway. */
    private static FootprintViews.BarsPage hourlyPage(ObjectMapper mapper, FootprintViews view, String date,
                                                       long sessionStart, long sessionEnd, long after, long to, int limit) {
        long width = WIDTHS_MS.get("1h"), first = bucketAfter(after, sessionStart, width);
        long last = bucketAt(to, sessionStart, width);
        List<String> rows = new ArrayList<>();
        long inspected = after;
        for (long bucket = first; bucket <= last && bucket < sessionEnd; bucket += width) {
            long bucketEnd = Math.min(sessionEnd, bucket + width);
            FootprintViews.BarsPage minutes = view.barsPage("1m", Math.min(to, bucketEnd - 1), bucket - 1, 60, date);
            if (minutes.sessionMismatch()) return new FootprintViews.BarsPage(minutes.sessionDate(), true, List.of(), null);
            String grouped = aggregateHour(mapper, minutes.records(), bucket, bucketEnd);
            inspected = bucket;
            if (grouped != null) rows.add(grouped);
            if (rows.size() >= limit) break;
        }
        boolean more = inspected < last && inspected < sessionEnd - 1;
        return new FootprintViews.BarsPage(date, false, rows, more ? inspected : null);
    }

    private static long bucketAt(long timestamp, long sessionStart, long width) {
        return sessionStart + Math.max(0L, (timestamp - sessionStart) / width) * width;
    }

    private static long bucketAfter(long cursor, long sessionStart, long width) {
        if (cursor < sessionStart) return sessionStart;
        long bucket = bucketAt(cursor, sessionStart, width);
        return cursor >= bucket ? bucket + width : bucket;
    }

    /**
     * Produces a deliberately small display record. Every sum and OHLC value is calculated here from
     * admitted one-minute records; the browser receives no constituent bars to aggregate.
     */
    private static String aggregateHour(ObjectMapper mapper, List<String> rows, long start, long end) {
        if (rows.isEmpty()) return null;
        try {
            JsonNode first = mapper.readTree(rows.get(0));
            String symbol = first.path("symbol").asText();
            if (symbol.isBlank()) return null;
            ObjectNode record = mapper.createObjectNode();
            record.put("schemaVersion", 6); record.put("symbol", symbol); record.put("timeframe", "1h");
            record.put("tickCents", first.path("tickCents").asInt(25)); record.put("widthMs", WIDTHS_MS.get("1h"));
            record.put("sessionDate", first.path("sessionDate").asText());
            ObjectNode o = record.putObject("observations");
            o.put("barStartMs", start); o.put("barEndMs", end);
            boolean complete = true, deterministic = true, truncated = false;
            long volume = 0, delta = 0; Long open = null, high = null, low = null, close = null;
            Map<Long, long[]> levels = new java.util.TreeMap<>();
            for (String json : rows) {
                JsonNode r = mapper.readTree(json), source = r.path("observations");
                if (!symbol.equals(r.path("symbol").asText()) || !source.isObject()) return null;
                complete &= source.path("complete").asBoolean(false);
                deterministic &= source.path("replayDeterministic").asBoolean(false);
                truncated |= source.path("levelsTruncated").asBoolean(false);
                volume = Math.addExact(volume, source.path("volume").asLong());
                delta = Math.addExact(delta, source.path("delta").asLong());
                long sourceOpen = source.path("openCents").asLong(), sourceHigh = source.path("highCents").asLong();
                long sourceLow = source.path("lowCents").asLong(), sourceClose = source.path("closeCents").asLong();
                if (sourceOpen <= 0 || sourceHigh <= 0 || sourceLow <= 0 || sourceClose <= 0) complete = false;
                else { if (open == null) open = sourceOpen; high = high == null ? sourceHigh : Math.max(high, sourceHigh);
                    low = low == null ? sourceLow : Math.min(low, sourceLow); close = sourceClose; }
                for (JsonNode level : source.path("levels")) {
                    long price = level.path("priceCents").asLong(); if (price <= 0) return null;
                    long[] totals = levels.computeIfAbsent(price, ignored -> new long[3]);
                    totals[0] = Math.addExact(totals[0], level.path("bid").asLong());
                    totals[1] = Math.addExact(totals[1], level.path("ask").asLong());
                    totals[2] = Math.addExact(totals[2], level.path("unk").asLong());
                }
            }
            if (levels.size() > 4_000) truncated = true;
            o.put("complete", complete && !truncated); o.put("barQuality", complete && !truncated ? "READABLE" : "PARTIAL");
            o.put("replayDeterministic", deterministic); o.put("levelsTruncated", truncated); o.put("volume", volume); o.put("delta", delta);
            if (open != null) { o.put("openCents", open); o.put("highCents", high); o.put("lowCents", low); o.put("closeCents", close); }
            var levelsOut = o.putArray("levels"); int emitted = 0;
            for (Map.Entry<Long, long[]> entry : levels.entrySet()) { if (emitted++ >= 4_000) break; var level = levelsOut.addObject();
                level.put("priceCents", entry.getKey()); level.put("bid", entry.getValue()[0]); level.put("ask", entry.getValue()[1]); level.put("unk", entry.getValue()[2]); }
            record.putObject("structure");
            return mapper.writeValueAsString(record);
        } catch (Exception invalid) { return null; }
    }
}

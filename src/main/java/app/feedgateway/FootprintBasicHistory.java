package app.feedgateway;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.LocalDate;
import java.time.ZoneId;

/** Read-only, bounded WebSocket history for Basic. Reuses the existing admitted footprint view. */
final class FootprintBasicHistory {
    static final String REQUEST = "es-footprint-basic-history";
    static final String RESPONSE = "es-footprint-basic-history";
    static final int MAX_REQUEST_BYTES = 1024;
    static final int MAX_PAGE_RECORDS = 4;
    private static final ZoneId NEW_YORK = ZoneId.of("America/New_York");

    private FootprintBasicHistory() { }

    static String reply(ObjectMapper mapper, FootprintViews view, JsonNode request, int limit) {
        ObjectNode out = mapper.createObjectNode();
        if (view == null) return out.put("error", "unavailable").toString();
        if (limit < 1) return out.put("error", "record_budget").toString();
        LocalDate date = FootprintViews.parseCanonicalDate(request.path("sessionDate").asText());
        JsonNode after = request.path("afterMs"), to = request.path("toMs");
        if (date == null || !after.isIntegralNumber() || !after.canConvertToLong()
                || !to.isIntegralNumber() || !to.canConvertToLong()) {
            return out.put("error", "bad_request").toString();
        }
        long start = date.minusDays(1).atTime(18, 0).atZone(NEW_YORK).toInstant().toEpochMilli();
        long end = date.atTime(16, 0).atZone(NEW_YORK).toInstant().toEpochMilli();
        long cursor = after.longValue(), ceiling = to.longValue();
        if (cursor < start - 1 || cursor >= end || ceiling < start - 1 || ceiling >= end) {
            return out.put("error", "bad_request").toString();
        }
        FootprintViews.BarsPage page = view.barsPage("1m", ceiling, cursor,
                Math.min(limit, MAX_PAGE_RECORDS), date.toString());
        if (page.sessionDate() == null) out.putNull("sessionDate"); else out.put("sessionDate", page.sessionDate());
        out.put("sessionMismatch", page.sessionMismatch());
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
}

package app.feedgateway;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;

/**
 * Read-only view of the market commentary service's per-minute records ({@code options.spx.market-commentary.minute}) for the
 * {@code /market-commentary} panel: the newest record of a trade date in full, plus one compact cell per minute for the session strip.
 * Only the current and the previous trade date are kept (a trade date is at most 391 records). The service publishes revision 0 only;
 * anything else is refused at the boundary. Polled over REST, deliberately no WebSocket event.
 */
final class MarketCommentaryView {
    static final int MAX_RECORD_BYTES = 65_536;
    private static final Pattern DATE = Pattern.compile("\\d{4}-\\d{2}-\\d{2}");
    private static final Pattern MINUTE = Pattern.compile("([01]\\d|2[0-3]):[0-5]\\d");
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** One minute of the strip. */
    record Cell(String minute, String alignment, String lean, String quality, String finalizedBy) { }

    private record Held(Cell cell, String json) { }

    /** The page the controller serialises (one atomic snapshot). */
    record Page(boolean enabled, String tradeDate, String latestJson, List<Cell> cells, long recordsSeen, long recordsRefused) { }

    private final TreeMap<String, TreeMap<String, Held>> byDate = new TreeMap<>();
    private final AtomicLong seen = new AtomicLong();
    private final AtomicLong refused = new AtomicLong();
    private final AtomicLong requests = new AtomicLong();

    /** Accepts one record; returns false (and counts it) when it is not a valid revision-0 minute record. */
    boolean accept(String json) {
        seen.incrementAndGet();
        if (json == null || json.getBytes(StandardCharsets.UTF_8).length > MAX_RECORD_BYTES) { refused.incrementAndGet(); return false; }
        try {
            JsonNode n = MAPPER.readTree(json);
            if (n == null || !n.isObject() || n.path("schemaVersion").asInt(-1) != 1 || n.path("revision").asInt(-1) != 0) { refused.incrementAndGet(); return false; }
            String date = text(n, "tradeDate"), minute = text(n, "minute");
            if (date == null || minute == null || !DATE.matcher(date).matches() || !MINUTE.matcher(minute).matches()) { refused.incrementAndGet(); return false; }
            LocalDate.parse(date);
            String alignment = text(n, "alignment"), lean = text(n, "lean"), quality = text(n, "alignmentQuality"), by = text(n, "finalizedBy");
            if (alignment == null || lean == null || quality == null || by == null) { refused.incrementAndGet(); return false; }
            synchronized (byDate) {
                byDate.computeIfAbsent(date, d -> new TreeMap<>()).put(minute, new Held(new Cell(minute, alignment, lean, quality, by), json));
                while (byDate.size() > 2) byDate.pollFirstEntry();
            }
            return true;
        } catch (Exception e) {
            refused.incrementAndGet();
            return false;
        }
    }

    private static String text(JsonNode n, String field) {
        JsonNode f = n.path(field);
        return f.isTextual() && f.asText().length() <= 64 ? f.asText() : null;
    }

    Page page(boolean enabled, String tradeDate) {
        requests.incrementAndGet();
        synchronized (byDate) {
            String date = tradeDate == null || tradeDate.isEmpty() ? (byDate.isEmpty() ? null : byDate.lastKey()) : tradeDate;
            TreeMap<String, Held> day = date == null ? null : byDate.get(date);
            if (day == null || day.isEmpty()) return new Page(enabled, date, null, List.of(), seen.get(), refused.get());
            List<Cell> cells = new ArrayList<>(day.size());
            for (Held h : day.values()) cells.add(h.cell());
            return new Page(enabled, date, day.lastEntry().getValue().json(), cells, seen.get(), refused.get());
        }
    }

    int cached() {
        synchronized (byDate) {
            int n = 0;
            for (TreeMap<String, Held> d : byDate.values()) n += d.size();
            return n;
        }
    }

    long seen() { return seen.get(); }
    long refused() { return refused.get(); }
    long requests() { return requests.get(); }
}

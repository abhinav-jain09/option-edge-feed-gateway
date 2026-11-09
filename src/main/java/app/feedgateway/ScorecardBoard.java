package app.feedgateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.util.Utf8;

/**
 * The in-memory table behind {@code GET /api/scorecard} (liq-V design section 10): the LATEST record per Kafka key of each scoreboard topic, for the newest
 * {@link #MAX_TRADE_DATES} trade dates. The gateway computes NOTHING here: no sum, no rate, no interval. Every field of a row is the field the emitting service
 * published, copied as it is; the board only keeps the newest record per key, drops tombstones and bounds its memory.
 */
final class ScorecardBoard {
    static final int MAX_TRADE_DATES = 120;
    /** A hard row ceiling on top of the date bound, so a producer writing many classes cannot grow the heap without limit. */
    static final int MAX_ROWS = 200_000;
    static final String LABEL = "DESCRIPTIVE_UNVALIDATED";
    private static final ObjectMapper JSON = new ObjectMapper();

    /** topic to (record key to row). */
    private final Map<String, Map<String, ObjectNode>> rows = new LinkedHashMap<>();
    private final Map<String, Long> undecodable = new LinkedHashMap<>();
    private final Map<String, String> state = new LinkedHashMap<>();   // topic to LOADING, LIVE or TOPIC_MISSING
    private long evicted;

    synchronized void configure(List<String> topics) {
        for (String t : topics) {
            rows.putIfAbsent(t, new LinkedHashMap<>());
            undecodable.putIfAbsent(t, 0L);
            state.putIfAbsent(t, "TOPIC_MISSING");
        }
    }

    synchronized void setState(String topic, String s) {
        state.put(topic, s);
    }

    synchronized void countUndecodable(String topic) {
        undecodable.merge(topic, 1L, Long::sum);
    }

    /** A tombstone removes the key. */
    synchronized void remove(String topic, String key) {
        Map<String, ObjectNode> m = rows.get(topic);
        if (m != null && key != null) {
            m.remove(key);
        }
    }

    /** The newest record of {@code key} replaces the older one. A record without a readable trade date is counted undecodable and never held. */
    synchronized void put(String topic, String key, GenericRecord r) {
        Map<String, ObjectNode> m = rows.get(topic);
        if (m == null || key == null) {
            return;
        }
        ObjectNode row = JSON.createObjectNode();
        for (org.apache.avro.Schema.Field f : r.getSchema().getFields()) {
            row.set(f.name(), node(r.get(f.name())));
        }
        String date = row.path("tradeDate").asText("");
        try {
            LocalDate.parse(date);
        } catch (DateTimeParseException e) {
            countUndecodable(topic);
            return;
        }
        m.put(key, row);
        bound();
    }

    private void bound() {
        TreeSet<String> dates = new TreeSet<>(Comparator.reverseOrder());
        int n = 0;
        for (Map<String, ObjectNode> m : rows.values()) {
            for (ObjectNode row : m.values()) {
                dates.add(row.path("tradeDate").asText());
                n++;
            }
        }
        while (dates.size() > MAX_TRADE_DATES || (n > MAX_ROWS && dates.size() > 1)) {
            String oldest = dates.pollLast();
            for (Map<String, ObjectNode> m : rows.values()) {
                int before = m.size();
                m.values().removeIf(row -> row.path("tradeDate").asText().equals(oldest));
                int gone = before - m.size();
                evicted += gone;
                n -= gone;
            }
        }
    }

    private static com.fasterxml.jackson.databind.JsonNode node(Object v) {
        if (v == null) {
            return JSON.nullNode();
        }
        if (v instanceof Utf8 || v instanceof CharSequence) {
            return JSON.getNodeFactory().textNode(v.toString());
        }
        if (v instanceof Boolean b) {
            return JSON.getNodeFactory().booleanNode(b);
        }
        if (v instanceof Integer i) {
            return JSON.getNodeFactory().numberNode(i);
        }
        if (v instanceof Long l) {
            return JSON.getNodeFactory().numberNode(l);
        }
        if (v instanceof Double d) {
            return JSON.getNodeFactory().numberNode(d);
        }
        if (v instanceof Float d) {
            return JSON.getNodeFactory().numberNode(d);
        }
        if (v instanceof Map<?, ?> map) {
            TreeMap<String, Object> sorted = new TreeMap<>();
            for (Map.Entry<?, ?> e : map.entrySet()) {
                sorted.put(e.getKey().toString(), e.getValue());
            }
            ObjectNode o = JSON.createObjectNode();
            sorted.forEach((k, x) -> o.set(k, node(x)));
            return o;
        }
        if (v instanceof Iterable<?> it) {
            ArrayNode a = JSON.createArrayNode();
            for (Object x : it) {
                a.add(node(x));
            }
            return a;
        }
        return JSON.getNodeFactory().textNode(v.toString());
    }

    synchronized int rowCount() {
        int n = 0;
        for (Map<String, ObjectNode> m : rows.values()) {
            n += m.size();
        }
        return n;
    }

    synchronized long evicted() {
        return evicted;
    }

    /** The response body. {@code fromTradeDate} (yyyy-MM-dd, inclusive) keeps the rows of that date and later; null keeps all held. */
    synchronized String render(String fromTradeDate, boolean readerOff) {
        ObjectNode out = JSON.createObjectNode();
        out.put("label", LABEL);
        ArrayNode topics = out.putArray("generatedFromTopics");
        ArrayNode sources = out.putArray("sources");
        List<String> problems = new ArrayList<>();
        List<ObjectNode> all = new ArrayList<>();
        for (Map.Entry<String, Map<String, ObjectNode>> e : rows.entrySet()) {
            String topic = e.getKey();
            topics.add(topic);
            long bad = undecodable.getOrDefault(topic, 0L);
            int held = e.getValue().size();
            String st = state.getOrDefault(topic, "TOPIC_MISSING");
            String status;
            if (st.equals("TOPIC_MISSING")) {
                status = "TOPIC_MISSING";
                problems.add(topic + ": the scoreboard topic does not exist yet (the service has not rolled)");
            } else if (st.equals("LOADING")) {
                status = "LOADING";
                problems.add(topic + ": still reading the topic from the beginning");
            } else if (bad > 0) {
                status = "UNDECODABLE";
                problems.add(topic + ": " + bad + " record(s) could not be decoded and are not shown");
            } else if (held == 0) {
                status = "NO_DATA";
                problems.add(topic + ": the topic exists and holds no scoreboard row");
            } else {
                status = "OK";
            }
            ObjectNode s = sources.addObject();
            s.put("topic", topic);
            s.put("status", status);
            s.put("rows", held);
            s.put("undecodable", bad);
            all.addAll(e.getValue().values());
        }
        all.removeIf(r -> fromTradeDate != null && r.path("tradeDate").asText().compareTo(fromTradeDate) < 0);
        all.sort(Comparator.<ObjectNode, String>comparing(r -> r.path("tradeDate").asText()).reversed()
                .thenComparing(r -> r.path("identityDigest").asText())
                .thenComparing(r -> r.path("family").asText())
                .thenComparing(r -> r.path("classKey").asText())
                .thenComparing(r -> r.path("stratum").asText())
                .thenComparingInt(r -> r.path("horizonSeconds").asInt()));
        if (readerOff) {
            out.put("message", "the scoreboard reader is not configured on this gateway (KAFKA_SCORECARD_SCOREBOARD_TOPICS is empty); there is no data to show");
        } else if (!problems.isEmpty()) {
            out.put("message", String.join("; ", problems));
        }
        ArrayNode rowsOut = out.putArray("rows");
        all.forEach(rowsOut::add);
        return out.toString();
    }
}

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
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.util.Utf8;

/**
 * The in-memory table behind {@code GET /api/scorecard} (liq-V design section 10): the LATEST record per Kafka key of each scoreboard topic, for the newest
 * {@link #MAX_TRADE_DATES} trade dates. The gateway computes NOTHING here: no sum, no rate, no interval. Every field of a row is the field the emitting service
 * published, copied as it is; the board only keeps the newest record per key, drops tombstones and bounds its memory.
 */
final class ScorecardBoard {
    static final int MAX_TRADE_DATES = 120;
    /** A hard row ceiling on top of the date bound: past it a NEW key is refused and counted (status CAP_REACHED). */
    static final int MAX_ROWS = 200_000;
    static final String LABEL = "DESCRIPTIVE_UNVALIDATED";
    private static final ObjectMapper JSON = new ObjectMapper();

    private final int maxRows;

    ScorecardBoard() {
        this(MAX_ROWS);
    }

    /** Test seam: a smaller row bound. */
    ScorecardBoard(int maxRows) {
        this.maxRows = maxRows;
    }

    /** topic to (record key to row). */
    private final Map<String, Map<String, ObjectNode>> rows = new LinkedHashMap<>();
    private final Map<String, Long> undecodable = new LinkedHashMap<>();
    private final Map<String, String> state = new LinkedHashMap<>();   // topic to LOADING, LIVE or TOPIC_MISSING
    private long evicted;
    private final Map<String, Long> capRefused = new LinkedHashMap<>();
    /** trade date to the number of rows held for it, kept in step with every put, remove and eviction so the bound is O(dates), not O(rows), per record. */
    private final TreeMap<String, Integer> dateCounts = new TreeMap<>();
    private int total;

    synchronized void configure(List<String> topics) {
        for (String t : topics) {
            rows.putIfAbsent(t, new LinkedHashMap<>());
            undecodable.putIfAbsent(t, 0L);
        capRefused.putIfAbsent(t, 0L);
            state.putIfAbsent(t, "TOPIC_MISSING");
        }
    }

    synchronized void setState(String topic, String s) {
        state.put(topic, s);
    }

    /** The topic's epoch is lost (it disappeared, was recreated, or its offsets moved): nothing it held may be shown as current. */
    synchronized void reset(String topic) {
        Map<String, ObjectNode> m = rows.get(topic);
        if (m != null) {
            for (ObjectNode row : m.values()) {
                forget(row);
            }
            m.clear();
        }
        undecodable.put(topic, 0L);
        capRefused.put(topic, 0L);
    }

    synchronized void countUndecodable(String topic) {
        undecodable.merge(topic, 1L, Long::sum);
    }

    /** A tombstone removes the key. */
    synchronized void remove(String topic, String key) {
        Map<String, ObjectNode> m = rows.get(topic);
        if (m != null && key != null) {
            forget(m.remove(key));
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
        if (total >= maxRows && !m.containsKey(key)) {
            capRefused.merge(topic, 1L, Long::sum);   // a hard bound: the new key is refused and the source says so, never silently truncated
            return;
        }
        forget(m.put(key, row));
        dateCounts.merge(date, 1, Integer::sum);
        total++;
        bound();
    }

    private void forget(ObjectNode old) {
        if (old != null) {
            dateCounts.computeIfPresent(old.path("tradeDate").asText(), (d, n) -> n <= 1 ? null : n - 1);
            total--;
        }
    }

    private void bound() {
        while (dateCounts.size() > MAX_TRADE_DATES) {
            String oldest = dateCounts.firstKey();
            for (Map<String, ObjectNode> m : rows.values()) {
                int before = m.size();
                m.values().removeIf(row -> row.path("tradeDate").asText().equals(oldest));
                evicted += before - m.size();
            }
            total -= dateCounts.remove(oldest);
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
        return total;
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
            long capped = capRefused.getOrDefault(topic, 0L);
            String status;
            if (st.equals("TOPIC_MISSING")) {
                status = "TOPIC_MISSING";
                problems.add(topic + ": the scoreboard topic does not exist yet (the service has not rolled)");
            } else {
                boolean loading = st.equals("LOADING");
                if (loading) {
                    problems.add(topic + ": still reading the topic from the beginning");
                }
                if (bad > 0) {
                    problems.add(topic + ": " + bad + " record(s) could not be decoded and are not shown");
                }
                if (capped > 0) {
                    problems.add(topic + ": " + capped + " new key(s) were refused at the " + maxRows + "-row bound and are not shown");
                }
                if (capped > 0) {
                    status = "CAP_REACHED";
                } else if (loading) {
                    status = bad > 0 ? "LOADING_UNDECODABLE" : "LOADING";
                } else if (bad > 0) {
                    status = "UNDECODABLE";
                } else if (held == 0) {
                    status = "NO_DATA";
                    problems.add(topic + ": the topic exists and holds no scoreboard row");
                } else {
                    status = "OK";
                }
            }
            ObjectNode s = sources.addObject();
            s.put("topic", topic);
            s.put("status", status);
            s.put("rows", held);
            s.put("undecodable", bad);
            s.put("capRefused", capped);
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
            ObjectNode off = sources.addObject();
            off.put("topic", "scorecard-reader");
            off.put("status", "NOT_CONFIGURED");
            off.put("rows", 0);
            off.put("undecodable", 0);
            off.put("capRefused", 0);
            out.put("message", "the scoreboard reader is not configured on this gateway (KAFKA_SCORECARD_SCOREBOARD_TOPICS is empty); there is no data to show");
        } else if (!problems.isEmpty()) {
            out.put("message", String.join("; ", problems));
        }
        ArrayNode rowsOut = out.putArray("rows");
        all.forEach(rowsOut::add);
        return out.toString();
    }
}

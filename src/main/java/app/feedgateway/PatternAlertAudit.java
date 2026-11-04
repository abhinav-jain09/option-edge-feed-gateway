package app.feedgateway;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiFunction;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import org.apache.avro.generic.GenericRecord;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.consumer.OffsetAndTimestamp;
import org.apache.kafka.common.PartitionInfo;
import org.apache.kafka.common.TopicPartition;

/**
 * The audit read of the server-side pattern break alerts (design section 6.4, AL-20): what the service raised and what it did not, and why, for any retained date, from the durable
 * alert topic itself. {@code GET /api/pattern-alerts?date=YYYY-MM-DD[&profile=][&limit=][&after=]} answers one session; {@code GET /api/pattern-alerts?days=N[&profile=][&limit=]} answers the
 * newest N session dates in the topic, newest first, each event with a server-written {@code statusText} (the page composes nothing). Every call opens a short-lived
 * {@code read_committed} consumer on the profile's topic, one at a time (a semaphore: a second concurrent call is {@code busy}), reads to the end offset captured first and gives up after a
 * wall-time bound. An empty event list is never returned without the per-timeframe coverage the watermarks prove, or an explicit status saying there is none.
 */
final class PatternAlertAudit {
    static final int DATE_LIMIT_DEFAULT = 500;
    static final int DATE_LIMIT_MAX = 2_000;
    static final int DAYS_LIMIT_DEFAULT = 200;
    static final int DAYS_LIMIT_MAX = 500;
    static final int DAYS_MAX = 7;
    /** A read that sees more records than this is refused (a week of the topic is about ten thousand). */
    static final int RECORD_BOUND = 100_000;
    /** A rendered event above this is rejected and counted, never retained (the relay refuses above 4 KiB; the audit is more lenient so it can show what the relay dropped). */
    static final int EVENT_MAX_CHARS = 16_384;
    /** The rendered events one read may retain; beyond it the read is refused as unreadable rather than risking the heap of the gateway. */
    static final long RETAINED_MAX_BYTES = 32L * 1024 * 1024;

    private static final String EVENT_SCHEMA = "com.optionsedge.contracts.marketstructure.alerts.PatternAlertEvent";
    private static final String WATERMARK_SCHEMA = "com.optionsedge.contracts.marketstructure.alerts.PatternAlertWatermark";
    private static final Pattern DATE = Pattern.compile("\\d{4}-\\d{2}-\\d{2}");
    private static final ZoneId EASTERN = ZoneId.of("America/New_York");
    private static final String[] TF_ENUM = {"TF_1M", "TF_5M", "TF_15M"};
    private static final String[] DISPOSITIONS = {"RAISED", "SUPPRESSED_SELF_CHECK", "SUPPRESSED_REPLAY", "SUPPRESSED_STALE"};
    private static final String[] KINDS = {"BREAK", "RETURN", "FAILED"};
    private static final String SERVICE = "market-structure";
    private static final ObjectMapper JSON = new ObjectMapper();

    /** The closed table of the page's wording for a disposition. */
    static String statusText(String disposition) {
        return switch (disposition) {
            case "RAISED" -> "Raised";
            case "SUPPRESSED_STALE" -> "Not alerted: service was catching up";
            case "SUPPRESSED_REPLAY" -> "Not alerted: service restarted";
            case "SUPPRESSED_SELF_CHECK" -> "Not alerted: record contradicted itself";
            default -> "Not alerted";
        };
    }

    record Response(int status, String body) {
    }

    private record Ev(String date, long seq, String disposition, String tf, String json) {
    }

    private record Wm(String date, int tf, int decision, int previous, long asOfMs, boolean evaluated) {
    }

    private static final class Scan {
        final List<Ev> events = new ArrayList<>();
        final List<Wm> watermarks = new ArrayList<>();
        long undecodable;   // not an Avro record of the registry, or of another subject
        long invalid;       // an alert record that contradicts the design's record shape (version, service, date, sequence, vocabulary)
        long oversize;      // an event whose rendered form exceeds EVENT_MAX_CHARS
        long retainedBytes;
        Long earliestTimestamp;
        String error;   // non-null: the read failed, nothing in it is to be trusted
    }

    private final GatewaySettings settings;
    private final Supplier<Consumer<String, byte[]>> consumers;
    private final BiFunction<String, byte[], Object> decoder;
    private final LongSupplier clock;
    private final Semaphore one = new Semaphore(1);
    private long retainedMax = RETAINED_MAX_BYTES;
    private final AtomicLong requests = new AtomicLong();
    private final AtomicLong busy = new AtomicLong();

    /** Production: a plain byte consumer and the registry-backed Avro decoder. */
    PatternAlertAudit(GatewaySettings settings) {
        this(settings, () -> new KafkaConsumer<>(consumerProperties(settings)), registryDecoder(settings), System::currentTimeMillis);
    }

    PatternAlertAudit(GatewaySettings settings, Supplier<Consumer<String, byte[]>> consumers, BiFunction<String, byte[], Object> decoder, LongSupplier clock) {
        this.settings = settings;
        this.consumers = consumers;
        this.decoder = decoder;
        this.clock = clock;
    }

    /** Test seam: a smaller byte budget for the events one read may hold. */
    PatternAlertAudit withRetainedMax(long bytes) {
        this.retainedMax = bytes;
        return this;
    }

    static Properties consumerProperties(GatewaySettings settings) {
        Properties p = new Properties();
        p.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, settings.bootstrapServers());
        p.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, org.apache.kafka.common.serialization.StringDeserializer.class.getName());
        p.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, org.apache.kafka.common.serialization.ByteArrayDeserializer.class.getName());
        p.put(ConsumerConfig.ISOLATION_LEVEL_CONFIG, "read_committed");
        p.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
        p.put(ConsumerConfig.ALLOW_AUTO_CREATE_TOPICS_CONFIG, "false");   // a reader never creates a topic
        p.put(ConsumerConfig.CLIENT_ID_CONFIG, settings.groupIdBase() + "-pattern-alert-audit");
        settings.applyKafkaSecurity(p);   // TLS/SASL when configured, exactly as for the gateway's other consumers
        return p;
    }

    private static BiFunction<String, byte[], Object> registryDecoder(GatewaySettings settings) {
        io.confluent.kafka.serializers.KafkaAvroDeserializer d = new io.confluent.kafka.serializers.KafkaAvroDeserializer();
        Map<String, Object> config = new HashMap<>();
        config.put("schema.registry.url", settings.schemaRegistryUrl());
        config.put("specific.avro.reader", "false");
        config.put("value.subject.name.strategy", "io.confluent.kafka.serializers.subject.RecordNameStrategy");
        d.configure(config, false);
        return d::deserialize;
    }

    // ── requests ────────────────────────────────────────────────────────────────────────────────────

    Response byDate(String profile, String date, String limit, String after) {
        int p = profileOf(profile);
        if (p < 0) {
            return bad("profile");
        }
        if (date == null || !DATE.matcher(date).matches() || !validDate(date)) {
            return bad("date");
        }
        Integer lim = limit(limit, DATE_LIMIT_DEFAULT, DATE_LIMIT_MAX);
        if (lim == null) {
            return bad("limit");
        }
        long afterSeq = 0;
        if (after != null) {
            try {
                afterSeq = Long.parseLong(after);
            } catch (NumberFormatException e) {
                return bad("after");
            }
            if (afterSeq < 0) {
                return bad("after");
            }
        }
        if (!one.tryAcquire()) {
            busy.incrementAndGet();
            return new Response(503, "{\"error\":\"busy\"}");
        }
        try {
            requests.incrementAndGet();
            long startMs = LocalDate.parse(date).atStartOfDay(EASTERN).toInstant().toEpochMilli() - 86_400_000L;
            long endMs = LocalDate.parse(date).plusDays(1).atStartOfDay(EASTERN).toInstant().toEpochMilli();
            Scan scan = scan(topic(p), startMs);
            if (scan.error == null && scan.events.isEmpty() && scan.watermarks.isEmpty() && scan.undecodable + scan.invalid + scan.oversize > 0) {
                scan.error = "no usable record: " + (scan.undecodable + scan.invalid + scan.oversize) + " records of the topic were rejected";
            }
            if (scan.error != null) {
                return unreadable(p, null, scan.error);
            }
            List<Ev> events = new ArrayList<>();
            List<Wm> wms = new ArrayList<>();
            for (Ev e : scan.events) {
                if (e.date().equals(date)) {
                    events.add(e);
                }
            }
            for (Wm w : scan.watermarks) {
                if (w.date().equals(date)) {
                    wms.add(w);
                }
            }
            events.sort(Comparator.comparingLong(Ev::seq));
            String status = !events.isEmpty() || !wms.isEmpty() ? "OK" : outsideRetention(scan, endMs) ? "OUTSIDE_RETENTION" : "EMPTY";
            List<Ev> after1 = new ArrayList<>();
            for (Ev e : events) {
                if (e.seq() > afterSeq) {
                    after1.add(e);
                }
            }
            boolean truncated = after1.size() > lim;
            List<Ev> page = truncated ? after1.subList(0, lim) : after1;
            StringBuilder sb = new StringBuilder("{\"date\":").append(quote(date)).append(",\"profile\":").append(quote(PatternAlertRelay.PROFILE[p]))
                    .append(',').append(retention(p, scan)).append(",\"status\":\"").append(status).append("\",\"rejected\":").append(rejected(scan)).append(",\"events\":[");
            for (int i = 0; i < page.size(); i++) {
                sb.append(i == 0 ? "" : ",").append(eventJson(page.get(i), false));
            }
            sb.append("],\"truncated\":").append(truncated).append(",\"nextAfter\":").append(truncated ? String.valueOf(page.get(page.size() - 1).seq()) : "null")
                    .append(",\"coverage\":").append(coverage(wms)).append(",\"counts\":").append(counts(events));
            if (page.isEmpty()) {
                sb.append(",\"note\":").append(quote("OK".equals(status) ? "no event in this answer; the coverage above shows what the evaluator checked"
                        : "EMPTY".equals(status) ? "no record of this date on the topic: nothing proves the evaluator ran" : "this date is older than the topic's retention"));
            }
            return new Response(200, sb.append('}').toString());
        } finally {
            one.release();
        }
    }

    Response byDays(String profile, String days, String limit) {
        int p = profileOf(profile);
        if (p < 0) {
            return bad("profile");
        }
        int n;
        try {
            n = Integer.parseInt(days == null ? "" : days);
        } catch (NumberFormatException e) {
            return bad("days");
        }
        if (n < 1 || n > DAYS_MAX) {
            return bad("days");
        }
        Integer lim = limit(limit, DAYS_LIMIT_DEFAULT, DAYS_LIMIT_MAX);
        if (lim == null) {
            return bad("limit");
        }
        if (!one.tryAcquire()) {
            busy.incrementAndGet();
            return new Response(503, "{\"error\":\"busy\"}");
        }
        try {
            requests.incrementAndGet();
            Scan scan = scan(topic(p), -1L);   // the topic holds a week: read it whole, the newest N session dates are chosen from what is there
            if (scan.error == null && scan.events.isEmpty() && scan.watermarks.isEmpty() && scan.undecodable + scan.invalid + scan.oversize > 0) {
                scan.error = "no usable record: " + (scan.undecodable + scan.invalid + scan.oversize) + " records of the topic were rejected";
            }
            if (scan.error != null) {
                return unreadable(p, n, scan.error);
            }
            TreeSet<String> dates = new TreeSet<>(Comparator.reverseOrder());
            scan.events.forEach(e -> dates.add(e.date()));
            scan.watermarks.forEach(w -> dates.add(w.date()));
            List<String> chosen = new ArrayList<>(dates).subList(0, Math.min(n, dates.size()));
            List<Ev> events = new ArrayList<>();
            for (Ev e : scan.events) {
                if (chosen.contains(e.date())) {
                    events.add(e);
                }
            }
            events.sort(Comparator.comparing(Ev::date).thenComparingLong(Ev::seq).reversed());   // newest first
            boolean truncated = events.size() > lim;
            List<Ev> page = truncated ? events.subList(0, lim) : events;
            StringBuilder sb = new StringBuilder("{\"days\":").append(n).append(",\"profile\":").append(quote(PatternAlertRelay.PROFILE[p]))
                    .append(',').append(retention(p, scan)).append(",\"status\":\"").append(chosen.isEmpty() ? "EMPTY" : "OK").append("\",\"rejected\":").append(rejected(scan)).append(",\"events\":[");
            for (int i = 0; i < page.size(); i++) {
                sb.append(i == 0 ? "" : ",").append(eventJson(page.get(i), true));
            }
            sb.append("],\"truncated\":").append(truncated).append(",\"coverage\":{");
            Map<String, String> counts = new LinkedHashMap<>();
            for (int i = 0; i < chosen.size(); i++) {
                String d = chosen.get(i);
                List<Wm> wms = new ArrayList<>();
                scan.watermarks.stream().filter(w -> w.date().equals(d)).forEach(wms::add);
                sb.append(i == 0 ? "" : ",").append(quote(d)).append(':').append(coverage(wms));
                counts.put(d, counts(events.stream().filter(e -> e.date().equals(d)).toList()));
            }
            sb.append("},\"counts\":{");
            int i = 0;
            for (Map.Entry<String, String> c : counts.entrySet()) {
                sb.append(i++ == 0 ? "" : ",").append(quote(c.getKey())).append(':').append(c.getValue());
            }
            return new Response(200, sb.append("}}").toString());
        } finally {
            one.release();
        }
    }

    // ── the read ────────────────────────────────────────────────────────────────────────────────────

    private String topic(int p) {
        return p == 0 ? settings.patternAlertsTopic() : settings.patternAlertsEstimateTopic();
    }

    /** One bounded read of the topic to the end offsets captured first; {@code startMs < 0} reads from the beginning, else from the first record at or after it (a volume optimisation only). */
    private Scan scan(String topic, long startMs) {
        Scan scan = new Scan();
        long deadline = clock.getAsLong() + settings.patternAlertsAuditTimeoutMs();
        try (Consumer<String, byte[]> consumer = consumers.get()) {
            List<PartitionInfo> infos = consumer.partitionsFor(topic, Duration.ofSeconds(5));
            if (infos == null || infos.isEmpty()) {
                scan.error = "the topic " + topic + " does not exist";
                return scan;
            }
            List<TopicPartition> partitions = new ArrayList<>();
            for (PartitionInfo info : infos) {
                partitions.add(new TopicPartition(topic, info.partition()));
            }
            consumer.assign(partitions);
            Map<TopicPartition, Long> begin = consumer.beginningOffsets(partitions);
            Map<TopicPartition, Long> end = consumer.endOffsets(partitions);
            for (Map.Entry<TopicPartition, OffsetAndTimestamp> e : consumer.offsetsForTimes(zeros(partitions)).entrySet()) {
                if (e.getValue() != null) {
                    scan.earliestTimestamp = scan.earliestTimestamp == null ? e.getValue().timestamp() : Math.min(scan.earliestTimestamp, e.getValue().timestamp());
                }
            }
            Map<TopicPartition, OffsetAndTimestamp> from = startMs < 0 ? Map.of() : consumer.offsetsForTimes(timestamps(partitions, startMs));
            for (TopicPartition tp : partitions) {
                OffsetAndTimestamp at = from.get(tp);
                consumer.seek(tp, startMs < 0 ? begin.get(tp) : at == null ? end.get(tp) : Math.max(begin.get(tp), at.offset()));
            }
            long seen = 0;
            while (!reached(consumer, partitions, end)) {
                if (clock.getAsLong() > deadline) {
                    scan.error = "the read did not reach the end offsets within " + settings.patternAlertsAuditTimeoutMs() + " ms";
                    return scan;
                }
                for (ConsumerRecord<String, byte[]> record : consumer.poll(Duration.ofMillis(200))) {
                    if (++seen > RECORD_BOUND) {
                        scan.error = "the topic holds more than " + RECORD_BOUND + " records to read";
                        return scan;
                    }
                    classify(scan, record);
                    if (scan.retainedBytes > retainedMax) {
                        scan.error = "the events of the topic exceed the " + retainedMax + " bytes one read may hold";
                        return scan;
                    }
                }
            }
        } catch (RuntimeException e) {
            scan.error = "the topic could not be read: " + e.getClass().getSimpleName();
        }
        return scan;
    }

    private static Map<TopicPartition, Long> zeros(List<TopicPartition> partitions) {
        return timestamps(partitions, 0L);
    }

    private static Map<TopicPartition, Long> timestamps(List<TopicPartition> partitions, long ts) {
        Map<TopicPartition, Long> m = new HashMap<>();
        partitions.forEach(tp -> m.put(tp, ts));
        return m;
    }

    private static boolean reached(Consumer<String, byte[]> consumer, List<TopicPartition> partitions, Map<TopicPartition, Long> end) {
        for (TopicPartition tp : partitions) {
            if (consumer.position(tp) < end.get(tp)) {
                return false;
            }
        }
        return true;
    }

    private void classify(Scan scan, ConsumerRecord<String, byte[]> record) {
        Object value;
        try {
            value = record.value() == null ? null : decoder.apply(record.topic(), record.value());
        } catch (RuntimeException e) {
            value = null;
        }
        try {
            if (!(value instanceof GenericRecord r)) {
                scan.undecodable++;
                return;
            }
            String schema = r.getSchema().getFullName();
            if (EVENT_SCHEMA.equals(schema)) {
                event(scan, r);
            } else if (WATERMARK_SCHEMA.equals(schema)) {
                watermark(scan, r);
            } else {
                scan.undecodable++;
            }
        } catch (RuntimeException e) {
            scan.invalid++;   // a missing or mistyped field: the record contradicts the design's shape
        }
    }

    private static void event(Scan scan, GenericRecord r) {
        String date = String.valueOf(r.get("sessionDate"));
        long seq = (Long) r.get("eventSeq");
        String disposition = String.valueOf(r.get("disposition"));
        int tf = PatternAlertRelay.indexOf(TF_ENUM, String.valueOf(r.get("timeframe")));
        if (!Integer.valueOf(1).equals(r.get("schemaVersion")) || !SERVICE.equals(String.valueOf(r.get("service"))) || !DATE.matcher(date).matches() || seq <= 0 || tf < 0
                || PatternAlertRelay.indexOf(KINDS, String.valueOf(r.get("kind"))) < 0 || "UNKNOWN".equals(disposition)) {
            scan.invalid++;
            return;
        }
        String json = AvroFrameJson.render(r, EVENT_MAX_CHARS, false);
        if (json == null) {
            scan.oversize++;   // too large to hold (or not renderable): counted, never retained
            return;
        }
        scan.retainedBytes += StudiesRelay.utf8Length(json);
        scan.events.add(new Ev(date, seq, disposition, TaRelay.TF[tf], json));
    }

    private static void watermark(Scan scan, GenericRecord r) {
        String date = String.valueOf(r.get("sessionDate"));
        int tf = PatternAlertRelay.indexOf(TF_ENUM, String.valueOf(r.get("timeframe")));
        int decision = (Integer) r.get("decisionOrdinal");
        int previous = (Integer) r.get("previousDecisionOrdinal");
        if (!Integer.valueOf(1).equals(r.get("schemaVersion")) || !SERVICE.equals(String.valueOf(r.get("service"))) || !DATE.matcher(date).matches() || tf < 0
                || decision < 0 || previous < -1 || previous >= decision) {
            scan.invalid++;
            return;
        }
        scan.watermarks.add(new Wm(date, tf, decision, previous, (Long) r.get("asOfMs"), (Boolean) r.get("evaluated")));
    }

    // ── rendering ───────────────────────────────────────────────────────────────────────────────────

    private static String eventJson(Ev e, boolean withStatus) {
        return e.json().substring(0, e.json().length() - 1) + ",\"tf\":\"" + e.tf() + "\"" + (withStatus ? ",\"statusText\":" + quote(statusText(e.disposition())) : "") + "}";
    }

    /** What the read refused, so a response is never an apparently trustworthy answer over records it silently skipped. */
    private static String rejected(Scan scan) {
        return "{\"undecodable\":" + scan.undecodable + ",\"invalid\":" + scan.invalid + ",\"oversize\":" + scan.oversize + "}";
    }

    private String retention(int p, Scan scan) {
        return "\"retention\":{\"topic\":" + quote(topic(p)) + ",\"retentionMs\":" + settings.patternAlertsRetentionMs() + ",\"earliestOffsetTimestamp\":"
                + (scan.earliestTimestamp == null ? "null" : scan.earliestTimestamp.toString()) + "}";
    }

    /** The date ends before the topic's retention reaches back, or before its earliest surviving record: it was lost, not quiet. */
    private boolean outsideRetention(Scan scan, long dateEndMs) {
        return dateEndMs < clock.getAsLong() - settings.patternAlertsRetentionMs() || scan.earliestTimestamp != null && scan.earliestTimestamp > dateEndMs;
    }

    /** Per timeframe: the first and last evaluated clock, the folds seen, the unevaluated ones and the holes of the previousDecisionOrdinal chain. Always all three timeframes. */
    private static String coverage(List<Wm> watermarks) {
        StringBuilder sb = new StringBuilder("{");
        for (int t = 0; t < TaRelay.TF.length; t++) {
            int last = -1;
            long folds = 0;
            long unevaluated = 0;
            Long firstAsOf = null;
            Long lastAsOf = null;
            StringBuilder holes = new StringBuilder("[");
            boolean anyHole = false;
            for (Wm w : watermarks) {
                if (w.tf() != t || w.decision() <= last) {
                    continue;   // another timeframe, or a record already counted (a duplicate or a rewind)
                }
                if (w.previous() > last) {
                    holes.append(anyHole ? "," : "").append("{\"fromOrdinal\":").append(last + 1).append(",\"toOrdinal\":").append(w.previous()).append('}');
                    anyHole = true;
                }
                last = w.decision();
                folds++;
                if (w.evaluated()) {
                    firstAsOf = firstAsOf == null ? w.asOfMs() : Math.min(firstAsOf, w.asOfMs());
                    lastAsOf = lastAsOf == null ? w.asOfMs() : Math.max(lastAsOf, w.asOfMs());
                } else {
                    unevaluated++;
                }
            }
            sb.append(t == 0 ? "" : ",").append('"').append(TaRelay.TF[t]).append("\":{\"evaluatedFirstAsOfMs\":").append(firstAsOf).append(",\"evaluatedLastAsOfMs\":").append(lastAsOf)
                    .append(",\"folds\":").append(folds).append(",\"unevaluated\":").append(unevaluated).append(",\"holes\":").append(holes).append("]}");
        }
        return sb.append('}').toString();
    }

    private static String counts(List<Ev> events) {
        Map<String, Integer> n = new TreeMap<>();
        for (String d : DISPOSITIONS) {
            n.put(d, 0);
        }
        events.forEach(e -> n.merge(e.disposition(), 1, Integer::sum));
        StringBuilder sb = new StringBuilder("{");
        int known = 0;
        int i = 0;
        for (String d : DISPOSITIONS) {
            sb.append(i++ == 0 ? "" : ",").append('"').append(d).append("\":").append(n.get(d));
            known += n.get(d);
        }
        return sb.append(",\"OTHER\":").append(events.size() - known).append('}').toString();   // a later additive disposition is counted, never lost
    }

    private Response unreadable(int p, Integer days, String error) {
        return new Response(503, "{" + (days == null ? "" : "\"days\":" + days + ",") + "\"profile\":" + quote(PatternAlertRelay.PROFILE[p]) + ",\"status\":\"UNREADABLE\",\"error\":" + quote(error)
                + ",\"events\":[],\"truncated\":false,\"coverage\":{}}");
    }

    // ── validation ──────────────────────────────────────────────────────────────────────────────────

    private static int profileOf(String profile) {
        return profile == null || profile.isEmpty() ? 0 : PatternAlertRelay.profileIndex(profile);
    }

    private static boolean validDate(String date) {
        try {
            LocalDate.parse(date);
            return true;
        } catch (java.time.format.DateTimeParseException e) {
            return false;
        }
    }

    /** A missing limit is the default; a non-numeric, non-positive or too large one is refused (never clamped silently). */
    private static Integer limit(String limit, int fallback, int max) {
        if (limit == null) {
            return fallback;
        }
        try {
            int n = Integer.parseInt(limit);
            return n >= 1 && n <= max ? n : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Response bad(String field) {
        return new Response(400, "{\"error\":\"" + field + "\"}");
    }

    private static String quote(String s) {
        try {
            return JSON.writeValueAsString(s);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    String metrics() {
        String p = "gateway_pattern_alerts_";
        return "# HELP " + p + "audit_requests_total Audit reads that reached the topic.\n# TYPE " + p + "audit_requests_total counter\n" + p + "audit_requests_total " + requests.get() + "\n"
                + "# HELP " + p + "audit_busy_total Audit reads refused because another read was running.\n# TYPE " + p + "audit_busy_total counter\n" + p + "audit_busy_total " + busy.get() + "\n";
    }
}

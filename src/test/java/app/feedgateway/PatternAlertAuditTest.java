package app.feedgateway;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.confluent.kafka.serializers.KafkaAvroDeserializer;
import io.confluent.kafka.serializers.KafkaAvroSerializer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.avro.generic.GenericRecord;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.MockConsumer;
import org.apache.kafka.clients.consumer.OffsetAndTimestamp;
import org.apache.kafka.clients.consumer.OffsetResetStrategy;
import org.apache.kafka.common.PartitionInfo;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.Test;

/** The audit read (design 6.4, AL-20) against a {@link MockConsumer} holding Confluent-framed records of the contracts schemas, decoded by the real Avro deserializer through a mock registry. */
class PatternAlertAuditTest {
    static final ObjectMapper JSON = new ObjectMapper();
    static final String TOPIC = "options.spx.market-structure.pattern-alerts";
    static final String ESTIMATE_TOPIC = "options.spx.market-structure.estimate.pattern-alerts";
    static final String DATE = PatternAlertFixtures.DATE;
    static final AtomicInteger SCOPE = new AtomicInteger();

    /** A consumer whose log is a list of (record, timestamp); {@code offsetsForTimes} answers from it (the MockConsumer does not implement it). */
    static final class Log extends MockConsumer<String, byte[]> {
        final TopicPartition tp;
        final List<Long> timestamps = new ArrayList<>();
        final List<ConsumerRecord<String, byte[]>> pending = new ArrayList<>();

        static final AtomicInteger POLLS = new AtomicInteger();

        @Override
        public synchronized org.apache.kafka.clients.consumer.ConsumerRecords<String, byte[]> poll(java.time.Duration timeout) {
            POLLS.incrementAndGet();
            return super.poll(timeout);
        }

        Log(String topic) {
            super(OffsetResetStrategy.EARLIEST);
            tp = new TopicPartition(topic, 0);
            updatePartitions(topic, List.of(new PartitionInfo(topic, 0, null, null, null)));
        }

        void append(byte[] value, long timestamp) {
            pending.add(new ConsumerRecord<>(tp.topic(), 0, timestamps.size(), timestamp, org.apache.kafka.common.record.TimestampType.CREATE_TIME, 0, value == null ? 0 : value.length, "k", value,
                    new org.apache.kafka.common.header.internals.RecordHeaders(), java.util.Optional.empty()));
            timestamps.add(timestamp);
        }

        @Override
        public synchronized void assign(java.util.Collection<TopicPartition> partitions) {
            super.assign(partitions);
            pending.forEach(this::addRecord);   // a MockConsumer takes records only for assigned partitions
        }

        void seal() {
            updateBeginningOffsets(Map.of(tp, 0L));
            updateEndOffsets(Map.of(tp, (long) timestamps.size()));
        }

        @Override
        public Map<TopicPartition, OffsetAndTimestamp> offsetsForTimes(Map<TopicPartition, Long> search) {
            Map<TopicPartition, OffsetAndTimestamp> out = new HashMap<>();
            for (Map.Entry<TopicPartition, Long> e : search.entrySet()) {
                OffsetAndTimestamp found = null;
                for (int i = 0; i < timestamps.size() && found == null; i++) {
                    if (timestamps.get(i) >= e.getValue()) {
                        found = new OffsetAndTimestamp(i, timestamps.get(i));
                    }
                }
                out.put(e.getKey(), found);
            }
            return out;
        }
    }

    /** One scenario: a topic of records and an audit over it. */
    final class Rig {
        final String scope = "audit-" + SCOPE.incrementAndGet();
        final KafkaAvroSerializer serializer = new KafkaAvroSerializer();
        final KafkaAvroDeserializer deserializer = new KafkaAvroDeserializer();
        final String topic;
        final List<Object[]> records = new ArrayList<>();
        boolean noPartitions;
        long phantomEnd;   // records the log claims to hold beyond what it can deliver
        final AtomicLong clock = new AtomicLong(PatternAlertFixtures.barEnd(300));
        final List<String> consumersOpenedOn = new ArrayList<>();
        PatternAlertAudit audit;

        Rig(String topic) {
            this.topic = topic;
            Map<String, Object> config = new HashMap<>();
            config.put("schema.registry.url", "mock://" + scope);
            config.put("auto.register.schemas", true);
            config.put("value.subject.name.strategy", "io.confluent.kafka.serializers.subject.RecordNameStrategy");
            serializer.configure(config, false);
            Map<String, Object> reader = new HashMap<>(config);
            reader.put("specific.avro.reader", "false");
            deserializer.configure(reader, false);
        }

        Rig put(GenericRecord r) {
            long ts = (Long) r.get("asOfMs");
            records.add(new Object[] {serializer.serialize(topic, r), ts});
            return this;
        }

        Rig putRaw(byte[] v, long ts) {
            records.add(new Object[] {v, ts});
            return this;
        }

        /** A fresh consumer per read, as in production (a consumer is closed after its read). */
        Log newLog() {
            Log log = new Log(topic);
            if (noPartitions) {
                log.updatePartitions(topic, List.of());
            }
            records.forEach(rec -> log.append((byte[]) rec[0], (Long) rec[1]));
            for (long i = 0; i < phantomEnd; i++) {
                log.timestamps.add(1L);
            }
            log.seal();
            return log;
        }

        PatternAlertAudit audit() {
            GatewaySettings settings = spy(new GatewaySettings());
            doReturn(5_000L).when(settings).patternAlertsAuditTimeoutMs();
            audit = new PatternAlertAudit(settings, () -> {
                consumersOpenedOn.add(topic);
                return newLog();
            }, (topic, bytes) -> deserializer.deserialize(topic, bytes), () -> clock.addAndGet(1));
            return audit;
        }

        /** Every decision fold 0..n-1 of the three timeframes evaluated, in the producer's order (the 1m, 5m, 15m watermarks of a fold together). */
        Rig watermarks(String date, int n, String... skipNone) {
            for (int d = 0; d < n; d++) {
                for (String tf : new String[] {"TF_1M", "TF_5M", "TF_15M"}) {
                    put(PatternAlertFixtures.watermark(date, tf, d, d - 1, true, 0));
                }
            }
            return this;
        }
    }

    static JsonNode json(PatternAlertAudit.Response r) {
        try {
            return JSON.readTree(r.body());
        } catch (Exception e) {
            throw new IllegalStateException(r.body(), e);
        }
    }

    Rig threeEvents() {
        Rig rig = new Rig(TOPIC);
        rig.put(PatternAlertFixtures.triangleBreak(1, "RAISED"));
        rig.watermarks(DATE, 3);
        rig.put(PatternAlertFixtures.wedgeBreak(2, "SUPPRESSED_STALE"));
        rig.put(PatternAlertFixtures.wedgeReturn(3, "RAISED"));
        return rig;
    }

    @Test
    void aDateAnswersEveryEventOfThatSessionWithItsDispositionAndTheCoverageTheWatermarksProve() {
        Rig rig = threeEvents();
        rig.put(PatternAlertFixtures.numbered("2026-11-02", 1, "RAISED", 40));   // another session: never in this answer
        JsonNode r = json(rig.audit().byDate("regular", DATE, null, null));
        assertEquals("OK", r.get("status").asText());
        assertEquals(DATE, r.get("date").asText());
        assertEquals("regular", r.get("profile").asText());
        assertEquals(TOPIC, r.get("retention").get("topic").asText());
        assertEquals(604_800_000L, r.get("retention").get("retentionMs").asLong());
        assertTrue(r.get("retention").get("earliestOffsetTimestamp").isNumber());
        assertEquals(3, r.get("events").size());
        assertEquals(List.of(1L, 2L, 3L), List.of(r.get("events").get(0).get("eventSeq").asLong(), r.get("events").get(1).get("eventSeq").asLong(), r.get("events").get(2).get("eventSeq").asLong()));
        assertEquals("SUPPRESSED_STALE", r.get("events").get(1).get("disposition").asText(), "every disposition is in the audit");
        assertEquals("1m", r.get("events").get(0).get("tf").asText());
        assertEquals("Bullish symmetrical triangle, one minute, broke up at 7,578.21", r.get("events").get(0).get("speech").asText());
        assertFalse(r.get("events").get(0).has("statusText"), "the date form is the raw record; statusText is for the past-events form");
        assertEquals(2, r.get("counts").get("RAISED").asInt());
        assertEquals(1, r.get("counts").get("SUPPRESSED_STALE").asInt());
        assertEquals(0, r.get("counts").get("SUPPRESSED_REPLAY").asInt());
        for (String tf : List.of("1m", "5m", "15m")) {
            JsonNode c = r.get("coverage").get(tf);
            assertEquals(3, c.get("folds").asInt(), tf);
            assertEquals(0, c.get("unevaluated").asInt());
            assertEquals(PatternAlertFixtures.barEnd(0), c.get("evaluatedFirstAsOfMs").asLong());
            assertEquals(PatternAlertFixtures.barEnd(2), c.get("evaluatedLastAsOfMs").asLong());
            assertEquals(0, c.get("holes").size());
        }
        assertFalse(r.get("truncated").asBoolean());
        assertTrue(r.get("nextAfter").isNull());
        assertEquals(List.of(TOPIC), rig.consumersOpenedOn, "a short-lived consumer on the regular profile's topic");
    }

    @Test
    void theEstimateProfileReadsItsOwnTopic() {
        Rig rig = new Rig(ESTIMATE_TOPIC);
        rig.put(PatternAlertFixtures.triangleBreak(1, "RAISED"));
        JsonNode r = json(rig.audit().byDate("estimate", DATE, null, null));
        assertEquals("estimate", r.get("profile").asText());
        assertEquals(ESTIMATE_TOPIC, r.get("retention").get("topic").asText());
        assertEquals(List.of(ESTIMATE_TOPIC), rig.consumersOpenedOn);
    }

    @Test
    void aHoleInThePreviousDecisionChainIsReportedAndAnUnevaluatedFoldIsCounted() {
        Rig rig = new Rig(TOPIC);
        rig.put(PatternAlertFixtures.watermark(DATE, "TF_1M", 0, -1, true, 0));
        rig.put(PatternAlertFixtures.watermark(DATE, "TF_1M", 1, 0, true, 0));
        rig.put(PatternAlertFixtures.watermark(DATE, "TF_1M", 5, 4, true, 0));   // 2..4 were never recorded: the predecessor says its previous was 4
        rig.put(PatternAlertFixtures.watermark(DATE, "TF_1M", 6, 5, false, 0));
        rig.put(PatternAlertFixtures.watermark(DATE, "TF_1M", 6, 5, true, 0));   // a duplicate of 6: not counted twice
        JsonNode c = json(rig.audit().byDate("regular", DATE, null, null)).get("coverage").get("1m");
        assertEquals(4, c.get("folds").asInt());
        assertEquals(1, c.get("unevaluated").asInt());
        assertEquals(1, c.get("holes").size());
        assertEquals(2, c.get("holes").get(0).get("fromOrdinal").asInt());
        assertEquals(4, c.get("holes").get(0).get("toOrdinal").asInt());
        assertEquals(PatternAlertFixtures.barEnd(0), c.get("evaluatedFirstAsOfMs").asLong());
        assertEquals(PatternAlertFixtures.barEnd(5), c.get("evaluatedLastAsOfMs").asLong(), "an unevaluated fold is not an evaluation");
    }

    @Test
    void anEmptyEventListIsNeverReturnedWithoutTheCoverageThatShowsTheEvaluatorRan() {
        Rig rig = new Rig(TOPIC);
        rig.watermarks(DATE, 4);
        JsonNode r = json(rig.audit().byDate("regular", DATE, null, null));
        assertEquals("OK", r.get("status").asText());
        assertEquals(0, r.get("events").size(), "nothing happened that day");
        assertEquals(4, r.get("coverage").get("1m").get("folds").asInt(), "and the proof that the evaluator ran");
        assertEquals(4, r.get("coverage").get("15m").get("folds").asInt());
        assertTrue(r.get("note").asText().contains("coverage"));
    }

    @Test
    void aDateWithNoRecordIsEmptyAndSaysNothingProvesTheEvaluatorRanAndAnOlderOneIsOutsideRetention() {
        Rig rig = new Rig(TOPIC);
        rig.put(PatternAlertFixtures.triangleBreak(1, "RAISED"));
        PatternAlertAudit audit = rig.audit();
        JsonNode empty = json(audit.byDate("regular", "2026-11-04", null, null));
        assertEquals("EMPTY", empty.get("status").asText());
        assertEquals(0, empty.get("events").size());
        assertTrue(empty.get("note").asText().contains("nothing proves"));
        rig.clock.set(PatternAlertFixtures.OPEN_MS + 8L * 86_400_000L);   // eight days later: a week of retention does not reach back
        JsonNode lost = json(audit.byDate("regular", "2026-10-20", null, null));
        assertEquals("OUTSIDE_RETENTION", lost.get("status").asText());
        assertEquals("EMPTY", json(audit.byDate("regular", "2026-11-04", null, null)).get("status").asText(), "a recent date without records is quiet, not lost");
        JsonNode beforeData = json(audit.byDate("regular", "2026-11-01", null, null));
        assertEquals("OUTSIDE_RETENTION", beforeData.get("status").asText(), "older than the earliest surviving record on the topic");
    }

    @Test
    @org.junit.jupiter.api.Timeout(value = 30, threadMode = org.junit.jupiter.api.Timeout.ThreadMode.SEPARATE_THREAD)
    void aTopicThatCannotBeReadIsUnreadableNeverAnEmptyAnswer() {
        Rig missing = new Rig(TOPIC);
        missing.noPartitions = true;
        PatternAlertAudit.Response gone = missing.audit().byDate("regular", DATE, null, null);
        assertEquals(503, gone.status());
        assertEquals("UNREADABLE", json(gone).get("status").asText());
        assertTrue(json(gone).get("error").asText().contains("does not exist"));
        // a read that never reaches the end offsets within the bound
        Rig stuck = new Rig(TOPIC);
        stuck.put(PatternAlertFixtures.triangleBreak(1, "RAISED"));
        stuck.phantomEnd = 1;   // the end offset is beyond what the log can deliver
        Log.POLLS.set(0);
        PatternAlertAudit.Response slow = stuck.audit().byDate("regular", DATE, null, null);
        assertEquals(503, slow.status());
        assertTrue(Log.POLLS.get() > 100 && Log.POLLS.get() < 10_000, "the read gave up at its 5 s bound (the test clock ticks 1 ms a call), after " + Log.POLLS.get() + " polls");
        assertTrue(json(slow).get("error").asText().contains("within 5000 ms"), slow.body());
        assertEquals(503, stuck.audit.byDays("regular", "3", null).status());
    }

    @Test
    void aRecordThatCannotBeDecodedOrIsAnotherSubjectIsCountedOutAndTheRestStillAnswers() {
        Rig rig = threeEvents();
        rig.putRaw(new byte[] {1, 2, 3}, 5L);
        rig.put(TaFixtures.tools("M1", 1, "OK"));
        rig.putRaw(null, 6L);
        JsonNode r = json(rig.audit().byDate("regular", DATE, null, null));
        assertEquals("OK", r.get("status").asText());
        assertEquals(3, r.get("events").size());
    }

    @Test
    void aLongSessionIsReadInPagesWithTheAfterContinuationRepeatingNoEventAndSkippingNone() {
        Rig rig = new Rig(TOPIC);
        rig.watermarks(DATE, 2);
        for (int i = 1; i <= 7; i++) {
            rig.put(PatternAlertFixtures.numbered(DATE, i, i % 2 == 0 ? "SUPPRESSED_REPLAY" : "RAISED", 100 + i));
        }
        PatternAlertAudit audit = rig.audit();
        List<Long> seen = new ArrayList<>();
        String after = null;
        int pages = 0;
        while (true) {
            JsonNode page = json(audit.byDate("regular", DATE, "3", after));
            pages++;
            page.get("events").forEach(e -> seen.add(e.get("eventSeq").asLong()));
            assertTrue(page.get("events").size() <= 3);
            assertEquals(2, page.get("coverage").get("1m").get("folds").asInt(), "the coverage rides every page");
            if (!page.get("truncated").asBoolean()) {
                assertTrue(page.get("nextAfter").isNull());
                break;
            }
            after = page.get("nextAfter").asText();
            assertEquals(page.get("events").get(page.get("events").size() - 1).get("eventSeq").asText(), after);
        }
        assertEquals(List.of(1L, 2L, 3L, 4L, 5L, 6L, 7L), seen);
        assertEquals(3, pages);
        JsonNode tail = json(audit.byDate("regular", DATE, null, "5"));
        assertEquals(List.of(6L, 7L), List.of(tail.get("events").get(0).get("eventSeq").asLong(), tail.get("events").get(1).get("eventSeq").asLong()));
        assertEquals(7, json(audit.byDate("regular", DATE, null, "0")).get("events").size());
    }

    @Test
    void theParametersAreValidatedAndRefusedNeverClampedSilently() {
        PatternAlertAudit audit = threeEvents().audit();
        for (String limit : new String[] {"0", "-1", "abc", "2001", "1.5", ""}) {
            PatternAlertAudit.Response r = audit.byDate("regular", DATE, limit, null);
            assertEquals(400, r.status(), "limit " + limit);
            assertEquals("{\"error\":\"limit\"}", r.body());
        }
        assertEquals(200, audit.byDate("regular", DATE, "2000", null).status());
        assertEquals(200, audit.byDate("regular", DATE, "1", null).status());
        assertEquals("{\"error\":\"after\"}", audit.byDate("regular", DATE, null, "-1").body());
        assertEquals("{\"error\":\"after\"}", audit.byDate("regular", DATE, null, "x").body());
        for (String date : new String[] {null, "", "2026-13-40", "03-11-2026", "2026-11-3", "2026-11-03 "}) {
            assertEquals("{\"error\":\"date\"}", audit.byDate("regular", date, null, null).body(), "date " + date);
        }
        assertEquals("{\"error\":\"profile\"}", audit.byDate("premarket", DATE, null, null).body());
        for (String days : new String[] {"0", "8", "-3", "abc", "", null}) {
            assertEquals("{\"error\":\"days\"}", audit.byDays("regular", days, null).body(), "days " + days);
        }
        for (String limit : new String[] {"0", "501", "abc"}) {
            assertEquals("{\"error\":\"limit\"}", audit.byDays("regular", "7", limit).body(), "days limit " + limit);
        }
        assertEquals(200, audit.byDays("regular", "7", "500").status());
        assertEquals(200, audit.byDays("regular", "1", null).status());
    }

    @Test
    void thePastEventsFormListsTheNewestSessionsFirstWithTheServersStatusTextOnEveryEvent() {
        Rig rig = new Rig(TOPIC);
        for (String d : List.of("2026-10-30", "2026-11-02", "2026-11-03")) {
            rig.watermarks(d, 1);
        }
        rig.put(PatternAlertFixtures.numbered("2026-10-30", 1, "RAISED", 50));
        rig.put(PatternAlertFixtures.numbered("2026-11-02", 1, "SUPPRESSED_REPLAY", 60));
        rig.put(PatternAlertFixtures.numbered("2026-11-02", 2, "SUPPRESSED_SELF_CHECK", 61));
        rig.put(PatternAlertFixtures.triangleBreak(1, "RAISED"));
        rig.put(PatternAlertFixtures.wedgeBreak(2, "SUPPRESSED_STALE"));
        PatternAlertAudit audit = rig.audit();
        JsonNode r = json(audit.byDays("regular", "2", null));
        assertEquals("OK", r.get("status").asText());
        assertEquals(2, r.get("days").asInt());
        assertEquals(4, r.get("events").size(), "the newest two session dates only");
        List<String> order = new ArrayList<>();
        r.get("events").forEach(e -> order.add(e.get("sessionDate").asText() + "#" + e.get("eventSeq").asLong()));
        assertEquals(List.of("2026-11-03#2", "2026-11-03#1", "2026-11-02#2", "2026-11-02#1"), order, "newest first by (sessionDate, eventSeq)");
        assertEquals("Not alerted: service was catching up", r.get("events").get(0).get("statusText").asText());
        assertEquals("Raised", r.get("events").get(1).get("statusText").asText());
        assertEquals("Not alerted: record contradicted itself", r.get("events").get(2).get("statusText").asText());
        assertEquals("Not alerted: service restarted", r.get("events").get(3).get("statusText").asText());
        assertTrue(r.get("coverage").has("2026-11-03") && r.get("coverage").has("2026-11-02") && !r.get("coverage").has("2026-10-30"));
        assertEquals(1, r.get("coverage").get("2026-11-03").get("1m").get("folds").asInt());
        assertEquals(3, json(audit.byDays("regular", "7", null)).get("coverage").size(), "all three dates are in the week");
        JsonNode capped = json(audit.byDays("regular", "7", "2"));
        assertEquals(2, capped.get("events").size());
        assertTrue(capped.get("truncated").asBoolean());
        assertEquals("2026-11-03", capped.get("events").get(0).get("sessionDate").asText());
        assertEquals("EMPTY", json(new Rig(TOPIC).audit().byDays("regular", "7", null)).get("status").asText());
    }

    @Test
    void theStatusTextTableIsClosedForEveryDisposition() {
        assertEquals("Raised", PatternAlertAudit.statusText("RAISED"));
        assertEquals("Not alerted: service was catching up", PatternAlertAudit.statusText("SUPPRESSED_STALE"));
        assertEquals("Not alerted: service restarted", PatternAlertAudit.statusText("SUPPRESSED_REPLAY"));
        assertEquals("Not alerted: record contradicted itself", PatternAlertAudit.statusText("SUPPRESSED_SELF_CHECK"));
        assertEquals("Not alerted", PatternAlertAudit.statusText("SOMETHING_NEW"));
    }

    @Test
    void aSecondReadWhileOneRunsIsBusyAndCounted() {
        Rig rig = threeEvents();
        AtomicInteger inner = new AtomicInteger();
        List<PatternAlertAudit.Response> nested = new ArrayList<>();
        GatewaySettings settings = new GatewaySettings();
        PatternAlertAudit[] holder = new PatternAlertAudit[1];
        holder[0] = new PatternAlertAudit(settings, () -> {
            if (inner.getAndIncrement() == 0) {
                nested.add(holder[0].byDate("regular", DATE, null, null));   // while the first read holds the permit
            }
            return rig.newLog();
        }, (t, b) -> rig.deserializer.deserialize(t, b), System::currentTimeMillis);
        PatternAlertAudit.Response first = holder[0].byDate("regular", DATE, null, null);
        assertEquals(200, first.status());
        assertEquals(1, nested.size());
        assertEquals(503, nested.get(0).status());
        assertEquals("{\"error\":\"busy\"}", nested.get(0).body());
        String metrics = holder[0].metrics();
        assertTrue(metrics.contains("gateway_pattern_alerts_audit_busy_total 1\n"), metrics);
        assertTrue(metrics.contains("gateway_pattern_alerts_audit_requests_total 1\n"), metrics);
        assertEquals(200, holder[0].byDate("regular", DATE, null, null).status(), "the permit is released");
    }

    @Test
    void theConsumerIsReadCommittedAndNeverCreatesATopic() {
        java.util.Properties p = PatternAlertAudit.consumerProperties(new GatewaySettings());
        assertEquals("read_committed", p.get("isolation.level"));
        assertEquals("false", p.get("allow.auto.create.topics"));
        assertEquals("false", p.get("enable.auto.commit"));
        assertEquals("org.apache.kafka.common.serialization.ByteArrayDeserializer", p.get("value.deserializer"));
        assertNotNull(p.get("bootstrap.servers"));
    }
}

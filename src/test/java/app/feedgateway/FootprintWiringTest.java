package app.feedgateway;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * ES-FOOTPRINT-GATEWAY-DESIGN.md G-R1/G-R2/G-R3/G-R6/G-R9/G-R10/G-R11 — the wiring-shape pins, the
 * flag-off identity, the hello field, the admission path both consumers share, and the exact
 * metrics contract, without a broker.
 */
class FootprintWiringTest {

    private static final Path SERVICE = Path.of("src/main/java/app/feedgateway/FeedGatewayService.java");
    private static final Path CONTROLLER = Path.of("src/main/java/app/feedgateway/GatewayController.java");

    private static FeedGatewayService off() {
        return new FeedGatewayService(new GatewaySettings(), new ObjectMapper(), new HpsfGatewayViewMapper(), null);
    }

    static FeedGatewayService on() {
        FootprintTopicGateTest.FakeReader r = new FootprintTopicGateTest.FakeReader();
        for (String t : List.of("futures.footprint", "futures.footprint.evidence", "futures.footprint.bars", "futures.footprint.outcomes", "futures.footprint.strike")) r.valid(t);
        return new FeedGatewayService(new GatewaySettings(), new ObjectMapper(), new HpsfGatewayViewMapper(), null, r);
    }

    private static int occurrences(String text, String needle) {
        int count = 0;
        for (int at = 0; (at = text.indexOf(needle, at)) >= 0; at += needle.length()) count++;
        return count;
    }

    // ---- G-R2: one wiring path, both state consumers, all four topics unconditionally --------------

    @Test void bootstrapAndLiveConsumersShareOneFootprintTopicWiringPathWithAllFourTopics() throws Exception {
        String src = Files.readString(SERVICE);
        assertEquals(2, occurrences(src, "addEsFootprintTopics(topicEvents);"), "called from the state cache AND the state live consumer");
        assertEquals(2, occurrences(src, "addEsCvdTopics(topicEvents);\n        addEsFootprintTopics(topicEvents);"), "right after the CVD wiring at both sites");
        int start = src.indexOf("void addEsFootprintTopics(Map<String, TopicBinding> topicEvents) {");
        String body = src.substring(start, src.indexOf("\n    }", start));
        assertTrue(body.contains("if (footprintViews == null) return;"), "flag off adds nothing");
        for (String e : new String[]{"es-footprint\"", "es-footprint-evidence\"", "es-footprint-bar\"", "es-footprint-outcome\"", "es-footprint-strike\""}) {
            assertEquals(1, occurrences(body, "new TopicBinding(\"DATABENTO\", \"" + e + ")"), e);
        }
        assertFalse(body.contains("admit("), "the topic SET is unconditional; admission gates consumption, not discovery");
    }

    @Test void thePartitionRefreshPredicateSeamSitsBeforeMergeAndAssignAndBothStateSitesPassTheGate() throws Exception {
        String src = Files.readString(SERVICE);
        assertEquals(2, occurrences(src, "new PartitionRefresh(name, topicEvents.keySet(), footprintTopicAdmit())"), "the two STATE consumers pass the gate");
        assertEquals(4, occurrences(src, "new PartitionRefresh(\"") + occurrences(src, "new PartitionRefresh(name, topicEvents.keySet());"),
                "the other four sites keep the always-true predicate (through the two-argument constructor)");
        int apply = src.indexOf("Refresh apply(KafkaConsumer<?, ?> consumer, List<TopicPartition> assigned)");
        int filter = src.indexOf("if (topicAdmit.test(p.topic())) admitted.add(p);", apply);
        int added = src.indexOf("List<TopicPartition> added = addedPartitions(assigned, admitted);", apply);
        int assign = src.indexOf("consumer.assign(merged);", apply);
        assertTrue(apply > 0 && filter > apply && added > filter && assign > added, "gate → added() → merge/assign, in that order inside apply()");
        assertEquals(2, occurrences(src, "bootstrapAssign(name, consumer, topicEvents)"), "both state consumers bootstrap through the ONE filtered assign seam");
        int seam = src.indexOf("List<TopicPartition> bootstrapAssign(String name, KafkaConsumer<?, ?> consumer, Map<String, TopicBinding> topicEvents) {");
        String seamBody = src.substring(seam, src.indexOf("\n    }", seam));
        assertTrue(seamBody.indexOf("footprintAdmitted(partitionsFor(") < seamBody.indexOf("consumer.assign(partitions);"), "filtered BEFORE the assign");
    }

    @Test void deliveryClassesAreWiredAsDesigned() throws Exception {
        String src = Files.readString(SERVICE);
        int live = src.indexOf("if (footprintViews != null && isFootprintEvent(binding.event())) {");
        int cvd = src.indexOf("if (\"es-cvd\".equals(binding.event())) {");
        assertTrue(live > 0 && live < cvd, "the live-consumer footprint branch precedes the es-cvd branch");
        String branch = src.substring(live, src.indexOf("continue;", live));
        assertTrue(branch.contains("onFootprintLiveRecord(binding.event(), json);"), "the live branch delegates to the one admit-then-broadcast method");
        int method = src.indexOf("boolean onFootprintLiveRecord(String event, String json) {");
        String body = src.substring(method, src.indexOf("\n    }", method));
        assertTrue(body.indexOf("admitFootprintRecord(event, json, \"live\")") < body.indexOf("broadcast(event, "), "view first, then broadcast");
        assertTrue(body.contains("FootprintStrikeView.quoted(json)"), "the strike record rides the frame as a JSON string literal: the page folds the bytes the relay folded");
        assertTrue(body.contains("if (!admitFootprintRecord(event, json, \"live\")) return false;"), "an oversize record is never broadcast");
        int cache = src.indexOf("admitFootprintRecord(binding.event(), json, \"cache\");");
        assertTrue(cache > 0 && cache < src.indexOf("updateCache(binding, record, json);", cache), "the cache consumer admits before the generic cache and never broadcasts");
        int raw = src.indexOf("private static boolean isRawPassThroughEvent(String event)");
        assertTrue(src.substring(raw, src.indexOf("}", raw)).contains("isFootprintEvent(event)"), "verbatim: never enriched");
        int allow = src.indexOf("\"es-cvd-bar\",");
        String after = src.substring(allow, allow + 600);
        for (String e : new String[]{"\"es-footprint\",", "\"es-footprint-evidence\",", "\"es-footprint-bar\",", "\"es-footprint-outcome\",", "\"es-footprint-strike\","}) assertTrue(after.contains(e), e + " allowlisted");
    }

    // ---- G-R1/G-R10: flag off is byte-identical ----------------------------------------------------

    @Test void flagOffHasNoViewsNoHelloFieldAndOnlyTheEnabledGauge() {
        FeedGatewayService s = off();
        assertFalse(s.footprintEnabled());
        assertNull(s.footprintViews());
        assertEquals("{\"sessionDate\":null,\"hwm\":{}}", s.cvdHelloJson(), "today's hello, byte for byte");
        String m = s.footprintMetricsText();
        assertTrue(m.endsWith("gateway_footprint_enabled 0\n"));
        assertEquals(1, m.lines().filter(l -> !l.startsWith("#")).count(), "exactly one footprint series flag-off");
        assertTrue(s.metrics().contains("gateway_footprint_enabled 0\n"));
    }

    /**
     * G-R2: the four bindings are asserted by EXECUTING the wiring, not by reading its source. A
     * source assertion counts the text of a {@code TopicBinding} that a surrounding {@code if (false)}
     * has made unreachable; only running the method can tell the two apart.
     */
    @Test void theWiringMethodPutsAllFourBindingsInTheMapItIsGiven() {
        java.util.Map<String, FeedGatewayService.TopicBinding> wired = new java.util.LinkedHashMap<>();
        on().addEsFootprintTopics(wired);
        GatewaySettings g = new GatewaySettings();
        assertEquals(java.util.Map.of(
                g.esFootprintTopic(), "es-footprint",
                g.esFootprintEvidenceTopic(), "es-footprint-evidence",
                g.esFootprintBarsTopic(), "es-footprint-bar",
                g.esFootprintOutcomesTopic(), "es-footprint-outcome",
                g.esFootprintStrikeTopic(), "es-footprint-strike"),
                wired.entrySet().stream().collect(java.util.stream.Collectors.toMap(
                        java.util.Map.Entry::getKey, e -> e.getValue().event())),
                "every topic is bound, to its own event");
        wired.values().forEach(b -> assertEquals("DATABENTO", b.source()));
        java.util.Map<String, FeedGatewayService.TopicBinding> none = new java.util.LinkedHashMap<>();
        off().addEsFootprintTopics(none);
        assertTrue(none.isEmpty(), "flag off adds nothing");
    }

    /** G-R3: the two live snapshot topics are never retained; the two keyed topics seek back a session. */
    @Test void liveSnapshotsAreNeverRetainedAndTheKeyedTopicsSeekBackASession() {
        FeedGatewayService s = on();
        long back = new GatewaySettings().esFootprintSeekBackMs();
        assertTrue(back > 0, "the keyed seek-back is a real window");
        for (String live : new String[]{"es-footprint", "es-footprint-evidence"}) {
            assertEquals(0L, s.cachePolicyFor(live, 0L).ttlMs(), live + " is never retained");
        }
        for (String keyed : new String[]{"es-footprint-bar", "es-footprint-outcome"}) {
            assertEquals(back, s.cachePolicyFor(keyed, 0L).ttlMs(), keyed + " re-fills from the compacted topic");
        }
        assertEquals(new GatewaySettings().esFootprintStrikeSeekBackMs(), s.cachePolicyFor("es-footprint-strike", 0L).ttlMs(), "the strike log's history crosses sessions: a week");
        assertTrue(new GatewaySettings().esFootprintStrikeSeekBackMs() > back);
    }

    /** G-R6: footprint alone is enough to send the hello — the page needs the handshake either way. */
    @Test void theHelloIsSentWhenFootprintAloneIsEnabled() {
        GatewaySettings g = new GatewaySettings();
        assertFalse(g.esCvdEnabled(), "the fixture isolates the footprint disjunct");
        assertFalse(g.esCvdSpxLevelsEnabled());
        assertTrue(on().sendsCvdHello(), "footprint alone still sends cvd-hello");
        assertFalse(off().sendsCvdHello(), "and nothing enabled sends none");
    }

    /**
     * G-R8: the deployment contingency is an inequality over the PEAK heap and the peak container
     * working set. The cgroup's memory.peak bounds the working set but says nothing about the heap
     * inside it, so H_peak was not derivable from anything this process published — the enablement
     * PR had to state it as an estimate. These series make it measurable:
     * {@code max_over_time(jvm_memory_bytes_used{area="heap"}[8h])} over a full session.
     */
    @Test void theMetricsCarryTheJvmMemorySeriesTheContingencyIsStatedOver() {
        // Drive the exposition from a STUB MXBean, so the assertion is on exact values rather than
        // on whatever the real heap happened to be doing. An earlier version allocated ballast and
        // asserted the reading had not fallen by more than 64 MiB — which a constant exporter
        // passes, and so does almost anything else.
        var prior = FeedGatewayService.memoryMxBean;
        try {
            FeedGatewayService.memoryMxBean = () -> stubMemory(
                    new java.lang.management.MemoryUsage(1L, 111L, 222L, 333L),      // heap
                    new java.lang.management.MemoryUsage(2L, 444L, 555L, -1L));      // non-heap, unbounded
            String m = FeedGatewayService.jvmMemorySeries();
            for (String series : new String[]{"jvm_memory_bytes_used", "jvm_memory_bytes_committed", "jvm_memory_bytes_max"}) {
                assertTrue(m.contains("# TYPE " + series + " gauge\n"), series + " is typed");
            }
            assertTrue(m.contains("jvm_memory_bytes_used{area=\"heap\"} 111\n"), m);
            assertTrue(m.contains("jvm_memory_bytes_committed{area=\"heap\"} 222\n"), m);
            assertTrue(m.contains("jvm_memory_bytes_max{area=\"heap\"} 333\n"), m);
            assertTrue(m.contains("jvm_memory_bytes_used{area=\"nonheap\"} 444\n"), m);
            assertTrue(m.contains("jvm_memory_bytes_committed{area=\"nonheap\"} 555\n"), m);
            assertTrue(m.contains("jvm_memory_bytes_max{area=\"nonheap\"} -1\n"),
                    "an unbounded area is -1, so 'unbounded' and 'exporter absent' stay distinct: " + m);

            // a SECOND reading reports the new numbers: a value captured once would not
            FeedGatewayService.memoryMxBean = () -> stubMemory(
                    new java.lang.management.MemoryUsage(1L, 999L, 1000L, 1001L),
                    new java.lang.management.MemoryUsage(2L, 7L, 8L, 9L));
            String again = FeedGatewayService.jvmMemorySeries();
            assertTrue(again.contains("jvm_memory_bytes_used{area=\"heap\"} 999\n"), again);
            assertTrue(again.contains("jvm_memory_bytes_max{area=\"nonheap\"} 9\n"), again);
            assertFalse(again.contains(" 111\n"), "the first reading is not cached: " + again);
        } finally {
            FeedGatewayService.memoryMxBean = prior;
        }
        // and the live endpoint carries them, with the real JVM's own numbers
        String live = on().metrics();
        assertTrue(live.contains("jvm_memory_bytes_used{area=\"heap\"} "), live.substring(0, 200));
        java.util.regex.Matcher used = java.util.regex.Pattern
                .compile("jvm_memory_bytes_used\\{area=\"heap\"\\} (\\d+)").matcher(live);
        java.util.regex.Matcher committed = java.util.regex.Pattern
                .compile("jvm_memory_bytes_committed\\{area=\"heap\"\\} (\\d+)").matcher(live);
        assertTrue(used.find() && committed.find());
        long u = Long.parseLong(used.group(1)), c = Long.parseLong(committed.group(1));
        assertTrue(u > 0 && u <= c, "used " + u + " must be positive and within committed " + c);
    }

    /** A MemoryMXBean that reports exactly what it is given; every other method is unused here. */
    private static java.lang.management.MemoryMXBean stubMemory(java.lang.management.MemoryUsage heap,
                                                                java.lang.management.MemoryUsage nonHeap) {
        return (java.lang.management.MemoryMXBean) java.lang.reflect.Proxy.newProxyInstance(
                FootprintWiringTest.class.getClassLoader(),
                new Class<?>[]{java.lang.management.MemoryMXBean.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getHeapMemoryUsage" -> heap;
                    case "getNonHeapMemoryUsage" -> nonHeap;
                    case "toString" -> "stubMemory";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    // ---- G-R6 hello ---------------------------------------------------------------------------------

    @Test void flagOnAddsOneHelloFieldFromTheCoordinatorSnapshot() {
        FeedGatewayService s = on();
        assertEquals("{\"sessionDate\":null,\"hwm\":{},\"footprint\":{\"sessionDate\":null,\"hwm\":{},\"outcomeHwm\":{}},"
                + "\"footprintStrike\":{\"sessionDate\":null,\"hwm\":{},\"historyBeginsAtMs\":null,\"replayBeginsAtMs\":null,\"loading\":true,\"refused\":0,\"unavailable\":false}}", s.cvdHelloJson());
        assertTrue(s.admitFootprintRecord("es-footprint-bar", FootprintViewsTest.bar("2026-08-14", "1m", 60_000), "live"));
        assertTrue(s.cvdHelloJson().contains("\"footprint\":{\"sessionDate\":\"2026-08-14\",\"hwm\":{\"1m\":60000},\"outcomeHwm\":{}}"));
        assertTrue(s.admitFootprintRecord("es-footprint-strike", FootprintStrikeViewTest.checkpoint("2026-08-14", "1m", 60_000), "cache"));
        assertTrue(s.cvdHelloJson().endsWith("\"footprintStrike\":{\"sessionDate\":\"2026-08-14\",\"hwm\":{\"1m\":60000},\"historyBeginsAtMs\":null,\"replayBeginsAtMs\":null,\"loading\":true,\"refused\":0,\"unavailable\":false}}"),
                "the episode high-water mark rides the SAME hello (R14)");
    }

    // ---- G-R3/G-R9: the shared admission path and overlap identities --------------------------------

    @Test void admissionCountsAndBroadcastDecisionsFollowTheOverlapRules() {
        FeedGatewayService s = on();
        assertTrue(s.admitFootprintRecord("es-footprint", "{\"schemaVersion\":6}", "live"), "live snapshots are broadcast, never admitted");
        assertTrue(s.admitFootprintRecord("es-footprint-bar", FootprintViewsTest.bar("2026-08-14", "1m", 1), "live"));
        assertTrue(s.admitFootprintRecord("es-footprint-bar", FootprintViewsTest.bar("2026-08-13", "1m", 1), "live"), "stale: dropped from the view, still broadcast");
        assertTrue(s.admitFootprintRecord("es-footprint-outcome", "{\"unrelated\":true}", "cache"), "shape: dropped from the view (cache path never broadcasts anyway)");
        assertFalse(s.admitFootprintRecord("es-footprint-outcome", "{\"pad\":\"" + "y".repeat(300_000) + "\"}", "live"), "oversize: dropped entirely");
        String m = s.footprintMetricsText();
        assertTrue(m.contains("gateway_footprint_records_total{event=\"es-footprint\",consumer=\"live\"} 1\n"), m);
        assertTrue(m.contains("gateway_footprint_records_total{event=\"es-footprint-bar\",consumer=\"live\"} 2\n"));
        assertTrue(m.contains("gateway_footprint_drops_total{event=\"es-footprint-bar\",consumer=\"live\",reason=\"stale_session\"} 1\n"));
        assertTrue(m.contains("gateway_footprint_drops_total{event=\"es-footprint-outcome\",consumer=\"cache\",reason=\"shape\"} 1\n"));
        assertTrue(m.contains("gateway_footprint_drops_total{event=\"es-footprint-outcome\",consumer=\"live\",reason=\"oversize\"} 1\n"));
        assertTrue(m.contains("gateway_footprint_drops_total{event=\"es-footprint-bar\",consumer=\"cache\",reason=\"stale_session\"} 0\n"));
        assertTrue(m.contains("gateway_footprint_bars_in_view 1\n"));
        assertEquals(1, s.footprintViews().barsInView());
    }

    @Test void bootstrapAdmissionWithholdsOnlyUnvalidatedFootprintPartitions() {
        FootprintTopicGateTest.FakeReader r = new FootprintTopicGateTest.FakeReader();
        r.valid("futures.footprint").valid("futures.footprint.evidence").valid("futures.footprint.bars").unknown("futures.footprint.outcomes");
        FeedGatewayService s = new FeedGatewayService(new GatewaySettings(), new ObjectMapper(), new HpsfGatewayViewMapper(), null, r);
        List<org.apache.kafka.common.TopicPartition> resolved = List.of(
                new org.apache.kafka.common.TopicPartition("futures.cvd", 0),
                new org.apache.kafka.common.TopicPartition("futures.footprint.bars", 0),
                new org.apache.kafka.common.TopicPartition("futures.footprint.outcomes", 0));
        List<org.apache.kafka.common.TopicPartition> admitted = s.footprintAdmitted(resolved);
        assertEquals(List.of(resolved.get(0), resolved.get(1)), admitted, "the absent/unvalidated topic's partition is withheld; others pass");
        r.valid("futures.footprint.outcomes");
        assertEquals(resolved, s.footprintAdmitted(resolved), "admitted on the next evaluation once validated");
        assertEquals(resolved, off().footprintAdmitted(resolved), "flag off: identity");
    }

    // ---- G-R9/G-R11 (12): the exact contract flag-on at start-up -----------------------------------

    @Test void everyMetricsSeriesIsExportedWithEveryLabelValueAtZeroAtStartUp() {
        String m = on().footprintMetricsText();
        String[] events = {"es-footprint", "es-footprint-evidence", "es-footprint-bar", "es-footprint-outcome", "es-footprint-strike"};
        String[] topics = {"futures.footprint", "futures.footprint.evidence", "futures.footprint.bars", "futures.footprint.outcomes", "futures.footprint.strike"};
        java.util.List<String> expect = new java.util.ArrayList<>();
        expect.add("gateway_footprint_enabled 1");
        for (String e : events) for (String c : new String[]{"cache", "live"}) expect.add("gateway_footprint_records_total{event=\"" + e + "\",consumer=\"" + c + "\"} 0");
        for (String e : new String[]{"es-footprint-bar", "es-footprint-outcome", "es-footprint-strike"}) for (String c : new String[]{"cache", "live"}) for (String r : new String[]{"oversize", "shape", "stale_session", "collision", "refused", "evicted", "unavailable"})
            expect.add("gateway_footprint_drops_total{event=\"" + e + "\",consumer=\"" + c + "\",reason=\"" + r + "\"} 0");
        for (String e : events) expect.add("gateway_footprint_broadcast_total{event=\"" + e + "\"} 0");
        expect.add("gateway_footprint_evictions_total{view=\"bars\"} 0"); expect.add("gateway_footprint_evictions_total{view=\"outcomes\"} 0");
        expect.add("gateway_footprint_rollovers_total 0"); expect.add("gateway_footprint_bars_in_view 0"); expect.add("gateway_footprint_outcomes_in_view 0");
        expect.add("gateway_footprint_view_bytes{view=\"bars\"} 0"); expect.add("gateway_footprint_view_bytes{view=\"outcomes\"} 0");
        for (String r : new String[]{"bars", "outcomes", "strike_latest", "strike_history"}) expect.add("gateway_footprint_backfill_requests_total{route=\"" + r + "\"} 0");
        for (String r : new String[]{"bars", "outcomes", "strike_latest", "strike_history"}) for (String x : new String[]{"busy", "bad_cursor", "session_mismatch", "unavailable"}) expect.add("gateway_footprint_backfill_rejected_total{route=\"" + r + "\",reason=\"" + x + "\"} 0");
        expect.add("gateway_footprint_strike_episodes_in_view 0"); expect.add("gateway_footprint_strike_view_bytes 0");
        // what the revision ledgers, identities and tombstones cost, and whether the cache replay has
        // crossed its bootstrap boundary — both budgeted/published since code round 2 (#1, #3)
        expect.add("gateway_footprint_strike_view_metadata_bytes 0"); expect.add("gateway_footprint_strike_loading 1");
        expect.add("gateway_footprint_strike_evictions_total 0");
        expect.add("gateway_footprint_strike_collisions_total 0"); expect.add("gateway_footprint_strike_refused_identities 0"); expect.add("gateway_footprint_strike_unavailable 0");
        for (String t : topics) expect.add("gateway_footprint_topic_validated{topic=\"" + t + "\"} 0");
        for (String t : topics) for (String r : new String[]{"admin", "unknown", "ceiling", "compression"}) expect.add("gateway_footprint_topic_validation_failures_total{topic=\"" + t + "\",reason=\"" + r + "\"} 0");
        List<String> actual = m.lines().filter(l -> !l.startsWith("#")).toList();
        assertEquals(expect, actual, "exact series set, order and initial values");
        for (String name : List.of("gateway_footprint_enabled", "gateway_footprint_records_total", "gateway_footprint_drops_total", "gateway_footprint_broadcast_total",
                "gateway_footprint_evictions_total", "gateway_footprint_rollovers_total", "gateway_footprint_bars_in_view", "gateway_footprint_outcomes_in_view",
                "gateway_footprint_view_bytes", "gateway_footprint_backfill_requests_total", "gateway_footprint_backfill_rejected_total",
                "gateway_footprint_strike_episodes_in_view", "gateway_footprint_strike_view_bytes",
                "gateway_footprint_strike_view_metadata_bytes", "gateway_footprint_strike_loading", "gateway_footprint_strike_evictions_total",
                "gateway_footprint_strike_collisions_total", "gateway_footprint_strike_refused_identities", "gateway_footprint_strike_unavailable",
                "gateway_footprint_topic_validated", "gateway_footprint_topic_validation_failures_total")) {
            assertEquals(1, occurrences(m, "# TYPE " + name + " "), name + " typed once");
        }
    }

    @Test void validationSeriesFollowTheGateAfterStartUp() {
        FootprintTopicGateTest.FakeReader r = new FootprintTopicGateTest.FakeReader();
        r.valid("futures.footprint").valid("futures.footprint.evidence").valid("futures.footprint.bars").unknown("futures.footprint.outcomes");
        FeedGatewayService s = new FeedGatewayService(new GatewaySettings(), new ObjectMapper(), new HpsfGatewayViewMapper(), null, r);
        s.footprintGate().validateExisting();
        assertFalse(s.footprintGate().admit("futures.footprint.outcomes"));
        String m = s.footprintMetricsText();
        assertTrue(m.contains("gateway_footprint_topic_validated{topic=\"futures.footprint.bars\"} 1\n"));
        assertTrue(m.contains("gateway_footprint_topic_validated{topic=\"futures.footprint.outcomes\"} 0\n"));
        assertTrue(m.contains("gateway_footprint_topic_validation_failures_total{topic=\"futures.footprint.outcomes\",reason=\"unknown\"} 2\n"));
    }

    @Test void settingsDefaults() {
        GatewaySettings g = new GatewaySettings();
        assertFalse(g.esFootprintEnabled());
        assertEquals("futures.footprint", g.esFootprintTopic()); assertEquals("futures.footprint.evidence", g.esFootprintEvidenceTopic());
        assertEquals("futures.footprint.bars", g.esFootprintBarsTopic()); assertEquals("futures.footprint.outcomes", g.esFootprintOutcomesTopic());
        assertEquals(262_144L, g.esFootprintMaxRecordBytes()); assertEquals(128L << 20, g.esFootprintBarsMaxBytes()); assertEquals(12_000, g.esFootprintBarsMaxCount());
        assertEquals(16L << 20, g.esFootprintOutcomesMaxBytes()); assertEquals(20_000, g.esFootprintOutcomesMaxCount());
        assertEquals(4, g.esFootprintBackfillConcurrency()); assertEquals(1_048_588L, g.esFootprintMaxMessageBytesCeiling()); assertEquals(24L * 3_600_000L, g.esFootprintSeekBackMs());
        assertEquals("futures.footprint.strike", g.esFootprintStrikeTopic()); assertEquals(64L << 20, g.esFootprintStrikeMaxBytes());
        assertEquals(50_000, g.esFootprintStrikeMaxEpisodes()); assertEquals(7L * 24 * 3_600_000L, g.esFootprintStrikeSeekBackMs());
        assertEquals(10_000, g.esFootprintStrikeMaxRefusedIdentities()); assertEquals("ES.v.0", g.esFootprintStrikeSymbol());
    }

    @Test void theControllerOrderIsBindingFlagAuthPermitCursorSnapshotWrite() throws Exception {
        String c = Files.readString(CONTROLLER);
        int m = c.indexOf("public void footprintOutcomes(");
        int flag = c.indexOf("footprintGate(response, \"outcomes\", authorization)", m);
        int permit = c.indexOf("permits.tryAcquire()", m);
        int cursor = c.indexOf("validOutcomeCursor(tf, after)", m);
        int page = c.indexOf(".outcomesPage(", m);
        int write = c.indexOf("writePage(", m);
        assertTrue(m > 0 && flag > m && permit > flag && cursor > permit && page > cursor && write > page);
        int gate = c.indexOf("private boolean footprintGate(");
        String g = c.substring(gate, c.indexOf("\n    }", gate));
        assertTrue(g.indexOf("footprintEnabled()") < g.indexOf("footprintBackfillRequested(route)") && g.indexOf("footprintBackfillRequested(route)") < g.indexOf("footprintAuth.apply(authorization)"),
                "flag → count → auth");
        Matcher lock = Pattern.compile("synchronized \\(lock\\)").matcher(Files.readString(Path.of("src/main/java/app/feedgateway/FootprintViews.java")));
        assertTrue(lock.find(), "the coordinator lock exists");
        assertFalse(c.contains("synchronized"), "the controller never holds the coordinator lock while writing");
    }
}

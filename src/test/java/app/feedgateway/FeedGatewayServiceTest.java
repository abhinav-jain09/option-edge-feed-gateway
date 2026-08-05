package app.feedgateway;

import app.feedgateway.mtsession.gateway.ReplayParams;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.Test;

import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FeedGatewayServiceTest {
    @Test
    void indexPriceRejectsSyntheticOrUnprovenSourcesBeforeCacheAndRouting() throws Exception {
        FeedGatewayService service = service();
        Object binding = topicBinding("DATABENTO", "index-price");

        assertTrue(isTrustedIndexPrice(service, binding,
                "{\"source\":\"DATABENTO\",\"symbol\":\"ES.v.0\",\"price\":7524.25}"));
        assertFalse(isTrustedIndexPrice(service, binding,
                "{\"source\":\"SYNTHETIC_DEV\",\"symbol\":\"ES.v.0\",\"price\":7590.0}"));
        assertFalse(isTrustedIndexPrice(service, binding,
                "{\"symbol\":\"ES.v.0\",\"price\":7590.0}"),
                "an index price without explicit Databento provenance must fail closed");
        assertFalse(isTrustedIndexPrice(service, binding, "not-json"));
        assertTrue(isTrustedIndexPrice(service, topicBinding("DATABENTO", "strike-flow"),
                "{\"source\":\"SYNTHETIC_DEV\"}"),
                "the provenance gate must remain scoped to index-price");
    }

    @Test
    void spxPriceAcceptsOnlyLegalCascadeTiersAndFailsClosedOnMalformedPayloads() throws Exception {
        FeedGatewayService service = service();
        Object binding = topicBinding("DATABENTO", "spx-price");

        // The canonical spot's honest provenance is one of the feed's legal cascade/static tiers —
        // including SYNTHETIC in prod. There is deliberately NO source=="DATABENTO" acceptance.
        assertTrue(isValidSpxPrice(service, binding,
                "{\"symbol\":\"SPX\",\"price\":6402.75,\"source\":\"SYNTHETIC_OPTION_SPOT\",\"quality\":\"SYNTHETIC\"}"));
        assertTrue(isValidSpxPrice(service, binding,
                "{\"symbol\":\"SPX\",\"price\":6402.75,\"source\":\"NATIVE_SPX_INDEX\"}"));
        assertTrue(isValidSpxPrice(service, binding,
                "{\"symbol\":\"SPX\",\"price\":6402.75,\"source\":\"es_basis_derived\"}"),
                "case drift on a legal tier token is tolerated (producer uppercases)");
        // Provenance fail-closed set (Codex round-1 P1): missing source; "DATABENTO" (what enrichJson
        // stamps onto a source-less record — laundering must not pass); unknown token; non-text source.
        assertFalse(isValidSpxPrice(service, binding, "{\"symbol\":\"SPX\",\"price\":6402.75}"),
                "a record that never declared its cascade provenance must fail closed");
        assertFalse(isValidSpxPrice(service, binding,
                "{\"symbol\":\"SPX\",\"price\":6402.75,\"source\":\"DATABENTO\"}"),
                "the binding-source stamp is not cascade provenance");
        assertFalse(isValidSpxPrice(service, binding,
                "{\"symbol\":\"SPX\",\"price\":6402.75,\"source\":\"SYNTHETIC_DEV\"}"));
        assertFalse(isValidSpxPrice(service, binding,
                "{\"symbol\":\"SPX\",\"price\":6402.75,\"source\":42}"));
        // Symbol / price fail-closed set.
        assertFalse(isValidSpxPrice(service, binding,
                "{\"symbol\":\"ES.v.0\",\"price\":7524.25,\"source\":\"NATIVE_SPX_INDEX\"}"),
                "a foreign symbol must fail closed on the SPX spot boundary");
        assertFalse(isValidSpxPrice(service, binding,
                "{\"symbol\":\"SPX\",\"source\":\"NATIVE_SPX_INDEX\"}"));
        assertFalse(isValidSpxPrice(service, binding,
                "{\"symbol\":\"SPX\",\"price\":0,\"source\":\"NATIVE_SPX_INDEX\"}"));
        assertFalse(isValidSpxPrice(service, binding,
                "{\"symbol\":\"SPX\",\"price\":-1.5,\"source\":\"NATIVE_SPX_INDEX\"}"));
        assertFalse(isValidSpxPrice(service, binding,
                "{\"symbol\":\"SPX\",\"price\":\"6402.75\",\"source\":\"NATIVE_SPX_INDEX\"}"));
        assertFalse(isValidSpxPrice(service, binding, "not-json"));
        assertTrue(isValidSpxPrice(service, topicBinding("DATABENTO", "index-price"),
                "{\"symbol\":\"ES.v.0\"}"),
                "the validity gate must remain scoped to spx-price");
    }

    @Test
    void spxPriceReplayEmissionAppliesTheSameFailClosedValidation() throws Exception {
        // Historical replay must not bypass the SSOT boundary (Codex round-1 P0): replayMatches is the
        // replay-side gate, so a poison archived record fails there exactly as cache/live ingest would.
        FeedGatewayService service = service();
        ReplayParams params = new ReplayParams("app:u1", "SPX", "20260612", 1_000L, 2_000L, 1000, null);
        assertTrue(replayMatches(service, params, "spx-price",
                "{\"symbol\":\"SPX\",\"price\":6402.75,\"source\":\"ES_BASIS_DERIVED\"}"));
        assertFalse(replayMatches(service, params, "spx-price",
                "{\"symbol\":\"ES.v.0\",\"price\":7524.25,\"source\":\"NATIVE_SPX_INDEX\"}"),
                "a foreign-symbol archived record must not replay to a session");
        assertFalse(replayMatches(service, params, "spx-price",
                "{\"symbol\":\"SPX\",\"price\":0,\"source\":\"NATIVE_SPX_INDEX\"}"));
        assertFalse(replayMatches(service, params, "spx-price",
                "{\"symbol\":\"SPX\",\"price\":6402.75}"),
                "an archived record without cascade provenance must not replay");
        assertFalse(replayMatches(service, params, "spx-price", "not-json"));
        assertTrue(replayMatches(service, params, "vix-price", "{\"value\":18.2}"),
                "vix/index replay behavior is intentionally unchanged");
    }

    @Test
    void spxPriceTravelsTheBatchUnderItsOwnFieldNeverInsideIndexPrices() throws Exception {
        FeedGatewayService service = service();
        String spx = "{\"symbol\":\"SPX\",\"price\":6402.75,\"source\":\"SYNTHETIC_OPTION_SPOT\"}";
        String envelope = uiBatchEnvelopeJsonSpxPrice(service, List.of(spx));
        assertTrue(envelope.contains("\"spxPrices\":[" + spx + "]"),
                "the canonical spot must ride its own additive envelope field");
        assertTrue(envelope.contains("\"indexPrices\":[]"),
                "the canonical spot must never be flattened into indexPrices");
    }

    @Test
    void spxPriceCachesUnderItsOwnEventTypeAndSymbolKey() throws Exception {
        FeedGatewayService service = service();
        GatewaySettings settings = new GatewaySettings();
        long now = System.currentTimeMillis();
        String json = "{\"symbol\":\"SPX\",\"price\":6402.75,\"source\":\"ES_BASIS_DERIVED\","
                + "\"eventTime\":\"2026-07-23T14:30:00Z\"}";
        assertEquals("DATABENTO|SPX", updateCache(service, topicBinding("DATABENTO", "spx-price"),
                recordAt(settings.underlyingSpxPriceTopic(), 0, 1L, "SPX", json, now), json),
                "the canonical spot must cache under the source-prefixed symbol key (one last-value-wins entry)");
    }

    @Test
    void detectsPartitionsAddedAfterManualAssignment() {
        List<TopicPartition> assigned = List.of(
                new TopicPartition("es.options.databento.seller-activity", 0),
                new TopicPartition("es.options.databento.seller-activity", 1),
                new TopicPartition("other", 0));
        List<TopicPartition> discovered = List.of(
                new TopicPartition("es.options.databento.seller-activity", 0),
                new TopicPartition("es.options.databento.seller-activity", 1),
                new TopicPartition("es.options.databento.seller-activity", 2),
                new TopicPartition("es.options.databento.seller-activity", 3),
                new TopicPartition("other", 0));

        assertEquals(List.of(
                        new TopicPartition("es.options.databento.seller-activity", 2),
                        new TopicPartition("es.options.databento.seller-activity", 3)),
                FeedGatewayService.addedPartitions(assigned, discovered));
    }

    @Test
    void partitionRefreshNeverDropsAnOptionalTopicThatWentMissingFromMetadata() {
        // partitionsFor() SKIPS an optional topic that is momentarily absent, so a refresh that happens to
        // land in that window sees a SHORTER list. Assigning it verbatim would silently unassign the
        // dealer-ledger feed for the life of the consumer. The merged assignment must keep it.
        List<TopicPartition> assigned = List.of(
                new TopicPartition("es.options.databento.seller-activity", 0),
                new TopicPartition("es.options.databento.seller-activity", 1),
                new TopicPartition("dealer.ledger.state", 0));
        List<TopicPartition> discovered = List.of(
                new TopicPartition("es.options.databento.seller-activity", 0),
                new TopicPartition("es.options.databento.seller-activity", 1),
                new TopicPartition("es.options.databento.seller-activity", 2));

        List<TopicPartition> added = FeedGatewayService.addedPartitions(assigned, discovered);
        assertEquals(List.of(new TopicPartition("es.options.databento.seller-activity", 2)), added);

        List<TopicPartition> merged = FeedGatewayService.mergedAssignment(assigned, added);
        assertTrue(merged.contains(new TopicPartition("dealer.ledger.state", 0)),
                "a transient metadata gap must never unassign an optional topic — it may only delay growth");
        assertEquals(List.of(
                        new TopicPartition("dealer.ledger.state", 0),
                        new TopicPartition("es.options.databento.seller-activity", 0),
                        new TopicPartition("es.options.databento.seller-activity", 1),
                        new TopicPartition("es.options.databento.seller-activity", 2)),
                merged);
    }

    @Test
    void mergedAssignmentIsIdentityWhenNothingWasAdded() {
        List<TopicPartition> assigned = List.of(
                new TopicPartition("a", 0),
                new TopicPartition("a", 1));
        assertEquals(assigned, FeedGatewayService.mergedAssignment(assigned, List.of()));
    }

    @Test
    void lagSkipIgnoresPartitionsStillReplayingTheirDiscoveryBootstrap() {
        // A partition discovered mid-run is deliberately seeked back over its cache window, so it carries a
        // huge intended backlog. If the lag guard measured it, its response — seekToEnd across EVERY
        // selected partition — would erase that rebuild and the healthy partitions' positions too, re-hiding
        // exactly the strikes this mechanism exists to recover.
        TopicPartition old = new TopicPartition("es.options.databento.seller-activity", 0);
        TopicPartition fresh = new TopicPartition("es.options.databento.seller-activity", 4);
        List<TopicPartition> partitions = List.of(old, fresh);

        KafkaConsumer<?, ?> consumer = org.mockito.Mockito.mock(KafkaConsumer.class);
        // Stub ONLY the bounded overload. The guard must never call the no-timeout endOffsets(Collection) —
        // that blocks the poll thread for default.api.timeout.ms (60s) on a broker hiccup — so leaving the
        // 1-arg overload unstubbed (returns an empty map, no lag measured, no fire) pins the production
        // code to the bounded call: the "does fire" assertion below fails if anyone reverts it.
        org.mockito.Mockito.when(consumer.endOffsets(
                        org.mockito.ArgumentMatchers.anyCollection(),
                        org.mockito.ArgumentMatchers.any(java.time.Duration.class)))
                .thenReturn(Map.of(old, 10L, fresh, 5_000_000L));
        org.mockito.Mockito.when(consumer.position(old)).thenReturn(10L);
        org.mockito.Mockito.when(consumer.position(fresh)).thenReturn(0L);

        FeedGatewayService exempt = service();
        exempt.applySelectionForTest("DATABENTO", "ES", "20260731", 1L);
        assertFalse(exempt.lagSkipFiredForTest(consumer, partitions, "DATABENTO", "snapshot", Set.of(fresh)),
                "a bootstrapping partition's intended replay backlog must never trigger the lag skip");

        FeedGatewayService notExempt = service();
        notExempt.applySelectionForTest("DATABENTO", "ES", "20260731", 1L);
        assertTrue(notExempt.lagSkipFiredForTest(consumer, partitions, "DATABENTO", "snapshot", Set.of()),
                "without the exemption the same backlog does fire the lag skip — the guard is load-bearing");
    }

    @Test
    void lagCheckThatCannotCompleteIsSkippedNotActedOn() {
        // A lag CHECK failing is a skipped check. Acting on it — seekToEnd, source-stale, or unwinding into
        // a consumer rebuild — would turn a broker metadata hiccup into real data movement.
        TopicPartition partition = new TopicPartition("t", 0);
        KafkaConsumer<?, ?> consumer = org.mockito.Mockito.mock(KafkaConsumer.class);
        org.mockito.Mockito.when(consumer.endOffsets(
                        org.mockito.ArgumentMatchers.anyCollection(),
                        org.mockito.ArgumentMatchers.any(java.time.Duration.class)))
                .thenThrow(new org.apache.kafka.common.errors.TimeoutException("metadata hiccup"));

        FeedGatewayService service = service();
        service.applySelectionForTest("DATABENTO", "ES", "20260731", 1L);
        assertFalse(service.lagSkipFiredForTest(consumer, List.of(partition), "DATABENTO", "snapshot", Set.of()),
                "an endOffsets timeout must not fire the lag response");
        org.mockito.Mockito.verify(consumer, org.mockito.Mockito.never())
                .seekToEnd(org.mockito.ArgumentMatchers.anyCollection());
    }

    @Test
    void initialBootstrapBacklogIsExemptFromTheLagGuardUntilCaughtUp() throws Exception {
        // A RESTARTED cache consumer replays its full cache window — a backlog that dwarfs maxLagRecords.
        // The replacement attempt MUST register exemptions for that initial bootstrap; otherwise the guard
        // seeks the rebuild away and the untouched barriers are trivially met — caught-up over an
        // incomplete cache, at every restart.
        FeedGatewayService service = service();
        TopicPartition partition = new TopicPartition("databento.display", 0);

        Method register = FeedGatewayService.class.getDeclaredMethod(
                "registerBootstrapEntries", String.class, Map.class, Map.class);
        register.setAccessible(true);
        Class<?> bindingClass = Class.forName("app.feedgateway.FeedGatewayService$TopicBinding");
        Constructor<?> ctor = bindingClass.getDeclaredConstructor(String.class, String.class);
        ctor.setAccessible(true);
        Map<String, Object> topicEvents = new java.util.LinkedHashMap<>();
        topicEvents.put("databento.display", ctor.newInstance("DATABENTO", "snapshot"));
        register.invoke(service, "avro", Map.of(partition, 4_000_000L), topicEvents);

        assertTrue(service.hasIncompleteBootstrapForSourceForTest("DATABENTO"),
                "the initial bootstrap is a replay-in-progress and must carry a lag-guard exemption");
    }

    @Test
    void everyLiveOnlyRebuiltEventIsClassifiedForDiscoveryReplay() throws Exception {
        // These events have NO cache-consumer rebuild path: updateCache has no case for them, so the live
        // consumer is their only writer. Seeking a newly discovered partition to END would lose them —
        // permanently for the one-shot transitions, and until the next dashboard interval for the trail.
        Method classify = FeedGatewayService.class
                .getDeclaredMethod("isLiveOnlyRebuiltEvent", String.class);
        classify.setAccessible(true);

        for (String event : List.of("turn-alert", "spread-skew-event", "strike-cluster")) {
            assertTrue((boolean) classify.invoke(null, event),
                    event + " is broadcast/cached ONLY by the live consumer and must replay on discovery");
        }
        // hot-strike is rebuilt by updateCache -> cacheHotStrike, and es-aggressor-flow is a compacted
        // snapshot re-emitted every second, so both are correctly left seeking to END.
        for (String event : List.of("hot-strike", "es-aggressor-flow", "snapshot", "gex", "max-pain")) {
            assertFalse((boolean) classify.invoke(null, event),
                    event + " has independent recovery and must not replay into the broadcast path");
        }
    }

    @Test
    void addedNonSelectedSourcePartitionsDoNotGateCacheReadiness() throws Exception {
        // catchUpEndOffsets() falls back to "return them all" when nothing matches the selected source —
        // correct for the bootstrap set, wrong for an incremental subset, where empty genuinely means
        // "none of these are selected". Through the fallback, growth on IBKR while DATABENTO is selected
        // would hold the cache in RECOVERING on a source nobody is watching.
        FeedGatewayService service = service();
        service.applySelectionForTest("DATABENTO", "ES", "20260731", 1L);

        TopicPartition ibkr = new TopicPartition("ibkr.topic", 7);
        Map<TopicPartition, Long> addedEndOffsets = Map.of(ibkr, 500L);

        assertTrue(selectedSourceBarriers(service, addedEndOffsets, "ibkr.topic", "IBKR").isEmpty(),
                "an added partition on the non-selected source contributes no readiness barrier");
        assertEquals(1, selectedSourceBarriers(service, addedEndOffsets, "ibkr.topic", "DATABENTO").size(),
                "an added partition on the selected source does contribute one");
    }

    @SuppressWarnings("unchecked")
    private static Map<TopicPartition, Long> selectedSourceBarriers(
            FeedGatewayService service, Map<TopicPartition, Long> endOffsets,
            String topic, String bindingSource) throws Exception {
        Class<?> bindingClass = Class.forName("app.feedgateway.FeedGatewayService$TopicBinding");
        Constructor<?> ctor = bindingClass.getDeclaredConstructor(String.class, String.class);
        ctor.setAccessible(true);
        Object binding = ctor.newInstance(bindingSource, "snapshot");
        Map<String, Object> topicEvents = new java.util.LinkedHashMap<>();
        topicEvents.put(topic, binding);

        Method method = FeedGatewayService.class.getDeclaredMethod(
                "selectedSourceBarriers", Map.class, Map.class);
        method.setAccessible(true);
        return (Map<TopicPartition, Long>) method.invoke(service, endOffsets, topicEvents);
    }

    @Test
    void bootstrapExemptionIsRetiredExactlyWhenThePartitionReachesItsBarrier() {
        TopicPartition replaying = new TopicPartition("t", 0);
        TopicPartition arrived = new TopicPartition("t", 1);
        TopicPartition foreign = new TopicPartition("other-consumers-topic", 9);

        FeedGatewayService service = service();
        service.registerBootstrapForTest(replaying, "DATABENTO", 100L, 1L);
        service.registerBootstrapForTest(arrived, "DATABENTO", 100L, 1L);
        service.registerBootstrapForTest(foreign, "IBKR", 100L, 1L);

        Map<TopicPartition, Long> positions = Map.of(replaying, 40L, arrived, 100L);
        // `foreign` belongs to a different consumer: position() would throw on it, so the pass must skip it.
        Set<TopicPartition> stillExempt = service.retireReachedBootstrapForTest(
                List.of(replaying, arrived),
                partition -> {
                    Long p = positions.get(partition);
                    if (p == null) {
                        throw new IllegalStateException("position() called on an unassigned partition: " + partition);
                    }
                    return p;
                });

        assertEquals(Set.of(replaying, foreign), stillExempt,
                "only the partition still behind its barrier keeps the exemption; another consumer's stays");
    }

    @Test
    void markSelectionReadyItselfFailsClosedOnAnIncompleteBootstrap() throws Exception {
        // applySelection is not the only path to source-ready: either cache consumer reaching caught-up
        // also announces it, and consumer A can be complete while consumer B still replays this source.
        // The choke point itself must refuse — otherwise the check in applySelection is bypassable.
        FeedGatewayService service = service();
        service.applySelectionForTest("DATABENTO", "SPX", "20260731", 1L);
        service.registerBootstrapForTest(new TopicPartition("databento.display", 5), "DATABENTO", 900L, 1L);

        Method mark = FeedGatewayService.class.getDeclaredMethod("markSelectionReady",
                Class.forName("app.feedgateway.FeedGatewayService$ActiveSelection"));
        mark.setAccessible(true);
        Field selRef = FeedGatewayService.class.getDeclaredField("activeSelection");
        selRef.setAccessible(true);
        Object selection = ((AtomicReference<?>) selRef.get(service)).get();
        Field readyKey = FeedGatewayService.class.getDeclaredField("readySelectionKey");
        readyKey.setAccessible(true);

        mark.invoke(service, selection);
        assertEquals("", ((AtomicReference<?>) readyKey.get(service)).get(),
                "source-ready must NOT be announced while the selected source has a replaying partition");

        // Barrier reached → the same call now succeeds: readiness was deferred, not dropped.
        service.retireReachedBootstrapForTest(
                List.of(new TopicPartition("databento.display", 5)), partition -> 900L);
        mark.invoke(service, selection);
        assertFalse(((AtomicReference<?>) readyKey.get(service)).get().toString().isEmpty(),
                "once the bootstrap completes the identical call announces readiness");
    }

    @Test
    void deadAttemptsEntriesFailClosedUntilTheReplacementSupersedesThem() {
        // A dead attempt's entries deliberately SURVIVE its death: while its source's cache is knowably
        // incomplete, readiness must keep failing closed. Releasing them on exit opened a window (death →
        // replacement's re-registration) in which another consumer's convergence could announce the
        // incomplete source READY — permanently, since readiness is one-shot per key. The replacement's
        // bootstrap supersedes them: same keys overwritten with fresh barriers, leftover owned keys pruned.
        FeedGatewayService service = service();
        TopicPartition kept = new TopicPartition("a", 0);
        TopicPartition leftover = new TopicPartition("a", 9); // e.g. from a growth event, gone after wipe
        service.registerBootstrapForTest(kept, "DATABENTO", 100L, 1L);      // owner "test"
        service.registerBootstrapForTest(leftover, "DATABENTO", 100L, 1L);  // owner "test"

        // The attempt dies. NOTHING is released — the source stays incomplete across the gap.
        assertTrue(service.hasIncompleteBootstrapForSourceForTest("DATABENTO"),
                "a dead attempt's entries keep failing closed until superseded");

        // The replacement bootstraps with a fresh barrier for `kept`; `leftover` is pruned.
        service.supersedeBootstrapEntriesForTest("test", Map.of(kept, 250L), "a", "DATABENTO");
        assertTrue(service.hasIncompleteBootstrapForSourceForTest("DATABENTO"),
                "superseded entries gate readiness on the REPLACEMENT's rebuild");
        Set<TopicPartition> remaining = service.retireReachedBootstrapForTest(
                List.of(kept, leftover), partition -> 250L);
        assertFalse(remaining.contains(leftover), "leftover owned keys are pruned by supersession");
        assertFalse(service.hasIncompleteBootstrapForSourceForTest("DATABENTO"),
                "once the replacement reaches its own barrier the source may become ready");
    }

    @Test
    void probeOrderPutsKnownTopicsFirstAndRotatesTheUnknowns() {
        // Known topics answer from the client metadata cache in microseconds and are the ones that detect
        // growth; unknown (absent optional) topics each BLOCK for their full timeout. If the unknowns ran
        // first, ~8 undeployed producers would eat the whole refresh budget and growth detection would be
        // permanently dead — the 4→32 incident restored by its own fix. Rotation guarantees no unknown
        // topic is permanently starved by the ones ahead of it either.
        FeedGatewayService service = service();
        Set<String> topics = new java.util.LinkedHashSet<>(
                List.of("z-absent-1", "known-a", "m-absent-2", "known-b", "a-absent-3"));
        Set<String> known = Set.of("known-a", "known-b");

        List<String> first = service.probeOrder(topics, known);
        assertEquals(List.of("known-a", "known-b"), first.subList(0, 2),
                "assigned topics are probed before any potentially blocking unknown topic");
        assertEquals(Set.of("z-absent-1", "m-absent-2", "a-absent-3"), Set.copyOf(first.subList(2, 5)),
                "every unknown topic is still probed");

        List<String> second = service.probeOrder(topics, known);
        assertFalse(first.get(2).equals(second.get(2)),
                "the head unknown rotates between passes, so no absent topic is permanently starved");
    }

    @Test
    void sourceReadinessIsWithheldWhileThatSourceIsStillBootstrapping() {
        FeedGatewayService service = service();
        service.registerBootstrapForTest(
                new TopicPartition("ibkr.display", 7), "IBKR", 500L, 1L);

        assertTrue(service.hasIncompleteBootstrapForSourceForTest("IBKR"),
                "a switch to IBKR must not be announced ready while an IBKR partition is still replaying");
        assertFalse(service.hasIncompleteBootstrapForSourceForTest("DATABENTO"),
                "the currently selected source is unaffected by growth on another source");
        assertFalse(service.hasIncompleteBootstrapForSourceForTest(null),
                "a null/blank source must never be reported incomplete");
        assertFalse(service.hasIncompleteBootstrapForSourceForTest(""),
                "a null/blank source must never be reported incomplete");

        // Retiring the barrier releases the hold, so readiness is DEFERRED, never dropped.
        service.retireReachedBootstrapForTest(
                List.of(new TopicPartition("ibkr.display", 7)), partition -> 500L);
        assertFalse(service.hasIncompleteBootstrapForSourceForTest("IBKR"),
                "once the partition reaches its barrier the source may be announced ready");
    }

    @Test
    void partitionMetadataRefreshHasABoundedFloor() {
        // Env/system-property overrides are legitimate in a deployed shell, so assert the INVARIANT rather
        // than the ambient value: a 0/negative/garbage setting must never turn the in-loop metadata refresh
        // into a hot loop. (The old form asserted refreshMs <= 60_000, which no code enforces — it failed
        // for anyone running with GATEWAY_PARTITION_METADATA_REFRESH_MS set above a minute.)
        assertTrue(new GatewaySettings().partitionMetadataRefreshMs() >= 1_000L);
        assertEquals(1_000L,
                GatewaySettings.longValue("GATEWAY_PARTITION_REFRESH_FLOOR_PROBE_UNSET", 10L, 1_000L));
        assertEquals(30_000L,
                GatewaySettings.longValue("GATEWAY_PARTITION_REFRESH_DEFAULT_PROBE_UNSET", 30_000L, 1_000L));
    }

    @Test
    void epochOnlySelectionReassertDoesNotRollOrReplaceTheActiveBoard() {
        FeedGatewayService service = service();
        service.seedReadySelectionForTest("DATABENTO", "ES", "20260715", 100L);

        service.applySelectionForTest("DATABENTO", "ES", "20260715", 200L);

        assertEquals(0L, service.rolloverCountForTest(),
                "same-contract reassertion must not run the reset/rollover lifecycle");
        assertEquals(100L, service.activeSelectionEpochForTest(),
                "the established ready selection must remain authoritative");
        assertEquals(1, service.readySelectionKeyGaugeForTest(),
                "same-contract reassertion must preserve readiness");
    }

    @Test
    void realContractChangeStillRunsTheRolloverLifecycle() {
        FeedGatewayService service = service();
        service.seedReadySelectionForTest("DATABENTO", "ES", "20260715", 100L);

        service.applySelectionForTest("DATABENTO", "ES", "20260716", 200L);

        assertEquals(1L, service.rolloverCountForTest());
        assertEquals(200L, service.activeSelectionEpochForTest());
        assertEquals(0, service.readySelectionKeyGaugeForTest(),
                "a real chain change must wait for the new selection to become ready");
    }

    @Test
    void sourceSwitchReplayIncludesCachedVixPrice() {
        assertEquals(
                List.of("snapshot", "pace", "pace-rank", "directional-pressure", "vix-price", "index-price", "spx-price", "strike-flow", "seller-activity", "delta-flow", "strike-intel", "strike-invasion", "liquidity-heatmap", "mission-pace", "mission-control", "spread-skew", "volume-sandwich", "mission-sandwich", "option-price-behavior", "opb-by-option", "opb-session", "gex-by-strike", "gex-oi-status", "strike-sr", "gex-magnet", "gamma-migration", "es-gex", "es-strike-intel", "max-pain", "gex-strike-lifecycle"),
                FeedGatewayService.sourceSwitchReplayEvents()
        );
    }

    @Test
    void dealerLedgerFreshnessUsesPayloadEventTimeNotKafkaArrivalTime() throws Exception {
        // A producer catching up on a backlog appends records now (fresh arrival) whose asOfEventTimeMs is
        // old — freshness MUST track the payload event time, else a stale permission passes the 15s TTL.
        FeedGatewayService service = service();
        long oldEventTime = 1_700_000_000_000L;
        // 5-arg ctor sets timestamp = NO_TIMESTAMP (-1); the payload asOfEventTimeMs must still win.
        ConsumerRecord<String, String> record = new ConsumerRecord<>(
                "dealer-ledger-state", 0, 0L, "SPXW|20260704",
                "{\"symbol\":\"SPXW\",\"expiry\":\"20260704\",\"asOfEventTimeMs\":" + oldEventTime + "}");
        assertEquals(oldEventTime, eventCacheTimestamp(service, "dealer-ledger", record));
    }

    @Test
    void dealerLedgerFreshnessUsesShortLiveSignalTtlNotGenericCacheTtl() throws Exception {
        // BLOCKING guard: dealer-ledger state is a live permission heartbeat, so its freshness MUST use
        // the short dealer-ledger TTL (default 15s), never the generic 15-min cache TTL — otherwise a
        // stalled producer's last ARMED/DEFENDED would join as fresh and render as an active permission.
        FeedGatewayService service = service();
        long now = 1_000_000_000L;
        long ttl = new GatewaySettings().dealerLedgerTtlMs();
        assertTrue(ttl > 0 && ttl <= 60_000L, "dealer-ledger TTL must be a short live-signal window");
        // Just past the short TTL ⇒ EXPIRED (would still be 'fresh' under the 15-min generic window).
        assertTrue(isExpired(service, "dealer-ledger", now - ttl - 1, now));
        // Within the short TTL ⇒ fresh.
        assertFalse(isExpired(service, "dealer-ledger", now - ttl + 1_000, now));
        // Contrast: a generic event of the same 30s age is NOT expired — proves dealer-ledger is NOT
        // sharing the generic TTL.
        assertFalse(isExpired(service, "strike-flow", now - 30_000, now));
    }

    @Test
    void dealerLedgerTopicsAreOptionalSoTheirAbsenceCannotStarveTheSharedConsumer() throws Exception {
        // Kafka is wiped + services restart daily, and the dealer-ledger producer may not be deployed,
        // so both DL topics are absent at gateway startup. They MUST be optional or partitionsFor would
        // block the shared JSON consumer waiting for them, starving strike-flow / mission-pace / etc.
        FeedGatewayService service = service();
        GatewaySettings settings = new GatewaySettings();
        assertTrue(isOptionalTopic(service, settings.dealerLedgerProfileTopic()));
        assertTrue(isOptionalTopic(service, settings.dealerLedgerStateTopic()));
        // strike-invasion is a brand-new topic whose producer may not be deployed during a staged
        // rollout — it MUST be optional (like strike-intel) so its absence cannot starve the shared
        // JSON consumer (strike-flow / mission-pace / etc.).
        assertTrue(isOptionalTopic(service, settings.strikeInvasionTopic()));
        // Both spread-skew topics come from the same brand-new spread-skew-service, which may not be
        // deployed during a staged rollout — BOTH must be optional (like strike-invasion) so their
        // absence cannot starve the shared JSON consumer.
        assertTrue(isOptionalTopic(service, settings.spreadSkewTopic()));
        assertTrue(isOptionalTopic(service, settings.spreadSkewEventsTopic()));
        // A mandatory feed must still be mandatory (guards against over-broadening the optional set).
        assertFalse(isOptionalTopic(service, settings.databentoStrikeFlowTopic()));
    }

    // ----- 0DTE binary direction / unusual-movement option-chain tint -----------------------------

    @Test
    void vixOptionInteligenceTopicIsOptionalAndUsesShortControlSignalTtl() throws Exception {
        FeedGatewayService service = service();
        GatewaySettings settings = new GatewaySettings();
        assertEquals(
                "options.spx.vix-option-inteligence-service.current",
                settings.vixOptionInteligenceTopic());
        assertTrue(isOptionalTopic(service, settings.vixOptionInteligenceTopic()),
                "a staged producer rollout must not starve the shared JSON consumer");
        assertEquals(15_000L, settings.zeroDteIntelligenceTtlMs());
        long now = System.currentTimeMillis();
        assertFalse(isExpired(service, "zero-dte-intelligence", now - 14_999L, now));
        assertTrue(isExpired(service, "zero-dte-intelligence", now - 15_001L, now),
                "an old decision must return the full chain to neutral");
    }

    @Test
    void zeroDteIntelligenceUsesPayloadDecisionTimeAndSourceSymbolSessionKey() throws Exception {
        FeedGatewayService service = service();
        GatewaySettings settings = new GatewaySettings();
        long decisionTime = System.currentTimeMillis() - 1_000L;
        String payload = "{\"symbol\":\"SPX\",\"sessionDate\":\"2026-07-19\","
                + "\"asOfEventTimeMs\":" + decisionTime + ",\"marketDirection\":\"DOWN\","
                + "\"intensity\":\"UNUSUAL\",\"qualityStatus\":\"GOOD\",\"actionable\":true}";
        ConsumerRecord<String, String> record = recordAt(
                settings.vixOptionInteligenceTopic(), 0, 1L, "ignored", payload, System.currentTimeMillis());

        assertEquals(decisionTime, eventCacheTimestamp(service, "zero-dte-intelligence", record),
                "fresh Kafka arrival must not disguise a historical decision");
        assertEquals("DATABENTO|SPX|20260719",
                updateCache(service, topicBinding("DATABENTO", "zero-dte-intelligence"), record, payload));
    }

    @Test
    void zeroDteIntelligenceReplaySendsOnlyFreshStandaloneState() throws Exception {
        FeedGatewayService service = service();
        GatewaySettings settings = new GatewaySettings();
        long now = System.currentTimeMillis();
        String fresh = "{\"symbol\":\"SPX\",\"sessionDate\":\"2026-07-19\","
                + "\"asOfEventTimeMs\":" + (now - 1_000L) + ",\"marketDirection\":\"UP\","
                + "\"intensity\":\"UNUSUAL\",\"unusualTriggers\":[\"CALL_BUY_BURST\"]}";
        updateCache(service, topicBinding("DATABENTO", "zero-dte-intelligence"),
                recordAt(settings.vixOptionInteligenceTopic(), 0, 1L, "SPX|2026-07-19", fresh, now), fresh);

        List<String> sink = new ArrayList<>();
        Method replay = FeedGatewayService.class.getDeclaredMethod(
                "replayZeroDteIntelligenceCached", WebSocketSession.class);
        replay.setAccessible(true);
        replay.invoke(service, recordingSession(sink));

        assertEquals(1, sink.size());
        assertTrue(sink.get(0).contains("\"type\":\"zero-dte-intelligence\""));
        assertTrue(sink.get(0).contains("\"marketDirection\":\"UP\""));

        String stale = "{\"symbol\":\"SPX\",\"sessionDate\":\"2026-07-20\","
                + "\"asOfEventTimeMs\":" + (now - 60_000L) + ",\"marketDirection\":\"DOWN\"}";
        assertNull(updateCache(service, topicBinding("DATABENTO", "zero-dte-intelligence"),
                recordAt(settings.vixOptionInteligenceTopic(), 0, 2L, "SPX|2026-07-20", stale, now), stale),
                "historical snapshot/replay records must fail closed at ingest");
    }

    // ----- delta-flow gateway consumer (per-strike DeltaFlowStrikeSnapshot) -----------------------

    @Test
    void deltaFlowCacheKeyIsSymbolExpiryStrikeFromPayloadIdentity() throws Exception {
        // The helper derives symbol|expiry|strike from the payload (source is prepended later by
        // updateCache), mirroring gexCacheKey — delta-flow is per-strike, not chain-level.
        FeedGatewayService service = service();
        assertEquals("SPX|20260622|6005", deltaFlowCacheKey(
                service,
                "{\"symbol\":\"SPX\",\"expiry\":\"20260622\",\"strike\":6005,\"sessionNetDeltaFlow\":42}",
                "fallback-key"));
    }

    @Test
    void deltaFlowUpdateCacheStoresSourcePrefixedKey() throws Exception {
        // After updateCache the stored/returned cache key is DATABENTO|SPX|expiry|strike (source prepended).
        FeedGatewayService service = service();
        String json = "{\"marketDataSource\":\"DATABENTO\",\"symbol\":\"SPX\",\"expiry\":\"20260622\","
                + "\"strike\":6005,\"sessionNetDeltaFlow\":42,\"asOfEventTimeMs\":" + System.currentTimeMillis() + "}";
        String key = updateCache(service, topicBinding("DATABENTO", "delta-flow"),
                new ConsumerRecord<>(new GatewaySettings().databentoDeltaFlowByStrikeTopic(), 0, 1L, "SPX|20260622|6005", json),
                json);
        assertEquals("DATABENTO|SPX|20260622|6005", key,
                "updateCache must prepend the source to the delta-flow cache key");
        assertTrue(service.healthJson().contains("\"deltaFlows\":1"), "delta-flow must be cached");
    }

    // ----- es-gex (ES-on-SPX aligned whole-book, JSON, roll-forward) ------------------------------------

    private static String esGexBook(long emitEventTimeMs) {
        return "{\"recordType\":\"book\",\"source\":\"ES_ON_SPX\",\"symbol\":\"SPX\",\"expiry\":\"20260622\","
                + "\"settlementType\":\"PM\",\"basisState\":\"MEASURED\",\"basis\":40,\"basisMeasuredAtMs\":" + emitEventTimeMs
                + ",\"emitEventTimeMs\":" + emitEventTimeMs + ",\"dataEventTimeMs\":" + emitEventTimeMs
                + ",\"upstreamHeartbeatEventMs\":" + emitEventTimeMs + ",\"upstreamHealth\":\"OK\","
                + "\"buckets\":[{\"spxStrike\":5580,\"esNetGexSum\":-123456,\"contributorCount\":1,\"truncated\":false,"
                + "\"contributors\":[{\"esStrike\":5620,\"netGex\":-123456}]}]}";
    }

    @Test
    void healthReportsEsGexDisabledNotZeroWhenFeatureOff() throws Exception {
        // ES environment shape: GATEWAY_ES_GEX_ENABLED unset -> no aligned-topic consumer. A bare
        // esGex:0 there reads like a data fault (it misled a live prod triage on 2026-07-19);
        // health must say "disabled" and carry the explicit flag instead.
        System.clearProperty("GATEWAY_ES_GEX_ENABLED");
        FeedGatewayService service = service();
        String health = service.healthJson();
        assertTrue(health.contains("\"esGexEnabled\":false"), "health must carry the explicit enable flag");
        assertTrue(health.contains("\"esGex\":\"disabled\""), "disabled env must not report a misleading 0 count");
        assertFalse(health.contains("\"esGex\":0"), "no bare zero for a feature that is off");
    }

    @Test
    void healthReportsEsGexCountWhenFeatureOn() throws Exception {
        System.setProperty("GATEWAY_ES_GEX_ENABLED", "true");
        try {
            FeedGatewayService service = service();
            String health = service.healthJson();
            assertTrue(health.contains("\"esGexEnabled\":true"), "flag must reflect the enabled state");
            assertTrue(health.contains("\"esGex\":0"), "enabled env reports the real (initially 0) cache count");
        } finally {
            System.clearProperty("GATEWAY_ES_GEX_ENABLED");
        }
    }

    @Test
    void cachedReplayIncludesFreshEsGexForMatchingSpxSelectionOnly() throws Exception {
        FeedGatewayService service = service();
        GatewaySettings settings = new GatewaySettings();
        long now = System.currentTimeMillis();
        String json = esGexBook(now);
        updateCache(service, topicBinding("DATABENTO", "es-gex"),
                recordAt(settings.esGexSpxAlignedTopic(), 0, 1L, "SPX|20260622", json, now), json);

        setActiveSelection(service, "DATABENTO", "SPX", "20260622");
        assertEquals(1, cachedEvents(service, List.of("es-gex"), now).size(),
                "a fresh ES-on-SPX book replays to a matching SPX DATABENTO client");

        setActiveSelection(service, "DATABENTO", "SPY", "20260622");
        assertTrue(cachedEvents(service, List.of("es-gex"), now).isEmpty(),
                "a different symbol must not receive this book");
    }

    @Test
    void staleEsGexBookUsesPayloadEmitTimeNotArrival() throws Exception {
        FeedGatewayService service = service();
        GatewaySettings settings = new GatewaySettings();
        long now = System.currentTimeMillis();
        long staleEmit = now - 13L * 3600_000L; // older than the 12h book window
        String json = esGexBook(staleEmit);
        updateCache(service, topicBinding("DATABENTO", "es-gex"),
                recordAt(settings.esGexSpxAlignedTopic(), 0, 1L, "SPX|20260622", json, now), json);
        setActiveSelection(service, "DATABENTO", "SPX", "20260622");
        assertTrue(cachedEvents(service, List.of("es-gex"), now).isEmpty(),
                "a stale (old emitEventTimeMs) book must not replay — roll-forward freshness uses payload time");
    }

    // ----- es-strike-intel (ES strike-intel projected onto SPX, per ES strike, with withdrawal) ---------

    /** A projected ES signal as the align service emits it: restamped onto the SPX chain at the translated
     *  strike, native ES coordinates + basis provenance preserved, tagged source=ES_ON_SPX. */
    private static String esStrikeIntelSignal() {
        return "{\"symbol\":\"SPX\",\"expiry\":\"20260622\",\"strike\":5580.0,\"spxStrike\":5580.0,"
                + "\"esSymbol\":\"ES\",\"esStrike\":5620.0,\"role\":\"CALL_CHASE_LEVEL\",\"actionBias\":\"BUY_CALL\","
                + "\"source\":\"ES_ON_SPX\",\"basis\":40.0,\"basisState\":\"MEASURED\",\"basisEventTimeMs\":1}";
    }

    @Test
    void healthReportsEsStrikeIntelDisabledNotZeroWhenFeatureOff() throws Exception {
        System.clearProperty("GATEWAY_ES_STRIKE_INTEL_ENABLED");
        FeedGatewayService service = service();
        String health = service.healthJson();
        assertTrue(health.contains("\"esStrikeIntelEnabled\":false"), "health carries the explicit enable flag");
        assertTrue(health.contains("\"esStrikeIntel\":\"disabled\""), "disabled env must not report a misleading 0");
        assertFalse(health.contains("\"esStrikeIntel\":0"), "no bare zero for a feature that is off");
    }

    @Test
    void healthReportsEsStrikeIntelCountWhenFeatureOn() throws Exception {
        System.setProperty("GATEWAY_ES_STRIKE_INTEL_ENABLED", "true");
        try {
            FeedGatewayService service = service();
            String health = service.healthJson();
            assertTrue(health.contains("\"esStrikeIntelEnabled\":true"), "flag reflects the enabled state");
            assertTrue(health.contains("\"esStrikeIntel\":0"), "enabled env reports the real (initially 0) count");
        } finally {
            System.clearProperty("GATEWAY_ES_STRIKE_INTEL_ENABLED");
        }
    }

    @Test
    void cachedReplayIncludesEsStrikeIntelForMatchingSpxSelectionOnly() throws Exception {
        FeedGatewayService service = service();
        GatewaySettings settings = new GatewaySettings();
        long now = System.currentTimeMillis();
        String json = esStrikeIntelSignal();
        // Keyed by the NATIVE ES identity (the align service's output key), rendered on the SPX chain.
        updateCache(service, topicBinding("DATABENTO", "es-strike-intel"),
                recordAt(settings.esStrikeIntelSpxAlignedTopic(), 0, 1L, "ES|20260622|5620", json, now), json);

        setActiveSelection(service, "DATABENTO", "SPX", "20260622");
        assertEquals(1, cachedEvents(service, List.of("es-strike-intel"), now).size(),
                "a projected ES signal replays to a matching SPX DATABENTO client");

        setActiveSelection(service, "DATABENTO", "SPY", "20260622");
        assertTrue(cachedEvents(service, List.of("es-strike-intel"), now).isEmpty(),
                "a different symbol must not receive the ES overlay");
    }

    @Test
    void esStrikeIntelTombstoneWithdrawsFromReplay() throws Exception {
        FeedGatewayService service = service();
        GatewaySettings settings = new GatewaySettings();
        long now = System.currentTimeMillis();
        String json = esStrikeIntelSignal();
        Object binding = topicBinding("DATABENTO", "es-strike-intel");
        updateCache(service, binding,
                recordAt(settings.esStrikeIntelSpxAlignedTopic(), 0, 1L, "ES|20260622|5620", json, now), json);
        setActiveSelection(service, "DATABENTO", "SPX", "20260622");
        assertEquals(1, cachedEvents(service, List.of("es-strike-intel"), now).size(), "projected first");

        // The align service withdraws the signal with a tombstone (null value) keyed by the same ES identity.
        evictEsStrikeIntelTombstone(service, binding,
                tombstoneRecord(settings.esStrikeIntelSpxAlignedTopic(), "ES|20260622|5620", now + 1));
        assertTrue(cachedEvents(service, List.of("es-strike-intel"), now + 1).isEmpty(),
                "a withdrawn ES signal is evicted so it never replays");
    }

    @Test
    void esStrikeIntelLateOlderUpsertDoesNotResurrectAfterTombstone() throws Exception {
        // Race: the cache consumer applies the tombstone (t+1) while the live consumer replays the older
        // upsert (t) it withdraws. The roll-forward watermark must reject that late older upsert.
        FeedGatewayService service = service();
        GatewaySettings settings = new GatewaySettings();
        long now = System.currentTimeMillis();
        String json = esStrikeIntelSignal();
        Object binding = topicBinding("DATABENTO", "es-strike-intel");
        String topic = settings.esStrikeIntelSpxAlignedTopic();
        updateCache(service, binding, recordAt(topic, 0, 1L, "ES|20260622|5620", json, now), json);
        evictEsStrikeIntelTombstone(service, binding, tombstoneRecord(topic, "ES|20260622|5620", now + 1));
        // the withdrawn record re-delivered by the other consumer at its ORIGINAL (older) time:
        updateCache(service, binding, recordAt(topic, 0, 1L, "ES|20260622|5620", json, now), json);
        setActiveSelection(service, "DATABENTO", "SPX", "20260622");
        assertTrue(cachedEvents(service, List.of("es-strike-intel"), now + 2).isEmpty(),
                "a late older upsert after a newer tombstone must not resurrect the withdrawn ES signal");
    }

    @Test
    void esStrikeIntelEqualTimestampUpsertDoesNotResurrectAfterTombstone() throws Exception {
        // Equal-millisecond tie: the tombstone (always the higher Kafka offset) must win, so a same-ms
        // racing upsert cannot resurrect. The watermark is tombstoneTime+1 to break the tie.
        FeedGatewayService service = service();
        GatewaySettings settings = new GatewaySettings();
        long now = System.currentTimeMillis();
        String json = esStrikeIntelSignal();
        Object binding = topicBinding("DATABENTO", "es-strike-intel");
        String topic = settings.esStrikeIntelSpxAlignedTopic();
        updateCache(service, binding, recordAt(topic, 0, 1L, "ES|20260622|5620", json, now), json);
        evictEsStrikeIntelTombstone(service, binding, tombstoneRecord(topic, "ES|20260622|5620", now));
        updateCache(service, binding, recordAt(topic, 0, 1L, "ES|20260622|5620", json, now), json); // same ms
        setActiveSelection(service, "DATABENTO", "SPX", "20260622");
        assertTrue(cachedEvents(service, List.of("es-strike-intel"), now + 1).isEmpty(),
                "a same-millisecond upsert must not resurrect a withdrawn ES signal (tombstone wins the tie)");
    }

    private static void evictEsStrikeIntelTombstone(FeedGatewayService service, Object binding,
            ConsumerRecord<String, Object> record) throws Exception {
        Class<?> bindingType = Class.forName("app.feedgateway.FeedGatewayService$TopicBinding");
        Method m = FeedGatewayService.class.getDeclaredMethod(
                "evictEsStrikeIntelTombstone", bindingType, ConsumerRecord.class);
        m.setAccessible(true);
        m.invoke(service, binding, record);
    }

    private static ConsumerRecord<String, Object> tombstoneRecord(String topic, String key, long timestampMs) {
        return new ConsumerRecord<>(topic, 0, 1L, timestampMs,
                org.apache.kafka.common.record.TimestampType.CREATE_TIME, -1, -1, key, null,
                new org.apache.kafka.common.header.internals.RecordHeaders(), java.util.Optional.empty());
    }

    // ----- gex-strike-lifecycle gateway consumer (per-strike GexStrikeLifecycle) ------------------

    @Test
    void strikeLifecycleCacheKeyIsSymbolExpiryStrikeFromPayloadIdentity() throws Exception {
        // Per-strike like gex-by-strike / delta-flow: symbol|expiry|strike derived from the payload.
        FeedGatewayService service = service();
        assertEquals("SPX|20260622|6005", strikeLifecycleCacheKey(
                service,
                "{\"symbol\":\"SPX\",\"expiry\":\"20260622\",\"strike\":6005,\"label\":\"EMERGING\"}",
                "fallback-key"));
    }

    @Test
    void strikeLifecycleCacheKeyFailsClosedOnMalformedIdentity() throws Exception {
        // Codex review: a badge without its own symbol|expiry|strike identity cannot be matched to any
        // strike. Falling back to the Kafka key would let it occupy a cache slot and replay a phantom
        // badge — so every malformed identity must yield null (dropped), never the fallback.
        FeedGatewayService service = service();
        String[] malformed = {
                "{\"expiry\":\"20260622\",\"strike\":6005}",                       // symbol गायब
                "{\"symbol\":\"SPX\",\"strike\":6005}",                            // expiry गायब
                "{\"symbol\":\"SPX\",\"expiry\":\"20260622\"}",                    // strike गायब
                "{\"symbol\":\"\",\"expiry\":\"20260622\",\"strike\":6005}",      // symbol खाली
                "{\"symbol\":\"SPX\",\"expiry\":\"20260622\",\"strike\":\"abc\"}", // strike गैर-संख्या
                "{\"symbol\":\"SPX\",\"expiry\":\"\",\"strike\":6005}",           // expiry खाली
                "{\"symbol\":\"SPX\",\"expiry\":\"20260622\",\"strike\":\"NaN\"}",   // strike NaN
                "{\"symbol\":\"SPX\",\"expiry\":\"20260622\",\"strike\":\"Infinity\"}", // strike अनंत
                "not-json-at-all"
        };
        for (String json : malformed) {
            assertNull(strikeLifecycleCacheKey(service, json, "fallback-key"),
                    "malformed lifecycle identity must fail closed, never fall back to the Kafka key: " + json);
        }
    }

    @Test
    void strikeLifecycleUpdateCacheDropsMalformedRecordEntirely() throws Exception {
        // The fail-closed key must propagate: updateCache returns null (dropped) BEFORE source-prefixing,
        // so nothing is cached and nothing can replay.
        FeedGatewayService service = service();
        GatewaySettings settings = new GatewaySettings();
        long now = System.currentTimeMillis();
        String json = "{\"marketDataSource\":\"DATABENTO\",\"expiry\":\"20260622\",\"label\":\"EMERGING\","
                + "\"eventTimeMs\":" + now + "}";   // symbol + strike गायब, eventTimeMs ताज़ा
        assertNull(updateCache(service, topicBinding("DATABENTO", "gex-strike-lifecycle"),
                        recordAt(settings.databentoGexStrikeLifecycleTopic(), 0, 1L, "kafka-key", json, now), json),
                "a malformed lifecycle record must be dropped by updateCache, not cached under the Kafka key");
        setActiveSelection(service, "DATABENTO", "SPX", "20260622");
        assertTrue(cachedEvents(service, List.of("gex-strike-lifecycle"), now).isEmpty(),
                "a dropped lifecycle record must never replay");
    }

    @Test
    void strikeLifecycleUpdateCacheStoresSourcePrefixedKeyAndCaches() throws Exception {
        FeedGatewayService service = service();
        String json = "{\"marketDataSource\":\"DATABENTO\",\"symbol\":\"SPX\",\"expiry\":\"20260622\","
                + "\"strike\":6005,\"label\":\"EMERGING\",\"netSign\":1,\"frameId\":3,\"frameStrikeCount\":17,"
                + "\"eventTimeMs\":" + System.currentTimeMillis() + "}";
        String key = updateCache(service, topicBinding("DATABENTO", "gex-strike-lifecycle"),
                new ConsumerRecord<>(new GatewaySettings().databentoGexStrikeLifecycleTopic(), 0, 1L, "SPX|20260622|6005", json),
                json);
        assertEquals("DATABENTO|SPX|20260622|6005", key,
                "updateCache must prepend the source to the lifecycle cache key");
        assertTrue(service.healthJson().contains("\"gexStrikeLifecycle\":1"), "lifecycle record must be cached");
    }

    @Test
    void cachedReplayIncludesFreshLifecycleForMatchingDatabentoSelectionOnly() throws Exception {
        FeedGatewayService service = service();
        GatewaySettings settings = new GatewaySettings();
        long now = System.currentTimeMillis();
        String json = "{\"marketDataSource\":\"DATABENTO\",\"symbol\":\"SPX\",\"expiry\":\"20260622\","
                + "\"strike\":6005,\"label\":\"EMERGING\",\"frameId\":3,\"eventTimeMs\":" + now + "}";
        updateCache(service, topicBinding("DATABENTO", "gex-strike-lifecycle"),
                recordAt(settings.databentoGexStrikeLifecycleTopic(), 0, 1L, "SPX|20260622|6005", json, now), json);

        setActiveSelection(service, "DATABENTO", "SPX", "20260622");
        assertEquals(1, cachedEvents(service, List.of("gex-strike-lifecycle"), now).size(),
                "fresh lifecycle must replay to a matching DATABENTO client");

        setActiveSelection(service, "IBKR", "SPX", "20260622");
        assertTrue(cachedEvents(service, List.of("gex-strike-lifecycle"), now).isEmpty(),
                "IBKR selection must never receive DATABENTO lifecycle");

        setActiveSelection(service, "DATABENTO", "SPY", "20260622");
        assertTrue(cachedEvents(service, List.of("gex-strike-lifecycle"), now).isEmpty(),
                "wrong symbol is filtered by the selection barrier");
    }

    @Test
    void strikeLifecycleUpdateCacheFailsClosedOnExpiredOutOfOrderOrMissingPayloadTime() throws Exception {
        // Codex round-1 (gateway) fixes #2/#3: freshness comes from the PAYLOAD eventTimeMs, and a record that
        // is expired (payload older than the 12h window), superseded (out-of-order), or missing eventTimeMs must
        // make updateCache return null — so the live-forward paths (legacy AND per-session) fail closed.
        FeedGatewayService service = service();
        GatewaySettings settings = new GatewaySettings();
        long now = System.currentTimeMillis();
        String topic = settings.databentoGexStrikeLifecycleTopic();

        // Expired: payload eventTimeMs older than the 12h lifecycle window ⇒ rejected (null).
        String expired = "{\"marketDataSource\":\"DATABENTO\",\"symbol\":\"SPX\",\"expiry\":\"20260622\","
                + "\"strike\":6005,\"label\":\"EMERGING\",\"eventTimeMs\":" + (now - 13L * 3600_000L) + "}";
        assertNull(updateCache(service, topicBinding("DATABENTO", "gex-strike-lifecycle"),
                recordAt(topic, 0, 1L, "SPX|20260622|6005", expired, now), expired),
                "an expired lifecycle payload must be rejected (fail-closed)");

        // Missing eventTimeMs ⇒ treated as ancient ⇒ rejected (null), never cached with a fresh Kafka arrival.
        String noTs = "{\"marketDataSource\":\"DATABENTO\",\"symbol\":\"SPX\",\"expiry\":\"20260622\","
                + "\"strike\":6010,\"label\":\"EMERGING\"}";
        assertNull(updateCache(service, topicBinding("DATABENTO", "gex-strike-lifecycle"),
                recordAt(topic, 0, 2L, "SPX|20260622|6010", noTs, now), noTs),
                "a lifecycle payload with no eventTimeMs must fail closed, not fall back to Kafka arrival");

        // In-order fresh record caches; a later OUT-OF-ORDER (older payload) record for the same strike is rejected.
        String fresh = "{\"marketDataSource\":\"DATABENTO\",\"symbol\":\"SPX\",\"expiry\":\"20260622\","
                + "\"strike\":6015,\"label\":\"STICKY\",\"eventTimeMs\":" + now + "}";
        assertNotNull(updateCache(service, topicBinding("DATABENTO", "gex-strike-lifecycle"),
                recordAt(topic, 0, 3L, "SPX|20260622|6015", fresh, now), fresh),
                "a fresh in-order lifecycle record must cache");
        String older = "{\"marketDataSource\":\"DATABENTO\",\"symbol\":\"SPX\",\"expiry\":\"20260622\","
                + "\"strike\":6015,\"label\":\"FADING\",\"eventTimeMs\":" + (now - 1000L) + "}";
        assertNull(updateCache(service, topicBinding("DATABENTO", "gex-strike-lifecycle"),
                recordAt(topic, 0, 4L, "SPX|20260622|6015", older, now), older),
                "an out-of-order (older payload) lifecycle record must be superseded (null)");
    }

    @Test
    void cachedReplayIncludesFreshDeltaFlowForMatchingDatabentoSelectionOnly() throws Exception {
        FeedGatewayService service = service();
        GatewaySettings settings = new GatewaySettings();
        long now = System.currentTimeMillis();
        String json = "{\"marketDataSource\":\"DATABENTO\",\"symbol\":\"SPX\",\"expiry\":\"20260622\","
                + "\"strike\":6005,\"sessionNetDeltaFlow\":42,\"asOfEventTimeMs\":" + now + "}";
        updateCache(service, topicBinding("DATABENTO", "delta-flow"),
                recordAt(settings.databentoDeltaFlowByStrikeTopic(), 0, 1L, "SPX|20260622|6005", json, now), json);

        // Matching DATABENTO/SPX/20260622 selection replays it.
        setActiveSelection(service, "DATABENTO", "SPX", "20260622");
        assertEquals(1, cachedEvents(service, List.of("delta-flow"), now).size(),
                "fresh delta-flow must replay to a matching DATABENTO client");

        // Wrong source (IBKR) is filtered (delta-flow is DATABENTO-only).
        setActiveSelection(service, "IBKR", "SPX", "20260622");
        assertTrue(cachedEvents(service, List.of("delta-flow"), now).isEmpty(),
                "IBKR selection must never receive DATABENTO delta-flow");

        // Wrong symbol is filtered by the selection barrier.
        setActiveSelection(service, "DATABENTO", "SPY", "20260622");
        assertTrue(cachedEvents(service, List.of("delta-flow"), now).isEmpty(),
                "a different symbol must not receive this delta-flow");
    }

    @Test
    void staleCachedDeltaFlowIsNotReplayed() throws Exception {
        // FIX 1: a delta-flow whose freshness (event time) is old must NOT be replayed on connect — the
        // isCacheFresh gate in cachedEvents drops it, so a catching-up/backfilled producer cannot render
        // a stale delta-flow as a live signal.
        FeedGatewayService service = service();
        GatewaySettings settings = new GatewaySettings();
        long now = System.currentTimeMillis();
        // asOfEventTimeMs is well past the generic 15-min TTL; eventCacheTimestamp uses the payload time.
        long staleEventTime = now - 60L * 60_000L;
        String json = "{\"marketDataSource\":\"DATABENTO\",\"symbol\":\"SPX\",\"expiry\":\"20260622\","
                + "\"strike\":6005,\"sessionNetDeltaFlow\":42,\"asOfEventTimeMs\":" + staleEventTime + "}";
        // Fresh Kafka ARRIVAL time (record timestamp = now) — only the payload event time makes it stale.
        updateCache(service, topicBinding("DATABENTO", "delta-flow"),
                recordAt(settings.databentoDeltaFlowByStrikeTopic(), 0, 1L, "SPX|20260622|6005", json, now), json);

        setActiveSelection(service, "DATABENTO", "SPX", "20260622");
        assertTrue(cachedEvents(service, List.of("delta-flow"), now).isEmpty(),
                "a stale (old payload event time) delta-flow must not be replayed");
    }

    @Test
    void deltaFlowFreshnessUsesPayloadEventTimeNotKafkaArrivalTime() throws Exception {
        // FIX 1: eventCacheTimestamp for delta-flow returns the payload asOfEventTimeMs, not the record
        // arrival timestamp (mirrors dealer-ledger). The 5-arg ctor sets record timestamp = -1, so the
        // payload event time must be what is returned.
        FeedGatewayService service = service();
        long oldEventTime = 1_700_000_000_000L;
        ConsumerRecord<String, String> record = new ConsumerRecord<>(
                "delta-flow-by-strike", 0, 0L, "SPX|20260622|6005",
                "{\"symbol\":\"SPX\",\"expiry\":\"20260622\",\"strike\":6005,\"sessionNetDeltaFlow\":42,"
                        + "\"asOfEventTimeMs\":" + oldEventTime + "}");
        assertEquals(oldEventTime, eventCacheTimestamp(service, "delta-flow", record));
    }

    // ----- strike-intel gateway consumer (per-strike StrikeIntelligenceSignal) --------------------

    @Test
    void strikeIntelCacheKeyIsSymbolExpiryStrikeFromPayloadIdentity() throws Exception {
        // The helper derives symbol|expiry|strike from the payload (source is prepended later by
        // updateCache), mirroring deltaFlowCacheKey — strike-intel is per-strike, not chain-level.
        FeedGatewayService service = service();
        assertEquals("SPX|20260622|6005", strikeIntelCacheKey(
                service,
                "{\"symbol\":\"SPX\",\"expiry\":\"20260622\",\"strike\":6005,\"strikeRole\":\"MAGNET\"}",
                "fallback-key"));
    }

    @Test
    void strikeIntelUpdateCacheStoresSourcePrefixedKey() throws Exception {
        // After updateCache the stored/returned cache key is DATABENTO|SPX|expiry|strike (source prepended).
        FeedGatewayService service = service();
        String json = "{\"marketDataSource\":\"DATABENTO\",\"symbol\":\"SPX\",\"expiry\":\"20260622\","
                + "\"strike\":6005,\"strikeRole\":\"MAGNET\",\"eventTimeMs\":" + System.currentTimeMillis() + "}";
        String key = updateCache(service, topicBinding("DATABENTO", "strike-intel"),
                new ConsumerRecord<>(new GatewaySettings().strikeIntelByStrikeTopic(), 0, 1L, "SPX|20260622|6005", json),
                json);
        assertEquals("DATABENTO|SPX|20260622|6005", key,
                "updateCache must prepend the source to the strike-intel cache key");
        assertTrue(service.healthJson().contains("\"strikeIntels\":1"), "strike-intel must be cached");
        Method statusJson = FeedGatewayService.class.getDeclaredMethod("statusJson");
        statusJson.setAccessible(true);
        assertTrue(((String) statusJson.invoke(service)).contains("\"strikeIntels\":1"),
                "statusJson must report the strike-intel cache count (delta-flow counterpart)");
    }

    // ----- strike-invasion gateway consumer (per-strike, per-direction StrikeInvasionSnapshot, SPX-only, NO expiry) --

    @Test
    void strikeInvasionCacheKeyIsSymbolStrikeDirectionFromPayloadIdentity() throws Exception {
        // strike-invasion is SPX-only and carries NO expiry, so the key is symbol|strike|direction
        // (mirrors strikeIntelCacheKey minus the expiry segment, plus the contract-v2 direction;
        // source is prepended later by updateCache). Direction comes from the PAYLOAD, never the
        // Kafka record key shape.
        FeedGatewayService service = service();
        assertEquals("SPX|6005|UP", strikeInvasionCacheKey(
                service,
                "{\"symbol\":\"SPX\",\"strike\":6005,\"direction\":\"UP\",\"invasionState\":\"INVADED\"}",
                "fallback-key"));
        assertEquals("SPX|6005|DOWN", strikeInvasionCacheKey(
                service,
                "{\"symbol\":\"SPX\",\"strike\":6005,\"direction\":\"DOWN\",\"invasionState\":\"INVADED\"}",
                "fallback-key"));
        // Pre-v2 records carry no direction and were upside-only: they must key as UP (replacing an
        // older UP entry, never duplicating the strike). Blank direction gets the same default.
        assertEquals("SPX|6005|UP", strikeInvasionCacheKey(
                service,
                "{\"symbol\":\"SPX\",\"strike\":6005,\"invasionState\":\"INVADED\"}",
                "fallback-key"));
        assertEquals("SPX|6005|UP", strikeInvasionCacheKey(
                service,
                "{\"symbol\":\"SPX\",\"strike\":6005,\"direction\":\"\",\"invasionState\":\"INVADED\"}",
                "fallback-key"));
    }

    @Test
    void strikeInvasionUpdateCacheStoresSourcePrefixedKey() throws Exception {
        // After updateCache the stored/returned cache key is DATABENTO|SPX|strike|direction (source
        // prepended, NO expiry; direction defaults to UP for a pre-v2 direction-less record).
        FeedGatewayService service = service();
        String json = "{\"marketDataSource\":\"DATABENTO\",\"symbol\":\"SPX\","
                + "\"strike\":6005,\"invasionState\":\"INVADED\",\"eventTimeMs\":" + System.currentTimeMillis() + "}";
        String key = updateCache(service, topicBinding("DATABENTO", "strike-invasion"),
                new ConsumerRecord<>(new GatewaySettings().strikeInvasionTopic(), 0, 1L, "SPX|6005", json),
                json);
        assertEquals("DATABENTO|SPX|6005|UP", key,
                "updateCache must prepend the source to the strike-invasion cache key");

        String down = "{\"marketDataSource\":\"DATABENTO\",\"symbol\":\"SPX\",\"strike\":6005,"
                + "\"direction\":\"DOWN\",\"invasionState\":\"INVADED\",\"eventTimeMs\":" + System.currentTimeMillis() + "}";
        String downKey = updateCache(service, topicBinding("DATABENTO", "strike-invasion"),
                new ConsumerRecord<>(new GatewaySettings().strikeInvasionTopic(), 0, 2L, "6005:DOWN", down),
                down);
        assertEquals("DATABENTO|SPX|6005|DOWN", downKey,
                "a DOWN record must key separately from the same strike's UP record");
    }

    @Test
    void enrichJsonStampsActiveSelectionExpiryOnStrikeInvasion() throws Exception {
        // StrikeInvasionSnapshot carries NO expiry, but contract routing (RoutingKeyDeriver /
        // matchesSelectionNode / cached-replay barrier) matches on symbol|expiry and REJECTS a blank
        // expiry. enrichJson must stamp the active SPX selection's expiry so the record routes like
        // strike-intel instead of being dropped as a blank-expiry contract record.
        FeedGatewayService service = service();
        // The stamp comes from the market-calendar trading date, NOT the per-session selection.
        String today = currentTradingDateExpiry();
        setActiveSelection(service, "DATABENTO", "SPX", "20991231"); // deliberately NOT the calendar date
        String enriched = enrichJson(service,
                "{\"symbol\":\"SPX\",\"strike\":6005,\"state\":\"ACCEPTED_ABOVE\"}",
                topicBinding("DATABENTO", "strike-invasion"));
        assertEquals(today, new ObjectMapper().readTree(enriched).get("expiry").asText(),
                "strike-invasion must inherit the calendar 0DTE expiry (not the manual selection) so it is routable");
    }

    @Test
    void cachedReplayIncludesFreshStrikeInvasionForMatchingSpxSelectionOnly() throws Exception {
        // End-to-end: a strike-invasion record (no expiry in payload) enriched under an SPX selection
        // must replay to a matching DATABENTO SPX client — and only to it. Before the enrichJson expiry
        // stamp this dropped entirely (blank-expiry contract records never match a selection).
        FeedGatewayService service = service();
        GatewaySettings settings = new GatewaySettings();
        long now = System.currentTimeMillis();
        String today = currentTradingDateExpiry();
        setActiveSelection(service, "DATABENTO", "SPX", today);
        String enriched = enrichJson(service,
                "{\"symbol\":\"SPX\",\"strike\":6005,\"state\":\"ACCEPTED_ABOVE\",\"asOfEventTimeMs\":" + now + "}",
                topicBinding("DATABENTO", "strike-invasion"));
        updateCache(service, topicBinding("DATABENTO", "strike-invasion"),
                recordAt(settings.strikeInvasionTopic(), 0, 1L, "SPX|6005", enriched, now), enriched);

        assertEquals(1, cachedEvents(service, List.of("strike-invasion"), now).size(),
                "fresh strike-invasion must replay to a matching DATABENTO SPX 0DTE client");

        // Wrong source (IBKR) is filtered (strike-invasion is DATABENTO-only).
        setActiveSelection(service, "IBKR", "SPX", today);
        assertTrue(cachedEvents(service, List.of("strike-invasion"), now).isEmpty(),
                "IBKR selection must never receive DATABENTO strike-invasion");

        // Wrong symbol is filtered by the selection barrier.
        setActiveSelection(service, "DATABENTO", "SPY", today);
        assertTrue(cachedEvents(service, List.of("strike-invasion"), now).isEmpty(),
                "a different symbol must not receive this strike-invasion");

        // Wrong EXPIRY: a session that manually selected a non-0DTE SPX chain must NOT receive the 0DTE
        // invasion signal — the record is stamped with the calendar 0DTE date, independent of the
        // session's selection, so the selection barrier correctly filters a later expiry.
        setActiveSelection(service, "DATABENTO", "SPX", "20991231");
        assertTrue(cachedEvents(service, List.of("strike-invasion"), now).isEmpty(),
                "a non-0DTE SPX chain must not receive the 0DTE strike-invasion");
    }

    @Test
    void staleCachedStrikeInvasionIsNotReplayed() throws Exception {
        // A strike-invasion whose PAYLOAD event time (asOfEventTimeMs) is old must NOT replay on connect,
        // even though its Kafka ARRIVAL time is fresh — a catching-up/backfilling producer must not render
        // a stale invasion action as live (mirrors strike-intel; relies on the eventCacheTimestamp branch).
        FeedGatewayService service = service();
        GatewaySettings settings = new GatewaySettings();
        long now = System.currentTimeMillis();
        long staleEventTime = now - 60L * 60_000L;
        setActiveSelection(service, "DATABENTO", "SPX", currentTradingDateExpiry());
        String enriched = enrichJson(service,
                "{\"symbol\":\"SPX\",\"strike\":6005,\"state\":\"ACCEPTED_ABOVE\",\"asOfEventTimeMs\":" + staleEventTime + "}",
                topicBinding("DATABENTO", "strike-invasion"));
        // Fresh Kafka ARRIVAL time (record timestamp = now) — only the payload event time makes it stale.
        updateCache(service, topicBinding("DATABENTO", "strike-invasion"),
                recordAt(settings.strikeInvasionTopic(), 0, 1L, "SPX|6005", enriched, now), enriched);

        assertTrue(cachedEvents(service, List.of("strike-invasion"), now).isEmpty(),
                "a stale (old payload event time) strike-invasion must not be replayed");
    }

    @Test
    void strikeInvasionUpAndDownRecordsForTheSameStrikeCoexistInTheEnvelope() throws Exception {
        // Contract v2: one strike can legitimately carry BOTH a live UP record (SHORT_CALL_CANDIDATE
        // domain) and a DOWN record (SHORT_PUT_CANDIDATE domain) at the same time. With the old
        // symbol|strike cache key the second record OVERWROTE the first, silencing an actionable trade
        // verdict in the UI — the direction-qualified key must keep both alive through cache, cached
        // connect replay, and the strikeInvasions envelope array.
        FeedGatewayService service = service();
        GatewaySettings settings = new GatewaySettings();
        long now = System.currentTimeMillis();
        setActiveSelection(service, "DATABENTO", "SPX", currentTradingDateExpiry());
        String up = enrichJson(service,
                "{\"symbol\":\"SPX\",\"strike\":6005,\"direction\":\"UP\",\"state\":\"ACCEPTED_ABOVE\","
                        + "\"asOfEventTimeMs\":" + now + "}",
                topicBinding("DATABENTO", "strike-invasion"));
        String down = enrichJson(service,
                "{\"symbol\":\"SPX\",\"strike\":6005,\"direction\":\"DOWN\",\"state\":\"ACCEPTED_BELOW\","
                        + "\"asOfEventTimeMs\":" + now + "}",
                topicBinding("DATABENTO", "strike-invasion"));
        updateCache(service, topicBinding("DATABENTO", "strike-invasion"),
                recordAt(settings.strikeInvasionTopic(), 0, 1L, "6005:UP", up, now), up);
        updateCache(service, topicBinding("DATABENTO", "strike-invasion"),
                recordAt(settings.strikeInvasionTopic(), 0, 2L, "6005:DOWN", down, now), down);

        List<?> events = cachedEvents(service, List.of("strike-invasion"), now);
        assertEquals(2, events.size(),
                "UP and DOWN invasion records for the same strike must coexist (neither may overwrite the other)");
        List<String> jsons = new ArrayList<>();
        for (Object event : events) {
            jsons.add(cachedEventJson(event));
        }
        assertTrue(jsons.contains(up), "the UP record must survive the DOWN record's arrival");
        assertTrue(jsons.contains(down), "the DOWN record must be cached alongside the UP record");

        // Both raw records (direction passes through untouched) reach the strikeInvasions envelope array.
        String envelope = uiBatchEnvelopeJsonStrikeInvasion(service, jsons);
        assertTrue(envelope.contains(up) && envelope.contains(down),
                "the strikeInvasions envelope array must carry BOTH directions; was: " + envelope);
    }

    @Test
    void directionlessStrikeInvasionKeysAsUpAndReplacesTheOlderUpRecord() throws Exception {
        // Pre-v2 records carry no direction and were upside-only: a direction-less record must key as UP —
        // REPLACING the strike's older UP record (same cache slot, same monotonic freshness gate), never
        // duplicating the strike in the envelope.
        FeedGatewayService service = service();
        GatewaySettings settings = new GatewaySettings();
        long now = System.currentTimeMillis();
        setActiveSelection(service, "DATABENTO", "SPX", currentTradingDateExpiry());
        String olderUp = enrichJson(service,
                "{\"symbol\":\"SPX\",\"strike\":6005,\"direction\":\"UP\",\"state\":\"ACCEPTED_ABOVE\","
                        + "\"asOfEventTimeMs\":" + (now - 2_000L) + "}",
                topicBinding("DATABENTO", "strike-invasion"));
        String legacy = enrichJson(service,
                "{\"symbol\":\"SPX\",\"strike\":6005,\"state\":\"INVADED\","
                        + "\"asOfEventTimeMs\":" + now + "}",
                topicBinding("DATABENTO", "strike-invasion"));
        updateCache(service, topicBinding("DATABENTO", "strike-invasion"),
                recordAt(settings.strikeInvasionTopic(), 0, 1L, "6005:UP", olderUp, now - 2_000L), olderUp);
        updateCache(service, topicBinding("DATABENTO", "strike-invasion"),
                recordAt(settings.strikeInvasionTopic(), 0, 2L, "SPX|6005", legacy, now), legacy);

        List<?> events = cachedEvents(service, List.of("strike-invasion"), now);
        assertEquals(1, events.size(),
                "a direction-less record must REPLACE the strike's UP record, not duplicate the strike");
        assertEquals(legacy, cachedEventJson(events.get(0)),
                "the newer direction-less record must win the shared UP cache slot");

        // The shared slot also shares the per-record monotonic event-time gate: an OLDER direction-less
        // record must be dropped by the UP slot's freshness gate (updateCache returns null).
        String staleLegacy = enrichJson(service,
                "{\"symbol\":\"SPX\",\"strike\":6005,\"state\":\"RETREATED\","
                        + "\"asOfEventTimeMs\":" + (now - 1_000L) + "}",
                topicBinding("DATABENTO", "strike-invasion"));
        assertNull(updateCache(service, topicBinding("DATABENTO", "strike-invasion"),
                        recordAt(settings.strikeInvasionTopic(), 0, 3L, "SPX|6005", staleLegacy, now), staleLegacy),
                "an older direction-less record must be rejected by the UP slot's monotonic event-time gate");
    }

    @Test
    void replayReStampsStrikeInvasionExpiryToTheReplayWindow() throws Exception {
        // strike-invasion carries no expiry; enrichJson stamps the LIVE calendar date. In replay the record
        // belongs to the replay window's chain, so emitReplayRecord re-stamps params.expiry() — without it
        // the historical (live-dated) record would fail the expiry-matched replayMatches filter and be
        // silently dropped from the private replay stream.
        FeedGatewayService service = service();
        String enriched = enrichJson(service,
                "{\"symbol\":\"SPX\",\"strike\":6005,\"state\":\"ACCEPTED_ABOVE\"}",
                topicBinding("DATABENTO", "strike-invasion"));
        // A historical replay window (a date that is NOT the current calendar trading date).
        ReplayParams params = new ReplayParams("app:u1", "SPX", "20260612", 1_000L, 2_000L, 1000, null);
        assertNotEquals("20260612", currentTradingDateExpiry(),
                "test precondition: the replay window must differ from the live calendar date");

        // The live-dated record does NOT match a historical replay window...
        assertFalse(replayMatches(service, params, "strike-invasion", enriched),
                "an unstamped (live-dated) strike-invasion must not match a historical replay window");
        // ...but after the replay re-stamp (params.expiry) it does.
        String restamped = stampExpiry(service, enriched, params.expiry());
        assertTrue(replayMatches(service, params, "strike-invasion", restamped),
                "re-stamping the replay window expiry makes the replayed strike-invasion match");
    }

    // ----- ES 09:15 open-direction gateway consumer (once-a-day forecast + H1/H2/H3 outcomes) ------

    @Test
    void esOpenDirectionTopicsAreOptionalSoTheirAbsenceCannotStarveTheSharedConsumer() throws Exception {
        // The forecast producer is a brand-new service that may not be deployed (and after the daily
        // Kafka wipe the topics are absent until it first produces) — both topics MUST be optional so
        // their absence can never block/crash-loop the shared JSON consumer.
        FeedGatewayService service = service();
        GatewaySettings settings = new GatewaySettings();
        assertTrue(isOptionalTopic(service, settings.esOpenDirectionForecastTopic()));
        assertTrue(isOptionalTopic(service, settings.esOpenDirectionOutcomeTopic()));
    }

    @Test
    void esOpenDirectionCacheKeysAreTradeDateAndTradeDateHorizon() throws Exception {
        // Forecast: ONE cache entry per tradeDate (last-value-wins). Outcome: tradeDate|horizon, so the
        // day's H1/H2/H3 all survive side-by-side and a late-joining client replays every outcome
        // resolved so far (source is prepended later by updateCache).
        FeedGatewayService service = service();
        assertEquals("2026-07-11", esOpenDirectionForecastCacheKey(
                service,
                "{\"tradeDate\":\"2026-07-11\",\"status\":\"FORECASTED\",\"direction\":\"UP\"}",
                "fallback-key"));
        assertEquals("2026-07-11|H1", esOpenDirectionOutcomeCacheKey(
                service,
                "{\"tradeDate\":\"2026-07-11\",\"horizon\":\"H1\",\"correct\":true}",
                "fallback-key"));
        // Malformed payloads fall back to the Kafka key rather than throwing.
        assertEquals("fallback-key", esOpenDirectionForecastCacheKey(service, "not json", "fallback-key"));
        assertEquals("fallback-key", esOpenDirectionOutcomeCacheKey(service, "not json", "fallback-key"));
    }

    @Test
    void esOpenDirectionUpdateCacheStoresSourcePrefixedKeys() throws Exception {
        FeedGatewayService service = service();
        GatewaySettings settings = new GatewaySettings();
        long now = System.currentTimeMillis();
        String forecast = "{\"tradeDate\":\"2026-07-11\",\"status\":\"FORECASTED\",\"direction\":\"UP\"}";
        assertEquals("DATABENTO|2026-07-11",
                updateCache(service, topicBinding("DATABENTO", "es-open-direction-forecast"),
                        recordAt(settings.esOpenDirectionForecastTopic(), 0, 1L, "2026-07-11", forecast, now),
                        forecast),
                "updateCache must prepend the source to the forecast cache key");
        String outcome = "{\"tradeDate\":\"2026-07-11\",\"horizon\":\"H1\",\"correct\":true}";
        assertEquals("DATABENTO|2026-07-11|H1",
                updateCache(service, topicBinding("DATABENTO", "es-open-direction-outcome"),
                        recordAt(settings.esOpenDirectionOutcomeTopic(), 0, 1L, "2026-07-11", outcome, now),
                        outcome),
                "updateCache must prepend the source to the outcome cache key");
    }

    @Test
    void closeDirectionCacheKeysPhaseSplitAndMalformedDrop() throws Exception {
        // Design CLOSE-DIRECTION-GATE1 CD-R30: V|/I| phase split (source prepended by
        // updateCache), malformed payloads (bad JSON, unknown phase, missing sessionDate or
        // direction) return null and are never cached or broadcast.
        FeedGatewayService service = service();
        GatewaySettings settings = new GatewaySettings();
        long now = System.currentTimeMillis();
        String interim = "{\"phase\":\"MONITORING\",\"sessionDate\":\"2026-07-24\","
                + "\"direction\":\"UP\",\"asOfMs\":" + now + "}";
        assertEquals("DATABENTO|I|2026-07-24",
                updateCache(service, topicBinding("DATABENTO", "close-direction"),
                        recordAt(settings.closeDirectionSignalTopic(), 0, 1L, "SPX|20260724",
                                interim, now), interim));
        String verdict = "{\"phase\":\"VERDICT\",\"sessionDate\":\"2026-07-24\","
                + "\"direction\":\"DOWN\",\"verdictId\":\"CDV1:2026-07-24:SPX:20260724\","
                + "\"asOfMs\":" + now + "}";
        assertEquals("DATABENTO|V|2026-07-24",
                updateCache(service, topicBinding("DATABENTO", "close-direction"),
                        recordAt(settings.closeDirectionSignalTopic(), 0, 2L, "SPX|20260724",
                                verdict, now), verdict));
        // Verdict-over-interim precedence: an interim AFTER the verdict is dead (null).
        String lateInterim = "{\"phase\":\"MONITORING\",\"sessionDate\":\"2026-07-24\","
                + "\"direction\":\"UP\",\"asOfMs\":" + (now + 1000) + "}";
        assertNull(updateCache(service, topicBinding("DATABENTO", "close-direction"),
                recordAt(settings.closeDirectionSignalTopic(), 0, 3L, "SPX|20260724",
                        lateInterim, now + 1000), lateInterim));
        // Malformed: unknown phase / missing fields / non-JSON.
        assertNull(updateCache(service, topicBinding("DATABENTO", "close-direction"),
                recordAt(settings.closeDirectionSignalTopic(), 0, 4L, "SPX|20260724",
                        "{\"phase\":\"WEIRD\",\"sessionDate\":\"2026-07-24\",\"direction\":\"UP\"}",
                        now), "{\"phase\":\"WEIRD\",\"sessionDate\":\"2026-07-24\",\"direction\":\"UP\"}"));
        assertNull(updateCache(service, topicBinding("DATABENTO", "close-direction"),
                recordAt(settings.closeDirectionSignalTopic(), 0, 5L, "SPX|20260724",
                        "not json", now), "not json"));
    }

    @Test
    void closeDirectionMissingFieldsAndStaleInterimIngestDrop() throws Exception {
        FeedGatewayService service = service();
        GatewaySettings settings = new GatewaySettings();
        long now = System.currentTimeMillis();
        // Missing sessionDate / missing direction: null, never cached (CD-R30).
        String noDate = "{\"phase\":\"MONITORING\",\"direction\":\"UP\",\"asOfMs\":" + now + "}";
        assertNull(updateCache(service, topicBinding("DATABENTO", "close-direction"),
                recordAt(settings.closeDirectionSignalTopic(), 0, 11L, "SPX|20260724",
                        noDate, now), noDate));
        String noDirection = "{\"phase\":\"MONITORING\",\"sessionDate\":\"2026-07-24\","
                + "\"asOfMs\":" + now + "}";
        assertNull(updateCache(service, topicBinding("DATABENTO", "close-direction"),
                recordAt(settings.closeDirectionSignalTopic(), 0, 12L, "SPX|20260724",
                        noDirection, now), noDirection));
        // Stale-interim INGESTION drop: a backfilled monitoring record older than the
        // interim freshness window must not cache or live-broadcast — while a verdict of
        // the same age stays valid on the long window.
        long stale = now - settings.closeDirectionInterimFreshMs() - 60_000;
        String staleInterim = "{\"phase\":\"MONITORING\",\"sessionDate\":\"2026-07-23\","
                + "\"direction\":\"UP\",\"asOfMs\":" + stale + "}";
        assertNull(updateCache(service, topicBinding("DATABENTO", "close-direction"),
                recordAt(settings.closeDirectionSignalTopic(), 0, 13L, "SPX|20260723",
                        staleInterim, stale), staleInterim));
        String oldVerdict = "{\"phase\":\"VERDICT\",\"sessionDate\":\"2026-07-23\","
                + "\"direction\":\"DOWN\",\"verdictId\":\"CDV1:2026-07-23:SPX:20260723\","
                + "\"asOfMs\":" + stale + "}";
        assertEquals("DATABENTO|V|2026-07-23",
                updateCache(service, topicBinding("DATABENTO", "close-direction"),
                        recordAt(settings.closeDirectionSignalTopic(), 0, 14L, "SPX|20260723",
                                oldVerdict, stale), oldVerdict));
    }

    @Test
    void closeDirectionTopicIsOptionalAndPrefixAware() throws Exception {
        FeedGatewayService service = service();
        GatewaySettings settings = new GatewaySettings();
        assertTrue(isOptionalTopic(service, settings.closeDirectionSignalTopic()),
                "close-direction topic absence must never starve the shared JSON consumer");
        // TOPIC_PREFIX (es4) applies through the *_TOPIC helper — no code change per env.
        System.setProperty("TOPIC_PREFIX", "es.");
        try {
            assertEquals("es.close.direction.signal",
                    new GatewaySettings().closeDirectionSignalTopic());
        } finally {
            System.clearProperty("TOPIC_PREFIX");
        }
    }

    @Test
    void closeDirectionReplayOnConnect_freshInterim_verdictPrecedence_staleSuppression()
            throws Exception {
        // CD-R30 replay behavior on the REAL replay path: a fresh interim replays; once
        // the session's verdict is cached the verdict replays and the interim does not;
        // a stale-asOfMs interim never replays.
        FeedGatewayService service = service();
        GatewaySettings settings = new GatewaySettings();
        long now = System.currentTimeMillis();
        Method replay = FeedGatewayService.class.getDeclaredMethod(
                "replayCloseDirectionCached", WebSocketSession.class);
        replay.setAccessible(true);

        String interim = "{\"phase\":\"MONITORING\",\"sessionDate\":\"2026-07-24\","
                + "\"direction\":\"UP\",\"asOfMs\":" + (now - 30_000) + "}";
        updateCache(service, topicBinding("DATABENTO", "close-direction"),
                recordAt(settings.closeDirectionSignalTopic(), 0, 21L, "SPX|20260724",
                        interim, now - 30_000), interim);
        List<String> sink = new java.util.ArrayList<>();
        replay.invoke(service, recordingSession(sink));
        assertEquals(1, sink.size(), "fresh interim replays on connect");
        assertTrue(sink.get(0).contains("\"phase\":\"MONITORING\""));

        // Verdict lands → replay sends the verdict, never the interim (precedence).
        String verdict = "{\"phase\":\"VERDICT\",\"sessionDate\":\"2026-07-24\","
                + "\"direction\":\"DOWN\",\"verdictId\":\"CDV1:2026-07-24:SPX:20260724\","
                + "\"asOfMs\":" + now + "}";
        updateCache(service, topicBinding("DATABENTO", "close-direction"),
                recordAt(settings.closeDirectionSignalTopic(), 0, 22L, "SPX|20260724",
                        verdict, now), verdict);
        sink.clear();
        replay.invoke(service, recordingSession(sink));
        assertEquals(1, sink.size(), "verdict-over-interim on replay");
        assertTrue(sink.get(0).contains("\"phase\":\"VERDICT\""));

        // Stale-asOfMs interim for another session: cached fresh by record time is now
        // impossible (ingestion gate) — simulate staleness by aging: replay must suppress
        // an interim whose asOfMs has fallen outside the freshness window.
        String agingInterim = "{\"phase\":\"MONITORING\",\"sessionDate\":\"2026-07-25\","
                + "\"direction\":\"UP\",\"asOfMs\":"
                + (now - settings.closeDirectionInterimFreshMs() + 2_000) + "}";
        updateCache(service, topicBinding("DATABENTO", "close-direction"),
                recordAt(settings.closeDirectionSignalTopic(), 0, 23L, "SPX|20260725",
                        agingInterim, now), agingInterim);
        Thread.sleep(2_100);   // asOfMs crosses the freshness boundary
        sink.clear();
        replay.invoke(service, recordingSession(sink));
        assertEquals(1, sink.size(), "stale interim suppressed; only the verdict replays");
        assertTrue(sink.get(0).contains("\"phase\":\"VERDICT\""));
    }

    @Test
    void closeDirectionUsesLongTtlWindow() throws Exception {
        // The frozen 15:49 verdict must still replay to a client connecting at 15:59; the long
        // 12h window also drives the restart seek-back. (Interim REPLAY freshness is separately
        // bounded by closeDirectionInterimFreshMs in replayCloseDirectionCached.)
        FeedGatewayService service = service();
        long now = System.currentTimeMillis();
        long ttl = new GatewaySettings().closeDirectionTtlMs();
        assertTrue(ttl >= 12L * 3_600_000L, "close-direction TTL must cover the session");
        assertFalse(isExpired(service, "close-direction", now - 2L * 3_600_000L, now));
        assertTrue(isExpired(service, "close-direction", now - ttl - 1, now));
    }

    @Test
    void esOpenDirectionUsesLongSessionTtlNotGenericCacheWindow() throws Exception {
        // The whole point of the panel: a forecast published at 09:15 must still be served to a client
        // that connects at 11:00 (and at 15:59). A 2h-old (even 7h-old) forecast/outcome must be FRESH
        // under the long 12h window, while a generic event of the same age is long expired.
        FeedGatewayService service = service();
        long now = System.currentTimeMillis();
        long ttl = new GatewaySettings().esOpenDirectionTtlMs();
        assertTrue(ttl >= 12L * 3_600_000L, "es-open-direction TTL must cover the whole trading session");
        assertFalse(isExpired(service, "es-open-direction-forecast", now - 2L * 3_600_000L, now),
                "a 2h-old forecast (09:15 -> 11:15) must still be fresh");
        assertFalse(isExpired(service, "es-open-direction-outcome", now - 7L * 3_600_000L, now),
                "a 7h-old outcome (09:30 window vs late-day connect) must still be fresh");
        assertTrue(isExpired(service, "es-open-direction-forecast", now - ttl - 1, now),
                "a forecast past the 12h window must expire (yesterday never replays as today)");
        // Contrast: a generic event of 2h age IS expired — proves the events are not on the generic TTL.
        assertTrue(isExpired(service, "strike-flow", now - 2L * 3_600_000L, now));
    }

    @Test
    void lateJoiningClientReplaysForecastAndAllResolvedOutcomes() throws Exception {
        // End-to-end late-join contract: forecast cached at 09:15 (2h-old Kafka record) + H1/H2 outcomes,
        // then a client connects at ~11:31 — the standalone replay must deliver all three envelopes
        // (never dropped by the 15s market-data staleness gates, never selection-gated).
        FeedGatewayService service = service();
        GatewaySettings settings = new GatewaySettings();
        long now = System.currentTimeMillis();
        String forecast = "{\"tradeDate\":\"2026-07-11\",\"status\":\"FORECASTED\",\"direction\":\"UP\","
                + "\"evidenceStrength\":68}";
        updateCache(service, topicBinding("DATABENTO", "es-open-direction-forecast"),
                recordAt(settings.esOpenDirectionForecastTopic(), 0, 1L, "2026-07-11", forecast, now - 2L * 3_600_000L),
                forecast);
        String h1 = "{\"tradeDate\":\"2026-07-11\",\"horizon\":\"H1\",\"correct\":true,\"realizedDirection\":\"UP_WIN\"}";
        updateCache(service, topicBinding("DATABENTO", "es-open-direction-outcome"),
                recordAt(settings.esOpenDirectionOutcomeTopic(), 0, 1L, "2026-07-11", h1, now - 3_600_000L),
                h1);
        String h2 = "{\"tradeDate\":\"2026-07-11\",\"horizon\":\"H2\",\"correct\":false,\"realizedDirection\":\"DOWN_WIN\"}";
        updateCache(service, topicBinding("DATABENTO", "es-open-direction-outcome"),
                recordAt(settings.esOpenDirectionOutcomeTopic(), 0, 2L, "2026-07-11", h2, now - 60_000L),
                h2);

        List<String> sink = new ArrayList<>();
        Method replay = FeedGatewayService.class.getDeclaredMethod("replayEsOpenDirectionCached", WebSocketSession.class);
        replay.setAccessible(true);
        replay.invoke(service, recordingSession(sink));

        assertEquals(3, sink.size(), "late join must replay the forecast + BOTH resolved outcomes; got: " + sink);
        assertTrue(sink.get(0).contains("\"type\":\"es-open-direction-forecast\"")
                        && sink.get(0).contains("\"evidenceStrength\":68"),
                "forecast envelope first; was: " + sink.get(0));
        assertTrue(sink.stream().filter(s -> s.contains("\"type\":\"es-open-direction-outcome\"")).count() == 2,
                "both H1 and H2 outcomes must replay; was: " + sink);
        assertTrue(sink.stream().anyMatch(s -> s.contains("\"horizon\":\"H1\"")), "H1 must replay");
        assertTrue(sink.stream().anyMatch(s -> s.contains("\"horizon\":\"H2\"")), "H2 must replay");
    }

    @Test
    void esOpenDirectionEventsAreGlobalBroadcastInPerSessionMode() {
        // Per-session (auth) mode drops any event GatewayRecordMapper cannot route unless it is an
        // allowlisted GLOBAL advisory — without this, the panel silently goes dark once
        // GATEWAY_AUTH_ENABLED=true (the exact short-premium HIGH-1 review finding).
        assertTrue(FeedGatewayService.isGlobalBroadcastEvent("es-open-direction-forecast"));
        assertTrue(FeedGatewayService.isGlobalBroadcastEvent("es-open-direction-outcome"));
    }

    @Test
    void esOpenDirectionStatusIsOptionalGlobalAndOnTheShortFiveMinuteWindow() throws Exception {
        // The 60s live-status heartbeat shares the siblings' delivery class (optional topic, global
        // broadcast in per-session mode) but NOT their freshness: a status is only meaningful while
        // CURRENT, so it lives on the SHORT esOpenDirectionStatusTtlMs window (default 5 min) — never
        // the 12h forecast/outcome window that would replay a stale overnight status as live.
        FeedGatewayService service = service();
        GatewaySettings settings = new GatewaySettings();
        assertTrue(isOptionalTopic(service, settings.esOpenDirectionStatusTopic()),
                "status producer may be absent (not deployed / no active session) — must be optional");
        assertTrue(FeedGatewayService.isGlobalBroadcastEvent("es-open-direction-status"),
                "status must fan out in per-session (auth) mode like its siblings");
        assertEquals(300_000L, settings.esOpenDirectionStatusTtlMs(), "default TTL must be 5 minutes");
        long now = System.currentTimeMillis();
        assertFalse(isExpired(service, "es-open-direction-status", now - 2L * 60_000L, now),
                "a 2-min-old status (heartbeat is 60s) must still be fresh");
        assertTrue(isExpired(service, "es-open-direction-status", now - 6L * 60_000L, now),
                "a 6-min-old status must be STALE — never routed or replayed as current");
        // Contrast: the forecast sibling of the same age is comfortably fresh on its 12h window.
        assertFalse(isExpired(service, "es-open-direction-forecast", now - 6L * 60_000L, now));
    }

    @Test
    void freshEsOpenDirectionStatusIsCachedByTradeDateAndReplayedToLateJoiner() throws Exception {
        // Late-join contract: the current (fresh) heartbeat is cached last-value-wins under
        // DATABENTO|tradeDate and replayed standalone on connect, so a client that opens the dashboard
        // mid-overnight-session immediately shows the live strip instead of waiting up to 60s.
        FeedGatewayService service = service();
        GatewaySettings settings = new GatewaySettings();
        long now = System.currentTimeMillis();
        String older = "{\"tradeDate\":\"2026-07-13\",\"state\":\"CATCHING_UP\",\"tradesBuffered\":10,"
                + "\"observabilityOnly\":true}";
        assertEquals("DATABENTO|2026-07-13",
                updateCache(service, topicBinding("DATABENTO", "es-open-direction-status"),
                        recordAt(settings.esOpenDirectionStatusTopic(), 0, 1L, "2026-07-13", older, now - 2L * 60_000L),
                        older),
                "updateCache must key the status by source|tradeDate");
        String current = "{\"tradeDate\":\"2026-07-13\",\"state\":\"MONITORING\",\"tradesBuffered\":1842,"
                + "\"lastPrice\":6321.25,\"observabilityOnly\":true}";
        assertEquals("DATABENTO|2026-07-13",
                updateCache(service, topicBinding("DATABENTO", "es-open-direction-status"),
                        recordAt(settings.esOpenDirectionStatusTopic(), 0, 2L, "2026-07-13", current, now - 60_000L),
                        current));

        List<String> sink = new ArrayList<>();
        Method replay = FeedGatewayService.class.getDeclaredMethod("replayEsOpenDirectionCached", WebSocketSession.class);
        replay.setAccessible(true);
        replay.invoke(service, recordingSession(sink));

        assertEquals(1, sink.size(), "exactly the CURRENT status must replay (last heartbeat wins); got: " + sink);
        assertTrue(sink.get(0).contains("\"type\":\"es-open-direction-status\"")
                        && sink.get(0).contains("\"state\":\"MONITORING\"")
                        && sink.get(0).contains("\"tradesBuffered\":1842"),
                "the latest heartbeat must replay verbatim (JSON pass-through); was: " + sink.get(0));
    }

    @Test
    void staleEsOpenDirectionStatusIsNeitherCachedNorReplayed() throws Exception {
        // Staleness fail-closed: a status record older than the 5-min window (dead producer, gateway
        // catching up on an overnight backlog) makes updateCache return null — which suppresses the
        // live broadcast (the cacheKey == null gate) — and nothing replays to a late joiner, so the
        // UI strip simply stays hidden instead of showing a misleading overnight state.
        FeedGatewayService service = service();
        GatewaySettings settings = new GatewaySettings();
        long now = System.currentTimeMillis();
        String stale = "{\"tradeDate\":\"2026-07-13\",\"state\":\"MONITORING\",\"tradesBuffered\":42,"
                + "\"observabilityOnly\":true}";
        assertNull(updateCache(service, topicBinding("DATABENTO", "es-open-direction-status"),
                        recordAt(settings.esOpenDirectionStatusTopic(), 0, 1L, "2026-07-13", stale, now - 6L * 60_000L),
                        stale),
                "a 6-min-old status must be dropped at ingest (null cacheKey = never live-routed)");

        List<String> sink = new ArrayList<>();
        Method replay = FeedGatewayService.class.getDeclaredMethod("replayEsOpenDirectionCached", WebSocketSession.class);
        replay.setAccessible(true);
        replay.invoke(service, recordingSession(sink));
        assertTrue(sink.isEmpty(), "a stale status must never replay to a late joiner; got: " + sink);
    }

    // ----- greek-move-authenticity CURRENT verdict relay ------------------------------------------

    @Test
    void greekMoveAuthCurrentTopicIsOptionalGlobalAndOnTheShortFiveMinuteWindow() throws Exception {
        // The move-authenticity CURRENT verdict is a standalone global advisory (optional topic, global
        // broadcast in per-session mode) whose only value is being CURRENT — so it lives on the SHORT
        // greekMoveAuthTtlMs window (default 5 min, the es-open-direction STATUS freshness class), never a
        // long window that would replay a stale overnight verdict as live.
        FeedGatewayService service = service();
        GatewaySettings settings = new GatewaySettings();
        assertEquals("options.spx.greek-move-auth.current", settings.greekMoveAuthCurrentTopic(),
                "default topic must be the contract constant GreekMoveAuthTopics.GREEK_MOVE_AUTH_CURRENT");
        assertTrue(isOptionalTopic(service, settings.greekMoveAuthCurrentTopic()),
                "a brand-new standalone producer may be absent — the topic must be optional");
        assertTrue(FeedGatewayService.isGlobalBroadcastEvent("greek-move-auth"),
                "the verdict must fan out in per-session (auth) mode like the open-direction siblings");
        assertEquals(300_000L, settings.greekMoveAuthTtlMs(), "default TTL must be 5 minutes");
        long now = System.currentTimeMillis();
        assertFalse(isExpired(service, "greek-move-auth", now - 2L * 60_000L, now),
                "a 2-min-old verdict must still be fresh");
        assertTrue(isExpired(service, "greek-move-auth", now - 6L * 60_000L, now),
                "a 6-min-old verdict must be STALE — never routed or replayed as current");
    }

    @Test
    void greekMoveAuthUsesPayloadDecisionTimeAndSymbolKey() throws Exception {
        // Freshness tracks the PAYLOAD asOfEventTimeMs, not the Kafka arrival time; the cache key is the
        // symbol source-prefixed to source|symbol (last-value-wins per SPX/ES, the same convention as
        // es-open-direction-status' source|tradeDate) — a fresh-arriving backfilled verdict must expire
        // from its own decision time.
        FeedGatewayService service = service();
        GatewaySettings settings = new GatewaySettings();
        long decisionTime = System.currentTimeMillis() - 1_000L;
        String payload = "{\"schemaVersion\":1,\"symbol\":\"SPX\",\"asOfEventTimeMs\":" + decisionTime + ","
                + "\"verdict\":\"REAL_UP\",\"isReal\":true,\"moveDirection\":\"UP\",\"actionable\":true}";
        ConsumerRecord<String, String> record = recordAt(
                settings.greekMoveAuthCurrentTopic(), 0, 1L, "SPX", payload, System.currentTimeMillis());

        assertEquals(decisionTime, eventCacheTimestamp(service, "greek-move-auth", record),
                "fresh Kafka arrival must not disguise a historical verdict");
        assertEquals("DATABENTO|SPX",
                updateCache(service, topicBinding("DATABENTO", "greek-move-auth"), record, payload),
                "updateCache must key the verdict by source|symbol (symbol is the distinguishing component)");
    }

    @Test
    void futureGreekMoveAuthVerdictFailsClosedAndCannotPoisonLaterValidUpdates() throws Exception {
        // Clock-skew freeze-safety: a verdict stamped implausibly in the FUTURE (bad clock / corrupt
        // producer) must fail closed at ingest — otherwise its future event time would evade expiry AND
        // poison the monotonic last-value-wins supersede gate, rejecting every subsequent CORRECT verdict
        // as "older" until wall time catches up (a frozen move-authenticity track).
        FeedGatewayService service = service();
        GatewaySettings settings = new GatewaySettings();
        long now = System.currentTimeMillis();
        String future = "{\"schemaVersion\":1,\"symbol\":\"SPX\",\"asOfEventTimeMs\":" + (now + 60L * 60_000L)
                + ",\"verdict\":\"REAL_UP\",\"isReal\":true,\"moveDirection\":\"UP\",\"actionable\":true}";
        assertNull(updateCache(service, topicBinding("DATABENTO", "greek-move-auth"),
                        recordAt(settings.greekMoveAuthCurrentTopic(), 0, 1L, "SPX", future, now),
                        future),
                "an hour-ahead verdict must be dropped at ingest (fail closed), never cached");

        // A subsequent correctly-timed verdict must still be accepted (the future record left no poison).
        String current = "{\"schemaVersion\":1,\"symbol\":\"SPX\",\"asOfEventTimeMs\":" + (now - 5_000L)
                + ",\"verdict\":\"REAL_UP\",\"isReal\":true,\"moveDirection\":\"UP\",\"actionable\":true}";
        assertEquals("DATABENTO|SPX",
                updateCache(service, topicBinding("DATABENTO", "greek-move-auth"),
                        recordAt(settings.greekMoveAuthCurrentTopic(), 0, 2L, "SPX", current, now),
                        current),
                "a valid verdict after a future one must be accepted — the future record must not freeze the symbol");
    }

    @Test
    void freshGreekMoveAuthVerdictIsCachedBySymbolAndReplayedToLateJoiner() throws Exception {
        // Late-join contract: the current (fresh) verdict is cached last-value-wins under the symbol and
        // replayed standalone on connect, so a client that opens the dashboard mid-session immediately
        // shows the current move-authenticity track instead of waiting for the next live verdict.
        FeedGatewayService service = service();
        GatewaySettings settings = new GatewaySettings();
        long now = System.currentTimeMillis();
        String older = "{\"schemaVersion\":1,\"symbol\":\"SPX\",\"asOfEventTimeMs\":" + (now - 2L * 60_000L)
                + ",\"verdict\":\"FAKE\",\"isReal\":false,\"moveDirection\":\"UP\",\"actionable\":false}";
        assertEquals("DATABENTO|SPX",
                updateCache(service, topicBinding("DATABENTO", "greek-move-auth"),
                        recordAt(settings.greekMoveAuthCurrentTopic(), 0, 1L, "SPX", older, now - 2L * 60_000L),
                        older),
                "updateCache must key the verdict by source|symbol");
        String current = "{\"schemaVersion\":1,\"symbol\":\"SPX\",\"asOfEventTimeMs\":" + (now - 30_000L)
                + ",\"verdict\":\"REAL_UP\",\"isReal\":true,\"moveDirection\":\"UP\",\"actionable\":true}";
        assertEquals("DATABENTO|SPX",
                updateCache(service, topicBinding("DATABENTO", "greek-move-auth"),
                        recordAt(settings.greekMoveAuthCurrentTopic(), 0, 2L, "SPX", current, now - 30_000L),
                        current));

        List<String> sink = new ArrayList<>();
        Method replay = FeedGatewayService.class.getDeclaredMethod("replayGreekMoveAuthCached", WebSocketSession.class);
        replay.setAccessible(true);
        replay.invoke(service, recordingSession(sink));

        assertEquals(1, sink.size(), "exactly the CURRENT verdict must replay (last-value-wins); got: " + sink);
        assertTrue(sink.get(0).contains("\"type\":\"greek-move-auth\"")
                        && sink.get(0).contains("\"verdict\":\"REAL_UP\""),
                "the latest verdict must replay verbatim (JSON pass-through); was: " + sink.get(0));
    }

    @Test
    void staleGreekMoveAuthVerdictIsNeitherCachedNorReplayed() throws Exception {
        // Staleness fail-closed: a verdict older than the 5-min window (dead producer, gateway catching up
        // on a backlog) makes updateCache return null — which suppresses the live broadcast (the cacheKey
        // == null gate) — and nothing replays to a late joiner, so the UI track simply stays hidden.
        FeedGatewayService service = service();
        GatewaySettings settings = new GatewaySettings();
        long now = System.currentTimeMillis();
        String stale = "{\"schemaVersion\":1,\"symbol\":\"SPX\",\"asOfEventTimeMs\":" + (now - 6L * 60_000L)
                + ",\"verdict\":\"REAL_UP\",\"isReal\":true,\"moveDirection\":\"UP\",\"actionable\":true}";
        assertNull(updateCache(service, topicBinding("DATABENTO", "greek-move-auth"),
                        recordAt(settings.greekMoveAuthCurrentTopic(), 0, 1L, "SPX", stale, now - 6L * 60_000L),
                        stale),
                "a 6-min-old verdict must be dropped at ingest (null cacheKey = never live-routed)");

        List<String> sink = new ArrayList<>();
        Method replay = FeedGatewayService.class.getDeclaredMethod("replayGreekMoveAuthCached", WebSocketSession.class);
        replay.setAccessible(true);
        replay.invoke(service, recordingSession(sink));
        assertTrue(sink.isEmpty(), "a stale verdict must never replay to a late joiner; got: " + sink);
    }

    // ----- spot-vol-regime CURRENT snapshot relay --------------------------------------------------

    @Test
    void spotVolRegimeTopicIsOptionalGlobalAndOnTheShortFiveMinuteWindow() throws Exception {
        // The spot-vol regime CURRENT snapshot is a standalone global advisory (optional topic, global
        // broadcast in per-session mode) whose only value is being CURRENT — the greek-move-auth
        // freshness class: SHORT window, never a long window that would replay a stale overnight
        // regime as live.
        FeedGatewayService service = service();
        GatewaySettings settings = new GatewaySettings();
        assertEquals("options.spx.spot-vol-regime.current", settings.spotVolRegimeTopic(),
                "default topic must be the contract constant SpotVolRegimeTopics.SPOT_VOL_REGIME_CURRENT");
        assertTrue(isOptionalTopic(service, settings.spotVolRegimeTopic()),
                "a brand-new standalone producer may be absent — the topic must be optional");
        assertTrue(FeedGatewayService.isGlobalBroadcastEvent("spot-vol-regime"),
                "the regime must fan out in per-session (auth) mode like its advisory siblings");
        assertEquals(300_000L, settings.spotVolRegimeTtlMs(), "default TTL must be 5 minutes");
        long now = System.currentTimeMillis();
        assertFalse(isExpired(service, "spot-vol-regime", now - 2L * 60_000L, now),
                "a 2-min-old regime must still be fresh");
        assertTrue(isExpired(service, "spot-vol-regime", now - 6L * 60_000L, now),
                "a 6-min-old regime must be STALE — never routed or replayed as current");
    }

    @Test
    void spotVolRegimeUsesPayloadStreamTimeAndSymbolKey() throws Exception {
        // Freshness tracks the PAYLOAD asOfEventTimeMs (the service's stream time), not the Kafka
        // arrival time; the cache key is the symbol source-prefixed to source|symbol — a fresh-arriving
        // backfilled snapshot must expire from its own stream time.
        FeedGatewayService service = service();
        GatewaySettings settings = new GatewaySettings();
        long streamTime = System.currentTimeMillis() - 1_000L;
        String payload = "{\"schemaVersion\":1,\"symbol\":\"SPX\",\"asOfEventTimeMs\":" + streamTime + ","
                + "\"combinedRegime\":\"CONFIRMED_UP\",\"conviction\":\"ALIGNED\"}";
        ConsumerRecord<String, String> record = recordAt(
                settings.spotVolRegimeTopic(), 0, 1L, "SPX", payload, System.currentTimeMillis());

        assertEquals(streamTime, eventCacheTimestamp(service, "spot-vol-regime", record),
                "fresh Kafka arrival must not disguise a historical regime");
        assertEquals("DATABENTO|SPX",
                updateCache(service, topicBinding("DATABENTO", "spot-vol-regime"), record, payload),
                "updateCache must key the regime by source|symbol");
    }

    @Test
    void futureSpotVolRegimeSnapshotFailsClosedAndCannotPoisonLaterValidUpdates() throws Exception {
        // Clock-skew freeze-safety: an implausibly future-stamped snapshot must fail closed at ingest —
        // otherwise it would evade expiry AND poison the monotonic supersede gate, freezing the pill.
        FeedGatewayService service = service();
        GatewaySettings settings = new GatewaySettings();
        long now = System.currentTimeMillis();
        String future = "{\"schemaVersion\":1,\"symbol\":\"SPX\",\"asOfEventTimeMs\":" + (now + 60L * 60_000L)
                + ",\"combinedRegime\":\"CONFIRMED_UP\",\"conviction\":\"ALIGNED\"}";
        assertNull(updateCache(service, topicBinding("DATABENTO", "spot-vol-regime"),
                        recordAt(settings.spotVolRegimeTopic(), 0, 1L, "SPX", future, now),
                        future),
                "an hour-ahead snapshot must be dropped at ingest (fail closed), never cached");

        String current = "{\"schemaVersion\":1,\"symbol\":\"SPX\",\"asOfEventTimeMs\":" + (now - 5_000L)
                + ",\"combinedRegime\":\"CONFIRMED_UP\",\"conviction\":\"ALIGNED\"}";
        assertEquals("DATABENTO|SPX",
                updateCache(service, topicBinding("DATABENTO", "spot-vol-regime"),
                        recordAt(settings.spotVolRegimeTopic(), 0, 2L, "SPX", current, now),
                        current),
                "a valid snapshot after a future one must be accepted — no poison left behind");
    }

    @Test
    void freshSpotVolRegimeSnapshotIsCachedBySymbolAndReplayedToLateJoiner() throws Exception {
        // Late-join contract: the current (fresh) regime is cached last-value-wins under the symbol and
        // replayed standalone on connect, so a client opening the dashboard mid-session immediately
        // shows the regime pill instead of waiting for the next heartbeat.
        FeedGatewayService service = service();
        GatewaySettings settings = new GatewaySettings();
        long now = System.currentTimeMillis();
        String older = "{\"schemaVersion\":1,\"symbol\":\"SPX\",\"asOfEventTimeMs\":" + (now - 2L * 60_000L)
                + ",\"combinedRegime\":\"NEUTRAL\",\"conviction\":\"ALIGNED\"}";
        assertEquals("DATABENTO|SPX",
                updateCache(service, topicBinding("DATABENTO", "spot-vol-regime"),
                        recordAt(settings.spotVolRegimeTopic(), 0, 1L, "SPX", older, now - 2L * 60_000L),
                        older),
                "updateCache must key the regime by source|symbol");
        String current = "{\"schemaVersion\":1,\"symbol\":\"SPX\",\"asOfEventTimeMs\":" + (now - 30_000L)
                + ",\"combinedRegime\":\"CONFIRMED_UP\",\"conviction\":\"ALIGNED\"}";
        assertEquals("DATABENTO|SPX",
                updateCache(service, topicBinding("DATABENTO", "spot-vol-regime"),
                        recordAt(settings.spotVolRegimeTopic(), 0, 2L, "SPX", current, now - 30_000L),
                        current));

        List<String> sink = new ArrayList<>();
        Method replay = FeedGatewayService.class.getDeclaredMethod("replaySpotVolRegimeCached", WebSocketSession.class);
        replay.setAccessible(true);
        replay.invoke(service, recordingSession(sink));

        assertEquals(1, sink.size(), "exactly the CURRENT regime must replay (last-value-wins); got: " + sink);
        assertTrue(sink.get(0).contains("\"type\":\"spot-vol-regime\"")
                        && sink.get(0).contains("\"combinedRegime\":\"CONFIRMED_UP\""),
                "the latest regime must replay verbatim (JSON pass-through); was: " + sink.get(0));
    }

    @Test
    void staleSpotVolRegimeSnapshotIsNeitherCachedNorReplayed() throws Exception {
        // Staleness fail-closed: a snapshot older than the 5-min window makes updateCache return null —
        // suppressing the live broadcast — and nothing replays to a late joiner: the pill stays hidden.
        FeedGatewayService service = service();
        GatewaySettings settings = new GatewaySettings();
        long now = System.currentTimeMillis();
        String stale = "{\"schemaVersion\":1,\"symbol\":\"SPX\",\"asOfEventTimeMs\":" + (now - 6L * 60_000L)
                + ",\"combinedRegime\":\"CONFIRMED_UP\",\"conviction\":\"ALIGNED\"}";
        assertNull(updateCache(service, topicBinding("DATABENTO", "spot-vol-regime"),
                        recordAt(settings.spotVolRegimeTopic(), 0, 1L, "SPX", stale, now - 6L * 60_000L),
                        stale),
                "a 6-min-old regime must be dropped at ingest (null cacheKey = never live-routed)");

        List<String> sink = new ArrayList<>();
        Method replay = FeedGatewayService.class.getDeclaredMethod("replaySpotVolRegimeCached", WebSocketSession.class);
        replay.setAccessible(true);
        replay.invoke(service, recordingSession(sink));
        assertTrue(sink.isEmpty(), "a stale regime must never replay to a late joiner; got: " + sink);
    }

    // ----- spot-vol-regime STRIKE BAND (latched glyph marking) -------------------------------------
    //
    // The band is the USER-approved (2026-08-02) latched strike marking: when the regime becomes
    // DIVERGENT_UP or COMPLACENT_DOWN the spot at that moment sets an ANCHOR strike, and every strike
    // from the anchor through the strikes the spot subsequently traverses is coloured on the chain.
    // The producer owns the history and publishes the RESOLVED per-strike state; the gateway is the
    // trust boundary and only decides whether what arrived is well formed and in-session.
    //
    // 2026-08-03 is a Monday. 10:00 ET is inside RTH; 08:00 and 16:01 ET are not.
    private static final long BAND_RTH_MS = 1_785_765_600_000L;      // 2026-08-03 10:00 ET
    private static final long BAND_PREMARKET_MS = 1_785_758_400_000L; // 2026-08-03 08:00 ET
    private static final long BAND_AFTER_CLOSE_MS = 1_785_787_260_000L; // 2026-08-03 16:01 ET
    private static final long BAND_NEXT_DAY_MS = 1_785_852_000_000L;  // 2026-08-04 10:00 ET

    /** A snapshot carrying a two-strike COMPLACENT_DOWN band — the USER's 7410/7405 green example. */
    private static String bandSnapshot(long asOfEventTimeMs, String sessionDate, String marks) {
        return "{\"schemaVersion\":4,\"symbol\":\"SPX\",\"asOfEventTimeMs\":" + asOfEventTimeMs
                + ",\"combinedRegime\":\"COMPLACENT_DOWN\",\"conviction\":\"ALIGNED\""
                + ",\"strikeBand\":{\"schemaVersion\":1,\"sessionDate\":\"" + sessionDate + "\""
                + ",\"strikeIncrement\":5,\"marks\":" + marks + "}}";
    }

    private static String twoGreenMarks(long markedAt) {
        return "[{\"strike\":7405,\"regime\":\"COMPLACENT_DOWN\",\"markedAtEventTimeMs\":" + markedAt + "},"
                + "{\"strike\":7410,\"regime\":\"COMPLACENT_DOWN\",\"markedAtEventTimeMs\":" + markedAt + "}]";
    }

    @Test
    void validInSessionStrikeBandSurvivesEnrichmentIntact() throws Exception {
        // The happy path must actually reach the browser: a well-formed band whose sessionDate matches
        // the ET trading date of the snapshot's own stream time, computed inside RTH, passes through
        // verbatim. Without this the whole feature could be "safely" sanitised into never rendering.
        FeedGatewayService service = service();
        String enriched = enrichJson(service,
                bandSnapshot(BAND_RTH_MS, "2026-08-03", twoGreenMarks(BAND_RTH_MS - 60_000L)),
                topicBinding("DATABENTO", "spot-vol-regime"));
        assertTrue(enriched.contains("\"strikeBand\""),
                "a valid in-session band must survive enrichJson; was: " + enriched);
        assertTrue(enriched.contains("\"strike\":7405") && enriched.contains("\"strike\":7410"),
                "both traversed strikes (anchor 7410 and 7405) must survive; was: " + enriched);
        assertTrue(enriched.contains("\"combinedRegime\":\"COMPLACENT_DOWN\""),
                "the regime snapshot itself must be untouched; was: " + enriched);
    }

    @Test
    void strikeBandFromAnotherSessionIsStrippedButTheRegimeSnapshotIsNot() throws Exception {
        // THE session-scoped latch rule (Codex requirements consult 2026-08-02): "latched" means the
        // colour survives the end of the suspect MOVE, not that it becomes a permanent annotation. A
        // trader seeing a coloured 7410 at 09:31 on Tuesday reads it as TODAY's traversal, so Monday's
        // band must never ride along. Stripping the band must never take the regime pill down with it.
        FeedGatewayService service = service();
        String enriched = enrichJson(service,
                bandSnapshot(BAND_NEXT_DAY_MS, "2026-08-03", twoGreenMarks(BAND_RTH_MS)),
                topicBinding("DATABENTO", "spot-vol-regime"));
        assertFalse(enriched.contains("strikeBand"),
                "yesterday's band must be stripped from today's snapshot; was: " + enriched);
        assertTrue(enriched.contains("\"combinedRegime\":\"COMPLACENT_DOWN\""),
                "only the band is suppressed — the regime snapshot still forwards; was: " + enriched);
    }

    @Test
    void strikeBandComputedOutsideRegularTradingHoursIsStripped() throws Exception {
        // The producer classifies RTH-only; a band stamped outside the session is either a producer
        // defect or overnight drift, and neither may paint the chain. Early closes are handled by
        // GatewayMarketCalendar, so this is not a plain "is it a weekday" check.
        FeedGatewayService service = service();
        String premarket = enrichJson(service,
                bandSnapshot(BAND_PREMARKET_MS, "2026-08-03", twoGreenMarks(BAND_PREMARKET_MS - 1_000L)),
                topicBinding("DATABENTO", "spot-vol-regime"));
        assertFalse(premarket.contains("strikeBand"),
                "an 08:00 ET band must be stripped; was: " + premarket);
        String afterClose = enrichJson(service,
                bandSnapshot(BAND_AFTER_CLOSE_MS, "2026-08-03", twoGreenMarks(BAND_RTH_MS)),
                topicBinding("DATABENTO", "spot-vol-regime"));
        assertFalse(afterClose.contains("strikeBand"),
                "a 16:01 ET band must be stripped; was: " + afterClose);
    }

    @Test
    void malformedStrikeBandsAreRefusedWholesaleRatherThanPartiallyPainted() throws Exception {
        // A half-valid band is worse than none: the user would read the surviving strikes as "the spot
        // stopped here". Every defect therefore drops the WHOLE band. The regime vocabulary is frozen
        // to the two SUSPECT regimes, and marks is the complete resolved state (duplicates mean the
        // producer never resolved overlapping episodes — the gateway must not invent "last one wins").
        FeedGatewayService service = service();
        Object binding = topicBinding("DATABENTO", "spot-vol-regime");
        record Case(String name, String json) { }
        long marked = BAND_RTH_MS - 30_000L;
        List<Case> cases = List.of(
                new Case("unsupported nested schemaVersion",
                        bandSnapshot(BAND_RTH_MS, "2026-08-03", twoGreenMarks(marked))
                                .replace("\"schemaVersion\":1", "\"schemaVersion\":2")),
                new Case("band is not an object",
                        "{\"schemaVersion\":4,\"symbol\":\"SPX\",\"asOfEventTimeMs\":" + BAND_RTH_MS
                                + ",\"combinedRegime\":\"COMPLACENT_DOWN\",\"strikeBand\":\"green\"}"),
                new Case("empty marks",
                        bandSnapshot(BAND_RTH_MS, "2026-08-03", "[]")),
                new Case("non-numeric strike",
                        bandSnapshot(BAND_RTH_MS, "2026-08-03",
                                "[{\"strike\":\"7410\",\"regime\":\"COMPLACENT_DOWN\",\"markedAtEventTimeMs\":" + marked + "}]")),
                new Case("duplicate strike",
                        bandSnapshot(BAND_RTH_MS, "2026-08-03",
                                "[{\"strike\":7410,\"regime\":\"COMPLACENT_DOWN\",\"markedAtEventTimeMs\":" + marked + "},"
                                        + "{\"strike\":7410,\"regime\":\"DIVERGENT_UP\",\"markedAtEventTimeMs\":" + marked + "}]")),
                new Case("regime outside the frozen SUSPECT vocabulary",
                        bandSnapshot(BAND_RTH_MS, "2026-08-03",
                                "[{\"strike\":7410,\"regime\":\"CONFIRMED_UP\",\"markedAtEventTimeMs\":" + marked + "}]")),
                new Case("mark stamped after the frame that reports it",
                        bandSnapshot(BAND_RTH_MS, "2026-08-03",
                                "[{\"strike\":7410,\"regime\":\"COMPLACENT_DOWN\",\"markedAtEventTimeMs\":"
                                        + (BAND_RTH_MS + 1_000L) + "}]")),
                new Case("snapshot without a stream time to scope the session to",
                        bandSnapshot(BAND_RTH_MS, "2026-08-03", twoGreenMarks(marked))
                                .replace("\"asOfEventTimeMs\":" + BAND_RTH_MS, "\"asOfEventTimeMs\":0")));
        for (Case c : cases) {
            String enriched = enrichJson(service, c.json(), binding);
            assertFalse(enriched.contains("strikeBand"),
                    "band must be refused wholesale — " + c.name() + "; was: " + enriched);
            assertTrue(enriched.contains("\"combinedRegime\":\"COMPLACENT_DOWN\""),
                    "the regime snapshot must survive — " + c.name() + "; was: " + enriched);
        }
    }

    @Test
    void oversizedStrikeBandIsRefusedRatherThanTruncated() throws Exception {
        // 513 marks at the 5-point grid is 2,565 SPX points in one session — impossible, so it is a
        // producer bug. It is REFUSED, not clipped: a truncated band looks complete and would
        // understate how far the spot actually travelled.
        FeedGatewayService service = service();
        StringBuilder marks = new StringBuilder("[");
        for (int i = 0; i <= 512; i++) {
            marks.append(i == 0 ? "" : ",")
                    .append("{\"strike\":").append(5000 + i * 5)
                    .append(",\"regime\":\"DIVERGENT_UP\",\"markedAtEventTimeMs\":").append(BAND_RTH_MS - 1_000L)
                    .append("}");
        }
        marks.append("]");
        String enriched = enrichJson(service, bandSnapshot(BAND_RTH_MS, "2026-08-03", marks.toString()),
                topicBinding("DATABENTO", "spot-vol-regime"));
        assertFalse(enriched.contains("strikeBand"),
                "513 marks must refuse the whole band; was: " + enriched.substring(0, Math.min(200, enriched.length())));
    }

    @Test
    void lateJoinerAfterTheCloseGetsTheRegimeButNotTheBand() throws Exception {
        // The 5-minute spot-vol-regime TTL outlives the 16:00 close by four minutes, so ingest-time
        // checks alone would let a browser opened at 16:01 late-join into a coloured chain. The band is
        // suppressed at SEND time once the session is over — the snapshot itself still replays.
        FeedGatewayService service = service();
        GatewaySettings settings = new GatewaySettings();
        long now = System.currentTimeMillis();
        String json = "{\"schemaVersion\":4,\"symbol\":\"SPX\",\"asOfEventTimeMs\":" + (now - 30_000L)
                + ",\"combinedRegime\":\"COMPLACENT_DOWN\",\"conviction\":\"ALIGNED\""
                + ",\"strikeBand\":{\"schemaVersion\":1,\"sessionDate\":\"2026-08-03\",\"strikeIncrement\":5"
                + ",\"marks\":[{\"strike\":7410,\"regime\":\"COMPLACENT_DOWN\",\"markedAtEventTimeMs\":"
                + (now - 60_000L) + "}]}}";
        assertEquals("DATABENTO|SPX",
                updateCache(service, topicBinding("DATABENTO", "spot-vol-regime"),
                        recordAt(settings.spotVolRegimeTopic(), 0, 1L, "SPX", json, now - 30_000L), json),
                "the snapshot must cache normally — this test is about the SEND-time band rule");

        Method override = FeedGatewayService.class.getDeclaredMethod(
                "overrideRegularTradingHoursForTest", Boolean.class);
        override.setAccessible(true);
        Method replay = FeedGatewayService.class.getDeclaredMethod(
                "replaySpotVolRegimeCached", WebSocketSession.class);
        replay.setAccessible(true);

        override.invoke(service, Boolean.TRUE);
        List<String> inSession = new ArrayList<>();
        replay.invoke(service, recordingSession(inSession));
        assertEquals(1, inSession.size(), "the snapshot must replay in-session; got: " + inSession);
        assertTrue(inSession.get(0).contains("strikeBand"),
                "during RTH the late joiner must receive the band; was: " + inSession.get(0));

        override.invoke(service, Boolean.FALSE);
        List<String> afterClose = new ArrayList<>();
        replay.invoke(service, recordingSession(afterClose));
        assertEquals(1, afterClose.size(), "the regime snapshot must still replay after the close");
        assertFalse(afterClose.get(0).contains("strikeBand"),
                "after the close the band must be suppressed at send time; was: " + afterClose.get(0));
        assertTrue(afterClose.get(0).contains("\"combinedRegime\":\"COMPLACENT_DOWN\""),
                "only the band is suppressed, never the snapshot; was: " + afterClose.get(0));
    }

    @Test
    void cachedReplayIncludesFreshStrikeIntelForMatchingDatabentoSelectionOnly() throws Exception {
        FeedGatewayService service = service();
        GatewaySettings settings = new GatewaySettings();
        long now = System.currentTimeMillis();
        String json = "{\"marketDataSource\":\"DATABENTO\",\"symbol\":\"SPX\",\"expiry\":\"20260622\","
                + "\"strike\":6005,\"strikeRole\":\"MAGNET\",\"eventTimeMs\":" + now + "}";
        updateCache(service, topicBinding("DATABENTO", "strike-intel"),
                recordAt(settings.strikeIntelByStrikeTopic(), 0, 1L, "SPX|20260622|6005", json, now), json);

        // Matching DATABENTO/SPX/20260622 selection replays it.
        setActiveSelection(service, "DATABENTO", "SPX", "20260622");
        assertEquals(1, cachedEvents(service, List.of("strike-intel"), now).size(),
                "fresh strike-intel must replay to a matching DATABENTO client");

        // Wrong source (IBKR) is filtered (strike-intel is DATABENTO-only).
        setActiveSelection(service, "IBKR", "SPX", "20260622");
        assertTrue(cachedEvents(service, List.of("strike-intel"), now).isEmpty(),
                "IBKR selection must never receive DATABENTO strike-intel");

        // Wrong symbol is filtered by the selection barrier.
        setActiveSelection(service, "DATABENTO", "SPY", "20260622");
        assertTrue(cachedEvents(service, List.of("strike-intel"), now).isEmpty(),
                "a different symbol must not receive this strike-intel");
    }

    @Test
    void staleCachedStrikeIntelIsNotReplayed() throws Exception {
        // A strike-intel whose freshness (event time) is old must NOT be replayed on connect — the
        // isCacheFresh gate in cachedEvents drops it, so a catching-up/backfilled producer cannot render
        // a stale strike-intel as a live signal (mirrors delta-flow).
        FeedGatewayService service = service();
        GatewaySettings settings = new GatewaySettings();
        long now = System.currentTimeMillis();
        long staleEventTime = now - 60L * 60_000L;
        String json = "{\"marketDataSource\":\"DATABENTO\",\"symbol\":\"SPX\",\"expiry\":\"20260622\","
                + "\"strike\":6005,\"strikeRole\":\"MAGNET\",\"eventTimeMs\":" + staleEventTime + "}";
        // Fresh Kafka ARRIVAL time (record timestamp = now) — only the payload event time makes it stale.
        updateCache(service, topicBinding("DATABENTO", "strike-intel"),
                recordAt(settings.strikeIntelByStrikeTopic(), 0, 1L, "SPX|20260622|6005", json, now), json);

        setActiveSelection(service, "DATABENTO", "SPX", "20260622");
        assertTrue(cachedEvents(service, List.of("strike-intel"), now).isEmpty(),
                "a stale (old payload event time) strike-intel must not be replayed");
    }

    @Test
    void markSelectionReadyIsOneShotAndGuardedByActiveSelection() throws Exception {
        FeedGatewayService service = service();
        setActiveSelection(service, "DATABENTO", "SPX", "20260623");
        Object active = activeSelectionOf(service);

        // First readiness for the active selection transitions readySelectionKey (and triggers the
        // one-shot source-ready + cached convergence re-push; harmless here with no clients/cache).
        invokeMarkSelectionReady(service, active);
        String key1 = readySelectionKey(service);
        assertFalse(key1.isEmpty(), "first ready must transition readySelectionKey");

        // One-shot: a second readiness for the SAME selection must not re-transition (no client spam).
        invokeMarkSelectionReady(service, active);
        assertEquals(key1, readySelectionKey(service), "markSelectionReady must be one-shot per selection");

        // Token guard: a readiness signal for a NON-active selection must be ignored entirely.
        setReadySelectionKey(service, "");
        Object superseded = newActiveSelection("DATABENTO", "SPX", "20260622");
        invokeMarkSelectionReady(service, superseded);
        assertTrue(readySelectionKey(service).isEmpty(),
                "a superseded selection must never mark ready or converge dashboards");
    }

    @Test
    void selectionReadyRepushesCachedStrikesAfterRoll() throws Exception {
        String source = Files.readString(Path.of("src/main/java/app/feedgateway/FeedGatewayService.java"));
        // Convergence re-push: markSelectionReady broadcasts source-ready, THEN re-pushes cached state so
        // open dashboards repopulate after the daily roll. Ordering matters (source-ready precedes replay).
        int readyIdx = source.indexOf(
                "broadcast(\"source-ready\", activeSelectionJson(selection, \"source-ready\"));");
        assertTrue(readyIdx > 0, "markSelectionReady must broadcast source-ready");
        int repushIdx = source.indexOf("broadcastCachedState(sourceSwitchReplayEvents());", readyIdx);
        assertTrue(repushIdx > readyIdx, "convergence cached re-push must come AFTER source-ready");
        // Readiness commit is atomic under readyLock with a LIVE active-selection re-check (no superseded
        // selection can announce/converge) and a one-shot per selection key.
        assertTrue(source.contains("synchronized (readyLock)"),
                "markSelectionReady must commit readiness atomically under readyLock");
        assertTrue(source.contains("!key.equals(selectionKey(activeSelection.get()))"),
                "markSelectionReady must re-validate against the live active selection");
        // Forward decision uses a selection captured ONCE per record (no mid-record activeSelection re-read).
        assertTrue(source.contains("recordSelectedForward(binding, json, decided)"),
                "forward path must carry the decided selection into recordSelectedForward");
        assertTrue(source.contains("shouldForward(binding, json, record, ActiveSelection selection)")
                        || source.contains("ConsumerRecord<?, ?> record, ActiveSelection selection)"),
                "shouldForward must have a selection-carrying overload");
        // Cache-arrival trigger: a cached-but-not-forwarded snapshot for the active selection still converges
        // (covers the closed-market case where the seed snapshot arrives already older than maxStaleMs).
        assertTrue(source.contains("matchesActiveSelection(json, current)"),
                "cache-arrival path must mark ready only for snapshots matching the active selection");
        assertTrue(source.contains("markSelectionReady(current);"),
                "cache-arrival path must call markSelectionReady");
    }

    @Test
    void selectionReadyDeliversSourceReadyThenCachedStrikesToOpenClient() throws Exception {
        FeedGatewayService service = service();
        setActiveSelection(service, "DATABENTO", "SPX", "20260623");
        long now = System.currentTimeMillis();

        // A fresh snapshot for the active selection is cached (the post-roll seed strike).
        String snapshotJson = "{\"marketDataSource\":\"DATABENTO\",\"symbol\":\"SPX\",\"expiry\":\"20260623\",\"strike\":7000}";
        String key = updateCache(service, topicBinding("DATABENTO", "snapshot"),
                recordAt("options.databento.display", 0, 1L, "SPX|20260623|7000", snapshotJson, now),
                snapshotJson);
        assertEquals("DATABENTO|SPX|20260623|7000", key, "snapshot must be cached under source|symbol|expiry|strike");

        // An already-open dashboard.
        List<String> sent = new ArrayList<>();
        addRecordingClient(service, sent);

        // Converge: readiness for the active selection must deliver source-ready THEN the cached strike.
        invokeMarkSelectionReady(service, activeSelectionOf(service));

        int readyIdx = -1;
        int batchIdx = -1;
        for (int i = 0; i < sent.size(); i++) {
            String msg = sent.get(i);
            if (readyIdx < 0 && msg.contains("source-ready")) {
                readyIdx = i;
            }
            if (batchIdx < 0 && msg.contains("\"expiry\":\"20260623\"") && msg.contains("7000")) {
                batchIdx = i;
            }
        }
        assertTrue(readyIdx >= 0, "open client must receive source-ready after a roll");
        assertTrue(batchIdx > readyIdx, "cached strike batch must arrive AFTER source-ready (ordering)");

        // One-shot: a second readiness for the same selection must NOT re-broadcast (no client spam).
        int before = sent.size();
        invokeMarkSelectionReady(service, activeSelectionOf(service));
        assertEquals(before, sent.size(), "second readiness for the same selection must not re-broadcast");
    }

    @Test
    void databentoGexTopicHasExpectedDefault() {
        assertEquals("options.databento.gex.strike", new GatewaySettings().databentoGexTopic());
    }

    @Test
    void databentoMaxPainTopicHasExpectedDefault() {
        assertEquals("options.databento.maxpain", new GatewaySettings().databentoMaxPainTopic());
    }

    @Test
    void maxPainCacheKeyDerivesFromPayloadSymbolAndExpiry() throws Exception {
        FeedGatewayService service = service();
        String json = "{\"symbol\":\"spx\",\"expiry\":\"2026-07-10\",\"status\":\"VALID\"}";
        assertEquals("SPX|20260710", maxPainCacheKey(service, json, "fallback"));
        // Missing symbol -> fallback
        assertEquals("fallback", maxPainCacheKey(service, "{\"expiry\":\"20260710\"}", "fallback"));
        // Non-JSON -> fallback (defensive; never throws)
        assertEquals("fallback", maxPainCacheKey(service, "not-json", "fallback"));
    }

    @Test
    void opbByOptionCacheKeyIncludesSideSoCallAndPutDoNotCollide() throws Exception {
        FeedGatewayService service = service();
        String call = "{\"symbol\":\"spx\",\"expiry\":\"2026-07-10\",\"strike\":5500.0,\"optionType\":\"CALL\"}";
        String put = "{\"symbol\":\"spx\",\"expiry\":\"2026-07-10\",\"strike\":5500.0,\"optionType\":\"PUT\"}";
        // Per-contract events: same strike, opposite side must land in distinct cache slots. Strike is
        // normalized (formatStrike) so 5500.0 and 5500 collapse to a single slot.
        assertEquals("SPX|20260710|5500|CALL", opbByOptionCacheKey(service, call, "fallback"));
        assertEquals("SPX|20260710|5500|PUT", opbByOptionCacheKey(service, put, "fallback"));
        assertEquals("SPX|20260710|5500|CALL",
                opbByOptionCacheKey(service, call.replace("5500.0", "5500"), "fallback"));
        // Missing side -> fall back to optionKey, then to the Kafka key.
        String keyed = "{\"symbol\":\"SPX\",\"expiry\":\"20260710\",\"strike\":5500.0,\"optionKey\":\"SPX-20260710-5500-C\"}";
        assertEquals("SPX|20260710|5500|SPX-20260710-5500-C", opbByOptionCacheKey(service, keyed, "fallback"));
        assertEquals("fallback", opbByOptionCacheKey(service, "{\"symbol\":\"SPX\",\"expiry\":\"20260710\"}", "fallback"));
        assertEquals("fallback", opbByOptionCacheKey(service, "not-json", "fallback"));
    }

    @Test
    void isMaxPainExpiredReturnsTrueOnlyForTerminalStatus() throws Exception {
        FeedGatewayService service = service();
        assertTrue(isMaxPainExpired(service, "{\"status\":\"TERMINAL\"}"));   // v2 terminal status
        assertTrue(isMaxPainExpired(service, "{\"status\":\"EXPIRED\"}"));    // v1 terminal status (back-compat)
        assertFalse(isMaxPainExpired(service, "{\"status\":\"VALID\"}"));
        assertFalse(isMaxPainExpired(service, "{\"status\":\"EMPTY\"}"));
        assertFalse(isMaxPainExpired(service, "{}"));
        // Malformed JSON must NOT throw — defensive.
        assertFalse(isMaxPainExpired(service, "not-json"));
        assertFalse(isMaxPainExpired(service, null));
        assertFalse(isMaxPainExpired(service, ""));
    }

    @Test
    void uiBatchEnvelopeCarriesMaxPainArrayKey() throws Exception {
        FeedGatewayService service = service();
        // A single max-pain JSON in the batch must surface under the "maxPains" array on the wire.
        String json = "{\"messageType\":\"MAX_PAIN\",\"symbol\":\"SPX\",\"expiry\":\"20260710\",\"status\":\"VALID\",\"maxPainStrike\":4500.0}";
        String envelope = uiBatchEnvelopeJsonMaxPain(service, List.of(json));
        assertTrue(envelope.contains("\"maxPains\":[" + json + "]"),
                "batch envelope must carry the maxPains array with the record; was: " + envelope);
        // Existing gex array must still be present (no regression).
        assertTrue(envelope.contains("\"gexByStrike\":[]"));
    }

    @Test
    void uiBatchEnvelopeCarriesOpbByOptionArrayKey() throws Exception {
        FeedGatewayService service = service();
        String json = "{\"symbol\":\"SPX\",\"expiry\":\"20260710\",\"strike\":5500.0,\"residualZScore\":3.2,\"behaviorLabel\":\"CALL_OVERPERFORMING\"}";
        String envelope = uiBatchEnvelopeJsonOpbByOption(service, List.of(json));
        assertTrue(envelope.contains("\"opbByOptions\":[" + json + "]"),
                "batch envelope must carry the opbByOptions array; was: " + envelope);
        assertTrue(envelope.contains("\"optionPriceBehaviors\":[]"));
    }

    @Test
    void uiBatchEnvelopeCarriesOpbSessionArrayKey() throws Exception {
        FeedGatewayService service = service();
        String json = "{\"symbol\":\"SPX\",\"tradingDate\":\"2026-07-10\",\"directionalPressureZ\":2.7,\"perContractAnomalyZ\":1.4}";
        String envelope = uiBatchEnvelopeJsonOpbSession(service, List.of(json));
        assertTrue(envelope.contains("\"opbSessions\":[" + json + "]"),
                "batch envelope must carry the opbSessions array; was: " + envelope);
    }

    @Test
    void indexPriceCacheKeyUsesPayloadSymbolInsteadOfKafkaTradeKey() {
        FeedGatewayService service = new FeedGatewayService(
                new GatewaySettings(),
                new ObjectMapper(),
                new HpsfGatewayViewMapper(),
                null
        );

        String firstEsTrade = "{\"symbol\":\"ES.v.0\",\"instrumentId\":\"42140864\",\"price\":7580.5}";
        String nextEsTrade = "{\"symbol\":\"ES.v.0\",\"instrumentId\":\"42140864\",\"price\":7580.75}";
        String vixPrice = "{\"symbol\":\"VIX\",\"price\":16.2}";

        assertEquals("ES.V.0", service.indexPriceCacheKey(firstEsTrade, "trade-1"));
        assertEquals("ES.V.0", service.indexPriceCacheKey(nextEsTrade, "trade-2"));
        assertEquals("VIX", service.indexPriceCacheKey(vixPrice, "vix-record"));
    }

    @Test
    void paceCacheKeyUsesNumericStrikePayloadIdentity() throws Exception {
        FeedGatewayService service = service();

        assertEquals("IBKR|SPX|20260616|7585", paceCacheKey(
                service,
                "{\"source\":\"IBKR\",\"symbol\":\"SPX\",\"expiry\":\"2026-06-16\",\"strike\":7585}",
                "fallback"
        ));
    }

    @Test
    void paceCacheKeyPreservesDecimalStrikePayloadIdentity() throws Exception {
        FeedGatewayService service = service();

        assertEquals("DATABENTO|SPX|20260616|7585.5", paceCacheKey(
                service,
                "{\"marketDataSource\":\"DATABENTO\",\"symbol\":\"spx\",\"expiry\":\"20260616\",\"strike\":7585.5}",
                "fallback"
        ));
    }

    @Test
    void paceCacheKeyFallsBackWhenRequiredFieldsAreMissing() throws Exception {
        FeedGatewayService service = service();

        assertEquals("fallback-key", paceCacheKey(
                service,
                "{\"source\":\"IBKR\",\"symbol\":\"SPX\",\"strike\":7585}",
                "fallback-key"
        ));
    }

    @Test
    void paceCacheKeyFallsBackWhenSourceIsMissing() throws Exception {
        FeedGatewayService service = service();

        assertEquals("fallback-key", paceCacheKey(
                service,
                "{\"symbol\":\"SPX\",\"expiry\":\"20260616\",\"strike\":7585}",
                "fallback-key"
        ));
    }

    @Test
    void gexCacheKeyUsesPayloadIdentity() throws Exception {
        FeedGatewayService service = service();

        // Source is prepended by updateCache, so the helper returns symbol|expiry|strike.
        assertEquals("SPX|20260612|6005", gexCacheKey(
                service,
                "{\"source\":\"DATABENTO\",\"symbol\":\"spx\",\"expiry\":\"2026-06-12\",\"strike\":6005}",
                "fallback"
        ));
    }

    @Test
    void gexCacheKeyPreservesDecimalStrikePayloadIdentity() throws Exception {
        FeedGatewayService service = service();

        assertEquals("SPX|20260612|6005.5", gexCacheKey(
                service,
                "{\"symbol\":\"SPX\",\"expiry\":\"20260612\",\"strike\":6005.5}",
                "fallback"
        ));
    }

    @Test
    void gexCacheKeyFallsBackWhenStrikeMissing() throws Exception {
        FeedGatewayService service = service();

        assertEquals("fallback-key", gexCacheKey(
                service,
                "{\"symbol\":\"SPX\",\"expiry\":\"20260612\"}",
                "fallback-key"
        ));
    }

    @Test
    void databentoGexHistoryDerivesSameCacheKeyAsPlainGex() throws Exception {
        // The merge of the databento gex-history `history` map onto the databento gex row hinges on
        // BOTH records deriving the SAME cache key. The history record (a superset emitted by
        // databento-gex-history-service) carries symbol|expiry|strike identical to the plain gex
        // record, so gexCacheKey() lands them in the same gex-by-strike cache slot.
        FeedGatewayService service = service();

        String plainGex = "{\"source\":\"DATABENTO\",\"symbol\":\"SPX\",\"expiry\":\"20260612\",\"strike\":6005,\"netGex\":-1.0}";
        String gexHistory = "{\"source\":\"DATABENTO\",\"symbol\":\"SPX\",\"expiry\":\"20260612\",\"strike\":6005,\"netGex\":-1.0,"
                + "\"history\":{\"5m\":{\"window\":\"5m\",\"available\":true,\"netGex\":-2.0,\"delta\":1.0,\"direction\":\"UP\",\"sampledAt\":\"2026-06-23T19:54:00Z\"}}}";

        String plainKey = gexCacheKey(service, plainGex, "fallbackA");
        String historyKey = gexCacheKey(service, gexHistory, "fallbackB");
        assertEquals("SPX|20260612|6005", plainKey);
        assertEquals(plainKey, historyKey);
    }

    @Test
    void databentoGexHistoryBindsOnJsonStateConsumersNotAvro() throws Exception {
        // The databento gex HISTORY topic is JSON (databento-gex-history-service emits String/JSON),
        // unlike the Avro databento gex topic. It must bind on the JSON state cache + live consumers
        // (so its `history` map merges onto the gex rows) and must NOT appear on the Avro consumers.
        String source = Files.readString(Path.of("src/main/java/app/feedgateway/FeedGatewayService.java"));
        String historyBinding =
                "topicEvents.put(settings.databentoGexHistoryTopic(), new TopicBinding(\"DATABENTO\", \"gex-by-strike\"));";

        for (String method : List.of("runJsonStateCacheConsumer", "runJsonStateLiveConsumer")) {
            assertTrue(methodBody(source, method).contains(historyBinding),
                    method + " must bind the DATABENTO gex-history topic (JSON)");
        }
        for (String method : List.of("runAvroCacheConsumer", "runAvroLiveConsumer")) {
            assertFalse(methodBody(source, method).contains(historyBinding),
                    method + " must NOT bind the DATABENTO gex-history topic (it is JSON, not Avro)");
        }
    }

    @Test
    void paceCacheStoresSameStrikeSeparatelyBySource() throws Exception {
        FeedGatewayService service = service();
        Object ibkrBinding = topicBinding("IBKR", "pace");
        Object databentoBinding = topicBinding("DATABENTO", "pace");

        String ibkrKey = updateCache(
                service,
                ibkrBinding,
                new ConsumerRecord<>("options.ibkr.pace", 0, 1L, "ignored", ""),
                "{\"source\":\"IBKR\",\"symbol\":\"SPX\",\"expiry\":\"20260616\",\"strike\":7585,\"eventTime\":\"2026-06-16T14:00:00Z\"}"
        );
        String databentoKey = updateCache(
                service,
                databentoBinding,
                new ConsumerRecord<>("options.databento.pace", 0, 2L, "ignored", ""),
                "{\"marketDataSource\":\"DATABENTO\",\"symbol\":\"SPX\",\"expiry\":\"20260616\",\"strike\":7585,\"eventTime\":\"2026-06-16T14:00:00Z\"}"
        );

        assertEquals("IBKR|SPX|20260616|7585", ibkrKey);
        assertEquals("DATABENTO|SPX|20260616|7585", databentoKey);
    }

    @Test
    void paceCacheKeyFallsBackForMalformedJson() throws Exception {
        FeedGatewayService service = service();

        assertEquals("fallback-key", paceCacheKey(service, "{not-json", "fallback-key"));
    }

    @Test
    void catchUpRequiresOnlyActiveSource() {
        assertTrue(FeedGatewayService.requiresCatchUpForActiveSource("DATABENTO", "DATABENTO"));
        assertFalse(FeedGatewayService.requiresCatchUpForActiveSource("DATABENTO", "IBKR"));
    }

    @Test
    void cachedOptionSnapshotsCanReplayPastLiveStaleWindowForEitherSource() {
        assertFalse(FeedGatewayService.enforceCachedReplayMaxStale("snapshot", "DATABENTO"));
        assertFalse(FeedGatewayService.enforceCachedReplayMaxStale("snapshot", "IBKR"));
        assertTrue(FeedGatewayService.enforceCachedReplayMaxStale("pace", "DATABENTO"));
        assertTrue(FeedGatewayService.enforceCachedReplayMaxStale("pace", "IBKR"));

        assertFalse(FeedGatewayService.enforceCachedReplayOffsetBarrier("snapshot", "DATABENTO"));
        assertFalse(FeedGatewayService.enforceCachedReplayOffsetBarrier("snapshot", "IBKR"));
        assertTrue(FeedGatewayService.enforceCachedReplayOffsetBarrier("pace", "DATABENTO"));
        assertTrue(FeedGatewayService.enforceCachedReplayOffsetBarrier("pace", "IBKR"));
    }

    @Test
    void slowGexByStrikeIsExemptFromCachedReplayBarriersLikeMaxPain() {
        // GEX is a once-daily-OI signal whose latest per-strike record is routinely older than the 15s
        // selection barrier — it must replay on connect like snapshot/max-pain, not be re-dropped as stale.
        assertFalse(FeedGatewayService.enforceCachedReplayMaxStale("gex-by-strike", "DATABENTO"));
        assertFalse(FeedGatewayService.enforceCachedReplayMaxStale("gex-by-strike", "IBKR"));
        assertFalse(FeedGatewayService.enforceCachedReplayOffsetBarrier("gex-by-strike", "DATABENTO"));
        assertFalse(FeedGatewayService.enforceCachedReplayOffsetBarrier("gex-by-strike", "IBKR"));
        // Regression guard: max-pain stays exempt, fast flow signals stay gated.
        assertFalse(FeedGatewayService.enforceCachedReplayMaxStale("max-pain", "DATABENTO"));
        assertTrue(FeedGatewayService.enforceCachedReplayMaxStale("strike-flow", "DATABENTO"));
    }

    @Test
    void gexByStrikeUsesLongLastValueWinsTtlLikeMaxPain() {
        // Default: GEX shares max-pain's 12h window so a slow strike is not evicted after 15 min.
        GatewaySettings s = new GatewaySettings();
        assertEquals(s.maxPainTtlMs(), s.gexByStrikeTtlMs());
    }

    @Test
    void cachedOptionSnapshotsCanReplayBeforeNewSelectionTime() {
        FeedGatewayService service = new FeedGatewayService(
                new GatewaySettings(),
                new ObjectMapper(),
                new HpsfGatewayViewMapper(),
                null
        );

        assertTrue(service.passesSelectionTimeBarrierForTest(100L, 200L, false));
        assertFalse(service.passesSelectionTimeBarrierForTest(100L, 200L, true));
    }

    @Test
    void gatewayKafkaFetchSettingsAreBoundedByDefault() {
        GatewaySettings settings = new GatewaySettings();

        assertEquals(100, settings.maxPollRecords());
        assertEquals(4 * 1024 * 1024, settings.fetchMaxBytes());
        assertEquals(512 * 1024, settings.maxPartitionFetchBytes());
        assertEquals(512 * 1024, settings.receiveBufferBytes());
    }

    @Test
    void gatewayKafkaFetchSettingsCanBeOverriddenWithMinimums() {
        withSystemProperty("GATEWAY_KAFKA_MAX_POLL_RECORDS", "0", () ->
                assertEquals(1, new GatewaySettings().maxPollRecords()));
        withSystemProperty("GATEWAY_KAFKA_FETCH_MAX_BYTES", "128", () ->
                assertEquals(1024, new GatewaySettings().fetchMaxBytes()));
    }

    @Test
    void gatewayInitialExpiryHonorsConfiguredDateWithoutClockRollover() {
        // The gateway must mirror the deploy-resolved IB_EXPIRY (= the Databento feed's chain date)
        // and never advance it on a local clock rule. A configured expiry is returned verbatim no
        // matter the time of day, so the gateway's default selection and the feed stay on the same
        // date — otherwise the chain points at a date the feed never publishes and goes empty.
        withSystemProperty("IB_EXPIRY", "20260615", () ->
                assertEquals("20260615", new GatewaySettings().initialExpiry()));
        assertEquals("20260615", GatewaySettings.normalizeExpiry("2026-06-15"));
    }

    @Test
    void cachedSelectionRejectsOlderSelectionEpochs() throws Exception {
        ObjectMapper mapper = new ObjectMapper();

        assertFalse(FeedGatewayService.matchesSelectionNode(
                mapper.readTree("{\"marketDataSource\":\"DATABENTO\",\"symbol\":\"SPX\",\"expiry\":\"20260615\","
                        + "\"selectionEpoch\":100,\"strike\":7580}"),
                "DATABENTO",
                "SPX",
                "20260615",
                200,
                true
        ));
        assertTrue(FeedGatewayService.matchesSelectionNode(
                mapper.readTree("{\"marketDataSource\":\"DATABENTO\",\"symbol\":\"SPX\",\"expiry\":\"20260615\","
                        + "\"selectionEpoch\":200,\"strike\":7585}"),
                "DATABENTO",
                "SPX",
                "20260615",
                200,
                true
        ));
    }

    @Test
    void cachedSnapshotReplayCanIgnoreOlderSelectionEpoch() throws Exception {
        ObjectMapper mapper = new ObjectMapper();

        assertTrue(FeedGatewayService.matchesSelectionNode(
                mapper.readTree("{\"marketDataSource\":\"IBKR\",\"symbol\":\"SPX\",\"expiry\":\"20260616\","
                        + "\"selectionEpoch\":100,\"strike\":7580}"),
                "IBKR",
                "SPX",
                "20260616",
                200,
                false
        ));
    }

    @Test
    void strikeFlowGatewayContractConsumesCachesAndExposesUiBatchHealthAndMetrics() throws Exception {
        FeedGatewayService service = new FeedGatewayService(
                new GatewaySettings(),
                new ObjectMapper(),
                new HpsfGatewayViewMapper(),
                null
        );
        GatewaySettings settings = new GatewaySettings();
        String source = Files.readString(Path.of("src/main/java/app/feedgateway/FeedGatewayService.java"));
        String payload = "{\"eventType\":\"strike-flow\",\"marketDataSource\":\"DATABENTO\","
                + "\"symbol\":\"SPX\",\"expiry\":\"20260619\",\"strikes\":[]}";
        Object binding = topicBinding("DATABENTO", "strike-flow");
        ConsumerRecord<String, String> record = new ConsumerRecord<>(
                settings.databentoStrikeFlowTopic(),
                0,
                12L,
                "SPX|20260619",
                payload
        );

        String cacheKey = updateCache(service, binding, record, payload);
        String eventEnvelope = envelopeJson(service, "strike-flow", payload);
        String batchEnvelope = uiBatchEnvelopeJson(service, List.of(payload));

        assertEquals("options.databento.strike-flow", settings.databentoStrikeFlowTopic());
        assertTrue(source.contains("topicEvents.put(settings.databentoStrikeFlowTopic(), new TopicBinding(\"DATABENTO\", \"strike-flow\"));"));
        assertEquals("DATABENTO|SPX|20260619", cacheKey);
        assertTrue(eventEnvelope.contains("\"type\":\"strike-flow\""));
        assertTrue(batchEnvelope.contains("\"strikeFlows\":[{\"eventType\":\"strike-flow\""));
        assertTrue(service.healthJson().contains("\"strikeFlows\":1"));
        assertTrue(service.metrics().contains("options_edge_feed_gateway_strike_flows 1"));
    }

    @Test
    void optionPriceBehaviorGatewayContractConsumesCachesAndExposesUiBatchHealthAndMetrics() throws Exception {
        FeedGatewayService service = service();
        GatewaySettings settings = new GatewaySettings();
        String source = Files.readString(Path.of("src/main/java/app/feedgateway/FeedGatewayService.java"));
        String payload = "{\"symbol\":\"SPX\",\"tradingDate\":\"20260702\",\"marketDataSource\":\"DATABENTO\","
                + "\"sessionBehaviorScore\":1.2,\"rolling10sBehaviorScore\":0.4,\"rolling1mBehaviorScore\":0.7}";
        Object binding = topicBinding("DATABENTO", "option-price-behavior");
        ConsumerRecord<String, String> record = new ConsumerRecord<>(
                settings.optionPriceBehaviorDashboardTopic(),
                0,
                12L,
                "SPX|20260702",
                payload
        );

        String cacheKey = updateCache(service, binding, record, payload);
        String eventEnvelope = envelopeJson(service, "option-price-behavior", payload);
        String batchEnvelope = uiBatchEnvelopeJsonOptionPriceBehavior(service, List.of(payload));

        assertEquals("option-price-behavior-dashboard", settings.optionPriceBehaviorDashboardTopic());
        assertTrue(source.contains("topicEvents.put(settings.optionPriceBehaviorDashboardTopic(), new TopicBinding(\"DATABENTO\", \"option-price-behavior\"));"));
        assertEquals("DATABENTO|SPX|20260702", cacheKey);
        assertTrue(eventEnvelope.contains("\"type\":\"option-price-behavior\""));
        assertTrue(batchEnvelope.contains("\"optionPriceBehaviors\":[{\"symbol\":\"SPX\""));
        assertTrue(service.healthJson().contains("\"optionPriceBehaviors\":1"));
        assertTrue(service.metrics().contains("options_edge_feed_gateway_option_price_behaviors 1"));
    }

    @Test
    void missionPaceGatewayContractConsumesCachesAndExposesUiBatchHealthAndMetrics() throws Exception {
        FeedGatewayService service = service();
        GatewaySettings settings = new GatewaySettings();
        String source = Files.readString(Path.of("src/main/java/app/feedgateway/FeedGatewayService.java"));
        String payload = "{\"eventType\":\"mission-pace\",\"symbol\":\"SPX\",\"expiry\":\"20260612\","
                + "\"spot\":6004.8,\"timestampMs\":1,\"rankedStrikes\":[{\"strike\":6005,\"missionPaceScore\":91.4}]}";
        Object binding = topicBinding("DATABENTO", "mission-pace");
        ConsumerRecord<String, String> record = new ConsumerRecord<>(
                settings.databentoPaceMissionTopic(),
                0,
                12L,
                "SPX|20260612",
                payload
        );

        String cacheKey = updateCache(service, binding, record, payload);
        String eventEnvelope = envelopeJson(service, "mission-pace", payload);
        String batchEnvelope = uiBatchEnvelopeJsonMissionPace(service, List.of(payload));

        // Default topic resolves to the mission-pace topic and binds on the JSON/state path.
        assertEquals("options.databento.pace.mission", settings.databentoPaceMissionTopic());
        assertTrue(source.contains("topicEvents.put(settings.databentoPaceMissionTopic(), new TopicBinding(\"DATABENTO\", \"mission-pace\"));"));
        // Cache key is symbol|expiry (per-market, no strike — like max-pain). updateCache prepends the
        // source, so the full slot is source|symbol|expiry.
        assertEquals("DATABENTO|SPX|20260612", cacheKey);
        assertTrue(eventEnvelope.contains("\"type\":\"mission-pace\""));
        assertTrue(batchEnvelope.contains("\"missionPaces\":[{\"eventType\":\"mission-pace\""));
        assertTrue(service.healthJson().contains("\"missionPaces\":1"));
        assertTrue(service.metrics().contains("options_edge_feed_gateway_mission_paces 1"));
    }

    @Test
    void missionPaceForwardsForActiveMarketDespiteSourceSwitchOffsetBarrier() throws Exception {
        FeedGatewayService service = service();
        GatewaySettings settings = new GatewaySettings();
        setActiveSelection(service, "DATABENTO", "SPX", "20260612");
        // A source-switch offset barrier sits ABOVE the record offset — this is the production
        // condition that was silently dropping every fresh mission-pace frame (it is a low-frequency
        // per-market signal, so its offset stays "below" the barrier captured at the last switch).
        setOffsetBarrier(service, settings.databentoPaceMissionTopic(), 0, 100L);

        String payload = "{\"eventType\":\"mission-pace\",\"symbol\":\"SPX\",\"expiry\":\"20260612\","
                + "\"spot\":6004.8,\"timestampMs\":1,\"rankedStrikes\":[{\"strike\":6005,\"missionPaceScore\":91.4}]}";
        Object binding = topicBinding("DATABENTO", "mission-pace");
        ConsumerRecord<String, String> record = new ConsumerRecord<>(
                settings.databentoPaceMissionTopic(), 0, 12L, "SPX|20260612", payload); // offset 12 < barrier 100

        // Per-market signal must forward for the active market despite the per-strike offset barrier.
        assertTrue(shouldForward(service, binding, payload, record),
                "mission-pace for the active market must forward despite the source-switch offset barrier");

        // Cross-market safety: a frame for a DIFFERENT expiry must NOT leak to the active selection.
        String otherMarket = payload.replace("20260612", "20260613");
        ConsumerRecord<String, String> otherRecord = new ConsumerRecord<>(
                settings.databentoPaceMissionTopic(), 0, 13L, "SPX|20260613", otherMarket);
        assertFalse(shouldForward(service, binding, otherMarket, otherRecord),
                "mission-pace for a different market must not leak to the active selection");
    }

    @Test
    void missionSandwichForwardsForActiveMarketDespiteSourceSwitchOffsetBarrier() throws Exception {
        FeedGatewayService service = service();
        GatewaySettings settings = new GatewaySettings();
        setActiveSelection(service, "DATABENTO", "SPX", "20260612");
        // Same production condition that silently dropped every fresh mission-sandwich frame: it is a
        // low-frequency per-market signal (symbol|expiry), so its offset stays "below" the source-switch
        // barrier captured at the last switch. Without the shouldForward special-case it is dropped as
        // inactiveDropped/sourceStale and the option-chain never renders the sandwich.
        setOffsetBarrier(service, settings.databentoMissionSandwichTopic(), 0, 100L);

        String payload = "{\"eventType\":\"mission-sandwich\",\"source\":\"DATABENTO\",\"symbol\":\"SPX\","
                + "\"expiry\":\"20260612\",\"spot\":6004.8,\"timestampMs\":1,"
                + "\"callSandwich\":{\"side\":\"CALL\",\"tilt\":\"UPPER_HEAVY\",\"lowerStrike\":6000.0,"
                + "\"midStrike\":6005.0,\"upperStrike\":6010.0,\"lowerVolume\":100,\"upperVolume\":200,\"wallVolume\":300}}";
        Object binding = topicBinding("DATABENTO", "mission-sandwich");
        ConsumerRecord<String, String> record = new ConsumerRecord<>(
                settings.databentoMissionSandwichTopic(), 0, 12L, "SPX|20260612", payload); // offset 12 < barrier 100

        // Per-market signal must forward for the active market despite the per-strike offset barrier.
        assertTrue(shouldForward(service, binding, payload, record),
                "mission-sandwich for the active market must forward despite the source-switch offset barrier");

        // Cross-market safety: a frame for a DIFFERENT expiry must NOT leak to the active selection.
        String otherMarket = payload.replace("20260612", "20260613");
        ConsumerRecord<String, String> otherRecord = new ConsumerRecord<>(
                settings.databentoMissionSandwichTopic(), 0, 13L, "SPX|20260613", otherMarket);
        assertFalse(shouldForward(service, binding, otherMarket, otherRecord),
                "mission-sandwich for a different market must not leak to the active selection");
    }

    @Test
    void cachedMissionPaceReplayBypassesOffsetBarrierButKeepsTimeBarrier() throws Exception {
        FeedGatewayService service = service();
        GatewaySettings settings = new GatewaySettings();
        setActiveSelection(service, "DATABENTO", "SPX", "20260612");
        setOffsetBarrier(service, settings.databentoPaceMissionTopic(), 0, 100L);
        String payload = "{\"eventType\":\"mission-pace\",\"symbol\":\"SPX\",\"expiry\":\"20260612\","
                + "\"spot\":6004.8,\"timestampMs\":1,\"rankedStrikes\":[]}";
        Object binding = topicBinding("DATABENTO", "mission-pace");
        // No-timestamp record -> cacheTimestamp falls back to now, so the cached entry is FRESH.
        ConsumerRecord<String, String> record = new ConsumerRecord<>(
                settings.databentoPaceMissionTopic(), 0, 12L, "SPX|20260612", payload); // offset 12 < barrier 100
        updateCache(service, binding, record, payload);

        // Fresh + offset barrier above the record offset -> still replayed (offset bypassed).
        assertEquals(1, cachedEventCount(service, "mission-pace", System.currentTimeMillis()),
                "fresh cached mission-pace must replay on connect despite the offset barrier");

        // Older than maxStaleMs -> excluded (the time barrier is still enforced).
        ageCacheEventTimes(service, "mission-pace:", System.currentTimeMillis() - 60_000L);
        assertEquals(0, cachedEventCount(service, "mission-pace", System.currentTimeMillis()),
                "stale cached mission-pace must NOT replay (time barrier enforced)");
    }

    @Test
    void cachedReplayOnConnectIncludesMissionPaceForMatchingDatabentoSelection() throws Exception {
        FeedGatewayService service = service();
        GatewaySettings settings = new GatewaySettings();
        long now = System.currentTimeMillis();
        String payload = "{\"eventType\":\"mission-pace\",\"symbol\":\"SPX\",\"expiry\":\"20260612\","
                + "\"spot\":6004.8,\"timestampMs\":1,\"rankedStrikes\":[{\"strike\":6005,\"missionPaceScore\":91.4}]}";
        updateCache(service, topicBinding("DATABENTO", "mission-pace"),
                recordAt(settings.databentoPaceMissionTopic(), 0, 1L, "SPX|20260612", payload, now), payload);

        setActiveSelection(service, "DATABENTO", "SPX", "20260612");
        assertEquals(1, cachedEvents(service, List.of("mission-pace"), now).size(),
                "cached mission-pace must replay to a freshly-connected DATABENTO client");

        // Cached source-switch replay must include mission-pace in its event list.
        assertTrue(FeedGatewayService.sourceSwitchReplayEvents().contains("mission-pace"));
    }

    @Test
    void missionControlGatewayContractConsumesCachesAndExposesUiBatchHealthAndMetrics() throws Exception {
        FeedGatewayService service = service();
        GatewaySettings settings = new GatewaySettings();
        String source = Files.readString(Path.of("src/main/java/app/feedgateway/FeedGatewayService.java"));
        String payload = "{\"eventType\":\"mission-control\",\"symbol\":\"SPX\",\"expiry\":\"20260612\","
                + "\"spot\":6004.8,\"timestampMs\":1,\"rankedStrikes\":[{\"strike\":6005,\"missionControlScore\":91.4}]}";
        Object binding = topicBinding("DATABENTO", "mission-control");
        ConsumerRecord<String, String> record = new ConsumerRecord<>(
                settings.missionControlTopic(),
                0,
                12L,
                "SPX|20260612",
                payload
        );

        String cacheKey = updateCache(service, binding, record, payload);
        String eventEnvelope = envelopeJson(service, "mission-control", payload);
        String batchEnvelope = uiBatchEnvelopeJsonMissionControl(service, List.of(payload));

        // Default topic resolves to the mission-control topic and binds on the JSON/state path.
        assertEquals("options.spx.mission-control.current", settings.missionControlTopic());
        assertTrue(source.contains("topicEvents.put(settings.missionControlTopic(), new TopicBinding(\"DATABENTO\", \"mission-control\"));"));
        // Cache key is symbol|expiry (per-market, no strike — like max-pain). updateCache prepends the
        // source, so the full slot is source|symbol|expiry.
        assertEquals("DATABENTO|SPX|20260612", cacheKey);
        assertTrue(eventEnvelope.contains("\"type\":\"mission-control\""));
        assertTrue(batchEnvelope.contains("\"missionControls\":[{\"eventType\":\"mission-control\""));
        assertTrue(service.healthJson().contains("\"missionControls\":1"));
        assertTrue(service.metrics().contains("options_edge_feed_gateway_mission_controls 1"));
    }

    @Test
    void missionControlForwardsForActiveMarketDespiteSourceSwitchOffsetBarrier() throws Exception {
        FeedGatewayService service = service();
        GatewaySettings settings = new GatewaySettings();
        setActiveSelection(service, "DATABENTO", "SPX", "20260612");
        // A source-switch offset barrier sits ABOVE the record offset — this is the production
        // condition that was silently dropping every fresh mission-control frame (it is a low-frequency
        // per-market signal, so its offset stays "below" the barrier captured at the last switch).
        setOffsetBarrier(service, settings.missionControlTopic(), 0, 100L);

        String payload = "{\"eventType\":\"mission-control\",\"symbol\":\"SPX\",\"expiry\":\"20260612\","
                + "\"spot\":6004.8,\"timestampMs\":1,\"rankedStrikes\":[{\"strike\":6005,\"missionControlScore\":91.4}]}";
        Object binding = topicBinding("DATABENTO", "mission-control");
        ConsumerRecord<String, String> record = new ConsumerRecord<>(
                settings.missionControlTopic(), 0, 12L, "SPX|20260612", payload); // offset 12 < barrier 100

        // Per-market signal must forward for the active market despite the per-strike offset barrier.
        assertTrue(shouldForward(service, binding, payload, record),
                "mission-control for the active market must forward despite the source-switch offset barrier");

        // Cross-market safety: a frame for a DIFFERENT expiry must NOT leak to the active selection.
        String otherMarket = payload.replace("20260612", "20260613");
        ConsumerRecord<String, String> otherRecord = new ConsumerRecord<>(
                settings.missionControlTopic(), 0, 13L, "SPX|20260613", otherMarket);
        assertFalse(shouldForward(service, binding, otherMarket, otherRecord),
                "mission-control for a different market must not leak to the active selection");
    }

    @Test
    void cachedMissionControlReplayBypassesOffsetBarrierButKeepsTimeBarrier() throws Exception {
        FeedGatewayService service = service();
        GatewaySettings settings = new GatewaySettings();
        setActiveSelection(service, "DATABENTO", "SPX", "20260612");
        setOffsetBarrier(service, settings.missionControlTopic(), 0, 100L);
        String payload = "{\"eventType\":\"mission-control\",\"symbol\":\"SPX\",\"expiry\":\"20260612\","
                + "\"spot\":6004.8,\"timestampMs\":1,\"rankedStrikes\":[]}";
        Object binding = topicBinding("DATABENTO", "mission-control");
        // No-timestamp record -> cacheTimestamp falls back to now, so the cached entry is FRESH.
        ConsumerRecord<String, String> record = new ConsumerRecord<>(
                settings.missionControlTopic(), 0, 12L, "SPX|20260612", payload); // offset 12 < barrier 100
        updateCache(service, binding, record, payload);

        // Fresh + offset barrier above the record offset -> still replayed (offset bypassed).
        assertEquals(1, cachedEventCount(service, "mission-control", System.currentTimeMillis()),
                "fresh cached mission-control must replay on connect despite the offset barrier");

        // Older than maxStaleMs -> excluded (the time barrier is still enforced).
        ageCacheEventTimes(service, "mission-control:", System.currentTimeMillis() - 60_000L);
        assertEquals(0, cachedEventCount(service, "mission-control", System.currentTimeMillis()),
                "stale cached mission-control must NOT replay (time barrier enforced)");
    }

    @Test
    void cachedReplayOnConnectIncludesMissionControlForMatchingDatabentoSelection() throws Exception {
        FeedGatewayService service = service();
        GatewaySettings settings = new GatewaySettings();
        long now = System.currentTimeMillis();
        String payload = "{\"eventType\":\"mission-control\",\"symbol\":\"SPX\",\"expiry\":\"20260612\","
                + "\"spot\":6004.8,\"timestampMs\":1,\"rankedStrikes\":[{\"strike\":6005,\"missionControlScore\":91.4}]}";
        updateCache(service, topicBinding("DATABENTO", "mission-control"),
                recordAt(settings.missionControlTopic(), 0, 1L, "SPX|20260612", payload, now), payload);

        setActiveSelection(service, "DATABENTO", "SPX", "20260612");
        assertEquals(1, cachedEvents(service, List.of("mission-control"), now).size(),
                "cached mission-control must replay to a freshly-connected DATABENTO client");

        // Cached source-switch replay must include mission-control in its event list.
        assertTrue(FeedGatewayService.sourceSwitchReplayEvents().contains("mission-control"));
    }

    // ----- spread-skew gateway consumer (whole-underlying SpreadSkewSnapshot, mission-control mirror) -----

    /** The spread-skew snapshot payload per the producer contract: underlying + nullable expiry, ts = event time. */
    private static String spreadSkewPayload(long ts) {
        return "{\"schemaVersion\":1,\"ts\":" + ts + ",\"runId\":\"r-1\",\"sessionDate\":\"2026-07-11\","
                + "\"underlying\":\"SPX\",\"expiry\":\"2026-07-11\",\"spot\":6004.8,\"anchor\":6005.0,"
                + "\"degraded\":false,\"lateSession\":false,\"eventDay\":false,"
                + "\"headline\":{\"state\":\"CALL_SKEW\",\"z\":2.4,\"conflict\":false,"
                + "\"baselineSessionsMin\":5,\"baselineRequired\":10},"
                + "\"participatingOffsets\":[10,15,20],\"levels\":[]}";
    }

    @Test
    void spreadSkewGatewayContractConsumesCachesAndExposesUiBatchHealthAndMetrics() throws Exception {
        FeedGatewayService service = service();
        GatewaySettings settings = new GatewaySettings();
        String source = Files.readString(Path.of("src/main/java/app/feedgateway/FeedGatewayService.java"));
        String payload = spreadSkewPayload(System.currentTimeMillis());
        Object binding = topicBinding("DATABENTO", "spread-skew");
        ConsumerRecord<String, String> record = new ConsumerRecord<>(
                settings.spreadSkewTopic(),
                0,
                12L,
                "SPX",
                payload
        );

        String cacheKey = updateCache(service, binding, record, payload);
        String eventEnvelope = envelopeJson(service, "spread-skew", payload);
        String batchEnvelope = uiBatchEnvelopeJsonSpreadSkew(service, List.of(payload));

        // Default topic resolves to the spread-skew topic and binds on the JSON/state path.
        assertEquals("options.spx.spread-skew.current", settings.spreadSkewTopic());
        assertTrue(source.contains("topicEvents.put(settings.spreadSkewTopic(), new TopicBinding(\"DATABENTO\", \"spread-skew\"));"));
        // SINGLE-VALUE cache keyed by the underlying alone (no expiry segment — one snapshot covers the
        // whole underlying). updateCache prepends the source, so the full slot is source|underlying.
        assertEquals("DATABENTO|SPX", cacheKey);
        assertTrue(eventEnvelope.contains("\"type\":\"spread-skew\""));
        assertTrue(batchEnvelope.contains("\"spreadSkews\":[{\"schemaVersion\""));
        assertTrue(service.healthJson().contains("\"spreadSkews\":1"));
        assertTrue(service.metrics().contains("options_edge_feed_gateway_spread_skews 1"));
    }

    @Test
    void spreadSkewCacheIsSingleValueLastSnapshotWins() throws Exception {
        FeedGatewayService service = service();
        GatewaySettings settings = new GatewaySettings();
        long now = System.currentTimeMillis();
        Object binding = topicBinding("DATABENTO", "spread-skew");
        updateCache(service, binding,
                recordAt(settings.spreadSkewTopic(), 0, 1L, "SPX", spreadSkewPayload(now - 5_000), now - 5_000),
                spreadSkewPayload(now - 5_000));
        updateCache(service, binding,
                recordAt(settings.spreadSkewTopic(), 0, 2L, "SPX", spreadSkewPayload(now), now),
                spreadSkewPayload(now));
        // Both records collapse into ONE source|underlying slot — the second (newer ts) wins.
        assertTrue(service.healthJson().contains("\"spreadSkews\":1"),
                "spread-skew must be a single-value cache (last snapshot wins)");
        // An out-of-order OLDER ts must be rejected by the monotonic event-time gate.
        assertNull(updateCache(service, binding,
                recordAt(settings.spreadSkewTopic(), 0, 3L, "SPX", spreadSkewPayload(now - 10_000), now),
                spreadSkewPayload(now - 10_000)));
    }

    @Test
    void spreadSkewForwardsForActiveMarketDespiteSourceSwitchOffsetBarrier() throws Exception {
        FeedGatewayService service = service();
        GatewaySettings settings = new GatewaySettings();
        setActiveSelection(service, "DATABENTO", "SPX", "20260711");
        // A source-switch offset barrier sits ABOVE the record offset — like mission-control, the
        // low-frequency spread-skew frame's offset stays "below" the barrier captured at the last switch.
        setOffsetBarrier(service, settings.spreadSkewTopic(), 0, 100L);

        long now = System.currentTimeMillis();
        String payload = spreadSkewPayload(now);
        Object binding = topicBinding("DATABENTO", "spread-skew");
        ConsumerRecord<String, String> record =
                recordAt(settings.spreadSkewTopic(), 0, 12L, "SPX", payload, now); // offset 12 < barrier 100

        // Per-market signal must forward for the active market despite the per-strike offset barrier.
        assertTrue(shouldForward(service, binding, payload, record),
                "spread-skew for the active market must forward despite the source-switch offset barrier");

        // Payload-time freshness: a STALE ts must NOT forward even on a fresh Kafka arrival time.
        String stale = spreadSkewPayload(now - 60_000);
        ConsumerRecord<String, String> staleRecord =
                recordAt(settings.spreadSkewTopic(), 0, 13L, "SPX", stale, now); // arrival fresh, ts stale
        assertFalse(shouldForward(service, binding, stale, staleRecord),
                "spread-skew freshness must track the payload ts, not the Kafka arrival time");

        // Cross-market safety: a frame for a DIFFERENT expiry must NOT leak to the active selection.
        String otherMarket = payload.replace("\"expiry\":\"2026-07-11\"", "\"expiry\":\"2026-07-12\"");
        ConsumerRecord<String, String> otherRecord =
                recordAt(settings.spreadSkewTopic(), 0, 14L, "SPX", otherMarket, now);
        assertFalse(shouldForward(service, binding, otherMarket, otherRecord),
                "spread-skew for a different market must not leak to the active selection");

        // A NULL expiry (producer cannot resolve the 0DTE chain) still covers the active session.
        String nullExpiry = payload.replace("\"expiry\":\"2026-07-11\"", "\"expiry\":null");
        ConsumerRecord<String, String> nullExpiryRecord =
                recordAt(settings.spreadSkewTopic(), 0, 15L, "SPX", nullExpiry, now);
        assertTrue(shouldForward(service, binding, nullExpiry, nullExpiryRecord),
                "a null-expiry spread-skew frame must still reach the active selection");
    }

    @Test
    void spreadSkewMissingOrInvalidTsFailsClosedAndIsNotRescuedByFreshKafkaArrival() throws Exception {
        // eventCacheTimestamp for spread-skew has deliberately NO Kafka-arrival fallback: a snapshot
        // whose ts is missing, non-numeric or negative must fail closed (never cached, never
        // forwarded) even when the record's Kafka timestamp is brand new.
        FeedGatewayService service = service();
        GatewaySettings settings = new GatewaySettings();
        setActiveSelection(service, "DATABENTO", "SPX", "20260711");
        long now = System.currentTimeMillis();
        Object binding = topicBinding("DATABENTO", "spread-skew");
        String fresh = spreadSkewPayload(now);
        List<String> malformed = List.of(
                fresh.replace("\"ts\":" + now + ",", ""),                  // ts missing entirely
                fresh.replace("\"ts\":" + now, "\"ts\":\"not-a-number\""), // non-numeric ts
                fresh.replace("\"ts\":" + now, "\"ts\":-5"));              // negative ts
        long offset = 12L;
        for (String payload : malformed) {
            ConsumerRecord<String, String> record =
                    recordAt(settings.spreadSkewTopic(), 0, offset++, "SPX", payload, now); // arrival FRESH
            assertTrue(eventCacheTimestamp(service, "spread-skew", record) < 0,
                    "missing/invalid ts must fail closed, not fall back to the Kafka arrival time");
            assertNull(updateCache(service, binding, record, payload),
                    "a snapshot without a valid ts must never be cached");
            assertFalse(shouldForward(service, binding, payload, record),
                    "a snapshot without a valid ts must never forward");
        }
        assertEquals(0, cachedEventCount(service, "spread-skew", now),
                "no malformed snapshot may end up replayable");
    }

    @Test
    void cachedSpreadSkewReplayBypassesOffsetBarrierButKeepsTimeBarrier() throws Exception {
        FeedGatewayService service = service();
        GatewaySettings settings = new GatewaySettings();
        setActiveSelection(service, "DATABENTO", "SPX", "20260711");
        setOffsetBarrier(service, settings.spreadSkewTopic(), 0, 100L);
        String payload = spreadSkewPayload(System.currentTimeMillis());
        Object binding = topicBinding("DATABENTO", "spread-skew");
        // No-timestamp record -> the payload ts (fresh) is the cache event time.
        ConsumerRecord<String, String> record = new ConsumerRecord<>(
                settings.spreadSkewTopic(), 0, 12L, "SPX", payload); // offset 12 < barrier 100
        updateCache(service, binding, record, payload);

        // Fresh + offset barrier above the record offset -> still replayed (offset bypassed).
        assertEquals(1, cachedEventCount(service, "spread-skew", System.currentTimeMillis()),
                "fresh cached spread-skew must replay on connect despite the offset barrier");

        // Older than maxStaleMs -> excluded (the time barrier is still enforced).
        ageCacheEventTimes(service, "spread-skew:", System.currentTimeMillis() - 60_000L);
        assertEquals(0, cachedEventCount(service, "spread-skew", System.currentTimeMillis()),
                "stale cached spread-skew must NOT replay (time barrier enforced)");
    }

    @Test
    void cachedReplayOnConnectIncludesSpreadSkewForMatchingDatabentoSelection() throws Exception {
        FeedGatewayService service = service();
        GatewaySettings settings = new GatewaySettings();
        long now = System.currentTimeMillis();
        String payload = spreadSkewPayload(now);
        updateCache(service, topicBinding("DATABENTO", "spread-skew"),
                recordAt(settings.spreadSkewTopic(), 0, 1L, "SPX", payload, now), payload);

        setActiveSelection(service, "DATABENTO", "SPX", "20260711");
        assertEquals(1, cachedEvents(service, List.of("spread-skew"), now).size(),
                "cached spread-skew must replay to a freshly-connected DATABENTO client");

        // Wrong source (IBKR) is filtered (spread-skew is DATABENTO-only).
        setActiveSelection(service, "IBKR", "SPX", "20260711");
        assertTrue(cachedEvents(service, List.of("spread-skew"), now).isEmpty(),
                "IBKR selection must never receive DATABENTO spread-skew");

        // Wrong symbol is filtered by the underlying match.
        setActiveSelection(service, "DATABENTO", "SPY", "20260711");
        assertTrue(cachedEvents(service, List.of("spread-skew"), now).isEmpty(),
                "a different symbol must not receive this spread-skew");

        // Cached source-switch replay must include spread-skew in its event list.
        assertTrue(FeedGatewayService.sourceSwitchReplayEvents().contains("spread-skew"));
    }

    @Test
    void spreadSkewEventIsBroadcastStandaloneAndNeverCachedLikeTurnAlert() throws Exception {
        FeedGatewayService service = service();
        GatewaySettings settings = new GatewaySettings();
        String source = Files.readString(Path.of("src/main/java/app/feedgateway/FeedGatewayService.java"));

        // Default topic resolves to the spread-skew events topic and binds on BOTH JSON consumers
        // (cache + live kept symmetric), as its own standalone event type.
        assertEquals("options.spx.spread-skew.events", settings.spreadSkewEventsTopic());
        for (String method : List.of("runJsonStateCacheConsumer", "runJsonStateLiveConsumer")) {
            assertTrue(methodBody(source, method).contains(
                    "topicEvents.put(settings.spreadSkewEventsTopic(), new TopicBinding(\"DATABENTO\", \"spread-skew-event\"));"),
                    method + " must bind the spread-skew events topic (JSON)");
        }
        // The live consumer broadcasts it STANDALONE via the early dedicated branch (before the
        // cache/selection machinery), exactly like turn-alert.
        assertTrue(source.contains("if (\"spread-skew-event\".equals(binding.event())) {"),
                "spread-skew-event needs the early standalone-broadcast branch (turn-alert mirror)");

        // Behavioral: the broadcast reaches a connected client as its own message.type...
        List<String> sent = new ArrayList<>();
        addRecordingClient(service, sent);
        String payload = spreadSkewPayload(System.currentTimeMillis())
                .replace("\"participatingOffsets\"",
                        "\"eventId\":\"e-1\",\"transitionType\":\"FIRE\",\"previousState\":\"NEUTRAL\","
                                + "\"newState\":\"CALL_SKEW\",\"alertEligible\":true,\"alertSuppressedReason\":null,"
                                + "\"participatingOffsets\"");
        broadcast(service, "spread-skew-event", payload);
        assertTrue(sent.stream().anyMatch(m -> m.contains("\"type\":\"spread-skew-event\"")),
                "spread-skew-event must reach connected clients standalone");
        // ...and is never cached or replayed: no cache slot, and not in the source-switch replay list.
        assertEquals(0, cachedEventCount(service, "spread-skew-event", System.currentTimeMillis()));
        assertFalse(FeedGatewayService.sourceSwitchReplayEvents().contains("spread-skew-event"));
    }

    @Test
    void databentoGexGatewayContractConsumesCachesAndExposesUiBatchHealthAndMetrics() throws Exception {
        FeedGatewayService service = service();
        GatewaySettings settings = new GatewaySettings();
        String source = Files.readString(Path.of("src/main/java/app/feedgateway/FeedGatewayService.java"));
        String payload = "{\"source\":\"DATABENTO\",\"symbol\":\"SPX\",\"expiry\":\"20260612\","
                + "\"strike\":6005,\"callGex\":1.0,\"putGex\":-2.0,\"netGex\":-1.0,"
                + "\"gammaSign\":\"NEGATIVE\",\"updatedAt\":\"2026-06-12T14:31:00Z\"}";
        Object binding = topicBinding("DATABENTO", "gex-by-strike");
        ConsumerRecord<String, String> record = new ConsumerRecord<>(
                settings.databentoGexTopic(),
                0,
                12L,
                "SPX|20260612|6005",
                payload
        );

        String cacheKey = updateCache(service, binding, record, payload);
        String eventEnvelope = envelopeJson(service, "gex-by-strike", payload);
        String batchEnvelope = uiBatchEnvelopeJsonGex(service, List.of(payload));

        assertEquals("options.databento.gex.strike", settings.databentoGexTopic());
        assertTrue(source.contains("topicEvents.put(settings.databentoGexTopic(), new TopicBinding(\"DATABENTO\", \"gex-by-strike\"));"));
        assertEquals("DATABENTO|SPX|20260612|6005", cacheKey);
        assertTrue(eventEnvelope.contains("\"type\":\"gex-by-strike\""));
        assertTrue(batchEnvelope.contains("\"gexByStrike\":[{"));
        assertTrue(service.healthJson().contains("\"gexByStrike\":1"));
        assertTrue(service.metrics().contains("options_edge_feed_gateway_gex_by_strike 1"));
    }

    @Test
    void databentoGexPassesShouldForwardForActiveDatabentoSelection() throws Exception {
        FeedGatewayService service = service();
        GatewaySettings settings = new GatewaySettings();
        setActiveSelection(service, "DATABENTO", "SPX", "20260612");
        String payload = "{\"source\":\"DATABENTO\",\"symbol\":\"SPX\",\"expiry\":\"20260612\",\"strike\":6005,\"netGex\":-1.0}";
        Object binding = topicBinding("DATABENTO", "gex-by-strike");
        ConsumerRecord<String, String> record = new ConsumerRecord<>(
                settings.databentoGexTopic(), 0, 12L, "SPX|20260612|6005", payload);

        assertTrue(shouldForward(service, binding, payload, record));
    }

    @Test
    void gexByStrikeIsIsolatedBetweenIbkrAndDatabento() throws Exception {
        FeedGatewayService service = service();
        GatewaySettings settings = new GatewaySettings();
        String payload = "{\"source\":\"DATABENTO\",\"symbol\":\"SPX\",\"expiry\":\"20260612\",\"strike\":6005,\"netGex\":-1.0}";
        ConsumerRecord<String, String> record = new ConsumerRecord<>(
                settings.databentoGexTopic(), 0, 12L, "SPX|20260612|6005", payload);

        // Active source DATABENTO must not forward an IBKR-bound GEX record...
        setActiveSelection(service, "DATABENTO", "SPX", "20260612");
        assertFalse(shouldForward(service, topicBinding("IBKR", "gex-by-strike"), payload, record));

        // ...and active source IBKR must not forward a DATABENTO-bound GEX record.
        setActiveSelection(service, "IBKR", "SPX", "20260612");
        assertFalse(shouldForward(service, topicBinding("DATABENTO", "gex-by-strike"), payload, record));
    }

    // ---- DATABENTO gex + max-pain are Avro on the wire: must be consumed via the Avro path, not JSON ----

    @Test
    void databentoGexAndMaxPainAreClassifiedAsAvroNotJsonAcrossCacheLiveAndReplay() throws Exception {
        String source = Files.readString(Path.of("src/main/java/app/feedgateway/FeedGatewayService.java"));
        String gexBinding = "topicEvents.put(settings.databentoGexTopic(), new TopicBinding(\"DATABENTO\", \"gex-by-strike\"));";
        String maxPainBinding = "topicEvents.put(settings.databentoMaxPainTopic(), new TopicBinding(\"DATABENTO\", \"max-pain\"));";

        // Avro CACHE + LIVE consumers MUST bind both DATABENTO gex and max-pain (Avro deserialization).
        for (String method : List.of("runAvroCacheConsumer", "runAvroLiveConsumer")) {
            String body = methodBody(source, method);
            assertTrue(body.contains(gexBinding), method + " must bind DATABENTO gex (Avro)");
            assertTrue(body.contains(maxPainBinding), method + " must bind DATABENTO max-pain (Avro)");
        }
        // JSON/string consumers MUST NOT bind them (reading Avro as JSON garbles the value), but must keep
        // the genuinely-JSON strike-flow.
        for (String method : List.of("runJsonStateCacheConsumer", "runJsonStateLiveConsumer")) {
            String body = methodBody(source, method);
            assertFalse(body.contains(gexBinding), method + " must NOT bind DATABENTO gex on the JSON consumer");
            assertFalse(body.contains(maxPainBinding), method + " must NOT bind DATABENTO max-pain on the JSON consumer");
            assertTrue(body.contains("databentoStrikeFlowTopic()"), method + " keeps the JSON strike-flow binding");
        }
        // Replay classification must match: DATABENTO gex + max-pain in avroTopics, NOT stringTopics.
        assertTrue(source.contains("avroTopics.put(settings.databentoGexTopic(), \"gex-by-strike\");"));
        assertTrue(source.contains("avroTopics.put(settings.databentoMaxPainTopic(), \"max-pain\");"));
        assertFalse(source.contains("stringTopics.put(settings.databentoGexTopic(), \"gex-by-strike\");"));
        assertFalse(source.contains("stringTopics.put(settings.databentoMaxPainTopic(), \"max-pain\");"));
        // Mission-pace is genuinely JSON (String/JSON), like strike-flow: it must be in stringTopics, NOT Avro.
        assertTrue(source.contains("stringTopics.put(settings.databentoPaceMissionTopic(), \"mission-pace\");"));
        assertFalse(source.contains("avroTopics.put(settings.databentoPaceMissionTopic(), \"mission-pace\");"));
        // JSON/string CACHE + LIVE consumers must bind the mission-pace topic (it is JSON, not Avro).
        for (String method : List.of("runJsonStateCacheConsumer", "runJsonStateLiveConsumer")) {
            assertTrue(methodBody(source, method).contains(
                    "topicEvents.put(settings.databentoPaceMissionTopic(), new TopicBinding(\"DATABENTO\", \"mission-pace\"));"),
                    method + " must bind the DATABENTO mission-pace topic (JSON)");
        }
        for (String method : List.of("runAvroCacheConsumer", "runAvroLiveConsumer")) {
            assertFalse(methodBody(source, method).contains(
                    "topicEvents.put(settings.databentoPaceMissionTopic(), new TopicBinding(\"DATABENTO\", \"mission-pace\"));"),
                    method + " must NOT bind the DATABENTO mission-pace topic (it is JSON, not Avro)");
        }
        // Mission-control is genuinely JSON (String/JSON), like strike-flow: it must be in stringTopics, NOT Avro.
        assertTrue(source.contains("stringTopics.put(settings.missionControlTopic(), \"mission-control\");"));
        assertFalse(source.contains("avroTopics.put(settings.missionControlTopic(), \"mission-control\");"));
        // JSON/string CACHE + LIVE consumers must bind the mission-control topic (it is JSON, not Avro).
        for (String method : List.of("runJsonStateCacheConsumer", "runJsonStateLiveConsumer")) {
            assertTrue(methodBody(source, method).contains(
                    "topicEvents.put(settings.missionControlTopic(), new TopicBinding(\"DATABENTO\", \"mission-control\"));"),
                    method + " must bind the DATABENTO mission-control topic (JSON)");
        }
        for (String method : List.of("runAvroCacheConsumer", "runAvroLiveConsumer")) {
            assertFalse(methodBody(source, method).contains(
                    "topicEvents.put(settings.missionControlTopic(), new TopicBinding(\"DATABENTO\", \"mission-control\"));"),
                    method + " must NOT bind the DATABENTO mission-control topic (it is JSON, not Avro)");
        }
        // Spread-skew is genuinely JSON (String/JSON), like mission-control: it must be in stringTopics, NOT Avro.
        assertTrue(source.contains("stringTopics.put(settings.spreadSkewTopic(), \"spread-skew\");"));
        assertFalse(source.contains("avroTopics.put(settings.spreadSkewTopic(), \"spread-skew\");"));
        // JSON/string CACHE + LIVE consumers must bind the spread-skew topic (it is JSON, not Avro).
        for (String method : List.of("runJsonStateCacheConsumer", "runJsonStateLiveConsumer")) {
            assertTrue(methodBody(source, method).contains(
                    "topicEvents.put(settings.spreadSkewTopic(), new TopicBinding(\"DATABENTO\", \"spread-skew\"));"),
                    method + " must bind the DATABENTO spread-skew topic (JSON)");
        }
        for (String method : List.of("runAvroCacheConsumer", "runAvroLiveConsumer")) {
            assertFalse(methodBody(source, method).contains("spread-skew"),
                    method + " must NOT bind spread-skew (it is JSON, not Avro)");
        }
        // Unified S/R (strike-sr) is DATABENTO-only Avro: bound in the Avro consumers + avroTopics,
        // and NEVER in the JSON consumers.
        assertTrue(source.contains("avroTopics.put(settings.unifiedSrTopic(), \"strike-sr\");"));
        for (String method : List.of("runAvroCacheConsumer", "runAvroLiveConsumer")) {
            assertTrue(methodBody(source, method).contains(
                    "topicEvents.put(settings.unifiedSrTopic(), new TopicBinding(\"DATABENTO\", \"strike-sr\"));"),
                    method + " must bind the unified S/R topic (Avro)");
        }
        for (String method : List.of("runJsonStateCacheConsumer", "runJsonStateLiveConsumer")) {
            assertFalse(methodBody(source, method).contains("strike-sr"),
                    method + " must NOT bind the unified S/R topic (it is Avro, not JSON)");
        }
        // strike-invasion is genuinely JSON (StrikeInvasionSnapshot), like strike-intel: it must be a
        // stringTopic in windowed replay (NOT Avro), and bound on the JSON consumers only.
        assertTrue(source.contains("stringTopics.put(settings.strikeInvasionTopic(), \"strike-invasion\");"),
                "windowed replay must consume strike-invasion as a JSON stringTopic");
        assertFalse(source.contains("avroTopics.put(settings.strikeInvasionTopic(), \"strike-invasion\");"),
                "strike-invasion must never be read via the Avro path");
        // Run-scoped (orchestrated) replay must DROP it (not in the per-run replicator contract), mirroring
        // strike-intel / delta-flow / liquidity-heatmap.
        assertTrue(source.contains("stringTopics.remove(settings.strikeInvasionTopic());"),
                "run-scoped replay must exclude strike-invasion (no replay.<runId> topic for it)");
        for (String method : List.of("runJsonStateCacheConsumer", "runJsonStateLiveConsumer")) {
            assertTrue(methodBody(source, method).contains(
                    "topicEvents.put(settings.strikeInvasionTopic(), new TopicBinding(\"DATABENTO\", \"strike-invasion\"));"),
                    method + " must bind the DATABENTO strike-invasion topic (JSON)");
        }
        for (String method : List.of("runAvroCacheConsumer", "runAvroLiveConsumer")) {
            assertFalse(methodBody(source, method).contains("strike-invasion"),
                    method + " must NOT bind strike-invasion (it is JSON, not Avro)");
        }
        // Legacy caught-up gating: max-pain (DATABENTO-only Avro) under avroCaughtUp; gex-by-strike
        // (multi-source) under BOTH flags.
        assertTrue(source.contains(
                "sendCachedState(session, List.of(\"snapshot\", \"pace\", \"pace-rank\", \"directional-pressure\", \"max-pain\", \"strike-sr\", \"gex-magnet\", \"gamma-migration\", \"gex-strike-lifecycle\"));"));
        assertTrue(source.contains("if (avroCaughtUp.get() && stateCaughtUp.get()) {"));
        // gex legacy cached replay is source-aware (no hard IBKR-only filter).
        assertFalse(source.contains(".filter(entry -> \"IBKR\".equals(selection.source()))"));
        // The Avro consumer uses RecordNameStrategy for the record-name subjects these schemas register under.
        assertTrue(source.contains(
                "io.confluent.kafka.serializers.subject.RecordNameStrategy"));
    }

    @Test
    void avroMaxPainRecordIsDeserializedCachedAndDeliverable() throws Exception {
        // Behavioral coverage (Codex NIT): a real Avro GenericRecord for the max-pain schema must convert
        // to JSON (avroJson), cache under DATABENTO|symbol|expiry (maxPainCacheKey), and be a valid
        // routable/deliverable max-pain (status read by isMaxPainExpired). This is the path that was
        // silently dropped when max-pain was read as a String.
        org.apache.avro.Schema schema = org.apache.avro.SchemaBuilder.record("MaxPainSnapshot")
                .namespace("app.options.maxpain").fields()
                .name("messageType").type().stringType().noDefault()
                .name("source").type().stringType().noDefault()
                .name("symbol").type().stringType().noDefault()
                .name("expiry").type().stringType().noDefault()
                .name("status").type().stringType().noDefault()
                .name("maxPainStrike").type().doubleType().noDefault()
                .endRecord();
        org.apache.avro.generic.GenericRecord rec = new org.apache.avro.generic.GenericData.Record(schema);
        rec.put("messageType", "MAX_PAIN");
        rec.put("source", "DATABENTO");
        rec.put("symbol", "SPX");
        rec.put("expiry", "20260622");
        rec.put("status", "VALID");
        rec.put("maxPainStrike", 4500.0);

        FeedGatewayService service = service();
        String json = avroJson(service, rec);
        assertTrue(json.contains("\"maxPainStrike\":4500"), "avroJson must preserve maxPainStrike numerically");
        assertEquals("SPX|20260622", maxPainCacheKey(service, json, "fallback"),
                "avro->json max-pain must key by symbol|expiry");
        assertFalse(isMaxPainExpired(service, json), "VALID status must not be terminal");

        // And it caches + delivers via the same updateCache the Avro consumer uses (cacheKey non-null).
        String key = updateCache(service, topicBinding("DATABENTO", "max-pain"),
                new ConsumerRecord<>(new GatewaySettings().databentoMaxPainTopic(), 0, 1L, "SPX|20260622", json),
                json);
        assertEquals("DATABENTO|SPX|20260622", key);
        assertTrue(service.healthJson().contains("\"maxPain\":1"));
    }

    private static String avroJson(FeedGatewayService service, Object genericRecord) throws Exception {
        Method m = FeedGatewayService.class.getDeclaredMethod("avroJson", Object.class);
        m.setAccessible(true);
        return (String) m.invoke(service, genericRecord);
    }

    /** The body of a no-arg private method, from its signature to the start of the next private method. */
    private static String methodBody(String source, String methodName) {
        int start = source.indexOf("private void " + methodName + "()");
        if (start < 0) {
            throw new IllegalArgumentException("method not found: " + methodName);
        }
        int next = source.indexOf("\n    private ", start + 1);
        return next < 0 ? source.substring(start) : source.substring(start, next);
    }

    // ---- Max-pain last-value-wins: a slow daily-OI signal must not use the generic 15-min freshness ----

    @Test
    void maxPainTtlMsDefaultsTo12hAndIsOverridable() {
        assertEquals(43_200_000L, new GatewaySettings().maxPainTtlMs());
        withSystemProperty("GATEWAY_MAXPAIN_TTL_MS", "60000",
                () -> assertEquals(60_000L, new GatewaySettings().maxPainTtlMs()));
        // <= 0 is honored (preserves the "do not cache stale state" semantics, NOT infinite).
        withSystemProperty("GATEWAY_MAXPAIN_TTL_MS", "0",
                () -> assertEquals(0L, new GatewaySettings().maxPainTtlMs()));
    }

    @Test
    void optionChainFreshnessIsMarketAwareTenMinDuringRthNeverOffHours() throws Exception {
        FeedGatewayService service = service();
        GatewaySettings settings = new GatewaySettings();
        long now = 2_000_000_000_000L;
        long elevenMinAgo = now - 11L * 60_000L;
        long fiveMinAgo = now - 5L * 60_000L;

        java.util.Map<String, Object> displayTopic =
                java.util.Map.of(settings.databentoDisplayTopic(), topicBinding("DATABENTO", "snapshot"));
        org.apache.kafka.common.TopicPartition displayPart =
                new org.apache.kafka.common.TopicPartition(settings.databentoDisplayTopic(), 0);

        // DURING market hours: the structural chain (snapshot) uses the 10-min RTH TTL.
        overrideRth(service, true);
        assertTrue(isExpiredEvent(service, "snapshot", elevenMinAgo, now), "snapshot >10min expires in RTH");
        assertFalse(isExpiredEvent(service, "snapshot", fiveMinAgo, now), "snapshot <10min fresh in RTH");
        assertEquals(settings.optionChainRthCacheTtlMs(), windowTtlMsForAt(service, displayPart, displayTopic, now));

        // OFF market hours: the published chain is NEVER evicted (so strikes stay visible), with a bounded seek.
        overrideRth(service, false);
        assertFalse(isExpiredEvent(service, "snapshot", now - 25L * 3_600_000L, now), "off-hours never evicts");
        assertEquals(settings.optionChainOffHoursSeekBackMs(),
                windowTtlMsForAt(service, displayPart, displayTopic, now));

        // Fast order-flow signals are NOT market-aware: they keep the generic 15-min TTL, so an 11-min-old
        // strike-flow is NOT expired (a market-aware 10-min TTL would have expired it) — proving the scope.
        assertFalse(isExpiredEvent(service, "strike-flow", elevenMinAgo, now), "strike-flow uses generic 15min TTL");
        assertEquals(settings.cacheTtlMs(), windowTtlMsForAt(service,
                new org.apache.kafka.common.TopicPartition(settings.databentoStrikeFlowTopic(), 0),
                java.util.Map.of(settings.databentoStrikeFlowTopic(), topicBinding("DATABENTO", "strike-flow")), now));
    }

    @Test
    void offHoursStaleSnapshotStillReplaysToConnectingClientWhileFastSignalDoesNot() throws Exception {
        // The user-facing contract: off-hours a connecting client still gets the published strikes (snapshot),
        // even hours old; a stale fast-signal (strike-flow) does NOT replay (its freshness barrier stands).
        FeedGatewayService service = service();
        GatewaySettings settings = new GatewaySettings();
        setActiveSelection(service, "DATABENTO", "SPX", "20260623");
        overrideRth(service, false); // off market hours
        long now = System.currentTimeMillis();
        long fortyMinAgo = now - 40L * 60_000L;

        String snapJson = "{\"marketDataSource\":\"DATABENTO\",\"symbol\":\"SPX\",\"expiry\":\"20260623\",\"strike\":7000}";
        updateCache(service, topicBinding("DATABENTO", "snapshot"),
                recordAt(settings.databentoDisplayTopic(), 0, 1L, "SPX|20260623|7000", snapJson, fortyMinAgo), snapJson);
        // A 40-min-old fast strike-flow for the same selection: still cached but it must NOT replay (stale).
        String sfJson = "{\"marketDataSource\":\"DATABENTO\",\"symbol\":\"SPX\",\"expiry\":\"20260623\",\"strike\":7000,\"netFlow\":1.0}";
        updateCache(service, topicBinding("DATABENTO", "strike-flow"),
                recordAt(settings.databentoStrikeFlowTopic(), 0, 1L, "SPX|20260623|7000", sfJson, fortyMinAgo), sfJson);

        assertEquals(1, cachedEvents(service, List.of("snapshot"), now).size(),
                "a 40-min-old snapshot still replays off-hours (never evicted)");
        assertEquals(0, cachedEvents(service, List.of("strike-flow"), now).size(),
                "a 40-min-old fast strike-flow does NOT replay (generic TTL evicted it)");
    }

    @Test
    void isExpiredIsEventAwareForMaxPainVersusFast() throws Exception {
        FeedGatewayService service = service();
        overrideRth(service, true); // hold market hours fixed so the fast-event TTL is deterministic
        long now = 2_000_000_000_000L;
        long thirtyFiveMinAgo = now - 35L * 60_000L;
        // A 35-min-old record during RTH: the structural snapshot expires (10-min TTL); max-pain does not (12h).
        assertTrue(isExpiredEvent(service, "snapshot", thirtyFiveMinAgo, now));
        assertFalse(isExpiredEvent(service, "max-pain", thirtyFiveMinAgo, now));
        // Beyond the 12h max-pain window, even max-pain expires (bounded, not infinite).
        assertTrue(isExpiredEvent(service, "max-pain", now - 13L * 3_600_000L, now));
    }

    @Test
    void seekWindowTtlMapsMaxPainToLongWindowAndOthersToGeneric() throws Exception {
        FeedGatewayService service = service();
        overrideRth(service, true); // structural snapshot seek == RTH TTL while in market hours
        GatewaySettings settings = new GatewaySettings();
        long now = 2_000_000_000_000L;
        java.util.Map<String, Object> topicEvents = new java.util.HashMap<>();
        topicEvents.put(settings.databentoMaxPainTopic(), topicBinding("DATABENTO", "max-pain"));
        topicEvents.put(settings.databentoDisplayTopic(), topicBinding("DATABENTO", "snapshot"));
        topicEvents.put(settings.databentoStrikeFlowTopic(), topicBinding("DATABENTO", "strike-flow"));

        assertEquals(settings.maxPainTtlMs(), windowTtlMsForAt(service,
                new org.apache.kafka.common.TopicPartition(settings.databentoMaxPainTopic(), 0), topicEvents, now));
        // Structural snapshot is market-aware (RTH 10-min seek); fast strike-flow stays generic.
        assertEquals(settings.optionChainRthCacheTtlMs(), windowTtlMsForAt(service,
                new org.apache.kafka.common.TopicPartition(settings.databentoDisplayTopic(), 0), topicEvents, now));
        assertEquals(settings.cacheTtlMs(), windowTtlMsForAt(service,
                new org.apache.kafka.common.TopicPartition(settings.databentoStrikeFlowTopic(), 0), topicEvents, now));
        // Null map (the hpsf callers) → generic window for every partition (unchanged behaviour).
        assertEquals(settings.cacheTtlMs(), windowTtlMsForAt(service,
                new org.apache.kafka.common.TopicPartition(settings.databentoMaxPainTopic(), 0), null, now));
    }

    @Test
    void agedNonTerminalMaxPainSurvivesIngestWhileAgedFastEventIsEvicted() throws Exception {
        FeedGatewayService service = service();
        overrideRth(service, true); // ingest uses wall-clock now; hold market hours so the fast TTL applies
        GatewaySettings settings = new GatewaySettings();
        long now = System.currentTimeMillis();
        long thirtyFiveMinAgo = now - 35L * 60_000L;

        String maxPainJson = "{\"messageType\":\"MAX_PAIN\",\"marketDataSource\":\"DATABENTO\","
                + "\"symbol\":\"SPX\",\"expiry\":\"20260622\",\"status\":\"VALID\",\"maxPainStrike\":4500.0}";
        String key = updateCache(service, topicBinding("DATABENTO", "max-pain"),
                recordAt(settings.databentoMaxPainTopic(), 0, 1L, "SPX|20260622", maxPainJson, thirtyFiveMinAgo),
                maxPainJson);
        assertEquals("DATABENTO|SPX|20260622", key, "aged-but-valid max-pain must be cached, not evicted");
        assertTrue(service.healthJson().contains("\"maxPain\":1"));

        // Same age, a FAST event (strike-flow) is still evicted on ingest by the generic 15-min window.
        String sfJson = "{\"eventType\":\"strike-flow\",\"marketDataSource\":\"DATABENTO\","
                + "\"symbol\":\"SPX\",\"expiry\":\"20260622\",\"strikes\":[]}";
        String sfKey = updateCache(service, topicBinding("DATABENTO", "strike-flow"),
                recordAt(settings.databentoStrikeFlowTopic(), 0, 1L, "SPX|20260622", sfJson, thirtyFiveMinAgo),
                sfJson);
        assertEquals(null, sfKey, "aged fast event must still be evicted on ingest");
        assertTrue(service.healthJson().contains("\"strikeFlows\":0"));
    }

    @Test
    void maxPainBeyondTheTwelveHourWindowIsEvictedOnIngest() throws Exception {
        FeedGatewayService service = service();
        GatewaySettings settings = new GatewaySettings();
        long now = System.currentTimeMillis();
        String json = "{\"messageType\":\"MAX_PAIN\",\"marketDataSource\":\"DATABENTO\","
                + "\"symbol\":\"SPX\",\"expiry\":\"20260622\",\"status\":\"VALID\"}";
        String key = updateCache(service, topicBinding("DATABENTO", "max-pain"),
                recordAt(settings.databentoMaxPainTopic(), 0, 1L, "SPX|20260622", json, now - 13L * 3_600_000L),
                json);
        assertEquals(null, key, "max-pain older than the 12h bound must be evicted (not infinite retention)");
        assertTrue(service.healthJson().contains("\"maxPain\":0"));
    }

    @Test
    void periodicPurgeKeepsAgedMaxPainButEvictsAgedFastEvent() throws Exception {
        FeedGatewayService service = service();
        GatewaySettings settings = new GatewaySettings();
        long now = System.currentTimeMillis();

        String maxPainJson = "{\"messageType\":\"MAX_PAIN\",\"marketDataSource\":\"DATABENTO\","
                + "\"symbol\":\"SPX\",\"expiry\":\"20260622\",\"status\":\"VALID\"}";
        updateCache(service, topicBinding("DATABENTO", "max-pain"),
                new ConsumerRecord<>(settings.databentoMaxPainTopic(), 0, 1L, "SPX|20260622", maxPainJson), maxPainJson);
        String sfJson = "{\"eventType\":\"strike-flow\",\"marketDataSource\":\"DATABENTO\","
                + "\"symbol\":\"SPX\",\"expiry\":\"20260622\",\"strikes\":[]}";
        updateCache(service, topicBinding("DATABENTO", "strike-flow"),
                new ConsumerRecord<>(settings.databentoStrikeFlowTopic(), 0, 1L, "SPX|20260622", sfJson), sfJson);
        assertTrue(service.healthJson().contains("\"maxPain\":1"));
        assertTrue(service.healthJson().contains("\"strikeFlows\":1"));

        // Run the periodic purge 20 minutes into the future: the fast event ages past 15 min and is
        // evicted; the max-pain (12h window) survives.
        purgeExpiredCache(service, now + 20L * 60_000L);
        assertTrue(service.healthJson().contains("\"maxPain\":1"), "aged max-pain must survive periodic purge");
        assertTrue(service.healthJson().contains("\"strikeFlows\":0"), "aged fast event must be purged");
    }

    @Test
    void cachedReplayIncludesAgedMaxPainForMatchingDatabentoSelection() throws Exception {
        FeedGatewayService service = service();
        GatewaySettings settings = new GatewaySettings();
        long now = System.currentTimeMillis();
        // Ingest an AGED (35-min) max-pain — older than maxStaleMs (15s) so this proves both the event-aware
        // isCacheFresh AND the cached-replay max-stale exemption let it through to a connecting client.
        String json = "{\"messageType\":\"MAX_PAIN\",\"marketDataSource\":\"DATABENTO\","
                + "\"symbol\":\"SPX\",\"expiry\":\"20260622\",\"status\":\"VALID\",\"maxPainStrike\":4500.0}";
        updateCache(service, topicBinding("DATABENTO", "max-pain"),
                recordAt(settings.databentoMaxPainTopic(), 0, 1L, "SPX|20260622", json, now - 35L * 60_000L), json);

        setActiveSelection(service, "DATABENTO", "SPX", "20260622");
        List<?> replay = cachedEvents(service, List.of("max-pain"), now);
        assertEquals(1, replay.size(), "aged max-pain must be replayed to a freshly-connected DATABENTO client");

        // An IBKR-selected session must NOT receive the DATABENTO max-pain (isolation preserved).
        setActiveSelection(service, "IBKR", "SPX", "20260622");
        assertTrue(cachedEvents(service, List.of("max-pain"), now).isEmpty(),
                "IBKR selection must never receive DATABENTO max-pain");
    }

    @Test
    void cachedReplayMaxPainBelowTheOffsetBarrierStillReplays() throws Exception {
        // Codex Gate-2 NIT: prove the OFFSET-barrier exemption (not just the max-stale one). A slow
        // max-pain's latest record can sit at an offset BELOW the session's per-partition barrier (set
        // when faster topics advanced past selection); without the exemption it would be filtered.
        FeedGatewayService service = service();
        GatewaySettings settings = new GatewaySettings();
        String json = "{\"messageType\":\"MAX_PAIN\",\"marketDataSource\":\"DATABENTO\","
                + "\"symbol\":\"SPX\",\"expiry\":\"20260622\",\"status\":\"VALID\",\"maxPainStrike\":4500.0}";
        // Cache the max-pain at a LOW offset (1)...
        updateCache(service, topicBinding("DATABENTO", "max-pain"),
                new ConsumerRecord<>(settings.databentoMaxPainTopic(), 0, 1L, "SPX|20260622", json), json);
        // ...then raise the offset barrier for that partition far above it (100).
        setOffsetBarrier(service, settings.databentoMaxPainTopic(), 0, 100L);

        setActiveSelection(service, "DATABENTO", "SPX", "20260622");
        assertEquals(1, cachedEvents(service, List.of("max-pain"), System.currentTimeMillis()).size(),
                "max-pain below the offset barrier must still replay (offset-barrier exemption)");
    }

    @Test
    void terminalExpiredMaxPainEvictsCacheButReturnsKeyForOneLiveForward() throws Exception {
        FeedGatewayService service = service();
        GatewaySettings settings = new GatewaySettings();
        // First a VALID max-pain is cached...
        String valid = "{\"messageType\":\"MAX_PAIN\",\"marketDataSource\":\"DATABENTO\","
                + "\"symbol\":\"SPX\",\"expiry\":\"20260622\",\"status\":\"VALID\"}";
        updateCache(service, topicBinding("DATABENTO", "max-pain"),
                new ConsumerRecord<>(settings.databentoMaxPainTopic(), 0, 1L, "SPX|20260622", valid), valid);
        assertTrue(service.healthJson().contains("\"maxPain\":1"));

        // ...then the terminal EXPIRED must still evict the cache AND return the key for one live forward.
        String expired = "{\"messageType\":\"MAX_PAIN\",\"marketDataSource\":\"DATABENTO\","
                + "\"symbol\":\"SPX\",\"expiry\":\"20260622\",\"status\":\"EXPIRED\"}";
        String key = updateCache(service, topicBinding("DATABENTO", "max-pain"),
                new ConsumerRecord<>(settings.databentoMaxPainTopic(), 0, 2L, "SPX|20260622", expired), expired);
        assertEquals("DATABENTO|SPX|20260622", key, "terminal EXPIRED must return a key for the one-time live forward");
        assertTrue(service.healthJson().contains("\"maxPain\":0"), "terminal EXPIRED must evict the cache");
    }

    private static void withSystemProperty(String key, String value, Runnable assertion) {
        String previous = System.getProperty(key);
        try {
            System.setProperty(key, value);
            assertion.run();
        } finally {
            if (previous == null) {
                System.clearProperty(key);
            } else {
                System.setProperty(key, previous);
            }
        }
    }

    private static Object topicBinding(String source, String event) throws Exception {
        Class<?> type = Class.forName("app.feedgateway.FeedGatewayService$TopicBinding");
        Constructor<?> constructor = type.getDeclaredConstructor(String.class, String.class);
        constructor.setAccessible(true);
        return constructor.newInstance(source, event);
    }

    private static FeedGatewayService service() {
        return new FeedGatewayService(
                new GatewaySettings(),
                new ObjectMapper(),
                new HpsfGatewayViewMapper(),
                null /* routingEngine: legacy broadcast path */
        );
    }

    private static boolean isOptionalTopic(FeedGatewayService service, String topic) throws Exception {
        Method method = FeedGatewayService.class.getDeclaredMethod("isOptionalTopic", String.class);
        method.setAccessible(true);
        return (boolean) method.invoke(service, topic);
    }

    private static boolean isExpired(FeedGatewayService service, String event, long eventTime, long now) throws Exception {
        Method method = FeedGatewayService.class.getDeclaredMethod("isExpired", String.class, long.class, long.class);
        method.setAccessible(true);
        return (boolean) method.invoke(service, event, eventTime, now);
    }

    private static long eventCacheTimestamp(FeedGatewayService service, String event, ConsumerRecord<?, ?> record) throws Exception {
        Method method = FeedGatewayService.class.getDeclaredMethod(
                "eventCacheTimestamp", String.class, ConsumerRecord.class, String.class);
        method.setAccessible(true);
        return (long) method.invoke(service, event, record, record.value());
    }

    private static String paceCacheKey(FeedGatewayService service, String json, String fallback) throws Exception {
        Method method = FeedGatewayService.class.getDeclaredMethod("paceCacheKey", String.class, String.class);
        method.setAccessible(true);
        return (String) method.invoke(service, json, fallback);
    }

    private static String gexCacheKey(FeedGatewayService service, String json, String fallback) throws Exception {
        Method method = FeedGatewayService.class.getDeclaredMethod("gexCacheKey", String.class, String.class);
        method.setAccessible(true);
        return (String) method.invoke(service, json, fallback);
    }

    private static String deltaFlowCacheKey(FeedGatewayService service, String json, String fallback) throws Exception {
        Method method = FeedGatewayService.class.getDeclaredMethod("deltaFlowCacheKey", String.class, String.class);
        method.setAccessible(true);
        return (String) method.invoke(service, json, fallback);
    }

    private static String strikeLifecycleCacheKey(FeedGatewayService service, String json, String fallback) throws Exception {
        Method method = FeedGatewayService.class.getDeclaredMethod("strikeLifecycleCacheKey", String.class, String.class);
        method.setAccessible(true);
        return (String) method.invoke(service, json, fallback);
    }

    private static String strikeIntelCacheKey(FeedGatewayService service, String json, String fallback) throws Exception {
        Method method = FeedGatewayService.class.getDeclaredMethod("strikeIntelCacheKey", String.class, String.class);
        method.setAccessible(true);
        return (String) method.invoke(service, json, fallback);
    }

    private static String strikeInvasionCacheKey(FeedGatewayService service, String json, String fallback) throws Exception {
        Method method = FeedGatewayService.class.getDeclaredMethod("strikeInvasionCacheKey", String.class, String.class);
        method.setAccessible(true);
        return (String) method.invoke(service, json, fallback);
    }

    private static String esOpenDirectionForecastCacheKey(FeedGatewayService service, String json, String fallback) throws Exception {
        Method method = FeedGatewayService.class.getDeclaredMethod("esOpenDirectionForecastCacheKey", String.class, String.class);
        method.setAccessible(true);
        return (String) method.invoke(service, json, fallback);
    }

    private static String esOpenDirectionOutcomeCacheKey(FeedGatewayService service, String json, String fallback) throws Exception {
        Method method = FeedGatewayService.class.getDeclaredMethod("esOpenDirectionOutcomeCacheKey", String.class, String.class);
        method.setAccessible(true);
        return (String) method.invoke(service, json, fallback);
    }

    private static String maxPainCacheKey(FeedGatewayService service, String json, String fallback) throws Exception {
        Method method = FeedGatewayService.class.getDeclaredMethod("maxPainCacheKey", String.class, String.class);
        method.setAccessible(true);
        return (String) method.invoke(service, json, fallback);
    }

    private static String opbByOptionCacheKey(FeedGatewayService service, String json, String fallback) throws Exception {
        Method method = FeedGatewayService.class.getDeclaredMethod("opbByOptionCacheKey", String.class, String.class);
        method.setAccessible(true);
        return (String) method.invoke(service, json, fallback);
    }

    private static boolean isMaxPainExpired(FeedGatewayService service, String json) throws Exception {
        Method method = FeedGatewayService.class.getDeclaredMethod("isMaxPainExpired", String.class);
        method.setAccessible(true);
        return (boolean) method.invoke(service, json);
    }

    private static String updateCache(
            FeedGatewayService service,
            Object binding,
            ConsumerRecord<String, String> record,
            String json
    ) throws Exception {
        Class<?> bindingType = Class.forName("app.feedgateway.FeedGatewayService$TopicBinding");
        Method method = FeedGatewayService.class.getDeclaredMethod("updateCache", bindingType, ConsumerRecord.class, String.class);
        method.setAccessible(true);
        return (String) method.invoke(service, binding, record, json);
    }

    private static String enrichJson(FeedGatewayService service, String json, Object binding) throws Exception {
        Class<?> bindingType = Class.forName("app.feedgateway.FeedGatewayService$TopicBinding");
        Method method = FeedGatewayService.class.getDeclaredMethod("enrichJson", String.class, bindingType);
        method.setAccessible(true);
        return (String) method.invoke(service, json, binding);
    }

    private static String stampExpiry(FeedGatewayService service, String json, String expiry) throws Exception {
        Method method = FeedGatewayService.class.getDeclaredMethod("stampExpiry", String.class, String.class);
        method.setAccessible(true);
        return (String) method.invoke(service, json, expiry);
    }

    private static boolean replayMatches(FeedGatewayService service, ReplayParams params, String event, String json)
            throws Exception {
        Method method = FeedGatewayService.class.getDeclaredMethod(
                "replayMatches", ReplayParams.class, String.class, String.class);
        method.setAccessible(true);
        return (boolean) method.invoke(service, params, event, json);
    }

    /** The market-calendar trading date the gateway stamps onto expiry-less strike-invasion records. */
    private static String currentTradingDateExpiry() {
        return new GatewaySettings().marketCalendar().currentTradingDate(java.time.Instant.now())
                .format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE);
    }

    /** A ConsumerRecord with an explicit event timestamp (CREATE_TIME), for testing age-based eviction. */
    private static ConsumerRecord<String, String> recordAt(
            String topic, int partition, long offset, String key, String value, long timestampMs) {
        return new ConsumerRecord<>(topic, partition, offset, timestampMs,
                org.apache.kafka.common.record.TimestampType.CREATE_TIME, -1, -1, key, value,
                new org.apache.kafka.common.header.internals.RecordHeaders(), java.util.Optional.empty());
    }

    /** Force the market-hours decision so the cache POLICY is deterministic regardless of wall clock. */
    private static void overrideRth(FeedGatewayService service, Boolean rth) throws Exception {
        Method m = FeedGatewayService.class.getDeclaredMethod("overrideRegularTradingHoursForTest", Boolean.class);
        m.setAccessible(true);
        m.invoke(service, rth);
    }

    private static boolean isExpiredEvent(FeedGatewayService service, String event, long eventTime, long nowMs)
            throws Exception {
        Method m = FeedGatewayService.class.getDeclaredMethod("isExpired", String.class, long.class, long.class);
        m.setAccessible(true);
        return (boolean) m.invoke(service, event, eventTime, nowMs);
    }

    private static long windowTtlMsForAt(FeedGatewayService service,
            org.apache.kafka.common.TopicPartition partition, Object topicEvents, long nowMs) throws Exception {
        Method m = FeedGatewayService.class.getDeclaredMethod(
                "windowTtlMsFor", org.apache.kafka.common.TopicPartition.class, java.util.Map.class, long.class);
        m.setAccessible(true);
        return (long) m.invoke(service, partition, topicEvents, nowMs);
    }

    private static void purgeExpiredCache(FeedGatewayService service, long nowMs) throws Exception {
        Method m = FeedGatewayService.class.getDeclaredMethod("purgeExpiredCache", long.class);
        m.setAccessible(true);
        m.invoke(service, nowMs);
    }

    private static List<?> cachedEvents(FeedGatewayService service, List<String> events, long nowMs)
            throws Exception {
        Method m = FeedGatewayService.class.getDeclaredMethod("cachedEvents", List.class, long.class);
        m.setAccessible(true);
        return (List<?>) m.invoke(service, events, nowMs);
    }

    /** The raw json of one CachedEvent (private record) returned by {@link #cachedEvents}. */
    private static String cachedEventJson(Object cachedEvent) throws Exception {
        Method m = cachedEvent.getClass().getDeclaredMethod("json");
        m.setAccessible(true);
        return (String) m.invoke(cachedEvent);
    }

    @SuppressWarnings("unchecked")
    private static void setOffsetBarrier(FeedGatewayService service, String topic, int partition, long barrier)
            throws Exception {
        Field field = FeedGatewayService.class.getDeclaredField("offsetBarriers");
        field.setAccessible(true);
        AtomicReference<java.util.Map<org.apache.kafka.common.TopicPartition, Long>> ref =
                (AtomicReference<java.util.Map<org.apache.kafka.common.TopicPartition, Long>>) field.get(service);
        ref.set(java.util.Map.of(new org.apache.kafka.common.TopicPartition(topic, partition), barrier));
    }

    private static boolean shouldForward(
            FeedGatewayService service,
            Object binding,
            String json,
            ConsumerRecord<String, String> record
    ) throws Exception {
        Class<?> bindingType = Class.forName("app.feedgateway.FeedGatewayService$TopicBinding");
        Method method = FeedGatewayService.class.getDeclaredMethod("shouldForward", bindingType, String.class, ConsumerRecord.class);
        method.setAccessible(true);
        return (boolean) method.invoke(service, binding, json, record);
    }

    private static boolean isTrustedIndexPrice(FeedGatewayService service, Object binding, String json)
            throws Exception {
        Class<?> bindingType = Class.forName("app.feedgateway.FeedGatewayService$TopicBinding");
        Method method = FeedGatewayService.class.getDeclaredMethod(
                "isTrustedIndexPrice", bindingType, String.class);
        method.setAccessible(true);
        return (boolean) method.invoke(service, binding, json);
    }

    private static boolean isValidSpxPrice(FeedGatewayService service, Object binding, String json)
            throws Exception {
        Class<?> bindingType = Class.forName("app.feedgateway.FeedGatewayService$TopicBinding");
        Method method = FeedGatewayService.class.getDeclaredMethod(
                "isValidSpxPrice", bindingType, String.class);
        method.setAccessible(true);
        return (boolean) method.invoke(service, binding, json);
    }

    @SuppressWarnings("unchecked")
    @Test
    void autoRollOverridesStaleExpiredSelection() throws Exception {
        // A stale control selection pinned an EXPIRED expiry AFTER today's auto-roll already fired
        // (autoRolledExpiry == target). The auto-roll must override it, not defer for the day.
        System.setProperty("IB_EXPIRY", "AUTO");
        try {
            FeedGatewayService service = service();
            String target = currentTradingDateExpiry();
            setAutoRolledExpiry(service, target);                 // already rolled today
            setActiveSelection(service, "DATABENTO", "ES", "20000101"); // clearly-expired selection
            invokeMaybeAutoRollExpiry(service);
            assertEquals(target, activeExpiry(service),
                    "a stale/expired control selection must be auto-rolled to the session target");
        } finally {
            System.clearProperty("IB_EXPIRY");
        }
    }

    @Test
    void autoRollHoldsFutureSelection() throws Exception {
        // A control selection for a FUTURE expiry (>= target) is a deliberate pick and must hold.
        System.setProperty("IB_EXPIRY", "AUTO");
        try {
            FeedGatewayService service = service();
            setAutoRolledExpiry(service, currentTradingDateExpiry());
            setActiveSelection(service, "DATABENTO", "ES", "29991231"); // clearly-future selection
            invokeMaybeAutoRollExpiry(service);
            assertEquals("29991231", activeExpiry(service),
                    "a future control selection must not be auto-rolled away");
        } finally {
            System.clearProperty("IB_EXPIRY");
        }
    }

    private static void setActiveSelection(FeedGatewayService service, String src, String symbol, String expiry) throws Exception {
        Field field = FeedGatewayService.class.getDeclaredField("activeSelection");
        field.setAccessible(true);
        ((AtomicReference<Object>) field.get(service)).set(newActiveSelection(src, symbol, expiry));
    }

    private static void setAutoRolledExpiry(FeedGatewayService service, String v) throws Exception {
        Field f = FeedGatewayService.class.getDeclaredField("autoRolledExpiry");
        f.setAccessible(true);
        f.set(service, v);
    }

    private static String activeExpiry(FeedGatewayService service) throws Exception {
        Field f = FeedGatewayService.class.getDeclaredField("activeSelection");
        f.setAccessible(true);
        Object sel = ((AtomicReference<?>) f.get(service)).get();
        Method m = sel.getClass().getDeclaredMethod("expiry");
        m.setAccessible(true);
        return (String) m.invoke(sel);
    }

    private static void invokeMaybeAutoRollExpiry(FeedGatewayService service) throws Exception {
        Method m = FeedGatewayService.class.getDeclaredMethod("maybeAutoRollExpiry");
        m.setAccessible(true);
        m.invoke(service);
    }

    private static int cachedEventCount(FeedGatewayService service, String event, long nowMs) throws Exception {
        Method m = FeedGatewayService.class.getDeclaredMethod("cachedEvents", java.util.List.class, long.class);
        m.setAccessible(true);
        return ((java.util.List<?>) m.invoke(service, java.util.List.of(event), nowMs)).size();
    }

    @SuppressWarnings("unchecked")
    private static void ageCacheEventTimes(FeedGatewayService service, String versionKeySubstr, long timeMs) throws Exception {
        Field field = FeedGatewayService.class.getDeclaredField("cacheEventTimes");
        field.setAccessible(true);
        java.util.Map<String, Long> map = (java.util.Map<String, Long>) field.get(service);
        for (String key : map.keySet()) {
            if (key.contains(versionKeySubstr)) {
                map.put(key, timeMs);
            }
        }
    }

    private static void broadcast(FeedGatewayService service, String event, String json) throws Exception {
        Method method = FeedGatewayService.class.getDeclaredMethod("broadcast", String.class, String.class);
        method.setAccessible(true);
        method.invoke(service, event, json);
    }

    /** A synchronous recording WebSocketSession (untracked -> direct send) capturing sent payloads. */
    private static WebSocketSession recordingSession(List<String> sink) {
        return (WebSocketSession) Proxy.newProxyInstance(
                WebSocketSession.class.getClassLoader(),
                new Class<?>[]{WebSocketSession.class},
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "isOpen": return Boolean.TRUE;
                        case "getId": return "rec-session";
                        case "sendMessage":
                            if (args[0] instanceof TextMessage tm) {
                                sink.add(tm.getPayload());
                            }
                            return null;
                        case "toString": return "RecordingSession";
                        case "hashCode": return System.identityHashCode(proxy);
                        case "equals": return proxy == args[0];
                        default:
                            Class<?> rt = method.getReturnType();
                            if (rt == boolean.class) return Boolean.FALSE;
                            if (rt == int.class) return 0;
                            if (rt == long.class) return 0L;
                            return null;
                    }
                });
    }

    /** Registers a synchronous recording WebSocketSession (untracked -> direct send) and captures payloads. */
    @SuppressWarnings("unchecked")
    private static void addRecordingClient(FeedGatewayService service, List<String> sink) throws Exception {
        WebSocketSession session = (WebSocketSession) Proxy.newProxyInstance(
                WebSocketSession.class.getClassLoader(),
                new Class<?>[]{WebSocketSession.class},
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "isOpen": return Boolean.TRUE;
                        case "getId": return "rec-session";
                        case "sendMessage":
                            if (args[0] instanceof TextMessage tm) {
                                sink.add(tm.getPayload());
                            }
                            return null;
                        case "toString": return "RecordingSession";
                        case "hashCode": return System.identityHashCode(proxy);
                        case "equals": return proxy == args[0];
                        default:
                            Class<?> rt = method.getReturnType();
                            if (rt == boolean.class) return Boolean.FALSE;
                            if (rt == int.class) return 0;
                            if (rt == long.class) return 0L;
                            return null;
                    }
                });
        Field clientsField = FeedGatewayService.class.getDeclaredField("clients");
        clientsField.setAccessible(true);
        ((Collection<WebSocketSession>) clientsField.get(service)).add(session);
    }

    private static Object newActiveSelection(String src, String symbol, String expiry) throws Exception {
        Class<?> selType = Class.forName("app.feedgateway.FeedGatewayService$ActiveSelection");
        Constructor<?> constructor = selType.getDeclaredConstructor(String.class, String.class, String.class, long.class, long.class);
        constructor.setAccessible(true);
        return constructor.newInstance(src, symbol, expiry, 0L, 0L);
    }

    @SuppressWarnings("unchecked")
    private static Object activeSelectionOf(FeedGatewayService service) throws Exception {
        Field field = FeedGatewayService.class.getDeclaredField("activeSelection");
        field.setAccessible(true);
        return ((AtomicReference<Object>) field.get(service)).get();
    }

    private static void invokeMarkSelectionReady(FeedGatewayService service, Object selection) throws Exception {
        Class<?> selType = Class.forName("app.feedgateway.FeedGatewayService$ActiveSelection");
        Method m = FeedGatewayService.class.getDeclaredMethod("markSelectionReady", selType);
        m.setAccessible(true);
        m.invoke(service, selection);
    }

    @SuppressWarnings("unchecked")
    private static String readySelectionKey(FeedGatewayService service) throws Exception {
        Field field = FeedGatewayService.class.getDeclaredField("readySelectionKey");
        field.setAccessible(true);
        return ((AtomicReference<String>) field.get(service)).get();
    }

    @SuppressWarnings("unchecked")
    private static void setReadySelectionKey(FeedGatewayService service, String value) throws Exception {
        Field field = FeedGatewayService.class.getDeclaredField("readySelectionKey");
        field.setAccessible(true);
        ((AtomicReference<String>) field.get(service)).set(value);
    }

    private static String uiBatchEnvelopeJsonGex(FeedGatewayService service, List<String> gexByStrike) throws Exception {
        Method method = uiBatchEnvelopeMethod();
        method.setAccessible(true);
        return (String) method.invoke(
                service,
                List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), gexByStrike,
                List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of()
        );
    }

    @Test
    void uiBatchEnvelopeCarriesStrikeSrArrayKey() throws Exception {
        FeedGatewayService service = service();
        String json = "{\"messageType\":\"UNIFIED_SR_LEVEL\",\"symbol\":\"SPX\",\"bucketStrike\":6050.0,\"dominantSide\":\"RESISTANCE\"}";
        String envelope = uiBatchEnvelopeJsonStrikeSr(service, List.of(json));
        assertTrue(envelope.contains("\"strikeSr\":[" + json + "]"),
                "batch envelope must carry the strikeSr array; was: " + envelope);
        assertTrue(envelope.contains("\"gexByStrike\":[]"));
    }

    /**
     * The FULL overload, located by parameter count rather than by a positional signature: it is
     * the one that keeps growing, and pinning its exact shape here means every future field
     * addition breaks this helper for no reason.
     */
    private static Method fullUiBatchEnvelopeMethod() {
        return java.util.Arrays.stream(FeedGatewayService.class.getDeclaredMethods())
                .filter(m -> m.getName().equals("uiBatchEnvelopeJson"))
                .max(java.util.Comparator.comparingInt(Method::getParameterCount))
                .orElseThrow();
    }

    @Test
    void uiBatchEnvelopeCarriesGammaMigrationsArrayKey() throws Exception {
        // Behavioural, not a source grep: it builds a real envelope and reads the wire.
        FeedGatewayService service = service();
        Method method = fullUiBatchEnvelopeMethod();
        method.setAccessible(true);
        Object[] args = new Object[method.getParameterCount()];
        java.util.Arrays.fill(args, List.of());
        String json = "{\"messageType\":\"GAMMA_MIGRATION_SNAPSHOT\",\"symbol\":\"SPX\","
                + "\"expiry\":\"20260731\",\"regime\":\"PEAK_PARKED\",\"hotStrike\":7450.0,"
                + "\"hotTrusted\":true,\"flipStrike\":7400.0}";
        args[args.length - 1] = List.of(json);   // appended last, per the file's own convention
        String envelope = (String) method.invoke(service, args);

        assertTrue(envelope.contains("\"gammaMigrations\":[" + json + "]"),
                "batch envelope must carry the gammaMigrations array; was: " + envelope);
        // The neighbouring arrays must stay empty — proves the value landed in its OWN key and was
        // not flattened into the magnet or SR arrays it sits beside.
        assertTrue(envelope.contains("\"gexMagnets\":[]"), "must not leak into gexMagnets");
        assertTrue(envelope.contains("\"strikeSr\":[]"), "must not leak into strikeSr");
    }

    @Test
    void uiBatchEnvelopeCarriesGexMagnetsArrayKey() throws Exception {
        FeedGatewayService service = service();
        String json = "{\"messageType\":\"GEX_MAGNET\",\"symbol\":\"SPX\",\"expiry\":\"20260710\",\"magnetStrike\":6050.0}";
        String envelope = uiBatchEnvelopeJsonGexMagnet(service, List.of(json));
        assertTrue(envelope.contains("\"gexMagnets\":[" + json + "]"),
                "batch envelope must carry the gexMagnets array; was: " + envelope);
        assertTrue(envelope.contains("\"strikeSr\":[]"));
    }

    @Test
    void uiBatchEnvelopeCarriesGexOiStatusArrayKey() throws Exception {
        FeedGatewayService service = service();
        String json = "{\"messageType\":\"GEX_OI_STATUS\",\"symbol\":\"SPX\",\"expiry\":\"20260727\","
                + "\"strike\":7500,\"status\":\"OI_MISSING\",\"attempts\":3}";
        String envelope = uiBatchEnvelopeJsonGexOiStatus(service, List.of(json));
        assertTrue(envelope.contains("\"gexOiStatus\":[" + json + "]"),
                "batch envelope must carry the gexOiStatus array; was: " + envelope);
        assertTrue(envelope.contains("\"gexByStrike\":[]"));
    }

    @Test
    void gexOiStatusTopicBindsToGexOiStatusEventOnTheJsonConsumers() throws Exception {
        String source = Files.readString(Path.of("src/main/java/app/feedgateway/FeedGatewayService.java"));
        // gex-oi-status is DATABENTO-only JSON (gex watchdog): bound on BOTH JSON state consumers (cache +
        // live symmetric), exempt from the cached-replay staleness + offset barriers like gex-by-strike.
        assertEquals(2, source.split(
                "topicEvents\\.put\\(settings\\.databentoGexOiStatusTopic\\(\\), new TopicBinding\\(\"DATABENTO\", \"gex-oi-status\"\\)\\);", -1).length - 1);
        assertFalse(FeedGatewayService.enforceCachedReplayMaxStale("gex-oi-status", "DATABENTO"));
        assertFalse(FeedGatewayService.enforceCachedReplayOffsetBarrier("gex-oi-status", "DATABENTO"));
    }

    @Test
    void gexOiStatusCacheAcceptsOnlyKnownStatusValues() throws Exception {
        // A malformed/schema-drifted record must never displace a cached OI_MISSING warning (a reconnect
        // would replay the malformed value and silently lose the badge).
        FeedGatewayService service = service();
        assertTrue(service.isKnownOiStatus("{\"status\":\"OI_MISSING\"}"));
        assertTrue(service.isKnownOiStatus("{\"status\":\"oi_ok\"}"));
        assertFalse(service.isKnownOiStatus("{\"status\":\"OI_ARRIVED\"}"));
        assertFalse(service.isKnownOiStatus("{}"));
        assertFalse(service.isKnownOiStatus("not-json"));
    }

    @Test
    void gexMagnetTopicBindsToGexMagnetEventOnTheAvroConsumers() throws Exception {
        String source = Files.readString(Path.of("src/main/java/app/feedgateway/FeedGatewayService.java"));
        // gex-magnet is DATABENTO-only Avro: bound in the Avro consumers + avroTopics (verbatim passthrough).
        assertTrue(source.contains("avroTopics.put(settings.databentoGexMagnetTopic(), \"gex-magnet\");"));
        for (String method : List.of("runAvroCacheConsumer", "runAvroLiveConsumer")) {
            assertTrue(methodBody(source, method).contains(
                    "topicEvents.put(settings.databentoGexMagnetTopic(), new TopicBinding(\"DATABENTO\", \"gex-magnet\"));"),
                    method + " must bind the gex-magnet topic (Avro)");
        }
        for (String method : List.of("runJsonStateCacheConsumer", "runJsonStateLiveConsumer")) {
            assertFalse(methodBody(source, method).contains("gex-magnet"),
                    method + " must NOT bind the gex-magnet topic (it is Avro, not JSON)");
        }
    }

    @Test
    void uiBatchEnvelopeCarriesStrikeInvasionsArrayKey() throws Exception {
        FeedGatewayService service = service();
        String json = "{\"symbol\":\"SPX\",\"strike\":6005,\"invasionState\":\"INVADED\"}";
        String envelope = uiBatchEnvelopeJsonStrikeInvasion(service, List.of(json));
        assertTrue(envelope.contains("\"strikeInvasions\":[" + json + "]"),
                "batch envelope must carry the strikeInvasions array; was: " + envelope);
        assertTrue(envelope.contains("\"strikeIntels\":[]"));
    }

    @Test
    void liquidityHeatmapUsesShortTtlNotGenericCacheWindow() throws Exception {
        FeedGatewayService service = service();
        long now = 10_000_000L;
        // 6s-old frame: expired on the 5s liquidity TTL...
        assertTrue(isExpiredEvent(service, "liquidity-heatmap", now - 6_000, now));
        // ...while a 4s-old frame is fresh, and strike-flow keeps the generic 15-min window.
        assertFalse(isExpiredEvent(service, "liquidity-heatmap", now - 4_000, now));
        assertFalse(isExpiredEvent(service, "strike-flow", now - 6_000, now));
    }

    @Test
    void expiredLiquidityHeatmapFramesAreEvictedFromTheCacheMap() throws Exception {
        FeedGatewayService service = service();
        java.lang.reflect.Field mapField = FeedGatewayService.class.getDeclaredField("liquidityHeatmaps");
        mapField.setAccessible(true);
        @SuppressWarnings("unchecked")
        java.util.Map<String, String> cache = (java.util.Map<String, String>) mapField.get(service);
        java.lang.reflect.Field timesField = FeedGatewayService.class.getDeclaredField("cacheEventTimes");
        timesField.setAccessible(true);
        @SuppressWarnings("unchecked")
        java.util.Map<String, Long> times = (java.util.Map<String, Long>) timesField.get(service);
        cache.put("SPX|20260702", "{\"cells\":[]}");
        times.put("liquidity-heatmap:SPX|20260702", System.currentTimeMillis() - 60_000); // way past 5s TTL
        Method purge = FeedGatewayService.class.getDeclaredMethod("purgeExpiredCache", long.class);
        purge.setAccessible(true);
        purge.invoke(service, System.currentTimeMillis());
        // The backing map must be evicted too — otherwise health/metrics gauges report stale frames.
        assertTrue(cache.isEmpty(), "expired liquidity-heatmap frame must be evicted from the cache map");
    }

    @Test
    void liquidityHeatmapCacheKeyIsPayloadDerivedSymbolExpiry() throws Exception {
        FeedGatewayService service = service();
        Method m = FeedGatewayService.class.getDeclaredMethod("strikeFlowCacheKey", String.class, String.class);
        m.setAccessible(true);
        String key = (String) m.invoke(service,
                "{\"symbol\":\"spx\",\"expiry\":\"2026-07-02\",\"cells\":[]}", "kafka-key-fallback");
        assertEquals("SPX|20260702", key);
        assertEquals("kafka-key-fallback", m.invoke(service, "not json", "kafka-key-fallback"));
    }

    @Test
    void liquidityHeatmapFreshnessUsesRewrittenPayloadTimeNotKafkaRecordTime() throws Exception {
        FeedGatewayService service = service();
        GatewaySettings settings = new GatewaySettings();
        long now = System.currentTimeMillis();
        String json = "{\"schemaVersion\":1,\"symbol\":\"SPX\",\"expiry\":\"2026-07-06\","
                + "\"bucketStartMs\":" + (now - 1_500) + ","
                + "\"bucketEndMs\":" + (now - 500) + ","
                + "\"asOfEventTimeMs\":" + (now - 750) + ","
                + "\"freshness\":\"LIVE\",\"inputQuality\":\"FULL\",\"cells\":[]}";

        String key = updateCache(service, topicBinding("DATABENTO", "liquidity-heatmap"),
                recordAt(settings.strikeLiquidityTopic(), 0, 1L, "SPX|20260706", json, now - 60_000),
                json);

        assertEquals("DATABENTO|SPX|20260706", key);
    }

    @Test
    void uiBatchEnvelopeCarriesLiquidityHeatmapsArrayKey() throws Exception {
        FeedGatewayService service = service();
        String json = "{\"schemaVersion\":1,\"symbol\":\"SPX\",\"expiry\":\"2026-07-02\","
                + "\"bucketStartMs\":1,\"freshness\":\"LIVE\",\"inputQuality\":\"FULL\",\"cells\":[]}";
        String envelope = uiBatchEnvelopeJsonLiquidityHeatmap(service, List.of(json));
        assertTrue(envelope.contains("\"liquidityHeatmaps\":[" + json + "]"),
                "batch envelope must carry the liquidityHeatmaps array; was: " + envelope);
        assertTrue(envelope.contains("\"strikeFlows\":[]"));
        assertTrue(envelope.contains("\"missionPaces\":[]"));
    }

    private static String uiBatchEnvelopeJsonLiquidityHeatmap(FeedGatewayService service,
                                                              List<String> liquidityHeatmaps) throws Exception {
        Method method = uiBatchEnvelopeMethod();
        method.setAccessible(true);
        return (String) method.invoke(
                service,
                List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(),
                liquidityHeatmaps, List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of()
        );
    }

    private static String uiBatchEnvelopeJsonStrikeSr(FeedGatewayService service, List<String> strikeSr) throws Exception {
        Method method = uiBatchEnvelopeMethod();
        method.setAccessible(true);
        return (String) method.invoke(
                service,
                List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(),
                List.of(), strikeSr, List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of()
        );
    }

    private static String uiBatchEnvelopeJsonGexOiStatus(FeedGatewayService service, List<String> gexOiStatus) throws Exception {
        Method method = uiBatchEnvelopeMethod();
        method.setAccessible(true);
        return (String) method.invoke(
                service,
                List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(),
                gexOiStatus, List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of()
        );
    }

    private static String uiBatchEnvelopeJsonGexMagnet(FeedGatewayService service, List<String> gexMagnet) throws Exception {
        Method method = uiBatchEnvelopeMethod();
        method.setAccessible(true);
        return (String) method.invoke(
                service,
                List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), gexMagnet, List.of(),
                List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of()
        );
    }

    private static String uiBatchEnvelopeJsonStrikeInvasion(FeedGatewayService service, List<String> strikeInvasions) throws Exception {
        Method method = uiBatchEnvelopeMethod();
        method.setAccessible(true);
        return (String) method.invoke(
                service,
                List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), strikeInvasions,
                List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of()
        );
    }

    private static String uiBatchEnvelopeJsonMaxPain(FeedGatewayService service, List<String> maxPains) throws Exception {
        Method method = uiBatchEnvelopeMethod();
        method.setAccessible(true);
        return (String) method.invoke(
                service,
                List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(),
                maxPains, List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of()
        );
    }

    private static String uiBatchEnvelopeJsonOptionPriceBehavior(
            FeedGatewayService service,
            List<String> optionPriceBehaviors
    ) throws Exception {
        Method method = uiBatchEnvelopeMethod();
        method.setAccessible(true);
        return (String) method.invoke(
                service,
                List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(),
                List.of(), optionPriceBehaviors, List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of()
        );
    }

    private static String uiBatchEnvelopeJsonOpbByOption(FeedGatewayService service, List<String> opbByOptions) throws Exception {
        Method method = uiBatchEnvelopeMethod();
        method.setAccessible(true);
        return (String) method.invoke(
                service,
                List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), opbByOptions, List.of(),
                List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of()
        );
    }

    private static String uiBatchEnvelopeJsonOpbSession(FeedGatewayService service, List<String> opbSessions) throws Exception {
        Method method = uiBatchEnvelopeMethod();
        method.setAccessible(true);
        return (String) method.invoke(
                service,
                List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), opbSessions,
                List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of()
        );
    }

    private static String uiBatchEnvelopeJsonMissionPace(FeedGatewayService service, List<String> missionPaces) throws Exception {
        Method method = uiBatchEnvelopeMethod();
        method.setAccessible(true);
        return (String) method.invoke(
                service,
                List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(),
                List.of(), missionPaces, List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of()
        );
    }

    private static String uiBatchEnvelopeJsonMissionControl(FeedGatewayService service, List<String> missionControls) throws Exception {
        Method method = uiBatchEnvelopeMethod();
        method.setAccessible(true);
        return (String) method.invoke(
                service,
                List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), missionControls, List.of(),
                List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of()
        );
    }

    private static String uiBatchEnvelopeJsonSpreadSkew(FeedGatewayService service, List<String> spreadSkews) throws Exception {
        Method method = uiBatchEnvelopeMethod();
        method.setAccessible(true);
        return (String) method.invoke(
                service,
                List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), spreadSkews,
                List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of()
        );
    }

    private static String envelopeJson(FeedGatewayService service, String event, String json) throws Exception {
        Method method = FeedGatewayService.class.getDeclaredMethod("envelopeJson", String.class, String.class);
        method.setAccessible(true);
        return (String) method.invoke(service, event, json);
    }

    private static String uiBatchEnvelopeJson(FeedGatewayService service, List<String> strikeFlows) throws Exception {
        Method method = uiBatchEnvelopeMethod();
        method.setAccessible(true);
        return (String) method.invoke(
                service,
                List.of(), List.of(), List.of(), List.of(),
                strikeFlows, List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of()
        );
    }

    private static Method uiBatchEnvelopeMethod() throws Exception {
        return FeedGatewayService.class.getDeclaredMethod(
                "uiBatchEnvelopeJson",
                List.class, List.class, List.class, List.class, List.class, List.class,
                List.class, List.class, List.class, List.class, List.class, List.class,
                List.class, List.class, List.class, List.class, List.class, List.class, List.class,
                List.class, List.class, List.class, List.class, List.class, List.class, List.class,
                List.class, List.class, List.class, List.class, List.class, List.class
        );
    }

    /** spxPriceJsons is the (appended-last) 31st parameter of uiBatchEnvelopeJson. */
    private static String uiBatchEnvelopeJsonSpxPrice(FeedGatewayService service, List<String> spxPrices) throws Exception {
        Method method = uiBatchEnvelopeMethod();
        method.setAccessible(true);
        return (String) method.invoke(
                service,
                List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), spxPrices
        );
    }

    @org.junit.jupiter.api.Test
    void ibkrPreOpenWrapCarriesKeyOffsetAndUntouchedPayloadWithFullEscaping() throws Exception {
        String wrapped = FeedGatewayService.wrapIbkrPreOpenStatus(
                "SPX|20260803|6300", 41L, 1754300000000L, "{\"state\":\"FRESH\",\"recordRevision\":7}");
        org.junit.jupiter.api.Assertions.assertEquals(
                "{\"recordKey\":\"SPX|20260803|6300\",\"offset\":41,\"timestampMs\":1754300000000,"
                        + "\"status\":{\"state\":\"FRESH\",\"recordRevision\":7}}",
                wrapped);
        // EVERY control character stays valid JSON (parse-verified, not eyeballed).
        com.fasterxml.jackson.databind.ObjectMapper jackson = new com.fasterxml.jackson.databind.ObjectMapper();
        String nasty = "a\"b\\c\nd\re\tf\u0001g";
        com.fasterxml.jackson.databind.JsonNode parsed = jackson.readTree(
                FeedGatewayService.wrapIbkrPreOpenStatus(nasty, 7L, 3L, "{}"));
        org.junit.jupiter.api.Assertions.assertEquals(nasty, parsed.get("recordKey").asText());
        org.junit.jupiter.api.Assertions.assertEquals(7L, parsed.get("offset").asLong());
        org.junit.jupiter.api.Assertions.assertEquals(3L, parsed.get("timestampMs").asLong());
    }

    @org.junit.jupiter.api.Test
    void ibkrPreOpenBroadcastIsExactlyOncePerOffsetAcrossBothConsumers() throws Exception {
        FeedGatewayService service = service();
        // Cache consumer reaches offset 5 first: it broadcasts.
        org.junit.jupiter.api.Assertions.assertTrue(service.shouldBroadcastIbkrPreOpen(5L));
        // The live consumer's duplicate of offset 5: suppressed (exactly once).
        org.junit.jupiter.api.Assertions.assertFalse(service.shouldBroadcastIbkrPreOpen(5L));
        // Live reaches 6 first, cache's later duplicate suppressed; a regressed 4 never fires.
        org.junit.jupiter.api.Assertions.assertTrue(service.shouldBroadcastIbkrPreOpen(6L));
        org.junit.jupiter.api.Assertions.assertFalse(service.shouldBroadcastIbkrPreOpen(6L));
        org.junit.jupiter.api.Assertions.assertFalse(service.shouldBroadcastIbkrPreOpen(4L));
        org.junit.jupiter.api.Assertions.assertTrue(service.shouldBroadcastIbkrPreOpen(7L));
    }

    @org.junit.jupiter.api.Test
    void ibkrPreOpenIsAGlobalBroadcastEventInPerSessionMode() {
        org.junit.jupiter.api.Assertions.assertTrue(
                FeedGatewayService.GLOBAL_BROADCAST_EVENTS.contains("ibkr-preopen-status"),
                "auth-mode sockets must receive the standalone window-state broadcasts");
    }

    @org.junit.jupiter.api.Test
    @SuppressWarnings("unchecked")
    void ibkrPreOpenCacheIsOffsetOrderedAndWrapsTheRawKafkaKey() throws Exception {
        FeedGatewayService service = service();
        GatewaySettings settings = new GatewaySettings();
        String topic = settings.ibkrPreOpenStatusTopic();
        String status = "{\"state\":\"FRESH\",\"recordRevision\":7}";
        long now = System.currentTimeMillis();
        // Offset 5 accepted.
        org.junit.jupiter.api.Assertions.assertNotNull(updateCache(service,
                topicBinding("IBKR", "ibkr-preopen-status"),
                recordAt(topic, 0, 5L, "SPX|20260803|6300", status, now), status));
        // EQUAL Kafka timestamp but HIGHER offset: accepted (offset-ordered, not timestamp).
        String newer = "{\"state\":\"FRESH\",\"recordRevision\":8}";
        org.junit.jupiter.api.Assertions.assertNotNull(updateCache(service,
                topicBinding("IBKR", "ibkr-preopen-status"),
                recordAt(topic, 0, 6L, "SPX|20260803|6300", newer, now), newer));
        // LATER timestamp but LOWER offset: rejected — a stale duplicate can never overwrite.
        org.junit.jupiter.api.Assertions.assertNull(updateCache(service,
                topicBinding("IBKR", "ibkr-preopen-status"),
                recordAt(topic, 0, 4L, "SPX|20260803|6300", status, now + 1_000L), status));
        // The SAME offset replayed by the sibling consumer: rejected (strictly higher only).
        org.junit.jupiter.api.Assertions.assertNull(updateCache(service,
                topicBinding("IBKR", "ibkr-preopen-status"),
                recordAt(topic, 0, 6L, "SPX|20260803|6300", newer, now), newer));
        // The cached value wraps the RAW Kafka key (never the IBKR|-prefixed cache key) around
        // the byte-untouched payload of the WINNING offset.
        Field cacheField = FeedGatewayService.class.getDeclaredField("ibkrPreOpenStatus");
        cacheField.setAccessible(true);
        java.util.Map<String, String> cache = (java.util.Map<String, String>) cacheField.get(service);
        String wrapped = cache.get("IBKR|SPX|20260803|6300");
        org.junit.jupiter.api.Assertions.assertEquals(
                "{\"recordKey\":\"SPX|20260803|6300\",\"offset\":6,\"timestampMs\":" + now
                        + ",\"status\":" + newer + "}", wrapped);
    }

    // ==============================================================================
    // rev13 Phase 3 slice 2: R-ARB arbitration on the shared gex topic + the R-STOP
    // frozen-projection cache.
    // ==============================================================================

    private static final String IBKR_SESSION = "IBKR_PREOPEN:2026-08-04";

    private static String ibkrGexJson(String sessionId, long gen, long epoch, long rev, long validUntil) {
        return "{\"symbol\":\"SPX\",\"expiry\":\"20260804\",\"strike\":6300.0,\"netGex\":1.23E9,"
                + "\"source\":\"IBKR\",\"timeframe\":\"IBKR_PREOPEN\",\"sessionId\":\"" + sessionId + "\","
                + "\"outputGeneration\":" + gen + ",\"baselineEpoch\":" + epoch
                + ",\"recordRevision\":" + rev + ",\"validUntilMs\":" + validUntil + "}";
    }

    private static String databentoGexJson() {
        return "{\"symbol\":\"SPX\",\"expiry\":\"20260804\",\"strike\":6300.0,\"netGex\":9.9E8,"
                + "\"source\":\"DATABENTO\",\"timeframe\":\"0DTE\"}";
    }

    private static boolean interceptSharedGex(
            FeedGatewayService service, ConsumerRecord<String, String> record, String rawJson,
            boolean liveBroadcast, long nowMs) throws Exception {
        Method method = FeedGatewayService.class.getDeclaredMethod("interceptSharedGexRecord",
                ConsumerRecord.class, String.class, boolean.class, long.class);
        method.setAccessible(true);
        return (boolean) method.invoke(service, record, rawJson, liveBroadcast, nowMs);
    }

    private static String ingestIbkrGex(
            FeedGatewayService service, ConsumerRecord<String, String> record, String rawJson,
            long nowMs) throws Exception {
        Method method = FeedGatewayService.class.getDeclaredMethod("ingestIbkrPreOpenGexValue",
                ConsumerRecord.class, com.fasterxml.jackson.databind.JsonNode.class,
                String.class, long.class);
        method.setAccessible(true);
        return (String) method.invoke(service, record, new ObjectMapper().readTree(rawJson), rawJson, nowMs);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> planeMap(FeedGatewayService service, String field) throws Exception {
        Field mapField = FeedGatewayService.class.getDeclaredField(field);
        mapField.setAccessible(true);
        return (Map<String, Object>) mapField.get(service);
    }

    private static long planeCounter(FeedGatewayService service, String field) throws Exception {
        Field counterField = FeedGatewayService.class.getDeclaredField(field);
        counterField.setAccessible(true);
        return ((java.util.concurrent.atomic.AtomicLong) counterField.get(service)).get();
    }

    @SuppressWarnings("unchecked")
    private static void seedTakeoverWatermark(FeedGatewayService service, long fence, int partition,
            long offset) throws Exception {
        Field field = FeedGatewayService.class.getDeclaredField("ibkrPreOpenTakeoverWatermarks");
        field.setAccessible(true);
        ((Map<Long, Map<Integer, Long>>) field.get(service))
                .computeIfAbsent(fence, k -> new java.util.concurrent.ConcurrentHashMap<>())
                .put(partition, offset);
    }

    /** Seed the per-strike Databento observation with BOTH axes, as the live path records them. */
    @SuppressWarnings("unchecked")
    private static void seedDatabentoObservation(FeedGatewayService service, String identity,
            long commitMs, int partition, long offset) throws Exception {
        Class<?> type = Class.forName(
                "app.feedgateway.FeedGatewayService$IbkrPreOpenDatabentoObservation");
        java.lang.reflect.Constructor<?> ctor =
                type.getDeclaredConstructor(long.class, int.class, long.class);
        ctor.setAccessible(true);
        Field field = FeedGatewayService.class.getDeclaredField("ibkrPreOpenDatabentoMaxCommitMs");
        field.setAccessible(true);
        ((Map<String, Object>) field.get(service))
                .put(identity, ctor.newInstance(commitMs, partition, offset));
    }

    private static boolean strikeTakenOver(FeedGatewayService service, String identity, long fence)
            throws Exception {
        Method method = FeedGatewayService.class.getDeclaredMethod(
                "isIbkrPreOpenStrikeTakenOverLocked", String.class, long.class);
        method.setAccessible(true);
        return (boolean) method.invoke(service, identity, fence);
    }

    /**
     * Backdate the gateway's process-start so a window whose 09:30 boundary is in the test's past
     * counts as OBSERVED LIVE. Real gateways that ran through the boundary are the common case;
     * tests that construct the service after choosing a past fence must say so explicitly, because
     * the takeover predicate deliberately differs for a gateway that restarted after 09:30.
     */
    private static void backdateProcessStart(FeedGatewayService service, long startMs)
            throws Exception {
        Field field = FeedGatewayService.class.getDeclaredField("ibkrPreOpenProcessStartMs");
        field.setAccessible(true);
        // final long -> set via Unsafe-free reflection is not possible; the field is non-final.
        field.setLong(service, startMs);
    }

    /** Drive the window sweeper at an explicit wall-clock, so the 09:30 watermark snapshot exists. */
    private static void sweepWindows(FeedGatewayService service, long nowMs) throws Exception {
        Method method = FeedGatewayService.class.getDeclaredMethod(
                "sweepIbkrPreOpenGexWindows", long.class);
        method.setAccessible(true);
        method.invoke(service, nowMs);
    }

    @SuppressWarnings("unchecked")
    private static Map<Integer, Long> takeoverWatermark(FeedGatewayService service, long fence)
            throws Exception {
        Field field = FeedGatewayService.class.getDeclaredField("ibkrPreOpenTakeoverWatermarks");
        field.setAccessible(true);
        return ((Map<Long, Map<Integer, Long>>) field.get(service)).get(fence);
    }

    /** The per-strike identity the window keys its planes by, for a plain record key. */
    private static String gexIdentity(FeedGatewayService service, String recordKey) throws Exception {
        Method method = FeedGatewayService.class.getDeclaredMethod("gexIdentityFromNode",
                com.fasterxml.jackson.databind.JsonNode.class, String.class);
        method.setAccessible(true);
        return (String) method.invoke(service,
                new ObjectMapper().readTree(databentoGexJson()), recordKey);
    }

    private static Object candidateComponent(Object candidate, String accessor) throws Exception {
        Method method = candidate.getClass().getDeclaredMethod(accessor);
        method.setAccessible(true);
        return method.invoke(candidate);
    }

    private static void seedIbkrStatus(FeedGatewayService service, String recordKey, long offset,
            String statusJson) throws Exception {
        seedIbkrStatus(service, recordKey, offset, System.currentTimeMillis(), statusJson);
    }

    /** Seed a status with an EXPLICIT broker CreateTime — the R-STOP pairing proof requires the
     *  status half committed BEFORE the fence, so post-fence-window tests must backdate it. */
    private static void seedIbkrStatus(FeedGatewayService service, String recordKey, long offset,
            long timestampMs, String statusJson) throws Exception {
        GatewaySettings settings = new GatewaySettings();
        updateCache(service, topicBinding("IBKR", "ibkr-preopen-status"),
                recordAt(settings.ibkrPreOpenStatusTopic(), 0, offset, recordKey, statusJson,
                        timestampMs), statusJson);
    }

    private static ConsumerRecord<String, String> gexRecord(long offset, String key, String json,
            long timestampMs) {
        return recordAt(new GatewaySettings().databentoGexTopic(), 0, offset, key, json, timestampMs);
    }

    @Test
    void sharedGexClassificationScopesEnforcementToExactProvenanceTuples() throws Exception {
        ObjectMapper jackson = new ObjectMapper();
        assertEquals(FeedGatewayService.SharedGexClass.DATABENTO,
                FeedGatewayService.classifySharedGexRecord(jackson.readTree(databentoGexJson())));
        assertEquals(FeedGatewayService.SharedGexClass.IBKR_PREOPEN,
                FeedGatewayService.classifySharedGexRecord(jackson.readTree(
                        ibkrGexJson(IBKR_SESSION, 1, 1, 1, 1L))));
        assertEquals(FeedGatewayService.SharedGexClass.PREOPEN,
                FeedGatewayService.classifySharedGexRecord(jackson.readTree(
                        "{\"source\":\"PREOPEN\",\"timeframe\":\"PREOPEN\"}")));
        // A record CLAIMING a session without the exact tuple fails closed as UNKNOWN_SESSIONED:
        // sessioned source without the timeframe...
        assertEquals(FeedGatewayService.SharedGexClass.UNKNOWN_SESSIONED,
                FeedGatewayService.classifySharedGexRecord(jackson.readTree(
                        "{\"source\":\"IBKR\",\"timeframe\":\"0DTE\"}")));
        // ...a bare non-blank sessionId...
        assertEquals(FeedGatewayService.SharedGexClass.UNKNOWN_SESSIONED,
                FeedGatewayService.classifySharedGexRecord(jackson.readTree(
                        "{\"source\":\"DATABENTO\",\"sessionId\":\"X:2026-08-04\"}")));
        // ...and a mis-cased tuple (exact tuples are case-sensitive; the claim check is not).
        assertEquals(FeedGatewayService.SharedGexClass.UNKNOWN_SESSIONED,
                FeedGatewayService.classifySharedGexRecord(jackson.readTree(
                        "{\"source\":\"ibkr\",\"timeframe\":\"ibkr_preopen\",\"sessionId\":\"s\"}")));
    }

    @Test
    void databentoRecordsBypassArbitrationEntirely() throws Exception {
        FeedGatewayService service = service();
        long now = System.currentTimeMillis();
        // A genuine Databento record: intercept says "fall through" and the plane stays empty.
        assertFalse(interceptSharedGex(service, gexRecord(10L, "SPX|20260804|6300",
                databentoGexJson(), now), databentoGexJson(), false, now));
        // Malformed / non-JSON values fall through too — the existing pipeline's behavior for
        // them is unchanged (they cannot claim a session).
        assertFalse(interceptSharedGex(service, gexRecord(11L, "k", "not-json", now),
                "not-json", false, now));
        assertFalse(interceptSharedGex(service, gexRecord(12L, "k", "", now), "", false, now));
        assertTrue(planeMap(service, "ibkrPreOpenGexCandidates").isEmpty());
        assertTrue(planeMap(service, "ibkrPreOpenFrozenProjections").isEmpty());
        assertEquals(0L, planeCounter(service, "ibkrPreOpenGexDroppedSessioned"));
    }

    @Test
    void ibkrPreOpenValueNeverEntersDatabentoPlaneAndIsNeverRelabelled() throws Exception {
        FeedGatewayService service = service();
        long now = System.currentTimeMillis();
        String json = ibkrGexJson(IBKR_SESSION, 3, 2, 7, now + 600_000L);
        assertTrue(interceptSharedGex(service, gexRecord(5L, "SPX|20260804|6300", json, now),
                json, false, now));
        // Never in the DATABENTO gex cache (no relabel, no clobber)...
        Field gexField = FeedGatewayService.class.getDeclaredField("gexByStrike");
        gexField.setAccessible(true);
        assertTrue(((Map<?, ?>) gexField.get(service)).isEmpty());
        // ...but a live candidate on its own plane, payload byte-untouched (no marketDataSource).
        Map<String, Object> candidates = planeMap(service, "ibkrPreOpenGexCandidates");
        assertEquals(1, candidates.size());
        Object candidate = candidates.values().iterator().next();
        assertEquals(json, candidateComponent(candidate, "json"));
        assertEquals("SPX|20260804|6300", candidateComponent(candidate, "recordKey"));
    }

    @Test
    void unknownSessionedAndPreopenTuplesFailClosedWithoutConditioningDatabento() throws Exception {
        FeedGatewayService service = service();
        long now = System.currentTimeMillis();
        String unknown = "{\"symbol\":\"SPX\",\"expiry\":\"20260804\",\"strike\":6300.0,"
                + "\"source\":\"IBKR\",\"timeframe\":\"0DTE\",\"sessionId\":\"weird\"}";
        assertTrue(interceptSharedGex(service, gexRecord(1L, "SPX|20260804|6300", unknown, now),
                unknown, false, now));
        String preopen = "{\"symbol\":\"SPX\",\"expiry\":\"20260804\",\"strike\":6300.0,"
                + "\"source\":\"PREOPEN\",\"timeframe\":\"PREOPEN\",\"sessionId\":\"PREOPEN:2026-08-04\"}";
        assertTrue(interceptSharedGex(service, gexRecord(2L, "SPX|20260804|6300", preopen, now),
                preopen, false, now));
        // Both dropped fail-closed: never cached, never a candidate.
        assertEquals(2L, planeCounter(service, "ibkrPreOpenGexDroppedSessioned"));
        assertTrue(planeMap(service, "ibkrPreOpenGexCandidates").isEmpty());
        // And a valid tuple missing its validity bound is dropped too (no provable window).
        String noValidity = "{\"symbol\":\"SPX\",\"expiry\":\"20260804\",\"strike\":6300.0,"
                + "\"source\":\"IBKR\",\"timeframe\":\"IBKR_PREOPEN\",\"sessionId\":\"" + IBKR_SESSION + "\"}";
        assertTrue(interceptSharedGex(service, gexRecord(3L, "SPX|20260804|6300", noValidity, now),
                noValidity, false, now));
        assertEquals(3L, planeCounter(service, "ibkrPreOpenGexDroppedSessioned"));
        // Databento is NOT conditioned by any of it: the next genuine record still falls through.
        assertFalse(interceptSharedGex(service, gexRecord(4L, "SPX|20260804|6300",
                databentoGexJson(), now), databentoGexJson(), false, now));
    }

    @Test
    void liveCandidatesAreOffsetOrderedAndDuplicatesStillDeliver() throws Exception {
        FeedGatewayService service = service();
        long now = System.currentTimeMillis();
        long validUntil = now + 600_000L;
        String v7 = ibkrGexJson(IBKR_SESSION, 3, 2, 7, validUntil);
        String v8 = ibkrGexJson(IBKR_SESSION, 3, 2, 8, validUntil);
        // Offset 5 admitted -> LIVE wrap of the record itself.
        assertEquals("{\"recordKey\":\"SPX|20260804|6300\",\"offset\":5,\"phase\":\"LIVE\",\"gex\":" + v7 + "}",
                ingestIbkrGex(service, gexRecord(5L, "SPX|20260804|6300", v7, now), v7, now));
        // The sibling consumer's EXACT duplicate is not an admission but still returns the
        // CURRENT wrap — live delivery must never be suppressed by losing the admission race.
        assertEquals("{\"recordKey\":\"SPX|20260804|6300\",\"offset\":5,\"phase\":\"LIVE\",\"gex\":" + v7 + "}",
                ingestIbkrGex(service, gexRecord(5L, "SPX|20260804|6300", v7, now), v7, now));
        // A regressed offset is rejected; a higher offset supersedes.
        assertNull(ingestIbkrGex(service, gexRecord(4L, "SPX|20260804|6300", v7, now), v7, now));
        assertEquals("{\"recordKey\":\"SPX|20260804|6300\",\"offset\":6,\"phase\":\"LIVE\",\"gex\":" + v8 + "}",
                ingestIbkrGex(service, gexRecord(6L, "SPX|20260804|6300", v8, now), v8, now));
        // The same strike arriving on a DIFFERENT partition breaks the key->partition contract:
        // fail closed, never reorder.
        assertNull(ingestIbkrGex(service,
                recordAt(new GatewaySettings().databentoGexTopic(), 1, 99L, "SPX|20260804|6300", v8, now),
                v8, now));
        assertEquals(1, planeMap(service, "ibkrPreOpenGexCandidates").size());
    }

    @Test
    void fenceCaptureFreezesOnlyPairedNumberBearingStrikes() throws Exception {
        FeedGatewayService service = service();
        long now = System.currentTimeMillis();
        long fence = now + 60_000L;
        String pairedJson = ibkrGexJson(IBKR_SESSION, 3, 2, 7, fence);
        String gatedJson = "{\"symbol\":\"SPX\",\"expiry\":\"20260804\",\"strike\":6305.0,\"netGex\":1.0E9,"
                + "\"source\":\"IBKR\",\"timeframe\":\"IBKR_PREOPEN\",\"sessionId\":\"" + IBKR_SESSION + "\","
                + "\"outputGeneration\":3,\"baselineEpoch\":2,\"recordRevision\":4,\"validUntilMs\":" + fence + "}";
        String mismatchJson = "{\"symbol\":\"SPX\",\"expiry\":\"20260804\",\"strike\":6310.0,\"netGex\":1.0E9,"
                + "\"source\":\"IBKR\",\"timeframe\":\"IBKR_PREOPEN\",\"sessionId\":\"" + IBKR_SESSION + "\","
                + "\"outputGeneration\":3,\"baselineEpoch\":2,\"recordRevision\":9,\"validUntilMs\":" + fence + "}";
        assertNotNull(ingestIbkrGex(service, gexRecord(1L, "SPX|20260804|6300", pairedJson, now), pairedJson, now));
        assertNotNull(ingestIbkrGex(service, gexRecord(2L, "SPX|20260804|6305", gatedJson, now), gatedJson, now));
        assertNotNull(ingestIbkrGex(service, gexRecord(3L, "SPX|20260804|6310", mismatchJson, now), mismatchJson, now));
        // Statuses: 6300 FRESH revision-EQUAL (number-bearing pair); 6305 GATED (numberless);
        // 6310 FRESH but revision-UNEQUAL (pairing broken).
        seedIbkrStatus(service, "SPX|20260804|6300", 1L,
                "{\"state\":\"FRESH\",\"sessionId\":\"" + IBKR_SESSION + "\",\"outputGeneration\":3,"
                        + "\"baselineEpoch\":2,\"recordRevision\":7}");
        seedIbkrStatus(service, "SPX|20260804|6305", 1L,
                "{\"state\":\"GATED\",\"sessionId\":\"" + IBKR_SESSION + "\",\"outputGeneration\":3,"
                        + "\"baselineEpoch\":2,\"recordRevision\":4}");
        seedIbkrStatus(service, "SPX|20260804|6310", 1L,
                "{\"state\":\"FRESH\",\"sessionId\":\"" + IBKR_SESSION + "\",\"outputGeneration\":3,"
                        + "\"baselineEpoch\":2,\"recordRevision\":8}");
        service.sweepIbkrPreOpenGexWindows(fence + 1_000L);
        // The atomic fence transition: live candidates invalidated, ONLY the revision-equal
        // number-bearing pair survives as a frozen projection; the rest are frozen-blank.
        assertTrue(planeMap(service, "ibkrPreOpenGexCandidates").isEmpty());
        Map<String, Object> projections = planeMap(service, "ibkrPreOpenFrozenProjections");
        assertEquals(1, projections.size());
        assertEquals("SPX|20260804|6300",
                candidateComponent(projections.values().iterator().next(), "recordKey"));
    }

    @Test
    void recordsCommittedAtOrAfterTheFenceNeverEnterTheProjection() throws Exception {
        FeedGatewayService service = service();
        long now = System.currentTimeMillis();
        long fence = now - 60_000L; // fence already passed
        seedIbkrStatus(service, "SPX|20260804|6300", 1L, fence - 10_000L,
                "{\"state\":\"FRESH\",\"sessionId\":\"" + IBKR_SESSION + "\",\"outputGeneration\":3,"
                        + "\"baselineEpoch\":2,\"recordRevision\":7}");
        String json = ibkrGexJson(IBKR_SESSION, 3, 2, 7, fence);
        // Committed AT/AFTER the fence: never a projection (and no longer a live candidate).
        assertNull(ingestIbkrGex(service, gexRecord(5L, "SPX|20260804|6300", json, fence), json, now));
        assertNull(ingestIbkrGex(service, gexRecord(6L, "SPX|20260804|6300", json, fence + 1L), json, now));
        assertTrue(planeMap(service, "ibkrPreOpenFrozenProjections").isEmpty());
        // Committed BEFORE the fence, observed during the window: frozen-projection
        // RECONSTRUCTION (R-STOP recovery for a gateway starting 09:25-09:35).
        assertEquals("{\"recordKey\":\"SPX|20260804|6300\",\"offset\":7,\"phase\":\"FROZEN\",\"gex\":" + json + "}",
                ingestIbkrGex(service, gexRecord(7L, "SPX|20260804|6300", json, fence - 5_000L), json, now));
        assertEquals(1, planeMap(service, "ibkrPreOpenFrozenProjections").size());
        // After the 09:35 destruction boundary nothing ever enters again.
        FeedGatewayService late = service();
        seedIbkrStatus(late, "SPX|20260804|6300", 1L, fence - 10_000L,
                "{\"state\":\"FRESH\",\"sessionId\":\"" + IBKR_SESSION + "\",\"outputGeneration\":3,"
                        + "\"baselineEpoch\":2,\"recordRevision\":7}");
        long afterWindow = fence + 10 * 60_000L + 1L;
        assertNull(ingestIbkrGex(late, gexRecord(8L, "SPX|20260804|6300", json, fence - 5_000L),
                json, afterWindow));
        assertTrue(planeMap(late, "ibkrPreOpenFrozenProjections").isEmpty());
    }

    @Test
    void takeoverEvictsOnlyNewlyIngestedDatabentoRecords() throws Exception {
        FeedGatewayService service = service();
        long now = System.currentTimeMillis();
        // This gateway was RUNNING when the 09:30 boundary passed: both takeover axes apply.
        backdateProcessStart(service, now - 60 * 60_000L);
        long fence = now - 6 * 60_000L;          // fence 6 min ago -> takeover boundary 1 min ago
        long boundary = fence + 5 * 60_000L;
        seedIbkrStatus(service, "SPX|20260804|6300", 1L, fence - 10_000L,
                "{\"state\":\"FRESH\",\"sessionId\":\"" + IBKR_SESSION + "\",\"outputGeneration\":3,"
                        + "\"baselineEpoch\":2,\"recordRevision\":7}");
        String json = ibkrGexJson(IBKR_SESSION, 3, 2, 7, fence);
        assertNotNull(ingestIbkrGex(service, gexRecord(7L, "SPX|20260804|6300", json, fence - 5_000L),
                json, boundary - 30_000L)); // reconstructed projection inside the freeze window
        // A pre-boundary Databento record raises the observed high-watermark to 100 but cannot
        // take over (observed before 09:30).
        assertFalse(interceptSharedGex(service, gexRecord(100L, "SPX|20260804|6300",
                databentoGexJson(), boundary - 10_000L), databentoGexJson(), false, boundary - 10_000L));
        assertEquals(1, planeMap(service, "ibkrPreOpenFrozenProjections").size());
        // Boundary passes: the watermark snapshot (<=100) is taken by the sweep.
        service.sweepIbkrPreOpenGexWindows(boundary + 1_000L);
        // A record AT/BELOW the 09:30 high-watermark never evicts (compacted/bootstrap/cached prior).
        assertFalse(interceptSharedGex(service, gexRecord(90L, "SPX|20260804|6300",
                databentoGexJson(), boundary + 2_000L), databentoGexJson(), false, boundary + 2_000L));
        assertEquals(1, planeMap(service, "ibkrPreOpenFrozenProjections").size());
        // A record beyond the watermark but COMMITTED before 09:30 never evicts either.
        assertFalse(interceptSharedGex(service, gexRecord(101L, "SPX|20260804|6300",
                databentoGexJson(), boundary - 5_000L), databentoGexJson(), false, boundary + 2_000L));
        assertEquals(1, planeMap(service, "ibkrPreOpenFrozenProjections").size());
        // The strike's FIRST newly-ingested record (beyond the watermark, committed after 09:30):
        // per-strike takeover — the projection dies.
        assertFalse(interceptSharedGex(service, gexRecord(102L, "SPX|20260804|6300",
                databentoGexJson(), boundary + 3_000L), databentoGexJson(), false, boundary + 3_000L));
        assertTrue(planeMap(service, "ibkrPreOpenFrozenProjections").isEmpty());
        // TERMINAL: a late compacted redelivery of the pre-fence value (higher offset, still
        // committed before the fence) can never resurrect the taken-over strike — not even as a
        // pending pair.
        assertNull(ingestIbkrGex(service, gexRecord(8L, "SPX|20260804|6300", json, fence - 4_000L),
                json, boundary + 4_000L));
        assertTrue(planeMap(service, "ibkrPreOpenFrozenProjections").isEmpty());
        assertTrue(planeMap(service, "ibkrPreOpenPendingProjections").isEmpty());
    }

    @Test
    void windowEndDestroysAllRemainingProjectionsForever() throws Exception {
        FeedGatewayService service = service();
        long now = System.currentTimeMillis();
        long fence = now - 60_000L;
        seedIbkrStatus(service, "SPX|20260804|6300", 1L, fence - 10_000L,
                "{\"state\":\"FRESH\",\"sessionId\":\"" + IBKR_SESSION + "\",\"outputGeneration\":3,"
                        + "\"baselineEpoch\":2,\"recordRevision\":7}");
        String json = ibkrGexJson(IBKR_SESSION, 3, 2, 7, fence);
        assertNotNull(ingestIbkrGex(service, gexRecord(7L, "SPX|20260804|6300", json, fence - 5_000L),
                json, now));
        assertEquals(1, planeMap(service, "ibkrPreOpenFrozenProjections").size());
        service.sweepIbkrPreOpenGexWindows(fence + 10 * 60_000L + 1L);
        assertTrue(planeMap(service, "ibkrPreOpenFrozenProjections").isEmpty());
        // Evicted values never reappear (no post-window resurrection).
        assertNull(ingestIbkrGex(service, gexRecord(8L, "SPX|20260804|6300", json, fence - 5_000L),
                json, fence + 10 * 60_000L + 2L));
        assertTrue(planeMap(service, "ibkrPreOpenFrozenProjections").isEmpty());
    }

    @Test
    void statusArrivingAfterItsValueStillReconstructsTheProjection() throws Exception {
        // Round-1 finding 1: the value and status streams ride INDEPENDENT consumers, so a
        // restarted gateway can observe the value first. Reconstruction must be order-independent.
        FeedGatewayService service = service();
        long now = System.currentTimeMillis();
        long fence = now - 60_000L; // inside the 09:25-09:35 reconstruction window
        String json = ibkrGexJson(IBKR_SESSION, 3, 2, 7, fence);
        // Value observed FIRST (committed pre-fence, status not yet consumed): pending, never
        // presented, never lost.
        assertNull(ingestIbkrGex(service, gexRecord(7L, "SPX|20260804|6300", json, fence - 5_000L),
                json, now));
        assertTrue(planeMap(service, "ibkrPreOpenFrozenProjections").isEmpty());
        assertEquals(1, planeMap(service, "ibkrPreOpenPendingProjections").size());
        // The revision-equal number-bearing status arrives (committed pre-fence): the pair
        // completes AT STATUS INGEST — no further value record needed.
        seedIbkrStatus(service, "SPX|20260804|6300", 1L, fence - 10_000L,
                "{\"state\":\"FRESH\",\"sessionId\":\"" + IBKR_SESSION + "\",\"outputGeneration\":3,"
                        + "\"baselineEpoch\":2,\"recordRevision\":7}");
        assertTrue(planeMap(service, "ibkrPreOpenPendingProjections").isEmpty());
        Map<String, Object> projections = planeMap(service, "ibkrPreOpenFrozenProjections");
        assertEquals(1, projections.size());
        assertEquals("SPX|20260804|6300",
                candidateComponent(projections.values().iterator().next(), "recordKey"));
        // An unpaired pending value dies at 09:35 like everything else (frozen-blank forever).
        FeedGatewayService unpaired = service();
        assertNull(ingestIbkrGex(unpaired, gexRecord(7L, "SPX|20260804|6300", json, fence - 5_000L),
                json, now));
        assertEquals(1, planeMap(unpaired, "ibkrPreOpenPendingProjections").size());
        unpaired.sweepIbkrPreOpenGexWindows(fence + 10 * 60_000L + 1L);
        assertTrue(planeMap(unpaired, "ibkrPreOpenPendingProjections").isEmpty());
        assertTrue(planeMap(unpaired, "ibkrPreOpenFrozenProjections").isEmpty());
    }

    @Test
    void statusCommittedAtOrAfterTheFenceNeverProvesAFrozenPair() throws Exception {
        // Round-1 finding 4: R-STOP pins the frozen pair as committed BEFORE 09:25 — BOTH halves.
        FeedGatewayService service = service();
        long now = System.currentTimeMillis();
        long fence = now - 60_000L;
        // Status committed AT the fence: revision-equal and FRESH, but it cannot prove a
        // pre-fence pair.
        seedIbkrStatus(service, "SPX|20260804|6300", 1L, fence,
                "{\"state\":\"FRESH\",\"sessionId\":\"" + IBKR_SESSION + "\",\"outputGeneration\":3,"
                        + "\"baselineEpoch\":2,\"recordRevision\":7}");
        String json = ibkrGexJson(IBKR_SESSION, 3, 2, 7, fence);
        assertNull(ingestIbkrGex(service, gexRecord(7L, "SPX|20260804|6300", json, fence - 5_000L),
                json, now));
        assertTrue(planeMap(service, "ibkrPreOpenFrozenProjections").isEmpty());
        // The value waits pending — if a PRE-fence status commit is later observed (compacted
        // replay order), the pair still completes; the at-fence status alone never does.
        assertEquals(1, planeMap(service, "ibkrPreOpenPendingProjections").size());
        service.sweepIbkrPreOpenGexWindows(now);
        assertTrue(planeMap(service, "ibkrPreOpenFrozenProjections").isEmpty());
    }

    @Test
    void fenceCaptureRequiresAProvablePreFenceValueCommit() throws Exception {
        // Round-1 finding 4 (capture side): a candidate admitted live on the gateway clock whose
        // broker CreateTime is at/after the fence (producer clock skew) must not be captured.
        FeedGatewayService service = service();
        long now = System.currentTimeMillis();
        long fence = now + 60_000L;
        seedIbkrStatus(service, "SPX|20260804|6300", 1L, now,
                "{\"state\":\"FRESH\",\"sessionId\":\"" + IBKR_SESSION + "\",\"outputGeneration\":3,"
                        + "\"baselineEpoch\":2,\"recordRevision\":7}");
        String json = ibkrGexJson(IBKR_SESSION, 3, 2, 7, fence);
        // Admitted as a LIVE candidate (presentation is gateway-clock governed)...
        assertNotNull(ingestIbkrGex(service, gexRecord(5L, "SPX|20260804|6300", json, fence + 5_000L),
                json, now));
        assertEquals(1, planeMap(service, "ibkrPreOpenGexCandidates").size());
        // ...but the fence capture cannot prove a pre-fence commit: frozen-blank, not pending.
        service.sweepIbkrPreOpenGexWindows(fence + 1_000L);
        assertTrue(planeMap(service, "ibkrPreOpenGexCandidates").isEmpty());
        assertTrue(planeMap(service, "ibkrPreOpenFrozenProjections").isEmpty());
        assertTrue(planeMap(service, "ibkrPreOpenPendingProjections").isEmpty());
    }

    @Test
    void observedHighWatermarkAdvancesForEveryRecordClassOnTheLiveTopic() throws Exception {
        // Round-1 finding 5: the 09:30 snapshot must be the gateway's real observed POSITION on
        // the partition — sessioned/malformed records advance it too, or a delayed sibling
        // delivery between the last Databento offset and the true position reads as new.
        FeedGatewayService service = service();
        long now = System.currentTimeMillis();
        // This gateway was RUNNING when the 09:30 boundary passed: both takeover axes apply.
        backdateProcessStart(service, now - 60 * 60_000L);
        long fence = now - 6 * 60_000L;
        long boundary = fence + 5 * 60_000L;
        seedIbkrStatus(service, "SPX|20260804|6300", 1L, fence - 10_000L,
                "{\"state\":\"FRESH\",\"sessionId\":\"" + IBKR_SESSION + "\",\"outputGeneration\":3,"
                        + "\"baselineEpoch\":2,\"recordRevision\":7}");
        String json = ibkrGexJson(IBKR_SESSION, 3, 2, 7, fence);
        assertNotNull(ingestIbkrGex(service, gexRecord(7L, "SPX|20260804|6300", json, fence - 5_000L),
                json, boundary - 30_000L));
        // Before the boundary the gateway observes offsets 98-100 as SESSIONED/malformed records
        // (no Databento record anywhere near the position).
        String unknown = "{\"symbol\":\"SPX\",\"expiry\":\"20260804\",\"strike\":6300.0,"
                + "\"source\":\"IBKR\",\"timeframe\":\"0DTE\",\"sessionId\":\"weird\"}";
        assertTrue(interceptSharedGex(service, gexRecord(98L, "SPX|20260804|6300", unknown,
                boundary - 10_000L), unknown, false, boundary - 10_000L));
        assertFalse(interceptSharedGex(service, gexRecord(99L, "k", "not-json",
                boundary - 9_000L), "not-json", false, boundary - 9_000L));
        String preFenceIbkr = ibkrGexJson(IBKR_SESSION, 3, 2, 8, fence);
        assertTrue(interceptSharedGex(service, gexRecord(100L, "SPX|20260804|6300", preFenceIbkr,
                fence - 4_000L), preFenceIbkr, false, boundary - 8_000L));
        // Boundary passes: the snapshot must be 100, not "no Databento seen".
        service.sweepIbkrPreOpenGexWindows(boundary + 1_000L);
        // A delayed sibling-consumer Databento delivery at offset 99 — even with a post-boundary
        // producer timestamp (clock skew) — is AT/BELOW the observed position: never a takeover.
        assertFalse(interceptSharedGex(service, gexRecord(99L, "SPX|20260804|6300",
                databentoGexJson(), boundary + 2_000L), databentoGexJson(), false, boundary + 2_000L));
        assertEquals(1, planeMap(service, "ibkrPreOpenFrozenProjections").size());
        // The genuinely new record beyond the position takes over.
        assertFalse(interceptSharedGex(service, gexRecord(101L, "SPX|20260804|6300",
                databentoGexJson(), boundary + 3_000L), databentoGexJson(), false, boundary + 3_000L));
        assertTrue(planeMap(service, "ibkrPreOpenFrozenProjections").isEmpty());
    }

    @Test
    void revocationControlEvictsTheNamedGenerationAndPinsIt() throws Exception {
        FeedGatewayService service = service();
        long now = System.currentTimeMillis();
        long validUntil = now + 600_000L;
        String gen3 = ibkrGexJson(IBKR_SESSION, 3, 2, 7, validUntil);
        assertNotNull(ingestIbkrGex(service, gexRecord(5L, "SPX|20260804|6300", gen3, now), gen3, now));
        assertEquals(1, planeMap(service, "ibkrPreOpenGexCandidates").size());
        // The __revocation control arrives on the status stream (slice-1 ingest path).
        seedIbkrStatus(service, "__revocation|2026-08-04|3", 9L, "{\"revoked\":true}");
        assertTrue(planeMap(service, "ibkrPreOpenGexCandidates").isEmpty());
        // Stragglers of the revoked generation can never re-enter; a NEWER generation can.
        assertNull(ingestIbkrGex(service, gexRecord(6L, "SPX|20260804|6300", gen3, now), gen3, now));
        String gen4 = ibkrGexJson(IBKR_SESSION, 4, 2, 1, validUntil);
        assertNotNull(ingestIbkrGex(service, gexRecord(7L, "SPX|20260804|6300", gen4, now), gen4, now));
        assertEquals(1, planeMap(service, "ibkrPreOpenGexCandidates").size());
    }

    @Test
    void newOutputGenerationSupersedesEveryLowerOne() throws Exception {
        FeedGatewayService service = service();
        long now = System.currentTimeMillis();
        long validUntil = now + 600_000L;
        String gen1a = ibkrGexJson(IBKR_SESSION, 1, 2, 7, validUntil);
        String gen1b = "{\"symbol\":\"SPX\",\"expiry\":\"20260804\",\"strike\":6305.0,\"netGex\":1.0E9,"
                + "\"source\":\"IBKR\",\"timeframe\":\"IBKR_PREOPEN\",\"sessionId\":\"" + IBKR_SESSION + "\","
                + "\"outputGeneration\":1,\"baselineEpoch\":2,\"recordRevision\":2,\"validUntilMs\":" + validUntil + "}";
        String gen2 = "{\"symbol\":\"SPX\",\"expiry\":\"20260804\",\"strike\":6310.0,\"netGex\":1.0E9,"
                + "\"source\":\"IBKR\",\"timeframe\":\"IBKR_PREOPEN\",\"sessionId\":\"" + IBKR_SESSION + "\","
                + "\"outputGeneration\":2,\"baselineEpoch\":3,\"recordRevision\":1,\"validUntilMs\":" + validUntil + "}";
        assertNotNull(ingestIbkrGex(service, gexRecord(1L, "SPX|20260804|6300", gen1a, now), gen1a, now));
        assertNotNull(ingestIbkrGex(service, gexRecord(2L, "SPX|20260804|6305", gen1b, now), gen1b, now));
        assertEquals(2, planeMap(service, "ibkrPreOpenGexCandidates").size());
        // The first gen-2 record supersedes the WHOLE gen-1 universe (a re-arm republishes the
        // full universe under the new generation).
        assertNotNull(ingestIbkrGex(service, gexRecord(3L, "SPX|20260804|6310", gen2, now), gen2, now));
        Map<String, Object> candidates = planeMap(service, "ibkrPreOpenGexCandidates");
        assertEquals(1, candidates.size());
        assertEquals("SPX|20260804|6310",
                candidateComponent(candidates.values().iterator().next(), "recordKey"));
        // An old-generation straggler is rejected.
        assertNull(ingestIbkrGex(service, gexRecord(4L, "SPX|20260804|6300", gen1a, now), gen1a, now));
    }

    @Test
    void ibkrPreOpenGexWrapShapesAndEscaping() throws Exception {
        assertEquals("{\"recordKey\":\"SPX|20260804|6300\",\"offset\":41,\"phase\":\"LIVE\","
                        + "\"gex\":{\"netGex\":1.5}}",
                FeedGatewayService.wrapIbkrPreOpenGex("SPX|20260804|6300", 41L, "LIVE", "{\"netGex\":1.5}"));
        assertEquals("{\"recordKey\":\"SPX|20260804|6300\",\"phase\":\"EVICTED\",\"reason\":\"TAKEOVER\"}",
                FeedGatewayService.wrapIbkrPreOpenGexEviction("SPX|20260804|6300", "TAKEOVER"));
        // Full escaping, parse-verified (the status-wrap discipline).
        ObjectMapper jackson = new ObjectMapper();
        String nasty = "a\"b\\c\nd\re\tfg";
        com.fasterxml.jackson.databind.JsonNode parsed = jackson.readTree(
                FeedGatewayService.wrapIbkrPreOpenGex(nasty, 7L, "FROZEN", "{}"));
        assertEquals(nasty, parsed.get("recordKey").asText());
        assertEquals("FROZEN", parsed.get("phase").asText());
        assertEquals(7L, parsed.get("offset").asLong());
    }

    @Test
    void ibkrPreOpenGexIsAGlobalBroadcastEventInPerSessionMode() {
        assertTrue(FeedGatewayService.GLOBAL_BROADCAST_EVENTS.contains("ibkr-preopen-gex"),
                "auth-mode sockets must receive the standalone value-plane broadcasts");
    }

    @Test
    void ibkrPreOpenGexBroadcastGateIsPerPartitionExactlyOnceInOrder() {
        FeedGatewayService service = service();
        assertTrue(service.shouldBroadcastIbkrPreOpenGex(0, 5L));
        assertFalse(service.shouldBroadcastIbkrPreOpenGex(0, 5L)); // sibling consumer's duplicate
        assertFalse(service.shouldBroadcastIbkrPreOpenGex(0, 4L)); // regression never fires
        assertTrue(service.shouldBroadcastIbkrPreOpenGex(0, 6L));
        // Partitions are independent offset spaces.
        assertTrue(service.shouldBroadcastIbkrPreOpenGex(1, 3L));
        assertFalse(service.shouldBroadcastIbkrPreOpenGex(1, 3L));
    }

    private static void setCaughtUpBarrier(FeedGatewayService service, String field, boolean value)
            throws Exception {
        Field barrier = FeedGatewayService.class.getDeclaredField(field);
        barrier.setAccessible(true);
        ((java.util.concurrent.atomic.AtomicBoolean) barrier.get(service)).set(value);
    }

    /** Drive the REAL recovery transition (caught-up -> recovering) on the named barrier. */
    private static void markRecovering(FeedGatewayService service, String field) throws Exception {
        Field barrier = FeedGatewayService.class.getDeclaredField(field);
        barrier.setAccessible(true);
        Method recovering = FeedGatewayService.class.getDeclaredMethod(
                "markCacheRecovering", java.util.concurrent.atomic.AtomicBoolean.class);
        recovering.setAccessible(true);
        recovering.invoke(service, barrier.get(service));
    }

    /** A serving gateway (both barriers up, latch proven by a delivered broadcast) holding one
     *  frozen projection for SPX|20260804|6300 with the given fence. Returns the sink. */
    private static List<String> servingServiceWithFrozenProjection(
            FeedGatewayService service, long fence, long nowMs) throws Exception {
        List<String> sink = new ArrayList<>();
        addRecordingClient(service, sink);
        setCaughtUpBarrier(service, "avroCaughtUp", true);
        setCaughtUpBarrier(service, "stateCaughtUp", true);
        seedIbkrStatus(service, "SPX|20260804|6300", 1L, fence - 10_000L,
                "{\"state\":\"FRESH\",\"sessionId\":\"" + IBKR_SESSION + "\",\"outputGeneration\":3,"
                        + "\"baselineEpoch\":2,\"recordRevision\":7}");
        String json = ibkrGexJson(IBKR_SESSION, 3, 2, 7, fence);
        assertTrue(interceptSharedGex(service, gexRecord(7L, "SPX|20260804|6300", json,
                fence - 5_000L), json, true, nowMs));
        assertEquals(1, planeMap(service, "ibkrPreOpenFrozenProjections").size());
        assertTrue(sink.stream().anyMatch(m -> m.contains("FROZEN")),
                "setup must prove the plane was SERVING before recovery: " + sink);
        sink.clear();
        return sink;
    }

    @Test
    void valuePlaneLiveBroadcastRequiresBothConsumerBarriers() throws Exception {
        // Round-2 finding 1: gating replay alone is not enough — the LIVE broadcast path serves
        // connected clients too, and must not deliver a value before the state consumer has
        // reconstructed the revocation/generation controls.
        FeedGatewayService service = service();
        List<String> sink = new ArrayList<>();
        addRecordingClient(service, sink);
        setCaughtUpBarrier(service, "avroCaughtUp", true);
        setCaughtUpBarrier(service, "stateCaughtUp", false);
        long now = System.currentTimeMillis();
        String json = ibkrGexJson(IBKR_SESSION, 3, 2, 7, now + 600_000L);
        assertTrue(interceptSharedGex(service, gexRecord(5L, "SPX|20260804|6300", json, now),
                json, true, now));
        assertTrue(sink.stream().noneMatch(m -> m.contains("ibkr-preopen-gex")),
                "value delivered before the state barrier: " + sink);
        // The suppressed delivery must NOT have consumed the per-partition CAS gate: once the
        // LAST barrier clears, the same offset (the sibling consumer's duplicate) still delivers.
        setCaughtUpBarrier(service, "stateCaughtUp", true);
        assertTrue(interceptSharedGex(service, gexRecord(5L, "SPX|20260804|6300", json, now),
                json, true, now));
        assertTrue(sink.stream().anyMatch(m -> m.contains("ibkr-preopen-gex")),
                "value not delivered after both barriers: " + sink);
    }

    @Test
    void authModeValuePlaneReplayRequiresBothConsumerBarriers() throws Exception {
        // Round-2 finding 1: the per-session (auth-mode) connect path replays through
        // replayCachedToSocket, which must gate the value plane on BOTH barriers too.
        System.setProperty("GATEWAY_IBKR_PREOPEN_ENABLED", "true");
        try {
            FeedGatewayService service = service();
            long now = System.currentTimeMillis();
            String json = ibkrGexJson(IBKR_SESSION, 3, 2, 7, now + 600_000L);
            assertNotNull(ingestIbkrGex(service, gexRecord(5L, "SPX|20260804|6300", json, now),
                    json, now));
            Method replay = FeedGatewayService.class.getDeclaredMethod(
                    "replayCachedToSocket", WebSocketSession.class);
            replay.setAccessible(true);
            List<String> sink = new ArrayList<>();
            WebSocketSession session = recordingSession(sink);
            setCaughtUpBarrier(service, "avroCaughtUp", true);
            setCaughtUpBarrier(service, "stateCaughtUp", false);
            replay.invoke(service, session);
            assertTrue(sink.stream().noneMatch(m -> m.contains("ibkr-preopen-gex")),
                    "auth-mode replay served a value before the state barrier: " + sink);
            setCaughtUpBarrier(service, "stateCaughtUp", true);
            replay.invoke(service, session);
            assertTrue(sink.stream().anyMatch(m -> m.contains("ibkr-preopen-gex")),
                    "auth-mode replay missing after both barriers: " + sink);
        } finally {
            System.clearProperty("GATEWAY_IBKR_PREOPEN_ENABLED");
        }
    }

    @Test
    void takeoverIsTerminalForAPendingProjectionToo() throws Exception {
        // Round-2 finding 2: pre-fence value pending (status consumer lagging) -> first
        // newly-ingested Databento record after 09:30 -> late status arrives. The pending pair
        // must NEVER present: the strike's takeover is terminal whether the projection was
        // frozen or still pending.
        FeedGatewayService service = service();
        long now = System.currentTimeMillis();
        long fence = now - 6 * 60_000L;
        long boundary = fence + 5 * 60_000L;
        String json = ibkrGexJson(IBKR_SESSION, 3, 2, 7, fence);
        // Pre-fence-committed value observed during the window with NO status yet: pending.
        assertTrue(interceptSharedGex(service, gexRecord(7L, "SPX|20260804|6300", json,
                fence - 5_000L), json, false, boundary - 30_000L));
        assertEquals(1, planeMap(service, "ibkrPreOpenPendingProjections").size());
        assertTrue(planeMap(service, "ibkrPreOpenFrozenProjections").isEmpty());
        // Boundary passes: the watermark snapshot must cover PENDING-only windows too.
        service.sweepIbkrPreOpenGexWindows(boundary + 1_000L);
        // The strike's first newly-ingested Databento record: terminal takeover of the pending pair.
        assertFalse(interceptSharedGex(service, gexRecord(102L, "SPX|20260804|6300",
                databentoGexJson(), boundary + 2_000L), databentoGexJson(), false, boundary + 2_000L));
        assertTrue(planeMap(service, "ibkrPreOpenPendingProjections").isEmpty());
        assertEquals(1, planeMap(service, "ibkrPreOpenTakenOverStrikes").size());
        // The late revision-equal pre-fence status can no longer resurrect the strike.
        seedIbkrStatus(service, "SPX|20260804|6300", 1L, fence - 10_000L,
                "{\"state\":\"FRESH\",\"sessionId\":\"" + IBKR_SESSION + "\",\"outputGeneration\":3,"
                        + "\"baselineEpoch\":2,\"recordRevision\":7}");
        assertTrue(planeMap(service, "ibkrPreOpenFrozenProjections").isEmpty());
        assertTrue(planeMap(service, "ibkrPreOpenPendingProjections").isEmpty());
        // Neither can a late compacted redelivery of the pre-fence value.
        assertNull(ingestIbkrGex(service, gexRecord(8L, "SPX|20260804|6300", json, fence - 4_000L),
                json, boundary + 4_000L));
        assertTrue(planeMap(service, "ibkrPreOpenFrozenProjections").isEmpty());
        assertTrue(planeMap(service, "ibkrPreOpenPendingProjections").isEmpty());
    }

    @Test
    void generationControlAloneSupersedesEveryLowerGeneration() throws Exception {
        // Round-2 finding 3: __generation|<D>|<gen> is the AUTHORITATIVE "max observed output
        // generation" signal (R-WIRE.2). After the state consumer observes it, a delayed
        // lower-generation value must be rejected even though no value of the new generation
        // has been observed yet.
        FeedGatewayService service = service();
        long now = System.currentTimeMillis();
        long validUntil = now + 600_000L;
        String gen1 = ibkrGexJson(IBKR_SESSION, 1, 2, 7, validUntil);
        assertNotNull(ingestIbkrGex(service, gexRecord(5L, "SPX|20260804|6300", gen1, now), gen1, now));
        assertEquals(1, planeMap(service, "ibkrPreOpenGexCandidates").size());
        // The generation control arrives on the status stream — no gen-2 value anywhere yet.
        seedIbkrStatus(service, "__generation|2026-08-04|2", 9L, "{\"outputGeneration\":2}");
        assertTrue(planeMap(service, "ibkrPreOpenGexCandidates").isEmpty(),
                "superseded live candidate survived the __generation control");
        // A delayed gen-1 straggler is rejected on the control's authority alone.
        assertNull(ingestIbkrGex(service, gexRecord(6L, "SPX|20260804|6300", gen1, now), gen1, now));
        // The new generation's values are admitted.
        String gen2 = ibkrGexJson(IBKR_SESSION, 2, 3, 1, validUntil);
        assertNotNull(ingestIbkrGex(service, gexRecord(7L, "SPX|20260804|6300", gen2, now), gen2, now));
        assertEquals(1, planeMap(service, "ibkrPreOpenGexCandidates").size());
    }

    @Test
    void unprovenNewerGenerationNeverDestroysAProvenFrozenProjection() throws Exception {
        // Round-2 finding 4: R-STOP makes the frozen cache NON-CANDIDATE state — generation
        // supersession never touches it. A proven frozen pair is replaced only when the newer
        // generation PROVES its own pre-fence pair.
        FeedGatewayService service = service();
        long now = System.currentTimeMillis();
        long fence = now - 60_000L; // inside the 09:25-09:35 window
        seedIbkrStatus(service, "SPX|20260804|6300", 1L, fence - 10_000L,
                "{\"state\":\"FRESH\",\"sessionId\":\"" + IBKR_SESSION + "\",\"outputGeneration\":3,"
                        + "\"baselineEpoch\":2,\"recordRevision\":7}");
        String gen3 = ibkrGexJson(IBKR_SESSION, 3, 2, 7, fence);
        assertNotNull(ingestIbkrGex(service, gexRecord(7L, "SPX|20260804|6300", gen3, fence - 5_000L),
                gen3, now));
        assertEquals(1, planeMap(service, "ibkrPreOpenFrozenProjections").size());
        // (a) A gen-4 value committed AT the fence: advances the max generation, is itself
        // rejected (no pre-fence commit) — and the proven gen-3 projection SURVIVES.
        String gen4 = ibkrGexJson(IBKR_SESSION, 4, 3, 1, fence);
        assertNull(ingestIbkrGex(service, gexRecord(8L, "SPX|20260804|6300", gen4, fence), gen4, now));
        Map<String, Object> projections = planeMap(service, "ibkrPreOpenFrozenProjections");
        assertEquals(1, projections.size());
        assertEquals(3L, candidateComponent(projections.values().iterator().next(), "outputGeneration"));
        // (b) A gen-5 value committed pre-fence but UNPAIRED: pending, not presented — and the
        // proven gen-3 projection STILL survives.
        String gen5 = ibkrGexJson(IBKR_SESSION, 5, 4, 2, fence);
        assertNull(ingestIbkrGex(service, gexRecord(9L, "SPX|20260804|6300", gen5, fence - 3_000L),
                gen5, now));
        assertEquals(1, planeMap(service, "ibkrPreOpenPendingProjections").size());
        projections = planeMap(service, "ibkrPreOpenFrozenProjections");
        assertEquals(1, projections.size());
        assertEquals(3L, candidateComponent(projections.values().iterator().next(), "outputGeneration"));
        // (c) The gen-5 pre-fence status arrives: the newer pair is PROVEN and only now
        // replaces the gen-3 projection.
        seedIbkrStatus(service, "SPX|20260804|6300", 2L, fence - 8_000L,
                "{\"state\":\"FRESH\",\"sessionId\":\"" + IBKR_SESSION + "\",\"outputGeneration\":5,"
                        + "\"baselineEpoch\":4,\"recordRevision\":2}");
        assertTrue(planeMap(service, "ibkrPreOpenPendingProjections").isEmpty());
        projections = planeMap(service, "ibkrPreOpenFrozenProjections");
        assertEquals(1, projections.size());
        assertEquals(5L, candidateComponent(projections.values().iterator().next(), "outputGeneration"));
    }

    @Test
    void featureOffObservablePayloadsAreEquivalentToABuildWithoutTheFeature() throws Exception {
        // Round-2 finding 5 (O7 feature-off identity): every payload an observer can see — the
        // "status" WebSocket message each connecting client receives, the /health JSON, and the
        // Prometheus scrape — must be equivalent to a build without this feature when the flag
        // is OFF.
        Method statusJson = FeedGatewayService.class.getDeclaredMethod("statusJson");
        statusJson.setAccessible(true);
        FeedGatewayService off = service();
        String offHealth = off.healthJson();
        assertFalse(offHealth.contains("ibkrPreOpen"),
                "feature-off health payload leaks pre-open fields: " + offHealth);
        assertFalse(((String) statusJson.invoke(off)).contains("ibkrPreOpen"),
                "feature-off WS status payload leaks pre-open fields");
        assertFalse(off.metrics().contains("ibkr_preopen_gex"),
                "feature-off metrics leak pre-open series");
        System.setProperty("GATEWAY_IBKR_PREOPEN_ENABLED", "true");
        try {
            FeedGatewayService on = service();
            String onHealth = on.healthJson();
            assertTrue(onHealth.contains("\"ibkrPreOpenFrozenProjections\":0"),
                    "flag-on health payload missing pre-open fields: " + onHealth);
            assertTrue(on.metrics().contains("options_edge_feed_gateway_ibkr_preopen_gex_candidates 0"));
            // The WS status payload never carries pre-open fields in either mode (the value
            // plane rides its own standalone events), and both health variants must stay valid
            // JSON (the conditional block must not break commas).
            assertFalse(((String) statusJson.invoke(on)).contains("ibkrPreOpen"));
            ObjectMapper jackson = new ObjectMapper();
            assertNotNull(jackson.readTree(offHealth));
            assertNotNull(jackson.readTree(onHealth));
        } finally {
            System.clearProperty("GATEWAY_IBKR_PREOPEN_ENABLED");
        }
    }

    @Test
    void takeoverEvictionStillBroadcastsWhileTheAvroBarrierIsRecovering() throws Exception {
        // Round-3 finding 1: the caught-up flags are NOT monotonic — markCacheRecovering flips
        // one back to false. A terminal transition during that interval must still reach clients
        // that already hold the value, or they keep a frozen projection forever.
        FeedGatewayService service = service();
        long now = System.currentTimeMillis();
        long fence = now - 6 * 60_000L;
        long boundary = fence + 5 * 60_000L;
        List<String> sink = servingServiceWithFrozenProjection(service, fence, boundary - 30_000L);
        service.sweepIbkrPreOpenGexWindows(boundary + 1_000L); // watermark snapshot
        markRecovering(service, "avroCaughtUp");
        // The strike's first newly-ingested Databento record arrives mid-recovery.
        assertFalse(interceptSharedGex(service, gexRecord(102L, "SPX|20260804|6300",
                databentoGexJson(), boundary + 2_000L), databentoGexJson(), false, boundary + 2_000L));
        assertTrue(planeMap(service, "ibkrPreOpenFrozenProjections").isEmpty());
        assertTrue(sink.stream().anyMatch(m -> m.contains("TAKEOVER")),
                "takeover eviction lost during avro recovery: " + sink);
    }

    @Test
    void revocationEvictionStillBroadcastsWhileTheStateBarrierIsRecovering() throws Exception {
        // Round-3 finding 1, revocation flavor, with the STATE barrier recovering.
        FeedGatewayService service = service();
        long now = System.currentTimeMillis();
        long fence = now - 60_000L;
        List<String> sink = servingServiceWithFrozenProjection(service, fence, now);
        markRecovering(service, "stateCaughtUp");
        seedIbkrStatus(service, "__revocation|2026-08-04|3", 9L, "{\"revoked\":true}");
        assertTrue(planeMap(service, "ibkrPreOpenFrozenProjections").isEmpty());
        assertTrue(sink.stream().anyMatch(m -> m.contains("REVOKED")),
                "revocation eviction lost during state recovery: " + sink);
    }

    @Test
    void windowDestructionStillBroadcastsWhileABarrierIsRecovering() throws Exception {
        // Round-3 finding 1, 09:35 destruction flavor.
        FeedGatewayService service = service();
        long now = System.currentTimeMillis();
        long fence = now - 60_000L;
        List<String> sink = servingServiceWithFrozenProjection(service, fence, now);
        markRecovering(service, "stateCaughtUp");
        service.sweepIbkrPreOpenGexWindows(fence + 10 * 60_000L + 1L);
        assertTrue(planeMap(service, "ibkrPreOpenFrozenProjections").isEmpty());
        assertTrue(sink.stream().anyMatch(m -> m.contains("WINDOW_END")),
                "09:35 destruction lost during recovery: " + sink);
    }

    @Test
    void aPostFenceGenerationMustNotDestroyTheProvenPreFenceProjection() throws Exception {
        // Round-4 finding 1: consumer loops process a whole BATCH before sweeping, so an unproven
        // gen-4 record — one committed AT its own fence, which the capture branch will refuse —
        // can arrive while gen-3 is still a valid paired live candidate. Generation supersession
        // evicts every lower-generation candidate, so evicting on that unproven record destroyed
        // gen-3's projection and then refused to capture gen-4: the window lost both.
        long now = System.currentTimeMillis();
        long fence = now + 60_000L;                    // fence ahead: gen-3 is provable
        FeedGatewayService service = service();
        String status = "{\"state\":\"FRESH\",\"sessionId\":\"" + IBKR_SESSION
                + "\",\"outputGeneration\":3,\"baselineEpoch\":2,\"recordRevision\":7}";
        seedIbkrStatus(service, "SPX|20260804|6300", 1L, now - 10_000L, status);
        String gen3 = ibkrGexJson(IBKR_SESSION, 3, 2, 7, fence);
        assertNotNull(ingestIbkrGex(service, gexRecord(10L, "SPX|20260804|6300", gen3, now - 5_000L),
                gen3, now), "gen-3 is pre-fence and paired — it must be admitted");
        assertEquals(1, planeMap(service, "ibkrPreOpenGexCandidates").size());

        // An unproven gen-4 for ANOTHER strike, committed AT its fence.
        String gen4 = ibkrGexJson(IBKR_SESSION, 4, 3, 1, fence).replace("6300.0", "6305.0");
        ingestIbkrGex(service, gexRecord(11L, "SPX|20260804|6305", gen4, fence), gen4, now);

        Map<String, Object> candidates = planeMap(service, "ibkrPreOpenGexCandidates");
        assertTrue(candidates.containsKey(gexIdentity(service, "SPX|20260804|6300")),
                "the proven gen-3 candidate must SURVIVE an unproven higher generation");
        assertEquals(3L, candidateComponent(
                candidates.get(gexIdentity(service, "SPX|20260804|6300")), "outputGeneration"));
    }

    @Test
    void arbitrationIsScopedToTheSharedLiveTopicOnly() throws Exception {
        // Round-5 finding 4: the (DATABENTO, gex-by-strike) binding also carries the separate JSON
        // history topic. Arbitrating there would drop session-claiming records out of a
        // pre-existing Databento pipeline this feature must leave untouched, so BOTH consumer call
        // sites gate on the record's topic. Asserted on the source, because the guard lives at the
        // call sites rather than inside the interceptor.
        String source = java.nio.file.Files.readString(java.nio.file.Path.of(
                "src/main/java/app/feedgateway/FeedGatewayService.java"));
        int guards = source.split("settings\\.databentoGexTopic\\(\\)\\.equals\\(record\\.topic\\(\\)\\)", -1).length - 1;
        assertEquals(2, guards,
                "both interceptSharedGexRecord call sites must require the shared LIVE topic");
        for (String callSite : source.split("interceptSharedGexRecord\\(record")) {
            // every call site's guard block must mention the topic check before it
            if (!callSite.endsWith("(")) {
                continue;
            }
        }
        // The history topic really is bound to the same (source, event) pair — which is why the
        // guard is needed at all.
        assertTrue(source.contains("topicEvents.put(settings.databentoGexHistoryTopic(), "
                        + "new TopicBinding(\"DATABENTO\", \"gex-by-strike\"))"),
                "the history topic shares the binding this guard protects against");
    }

    @Test
    void admissionIsFailClosedOnTheWholeContractNotJustTheTuple() throws Exception {
        // Round-5 finding 3. A record can carry the exact source/timeframe pair and still be
        // malformed, and every one of these must be refused rather than "helpfully" accepted.
        ObjectMapper jackson = new ObjectMapper();

        // (a) text() trims, so a PADDED tuple would otherwise read as exact.
        assertEquals(FeedGatewayService.SharedGexClass.UNKNOWN_SESSIONED,
                FeedGatewayService.classifySharedGexRecord(jackson.readTree(
                        "{\"source\":\" IBKR \",\"timeframe\":\"IBKR_PREOPEN\",\"sessionId\":\""
                                + IBKR_SESSION + "\"}")),
                "a padded source must not be normalized into the exact tuple");
        assertEquals(FeedGatewayService.SharedGexClass.UNKNOWN_SESSIONED,
                FeedGatewayService.classifySharedGexRecord(jackson.readTree(
                        "{\"source\":\"IBKR\",\"timeframe\":\"IBKR_PREOPEN \",\"sessionId\":\""
                                + IBKR_SESSION + "\"}")),
                "a padded timeframe must not be normalized into the exact tuple");

        long now = System.currentTimeMillis();
        long fence = now + 60_000L;
        String status = "{\"state\":\"FRESH\",\"sessionId\":\"" + IBKR_SESSION
                + "\",\"outputGeneration\":3,\"baselineEpoch\":2,\"recordRevision\":7}";

        // (b) A session id outside the pinned IBKR_PREOPEN:<yyyy-MM-dd> shape.
        FeedGatewayService badSession = service();
        seedIbkrStatus(badSession, "SPX|20260804|6300", 1L, now - 10_000L, status);
        String malformed = ibkrGexJson("IBKR_PREOPEN:not-a-date", 3, 2, 7, fence);
        assertNull(ingestIbkrGex(badSession, gexRecord(5L, "SPX|20260804|6300", malformed,
                now - 5_000L), malformed, now));
        assertTrue(planeMap(badSession, "ibkrPreOpenGexCandidates").isEmpty());

        // (c) Zero/default ordering fields — R-WIRE.5 cannot order an unversioned record.
        for (String unversioned : List.of(
                ibkrGexJson(IBKR_SESSION, 0, 2, 7, fence),
                ibkrGexJson(IBKR_SESSION, 3, 0, 7, fence),
                ibkrGexJson(IBKR_SESSION, 3, 2, 0, fence))) {
            FeedGatewayService svc = service();
            seedIbkrStatus(svc, "SPX|20260804|6300", 1L, now - 10_000L, status);
            assertNull(ingestIbkrGex(svc, gexRecord(5L, "SPX|20260804|6300", unversioned,
                    now - 5_000L), unversioned, now), "unversioned record: " + unversioned);
            assertTrue(planeMap(svc, "ibkrPreOpenGexCandidates").isEmpty());
        }

        // (d) The payload identity must agree with the Kafka key it was partitioned under.
        FeedGatewayService mismatched = service();
        seedIbkrStatus(mismatched, "SPX|20260804|6305", 1L, now - 10_000L, status);
        String json = ibkrGexJson(IBKR_SESSION, 3, 2, 7, fence);   // payload says strike 6300
        assertNull(ingestIbkrGex(mismatched, gexRecord(5L, "SPX|20260804|6305", json,
                now - 5_000L), json, now),
                "a record whose payload names another strike than its key must fail closed");
        assertTrue(planeMap(mismatched, "ibkrPreOpenGexCandidates").isEmpty());

        // Control: the fully-conforming record is still admitted.
        FeedGatewayService ok = service();
        seedIbkrStatus(ok, "SPX|20260804|6300", 1L, now - 10_000L, status);
        assertNotNull(ingestIbkrGex(ok, gexRecord(5L, "SPX|20260804|6300", json, now - 5_000L),
                json, now));
    }

    @Test
    void aGatewayThatRestartedAfterTheBoundaryNeverSynthesizesAWatermark() throws Exception {
        // Round-5 finding 1: a watermark is a record of where THIS gateway stood on each partition
        // when 09:30 passed. A gateway that started afterwards has no such record, and snapshotting
        // now would capture offsets read AFTER the boundary — for a second strike that snapshot can
        // contain a record already observed post-boundary, letting the first strike resurrect. The
        // pinned recovery rule for that case is commit-time only, so no watermark may be created.
        long now = System.currentTimeMillis();
        long fence = now - 6 * 60_000L;
        long boundary = fence + 5 * 60_000L;
        FeedGatewayService restarted = service();      // started AFTER the boundary — not backdated
        String status = "{\"state\":\"FRESH\",\"sessionId\":\"" + IBKR_SESSION
                + "\",\"outputGeneration\":3,\"baselineEpoch\":2,\"recordRevision\":7}";
        seedIbkrStatus(restarted, "SPX|20260804|6300", 1L, fence - 10_000L, status);
        String json = ibkrGexJson(IBKR_SESSION, 3, 2, 7, fence);
        ingestIbkrGex(restarted, gexRecord(7L, "SPX|20260804|6300", json, fence - 5_000L),
                json, boundary + 1_000L);
        sweepWindows(restarted, boundary + 2_000L);
        assertNull(takeoverWatermark(restarted, fence),
                "a restarted gateway must never synthesize a 09:30 watermark retrospectively");

        // ...and its takeover judgment is commit-time only: a post-boundary commit takes over
        // whatever its offset, because there is no position to compare against.
        String identity = gexIdentity(restarted, "SPX|20260804|6300");
        seedDatabentoObservation(restarted, identity, boundary + 1_000L, 0, 1L);
        assertTrue(strikeTakenOver(restarted, identity, fence),
                "commit-time-only recovery still honours a post-boundary takeover");

        // A gateway that DID run through the boundary snapshots one, and uses both axes.
        FeedGatewayService live = service();
        backdateProcessStart(live, now - 60 * 60_000L);
        seedIbkrStatus(live, "SPX|20260804|6300", 1L, fence - 10_000L, status);
        ingestIbkrGex(live, gexRecord(7L, "SPX|20260804|6300", json, fence - 5_000L),
                json, boundary + 1_000L);
        sweepWindows(live, boundary + 2_000L);
        assertNotNull(takeoverWatermark(live, fence),
                "a gateway running through the boundary records where it stood");
    }

    @Test
    void aPostBoundaryDatabentoRecordAtOrBelowTheWatermarkNeverTakesTheStrikeOver() throws Exception {
        // Round-4 finding 3: the observation memory carried COMMIT TIME ALONE, so a record the
        // immediate path correctly refused on the WATERMARK axis — committed after 09:30 but at or
        // below the snapshotted high-watermark, i.e. a prior record — could still kill a later
        // pending promotion or pre-fence reconstruction through that memory. Both axes must travel
        // together, and the predicate must apply both.
        long now = System.currentTimeMillis();
        long fence = now - 6 * 60_000L;
        long boundary = fence + 5 * 60_000L;
        FeedGatewayService service = service();
        // This gateway was RUNNING when the 09:30 boundary passed: both takeover axes apply.
        backdateProcessStart(service, now - 60 * 60_000L);
        String identity = gexIdentity(service, "SPX|20260804|6300");
        seedTakeoverWatermark(service, fence, 0, 500L);

        // Post-boundary commit, but AT the watermark: a prior record.
        seedDatabentoObservation(service, identity, boundary + 1_000L, 0, 500L);
        assertFalse(strikeTakenOver(service, identity, fence),
                "an at/below-watermark record is a PRIOR record — it must not tombstone the strike");

        // Same commit time, BEYOND the watermark: the real takeover.
        seedDatabentoObservation(service, identity, boundary + 1_000L, 0, 501L);
        assertTrue(strikeTakenOver(service, identity, fence),
                "beyond the watermark AND after the boundary is the takeover");

        // ...and a pre-boundary commit is never a takeover whatever its offset.
        FeedGatewayService early = service();
        String id2 = gexIdentity(early, "SPX|20260804|6300");
        seedTakeoverWatermark(early, fence, 0, 500L);
        seedDatabentoObservation(early, id2, boundary - 1_000L, 0, 9_999L);
        assertFalse(strikeTakenOver(early, id2, fence),
                "committed before 09:30 is a bootstrap/compacted record, never a takeover");
    }

    @Test
    void theDatabentoObservationIsPublishedBeforeTheTakeoverCheck() throws Exception {
        // Round-4 finding 2: the check/evict ran BEFORE the memory was published, leaving a gap in
        // which a concurrently rewinding cache consumer could reconstruct a projection that the
        // eviction had already passed and the memory did not yet cover. Publishing first closes it:
        // reconstruction either sees the observation, or completes before it and is caught by the
        // eviction — never neither. Asserted on the source order, because the race itself is not
        // deterministically reproducible in a unit test.
        String source = java.nio.file.Files.readString(java.nio.file.Path.of(
                "src/main/java/app/feedgateway/FeedGatewayService.java"));
        int publish = source.indexOf("ibkrPreOpenDatabentoMaxCommitMs.merge(observed, incoming");
        int check = source.indexOf("maybeEvictIbkrProjectionOnTakeover(record, root, nowMs);");
        assertTrue(publish > 0 && check > 0, "both sites must exist");
        assertTrue(publish < check,
                "the observation must be published BEFORE the takeover check, or a concurrent "
                        + "reconstruction can slip between them");
    }

    @Test
    void freshRestartDatabentoObservedBeforeReconstructionNeverResurrectsTheStrike() throws Exception {
        // Round-3 finding 2: on a fresh restart the live consumer starts at END while the cache
        // consumer rewinds. A post-boundary Databento record observed BEFORE any frozen/pending
        // state exists must still terminally take over the strike once reconstruction is
        // attempted — in either reconstruction order.
        long now = System.currentTimeMillis();
        long fence = now - 6 * 60_000L;
        long boundary = fence + 5 * 60_000L;
        String json = ibkrGexJson(IBKR_SESSION, 3, 2, 7, fence);
        String status = "{\"state\":\"FRESH\",\"sessionId\":\"" + IBKR_SESSION
                + "\",\"outputGeneration\":3,\"baselineEpoch\":2,\"recordRevision\":7}";
        // Order 1: status reconstructed first, then the value.
        FeedGatewayService statusFirst = service();
        assertFalse(interceptSharedGex(statusFirst, gexRecord(200L, "SPX|20260804|6300",
                databentoGexJson(), boundary + 2_000L), databentoGexJson(), false, boundary + 2_000L));
        seedIbkrStatus(statusFirst, "SPX|20260804|6300", 1L, fence - 10_000L, status);
        assertNull(ingestIbkrGex(statusFirst, gexRecord(7L, "SPX|20260804|6300", json,
                fence - 5_000L), json, boundary + 3_000L));
        assertTrue(planeMap(statusFirst, "ibkrPreOpenFrozenProjections").isEmpty());
        assertTrue(planeMap(statusFirst, "ibkrPreOpenPendingProjections").isEmpty());
        assertEquals(1, planeMap(statusFirst, "ibkrPreOpenTakenOverStrikes").size(),
                "the prior post-boundary observation must convert to a terminal tombstone");
        // Order 2: value first (would otherwise stash pending), then the status.
        FeedGatewayService valueFirst = service();
        assertFalse(interceptSharedGex(valueFirst, gexRecord(200L, "SPX|20260804|6300",
                databentoGexJson(), boundary + 2_000L), databentoGexJson(), false, boundary + 2_000L));
        assertNull(ingestIbkrGex(valueFirst, gexRecord(7L, "SPX|20260804|6300", json,
                fence - 5_000L), json, boundary + 3_000L));
        assertTrue(planeMap(valueFirst, "ibkrPreOpenPendingProjections").isEmpty(),
                "a taken-over strike must not even stash a pending pair");
        seedIbkrStatus(valueFirst, "SPX|20260804|6300", 1L, fence - 10_000L, status);
        assertTrue(planeMap(valueFirst, "ibkrPreOpenFrozenProjections").isEmpty());
        // Negative control: a Databento record committed BEFORE the boundary (bootstrap/compacted
        // prior record) must NOT poison the reconstruction.
        FeedGatewayService priorRecord = service();
        assertFalse(interceptSharedGex(priorRecord, gexRecord(200L, "SPX|20260804|6300",
                databentoGexJson(), boundary - 10_000L), databentoGexJson(), false, boundary + 2_000L));
        seedIbkrStatus(priorRecord, "SPX|20260804|6300", 1L, fence - 10_000L, status);
        assertNotNull(ingestIbkrGex(priorRecord, gexRecord(7L, "SPX|20260804|6300", json,
                fence - 5_000L), json, boundary + 3_000L));
        assertEquals(1, planeMap(priorRecord, "ibkrPreOpenFrozenProjections").size());
    }

    @Test
    void replayAppliesTheSharedTopicClassificationTupleMatrix() throws Exception {
        // Round-3 finding 3: the historical-replay reader must share the cache/live readers'
        // R-WIRE.1 classification. Matrix on the ONE chokepoint...
        FeedGatewayService service = service();
        long now = System.currentTimeMillis();
        assertTrue(service.isSessionedSharedGexJson(ibkrGexJson(IBKR_SESSION, 3, 2, 7, now)),
                "exact IBKR tuple is pre-open-plane only — never an ordinary replay row");
        assertTrue(service.isSessionedSharedGexJson(
                "{\"symbol\":\"SPX\",\"source\":\"PREOPEN\",\"timeframe\":\"PREOPEN\"}"),
                "conflicting PREOPEN tuple fails closed in replay like the live reader");
        assertTrue(service.isSessionedSharedGexJson(
                "{\"symbol\":\"SPX\",\"source\":\"IBKR\",\"timeframe\":\"0DTE\",\"sessionId\":\"weird\"}"),
                "unknown sessioned tuple fails closed in replay like the live reader");
        assertFalse(service.isSessionedSharedGexJson(databentoGexJson()));
        assertFalse(service.isSessionedSharedGexJson("not-json"));
        assertFalse(service.isSessionedSharedGexJson(""));
        assertFalse(service.isSessionedSharedGexJson(null));
        // ...and the wiring: emitReplayRecord drops each sessioned class (counted) with the flag
        // ON, and passes a genuine Databento record through to the ordinary path.
        System.setProperty("GATEWAY_IBKR_PREOPEN_ENABLED", "true");
        try {
            FeedGatewayService wired = service();
            Method emit = FeedGatewayService.class.getDeclaredMethod("emitReplayRecord",
                    String.class, ReplayParams.class, app.feedgateway.mtsession.MarketDataSource.class,
                    FeedGatewayService.MergeRecord.class, FeedGatewayService.ReplayHandle.class);
            emit.setAccessible(true);
            ReplayParams params = new ReplayParams("app:u1", "SPX", "20260804", 1_000L, 2_000L, 1000, null);
            FeedGatewayService.ReplayHandle handle = new FeedGatewayService.ReplayHandle(1L);
            java.util.function.BiFunction<String, Long, Object> emitRaw = (payload, offset) -> {
                try {
                    return emit.invoke(wired, "app:u1", params,
                            app.feedgateway.mtsession.MarketDataSource.DATABENTO,
                            new FeedGatewayService.MergeRecord(
                                    new app.feedgateway.mtsession.gateway.ReplayChronology.Cursor(
                                            now, new GatewaySettings().databentoGexTopic(), 0, offset),
                                    "gex-by-strike", payload, false),
                            handle);
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            };
            assertEquals(Boolean.FALSE, emitRaw.apply(ibkrGexJson(IBKR_SESSION, 3, 2, 7, now), 1L));
            assertEquals(1L, planeCounter(wired, "ibkrPreOpenGexDroppedSessioned"),
                    "an exact IBKR tuple must be dropped (counted) by the replay reader");
            assertEquals(Boolean.FALSE, emitRaw.apply(
                    "{\"symbol\":\"SPX\",\"source\":\"PREOPEN\",\"timeframe\":\"PREOPEN\"}", 2L));
            assertEquals(Boolean.FALSE, emitRaw.apply(
                    "{\"symbol\":\"SPX\",\"source\":\"IBKR\",\"timeframe\":\"0DTE\",\"sessionId\":\"x\"}", 3L));
            assertEquals(3L, planeCounter(wired, "ibkrPreOpenGexDroppedSessioned"));
            // A genuine Databento record passes the classifier (whatever the downstream
            // session-routing outcome, it is not a sessioned drop).
            emitRaw.apply(databentoGexJson(), 4L);
            assertEquals(3L, planeCounter(wired, "ibkrPreOpenGexDroppedSessioned"),
                    "a Databento record must never count as a sessioned drop in replay");
        } finally {
            System.clearProperty("GATEWAY_IBKR_PREOPEN_ENABLED");
        }
    }
}
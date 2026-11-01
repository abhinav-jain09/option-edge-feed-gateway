package app.feedgateway;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.OffsetAndTimestamp;
import org.apache.kafka.common.Node;
import org.apache.kafka.common.PartitionInfo;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.Test;

class MarketStructureRecoverySeekTest {
    private static final TopicPartition STRUCTURE = new TopicPartition("structure.ui", 0);
    private static final TopicPartition OTHER = new TopicPartition("other.ui", 0);
    private static final Map<String, FeedGatewayService.TopicBinding> BINDINGS = Map.of(
        STRUCTURE.topic(), new FeedGatewayService.TopicBinding("DATABENTO", "market-structure"),
        OTHER.topic(), new FeedGatewayService.TopicBinding("DATABENTO", "snapshot"));

    private static FeedGatewayService service() {
        GatewaySettings settings = spy(new GatewaySettings());
        doReturn(true).when(settings).marketStructureEnabled();
        doReturn(STRUCTURE.topic()).when(settings).marketStructureTopic();
        return new FeedGatewayService(settings, new ObjectMapper(), new HpsfGatewayViewMapper(), null);
    }

    /** A UiProjection whose frame carries a 101 KiB warning (bigger than the 64 KiB of the pre-W0 bound). */
    private static org.apache.avro.generic.GenericRecord ui(long asOfMs) {
        var r = TaFixtures.uiProjection(asOfMs);
        TaFixtures.put(r, "warning", "x".repeat(101 * 1024));
        TaFixtures.put(r, "sessionEnded", true);
        return r;
    }

    @Test
    @SuppressWarnings("unchecked")
    void coldAndRetryStartsOverrideEndForTheQuietStructureSnapshotOnly() {
        for (boolean retry : List.of(false, true)) {
            KafkaConsumer<String, Object> consumer = mock(KafkaConsumer.class);
            when(consumer.offsetsForTimes(anyMap())).thenAnswer(call -> {
                Map<TopicPartition, Long> cuts = call.getArgument(0);
                return Map.of(STRUCTURE, new OffsetAndTimestamp(41, cuts.get(STRUCTURE)));
            });
            when(consumer.endOffsets(anyCollection())).thenReturn(Map.of(STRUCTURE, 42L));
            long before = System.currentTimeMillis();
            service().liveBootstrapSeek(consumer, List.of(STRUCTURE, OTHER), BINDINGS, retry);
            long after = System.currentTimeMillis();
            verify(consumer, atLeastOnce()).seek(STRUCTURE, 41);
            verify(consumer, never()).seek(eq(OTHER), anyLong());
            var calls = mockingDetails(consumer).getInvocations().stream()
                .filter(c -> c.getMethod().getName().equals("offsetsForTimes")).toList();
            Map<TopicPartition, Long> lastCut = (Map<TopicPartition, Long>) calls.get(calls.size() - 1).getArgument(0);
            assertEquals(java.util.Set.of(STRUCTURE), lastCut.keySet());
            assertTrue(lastCut.get(STRUCTURE) >= before - Duration.ofDays(7).toMillis());
            assertTrue(lastCut.get(STRUCTURE) <= after - Duration.ofDays(7).toMillis());
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void aNewlyDiscoveredStructureTopicRecoversItsExistingSnapshot() {
        var s = service();
        KafkaConsumer<String, Object> consumer = mock(KafkaConsumer.class);
        when(consumer.offsetsForTimes(anyMap())).thenReturn(Map.of(STRUCTURE, new OffsetAndTimestamp(19, 1)));
        when(consumer.endOffsets(anyCollection())).thenReturn(Map.of(STRUCTURE, 20L));
        var refresh = s.refreshForTest(List.of(STRUCTURE, OTHER), List.of(STRUCTURE), List.of(OTHER));
        s.liveAdoptionSeek(consumer, refresh, BINDINGS);
        verify(consumer).seek(STRUCTURE, 19);
        verify(consumer, never()).seek(eq(OTHER), anyLong());
    }

    @Test
    @SuppressWarnings("unchecked")
    void anEmptyOrExpiredStructureWindowStaysAtEndAndDisabledBindingsDoNothing() {
        var s = service();
        KafkaConsumer<String, Object> consumer = mock(KafkaConsumer.class);
        when(consumer.offsetsForTimes(anyMap())).thenReturn(Map.of());
        when(consumer.endOffsets(anyCollection())).thenReturn(Map.of(STRUCTURE, 0L));
        s.seekMarketStructureSnapshot(consumer, List.of(STRUCTURE, OTHER), BINDINGS);
        verify(consumer).seekToEnd(List.of(STRUCTURE));
        verify(consumer, never()).seek(any(), anyLong());
        reset(consumer);
        s.seekMarketStructureSnapshot(consumer, List.of(OTHER), BINDINGS);
        verifyNoInteractions(consumer);
    }

    @Test
    @SuppressWarnings("unchecked")
    void coldAndRetryRecoveryPublishOnlyTheFinalSnapshotAndKeepTombstonesPrivate() throws Exception {
        for (boolean retry : List.of(false, true)) {
          for (boolean terminalTombstone : List.of(false, true)) {
            var s = service();
            // a record whose frame exceeds the pre-W0 64 KiB bound: the 101 KiB warning rides the frame (the ui bound is 128 KiB)
            var payloadRecord = ui(1791921600000L);
            var older = ui(1791921500000L);
            var live = ui(1791921600001L);
            String payload = TaFixtures.frame(payloadRecord);
            if (retry) s.retainMarketStructure(1791921600000L, payload);
            String held = s.marketStructureLatestForTest().get();
            s.runOutboundWritesInline();
            var socket = mock(org.springframework.web.socket.WebSocketSession.class);
            when(socket.getId()).thenReturn("structure-recovery");
            when(socket.isOpen()).thenReturn(true);
            s.addClient(socket);
            clearInvocations(socket);
            KafkaConsumer<String, Object> consumer = mock(KafkaConsumer.class);
            Node node = new Node(1, "b", 9092);
            when(consumer.listTopics(any(Duration.class))).thenReturn(Map.of(STRUCTURE.topic(),
                List.of(new PartitionInfo(STRUCTURE.topic(), 0, node, new Node[]{node}, new Node[]{node}))));
            when(consumer.offsetsForTimes(anyMap())).thenReturn(Map.of(STRUCTURE, new OffsetAndTimestamp(0, 1)));
            long capturedEnd = terminalTombstone ? 2L : 3L;
            when(consumer.endOffsets(anyCollection(), any(Duration.class))).thenReturn(Map.of(STRUCTURE, capturedEnd));
            var position = new java.util.concurrent.atomic.AtomicLong();
            doAnswer(call -> { position.set(capturedEnd); return null; }).when(consumer).seekToEnd(anyCollection());
            doAnswer(call -> { position.set(call.getArgument(1)); return null; }).when(consumer).seek(eq(STRUCTURE), anyLong());
            when(consumer.assignment()).thenReturn(java.util.Set.of(STRUCTURE));
            when(consumer.position(STRUCTURE)).thenAnswer(call -> position.get());
            var polls = new java.util.concurrent.atomic.AtomicInteger();
            when(consumer.poll(any(Duration.class))).thenAnswer(call -> {
                int poll = polls.incrementAndGet();
                if (poll <= capturedEnd) {
                    assertTrue(java.util.Objects.equals(held, s.marketStructureLatestForTest().get()),
                        "partial history and its tombstone must not change a connect hello");
                    verify(socket, never()).sendMessage(any());
                }
                if (poll == capturedEnd + 1) {
                    assertTrue(java.util.Objects.equals(terminalTombstone ? null : payload, s.marketStructureLatestForTest().get()),
                        "final committed snapshot or withdrawal recovered");
                    verify(socket, times(terminalTombstone ? 0 : 1)).sendMessage(any());
                }
                if (poll <= capturedEnd + (terminalTombstone ? 0 : 1) && position.get() < poll) {
                    position.set(poll);
                    Object value = switch (poll) { case 1 -> older; case 2 -> null; case 3 -> payloadRecord; default -> live; };
                    return new ConsumerRecords<>(Map.of(STRUCTURE, List.of(
                        new ConsumerRecord<String, Object>(STRUCTURE.topic(), 0, poll - 1, "SPX", value))));
                }
                s.setRunningForTest(false);
                return ConsumerRecords.empty();
            });
            s.setRunningForTest(true);
            s.runLiveConsumerAttemptForTest("avro-live", Map.of(STRUCTURE.topic(), BINDINGS.get(STRUCTURE.topic())),
                new java.util.concurrent.atomic.AtomicBoolean(), consumer, retry);
            if (terminalTombstone) {
                assertNull(s.marketStructureLatestForTest().get(), "an authoritative final withdrawal clears the complete hello");
                verify(socket, never()).sendMessage(any());
                assertTrue(s.retainMarketStructure(1791921500000L, TaFixtures.frame(older)), "a withdrawal resets the retention clock");
                continue;
            }
            String liveText = TaFixtures.frame(live);
            assertTrue(liveText.equals(s.marketStructureLatestForTest().get()), "continuous live delivery resumes after recovery");
            assertTrue(s.cvdHelloJson().contains("\"structure\":" + liveText), "hello keeps original prices and event time");
            var frames = org.mockito.ArgumentCaptor.forClass(org.springframework.web.socket.TextMessage.class);
            verify(socket, times(2)).sendMessage(frames.capture());
            assertTrue(frames.getAllValues().get(0).getPayload().contains(payload), "only final recovered snapshot fans out");
            assertTrue(frames.getAllValues().get(1).getPayload().contains(liveText), "next fresh event fans out normally");
        }
              }
}
    @Test
    @SuppressWarnings("unchecked")
    void lateTopicAdoptionHydratesPrivatelyThroughTheProductionPollLoop() throws Exception {
        GatewaySettings settings = spy(new GatewaySettings());
        doReturn(true).when(settings).marketStructureEnabled();
        doReturn(STRUCTURE.topic()).when(settings).marketStructureTopic();
        doReturn(0L).when(settings).partitionMetadataRefreshMs();
        var s = spy(new FeedGatewayService(settings, new ObjectMapper(), new HpsfGatewayViewMapper(), null));
        KafkaConsumer<String, Object> consumer = mock(KafkaConsumer.class);
        // Start with only the already-existing topic. Refresh/assign/adoption/seek and all
        // following polls use production code, so omitting the adoption ledger breaks this test.
        doAnswer(call -> { consumer.assign(List.of(OTHER)); return List.of(OTHER); })
            .when(s).bootstrapAssign(anyString(), same(consumer), anyMap());
        Node node = new Node(1, "b", 9092);
        when(consumer.listTopics(any(Duration.class))).thenReturn(Map.of(
            STRUCTURE.topic(), List.of(new PartitionInfo(STRUCTURE.topic(), 0, node, new Node[]{node}, new Node[]{node})),
            OTHER.topic(), List.of(new PartitionInfo(OTHER.topic(), 0, node, new Node[]{node}, new Node[]{node}))));
        when(consumer.offsetsForTimes(anyMap())).thenReturn(Map.of(STRUCTURE, new OffsetAndTimestamp(0, 1)));
        when(consumer.endOffsets(anyCollection(), any(Duration.class))).thenReturn(Map.of(STRUCTURE, 2L));
        var position = new java.util.concurrent.atomic.AtomicLong();
        doAnswer(call -> { position.set(2); return null; }).when(consumer).seekToEnd(anyCollection());
        doAnswer(call -> { position.set(call.getArgument(1)); return null; }).when(consumer).seek(eq(STRUCTURE), anyLong());
        when(consumer.assignment()).thenReturn(java.util.Set.of(STRUCTURE, OTHER));
        when(consumer.position(STRUCTURE)).thenAnswer(call -> position.get());
        s.runOutboundWritesInline();
        var socket = mock(org.springframework.web.socket.WebSocketSession.class);
        when(socket.getId()).thenReturn("late-structure");
        when(socket.isOpen()).thenReturn(true);
        s.addClient(socket);
        clearInvocations(socket);
        var old = ui(100L);
        var latest = ui(200L);
        var polls = new java.util.concurrent.atomic.AtomicInteger();
        when(consumer.poll(any(Duration.class))).thenAnswer(call -> {
            int poll = polls.incrementAndGet();
            if (poll <= 3) {
                assertNull(s.marketStructureLatestForTest().get(), "adoption must not expose partial history in hello");
                verify(socket, never()).sendMessage(any());
            }
            if (poll == 1) return ConsumerRecords.empty();
            if (poll == 2 || poll == 3) {
                position.set(poll - 1);
                return new ConsumerRecords<>(Map.of(STRUCTURE, List.of(
                    new ConsumerRecord<String, Object>(STRUCTURE.topic(), 0, poll - 2, "SPX", poll == 2 ? old : latest))));
            }
            s.setRunningForTest(false);
            return ConsumerRecords.empty();
        });
        s.setRunningForTest(true);
        s.runLiveConsumerAttemptForTest("avro-live", BINDINGS, new java.util.concurrent.atomic.AtomicBoolean(), consumer, false);
        assertEquals(TaFixtures.frame(latest), s.marketStructureLatestForTest().get());
        var frame = org.mockito.ArgumentCaptor.forClass(org.springframework.web.socket.TextMessage.class);
        verify(socket, times(1)).sendMessage(frame.capture());
        assertTrue(frame.getValue().getPayload().contains(TaFixtures.frame(latest)));
        assertTrue(s.cvdHelloJson().contains("\"structure\":" + TaFixtures.frame(latest)));
    }

}

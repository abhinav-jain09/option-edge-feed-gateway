package app.feedgateway;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.consumer.OffsetAndTimestamp;
import org.apache.kafka.common.Node;
import org.apache.kafka.common.PartitionInfo;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.Test;

/**
 * PATTERNS recovery = the structure record's path, per timeframe: the retained window is re-read on a cold or retry
 * start, held PRIVATELY until the captured end offsets are consumed, and only the final committed set (or
 * withdrawal) of each timeframe becomes the hello and fans out — never a partial history, never a tombstone's gap.
 */
class MarketStructurePatternsRecoveryTest {
    private static final TopicPartition PATTERNS = new TopicPartition("structure.patterns", 0);
    private static final Map<String, FeedGatewayService.TopicBinding> BINDINGS = Map.of(
        PATTERNS.topic(), new FeedGatewayService.TopicBinding("DATABENTO", "market-structure-patterns"));

    private static FeedGatewayService service() {
        GatewaySettings settings = spy(new GatewaySettings());
        doReturn(true).when(settings).marketStructurePatternsEnabled();
        doReturn(PATTERNS.topic()).when(settings).marketStructurePatternsTopic();
        return new FeedGatewayService(settings, new ObjectMapper(), new HpsfGatewayViewMapper(), null);
    }

    private static String set(String tf, long asOfMs) {
        return "{\"schemaVersion\":1,\"service\":\"market-structure\",\"state\":\"OK\",\"sessionDate\":\"2026-10-16\","
                + "\"asOfMs\":" + asOfMs + ",\"scheduleId\":\"2026-10-16.r0000000000\",\"timeframe\":\"" + tf + "\","
                + "\"decisionOrdinal\":7,\"parameterSetHash\":\"" + "a".repeat(64) + "\","
                + "\"effectiveLimits\":{\"maxPatternBars\":24,\"maxAnnotations\":64},\"annotations\":[]}";
    }

    @Test
    @SuppressWarnings("unchecked")
    void coldStartSeeksThePatternTopicBackLikeTheStructureTopic() {
        KafkaConsumer<String, Object> consumer = mock(KafkaConsumer.class);
        when(consumer.offsetsForTimes(anyMap())).thenAnswer(call -> {
            Map<TopicPartition, Long> cuts = call.getArgument(0);
            return Map.of(PATTERNS, new OffsetAndTimestamp(41, cuts.get(PATTERNS)));
        });
        when(consumer.endOffsets(anyCollection())).thenReturn(Map.of(PATTERNS, 42L));
        long before = System.currentTimeMillis();
        service().seekMarketStructureSnapshot(consumer, List.of(PATTERNS), BINDINGS);
        verify(consumer).seek(PATTERNS, 41);
        var calls = mockingDetails(consumer).getInvocations().stream().filter(c -> c.getMethod().getName().equals("offsetsForTimes")).toList();
        Map<TopicPartition, Long> cut = (Map<TopicPartition, Long>) calls.get(calls.size() - 1).getArgument(0);
        assertTrue(cut.get(PATTERNS) >= before - Duration.ofDays(7).toMillis());
    }

    @Test
    @SuppressWarnings("unchecked")
    void recoveryPublishesOnlyTheFinalSetPerTimeframeAndKeepsTombstonesPrivate() throws Exception {
        for (boolean retry : List.of(false, true)) {
            var s = service();
            String older1m = set("1m", 1791921500000L), final1m = set("1m", 1791921600000L), live1m = set("1m", 1791921600001L);
            String only5m = set("5m", 1791921550000L);
            if (retry) s.retainMarketStructurePatterns("1m", 1791921500000L, older1m);
            s.runOutboundWritesInline();
            var socket = mock(org.springframework.web.socket.WebSocketSession.class);
            when(socket.getId()).thenReturn("patterns-recovery");
            when(socket.isOpen()).thenReturn(true);
            s.addClient(socket);
            clearInvocations(socket);
            KafkaConsumer<String, Object> consumer = mock(KafkaConsumer.class);
            Node node = new Node(1, "b", 9092);
            when(consumer.listTopics(any(Duration.class))).thenReturn(Map.of(PATTERNS.topic(),
                List.of(new PartitionInfo(PATTERNS.topic(), 0, node, new Node[]{node}, new Node[]{node}))));
            when(consumer.offsetsForTimes(anyMap())).thenReturn(Map.of(PATTERNS, new OffsetAndTimestamp(0, 1)));
            // history: 1m older, 5m set, a KEYED 15m tombstone, a 5m tombstone, 1m final — then live 1m
            long capturedEnd = 5L;
            when(consumer.endOffsets(anyCollection(), any(Duration.class))).thenReturn(Map.of(PATTERNS, capturedEnd));
            var position = new java.util.concurrent.atomic.AtomicLong();
            doAnswer(call -> { position.set(capturedEnd); return null; }).when(consumer).seekToEnd(anyCollection());
            doAnswer(call -> { position.set(call.getArgument(1)); return null; }).when(consumer).seek(eq(PATTERNS), anyLong());
            when(consumer.assignment()).thenReturn(java.util.Set.of(PATTERNS));
            when(consumer.position(PATTERNS)).thenAnswer(call -> position.get());
            var polls = new java.util.concurrent.atomic.AtomicInteger();
            when(consumer.poll(any(Duration.class))).thenAnswer(call -> {
                int poll = polls.incrementAndGet();
                if (poll <= capturedEnd) {
                    assertEquals(retry ? older1m : null, s.marketStructurePatternsLatestForTest("1m"), "partial history never changes a connect hello");
                    assertNull(s.marketStructurePatternsLatestForTest("5m"));
                    verify(socket, never()).sendMessage(any());
                }
                if (poll == capturedEnd + 1) {
                    assertEquals(final1m, s.marketStructurePatternsLatestForTest("1m"), "the final committed 1m set is recovered");
                    assertNull(s.marketStructurePatternsLatestForTest("5m"), "the 5m set was withdrawn by its keyed tombstone");
                    assertNull(s.marketStructurePatternsLatestForTest("15m"));
                    verify(socket, times(1)).sendMessage(any());
                }
                if (poll <= capturedEnd + 1 && position.get() < poll) {
                    position.set(poll);
                    String key = switch (poll) { case 2, 4 -> "2026-10-16.r0000000000|5m"; case 3 -> "2026-10-16.r0000000000|15m"; default -> "2026-10-16.r0000000000|1m"; };
                    String value = switch (poll) { case 1 -> older1m; case 2 -> only5m; case 3, 4 -> null; case 5 -> final1m; default -> live1m; };
                    return new ConsumerRecords<>(Map.of(PATTERNS, List.of(
                        new ConsumerRecord<String, Object>(PATTERNS.topic(), 0, poll - 1, key, value))));
                }
                s.setRunningForTest(false);
                return ConsumerRecords.empty();
            });
            s.setRunningForTest(true);
            s.runLiveConsumerAttemptForTest("state-live", Map.of(PATTERNS.topic(), BINDINGS.get(PATTERNS.topic())),
                new java.util.concurrent.atomic.AtomicBoolean(), consumer, retry);
            assertEquals(live1m, s.marketStructurePatternsLatestForTest("1m"), "continuous live delivery resumes after recovery");
            verify(socket, times(2)).sendMessage(any());
        }
    }
}

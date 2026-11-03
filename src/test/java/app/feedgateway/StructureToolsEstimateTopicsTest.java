package app.feedgateway;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
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
 * GTH-STRUCTURE-OUTPUTS (processing market-structure-service docs): the overnight (Globex estimate) structure tools and classical geometry sets arrive on two
 * topics of their own and feed the SAME event and per-timeframe retention as the regular sets, newest asOfMs wins, live and in recovery. No flag of their own.
 */
class StructureToolsEstimateTopicsTest {
    private static final TopicPartition REGULAR = new TopicPartition("structure.tools", 0);
    private static final TopicPartition ESTIMATE = new TopicPartition("structure.estimate.tools", 0);
    private static final TopicPartition GEO_REGULAR = new TopicPartition("structure.geometry", 0);
    private static final TopicPartition GEO_ESTIMATE = new TopicPartition("structure.estimate.geometry", 0);

    private static FeedGatewayService service() {
        GatewaySettings settings = spy(new GatewaySettings());
        doReturn(true).when(settings).structureToolsEnabled();
        doReturn(REGULAR.topic()).when(settings).structureToolsTopic();
        doReturn(ESTIMATE.topic()).when(settings).structureToolsEstimateTopic();
        doReturn(true).when(settings).geometryEnabled();
        doReturn(GEO_REGULAR.topic()).when(settings).geometryTopic();
        doReturn(GEO_ESTIMATE.topic()).when(settings).geometryEstimateTopic();
        return new FeedGatewayService(settings, new ObjectMapper(), new HpsfGatewayViewMapper(), null);
    }

    @Test
    void theEstimateTopicsHaveDefaultNamesAndNoFlagOfTheirOwn() {
        GatewaySettings s = new GatewaySettings();
        assertEquals("options.spx.market-structure.estimate.tools", s.structureToolsEstimateTopic());
        assertEquals("options.spx.market-structure.estimate.geometry", s.geometryEstimateTopic());
        assertFalse(s.structureToolsEnabled(), "the regular flags stay off by default; nothing new switches on");
        assertFalse(s.geometryEnabled());
    }

    @Test
    void bothEstimateTopicsAreBoundToTheRegularEventsAndListedAsTolerantAndRecoveryTopics() throws Exception {
        String source = Files.readString(Path.of("src/main/java/app/feedgateway/FeedGatewayService.java"));
        assertTrue(source.contains("bindChartTopic(topicEvents, settings.structureToolsEstimateTopic(), TimeframeSetRelay.TOOLS_EVENT);"));
        assertTrue(source.contains("bindChartTopic(topicEvents, settings.geometryEstimateTopic(), TimeframeSetRelay.GEOMETRY_EVENT);"));
        assertTrue(source.contains("topics.add(settings.structureToolsEstimateTopic());"));
        assertTrue(source.contains("topics.add(settings.geometryEstimateTopic());"));
        // both sit inside the regular flag's block: they never bind without the stream itself
        int tools = source.indexOf("if (settings.structureToolsEnabled()) {\n            bindChartTopic(topicEvents, settings.structureToolsTopic()");
        assertTrue(tools > 0 && source.indexOf("settings.structureToolsEstimateTopic(), TimeframeSetRelay.TOOLS_EVENT", tools) < tools + 700);
    }

    @Test
    void toolsRecoveryAndLiveKeepTheNewestSetWhicheverTopicIsReadLast() throws Exception {
        for (boolean estimateLast : List.of(true, false)) run(false, estimateLast, true);
    }

    @Test
    void geometryRecoveryAndLiveKeepTheNewestSetWhicheverTopicIsReadLast() throws Exception {
        for (boolean estimateLast : List.of(true, false)) run(true, estimateLast, true);
    }

    @Test
    void anEstimateTopicThatDoesNotExistYetChangesNothing() throws Exception {
        run(false, false, false);   // production, or before the producer is deployed: only the regular topic exists
        run(true, false, false);
    }

    /** Two topics, one event: the regular set (asOf 2000) and the estimate set (asOf 1000) arrive in either order; the newer is retained. */
    @SuppressWarnings("unchecked")
    private void run(boolean geometry, boolean estimateLast, boolean estimateExists) throws Exception {
        TopicPartition regular = geometry ? GEO_REGULAR : REGULAR, estimate = geometry ? GEO_ESTIMATE : ESTIMATE;
        String event = geometry ? TimeframeSetRelay.GEOMETRY_EVENT : TimeframeSetRelay.TOOLS_EVENT;
        FeedGatewayService.TopicBinding binding = new FeedGatewayService.TopicBinding("DATABENTO", event);
        Map<String, FeedGatewayService.TopicBinding> bindings = Map.of(regular.topic(), binding, estimate.topic(), binding);
        var s = service();
        s.runOutboundWritesInline();
        var socket = mock(org.springframework.web.socket.WebSocketSession.class);
        when(socket.getId()).thenReturn("estimate-set-recovery");
        when(socket.isOpen()).thenReturn(true);
        s.addClient(socket);
        clearInvocations(socket);
        KafkaConsumer<String, Object> consumer = mock(KafkaConsumer.class);
        Node node = new Node(1, "b", 9092);
        Map<String, List<PartitionInfo>> listing = new java.util.HashMap<>();
        listing.put(regular.topic(), List.of(new PartitionInfo(regular.topic(), 0, node, new Node[]{node}, new Node[]{node})));
        if (estimateExists) listing.put(estimate.topic(), List.of(new PartitionInfo(estimate.topic(), 0, node, new Node[]{node}, new Node[]{node})));
        when(consumer.listTopics(any(Duration.class))).thenReturn(listing);
        List<TopicPartition> present = estimateExists ? List.of(regular, estimate) : List.of(regular);
        Map<TopicPartition, OffsetAndTimestamp> times = new java.util.HashMap<>();
        Map<TopicPartition, Long> ones = new java.util.HashMap<>(), zeros = new java.util.HashMap<>();
        for (TopicPartition tp : present) { times.put(tp, new OffsetAndTimestamp(0, 1)); ones.put(tp, 1L); zeros.put(tp, 0L); }
        when(consumer.offsetsForTimes(anyMap())).thenReturn(times);
        when(consumer.endOffsets(anyCollection(), any(Duration.class))).thenReturn(ones);
        when(consumer.endOffsets(anyCollection())).thenReturn(ones);
        when(consumer.beginningOffsets(anyCollection())).thenReturn(zeros);
        var positions = new java.util.concurrent.ConcurrentHashMap<TopicPartition, Long>(zeros);
        doAnswer(call -> { positions.put(call.getArgument(0), (Long) call.getArgument(1)); return null; }).when(consumer).seek(any(TopicPartition.class), anyLong());
        doAnswer(call -> { for (Object tp : (java.util.Collection<?>) call.getArgument(0)) positions.put((TopicPartition) tp, 1L); return null; }).when(consumer).seekToEnd(anyCollection());
        when(consumer.assignment()).thenReturn(new java.util.HashSet<>(present));
        when(consumer.position(any(TopicPartition.class))).thenAnswer(call -> positions.get((TopicPartition) call.getArgument(0)));
        var polls = new java.util.concurrent.atomic.AtomicInteger();
        when(consumer.poll(any(Duration.class))).thenAnswer(call -> {
            int poll = polls.incrementAndGet();
            if (poll <= 2) {
                boolean regularNow = !estimateExists || (estimateLast ? poll == 1 : poll == 2);
                TopicPartition tp = regularNow ? regular : estimate;
                positions.put(tp, 1L);
                long asOf = regularNow ? 2000L : 1000L;
                Object value = geometry ? TaFixtures.geometrySet("TF_5M", asOf) : TaFixtures.tools("M5", asOf, "OK");
                return new ConsumerRecords<>(Map.of(tp, List.of(new ConsumerRecord<String, Object>(tp.topic(), 0, 0, "k", value))));
            }
            s.setRunningForTest(false);
            return ConsumerRecords.empty();
        });
        s.setRunningForTest(true);
        s.runLiveConsumerAttemptForTest("avro-live", bindings, new java.util.concurrent.atomic.AtomicBoolean(), consumer, false);
        TimeframeSetRelay relay = s.taRelaysForTest().stream().filter(r -> r.handles(event)).map(r -> (TimeframeSetRelay) r).findFirst().orElseThrow();
        String hello = relay.helloField(1 << 20);
        String label = "geometry=" + geometry + " estimateLast=" + estimateLast + " estimateExists=" + estimateExists;
        assertTrue(hello.contains("\"asOfMs\":2000"), "the newer set is retained (" + label + "): " + hello);
        assertFalse(hello.contains("\"asOfMs\":1000"), label + ": " + hello);
    }
}

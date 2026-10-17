package app.feedgateway;

import static org.junit.jupiter.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.MockConsumer;
import org.apache.kafka.clients.consumer.OffsetResetStrategy;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.errors.RecordDeserializationException;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.apache.kafka.common.record.TimestampType;
import org.junit.jupiter.api.Test;

class CvdRetainedPollTest {
    @Test void malformedLegacyValuesAreCountedAndSkippedWithoutWedgingOtherTopics() {
        var settings=new GatewaySettings();var service=new FeedGatewayService(settings,new ObjectMapper(),new HpsfGatewayViewMapper(),null);
        TopicPartition tp=new TopicPartition(settings.esCvdBarsTopic(),0);
        try(var consumer=new MockConsumer<String,Object>(OffsetResetStrategy.NONE)) {
            consumer.assign(List.of(tp));consumer.updateBeginningOffsets(Map.of(tp,0L));consumer.seek(tp,10);
            consumer.setPollException(failure(tp,10,new byte[]{'[',']'}));
            assertTrue(service.pollCompatible(consumer,Duration.ZERO).isEmpty());assertEquals(11,consumer.position(tp));
            assertTrue(service.metrics().contains("options_edge_feed_gateway_cvd_retained_records_dropped_total 1\n"));
            consumer.addRecord(new ConsumerRecord<>(tp.topic(),0,11,"ES","{\"next\":true}"));
            assertEquals(1,service.pollCompatible(consumer,Duration.ZERO).count());
            for(byte[] malformed:new byte[][]{new byte[0],new byte[]{(byte)0xff},null}) {
                long offset=consumer.position(tp);consumer.setPollException(failure(tp,offset,malformed));
                assertTrue(service.pollCompatible(consumer,Duration.ZERO).isEmpty());assertEquals(offset+1,consumer.position(tp));
            }
        }
    }

    @Test void framedAvroErrorsAndUnboundTopicErrorsRemainFatalAtTheSameOffset() {
        var settings=new GatewaySettings();var service=new FeedGatewayService(settings,new ObjectMapper(),new HpsfGatewayViewMapper(),null);
        for(String topic:List.of(settings.esCvdBarsTopic(),"unrelated-topic")) {
            var tp=new TopicPartition(topic,0);
            try(var consumer=new MockConsumer<String,Object>(OffsetResetStrategy.NONE)) {
                consumer.assign(List.of(tp));consumer.updateBeginningOffsets(Map.of(tp,0L));consumer.seek(tp,7);
                var error=failure(tp,7,topic.equals(settings.esCvdBarsTopic())?new byte[]{0,0,0,0,99,1}:new byte[]{'['});
                consumer.setPollException(error);
                assertSame(error,assertThrows(RecordDeserializationException.class,()->service.pollCompatible(consumer,Duration.ZERO)));
                assertEquals(7,consumer.position(tp));
            }
        }
    }

    @Test void malformedRunReplayValuesFailWithoutSkippingOrCountingLiveRetainedDrops() {
        var settings = new GatewaySettings();
        var service = new FeedGatewayService(settings, new ObjectMapper(), new HpsfGatewayViewMapper(), null);
        String topic = app.feedgateway.mtsession.gateway.ReplayTopicResolver.toReplayTopic(settings.esGexSpxAlignedTopic(), "fixture-run");
        var tp = new TopicPartition(topic, 0);
        try (var consumer = new MockConsumer<String, Object>(OffsetResetStrategy.NONE)) {
            consumer.assign(List.of(tp)); consumer.updateBeginningOffsets(Map.of(tp, 0L)); consumer.seek(tp, 7);
            var error = failure(tp, 7, new byte[] {'[', ']'});
            consumer.setPollException(error);
            assertSame(error, assertThrows(RecordDeserializationException.class,
                    () -> service.pollCompatible(consumer, Duration.ZERO)));
            assertEquals(7, consumer.position(tp));
            assertTrue(service.metrics().contains("options_edge_feed_gateway_cvd_retained_records_dropped_total 0\n"));
        }
    }

    @Test void bufferedPollsDoNotMultiplyFullCacheExpiryScans() {
        var maintenance=new FeedGatewayService.PollMaintenance(250);
        assertTrue(maintenance.due(1_000_000_000L));
        assertFalse(maintenance.due(1_000_000_001L));
        assertFalse(maintenance.due(1_249_999_999L));
        assertTrue(maintenance.due(1_250_000_000L));
        assertFalse(maintenance.due(1_250_000_001L));
    }

    private static RecordDeserializationException failure(TopicPartition tp,long offset,byte[] value) {
        return new RecordDeserializationException(RecordDeserializationException.DeserializationExceptionOrigin.VALUE,tp,offset,123L,
                TimestampType.CREATE_TIME,ByteBuffer.wrap(new byte[]{1}),value==null?null:ByteBuffer.wrap(value),new RecordHeaders(),
                "retained fixture contract violation",new IllegalArgumentException("fixture"));
    }
}

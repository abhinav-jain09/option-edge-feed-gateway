package app.feedgateway;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.Map;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.Test;

class OrderChartReplayGateTest {
    private final OrderChartReplayGate gate = new OrderChartReplayGate();
    private final TopicPartition p = new TopicPartition("bars",0), q = new TopicPartition("bars",1);
    private boolean ready() { return gate.read(() -> true, () -> false); }
    @Test void capabilityAndUncapturedOrPartialReplayNeverMeanComplete() {
        Object owner=new Object();assertFalse(ready());gate.begin(owner,List.of(p,q));
        gate.capture(owner,Map.of(p,5L));gate.advance(owner,t->100);assertFalse(ready());
        gate.capture(owner,Map.of(q,7L));gate.advance(owner,t->t.equals(p)?5:6);assertFalse(ready());
        gate.advance(owner,t->t.equals(p)?5:7);assertTrue(ready());
    }
    @Test void latePartitionReopensAndRetainsPriorBarriers() {
        Object owner=new Object();gate.begin(owner,List.of(p));gate.capture(owner,Map.of(p,5L));gate.advance(owner,t->5);assertTrue(ready());
        gate.begin(owner,List.of(q));assertFalse(ready());gate.capture(owner,Map.of(q,7L));
        gate.advance(owner,t->t.equals(p)?4:7);assertFalse(ready());gate.advance(owner,t->t.equals(p)?5:7);assertTrue(ready());
    }
    @Test void retryAndFailureFenceThePriorAttempt() {
        Object first=new Object(),next=new Object();gate.begin(first,List.of(p));gate.capture(first,Map.of(p,5L));gate.advance(first,t->5);assertTrue(ready());
        gate.invalidate();assertFalse(ready());gate.advance(first,t->100);assertFalse(ready());
        gate.begin(next,List.of(p));gate.capture(first,Map.of(p,0L));gate.advance(first,t->100);assertFalse(ready());
        gate.capture(next,Map.of(p,8L));gate.advance(next,t->{throw new IllegalStateException("unassigned");});assertFalse(ready());
        gate.advance(next,t->8);assertTrue(ready());
    }
    @Test void missingPartitionsRemainLoadingAndEmptyCapturedPartitionCanComplete() {
        Object owner=new Object();gate.begin(owner,List.of());gate.advance(owner,t->0);assertFalse(ready());
        gate.begin(owner,List.of(p));gate.capture(owner,Map.of(p,0L));gate.advance(owner,t->0);assertTrue(ready());
    }
    @Test void actualCacheSeekAndPostApplyProgressControlTheHistoryGate() throws Exception {
        var service=FootprintWiringTest.on();var field=FeedGatewayService.class.getDeclaredField("orderChartFootprintReplay");field.setAccessible(true);var actual=(OrderChartReplayGate)field.get(service);
        @SuppressWarnings("unchecked") var consumer=(org.apache.kafka.clients.consumer.KafkaConsumer<String,Object>)org.mockito.Mockito.mock(org.apache.kafka.clients.consumer.KafkaConsumer.class);
        var partition=new TopicPartition("futures.footprint.bars",0);
        org.mockito.Mockito.when(consumer.offsetsForTimes(org.mockito.ArgumentMatchers.anyMap())).thenAnswer(invocation->{assertFalse(actual.read(()->true,()->false));return Map.of(partition,new org.apache.kafka.clients.consumer.OffsetAndTimestamp(0,0));});
        org.mockito.Mockito.when(consumer.endOffsets(org.mockito.ArgumentMatchers.anyCollection(),org.mockito.ArgumentMatchers.any(java.time.Duration.class))).thenReturn(Map.of(partition,5L));
        org.mockito.Mockito.when(consumer.position(partition)).thenReturn(4L,5L);
        var attempt=new FeedGatewayService.StrikeReplay();
        service.cacheHydrationSeek(consumer,List.of(partition),Map.of(partition.topic(),new FeedGatewayService.TopicBinding("DATABENTO","es-footprint-bar")),attempt,1000);
        assertFalse(actual.read(()->true,()->false));service.footprintStrikeReplayProgress(consumer,attempt);assertFalse(actual.read(()->true,()->false));service.footprintStrikeReplayProgress(consumer,attempt);assertTrue(actual.read(()->true,()->false));
    }
    @Test void aSameOwnerReopenCannotPublishPositionsFromAnOlderRevision() {
        Object owner=new Object();gate.begin(owner,List.of(p));gate.capture(owner,Map.of(p,5L));
        gate.advance(owner,t->{gate.begin(owner,List.of(p));gate.capture(owner,Map.of(p,5L));return 5;});assertFalse(ready());
        gate.advance(owner,t->5);assertTrue(ready());
    }
    @Test void consumerPositionLookupDoesNotHoldTheGateMonitor() throws Exception {
        Object owner=new Object();gate.begin(owner,List.of(p));gate.capture(owner,Map.of(p,5L));
        var entered=new java.util.concurrent.CountDownLatch(1);var release=new java.util.concurrent.CountDownLatch(1);
        Thread worker=new Thread(()->gate.advance(owner,t->{entered.countDown();try{release.await(2,java.util.concurrent.TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}return 5;}));worker.start();
        try{assertTrue(entered.await(1,java.util.concurrent.TimeUnit.SECONDS));assertTimeoutPreemptively(java.time.Duration.ofSeconds(1),()->gate.invalidate());assertFalse(ready());}finally{release.countDown();worker.join(2000);}assertFalse(worker.isAlive());assertFalse(ready());
    }
    @Test void actualLateAdoptionRecoveryKeepsTheAttemptOwnedHistoryBarrier() throws Exception {
        var service=FootprintWiringTest.on();var field=FeedGatewayService.class.getDeclaredField("orderChartFootprintReplay");field.setAccessible(true);var actual=(OrderChartReplayGate)field.get(service);
        @SuppressWarnings("unchecked") var consumer=(org.apache.kafka.clients.consumer.KafkaConsumer<String,Object>)org.mockito.Mockito.mock(org.apache.kafka.clients.consumer.KafkaConsumer.class);
        var first=new TopicPartition("futures.footprint.bars",0);var added=new TopicPartition("futures.footprint.bars",1);
        org.mockito.Mockito.when(consumer.offsetsForTimes(org.mockito.ArgumentMatchers.anyMap())).thenReturn(Map.of(first,new org.apache.kafka.clients.consumer.OffsetAndTimestamp(0,0),added,new org.apache.kafka.clients.consumer.OffsetAndTimestamp(0,0)));
        org.mockito.Mockito.when(consumer.endOffsets(org.mockito.ArgumentMatchers.anyCollection(),org.mockito.ArgumentMatchers.any(java.time.Duration.class))).thenAnswer(inv->{java.util.Collection<TopicPartition> parts=inv.getArgument(0);return parts.contains(added)?Map.of(added,7L):Map.of(first,5L);});
        org.mockito.Mockito.when(consumer.position(first)).thenReturn(5L);org.mockito.Mockito.when(consumer.position(added)).thenReturn(6L,7L);
        var attempt=new FeedGatewayService.StrikeReplay();var bindings=Map.of(first.topic(),new FeedGatewayService.TopicBinding("DATABENTO","es-footprint-bar"));
        service.cacheHydrationSeek(consumer,List.of(first),bindings,attempt,1000);service.footprintStrikeReplayProgress(consumer,attempt);assertTrue(actual.read(()->true,()->false));
        service.cacheHydrationSeek(consumer,List.of(added),bindings,attempt,1000);
        var recovering=FeedGatewayService.class.getDeclaredMethod("markCacheRecovering",java.util.concurrent.atomic.AtomicBoolean.class,java.util.Collection.class);recovering.setAccessible(true);recovering.invoke(service,new java.util.concurrent.atomic.AtomicBoolean(true),List.of("es-footprint-bar"));
        service.footprintStrikeReplayProgress(consumer,attempt);assertFalse(actual.read(()->true,()->false));service.footprintStrikeReplayProgress(consumer,attempt);assertTrue(actual.read(()->true,()->false));
    }
}

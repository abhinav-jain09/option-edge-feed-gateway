package app.feedgateway;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;
import java.io.IOException;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.*;

class ChartOutboundChannelTest {
    static final class Metrics implements OutboundChannel.Metrics {
        final AtomicInteger slow=new AtomicInteger(),errors=new AtomicInteger();
        public void enqueued(int bytes){}public void coalesced(){}public void sent(int bytes){}
        public void disconnectedSlow(){slow.incrementAndGet();}public void writeError(){errors.incrementAndGet();}public void droppedOnClose(int n){}
    }
    static WebSocketSession session() {var s=mock(WebSocketSession.class);when(s.getId()).thenReturn("chart-fixture");when(s.isOpen()).thenReturn(true);return s;}
    static OutboundChannel channel(WebSocketSession s,java.util.concurrent.Executor writers,Metrics metrics,int messages,long bytes) {
        return new OutboundChannel(s,writers,task->{},messages,bytes,metrics,ignored->{});
    }
    @Test void sharedQueueSaturationParksWithoutClosingOrReservingChartBytes() {
        var tasks=new OutboundIdleDispatcherTest.Manual();var m=new Metrics();var c=channel(session(),tasks,m,2,1<<20);
        var b=new ChartOutboundBudget(()->{});var owner=b.attach();assertTrue(c.enqueue("already queued",null));
        assertEquals(OutboundChannel.HistoryAdmission.BUSY,c.tryEnqueueChartBinary(new byte[64],()->true,owner));
        assertFalse(c.isClosed());assertEquals(0,m.slow.get());assertEquals(0,b.reservedBytes());assertEquals(1,c.queueDepth());
    }
    @Test void globalSaturationParksThenAdmissionResumesAfterActualSend() {
        var tasks=new OutboundIdleDispatcherTest.Manual();var b=new ChartOutboundBudget(65_536,131_072,2,2,()->{});
        var one=channel(session(),tasks,new Metrics(),100,1<<20);var two=channel(session(),tasks,new Metrics(),100,1<<20);
        var a=b.attach();var z=b.attach();
        assertEquals(OutboundChannel.HistoryAdmission.ENQUEUED,one.tryEnqueueChartBinary(new byte[65_536],()->true,a));
        assertEquals(OutboundChannel.HistoryAdmission.BUSY,two.tryEnqueueChartBinary(new byte[65_536],()->true,z));assertFalse(two.isClosed());
        tasks.all();assertEquals(0,b.reservedBytes());
        assertEquals(OutboundChannel.HistoryAdmission.ENQUEUED,two.tryEnqueueChartBinary(new byte[65_536],()->true,z));tasks.all();assertEquals(0,b.reservedBytes());
    }
    @Test void inFlightCopyStaysAccountedThroughDetachAndChannelCloseUntilWriterReturns() throws Exception {
        var tasks=new OutboundIdleDispatcherTest.Manual();var entered=new CountDownLatch(1);var release=new CountDownLatch(1);var s=session();
        doAnswer(inv->{entered.countDown();assertTrue(release.await(5,TimeUnit.SECONDS));return null;}).when(s).sendMessage(any());
        var b=new ChartOutboundBudget(100,100,2,1,()->{});var owner=b.attach();var c=channel(s,tasks,new Metrics(),100,1<<20);
        assertEquals(OutboundChannel.HistoryAdmission.ENQUEUED,c.tryEnqueueChartBinary(new byte[64],()->true,owner));
        var writer=new Thread(tasks::one);writer.start();assertTrue(entered.await(5,TimeUnit.SECONDS));
        try {
            assertEquals(0,c.queuedBytes());assertEquals(64,b.reservedBytes());owner.close();c.revokeAuthorization();
            assertEquals(64,b.reservedBytes());assertNull(b.attach());
        } finally {release.countDown();writer.join(5000);}
        assertFalse(writer.isAlive());assertEquals(0,b.reservedBytes());assertNotNull(b.attach());
    }
    @Test void queuedCopiesAreReleasedExactlyOnceOnCloseBeforeWriterStarts() throws Exception {
        var tasks=new OutboundIdleDispatcherTest.Manual();var b=new ChartOutboundBudget(()->{});var owner=b.attach();var s=session();var c=channel(s,tasks,new Metrics(),100,1<<20);
        for(int n=0;n<2;n++)assertEquals(OutboundChannel.HistoryAdmission.ENQUEUED,c.tryEnqueueChartBinary(new byte[1024],()->true,owner));
        assertEquals(2048,b.reservedBytes());c.revokeAuthorization();assertEquals(0,b.reservedBytes());tasks.all();assertEquals(0,b.reservedBytes());
        verify(s,never()).sendMessage(any());
    }
    @Test void callerMutationCannotChangeTheAdmittedWireCopy() throws Exception {
        var tasks=new OutboundIdleDispatcherTest.Manual();var s=session();var received=new AtomicInteger();
        doAnswer(inv->{received.set(((BinaryMessage)inv.getArgument(0)).getPayload().get(0));return null;}).when(s).sendMessage(any());
        var b=new ChartOutboundBudget(()->{});var c=channel(s,tasks,new Metrics(),100,1<<20);byte[] bytes={7};
        assertEquals(OutboundChannel.HistoryAdmission.ENQUEUED,c.tryEnqueueChartBinary(bytes,()->true,b.attach()));bytes[0]=9;
        tasks.all();assertEquals(7,received.get());assertEquals(0,b.reservedBytes());
    }
    @Test void writerFailureAndAuthorizationWithdrawalReleaseCopiesWithoutSendingUnauthorisedData() throws Exception {
        for(boolean ioFailure:new boolean[]{false,true}) {
            var tasks=new OutboundIdleDispatcherTest.Manual();var s=session();var allowed=new AtomicBoolean(true);var m=new Metrics();
            if(ioFailure)doThrow(new IOException("fixture")).when(s).sendMessage(any());
            var b=new ChartOutboundBudget(()->{});var c=channel(s,tasks,m,100,1<<20);
            assertEquals(OutboundChannel.HistoryAdmission.ENQUEUED,c.tryEnqueueChartBinary(new byte[10],allowed::get,b.attach()));
            if(!ioFailure)allowed.set(false);tasks.all();assertTrue(c.isClosed());assertEquals(0,b.reservedBytes());
            if(!ioFailure)verify(s,never()).sendMessage(any());else assertEquals(1,m.errors.get());
        }
    }
    @Test void configuredCapacityAndWriterRejectionNeverLeakAReservedCopy() {
        var b=new ChartOutboundBudget(()->{});var owner=b.attach();var m=new Metrics();var c=channel(session(),task->{},m,100,4096);
        assertEquals(OutboundChannel.HistoryAdmission.CAPACITY,c.tryEnqueueChartBinary(new byte[1025],()->true,owner));assertFalse(c.isClosed());assertEquals(0,b.reservedBytes());
        c=channel(session(),task->{throw new RejectedExecutionException("fixture");},m,100,1<<20);
        assertEquals(OutboundChannel.HistoryAdmission.CLOSED,c.tryEnqueueChartBinary(new byte[64],()->true,owner));assertEquals(0,b.reservedBytes());
    }
    @Test void racingProducersCannotExceedTwoOwnedCopiesOrCloseTheSocket() throws Exception {
        var tasks=new OutboundIdleDispatcherTest.Manual();var b=new ChartOutboundBudget(()->{});var owner=b.attach();var c=channel(session(),tasks,new Metrics(),100,1<<20);
        var accepted=new AtomicInteger();var workers=Executors.newFixedThreadPool(4);
        try {
            for(int n=0;n<100;n++)workers.submit(()->{if(c.tryEnqueueChartBinary(new byte[1024],()->true,owner)==OutboundChannel.HistoryAdmission.ENQUEUED)accepted.incrementAndGet();});
            workers.shutdown();assertTrue(workers.awaitTermination(5,TimeUnit.SECONDS));
        } finally {workers.shutdownNow();}
        assertEquals(2,accepted.get());assertEquals(2048,b.reservedBytes());assertFalse(c.isClosed());tasks.all();assertEquals(0,b.reservedBytes());
    }
    @Test void namespacedVolPremiumAndChartOwnersRunOutsideWriterTasksAndUnregisterIndependently() {
        var writes=new OutboundIdleDispatcherTest.Manual();var idles=new OutboundIdleDispatcherTest.Manual();
        var c=new OutboundChannel(session(),writes,task->{},idles,100,1<<20,new Metrics(),ignored->{});
        var vp=new AtomicInteger();var chart=new AtomicInteger();Object vpKey=new Object(),chartKey=new Object();
        assertTrue(c.onIdle(vpKey,vp::incrementAndGet));var registration=c.registerIdleOwner(chartKey,chart::incrementAndGet,()->fail("chart failure"));assertNotNull(registration);
        c.enqueue("first",null);writes.all();assertEquals(0,vp.get());assertEquals(0,chart.get());idles.all();assertEquals(1,vp.get());assertEquals(1,chart.get());
        assertTrue(c.onIdle(vpKey,null));c.enqueue("second",null);writes.all();idles.all();assertEquals(1,vp.get());assertEquals(2,chart.get());
        registration.close();assertTrue(c.onIdle(vpKey,vp::incrementAndGet));c.enqueue("third",null);writes.all();idles.all();assertEquals(2,vp.get());assertEquals(2,chart.get());
    }
    @Test void anOwnerFaultDoesNotRemoveAnotherStreamAndCloseCancelsPendingCallbacks() {
        var writes=new OutboundIdleDispatcherTest.Manual();var idles=new OutboundIdleDispatcherTest.Manual();
        var c=new OutboundChannel(session(),writes,task->{},idles,100,1<<20,new Metrics(),ignored->{});
        var vp=new AtomicInteger();var failed=new AtomicInteger();
        c.onIdle(new Object(),vp::incrementAndGet);c.registerIdleOwner(new Object(),()->{throw new AssertionError("fixture");},failed::incrementAndGet);
        c.enqueue("first",null);writes.all();idles.all();assertEquals(1,failed.get());assertEquals(1,vp.get());assertFalse(c.isClosed());
        c.enqueue("second",null);writes.all();c.revokeAuthorization();idles.all();assertEquals(1,vp.get());
        assertNull(c.registerIdleOwner(new Object(),()->fail("closed"),()->{}));
    }
    @Test void normalShutdownReleasesQueuedCopiesAndCancelsIdleOwners(){
        var writes=new OutboundIdleDispatcherTest.Manual();var idles=new OutboundIdleDispatcherTest.Manual();
        var budget=new ChartOutboundBudget(()->{});var owner=budget.attach();var c=new OutboundChannel(session(),writes,task->{},idles,100,1<<20,new Metrics(),ignored->{});
        var calls=new AtomicInteger();var registration=c.registerIdleOwner(new Object(),calls::incrementAndGet,()->fail("normal shutdown"));registration.wake();
        for(int n=0;n<2;n++)assertEquals(OutboundChannel.HistoryAdmission.ENQUEUED,c.tryEnqueueChartBinary(new byte[1024],()->true,owner));
        owner.close();assertEquals(1,budget.retainedOwners());c.shutdown();c.shutdown();assertEquals(0,budget.reservedBytes());assertEquals(0,budget.retainedOwners());writes.all();idles.all();assertEquals(0,calls.get());
        assertNull(c.registerIdleOwner(new Object(),()->{},()->{}));
    }

}

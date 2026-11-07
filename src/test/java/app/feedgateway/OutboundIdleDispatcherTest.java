package app.feedgateway;

import static org.junit.jupiter.api.Assertions.*;
import java.util.ArrayList;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;

class OutboundIdleDispatcherTest {
    static final class Manual implements Executor {
        final ConcurrentLinkedQueue<Runnable> tasks=new ConcurrentLinkedQueue<>();
        public void execute(Runnable task){tasks.add(task);}
        void one(){var task=tasks.poll();assertNotNull(task);task.run();}
        void all(){int count=0;while(!tasks.isEmpty()){assertTrue(count++<1000,"unbounded dispatcher");one();}}
    }
    @Test void boundedOwnersAndReplacingOneNeverClobbersAnother() {
        var e=new Manual();var d=new OutboundIdleDispatcher(e);var calls=new ArrayList<String>();
        var old=d.register("chart",()->calls.add("old"),()->fail("old failure"));
        var replacement=d.register("chart",()->calls.add("chart"),()->fail("chart failure"));
        old.close();
        for(String key:new String[]{"vol","other","fourth"})assertNotNull(d.register(key,()->calls.add(key),()->fail("unexpected")));
        assertNull(d.register("fifth",()->fail("capacity"),()->{}));
        d.signalIdle();assertEquals(1,e.tasks.size());e.all();
        assertEquals(java.util.Set.of("chart","vol","other","fourth"),java.util.Set.copyOf(calls));
        assertEquals(4,calls.size());replacement.close();calls.clear();d.signalIdle();e.all();assertFalse(calls.contains("chart"));
    }
    @Test void aFailingOwnerIsRemovedAndDoesNotStopTheHealthyOwner() {
        var e=new Manual();var d=new OutboundIdleDispatcher(e);var failed=new AtomicInteger();var healthy=new AtomicInteger();
        d.register("bad",()->{throw new AssertionError("fixture");},failed::incrementAndGet);
        d.register("good",healthy::incrementAndGet,()->fail("healthy"));
        d.signalIdle();e.all();d.signalIdle();e.all();
        assertEquals(1,failed.get());assertEquals(2,healthy.get());
    }
    @Test void selfWakeYieldsAndRoundRobinLetsOtherOwnersRun() {
        var e=new Manual();var d=new OutboundIdleDispatcher(e);var count=new AtomicInteger();var other=new AtomicInteger();var otherAt=new AtomicInteger(-1);
        var handle=new AtomicReference<OutboundIdleDispatcher.Registration>();
        handle.set(d.register("busy",()->{if(count.incrementAndGet()<100)handle.get().wake();},()->fail("busy failure")));
        d.register("other",()->{other.incrementAndGet();otherAt.set(count.get());},()->fail("other failure"));
        d.signalIdle();e.one();assertTrue(count.get()<=OutboundIdleDispatcher.MAX_CALLBACKS);
        assertFalse(e.tasks.isEmpty());e.all();assertEquals(100,count.get());assertEquals(1,other.get());assertTrue(otherAt.get()<100);
    }
    @Test void closeBeforeDrainCancelsEveryPendingOwner() {
        var e=new Manual();var d=new OutboundIdleDispatcher(e);var calls=new AtomicInteger();
        var r=d.register("chart",calls::incrementAndGet,()->fail("close is not a fault"));r.wake();
        d.close();e.all();r.wake();d.signalIdle();d.retry();
        assertEquals(0,calls.get());assertTrue(e.tasks.isEmpty());assertNull(d.register("new",()->{},()->{}));
    }
    @Test void rejectedHandoffRetainsOneReadyWakeForWatchdogRetry() {
        var e=new Manual();var reject=new AtomicBoolean(true);var calls=new AtomicInteger();
        var d=new OutboundIdleDispatcher(task->{if(reject.getAndSet(false))throw new RejectedExecutionException("fixture");e.execute(task);});
        d.register("chart",calls::incrementAndGet,()->fail("rejection must park"));
        d.signalIdle();assertTrue(e.tasks.isEmpty());d.retry();assertEquals(1,e.tasks.size());e.all();assertEquals(1,calls.get());
    }
    @Test void anExecutorThatPublishesThenThrowsCannotRunAStaleDuplicateTask() {
        var e=new Manual();var reject=new AtomicBoolean(true);var calls=new AtomicInteger();
        var d=new OutboundIdleDispatcher(task->{e.execute(task);if(reject.getAndSet(false))throw new RejectedExecutionException("published fixture");});
        d.register("chart",calls::incrementAndGet,()->fail("unexpected"));d.signalIdle();d.retry();
        assertEquals(2,e.tasks.size());e.all();assertEquals(1,calls.get());
    }
    @Test void anInlineTestExecutorCannotRecurseWithoutBound() {
        var d=new OutboundIdleDispatcher(Runnable::run);var calls=new AtomicInteger();var r=new AtomicReference<OutboundIdleDispatcher.Registration>();
        r.set(d.register("chart",()->{if(calls.incrementAndGet()<100)r.get().wake();},()->fail("inline")));
        r.get().wake();assertTrue(calls.get()<=OutboundIdleDispatcher.MAX_CALLBACKS);
        for(int n=0;n<200&&calls.get()<100;n++)d.retry();assertEquals(100,calls.get());
    }
    @Test void aReplacedFaultingCallbackCannotDeleteItsSuccessor() {
        var e=new Manual();var d=new OutboundIdleDispatcher(e);var calls=new AtomicInteger();var failures=new AtomicInteger();
        d.register("chart",()->{d.register("chart",calls::incrementAndGet,()->fail("replacement")).wake();throw new IllegalStateException("old fixture");},failures::incrementAndGet);
        d.signalIdle();e.all();assertEquals(1,failures.get());assertEquals(1,calls.get());
    }
    @Test void concurrentWakesCoalesceWhileAnOwnerIsRunning() throws Exception {
        var e=new Manual();var d=new OutboundIdleDispatcher(e);var entered=new CountDownLatch(1);var released=new CountDownLatch(1);var calls=new AtomicInteger();
        var r=d.register("chart",()->{if(calls.incrementAndGet()==1){entered.countDown();try{assertTrue(released.await(5,TimeUnit.SECONDS));}catch(InterruptedException ex){throw new AssertionError(ex);}}},()->fail("unexpected"));
        r.wake();var runner=new Thread(e::one);runner.start();assertTrue(entered.await(5,TimeUnit.SECONDS));
        var workers=Executors.newFixedThreadPool(4);
        try {for(int n=0;n<1000;n++)workers.submit(r::wake);workers.shutdown();assertTrue(workers.awaitTermination(5,TimeUnit.SECONDS));assertTrue(e.tasks.isEmpty());}
        finally {released.countDown();workers.shutdownNow();runner.join(5000);}
        assertFalse(runner.isAlive());e.all();assertEquals(2,calls.get());
    }
}

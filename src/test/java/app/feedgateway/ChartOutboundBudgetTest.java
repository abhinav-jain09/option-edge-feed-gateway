package app.feedgateway;

import static org.junit.jupiter.api.Assertions.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;

class ChartOutboundBudgetTest {
    @Test void globalAndPerOwnerLimitsRefuseWithoutMutation() {
        var b=new ChartOutboundBudget(12,8,2,3,()->{});var a=b.attach();var c=b.attach();
        var one=a.tryAcquire(4);var two=a.tryAcquire(4);assertNull(a.tryAcquire(1));
        var three=c.tryAcquire(4);assertNull(c.tryAcquire(1));assertEquals(12,b.reservedBytes());
        two.close();assertEquals(8,b.reservedBytes());var four=c.tryAcquire(4);assertNotNull(four);
        assertNull(c.tryAcquire(0));assertNull(c.tryAcquire(-1));assertNull(c.tryAcquire(9));one.close();three.close();four.close();
    }
    @Test void detachDoesNotForgetCopiesStillQueuedOrInFlight() {
        var b=new ChartOutboundBudget(100,100,2,1,()->{});var a=b.attach();var pending=a.tryAcquire(50);
        a.close();assertNull(a.tryAcquire(1));assertNull(b.attach());assertEquals(50,b.reservedBytes());assertEquals(1,b.retainedOwners());
        pending.close();assertEquals(0,b.reservedBytes());assertEquals(0,b.retainedOwners());assertNotNull(b.attach());
    }
    @Test void closeBlocksAdmissionButReleasesOnlyAfterActualCopyDisposal() {
        var b=new ChartOutboundBudget(100,100,2,2,()->{});var a=b.attach();var lease=a.tryAcquire(90);b.attach();
        b.close();assertNull(b.attach());assertNull(a.tryAcquire(1));assertEquals(90,b.reservedBytes());assertEquals(1,b.retainedOwners());
        lease.close();assertEquals(0,b.reservedBytes());assertEquals(0,b.retainedOwners());
    }
    @Test void aLeaseReleasesExactlyOnceAcrossRacingClosePaths() throws Exception {
        var wakes=new AtomicInteger();var b=new ChartOutboundBudget(wakes::incrementAndGet);var a=b.attach();var lease=a.tryAcquire(65536);
        var workers=Executors.newFixedThreadPool(4);
        try {for(int n=0;n<1000;n++)workers.submit(lease::close);workers.shutdown();assertTrue(workers.awaitTermination(5,TimeUnit.SECONDS));}
        finally {workers.shutdownNow();}
        assertEquals(0,b.reservedBytes());assertEquals(1,wakes.get());
    }
    @Test void failingWakeupCannotCorruptAccountingOrPreventFurtherAdmission() {
        var b=new ChartOutboundBudget(100,100,2,1,()->{throw new AssertionError("fixture");});var a=b.attach();
        a.tryAcquire(100).close();assertEquals(0,b.reservedBytes());var again=a.tryAcquire(100);assertNotNull(again);again.close();
    }
    @Test void wakeupCanReenterAfterAccountingHasBeenReleased() {
        var owner=new AtomicReference<ChartOutboundBudget.Owner>();var acquired=new AtomicReference<ChartOutboundBudget.Lease>();
        var b=new ChartOutboundBudget(10,10,1,1,()->acquired.set(owner.get().tryAcquire(10)));owner.set(b.attach());
        var first=owner.get().tryAcquire(10);assertNotNull(first);first.close();assertNotNull(acquired.get());assertEquals(10,b.reservedBytes());
        var last=acquired.get();b.close();last.close();assertEquals(0,b.reservedBytes());
    }
}

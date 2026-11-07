package app.feedgateway;

import static org.junit.jupiter.api.Assertions.*;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class OrderChartMemoryBudgetTest {
    @Test void independentCategoriesCannotBorrowCapacityOrEraseAnInflightCharge(){
        var wakes=new AtomicInteger();var budget=new OrderChartMemoryBudget(wakes::incrementAndGet);
        var owners=new ArrayList<OrderChartMemoryBudget.Reservation>();
        for(var c:OrderChartMemoryBudget.Category.values()){
            var owner=budget.reserve(c,c.ceiling);assertNotNull(owner);owners.add(owner);assertNull(budget.reserve(c,1));assertEquals(c.ceiling,budget.reserved(c));
        }
        budget.close();assertEquals(3,budget.reservations());
        for(var c:OrderChartMemoryBudget.Category.values()){assertNull(budget.reserve(c,1));assertEquals(c.ceiling,budget.reserved(c));}
        owners.forEach(o->{o.close();o.close();});assertEquals(0,budget.reservations());assertEquals(3,wakes.get());
        for(var c:OrderChartMemoryBudget.Category.values())assertEquals(0,budget.reserved(c));
    }
    @Test void preparationGrowthRefusalAndShrinkPreserveAccounting(){
        var budget=new OrderChartMemoryBudget(()->{});var c=OrderChartMemoryBudget.Category.SNAPSHOT;
        var pinned=budget.reserve(c,c.ceiling-100);var preparing=budget.reserve(c,100);assertNotNull(pinned);assertNotNull(preparing);
        assertFalse(preparing.resize(101));assertEquals(100,preparing.bytes());assertEquals(c.ceiling,budget.reserved(c));
        assertTrue(pinned.resize(c.ceiling-200));assertTrue(preparing.resize(200));assertEquals(c.ceiling,budget.reserved(c));
        pinned.close();preparing.close();assertEquals(0,budget.reserved(c));assertFalse(preparing.resize(1));
    }
    @Test void concurrentPreparationNeverExceedsTheCategoryAndReleasesOnce()throws Exception{
        var wakes=new AtomicInteger();var budget=new OrderChartMemoryBudget(wakes::incrementAndGet);var c=OrderChartMemoryBudget.Category.PENDING;
        int n=16;var start=new CountDownLatch(1);var admitted=new CountDownLatch(n);var release=new CountDownLatch(1);var count=new AtomicInteger();
        try(var pool=Executors.newFixedThreadPool(n)){
            var futures=new ArrayList<java.util.concurrent.Future<?>>();
            for(int i=0;i<n;i++)futures.add(pool.submit(()->{try{if(!start.await(5,java.util.concurrent.TimeUnit.SECONDS))throw new IllegalStateException("start deadline");var lease=budget.reserve(c,c.ceiling/4);if(lease!=null)count.incrementAndGet();admitted.countDown();if(!release.await(5,java.util.concurrent.TimeUnit.SECONDS))throw new IllegalStateException("release deadline");if(lease!=null){lease.close();lease.close();}}catch(InterruptedException e){Thread.currentThread().interrupt();throw new RuntimeException(e);}}));
            start.countDown();assertTrue(admitted.await(5,java.util.concurrent.TimeUnit.SECONDS));assertEquals(4,count.get());assertEquals(c.ceiling,budget.reserved(c));release.countDown();for(var f:futures)f.get(5,java.util.concurrent.TimeUnit.SECONDS);
        }finally{release.countDown();}
        assertEquals(0,budget.reserved(c));assertEquals(4,wakes.get());
    }
    @Test void reservationObjectsAndInvalidRequestsAreBounded(){
        var budget=new OrderChartMemoryBudget(()->{throw new IllegalStateException("wake failure");});var c=OrderChartMemoryBudget.Category.SOURCE;
        assertNull(budget.reserve(c,0));assertNull(budget.reserve(c,-1));assertNull(budget.reserve(c,Long.MAX_VALUE));
        var owners=new ArrayList<OrderChartMemoryBudget.Reservation>();for(int i=0;i<OrderChartMemoryBudget.MAX_RESERVATIONS;i++)owners.add(budget.reserve(c,1));
        assertTrue(owners.stream().allMatch(java.util.Objects::nonNull));assertNull(budget.reserve(c,1));owners.forEach(OrderChartMemoryBudget.Reservation::close);assertEquals(0,budget.reservations());assertEquals(0,budget.reserved(c));
    }
}

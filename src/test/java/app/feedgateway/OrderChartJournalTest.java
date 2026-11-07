package app.feedgateway;

import static org.junit.jupiter.api.Assertions.*;
import java.util.ArrayList;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;

class OrderChartJournalTest {
    @Test void contiguousReplayOwnsItsPayloadAndRefusesBadCursors(){
        var journal=new OrderChartJournal();var scope=journal.attach();byte[] wire={4,5};
        assertFalse(scope.append(2,wire));assertTrue(scope.append(1,wire));wire[0]=9;
        assertFalse(scope.append(1,wire));assertFalse(scope.append(3,wire));
        var pins=scope.pinAfter(0);assertEquals(1,pins.size());byte[] received=new byte[2];pins.getFirst().copyTo(received,0);assertArrayEquals(new byte[]{4,5},received);
        assertEquals(1,pins.getFirst().revision());assertNull(scope.pinAfter(-1));assertNull(scope.pinAfter(2));assertTrue(scope.pinAfter(1).isEmpty());
        pins.getFirst().close();assertThrows(IllegalStateException.class,()->pins.getFirst().size());scope.close();assertEquals(0,journal.reservedBytes());
    }
    @Test void evictedButPinnedEntriesRetainTheirChargeAndCreateAnExplicitCursorGap(){
        var journal=new OrderChartJournal();var scope=journal.attach();var pins=new ArrayList<OrderChartJournal.Pin>();
        for(int n=1;n<=128;n++){assertTrue(scope.append(n,new byte[1]));if(n==1)pins.addAll(scope.pinAfter(0));}
        long before=journal.reservedBytes();assertTrue(scope.append(129,new byte[1]));
        assertEquals(before,journal.reservedBytes());assertNull(scope.pinAfter(0));assertNull(scope.pinAfter(1));
        var remaining=scope.pinAfter(2);assertEquals(127,remaining.size());remaining.forEach(OrderChartJournal.Pin::close);
        // First entry is retired but still belongs to the delivery pin; scope close cannot forgive it.
        scope.close();assertEquals(257,journal.reservedBytes());assertEquals(1,journal.retainedScopes());
        byte[] first=new byte[1];pins.getFirst().copyTo(first,0);pins.getFirst().close();pins.getFirst().close();assertEquals(0,journal.reservedBytes());assertEquals(0,journal.retainedScopes());
    }
    @Test void allPinnedJournalRefusesWithoutChangingRevisionOrReplay(){
        var journal=new OrderChartJournal();var scope=journal.attach();
        for(int n=1;n<=128;n++)assertTrue(scope.append(n,new byte[1]));
        var pins=scope.pinAfter(0);assertFalse(scope.append(129,new byte[1]));assertEquals(128,scope.revision());var secondPins=scope.pinAfter(0);assertEquals(128,secondPins.size());
        // Release both independent recipient pin sets before teardown.
        journal.close();assertEquals(128*257,journal.reservedBytes());pins.forEach(OrderChartJournal.Pin::close);
        assertEquals(128*257,journal.reservedBytes());secondPins.forEach(OrderChartJournal.Pin::close);assertEquals(0,journal.reservedBytes());
    }
    @Test void globalBudgetAndScopeBytesCannotBeBypassedByDetach(){
        var journal=new OrderChartJournal(1024);var first=journal.attach();var second=journal.attach();
        assertTrue(first.append(1,new byte[512]));var pins=first.pinAfter(0);first.close();assertFalse(second.append(1,new byte[1]));
        pins.forEach(OrderChartJournal.Pin::close);assertTrue(second.append(1,new byte[1]));journal.close();assertEquals(0,journal.reservedBytes());
        var full=new OrderChartJournal();var scope=full.attach();assertFalse(scope.append(1,new byte[OrderChartJournal.SCOPE_BYTES]));assertTrue(scope.append(1,new byte[OrderChartJournal.SCOPE_BYTES-256]));assertTrue(scope.append(2,new byte[1]));assertEquals(257,scope.reservedBytes());
    }
    @Test void detachedPinnedScopesOccupyTheSameThirtyOwnerLimit(){
        var journal=new OrderChartJournal();var pins=new ArrayList<OrderChartJournal.Pin>();
        for(int n=0;n<30;n++){var scope=journal.attach();assertNotNull(scope);assertTrue(scope.append(1,new byte[1]));pins.addAll(scope.pinAfter(0));scope.close();}
        assertNull(journal.attach());pins.removeFirst().close();assertNotNull(journal.attach());journal.close();pins.forEach(OrderChartJournal.Pin::close);assertEquals(0,journal.reservedBytes());assertEquals(0,journal.retainedScopes());
    }
    @Test void concurrentDuplicatePinReleaseDoesNotUnderflow()throws Exception{
        var journal=new OrderChartJournal();var scope=journal.attach();assertTrue(scope.append(1,new byte[4]));var pin=scope.pinAfter(0).getFirst();journal.close();
        var workers=Executors.newFixedThreadPool(4);try{for(int n=0;n<100;n++)workers.submit(pin::close);workers.shutdown();assertTrue(workers.awaitTermination(5,TimeUnit.SECONDS));}finally{workers.shutdownNow();}
        assertEquals(0,journal.reservedBytes());assertEquals(0,journal.retainedScopes());assertNull(scope.pinAfter(0));assertFalse(scope.append(2,new byte[1]));
    }
    @Test void recipientPinMetadataHasItsOwnGlobalBootstrapCapacity(){
        var journal=new OrderChartJournal();var scope=journal.attach();for(int n=1;n<=128;n++)assertTrue(scope.append(n,new byte[1]));
        var pins=new ArrayList<OrderChartJournal.Pin>();for(int n=0;n<512;n++)pins.addAll(scope.pinAfter(0));
        assertEquals(OrderChartJournal.PIN_BYTES,journal.reservedPinBytes());assertNull(scope.pinAfter(0));
        // Empty current-cursor reads need no pin allocation even at capacity.
        assertTrue(scope.pinAfter(128).isEmpty());journal.close();pins.forEach(OrderChartJournal.Pin::close);
        assertEquals(0,journal.reservedPinBytes());assertEquals(0,journal.reservedBytes());
    }

    @Test void nonreplayablePublicationAdvancesTheCursorAndNeverReturnsAnIncompleteReplay(){
        var journal=new OrderChartJournal();var scope=journal.attach();assertTrue(scope.append(1,new byte[1]));var pin=scope.pinAfter(0).getFirst();
        assertFalse(scope.nonreplayable(3));assertTrue(scope.nonreplayable(2));assertNull(scope.pinAfter(0));assertNull(scope.pinAfter(1));assertTrue(scope.pinAfter(2).isEmpty());
        assertEquals(257,journal.reservedBytes());assertTrue(scope.append(3,new byte[1]));assertNull(scope.pinAfter(1));var next=scope.pinAfter(2);assertEquals(3,next.getFirst().revision());
        journal.close();pin.close();next.forEach(OrderChartJournal.Pin::close);assertEquals(0,journal.reservedBytes());assertEquals(0,journal.reservedPinBytes());
    }

}

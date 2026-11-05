package app.feedgateway;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.Test;

class DepthUnitJournalTest {
    @Test void replayPreservesEveryUnitAndOrdersHeartbeatAfterItsK() {
        var j=new DepthUnitJournal();
        j.append(1,100,false,bytes("snapshot"));j.append(1,110,true,bytes("healthy"));
        j.append(2,120,false,bytes("remove"));j.append(3,130,false,bytes("refill"));
        var page=j.after(1,100,256,4096);
        assertEquals(List.of(1L,2L,3L),page.stream().map(DepthUnitJournal.Item::K).toList());
        assertTrue(page.getFirst().heartbeat());assertArrayEquals(bytes("remove"),page.get(1).datum());
        assertEquals(2,j.after(1,110,256,4096).size());
    }
    @Test void sourceGapsAndRetimedHeartbeatsNeverBecomeContiguousJournal() {
        var j=new DepthUnitJournal();j.append(4,100,false,bytes("checkpoint"));
        assertThrows(IllegalArgumentException.class,()->j.append(6,110,false,bytes("gap")));
        assertThrows(IllegalArgumentException.class,()->j.append(5,110,true,bytes("ahead")));
        assertEquals(4,j.lastK());assertTrue(j.canResume(3));assertFalse(j.canResume(2));
    }
    @Test void compressedBlocksRoundTripAcrossBoundariesAndPagination() {
        var j=new DepthUnitJournal();
        for(int k=1;k<=1000;k++)j.append(k,k*10L,false,bytes("source-"+k+"x".repeat(200)));
        long cursor=0;int count=0;
        while(cursor<1000){var page=j.after(cursor,0,33,10000);assertFalse(page.isEmpty());for(var row:page){assertEquals(++count,row.K());assertArrayEquals(bytes("source-"+count+"x".repeat(200)),row.datum());}cursor=page.getLast().K();}
        assertEquals(1000,count);assertTrue(j.bytes()<300000);
    }
    @Test void incompressibleOverflowEvictsPastWithinCapRatherThanChangingSourceIdentity() {
        long cap=DepthUnitJournal.MAX_UNIT+DepthUnitJournal.BLOCK_TARGET;
        var j=new DepthUnitJournal(cap);var random=new Random(3);
        for(int k=1;k<=100;k++){byte[] b=new byte[32768];random.nextBytes(b);j.append(k,k,false,b);}
        assertTrue(j.bytes()<=cap);assertTrue(j.evictions()>0);assertEquals(100,j.lastK());assertFalse(j.canResume(0));
        assertEquals(1,j.after(99,0,1,40000).size());
    }
    @Test void callerCannotGrowPageOrReturnPartialRecordOnCapacityFailure() {
        var j=new DepthUnitJournal();j.append(1,1,false,new byte[100]);
        assertTrue(j.after(0,0,1,99).isEmpty());assertTrue(j.after(0,0,257,4096).isEmpty());
        assertThrows(IllegalArgumentException.class,()->j.append(2,2,false,new byte[DepthUnitJournal.MAX_UNIT+1]));
    }
    @Test void watermarkRegressionCannotPoisonResumeMetadata() {
        var j=new DepthUnitJournal();j.append(1,100,false,bytes("checkpoint"));
        assertThrows(IllegalArgumentException.class,()->j.append(1,99,true,bytes("old heartbeat")));
        assertThrows(IllegalArgumentException.class,()->j.append(2,99,false,bytes("old unit")));
        assertEquals(1,j.lastK());assertEquals(100,j.earliestWatermark());
    }
    @Test void oversizedIncompressibleBlockEvictionCannotAdvertiseLostUnitsAsResumable() {
        var j=new DepthUnitJournal(DepthUnitJournal.MAX_UNIT+DepthUnitJournal.BLOCK_TARGET);
        byte[] random=new byte[DepthUnitJournal.MAX_UNIT];new Random(42).nextBytes(random);
        j.append(40,100,false,random);
        assertEquals(1,j.evictions());assertTrue(j.canResume(40));assertFalse(j.canResume(39));
        assertTrue(j.after(39,0,1,DepthUnitJournal.MAX_UNIT).isEmpty());
    }
    private static byte[] bytes(String text){return text.getBytes(StandardCharsets.UTF_8);}
}

package app.feedgateway;

import com.optionsedge.contracts.rithmic.RithmicSchemas;
import java.util.ArrayList;
import java.util.Map;
import org.apache.avro.generic.GenericRecord;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;

/** Synthetic volatile normal-density qualification, not an actual captured market window. */
class RithmicNativeHistoryDensityTest {
    @Test void oneSharedCacheRetainsAndPagesTheWholeVolatileTwoHourNormalWindow()throws Exception{
        long now=RithmicDepthJsonTest.NOW,from=now-RithmicDepthHistory.WINDOW,started=System.nanoTime(),peakUsed=0;
        var h=RithmicNativeHistoryCacheTest.history();
        for(int i=0;i<=7200;i++){
            long time=from+i*1000L,basis=i%25001;var r=RithmicDepthHistoryTest.frame(time,i+1);r.put("basisCents",basis);var bids=new ArrayList<GenericRecord>();var asks=new ArrayList<GenericRecord>();
            for(int j=0;j<770;j++){
                var bid=RithmicDepthJsonTest.level(30360-j);bid.put("spxPriceCents",(30360L-j)*25-basis);bid.put("quantity",1L+(i*1540L+j)%10000);bids.add(bid);
                var ask=RithmicDepthJsonTest.level(30361+j);ask.put("spxPriceCents",(30361L+j)*25-basis);ask.put("quantity",1L+(i*1540L+770+j)%10000);asks.add(ask);
            }
            r.put("bids",bids);r.put("asks",asks);h.accept(RithmicSchemas.KEY,r,now);
            if(i%32==0)peakUsed=Math.max(peakUsed,Runtime.getRuntime().totalMemory()-Runtime.getRuntime().freeMemory());
        }
        @SuppressWarnings("unchecked") var columns=(Map<Long,RithmicNativeHistoryColumn>)ReflectionTestUtils.getField(h,"columns");
        assertNotNull(columns);assertEquals(7201,columns.size());assertTrue(columns.values().stream().allMatch(RithmicNativeHistoryColumn::book));
        var sizes=columns.values().stream().mapToInt(RithmicNativeHistoryColumn::retainedBytes).sorted().toArray();long retained=java.util.Arrays.stream(sizes).asLongStream().sum();assertTrue(retained<=128L*1024*1024);assertTrue(retained>64L*1024*1024,"The fixture must exceed the previous source-cache byte cap");
        long cursor=from-1,wireBytes=0;int pages=0,count=0;boolean done=false;
        while(!done){
            var response=h.nativePage("density-"+pages,RithmicNativeHistoryCacheTest.request(from,now,cursor),now,524288);assertTrue(response.accepted(),response.error());assertTrue(response.binary().length<=524288);wireBytes+=response.binary().length;var header=RithmicNativeHistoryPacketTest.header(response.binary());assertFalse(header.path("limited").asBoolean());assertFalse(header.path("coverageUncertain").asBoolean());assertEquals(from,header.path("availableFromMs").asLong());assertEquals(cursor,header.path("pageFromExclusiveMs").asLong());assertTrue(header.path("afterMs").asLong()>cursor);cursor=header.path("afterMs").asLong();count+=header.path("columns").size();done=header.path("done").asBoolean();assertTrue(++pages<=300);
        }
        assertEquals(7201,count);assertEquals(now,cursor);
        System.out.println("RITHMIC_NATIVE_DENSITY {\"columns\":"+count+",\"levelsPerColumn\":1540,\"spanMs\":7200000,\"retainedBytes\":"+retained+",\"meanColumnBytes\":"+(retained/7201.0)+",\"p95ColumnBytes\":"+sizes[(int)(sizes.length*.95)]+",\"maxColumnBytes\":"+sizes[sizes.length-1]+",\"greedyPages\":"+pages+",\"wireBytes\":"+wireBytes+",\"elapsedMs\":"+((System.nanoTime()-started)/1000000.0)+",\"sampledPeakUsedHeapBytes\":"+peakUsed+",\"maxHeapBytes\":"+Runtime.getRuntime().maxMemory()+"}");
    }
}

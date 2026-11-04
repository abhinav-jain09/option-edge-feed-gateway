package app.feedgateway;

import com.optionsedge.contracts.rithmic.RithmicSchemas;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.apache.avro.generic.GenericRecord;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RithmicDepthRelayTest {
    static final long NOW=RithmicDepthJsonTest.NOW;
    final FeedGatewayService gateway=mock(FeedGatewayService.class);
    @SuppressWarnings("unchecked")
    final ObjectProvider<FeedGatewayService> provider=mock(ObjectProvider.class);
    final RithmicDepthRelay relay;
    RithmicDepthRelayTest(){when(provider.getObject()).thenReturn(gateway);relay=new RithmicDepthRelay(mock(GatewaySettings.class),provider);}
    GenericRecord frame(long sequence,long time){
        var r=RithmicDepthJsonTest.frame();r.put("sequence",sequence);r.put("receivedAtMs",time);r.put("emittedAtMs",time);
        r.put("basisEventTimeMs",time);r.put("basisMeasuredAtMs",time);
        r.put("bids",java.util.stream.LongStream.range(0,100).mapToObj(i->RithmicDepthJsonTest.level(30360-i)).toList());
        r.put("asks",java.util.stream.LongStream.range(0,100).mapToObj(i->RithmicDepthJsonTest.level(30361+i)).toList());return r;
    }
    void accept(GenericRecord r,long now){relay.accept(RithmicSchemas.KEY,r,now);}
    List<String> sent(int count){var c=ArgumentCaptor.forClass(String.class);verify(gateway,times(count)).streamRithmicDepth(c.capture());return c.getAllValues();}
    @Test void burstPublishesFourBooksPerSecondAndFlushesNewestCompleteBook() throws Exception {
        for(int i=0;i<20;i++)accept(frame(i+1,NOW+i*50),NOW+i*50);
        relay.flush(NOW+999);verify(gateway,times(4)).streamRithmicDepth(anyString());
        relay.flush(NOW+1000);
        var frames=sent(5);var json=new ObjectMapper();
        assertEquals(List.of(1L,6L,11L,16L,20L),frames.stream().map(s->{try{return json.readTree(s).path("sequence").longValue();}catch(Exception e){throw new RuntimeException(e);}}).toList());
        assertEquals(RithmicDepthJson.render(RithmicSchemas.KEY,frame(20,NOW+950),NOW+1000),frames.getLast());
        relay.flush(NOW+2000);verifyNoMoreInteractions(gateway);
    }
    @Test void outageAndRecoveryAreImmediateAndSupersedePendingBooks() throws Exception {
        accept(frame(1,NOW),NOW);accept(frame(2,NOW+10),NOW+10);
        var outage=frame(3,NOW+20);outage.put("mappingReady",false);outage.put("reason","SOURCE_STALE");outage.put("bids",List.of());outage.put("asks",List.of());
        accept(outage,NOW+20);accept(frame(4,NOW+30),NOW+30);relay.flush(NOW+1000);
        var json=new ObjectMapper();assertEquals(List.of(1L,3L,4L),sent(3).stream().map(s->{try{return json.readTree(s).path("sequence").longValue();}catch(Exception e){throw new RuntimeException(e);}}).toList());
    }
    @Test void streamChangeIsImmediateAndOldSequenceCannotReplacePendingBook(){
        accept(frame(10,NOW),NOW);accept(frame(12,NOW+10),NOW+10);accept(frame(11,NOW+20),NOW+20);
        relay.flush(NOW+250);var next=frame(1,NOW+260);next.put("streamId","new-source");accept(next,NOW+260);
        var values=sent(3);assertTrue(values.get(1).contains("\"sequence\":12"));assertTrue(values.get(2).contains("new-source"));
    }
    @Test void reconnectReplaysLatestPendingBookWithoutWaitingForCadence(){
        long now=System.currentTimeMillis();accept(frame(1,now),now);accept(frame(2,now+1),now+1);
        var replay=new java.util.ArrayList<String>();relay.replay(replay::add);
        assertEquals(List.of(RithmicDepthJson.render(RithmicSchemas.KEY,frame(2,now+1),now+1)),replay);sent(1);
    }
    @Test void malformedAndExpiredPendingBooksAreNeverPublished(){
        accept(frame(1,NOW),NOW);accept(frame(2,NOW+10),NOW+10);
        var malformed=frame(3,NOW+20);malformed.put("contract","MESZ6");accept(malformed,NOW+20);
        relay.flush(NOW+10011);sent(1);
    }
}

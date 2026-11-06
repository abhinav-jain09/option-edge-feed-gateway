package app.feedgateway;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RithmicNativeHistoryPacketTest {
    static final long NOW=RithmicDepthJsonTest.NOW;
    static final ObjectMapper JSON=new ObjectMapper();
    static final RithmicNativeHistoryRequest.Scope TEST=new RithmicNativeHistoryRequest.Scope("Rithmic Test","CME","ESZ6");
    static RithmicNativeHistoryRequest.Request request(){return new RithmicNativeHistoryRequest.Request("java-golden",TEST,NOW-1000,NOW,NOW-1001);}
    static RithmicNativeHistoryColumn book(long at)throws Exception{
        var raw=RithmicDepthHistoryTest.frame(at,1);return RithmicNativeHistoryColumn.capture(RithmicDepthJson.validate(com.optionsedge.contracts.rithmic.RithmicSchemas.KEY,raw,at),at,null,null);
    }
    static RithmicNativeHistoryPacket.Snapshot snapshot(List<RithmicNativeHistoryColumn> columns,String state,Long covered,List<RithmicNativeHistoryPacket.Gap> gaps){
        return new RithmicNativeHistoryPacket.Snapshot(columns,state,covered,NOW-1000,NOW-1000,false,List.of(),"java-cache",0,gaps);
    }
    static JsonNode header(byte[] bytes)throws Exception{var buffer=ByteBuffer.wrap(bytes);assertEquals(0x524c4631,buffer.getInt());int size=buffer.getInt();assertTrue(size<=16384);byte[] json=new byte[size];buffer.get(json);return JSON.readTree(json);}
    @Test void independentlyGeneratedGoldenPacketContainsExactCompleteIngestPayload()throws Exception{
        var column=book(NOW);byte[] packet=RithmicNativeHistoryPacket.build(request(),snapshot(List.of(column),"READY",NOW,List.of()),524288).binary();assertNotNull(packet);
        var h=header(packet);assertTrue(h.path("done").asBoolean());assertEquals(NOW,h.path("afterMs").asLong());assertEquals(3,h.path("namespace").size());assertFalse(h.path("namespace").has("subject"));assertFalse(h.has("subject"));
        int offset=8+ByteBuffer.wrap(packet).getInt(4);byte[] compressed=java.util.Arrays.copyOfRange(packet,offset,packet.length);var body=JSON.readTree(RithmicDepthHistory.expand(compressed));assertEquals(1,body.path("bids").size());assertEquals(15,body.path("bids").get(0).path("quantity").asInt());assertEquals(column.nativeBytes(),compressed.length);
        System.out.println("RLF1_JAVA_GOLDEN "+java.util.HexFormat.of().formatHex(packet));
    }
    @Test void nativeGapHasNoBodyAndTheReasonIsMandatory()throws Exception{
        var gap=RithmicNativeHistoryColumn.gap(NOW,TEST,RithmicDepthJson.GapReason.STREAM_CHANGE,NOW-100);
        byte[] packet=RithmicNativeHistoryPacket.build(request(),snapshot(List.of(gap),"READY",NOW,List.of()),524288).binary();var h=header(packet);assertEquals(0,h.path("columns").get(0).path("compressedBytes").asInt());assertEquals("STREAM_CHANGE",h.path("columns").get(0).path("gapReason").asText());assertEquals(8+ByteBuffer.wrap(packet).getInt(4),packet.length);
        assertThrows(IllegalArgumentException.class,()->RithmicNativeHistoryColumn.gap(NOW,TEST,null,null));assertThrows(IllegalArgumentException.class,()->RithmicNativeHistoryColumn.gap(NOW,TEST,RithmicDepthJson.GapReason.STREAM_CHANGE,NOW));
    }
    @Test void unavailableIsTextControlAndWarmingNeverReturnsCachedBookColumns()throws Exception{
        assertEquals("unavailable",RithmicNativeHistoryPacket.build(request(),snapshot(List.of(),"UNAVAILABLE",null,List.of()),524288).error());
        var h=header(RithmicNativeHistoryPacket.build(request(),snapshot(List.of(book(NOW)),"LOADING",NOW,List.of()),524288).binary());assertFalse(h.path("ready").asBoolean());assertFalse(h.path("done").asBoolean());assertFalse(h.path("waitingForSource").asBoolean());assertEquals(0,h.path("columns").size());assertEquals(request().afterMs(),h.path("afterMs").asLong());
    }
    @Test void doneRequiresSnapshotCoverageAndWaitingNeverAdvancesTheCursor()throws Exception{
        var h=header(RithmicNativeHistoryPacket.build(request(),snapshot(List.of(),"READY",NOW-1,List.of()),524288).binary());assertFalse(h.path("done").asBoolean());assertTrue(h.path("waitingForSource").asBoolean());assertEquals(request().afterMs(),h.path("afterMs").asLong());
        h=header(RithmicNativeHistoryPacket.build(request(),snapshot(List.of(book(NOW-1)),"READY",NOW-1,List.of()),524288).binary());assertFalse(h.path("done").asBoolean());assertFalse(h.path("waitingForSource").asBoolean());assertEquals(NOW-1,h.path("afterMs").asLong());
    }
    @Test void oneLookaheadAndThirtyTwoColumnBoundPreserveTheNextEmittedKey()throws Exception{
        var columns=new ArrayList<RithmicNativeHistoryColumn>();for(int i=0;i<33;i++)columns.add(book(NOW-1000+i));
        var h=header(RithmicNativeHistoryPacket.build(request(),snapshot(columns,"READY",NOW,List.of()),524288).binary());assertEquals(32,h.path("columns").size());assertEquals(NOW-969,h.path("afterMs").asLong());assertFalse(h.path("done").asBoolean());
    }
    @Test void scopeOrderAndDuplicateKeysCannotBeRelabelledIntoRequestedHistory()throws Exception{
        var paper=RithmicDepthJsonTest.frame();paper.put("environment","Rithmic Paper Trading");var foreign=RithmicNativeHistoryColumn.capture(RithmicDepthJson.validate(com.optionsedge.contracts.rithmic.RithmicSchemas.KEY,paper,NOW),NOW,null,null);
        for(var columns:List.of(List.of(foreign),List.of(book(NOW),book(NOW)),List.of(book(NOW),book(NOW-1))))assertEquals("invalid_source",RithmicNativeHistoryPacket.build(request(),snapshot(columns,"READY",NOW,List.of()),524288).error());
    }
    @Test void sourceIntervalsTooLargeForHeaderBecomeExplicitStickyUncertainty()throws Exception{
        var gaps=new ArrayList<RithmicNativeHistoryPacket.Gap>();for(int i=0;i<500;i++)gaps.add(new RithmicNativeHistoryPacket.Gap(NOW-1000+i*2,NOW-999+i*2,RithmicDepthJson.GapReason.INVALID_RECORD));
        var h=header(RithmicNativeHistoryPacket.build(request(),snapshot(List.of(book(NOW)),"READY",NOW,gaps),524288).binary());assertTrue(h.path("limited").asBoolean());assertTrue(h.path("coverageUncertain").asBoolean());assertFalse(h.has("sourceGapIntervals"));assertTrue(h.path("done").asBoolean());
    }
    @Test void legacyPayloadOwnershipCannotMutateThePrivateNativeColumn()throws Exception{
        var column=book(NOW);byte[] leaked=column.legacyBytes();leaked[0]=0;byte[] packet=RithmicNativeHistoryPacket.build(request(),snapshot(List.of(column),"READY",NOW,List.of()),524288).binary();assertNotNull(packet);int offset=8+ByteBuffer.wrap(packet).getInt(4);assertEquals(31,packet[offset]&255);assertNotNull(JSON.readTree(RithmicDepthHistory.expand(java.util.Arrays.copyOfRange(packet,offset,packet.length))));
    }
    @Test void unsupportedPacketBudgetReturnsCapacityWithoutInventingASourceGap()throws Exception{
        var snapshot=snapshot(List.of(book(NOW)),"READY",NOW,List.of());var result=RithmicNativeHistoryPacket.build(request(),snapshot,32767);assertEquals("capacity",result.error());assertNull(result.binary());assertTrue(snapshot.columns().getFirst().book());
    }
    @Test void aValidColumnThatDoesNotFitSmallerSessionBudgetIsNotReclassified()throws Exception{
        var raw=RithmicDepthJsonTest.frame();var bids=new ArrayList<org.apache.avro.generic.GenericRecord>();var asks=new ArrayList<org.apache.avro.generic.GenericRecord>();
        for(int i=0;i<3500;i++){var b=RithmicDepthJsonTest.level(30360-i);b.put("quantity",1L+(i*797L)%997);bids.add(b);var a=RithmicDepthJsonTest.level(30361+i);a.put("quantity",1L+(i*991L)%997);asks.add(a);}raw.put("bids",bids);raw.put("asks",asks);
        var checked=RithmicDepthJson.validate(com.optionsedge.contracts.rithmic.RithmicSchemas.KEY,raw,NOW);assertTrue(checked.fullBook());var column=RithmicNativeHistoryColumn.capture(checked,NOW,null,null);assertTrue(column.nativeBytes()>32768);assertTrue(column.book());
        assertEquals("capacity",RithmicNativeHistoryPacket.build(request(),snapshot(List.of(column),"READY",NOW,List.of()),32768).error());assertTrue(column.book());
    }
}

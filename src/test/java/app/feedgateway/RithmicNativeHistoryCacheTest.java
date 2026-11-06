package app.feedgateway;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.optionsedge.contracts.rithmic.RithmicSchemas;
import java.nio.ByteBuffer;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RithmicNativeHistoryCacheTest {
    static final long NOW=RithmicDepthJsonTest.NOW;
    static final ObjectMapper JSON=new ObjectMapper();
    static RithmicDepthHistory history(){var settings=spy(new GatewaySettings());doReturn("Rithmic Test").when(settings).rithmicSystem();var h=new RithmicDepthHistory(settings);ReflectionTestUtils.setField(h,"ready",true);ReflectionTestUtils.setField(h,"sourceState","READY");return h;}
    static RithmicNativeHistoryRequest.Request request(long from,long to,long after){return new RithmicNativeHistoryRequest.Request("native",RithmicNativeHistoryPacketTest.TEST,from,to,after);}
    static JsonNode header(RithmicDepthHistory h,String socket,RithmicNativeHistoryRequest.Request request,long now)throws Exception{var packet=h.nativePage(socket,request,now,524288);assertTrue(packet.accepted(),packet.error());return RithmicNativeHistoryPacketTest.header(packet.binary());}
    @Test void invalidToValidRecoveryWithinOneBucketCannotBeSuppressed()throws Exception{
        var h=history();var invalid=RithmicDepthHistoryTest.frame(NOW,1);invalid.put("bids",List.of());h.accept(RithmicSchemas.KEY,invalid,NOW);h.accept(RithmicSchemas.KEY,RithmicDepthHistoryTest.frame(NOW+1,2),NOW+1);
        var result=header(h,"s",request(NOW-1000,NOW+1,NOW-1001),NOW+1);assertEquals(2,result.path("columns").size());assertEquals("SOURCE_GAP",result.path("columns").get(0).path("kind").asText());assertEquals("EMPTY_BOOK",result.path("columns").get(0).path("gapReason").asText());assertEquals("BOOK",result.path("columns").get(1).path("kind").asText());
    }
    @Test void identicalDuplicatesDoNotInvalidateButCorrectionsAndLateInsertionsDo()throws Exception{
        var h=history();var first=RithmicDepthHistoryTest.frame(NOW,1);h.accept(RithmicSchemas.KEY,first,NOW);h.accept(RithmicSchemas.KEY,first,NOW);var req=request(NOW-2000,NOW,NOW-2001);assertEquals(0,header(h,"a",req,NOW).path("lateRevision").asLong());
        var corrected=RithmicDepthHistoryTest.frame(NOW,2);h.accept(RithmicSchemas.KEY,corrected,NOW);assertEquals(1,header(h,"b",req,NOW).path("lateRevision").asLong());h.accept(RithmicSchemas.KEY,corrected,NOW);assertEquals(1,header(h,"c",req,NOW).path("lateRevision").asLong());
        h.accept(RithmicSchemas.KEY,RithmicDepthHistoryTest.frame(NOW-1000,3),NOW);assertEquals(2,header(h,"d",req,NOW).path("lateRevision").asLong());
    }
    @Test void syntheticStreamBoundaryNeverOverwritesAnExistingRealBook()throws Exception{
        var h=history();h.accept(RithmicSchemas.KEY,RithmicDepthHistoryTest.frame(NOW-1,1),NOW);var next=RithmicDepthHistoryTest.frame(NOW,2);next.put("streamId","replacement");h.accept(RithmicSchemas.KEY,next,NOW);
        var result=header(h,"s",request(NOW-1000,NOW,NOW-1001),NOW);assertEquals(2,result.path("columns").size());assertEquals("BOOK",result.path("columns").get(0).path("kind").asText());assertEquals("BOOK",result.path("columns").get(1).path("kind").asText());assertEquals(NOW-1,result.path("columns").get(1).path("breakBeforeMs").asLong());
        var legacy=JSON.readTree(h.page("legacy",RithmicDepthHistoryTest.request(NOW-1000,NOW,NOW-1001),NOW,524288));assertEquals(2,legacy.path("frames").size());assertFalse(legacy.path("frames").get(0).has("gapAtMs"));
    }
    @Test void unknownScopeBecomesABoundedGapAndCannotAcquireBookProvenance()throws Exception{
        var h=history();var raw=RithmicDepthHistoryTest.frame(NOW,1);raw.put("environment","unrecognised input scope");h.accept(RithmicSchemas.KEY,raw,NOW);var result=header(h,"s",request(NOW-1000,NOW,NOW-1001),NOW);assertEquals("SOURCE_GAP",result.path("columns").get(0).path("kind").asText());assertFalse(result.toString().contains("unrecognised"));
    }
    @Test void nativeAndLegacyThrottleStateIsIndependentAndSocketRemovalReleasesBoth()throws Exception{
        var h=history();h.accept(RithmicSchemas.KEY,RithmicDepthHistoryTest.frame(NOW,1),NOW);var req=request(NOW-1000,NOW,NOW-1001);
        assertTrue(h.nativePage("s",req,NOW,524288).accepted());assertFalse(JSON.readTree(h.page("s",RithmicDepthHistoryTest.request(NOW-1000,NOW,NOW-1001),NOW,524288)).has("error"));assertEquals("busy",h.nativePage("s",req,NOW+49,524288).error());assertTrue(h.nativePage("s",req,NOW+50,524288).accepted());h.forget("s");assertTrue(h.nativePage("s",req,NOW+50,524288).accepted());
    }
    @Test void optInSocketMetadataIsBoundedWithoutEvictingAnExistingOwner()throws Exception{
        var h=history();var req=request(NOW-1000,NOW,NOW-1001);for(int i=0;i<1024;i++)assertTrue(h.nativePage("s"+i,req,NOW,524288).accepted());assertEquals("busy",h.nativePage("overflow",req,NOW,524288).error());h.forget("s0");assertTrue(h.nativePage("overflow",req,NOW,524288).accepted());
    }
    @Test void foreignNamespaceAndUnavailableMetadataCannotReturnBinary()throws Exception{
        var h=history();var foreign=new RithmicNativeHistoryRequest.Request("wrong",new RithmicNativeHistoryRequest.Scope("Rithmic Paper Trading","CME","ESZ6"),NOW-1000,NOW,NOW-1001);assertEquals("unsupported_namespace",h.nativePage("s",foreign,NOW,524288).error());ReflectionTestUtils.setField(h,"metadataUnavailable",true);assertEquals("unavailable",h.nativePage("s",request(NOW-1000,NOW,NOW-1001),NOW,524288).error());
    }
    @Test void lateRevisionChangesIncarnationBeforeTheSafeIntegerCounterWouldWrap()throws Exception{
        var h=history();h.accept(RithmicSchemas.KEY,RithmicDepthHistoryTest.frame(NOW,1),NOW);var req=request(NOW-1000,NOW,NOW-1001);var before=header(h,"a",req,NOW);ReflectionTestUtils.setField(h,"lateRevision",RithmicNativeHistoryRequest.MAX_SAFE);h.accept(RithmicSchemas.KEY,RithmicDepthHistoryTest.frame(NOW,2),NOW);var after=header(h,"b",req,NOW);assertNotEquals(before.path("cacheIncarnation").asText(),after.path("cacheIncarnation").asText());assertEquals(0,after.path("lateRevision").asLong());
    }

    @Test void scanSkipRemainsVisibleUntilItsSupportedHorizonExpires()throws Exception{
        var h=history();h.accept(RithmicSchemas.KEY,RithmicDepthHistoryTest.frame(NOW-1000,1),NOW);h.markScanSkip(NOW);var value=header(h,"scan",request(NOW-2000,NOW,NOW-2001),NOW);assertTrue(value.path("limited").asBoolean());assertTrue(value.path("sourceGapIntervals").toString().contains("SCAN_SKIP"));assertTrue(value.path("lateRevision").asLong()>0);
        h.accept(RithmicSchemas.KEY,RithmicDepthHistoryTest.frame(NOW+1,2),NOW+1);long later=NOW+RithmicDepthHistory.WINDOW+2;h.accept(RithmicSchemas.KEY,RithmicDepthHistoryTest.frame(later,2),later);var expired=header(h,"expired",request(later-1000,later,later-1001),later);assertFalse(expired.path("limited").asBoolean());assertEquals(0,expired.path("sourceGapIntervals").size());
    }

    @Test void forwardRetentionEvictionDoesNotPretendToBeALateSourceMutation()throws Exception{
        var h=history();@SuppressWarnings("unchecked") var columns=(java.util.TreeMap<Long,RithmicNativeHistoryColumn>)ReflectionTestUtils.getField(h,"columns");long bytes=0;for(int i=8192;i>0;i--){var c=RithmicNativeHistoryColumn.gap(NOW-i,RithmicNativeHistoryPacketTest.TEST,RithmicDepthJson.GapReason.NOT_READY,null);columns.put(NOW-i,c);bytes+=c.retainedBytes();}ReflectionTestUtils.setField(h,"bytes",bytes);h.accept(RithmicSchemas.KEY,RithmicDepthHistoryTest.frame(NOW,1),NOW);assertEquals(0L,ReflectionTestUtils.getField(h,"lateRevision"));assertEquals(8192,columns.size());
    }
    @Test void metadataLossRecoversOnlyAfterEveryUnrecordedGapHasExpired()throws Exception{
        var h=history();ReflectionTestUtils.setField(h,"metadataUnavailable",true);ReflectionTestUtils.setField(h,"metadataLostThrough",NOW);var req=request(NOW-1000,NOW,NOW-1001);assertEquals("unavailable",h.nativePage("early",req,NOW,524288).error());long later=NOW+RithmicDepthHistory.WINDOW;assertTrue(h.nativePage("aged",request(later-1000,later,later-1001),later,524288).accepted());
    }
    @Test void continuousUnreadyOutageCoalescesRatherThanExhaustingGapMetadata()throws Exception{
        var h=history();for(int i=0;i<2200;i++){var raw=RithmicDepthHistoryTest.frame(NOW+i*1000L,i+1);raw.put("mappingReady",false);h.accept(RithmicSchemas.KEY,raw,NOW+i*1000L);}assertEquals(false,ReflectionTestUtils.getField(h,"metadataUnavailable"));@SuppressWarnings("unchecked") var gaps=(java.util.Map<Long,?>)ReflectionTestUtils.getField(h,"sourceGaps");assertEquals(1,gaps.size());assertEquals(0L,ReflectionTestUtils.getField(h,"lateRevision"));
    }

    @Test void outOfOrderPostScanEventCannotConsumeThePendingSkippedSpan()throws Exception{
        var h=history();h.accept(RithmicSchemas.KEY,RithmicDepthHistoryTest.frame(NOW-5000,1),NOW);h.markScanSkip(NOW);h.accept(RithmicSchemas.KEY,RithmicDepthHistoryTest.frame(NOW-9000,2),NOW);assertNotNull(ReflectionTestUtils.getField(h,"pendingScanFrom"));long before=(long)ReflectionTestUtils.getField(h,"lateRevision");h.accept(RithmicSchemas.KEY,RithmicDepthHistoryTest.frame(NOW+1,3),NOW+1);assertNull(ReflectionTestUtils.getField(h,"pendingScanFrom"));assertEquals(before,ReflectionTestUtils.getField(h,"lateRevision"));var result=header(h,"scan-order",request(NOW-10000,NOW+1,NOW-10001),NOW+1);assertTrue(result.path("sourceGapIntervals").toString().contains("SCAN_SKIP"));assertEquals(NOW+2,result.path("sourceGapIntervals").get(0).path("endMs").asLong());
    }

    @Test void actualGapOverflowCannotRecoverEarlyAndCanRecordAgainAfterExpiry()throws Exception{
        var h=history();long last=NOW;
        for(int i=0;i<2049;i++){last=NOW+i*1000L;var raw=RithmicDepthHistoryTest.frame(last,i+1);if(i%2==0)raw.put("mappingReady",false);else raw.put("bids",List.of());h.accept(RithmicSchemas.KEY,raw,last);}
        assertEquals(true,ReflectionTestUtils.getField(h,"metadataUnavailable"));h.accept(RithmicSchemas.KEY,RithmicDepthHistoryTest.frame(last+1000,3000),last+1000);assertEquals("unavailable",h.nativePage("held",request(last-1000,last,last-1001),last+1000,524288).error());
        long later=last+RithmicDepthHistory.WINDOW+2;h.accept(RithmicSchemas.KEY,RithmicDepthHistoryTest.frame(later,4000),later);assertEquals(false,ReflectionTestUtils.getField(h,"metadataUnavailable"));var invalid=RithmicDepthHistoryTest.frame(later+1,4001);invalid.put("mappingReady",false);h.accept(RithmicSchemas.KEY,invalid,later+1);assertTrue(h.nativePage("recovered",request(later-1000,later+1,later-1001),later+1,524288).accepted());@SuppressWarnings("unchecked")var gaps=(java.util.Map<Long,?>)ReflectionTestUtils.getField(h,"sourceGaps");assertEquals(1,gaps.size());
    }
}

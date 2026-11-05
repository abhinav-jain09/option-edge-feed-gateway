package app.feedgateway;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.optionsedge.contracts.rithmic.RithmicSchemas;
import java.util.List;
import org.apache.avro.generic.GenericRecord;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class RithmicDepthHistoryTest {
 static final long NOW=1800000000000L;
 static final ObjectMapper JSON=new ObjectMapper();
 static GenericRecord frame(long t,int seq){var r=RithmicDepthJsonTest.frame();r.put("receivedAtMs",t);r.put("emittedAtMs",t);r.put("basisEventTimeMs",t);r.put("basisMeasuredAtMs",t);r.put("sequence",(long)seq);return r;}
 static com.fasterxml.jackson.databind.node.ObjectNode request(long from,long to,long after){var r=JSON.createObjectNode();r.put("requestId","one");r.put("fromMs",from);r.put("toMs",to);r.put("afterMs",after);r.put("minPriceCents",755700);r.put("maxPriceCents",755800);return r;}
 @Test void oldMappedFramesAreHistoricalWithoutRefreshingTheirClocks()throws Exception{
  var h=new RithmicDepthHistory(new GatewaySettings());long time=NOW-60000;var raw=frame(time,1);h.accept(RithmicSchemas.KEY,raw,NOW);
  assertNull(RithmicDepthJson.render(RithmicSchemas.KEY,raw,NOW),"the live contract still rejects stale frames");
  var page=JSON.readTree(h.page("socket",request(NOW-120000,NOW,NOW-120001),NOW,524288));var value=page.path("frames").get(0);
  assertEquals(time,value.path("receivedAtMs").asLong());assertEquals(time,value.path("emittedAtMs").asLong());assertEquals(755750,value.path("bids").get(0).path("spxPriceCents").asLong());
 }
 @Test void pagesAreBoundedAndCursorDoesNotLoseTheNextRecord()throws Exception{
  var h=new RithmicDepthHistory(new GatewaySettings());for(int i=0;i<40;i++)h.accept(RithmicSchemas.KEY,frame(NOW-60000+i*1000,i+1),NOW);
  var req=request(NOW-60000,NOW,NOW-60001);var first=JSON.readTree(h.page("s",req,NOW,524288));assertEquals(32,first.path("frames").size());assertFalse(first.path("done").asBoolean());
  req.put("afterMs",first.path("afterMs").asLong());var second=JSON.readTree(h.page("s",req,NOW+101,524288));assertEquals(8,second.path("frames").size());assertTrue(second.path("done").asBoolean());
  assertEquals(NOW-28000,second.path("frames").get(0).path("receivedAtMs").asLong());
 }
 @Test void malformedMappedDataCreatesAGapInsteadOfFabricatedLiquidity()throws Exception{
  var h=new RithmicDepthHistory(new GatewaySettings());var r=frame(NOW-2000,1);r.put("mappingReady",false);r.put("bids",List.of());r.put("asks",List.of());r.put("basisCents",null);
  h.accept(RithmicSchemas.KEY,r,NOW);var result=JSON.readTree(h.page("s",request(NOW-3000,NOW,NOW-3001),NOW,524288));assertEquals(NOW-2000,result.path("frames").get(0).path("gapAtMs").asLong());
 }
 @Test void rangeFilterCannotChangePricesOrPromoteEmptySidesToLive()throws Exception{
  var h=new RithmicDepthHistory(new GatewaySettings());h.accept(RithmicSchemas.KEY,frame(NOW-2000,1),NOW);var req=request(NOW-3000,NOW,NOW-3001);req.put("minPriceCents",755770);req.put("maxPriceCents",755790);
  var r=JSON.readTree(h.page("s",req,NOW,524288)).path("frames").get(0);assertTrue(r.path("bids").isEmpty());assertEquals(755775,r.path("asks").get(0).path("spxPriceCents").asLong());
 }
 @Test void replyBytesAndRateAreBounded()throws Exception{
  var h=new RithmicDepthHistory(new GatewaySettings());h.accept(RithmicSchemas.KEY,frame(NOW-1000,1),NOW);var req=request(NOW-2000,NOW,NOW-2001);
  String value=h.page("s",req,NOW,1024);assertTrue(value.getBytes(java.nio.charset.StandardCharsets.UTF_8).length<=1024);
  assertEquals("busy",JSON.readTree(h.page("s",req,NOW,1024)).path("error").asText());req.put("toMs",NOW+10000);assertEquals("invalid_request",JSON.readTree(h.page("other",req,NOW,1024)).path("error").asText());
 }
 @Test void sourceIncarnationsHaveAnExplicitGapEvenInsideOneSecond()throws Exception{
  var h=new RithmicDepthHistory(new GatewaySettings());h.accept(RithmicSchemas.KEY,frame(NOW-1000,10),NOW);var next=frame(NOW-900,1);next.put("streamId","new");h.accept(RithmicSchemas.KEY,next,NOW);
  var frames=JSON.readTree(h.page("s",request(NOW-2000,NOW,NOW-2001),NOW,524288)).path("frames");assertEquals(3,frames.size());assertEquals(NOW-901,frames.get(1).path("gapAtMs").asLong());
 }
 @Test void cacheTimeAndColumnBoundsEvictOldestData()throws Exception{
  var h=new RithmicDepthHistory(new GatewaySettings());for(int i=0;i<=7202;i++)h.accept(RithmicSchemas.KEY,frame(NOW+i*1000,i+1),NOW+i*1000);
  long now=NOW+7202000;var value=JSON.readTree(h.page("s",request(NOW,now,NOW-1),now,524288));assertTrue(value.path("availableFromMs").asLong()>=now-RithmicDepthHistory.WINDOW);
 }
}

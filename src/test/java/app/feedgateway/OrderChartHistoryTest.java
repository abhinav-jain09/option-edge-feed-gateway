package app.feedgateway;
import static org.junit.jupiter.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;
import org.mockito.ArgumentCaptor;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
class OrderChartHistoryTest {
 private final ObjectMapper mapper=new ObjectMapper();
 private static final long OPEN=Instant.parse("2026-09-11T13:30:00Z").toEpochMilli();
 private FootprintViews view(){return new FootprintViews(mapper,262144,32*1024*1024,1000,1024*1024,1000);}
 private String bar(int i){return "{\"schemaVersion\":6,\"sessionDate\":\"2026-09-11\",\"symbol\":\"ES.v.0\",\"timeframe\":\"1m\",\"observations\":{\"barStartMs\":"+(OPEN+i*60000)+"}}";}
 private JsonNode request() throws Exception{return mapper.readTree("{\"type\":\"order-chart-history\",\"schemaVersion\":1,\"requestId\":\"generation-7\",\"resource\":\"footprint\",\"sessionDate\":\"2026-09-11\",\"timeframe\":\"1m\",\"after\":"+(OPEN-1)+",\"toMs\":"+(OPEN+30*60000)+"}");}
 @Test void boundedPagesReuseAdmittedViewAndAdvanceExclusiveCursor() throws Exception {
  var v=view();for(int i=0;i<20;i++)v.admitBar(bar(i));var req=request();var first=mapper.readTree(OrderChartHistory.reply(mapper,v,null,req,524288,262144));
  assertEquals(16,first.path("records").size());assertFalse(first.path("done").asBoolean());assertEquals("generation-7",first.path("requestId").asText());
  ((com.fasterxml.jackson.databind.node.ObjectNode)req).put("after",first.path("nextCursor").asLong());var last=mapper.readTree(OrderChartHistory.reply(mapper,v,null,req,524288,262144));assertEquals(4,last.path("records").size());assertTrue(last.path("done").asBoolean());
 }
 @Test void rejectsCoercionsCrossSessionAndUnknownResource() throws Exception {
  var v=view();v.admitBar(bar(0));for(String field:List.of("schemaVersion","after","toMs")){var req=(com.fasterxml.jackson.databind.node.ObjectNode)request();req.put(field,"1");assertTrue(mapper.readTree(OrderChartHistory.reply(mapper,v,null,req,524288,262144)).has("error"));}
  var unknown=(com.fasterxml.jackson.databind.node.ObjectNode)request();unknown.put("resource","unknown");assertEquals("bad_resource",mapper.readTree(OrderChartHistory.reply(mapper,v,null,unknown,524288,262144)).path("error").asText());
  var req=(com.fasterxml.jackson.databind.node.ObjectNode)request();req.put("sessionDate","2026-09-10");req.put("after",OPEN-86400000-1);req.put("toMs",OPEN-86400000+60000);assertEquals("session_mismatch",mapper.readTree(OrderChartHistory.reply(mapper,v,null,req,524288,262144)).path("error").asText());
 }
 @Test void registeredSocketsUseExistingBoundedWriterAndBusyLimit() throws Exception {
  var service=FootprintWiringTest.on();service.runOutboundWritesInline();service.footprintViews().admitBar(bar(0));

  WebSocketSession socket=mock(WebSocketSession.class);when(socket.getId()).thenReturn("order-history");when(socket.isOpen()).thenReturn(true);
  service.handleFootprintBasicMessage(socket,request().toString());verify(socket,never()).sendMessage(any());service.addClient(socket);clearInvocations(socket);
  service.handleFootprintBasicMessage(socket,request().toString());var sent=ArgumentCaptor.forClass(TextMessage.class);verify(socket).sendMessage(sent.capture());
  assertEquals("loading",mapper.readTree(sent.getValue().getPayload()).path("data").path("error").asText());clearInvocations(socket);
  var field=FeedGatewayService.class.getDeclaredField("orderChartFootprintReplay");field.setAccessible(true);var gate=(OrderChartReplayGate)field.get(service);Object owner=new Object();var partition=new org.apache.kafka.common.TopicPartition("fixture-bars",0);gate.begin(owner,List.of(partition));gate.capture(owner,java.util.Map.of(partition,0L));gate.advance(owner,p->0L);
  service.handleFootprintBasicMessage(socket,request().toString());verify(socket).sendMessage(sent.capture());
  var envelope=mapper.readTree(sent.getValue().getPayload());assertEquals(OrderChartHistory.RESPONSE,envelope.path("type").asText());assertEquals(1,envelope.path("data").path("records").size());
  clearInvocations(socket);assertTrue(service.footprintBackfillPermits().tryAcquire(4));try{service.handleFootprintBasicMessage(socket,request().toString());verify(socket).sendMessage(sent.capture());assertEquals("busy",mapper.readTree(sent.getValue().getPayload()).path("data").path("error").asText());}finally{service.footprintBackfillPermits().release(4);service.removeClient(socket);}
 }
 @Test void auctionCursorIsInclusiveAndWireBudgetShrinksBeforeSerializing() throws Exception {
  var req=(com.fasterxml.jackson.databind.node.ObjectNode)request();req.put("resource","auction");req.put("after","");
  var source=(OrderChartHistory.AuctionSource)(date,after,limit)->{
   int start=after.isEmpty()?0:Integer.parseInt(after.substring(3))-30;
   return new FeedGatewayService.EsAuctionMinutesPage(date,java.util.stream.IntStream.range(start,start+limit).mapToObj(i->"{\"minute\":\"09:"+(30+i)+"\",\"text\":\""+"x".repeat(1000)+"\"}").toList(),"09:"+(30+start+limit));
  };
  String raw=OrderChartHistory.reply(mapper,null,source,req,5000,262144);JsonNode reply=mapper.readTree(raw);assertTrue(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8).length<=5000);assertFalse(reply.has("error"));assertEquals(2,reply.path("records").size());assertEquals("09:32",reply.path("nextCursor").asText());
  req.put("after",reply.path("nextCursor").asText());var second=mapper.readTree(OrderChartHistory.reply(mapper,null,source,req,5000,262144));assertEquals("09:32",second.path("records").get(0).path("minute").asText());
  req.put("after","25:99");assertEquals("bad_cursor",mapper.readTree(OrderChartHistory.reply(mapper,null,source,req,5000,262144)).path("error").asText());
 }
 @Test void footprintShrinksByWireBytesAndRejectsAnOversizedIndividualRecord() throws Exception {
  var v=view();for(int i=0;i<20;i++){var row=(com.fasterxml.jackson.databind.node.ObjectNode)mapper.readTree(bar(i));row.put("padding","x".repeat(1200));v.admitBar(row.toString());}
  var first=mapper.readTree(OrderChartHistory.reply(mapper,v,null,request(),5000,262144));assertFalse(first.has("error"));assertTrue(first.path("records").size()>0&&first.path("records").size()<16);assertTrue(first.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8).length<=5000);
  var limited=mapper.readTree(OrderChartHistory.reply(mapper,v,null,request(),5000,500));assertEquals("record_budget",limited.path("error").asText());assertFalse(limited.has("nextCursor"));assertFalse(limited.has("done"));
 }
 @Test void unavailableRepliesDoNotEchoUnboundedIdentityAndPreserveValidHeader() throws Exception {
  var req=(com.fasterxml.jackson.databind.node.ObjectNode)request();var reply=mapper.readTree(OrderChartHistory.refusal(mapper,req,"loading"));assertEquals("generation-7",reply.path("requestId").asText());assertEquals("footprint",reply.path("resource").asText());assertEquals("2026-09-11",reply.path("sessionDate").asText());
  req.put("requestId","x".repeat(65));reply=mapper.readTree(OrderChartHistory.refusal(mapper,req,"busy"));assertEquals("bad_request",reply.path("error").asText());assertFalse(reply.has("requestId"));
 }
 @Test void disabledFeaturesAnswerUnavailableThroughTheRegisteredSocket() throws Exception {
  var service=new FeedGatewayService(new GatewaySettings(),mapper,new HpsfGatewayViewMapper(),null);service.runOutboundWritesInline();var socket=mock(WebSocketSession.class);when(socket.getId()).thenReturn("disabled-history");when(socket.isOpen()).thenReturn(true);service.addClient(socket);clearInvocations(socket);
  try{service.handleFootprintBasicMessage(socket,request().toString());var sent=ArgumentCaptor.forClass(TextMessage.class);verify(socket).sendMessage(sent.capture());assertEquals("unavailable",mapper.readTree(sent.getValue().getPayload()).path("data").path("error").asText());}finally{service.removeClient(socket);}
 }
 @Test void misboundFootprintTopicIsUnavailableInsteadOfPermanentlyLoading() throws Exception {
  var service=FootprintWiringTest.on();service.runOutboundWritesInline();@SuppressWarnings("unchecked") var consumer=(org.apache.kafka.clients.consumer.KafkaConsumer<String,Object>)mock(org.apache.kafka.clients.consumer.KafkaConsumer.class);var partition=new org.apache.kafka.common.TopicPartition("futures.footprint.bars",0);
  when(consumer.endOffsets(anyCollection(),any(java.time.Duration.class))).thenReturn(java.util.Map.of(partition,0L));
  service.cacheHydrationSeek(consumer,List.of(partition),java.util.Map.of(partition.topic(),new FeedGatewayService.TopicBinding("OTHER","es-footprint-bar")),new FeedGatewayService.StrikeReplay(),1000);assertFalse(service.supportsOrderFootprintHistory());
  var socket=mock(WebSocketSession.class);when(socket.getId()).thenReturn("misbound-history");when(socket.isOpen()).thenReturn(true);service.addClient(socket);clearInvocations(socket);
  try{service.handleFootprintBasicMessage(socket,request().toString());var sent=ArgumentCaptor.forClass(TextMessage.class);verify(socket).sendMessage(sent.capture());assertEquals("unavailable",mapper.readTree(sent.getValue().getPayload()).path("data").path("error").asText());}finally{service.removeClient(socket);}
 }
 @Test void auctionHandlerDistinguishesLoadingHeldSessionAndMissingSession() throws Exception {
  String old=System.getProperty("GATEWAY_ES_AUCTION_ENABLED");System.setProperty("GATEWAY_ES_AUCTION_ENABLED","true");var service=FootprintWiringTest.on();service.runOutboundWritesInline();var socket=mock(WebSocketSession.class);when(socket.getId()).thenReturn("auction-history");when(socket.isOpen()).thenReturn(true);service.addClient(socket);clearInvocations(socket);
  try{var req=(com.fasterxml.jackson.databind.node.ObjectNode)request();req.put("resource","auction");req.put("after","");var sent=ArgumentCaptor.forClass(TextMessage.class);service.handleFootprintBasicMessage(socket,req.toString());verify(socket).sendMessage(sent.capture());assertEquals("loading",mapper.readTree(sent.getValue().getPayload()).path("data").path("error").asText());
   var frozen=FeedGatewayService.class.getDeclaredField("esAuctionHandoffFrozen");frozen.setAccessible(true);((java.util.concurrent.atomic.AtomicBoolean)frozen.get(service)).set(true);var date=FeedGatewayService.class.getDeclaredField("esAuctionTradeDate");date.setAccessible(true);date.set(service,"2026-09-11");clearInvocations(socket);service.handleFootprintBasicMessage(socket,req.toString());verify(socket).sendMessage(sent.capture());assertFalse(mapper.readTree(sent.getValue().getPayload()).path("data").has("error"));
   clearInvocations(socket);req.put("sessionDate","2026-09-10");service.handleFootprintBasicMessage(socket,req.toString());verify(socket).sendMessage(sent.capture());assertEquals("session_unavailable",mapper.readTree(sent.getValue().getPayload()).path("data").path("error").asText());
  }finally{service.removeClient(socket);if(old==null)System.clearProperty("GATEWAY_ES_AUCTION_ENABLED");else System.setProperty("GATEWAY_ES_AUCTION_ENABLED",old);}
 }
}

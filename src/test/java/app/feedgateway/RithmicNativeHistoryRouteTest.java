package app.feedgateway;

import app.feedgateway.mtsession.*;
import app.feedgateway.mtsession.gateway.TicketHandshakeInterceptor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.optionsedge.contracts.rithmic.RithmicSchemas;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.socket.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RithmicNativeHistoryRouteTest {
 @Test void exactBoundSessionReadsBinaryButRevokedOrExpiredSocketDoesNot()throws Exception {
  var settings=spy(new GatewaySettings());doReturn(true).when(settings).authEnabled();doReturn(true).when(settings).rithmicDepthEnabled();doReturn("Rithmic Test").when(settings).rithmicSystem();doReturn("").when(settings).approvalUrl();doReturn("approved").when(settings).approvalRole();
  var engine=new SessionRoutingEngine(new ConcurrencyLimits(5,5,100),new SubscriptionManager());
  engine.registerAppSession("app:u","u",new Selection(MarketDataSource.DATABENTO,"SPX","20261106",StrikeWindow.ALL),Set.of("approved"));engine.attachSocket("app:u","s");
  var json=new ObjectMapper();var service=new FeedGatewayService(settings,json,new HpsfGatewayViewMapper(),engine);service.runOutboundWritesInline();service.outboundClosersForTest(Runnable::run);
  var history=new RithmicDepthHistory(settings);long now=System.currentTimeMillis();history.accept(RithmicSchemas.KEY,RithmicDepthHistoryTest.frame(now-1000,1),now);
  @SuppressWarnings("unchecked") ObjectProvider<RithmicDepthHistory> provider=mock(ObjectProvider.class);when(provider.getIfAvailable()).thenReturn(history);ReflectionTestUtils.setField(service,"rithmicDepthHistory",provider);
  var attrs=new HashMap<String,Object>();attrs.put(TicketHandshakeInterceptor.ATTR_APP_SESSION_ID,"app:u");attrs.put(TicketHandshakeInterceptor.ATTR_USER_ID,"u");attrs.put(WsJwtHandshakeInterceptor.AUTH_EXPIRES_AT_ATTR,now+60000L);
  var socket=mock(WebSocketSession.class);when(socket.getId()).thenReturn("s");when(socket.isOpen()).thenReturn(true);when(socket.getAttributes()).thenReturn(attrs);service.addClient(socket);clearInvocations(socket);
  var req=json.createObjectNode().put("type",RithmicDepthHistory.REQUEST).put("format",RithmicNativeHistoryRequest.FORMAT).put("requestId","one").put("fromMs",now-2000).put("toMs",now).put("afterMs",now-2001);req.putObject("namespace").put("environment","Rithmic Test").put("exchange","CME").put("contract","ESZ6");
  service.handleFootprintBasicMessage(socket,req.toString());var sent=org.mockito.ArgumentCaptor.forClass(BinaryMessage.class);verify(socket).sendMessage(sent.capture());assertTrue(sent.getValue().getPayloadLength()>8);assertEquals(4,service.footprintBackfillPermits().availablePermits());
  clearInvocations(socket);attrs.put(WsJwtHandshakeInterceptor.AUTH_EXPIRES_AT_ATTR,now-1);service.handleFootprintBasicMessage(socket,req.toString());verify(socket,never()).sendMessage(any());
  verify(socket).close(argThat((CloseStatus status)->status.getCode()==4001));service.removeClient(socket);
 }
 @Test void atomicAuthorityRequiresExactSubjectMembershipAndCurrentRole(){
  var e=new SessionRoutingEngine(new ConcurrencyLimits(5,5,100),new SubscriptionManager());e.registerAppSession("a","u",new Selection(MarketDataSource.DATABENTO,"SPX","20261106",StrikeWindow.ALL),Set.of("approved"));e.attachSocket("a","s");long now=System.currentTimeMillis();assertTrue(e.canReadNativeHistory("a","s","u",now,"approved"));assertFalse(e.canReadNativeHistory("a","s","wrong",now,"approved"));assertFalse(e.canReadNativeHistory("a","x","u",now,"approved"));assertFalse(e.canReadNativeHistory("a","s","u",now,"missing"));e.detachSocket("s");assertFalse(e.canReadNativeHistory("a","s","u",now,"approved"));e.teardownAppSession("a");assertFalse(e.canReadNativeHistory("a","s","u",now,"approved"));
 }

 @Test void featureOffReturnsUnavailableWithoutRevokingTheLiveSocket()throws Exception{
  var settings=spy(new GatewaySettings());doReturn(false).when(settings).authEnabled();doReturn(false).when(settings).rithmicDepthEnabled();var json=new ObjectMapper();var service=new FeedGatewayService(settings,json,new HpsfGatewayViewMapper(),null);service.runOutboundWritesInline();var socket=mock(WebSocketSession.class);when(socket.getId()).thenReturn("off");when(socket.isOpen()).thenReturn(true);service.addClient(socket);clearInvocations(socket);service.handleFootprintBasicMessage(socket,"{\"type\":\"rithmic-depth-history\",\"format\":\"rithmic-native-fullbook-gzip-v1\",\"requestId\":\"off\"}");var sent=org.mockito.ArgumentCaptor.forClass(TextMessage.class);verify(socket).sendMessage(sent.capture());assertEquals("unavailable",json.readTree(sent.getValue().getPayload()).path("data").path("error").asText());verify(socket,never()).close(any());verify(socket,never()).close();service.removeClient(socket);
 }

 @Test void missingSessionModeConfigurationReturnsUnavailableWithoutRevoking()throws Exception{
  var settings=spy(new GatewaySettings());doReturn(true).when(settings).authEnabled();doReturn(true).when(settings).rithmicDepthEnabled();doReturn("Rithmic Test").when(settings).rithmicSystem();doReturn("").when(settings).approvalRole();doReturn("").when(settings).approvalUrl();
  for(var engine:java.util.Arrays.asList(null,new SessionRoutingEngine(new ConcurrencyLimits(5,5,100),new SubscriptionManager()))){
   var json=new ObjectMapper();var service=new FeedGatewayService(settings,json,new HpsfGatewayViewMapper(),engine);service.runOutboundWritesInline();var socket=mock(WebSocketSession.class);when(socket.getId()).thenReturn("missing");when(socket.isOpen()).thenReturn(true);when(socket.getAttributes()).thenReturn(new HashMap<>());service.addClient(socket);clearInvocations(socket);service.handleFootprintBasicMessage(socket,"{\"type\":\"rithmic-depth-history\",\"format\":\"rithmic-native-fullbook-gzip-v1\",\"requestId\":\"missing\"}");var sent=org.mockito.ArgumentCaptor.forClass(TextMessage.class);verify(socket).sendMessage(sent.capture());assertEquals("unavailable",json.readTree(sent.getValue().getPayload()).path("data").path("error").asText());verify(socket,never()).close(any());verify(socket,never()).close();service.removeClient(socket);
  }
 }
 @Test void unsupportedLegacyOnlyCacheNotificationsCannotRestartTheConsumer(){
  var settings=spy(new GatewaySettings());doReturn("").when(settings).rithmicSystem();
  var service=new FeedGatewayService(settings,new ObjectMapper(),new HpsfGatewayViewMapper(),null);
  var history=new RithmicDepthHistory(settings);
  @SuppressWarnings("unchecked") ObjectProvider<RithmicDepthHistory> provider=mock(ObjectProvider.class);when(provider.getIfAvailable()).thenReturn(history);ReflectionTestUtils.setField(service,"rithmicDepthHistory",provider);
  @SuppressWarnings("unchecked") ObjectProvider<FeedGatewayService> gateway=mock(ObjectProvider.class);when(gateway.getIfAvailable()).thenReturn(service);ReflectionTestUtils.setField(history,"gateway",gateway);
  long now=System.currentTimeMillis();assertDoesNotThrow(()->{
   history.accept(RithmicSchemas.KEY,RithmicDepthHistoryTest.frame(now,1),now);
   history.accept(RithmicSchemas.KEY,RithmicDepthHistoryTest.frame(now-1000,2),now);
   history.markScanSkip(now);
  });verifyNoInteractions(provider);
 }

}

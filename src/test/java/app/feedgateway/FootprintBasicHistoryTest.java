package app.feedgateway;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

class FootprintBasicHistoryTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private static final long OPEN = Instant.parse("2026-09-11T13:30:00Z").toEpochMilli();
    private FootprintViews view() { return new FootprintViews(mapper, 262144, 32*1024*1024, 1000, 1024*1024, 1000); }
    private String bar(int i) { return "{\"schemaVersion\":6,\"sessionDate\":\"2026-09-11\",\"symbol\":\"ES.v.0\",\"timeframe\":\"1m\",\"observations\":{\"barStartMs\":"+(OPEN+i*60000)+"}}"; }
    private JsonNode request(long after, long to) throws Exception { return mapper.readTree("{\"type\":\"es-footprint-basic-history\",\"sessionDate\":\"2026-09-11\",\"afterMs\":"+after+",\"toMs\":"+to+"}"); }
    @Test void streamedPagesAreBoundedAscendingAndExcludeOvernight() throws Exception {
        FootprintViews v=view(); for(int i=-1;i<7;i++)v.admitBar(bar(i));
        JsonNode page=mapper.readTree(FootprintBasicHistory.reply(mapper,v,request(OPEN-1,OPEN+360000),100));
        assertEquals(4,page.path("bars").size()); assertEquals(OPEN,page.path("bars").get(0).path("observations").path("barStartMs").asLong());
        assertEquals(OPEN+180000,page.path("nextCursor").asLong());
        JsonNode last=mapper.readTree(FootprintBasicHistory.reply(mapper,v,request(page.path("nextCursor").asLong(),OPEN+360000),4));
        assertEquals(3,last.path("bars").size());assertTrue(last.path("nextCursor").isNull());
        assertEquals(OPEN,page.path("sessionStartMs").asLong());
        assertEquals(Instant.parse("2026-09-11T20:00:00Z").toEpochMilli(),page.path("sessionEndMs").asLong());
    }
    @Test void badDatesCoercionsAndCrossSessionReadsFailClosed() throws Exception {
        FootprintViews v=view();v.admitBar(bar(0));
        for(String bad:new String[]{"{}","{\"sessionDate\":\"2026-02-30\",\"afterMs\":0,\"toMs\":1}",
                "{\"sessionDate\":\"2026-09-11\",\"afterMs\":\"0\",\"toMs\":1}"})
            assertEquals("bad_request",mapper.readTree(FootprintBasicHistory.reply(mapper,v,mapper.readTree(bad),4)).path("error").asText());
        assertEquals("bad_request",mapper.readTree(FootprintBasicHistory.reply(mapper,v,request(OPEN-60000,OPEN),4)).path("error").asText());
        assertEquals("record_budget",mapper.readTree(FootprintBasicHistory.reply(mapper,v,request(OPEN-1,OPEN),0)).path("error").asText());
        v.admitBar(bar(0).replace("2026-09-11","2026-09-14"));
        JsonNode mismatch=mapper.readTree(FootprintBasicHistory.reply(mapper,v,request(OPEN-1,OPEN),4));
        assertTrue(mismatch.path("sessionMismatch").asBoolean());assertTrue(mismatch.path("bars").isEmpty());
    }
    @Test void historyUsesRegisteredSocketsAndTheExistingBoundedWriter() throws Exception {
        FeedGatewayService s=FootprintWiringTest.on();s.runOutboundWritesInline();s.footprintViews().admitBar(bar(0));
        WebSocketSession ws=mock(WebSocketSession.class);when(ws.getId()).thenReturn("basic");when(ws.isOpen()).thenReturn(true);
        s.handleFootprintBasicMessage(ws,request(OPEN-1,OPEN).toString());verify(ws,never()).sendMessage(any());
        s.addClient(ws);clearInvocations(ws);
        s.handleFootprintBasicMessage(ws,request(OPEN-1,OPEN).toString());
        ArgumentCaptor<TextMessage> frame=ArgumentCaptor.forClass(TextMessage.class);verify(ws).sendMessage(frame.capture());
        JsonNode envelope=mapper.readTree(frame.getValue().getPayload());assertEquals(FootprintBasicHistory.RESPONSE,envelope.path("type").asText());
        assertEquals(1,envelope.path("data").path("bars").size());
        assertEquals(4,s.footprintBackfillPermits().availablePermits());
        clearInvocations(ws);s.handleFootprintBasicMessage(ws,"x".repeat(1025));verify(ws,never()).sendMessage(any());
        clearInvocations(ws);
        var oversized = (com.fasterxml.jackson.databind.node.ObjectNode) request(OPEN-1,OPEN);
        oversized.put("padding", "界".repeat(350));
        assertTrue(oversized.toString().length() < FootprintBasicHistory.MAX_REQUEST_BYTES);
        s.handleFootprintBasicMessage(ws,oversized.toString());verify(ws,never()).sendMessage(any());
        assertTrue(s.footprintBackfillPermits().tryAcquire(4));
        s.handleFootprintBasicMessage(ws,request(OPEN-1,OPEN).toString());
        verify(ws).sendMessage(frame.capture());
        assertEquals("busy",mapper.readTree(frame.getValue().getPayload()).path("data").path("error").asText());
        s.footprintBackfillPermits().release(4);
        s.removeClient(ws);
    }
}

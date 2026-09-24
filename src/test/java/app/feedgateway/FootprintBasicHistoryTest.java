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
    private static final long SESSION_OPEN = Instant.parse("2026-09-10T22:00:00Z").toEpochMilli();
    private FootprintViews view() { return new FootprintViews(mapper, 262144, 32*1024*1024, 1000, 1024*1024, 1000); }
    private String bar(int i) { return "{\"schemaVersion\":6,\"sessionDate\":\"2026-09-11\",\"symbol\":\"ES.v.0\",\"timeframe\":\"1m\",\"observations\":{\"barStartMs\":"+(OPEN+i*60000)+"}}"; }
    private JsonNode request(long after, long to) throws Exception { return mapper.readTree("{\"type\":\"es-footprint-basic-history\",\"sessionDate\":\"2026-09-11\",\"afterMs\":"+after+",\"toMs\":"+to+"}"); }
    private String detailedBar(int i) {
        long t = SESSION_OPEN + i * 60_000L, price = 760000L + i * 25L;
        return "{\"schemaVersion\":6,\"sessionDate\":\"2026-09-11\",\"symbol\":\"ES.v.0\",\"timeframe\":\"1m\",\"tickCents\":25,\"observations\":{"
                + "\"barStartMs\":" + t + ",\"barEndMs\":" + (t + 60_000L) + ",\"complete\":true,\"barQuality\":\"READABLE\",\"replayDeterministic\":true,\"levelsTruncated\":false,\"volume\":3,\"delta\":1,\"openCents\":" + price + ",\"highCents\":" + (price + 25) + ",\"lowCents\":" + (price - 25) + ",\"closeCents\":" + price + ",\"levels\":[{\"priceCents\":" + price + ",\"bid\":1,\"ask\":2,\"unk\":0}]},\"structure\":{}}";
    }
    // Keep this identifier stable because the mutation-audit provenance record keys on it.
    @Test void streamedPagesAreBoundedAscendingAndExcludeOvernight() throws Exception {
        FootprintViews v=view(); for(int i=-1;i<7;i++)v.admitBar(bar(i));
        JsonNode page=mapper.readTree(FootprintBasicHistory.reply(mapper,v,request(OPEN-60001,OPEN+360000),100));
        assertEquals(4,page.path("bars").size()); assertEquals(OPEN-60000,page.path("bars").get(0).path("observations").path("barStartMs").asLong());
        assertEquals(OPEN+120000,page.path("nextCursor").asLong());
        JsonNode last=mapper.readTree(FootprintBasicHistory.reply(mapper,v,request(page.path("nextCursor").asLong(),OPEN+360000),4));
        assertEquals(4,last.path("bars").size());
        JsonNode terminal=mapper.readTree(FootprintBasicHistory.reply(mapper,v,request(last.path("nextCursor").asLong(),OPEN+360000),4));
        assertTrue(terminal.path("bars").isEmpty());assertTrue(terminal.path("nextCursor").isNull());
        assertEquals(SESSION_OPEN,page.path("sessionStartMs").asLong());
        assertEquals(Instant.parse("2026-09-11T20:00:00Z").toEpochMilli(),page.path("sessionEndMs").asLong());
    }
    @Test void badDatesCoercionsAndCrossSessionReadsFailClosed() throws Exception {
        FootprintViews v=view();v.admitBar(bar(0));
        for(String bad:new String[]{"{}","{\"sessionDate\":\"2026-02-30\",\"afterMs\":0,\"toMs\":1}",
                "{\"sessionDate\":\"2026-09-11\",\"afterMs\":\"0\",\"toMs\":1}"})
            assertEquals("bad_request",mapper.readTree(FootprintBasicHistory.reply(mapper,v,mapper.readTree(bad),4)).path("error").asText());
        assertEquals("bad_request",mapper.readTree(FootprintBasicHistory.reply(mapper,v,request(SESSION_OPEN-60000,SESSION_OPEN),4)).path("error").asText());
        assertEquals("record_budget",mapper.readTree(FootprintBasicHistory.reply(mapper,v,request(OPEN-1,OPEN),0)).path("error").asText());
        v.admitBar(bar(0).replace("2026-09-11","2026-09-14"));
        JsonNode mismatch=mapper.readTree(FootprintBasicHistory.reply(mapper,v,request(OPEN-1,OPEN),4));
        assertTrue(mismatch.path("sessionMismatch").asBoolean());assertTrue(mismatch.path("bars").isEmpty());
    }
    @Test void oneHourHistoryIsAggregatedInTheGatewayNotTheBrowser() throws Exception {
        FootprintViews v = view();
        for (int i = 0; i < 60; i++) v.admitBar(detailedBar(i));
        var request = (com.fasterxml.jackson.databind.node.ObjectNode) request(SESSION_OPEN - 1, SESSION_OPEN + 59 * 60_000L);
        request.put("timeframe", "1h");
        JsonNode page = mapper.readTree(FootprintBasicHistory.reply(mapper, v, request, 4));
        assertEquals("1h", page.path("timeframe").asText());
        assertEquals(1, page.path("bars").size());
        JsonNode bar = page.path("bars").get(0);
        assertEquals("1h", bar.path("timeframe").asText());
        assertEquals(SESSION_OPEN, bar.path("observations").path("barStartMs").asLong());
        assertEquals(180, bar.path("observations").path("volume").asLong());
        assertEquals(60, bar.path("observations").path("levels").size());
        assertTrue(page.path("nextCursor").isNull());
    }
    @Test void oneHourHistoryNeverLabelsMissingMinutesComplete() throws Exception {
        FootprintViews v = view();
        for (int i = 0; i < 59; i++) v.admitBar(detailedBar(i));
        var request = (com.fasterxml.jackson.databind.node.ObjectNode) request(SESSION_OPEN - 1, SESSION_OPEN + 59 * 60_000L);
        request.put("timeframe", "1h");
        JsonNode page = mapper.readTree(FootprintBasicHistory.reply(mapper, v, request, 4));
        assertEquals(1, page.path("bars").size());
        assertFalse(page.path("bars").get(0).path("observations").path("complete").asBoolean());
        assertEquals("PARTIAL", page.path("bars").get(0).path("observations").path("barQuality").asText());
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

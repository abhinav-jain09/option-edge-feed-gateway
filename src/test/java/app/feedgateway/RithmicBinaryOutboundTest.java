package app.feedgateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RithmicBinaryOutboundTest {
    static final class Fixture {
        final WebSocketSession socket=mock(WebSocketSession.class);
        final ArrayDeque<Runnable> writers=new ArrayDeque<>();
        final List<WebSocketMessage<?>> sent=new ArrayList<>();
        final AtomicBoolean allowed=new AtomicBoolean(true);
        int slow,errors,dropped,coalesced,closed;long sentBytes;
        final OutboundChannel channel;
        Fixture(int messages,long bytes)throws Exception{
            when(socket.getId()).thenReturn("native");when(socket.isOpen()).thenReturn(true);
            doAnswer(a->{sent.add(a.getArgument(0));return null;}).when(socket).sendMessage(any());
            var metrics=new OutboundChannel.Metrics(){public void enqueued(int n){}public void coalesced(){coalesced++;}public void sent(int n){sentBytes+=n;}public void disconnectedSlow(){slow++;}public void writeError(){errors++;}public void droppedOnClose(int n){dropped+=n;}};
            channel=new OutboundChannel(socket,writers::add,Runnable::run,messages,bytes,metrics,c->closed++);
        }
        void drain(){while(!writers.isEmpty())writers.remove().run();}
        OutboundChannel.HistoryAdmission binary(byte[] bytes){return channel.tryEnqueueHistoryBinary(bytes,allowed::get);}
    }
    static byte[] payload(){return new byte[]{82,76,70,49,0,0,0,2,123,125};}
    static RithmicHistoryInvalidation invalidation(String incarnation,long revision,long affected){return new RithmicHistoryInvalidation(RithmicNativeHistoryPacketTest.TEST,incarnation,revision,affected);}

    @Test void mixedMessagesKeepOrderAndOwnExactlyOneIndependentBinaryCopy()throws Exception{
        var f=new Fixture(8,2097152);f.channel.enqueue("€",null);byte[] original=payload(),expected=original.clone();assertEquals(OutboundChannel.HistoryAdmission.ENQUEUED,f.binary(original));original[0]=0;f.channel.enqueue("tail",null);assertEquals(3+expected.length+4,f.channel.queuedBytes());f.drain();
        assertEquals(3,f.sent.size());assertEquals("€",((TextMessage)f.sent.get(0)).getPayload());assertInstanceOf(BinaryMessage.class,f.sent.get(1));byte[] actual=new byte[expected.length];((BinaryMessage)f.sent.get(1)).getPayload().duplicate().get(actual);assertArrayEquals(expected,actual);assertEquals("tail",((TextMessage)f.sent.get(2)).getPayload());assertEquals(0,f.channel.queuedBytes());assertEquals(3+expected.length+4,f.sentBytes);
    }
    @Test void unicodeTextUsesActualUtf8BytesForOverflowAndCoalescing()throws Exception{
        var tooSmall=new Fixture(8,2);assertFalse(tooSmall.channel.enqueue("€",null));assertEquals(1,tooSmall.slow);assertEquals(0,tooSmall.channel.queuedBytes());
        var f=new Fixture(8,32768);assertTrue(f.channel.enqueue("€","snapshot"));assertTrue(f.channel.enqueue("tail",null));assertTrue(f.channel.enqueue("🟢","snapshot"));assertEquals(8,f.channel.queuedBytes());assertEquals(1,f.coalesced);f.drain();assertEquals("🟢",((TextMessage)f.sent.getFirst()).getPayload());assertEquals(8,f.sentBytes);
    }
    @Test void historyBusyLeavesControlSpaceWithoutClosingOrQueuingABinary()throws Exception{
        var f=new Fixture(2,32768);f.channel.enqueue("pending",null);long before=f.channel.queuedBytes();assertEquals(OutboundChannel.HistoryAdmission.BUSY,f.binary(payload()));assertEquals(before,f.channel.queuedBytes());assertEquals(0,f.closed);assertEquals(0,f.slow);assertTrue(f.channel.enqueueAuthorizedText("busy",null,f.allowed::get));f.drain();assertEquals(2,f.sent.size());assertTrue(f.sent.stream().allMatch(TextMessage.class::isInstance));
    }
    @Test void byteReserveDistinguishesBusyFromActualSaturation()throws Exception{
        var f=new Fixture(8,32768);f.channel.enqueue("a".repeat(32768-4096),null);assertEquals(OutboundChannel.HistoryAdmission.BUSY,f.binary(payload()));assertEquals(0,f.closed);f.channel.enqueue("busy",null);assertEquals(OutboundChannel.HistoryAdmission.CLOSED,f.binary(payload()));assertEquals(1,f.slow);assertEquals(0,f.channel.queuedBytes());verify(f.socket).close();
    }
    @Test void payloadAbovePerChannelPacketBudgetIsCapacityWithoutSourceRelabellingOrClose()throws Exception{
        var f=new Fixture(8,32768);assertEquals(OutboundChannel.HistoryAdmission.CAPACITY,f.binary(new byte[8193]));assertEquals(0,f.closed);assertEquals(0,f.channel.queueDepth());
    }
    @Test void authorizationIsRequiredBeforeEnqueueAndRecheckedBeforeBinaryWrite()throws Exception{
        var refused=new Fixture(8,32768);refused.allowed.set(false);assertEquals(OutboundChannel.HistoryAdmission.CLOSED,refused.binary(payload()));assertEquals(0,refused.sent.size());assertEquals(1,refused.closed);verify(refused.socket).close(argThat(s->s.getCode()==4001));assertEquals(0,refused.errors);
        var revoked=new Fixture(8,32768);assertEquals(OutboundChannel.HistoryAdmission.ENQUEUED,revoked.binary(payload()));revoked.channel.enqueue("must be dropped",null);revoked.allowed.set(false);revoked.drain();assertEquals(0,revoked.sent.size());assertEquals(1,revoked.dropped);assertEquals(1,revoked.closed);assertEquals(0,revoked.channel.queuedBytes());verify(revoked.socket).close(argThat(s->s.getCode()==4001));
    }
    @Test void authorizedControlsAlsoRecheckAndAuthorityErrorsFailClosed()throws Exception{
        var f=new Fixture(8,32768);assertTrue(f.channel.enqueueAuthorizedText("hello",null,f.allowed::get));f.allowed.set(false);f.drain();assertEquals(0,f.sent.size());assertEquals(1,f.closed);
        var unavailable=new Fixture(8,32768);assertEquals(OutboundChannel.HistoryAdmission.CLOSED,unavailable.channel.tryEnqueueHistoryBinary(payload(),()->{throw new IllegalStateException("private authority failure");}));assertEquals(0,unavailable.sent.size());verify(unavailable.socket).close(argThat(s->s.getReason().equals("authorization unavailable")));
    }
    @Test void invalidationStormKeepsMaxRevisionAndTheLowestPendingAffectedBoundary()throws Exception{
        var f=new Fixture(8,32768);assertTrue(f.channel.enqueueHistoryInvalidation(invalidation("cache",2,100),f.allowed::get));assertTrue(f.channel.enqueueHistoryInvalidation(invalidation("cache",4,50),f.allowed::get));assertTrue(f.channel.enqueueHistoryInvalidation(invalidation("cache",3,200),f.allowed::get));assertEquals(1,f.channel.queueDepth());assertEquals(2,f.coalesced);f.drain();var json=new ObjectMapper().readTree(((TextMessage)f.sent.getFirst()).getPayload());assertEquals(4,json.path("data").path("lateRevision").asLong());assertEquals(50,json.path("data").path("affectedFromMs").asLong());assertFalse(json.toString().contains("subject"));assertEquals(0,f.channel.queuedBytes());
    }
    @Test void oneInflightInvalidationHasAtMostOneQueuedReplacement()throws Exception{
        var f=new Fixture(8,32768);doAnswer(a->{f.sent.add(a.getArgument(0));if(f.sent.size()==1){f.channel.enqueueHistoryInvalidation(invalidation("cache",2,20),f.allowed::get);f.channel.enqueueHistoryInvalidation(invalidation("cache",3,30),f.allowed::get);assertEquals(1,f.channel.queueDepth());}return null;}).when(f.socket).sendMessage(any());f.channel.enqueueHistoryInvalidation(invalidation("cache",1,10),f.allowed::get);f.drain();assertEquals(2,f.sent.size());var last=new ObjectMapper().readTree(((TextMessage)f.sent.getLast()).getPayload());assertEquals(3,last.path("data").path("lateRevision").asLong());assertEquals(20,last.path("data").path("affectedFromMs").asLong());
    }
    @Test void newIncarnationDoesNotInheritOldRevisionButPreservesAQueuedLowerBoundary()throws Exception{
        var f=new Fixture(8,32768);f.channel.enqueueHistoryInvalidation(invalidation("old",100,10),f.allowed::get);f.channel.enqueueHistoryInvalidation(invalidation("new",0,20),f.allowed::get);f.drain();var value=new ObjectMapper().readTree(((TextMessage)f.sent.getFirst()).getPayload()).path("data");assertEquals("new",value.path("cacheIncarnation").asText());assertEquals(0,value.path("lateRevision").asLong());assertEquals(10,value.path("affectedFromMs").asLong());
    }
    @Test void shutdownDropsMixedMessagesAndARejectedWriterCannotStrandTheQueue()throws Exception{
        var f=new Fixture(8,32768);f.binary(payload());f.channel.enqueue("text",null);f.channel.shutdown();f.drain();assertEquals(0,f.sent.size());assertEquals(0,f.channel.queuedBytes());assertEquals(OutboundChannel.HistoryAdmission.CLOSED,f.binary(payload()));
        var ws=mock(WebSocketSession.class);when(ws.getId()).thenReturn("rejected");when(ws.isOpen()).thenReturn(true);var metrics=mock(OutboundChannel.Metrics.class);var channel=new OutboundChannel(ws,r->{throw new java.util.concurrent.RejectedExecutionException();},Runnable::run,8,32768,metrics,c->{});assertEquals(OutboundChannel.HistoryAdmission.CLOSED,channel.tryEnqueueHistoryBinary(payload(),()->true));assertEquals(0,channel.queuedBytes());verify(metrics).writeError();verify(ws).close();
    }

    @Test void allocationFreeUtf8LengthMatchesJavaForAsciiBmpSupplementaryAndMalformedSurrogates(){
        for(String value:java.util.List.of("ascii","é€","🎯","a\uD800b","x\uDC00y","\uD800\uD800\uDC00"))assertEquals(value.getBytes(java.nio.charset.StandardCharsets.UTF_8).length,OutboundChannel.utf8Length(value));
    }
}

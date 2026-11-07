package app.feedgateway;

import static org.junit.jupiter.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;

class OrderChartWireCodecTest {
    private final ObjectMapper mapper=new ObjectMapper();
    private final OrderChartWireCodec codec=new OrderChartWireCodec(mapper);
    private static OrderChartWireCodec.Identity identity(OrderChartWireCodec.Kind kind,long revision,long base){return new OrderChartWireCodec.Identity("client_1","a".repeat(64),"b".repeat(32),revision,base,kind);}
    @Test void maximumRevisionHasBoundedFramesAndOneExactPayloadDigest()throws Exception{
        byte[] source=new byte[OrderChartWireCodec.MAX_LOGICAL];for(int i=0;i<source.length;i++)source[i]=(byte)(i%251);
        byte[] expected=source.clone();var revision=codec.prepare(source);source[0]=42;byte[] rebuilt=new byte[expected.length];int offset=0;
        for(int index=0;index<revision.chunks();index++){
            byte[] wire=codec.chunk(revision,identity(OrderChartWireCodec.Kind.SNAPSHOT,123,-1),index);assertTrue(wire.length<=65536);
            var input=ByteBuffer.wrap(wire);assertEquals(OrderChartWireCodec.MAGIC,input.getInt());int headerSize=input.getInt();assertTrue(headerSize<=2048);
            byte[] header=new byte[headerSize];input.get(header);var h=mapper.readTree(header);assertEquals(index,h.path("chunkIndex").intValue());assertEquals(revision.chunks(),h.path("chunkCount").intValue());
            assertEquals(123,h.path("revision").longValue());assertEquals(-1,h.path("baseRevision").longValue());assertEquals(expected.length,h.path("totalBytes").intValue());assertEquals(revision.digest(),h.path("sha256").asText());
            int size=input.remaining();input.get(rebuilt,offset,size);offset+=size;
        }
        assertEquals(expected.length,offset);assertArrayEquals(expected,rebuilt);assertEquals(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(rebuilt)),revision.digest());
    }
    @Test void boundariesAndIdentityRefuseBeforeFraming(){
        assertThrows(IllegalArgumentException.class,()->codec.prepare(new byte[0]));assertThrows(IllegalArgumentException.class,()->codec.prepare(new byte[OrderChartWireCodec.MAX_LOGICAL+1]));
        var revision=codec.prepare(new byte[]{1});assertEquals(1,revision.chunks());assertThrows(IllegalArgumentException.class,()->codec.chunk(revision,identity(OrderChartWireCodec.Kind.SNAPSHOT,0,-1),-1));assertThrows(IllegalArgumentException.class,()->codec.chunk(revision,identity(OrderChartWireCodec.Kind.SNAPSHOT,0,-1),1));
        assertThrows(IllegalArgumentException.class,()->identity(OrderChartWireCodec.Kind.DELTA,3,1));assertThrows(IllegalArgumentException.class,()->identity(OrderChartWireCodec.Kind.SNAPSHOT,3,0));
        assertThrows(IllegalArgumentException.class,()->new OrderChartWireCodec.Identity("bad/id","a".repeat(64),"b".repeat(32),0,-1,OrderChartWireCodec.Kind.SNAPSHOT));
        assertThrows(IllegalArgumentException.class,()->identity(OrderChartWireCodec.Kind.DELTA,0,Long.MAX_VALUE));
        assertThrows(IllegalArgumentException.class,()->identity(OrderChartWireCodec.Kind.SNAPSHOT,OrderChartWireCodec.MAX_REVISION+1,-1));
    }
    @Test void consecutiveDeltaIdentityIsCarriedWithoutRenewingSourceTime()throws Exception{
        var revision=codec.prepare("{\"asOfMs\":1234}".getBytes(java.nio.charset.StandardCharsets.UTF_8));byte[] wire=codec.chunk(revision,identity(OrderChartWireCodec.Kind.DELTA,9,8),0);var input=ByteBuffer.wrap(wire);input.getInt();byte[] header=new byte[input.getInt()];input.get(header);var h=mapper.readTree(header);
        assertEquals("DELTA",h.path("kind").asText());assertEquals(8,h.path("baseRevision").longValue());assertFalse(h.has("asOfMs"));assertFalse(h.has("receivedAtMs"));
        byte[] body=new byte[input.remaining()];input.get(body);assertEquals(1234,mapper.readTree(body).path("asOfMs").longValue());
    }
}

package app.feedgateway;
import static org.junit.jupiter.api.Assertions.*;
import java.io.ByteArrayOutputStream;
import org.apache.avro.Schema;
import org.junit.jupiter.api.Test;
class DepthDatumBoundsTest {
    private static byte[] wire(long... numbers) {
        var out=new ByteArrayOutputStream();out.writeBytes(new byte[5]);
        for(long n:numbers){long value=(n<<1)^(n>>63);while((value&~127L)!=0){out.write((int)(value&127)|128);value>>>=7;}out.write((int)value);}
        return out.toByteArray();
    }
    @Test void hugeArrayCountFailsBeforeAnyItemAllocationAndAcrossMultipleBlocks() {
        var schema=Schema.createArray(Schema.create(Schema.Type.NULL));
        assertThrows(IllegalArgumentException.class,()->DepthDatumBounds.check(wire(Integer.MAX_VALUE,0),schema));
        assertThrows(IllegalArgumentException.class,()->DepthDatumBounds.check(wire(25000,1,0),schema));
        assertDoesNotThrow(()->DepthDatumBounds.check(wire(25000,0),schema));
    }
    @Test void negativeBlockSizeAndIntegerOverflowAreRejected() {
        var array=Schema.createArray(Schema.create(Schema.Type.LONG));
        assertDoesNotThrow(()->DepthDatumBounds.check(wire(-1,1,1,0),array));
        assertThrows(IllegalArgumentException.class,()->DepthDatumBounds.check(wire(-1,0,1,0),array));
        assertThrows(IllegalArgumentException.class,()->DepthDatumBounds.check(wire(-1,-1,1,0),array));
        assertThrows(IllegalArgumentException.class,()->DepthDatumBounds.check(wire((long)Integer.MAX_VALUE+1),Schema.create(Schema.Type.INT)));
        byte[] overflow=new byte[15];java.util.Arrays.fill(overflow,5,15,(byte)255);
        assertThrows(IllegalArgumentException.class,()->DepthDatumBounds.check(overflow,Schema.create(Schema.Type.LONG)));
    }
    @Test void oversizedStringsAndMalformedUtf8FailWithoutLargeAllocation() {
        var schema=Schema.create(Schema.Type.STRING);
        assertThrows(IllegalArgumentException.class,()->DepthDatumBounds.check(wire(Integer.MAX_VALUE),schema));
        byte[] malformed=new byte[]{0,0,0,0,0,2,(byte)255};
        assertThrows(IllegalArgumentException.class,()->DepthDatumBounds.check(malformed,schema));
    }
}

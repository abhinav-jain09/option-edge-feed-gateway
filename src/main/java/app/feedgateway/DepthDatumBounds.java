package app.feedgateway;

import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import org.apache.avro.Schema;

/** Allocation-free Avro shape/length pass before GenericDatumReader allocates strings/arrays. */
final class DepthDatumBounds {
    private final byte[] bytes;
    private int position;
    private DepthDatumBounds(byte[] bytes, int start) {this.bytes=bytes;position=start;}
    static void check(byte[] wire, Schema schema) {
        var cursor=new DepthDatumBounds(wire,5);cursor.value(schema,0);
        if(cursor.position!=wire.length)throw new IllegalArgumentException("trailing depth datum");
    }
    private long number() {
        long value=0;
        for(int i=0;i<10;i++) {
            if(position>=bytes.length)throw new IllegalArgumentException("short depth datum");
            int b=bytes[position++]&255;
            if(i==9&&(b&254)!=0)throw new IllegalArgumentException("depth integer overflow");
            value|=(long)(b&127)<<(7*i);
            if((b&128)==0)return(value>>>1)^-(value&1);
        }
        throw new IllegalArgumentException("depth integer width");
    }
    private void value(Schema schema,int depth) {
        if(depth>12)throw new IllegalArgumentException("depth schema nesting");
        switch(schema.getType()) {
            case NULL -> {}
            case LONG -> number();
            case INT -> {long n=number();if(n<Integer.MIN_VALUE||n>Integer.MAX_VALUE)throw new IllegalArgumentException("depth int range");}
            case ENUM -> {long n=number();if(n<0||n>=schema.getEnumSymbols().size())throw new IllegalArgumentException("depth enum");}
            case UNION -> {long n=number();if(n<0||n>=schema.getTypes().size())throw new IllegalArgumentException("depth union");value(schema.getTypes().get((int)n),depth+1);}
            case STRING -> {
                long length=number();if(length<0||length>512||length>bytes.length-position)throw new IllegalArgumentException("depth string bound");
                try {StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes,position,(int)length));}
                catch(java.nio.charset.CharacterCodingException e){throw new IllegalArgumentException("depth UTF-8",e);}
                position+=(int)length;
            }
            case RECORD -> {for(var field:schema.getFields())value(field.schema(),depth+1);}
            case ARRAY -> {
                long total=0;
                for(long count=number();count!=0;count=number()) {
                    long declared=-1;
                    if(count<0){if(count==Long.MIN_VALUE)throw new IllegalArgumentException("depth array count");count=-count;declared=number();if(declared<0||declared>bytes.length-position)throw new IllegalArgumentException("depth array block");}
                    if(count>25000-total)throw new IllegalArgumentException("depth entry count");total+=count;int start=position;
                    for(long i=0;i<count;i++)value(schema.getElementType(),depth+1);
                    if(declared>=0&&position-start!=declared)throw new IllegalArgumentException("depth array block length");
                }
            }
            default -> throw new IllegalArgumentException("unsupported depth schema shape");
        }
    }
}

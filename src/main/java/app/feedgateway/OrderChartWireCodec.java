package app.feedgateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/** Independent binary family; a complete logical revision is adopted only after digest validation. */
final class OrderChartWireCodec {
    static final long MAX_REVISION=9_007_199_254_740_991L;
    static final int MAGIC=0x4f454331,MAX_FRAME=64*1024,MAX_HEADER=2048,CHUNK_BYTES=60*1024,MAX_LOGICAL=4*1024*1024;
    enum Kind { SNAPSHOT, DELTA }
    record Identity(String subscriptionId,String scopeHash,String epoch,long revision,long baseRevision,Kind kind){
        Identity{
            if(subscriptionId==null||!subscriptionId.matches("[A-Za-z0-9_-]{1,64}")||scopeHash==null||!scopeHash.matches("[a-f0-9]{64}")
                    ||epoch==null||!epoch.matches("[a-f0-9]{32}")||revision<0||revision>MAX_REVISION||baseRevision>MAX_REVISION||kind==null
                    ||kind==Kind.SNAPSHOT&&baseRevision!=-1||kind==Kind.DELTA&&(baseRevision<0||baseRevision==Long.MAX_VALUE||revision!=baseRevision+1))
                throw new IllegalArgumentException("chart wire identity");
        }
    }
    /** Payload lifetime and its storage category remain with the source owner, never each recipient. */
    static final class Revision {
        private final byte[] logical;
        private final String digest;
        private final int chunks;
        private Revision(byte[] owned){logical=owned;digest=sha(owned);chunks=(owned.length+CHUNK_BYTES-1)/CHUNK_BYTES;}
        int bytes(){return logical.length;}
        int chunks(){return chunks;}
        String digest(){return digest;}
    }
    private final ObjectMapper mapper;
    OrderChartWireCodec(ObjectMapper mapper){this.mapper=Objects.requireNonNull(mapper);}
    /** Caller must reserve the serialized category before invoking this ownership-copy boundary. */
    Revision prepare(byte[] logical){
        Objects.requireNonNull(logical);if(logical.length<1||logical.length>MAX_LOGICAL)throw new IllegalArgumentException("chart logical capacity");
        return new Revision(logical.clone());
    }
    /** Caller reserves bounded preparation scratch before allocation; outbound takes its own wire lease. */
    byte[] chunk(Revision revision,Identity identity,int index){
        Objects.requireNonNull(revision);Objects.requireNonNull(identity);if(index<0||index>=revision.chunks)throw new IllegalArgumentException("chart chunk index");
        var header=mapper.createObjectNode().put("schemaVersion",1).put("type","order-chart-stream").put("subscriptionId",identity.subscriptionId())
                .put("scopeHash",identity.scopeHash()).put("epoch",identity.epoch()).put("revision",identity.revision()).put("baseRevision",identity.baseRevision())
                .put("kind",identity.kind().name()).put("chunkIndex",index).put("chunkCount",revision.chunks).put("totalBytes",revision.logical.length).put("sha256",revision.digest);
        final byte[] encoded;
        try{encoded=mapper.writeValueAsBytes(header);}catch(java.io.IOException e){throw new IllegalStateException("chart header serialization",e);}
        int offset=index*CHUNK_BYTES,length=Math.min(CHUNK_BYTES,revision.logical.length-offset);
        if(encoded.length>MAX_HEADER||8+encoded.length+length>MAX_FRAME)throw new IllegalStateException("chart frame capacity");
        return ByteBuffer.allocate(8+encoded.length+length).putInt(MAGIC).putInt(encoded.length).put(encoded).put(revision.logical,offset,length).array();
    }
    private static String sha(byte[] bytes){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}catch(NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}}
}

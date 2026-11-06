package app.feedgateway;

import java.io.IOException;
import java.nio.ByteBuffer;
import org.apache.avro.generic.GenericRecord;

/** Immutable ingest provenance plus one shared compressed legacy/full-book payload. */
final class RithmicNativeHistoryColumn {
    static final int MAX_BODY=507896;
    private final long emittedAtMs;
    private final RithmicNativeHistoryRequest.Scope namespace;
    private final byte[] legacyBody;
    private final boolean book;
    private final RithmicDepthJson.GapReason gapReason;
    private final Long breakBeforeMs;

    private RithmicNativeHistoryColumn(long emitted,RithmicNativeHistoryRequest.Scope namespace,
            byte[] body,boolean book,RithmicDepthJson.GapReason reason,Long boundary){
        if(emitted<0||emitted>RithmicNativeHistoryRequest.MAX_SAFE||body==null||book&&(namespace==null||reason!=null||body.length>MAX_BODY)||!book&&reason==null
                ||boundary!=null&&(boundary<Math.max(0,emitted-10001)||boundary>=emitted))throw new IllegalArgumentException("Invalid history provenance");
        this.emittedAtMs=emitted;this.namespace=namespace;this.legacyBody=body;this.book=book;this.gapReason=reason;this.breakBeforeMs=boundary;
    }

    /** BOOK is granted only by the shared typed ingest validator, never by response code. */
    static RithmicNativeHistoryColumn capture(RithmicDepthJson.Validation checked,long emitted,
            RithmicNativeHistoryRequest.Scope gapScope,Long boundary)throws IOException{
        if(checked==null)throw new IllegalArgumentException("Missing validation");
        if(checked.fullBook()){
            GenericRecord r=checked.record();
            if(!checked.renderable()||!Long.valueOf(emitted).equals(r.get("emittedAtMs")))throw new IllegalArgumentException("Invalid BOOK stamp");
            var scope=new RithmicNativeHistoryRequest.Scope(r.get("environment").toString(),r.get("exchange").toString(),r.get("contract").toString());
            String rendered=RithmicDepthJson.render(checked);
            if(rendered==null)return gap(emitted,scope,RithmicDepthJson.GapReason.OVERSIZE_COLUMN,boundary);
            byte[] body=RithmicDepthHistory.compress(rendered);
            return new RithmicNativeHistoryColumn(emitted,scope,body,body.length<=MAX_BODY,body.length<=MAX_BODY?null:RithmicDepthJson.GapReason.OVERSIZE_COLUMN,boundary);
        }
        return gap(emitted,gapScope,checked.gapReason()==null?RithmicDepthJson.GapReason.INVALID_RECORD:checked.gapReason(),boundary);
    }
    static RithmicNativeHistoryColumn gap(long emitted,RithmicNativeHistoryRequest.Scope scope,RithmicDepthJson.GapReason reason,Long boundary)throws IOException{
        return new RithmicNativeHistoryColumn(emitted,scope,RithmicDepthHistory.compress("{\"gapAtMs\":"+emitted+"}"),false,reason,boundary);
    }
    long emittedAtMs(){return emittedAtMs;}
    RithmicNativeHistoryRequest.Scope namespace(){return namespace;}
    boolean book(){return book;}
    RithmicDepthJson.GapReason gapReason(){return gapReason;}
    Long breakBeforeMs(){return breakBeforeMs;}
    int nativeBytes(){return book?legacyBody.length:0;}
    int retainedBytes(){return legacyBody.length;}
    byte[] legacyBytes(){return legacyBody.clone();}
    boolean sameContent(RithmicNativeHistoryColumn other){return other!=null&&emittedAtMs==other.emittedAtMs&&java.util.Objects.equals(namespace,other.namespace)&&book==other.book&&gapReason==other.gapReason&&java.util.Objects.equals(breakBeforeMs,other.breakBeforeMs)&&java.util.Arrays.equals(legacyBody,other.legacyBody);}
    void appendNative(ByteBuffer target){if(book)target.put(legacyBody);}
}

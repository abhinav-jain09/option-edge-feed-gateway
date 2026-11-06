package app.feedgateway;
import com.optionsedge.contracts.rithmic.RithmicSchemas;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashSet;
import java.util.List;
import org.apache.avro.generic.GenericRecord;
/** Final bounded display contract validation; browser JSON is never written back to Kafka. */
final class RithmicDepthJson {
    private static final ObjectMapper JSON=new ObjectMapper();
    enum GapReason { NOT_READY, INVALID_RECORD, EMPTY_BOOK, OVERSIZE_COLUMN, STREAM_CHANGE, SCAN_SKIP, RETENTION_EVICTED }
    record Validation(GenericRecord record,boolean fullBook,GapReason gapReason,int jsonBytes) {
        boolean renderable(){return record!=null;}
    }
    private static Validation invalid(GapReason reason){return new Validation(null,false,reason,0);}
    static boolean knownForeign(Object input,String configuredSystem){
        if(!RithmicNativeHistoryRequest.supportedSystem(configuredSystem)||!(input instanceof GenericRecord r)||!RithmicSchemas.FRAME.equals(r.getSchema()))return false;
        Object value=r.get("environment");String system=value==null?"":value.toString();
        return RithmicNativeHistoryRequest.supportedSystem(system)&&!configuredSystem.equals(system);
    }
    static Validation validate(String key,Object input,long now){
        try {
            if(!RithmicSchemas.KEY.equals(key)||!(input instanceof GenericRecord r)||!RithmicSchemas.FRAME.equals(r.getSchema()))return invalid(GapReason.INVALID_RECORD);
            if(!List.of("Rithmic Test","Rithmic Paper Trading").contains(r.get("environment").toString())||!"CME".equals(r.get("exchange").toString())||!"ESZ6".equals(r.get("contract").toString())
                ||(Integer)r.get("schemaVersion")!=1||((Long)r.get("sequence")<1||(Long)r.get("sequence")>9007199254740991L))return invalid(GapReason.INVALID_RECORD);
            String stream=r.get("streamId").toString();if(stream.isBlank()||stream.length()>128)return invalid(GapReason.INVALID_RECORD);
            long source=(Long)r.get("receivedAtMs"),emitted=(Long)r.get("emittedAtMs");
            if(source<0||source>now+5000||emitted<0||emitted<now-10000||emitted>now+5000||source>9007199254740991L||emitted>9007199254740991L)return invalid(GapReason.INVALID_RECORD);
            boolean mapped=Boolean.TRUE.equals(r.get("mappingReady"));Long basis=(Long)r.get("basisCents");
            Long basisTime=(Long)r.get("basisEventTimeMs");
            if(mapped&&(basisTime==null||basisTime<now-30000||basisTime>now+5000||!Boolean.TRUE.equals(r.get("connected"))||!Boolean.TRUE.equals(r.get("bookReady"))||source<now-10000
                ||basis==null||basis< -25000||basis>25000||!List.of("ANCHORED","MEASURED","PROJECTED").contains(r.get("basisState").toString())))return invalid(GapReason.NOT_READY);
            Long origin=(Long)r.get("basisMeasuredAtMs");
            if(mapped&&"PROJECTED".equals(r.get("basisState").toString())&&(origin==null||origin<=0||origin>now+5000))return invalid(GapReason.INVALID_RECORD);
            long bid=levels(r.get("bids"),basis,mapped,true),ask=levels(r.get("asks"),basis,mapped,false);
            if(mapped&&(bid<=0||ask==Long.MAX_VALUE))return invalid(GapReason.EMPTY_BOOK);
            if(mapped&&bid>=ask)return invalid(GapReason.INVALID_RECORD);
            long size=jsonSize(r,0);if(size>524288)return invalid(GapReason.OVERSIZE_COLUMN);
            return new Validation(r,mapped,mapped?null:GapReason.NOT_READY,(int)size);
        }catch(Exception invalid){return invalid(GapReason.INVALID_RECORD);}
    }
    static String render(String key,Object input,long now){return render(validate(key,input,now));}
    static String render(Validation checked){
        if(checked==null||!checked.renderable())return null;
        try{
            String result=JSON.writeValueAsString(AvroJson.toJsonNode(JSON,checked.record()));
            return result.getBytes(java.nio.charset.StandardCharsets.UTF_8).length<=524288?result:null;
        }catch(Exception invalid){return null;}
    }
    /** Count bounded canonical JSON bytes without serializing/allocating a tree before sampling. */
    private static long jsonSize(Object value,int depth){
        if(depth>4)throw new IllegalArgumentException();
        if(value==null)return 4;
        if(value instanceof Boolean b)return b?4:5;
        if(value instanceof Long||value instanceof Integer){long n=((Number)value).longValue();if(n==Long.MIN_VALUE)return 20;long digits=n<0?1:0;n=Math.abs(n);do{digits++;n/=10;}while(n!=0);return digits;}
        if(value instanceof CharSequence s)return stringSize(s.toString());
        if(value instanceof GenericRecord r){long total=2;boolean first=true;for(var f:r.getSchema().getFields()){total+=(first?0:1)+stringSize(f.name())+1+jsonSize(r.get(f.name()),depth+1);first=false;if(total>524288)return total;}return total;}
        if(value instanceof List<?> rows){if(rows.size()>4096)throw new IllegalArgumentException();long total=2;for(int i=0;i<rows.size();i++){total+=(i==0?0:1)+jsonSize(rows.get(i),depth+1);if(total>524288)return total;}return total;}
        throw new IllegalArgumentException();
    }
    private static int stringSize(String value){
        if(value.length()>1024)throw new IllegalArgumentException();int size=2;
        for(int i=0;i<value.length();i++){
            char c=value.charAt(i);
            if(c=='"'||c=='\\'||c=='\b'||c=='\f'||c=='\n'||c=='\r'||c=='\t')size+=2;
            else if(c<32)size+=6;
            else if(c<128)size++;
            else if(c<2048)size+=2;
            else if(Character.isHighSurrogate(c)){if(++i>=value.length()||!Character.isLowSurrogate(value.charAt(i)))throw new IllegalArgumentException();size+=4;}
            else if(Character.isLowSurrogate(c))throw new IllegalArgumentException();
            else size+=3;
        }return size;
    }
    private static long levels(Object input,Long basis,boolean mapped,boolean bid){
        if(!(input instanceof List<?> rows)||rows.size()>4096)throw new IllegalArgumentException();
        long best=bid?0:Long.MAX_VALUE;var seen=new HashSet<Long>();
        for(Object item:rows){var r=(GenericRecord)item;long ticks=(Long)r.get("esPriceTicks"),size=(Long)r.get("quantity");
            if(ticks<=0||ticks>4000000||size<=0||size>9007199254740991L||!seen.add(ticks))throw new IllegalArgumentException();
            Long spx=(Long)r.get("spxPriceCents");if(mapped&&(spx==null||spx!=ticks*25-basis||spx<=0))throw new IllegalArgumentException();
            if(!mapped&&spx!=null)throw new IllegalArgumentException();best=bid?Math.max(best,ticks):Math.min(best,ticks);
        }return best;
    }
}

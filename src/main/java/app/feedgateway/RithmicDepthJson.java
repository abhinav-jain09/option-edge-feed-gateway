package app.feedgateway;
import com.optionsedge.contracts.rithmic.RithmicSchemas;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashSet;
import java.util.List;
import org.apache.avro.generic.GenericRecord;
/** Final bounded display contract validation; browser JSON is never written back to Kafka. */
final class RithmicDepthJson {
    private static final ObjectMapper JSON=new ObjectMapper();
    static String render(String key,Object input,long now){
        try {
            if(!RithmicSchemas.KEY.equals(key)||!(input instanceof GenericRecord r)||!RithmicSchemas.FRAME.equals(r.getSchema()))return null;
            if(!"Rithmic Test".equals(r.get("environment").toString())||!"CME".equals(r.get("exchange").toString())||!"ESZ6".equals(r.get("contract").toString())
                ||(Integer)r.get("schemaVersion")!=1||((Long)r.get("sequence")<1||(Long)r.get("sequence")>9007199254740991L))return null;
            String stream=r.get("streamId").toString();if(stream.isBlank()||stream.length()>128)return null;
            long source=(Long)r.get("receivedAtMs"),emitted=(Long)r.get("emittedAtMs");
            if(source<0||source>now+5000||emitted<now-10000||emitted>now+5000)return null;
            boolean mapped=Boolean.TRUE.equals(r.get("mappingReady"));Long basis=(Long)r.get("basisCents");
            Long basisTime=(Long)r.get("basisEventTimeMs");
            if(mapped&&(basisTime==null||basisTime<now-30000||basisTime>now+5000||!Boolean.TRUE.equals(r.get("connected"))||!Boolean.TRUE.equals(r.get("bookReady"))||source<now-10000
                ||basis==null||basis< -25000||basis>25000||!List.of("ANCHORED","MEASURED","PROJECTED").contains(r.get("basisState").toString())))return null;
            Long origin=(Long)r.get("basisMeasuredAtMs");
            if(mapped&&"PROJECTED".equals(r.get("basisState").toString())&&(origin==null||origin<=0||origin>now+5000))return null;
            long bid=levels(r.get("bids"),basis,mapped,true),ask=levels(r.get("asks"),basis,mapped,false);
            if(mapped&&(bid<=0||ask==Long.MAX_VALUE||bid>=ask))return null;
            String result=JSON.writeValueAsString(AvroJson.toJsonNode(JSON,r));
            return result.getBytes(java.nio.charset.StandardCharsets.UTF_8).length<=524288?result:null;
        }catch(Exception invalid){return null;}
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

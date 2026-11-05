package app.feedgateway;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.optionsedge.contracts.rithmic.RithmicSchemas;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import org.apache.avro.generic.GenericRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.errors.WakeupException;
import org.springframework.stereotype.Component;

/** Separate bounded Kafka reader: historical display data never enters the live relay. */
@Component
final class RithmicDepthHistory {
    static final String REQUEST="rithmic-depth-history", RESPONSE="rithmic-liquidity-history";
    static final long WINDOW=2*60*60*1000L, MAX_BYTES=64L*1024*1024;
    static final int MAX_COLUMNS=7201, MAX_SCAN=160000, PAGE_COLUMNS=32;
    private static final ObjectMapper JSON=new ObjectMapper();
    private static final org.slf4j.Logger LOG=org.slf4j.LoggerFactory.getLogger(RithmicDepthHistory.class);
    private final GatewaySettings settings;
    private final TreeMap<Long,byte[]> columns=new TreeMap<>();
    private final LinkedHashMap<String,Long> requests=new LinkedHashMap<>();
    private final AtomicBoolean running=new AtomicBoolean();
    private long bytes,lastBucket=Long.MIN_VALUE;
    private boolean lastMapped;
    private String lastStream="";
    private volatile boolean limited;
    private volatile boolean ready;
    private volatile String sourceState="LOADING";
    private volatile KafkaConsumer<String,Object> consumer;
    private Thread thread;
    RithmicDepthHistory(GatewaySettings settings){this.settings=settings;}
    @PostConstruct void start(){if(!settings.rithmicDepthEnabled())return;running.set(true);thread=new Thread(this::run,"rithmic-depth-history");thread.setDaemon(true);thread.start();}
    private void run(){
        while(running.get()){
            Properties p=new Properties();p.put("bootstrap.servers",settings.bootstrapServers());p.put("client.id","rithmic-history");
            p.put("key.deserializer","org.apache.kafka.common.serialization.StringDeserializer");p.put("value.deserializer","io.confluent.kafka.serializers.KafkaAvroDeserializer");
            p.put("schema.registry.url",settings.schemaRegistryUrl());p.put("enable.auto.commit",false);p.put("isolation.level","read_committed");p.put("max.poll.records",100);
            p.put("fetch.max.bytes",1048576);p.put("max.partition.fetch.bytes",524288);p.put("default.api.timeout.ms",10000);p.put("request.timeout.ms",10000);
            p.put("http.connect.timeout.ms",1000);p.put("http.read.timeout.ms",1000);settings.applyKafkaSecurity(p);
            try(var c=new KafkaConsumer<String,Object>(p)){
                consumer=c;sourceState="LOADING";var parts=new ArrayList<TopicPartition>();for(var info:c.partitionsFor(RithmicSchemas.DISPLAY_TOPIC,Duration.ofSeconds(10)))parts.add(new TopicPartition(info.topic(),info.partition()));
                if(parts.size()!=1)throw new IllegalStateException("Depth history requires the single ordered partition");
                c.assign(parts);long now=System.currentTimeMillis();var ends=c.endOffsets(parts);var seek=c.offsetsForTimes(Map.of(parts.getFirst(),now-WINDOW));
                var offset=seek.get(parts.getFirst());c.seek(parts.getFirst(),offset==null?ends.get(parts.getFirst()):offset.offset());int scanned=0;
                while(running.get()){
                    for(var record:c.poll(Duration.ofMillis(250))){accept(record.key(),record.value(),System.currentTimeMillis());if(!ready&&++scanned>MAX_SCAN){limited=true;c.seekToEnd(parts);break;}}
                    if(!ready&&c.position(parts.getFirst())>=ends.get(parts.getFirst())){ready=true;sourceState="READY";synchronized(this){LOG.info("Recorded depth cache ready: {} columns, {} compressed bytes, bounded scan={}",columns.size(),bytes,limited);}}
                }
            }catch(WakeupException stopped){if(running.get()){ready=false;sourceState="UNAVAILABLE";}}catch(RuntimeException unavailable){ready=false;sourceState="UNAVAILABLE";LOG.warn("Recorded depth history unavailable ({})",unavailable.getClass().getSimpleName());}
            finally{consumer=null;}
            if(running.get())try{Thread.sleep(2000);}catch(InterruptedException e){Thread.currentThread().interrupt();break;}
        }
    }
    synchronized void accept(String key,Object value,long now){
        try{
            if(!RithmicSchemas.KEY.equals(key)||!(value instanceof GenericRecord r)||!RithmicSchemas.FRAME.equals(r.getSchema()))return;
            long emitted=(Long)r.get("emittedAtMs");if(emitted<now-WINDOW||emitted>now+5000)return;
            boolean mapped=Boolean.TRUE.equals(r.get("mappingReady"));long bucket=emitted/1000;String stream=r.get("streamId").toString();
            if(bucket==lastBucket&&mapped==lastMapped&&stream.equals(lastStream))return;
            String text=RithmicDepthJson.render(key,r,emitted);
            if(text==null||!mapped)text="{\"gapAtMs\":"+emitted+"}";
            if(!lastStream.isEmpty()&&!stream.equals(lastStream)){byte[] gap=compress("{\"gapAtMs\":"+(emitted-1)+"}");byte[] prior=columns.put(emitted-1,gap);bytes+=gap.length-(prior==null?0:prior.length);}
            byte[] compressed=compress(text);byte[] old=columns.put(emitted,compressed);bytes+=compressed.length-(old==null?0:old.length);
            lastBucket=bucket;lastMapped=mapped;lastStream=stream;
            while(!columns.isEmpty()&&(columns.size()>MAX_COLUMNS||bytes>MAX_BYTES||columns.firstKey()<now-WINDOW))bytes-=columns.pollFirstEntry().getValue().length;
        }catch(Exception invalid){/* Invalid data never becomes historical liquidity. */}
    }
    String page(String socket,JsonNode request,long now,int capacity){
        try{
            if(capacity<1024)return "{\"error\":\"capacity\"}";
            for(String field:List.of("fromMs","toMs","afterMs","minPriceCents","maxPriceCents"))if(!request.path(field).isIntegralNumber()||!request.path(field).canConvertToLong())return "{\"error\":\"invalid_request\"}";
            String id=request.path("requestId").asText();long from=request.path("fromMs").asLong(-1),to=request.path("toMs").asLong(-1),after=request.path("afterMs").asLong(-1);
            long low=request.path("minPriceCents").asLong(-1),high=request.path("maxPriceCents").asLong(-1);
            if(id.isEmpty()||id.length()>64||from<0||to<=from||to-from>WINDOW+20000||to>now+5000||low<=0||high<=low||high>100000000||after<from-1||after>to)return "{\"error\":\"invalid_request\"}";
            List<Map.Entry<Long,byte[]>> items;long earliest;
            synchronized(this){
                Long previous=requests.get(socket);if(previous!=null&&now>=previous&&now-previous<100)return JSON.writeValueAsString(Map.of("requestId",id,"error","busy"));
                requests.put(socket,now);while(requests.size()>1024)requests.remove(requests.keySet().iterator().next());
                earliest=columns.isEmpty()?now:columns.firstKey();items=columns.tailMap(Math.max(from,after+1),true).entrySet().stream().takeWhile(e->e.getKey()<=to).limit(PAGE_COLUMNS+1).map(e->Map.entry(e.getKey(),e.getValue())).toList();
            }
            var result=JSON.createObjectNode();result.put("requestId",id);result.put("ready",ready);result.put("sourceState",sourceState);result.put("availableFromMs",earliest);result.put("sampleMs",1000);result.put("limited",limited);
            ArrayNode frames=result.putArray("frames");long cursor=after;int used=768;
            for(var item:items){if(frames.size()>=PAGE_COLUMNS)break;var frame=JSON.readTree(expand(item.getValue()));
                if(!frame.has("gapAtMs"))for(String side:List.of("bids","asks")){ArrayNode levels=JSON.createArrayNode();for(var level:frame.path(side)){long price=level.path("spxPriceCents").asLong(-1);if(price>=low&&price<=high)levels.add(level);}((com.fasterxml.jackson.databind.node.ObjectNode)frame).set(side,levels);}
                int size=JSON.writeValueAsBytes(frame).length+1;if(used+size>Math.min(capacity,524288)){if(frames.isEmpty())return JSON.writeValueAsString(Map.of("requestId",id,"error","range_too_wide"));break;}
                frames.add(frame);used+=size;cursor=item.getKey();
            }
            result.put("afterMs",cursor);result.put("done",items.isEmpty()||cursor>=items.getLast().getKey());return JSON.writeValueAsString(result);
        }catch(Exception invalid){return "{\"error\":\"invalid_request\"}";}
    }
    static byte[] compress(String value)throws IOException{var out=new ByteArrayOutputStream();try(var gzip=new GZIPOutputStream(out)){gzip.write(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));}return out.toByteArray();}
    static byte[] expand(byte[] value)throws IOException{try(var gzip=new GZIPInputStream(new ByteArrayInputStream(value))){byte[] result=gzip.readNBytes(524289);if(result.length>524288)throw new IOException("History column bound");return result;}}
    @PreDestroy void stop(){running.set(false);var c=consumer;if(c!=null)c.wakeup();if(thread!=null)try{thread.join(5000);}catch(InterruptedException e){Thread.currentThread().interrupt();}}
}

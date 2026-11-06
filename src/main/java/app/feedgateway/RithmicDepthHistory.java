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
    static final long WINDOW=2*60*60*1000L, MAX_BYTES=128L*1024*1024;
    static final int MAX_COLUMNS=8192, MAX_SCAN=160000, PAGE_COLUMNS=32;
    private static final ObjectMapper JSON=new ObjectMapper();
    private static final org.slf4j.Logger LOG=org.slf4j.LoggerFactory.getLogger(RithmicDepthHistory.class);
    private final GatewaySettings settings;
    private final TreeMap<Long,RithmicNativeHistoryColumn> columns=new TreeMap<>();
    private final TreeMap<Long,RithmicNativeHistoryPacket.Gap> sourceGaps=new TreeMap<>();
    private final Map<String,Long> limitedUntil=new HashMap<>();
    private final LinkedHashMap<String,Long> requests=new LinkedHashMap<>();
    private final LinkedHashMap<String,Long> nativeRequests=new LinkedHashMap<>();
    private final AtomicBoolean running=new AtomicBoolean();
    private long bytes,lateRevision;
    private String lastIdentity="",lastStream="",cacheIncarnation=UUID.randomUUID().toString();
    private Long sourceAvailableFromMs;
    private boolean metadataUnavailable;
    private long metadataLostThrough=-1;
    private Long pendingScanFrom;
    private String lastOversizeIdentity="";
    private long lastOversizeAt=-1;
    private volatile boolean limited;
    private volatile boolean ready;
    private volatile String sourceState="LOADING";
    private volatile KafkaConsumer<String,Object> consumer;
    private Thread thread;
    @org.springframework.beans.factory.annotation.Autowired(required=false)
    private org.springframework.beans.factory.ObjectProvider<FeedGatewayService> gateway;
    private long mutationAffected=Long.MAX_VALUE;
    private boolean mutationBumped;
    private void notifySubscribers(Long affected){
        var service=gateway==null?null:gateway.getIfAvailable();
        if(service!=null)service.rithmicHistoryChanged(affected);
    }
    synchronized String nativeHello(){
        prune(System.currentTimeMillis());
        try {
            var root=JSON.createObjectNode();root.put("format",RithmicNativeHistoryRequest.FORMAT);
            root.putObject("namespace").put("environment",settings.rithmicSystem()).put("exchange","CME").put("contract","ESZ6");
            root.put("sourceState",metadataUnavailable?"UNAVAILABLE":sourceState);root.put("ready",ready&&!metadataUnavailable);root.put("cacheIncarnation",cacheIncarnation);root.put("lateRevision",lateRevision);root.put("limited",limited);
            if(columns.isEmpty())root.putNull("availableThroughMs");else root.put("availableThroughMs",columns.lastKey());
            return root.toString();
        }catch(RuntimeException invalid){return "{\"ready\":false,\"sourceState\":\"UNAVAILABLE\"}";}
    }
    synchronized RithmicHistoryInvalidation invalidation(long affected){return new RithmicHistoryInvalidation(new RithmicNativeHistoryRequest.Scope(settings.rithmicSystem(),"CME","ESZ6"),cacheIncarnation,lateRevision,Math.max(0,affected));}
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
                    for(var record:c.poll(Duration.ofMillis(250))){accept(record.key(),record.value(),System.currentTimeMillis());if(!ready&&++scanned>MAX_SCAN){markScanSkip(System.currentTimeMillis());c.seekToEnd(parts);break;}}
                    if(!ready&&c.position(parts.getFirst())>=ends.get(parts.getFirst())){ready=true;sourceState="READY";synchronized(this){LOG.info("Recorded depth cache ready: {} columns, {} compressed bytes, bounded scan={}",columns.size(),bytes,limited);}notifySubscribers(null);}
                }
            }catch(WakeupException stopped){if(running.get()){ready=false;sourceState="UNAVAILABLE";}}catch(RuntimeException unavailable){ready=false;sourceState="UNAVAILABLE";LOG.warn("Recorded depth history unavailable ({})",unavailable.getClass().getSimpleName());}
            finally{consumer=null;}
            if(running.get())try{Thread.sleep(2000);}catch(InterruptedException e){Thread.currentThread().interrupt();break;}
        }
    }
    void accept(String key,Object value,long now){
        Long affected=null;boolean first;
        synchronized(this){boolean empty=columns.isEmpty();long before=lateRevision;String incarnation=cacheIncarnation;mutationAffected=Long.MAX_VALUE;mutationBumped=false;acceptLocked(key,value,now);first=empty&&!columns.isEmpty();if(before!=lateRevision||!incarnation.equals(cacheIncarnation))affected=mutationAffected==Long.MAX_VALUE?Math.max(0,now-WINDOW):mutationAffected;}
        if(affected!=null||first&&ready)notifySubscribers(affected);
    }
    private void acceptLocked(String key,Object value,long now){
        try{
            if(!RithmicSchemas.KEY.equals(key)||!(value instanceof GenericRecord r)||!RithmicSchemas.FRAME.equals(r.getSchema()))return;
            if(RithmicDepthJson.knownForeign(r,settings.rithmicSystem()))return;
            long emitted=(Long)r.get("emittedAtMs");if(emitted<now-WINDOW||emitted>now+5000)return;
            Long previousMaximum=columns.isEmpty()?null:columns.lastKey();
            var checked=RithmicDepthJson.validate(key,r,emitted);
            RithmicNativeHistoryRequest.Scope scope=null;
            String environment=String.valueOf(r.get("environment")),exchange=String.valueOf(r.get("exchange")),contract=String.valueOf(r.get("contract"));
            if(RithmicNativeHistoryRequest.supportedSystem(environment)&&"CME".equals(exchange)&&"ESZ6".equals(contract))scope=new RithmicNativeHistoryRequest.Scope(environment,exchange,contract);
            String rawStream=String.valueOf(r.get("streamId"));boolean known=scope!=null&&!rawStream.isBlank()&&rawStream.length()<=128;
            String stream=known?rawStream:"UNKNOWN",identityPrefix=(known?scope.environment()+"|CME|ESZ6|"+stream:"UNKNOWN")+"|"+(emitted/1000)+"|"+Boolean.TRUE.equals(r.get("mappingReady"))+"|"+Boolean.TRUE.equals(r.get("connected"))+"|"+Boolean.TRUE.equals(r.get("bookReady"))+"|";
            String identity=identityPrefix+checked.fullBook()+"|"+checked.gapReason();
            if(identity.equals(lastIdentity)&&!columns.containsKey(emitted))return;
            // Compressed-oversize recovery is retried at most four times per source second.
            if(identity.equals(lastOversizeIdentity)&&now>=lastOversizeAt&&now-lastOversizeAt<250)return;
            Long boundary=null;
            if(checked.fullBook()&&!lastStream.isEmpty()&&!stream.equals(lastStream)){
                long first=Math.min(emitted,(Long)r.get("receivedAtMs"));
                if(first==0)checked=new RithmicDepthJson.Validation(null,false,RithmicDepthJson.GapReason.STREAM_CHANGE,0);
                else{
                    boundary=first-1;
                    if(emitted>0&&!columns.containsKey(emitted-1))put(RithmicNativeHistoryColumn.gap(emitted-1,scope,RithmicDepthJson.GapReason.STREAM_CHANGE,null));
                    rememberGap(boundary,boundary+1,RithmicDepthJson.GapReason.STREAM_CHANGE);
                }
            }
            var column=RithmicNativeHistoryColumn.capture(checked,emitted,scope,boundary);
            if(column.gapReason()==RithmicDepthJson.GapReason.OVERSIZE_COLUMN&&checked.fullBook()){lastOversizeIdentity=identity;lastOversizeAt=now;}else{lastOversizeIdentity="";lastOversizeAt=-1;}
            put(column);
            if(boundary!=null&&previousMaximum!=null&&boundary<=previousMaximum)bumpRevision(boundary-10000);
            if(pendingScanFrom!=null&&emitted>=pendingScanFrom){rememberGap(Math.min(pendingScanFrom,emitted),emitted+1,RithmicDepthJson.GapReason.SCAN_SKIP);pendingScanFrom=null;}
            if(!column.book()){
                var previous=columns.lowerEntry(emitted);long start=emitted;
                if(previous!=null&&!previous.getValue().book()&&previous.getValue().gapReason()==column.gapReason())start=previous.getKey();
                rememberGap(start,emitted+1,column.gapReason());
            }
            if(checked.fullBook())lastStream=stream;
            lastIdentity=identityPrefix+column.book()+"|"+column.gapReason();
            if(sourceAvailableFromMs==null)sourceAvailableFromMs=emitted;else sourceAvailableFromMs=Math.min(sourceAvailableFromMs,emitted);
            Long lostFrom=null,lostThrough=null;
            while(!columns.isEmpty()&&(columns.size()>MAX_COLUMNS||bytes>MAX_BYTES||columns.firstKey()<now-WINDOW)){
                boolean sizeLoss=columns.size()>MAX_COLUMNS,byteLoss=bytes>MAX_BYTES;var old=columns.pollFirstEntry();bytes-=old.getValue().retainedBytes();
                if(old.getKey()>=now-WINDOW&&(sizeLoss||byteLoss)){lostFrom=lostFrom==null?old.getKey():Math.min(lostFrom,old.getKey());lostThrough=old.getKey();if(sizeLoss)limitedUntil.merge("COLUMN_EVICTION",old.getKey()+WINDOW,Math::max);if(byteLoss)limitedUntil.merge("BYTE_EVICTION",old.getKey()+WINDOW,Math::max);}
            }
            if(lostFrom!=null)rememberGap(lostFrom,columns.isEmpty()?lostThrough+1:columns.firstKey(),RithmicDepthJson.GapReason.RETENTION_EVICTED);
            prune(now);
        }catch(Exception invalid){/* Invalid data never becomes historical liquidity. */}
    }
    void markScanSkip(long now){
        long affected;
        synchronized(this){mutationBumped=false;affected=columns.isEmpty()?Math.max(0,now-WINDOW):Math.min(now,columns.lastKey());pendingScanFrom=affected;if(!columns.isEmpty())rememberGap(affected,columns.lastKey()+1,RithmicDepthJson.GapReason.SCAN_SKIP);limitedUntil.put("SCAN_LIMIT",now+WINDOW);limited=true;bumpRevision(affected);}
        notifySubscribers(affected);
    }
    private void bumpRevision(long affected){
        mutationAffected=Math.min(mutationAffected,Math.max(0,affected));
        if(mutationBumped)return;mutationBumped=true;
        if(lateRevision==RithmicNativeHistoryRequest.MAX_SAFE){cacheIncarnation=UUID.randomUUID().toString();lateRevision=0;}else lateRevision++;
    }
    private void put(RithmicNativeHistoryColumn next){
        var existing=columns.get(next.emittedAtMs());if(next.sameContent(existing))return;
        Long maximum=columns.isEmpty()?null:columns.lastKey();var old=columns.put(next.emittedAtMs(),next);bytes+=next.retainedBytes()-(old==null?0:old.retainedBytes());
        if(maximum!=null&&next.emittedAtMs()<=maximum)bumpRevision(next.emittedAtMs()-10000);
    }
    private void rememberGap(long start,long end,RithmicDepthJson.GapReason reason){
        var all=new ArrayList<>(sourceGaps.values());all.add(new RithmicNativeHistoryPacket.Gap(start,end,reason));all.sort(Comparator.comparingLong(RithmicNativeHistoryPacket.Gap::startMs));
        var merged=new ArrayList<RithmicNativeHistoryPacket.Gap>();
        for(var gap:all){
            var previous=merged.isEmpty()?null:merged.getLast();
            if(previous!=null&&(gap.startMs()<previous.endMs()||gap.startMs()==previous.endMs()&&gap.gapReason()==previous.gapReason())){
                var cause=previous.gapReason()==gap.gapReason()?gap.gapReason():RithmicDepthJson.GapReason.INVALID_RECORD;
                merged.set(merged.size()-1,new RithmicNativeHistoryPacket.Gap(previous.startMs(),Math.max(previous.endMs(),gap.endMs()),cause));
            }else merged.add(gap);
        }
        if(merged.size()>2048){metadataUnavailable=true;metadataLostThrough=Math.max(metadataLostThrough,end);return;}
        sourceGaps.clear();for(var gap:merged)sourceGaps.put(gap.startMs(),gap);
    }
    private void prune(long now){
        long floor=Math.max(0,now-WINDOW);if(metadataUnavailable&&metadataLostThrough>=0&&metadataLostThrough<=floor){metadataUnavailable=false;metadataLostThrough=-1;}while(!columns.isEmpty()&&columns.firstKey()<floor)bytes-=columns.pollFirstEntry().getValue().retainedBytes();if(sourceAvailableFromMs!=null)sourceAvailableFromMs=Math.max(sourceAvailableFromMs,floor);
        sourceGaps.entrySet().removeIf(e->e.getValue().endMs()<=floor);
        if(!sourceGaps.isEmpty()&&sourceGaps.firstKey()<floor){var gap=sourceGaps.pollFirstEntry().getValue();sourceGaps.put(floor,new RithmicNativeHistoryPacket.Gap(floor,gap.endMs(),gap.gapReason()));}
        limitedUntil.entrySet().removeIf(e->e.getValue()<now);limited=!limitedUntil.isEmpty();
    }
    RithmicNativeHistoryPacket.Reply nativePage(String socket,RithmicNativeHistoryRequest.Request request,long now,int capacity){
        RithmicNativeHistoryPacket.Snapshot snapshot;
        synchronized(this){
            if(!RithmicNativeHistoryRequest.supportedSystem(settings.rithmicSystem())||!request.namespace().environment().equals(settings.rithmicSystem()))return new RithmicNativeHistoryPacket.Reply(null,"unsupported_namespace");
            prune(now);if(metadataUnavailable)return new RithmicNativeHistoryPacket.Reply(null,"unavailable");
            Long previous=nativeRequests.get(socket);if(previous!=null&&now>=previous&&now-previous<50)return new RithmicNativeHistoryPacket.Reply(null,"busy");
            if(previous==null&&nativeRequests.size()>=1024)return new RithmicNativeHistoryPacket.Reply(null,"busy");nativeRequests.put(socket,now);prune(now);
            var items=columns.tailMap(Math.max(request.fromMs(),request.afterMs()+1),true).values().stream().takeWhile(c->c.emittedAtMs()<=request.toMs()).limit(33).toList();
            var reasons=new ArrayList<String>(limitedUntil.keySet());if(limited&&reasons.isEmpty())reasons.add("SCAN_LIMIT");
            snapshot=new RithmicNativeHistoryPacket.Snapshot(items,sourceState,columns.isEmpty()?null:columns.lastKey(),columns.isEmpty()?null:columns.firstKey(),sourceAvailableFromMs,limited,reasons,cacheIncarnation,lateRevision,new ArrayList<>(sourceGaps.values()));
        }
        return RithmicNativeHistoryPacket.build(request,snapshot,capacity);
    }
    synchronized void forget(String socket){requests.remove(socket);nativeRequests.remove(socket);}
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
                earliest=columns.isEmpty()?now:columns.firstKey();items=columns.tailMap(Math.max(from,after+1),true).entrySet().stream().takeWhile(e->e.getKey()<=to).limit(PAGE_COLUMNS+1).map(e->Map.entry(e.getKey(),e.getValue().legacyBytes())).toList();
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
    static byte[] compress(String value)throws IOException{var out=new ByteArrayOutputStream();try(var gzip=new GZIPOutputStream(out){{def.setLevel(java.util.zip.Deflater.BEST_SPEED);}}){gzip.write(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));}return out.toByteArray();}
    static byte[] expand(byte[] value)throws IOException{try(var gzip=new GZIPInputStream(new ByteArrayInputStream(value))){byte[] result=gzip.readNBytes(524289);if(result.length>524288)throw new IOException("History column bound");return result;}}
    @PreDestroy void stop(){running.set(false);var c=consumer;if(c!=null)c.wakeup();if(thread!=null)try{thread.join(5000);}catch(InterruptedException e){Thread.currentThread().interrupt();}}
}

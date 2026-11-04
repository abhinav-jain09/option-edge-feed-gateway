package app.feedgateway;

import com.optionsedge.contracts.rithmic.RithmicSchemas;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.avro.generic.GenericRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.errors.WakeupException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/** One read-only display-topic tail per gateway replica; uses existing authenticated socket fanout. */
@Component
final class RithmicDepthRelay {
    private final GatewaySettings settings;
    private final ObjectProvider<FeedGatewayService> gateway;
    private final AtomicBoolean running=new AtomicBoolean();
    static final long DISPLAY_INTERVAL_MS=250;
    private record Cached(String json,long source,long emitted,long basisTime,boolean mapped,String stream,long sequence) {}
    private Cached latest, published;
    private long publishedAt;
    private volatile KafkaConsumer<String,Object> consumer;
    private Thread thread;
    RithmicDepthRelay(GatewaySettings settings,ObjectProvider<FeedGatewayService> gateway){this.settings=settings;this.gateway=gateway;}
    @PostConstruct void start(){
        if(!settings.rithmicDepthEnabled())return;
        running.set(true);thread=new Thread(this::run,"rithmic-depth-relay");thread.setDaemon(true);thread.start();
    }
    private void run(){
        while(running.get()){
            Properties p=new Properties();p.put("bootstrap.servers",settings.bootstrapServers());p.put("client.id","rithmic-depth-relay");
            p.put("key.deserializer","org.apache.kafka.common.serialization.StringDeserializer");
            p.put("value.deserializer","io.confluent.kafka.serializers.KafkaAvroDeserializer");
            p.put("schema.registry.url",settings.schemaRegistryUrl());p.put("specific.avro.reader",false);
            p.put("enable.auto.commit",false);p.put("isolation.level","read_committed");p.put("max.poll.records",100);
            p.put("fetch.max.bytes",1048576);p.put("max.partition.fetch.bytes",524288);p.put("request.timeout.ms",10000);
            p.put("default.api.timeout.ms",10000);p.put("http.connect.timeout.ms",1000);p.put("http.read.timeout.ms",1000);
            p.put("max.schemas.per.subject",64);settings.applyKafkaSecurity(p);
            try(var c=new KafkaConsumer<String,Object>(p)){
                consumer=c;var partitions=new ArrayList<TopicPartition>();
                for(var partition:c.partitionsFor(RithmicSchemas.DISPLAY_TOPIC,Duration.ofSeconds(10)))
                    partitions.add(new TopicPartition(partition.topic(),partition.partition()));
                if(partitions.isEmpty()||partitions.size()>16)throw new IllegalStateException("Depth topic partition bounds");
                c.assign(partitions);c.seekToEnd(partitions);
                while(running.get()){
                    for(var record:c.poll(Duration.ofMillis(100)))
                        accept(record.key(),record.value(),System.currentTimeMillis());
                    flush(System.currentTimeMillis());
                }
            }catch(WakeupException stopped){if(running.get())clear();}
            catch(RuntimeException unavailable){clear();}
            finally{consumer=null;}
            if(running.get())try{Thread.sleep(2000);}catch(InterruptedException e){Thread.currentThread().interrupt();break;}
        }
    }
    synchronized void accept(String key,Object value,long now){
        String json=RithmicDepthJson.render(key,value,now);
        if(json!=null){
            var r=(GenericRecord)value;boolean mapped=Boolean.TRUE.equals(r.get("mappingReady"));
            String stream=r.get("streamId").toString();long sequence=(Long)r.get("sequence");
            if(latest!=null&&latest.stream().equals(stream)&&sequence<=latest.sequence())return;
            latest=new Cached(json,(Long)r.get("receivedAtMs"),(Long)r.get("emittedAtMs"),mapped?(Long)r.get("basisEventTimeMs"):0,mapped,stream,sequence);
            // Snapshot cadence bounds browser ingress; status/recovery and stream changes stay immediate.
            if(published==null||published.mapped()!=mapped||!published.stream().equals(stream))publish(now);
            else flush(now);
        }
    }
    synchronized void flush(long now){
        if(latest!=null&&latest!=published&&(now-publishedAt>=DISPLAY_INTERVAL_MS||now<publishedAt)&&fresh(latest,now))publish(now);
    }
    private void publish(long now){
        gateway.getObject().streamRithmicDepth(latest.json());published=latest;publishedAt=now;
    }
    private static boolean fresh(Cached value,long now){
        return value.emitted()>=now-10000&&value.emitted()<=now+5000
            &&(!value.mapped()||(value.source()>=now-10000&&value.source()<=now+5000&&value.basisTime()>=now-30000&&value.basisTime()<=now+5000));
    }
    synchronized void replay(java.util.function.Consumer<String> send){
        var value=latest;if(value!=null&&fresh(value,System.currentTimeMillis()))send.accept(value.json());
    }
    private synchronized void clear(){latest=null;published=null;publishedAt=0;}
    @PreDestroy void stop(){running.set(false);var c=consumer;if(c!=null)c.wakeup();if(thread!=null)try{thread.join(5000);}catch(InterruptedException e){Thread.currentThread().interrupt();}}
}

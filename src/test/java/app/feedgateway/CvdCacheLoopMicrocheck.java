package app.feedgateway;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.management.ManagementFactory;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.TopicPartition;

/** One prime plus five fixed producer-fixture passes through the cache loop; no broker or socket load. */
public class CvdCacheLoopMicrocheck {
    public static void main(String[] args) throws Exception {
        var fixtures=new ObjectMapper().readTree(Path.of(args[0]).toFile());
        var method=FeedGatewayService.class.getDeclaredMethod("stringObjectConsumerProperties",String.class);method.setAccessible(true);
        var cpu=ManagementFactory.getThreadMXBean();var allocated=(com.sun.management.ThreadMXBean)cpu;long thread=Thread.currentThread().threadId();
        for(int pass=0;pass<6;pass++) {
            var service=FootprintWiringTest.on();var broker=new FootprintStrikeDeliveryTest.FakeBroker();
            var bindings=new LinkedHashMap<String,FeedGatewayService.TopicBinding>();
            var settings=new GatewaySettings();
            for(var fixture:fixtures) {
                String name=fixture.path("schema").asText();
                String topic=switch(name) {
                    case "CvdSnapshot" -> settings.esCvdTopic();case "CvdBarEnvelope" -> settings.esCvdBarsTopic();
                    case "FootprintSnapshot" -> settings.esFootprintTopic();case "FootprintEvidenceSnapshot" -> settings.esFootprintEvidenceTopic();
                    case "FootprintBarEnvelope" -> settings.esFootprintBarsTopic();case "FootprintOutcome" -> settings.esFootprintOutcomesTopic();
                    default -> throw new AssertionError(name);
                };
                String event=switch(name) {
                    case "CvdSnapshot" -> "es-cvd";case "CvdBarEnvelope" -> "es-cvd-bar";case "FootprintSnapshot" -> "es-footprint";
                    case "FootprintEvidenceSnapshot" -> "es-footprint-evidence";case "FootprintBarEnvelope" -> "es-footprint-bar";case "FootprintOutcome" -> "es-footprint-outcome";default -> throw new AssertionError(name);
                };
                broker.logs.computeIfAbsent(topic,k->new java.util.concurrent.CopyOnWriteArrayList<>()).add(fixture.get("value").toString());
                broker.present.add(topic);bindings.put(topic,new FeedGatewayService.TopicBinding("DATABENTO",event));
            }
            int limit=Integer.parseInt(((Properties)method.invoke(service,"state")).getProperty(ConsumerConfig.MAX_POLL_RECORDS_CONFIG));
            var consumer=broker.consumer();
            int[] delivered={0};
            when(consumer.poll(any(Duration.class))).thenAnswer(inv->{
                var batch=new LinkedHashMap<TopicPartition,List<ConsumerRecord<String,Object>>>();int remaining=limit;
                for(var tp:broker.assigned) {
                    long offset=broker.positions.getOrDefault(tp,0L);var values=broker.logs.get(tp.topic());
                    int count=(int)Math.min(remaining,values.size()-offset);
                    if(count>0) {
                        var rows=new ArrayList<ConsumerRecord<String,Object>>(count);
                        for(int i=0;i<count;i++)rows.add(new ConsumerRecord<>(tp.topic(),tp.partition(),offset+i,"ES",values.get((int)offset+i)));
                        batch.put(tp,rows);broker.positions.put(tp,offset+count);remaining-=count;
                    }
                    if(remaining==0)break;
                }
                delivered[0]+=batch.values().stream().mapToInt(List::size).sum();broker.polls.incrementAndGet();
                if(broker.assigned.stream().allMatch(tp->broker.positions.getOrDefault(tp,0L)>=broker.end(tp)))service.setRunningForTest(false);
                return new ConsumerRecords<>(batch,Map.of());
            });
            service.setRunningForTest(true);long before=allocated.getThreadAllocatedBytes(thread);long cpuStart=cpu.getCurrentThreadCpuTime(),wall=System.nanoTime();
            service.runCacheConsumerAttemptForTest("state",bindings,new AtomicBoolean(),consumer);
            long wallNs=System.nanoTime()-wall,cpuNs=cpu.getCurrentThreadCpuTime()-cpuStart,bytes=allocated.getThreadAllocatedBytes(thread)-before;
            if (delivered[0] != 9) throw new IllegalStateException("Expected nine retained-window fixture rows; live snapshots seek to end");
            System.out.printf("{\"pass\":%d,\"prime\":%s,\"fixtures\":%d,\"maxPollRecords\":%d,\"polls\":%d,\"cpuNs\":%d,\"wallNs\":%d,\"allocatedBytes\":%d}%n",pass,pass==0,fixtures.size(),limit,broker.polls.get(),cpuNs,wallNs,bytes);
            service.setRunningForTest(false);consumer.close();
        }
    }
}

package app.feedgateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.lang.management.ManagementFactory;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.kafka.clients.consumer.ConsumerRecord;

/** Fixed ingestion units, not a gateway/feed/load/restore test. Construction is excluded. */
public final class DirectionReaderMicrocheck {
    private static final com.sun.management.ThreadMXBean CPU=(com.sun.management.ThreadMXBean)ManagementFactory.getThreadMXBean();
    private static volatile Object witness;
    private static void sample(String name,Runnable op) throws Exception {
        op.run();var cpu=new ArrayList<Long>();var bytes=new ArrayList<Long>();
        for(int i=0;i<5;i++) {
            long id=Thread.currentThread().threadId(),allocation=CPU.getThreadAllocatedBytes(id),start=CPU.getCurrentThreadCpuTime();
            op.run();long duration=CPU.getCurrentThreadCpuTime()-start,allocated=CPU.getThreadAllocatedBytes(id)-allocation;
            cpu.add(duration);bytes.add(allocated);
        }
        boolean pass=cpu.stream().allMatch(n->n>=0&&n<=100_000_000L)&&bytes.stream().allMatch(n->n>=0&&n<=8_388_608L);
        System.out.println(new ObjectMapper().writeValueAsString(Map.of("path",name,"cpuNs",cpu,"allocatedBytes",bytes,"within100ms8MiB",pass)));
        if(!pass)throw new AssertionError(name);
    }
    public static void main(String[] args) throws Exception {
        if(!CPU.isCurrentThreadCpuTimeSupported()||!CPU.isThreadAllocatedMemorySupported())throw new IllegalStateException("CPU/allocation counters required");
        CPU.setThreadCpuTimeEnabled(true);CPU.setThreadAllocatedMemoryEnabled(true);
        long before=CPU.getThreadAllocatedBytes(Thread.currentThread().threadId());witness=new byte[1024];
        if(CPU.getThreadAllocatedBytes(Thread.currentThread().threadId())-before<1024)throw new AssertionError("allocation counter positive control");
        var json=new ObjectMapper();ObjectNode model=null;
        try(var input=DirectionReaderMicrocheck.class.getResourceAsStream("/cvd/direction-reader-models.json")) {
            for(var row:json.readTree(input).path("models"))if(row.path("schema").asText().equals("DirectionPush")&&!row.path("model").has("tombstone")){model=(ObjectNode)row.path("model");break;}
        }
        if(model==null)throw new AssertionError("source push fixture required");long now=System.currentTimeMillis()-50;
        String push=model.put("ts",now).put("eventTMs",now).put("sessionDate",java.time.LocalDate.now(java.time.ZoneId.of("America/New_York")).toString()).toString();
        String deletion="{\"symbol\":\"SPX\",\"tombstone\":true}";var settings=new GatewaySettings();
        for(String arm:List.of("upsert","legacy-null","typed-delete","stale-after-delete")) {
            var service=new FeedGatewayService(settings,json,new HpsfGatewayViewMapper(),null);var offset=new AtomicLong();
            sample(arm,()->{
                long n=offset.addAndGet(3);service.onDirectionPushRecord(new ConsumerRecord<>(settings.directionPushTopic(),0,n,"SPX",push),push,false);
                if(!arm.equals("upsert")) {
                    Object value=arm.equals("legacy-null")?null:deletion;String text=value==null?null:deletion;
                    service.onDirectionPushRecord(new ConsumerRecord<>(settings.directionPushTopic(),0,n+2,"SPX",value),text,false);
                    if(arm.equals("stale-after-delete"))service.onDirectionPushRecord(new ConsumerRecord<>(settings.directionPushTopic(),0,n+1,"SPX",push),push,false);
                }
            });
        }
    }
}

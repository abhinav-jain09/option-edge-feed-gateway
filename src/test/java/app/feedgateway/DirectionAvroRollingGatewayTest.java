package app.feedgateway;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.optionsedge.contracts.avro.*;
import com.optionsedge.contracts.cvd.CvdRelatedSchemas;
import io.confluent.kafka.schemaregistry.testutil.MockSchemaRegistry;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.avro.Schema;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.errors.SerializationException;
import org.junit.jupiter.api.Test;

class DirectionAvroRollingGatewayTest {
    private final GatewaySettings settings=new GatewaySettings();
    private final ObjectMapper json=new ObjectMapper();
    private FeedGatewayService service(){return new FeedGatewayService(settings,json,new HpsfGatewayViewMapper(),null);}
    private JsonNode fixtures() throws Exception {
        try(var in=getClass().getResourceAsStream("/cvd/direction-reader-models.json")){return json.readTree(in).get("models");}
    }
    private ObjectNode push() throws Exception {
        for(var row:fixtures())if(row.path("schema").asText().equals("DirectionPush")&&!row.path("model").has("tombstone")) {
            var model=(ObjectNode)row.path("model").deepCopy();long now=System.currentTimeMillis()-50;
            return model.put("ts",now).put("eventTMs",now).put("sessionDate",java.time.LocalDate.now(java.time.ZoneId.of("America/New_York")).toString());
        }
        throw new AssertionError("source push cut required");
    }
    @SuppressWarnings("unchecked") private Map<String,String> cache(FeedGatewayService service) throws Exception {
        var f=FeedGatewayService.class.getDeclaredField("directionPush");f.setAccessible(true);return(Map<String,String>)f.get(service);
    }
    private long forwarded(FeedGatewayService service) throws Exception {
        var f=FeedGatewayService.class.getDeclaredField("forwardedEvents");f.setAccessible(true);return((AtomicLong)f.get(service)).get();
    }
    private ConsumerRecord<String,Object> record(long offset,Object value){return new ConsumerRecord<>(settings.directionPushTopic(),0,offset,"SPX",value);}
    @Test void allFourSourceModelsUseReleasedReadersAndOnlyDeclaredRetainedNullsAreAllowed() throws Exception {
        var service=service();String scope="direction-reader-gateway";
        var method=FeedGatewayService.class.getDeclaredMethod("stringObjectConsumerProperties",String.class);method.setAccessible(true);
        assertSame(CvdRelatedSchemas.directionCurrent(),service.cvdAvroBindings().get(settings.directionCurrentTopic()));
        assertSame(CvdRelatedSchemas.directionPush(),service.cvdAvroBindings().get(settings.directionPushTopic()));
        assertSame(CvdRelatedSchemas.directionAlert(),service.cvdAvroBindings().get(settings.directionAlertTopic()));
        assertSame(CvdRelatedSchemas.directionScorecard(),service.cvdAvroBindings().get(settings.directionScorecardTopic()));
        assertSame(CvdRelatedSchemas.ALIGNED_STRIKE_INTELLIGENCE,service.cvdAvroBindings().get(settings.esStrikeIntelSpxAlignedTopic()));
        var properties=(Properties)method.invoke(service,"state");properties.put("schema.registry.url","mock://"+scope);
        var config=new HashMap<String,Object>();properties.forEach((key,value)->config.put((String)key,value));
        var topics=Map.of("DirectionCurrent",settings.directionCurrentTopic(),"DirectionPush",settings.directionPushTopic(),
                "DirectionAlert",settings.directionAlertTopic(),"DirectionScorecard",settings.directionScorecardTopic());
        var schemas=Map.of("DirectionCurrent",CvdRelatedSchemas.directionCurrent(),"DirectionPush",CvdRelatedSchemas.directionPush(),
                "DirectionAlert",CvdRelatedSchemas.directionAlert(),"DirectionScorecard",CvdRelatedSchemas.directionScorecard());
        assertEquals(19,service.cvdAvroBindings().size());
        assertSame(CvdRelatedSchemas.ALIGNED_STRIKE_INTELLIGENCE,service.cvdAvroBindings().get(settings.esStrikeIntelSpxAlignedTopic()));
        assertEquals(CanonicalModelDeserializer.class,config.get(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG));
        assertEquals("read_committed",config.get(ConsumerConfig.ISOLATION_LEVEL_CONFIG));assertEquals("false",config.get(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG));
        assertTrue(Integer.parseInt((String)config.get(ConsumerConfig.MAX_POLL_RECORDS_CONFIG))<=8);
        var registry=MockSchemaRegistry.getClientForScope(scope);
        try(var reader=new CanonicalModelDeserializer();var writer=new CanonicalAvroSerializer(registry)) {
            config.put("auto.register.schemas",false);reader.configure(config,false);writer.configure(config,false);
            for(var name:topics.keySet()) {
                assertSame(schemas.get(name),service.cvdAvroBindings().get(topics.get(name)));
                registry.register(topics.get(name)+"-value",schemas.get(name));
            }
            assertEquals(9,fixtures().size());
            for(var row:fixtures()) {
                String name=row.path("schema").asText(),topic=topics.get(name),text=row.path("model").toString();
                byte[] wire=writer.serialize(topic,CanonicalJsonAvro.record(schemas.get(name),row.path("model")));
                for(byte[] input:List.of(text.getBytes(StandardCharsets.UTF_8),wire,text.getBytes(StandardCharsets.UTF_8)))
                    assertEquals(text,reader.deserialize(topic,input));
                String wrong=topics.get(name.equals("DirectionCurrent")?"DirectionAlert":"DirectionCurrent");
                assertThrows(SerializationException.class,()->reader.deserialize(wrong,wire));
                assertThrows(SerializationException.class,()->reader.deserialize(topic,new byte[]{0,0,0}));
            }
            assertNull(assertDoesNotThrow(()->reader.deserialize(settings.directionPushTopic(),null)));
            assertNull(reader.deserialize(settings.esStrikeIntelSpxAlignedTopic(),null));
            for(String topic:List.of(settings.directionCurrentTopic(),settings.directionAlertTopic(),settings.directionScorecardTopic()))
                assertThrows(SerializationException.class,()->reader.deserialize(topic,null));
        } finally{MockSchemaRegistry.dropScope(scope);}
    }
    @Test void legacyNullAndTypedDeletionClearWithoutOlderConsumerRecordsResurrectingState() throws Exception {
        var service=service();String push=push().toString();
        service.onDirectionPushRecord(record(1,push),push,false);assertEquals(push,cache(service).get("DATABENTO|SPX"));
        service.onDirectionPushRecord(record(3,null),null,false);assertTrue(cache(service).isEmpty());assertEquals(0,forwarded(service));
        service.onDirectionPushRecord(record(2,push),push,true);assertTrue(cache(service).isEmpty());assertEquals(0,forwarded(service));
        service.onDirectionPushRecord(record(3,null),null,true);assertEquals(1,forwarded(service));
        service.onDirectionPushRecord(record(3,null),null,true);assertEquals(1,forwarded(service));
        service.onDirectionPushRecord(record(4,push),push,true);assertEquals(push,cache(service).get("DATABENTO|SPX"));assertEquals(2,forwarded(service));
        service.onDirectionPushRecord(record(3,null),null,true);assertEquals(push,cache(service).get("DATABENTO|SPX"));assertEquals(2,forwarded(service));
        String deletion="{\"symbol\":\"SPX\",\"tombstone\":true}";
        service.onDirectionPushRecord(record(5,deletion),deletion,true);assertTrue(cache(service).isEmpty());assertEquals(3,forwarded(service));
        service.onDirectionPushRecord(record(4,push),push,true);assertTrue(cache(service).isEmpty());assertEquals(3,forwarded(service));
        service.onDirectionPushRecord(new ConsumerRecord<>(settings.directionPushTopic(),1,6,"SPX",push),push,true);
        assertTrue(cache(service).isEmpty());assertEquals(3,forwarded(service));
    }
    @Test void malformedFalseOversizeWrongIdentityAndEscapedDeletionHaveExplicitSemantics() throws Exception {
        var service=service();String push=push().toString();service.onDirectionPushRecord(record(1,push),push,false);
        for(String bad:List.of("{\"symbol\":\"SPX\",\"tombstone\":false}","{\"symbol\":\"SPX\",\"tombstone\":\"true\"}",
                "{\"symbol\":\"ES\",\"tombstone\":true}","{\"symbol\":\"SPX\",\"tombstone\":true,\"x\":\""+"x".repeat(4096)+"\"}","{\"tombstone\":true")) {
            service.onDirectionPushRecord(record(2,bad),bad,true);assertEquals(push,cache(service).get("DATABENTO|SPX"));
        }
        service.onDirectionPushRecord(new ConsumerRecord<>(settings.directionPushTopic(),0,2,"ES",null),null,true);
        service.onDirectionPushRecord(new ConsumerRecord<>("unrelated",0,2,"SPX",null),null,true);
        assertEquals(push,cache(service).get("DATABENTO|SPX"));assertEquals(0,forwarded(service));
        String escaped="{\"symbol\":\"SPX\",\""+"\\u0074ombstone\":true}";
        service.onDirectionPushRecord(record(2,escaped),escaped,true);assertTrue(cache(service).isEmpty());assertEquals(1,forwarded(service));
    }
    @Test void markerExpiresWhenNoEarlierPushCanStillBeFresh() throws Exception {
        var service=service();service.onDirectionPushRecord(record(1,null),null,false);
        var field=FeedGatewayService.class.getDeclaredField("directionPushWithdrawal");field.setAccessible(true);assertNotNull(field.get(service));
        System.setProperty("GATEWAY_DIRECTION_PUSH_TTL_MS","0");
        try {service.onDirectionPushRecord(record(2,""),"",false);assertNull(field.get(service));}
        finally{System.clearProperty("GATEWAY_DIRECTION_PUSH_TTL_MS");}
    }
    @Test void cachedReplayAndWithdrawalUseTheSameEmitLock() throws Exception {
        var service=service();String push=push().toString();service.onDirectionPushRecord(record(1,push),push,false);
        var entered=new java.util.concurrent.CountDownLatch(1);var release=new java.util.concurrent.CountDownLatch(1);
        var failure=new java.util.concurrent.atomic.AtomicReference<Throwable>();
        var slow=new java.util.concurrent.ConcurrentHashMap<String,String>(cache(service)) {
            @Override public Set<Map.Entry<String,String>> entrySet() {
                Set<Map.Entry<String,String>> original=super.entrySet();
                return new AbstractSet<>() {
                    public int size(){return original.size();}
                    public Iterator<Map.Entry<String,String>> iterator() {
                        entered.countDown();
                        try {if(!release.await(5,java.util.concurrent.TimeUnit.SECONDS))throw new AssertionError("replay gate timed out");}
                        catch(InterruptedException e){throw new AssertionError(e);}
                        return original.iterator();
                    }
                };
            }
        };
        var field=FeedGatewayService.class.getDeclaredField("directionPush");field.setAccessible(true);field.set(service,slow);
        var emit=FeedGatewayService.class.getDeclaredField("directionPushEmitLock");emit.setAccessible(true);Object lock=emit.get(service);
        var replay=FeedGatewayService.class.getDeclaredMethod("replayDirectionPushCached",org.springframework.web.socket.WebSocketSession.class);replay.setAccessible(true);
        Thread reader=new Thread(()->{try{replay.invoke(service,(Object)null);}catch(Throwable e){failure.set(e);}},"direction-replay-unit");
        Thread deletion=new Thread(()->{try{service.onDirectionPushRecord(record(2,null),null,false);}catch(Throwable e){failure.set(e);}},"direction-delete-unit");
        try {
            reader.start();assertTrue(entered.await(3,java.util.concurrent.TimeUnit.SECONDS));deletion.start();
            var threads=java.lang.management.ManagementFactory.getThreadMXBean();long deadline=System.nanoTime()+3_000_000_000L;
            java.lang.management.ThreadInfo info;
            do {info=threads.getThreadInfo(deletion.threadId());if(info==null||info.getThreadState()==Thread.State.BLOCKED)break;Thread.sleep(1);}
            while(System.nanoTime()<deadline);
            assertNotNull(info,"withdrawal must wait for replay, not complete ahead of it");
            assertEquals(Thread.State.BLOCKED,info.getThreadState());assertEquals(reader.threadId(),info.getLockOwnerId());
            assertNotNull(info.getLockInfo());assertEquals(System.identityHashCode(lock),info.getLockInfo().getIdentityHashCode());
        } finally {release.countDown();reader.join(4000);if(deletion.getState()!=Thread.State.NEW)deletion.join(4000);}
        assertFalse(reader.isAlive());assertFalse(deletion.isAlive());assertNull(failure.get());assertTrue(cache(service).isEmpty());
    }

}

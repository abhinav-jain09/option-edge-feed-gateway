package app.feedgateway;

import static org.junit.jupiter.api.Assertions.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.optionsedge.contracts.avro.CanonicalAvroKafka;
import com.optionsedge.contracts.avro.CanonicalJsonAvro;
import com.optionsedge.contracts.avro.CanonicalModelDeserializer;
import io.confluent.kafka.schemaregistry.testutil.MockSchemaRegistry;
import io.confluent.kafka.serializers.KafkaAvroSerializer;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.apache.avro.Schema;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.junit.jupiter.api.Test;

class CvdAvroRollingGatewayTest {
    @Test void liveCacheAndHistoryBuildersReadRealProducerModelsOnTheirExistingTopicNames() throws Exception {
        String scope="cvd-gateway-rolling";
        GatewaySettings settings=new GatewaySettings();
        var service=new FeedGatewayService(settings,new ObjectMapper(),new HpsfGatewayViewMapper(),null);
        try(var input=getClass().getResourceAsStream("/cvd/producer-23866b17.json")) {
            assertNotNull(input);JsonNode fixtures=new ObjectMapper().readTree(input);
            for(String builder:List.of("stringConsumerProperties","stringObjectConsumerProperties")) {
                var method=FeedGatewayService.class.getDeclaredMethod(builder,String.class);method.setAccessible(true);
                var props=(Properties)method.invoke(service,"state-test");
                assertEquals("false",props.get(ConsumerConfig.ALLOW_AUTO_CREATE_TOPICS_CONFIG));
                assertEquals("1",props.get(ConsumerConfig.MAX_POLL_RECORDS_CONFIG));
                assertEquals(settings.schemaRegistryUrl(),props.get("schema.registry.url"));
                CanonicalAvroKafka.producer(props,"mock://"+scope);
                var config=new HashMap<String,Object>();props.forEach((k,v)->config.put((String)k,v));
                try(var writer=new KafkaAvroSerializer();var reader=new CanonicalModelDeserializer()) {
                    writer.configure(config,false);reader.configure(config,false);
                    for(JsonNode fixture:fixtures) {
                        String name=fixture.path("schema").asText();
                        String topic=switch(name) {
                            case "CvdBarEnvelope" -> settings.esCvdBarsTopic();
                            case "CvdSnapshot" -> settings.esCvdTopic();
                            case "FootprintBarEnvelope" -> settings.esFootprintBarsTopic();
                            case "FootprintOutcome" -> settings.esFootprintOutcomesTopic();
                            case "FootprintSnapshot" -> settings.esFootprintTopic();
                            case "FootprintEvidenceSnapshot" -> settings.esFootprintEvidenceTopic();
                            default -> throw new AssertionError(name);
                        };
                        Schema schema=service.cvdAvroBindings().get(topic);assertNotNull(schema);
                        MockSchemaRegistry.getClientForScope(scope).register(topic+"-value",schema);
                        JsonNode model=fixture.get("value");String legacy=model.toString();
                        assertEquals(legacy,reader.deserialize(topic,legacy.getBytes(StandardCharsets.UTF_8)));
                        byte[] wire=writer.serialize(topic,CanonicalJsonAvro.record(schema,model));assertEquals(0,wire[0]);
                        same(model,new ObjectMapper().readTree(reader.deserialize(topic,wire)));
                        assertEquals(legacy,reader.deserialize(topic,legacy.getBytes(StandardCharsets.UTF_8)));
                    }
                }
            }
        } finally {MockSchemaRegistry.dropScope(scope);}
    }

    private static void same(JsonNode expected,JsonNode actual) {
        if(expected.isNumber()){assertTrue(actual.isNumber());assertEquals(0,expected.decimalValue().compareTo(actual.decimalValue()));return;}
        if(expected.isObject()){assertEquals(expected.size(),actual.size());expected.fields().forEachRemaining(e->{assertTrue(actual.has(e.getKey()));same(e.getValue(),actual.get(e.getKey()));});}
        else if(expected.isArray()){assertEquals(expected.size(),actual.size());for(int i=0;i<expected.size();i++)same(expected.get(i),actual.get(i));}
        else assertEquals(expected,actual);
    }
}

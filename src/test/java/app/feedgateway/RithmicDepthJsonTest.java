package app.feedgateway;
import com.optionsedge.contracts.rithmic.RithmicSchemas;
import java.util.List;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class RithmicDepthJsonTest {
    @Test void paperTradingDepthIsAdmittedWithoutRelabelling() {
        var input=frame();input.put("environment","Rithmic Paper Trading");
        var output=RithmicDepthJson.render(RithmicSchemas.KEY,input,NOW);
        assertNotNull(output);assertTrue(output.contains("Rithmic Paper Trading"));
        input.put("environment","unknown");assertNull(RithmicDepthJson.render(RithmicSchemas.KEY,input,NOW));
    }
    static final long NOW=1800000000000L;
    static GenericRecord frame(){
        var r=new GenericData.Record(RithmicSchemas.FRAME);
        r.put("schemaVersion",1);r.put("environment","Rithmic Test");r.put("exchange","CME");r.put("contract","ESZ6");r.put("streamId","source");r.put("sequence",1L);
        r.put("receivedAtMs",NOW);r.put("emittedAtMs",NOW);r.put("connected",true);r.put("bookReady",true);r.put("mappingReady",true);
        r.put("basisCents",3250L);r.put("basisState","MEASURED");r.put("basisEventTimeMs",NOW);r.put("basisMeasuredAtMs",NOW);r.put("reason","OK");
        r.put("bids",List.of(level(30360)));r.put("asks",List.of(level(30361)));return r;
    }
    static GenericRecord level(long ticks){var r=new GenericData.Record(RithmicSchemas.FRAME.getField("bids").schema().getElementType());r.put("esPriceTicks",ticks);r.put("quantity",15L);r.put("spxPriceCents",ticks*25-3250);return r;}
    @Test void canonicalMappedFrameKeepsExactPricesOnTheSocket() throws Exception {
        String result=RithmicDepthJson.render(RithmicSchemas.KEY,frame(),NOW);assertNotNull(result);
        var value=new com.fasterxml.jackson.databind.ObjectMapper().readTree(result);
        assertEquals(755750L,value.path("bids").get(0).path("spxPriceCents").longValue());assertEquals(15L,value.path("bids").get(0).path("quantity").longValue());
        assertTrue(FeedGatewayService.isGlobalBroadcastEvent("rithmic-liquidity"));
    }
    @Test void wrongIdentityStaleSourceAndIncorrectTranslationAreRefused(){
        var r=frame();assertNull(RithmicDepthJson.render("other",r,NOW));
        r.put("receivedAtMs",NOW-10001);assertNull(RithmicDepthJson.render(RithmicSchemas.KEY,r,NOW));
        r=frame();((GenericRecord)((List<?>)r.get("bids")).getFirst()).put("spxPriceCents",755751L);assertNull(RithmicDepthJson.render(RithmicSchemas.KEY,r,NOW));
    }
    @Test void recentUnavailableStatusCanInvalidateAnOlderBook(){
        var r=frame();r.put("mappingReady",false);r.put("basisCents",null);r.put("basisState","UNAVAILABLE");r.put("receivedAtMs",NOW-20000);
        r.put("bids",List.of());r.put("asks",List.of());r.put("reason","SOURCE_STALE");assertNotNull(RithmicDepthJson.render(RithmicSchemas.KEY,r,NOW));
        r.put("emittedAtMs",NOW-10001);assertNull(RithmicDepthJson.render(RithmicSchemas.KEY,r,NOW));
    }
}

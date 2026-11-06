package app.feedgateway;
import com.optionsedge.contracts.rithmic.RithmicSchemas;
import java.util.List;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class RithmicDepthJsonTest {
    @Test void typedValidationDistinguishesFullBookFromRenderableUnavailableStatus(){
        var valid=RithmicDepthJson.validate(RithmicSchemas.KEY,frame(),NOW);assertTrue(valid.fullBook());assertTrue(valid.renderable());assertNull(valid.gapReason());
        var status=frame();status.put("mappingReady",false);status.put("basisCents",null);status.put("bids",List.of());status.put("asks",List.of());
        var checked=RithmicDepthJson.validate(RithmicSchemas.KEY,status,NOW);assertFalse(checked.fullBook());assertTrue(checked.renderable());assertEquals(RithmicDepthJson.GapReason.NOT_READY,checked.gapReason());assertNotNull(RithmicDepthJson.render(checked));
    }
    @Test void typedRefusalReasonsAreBoundedAndCannotBePromotedIntoBooks(){
        var empty=frame();empty.put("bids",List.of());assertEquals(RithmicDepthJson.GapReason.EMPTY_BOOK,RithmicDepthJson.validate(RithmicSchemas.KEY,empty,NOW).gapReason());
        var disconnected=frame();disconnected.put("connected",false);assertEquals(RithmicDepthJson.GapReason.NOT_READY,RithmicDepthJson.validate(RithmicSchemas.KEY,disconnected,NOW).gapReason());
        var wrong=frame();((GenericRecord)((List<?>)wrong.get("bids")).getFirst()).put("spxPriceCents",1L);var invalid=RithmicDepthJson.validate(RithmicSchemas.KEY,wrong,NOW);assertFalse(invalid.fullBook());assertFalse(invalid.renderable());assertEquals(RithmicDepthJson.GapReason.INVALID_RECORD,invalid.gapReason());
    }
    @Test void canonicalSizeIsCountedWithoutSerializationAndMatchesEscapedUtf8Output(){
        for(String reason:List.of("OK","escaped \" \\ \n \u0001 € 🟢","x".repeat(1024))){var r=frame();r.put("reason",reason);var checked=RithmicDepthJson.validate(RithmicSchemas.KEY,r,NOW);assertTrue(checked.fullBook());assertEquals(RithmicDepthJson.render(checked).getBytes(java.nio.charset.StandardCharsets.UTF_8).length,checked.jsonBytes());}
        var tooLong=frame();tooLong.put("reason","x".repeat(1025));assertFalse(RithmicDepthJson.validate(RithmicSchemas.KEY,tooLong,NOW).renderable());
    }
    @Test void maxWidthFullBookIsAnExplicitOversizeGapRatherThanClippedDepth(){
        var r=frame();var bids=new java.util.ArrayList<GenericRecord>();var asks=new java.util.ArrayList<GenericRecord>();
        for(int i=0;i<4096;i++){var b=level(30360-i);b.put("quantity",9007199254740991L);bids.add(b);var a=level(30361+i);a.put("quantity",9007199254740991L);asks.add(a);}r.put("bids",bids);r.put("asks",asks);
        var checked=RithmicDepthJson.validate(RithmicSchemas.KEY,r,NOW);assertEquals(RithmicDepthJson.GapReason.OVERSIZE_COLUMN,checked.gapReason());assertFalse(checked.fullBook());assertNull(RithmicDepthJson.render(checked));
    }
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

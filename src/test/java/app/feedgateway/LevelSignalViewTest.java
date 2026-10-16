package app.feedgateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class LevelSignalViewTest {
    static final ObjectMapper M=new ObjectMapper();
    static final String DATE="2026-10-15", TF="1m";
    static final long START=Instant.parse("2026-10-15T13:59:00Z").toEpochMilli(), END=START+60_000, NOW=END+5_000;
    static ObjectNode level(String id,String role,double price) {
        return M.valueToTree(Map.of("levelId",id,"role",role,"price",price,"state","ACTIVE","sinceMs",START-60_000));
    }
    static ObjectNode structure() {
        ObjectNode n=M.createObjectNode();n.put("schemaVersion",1).put("service","market-structure").put("state","OK").put("sessionDate",DATE).put("asOfMs",END);
        n.putArray("levels").add(level("R","RESISTANCE",6025)).add(level("S","SUPPORT",6011))
            .add(level("far","RESISTANCE",6040)).add(level("broken","SUPPORT",6012).put("state","BROKEN"))
            .add(level("neutral","NEUTRAL",6010));return n;
    }
    static ObjectNode snapshot(String symbol) {
        ObjectNode n=M.createObjectNode();n.put("schemaVersion",1).put("symbol",symbol).put("tradeDate",DATE)
            .put("serviceState","RUNNING").put("marketState","OPEN").put("publishedAt",Instant.ofEpochMilli(NOW).toString())
            .put("asOfEventTime",Instant.ofEpochMilli(NOW).toString()).put("lastTickAgeMs",0).put("ingestSilenceMs",0);
        ObjectNode frame=n.putObject("timeframes").putObject(TF);
        frame.putObject("lastFinalizedBar").put("barId","bar-1").put("timeframe",TF).put("final",true).put("status","COMPLETE")
            .put("barStart",Instant.ofEpochMilli(START).toString()).put("barEnd",Instant.ofEpochMilli(END).toString())
            .put("open",6020).put("high",6030).put("low",6010).put("close",6015);
        ObjectNode ex=frame.putObject("exhaustion");ex.put("state","READY").put("buying","DETECTED").put("selling","NONE");
        var arr=ex.putArray("evidence");
        for(String direction:List.of("BUYING","SELLING")) for(String name:List.of("RSI_DIVERGENCE","AGGRESSOR_SHARE","REJECTION_CLOSE","VOLUME_CLIMAX")) {
            boolean fired=direction.equals("BUYING") && (symbol.equals("ES") || name.equals("RSI_DIVERGENCE") || name.equals("REJECTION_CLOSE"));
            arr.addObject().put("direction",direction).put("name",name).put("state","READY").put("fired",fired);
        }
        n.putObject("bias").putObject(TF).put("bias","BEARISH").put("evaluatedBarId","bar-1").put("evaluatedAt",Instant.ofEpochMilli(END).toString());return n;
    }
    static LevelSignalView.Reading read(ObjectNode levels,ObjectNode spx,ObjectNode es) {
        return LevelSignalView.read(M,levels.toString(),spx==null?null:spx.toString(),es==null?null:es.toString(),DATE,TF,NOW);
    }
    @Test void joinsExistingEvidenceOnlyAtTestedActiveLevelsWithExplicitSourceAndBarTime() {
        var r=read(structure(),snapshot("SPX"),snapshot("ES"));assertEquals("READY",r.status());assertEquals(3,r.levels().size());
        var resistance=r.levels().get(0);assertEquals("TESTED",resistance.state());assertEquals(END,resistance.barEndMs());
        assertEquals("Closed below resistance",resistance.relation());
        assertTrue(resistance.signals().stream().anyMatch(s->s.source().equals("SPX") && s.name().equals("REJECTION_CLOSE")));
        assertTrue(resistance.signals().stream().anyMatch(s->s.source().equals("ES") && s.name().equals("AGGRESSOR_SHARE")));
        assertFalse(resistance.signals().stream().anyMatch(s->s.source().equals("SPX") && s.name().equals("VOLUME_CLIMAX")));
        assertEquals("Closed above support",r.levels().get(1).relation());
        assertFalse(r.levels().get(1).signals().stream().anyMatch(s->s.name().equals("EXHAUSTION")),"buying exhaustion must not be relabelled selling exhaustion");
        assertEquals("NOT_TESTED",r.levels().get(2).state());assertTrue(r.levels().get(2).signals().isEmpty());
    }
    @Test void preservesContradictoryBullishBiasAtResistanceInsteadOfInventingBearishVerdict() {
        var s=snapshot("SPX");((ObjectNode)s.path("bias").path(TF)).put("bias","BULLISH");
        assertTrue(read(structure(),s,null).levels().getFirst().signals().stream().anyMatch(v->v.text().equals("Bullish bias")));
    }
    @Test void newlyConfirmedLevelCannotClaimAnEarlierBarTest() {
        var levels=structure();((ObjectNode)levels.path("levels").get(0)).put("sinceMs",END);
        assertEquals("NOT_TESTED",read(levels,snapshot("SPX"),null).levels().getFirst().state());
    }
    @Test void wrongOrStaleOrRestoringSnapshotsCannotProduceConfirmation() {
        List<ObjectNode> bad=new ArrayList<>();
        bad.add(snapshot("ES"));bad.add(snapshot("SPX").put("tradeDate","2026-10-14"));
        bad.add(snapshot("SPX").put("serviceState","RESTORING"));bad.add(snapshot("SPX").put("marketState","CLOSED"));
        bad.add(snapshot("SPX").put("asOfEventTime",Instant.ofEpochMilli(NOW-20_001).toString()));
        bad.add(snapshot("SPX").put("publishedAt",Instant.ofEpochMilli(NOW+1).toString()));
        bad.add(snapshot("SPX").put("lastTickAgeMs",20_001));bad.add(snapshot("SPX").put("ingestSilenceMs",20_001));
        for(var s:bad) {var r=read(structure(),s,snapshot("ES"));assertEquals("PARTIAL",r.status());assertEquals("UNAVAILABLE",r.levels().getFirst().state());assertTrue(r.levels().getFirst().signals().isEmpty());}
    }
    @Test void esEvidenceMustReferToExactlyTheSameClosedBar() {
        var es=snapshot("ES");((ObjectNode)es.path("timeframes").path(TF).path("lastFinalizedBar"))
            .put("barStart",Instant.ofEpochMilli(START-60_000).toString()).put("barEnd",Instant.ofEpochMilli(END-60_000).toString());
        assertFalse(read(structure(),snapshot("SPX"),es).levels().getFirst().signals().stream().anyMatch(s->s.source().equals("ES")));
    }
    @Test void warmingEvidenceAndMismatchedBiasIdentityCannotMasqueradeAsConfirmation() {
        var s=snapshot("SPX");((ObjectNode)s.path("timeframes").path(TF).path("exhaustion")).put("state","WARMING_UP");
        ((ObjectNode)s.path("bias").path(TF)).put("evaluatedBarId","older-bar");
        assertTrue(read(structure(),s,null).levels().getFirst().signals().isEmpty());
    }
    @Test void malformedOrFormingBarCannotSupplyClosedBarEvidence() {
        for(String field:List.of("final","status","high","barEnd")) {
            var s=snapshot("SPX");var bar=(ObjectNode)s.path("timeframes").path(TF).path("lastFinalizedBar");
            switch(field) {case "final" -> bar.put(field,false);case "status" -> bar.put(field,"EMPTY");case "high" -> bar.put(field,6000);default -> bar.put(field,Instant.ofEpochMilli(END+1).toString());}
            assertEquals("UNAVAILABLE",read(structure(),s,null).levels().getFirst().state());
        }
    }
    @Test void staleUnavailableWrongSessionAndMalformedLevelsAreHidden() {
        for(ObjectNode levels:List.of(structure().put("asOfMs",NOW-180_001),structure().put("state","UNAVAILABLE"),
                structure().put("sessionDate","2026-10-14"),structure().put("asOfMs",NOW+1),structure().put("schemaVersion","1")))
            assertTrue(read(levels,snapshot("SPX"),null).levels().isEmpty());
        var duplicate=structure();((com.fasterxml.jackson.databind.node.ArrayNode)duplicate.path("levels")).add(level("R","RESISTANCE",6025));
        assertTrue(read(duplicate,snapshot("SPX"),null).levels().isEmpty());
        assertTrue(LevelSignalView.read(M,structure()+"{}",null,null,DATE,TF,NOW).levels().isEmpty());
        assertFalse(LevelSignalView.validRequest("2026-02-30",TF));assertFalse(LevelSignalView.validRequest(DATE,"30s"));
    }
    @Test void expirationIsBoundToOldestContributingSource() {
        var es=snapshot("ES").put("asOfEventTime",Instant.ofEpochMilli(NOW-4000).toString());
        assertEquals(NOW+16000,read(structure(),snapshot("SPX"),es).validUntilMs());
    }
}

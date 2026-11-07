package app.feedgateway;

import static org.junit.jupiter.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

class OrderChartSubscriptionTest {
    private final ObjectMapper mapper=new ObjectMapper();
    private ObjectNode request() {
        ObjectNode n=mapper.createObjectNode().put("schemaVersion",1).put("subscriptionId","native_1");
        n.putObject("scope").put("environment","dev").put("symbol","SPX").put("sessionDate","2026-11-09").put("timeframe","1m")
            .putArray("resources").add("delta-flow").add("path").add("vwap").add("signals");return n;
    }
    private OrderChartSubscription parse(ObjectNode n){return OrderChartSubscription.parse(mapper,n.toString(),"dev");}
    @Test void fixedResourcesHaveCanonicalIdentityRegardlessOfRequestOrdering() {
        var n=request();var first=parse(n);((ObjectNode)n.path("scope")).putArray("resources").add("signals").add("path").add("delta-flow").add("vwap");
        assertEquals(first,parse(n));assertNull(first.resume());assertEquals(64,first.scope().hash().length());
        n.putObject("resume").put("scopeHash",first.scope().hash()).put("epoch","a".repeat(32)).put("revision",7);
        assertEquals(7,parse(n).resume().revision());
        ((ObjectNode)n.path("scope")).put("timeframe","5m");assertThrows(IllegalArgumentException.class,()->parse(n));
    }
    @Test void authorityAndExactDateIdentityCannotBeCoerced() {
        for(String env:new String[]{"prod","DEV"," dev","dev\n"}){var n=request();((ObjectNode)n.path("scope")).put("environment",env);assertThrows(IllegalArgumentException.class,()->parse(n));}
        for(String date:new String[]{"2026-02-29","20261109","2026-11-9","2026-11-09T00:00:00Z"}){var n=request();((ObjectNode)n.path("scope")).put("sessionDate",date);assertThrows(IllegalArgumentException.class,()->parse(n));}
        for(String symbol:new String[]{"ES","MES","spx","SPX "}){var n=request();((ObjectNode)n.path("scope")).put("symbol",symbol);assertThrows(IllegalArgumentException.class,()->parse(n));}
        // Scope parsing never imposes a cash-session calendar on a valid Globex/source date.
        var holiday=request();((ObjectNode)holiday.path("scope")).put("sessionDate","2026-11-26");assertEquals("2026-11-26",parse(holiday).scope().sessionDate());
    }
    @Test void malformedOrPartialResourceAndCursorRequestsFailClosed() {
        for(String tf:new String[]{"1","2m","60m",""}){var n=request();((ObjectNode)n.path("scope")).put("timeframe",tf);assertThrows(IllegalArgumentException.class,()->parse(n));}
        var partial=request();((ObjectNode)partial.path("scope")).putArray("resources").add("path");assertThrows(IllegalArgumentException.class,()->parse(partial));
        var n=request();((ObjectNode)n.path("scope")).putArray("resources").add("path").add("path").add("vwap").add("signals");var duplicate=n;assertThrows(IllegalArgumentException.class,()->parse(duplicate));
        for(long revision:new long[]{-1,OrderChartWireCodec.MAX_REVISION+1}){var r=request();String scopeHash=parse(r).scope().hash();r.putObject("resume").put("scopeHash",scopeHash).put("epoch","a".repeat(32)).put("revision",revision);assertThrows(IllegalArgumentException.class,()->parse(r));}
        var r=request();r.putNull("resume");assertThrows(IllegalArgumentException.class,()->parse(r));
        var extra=request().put("ignored","anything");assertThrows(IllegalArgumentException.class,()->parse(extra));
        var fractional=request().put("schemaVersion",1.0);assertThrows(IllegalArgumentException.class,()->parse(fractional));
    }
    @Test void actualUtf8BudgetAndUnknownFieldsAreCheckedBeforeRetention() {
        assertThrows(IllegalArgumentException.class,()->OrderChartSubscription.parse(mapper," ".repeat(2049),"dev"));
        String unicode="{\"schemaVersion\":1,\"subscriptionId\":\""+"€".repeat(800)+"\"}";assertTrue(unicode.length()<2048);assertThrows(IllegalArgumentException.class,()->OrderChartSubscription.parse(mapper,unicode,"dev"));
        for(String raw:new String[]{"null","[]","true","{bad","{}","{} {}","{\"schemaVersion\":1,\"schemaVersion\":1}"})assertThrows(IllegalArgumentException.class,()->OrderChartSubscription.parse(mapper,raw,"dev"));
    }
}

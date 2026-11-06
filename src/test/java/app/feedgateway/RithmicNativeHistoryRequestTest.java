package app.feedgateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RithmicNativeHistoryRequestTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final long NOW = 1_800_000_000_000L;
    private static final String SYSTEM = "Rithmic Paper Trading";

    private static ObjectNode request() {
        ObjectNode root = JSON.createObjectNode();
        root.put("type", "rithmic-depth-history");root.put("format", RithmicNativeHistoryRequest.FORMAT);
        root.put("requestId", "epoch-1.page-1");root.put("fromMs", NOW - 7_200_000);
        root.put("toMs", NOW);root.put("afterMs", NOW - 7_200_001);
        root.putObject("namespace").put("environment", SYSTEM).put("exchange", "CME").put("contract", "ESZ6");
        return root;
    }

    @Test void frozenFullBookScopeAndExclusiveCursorRoundTrip() {
        var result = RithmicNativeHistoryRequest.parse(request(), SYSTEM, NOW);
        assertTrue(result.accepted());assertNull(result.error());
        assertEquals(NOW - 7_200_001, result.request().afterMs());
        assertEquals(new RithmicNativeHistoryRequest.Scope(SYSTEM, "CME", "ESZ6"), result.request().namespace());
        assertEquals("epoch-1.page-1", result.requestId());
    }

    @Test void legacyPriceSlicesAndSubjectInjectionNeverBecomeFullBooks() {
        for (String extra : new String[] {"minPriceCents", "maxPriceCents", "subject", "unknown"}) {
            var root = request();root.put(extra, 1);
            assertEquals("invalid_request", RithmicNativeHistoryRequest.parse(root, SYSTEM, NOW).error());
        }
        var root = request();((ObjectNode)root.get("namespace")).put("subject", "other-user");
        assertEquals("invalid_request", RithmicNativeHistoryRequest.parse(root, SYSTEM, NOW).error());
    }

    @Test void unsupportedConfigurationAndForeignNamespacesAreExplicit() {
        for (String configured : new String[] {null, "", "Rithmic 01", "unknown"}) {
            assertEquals("unavailable", RithmicNativeHistoryRequest.parse(request(), configured, NOW).error());
        }
        for (String field : new String[] {"environment", "exchange", "contract"}) {
            var root = request();((ObjectNode)root.get("namespace")).put(field, "other");
            assertEquals("unsupported_namespace", RithmicNativeHistoryRequest.parse(root, SYSTEM, NOW).error());
        }
        var test = request();((ObjectNode)test.get("namespace")).put("environment", "Rithmic Test");
        assertTrue(RithmicNativeHistoryRequest.parse(test, "Rithmic Test", NOW).accepted());
        assertFalse(RithmicNativeHistoryRequest.parse(test, SYSTEM, NOW).accepted());
    }

    @Test void timeAndIdentifierBoundariesRejectWithoutCoercion() {
        for (String field : new String[] {"fromMs", "toMs", "afterMs"}) {
            var root = request();root.put(field, "1800000000000");assertFalse(RithmicNativeHistoryRequest.parse(root, SYSTEM, NOW).accepted());
            root=request();root.put(field, 1.5);assertFalse(RithmicNativeHistoryRequest.parse(root, SYSTEM, NOW).accepted());
            root=request();root.put(field, Long.MAX_VALUE);assertFalse(RithmicNativeHistoryRequest.parse(root, SYSTEM, NOW).accepted());
        }
        var root = request();root.put("fromMs", NOW - 7_220_001);root.put("afterMs", NOW - 7_220_002);assertFalse(RithmicNativeHistoryRequest.parse(root, SYSTEM, NOW).accepted());
        root=request();root.put("afterMs", NOW+1);assertFalse(RithmicNativeHistoryRequest.parse(root, SYSTEM, NOW).accepted());
        root=request();root.put("afterMs", NOW-7_200_002);assertFalse(RithmicNativeHistoryRequest.parse(root, SYSTEM, NOW).accepted());
        root=request();root.put("toMs", NOW+5001);assertFalse(RithmicNativeHistoryRequest.parse(root, SYSTEM, NOW).accepted());
        root=request();root.put("requestId", "x".repeat(65));var bad=RithmicNativeHistoryRequest.parse(root, SYSTEM, NOW);assertFalse(bad.accepted());assertEquals("",bad.requestId());
        root=request();root.put("requestId", 123);assertFalse(RithmicNativeHistoryRequest.parse(root, SYSTEM, NOW).accepted());
        assertFalse(RithmicNativeHistoryRequest.parse(JSON.nullNode(), SYSTEM, NOW).accepted());
    }

    @Test void exactEdgesAndZeroEpochCursorAreValid() {
        var root = request();root.put("fromMs", 0);root.put("toMs", 10);root.put("afterMs", -1);
        assertTrue(RithmicNativeHistoryRequest.parse(root, SYSTEM, 10).accepted());
        root=request();root.put("fromMs",NOW-7_220_000);root.put("afterMs",NOW-7_220_001);
        assertTrue(RithmicNativeHistoryRequest.parse(root,SYSTEM,NOW).accepted());
        root=request();root.put("toMs",NOW+5000);assertTrue(RithmicNativeHistoryRequest.parse(root,SYSTEM,NOW).accepted());
        root=request();root.put("fromMs",NOW);root.put("afterMs",NOW);assertTrue(RithmicNativeHistoryRequest.parse(root,SYSTEM,NOW).accepted());
        root=request();root.put("requestId","x".repeat(64));assertTrue(RithmicNativeHistoryRequest.parse(root,SYSTEM,NOW).accepted());
    }
}

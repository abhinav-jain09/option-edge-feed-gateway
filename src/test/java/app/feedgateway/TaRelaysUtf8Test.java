package app.feedgateway;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** The hello budget of every chart relay is a UTF-8 BYTE budget: two-byte text is charged twice. */
class TaRelaysUtf8Test {

    @Test
    void aTimeframeSetRelayChargesBytesNotCharacters() {
        var relay = TimeframeSetRelay.structureTools();
        for (int i = 0; i < 3; i++) {
            String tf = new String[] {"M1", "M5", "M15"}[i];
            assertNotNull(relay.onLive(TimeframeSetRelay.TOOLS_EVENT, TaFixtures.put(TaFixtures.tools(tf, 10 + i, "OK"), "parameterSetHash", "é".repeat(30_000))));
        }
        String all = relay.helloField(1 << 20);
        long oneSet = StudiesRelay.utf8Length(all) / 3;
        assertTrue(oneSet > 60_000, "30000 two-byte characters are 60000 bytes: " + oneSet);
        // a budget that holds two sets in CHARACTERS but not in bytes keeps only what truly fits in bytes
        long budget = oneSet * 2 + 100;
        String part = relay.helloField(budget);
        assertTrue(StudiesRelay.utf8Length(part) <= budget + 60, "the part is within its byte budget plus its skeleton: " + StudiesRelay.utf8Length(part) + " vs " + budget);
        assertFalse(part.contains("\"15m\":{"), "the third set did not fit in bytes");
    }
}

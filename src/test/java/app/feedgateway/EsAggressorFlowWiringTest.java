package app.feedgateway;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class EsAggressorFlowWiringTest {
    @Test void isOptInAndUsesTheEsPrefixedRuntimeTopic() {
        GatewaySettings off = new GatewaySettings();
        assertFalse(off.esAggressorFlowEnabled());
        assertEquals("futures.aggressor-flow", off.esAggressorFlowTopic());
        assertTrue(FeedGatewayService.isGlobalBroadcastEvent("es-aggressor-flow"));
    }
}

package app.feedgateway;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class EsAggressorFlowWiringTest {
    @Test void isOptInAndUsesTheEsPrefixedRuntimeTopic() {
        GatewaySettings off = new GatewaySettings();
        assertFalse(off.esAggressorFlowEnabled());
        assertEquals("futures.aggressor-flow", off.esAggressorFlowTopic());
        assertTrue(FeedGatewayService.isGlobalBroadcastEvent("es-aggressor-flow"));
    }

    @Test void liveSnapshotBypassesTheGenericCacheKeyGate() throws Exception {
        String source = Files.readString(Path.of("src/main/java/app/feedgateway/FeedGatewayService.java"));
        int direct = source.indexOf("if (\"es-aggressor-flow\".equals(binding.event()))");
        int generic = source.indexOf("String cacheKey = updateCache(binding, record, json);", direct);
        assertTrue(direct >= 0 && generic > direct,
                "ES flow must broadcast directly before the generic cache-key gate");
        assertTrue(source.substring(direct, generic).contains("broadcast(binding.event(), json);"));
    }
}

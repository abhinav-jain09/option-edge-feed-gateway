package app.feedgateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FeedGatewayServiceTest {
    @Test
    void sourceSwitchReplayIncludesCachedVixPrice() {
        assertEquals(
                List.of("snapshot", "pace", "directional-pressure", "vix-price", "index-price", "volume-sandwich", "gex-by-strike"),
                FeedGatewayService.sourceSwitchReplayEvents()
        );
    }

    @Test
    void indexPriceCacheKeyUsesPayloadSymbolInsteadOfKafkaTradeKey() {
        FeedGatewayService service = new FeedGatewayService(
                new GatewaySettings(),
                new ObjectMapper(),
                new HpsfGatewayViewMapper()
        );

        String firstEsTrade = "{\"symbol\":\"ES.v.0\",\"instrumentId\":\"42140864\",\"price\":7580.5}";
        String nextEsTrade = "{\"symbol\":\"ES.v.0\",\"instrumentId\":\"42140864\",\"price\":7580.75}";
        String vixPrice = "{\"symbol\":\"VIX\",\"price\":16.2}";

        assertEquals("ES.V.0", service.indexPriceCacheKey(firstEsTrade, "trade-1"));
        assertEquals("ES.V.0", service.indexPriceCacheKey(nextEsTrade, "trade-2"));
        assertEquals("VIX", service.indexPriceCacheKey(vixPrice, "vix-record"));
    }
}

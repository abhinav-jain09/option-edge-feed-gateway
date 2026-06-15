package app.feedgateway;

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
}

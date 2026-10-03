package app.feedgateway.gammamigration;

import app.feedgateway.FeedGatewayService;
import app.feedgateway.GatewaySettings;
import app.feedgateway.liquidityhistory.LiquidityHistoryAuth;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GammaNavigationControllerTest {
    private static final String RECORD = "{\"messageType\":\"GAMMA_NAVIGATION_V1\","
            + "\"symbol\":\"SPX\",\"expiry\":\"20261003\",\"flipState\":\"FLIP_TEST_ACTIVE\"}";

    private static LiquidityHistoryAuth auth(int status) {
        LiquidityHistoryAuth auth = mock(LiquidityHistoryAuth.class);
        when(auth.enforcing()).thenReturn(true);
        when(auth.authenticate(any())).thenReturn(new LiquidityHistoryAuth.Result(status, "tester"));
        return auth;
    }

    private static GatewaySettings enabledSettings() {
        GatewaySettings settings = mock(GatewaySettings.class);
        when(settings.gammaNavigationEnabled()).thenReturn(true);
        return settings;
    }

    @Test
    void servesTheNavigationRecordVerbatimFromItsOwnCache() {
        FeedGatewayService service = mock(FeedGatewayService.class);
        when(service.cachedGammaNavigation("SPX", "20261003")).thenReturn(RECORD);
        var response = new GammaNavigationController(service, auth(200), new ObjectMapper(), enabledSettings())
                .gammaNavigation("SPX", "20261003", "Bearer t");
        assertEquals(RECORD, response.getBody());
        verify(service).cachedGammaNavigation("SPX", "20261003");
        verify(service, never()).cachedGammaMigration(any(), any());
    }

    @Test
    void absenceIsAnExplicitPresentFalseResponse() {
        FeedGatewayService service = mock(FeedGatewayService.class);
        var response = new GammaNavigationController(service, auth(200), new ObjectMapper(), enabledSettings())
                .gammaNavigation("spx", "20261003", "Bearer t");
        assertEquals(200, response.getStatusCode().value());
        assertTrue(response.getBody().contains("\"present\":false"));
        assertTrue(response.getBody().contains("\"symbol\":\"SPX\""));
    }

    @Test
    void missingChainUsesTheActiveSelection() {
        FeedGatewayService service = mock(FeedGatewayService.class);
        when(service.activeSymbolExpiry()).thenReturn(new String[]{"SPX", "20261003"});
        when(service.cachedGammaNavigation("SPX", "20261003")).thenReturn(RECORD);
        var response = new GammaNavigationController(service, auth(200), new ObjectMapper(), enabledSettings())
                .gammaNavigation(null, null, "Bearer t");
        assertEquals(RECORD, response.getBody());
    }

    @Test
    void authenticationRunsBeforeCacheAccess() {
        FeedGatewayService service = mock(FeedGatewayService.class);
        var response = new GammaNavigationController(service, auth(401), new ObjectMapper(), enabledSettings())
                .gammaNavigation("SPX", "20261003", null);
        assertEquals(401, response.getStatusCode().value());
        verify(service, never()).cachedGammaNavigation(any(), any());
    }

    @Test
    void authenticationDisabledFailsClosedBeforeCacheAccess() {
        FeedGatewayService service = mock(FeedGatewayService.class);
        LiquidityHistoryAuth auth = mock(LiquidityHistoryAuth.class);
        when(auth.enforcing()).thenReturn(false);
        var response = new GammaNavigationController(service, auth, new ObjectMapper(), enabledSettings())
                .gammaNavigation("SPX", "20261003", null);
        assertEquals(401, response.getStatusCode().value());
        verify(service, never()).cachedGammaNavigation(any(), any());
        verify(auth, never()).authenticate(any());
    }

    @Test
    void killSwitchWithdrawsEndpointBeforeAuthOrCacheWork() {
        FeedGatewayService service = mock(FeedGatewayService.class);
        LiquidityHistoryAuth auth = mock(LiquidityHistoryAuth.class);
        GatewaySettings settings = mock(GatewaySettings.class);
        when(settings.gammaNavigationEnabled()).thenReturn(false);
        var response = new GammaNavigationController(service, auth, new ObjectMapper(), settings)
                .gammaNavigation("SPX", "20261003", "Bearer t");
        assertEquals(404, response.getStatusCode().value());
        verify(service, never()).cachedGammaNavigation(any(), any());
        verify(auth, never()).authenticate(any());
    }

    @Test
    void producerRecordUsesDedicatedAvroJsonAndNeverEntersReplayOrWebsocketDelivery() throws Exception {
        Method raw = FeedGatewayService.class.getDeclaredMethod("isRawPassThroughEvent", String.class);
        raw.setAccessible(true);
        assertEquals(false, raw.invoke(null, "gamma-navigation"),
                "an Avro GenericRecord must not be serialized through Object.toString()");

        String source = Files.readString(Path.of("src/main/java/app/feedgateway/FeedGatewayService.java"));
        assertTrue(source.contains("\"gamma-navigation\".equals(binding.event())"));
        assertTrue(source.contains("json = avroJson(record.value())"),
                "navigation Avro must use the canonical Avro-to-JSON serializer without enrichment");
        assertEquals(0, occurrences(source,
                "avroTopics.put(settings.gammaNavigationTopic(), \"gamma-navigation\")"),
                "REST-only navigation must not enter websocket replay");
        assertTrue(source.contains("if (\"gamma-navigation\".equals(binding.event()))"),
                "live cache ingest must stop before both websocket routing modes and accounting");
    }

    private static int occurrences(String text, String needle) {
        int count = 0;
        for (int at = text.indexOf(needle); at >= 0; at = text.indexOf(needle, at + needle.length())) {
            count++;
        }
        return count;
    }
}

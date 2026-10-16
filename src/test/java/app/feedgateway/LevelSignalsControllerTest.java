package app.feedgateway;

import app.feedgateway.liquidityhistory.LiquidityHistoryAuth;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class LevelSignalsControllerTest {
    @Test void realHttpMappingRequiresEnforcingAuthAndNeverReadsDataOnRefusal() throws Exception {
        var service=mock(FeedGatewayService.class);var auth=mock(LiquidityHistoryAuth.class);
        var mvc=MockMvcBuilders.standaloneSetup(new LevelSignalsController(service,auth)).build();
        String path="/api/context-tape/level-signals?sessionDate=2026-10-15&tf=1m";
        mvc.perform(get(path)).andExpect(status().isUnauthorized());verifyNoInteractions(service);
        when(auth.enforcing()).thenReturn(true);when(auth.authenticate(null)).thenReturn(new LiquidityHistoryAuth.Result(401,null));
        mvc.perform(get(path)).andExpect(status().isUnauthorized());verifyNoInteractions(service);
        when(auth.authenticate("Bearer denied")).thenReturn(new LiquidityHistoryAuth.Result(403,null));
        mvc.perform(get(path).header("Authorization","Bearer denied")).andExpect(status().isForbidden());verifyNoInteractions(service);
        when(auth.authenticate("Bearer fixture")).thenReturn(new LiquidityHistoryAuth.Result(200,"fixture"));
        when(service.levelSignals("2026-10-15","1m")).thenReturn(LevelSignalViewTest.read(LevelSignalViewTest.structure(),LevelSignalViewTest.snapshot("SPX"),null));
        mvc.perform(get(path).header("Authorization","Bearer fixture")).andExpect(status().isOk())
            .andExpect(header().string("Cache-Control","no-store")).andExpect(jsonPath("$.levels[0].state").value("TESTED"))
            .andExpect(jsonPath("$.source").value(LevelSignalView.SOURCE));
        verify(service).levelSignals("2026-10-15","1m");
    }
    @Test void invalidDateAndTimeframeAreRejectedBeforeDataRead() throws Exception {
        var service=mock(FeedGatewayService.class);var auth=mock(LiquidityHistoryAuth.class);
        when(auth.enforcing()).thenReturn(true);when(auth.authenticate(any())).thenReturn(new LiquidityHistoryAuth.Result(200,"fixture"));
        var mvc=MockMvcBuilders.standaloneSetup(new LevelSignalsController(service,auth)).build();
        for(String q:new String[]{"sessionDate=2026-02-30&tf=1m","sessionDate=2026-10-15&tf=4h","tf=1m"})
            mvc.perform(get("/api/context-tape/level-signals?"+q).header("Authorization","Bearer fixture")).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
}

package app.feedgateway.liquidityhistory;

import app.feedgateway.FeedGatewayService;
import app.feedgateway.GatewaySettings;
import app.feedgateway.mtsession.gateway.WsTicketService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the strike-liquidity session-history backfill (HEATMAP-SESSION-HISTORY-BACKFILL v8). The
 * store bean always exists so the endpoint can answer a definite 404 when the feature is off; its
 * read thread only starts when {@code HEATMAP_HISTORY_ENABLED} (default true) AND Kafka are on —
 * the store logs the single-replica deployment invariant at start (spec §2).
 */
@Configuration
public class LiquidityHistoryConfig {

    @Bean(destroyMethod = "close")
    public LiquidityHistoryStore liquidityHistoryStore(GatewaySettings settings, FeedGatewayService service) {
        LiquidityHistoryStore store = new LiquidityHistoryStore(settings, settings.marketCalendar(),
                service::liquidityHistoryWsChains);
        store.start();
        return store;
    }

    @Bean
    public LiquidityHistoryAuth liquidityHistoryAuth(GatewaySettings settings,
                                                     ObjectProvider<WsTicketService> ticketService) {
        return new LiquidityHistoryAuth(settings, ticketService.getIfAvailable());
    }
}

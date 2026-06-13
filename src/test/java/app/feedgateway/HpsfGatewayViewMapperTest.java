package app.feedgateway;

import com.optionsedge.contracts.hpsf.CandidateRole;
import com.optionsedge.contracts.hpsf.HpsfAction;
import com.optionsedge.contracts.hpsf.HpsfAuditEvent;
import com.optionsedge.contracts.hpsf.HpsfAuditView;
import com.optionsedge.contracts.hpsf.HpsfCandidateSnapshot;
import com.optionsedge.contracts.hpsf.HpsfExitIntentView;
import com.optionsedge.contracts.hpsf.HpsfFixtures;
import com.optionsedge.contracts.hpsf.HpsfLatestSignalView;
import com.optionsedge.contracts.hpsf.HpsfMarketFlowView;
import com.optionsedge.contracts.hpsf.HpsfSignal;
import com.optionsedge.contracts.hpsf.HpsfTopics;
import com.optionsedge.contracts.hpsf.HpsfUiDisplayState;
import com.optionsedge.contracts.hpsf.OptionType;
import com.optionsedge.contracts.hpsf.OrderInstruction;
import com.optionsedge.contracts.hpsf.RiskPlan;
import com.optionsedge.contracts.hpsf.StrikeScoreSnapshot;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class HpsfGatewayViewMapperTest {
    private final HpsfGatewayViewMapper mapper = new HpsfGatewayViewMapper();

    @Test
    void buyCallEarlyMapsToUiReadyLatestSignalView() {
        HpsfLatestSignalView view = mapper.latestSignalView(HpsfTopics.HPSF_LATEST_SIGNAL, HpsfFixtures.buyCallEarlySignal());

        assertEquals(HpsfAction.BUY_CALL_EARLY, view.action());
        assertEquals(HpsfUiDisplayState.BUY_CALL_EARLY, view.displayState());
        assertEquals("BUY CALL EARLY", view.title());
        assertEquals("6005 CALL", view.executionText());
        assertEquals("buy-call-early", view.colorClass());
        assertEquals(6005.0, view.executionStrike());
        assertEquals(6050.0, view.flowAnchorStrike());
        assertEquals(6040.0, view.targetZoneLow());
        assertEquals(6050.0, view.targetZoneHigh());
        assertFalse(view.reasons().isEmpty());
        assertEquals("Invalid if SPX breaks reclaimLow or bearish flow dominates", view.riskText());
    }

    @Test
    void plainNoTradeMapsToGreyNoTradeView() {
        HpsfLatestSignalView view = mapper.latestSignalView(HpsfTopics.HPSF_LATEST_SIGNAL, noTradeSignal(List.of("market score below threshold"), false));

        assertEquals(HpsfAction.NO_TRADE, view.action());
        assertEquals(HpsfUiDisplayState.NO_TRADE, view.displayState());
        assertEquals("NO TRADE", view.title());
        assertEquals("NO EXECUTION", view.executionText());
        assertEquals("no-trade", view.colorClass());
    }

    @Test
    void staleDataForcesWarningDisplayState() {
        HpsfLatestSignalView view = mapper.latestSignalView(HpsfTopics.HPSF_LATEST_SIGNAL, noTradeSignal(List.of("SPX_SPOT_STALE"), false));

        assertEquals(HpsfUiDisplayState.DATA_STALE, view.displayState());
        assertEquals("DATA STALE - NO TRADE", view.title());
        assertEquals("data-stale", view.colorClass());
    }

    @Test
    void mixedFlowForcesWarningDisplayState() {
        HpsfLatestSignalView view = mapper.latestSignalView(HpsfTopics.HPSF_LATEST_SIGNAL, HpsfFixtures.noTradeMixedFlowSignal());

        assertEquals(HpsfUiDisplayState.MIXED_FLOW, view.displayState());
        assertEquals("MIXED FLOW - NO TRADE", view.title());
        assertEquals("mixed-flow", view.colorClass());
    }

    @Test
    void latestSignalMappingRejectsHistoricalSignalTopic() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> mapper.latestSignalView(HpsfTopics.HPSF_SIGNAL, HpsfFixtures.buyCallEarlySignal()));

        assertEquals("Current HPSF decision must be mapped from options.hpsf.latest-signal", error.getMessage());
    }

    @Test
    void marketAuditAndExitViewsMapWithoutTradingLogic() {
        HpsfMarketFlowView market = mapper.marketFlowView(HpsfFixtures.stageBMarketFlowOutput());
        assertEquals(90.0, market.bullishMarketScore());
        assertEquals("BULLISH_RECLAIM", market.marketBias());
        assertEquals("LIVE", market.dataHealth());

        HpsfAuditEvent auditEvent = HpsfFixtures.auditOutput();
        HpsfAuditView audit = mapper.auditView(auditEvent);
        assertEquals(HpsfAction.BUY_CALL_EARLY, audit.selectedAction());
        assertEquals(auditEvent.reasonSummary(), audit.reasonSummary());

        HpsfExitIntentView exit = mapper.exitIntentView(HpsfFixtures.exitIntentOutput());
        assertEquals(HpsfFixtures.exitIntentOutput().exitAction(), exit.exitAction());
        assertEquals(0.31, exit.unrealizedPct());
    }

    @Test
    void topCandidatesViewSortsAndLimitsCallsPutsAndAnchors() {
        List<StrikeScoreSnapshot> scores = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            scores.add(score(6000 + i * 5, OptionType.CALL, CandidateRole.EXECUTION_STRIKE, 80.0 + i));
        }
        scores.add(score(5980, OptionType.PUT, CandidateRole.EXECUTION_STRIKE, 73.0));
        scores.add(score(6050, OptionType.CALL, CandidateRole.FLOW_ANCHOR_STRIKE, 94.0));
        scores.add(score(5975, OptionType.PUT, CandidateRole.FLOW_ANCHOR_STRIKE, 91.0));

        List<HpsfCandidateSnapshot> calls = mapper.topCandidatesView(scores).topCallExecutionCandidates();

        assertEquals(5, calls.size());
        assertEquals(6025.0, calls.getFirst().strike());
        assertEquals(6050.0, mapper.topCandidatesView(scores).callFlowAnchors().getFirst().strike());
        assertEquals(5980.0, mapper.topCandidatesView(scores).topPutExecutionCandidates().getFirst().strike());
        assertEquals(5975.0, mapper.topCandidatesView(scores).putFlowAnchors().getFirst().strike());
    }

    private static StrikeScoreSnapshot score(double strike, OptionType optionType, CandidateRole role, double finalScore) {
        return new StrikeScoreSnapshot(
                2,
                "SPX-20260612-143105250",
                HpsfFixtures.ALGORITHM_VERSION,
                HpsfFixtures.CONFIG_VERSION,
                HpsfFixtures.CODE_GIT_SHA,
                HpsfFixtures.EVENT_TIME,
                HpsfFixtures.TRADE_DATE,
                "SPX",
                HpsfFixtures.EXPIRY,
                strike,
                optionType,
                role,
                6006.25,
                6010.0,
                finalScore,
                finalScore,
                70.0,
                80.0,
                90.0,
                0.0,
                0.0,
                finalScore,
                true,
                true,
                false,
                List.of("candidate accepted")
        );
    }

    private static HpsfSignal noTradeSignal(List<String> reasons, boolean mixedFlow) {
        HpsfSignal base = HpsfFixtures.noTradeMixedFlowSignal();
        return new HpsfSignal(
                base.schemaVersion(),
                base.evaluationId(),
                base.algorithmVersion(),
                base.configVersion(),
                base.codeGitSha(),
                base.eventTime(),
                base.tradeDate(),
                base.underlying(),
                base.expiry(),
                HpsfAction.NO_TRADE,
                base.setup(),
                base.internalVwapState(),
                base.spot(),
                base.vwap(),
                base.distanceToVwap(),
                null,
                null,
                null,
                null,
                null,
                0.0,
                base.marketScore(),
                0.0,
                base.bullishMarketScore(),
                base.bearishMarketScore(),
                base.vwapReclaimScore(),
                base.vwapBreakdownScore(),
                base.bullishPremiumFlow1m(),
                base.bullishPremiumFlow5m(),
                base.bearishPremiumFlow1m(),
                base.bearishPremiumFlow5m(),
                base.volumeSpeed(),
                base.spreadPct(),
                false,
                false,
                mixedFlow,
                base.firstVwapTestTime(),
                base.firstVwapTestPrice(),
                base.reclaimLow(),
                base.softRejectLow(),
                base.higherLowConfirmed(),
                base.breakdownHigh(),
                base.softBounceHigh(),
                base.lowerHighConfirmed(),
                base.retestWindowActive(),
                base.hardRejectionDetected(),
                base.softRejectionDetected(),
                base.hardBounceDetected(),
                base.softBounceDetected(),
                reasons,
                new RiskPlan(null, null, null, null, null, null),
                OrderInstruction.disabled()
        );
    }
}

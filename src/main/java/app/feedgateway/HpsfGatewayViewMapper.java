package app.feedgateway;

import com.optionsedge.contracts.hpsf.CandidateRole;
import com.optionsedge.contracts.hpsf.HpsfAction;
import com.optionsedge.contracts.hpsf.HpsfAuditEvent;
import com.optionsedge.contracts.hpsf.HpsfAuditView;
import com.optionsedge.contracts.hpsf.HpsfCandidateSnapshot;
import com.optionsedge.contracts.hpsf.HpsfExitIntentEvent;
import com.optionsedge.contracts.hpsf.HpsfExitIntentView;
import com.optionsedge.contracts.hpsf.HpsfLatestSignalView;
import com.optionsedge.contracts.hpsf.HpsfMarketFlowView;
import com.optionsedge.contracts.hpsf.HpsfSignal;
import com.optionsedge.contracts.hpsf.HpsfTopCandidatesView;
import com.optionsedge.contracts.hpsf.HpsfTopics;
import com.optionsedge.contracts.hpsf.HpsfUiDisplayState;
import com.optionsedge.contracts.hpsf.InternalVwapState;
import com.optionsedge.contracts.hpsf.MarketFlowSnapshot;
import com.optionsedge.contracts.hpsf.OptionType;
import com.optionsedge.contracts.hpsf.RiskPlan;
import com.optionsedge.contracts.hpsf.StrikeScoreSnapshot;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

@Component
public class HpsfGatewayViewMapper {
    private static final int TOP_CANDIDATE_LIMIT = 5;

    public HpsfLatestSignalView latestSignalView(String sourceTopic, HpsfSignal signal) {
        if (!HpsfTopics.HPSF_LATEST_SIGNAL.equals(sourceTopic)) {
            throw new IllegalArgumentException("Current HPSF decision must be mapped from " + HpsfTopics.HPSF_LATEST_SIGNAL);
        }
        if (signal == null) {
            throw new IllegalArgumentException("HPSF signal is required");
        }
        HpsfUiDisplayState displayState = displayState(signal);
        return new HpsfLatestSignalView(
                signal.action(),
                displayState,
                title(displayState),
                executionText(signal),
                signal.confidence(),
                signal.setup(),
                signal.spot(),
                signal.vwap(),
                signal.distanceToVwap(),
                signal.executionStrike(),
                signal.flowAnchorStrike(),
                signal.targetZoneLow(),
                signal.targetZoneHigh(),
                safeList(signal.reasons()),
                riskText(signal.riskPlan(), signal.reasons()),
                colorClass(displayState),
                signal.eventTime()
        );
    }

    public HpsfMarketFlowView marketFlowView(MarketFlowSnapshot snapshot) {
        if (snapshot == null) {
            throw new IllegalArgumentException("HPSF market-flow snapshot is required");
        }
        InternalVwapState internalVwapState = snapshot.vwapRetest() == null ? null : snapshot.vwapRetest().internalVwapState();
        return new HpsfMarketFlowView(
                snapshot.bullishMarketScore(),
                snapshot.bearishMarketScore(),
                snapshot.marketBias(),
                internalVwapState,
                snapshot.vwapReclaimScore(),
                snapshot.vwapBreakdownScore(),
                snapshot.bullishPremiumFlow1m(),
                snapshot.bearishPremiumFlow1m(),
                snapshot.mixedFlow(),
                Boolean.TRUE.equals(snapshot.mixedFlow()) ? "MIXED_FLOW" : "LIVE"
        );
    }

    public HpsfTopCandidatesView topCandidatesView(Collection<StrikeScoreSnapshot> scores) {
        List<StrikeScoreSnapshot> safeScores = scores == null ? List.of() : scores.stream().filter(Objects::nonNull).toList();
        return new HpsfTopCandidatesView(
                candidates(safeScores, OptionType.CALL, CandidateRole.EXECUTION_STRIKE),
                candidates(safeScores, OptionType.PUT, CandidateRole.EXECUTION_STRIKE),
                candidates(safeScores, OptionType.CALL, CandidateRole.FLOW_ANCHOR_STRIKE),
                candidates(safeScores, OptionType.PUT, CandidateRole.FLOW_ANCHOR_STRIKE)
        );
    }

    public HpsfAuditView auditView(HpsfAuditEvent audit) {
        if (audit == null) {
            throw new IllegalArgumentException("HPSF audit event is required");
        }
        return new HpsfAuditView(
                audit.selectedAction(),
                safeList(audit.rejectedActions()),
                audit.reasonSummary(),
                safeList(audit.noTradeGates()),
                safeList(audit.sourceOffsets())
        );
    }

    public HpsfExitIntentView exitIntentView(HpsfExitIntentEvent event) {
        if (event == null) {
            throw new IllegalArgumentException("HPSF exit intent event is required");
        }
        return new HpsfExitIntentView(
                event.exitAction(),
                event.reason(),
                event.unrealizedPct(),
                event.flowReversalConfirmed()
        );
    }

    private List<HpsfCandidateSnapshot> candidates(List<StrikeScoreSnapshot> scores, OptionType optionType, CandidateRole role) {
        return scores.stream()
                .filter(score -> optionType == score.optionType())
                .filter(score -> role == score.candidateRole())
                .sorted(Comparator
                        .comparingDouble((StrikeScoreSnapshot score) -> scoreValue(score)).reversed()
                        .thenComparing(score -> score.strike() == null ? Double.MAX_VALUE : score.strike()))
                .limit(TOP_CANDIDATE_LIMIT)
                .map(this::candidate)
                .toList();
    }

    private HpsfCandidateSnapshot candidate(StrikeScoreSnapshot score) {
        return new HpsfCandidateSnapshot(
                score.evaluationId(),
                score.strike(),
                score.optionType(),
                score.candidateRole(),
                scoreValue(score),
                null,
                null,
                null,
                null,
                null,
                score.liquidityOk(),
                score.candidateDistanceOk(),
                reason(score)
        );
    }

    private static double scoreValue(StrikeScoreSnapshot score) {
        if (score.finalScore() != null) {
            return score.finalScore();
        }
        return score.score() == null ? 0.0 : score.score();
    }

    private static String reason(StrikeScoreSnapshot score) {
        List<String> reasons = safeList(score.reasons());
        if (reasons.isEmpty()) {
            if (Boolean.FALSE.equals(score.liquidityOk())) {
                return "Liquidity failed";
            }
            if (Boolean.FALSE.equals(score.candidateDistanceOk())) {
                return "Distance failed";
            }
            return null;
        }
        return String.join("; ", reasons);
    }

    private static HpsfUiDisplayState displayState(HpsfSignal signal) {
        if (hasDataStale(signal)) {
            return HpsfUiDisplayState.DATA_STALE;
        }
        if (Boolean.TRUE.equals(signal.mixedFlow()) || contains(signal.reasons(), "mixed flow")) {
            return HpsfUiDisplayState.MIXED_FLOW;
        }
        HpsfAction action = signal.action() == null ? HpsfAction.NO_TRADE : signal.action();
        return switch (action) {
            case WATCH_CALL_RECLAIM -> HpsfUiDisplayState.WATCH_CALL_RECLAIM;
            case BUY_CALL_EARLY -> HpsfUiDisplayState.BUY_CALL_EARLY;
            case BUY_CALL_CONFIRMED -> HpsfUiDisplayState.BUY_CALL_CONFIRMED;
            case WATCH_PUT_BREAKDOWN -> HpsfUiDisplayState.WATCH_PUT_BREAKDOWN;
            case BUY_PUT_EARLY -> HpsfUiDisplayState.BUY_PUT_EARLY;
            case BUY_PUT_CONFIRMED -> HpsfUiDisplayState.BUY_PUT_CONFIRMED;
            case NO_TRADE -> HpsfUiDisplayState.NO_TRADE;
        };
    }

    private static boolean hasDataStale(HpsfSignal signal) {
        return contains(signal.reasons(), "stale")
                || contains(signal.reasons(), "lag")
                || contains(signal.reasons(), "data not safe")
                || contains(signal.reasons(), "bad data");
    }

    private static String title(HpsfUiDisplayState displayState) {
        return switch (displayState) {
            case NO_TRADE -> "NO TRADE";
            case WATCH_CALL_RECLAIM -> "WATCH CALL RECLAIM";
            case BUY_CALL_EARLY -> "BUY CALL EARLY";
            case BUY_CALL_CONFIRMED -> "BUY CALL CONFIRMED";
            case WATCH_PUT_BREAKDOWN -> "WATCH PUT BREAKDOWN";
            case BUY_PUT_EARLY -> "BUY PUT EARLY";
            case BUY_PUT_CONFIRMED -> "BUY PUT CONFIRMED";
            case MIXED_FLOW -> "MIXED FLOW - NO TRADE";
            case DATA_STALE -> "DATA STALE - NO TRADE";
        };
    }

    private static String colorClass(HpsfUiDisplayState displayState) {
        return switch (displayState) {
            case NO_TRADE -> "no-trade";
            case WATCH_CALL_RECLAIM -> "watch-call";
            case BUY_CALL_EARLY -> "buy-call-early";
            case BUY_CALL_CONFIRMED -> "buy-call-confirmed";
            case WATCH_PUT_BREAKDOWN -> "watch-put";
            case BUY_PUT_EARLY -> "buy-put-early";
            case BUY_PUT_CONFIRMED -> "buy-put-confirmed";
            case MIXED_FLOW -> "mixed-flow";
            case DATA_STALE -> "data-stale";
        };
    }

    private static String executionText(HpsfSignal signal) {
        if (signal.executionStrike() == null || signal.selectedOptionType() == null) {
            return "NO EXECUTION";
        }
        return formatStrike(signal.executionStrike()) + " " + signal.selectedOptionType().name();
    }

    private static String riskText(RiskPlan riskPlan, List<String> reasons) {
        if (riskPlan != null && riskPlan.invalidIf() != null && !riskPlan.invalidIf().isBlank()) {
            return "Invalid if " + riskPlan.invalidIf();
        }
        List<String> safeReasons = safeList(reasons);
        return safeReasons.isEmpty() ? null : safeReasons.getFirst();
    }

    private static String formatStrike(Double strike) {
        if (strike == null) {
            return "";
        }
        if (Math.rint(strike) == strike) {
            return Long.toString(strike.longValue());
        }
        return String.format(Locale.US, "%.2f", strike);
    }

    private static boolean contains(List<String> values, String needle) {
        if (values == null || needle == null || needle.isBlank()) {
            return false;
        }
        String lowerNeedle = needle.toLowerCase(Locale.US);
        return values.stream()
                .filter(Objects::nonNull)
                .map(value -> value.toLowerCase(Locale.US))
                .anyMatch(value -> value.contains(lowerNeedle));
    }

    private static <T> List<T> safeList(List<T> values) {
        return values == null ? List.of() : List.copyOf(values);
    }
}

package app.feedgateway.mtsession;

import java.util.Map;
import java.util.Set;

/**
 * Validates administrator-driven transitions of {@link ApprovalState} (OE-DDD-001 §5.4).
 * Invalid transitions throw {@link IllegalTransitionException} — the state graph is the single
 * source of truth for what an admin may do.
 */
public final class ApprovalStateMachine {

    public enum Action { APPROVE, REJECT, SUSPEND, REINSTATE }

    /** Thrown when a requested transition is not permitted from the current state. */
    public static final class IllegalTransitionException extends RuntimeException {
        public IllegalTransitionException(ApprovalState from, Action action) {
            super("Illegal approval transition: " + action + " from " + from);
        }
    }

    private static final Map<ApprovalState, Map<Action, ApprovalState>> GRAPH = Map.of(
            ApprovalState.PENDING_APPROVAL, Map.of(
                    Action.APPROVE, ApprovalState.APPROVED,
                    Action.REJECT, ApprovalState.REJECTED),
            ApprovalState.APPROVED, Map.of(
                    Action.SUSPEND, ApprovalState.SUSPENDED),
            ApprovalState.SUSPENDED, Map.of(
                    Action.REINSTATE, ApprovalState.APPROVED),
            ApprovalState.REJECTED, Map.of(
                    Action.APPROVE, ApprovalState.APPROVED,
                    Action.REINSTATE, ApprovalState.APPROVED));

    private ApprovalStateMachine() {
    }

    /** The resulting state, or throw if {@code action} is not allowed from {@code from}. */
    public static ApprovalState transition(ApprovalState from, Action action) {
        ApprovalState to = GRAPH.getOrDefault(from, Map.of()).get(action);
        if (to == null) {
            throw new IllegalTransitionException(from, action);
        }
        return to;
    }

    public static boolean isAllowed(ApprovalState from, Action action) {
        return GRAPH.getOrDefault(from, Map.of()).containsKey(action);
    }

    public static Set<Action> allowedActions(ApprovalState from) {
        return GRAPH.getOrDefault(from, Map.of()).keySet();
    }
}

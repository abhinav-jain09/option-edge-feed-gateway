package app.feedgateway.mtsession;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.feedgateway.mtsession.ApprovalStateMachine.Action;
import org.junit.jupiter.api.Test;

class ApprovalStateMachineTest {

    @Test
    void validTransitions() {
        assertEquals(ApprovalState.APPROVED,
                ApprovalStateMachine.transition(ApprovalState.PENDING_APPROVAL, Action.APPROVE));
        assertEquals(ApprovalState.REJECTED,
                ApprovalStateMachine.transition(ApprovalState.PENDING_APPROVAL, Action.REJECT));
        assertEquals(ApprovalState.SUSPENDED,
                ApprovalStateMachine.transition(ApprovalState.APPROVED, Action.SUSPEND));
        assertEquals(ApprovalState.APPROVED,
                ApprovalStateMachine.transition(ApprovalState.SUSPENDED, Action.REINSTATE));
        assertEquals(ApprovalState.APPROVED,
                ApprovalStateMachine.transition(ApprovalState.REJECTED, Action.APPROVE));
    }

    @Test
    void invalidTransitionsThrow() {
        assertThrows(ApprovalStateMachine.IllegalTransitionException.class,
                () -> ApprovalStateMachine.transition(ApprovalState.PENDING_APPROVAL, Action.SUSPEND));
        assertThrows(ApprovalStateMachine.IllegalTransitionException.class,
                () -> ApprovalStateMachine.transition(ApprovalState.APPROVED, Action.APPROVE));
        assertThrows(ApprovalStateMachine.IllegalTransitionException.class,
                () -> ApprovalStateMachine.transition(ApprovalState.SUSPENDED, Action.SUSPEND));
    }

    @Test
    void isAllowedAndAllowedActions() {
        assertTrue(ApprovalStateMachine.isAllowed(ApprovalState.APPROVED, Action.SUSPEND));
        assertFalse(ApprovalStateMachine.isAllowed(ApprovalState.APPROVED, Action.REJECT));
        assertEquals(2, ApprovalStateMachine.allowedActions(ApprovalState.PENDING_APPROVAL).size());
    }

    @Test
    void onlyApprovedGrantsAccess() {
        assertTrue(ApprovalState.APPROVED.grantsAccess());
        assertFalse(ApprovalState.PENDING_APPROVAL.grantsAccess());
        assertFalse(ApprovalState.REJECTED.grantsAccess());
        assertFalse(ApprovalState.SUSPENDED.grantsAccess());
    }
}

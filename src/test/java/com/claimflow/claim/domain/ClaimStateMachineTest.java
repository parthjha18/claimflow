package com.claimflow.claim.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static com.claimflow.claim.domain.ClaimAction.*;
import static com.claimflow.claim.domain.ClaimStatus.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ClaimStateMachineTest {

    @Test
    @DisplayName("happy path with finance: DRAFT -> SUBMITTED -> MANAGER_APPROVED -> FINANCE_APPROVED -> PAID")
    void fullPathWithFinance() {
        ClaimStatus s = DRAFT;
        s = ClaimStateMachine.next(s, SUBMIT, true);
        assertThat(s).isEqualTo(SUBMITTED);
        s = ClaimStateMachine.next(s, APPROVE, true);
        assertThat(s).isEqualTo(MANAGER_APPROVED);
        s = ClaimStateMachine.next(s, APPROVE, true);
        assertThat(s).isEqualTo(FINANCE_APPROVED);
        s = ClaimStateMachine.next(s, PAY, true);
        assertThat(s).isEqualTo(PAID);
    }

    @Test
    @DisplayName("small claims skip finance approval: MANAGER_APPROVED -> PAID")
    void shortPathWithoutFinance() {
        assertThat(ClaimStateMachine.next(MANAGER_APPROVED, PAY, false)).isEqualTo(PAID);
    }

    @Test
    void financeApprovalIsNotAllowedForSmallClaims() {
        assertThatThrownBy(() -> ClaimStateMachine.next(MANAGER_APPROVED, APPROVE, false))
                .isInstanceOf(InvalidStateTransitionException.class);
    }

    @Test
    void largeClaimsCannotBePaidWithoutFinanceApproval() {
        assertThatThrownBy(() -> ClaimStateMachine.next(MANAGER_APPROVED, PAY, true))
                .isInstanceOf(InvalidStateTransitionException.class);
    }

    @ParameterizedTest
    @EnumSource(value = ClaimStatus.class, names = {"SUBMITTED", "MANAGER_APPROVED", "FINANCE_APPROVED"})
    void anyApprovalStageCanRejectOrRequestChanges(ClaimStatus stage) {
        assertThat(ClaimStateMachine.next(stage, REJECT, true)).isEqualTo(REJECTED);
        assertThat(ClaimStateMachine.next(stage, REQUEST_CHANGES, true)).isEqualTo(DRAFT);
    }

    @ParameterizedTest
    @EnumSource(value = ClaimAction.class)
    void paidAndRejectedAreTerminal(ClaimAction action) {
        for (boolean finance : new boolean[]{true, false}) {
            assertThat(ClaimStateMachine.canTransition(PAID, action, finance)).isFalse();
            assertThat(ClaimStateMachine.canTransition(REJECTED, action, finance)).isFalse();
        }
    }

    @ParameterizedTest
    @EnumSource(value = ClaimAction.class, names = {"APPROVE", "REJECT", "REQUEST_CHANGES", "PAY", "ESCALATE"})
    void draftCanOnlyBeSubmitted(ClaimAction action) {
        assertThat(ClaimStateMachine.canTransition(DRAFT, action, true)).isFalse();
    }

    @Test
    void submittedCannotBeSubmittedAgainOrPaid() {
        assertThat(ClaimStateMachine.canTransition(SUBMITTED, SUBMIT, true)).isFalse();
        assertThat(ClaimStateMachine.canTransition(SUBMITTED, PAY, false)).isFalse();
    }

    @Test
    void escalationKeepsStatusButOnlyWhileAwaitingManager() {
        assertThat(ClaimStateMachine.next(SUBMITTED, ESCALATE, true)).isEqualTo(SUBMITTED);
        assertThat(ClaimStateMachine.canTransition(MANAGER_APPROVED, ESCALATE, true)).isFalse();
        assertThat(ClaimStateMachine.canTransition(DRAFT, ESCALATE, true)).isFalse();
    }

    @Test
    void errorCarriesAConflictCode() {
        assertThatThrownBy(() -> ClaimStateMachine.next(PAID, APPROVE, true))
                .isInstanceOfSatisfying(InvalidStateTransitionException.class, e -> {
                    assertThat(e.getStatus().value()).isEqualTo(409);
                    assertThat(e.getCode()).isEqualTo("INVALID_STATE_TRANSITION");
                });
    }
}

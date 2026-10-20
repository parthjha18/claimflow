package com.claimflow.claim.domain;

public enum ClaimStatus {
    DRAFT, SUBMITTED, MANAGER_APPROVED, FINANCE_APPROVED, PAID, REJECTED;

    /** Statuses in which a claim is waiting for somebody to act on it. */
    public boolean isPendingApproval() {
        return this == SUBMITTED || this == MANAGER_APPROVED;
    }

    public boolean isTerminal() {
        return this == PAID || this == REJECTED;
    }
}

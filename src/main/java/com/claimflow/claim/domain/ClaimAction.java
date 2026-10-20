package com.claimflow.claim.domain;

/** Everything that can happen to a claim; also what is recorded in an ApprovalStep. */
public enum ClaimAction {
    SUBMIT, APPROVE, REJECT, REQUEST_CHANGES, PAY, ESCALATE
}

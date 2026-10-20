package com.claimflow.claim.domain;

import com.claimflow.common.exception.ConflictException;

public class InvalidStateTransitionException extends ConflictException {

    public InvalidStateTransitionException(ClaimStatus from, ClaimAction action) {
        super("INVALID_STATE_TRANSITION", "Action " + action + " is not allowed while the claim is " + from);
    }
}

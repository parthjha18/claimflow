package com.claimflow.common.exception;

import org.springframework.http.HttpStatus;

import java.util.List;

/** Raised on submit when the claim breaks the organisation's expense policy. Carries every violation. */
public class PolicyViolationException extends ApiException {

    private final List<String> violations;

    public PolicyViolationException(List<String> violations) {
        super(HttpStatus.UNPROCESSABLE_ENTITY, "POLICY_VIOLATION",
                "Claim violates the expense policy (" + violations.size() + " issue(s))");
        this.violations = List.copyOf(violations);
    }

    public List<String> getViolations() {
        return violations;
    }
}

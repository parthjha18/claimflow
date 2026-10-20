package com.claimflow.policy;

import com.claimflow.claim.domain.ExpenseCategory;

import java.math.BigDecimal;
import java.util.Map;

public record PolicyResponse(
        BigDecimal perClaimMax,
        BigDecimal receiptRequiredAbove,
        BigDecimal financeApprovalAbove,
        int escalationAfterDays,
        Map<ExpenseCategory, BigDecimal> categoryLimits,
        String currency) {

    public static PolicyResponse from(PolicySnapshot s) {
        return new PolicyResponse(s.perClaimMax(), s.receiptRequiredAbove(), s.financeApprovalAbove(),
                s.escalationAfterDays(), s.categoryLimits(), "INR");
    }
}

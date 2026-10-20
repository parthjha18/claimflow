package com.claimflow.policy;

import com.claimflow.claim.domain.ExpenseCategory;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.util.Map;

public record PolicyRequest(
        @NotNull @Positive BigDecimal perClaimMax,
        @NotNull @PositiveOrZero BigDecimal receiptRequiredAbove,
        @NotNull @PositiveOrZero BigDecimal financeApprovalAbove,
        @Min(1) int escalationAfterDays,
        @NotNull Map<ExpenseCategory, @NotNull @Positive BigDecimal> categoryLimits) {
}

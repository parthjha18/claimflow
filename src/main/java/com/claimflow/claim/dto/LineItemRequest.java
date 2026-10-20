package com.claimflow.claim.dto;

import com.claimflow.claim.domain.ExpenseCategory;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public record LineItemRequest(
        @NotNull ExpenseCategory category,
        @NotNull @DecimalMin("0.01") @Digits(integer = 12, fraction = 2) BigDecimal amount,
        @NotNull @PastOrPresent LocalDate expenseDate,
        @Size(max = 500) String description) {
}

package com.claimflow.claim.dto;

import com.claimflow.claim.domain.ClaimLineItem;
import com.claimflow.claim.domain.ExpenseCategory;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record LineItemResponse(UUID id, ExpenseCategory category, BigDecimal amount, LocalDate expenseDate,
                               String description, boolean hasReceipt, String receiptFilename) {

    public static LineItemResponse from(ClaimLineItem i) {
        return new LineItemResponse(i.getId(), i.getCategory(), i.getAmount(), i.getExpenseDate(),
                i.getDescription(), i.hasReceipt(), i.getReceiptFilename());
    }
}

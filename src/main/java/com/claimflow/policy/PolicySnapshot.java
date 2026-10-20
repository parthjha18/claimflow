package com.claimflow.policy;

import com.claimflow.claim.domain.ExpenseCategory;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

/**
 * Immutable, detached copy of an organisation's policy. This (not the JPA entity) is what gets cached in
 * Redis and handed to the validator, so no lazy proxies or persistence context leak into the cache.
 */
public record PolicySnapshot(
        UUID organizationId,
        BigDecimal perClaimMax,
        BigDecimal receiptRequiredAbove,
        BigDecimal financeApprovalAbove,
        int escalationAfterDays,
        Map<ExpenseCategory, BigDecimal> categoryLimits) implements Serializable {

    public PolicySnapshot {
        categoryLimits = Map.copyOf(categoryLimits);
    }

    public static PolicySnapshot from(ExpensePolicy p) {
        return new PolicySnapshot(p.getOrganizationId(), p.getPerClaimMax(), p.getReceiptRequiredAbove(),
                p.getFinanceApprovalAbove(), p.getEscalationAfterDays(), p.getCategoryLimits());
    }

    public boolean requiresFinance(BigDecimal claimTotal) {
        return claimTotal.compareTo(financeApprovalAbove) > 0;
    }
}

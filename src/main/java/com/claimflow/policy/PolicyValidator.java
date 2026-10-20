package com.claimflow.policy;

import com.claimflow.claim.domain.Claim;
import com.claimflow.claim.domain.ClaimLineItem;
import com.claimflow.claim.domain.ExpenseCategory;
import com.claimflow.common.exception.PolicyViolationException;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Checks a claim against the organisation's policy at submit time. Collects every violation rather than
 * stopping at the first, so the employee can fix everything in one go.
 *
 * Interpretation of the limits: a category limit caps the <em>sum</em> of that category's items on one
 * claim (which also means no single item can exceed it); the per-claim max caps the whole claim; a receipt
 * is required for every single item above the receipt threshold.
 */
@Component
public class PolicyValidator {

    public List<String> findViolations(PolicySnapshot policy, Claim claim) {
        List<String> violations = new ArrayList<>();
        List<ClaimLineItem> items = claim.getItems();

        if (items.isEmpty()) {
            violations.add("A claim needs at least one line item");
            return violations;
        }

        BigDecimal total = claim.getTotalAmount();
        if (total.compareTo(policy.perClaimMax()) > 0) {
            violations.add("Claim total " + total + " exceeds the per-claim maximum of " + policy.perClaimMax());
        }

        Map<ExpenseCategory, BigDecimal> totalsByCategory = new EnumMap<>(ExpenseCategory.class);
        for (ClaimLineItem item : items) {
            totalsByCategory.merge(item.getCategory(), item.getAmount(), BigDecimal::add);

            if (item.getAmount().compareTo(policy.receiptRequiredAbove()) > 0 && !item.hasReceipt()) {
                violations.add("Item '" + label(item) + "' (" + item.getCategory() + ", " + item.getAmount()
                        + ") needs a receipt; receipts are required above " + policy.receiptRequiredAbove());
            }
        }

        totalsByCategory.forEach((category, sum) -> {
            BigDecimal limit = policy.categoryLimits().get(category);
            if (limit != null && sum.compareTo(limit) > 0) {
                violations.add(category + " total " + sum + " exceeds the category limit of " + limit);
            }
        });
        return violations;
    }

    public void validateOrThrow(PolicySnapshot policy, Claim claim) {
        List<String> violations = findViolations(policy, claim);
        if (!violations.isEmpty()) {
            throw new PolicyViolationException(violations);
        }
    }

    private static String label(ClaimLineItem item) {
        return item.getDescription() != null && !item.getDescription().isBlank()
                ? item.getDescription() : item.getCategory().name();
    }
}

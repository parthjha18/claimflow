package com.claimflow.claim.dto;

import com.claimflow.claim.domain.Claim;
import com.claimflow.claim.domain.ClaimStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** List row. Deliberately has no items, so list queries never touch the line-item table. */
public record ClaimSummaryResponse(UUID id, String title, ClaimStatus status, BigDecimal totalAmount, String currency,
                                   UUID claimantId, String claimantName, UUID currentApproverId,
                                   String currentApproverName, boolean requiresFinance, Instant submittedAt,
                                   Instant createdAt, long version) {

    public static ClaimSummaryResponse from(Claim c) {
        return new ClaimSummaryResponse(c.getId(), c.getTitle(), c.getStatus(), c.getTotalAmount(), c.getCurrency(),
                c.getClaimant().getId(), c.getClaimant().getFullName(),
                c.getCurrentApprover() != null ? c.getCurrentApprover().getId() : null,
                c.getCurrentApprover() != null ? c.getCurrentApprover().getFullName() : null,
                c.isRequiresFinance(), c.getSubmittedAt(), c.getCreatedAt(), c.getVersion());
    }
}

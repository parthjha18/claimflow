package com.claimflow.notification;

import com.claimflow.claim.domain.ClaimAction;
import com.claimflow.claim.domain.ClaimStatus;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Published inside the transition's transaction; delivered to listeners only after it commits, so nobody is
 * ever told about a change that was rolled back (for instance by an optimistic-lock conflict).
 */
public record ClaimStatusChangedEvent(
        UUID claimId,
        UUID organizationId,
        String claimTitle,
        BigDecimal totalAmount,
        ClaimAction action,
        ClaimStatus fromStatus,
        ClaimStatus toStatus,
        UUID actorId,           // null when the system acted (escalation)
        String comment,
        UUID claimantId,
        UUID currentApproverId,
        boolean requiresFinance) {
}

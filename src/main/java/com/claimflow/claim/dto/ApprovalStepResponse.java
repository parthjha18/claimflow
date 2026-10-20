package com.claimflow.claim.dto;

import com.claimflow.claim.approval.ApprovalStep;
import com.claimflow.claim.domain.ClaimAction;
import com.claimflow.claim.domain.ClaimStatus;

import java.time.Instant;
import java.util.UUID;

public record ApprovalStepResponse(UUID id, UUID actorId, String actorName, ClaimAction action,
                                   ClaimStatus fromStatus, ClaimStatus toStatus, String comment, Instant at) {

    public static ApprovalStepResponse from(ApprovalStep s) {
        return new ApprovalStepResponse(s.getId(), s.getActor().getId(), s.getActor().getFullName(), s.getAction(),
                s.getFromStatus(), s.getToStatus(), s.getComment(), s.getCreatedAt());
    }
}

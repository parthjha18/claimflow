package com.claimflow.claim.dto;

import com.claimflow.claim.approval.ApprovalStep;
import com.claimflow.claim.domain.Claim;

import java.util.List;

public record ClaimDetailResponse(ClaimSummaryResponse claim, String description, List<LineItemResponse> items,
                                  List<ApprovalStepResponse> history) {

    public static ClaimDetailResponse from(Claim c, List<ApprovalStep> steps) {
        return new ClaimDetailResponse(ClaimSummaryResponse.from(c), c.getDescription(),
                c.getItems().stream().map(LineItemResponse::from).toList(),
                steps.stream().map(ApprovalStepResponse::from).toList());
    }
}

package com.claimflow.claim;

import com.claimflow.claim.domain.ClaimStatus;
import com.claimflow.claim.dto.ClaimSummaryResponse;
import com.claimflow.common.web.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/claims")
@Tag(name = "Claim queries")
@SecurityRequirement(name = "bearerAuth")
public class ClaimQueryController {

    private final ClaimQueryService service;

    public ClaimQueryController(ClaimQueryService service) {
        this.service = service;
    }

    @GetMapping("/mine")
    @Operation(summary = "My claims, optionally filtered by status. sort: createdAt|submittedAt|totalAmount|status|title")
    public PageResponse<ClaimSummaryResponse> mine(@RequestParam(required = false) ClaimStatus status, Pageable pageable) {
        return PageResponse.of(service.myClaims(status, pageable).map(ClaimSummaryResponse::from));
    }

    @GetMapping("/pending-approvals")
    @Operation(summary = "Claims waiting for me: as manager, those assigned to me; as finance, everything awaiting finance")
    public PageResponse<ClaimSummaryResponse> pendingApprovals(Pageable pageable) {
        return PageResponse.of(service.pendingApprovals(pageable).map(ClaimSummaryResponse::from));
    }
}

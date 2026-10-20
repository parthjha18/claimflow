package com.claimflow.claim.approval;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ApprovalStepRepository extends JpaRepository<ApprovalStep, UUID> {

    @EntityGraph(attributePaths = "actor")
    List<ApprovalStep> findByClaimIdOrderByCreatedAtAsc(UUID claimId);
}

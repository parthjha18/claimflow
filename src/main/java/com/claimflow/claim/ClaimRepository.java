package com.claimflow.claim;

import com.claimflow.claim.domain.Claim;
import com.claimflow.claim.domain.ClaimStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ClaimRepository extends JpaRepository<Claim, UUID> {

    /** Single-claim loads fetch everything the detail view needs in one query (no N+1). */
    @EntityGraph(attributePaths = {"items", "claimant", "currentApprover"})
    @Query("select c from Claim c where c.id = :id and c.organizationId = :orgId")
    Optional<Claim> findDetailed(UUID id, UUID orgId);

    /**
     * List pages join the people but not the items: fetching a collection together with LIMIT/OFFSET makes
     * Hibernate paginate in memory. Summaries do not need the items.
     */
    @EntityGraph(attributePaths = {"claimant", "currentApprover"})
    @Query("""
            select c from Claim c
            where c.organizationId = :orgId and c.claimant.id = :claimantId
              and (:status is null or c.status = :status)
            """)
    Page<Claim> findMine(UUID orgId, UUID claimantId, ClaimStatus status, Pageable pageable);

    /** Claims waiting on this manager (manager stage). */
    @EntityGraph(attributePaths = {"claimant", "currentApprover"})
    @Query("""
            select c from Claim c
            where c.organizationId = :orgId and c.status = com.claimflow.claim.domain.ClaimStatus.SUBMITTED
              and c.currentApprover.id = :approverId
            """)
    Page<Claim> findPendingForManager(UUID orgId, UUID approverId, Pageable pageable);

    /** Claims waiting on the finance team: approved by the manager (or by finance) and not yet paid. */
    @EntityGraph(attributePaths = {"claimant", "currentApprover"})
    @Query("""
            select c from Claim c
            where c.organizationId = :orgId
              and c.status in (com.claimflow.claim.domain.ClaimStatus.MANAGER_APPROVED,
                               com.claimflow.claim.domain.ClaimStatus.FINANCE_APPROVED)
              and c.claimant.id <> :excludeClaimantId
            """)
    Page<Claim> findPendingForFinance(UUID orgId, UUID excludeClaimantId, Pageable pageable);

    /** Candidates for the escalation job; the actual change is re-checked per claim under its own lock. */
    @Query("""
            select c.id from Claim c
            where c.organizationId = :orgId and c.status = com.claimflow.claim.domain.ClaimStatus.SUBMITTED
              and c.statusChangedAt < :cutoff
            order by c.statusChangedAt
            """)
    List<UUID> findOverdueSubmitted(UUID orgId, Instant cutoff);
}

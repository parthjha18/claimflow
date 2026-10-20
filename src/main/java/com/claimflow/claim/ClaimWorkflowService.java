package com.claimflow.claim;

import com.claimflow.claim.approval.ApprovalStep;
import com.claimflow.claim.approval.ApprovalStepRepository;
import com.claimflow.claim.domain.Claim;
import com.claimflow.claim.domain.ClaimAction;
import com.claimflow.claim.domain.ClaimStateMachine;
import com.claimflow.claim.domain.ClaimStatus;
import com.claimflow.claim.ClaimService.ClaimDetail;
import com.claimflow.common.audit.AuditService;
import com.claimflow.common.exception.BadRequestException;
import com.claimflow.common.exception.ConflictException;
import com.claimflow.common.exception.ForbiddenOperationException;
import com.claimflow.common.exception.NotFoundException;
import com.claimflow.common.exception.ApiException;
import com.claimflow.common.tenant.TenantContext;
import com.claimflow.notification.ClaimStatusChangedEvent;
import com.claimflow.policy.PolicyService;
import com.claimflow.policy.PolicySnapshot;
import com.claimflow.policy.PolicyValidator;
import com.claimflow.security.UserPrincipal;
import com.claimflow.user.Role;
import com.claimflow.user.User;
import com.claimflow.user.UserRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Every status change goes through {@link #transition}. The checks run in a deliberate order:
 * <ol>
 *   <li>can the caller see the claim at all? (404 otherwise)</li>
 *   <li>maker-checker: the claimant never acts as approver (403)</li>
 *   <li>does the state machine allow this action now? (409)</li>
 *   <li>is the caller the person/role responsible for this stage? (403)</li>
 * </ol>
 * The claim row is flushed before anything else is written; if a concurrent approver got there first the
 * {@code @Version} check fails and the whole transaction (including step/audit rows) rolls back, which the
 * exception handler reports as HTTP 409.
 */
@Service
public class ClaimWorkflowService {

    private static final int MAX_CHAIN_DEPTH = 50;

    private final ClaimRepository claims;
    private final ApprovalStepRepository steps;
    private final UserRepository users;
    private final PolicyService policies;
    private final PolicyValidator validator;
    private final ClaimAccess access;
    private final AuditService audit;
    private final ApplicationEventPublisher events;

    public ClaimWorkflowService(ClaimRepository claims, ApprovalStepRepository steps, UserRepository users,
                                PolicyService policies, PolicyValidator validator, ClaimAccess access,
                                AuditService audit, ApplicationEventPublisher events) {
        this.claims = claims;
        this.steps = steps;
        this.users = users;
        this.policies = policies;
        this.validator = validator;
        this.access = access;
        this.audit = audit;
        this.events = events;
    }

    @Transactional
    public ClaimDetail submit(UUID claimId) {
        UserPrincipal me = TenantContext.requireUser();
        Claim claim = loadVisible(claimId, me);
        if (!claim.getClaimant().getId().equals(me.userId())) {
            throw new ForbiddenOperationException("NOT_CLAIMANT", "Only the claimant can submit a claim");
        }
        // state first, so re-submitting an already submitted claim is a clean 409 rather than a policy error
        ClaimStateMachine.next(claim.getStatus(), ClaimAction.SUBMIT, claim.isRequiresFinance());

        PolicySnapshot policy = loadPolicy(me.organizationId());
        validator.validateOrThrow(policy, claim);

        User approver = firstActiveManagerAbove(claim.getClaimant());
        if (approver == null) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "NO_APPROVER",
                    "You have no active manager to approve this claim; ask an admin to assign one");
        }
        claim.markSubmitted(policy.requiresFinance(claim.getTotalAmount()), approver);
        return applyTransition(claim, ClaimAction.SUBMIT, me, null);
    }

    @Transactional
    public ClaimDetail approve(UUID claimId, String comment) {
        return transition(claimId, ClaimAction.APPROVE, comment);
    }

    @Transactional
    public ClaimDetail reject(UUID claimId, String comment) {
        requireComment(comment);
        return transition(claimId, ClaimAction.REJECT, comment);
    }

    @Transactional
    public ClaimDetail requestChanges(UUID claimId, String comment) {
        requireComment(comment);
        return transition(claimId, ClaimAction.REQUEST_CHANGES, comment);
    }

    @Transactional
    public ClaimDetail markPaid(UUID claimId, String comment) {
        return transition(claimId, ClaimAction.PAY, comment);
    }

    /**
     * System action used by the scheduler: hands a claim that has waited too long to the next manager up the
     * chain. Returns false when there is nothing to do (already handled, or no manager above).
     */
    @Transactional
    public boolean escalate(UUID claimId, Instant waitingSince, int days) {
        Claim claim = claims.findById(claimId).orElse(null);
        if (claim == null || claim.getStatus() != ClaimStatus.SUBMITTED
                || !claim.getStatusChangedAt().isBefore(waitingSince) || claim.getCurrentApprover() == null) {
            return false;
        }
        User previous = claim.getCurrentApprover();
        User next = firstActiveManagerAbove(previous);
        if (next == null) {
            return false;   // top of the hierarchy: nobody to escalate to
        }
        ClaimStateMachine.next(claim.getStatus(), ClaimAction.ESCALATE, claim.isRequiresFinance());
        Map<String, Object> before = Map.of("approver", previous.getId().toString());
        claim.reassignApprover(next);
        claims.saveAndFlush(claim);   // loses cleanly if someone approved in the meantime

        // The step is attributed to the manager who sat on the claim; the audit row has no actor (= system).
        String note = "Auto-escalated from " + previous.getFullName() + " to " + next.getFullName()
                + " after " + days + " day(s) without action";
        steps.save(new ApprovalStep(claim.getId(), previous, ClaimAction.ESCALATE, ClaimStatus.SUBMITTED,
                ClaimStatus.SUBMITTED, note));
        audit.record(claim.getOrganizationId(), "CLAIM", claim.getId(), "CLAIM_ESCALATED", before,
                Map.of("approver", next.getId().toString(), "reason", note));
        events.publishEvent(event(claim, ClaimAction.ESCALATE, ClaimStatus.SUBMITTED, null, note));
        return true;
    }

    // ---- internals -------------------------------------------------------------------------------------

    private ClaimDetail transition(UUID claimId, ClaimAction action, String comment) {
        UserPrincipal me = TenantContext.requireUser();
        Claim claim = loadVisible(claimId, me);

        if (claim.getClaimant().getId().equals(me.userId())) {
            throw new ForbiddenOperationException("MAKER_CHECKER_VIOLATION",
                    "You cannot " + action.name().toLowerCase().replace('_', ' ') + " your own claim");
        }
        ClaimStateMachine.next(claim.getStatus(), action, claim.isRequiresFinance());
        assertResponsibleForStage(me, claim);
        return applyTransition(claim, action, me, comment);
    }

    private void assertResponsibleForStage(UserPrincipal me, Claim claim) {
        switch (claim.getStatus()) {
            case SUBMITTED -> {
                boolean isApprover = claim.getCurrentApprover() != null
                        && claim.getCurrentApprover().getId().equals(me.userId());
                if (!isApprover || me.role() != Role.MANAGER) {
                    throw new ForbiddenOperationException("NOT_CURRENT_APPROVER",
                            "This claim is waiting for a different manager");
                }
            }
            case MANAGER_APPROVED, FINANCE_APPROVED -> {
                if (me.role() != Role.FINANCE) {
                    throw new ForbiddenOperationException("FINANCE_ROLE_REQUIRED",
                            "Only the finance team can act on this claim at this stage");
                }
            }
            default -> throw new ForbiddenOperationException("NOT_ALLOWED", "No one can act on this claim now");
        }
    }

    private ClaimDetail applyTransition(Claim claim, ClaimAction action, UserPrincipal me, String comment) {
        ClaimStatus from = claim.getStatus();
        ClaimStatus to = ClaimStateMachine.next(from, action, claim.isRequiresFinance());
        Map<String, Object> before = Map.of("status", from.name(), "version", claim.getVersion());

        claim.moveTo(to);
        if (to == ClaimStatus.DRAFT) {
            claim.clearApprover();
        }
        claims.saveAndFlush(claim);   // optimistic-lock check happens here, before any other write

        steps.save(new ApprovalStep(claim.getId(), users.getReferenceById(me.userId()), action, from, to, comment));
        audit.record(claim.getOrganizationId(), "CLAIM", claim.getId(), "CLAIM_" + action.name(), before,
                Map.of("status", to.name(), "requiresFinance", claim.isRequiresFinance(),
                        "total", claim.getTotalAmount().toPlainString()));
        events.publishEvent(event(claim, action, from, me.userId(), comment));
        return new ClaimDetail(claim, steps.findByClaimIdOrderByCreatedAtAsc(claim.getId()));
    }

    private ClaimStatusChangedEvent event(Claim c, ClaimAction action, ClaimStatus from, UUID actorId, String comment) {
        return new ClaimStatusChangedEvent(c.getId(), c.getOrganizationId(), c.getTitle(), c.getTotalAmount(), action,
                from, c.getStatus(), actorId, comment, c.getClaimant().getId(),
                c.getCurrentApprover() != null ? c.getCurrentApprover().getId() : null, c.isRequiresFinance());
    }

    private Claim loadVisible(UUID claimId, UserPrincipal me) {
        Claim claim = claims.findDetailed(claimId, me.organizationId())
                .orElseThrow(() -> new NotFoundException("Claim"));
        access.assertCanView(me, claim);
        return claim;
    }

    private PolicySnapshot loadPolicy(UUID organizationId) {
        try {
            return policies.getSnapshot(organizationId);
        } catch (NotFoundException e) {
            throw new ConflictException("POLICY_MISSING", "Your organisation has no expense policy; ask an admin to create one");
        }
    }

    /** Closest active MANAGER above {@code person} in the reporting chain, or null. */
    private User firstActiveManagerAbove(User person) {
        User cursor = person.getManager();
        for (int depth = 0; cursor != null && depth < MAX_CHAIN_DEPTH; depth++) {
            if (cursor.isActive() && cursor.getRole() == Role.MANAGER) {
                return cursor;
            }
            cursor = cursor.getManager();
        }
        return null;
    }

    private static void requireComment(String comment) {
        if (comment == null || comment.isBlank()) {
            throw new BadRequestException("COMMENT_REQUIRED", "A comment explaining the decision is required");
        }
    }
}

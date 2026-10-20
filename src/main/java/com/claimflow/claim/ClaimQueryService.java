package com.claimflow.claim;

import com.claimflow.claim.domain.Claim;
import com.claimflow.claim.domain.ClaimStatus;
import com.claimflow.common.exception.ForbiddenOperationException;
import com.claimflow.common.tenant.TenantContext;
import com.claimflow.common.web.SafePageable;
import com.claimflow.security.UserPrincipal;
import com.claimflow.user.Role;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

@Service
@Transactional(readOnly = true)
public class ClaimQueryService {

    private static final Set<String> SORTABLE = Set.of("createdAt", "submittedAt", "totalAmount", "status", "title");

    private final ClaimRepository claims;

    public ClaimQueryService(ClaimRepository claims) {
        this.claims = claims;
    }

    public Page<Claim> myClaims(ClaimStatus status, Pageable pageable) {
        UserPrincipal me = TenantContext.requireUser();
        return claims.findMine(me.organizationId(), me.userId(), status, page(pageable));
    }

    /** Managers see what is waiting on them; the finance team sees everything awaiting finance action. */
    public Page<Claim> pendingApprovals(Pageable pageable) {
        UserPrincipal me = TenantContext.requireUser();
        Pageable p = page(pageable);
        return switch (me.role()) {
            case MANAGER -> claims.findPendingForManager(me.organizationId(), me.userId(), p);
            case FINANCE -> claims.findPendingForFinance(me.organizationId(), me.userId(), p);
            default -> throw new ForbiddenOperationException("NO_APPROVAL_ROLE",
                    "Only managers and finance users have approval queues");
        };
    }

    private static Pageable page(Pageable pageable) {
        return SafePageable.of(pageable, SORTABLE, Sort.by(Sort.Direction.DESC, "createdAt"));
    }
}

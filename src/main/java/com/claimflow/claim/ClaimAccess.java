package com.claimflow.claim;

import com.claimflow.claim.domain.Claim;
import com.claimflow.claim.domain.ClaimStatus;
import com.claimflow.common.exception.NotFoundException;
import com.claimflow.security.UserPrincipal;
import com.claimflow.user.Role;
import com.claimflow.user.User;
import org.springframework.stereotype.Component;

/**
 * Who may see a claim. Organisation scoping is already guaranteed by the repository query; this decides
 * visibility inside the organisation. Drafts are private to their author.
 */
@Component
public class ClaimAccess {

    private static final int MAX_CHAIN_DEPTH = 50;

    public boolean canView(UserPrincipal me, Claim claim) {
        if (claim.getClaimant().getId().equals(me.userId())) {
            return true;
        }
        if (claim.getStatus() == ClaimStatus.DRAFT) {
            return false;
        }
        if (me.role() == Role.FINANCE || me.role() == Role.ADMIN) {
            return true;
        }
        if (claim.getCurrentApprover() != null && claim.getCurrentApprover().getId().equals(me.userId())) {
            return true;
        }
        return isInManagementChain(me, claim.getClaimant());
    }

    /** Hides existence (404) rather than leaking it (403) when the caller may not see the claim. */
    public void assertCanView(UserPrincipal me, Claim claim) {
        if (!canView(me, claim)) {
            throw new NotFoundException("Claim");
        }
    }

    private boolean isInManagementChain(UserPrincipal me, User claimant) {
        User cursor = claimant.getManager();
        for (int depth = 0; cursor != null && depth < MAX_CHAIN_DEPTH; depth++) {
            if (cursor.getId().equals(me.userId())) {
                return true;
            }
            cursor = cursor.getManager();
        }
        return false;
    }
}

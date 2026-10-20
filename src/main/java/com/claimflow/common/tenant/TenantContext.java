package com.claimflow.common.tenant;

import com.claimflow.security.UserPrincipal;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;
import java.util.UUID;

/**
 * Single place that answers "who is calling and which organisation do they belong to".
 * Services take the organisation id from here, never from request parameters, so a caller
 * cannot name another tenant.
 */
public final class TenantContext {

    private TenantContext() {
    }

    public static Optional<UserPrincipal> currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UserPrincipal p) {
            return Optional.of(p);
        }
        return Optional.empty();
    }

    public static UserPrincipal requireUser() {
        return currentUser().orElseThrow(() -> new IllegalStateException("No authenticated user in context"));
    }

    public static UUID organizationId() {
        return requireUser().organizationId();
    }

    public static UUID userId() {
        return requireUser().userId();
    }
}

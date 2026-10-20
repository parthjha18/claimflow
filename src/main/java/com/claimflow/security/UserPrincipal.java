package com.claimflow.security;

import com.claimflow.user.Role;
import java.util.UUID;

/** The authenticated caller, rebuilt from the JWT on every request (no DB hit). */
public record UserPrincipal(UUID userId, UUID organizationId, String email, Role role) {
}

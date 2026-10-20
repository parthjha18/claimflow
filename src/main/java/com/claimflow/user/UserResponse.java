package com.claimflow.user;

import java.util.UUID;

public record UserResponse(UUID id, String email, String fullName, Role role, UUID managerId, boolean active) {

    public static UserResponse from(User u) {
        return new UserResponse(u.getId(), u.getEmail(), u.getFullName(), u.getRole(),
                u.getManager() != null ? u.getManager().getId() : null, u.isActive());
    }
}

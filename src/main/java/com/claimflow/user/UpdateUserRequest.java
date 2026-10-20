package com.claimflow.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/** Null fields are left unchanged, except {@code managerId}, where {@code clearManager=true} removes it. */
public record UpdateUserRequest(
        @Size(max = 200) String fullName,
        Role role,
        UUID managerId,
        boolean clearManager,
        Boolean active) {
}

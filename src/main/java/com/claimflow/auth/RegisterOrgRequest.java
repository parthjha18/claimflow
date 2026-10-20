package com.claimflow.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterOrgRequest(
        @NotBlank @Size(max = 200) String organizationName,
        @NotBlank @Size(max = 200) String adminFullName,
        @NotBlank @Email @Size(max = 255) String adminEmail,
        @NotBlank @Size(min = 8, max = 72) String adminPassword) {   // 72 = BCrypt's input limit
}

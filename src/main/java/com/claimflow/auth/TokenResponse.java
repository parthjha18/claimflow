package com.claimflow.auth;

import java.util.UUID;

public record TokenResponse(String accessToken, String refreshToken, String tokenType, long expiresInSeconds,
                            UUID userId, UUID organizationId, String role) {
}

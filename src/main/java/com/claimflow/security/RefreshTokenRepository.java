package com.claimflow.security;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    @Modifying
    @Query("update RefreshToken t set t.revoked = true where t.userId = :userId and t.revoked = false")
    int revokeAllForUser(UUID userId);

    /** Atomic claim of a token: of two concurrent refreshes with the same token, only one gets 1 back. */
    @Modifying
    @Query("update RefreshToken t set t.revoked = true where t.tokenHash = :hash and t.revoked = false and t.expiresAt > :now")
    int revokeIfActive(String hash, java.time.Instant now);
}

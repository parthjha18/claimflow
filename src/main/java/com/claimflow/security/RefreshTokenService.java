package com.claimflow.security;

import com.claimflow.common.exception.ApiException;
import com.claimflow.user.User;
import com.claimflow.user.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Opaque, rotating refresh tokens. Only a SHA-256 hash is stored, so a database leak does not leak usable
 * tokens. Every refresh revokes the presented token and issues a new one; presenting an already-used token
 * is treated as theft and revokes all of that user's sessions.
 */
@Service
public class RefreshTokenService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final RefreshTokenRepository tokens;
    private final UserRepository users;
    private final JwtProperties props;
    private final Clock clock;

    public RefreshTokenService(RefreshTokenRepository tokens, UserRepository users, JwtProperties props, Clock clock) {
        this.tokens = tokens;
        this.users = users;
        this.props = props;
        this.clock = clock;
    }

    @Transactional
    public String issue(User user) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        tokens.save(new RefreshToken(user.getId(), hash(raw), clock.instant().plus(props.refreshTtl())));
        return raw;
    }

    /** Validates and consumes {@code raw}; returns the owner so the caller can mint a fresh pair. */
    @Transactional(noRollbackFor = ApiException.class)
    public User consume(String raw) {
        String hash = hash(raw);
        Instant now = clock.instant();
        RefreshToken stored = tokens.findByTokenHash(hash).orElseThrow(RefreshTokenService::invalid);

        if (stored.isRevoked()) {
            tokens.revokeAllForUser(stored.getUserId());
            throw invalid();
        }
        if (tokens.revokeIfActive(hash, now) != 1) {
            throw invalid();
        }
        User user = users.findById(stored.getUserId()).orElseThrow(RefreshTokenService::invalid);
        if (!user.isActive()) {
            throw invalid();
        }
        return user;
    }

    private static ApiException invalid() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_REFRESH_TOKEN", "Refresh token is invalid or expired");
    }

    static String hash(String raw) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}

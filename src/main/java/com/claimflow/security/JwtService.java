package com.claimflow.security;

import com.claimflow.user.Role;
import com.claimflow.user.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

/**
 * Issues and verifies short-lived access tokens. The token carries user id, organisation and role, so
 * authorisation never needs a database round trip. Refresh tokens are opaque and live in the database
 * (see {@link RefreshTokenService}), which is what makes them revocable.
 */
@Service
public class JwtService {

    private static final String CLAIM_ORG = "org";
    private static final String CLAIM_ROLE = "role";
    private static final String CLAIM_EMAIL = "email";

    private final SecretKey key;
    private final JwtProperties props;
    private final Clock clock;

    public JwtService(JwtProperties props, Clock clock) {
        this.props = props;
        this.clock = clock;
        this.key = Keys.hmacShaKeyFor(props.secret().getBytes(StandardCharsets.UTF_8));
    }

    public String createAccessToken(User user) {
        Instant now = clock.instant();
        return Jwts.builder()
                .subject(user.getId().toString())
                .claim(CLAIM_ORG, user.getOrganizationId().toString())
                .claim(CLAIM_ROLE, user.getRole().name())
                .claim(CLAIM_EMAIL, user.getEmail())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(props.accessTtl())))
                .signWith(key)
                .compact();
    }

    /** Empty when the token is malformed, tampered with or expired. */
    public Optional<UserPrincipal> parse(String token) {
        try {
            Claims c = Jwts.parser().verifyWith(key).clock(() -> Date.from(clock.instant())).build()
                    .parseSignedClaims(token).getPayload();
            return Optional.of(new UserPrincipal(
                    UUID.fromString(c.getSubject()),
                    UUID.fromString(c.get(CLAIM_ORG, String.class)),
                    c.get(CLAIM_EMAIL, String.class),
                    Role.valueOf(c.get(CLAIM_ROLE, String.class))));
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    public long accessTtlSeconds() {
        return props.accessTtl().toSeconds();
    }
}

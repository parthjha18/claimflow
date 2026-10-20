package com.claimflow.security;

import com.claimflow.user.Role;
import com.claimflow.user.User;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private static final String SECRET = "unit-test-secret-unit-test-secret-1234";
    private final UUID org = UUID.randomUUID();

    private User user() {
        User u = new User(org, "Boss@Example.com", "hash", "Boss", Role.MANAGER, null);
        ReflectionTestUtils.setField(u, "id", UUID.randomUUID());
        return u;
    }

    private JwtService service(Clock clock) {
        return new JwtService(new JwtProperties(SECRET, Duration.ofMinutes(15), Duration.ofDays(7)), clock);
    }

    @Test
    void roundTripsPrincipal() {
        JwtService svc = service(Clock.systemUTC());
        User u = user();
        UserPrincipal p = svc.parse(svc.createAccessToken(u)).orElseThrow();
        assertThat(p.userId()).isEqualTo(u.getId());
        assertThat(p.organizationId()).isEqualTo(org);
        assertThat(p.role()).isEqualTo(Role.MANAGER);
        assertThat(p.email()).isEqualTo("boss@example.com");
    }

    @Test
    void expiredTokenIsRejected() {
        Instant issued = Instant.parse("2026-01-01T00:00:00Z");
        String token = service(Clock.fixed(issued, ZoneOffset.UTC)).createAccessToken(user());
        JwtService later = service(Clock.fixed(issued.plus(Duration.ofMinutes(16)), ZoneOffset.UTC));
        assertThat(later.parse(token)).isEmpty();
    }

    @Test
    void tamperedAndGarbageTokensAreRejected() {
        JwtService svc = service(Clock.systemUTC());
        String token = svc.createAccessToken(user());
        assertThat(svc.parse(token.substring(0, token.length() - 3) + "abc")).isEmpty();
        assertThat(svc.parse("not-a-jwt")).isEmpty();
    }

    @Test
    void tokenSignedWithAnotherKeyIsRejected() {
        JwtService other = new JwtService(
                new JwtProperties("another-secret-another-secret-another-0", null, null), Clock.systemUTC());
        assertThat(service(Clock.systemUTC()).parse(other.createAccessToken(user()))).isEmpty();
    }

    @Test
    void shortSecretIsRefused() {
        assertThatThrownBy(() -> new JwtProperties("short", null, null)).isInstanceOf(IllegalArgumentException.class);
    }
}

package com.claimflow.auth;

import com.claimflow.common.exception.ApiException;
import com.claimflow.common.exception.ConflictException;
import com.claimflow.organization.Organization;
import com.claimflow.organization.OrganizationRepository;
import com.claimflow.policy.PolicyService;
import com.claimflow.security.JwtService;
import com.claimflow.security.RefreshTokenService;
import com.claimflow.user.Role;
import com.claimflow.user.User;
import com.claimflow.user.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final OrganizationRepository organizations;
    private final UserRepository users;
    private final PolicyService policies;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final RefreshTokenService refreshTokens;

    public AuthService(OrganizationRepository organizations, UserRepository users, PolicyService policies,
                       PasswordEncoder encoder, JwtService jwt, RefreshTokenService refreshTokens) {
        this.organizations = organizations;
        this.users = users;
        this.policies = policies;
        this.encoder = encoder;
        this.jwt = jwt;
        this.refreshTokens = refreshTokens;
    }

    /** Creates a tenant: the organisation, its first ADMIN and a default expense policy, atomically. */
    @Transactional
    public TokenResponse registerOrganization(RegisterOrgRequest req) {
        if (organizations.existsByNameIgnoreCase(req.organizationName())) {
            throw new ConflictException("ORG_EXISTS", "An organisation with that name already exists");
        }
        if (users.existsByEmailIgnoreCase(req.adminEmail())) {
            throw new ConflictException("EMAIL_TAKEN", "That email is already registered");
        }
        Organization org = organizations.save(new Organization(req.organizationName().trim()));
        User admin = users.save(new User(org.getId(), req.adminEmail().trim(), encoder.encode(req.adminPassword()),
                req.adminFullName().trim(), Role.ADMIN, null));
        policies.createDefault(org.getId());
        return tokensFor(admin);
    }

    @Transactional
    public TokenResponse login(LoginRequest req) {
        User user = users.findByEmailIgnoreCase(req.email())
                .filter(User::isActive)
                .filter(u -> encoder.matches(req.password(), u.getPasswordHash()))
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS",
                        "Invalid email or password"));
        return tokensFor(user);
    }

    @Transactional(noRollbackFor = ApiException.class)
    public TokenResponse refresh(RefreshRequest req) {
        return tokensFor(refreshTokens.consume(req.refreshToken()));
    }

    private TokenResponse tokensFor(User user) {
        return new TokenResponse(jwt.createAccessToken(user), refreshTokens.issue(user), "Bearer",
                jwt.accessTtlSeconds(), user.getId(), user.getOrganizationId(), user.getRole().name());
    }
}

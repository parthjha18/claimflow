package com.claimflow.user;

import com.claimflow.common.audit.AuditService;
import com.claimflow.common.exception.BadRequestException;
import com.claimflow.common.exception.ConflictException;
import com.claimflow.common.exception.NotFoundException;
import com.claimflow.common.tenant.TenantContext;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class UserService {

    private static final int MAX_CHAIN_DEPTH = 50;

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final AuditService audit;

    public UserService(UserRepository users, PasswordEncoder encoder, AuditService audit) {
        this.users = users;
        this.encoder = encoder;
        this.audit = audit;
    }

    @Transactional
    public User create(CreateUserRequest req) {
        UUID orgId = TenantContext.organizationId();
        if (users.existsByEmailIgnoreCase(req.email())) {
            throw new ConflictException("EMAIL_TAKEN", "That email is already registered");
        }
        User manager = req.managerId() == null ? null : requireManager(req.managerId(), orgId);
        User user = users.save(new User(orgId, req.email().trim(), encoder.encode(req.password()),
                req.fullName().trim(), req.role(), manager));
        audit.record(orgId, "USER", user.getId(), "USER_CREATED", null, snapshot(user));
        return user;
    }

    @Transactional
    public User update(UUID id, UpdateUserRequest req) {
        UUID orgId = TenantContext.organizationId();
        User user = find(id);
        Map<String, Object> before = snapshot(user);

        boolean self = user.getId().equals(TenantContext.userId());
        if (self && ((req.role() != null && req.role() != Role.ADMIN) || Boolean.FALSE.equals(req.active()))) {
            throw new BadRequestException("SELF_LOCKOUT", "Admins cannot demote or deactivate themselves");
        }
        if (req.fullName() != null && !req.fullName().isBlank()) {
            user.setFullName(req.fullName().trim());
        }
        if (req.role() != null) {
            user.setRole(req.role());
        }
        if (req.active() != null) {
            user.setActive(req.active());
        }
        if (req.clearManager()) {
            user.setManager(null);
        } else if (req.managerId() != null) {
            User manager = requireManager(req.managerId(), orgId);
            assertNoCycle(user, manager);
            user.setManager(manager);
        }
        users.saveAndFlush(user);
        audit.record(orgId, "USER", user.getId(), "USER_UPDATED", before, snapshot(user));
        return user;
    }

    @Transactional(readOnly = true)
    public User get(UUID id) {
        return find(id);
    }

    @Transactional(readOnly = true)
    public Page<User> list(Pageable pageable) {
        return users.findByOrganizationId(TenantContext.organizationId(), pageable);
    }

    private User find(UUID id) {
        return users.findByIdAndOrganizationId(id, TenantContext.organizationId())
                .orElseThrow(() -> new NotFoundException("User"));
    }

    private User requireManager(UUID managerId, UUID orgId) {
        User manager = users.findByIdAndOrganizationId(managerId, orgId)
                .orElseThrow(() -> new BadRequestException("INVALID_MANAGER", "Manager does not exist in this organisation"));
        if (manager.getRole() != Role.MANAGER || !manager.isActive()) {
            throw new BadRequestException("INVALID_MANAGER", "A manager must be an active user with the MANAGER role");
        }
        return manager;
    }

    /** Walks up from the proposed manager; reaching {@code user} would create a loop in the hierarchy. */
    private void assertNoCycle(User user, User proposedManager) {
        User cursor = proposedManager;
        for (int depth = 0; cursor != null && depth < MAX_CHAIN_DEPTH; depth++) {
            if (cursor.getId().equals(user.getId())) {
                throw new BadRequestException("MANAGER_CYCLE", "That assignment would create a reporting loop");
            }
            cursor = cursor.getManager();
        }
    }

    private static Map<String, Object> snapshot(User u) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("email", u.getEmail());
        m.put("fullName", u.getFullName());
        m.put("role", u.getRole().name());
        m.put("managerId", u.getManager() != null ? u.getManager().getId().toString() : null);
        m.put("active", u.isActive());
        return m;
    }
}

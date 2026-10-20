package com.claimflow.common.audit;

import com.claimflow.common.tenant.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

/**
 * Writes the business audit trail. Joins the caller's transaction, so an audit row exists if and only if
 * the change it describes was committed. The actor is read from the security context; background jobs
 * have none and are recorded as the system (null actor).
 */
@Service
public class AuditService {

    private final AuditLogRepository repository;

    public AuditService(AuditLogRepository repository) {
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void record(UUID organizationId, String entityType, UUID entityId, String action,
                       Map<String, Object> oldValue, Map<String, Object> newValue) {
        UUID actor = TenantContext.currentUser().map(u -> u.userId()).orElse(null);
        repository.save(new AuditLog(organizationId, entityType, entityId, action, actor, oldValue, newValue));
    }
}

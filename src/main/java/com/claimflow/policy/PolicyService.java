package com.claimflow.policy;

import com.claimflow.claim.domain.ExpenseCategory;
import com.claimflow.common.exception.ConflictException;
import com.claimflow.common.exception.NotFoundException;
import com.claimflow.config.CacheConfig;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

@Service
public class PolicyService {

    private final ExpensePolicyRepository repository;
    private final CacheManager cacheManager;

    public PolicyService(ExpensePolicyRepository repository, CacheManager cacheManager) {
        this.repository = repository;
        this.cacheManager = cacheManager;
    }

    /** Hot path: read on every submit. Served from Redis; the policy changes rarely. */
    @Cacheable(cacheNames = CacheConfig.POLICIES, key = "#organizationId")
    @Transactional(readOnly = true)
    public PolicySnapshot getSnapshot(UUID organizationId) {
        return repository.findByOrganizationId(organizationId)
                .map(PolicySnapshot::from)
                .orElseThrow(() -> new NotFoundException("Expense policy"));
    }

    @Transactional
    public PolicySnapshot create(UUID organizationId, PolicyRequest req) {
        if (repository.findByOrganizationId(organizationId).isPresent()) {
            throw new ConflictException("POLICY_EXISTS", "This organisation already has a policy; use PUT to change it");
        }
        ExpensePolicy policy = new ExpensePolicy(organizationId);
        apply(policy, req);
        repository.save(policy);
        evictAfterCommit(organizationId);
        return PolicySnapshot.from(policy);
    }

    @Transactional
    public PolicySnapshot update(UUID organizationId, PolicyRequest req) {
        ExpensePolicy policy = repository.findByOrganizationId(organizationId)
                .orElseThrow(() -> new NotFoundException("Expense policy"));
        apply(policy, req);
        repository.saveAndFlush(policy);
        evictAfterCommit(organizationId);
        return PolicySnapshot.from(policy);
    }

    @Transactional
    public void delete(UUID organizationId) {
        ExpensePolicy policy = repository.findByOrganizationId(organizationId)
                .orElseThrow(() -> new NotFoundException("Expense policy"));
        repository.delete(policy);
        evictAfterCommit(organizationId);
    }

    /** Default limits given to every new organisation so claims can be submitted straight away. */
    @Transactional
    public void createDefault(UUID organizationId) {
        ExpensePolicy policy = new ExpensePolicy(organizationId);
        policy.update(new BigDecimal("100000.00"), new BigDecimal("500.00"), new BigDecimal("10000.00"), 3,
                Map.of(ExpenseCategory.TRAVEL, new BigDecimal("50000.00"),
                        ExpenseCategory.FOOD, new BigDecimal("10000.00"),
                        ExpenseCategory.LODGING, new BigDecimal("40000.00"),
                        ExpenseCategory.OTHER, new BigDecimal("20000.00")));
        repository.save(policy);
    }

    private void apply(ExpensePolicy policy, PolicyRequest r) {
        policy.update(r.perClaimMax(), r.receiptRequiredAbove(), r.financeApprovalAbove(),
                r.escalationAfterDays(), r.categoryLimits());
    }

    /**
     * Evicting inside the transaction would let a concurrent reader re-populate the cache with the old row
     * before we commit. Evicting after commit closes that window.
     */
    private void evictAfterCommit(UUID organizationId) {
        Runnable evict = () -> {
            Cache cache = cacheManager.getCache(CacheConfig.POLICIES);
            if (cache != null) {
                cache.evict(organizationId);
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    evict.run();
                }
            });
        } else {
            evict.run();
        }
    }
}

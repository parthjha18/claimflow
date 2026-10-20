package com.claimflow.policy;

import com.claimflow.claim.domain.ExpenseCategory;
import com.claimflow.common.audit.AuditedEntity;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapKeyColumn;
import jakarta.persistence.MapKeyEnumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "expense_policies")
public class ExpensePolicy extends AuditedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "organization_id", nullable = false, updatable = false, unique = true)
    private UUID organizationId;

    @Column(name = "per_claim_max", nullable = false)
    private BigDecimal perClaimMax;

    @Column(name = "receipt_required_above", nullable = false)
    private BigDecimal receiptRequiredAbove;

    @Column(name = "finance_approval_above", nullable = false)
    private BigDecimal financeApprovalAbove;

    @Column(name = "escalation_after_days", nullable = false)
    private int escalationAfterDays = 3;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "policy_category_limits", joinColumns = @JoinColumn(name = "policy_id"))
    @MapKeyColumn(name = "category")
    @MapKeyEnumerated(EnumType.STRING)
    @Column(name = "max_amount", nullable = false)
    private Map<ExpenseCategory, BigDecimal> categoryLimits = new EnumMap<>(ExpenseCategory.class);

    @Version
    private long version;

    protected ExpensePolicy() {
    }

    public ExpensePolicy(UUID organizationId) {
        this.organizationId = organizationId;
    }

    public UUID getId() {
        return id;
    }

    public UUID getOrganizationId() {
        return organizationId;
    }

    public BigDecimal getPerClaimMax() {
        return perClaimMax;
    }

    public BigDecimal getReceiptRequiredAbove() {
        return receiptRequiredAbove;
    }

    public BigDecimal getFinanceApprovalAbove() {
        return financeApprovalAbove;
    }

    public int getEscalationAfterDays() {
        return escalationAfterDays;
    }

    public Map<ExpenseCategory, BigDecimal> getCategoryLimits() {
        return categoryLimits;
    }

    public long getVersion() {
        return version;
    }

    public void update(BigDecimal perClaimMax, BigDecimal receiptRequiredAbove, BigDecimal financeApprovalAbove,
                       int escalationAfterDays, Map<ExpenseCategory, BigDecimal> limits) {
        this.perClaimMax = perClaimMax;
        this.receiptRequiredAbove = receiptRequiredAbove;
        this.financeApprovalAbove = financeApprovalAbove;
        this.escalationAfterDays = escalationAfterDays;
        this.categoryLimits.clear();
        this.categoryLimits.putAll(limits);
    }
}

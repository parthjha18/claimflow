package com.claimflow.claim.domain;

import com.claimflow.common.audit.AuditedEntity;
import com.claimflow.user.User;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "claims")
public class Claim extends AuditedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "claimant_id", nullable = false, updatable = false)
    private User claimant;

    @Column(nullable = false)
    private String title;

    private String description;

    @Column(name = "total_amount", nullable = false)
    private BigDecimal totalAmount = BigDecimal.ZERO;

    @Column(nullable = false, length = 3)
    private String currency = "INR";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ClaimStatus status = ClaimStatus.DRAFT;

    @Column(name = "requires_finance", nullable = false)
    private boolean requiresFinance;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "current_approver_id")
    private User currentApprover;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "status_changed_at", nullable = false)
    private Instant statusChangedAt = Instant.now();

    /** Optimistic lock: of two concurrent approvals, the second flush fails with a version conflict. */
    @Version
    private long version;

    @OneToMany(mappedBy = "claim", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("expenseDate ASC, createdAt ASC")
    private List<ClaimLineItem> items = new ArrayList<>();

    protected Claim() {
    }

    public Claim(User claimant, String title, String description) {
        this.organizationId = claimant.getOrganizationId();
        this.claimant = claimant;
        this.title = title;
        this.description = description;
    }

    public ClaimLineItem addItem(ExpenseCategory category, BigDecimal amount, LocalDate date, String description) {
        ClaimLineItem item = new ClaimLineItem(this, organizationId, category, amount, date, description);
        items.add(item);
        recalculateTotal();
        return item;
    }

    public boolean removeItem(UUID itemId) {
        boolean removed = items.removeIf(i -> itemId.equals(i.getId()));
        if (removed) {
            recalculateTotal();
        }
        return removed;
    }

    public void recalculateTotal() {
        this.totalAmount = items.stream().map(ClaimLineItem::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** Applies a state-machine decision. Callers must have validated it with {@link ClaimStateMachine}. */
    public void moveTo(ClaimStatus next) {
        this.status = next;
        this.statusChangedAt = Instant.now();
    }

    public void markSubmitted(boolean requiresFinance, User approver) {
        this.requiresFinance = requiresFinance;
        this.currentApprover = approver;
        this.submittedAt = Instant.now();
    }

    public void clearApprover() {
        this.currentApprover = null;
    }

    public void reassignApprover(User approver) {
        this.currentApprover = approver;
        this.statusChangedAt = Instant.now();
    }

    public void updateDetails(String title, String description) {
        this.title = title;
        this.description = description;
    }

    public UUID getId() {
        return id;
    }

    public UUID getOrganizationId() {
        return organizationId;
    }

    public User getClaimant() {
        return claimant;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public String getCurrency() {
        return currency;
    }

    public ClaimStatus getStatus() {
        return status;
    }

    public boolean isRequiresFinance() {
        return requiresFinance;
    }

    public User getCurrentApprover() {
        return currentApprover;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }

    public Instant getStatusChangedAt() {
        return statusChangedAt;
    }

    public long getVersion() {
        return version;
    }

    public List<ClaimLineItem> getItems() {
        return items;
    }
}

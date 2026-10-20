package com.claimflow.claim.domain;

import com.claimflow.common.audit.TimestampedEntity;
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
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "claim_line_items")
public class ClaimLineItem extends TimestampedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "claim_id", nullable = false, updatable = false)
    private Claim claim;

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ExpenseCategory category;

    @Column(nullable = false)
    private BigDecimal amount;

    @Column(name = "expense_date", nullable = false)
    private LocalDate expenseDate;

    private String description;

    @Column(name = "receipt_ref")
    private String receiptRef;

    @Column(name = "receipt_filename")
    private String receiptFilename;

    @Column(name = "receipt_content_type")
    private String receiptContentType;

    protected ClaimLineItem() {
    }

    ClaimLineItem(Claim claim, UUID organizationId, ExpenseCategory category, BigDecimal amount,
                  LocalDate expenseDate, String description) {
        this.claim = claim;
        this.organizationId = organizationId;
        this.category = category;
        this.amount = amount;
        this.expenseDate = expenseDate;
        this.description = description;
    }

    public UUID getId() {
        return id;
    }

    public Claim getClaim() {
        return claim;
    }

    public UUID getOrganizationId() {
        return organizationId;
    }

    public ExpenseCategory getCategory() {
        return category;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public LocalDate getExpenseDate() {
        return expenseDate;
    }

    public String getDescription() {
        return description;
    }

    public String getReceiptRef() {
        return receiptRef;
    }

    public String getReceiptFilename() {
        return receiptFilename;
    }

    public String getReceiptContentType() {
        return receiptContentType;
    }

    public boolean hasReceipt() {
        return receiptRef != null;
    }

    public void update(ExpenseCategory category, BigDecimal amount, LocalDate expenseDate, String description) {
        this.category = category;
        this.amount = amount;
        this.expenseDate = expenseDate;
        this.description = description;
    }

    public void attachReceipt(String ref, String filename, String contentType) {
        this.receiptRef = ref;
        this.receiptFilename = filename;
        this.receiptContentType = contentType;
    }

    public void clearReceipt() {
        this.receiptRef = null;
        this.receiptFilename = null;
        this.receiptContentType = null;
    }
}

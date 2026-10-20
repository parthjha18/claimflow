package com.claimflow.claim;

import com.claimflow.claim.approval.ApprovalStepRepository;
import com.claimflow.claim.domain.Claim;
import com.claimflow.claim.domain.ClaimLineItem;
import com.claimflow.claim.domain.ClaimStatus;
import com.claimflow.claim.dto.ClaimRequest;
import com.claimflow.claim.dto.LineItemRequest;
import com.claimflow.common.audit.AuditService;
import com.claimflow.common.exception.ConflictException;
import com.claimflow.common.exception.ForbiddenOperationException;
import com.claimflow.common.exception.NotFoundException;
import com.claimflow.common.tenant.TenantContext;
import com.claimflow.security.UserPrincipal;
import com.claimflow.storage.StorageService;
import com.claimflow.user.User;
import com.claimflow.user.UserRepository;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Everything a claimant does while a claim is still a DRAFT. Status changes live in ClaimWorkflowService. */
@Service
public class ClaimService {

    private final ClaimRepository claims;
    private final UserRepository users;
    private final ApprovalStepRepository steps;
    private final ClaimAccess access;
    private final StorageService storage;
    private final AuditService audit;

    public ClaimService(ClaimRepository claims, UserRepository users, ApprovalStepRepository steps,
                        ClaimAccess access, StorageService storage, AuditService audit) {
        this.claims = claims;
        this.users = users;
        this.steps = steps;
        this.access = access;
        this.storage = storage;
        this.audit = audit;
    }

    @Transactional
    public ClaimDetail create(ClaimRequest req) {
        UserPrincipal me = TenantContext.requireUser();
        User claimant = users.getReferenceById(me.userId());
        Claim claim = new Claim(claimant, req.title().trim(), req.description());
        if (req.items() != null) {
            req.items().forEach(i -> claim.addItem(i.category(), i.amount(), i.expenseDate(), i.description()));
        }
        claims.save(claim);
        audit.record(me.organizationId(), "CLAIM", claim.getId(), "CLAIM_CREATED", null, snapshot(claim));
        return detailOf(claim);
    }

    @Transactional(readOnly = true)
    public ClaimDetail getDetail(UUID id) {
        UserPrincipal me = TenantContext.requireUser();
        Claim claim = load(id, me);
        access.assertCanView(me, claim);
        return new ClaimDetail(claim, steps.findByClaimIdOrderByCreatedAtAsc(claim.getId()));
    }

    @Transactional
    public ClaimDetail updateDetails(UUID id, ClaimRequest req) {
        Claim claim = loadOwnDraft(id);
        Map<String, Object> before = snapshot(claim);
        claim.updateDetails(req.title().trim(), req.description());
        claims.saveAndFlush(claim);
        audit.record(claim.getOrganizationId(), "CLAIM", claim.getId(), "CLAIM_UPDATED", before, snapshot(claim));
        return detailOf(claim);
    }

    @Transactional
    public ClaimDetail addItem(UUID claimId, LineItemRequest req) {
        Claim claim = loadOwnDraft(claimId);
        Map<String, Object> before = snapshot(claim);
        claim.addItem(req.category(), req.amount(), req.expenseDate(), req.description());
        claims.saveAndFlush(claim);
        audit.record(claim.getOrganizationId(), "CLAIM", claim.getId(), "ITEM_ADDED", before, snapshot(claim));
        return detailOf(claim);
    }

    @Transactional
    public ClaimDetail updateItem(UUID claimId, UUID itemId, LineItemRequest req) {
        Claim claim = loadOwnDraft(claimId);
        Map<String, Object> before = snapshot(claim);
        findItem(claim, itemId).update(req.category(), req.amount(), req.expenseDate(), req.description());
        claim.recalculateTotal();
        claims.saveAndFlush(claim);
        audit.record(claim.getOrganizationId(), "CLAIM", claim.getId(), "ITEM_UPDATED", before, snapshot(claim));
        return detailOf(claim);
    }

    @Transactional
    public ClaimDetail removeItem(UUID claimId, UUID itemId) {
        Claim claim = loadOwnDraft(claimId);
        Map<String, Object> before = snapshot(claim);
        ClaimLineItem item = findItem(claim, itemId);
        String receiptRef = item.getReceiptRef();
        claim.removeItem(itemId);
        claims.saveAndFlush(claim);
        audit.record(claim.getOrganizationId(), "CLAIM", claim.getId(), "ITEM_REMOVED", before, snapshot(claim));
        if (receiptRef != null) {
            deleteFileAfterCommit(receiptRef);
        }
        return detailOf(claim);
    }

    @Transactional
    public ClaimDetail attachReceipt(UUID claimId, UUID itemId, MultipartFile file) {
        Claim claim = loadOwnDraft(claimId);
        ClaimLineItem item = findItem(claim, itemId);
        if (file.isEmpty()) {
            throw new com.claimflow.storage.InvalidUploadException("Uploaded file is empty");
        }
        String oldRef = item.getReceiptRef();
        String newRef;
        try (InputStream in = file.getInputStream()) {
            newRef = storage.store(claim.getOrganizationId(), in, file.getOriginalFilename(), file.getContentType());
        } catch (IOException e) {
            throw new IllegalStateException("Could not store receipt", e);
        }
        item.attachReceipt(newRef, sanitize(file.getOriginalFilename()), file.getContentType());
        claims.saveAndFlush(claim);
        audit.record(claim.getOrganizationId(), "CLAIM", claim.getId(), "RECEIPT_ATTACHED", null,
                Map.of("itemId", itemId.toString(), "filename", String.valueOf(item.getReceiptFilename())));
        // a failed commit must not leave a replaced receipt deleted, so remove the old file only afterwards
        if (oldRef != null) {
            deleteFileAfterCommit(oldRef);
        }
        return detailOf(claim);
    }

    @Transactional(readOnly = true)
    public ReceiptFile loadReceipt(UUID claimId, UUID itemId) {
        UserPrincipal me = TenantContext.requireUser();
        Claim claim = load(claimId, me);
        access.assertCanView(me, claim);
        ClaimLineItem item = findItem(claim, itemId);
        if (!item.hasReceipt()) {
            throw new NotFoundException("Receipt");
        }
        return new ReceiptFile(storage.load(item.getReceiptRef()), item.getReceiptFilename(), item.getReceiptContentType());
    }

    @Transactional
    public void deleteDraft(UUID id) {
        Claim claim = loadOwnDraft(id);
        claim.getItems().stream().map(ClaimLineItem::getReceiptRef).filter(r -> r != null)
                .forEach(this::deleteFileAfterCommit);
        audit.record(claim.getOrganizationId(), "CLAIM", claim.getId(), "CLAIM_DELETED", snapshot(claim), null);
        claims.delete(claim);
    }

    // ---- helpers ---------------------------------------------------------------------------------------

    private ClaimDetail detailOf(Claim claim) {
        return new ClaimDetail(claim, steps.findByClaimIdOrderByCreatedAtAsc(claim.getId()));
    }

    Claim load(UUID id, UserPrincipal me) {
        return claims.findDetailed(id, me.organizationId()).orElseThrow(() -> new NotFoundException("Claim"));
    }

    private Claim loadOwnDraft(UUID id) {
        UserPrincipal me = TenantContext.requireUser();
        Claim claim = load(id, me);
        access.assertCanView(me, claim);
        if (!claim.getClaimant().getId().equals(me.userId())) {
            throw new ForbiddenOperationException("NOT_CLAIMANT", "Only the claimant can edit this claim");
        }
        if (claim.getStatus() != ClaimStatus.DRAFT) {
            throw new ConflictException("CLAIM_NOT_EDITABLE", "Only DRAFT claims can be edited; this one is " + claim.getStatus());
        }
        return claim;
    }

    private static ClaimLineItem findItem(Claim claim, UUID itemId) {
        return claim.getItems().stream().filter(i -> i.getId().equals(itemId)).findFirst()
                .orElseThrow(() -> new NotFoundException("Line item"));
    }

    private void deleteFileAfterCommit(String ref) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    storage.delete(ref);
                }
            });
        } else {
            storage.delete(ref);
        }
    }

    private static String sanitize(String filename) {
        if (filename == null || filename.isBlank()) {
            return "receipt";
        }
        String base = filename.replace('\\', '/');
        base = base.substring(base.lastIndexOf('/') + 1).replaceAll("[^A-Za-z0-9._ -]", "_");
        return base.length() > 200 ? base.substring(base.length() - 200) : base;
    }

    static Map<String, Object> snapshot(Claim c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("title", c.getTitle());
        m.put("status", c.getStatus().name());
        m.put("totalAmount", c.getTotalAmount().toPlainString());
        m.put("itemCount", c.getItems().size());
        return m;
    }

    public record ClaimDetail(Claim claim, java.util.List<com.claimflow.claim.approval.ApprovalStep> history) {
    }

    public record ReceiptFile(Resource resource, String filename, String contentType) {
    }
}

package com.claimflow.claim;

import com.claimflow.claim.dto.ClaimDetailResponse;
import com.claimflow.claim.dto.ClaimRequest;
import com.claimflow.claim.dto.LineItemRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/claims")
@Tag(name = "Claims")
@SecurityRequirement(name = "bearerAuth")
public class ClaimController {

    private final ClaimService service;

    public ClaimController(ClaimService service) {
        this.service = service;
    }

    private static ClaimDetailResponse body(ClaimService.ClaimDetail d) {
        return ClaimDetailResponse.from(d.claim(), d.history());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a DRAFT claim (optionally with line items)")
    public ClaimDetailResponse create(@Valid @RequestBody ClaimRequest request) {
        return body(service.create(request));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Claim with items and approval history")
    public ClaimDetailResponse get(@PathVariable UUID id) {
        return body(service.getDetail(id));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Edit title/description of my DRAFT claim")
    public ClaimDetailResponse update(@PathVariable UUID id, @Valid @RequestBody ClaimRequest request) {
        return body(service.updateDetails(id, request));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete my DRAFT claim")
    public void delete(@PathVariable UUID id) {
        service.deleteDraft(id);
    }

    @PostMapping("/{id}/items")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add a line item to my DRAFT claim")
    public ClaimDetailResponse addItem(@PathVariable UUID id, @Valid @RequestBody LineItemRequest request) {
        return body(service.addItem(id, request));
    }

    @PutMapping("/{id}/items/{itemId}")
    public ClaimDetailResponse updateItem(@PathVariable UUID id, @PathVariable UUID itemId,
                                          @Valid @RequestBody LineItemRequest request) {
        return body(service.updateItem(id, itemId, request));
    }

    @DeleteMapping("/{id}/items/{itemId}")
    public ClaimDetailResponse removeItem(@PathVariable UUID id, @PathVariable UUID itemId) {
        return body(service.removeItem(id, itemId));
    }

    @PostMapping(path = "/{id}/items/{itemId}/receipt", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload (or replace) the receipt for an item; PDF, PNG or JPEG")
    public ClaimDetailResponse uploadReceipt(@PathVariable UUID id, @PathVariable UUID itemId,
                                             @RequestPart("file") MultipartFile file) {
        return body(service.attachReceipt(id, itemId, file));
    }

    @GetMapping("/{id}/items/{itemId}/receipt")
    @Operation(summary = "Download an item's receipt")
    public ResponseEntity<org.springframework.core.io.Resource> downloadReceipt(@PathVariable UUID id,
                                                                                 @PathVariable UUID itemId) {
        ClaimService.ReceiptFile f = service.loadReceipt(id, itemId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(f.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(f.filename()).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(f.resource());
    }
}

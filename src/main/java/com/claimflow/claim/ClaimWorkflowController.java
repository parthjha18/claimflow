package com.claimflow.claim;

import com.claimflow.claim.dto.ClaimDetailResponse;
import com.claimflow.claim.dto.CommentRequest;
import com.claimflow.common.exception.BadRequestException;
import com.claimflow.common.tenant.TenantContext;
import com.claimflow.idempotency.IdempotencyService;
import com.claimflow.idempotency.IdempotencyService.Outcome;
import com.claimflow.idempotency.IdempotencyService.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;
import java.util.function.Supplier;

@RestController
@RequestMapping("/api/v1/claims/{id}")
@Tag(name = "Claim workflow")
@SecurityRequirement(name = "bearerAuth")
public class ClaimWorkflowController {

    private static final String IDEMPOTENCY_KEY = "Idempotency-Key";

    private final ClaimWorkflowService workflow;
    private final IdempotencyService idempotency;

    public ClaimWorkflowController(ClaimWorkflowService workflow, IdempotencyService idempotency) {
        this.workflow = workflow;
        this.idempotency = idempotency;
    }

    @PostMapping("/submit")
    @Operation(summary = "Submit a DRAFT claim: validates the org policy, picks the approver")
    @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = ClaimDetailResponse.class)))
    public ResponseEntity<String> submit(@PathVariable UUID id,
                                         @Parameter(description = "Makes the call safe to retry; same key returns the original result")
                                         @RequestHeader(value = IDEMPOTENCY_KEY, required = false) String key) {
        return run(key, "submit:" + id, "submit|" + id, () -> workflow.submit(id));
    }

    @PostMapping("/approve")
    @Operation(summary = "Approve at the current stage (manager, then finance for large claims)")
    @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = ClaimDetailResponse.class)))
    public ResponseEntity<String> approve(@PathVariable UUID id,
                                          @RequestHeader(value = IDEMPOTENCY_KEY, required = false) String key,
                                          @Valid @RequestBody(required = false) CommentRequest body) {
        String comment = comment(body);
        return run(key, "approve:" + id, "approve|" + id + "|" + comment, () -> workflow.approve(id, comment));
    }

    @PostMapping("/reject")
    @Operation(summary = "Reject the claim (terminal). A comment is required.")
    @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = ClaimDetailResponse.class)))
    public ResponseEntity<String> reject(@PathVariable UUID id,
                                         @RequestHeader(value = IDEMPOTENCY_KEY, required = false) String key,
                                         @Valid @RequestBody(required = false) CommentRequest body) {
        String comment = comment(body);
        return run(key, "reject:" + id, "reject|" + id + "|" + comment, () -> workflow.reject(id, comment));
    }

    @PostMapping("/request-changes")
    @Operation(summary = "Send the claim back to DRAFT for the claimant to fix. A comment is required.")
    @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = ClaimDetailResponse.class)))
    public ResponseEntity<String> requestChanges(@PathVariable UUID id,
                                                 @RequestHeader(value = IDEMPOTENCY_KEY, required = false) String key,
                                                 @Valid @RequestBody(required = false) CommentRequest body) {
        String comment = comment(body);
        return run(key, "request-changes:" + id, "request-changes|" + id + "|" + comment,
                () -> workflow.requestChanges(id, comment));
    }

    @PostMapping("/pay")
    @Operation(summary = "Mark the claim as paid (finance). Idempotent with an Idempotency-Key.")
    @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = ClaimDetailResponse.class)))
    public ResponseEntity<String> pay(@PathVariable UUID id,
                                      @RequestHeader(value = IDEMPOTENCY_KEY, required = false) String key,
                                      @Valid @RequestBody(required = false) CommentRequest body) {
        String comment = comment(body);
        return run(key, "pay:" + id, "pay|" + id + "|" + comment, () -> workflow.markPaid(id, comment));
    }

    private ResponseEntity<String> run(String key, String endpoint, String fingerprint,
                                       Supplier<ClaimService.ClaimDetail> action) {
        if (key != null && (key.isBlank() || key.length() > 100)) {
            throw new BadRequestException("INVALID_IDEMPOTENCY_KEY", "Idempotency-Key must be 1-100 characters");
        }
        Result r = idempotency.execute(TenantContext.userId(), key, endpoint, fingerprint, () -> {
            ClaimService.ClaimDetail d = action.get();
            return new Outcome(200, ClaimDetailResponse.from(d.claim(), d.history()));
        });
        ResponseEntity.BodyBuilder response = ResponseEntity.status(r.status()).contentType(MediaType.APPLICATION_JSON);
        if (r.replayed()) {
            response.header("Idempotent-Replay", "true");
        }
        return response.body(r.bodyJson());
    }

    private static String comment(CommentRequest body) {
        return body == null ? null : body.comment();
    }
}

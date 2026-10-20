package com.claimflow.policy;

import com.claimflow.common.tenant.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/policy")
@Tag(name = "Policy")
@SecurityRequirement(name = "bearerAuth")
public class PolicyController {

    private final PolicyService service;

    public PolicyController(PolicyService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Current expense policy of my organisation (any role, served from Redis)")
    public PolicyResponse get() {
        return PolicyResponse.from(service.getSnapshot(TenantContext.organizationId()));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Create the policy (only if the organisation has none)")
    public PolicyResponse create(@Valid @RequestBody PolicyRequest request) {
        return PolicyResponse.from(service.create(TenantContext.organizationId(), request));
    }

    @PutMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Replace the policy; evicts the Redis cache entry")
    public PolicyResponse update(@Valid @RequestBody PolicyRequest request) {
        return PolicyResponse.from(service.update(TenantContext.organizationId(), request));
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Delete the policy (claims cannot be submitted until a new one exists)")
    public void delete() {
        service.delete(TenantContext.organizationId());
    }
}

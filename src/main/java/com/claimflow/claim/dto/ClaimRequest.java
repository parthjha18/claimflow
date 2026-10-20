package com.claimflow.claim.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

/** Create/update of a draft. {@code items} is optional on create and ignored on update (use the item endpoints). */
public record ClaimRequest(
        @NotBlank @Size(max = 200) String title,
        @Size(max = 2000) String description,
        @Valid List<LineItemRequest> items) {
}

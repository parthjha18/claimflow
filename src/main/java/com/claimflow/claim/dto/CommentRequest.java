package com.claimflow.claim.dto;

import jakarta.validation.constraints.Size;

public record CommentRequest(@Size(max = 1000) String comment) {
}

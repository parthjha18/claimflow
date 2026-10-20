package com.claimflow.common.exception;

import org.springframework.http.HttpStatus;

/** Also used when a resource exists but belongs to another organisation, so tenants cannot probe ids. */
public class NotFoundException extends ApiException {

    public NotFoundException(String what) {
        super(HttpStatus.NOT_FOUND, "NOT_FOUND", what + " not found");
    }
}

package com.claimflow.storage;

import com.claimflow.common.exception.BadRequestException;

public class InvalidUploadException extends BadRequestException {

    public InvalidUploadException(String message) {
        super("INVALID_UPLOAD", message);
    }
}

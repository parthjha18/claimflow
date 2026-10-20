package com.claimflow.storage;

import org.springframework.core.io.Resource;

import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

/**
 * Where receipt files live. Callers only ever hold the opaque {@code ref} returned by {@link #store}, so the
 * backing store can move from local disk to S3 (an {@code S3StorageService} bean) without touching the
 * claim code or the database.
 */
public interface StorageService {

    /** Saves the stream under the organisation's namespace and returns the reference to persist. */
    String store(UUID organizationId, InputStream content, String originalFilename, String contentType)
            throws IOException;

    Resource load(String ref);

    /** Idempotent: deleting a missing ref is not an error. */
    void delete(String ref);
}

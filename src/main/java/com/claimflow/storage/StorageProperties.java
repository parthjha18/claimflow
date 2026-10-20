package com.claimflow.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "claimflow.storage")
public record StorageProperties(String localRoot, List<String> allowedContentTypes) {

    public StorageProperties {
        localRoot = localRoot != null ? localRoot : "./uploads";
        allowedContentTypes = allowedContentTypes != null && !allowedContentTypes.isEmpty()
                ? allowedContentTypes : List.of("application/pdf", "image/png", "image/jpeg");
    }
}

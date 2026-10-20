package com.claimflow.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "claimflow.security.jwt")
public record JwtProperties(String secret, Duration accessTtl, Duration refreshTtl) {

    public JwtProperties {
        if (secret == null || secret.getBytes().length < 32) {
            throw new IllegalArgumentException("claimflow.security.jwt.secret must be at least 32 bytes (256 bits)");
        }
        accessTtl = accessTtl != null ? accessTtl : Duration.ofMinutes(15);
        refreshTtl = refreshTtl != null ? refreshTtl : Duration.ofDays(7);
    }
}

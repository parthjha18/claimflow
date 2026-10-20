package com.claimflow.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Switched off in tests (claimflow.scheduling.enabled=false) so jobs only run when a test calls them. */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "claimflow.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class SchedulingConfig {
}

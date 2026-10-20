package com.claimflow.config;

import com.claimflow.common.tenant.TenantContext;
import com.claimflow.security.UserPrincipal;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

import java.util.Optional;
import java.util.UUID;

@Configuration
@EnableJpaAuditing(auditorAwareRef = "auditorProvider", dateTimeProviderRef = "auditingDateTimeProvider")
public class JpaAuditingConfig {

    /** Empty for system actors such as the escalation job, which leaves created_by/updated_by NULL. */
    @Bean
    AuditorAware<UUID> auditorProvider() {
        return () -> TenantContext.currentUser().map(UserPrincipal::userId);
    }

    @Bean
    org.springframework.data.auditing.DateTimeProvider auditingDateTimeProvider() {
        return () -> Optional.of(java.time.Instant.now());
    }
}

package com.claimflow.policy;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ExpensePolicyRepository extends JpaRepository<ExpensePolicy, UUID> {

    Optional<ExpensePolicy> findByOrganizationId(UUID organizationId);

    /** Just what the escalation job needs, without loading the category-limit collections. */
    @Query("select p.organizationId as organizationId, p.escalationAfterDays as days from ExpensePolicy p")
    List<EscalationSetting> findEscalationSettings();

    interface EscalationSetting {
        UUID getOrganizationId();

        int getDays();
    }
}

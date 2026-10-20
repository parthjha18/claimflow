package com.claimflow.policy;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ExpensePolicyRepository extends JpaRepository<ExpensePolicy, UUID> {

    Optional<ExpensePolicy> findByOrganizationId(UUID organizationId);
}

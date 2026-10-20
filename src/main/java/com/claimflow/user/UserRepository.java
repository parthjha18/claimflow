package com.claimflow.user;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    /** Every lookup that takes an id from a client also takes the organisation id. */
    Optional<User> findByIdAndOrganizationId(UUID id, UUID organizationId);

    List<User> findByOrganizationIdAndRoleAndActiveTrue(UUID organizationId, Role role);

    @EntityGraph(attributePaths = "manager")
    Page<User> findByOrganizationId(UUID organizationId, Pageable pageable);
}

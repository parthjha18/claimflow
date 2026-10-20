package com.claimflow.user;

import com.claimflow.common.audit.AuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "users")
public class User extends AuditedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "manager_id")
    private User manager;

    @Column(nullable = false)
    private boolean active = true;

    protected User() {
    }

    public User(UUID organizationId, String email, String passwordHash, String fullName, Role role, User manager) {
        this.organizationId = organizationId;
        this.email = email.toLowerCase();
        this.passwordHash = passwordHash;
        this.fullName = fullName;
        this.role = role;
        this.manager = manager;
    }

    public UUID getId() {
        return id;
    }

    public UUID getOrganizationId() {
        return organizationId;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getFullName() {
        return fullName;
    }

    public Role getRole() {
        return role;
    }

    public User getManager() {
        return manager;
    }

    public boolean isActive() {
        return active;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public void setRole(Role role) {
        this.role = role;
    }

    public void setManager(User manager) {
        this.manager = manager;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }
}

package com.claimflow.idempotency;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "idempotency_keys")
@EntityListeners(AuditingEntityListener.class)
public class IdempotencyKey {

    public enum State { IN_PROGRESS, COMPLETED }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "idem_key", nullable = false, updatable = false)
    private String key;

    @Column(nullable = false, updatable = false)
    private String endpoint;

    @Column(name = "request_hash", nullable = false, updatable = false)
    private String requestHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private State state = State.IN_PROGRESS;

    @Column(name = "response_status")
    private Integer responseStatus;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "response_body", columnDefinition = "jsonb")
    private String responseBody;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected IdempotencyKey() {
    }

    public IdempotencyKey(UUID userId, String key, String endpoint, String requestHash) {
        this.userId = userId;
        this.key = key;
        this.endpoint = endpoint;
        this.requestHash = requestHash;
    }

    public UUID getId() {
        return id;
    }

    public String getRequestHash() {
        return requestHash;
    }

    public State getState() {
        return state;
    }

    public Integer getResponseStatus() {
        return responseStatus;
    }

    public String getResponseBody() {
        return responseBody;
    }

    public void complete(int status, String body) {
        this.state = State.COMPLETED;
        this.responseStatus = status;
        this.responseBody = body;
    }
}

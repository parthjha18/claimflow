package com.claimflow.idempotency;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface IdempotencyKeyRepository extends JpaRepository<IdempotencyKey, UUID> {

    /**
     * Claims the key. With ON CONFLICT DO NOTHING, a concurrent request holding the same key blocks here until
     * the first transaction finishes, then gets 0 (it committed) or 1 (it rolled back and the key is free).
     */
    @Modifying
    @Query(value = """
            insert into idempotency_keys (id, user_id, idem_key, endpoint, request_hash, state, created_at)
            values (:id, :userId, :key, :endpoint, :requestHash, 'IN_PROGRESS', now())
            on conflict (user_id, idem_key, endpoint) do nothing
            """, nativeQuery = true)
    int tryInsert(UUID id, UUID userId, String key, String endpoint, String requestHash);

    Optional<IdempotencyKey> findByUserIdAndIdemKeyAndEndpoint(UUID userId, String idemKey, String endpoint);

    @Modifying
    @Query("delete from IdempotencyKey k where k.createdAt < :cutoff")
    int deleteOlderThan(Instant cutoff);
}

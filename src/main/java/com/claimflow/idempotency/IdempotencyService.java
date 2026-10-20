package com.claimflow.idempotency;

import com.claimflow.common.exception.ApiException;
import com.claimflow.common.exception.ConflictException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Makes unsafe POSTs safe to retry. The key row is written in the <em>same transaction</em> as the business
 * change, so either both commit or neither does: there is no "key stored but work lost" or "work done but key
 * missing" window, and no stale IN_PROGRESS rows to time out.
 */
@Service
public class IdempotencyService {

    public record Outcome(int status, Object body) {
    }

    public record Result(int status, String bodyJson, boolean replayed) {
    }

    private final IdempotencyKeyRepository repository;
    private final ObjectMapper mapper;

    public IdempotencyService(IdempotencyKeyRepository repository, ObjectMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    /**
     * @param key         value of the Idempotency-Key header; null means the client did not ask for idempotency
     * @param endpoint    what is being done and to what, e.g. "submit:{claimId}"
     * @param fingerprint canonical form of the request, so reusing a key for a different request is detected
     */
    @Transactional
    public Result execute(UUID userId, String key, String endpoint, String fingerprint, Supplier<Outcome> action) {
        if (key == null) {
            return render(action.get(), false);
        }
        String requestHash = sha256(fingerprint);
        int inserted = repository.tryInsert(UUID.randomUUID(), userId, key, endpoint, requestHash);

        if (inserted == 0) {
            IdempotencyKey existing = repository.findByUserIdAndIdemKeyAndEndpoint(userId, key, endpoint)
                    .orElseThrow(() -> new ConflictException("IDEMPOTENCY_RETRY", "Please retry the request"));
            if (!existing.getRequestHash().equals(requestHash)) {
                throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "IDEMPOTENCY_KEY_REUSED",
                        "This Idempotency-Key was already used for a different request");
            }
            if (existing.getState() != IdempotencyKey.State.COMPLETED) {
                throw new ConflictException("IDEMPOTENCY_IN_PROGRESS", "A request with this key is still running");
            }
            return new Result(existing.getResponseStatus(), existing.getResponseBody(), true);
        }

        Result result = render(action.get(), false);
        IdempotencyKey stored = repository.findByUserIdAndIdemKeyAndEndpoint(userId, key, endpoint).orElseThrow();
        stored.complete(result.status(), result.bodyJson());
        return result;
    }

    @Transactional
    public int purgeOlderThan(java.time.Instant cutoff) {
        return repository.deleteOlderThan(cutoff);
    }

    private Result render(Outcome outcome, boolean replayed) {
        try {
            return new Result(outcome.status(), mapper.writeValueAsString(outcome.body()), replayed);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialise response", e);
        }
    }

    private static String sha256(String s) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}

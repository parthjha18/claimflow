package com.claimflow.integration;

import com.claimflow.claim.ClaimRepository;
import com.claimflow.claim.domain.Claim;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConcurrentApprovalIT extends AbstractIntegrationTest {

    @Autowired
    ClaimRepository claims;
    @Autowired
    TransactionTemplate tx;

    /** The mechanism itself, deterministically: two stale copies of one row cannot both be saved. */
    @Test
    void secondWriterOfAStaleVersionFailsWithOptimisticLockError() {
        TestOrg org = createOrg();
        UUID id = UUID.fromString(draftClaim(org.employee(), "FOOD", "300.00", false));

        Claim copyA = tx.execute(s -> claims.findDetailed(id, orgOf(id)).orElseThrow());
        Claim copyB = tx.execute(s -> claims.findDetailed(id, orgOf(id)).orElseThrow());

        copyA.updateDetails("written by A", null);
        copyB.updateDetails("written by B", null);
        tx.executeWithoutResult(s -> claims.saveAndFlush(copyA));                       // first writer wins
        assertThatThrownBy(() -> tx.executeWithoutResult(s -> claims.saveAndFlush(copyB)))   // loser is rejected
                .isInstanceOf(OptimisticLockingFailureException.class);
        String title = tx.execute(s -> claims.findById(id).orElseThrow().getTitle());
        assertThat(title).isEqualTo("written by A");
    }

    private UUID orgOf(UUID claimId) {
        return tx.execute(s -> claims.findById(claimId).orElseThrow().getOrganizationId());
    }

    /** Two finance users approve the same claim at the same instant: exactly one succeeds, the other gets 409. */
    @Test
    void twoFinanceApproversRacingOnOneClaimYieldExactlyOneWinner() throws Exception {
        TestOrg org = createOrg();
        Actor finance2 = createUser(org.admin(), "fin2-" + UUID.randomUUID() + "@t.com", "Finance 2", "FINANCE", null);

        int rounds = 15;
        int conflictsFromLock = 0;
        for (int round = 0; round < rounds; round++) {
            String id = draftClaim(org.employee(), "TRAVEL", "12000.00", true);
            act(id, "submit", org.employee(), null);
            act(id, "approve", org.manager(), null);      // now MANAGER_APPROVED, waiting on finance

            List<ResponseEntity<JsonNode>> results = race(
                    () -> act(id, "approve", org.finance(), "first"),
                    () -> act(id, "approve", finance2, "second"));

            long ok = results.stream().filter(r -> r.getStatusCode() == HttpStatus.OK).count();
            long conflict = results.stream().filter(r -> r.getStatusCode() == HttpStatus.CONFLICT).count();
            assertThat(ok).as("round %d: exactly one approver wins", round).isEqualTo(1);
            assertThat(conflict).as("round %d: the other gets 409", round).isEqualTo(1);

            JsonNode loser = results.stream().filter(r -> r.getStatusCode() == HttpStatus.CONFLICT).findFirst().orElseThrow().getBody();
            assertThat(loser.get("code").asText()).isIn("CONCURRENT_MODIFICATION", "INVALID_STATE_TRANSITION");
            if (loser.get("code").asText().equals("CONCURRENT_MODIFICATION")) {
                conflictsFromLock++;
            }

            JsonNode finalClaim = call(HttpMethod.GET, "/api/v1/claims/" + id, org.finance().token(), null).getBody();
            assertThat(finalClaim.get("claim").get("status").asText()).isEqualTo("FINANCE_APPROVED");
            assertThat(finalClaim.get("history")).as("only the winner's step is recorded").hasSize(3);   // SUBMIT, APPROVE, APPROVE
        }
        System.out.printf("[ConcurrentApprovalIT] %d rounds: %d losers were stopped by the @Version check, %d by the state check%n",
                rounds, conflictsFromLock, rounds - conflictsFromLock);
    }

    /** Approve and reject by the same manager at once: still exactly one decision is recorded. */
    @Test
    void approveAndRejectRacingProduceASingleDecision() throws Exception {
        TestOrg org = createOrg();
        for (int round = 0; round < 10; round++) {
            String id = draftClaim(org.employee(), "FOOD", "300.00", false);
            act(id, "submit", org.employee(), null);

            List<ResponseEntity<JsonNode>> results = race(
                    () -> act(id, "approve", org.manager(), "yes"),
                    () -> act(id, "reject", org.manager(), "no"));

            assertThat(results.stream().filter(r -> r.getStatusCode() == HttpStatus.OK).count()).isEqualTo(1);
            assertThat(results.stream().filter(r -> r.getStatusCode() == HttpStatus.CONFLICT).count()).isEqualTo(1);
            JsonNode claim = call(HttpMethod.GET, "/api/v1/claims/" + id, org.manager().token(), null).getBody();
            assertThat(claim.get("history")).hasSize(2);   // SUBMIT + exactly one decision
        }
    }

    /** Releases both callers from a barrier together to maximise overlap. */
    private List<ResponseEntity<JsonNode>> race(Callable<ResponseEntity<JsonNode>> a, Callable<ResponseEntity<JsonNode>> b)
            throws Exception {
        CyclicBarrier barrier = new CyclicBarrier(2);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<ResponseEntity<JsonNode>>> futures = new ArrayList<>();
            for (Callable<ResponseEntity<JsonNode>> task : List.of(a, b)) {
                futures.add(pool.submit(() -> {
                    barrier.await();
                    return task.call();
                }));
            }
            List<ResponseEntity<JsonNode>> out = new ArrayList<>();
            for (Future<ResponseEntity<JsonNode>> f : futures) {
                out.add(f.get());
            }
            return out;
        } finally {
            pool.shutdownNow();
        }
    }
}

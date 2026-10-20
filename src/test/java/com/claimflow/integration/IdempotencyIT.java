package com.claimflow.integration;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

class IdempotencyIT extends AbstractIntegrationTest {

    private static final String KEY = "Idempotency-Key";

    @Test
    void repeatedSubmitReturnsTheOriginalResultAndDoesNothingTwice() {
        TestOrg org = createOrg();
        String id = draftClaim(org.employee(), "FOOD", "300.00", false);

        ResponseEntity<JsonNode> first = act(id, "submit", org.employee(), null, KEY, "submit-1");
        ResponseEntity<JsonNode> second = act(id, "submit", org.employee(), null, KEY, "submit-1");

        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(first.getHeaders().containsKey("Idempotent-Replay")).isFalse();
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(second.getHeaders().getFirst("Idempotent-Replay")).isEqualTo("true");
        assertThat(second.getBody()).isEqualTo(first.getBody());

        JsonNode claim = call(HttpMethod.GET, "/api/v1/claims/" + id, org.employee().token(), null).getBody();
        assertThat(claim.get("history")).as("the work happened once").hasSize(1);

        // without a key, the same repeat is rejected by the state machine instead
        assertThat(act(id, "submit", org.employee(), null).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void tenParallelSubmitsWithTheSameKeyAllSeeOneResult() throws Exception {
        TestOrg org = createOrg();
        String id = draftClaim(org.employee(), "FOOD", "300.00", false);

        int n = 10;
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(n);
        List<Future<ResponseEntity<JsonNode>>> futures = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            futures.add(pool.submit(() -> {
                start.await();
                return act(id, "submit", org.employee(), null, KEY, "parallel-key");
            }));
        }
        start.countDown();
        List<ResponseEntity<JsonNode>> results = new ArrayList<>();
        for (Future<ResponseEntity<JsonNode>> f : futures) {
            results.add(f.get());
        }
        pool.shutdownNow();

        assertThat(results).allSatisfy(r -> assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK));
        // compared as JSON trees: Postgres jsonb may reorder object keys, which does not change the content
        assertThat(results.stream().map(ResponseEntity::getBody).distinct()).hasSize(1);
        assertThat(results.stream().filter(r -> r.getHeaders().containsKey("Idempotent-Replay")).count()).isEqualTo(n - 1);
        JsonNode claim = call(HttpMethod.GET, "/api/v1/claims/" + id, org.employee().token(), null).getBody();
        assertThat(claim.get("history")).hasSize(1);
    }

    @Test
    void paymentIsIdempotentAndKeyReuseForADifferentRequestIsRefused() {
        TestOrg org = createOrg();
        String id = draftClaim(org.employee(), "FOOD", "300.00", false);
        act(id, "submit", org.employee(), null);
        act(id, "approve", org.manager(), null);

        ResponseEntity<JsonNode> first = act(id, "pay", org.finance(), "ref 1", KEY, "pay-1");
        ResponseEntity<JsonNode> replay = act(id, "pay", org.finance(), "ref 1", KEY, "pay-1");
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(replay.getBody()).isEqualTo(first.getBody());
        assertThat(replay.getBody().get("history")).as("paid once").hasSize(3);   // SUBMIT, APPROVE, PAY

        ResponseEntity<JsonNode> misuse = act(id, "pay", org.finance(), "a different comment", KEY, "pay-1");
        assertThat(misuse.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(misuse.getBody().get("code").asText()).isEqualTo("IDEMPOTENCY_KEY_REUSED");
    }

    @Test
    void failedRequestsDoNotConsumeTheKey() {
        TestOrg org = createOrg();
        String id = draftClaim(org.employee(), "FOOD", "20000.00", false);   // violates the policy
        assertThat(act(id, "submit", org.employee(), null, KEY, "retry-me").getStatusCode())
                .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);

        // fix the claim, then retry with the very same key: it runs for real
        JsonNode claim = call(HttpMethod.GET, "/api/v1/claims/" + id, org.employee().token(), null).getBody();
        String itemId = claim.get("items").get(0).get("id").asText();
        call(HttpMethod.PUT, "/api/v1/claims/" + id + "/items/" + itemId, org.employee().token(),
                java.util.Map.of("category", "FOOD", "amount", "300.00", "expenseDate", java.time.LocalDate.now().toString()));
        assertThat(act(id, "submit", org.employee(), null, KEY, "retry-me").getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void keysAreScopedPerUserAndPerClaim() {
        TestOrg org = createOrg();
        String a = draftClaim(org.employee(), "FOOD", "300.00", false);
        String b = draftClaim(org.employee(), "FOOD", "310.00", false);
        assertThat(act(a, "submit", org.employee(), null, KEY, "shared").getStatusCode()).isEqualTo(HttpStatus.OK);
        ResponseEntity<JsonNode> other = act(b, "submit", org.employee(), null, KEY, "shared");
        assertThat(other.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(other.getBody().get("claim").get("id").asText()).isEqualTo(b);
        assertThat(other.getHeaders().containsKey("Idempotent-Replay")).isFalse();
    }
}

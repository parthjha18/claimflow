package com.claimflow.integration;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PolicyCacheIT extends AbstractIntegrationTest {

    @Autowired
    StringRedisTemplate redis;

    private static Map<String, Object> policy(String perClaimMax) {
        return Map.of("perClaimMax", perClaimMax, "receiptRequiredAbove", "250.00", "financeApprovalAbove", "5000.00",
                "escalationAfterDays", 2, "categoryLimits", Map.of("TRAVEL", "2000.00", "FOOD", "800.00"));
    }

    @Test
    void policyIsCachedInRedisAndEvictedOnUpdate() {
        // tests in this class run sequentially, so after clearing there is exactly one entry: this org's
        redis.delete(redis.keys("claimflow:policies::*"));
        TestOrg org = createOrg();

        call(HttpMethod.GET, "/api/v1/policy", org.employee().token(), null);   // first read fills the cache
        var keys = redis.keys("claimflow:policies::*");
        assertThat(keys).hasSize(1);
        String key = keys.iterator().next();
        assertThat(redis.opsForValue().get(key)).contains("perClaimMax");

        ResponseEntity<JsonNode> updated = call(HttpMethod.PUT, "/api/v1/policy", org.admin().token(), policy("7777.00"));
        assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(redis.hasKey(key)).as("entry evicted after the update commits").isFalse();

        JsonNode fresh = call(HttpMethod.GET, "/api/v1/policy", org.employee().token(), null).getBody();
        assertThat(fresh.get("perClaimMax").decimalValue()).isEqualByComparingTo("7777.00");
        assertThat(fresh.get("categoryLimits").has("LODGING")).isFalse();
        assertThat(redis.hasKey(key)).as("re-populated by the next read").isTrue();
    }

    @Test
    void onlyAdminsMayChangeThePolicyAndInputIsValidated() {
        TestOrg org = createOrg();
        assertThat(call(HttpMethod.PUT, "/api/v1/policy", org.manager().token(), policy("1000.00")).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        ResponseEntity<JsonNode> bad = call(HttpMethod.PUT, "/api/v1/policy", org.admin().token(), policy("-5"));
        assertThat(bad.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(bad.getBody().get("details").toString()).contains("perClaimMax");
    }

    @Test
    void policiesAreIsolatedPerOrganization() {
        TestOrg a = createOrg();
        TestOrg b = createOrg();
        call(HttpMethod.PUT, "/api/v1/policy", a.admin().token(), policy("1234.00"));
        JsonNode other = call(HttpMethod.GET, "/api/v1/policy", b.employee().token(), null).getBody();
        assertThat(other.get("perClaimMax").decimalValue()).isEqualByComparingTo("100000.00");
    }
}

package com.claimflow.integration;

import com.claimflow.scheduler.EscalationJob;
import com.claimflow.scheduler.MaintenanceJobs;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class SchedulerIT extends AbstractIntegrationTest {

    @Autowired
    EscalationJob escalation;
    @Autowired
    MaintenanceJobs maintenance;
    @Autowired
    JdbcTemplate jdbc;

    private void age(String claimId, int days) {
        jdbc.update("update claims set status_changed_at = now() - make_interval(days => ?) where id = ?::uuid", days, claimId);
    }

    private JsonNode claim(String id, Actor viewer) {
        return call(HttpMethod.GET, "/api/v1/claims/" + id, viewer.token(), null).getBody();
    }

    @Test
    void overdueClaimMovesToTheNextManagerAndIsAudited() {
        TestOrg org = createOrg();   // default policy: escalate after 3 days
        String id = draftClaim(org.employee(), "FOOD", "300.00", false);
        act(id, "submit", org.employee(), null);
        age(id, 5);

        escalation.runOnce();

        JsonNode c = claim(id, org.employee());
        assertThat(c.get("claim").get("status").asText()).isEqualTo("SUBMITTED");
        assertThat(c.get("claim").get("currentApproverId").asText()).isEqualTo(org.boss().id());
        JsonNode last = c.get("history").get(c.get("history").size() - 1);
        assertThat(last.get("action").asText()).isEqualTo("ESCALATE");
        assertThat(last.get("comment").asText()).contains("Auto-escalated", "3 day");

        // audit entry written by the system (no actor)
        Map<String, Object> audit = jdbc.queryForMap(
                "select actor_id, new_value->>'approver' as approver from audit_logs where entity_id = ?::uuid and action = 'CLAIM_ESCALATED'", id);
        assertThat(audit.get("actor_id")).isNull();
        assertThat(audit.get("approver")).isEqualTo(org.boss().id());

        // the original manager has lost the right to act, the new approver has it
        ResponseEntity<JsonNode> old = act(id, "approve", org.manager(), null);
        assertThat(old.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(old.getBody().get("code").asText()).isEqualTo("NOT_CURRENT_APPROVER");
        assertThat(act(id, "approve", org.boss(), null).getStatusCode()).isEqualTo(HttpStatus.OK);

        // and the new approver is told about it, asynchronously
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(TestMailConfig.SENT).anyMatch(m -> m.to().equals(org.boss().email())));
    }

    @Test
    void youngClaimsApprovedClaimsAndTopOfChainClaimsAreLeftAlone() {
        TestOrg org = createOrg();
        String young = draftClaim(org.employee(), "FOOD", "300.00", false);
        act(young, "submit", org.employee(), null);
        age(young, 2);

        String approved = draftClaim(org.employee(), "FOOD", "310.00", false);
        act(approved, "submit", org.employee(), null);
        act(approved, "approve", org.manager(), null);
        age(approved, 10);

        // manager's own claim is already with the boss, who has nobody above them
        String top = draftClaim(org.manager(), "FOOD", "320.00", false);
        act(top, "submit", org.manager(), null);
        age(top, 10);

        escalation.runOnce();

        assertThat(claim(young, org.employee()).get("claim").get("currentApproverId").asText()).isEqualTo(org.manager().id());
        assertThat(claim(approved, org.employee()).get("claim").get("status").asText()).isEqualTo("MANAGER_APPROVED");
        assertThat(claim(top, org.manager()).get("claim").get("currentApproverId").asText()).isEqualTo(org.boss().id());
        assertThat(claim(top, org.manager()).get("history")).hasSize(1);   // no escalation step
    }

    @Test
    void secondRunDoesNotEscalateAgainAndThresholdComesFromThePolicy() {
        TestOrg org = createOrg();
        call(HttpMethod.PUT, "/api/v1/policy", org.admin().token(), Map.of("perClaimMax", "100000.00",
                "receiptRequiredAbove", "500.00", "financeApprovalAbove", "10000.00", "escalationAfterDays", 1,
                "categoryLimits", Map.of("FOOD", "10000.00")));
        String id = draftClaim(org.employee(), "FOOD", "300.00", false);
        act(id, "submit", org.employee(), null);
        age(id, 2);   // overdue under the 1-day policy, would not be under the default 3

        escalation.runOnce();
        escalation.runOnce();

        JsonNode c = claim(id, org.employee());
        assertThat(c.get("claim").get("currentApproverId").asText()).isEqualTo(org.boss().id());
        assertThat(c.get("history")).hasSize(2);   // SUBMIT + a single ESCALATE
    }

    @Test
    void expiredIdempotencyKeysArePurged() {
        TestOrg org = createOrg();
        String id = draftClaim(org.employee(), "FOOD", "300.00", false);
        act(id, "submit", org.employee(), null, "Idempotency-Key", "old-" + UUID.randomUUID());
        jdbc.update("update idempotency_keys set created_at = now() - interval '2 days'");
        String fresh = draftClaim(org.employee(), "FOOD", "301.00", false);
        act(fresh, "submit", org.employee(), null, "Idempotency-Key", "fresh-" + UUID.randomUUID());

        maintenance.purgeIdempotencyKeys();

        assertThat(jdbc.queryForObject("select count(*) from idempotency_keys where created_at < now() - interval '1 day'",
                Integer.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from idempotency_keys where idem_key like 'fresh-%'", Integer.class))
                .isGreaterThanOrEqualTo(1);
        assertThat(id).isNotBlank();
    }
}

package com.claimflow.integration;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class WorkflowIT extends AbstractIntegrationTest {

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void largeClaimGoesThroughManagerFinanceAndPayment() {
        TestOrg org = createOrg();
        // default policy: finance approval above 10,000; receipts above 500
        String id = draftClaim(org.employee(), "TRAVEL", "12000.00", true);

        ResponseEntity<JsonNode> submitted = act(id, "submit", org.employee(), null);
        assertThat(submitted.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(submitted.getBody().get("claim").get("status").asText()).isEqualTo("SUBMITTED");
        assertThat(submitted.getBody().get("claim").get("requiresFinance").asBoolean()).isTrue();
        assertThat(submitted.getBody().get("claim").get("currentApproverId").asText()).isEqualTo(org.manager().id());

        assertThat(act(id, "approve", org.manager(), "ok").getBody().get("claim").get("status").asText())
                .isEqualTo("MANAGER_APPROVED");
        // a large claim cannot be paid before finance has approved it
        assertThat(act(id, "pay", org.finance(), null).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(act(id, "approve", org.finance(), null).getBody().get("claim").get("status").asText())
                .isEqualTo("FINANCE_APPROVED");
        JsonNode paid = act(id, "pay", org.finance(), "paid via NEFT").getBody();
        assertThat(paid.get("claim").get("status").asText()).isEqualTo("PAID");

        // history: SUBMIT, APPROVE, APPROVE, PAY, each with its actor
        JsonNode history = paid.get("history");
        assertThat(history).hasSize(4);
        assertThat(history.get(0).get("action").asText()).isEqualTo("SUBMIT");
        assertThat(history.get(1).get("actorId").asText()).isEqualTo(org.manager().id());
        assertThat(history.get(3).get("action").asText()).isEqualTo("PAY");

        // audit log has one row per change, attributed to who did it
        assertThat(jdbc.queryForObject("select count(*) from audit_logs where entity_id = ?::uuid and action like 'CLAIM_%'",
                Integer.class, id)).isGreaterThanOrEqualTo(5);   // CREATED + 4 transitions
        assertThat(jdbc.queryForObject("select actor_id::text from audit_logs where entity_id = ?::uuid and action = 'CLAIM_PAY'",
                String.class, id)).isEqualTo(org.finance().id());
        // JPA auditing recorded who last touched the row
        assertThat(jdbc.queryForObject("select updated_by::text from claims where id = ?::uuid", String.class, id))
                .isEqualTo(org.finance().id());

        // notifications are sent asynchronously after commit
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(TestMailConfig.SENT).anyMatch(m -> m.to().equals(org.employee().email())
                        && m.subject().contains("PAID")));
    }

    @Test
    void smallClaimSkipsFinanceApproval() {
        TestOrg org = createOrg();
        String id = draftClaim(org.employee(), "FOOD", "300.00", false);
        act(id, "submit", org.employee(), null);
        assertThat(act(id, "approve", org.manager(), null).getBody().get("claim").get("requiresFinance").asBoolean()).isFalse();
        assertThat(act(id, "approve", org.finance(), null).getStatusCode())
                .as("no finance approval step for small claims").isEqualTo(HttpStatus.CONFLICT);
        assertThat(act(id, "pay", org.finance(), null).getBody().get("claim").get("status").asText()).isEqualTo("PAID");
    }

    @Test
    void makerCheckerPreventsApprovingOwnClaim() {
        TestOrg org = createOrg();
        // the manager files a claim of their own; it goes to the boss, never back to them
        String own = draftClaim(org.manager(), "FOOD", "200.00", false);
        JsonNode submitted = act(own, "submit", org.manager(), null).getBody();
        assertThat(submitted.get("claim").get("currentApproverId").asText()).isEqualTo(org.boss().id());

        ResponseEntity<JsonNode> self = act(own, "approve", org.manager(), null);
        assertThat(self.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(self.getBody().get("code").asText()).isEqualTo("MAKER_CHECKER_VIOLATION");

        act(own, "approve", org.boss(), null);
        // a finance user cannot approve or pay their own claim either
        Actor finance2 = createUser(org.admin(), "fin2-" + UUID.randomUUID() + "@t.com", "Finance 2", "FINANCE", org.manager().id());
        String financeClaim = draftClaim(finance2, "FOOD", "150.00", false);
        act(financeClaim, "submit", finance2, null);
        act(financeClaim, "approve", org.manager(), null);
        ResponseEntity<JsonNode> selfPay = act(financeClaim, "pay", finance2, null);
        assertThat(selfPay.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(selfPay.getBody().get("code").asText()).isEqualTo("MAKER_CHECKER_VIOLATION");
        assertThat(act(financeClaim, "pay", org.finance(), null).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void onlyTheResponsiblePersonMayActAtEachStage() {
        TestOrg org = createOrg();
        String id = draftClaim(org.employee(), "FOOD", "300.00", false);
        act(id, "submit", org.employee(), null);

        // boss is above the manager and can see the claim, but is not the current approver
        ResponseEntity<JsonNode> boss = act(id, "approve", org.boss(), null);
        assertThat(boss.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(boss.getBody().get("code").asText()).isEqualTo("NOT_CURRENT_APPROVER");
        // finance cannot jump the queue
        assertThat(act(id, "approve", org.finance(), null).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        act(id, "approve", org.manager(), null);
        // after the manager stage, managers have no say
        assertThat(act(id, "pay", org.manager(), null).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        // someone from another organisation cannot even tell the claim exists
        TestOrg other = createOrg();
        assertThat(act(id, "pay", other.finance(), null).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void policyViolationsBlockSubmissionAndAreAllListed() {
        TestOrg org = createOrg();
        // 20,000 FOOD without receipt: breaks the FOOD category limit (10,000) and the receipt rule (500)
        String id = draftClaim(org.employee(), "FOOD", "20000.00", false);
        ResponseEntity<JsonNode> res = act(id, "submit", org.employee(), null);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(res.getBody().get("code").asText()).isEqualTo("POLICY_VIOLATION");
        assertThat(res.getBody().get("details")).hasSize(2);
        assertThat(statusOf(id, org.employee())).isEqualTo("DRAFT");
    }

    @Test
    void emptyClaimAndManagerlessUserCannotSubmit() {
        TestOrg org = createOrg();
        JsonNode empty = call(HttpMethod.POST, "/api/v1/claims", org.employee().token(),
                java.util.Map.of("title", "Nothing")).getBody();
        assertThat(act(empty.get("claim").get("id").asText(), "submit", org.employee(), null).getStatusCode())
                .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);

        // the boss has no manager, so their claim has nobody to approve it
        String bossClaim = draftClaim(org.boss(), "FOOD", "100.00", false);
        ResponseEntity<JsonNode> res = act(bossClaim, "submit", org.boss(), null);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(res.getBody().get("code").asText()).isEqualTo("NO_APPROVER");
    }

    @Test
    void requestChangesReopensTheDraftAndResubmissionWorks() {
        TestOrg org = createOrg();
        String id = draftClaim(org.employee(), "FOOD", "300.00", false);
        act(id, "submit", org.employee(), null);

        assertThat(act(id, "request-changes", org.manager(), null).getStatusCode())
                .as("a reason is mandatory").isEqualTo(HttpStatus.BAD_REQUEST);
        JsonNode back = act(id, "request-changes", org.manager(), "Please split the dinner").getBody();
        assertThat(back.get("claim").get("status").asText()).isEqualTo("DRAFT");
        assertThat(back.get("claim").get("currentApproverId").isNull()).isTrue();

        // editable again, and can be resubmitted
        assertThat(call(HttpMethod.PUT, "/api/v1/claims/" + id, org.employee().token(),
                java.util.Map.of("title", "Fixed")).getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode again = act(id, "submit", org.employee(), null).getBody();
        assertThat(again.get("claim").get("status").asText()).isEqualTo("SUBMITTED");
        assertThat(again.get("history")).hasSize(3);   // SUBMIT, REQUEST_CHANGES, SUBMIT
    }

    @Test
    void rejectedClaimsAreTerminal() {
        TestOrg org = createOrg();
        String id = draftClaim(org.employee(), "FOOD", "300.00", false);
        act(id, "submit", org.employee(), null);
        assertThat(act(id, "reject", org.manager(), "").getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(act(id, "reject", org.manager(), "Not business related").getBody().get("claim").get("status").asText())
                .isEqualTo("REJECTED");
        for (String action : new String[]{"approve", "pay", "submit"}) {
            Actor who = action.equals("submit") ? org.employee() : org.manager();
            assertThat(act(id, action, who, null).getStatusCode()).as(action).isEqualTo(HttpStatus.CONFLICT);
        }
        // and the claimant can no longer edit it
        assertThat(call(HttpMethod.PUT, "/api/v1/claims/" + id, org.employee().token(), java.util.Map.of("title", "x"))
                .getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }
}

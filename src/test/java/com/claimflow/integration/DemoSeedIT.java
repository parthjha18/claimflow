package com.claimflow.integration;

import com.claimflow.demo.DemoDataSeeder;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;
import java.util.TreeMap;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("demo")
class DemoSeedIT extends AbstractIntegrationTest {

    @Autowired
    DemoDataSeeder seeder;
    @Autowired
    JdbcTemplate jdbc;

    private String login(String email) {
        return call(HttpMethod.POST, "/api/v1/auth/login", null,
                Map.of("email", email, "password", DemoDataSeeder.PASSWORD)).getBody().get("accessToken").asText();
    }

    @Test
    void demoOrganisationHasEveryRoleAndClaimsInEveryState() throws Exception {
        String admin = login("admin@acme.demo");
        JsonNode users = call(HttpMethod.GET, "/api/v1/users?size=50", admin, null).getBody();
        assertThat(users.get("totalElements").asInt()).isEqualTo(7);
        Map<String, Integer> roles = new TreeMap<>();
        users.get("content").forEach(u -> roles.merge(u.get("role").asText(), 1, Integer::sum));
        assertThat(roles).containsEntry("ADMIN", 1).containsEntry("MANAGER", 2).containsEntry("EMPLOYEE", 2).containsEntry("FINANCE", 2);

        Map<String, Integer> byStatus = new TreeMap<>();
        for (String who : new String[]{"anita.employee@acme.demo", "vikram.employee@acme.demo"}) {
            call(HttpMethod.GET, "/api/v1/claims/mine?size=100", login(who), null).getBody().get("content")
                    .forEach(c -> byStatus.merge(c.get("status").asText(), 1, Integer::sum));
        }
        assertThat(byStatus.keySet()).containsExactlyInAnyOrder(
                "DRAFT", "SUBMITTED", "MANAGER_APPROVED", "FINANCE_APPROVED", "PAID", "REJECTED");

        // queues are populated for the people who would work on them
        assertThat(call(HttpMethod.GET, "/api/v1/claims/pending-approvals", login("rahul.manager@acme.demo"), null)
                .getBody().get("totalElements").asInt()).isEqualTo(2);
        assertThat(call(HttpMethod.GET, "/api/v1/claims/pending-approvals", login("meera.finance@acme.demo"), null)
                .getBody().get("totalElements").asInt()).isEqualTo(3);

        // the spend report has several months of paid history
        JsonNode report = call(HttpMethod.GET, "/api/v1/reports/spend?groupBy=MONTH", admin, null).getBody();
        assertThat(report.get("rows").get("content").size()).isGreaterThanOrEqualTo(4);

        // seeded receipts are real files that can be downloaded
        JsonNode anita = call(HttpMethod.GET, "/api/v1/claims/mine?size=100&status=PAID", login("anita.employee@acme.demo"), null).getBody();
        String claimId = anita.get("content").get(0).get("id").asText();
        JsonNode detail = call(HttpMethod.GET, "/api/v1/claims/" + claimId, login("anita.employee@acme.demo"), null).getBody();
        assertThat(detail.get("items").get(0).get("hasReceipt").asBoolean()).isTrue();
        assertThat(detail.get("history").size()).isGreaterThanOrEqualTo(3);
    }

    @Test
    void runningTheSeederAgainChangesNothing() throws Exception {
        int before = jdbc.queryForObject("select count(*) from claims", Integer.class);
        seeder.run(null);
        assertThat(jdbc.queryForObject("select count(*) from claims", Integer.class)).isEqualTo(before);
    }
}

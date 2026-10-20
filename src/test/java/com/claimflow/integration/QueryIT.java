package com.claimflow.integration;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class QueryIT extends AbstractIntegrationTest {

    @Autowired
    EntityManagerFactory emf;

    private JsonNode get(String path, Actor who) {
        return call(HttpMethod.GET, path, who.token(), null).getBody();
    }

    @Test
    void myClaimsArePagedSortedFilteredAndBounded() {
        TestOrg org = createOrg();
        for (String amount : List.of("500.00", "100.00", "300.00", "200.00", "400.00")) {
            draftClaim(org.employee(), "FOOD", amount, false);
        }
        String submitted = draftClaim(org.employee(), "FOOD", "50.00", false);
        act(submitted, "submit", org.employee(), null);

        JsonNode page0 = get("/api/v1/claims/mine?size=2&sort=totalAmount,asc&status=DRAFT", org.employee());
        assertThat(page0.get("totalElements").asInt()).isEqualTo(5);
        assertThat(page0.get("totalPages").asInt()).isEqualTo(3);
        assertThat(page0.get("content").get(0).get("totalAmount").decimalValue()).isEqualByComparingTo("100.00");
        assertThat(page0.get("content").get(1).get("totalAmount").decimalValue()).isEqualByComparingTo("200.00");
        JsonNode page2 = get("/api/v1/claims/mine?size=2&page=2&sort=totalAmount,asc&status=DRAFT", org.employee());
        assertThat(page2.get("content")).hasSize(1);
        assertThat(page2.get("content").get(0).get("totalAmount").decimalValue()).isEqualByComparingTo("500.00");

        assertThat(get("/api/v1/claims/mine?status=SUBMITTED", org.employee()).get("totalElements").asInt()).isEqualTo(1);
        assertThat(get("/api/v1/claims/mine", org.manager()).get("totalElements").asInt()).isZero();   // only my own

        assertThat(get("/api/v1/claims/mine?size=100000", org.employee()).get("size").asInt()).isEqualTo(100);
        ResponseEntity<JsonNode> bad = call(HttpMethod.GET, "/api/v1/claims/mine?sort=claimant.password,asc", org.employee().token(), null);
        assertThat(bad.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(bad.getBody().get("code").asText()).isEqualTo("INVALID_SORT");
    }

    @Test
    void approvalQueuesShowOnlyWhatIsWaitingOnMe() {
        TestOrg org = createOrg();
        String waiting = draftClaim(org.employee(), "FOOD", "300.00", false);
        act(waiting, "submit", org.employee(), null);
        String approved = draftClaim(org.employee(), "FOOD", "310.00", false);
        act(approved, "submit", org.employee(), null);
        act(approved, "approve", org.manager(), null);

        JsonNode managerQueue = get("/api/v1/claims/pending-approvals", org.manager());
        assertThat(managerQueue.get("totalElements").asInt()).isEqualTo(1);
        assertThat(managerQueue.get("content").get(0).get("id").asText()).isEqualTo(waiting);
        assertThat(get("/api/v1/claims/pending-approvals", org.boss()).get("totalElements").asInt()).isZero();

        JsonNode financeQueue = get("/api/v1/claims/pending-approvals", org.finance());
        assertThat(financeQueue.get("totalElements").asInt()).isEqualTo(1);
        assertThat(financeQueue.get("content").get(0).get("id").asText()).isEqualTo(approved);

        assertThat(call(HttpMethod.GET, "/api/v1/claims/pending-approvals", org.employee().token(), null).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void listingManyClaimsDoesNotIssueOneQueryPerRow() {
        TestOrg org = createOrg();
        // six claims from six different employees under six different managers: lazy loading would show up
        for (int i = 0; i < 6; i++) {
            String tag = UUID.randomUUID().toString().substring(0, 6);
            Actor mgr = createUser(org.admin(), "m" + tag + "@t.com", "Mgr " + tag, "MANAGER", org.boss().id());
            Actor emp = createUser(org.admin(), "e" + tag + "@t.com", "Emp " + tag, "EMPLOYEE", mgr.id());
            String id = draftClaim(emp, "FOOD", "300.00", false);
            act(id, "submit", emp, null);
            act(id, "approve", mgr, null);
        }
        SessionFactory sf = emf.unwrap(SessionFactory.class);
        long before = sf.getStatistics().getPrepareStatementCount();
        JsonNode queue = get("/api/v1/claims/pending-approvals?size=50", org.finance());
        long statements = sf.getStatistics().getPrepareStatementCount() - before;

        assertThat(queue.get("content")).hasSize(6);
        assertThat(statements).as("statements for a 6-row page (page + count)").isLessThanOrEqualTo(3);
    }

    @Test
    void spendReportGroupsByMonthAndCategoryAndIsAdminOrFinanceOnly() {
        TestOrg org = createOrg();
        for (String[] spec : new String[][]{{"FOOD", "300.00"}, {"FOOD", "200.00"}, {"TRAVEL", "900.00"}}) {
            String id = draftClaim(org.employee(), spec[0], spec[1], spec[1].equals("900.00"));
            act(id, "submit", org.employee(), null);
            act(id, "approve", org.manager(), null);
            act(id, "pay", org.finance(), null);
        }
        String unpaid = draftClaim(org.employee(), "FOOD", "111.00", false);   // draft: must not be counted
        assertThat(unpaid).isNotBlank();

        JsonNode byCategory = get("/api/v1/reports/spend?groupBy=CATEGORY&sort=total&dir=desc", org.finance());
        assertThat(byCategory.get("grandTotal").decimalValue()).isEqualByComparingTo("1400.00");
        JsonNode rows = byCategory.get("rows").get("content");
        assertThat(rows).hasSize(2);
        assertThat(rows.get(0).get("category").asText()).isEqualTo("TRAVEL");
        assertThat(rows.get(0).get("total").decimalValue()).isEqualByComparingTo("900.00");
        assertThat(rows.get(1).get("category").asText()).isEqualTo("FOOD");
        assertThat(rows.get(1).get("itemCount").asInt()).isEqualTo(2);

        JsonNode byMonth = get("/api/v1/reports/spend?groupBy=MONTH", org.admin());
        assertThat(byMonth.get("rows").get("content")).hasSize(1);
        assertThat(byMonth.get("rows").get("content").get(0).get("total").decimalValue()).isEqualByComparingTo("1400.00");

        // including unpaid statuses widens the picture
        JsonNode withDraft = get("/api/v1/reports/spend?groupBy=CATEGORY&status=PAID&status=DRAFT", org.admin());
        assertThat(withDraft.get("grandTotal").decimalValue()).isEqualByComparingTo(new BigDecimal("1511.00"));

        assertThat(call(HttpMethod.GET, "/api/v1/reports/spend", org.employee().token(), null).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(call(HttpMethod.GET, "/api/v1/reports/spend", org.manager().token(), null).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(call(HttpMethod.GET, "/api/v1/reports/spend?groupBy=MONTH&sort=category", org.admin().token(), null)
                .getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        // another organisation sees none of it
        TestOrg other = createOrg();
        assertThat(get("/api/v1/reports/spend", other.admin()).get("grandTotal").decimalValue()).isEqualByComparingTo("0");
    }
}

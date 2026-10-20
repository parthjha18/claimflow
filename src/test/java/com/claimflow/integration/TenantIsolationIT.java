package com.claimflow.integration;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Another organisation must not be able to see or touch a claim, whatever role its users have. Finance and admin
 * can see every non-draft claim inside their own organisation, which is exactly why the organisation filter on
 * the query is the thing standing between them and a rival company's expenses.
 */
class TenantIsolationIT extends AbstractIntegrationTest {

    @Test
    void anotherOrganisationsFinanceAdminAndManagersCannotReadOrActOnASubmittedClaim() {
        TestOrg a = createOrg();
        TestOrg b = createOrg();
        String id = draftClaim(a.employee(), "TRAVEL", "12000.00", true);
        act(id, "submit", a.employee(), null);
        act(id, "approve", a.manager(), null);       // now waiting on finance, visible to finance/admin of org A

        // sanity: the owner's own organisation can see it
        assertThat(call(HttpMethod.GET, "/api/v1/claims/" + id, a.finance().token(), null).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(call(HttpMethod.GET, "/api/v1/claims/" + id, a.admin().token(), null).getStatusCode()).isEqualTo(HttpStatus.OK);

        for (Actor outsider : List.of(b.finance(), b.admin(), b.manager(), b.boss(), b.employee())) {
            assertThat(call(HttpMethod.GET, "/api/v1/claims/" + id, outsider.token(), null).getStatusCode())
                    .as("read by %s", outsider.email()).isEqualTo(HttpStatus.NOT_FOUND);
        }
        for (String action : List.of("approve", "reject", "request-changes", "pay")) {
            ResponseEntity<JsonNode> res = act(id, action, b.finance(), "nope");
            assertThat(res.getStatusCode()).as(action + " by another organisation's finance").isEqualTo(HttpStatus.NOT_FOUND);
        }
        // the claim is unchanged and does not appear in the outsiders' queues
        assertThat(statusOf(id, a.finance())).isEqualTo("MANAGER_APPROVED");
        JsonNode queue = call(HttpMethod.GET, "/api/v1/claims/pending-approvals", b.finance().token(), null).getBody();
        assertThat(queue.get("totalElements").asInt()).isZero();
    }

    @Test
    void receiptsOfAnotherOrganisationCannotBeDownloaded() {
        TestOrg a = createOrg();
        TestOrg b = createOrg();
        String id = draftClaim(a.employee(), "TRAVEL", "2500.00", true);
        act(id, "submit", a.employee(), null);
        String itemId = call(HttpMethod.GET, "/api/v1/claims/" + id, a.employee().token(), null)
                .getBody().get("items").get(0).get("id").asText();
        String url = "/api/v1/claims/" + id + "/items/" + itemId + "/receipt";

        assertThat(download(url, a.finance())).isEqualTo(HttpStatus.OK);
        assertThat(download(url, b.finance())).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(download(url, b.admin())).isEqualTo(HttpStatus.NOT_FOUND);
    }

    /** A receipt is a file, not JSON, so read the response as raw bytes. */
    private HttpStatus download(String url, Actor who) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(who.token());
        return HttpStatus.valueOf(rest.exchange(url, HttpMethod.GET, new HttpEntity<>(headers), byte[].class)
                .getStatusCode().value());
    }
}

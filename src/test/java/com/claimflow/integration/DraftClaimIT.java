package com.claimflow.integration;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DraftClaimIT extends AbstractIntegrationTest {

    static Map<String, Object> item(String category, String amount, String description) {
        return Map.of("category", category, "amount", amount, "expenseDate", LocalDate.now().minusDays(1).toString(),
                "description", description);
    }

    private JsonNode createDraft(Actor who, List<Map<String, Object>> items) {
        ResponseEntity<JsonNode> res = call(HttpMethod.POST, "/api/v1/claims", who.token(),
                Map.of("title", "Client visit", "description", "Pune trip", "items", items));
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return res.getBody();
    }

    @Test
    void createEditItemsAndTotalsStayConsistent() {
        TestOrg org = createOrg();
        JsonNode draft = createDraft(org.employee(), List.of(item("FOOD", "300.00", "Lunch"), item("TRAVEL", "1200.50", "Cab")));
        String id = draft.get("claim").get("id").asText();
        assertThat(draft.get("claim").get("status").asText()).isEqualTo("DRAFT");
        assertThat(draft.get("claim").get("totalAmount").decimalValue()).isEqualByComparingTo("1500.50");
        assertThat(draft.get("items")).hasSize(2);

        JsonNode added = call(HttpMethod.POST, "/api/v1/claims/" + id + "/items", org.employee().token(),
                item("LODGING", "4000.00", "Hotel")).getBody();
        assertThat(added.get("claim").get("totalAmount").decimalValue()).isEqualByComparingTo("5500.50");

        String lunchId = added.get("items").get(0).get("id").asText();
        JsonNode removed = call(HttpMethod.DELETE, "/api/v1/claims/" + id + "/items/" + lunchId, org.employee().token(), null).getBody();
        assertThat(removed.get("items")).hasSize(2);

        String remaining = removed.get("items").get(0).get("id").asText();
        JsonNode updated = call(HttpMethod.PUT, "/api/v1/claims/" + id + "/items/" + remaining, org.employee().token(),
                item("OTHER", "10.00", "Parking")).getBody();
        assertThat(updated.get("claim").get("totalAmount").decimalValue()).isGreaterThan(java.math.BigDecimal.ZERO);
        // total always equals the sum of its items
        java.math.BigDecimal sum = java.math.BigDecimal.ZERO;
        for (JsonNode i : updated.get("items")) {
            sum = sum.add(i.get("amount").decimalValue());
        }
        assertThat(updated.get("claim").get("totalAmount").decimalValue()).isEqualByComparingTo(sum);
    }

    @Test
    void receiptUploadAndDownloadRoundTrip() {
        TestOrg org = createOrg();
        JsonNode draft = createDraft(org.employee(), List.of(item("TRAVEL", "2500.00", "Flight")));
        String id = draft.get("claim").get("id").asText();
        String itemId = draft.get("items").get(0).get("id").asText();
        String url = "/api/v1/claims/" + id + "/items/" + itemId + "/receipt";

        byte[] pdf = "%PDF-1.4 fake receipt".getBytes(StandardCharsets.UTF_8);
        ResponseEntity<JsonNode> up = upload(url, org.employee().token(), "../boarding pass.pdf", "application/pdf", pdf);
        assertThat(up.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(up.getBody().get("items").get(0).get("hasReceipt").asBoolean()).isTrue();
        assertThat(up.getBody().get("items").get(0).get("receiptFilename").asText()).doesNotContain("..", "/");

        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(org.employee().token());
        ResponseEntity<byte[]> dl = rest.exchange(url, HttpMethod.GET, new HttpEntity<>(h), byte[].class);
        assertThat(dl.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(dl.getBody()).isEqualTo(pdf);

        ResponseEntity<JsonNode> bad = upload(url, org.employee().token(), "virus.exe", "application/x-msdownload", pdf);
        assertThat(bad.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(bad.getBody().get("code").asText()).isEqualTo("INVALID_UPLOAD");
    }

    @Test
    void draftsArePrivateAndTenantsAreIsolated() {
        TestOrg a = createOrg();
        TestOrg b = createOrg();
        String id = createDraft(a.employee(), List.of(item("FOOD", "100.00", "Tea"))).get("claim").get("id").asText();

        // the employee's own manager, finance and admin cannot see a draft; another tenant cannot either
        for (Actor other : List.of(a.manager(), a.finance(), a.admin(), b.admin(), b.manager())) {
            assertThat(call(HttpMethod.GET, "/api/v1/claims/" + id, other.token(), null).getStatusCode())
                    .as("draft hidden from " + other.email()).isEqualTo(HttpStatus.NOT_FOUND);
        }
        // and nobody else may edit it
        assertThat(call(HttpMethod.PUT, "/api/v1/claims/" + id, b.employee().token(),
                Map.of("title", "hijack")).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void validationRejectsBadItems() {
        TestOrg org = createOrg();
        String id = createDraft(org.employee(), List.of()).get("claim").get("id").asText();
        ResponseEntity<JsonNode> res = call(HttpMethod.POST, "/api/v1/claims/" + id + "/items", org.employee().token(),
                Map.of("category", "FOOD", "amount", "-5", "expenseDate", LocalDate.now().plusDays(3).toString()));
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(res.getBody().get("details").toString()).contains("amount", "expenseDate");
        assertThat(call(HttpMethod.POST, "/api/v1/claims/" + id + "/items", org.employee().token(),
                Map.of("category", "NOT_A_CATEGORY", "amount", "5", "expenseDate", LocalDate.now().toString()))
                .getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
}

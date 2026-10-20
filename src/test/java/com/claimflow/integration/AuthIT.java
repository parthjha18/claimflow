package com.claimflow.integration;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AuthIT extends AbstractIntegrationTest {

    private Map<String, String> registration(String suffix) {
        return Map.of("organizationName", "Org " + suffix, "adminFullName", "Admin " + suffix,
                "adminEmail", "admin-" + suffix + "@example.com", "adminPassword", "Passw0rd!");
    }

    @Test
    void registerLoginAndRefreshRotatesTokens() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        ResponseEntity<JsonNode> reg = call(HttpMethod.POST, "/api/v1/auth/register", null, registration(suffix));
        assertThat(reg.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(reg.getBody().get("role").asText()).isEqualTo("ADMIN");

        ResponseEntity<JsonNode> login = call(HttpMethod.POST, "/api/v1/auth/login", null,
                Map.of("email", "admin-" + suffix + "@example.com", "password", "Passw0rd!"));
        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);
        String access = login.getBody().get("accessToken").asText();
        String refresh = login.getBody().get("refreshToken").asText();

        // the access token opens protected endpoints (the default policy is created at registration)
        ResponseEntity<JsonNode> policy = call(HttpMethod.GET, "/api/v1/policy", access, null);
        assertThat(policy.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(policy.getBody().get("categoryLimits").has("TRAVEL")).isTrue();

        ResponseEntity<JsonNode> refreshed = call(HttpMethod.POST, "/api/v1/auth/refresh", null,
                Map.of("refreshToken", refresh));
        assertThat(refreshed.getStatusCode()).isEqualTo(HttpStatus.OK);
        String newRefresh = refreshed.getBody().get("refreshToken").asText();
        assertThat(newRefresh).isNotEqualTo(refresh);

        // replaying the old refresh token is refused AND revokes the whole chain (theft detection)
        assertThat(call(HttpMethod.POST, "/api/v1/auth/refresh", null, Map.of("refreshToken", refresh))
                .getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(call(HttpMethod.POST, "/api/v1/auth/refresh", null, Map.of("refreshToken", newRefresh))
                .getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void wrongPasswordIsUnauthorizedWithErrorJson() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        call(HttpMethod.POST, "/api/v1/auth/register", null, registration(suffix));
        ResponseEntity<JsonNode> res = call(HttpMethod.POST, "/api/v1/auth/login", null,
                Map.of("email", "admin-" + suffix + "@example.com", "password", "wrong-password"));
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(res.getBody().get("code").asText()).isEqualTo("INVALID_CREDENTIALS");
        assertThat(res.getBody().has("timestamp")).isTrue();
    }

    @Test
    void protectedEndpointsRequireAToken() {
        ResponseEntity<JsonNode> res = call(HttpMethod.GET, "/api/v1/policy", null, null);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(res.getBody().get("code").asText()).isEqualTo("UNAUTHENTICATED");
        assertThat(call(HttpMethod.GET, "/api/v1/policy", "garbage", null).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void validationErrorsListTheBadFields() {
        ResponseEntity<JsonNode> res = call(HttpMethod.POST, "/api/v1/auth/register", null,
                Map.of("organizationName", "", "adminFullName", "x", "adminEmail", "not-an-email",
                        "adminPassword", "short"));
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(res.getBody().get("code").asText()).isEqualTo("VALIDATION_FAILED");
        assertThat(res.getBody().get("details").size()).isGreaterThanOrEqualTo(3);
    }

    @Test
    void duplicateOrganizationIsConflict() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        call(HttpMethod.POST, "/api/v1/auth/register", null, registration(suffix));
        ResponseEntity<JsonNode> again = call(HttpMethod.POST, "/api/v1/auth/register", null, registration(suffix));
        assertThat(again.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }
}

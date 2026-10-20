package com.claimflow.integration;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class UserManagementIT extends AbstractIntegrationTest {

    @Test
    void adminCreatesUsersAndNonAdminsAreForbidden() {
        TestOrg org = createOrg();
        assertThat(call(HttpMethod.GET, "/api/v1/users", org.admin().token(), null).getBody()
                .get("totalElements").asInt()).isEqualTo(5);

        ResponseEntity<JsonNode> forbidden = call(HttpMethod.GET, "/api/v1/users", org.employee().token(), null);
        assertThat(forbidden.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(forbidden.getBody().get("code").asText()).isEqualTo("ACCESS_DENIED");

        ResponseEntity<JsonNode> me = call(HttpMethod.GET, "/api/v1/users/me", org.employee().token(), null);
        assertThat(me.getBody().get("managerId").asText()).isEqualTo(org.manager().id());
    }

    @Test
    void usersOfOtherOrganizationsAreInvisible() {
        TestOrg a = createOrg();
        TestOrg b = createOrg();
        assertThat(call(HttpMethod.GET, "/api/v1/users/" + b.employee().id(), a.admin().token(), null)
                .getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        // a manager from another tenant cannot be assigned either
        ResponseEntity<JsonNode> res = call(HttpMethod.PUT, "/api/v1/users/" + a.employee().id(), a.admin().token(),
                Map.of("managerId", b.manager().id()));
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(res.getBody().get("code").asText()).isEqualTo("INVALID_MANAGER");
    }

    @Test
    void reportingLoopsAndSelfLockoutAreRejected() {
        TestOrg org = createOrg();
        // boss -> manager would close the loop boss <- manager <- employee? here: manager already reports to boss
        ResponseEntity<JsonNode> loop = call(HttpMethod.PUT, "/api/v1/users/" + org.boss().id(), org.admin().token(),
                Map.of("managerId", org.manager().id()));
        assertThat(loop.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(loop.getBody().get("code").asText()).isEqualTo("MANAGER_CYCLE");

        ResponseEntity<JsonNode> lockout = call(HttpMethod.PUT, "/api/v1/users/" + org.admin().id(),
                org.admin().token(), Map.of("active", false));
        assertThat(lockout.getBody().get("code").asText()).isEqualTo("SELF_LOCKOUT");
    }

    @Test
    void duplicateEmailIsConflict() {
        TestOrg org = createOrg();
        ResponseEntity<JsonNode> dup = call(HttpMethod.POST, "/api/v1/users", org.admin().token(), Map.of(
                "email", org.employee().email(), "password", "Passw0rd!", "fullName", "Dup", "role", "EMPLOYEE"));
        assertThat(dup.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }
}

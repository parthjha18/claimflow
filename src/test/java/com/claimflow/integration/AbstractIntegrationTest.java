package com.claimflow.integration;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Starts one Postgres and one Redis for the whole test JVM (singleton containers) and points the
 * Spring context at them. Reusing the containers keeps the integration suite fast.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class AbstractIntegrationTest {

    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
    static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);

    static final java.nio.file.Path STORAGE_DIR;

    static {
        try {
            STORAGE_DIR = java.nio.file.Files.createTempDirectory("claimflow-test-uploads");
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
        POSTGRES.start();
        REDIS.start();
    }

    @Autowired
    protected TestRestTemplate rest;

    /** Calls the running app over real HTTP. {@code headers} are name,value pairs. */
    protected ResponseEntity<JsonNode> call(HttpMethod method, String path, String token, Object body,
                                            String... headers) {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) {
            h.setBearerAuth(token);
        }
        for (int i = 0; i + 1 < headers.length; i += 2) {
            h.set(headers[i], headers[i + 1]);
        }
        return rest.exchange(path, method, new HttpEntity<>(body, h), JsonNode.class);
    }

    protected ResponseEntity<JsonNode> upload(String path, String token, String filename, String contentType,
                                              byte[] content) {
        HttpHeaders fileHeaders = new HttpHeaders();
        fileHeaders.setContentType(MediaType.parseMediaType(contentType));
        org.springframework.util.LinkedMultiValueMap<String, Object> form = new org.springframework.util.LinkedMultiValueMap<>();
        form.add("file", new HttpEntity<>(new org.springframework.core.io.ByteArrayResource(content) {
            @Override
            public String getFilename() {
                return filename;
            }
        }, fileHeaders));
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.MULTIPART_FORM_DATA);
        h.setBearerAuth(token);
        return rest.exchange(path, HttpMethod.POST, new HttpEntity<>(form, h), JsonNode.class);
    }

    public record Actor(String id, String email, String token) {
    }

    /** One tenant with a user of every role: employee -> manager -> boss, plus finance and admin. */
    public record TestOrg(Actor admin, Actor boss, Actor manager, Actor employee, Actor finance) {
    }

    protected TestOrg createOrg() {
        String suffix = java.util.UUID.randomUUID().toString().substring(0, 8);
        JsonNode reg = call(HttpMethod.POST, "/api/v1/auth/register", null, java.util.Map.of(
                "organizationName", "Org " + suffix, "adminFullName", "Admin",
                "adminEmail", "admin-" + suffix + "@t.com", "adminPassword", "Passw0rd!")).getBody();
        Actor admin = new Actor(reg.get("userId").asText(), "admin-" + suffix + "@t.com", reg.get("accessToken").asText());
        Actor boss = createUser(admin, "boss-" + suffix + "@t.com", "Boss", "MANAGER", null);
        Actor manager = createUser(admin, "mgr-" + suffix + "@t.com", "Manager", "MANAGER", boss.id());
        Actor employee = createUser(admin, "emp-" + suffix + "@t.com", "Employee", "EMPLOYEE", manager.id());
        Actor finance = createUser(admin, "fin-" + suffix + "@t.com", "Finance", "FINANCE", null);
        return new TestOrg(admin, boss, manager, employee, finance);
    }

    protected Actor createUser(Actor admin, String email, String name, String role, String managerId) {
        java.util.Map<String, Object> body = new java.util.HashMap<>(java.util.Map.of(
                "email", email, "password", "Passw0rd!", "fullName", name, "role", role));
        if (managerId != null) {
            body.put("managerId", managerId);
        }
        ResponseEntity<JsonNode> created = call(HttpMethod.POST, "/api/v1/users", admin.token(), body);
        if (created.getStatusCode().isError()) {
            throw new IllegalStateException("createUser failed: " + created.getBody());
        }
        JsonNode login = call(HttpMethod.POST, "/api/v1/auth/login", null,
                java.util.Map.of("email", email, "password", "Passw0rd!")).getBody();
        return new Actor(created.getBody().get("id").asText(), email, login.get("accessToken").asText());
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("claimflow.storage.local-root", () -> STORAGE_DIR.toString());
    }
}

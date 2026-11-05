package com.ratelimiter.config.snapshot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.HexFormat;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ratelimiter.config.apikey.ApiKeyGenerator;
import com.ratelimiter.snapshot.ConfigurationSnapshot;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SnapshotApiIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

    private final ObjectMapper mapper = new ObjectMapper();

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private SnapshotService snapshotService;

    @Autowired
    private EntityManager entityManager;

    @Test
    @Order(1)
    void seededSnapshotContainsEnabledDefaultsAndNoTimestamps() throws Exception {
        ResponseEntity<byte[]> response = getSnapshot(null);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getHeaders().getContentType().includes(MediaType.APPLICATION_JSON));
        assertTrue(response.getHeaders().getFirst("X-Request-Id") != null);
        byte[] body = response.getBody();
        assertTrue(body.length > 0);
        assertEquals('{', body[0]);
        String raw = new String(body, StandardCharsets.UTF_8);
        assertFalse(raw.contains("generatedAt"));
        assertFalse(raw.contains("updatedAt"));
        assertEtagMatchesBody(response.getHeaders().getETag(), body);

        JsonNode snapshot = mapper.readTree(body);
        assertEquals(3, snapshot.get("plans").size());
        assertEquals(0, snapshot.get("apiKeys").size());
        assertPlan(snapshot.get("plans").get(0), "FREE", 20, 100, 60);
        assertPlan(snapshot.get("plans").get(1), "PRO", 100, 600, 60);
        assertPlan(snapshot.get("plans").get(2), "ENTERPRISE", 1000, 6000, 60);
    }

    @Test
    @Order(2)
    void etagIsStableWhenNothingChangesAndMismatchReturnsTheBody() throws Exception {
        ResponseEntity<byte[]> first = getSnapshot(null);
        ResponseEntity<byte[]> second = getSnapshot(null);
        assertEquals(HttpStatus.OK, first.getStatusCode());
        assertEquals(HttpStatus.OK, second.getStatusCode());
        assertEquals(new String(first.getBody(), StandardCharsets.UTF_8), new String(second.getBody(), StandardCharsets.UTF_8));
        assertEquals(first.getHeaders().getETag(), second.getHeaders().getETag());
        assertEtagMatchesBody(first.getHeaders().getETag(), first.getBody());

        ResponseEntity<byte[]> notModified = getSnapshot(first.getHeaders().getETag());
        assertEquals(HttpStatus.NOT_MODIFIED, notModified.getStatusCode());
        assertTrue(notModified.getBody() == null || notModified.getBody().length == 0);
        assertEquals(first.getHeaders().getETag(), notModified.getHeaders().getETag());

        ResponseEntity<byte[]> different = getSnapshot("\"0000000000000000000000000000000000000000000000000000000000000000\"");
        assertEquals(HttpStatus.OK, different.getStatusCode());
        assertEquals(new String(first.getBody(), StandardCharsets.UTF_8), new String(different.getBody(), StandardCharsets.UTF_8));
    }

    @Test
    @Order(3)
    void disabledPolicyIsOmittedAndTheDefaultRemains() throws Exception {
        long planId = createPlan("SNAP_DISABLED");
        ResponseEntity<JsonNode> created = post(
                "/api/v1/admin/plans/" + planId + "/policies",
                """
                {"pathPattern":"/api/snapshot-hidden/**","httpMethod":"GET","capacity":5,"refillTokens":5,"refillPeriodSeconds":60,"enabled":false}
                """);
        assertEquals(HttpStatus.CREATED, created.getStatusCode());

        JsonNode snapshot = mapper.readTree(getSnapshot(null).getBody());
        JsonNode plan = plan(snapshot, "SNAP_DISABLED");
        assertEquals(1, plan.get("policies").size());
        assertEquals("/**", plan.get("policies").get(0).get("pathPattern").asText());
        assertEquals("ANY", plan.get("policies").get(0).get("httpMethod").asText());
        assertFalse(snapshot.toString().contains("/api/snapshot-hidden/**"));
    }

    @Test
    @Order(4)
    void revokedKeyIsOmittedAndSuspendedClientKeyRemains() throws Exception {
        long planId = planId("FREE");
        long revokedClientId = createClient("Revoked Snapshot", planId);
        String revokedHash = createKey(revokedClientId);
        JsonNode beforeRevoke = mapper.readTree(getSnapshot(null).getBody());
        assertTrue(containsHash(beforeRevoke, revokedHash));

        long keyId = onlyKeyId(revokedClientId);
        ResponseEntity<JsonNode> revoked = post(
                "/api/v1/admin/clients/" + revokedClientId + "/api-keys/" + keyId + "/revoke",
                "");
        assertEquals(HttpStatus.OK, revoked.getStatusCode());
        assertFalse(containsHash(mapper.readTree(getSnapshot(null).getBody()), revokedHash));

        long suspendedClientId = createClient("Suspended Snapshot", planId);
        String suspendedHash = createKey(suspendedClientId);
        ResponseEntity<JsonNode> suspended = exchange(
                HttpMethod.PATCH,
                "/api/v1/admin/clients/" + suspendedClientId,
                "{\"status\":\"SUSPENDED\"}");
        assertEquals(HttpStatus.OK, suspended.getStatusCode());

        JsonNode keys = mapper.readTree(getSnapshot(null).getBody()).get("apiKeys");
        JsonNode match = null;
        for (JsonNode key : keys) {
            if (suspendedHash.equals(key.get("keyHash").asText())) {
                match = key;
            }
        }
        assertTrue(match != null);
        assertEquals(suspendedClientId, match.get("clientId").asLong());
        assertEquals(planId, match.get("planId").asLong());
        assertEquals("SUSPENDED", match.get("clientStatus").asText());
    }

    @Test
    @Order(5)
    void capacityChangeChangesTheEtagThenItStaysStable() throws Exception {
        long planId = createPlan("SNAP_CAPACITY");
        String before = getSnapshot(null).getHeaders().getETag();
        JsonNode policy = get("/api/v1/admin/plans/" + planId + "/policies").get(0);
        ResponseEntity<JsonNode> updated = exchange(
                HttpMethod.PUT,
                "/api/v1/admin/plans/" + planId + "/policies/" + policy.get("id").asLong(),
                """
                {"pathPattern":"/**","httpMethod":"ANY","capacity":77,"refillTokens":15,"refillPeriodSeconds":60,"enabled":true,"version":%d}
                """.formatted(policy.get("version").asInt()));
        assertEquals(HttpStatus.OK, updated.getStatusCode());

        ResponseEntity<byte[]> after = getSnapshot(null);
        assertFalse(before.equals(after.getHeaders().getETag()));
        JsonNode plan = plan(mapper.readTree(after.getBody()), "SNAP_CAPACITY");
        assertEquals(77, plan.get("policies").get(0).get("capacity").asInt());
        assertEquals(after.getHeaders().getETag(), getSnapshot(null).getHeaders().getETag());
    }

    @Test
    @Order(6)
    void repeatableReadHidesAKeyCommittedBetweenTheQueries() throws Exception {
        long clientId = createClient("Race Snapshot", planId("PRO"));
        String hash = "ab".repeat(32);
        String[] isolation = new String[1];
        String[] readOnly = new String[1];
        snapshotService.setBetweenQueries(() -> {
            isolation[0] = entityManager.createNativeQuery("select current_setting('transaction_isolation')")
                    .getSingleResult()
                    .toString();
            readOnly[0] = entityManager.createNativeQuery("select current_setting('transaction_read_only')")
                    .getSingleResult()
                    .toString();
            try {
                insertKey(clientId, hash);
            } catch (SQLException exception) {
                throw new IllegalStateException(exception);
            }
        });

        ConfigurationSnapshot during = snapshotService.load();
        assertEquals("repeatable read", isolation[0]);
        assertEquals("on", readOnly[0]);
        assertTrue(during.apiKeys().stream().noneMatch(key -> hash.equals(key.keyHash())));
        assertTrue(containsHash(mapper.readTree(getSnapshot(null).getBody()), hash));
    }

    @Test
    @Order(7)
    void adminPlansRemainAvailable() {
        ResponseEntity<JsonNode> plans = exchange(HttpMethod.GET, "/api/v1/admin/plans", null);
        assertEquals(HttpStatus.OK, plans.getStatusCode());
        assertTrue(plans.getBody().toString().contains("FREE"));
    }

    private static void assertPlan(JsonNode plan, String code, int capacity, int refillTokens, int period) {
        assertEquals(code, plan.get("code").asText());
        assertFalse(plan.has("name"));
        assertEquals(1, plan.get("policies").size());
        JsonNode policy = plan.get("policies").get(0);
        Set<String> names = new java.util.HashSet<>();
        policy.fieldNames().forEachRemaining(names::add);
        assertEquals(
                Set.of("policyId", "pathPattern", "httpMethod", "capacity", "refillTokens", "refillPeriodSeconds"),
                names);
        assertEquals("/**", policy.get("pathPattern").asText());
        assertEquals("ANY", policy.get("httpMethod").asText());
        assertEquals(capacity, policy.get("capacity").asInt());
        assertEquals(refillTokens, policy.get("refillTokens").asInt());
        assertEquals(period, policy.get("refillPeriodSeconds").asInt());
    }

    private static void assertEtagMatchesBody(String etag, byte[] body) throws Exception {
        assertTrue(etag.startsWith("\""));
        assertFalse(etag.startsWith("W/"));
        String hex = etag.substring(1, etag.length() - 1);
        assertEquals(hex, HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(body)));
    }

    private static boolean containsHash(JsonNode snapshot, String hash) {
        for (JsonNode key : snapshot.get("apiKeys")) {
            if (hash.equals(key.get("keyHash").asText())) {
                return true;
            }
        }
        return false;
    }

    private JsonNode plan(JsonNode snapshot, String code) {
        for (JsonNode plan : snapshot.get("plans")) {
            if (code.equals(plan.get("code").asText())) {
                return plan;
            }
        }
        throw new AssertionError("Missing plan " + code);
    }

    private long planId(String code) throws Exception {
        for (JsonNode plan : mapper.readTree(getSnapshot(null).getBody()).get("plans")) {
            if (code.equals(plan.get("code").asText())) {
                return plan.get("planId").asLong();
            }
        }
        throw new AssertionError("Missing plan " + code);
    }

    private long createPlan(String code) {
        ResponseEntity<JsonNode> created = post(
                "/api/v1/admin/plans",
                """
                {"code":"%s","name":"%s","defaultPolicy":{"capacity":15,"refillTokens":15,"refillPeriodSeconds":60}}
                """.formatted(code, code));
        assertEquals(HttpStatus.CREATED, created.getStatusCode());
        return created.getBody().get("id").asLong();
    }

    private long createClient(String name, long planId) {
        ResponseEntity<JsonNode> created = post(
                "/api/v1/admin/clients",
                "{\"name\":\"" + name + "\",\"planId\":" + planId + "}");
        assertEquals(HttpStatus.CREATED, created.getStatusCode());
        return created.getBody().get("id").asLong();
    }

    private String createKey(long clientId) {
        ResponseEntity<JsonNode> created = post(
                "/api/v1/admin/clients/" + clientId + "/api-keys",
                "{\"label\":\"snapshot\"}");
        assertEquals(HttpStatus.CREATED, created.getStatusCode());
        return ApiKeyGenerator.sha256Hex(created.getBody().get("apiKey").asText());
    }

    private long onlyKeyId(long clientId) {
        JsonNode keys = get("/api/v1/admin/clients/" + clientId + "/api-keys");
        assertEquals(1, keys.size());
        return keys.get(0).get("id").asLong();
    }

    private void insertKey(long clientId, String hash) throws SQLException {
        try (Connection connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(),
                POSTGRES.getUsername(),
                POSTGRES.getPassword())) {
            connection.setAutoCommit(true);
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO api_keys (client_id, key_hash, key_prefix) VALUES (?, ?, ?)")) {
                statement.setLong(1, clientId);
                statement.setString(2, hash);
                statement.setString(3, "rlk_race");
                statement.executeUpdate();
            }
        }
    }

    private ResponseEntity<byte[]> getSnapshot(String ifNoneMatch) {
        HttpHeaders headers = new HttpHeaders();
        if (ifNoneMatch != null) {
            headers.set(HttpHeaders.IF_NONE_MATCH, ifNoneMatch);
        }
        return rest.exchange("/internal/v1/snapshot", HttpMethod.GET, new HttpEntity<>(headers), byte[].class);
    }

    private JsonNode get(String path) {
        ResponseEntity<JsonNode> response = exchange(HttpMethod.GET, path, null);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        return response.getBody();
    }

    private ResponseEntity<JsonNode> post(String path, String json) {
        return exchange(HttpMethod.POST, path, json);
    }

    private ResponseEntity<JsonNode> exchange(HttpMethod method, String path, String json) {
        HttpHeaders headers = new HttpHeaders();
        HttpEntity<String> entity;
        if (json == null) {
            entity = new HttpEntity<>(headers);
        } else {
            headers.setContentType(MediaType.APPLICATION_JSON);
            entity = new HttpEntity<>(json, headers);
        }
        return rest.exchange(path, method, entity, JsonNode.class);
    }
}

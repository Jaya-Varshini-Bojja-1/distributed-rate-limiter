package com.ratelimiter.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.ratelimiter.config.apikey.ApiKeyGenerator;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class AdminApiIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void flywayCreatesSchemaSeedAndConstraints() {
        List<String> tables = jdbc.queryForList(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public'",
                String.class);
        assertTrue(tables.containsAll(List.of("plans", "clients", "api_keys", "rate_limit_policies")));

        List<String> keyColumns = jdbc.queryForList(
                "SELECT column_name FROM information_schema.columns WHERE table_name = 'api_keys'",
                String.class);
        assertTrue(keyColumns.contains("key_hash"));
        assertFalse(keyColumns.contains("api_key"));
        assertFalse(keyColumns.contains("last_used_at"));

        Map<String, String> deleteActions = jdbc.query(
                """
                SELECT conname, confdeltype
                FROM pg_constraint
                WHERE contype = 'f'
                """,
                (rs, row) -> Map.entry(rs.getString("conname"), rs.getString("confdeltype")))
                .stream()
                .collect(java.util.stream.Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        assertEquals("r", deleteActions.get("clients_plan_fk"));
        assertEquals("r", deleteActions.get("api_keys_client_fk"));
        assertEquals("c", deleteActions.get("rate_limit_policies_plan_fk"));

        Integer indexes = jdbc.queryForObject(
                """
                SELECT COUNT(*) FROM pg_indexes
                WHERE indexname IN ('clients_plan_id_idx', 'api_keys_client_id_idx')
                """,
                Integer.class);
        assertEquals(2, indexes);

        JsonNode plans = get("/api/v1/admin/plans");
        assertTrue(codes(plans).containsAll(List.of("FREE", "PRO", "ENTERPRISE")));

        long freeId = planByCode("FREE").get("id").asLong();
        JsonNode policies = get("/api/v1/admin/plans/" + freeId + "/policies");
        assertEquals(1, policies.size());
        assertEquals("/**", policies.get(0).get("pathPattern").asText());
        assertEquals("ANY", policies.get(0).get("httpMethod").asText());
        assertEquals(20, policies.get(0).get("capacity").asInt());
        assertEquals(100, policies.get(0).get("refillTokens").asInt());
        assertEquals(60, policies.get(0).get("refillPeriodSeconds").asInt());
        assertTrue(policies.get(0).get("enabled").asBoolean());
        assertTrue(policies.get(0).get("isDefault").asBoolean());
        assertEquals(0, policies.get(0).get("version").asInt());
    }

    @Test
    void duplicateAnyMethodIsRejectedByTheApiAndTheUniqueConstraint() {
        long planId = createPlan("UNIQUE_PLAN", 10, 10, 60);
        String policy = policyJson("/api/orders/**", "ANY", 5, 5, 60, true);
        ResponseEntity<JsonNode> created = post("/api/v1/admin/plans/" + planId + "/policies", policy);
        assertEquals(HttpStatus.CREATED, created.getStatusCode());

        ResponseEntity<JsonNode> duplicate = post("/api/v1/admin/plans/" + planId + "/policies", policy);
        assertConflict(duplicate, "DUPLICATE");

        Long policyPlanId = planId;
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
                """
                INSERT INTO rate_limit_policies
                    (plan_id, path_pattern, http_method, capacity, refill_tokens, refill_period_seconds, enabled)
                VALUES (?, '/api/orders/**', 'ANY', 5, 5, 60, TRUE)
                """,
                policyPlanId));
    }

    @Test
    void defaultPolicyCannotBeDisabledOrDeleted() {
        long planId = createPlan("PROTECTED_PLAN", 15, 15, 60);
        JsonNode policy = get("/api/v1/admin/plans/" + planId + "/policies").get(0);
        long policyId = policy.get("id").asLong();
        int version = policy.get("version").asInt();

        ResponseEntity<JsonNode> disabled = put(
                "/api/v1/admin/plans/" + planId + "/policies/" + policyId,
                updateJson("/**", "ANY", 15, 15, 60, false, version));
        assertConflict(disabled, "DEFAULT_POLICY_PROTECTED");

        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
                "UPDATE rate_limit_policies SET enabled = FALSE WHERE id = ?",
                policyId));

        ResponseEntity<JsonNode> deleted = exchange(
                HttpMethod.DELETE,
                "/api/v1/admin/plans/" + planId + "/policies/" + policyId,
                null);
        assertConflict(deleted, "DEFAULT_POLICY_PROTECTED");

        JsonNode stillThere = get("/api/v1/admin/plans/" + planId + "/policies/" + policyId);
        assertTrue(stillThere.get("enabled").asBoolean());
        assertEquals(15, stillThere.get("capacity").asInt());
    }

    @Test
    void planWithClientsCannotBeDeletedAndEmptyPlanCascadesPolicies() {
        JsonNode free = planByCode("FREE");
        long freeId = free.get("id").asLong();
        ResponseEntity<JsonNode> client = post(
                "/api/v1/admin/clients",
                "{\"name\":\"Acme\",\"planId\":" + freeId + "}");
        assertEquals(HttpStatus.CREATED, client.getStatusCode());

        ResponseEntity<JsonNode> blocked = exchange(HttpMethod.DELETE, "/api/v1/admin/plans/" + freeId, null);
        assertConflict(blocked, "PLAN_IN_USE");
        assertEquals("FREE", get("/api/v1/admin/plans/" + freeId).get("code").asText());

        long emptyId = createPlan("EMPTY_PLAN", 8, 8, 30);
        Integer policiesBefore = jdbc.queryForObject(
                "SELECT COUNT(*) FROM rate_limit_policies WHERE plan_id = ?",
                Integer.class,
                emptyId);
        assertEquals(1, policiesBefore);

        ResponseEntity<JsonNode> deleted = exchange(HttpMethod.DELETE, "/api/v1/admin/plans/" + emptyId, null);
        assertEquals(HttpStatus.NO_CONTENT, deleted.getStatusCode());
        assertEquals(HttpStatus.NOT_FOUND, exchange(HttpMethod.GET, "/api/v1/admin/plans/" + emptyId, null).getStatusCode());
        Integer policiesAfter = jdbc.queryForObject(
                "SELECT COUNT(*) FROM rate_limit_policies WHERE plan_id = ?",
                Integer.class,
                emptyId);
        assertEquals(0, policiesAfter);
    }

    @Test
    void stalePolicyVersionReturnsConflictAndKeepsTheFirstWrite() {
        long planId = createPlan("VERSION_PLAN", 20, 20, 60);
        JsonNode policy = get("/api/v1/admin/plans/" + planId + "/policies").get(0);
        long policyId = policy.get("id").asLong();
        String path = "/api/v1/admin/plans/" + planId + "/policies/" + policyId;

        ResponseEntity<JsonNode> first = put(path, updateJson("/**", "ANY", 40, 20, 60, true, 0));
        assertEquals(HttpStatus.OK, first.getStatusCode());
        assertEquals(1, first.getBody().get("version").asInt());
        assertEquals(40, first.getBody().get("capacity").asInt());

        ResponseEntity<JsonNode> second = put(path, updateJson("/**", "ANY", 80, 20, 60, true, 0));
        assertConflict(second, "POLICY_VERSION_CONFLICT");
        assertEquals(40, get(path).get("capacity").asInt());
        assertEquals(1, get(path).get("version").asInt());
    }

    @Test
    void createdKeyIsReturnedOnceHashedAndRevokeIsIdempotent() {
        long planId = planByCode("PRO").get("id").asLong();
        long clientId = post("/api/v1/admin/clients", "{\"name\":\"Key Client\",\"planId\":" + planId + "}")
                .getBody()
                .get("id")
                .asLong();

        ResponseEntity<JsonNode> created = post(
                "/api/v1/admin/clients/" + clientId + "/api-keys",
                "{\"label\":\"production\"}");
        assertEquals(HttpStatus.CREATED, created.getStatusCode());
        assertTrue(created.getHeaders().getCacheControl().contains("no-store"));
        String rawKey = created.getBody().get("apiKey").asText();
        long keyId = created.getBody().get("id").asLong();
        assertTrue(rawKey.startsWith("rlk_"));
        assertEquals(rawKey.substring(0, 12), created.getBody().get("keyPrefix").asText());
        assertEquals("Store this key securely. It will not be shown again.", created.getBody().get("warning").asText());

        Map<String, Object> stored = jdbc.queryForMap(
                "SELECT key_hash, key_prefix FROM api_keys WHERE id = ?",
                keyId);
        assertEquals(ApiKeyGenerator.sha256Hex(rawKey), stored.get("key_hash").toString().trim());
        assertEquals(rawKey.substring(0, 12), stored.get("key_prefix"));
        assertFalse(stored.get("key_hash").toString().contains(rawKey));

        String listed = get("/api/v1/admin/clients/" + clientId + "/api-keys").toString();
        assertFalse(listed.contains(rawKey));
        assertFalse(listed.contains("apiKey"));
        assertFalse(listed.contains(ApiKeyGenerator.sha256Hex(rawKey)));

        ResponseEntity<JsonNode> firstRevoke = post(
                "/api/v1/admin/clients/" + clientId + "/api-keys/" + keyId + "/revoke",
                "");
        assertEquals(HttpStatus.OK, firstRevoke.getStatusCode());
        String revokedAt = firstRevoke.getBody().get("revokedAt").asText();
        ResponseEntity<JsonNode> secondRevoke = post(
                "/api/v1/admin/clients/" + clientId + "/api-keys/" + keyId + "/revoke",
                "");
        assertEquals(HttpStatus.OK, secondRevoke.getStatusCode());
        assertEquals(revokedAt, secondRevoke.getBody().get("revokedAt").asText());
    }

    @Test
    void suspendedClientCannotReceiveAKeyAndInvalidInputIs400() {
        long planId = planByCode("ENTERPRISE").get("id").asLong();
        JsonNode client = post(
                "/api/v1/admin/clients",
                "{\"name\":\"Suspended Co\",\"planId\":" + planId + "}").getBody();
        long clientId = client.get("id").asLong();
        ResponseEntity<JsonNode> suspended = exchange(
                HttpMethod.PATCH,
                "/api/v1/admin/clients/" + clientId,
                "{\"status\":\"SUSPENDED\"}");
        assertEquals(HttpStatus.OK, suspended.getStatusCode());

        ResponseEntity<JsonNode> rejected = post(
                "/api/v1/admin/clients/" + clientId + "/api-keys",
                "{\"label\":\"nope\"}");
        assertConflict(rejected, "CLIENT_SUSPENDED");

        ResponseEntity<JsonNode> badPlan = post(
                "/api/v1/admin/plans",
                """
                {"code":"free","name":"Lower","defaultPolicy":{"capacity":1,"refillTokens":1,"refillPeriodSeconds":1}}
                """);
        assertEquals(HttpStatus.BAD_REQUEST, badPlan.getStatusCode());
        assertEquals("VALIDATION_FAILED", badPlan.getBody().get("code").asText());
        assertEquals("about:blank", badPlan.getBody().get("type").asText());
        assertTrue(badPlan.getBody().has("requestId"));
        assertTrue(badPlan.getBody().get("errors").isArray());
    }

    private long createPlan(String code, int capacity, int refillTokens, int period) {
        ResponseEntity<JsonNode> created = post(
                "/api/v1/admin/plans",
                """
                {"code":"%s","name":"%s","description":"test","defaultPolicy":{"capacity":%d,"refillTokens":%d,"refillPeriodSeconds":%d}}
                """.formatted(code, code, capacity, refillTokens, period));
        assertEquals(HttpStatus.CREATED, created.getStatusCode());
        assertTrue(created.getHeaders().getLocation().getPath().endsWith("/" + created.getBody().get("id").asLong()));
        JsonNode policies = get("/api/v1/admin/plans/" + created.getBody().get("id").asLong() + "/policies");
        assertEquals(1, policies.size());
        assertTrue(policies.get(0).get("isDefault").asBoolean());
        return created.getBody().get("id").asLong();
    }

    private static java.util.Set<String> codes(JsonNode plans) {
        java.util.Set<String> codes = new java.util.HashSet<>();
        plans.forEach(plan -> codes.add(plan.get("code").asText()));
        return codes;
    }

    private JsonNode planByCode(String code) {
        for (JsonNode plan : get("/api/v1/admin/plans")) {
            if (code.equals(plan.get("code").asText())) {
                return plan;
            }
        }
        throw new AssertionError("Missing plan " + code);
    }

    private static String policyJson(
            String path, String method, int capacity, int refillTokens, int period, boolean enabled) {
        return """
                {"pathPattern":"%s","httpMethod":"%s","capacity":%d,"refillTokens":%d,"refillPeriodSeconds":%d,"enabled":%s}
                """.formatted(path, method, capacity, refillTokens, period, enabled);
    }

    private static String updateJson(
            String path, String method, int capacity, int refillTokens, int period, boolean enabled, int version) {
        return policyJson(path, method, capacity, refillTokens, period, enabled)
                .replace("}", ",\"version\":" + version + "}");
    }

    private JsonNode get(String path) {
        ResponseEntity<JsonNode> response = exchange(HttpMethod.GET, path, null);
        assertEquals(HttpStatus.OK, response.getStatusCode(), response.getBody() == null ? "" : response.getBody().toString());
        return response.getBody();
    }

    private ResponseEntity<JsonNode> post(String path, String json) {
        return exchange(HttpMethod.POST, path, json);
    }

    private ResponseEntity<JsonNode> put(String path, String json) {
        return exchange(HttpMethod.PUT, path, json);
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

    private static void assertConflict(ResponseEntity<JsonNode> response, String code) {
        assertEquals(HttpStatus.CONFLICT, response.getStatusCode(), response.getBody() == null ? "" : response.getBody().toString());
        assertEquals(code, response.getBody().get("code").asText());
    }
}

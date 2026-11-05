package com.ratelimiter.config.snapshot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.nio.charset.StandardCharsets;
import java.util.List;

import com.ratelimiter.snapshot.ApiKeySnapshot;
import com.ratelimiter.snapshot.ConfigurationSnapshot;
import com.ratelimiter.snapshot.PlanSnapshot;
import com.ratelimiter.snapshot.PolicySnapshot;

import org.junit.jupiter.api.Test;

class SnapshotJsonTest {

    private static final String FIXTURE = "{\"plans\":[{\"planId\":1,\"code\":\"FREE\",\"policies\":[{\"policyId\":1,\"pathPattern\":\"/**\",\"httpMethod\":\"ANY\",\"capacity\":20,\"refillTokens\":100,\"refillPeriodSeconds\":60}]}],\"apiKeys\":[{\"keyHash\":\"9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08\",\"clientId\":42,\"planId\":1,\"clientStatus\":\"ACTIVE\"}]}";

    private final SnapshotJson json = new SnapshotJson();

    @Test
    void writesCompactJsonInContractFieldOrder() {
        byte[] first = json.write(fixture());
        byte[] second = json.write(fixture());

        assertEquals(FIXTURE, new String(first, StandardCharsets.UTF_8));
        assertEquals(new String(first, StandardCharsets.UTF_8), new String(second, StandardCharsets.UTF_8));
        assertFalse(new String(first, StandardCharsets.UTF_8).contains("generatedAt"));
    }

    @Test
    void writesEmptyLists() {
        byte[] body = json.write(new ConfigurationSnapshot(List.of(), List.of()));
        assertEquals("{\"plans\":[],\"apiKeys\":[]}", new String(body, StandardCharsets.UTF_8));
    }

    @Test
    void aChangedFieldChangesTheBytes() {
        ConfigurationSnapshot changed = new ConfigurationSnapshot(
                List.of(new PlanSnapshot(1, "FREE", List.of(new PolicySnapshot(1, "/**", "ANY", 21, 100, 60)))),
                List.of());
        assertNotEquals(FIXTURE, new String(json.write(changed), StandardCharsets.UTF_8));
    }

    private static ConfigurationSnapshot fixture() {
        return new ConfigurationSnapshot(
                List.of(new PlanSnapshot(
                        1,
                        "FREE",
                        List.of(new PolicySnapshot(1, "/**", "ANY", 20, 100, 60)))),
                List.of(new ApiKeySnapshot(
                        "9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08",
                        42,
                        1,
                        "ACTIVE")));
    }
}

package com.ratelimiter.config.snapshot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import com.ratelimiter.config.client.ClientStatus;
import com.ratelimiter.snapshot.ConfigurationSnapshot;

import org.junit.jupiter.api.Test;

class SnapshotAssemblerTest {

    @Test
    void groupsOrderedPoliciesAndCopiesKeyStatus() {
        List<EnabledPolicyRow> policies = List.of(
                new EnabledPolicyRow(1, "FREE", 10, "/**", "ANY", 20, 100, 60),
                new EnabledPolicyRow(1, "FREE", 11, "/api/orders/**", "GET", 10, 30, 60),
                new EnabledPolicyRow(2, "PRO", 12, "/**", "ANY", 100, 600, 60));
        List<ActiveKeyRow> keys = List.of(
                new ActiveKeyRow("abc", 42, 1, "ACTIVE"),
                new ActiveKeyRow("def", 43, 2, "SUSPENDED"));

        ConfigurationSnapshot snapshot = SnapshotAssembler.assemble(policies, keys);

        assertEquals(2, snapshot.plans().size());
        assertEquals(1, snapshot.plans().get(0).planId());
        assertEquals("FREE", snapshot.plans().get(0).code());
        assertEquals(2, snapshot.plans().get(0).policies().size());
        assertEquals(10, snapshot.plans().get(0).policies().get(0).policyId());
        assertEquals("/**", snapshot.plans().get(0).policies().get(0).pathPattern());
        assertEquals(11, snapshot.plans().get(0).policies().get(1).policyId());
        assertEquals("GET", snapshot.plans().get(0).policies().get(1).httpMethod());
        assertEquals(2, snapshot.plans().get(1).planId());
        assertEquals(1, snapshot.plans().get(1).policies().size());
        assertEquals(2, snapshot.apiKeys().size());
        assertEquals("abc", snapshot.apiKeys().get(0).keyHash());
        assertEquals("ACTIVE", snapshot.apiKeys().get(0).clientStatus());
        assertEquals(43, snapshot.apiKeys().get(1).clientId());
        assertEquals(2, snapshot.apiKeys().get(1).planId());
        assertEquals("SUSPENDED", snapshot.apiKeys().get(1).clientStatus());
    }

    @Test
    void emptyInputsStayEmptyLists() {
        ConfigurationSnapshot snapshot = SnapshotAssembler.assemble(List.of(), List.of());
        assertTrue(snapshot.plans().isEmpty());
        assertTrue(snapshot.apiKeys().isEmpty());
    }

    @Test
    void trimsCharPaddingFromTheKeyHash() {
        ActiveKeyRow row = new ActiveKeyRow("abc   ", 7L, 1L, ClientStatus.ACTIVE);
        ConfigurationSnapshot snapshot = SnapshotAssembler.assemble(List.of(), List.of(row));
        assertEquals("abc", snapshot.apiKeys().get(0).keyHash());
        assertEquals("ACTIVE", snapshot.apiKeys().get(0).clientStatus());
    }
}

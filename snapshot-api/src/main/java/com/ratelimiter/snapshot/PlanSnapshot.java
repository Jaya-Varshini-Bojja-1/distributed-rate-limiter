package com.ratelimiter.snapshot;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder({"planId", "code", "policies"})
public record PlanSnapshot(long planId, String code, List<PolicySnapshot> policies) {
}

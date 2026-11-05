package com.ratelimiter.snapshot;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder({"plans", "apiKeys"})
public record ConfigurationSnapshot(List<PlanSnapshot> plans, List<ApiKeySnapshot> apiKeys) {
}

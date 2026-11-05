package com.ratelimiter.snapshot;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder({"keyHash", "clientId", "planId", "clientStatus"})
public record ApiKeySnapshot(String keyHash, long clientId, long planId, String clientStatus) {
}

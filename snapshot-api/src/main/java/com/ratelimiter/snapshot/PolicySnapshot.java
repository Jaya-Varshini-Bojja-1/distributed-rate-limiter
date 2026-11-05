package com.ratelimiter.snapshot;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder({
        "policyId",
        "pathPattern",
        "httpMethod",
        "capacity",
        "refillTokens",
        "refillPeriodSeconds"
})
public record PolicySnapshot(
        long policyId,
        String pathPattern,
        String httpMethod,
        int capacity,
        int refillTokens,
        int refillPeriodSeconds) {
}

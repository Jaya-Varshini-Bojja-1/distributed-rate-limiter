package com.ratelimiter.config.policy;

import java.time.Instant;

public record PolicyResponse(
        Long id,
        Long planId,
        String pathPattern,
        HttpMethodMatch httpMethod,
        int capacity,
        int refillTokens,
        int refillPeriodSeconds,
        boolean enabled,
        boolean isDefault,
        Integer version,
        Instant createdAt,
        Instant updatedAt) {
}

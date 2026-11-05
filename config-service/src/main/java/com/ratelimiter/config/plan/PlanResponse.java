package com.ratelimiter.config.plan;

import java.time.Instant;

public record PlanResponse(
        Long id,
        String code,
        String name,
        String description,
        Instant createdAt,
        Instant updatedAt) {
}

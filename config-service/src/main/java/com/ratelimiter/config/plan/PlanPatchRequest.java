package com.ratelimiter.config.plan;

import jakarta.validation.constraints.Size;

public record PlanPatchRequest(
        @Size(max = 100)
        String name,
        String description) {
}

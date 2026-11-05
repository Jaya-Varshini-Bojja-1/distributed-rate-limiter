package com.ratelimiter.config.plan;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record DefaultPolicyRequest(
        @NotNull
        @Min(value = 1, message = "must be between 1 and 1000000")
        @Max(value = 1000000, message = "must be between 1 and 1000000")
        Integer capacity,
        @NotNull
        @Min(value = 1, message = "must be between 1 and 1000000")
        @Max(value = 1000000, message = "must be between 1 and 1000000")
        Integer refillTokens,
        @NotNull
        @Min(value = 1, message = "must be between 1 and 86400")
        @Max(value = 86400, message = "must be between 1 and 86400")
        Integer refillPeriodSeconds) {
}

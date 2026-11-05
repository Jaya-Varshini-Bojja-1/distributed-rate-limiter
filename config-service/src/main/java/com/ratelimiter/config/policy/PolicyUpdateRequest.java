package com.ratelimiter.config.policy;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record PolicyUpdateRequest(
        @NotBlank
        @Size(max = 255)
        @Pattern(regexp = "^/.*", message = "must start with /")
        String pathPattern,
        @NotNull
        HttpMethodMatch httpMethod,
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
        Integer refillPeriodSeconds,
        @NotNull
        Boolean enabled,
        @NotNull
        @Min(0)
        Integer version) {
}

package com.ratelimiter.config.plan;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record PlanCreateRequest(
        @NotBlank
        @Size(max = 32)
        @Pattern(regexp = "^[A-Z][A-Z0-9_]*$", message = "must match ^[A-Z][A-Z0-9_]*$")
        String code,
        @NotBlank
        @Size(max = 100)
        String name,
        String description,
        @NotNull
        @Valid
        DefaultPolicyRequest defaultPolicy) {
}

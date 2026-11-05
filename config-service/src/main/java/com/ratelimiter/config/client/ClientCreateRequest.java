package com.ratelimiter.config.client;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ClientCreateRequest(
        @NotBlank
        @Size(max = 100)
        String name,
        @NotNull
        Long planId) {
}

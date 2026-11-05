package com.ratelimiter.config.client;

import jakarta.validation.constraints.Size;

public record ClientPatchRequest(
        @Size(max = 100)
        String name,
        Long planId,
        ClientStatus status) {
}

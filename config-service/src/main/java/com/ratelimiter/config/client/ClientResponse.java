package com.ratelimiter.config.client;

import java.time.Instant;

public record ClientResponse(
        Long id,
        String name,
        Long planId,
        ClientStatus status,
        Instant createdAt,
        Instant updatedAt) {
}

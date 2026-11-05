package com.ratelimiter.config.apikey;

import java.time.Instant;

public record ApiKeyResponse(
        Long id,
        String label,
        String keyPrefix,
        Instant createdAt,
        Instant revokedAt) {
}

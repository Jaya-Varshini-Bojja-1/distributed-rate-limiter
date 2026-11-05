package com.ratelimiter.config.apikey;

import java.time.Instant;

public record ApiKeyCreatedResponse(
        Long id,
        String label,
        String keyPrefix,
        String apiKey,
        Instant createdAt,
        String warning) {
}

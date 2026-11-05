package com.ratelimiter.config.apikey;

import jakarta.validation.constraints.Size;

public record ApiKeyCreateRequest(
        @Size(max = 100)
        String label) {
}

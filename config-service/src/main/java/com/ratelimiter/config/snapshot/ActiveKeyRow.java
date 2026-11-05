package com.ratelimiter.config.snapshot;

import com.ratelimiter.config.client.ClientStatus;

public record ActiveKeyRow(String keyHash, long clientId, long planId, String clientStatus) {

    public ActiveKeyRow {
        keyHash = keyHash.trim();
    }

    public ActiveKeyRow(String keyHash, Long clientId, Long planId, ClientStatus clientStatus) {
        this(keyHash, clientId, planId, clientStatus.name());
    }
}

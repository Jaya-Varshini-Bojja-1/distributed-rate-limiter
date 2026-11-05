package com.ratelimiter.core.redis;

import java.time.Duration;

/**
 * Where the shared Lettuce client connects. Command and connect timeouts stay
 * at the frozen 100 ms and 500 ms values.
 */
public record RedisClientSettings(String host, int port, String password) {

    public static final Duration COMMAND_TIMEOUT = Duration.ofMillis(100);
    public static final Duration CONNECT_TIMEOUT = Duration.ofMillis(500);

    public RedisClientSettings {
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("host must not be blank");
        }
        if (port < 1 || port > 65_535) {
            throw new IllegalArgumentException("port must be between 1 and 65535");
        }
        if (password != null && password.isEmpty()) {
            password = null;
        }
    }

    public static RedisClientSettings of(String host, int port) {
        return new RedisClientSettings(host, port, null);
    }

    public static RedisClientSettings of(String host, int port, String password) {
        return new RedisClientSettings(host, port, password);
    }
}

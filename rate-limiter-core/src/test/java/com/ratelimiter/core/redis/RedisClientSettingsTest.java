package com.ratelimiter.core.redis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;

import org.junit.jupiter.api.Test;

class RedisClientSettingsTest {

    @Test
    void usesTheFrozenTimeoutsAndOptionalPassword() {
        RedisClientSettings plain = RedisClientSettings.of("localhost", 6379);
        assertNull(plain.password());
        assertEquals(Duration.ofMillis(100), RedisClientSettings.COMMAND_TIMEOUT);
        assertEquals(Duration.ofMillis(500), RedisClientSettings.CONNECT_TIMEOUT);

        RedisClientSettings secret = RedisClientSettings.of("redis", 6379, "s3cret");
        assertEquals("s3cret", secret.password());
        assertNull(new RedisClientSettings("localhost", 6379, "").password());
    }

    @Test
    void rejectsABlankHostOrAnImpossiblePort() {
        assertThrows(IllegalArgumentException.class, () -> RedisClientSettings.of("  ", 6379));
        assertThrows(IllegalArgumentException.class, () -> RedisClientSettings.of("localhost", 0));
        assertThrows(IllegalArgumentException.class, () -> RedisClientSettings.of("localhost", 70_000));
    }
}

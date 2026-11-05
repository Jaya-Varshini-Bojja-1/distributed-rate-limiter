package com.ratelimiter.core.redis;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.lettuce.core.RedisCommandExecutionException;
import io.lettuce.core.RedisCommandInterruptedException;
import io.lettuce.core.RedisCommandTimeoutException;

import org.junit.jupiter.api.Test;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.RedisSystemException;

class RedisFailuresTest {

    @Test
    void connectionTimeoutAndAuthFailuresAreUnavailable() {
        assertTrue(RedisFailures.unavailable(new RedisConnectionFailureException("connection refused")));
        assertTrue(RedisFailures.unavailable(new QueryTimeoutException("command timed out")));
        assertTrue(RedisFailures.unavailable(new RedisCommandTimeoutException("timed out")));
        assertTrue(RedisFailures.unavailable(new RedisSystemException(
                "auth",
                new RedisCommandExecutionException("WRONGPASS invalid username-password pair"))));
    }

    @Test
    void callerBugsAreNotTreatedAsRedisFailures() {
        assertFalse(RedisFailures.unavailable(new IllegalArgumentException("capacity must be between 1 and 1000000")));
        assertFalse(RedisFailures.unavailable(new IllegalStateException("Unexpected token bucket reply")));
        assertFalse(RedisFailures.unavailable(new RuntimeException("bug")));
    }

    @Test
    void anInterruptedRedisCallRestoresTheInterruptFlag() {
        Thread.interrupted();
        try {
            assertTrue(RedisFailures.unavailable(new RedisCommandInterruptedException(new InterruptedException("stop"))));
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted();
        }
    }
}

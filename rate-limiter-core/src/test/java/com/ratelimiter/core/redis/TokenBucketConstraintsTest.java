package com.ratelimiter.core.redis;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ratelimiter.core.RateLimiter;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.springframework.data.redis.core.StringRedisTemplate;

class TokenBucketConstraintsTest {

    @Test
    void acceptsTheSameBoundsAsTheConfigurationService() {
        assertDoesNotThrow(() -> TokenBucketConstraints.check(1, 1, 1, 1, 1));
        assertDoesNotThrow(() -> TokenBucketConstraints.check(
                1,
                1,
                TokenBucketConstraints.MAX_TOKENS,
                TokenBucketConstraints.MAX_TOKENS,
                TokenBucketConstraints.MAX_PERIOD_SECONDS));
    }

    @Test
    void rejectsIdsAndPolicyNumbersOutsideTheBounds() {
        assertRejected(() -> TokenBucketConstraints.check(0, 1, 1, 1, 1), "clientId");
        assertRejected(() -> TokenBucketConstraints.check(1, 0, 1, 1, 1), "policyId");
        assertRejected(() -> TokenBucketConstraints.check(1, 1, 0, 1, 1), "capacity");
        assertRejected(() -> TokenBucketConstraints.check(1, 1, TokenBucketConstraints.MAX_TOKENS + 1, 1, 1), "capacity");
        assertRejected(() -> TokenBucketConstraints.check(1, 1, 1, 0, 1), "refillTokens");
        assertRejected(() -> TokenBucketConstraints.check(1, 1, 1, 1, 0), "refillPeriodSeconds");
        assertRejected(
                () -> TokenBucketConstraints.check(1, 1, 1, 1, TokenBucketConstraints.MAX_PERIOD_SECONDS + 1),
                "refillPeriodSeconds");
    }

    @Test
    void invalidArgumentsThrowBeforeRedisIsCalled() {
        RateLimiter limiter = new RedisTokenBucketRateLimiter(new StringRedisTemplate());
        assertRejected(() -> limiter.tryConsume(0, 1, 1, 1, 1), "clientId");
        assertRejected(() -> limiter.tryConsume(1, 1, 0, 1, 1), "capacity");
    }

    private static void assertRejected(Executable call, String field) {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, call);
        assertTrue(exception.getMessage().contains(field), exception.getMessage());
    }
}

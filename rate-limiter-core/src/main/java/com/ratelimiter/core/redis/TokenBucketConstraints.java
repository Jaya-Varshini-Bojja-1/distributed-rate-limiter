package com.ratelimiter.core.redis;

/**
 * Caller checks that run before Redis. The bounds match the Configuration Service
 * policy validators. A violation is a programming error, not {@code UNAVAILABLE}.
 */
final class TokenBucketConstraints {

    static final int MAX_TOKENS = 1_000_000;
    static final int MAX_PERIOD_SECONDS = 86_400;

    private TokenBucketConstraints() {
    }

    static void check(
            long clientId,
            long policyId,
            int capacity,
            int refillTokens,
            int refillPeriodSeconds) {
        positiveId("clientId", clientId);
        positiveId("policyId", policyId);
        inRange("capacity", capacity, MAX_TOKENS);
        inRange("refillTokens", refillTokens, MAX_TOKENS);
        inRange("refillPeriodSeconds", refillPeriodSeconds, MAX_PERIOD_SECONDS);
    }

    static void positiveId(String name, long value) {
        if (value <= 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }

    private static void inRange(String name, int value, int max) {
        if (value < 1 || value > max) {
            throw new IllegalArgumentException(name + " must be between 1 and " + max);
        }
    }
}

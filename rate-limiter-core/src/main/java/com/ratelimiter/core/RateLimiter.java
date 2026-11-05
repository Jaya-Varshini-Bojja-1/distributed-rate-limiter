package com.ratelimiter.core;

/**
 * Consumes one token from the bucket for a client and policy.
 * Implementations share one Redis key across gateway instances.
 */
public interface RateLimiter {

    ConsumeResult tryConsume(
            long clientId,
            long policyId,
            int capacity,
            int refillTokens,
            int refillPeriodSeconds);
}

package com.ratelimiter.core;

/**
 * Result of one Token Bucket attempt. {@link #UNAVAILABLE} is not a deny:
 * the gateway decides fail-open after authentication, in a later milestone.
 */
public enum Outcome {
    ALLOWED,
    DENIED,
    UNAVAILABLE
}

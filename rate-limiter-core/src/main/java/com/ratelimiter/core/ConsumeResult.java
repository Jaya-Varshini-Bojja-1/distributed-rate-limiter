package com.ratelimiter.core;

/**
 * One {@code tryConsume} outcome. Branch on {@link #outcome()} first.
 * When the outcome is {@link Outcome#UNAVAILABLE}, the numeric fields are zero
 * and must not be read as a deny.
 */
public record ConsumeResult(
        Outcome outcome,
        int limit,
        int remaining,
        long retryAfterMs,
        long resetMs) {

    public static ConsumeResult unavailable() {
        return new ConsumeResult(Outcome.UNAVAILABLE, 0, 0, 0, 0);
    }
}

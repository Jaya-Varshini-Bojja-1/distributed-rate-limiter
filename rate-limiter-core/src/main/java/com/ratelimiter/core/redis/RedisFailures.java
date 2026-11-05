package com.ratelimiter.core.redis;

import io.lettuce.core.RedisException;

import org.springframework.dao.DataAccessException;

/**
 * Maps Redis outages to {@code UNAVAILABLE}. Caller bugs stay exceptions.
 * A timed-out command is not retried: the script may already have consumed a token.
 */
final class RedisFailures {

    private RedisFailures() {
    }

    static boolean unavailable(Throwable error) {
        if (!isRedisFailure(error)) {
            return false;
        }
        if (interrupted(error)) {
            Thread.currentThread().interrupt();
        }
        return true;
    }

    private static boolean isRedisFailure(Throwable error) {
        for (Throwable current = error; current != null; current = current.getCause()) {
            if (current instanceof DataAccessException || current instanceof RedisException) {
                return true;
            }
        }
        return false;
    }

    private static boolean interrupted(Throwable error) {
        for (Throwable current = error; current != null; current = current.getCause()) {
            if (current instanceof InterruptedException
                    || current.getClass().getSimpleName().contains("Interrupt")) {
                return true;
            }
        }
        return false;
    }
}

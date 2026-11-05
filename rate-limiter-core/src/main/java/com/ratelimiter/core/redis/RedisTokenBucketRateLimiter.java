package com.ratelimiter.core.redis;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import com.ratelimiter.core.ConsumeResult;
import com.ratelimiter.core.Outcome;
import com.ratelimiter.core.RateLimiter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

/**
 * Runs {@code redis/token_bucket.lua} through Spring Data Redis.
 * That client sends {@code EVALSHA} and reloads the script on {@code NOSCRIPT}.
 * Refill math is not reimplemented here.
 */
public final class RedisTokenBucketRateLimiter implements RateLimiter, AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(RedisTokenBucketRateLimiter.class);
    private static final RedisScript<List<Object>> SCRIPT = script();

    private final StringRedisTemplate redis;
    private final LettuceConnectionFactory connectionFactory;
    private final AtomicBoolean closed = new AtomicBoolean();

    RedisTokenBucketRateLimiter(StringRedisTemplate redis) {
        this(redis, null);
    }

    RedisTokenBucketRateLimiter(StringRedisTemplate redis, LettuceConnectionFactory connectionFactory) {
        if (redis == null) {
            throw new IllegalArgumentException("redis is required");
        }
        this.redis = redis;
        this.connectionFactory = connectionFactory;
    }

    @Override
    public ConsumeResult tryConsume(
            long clientId,
            long policyId,
            int capacity,
            int refillTokens,
            int refillPeriodSeconds) {
        TokenBucketConstraints.check(clientId, policyId, capacity, refillTokens, refillPeriodSeconds);
        String key = BucketKeys.bucket(clientId, policyId);
        long periodMs = refillPeriodSeconds * 1000L;
        try {
            List<Object> raw = redis.execute(
                    SCRIPT,
                    List.of(key),
                    Integer.toString(capacity),
                    Integer.toString(refillTokens),
                    Long.toString(periodMs),
                    "1");
            return toResult(raw, capacity);
        } catch (RuntimeException exception) {
            if (exception instanceof IllegalArgumentException || exception instanceof IllegalStateException) {
                throw exception;
            }
            if (RedisFailures.unavailable(exception)) {
                log.warn("Token bucket Redis call failed for {}: {}", key, exception.toString());
                return ConsumeResult.unavailable();
            }
            throw exception;
        }
    }

    @Override
    public void close() {
        if (connectionFactory != null && closed.compareAndSet(false, true)) {
            connectionFactory.destroy();
        }
    }

    private static ConsumeResult toResult(List<Object> raw, int capacity) {
        if (raw == null || raw.size() != 4) {
            throw new IllegalStateException("Unexpected token bucket reply: " + raw);
        }
        long allowed = asLong(raw.get(0));
        long remaining = asLong(raw.get(1));
        long retryAfterMs = asLong(raw.get(2));
        long resetMs = asLong(raw.get(3));
        if ((allowed != 0 && allowed != 1)
                || remaining < 0
                || remaining > capacity
                || retryAfterMs < 0
                || resetMs < 0
                || (allowed == 1 && retryAfterMs != 0)) {
            throw new IllegalStateException("Unexpected token bucket reply: " + raw);
        }
        Outcome outcome = allowed == 1 ? Outcome.ALLOWED : Outcome.DENIED;
        return new ConsumeResult(outcome, capacity, (int) remaining, retryAfterMs, resetMs);
    }

    private static long asLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof byte[] bytes) {
            return Long.parseLong(new String(bytes, StandardCharsets.UTF_8).trim());
        }
        if (value instanceof String text) {
            return Long.parseLong(text.trim());
        }
        String type = value == null ? "null" : value.getClass().getName();
        throw new IllegalStateException("Unexpected script value type: " + type);
    }

    @SuppressWarnings("unchecked")
    private static RedisScript<List<Object>> script() {
        DefaultRedisScript<List<Object>> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("redis/token_bucket.lua"));
        script.setResultType((Class<List<Object>>) (Class<?>) List.class);
        return script;
    }
}

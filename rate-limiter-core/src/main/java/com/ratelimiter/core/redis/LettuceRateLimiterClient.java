package com.ratelimiter.core.redis;

import io.lettuce.core.ClientOptions;
import io.lettuce.core.SocketOptions;

import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * One shared Lettuce connection for every caller of the returned limiter.
 * Close it when the process shuts down. Do not open a client per request.
 */
public final class LettuceRateLimiterClient {

    private LettuceRateLimiterClient() {
    }

    public static RedisTokenBucketRateLimiter connect(RedisClientSettings settings) {
        if (settings == null) {
            throw new IllegalArgumentException("settings are required");
        }
        LettuceConnectionFactory factory = factory(settings);
        try {
            factory.afterPropertiesSet();
            StringRedisTemplate template = new StringRedisTemplate(factory);
            template.afterPropertiesSet();
            return new RedisTokenBucketRateLimiter(template, factory);
        } catch (RuntimeException exception) {
            factory.destroy();
            throw exception;
        }
    }

    private static LettuceConnectionFactory factory(RedisClientSettings settings) {
        RedisStandaloneConfiguration standalone = new RedisStandaloneConfiguration(settings.host(), settings.port());
        if (settings.password() != null) {
            standalone.setPassword(settings.password());
        }
        LettuceClientConfiguration client = LettuceClientConfiguration.builder()
                .commandTimeout(RedisClientSettings.COMMAND_TIMEOUT)
                .clientOptions(ClientOptions.builder()
                        .socketOptions(SocketOptions.builder()
                                .connectTimeout(RedisClientSettings.CONNECT_TIMEOUT)
                                .build())
                        .disconnectedBehavior(ClientOptions.DisconnectedBehavior.REJECT_COMMANDS)
                        .build())
                .build();
        LettuceConnectionFactory factory = new LettuceConnectionFactory(standalone, client);
        factory.setShareNativeConnection(true);
        return factory;
    }
}

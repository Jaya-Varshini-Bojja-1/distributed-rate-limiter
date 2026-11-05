package com.ratelimiter.core.redis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.net.ServerSocket;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import com.ratelimiter.core.ConsumeResult;
import com.ratelimiter.core.Outcome;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
class TokenBucketIT {

    private static final int SLOW_REFILL_TOKENS = 1;
    private static final int SLOW_REFILL_PERIOD_SECONDS = 86_400;

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7"))
            .withExposedPorts(6379);

    private static final AtomicLong IDS = new AtomicLong(1_000);

    private static RedisTokenBucketRateLimiter limiter;

    @BeforeAll
    static void connect() {
        limiter = LettuceRateLimiterClient.connect(
                RedisClientSettings.of(REDIS.getHost(), REDIS.getMappedPort(6379)));
    }

    @AfterAll
    static void disconnect() {
        if (limiter != null) {
            limiter.close();
        }
    }

    @Test
    void concurrentCallersAllowOnlyTheAvailableTokens() throws Exception {
        int capacity = 30;
        int callers = capacity * 2;
        long clientId = nextId();
        long policyId = nextId();
        CountDownLatch gate = new CountDownLatch(callers);
        List<Future<ConsumeResult>> futures = new ArrayList<>();
        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < callers; i++) {
                futures.add(pool.submit(() -> {
                    gate.countDown();
                    if (!gate.await(30, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("timed out waiting to start");
                    }
                    return limiter.tryConsume(
                            clientId,
                            policyId,
                            capacity,
                            SLOW_REFILL_TOKENS,
                            SLOW_REFILL_PERIOD_SECONDS);
                }));
            }
        }

        int allowed = 0;
        int denied = 0;
        for (Future<ConsumeResult> future : futures) {
            ConsumeResult result = future.get(5, TimeUnit.SECONDS);
            if (result.outcome() == Outcome.ALLOWED) {
                allowed++;
            } else if (result.outcome() == Outcome.DENIED) {
                denied++;
            } else {
                fail("Redis became unavailable during the concurrent test");
            }
        }
        assertEquals(capacity, allowed);
        assertEquals(capacity, denied);
    }

    @Test
    void firstRequestStartsFullAndLeavesCapacityMinusOne() throws Exception {
        long clientId = nextId();
        long policyId = nextId();
        ConsumeResult result = limiter.tryConsume(clientId, policyId, 20, 100, 60);

        assertEquals(Outcome.ALLOWED, result.outcome());
        assertEquals(20, result.limit());
        assertEquals(19, result.remaining());
        assertEquals(0, result.retryAfterMs());
        assertTrue(result.resetMs() > 0);

        String key = BucketKeys.bucket(clientId, policyId);
        assertEquals("1", redis("EXISTS", key));
        assertEquals(19.0, Double.parseDouble(redis("HGET", key, "tokens")));
        assertTrue(Long.parseLong(redis("HGET", key, "ts")) > 0);
    }

    @Test
    void burstAllowsCapacityThenDeniesWithoutAFurtherToken() {
        long clientId = nextId();
        long policyId = nextId();
        for (int i = 0; i < 5; i++) {
            ConsumeResult allowed = consumeSlow(clientId, policyId, 5);
            assertEquals(Outcome.ALLOWED, allowed.outcome(), "request " + i);
        }
        ConsumeResult denied = consumeSlow(clientId, policyId, 5);
        assertEquals(Outcome.DENIED, denied.outcome());
        assertEquals(5, denied.limit());
        assertEquals(0, denied.remaining());
        assertTrue(denied.retryAfterMs() > 0);
        assertTrue(denied.resetMs() > 0);
    }

    @Test
    void denyDoesNotWriteTheHashOrRefreshTtl() throws Exception {
        long clientId = nextId();
        long policyId = nextId();
        String key = BucketKeys.bucket(clientId, policyId);
        long now = redisNowMs();
        redis("HSET", key, "tokens", "0.4", "ts", Long.toString(now));
        redis("PEXPIRE", key, "5000");

        String tokensBefore = redis("HGET", key, "tokens");
        String tsBefore = redis("HGET", key, "ts");
        long pttlBefore = Long.parseLong(redis("PTTL", key));

        ConsumeResult denied = consumeSlow(clientId, policyId, 20);
        assertEquals(Outcome.DENIED, denied.outcome());
        assertEquals(0, denied.remaining());
        assertTrue(denied.retryAfterMs() > 0);

        assertEquals(tokensBefore, redis("HGET", key, "tokens"));
        assertEquals(tsBefore, redis("HGET", key, "ts"));
        long pttlAfter = Long.parseLong(redis("PTTL", key));
        assertTrue(pttlAfter > 0 && pttlAfter <= pttlBefore, "pttl " + pttlBefore + " -> " + pttlAfter);
    }

    @Test
    void shortPeriodRefillIsClampedToAFullBucket() throws Exception {
        long clientId = nextId();
        long policyId = nextId();
        int capacity = 5;
        for (int i = 0; i < capacity; i++) {
            assertEquals(Outcome.ALLOWED, limiter.tryConsume(clientId, policyId, capacity, capacity, 1).outcome());
        }
        assertEquals(Outcome.DENIED, limiter.tryConsume(clientId, policyId, capacity, capacity, 1).outcome());

        Thread.sleep(1_600);

        ConsumeResult refilled = limiter.tryConsume(clientId, policyId, capacity, capacity, 1);
        assertEquals(Outcome.ALLOWED, refilled.outcome());
        assertEquals(capacity - 1, refilled.remaining());
    }

    @Test
    void storedTokensAboveALoweredCapacityAreClamped() throws Exception {
        long clientId = nextId();
        long policyId = nextId();
        String key = BucketKeys.bucket(clientId, policyId);
        redis("HSET", key, "tokens", "50", "ts", Long.toString(redisNowMs()));

        ConsumeResult result = consumeSlow(clientId, policyId, 10);
        assertEquals(Outcome.ALLOWED, result.outcome());
        assertEquals(9, result.remaining());
        assertEquals(9.0, Double.parseDouble(redis("HGET", key, "tokens")));
    }

    @Test
    void aFutureTimestampDoesNotAddTokensAndIsRewrittenOnAllow() throws Exception {
        long clientId = nextId();
        long policyId = nextId();
        String key = BucketKeys.bucket(clientId, policyId);
        long future = redisNowMs() + 60_000;
        redis("HSET", key, "tokens", "5", "ts", Long.toString(future));

        ConsumeResult result = consumeSlow(clientId, policyId, 10);
        assertEquals(Outcome.ALLOWED, result.outcome());
        assertEquals(4, result.remaining());

        long storedTs = Long.parseLong(redis("HGET", key, "ts"));
        assertTrue(storedTs < future, "ts was left in the future: " + storedTs);
    }

    @Test
    void allowSetsTtlToTheEmptyToFullIntervalPlusBuffer() throws Exception {
        long clientId = nextId();
        long policyId = nextId();
        ConsumeResult result = limiter.tryConsume(clientId, policyId, 20, 100, 60);
        assertEquals(Outcome.ALLOWED, result.outcome());

        long pttl = Long.parseLong(redis("PTTL", BucketKeys.bucket(clientId, policyId)));
        // ceil(20 * 60_000 / 100) + 1_000 = 13_000
        assertTrue(pttl >= 12_000 && pttl <= 13_000, "pttl=" + pttl);
    }

    @Test
    void scriptFlushStillAllowsTheNextRequest() throws Exception {
        redis("SCRIPT", "FLUSH", "SYNC");
        long clientId = nextId();
        long policyId = nextId();
        ConsumeResult result = limiter.tryConsume(clientId, policyId, 3, 3, 60);
        assertEquals(Outcome.ALLOWED, result.outcome());
        assertEquals(2, result.remaining());
    }

    @Test
    void closedPortReturnsUnavailableWithinTheCommandTimeout() throws Exception {
        int port;
        try (ServerSocket socket = new ServerSocket(0)) {
            port = socket.getLocalPort();
        }
        long started = System.nanoTime();
        try (RedisTokenBucketRateLimiter closed = LettuceRateLimiterClient.connect(
                RedisClientSettings.of("127.0.0.1", port))) {
            ConsumeResult result = closed.tryConsume(1, 1, 5, 5, 60);
            long elapsedMs = Duration.ofNanos(System.nanoTime() - started).toMillis();
            assertEquals(Outcome.UNAVAILABLE, result.outcome());
            assertEquals(0, result.limit());
            assertEquals(0, result.remaining());
            assertEquals(0, result.retryAfterMs());
            assertEquals(0, result.resetMs());
            assertTrue(elapsedMs < 2_000, "elapsedMs=" + elapsedMs);
        }
    }

    @Test
    void distinctClientsAndPoliciesDoNotShareABucket() {
        long clientA = nextId();
        long clientB = nextId();
        long policy = nextId();
        assertEquals(Outcome.ALLOWED, consumeSlow(clientA, policy, 1).outcome());
        assertEquals(Outcome.DENIED, consumeSlow(clientA, policy, 1).outcome());
        assertEquals(Outcome.ALLOWED, consumeSlow(clientB, policy, 1).outcome());

        long client = nextId();
        long policyA = nextId();
        long policyB = nextId();
        assertEquals(Outcome.ALLOWED, consumeSlow(client, policyA, 1).outcome());
        assertEquals(Outcome.DENIED, consumeSlow(client, policyA, 1).outcome());
        assertEquals(Outcome.ALLOWED, consumeSlow(client, policyB, 1).outcome());
    }

    @Test
    void passwordIsSentWhenRedisRequiresOne() {
        try (GenericContainer<?> secured = new GenericContainer<>(DockerImageName.parse("redis:7"))
                .withExposedPorts(6379)
                .withCommand("redis-server", "--requirepass", "s3cret")) {
            secured.start();
            RedisClientSettings good = RedisClientSettings.of(secured.getHost(), secured.getMappedPort(6379), "s3cret");
            try (RedisTokenBucketRateLimiter authed = LettuceRateLimiterClient.connect(good)) {
                assertEquals(Outcome.ALLOWED, authed.tryConsume(nextId(), nextId(), 4, 4, 60).outcome());
            }
            RedisClientSettings bad = RedisClientSettings.of(secured.getHost(), secured.getMappedPort(6379), "wrong");
            try (RedisTokenBucketRateLimiter rejected = LettuceRateLimiterClient.connect(bad)) {
                assertEquals(Outcome.UNAVAILABLE, rejected.tryConsume(nextId(), nextId(), 4, 4, 60).outcome());
            }
        }
    }

    private static ConsumeResult consumeSlow(long clientId, long policyId, int capacity) {
        return limiter.tryConsume(clientId, policyId, capacity, SLOW_REFILL_TOKENS, SLOW_REFILL_PERIOD_SECONDS);
    }

    private static long nextId() {
        return IDS.incrementAndGet();
    }

    private static long redisNowMs() throws Exception {
        String[] lines = redis("TIME").split("\\R");
        long seconds = Long.parseLong(lines[0].trim());
        long micros = Long.parseLong(lines[1].trim());
        return seconds * 1000L + micros / 1000L;
    }

    private static String redis(String... args) throws Exception {
        String[] command = new String[args.length + 1];
        command[0] = "redis-cli";
        System.arraycopy(args, 0, command, 1, args.length);
        org.testcontainers.containers.Container.ExecResult result = REDIS.execInContainer(command);
        if (result.getExitCode() != 0) {
            throw new IllegalStateException(result.getStdout() + result.getStderr());
        }
        return result.getStdout().strip();
    }
}

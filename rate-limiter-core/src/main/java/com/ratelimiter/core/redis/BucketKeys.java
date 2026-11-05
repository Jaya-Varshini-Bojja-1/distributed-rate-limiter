package com.ratelimiter.core.redis;

/**
 * Redis key for one client and one policy. Built from longs so the key cannot
 * pick up extra segments. Braces are omitted because they are Cluster hash tags.
 */
public final class BucketKeys {

    private BucketKeys() {
    }

    public static String bucket(long clientId, long policyId) {
        TokenBucketConstraints.positiveId("clientId", clientId);
        TokenBucketConstraints.positiveId("policyId", policyId);
        return "rl:bucket:" + clientId + ":" + policyId;
    }
}

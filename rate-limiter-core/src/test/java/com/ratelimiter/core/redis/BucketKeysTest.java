package com.ratelimiter.core.redis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class BucketKeysTest {

    @Test
    void formatsClientAndPolicyWithoutHashTagBraces() {
        String key = BucketKeys.bucket(42, 7);
        assertEquals("rl:bucket:42:7", key);
        assertFalse(key.contains("{"));
        assertFalse(key.contains("}"));
    }

    @Test
    void rejectsNonPositiveIds() {
        assertTrue(assertThrows(IllegalArgumentException.class, () -> BucketKeys.bucket(0, 1))
                .getMessage().contains("clientId"));
        assertTrue(assertThrows(IllegalArgumentException.class, () -> BucketKeys.bucket(1, -1))
                .getMessage().contains("policyId"));
    }
}

package com.ratelimiter.config.apikey;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ApiKeyGeneratorTest {

    @Test
    void sha256HexIsLowercaseAndStable() {
        assertEquals(
                "f179b3fed5dee043299e786da9c79d399824be4bac6a85a886448a01c0dd4000",
                ApiKeyGenerator.sha256Hex("rlk_test"));
    }

    @Test
    void generateStoresOnlyAHashAndAShortPrefix() {
        ApiKeyGenerator.GeneratedKey generated = ApiKeyGenerator.generate();

        assertTrue(generated.rawKey().startsWith(ApiKeyGenerator.PREFIX));
        assertEquals(ApiKeyGenerator.DISPLAY_PREFIX_LENGTH, generated.prefix().length());
        assertEquals(generated.rawKey().substring(0, ApiKeyGenerator.DISPLAY_PREFIX_LENGTH), generated.prefix());
        assertEquals(64, generated.hash().length());
        assertTrue(generated.hash().matches("[0-9a-f]{64}"));
        assertEquals(ApiKeyGenerator.sha256Hex(generated.rawKey()), generated.hash());
        assertNotEquals(generated.rawKey(), generated.hash());
    }
}

package com.ratelimiter.config.apikey;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

public final class ApiKeyGenerator {

    public static final String PREFIX = "rlk_";
    public static final int DISPLAY_PREFIX_LENGTH = 12;

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();

    private ApiKeyGenerator() {
    }

    public record GeneratedKey(String rawKey, String prefix, String hash) {
    }

    public static GeneratedKey generate() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String rawKey = PREFIX + ENCODER.encodeToString(bytes);
        return new GeneratedKey(rawKey, rawKey.substring(0, DISPLAY_PREFIX_LENGTH), sha256Hex(rawKey));
    }

    public static String sha256Hex(String rawKey) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(rawKey.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }
}

package com.ratelimiter.config.snapshot;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public final class Etags {

    private Etags() {
    }

    public static String strong(byte[] body) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(body);
            return "\"" + HexFormat.of().formatHex(digest) + "\"";
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    public static boolean noneMatch(String header, String strongEtag) {
        if (header == null || header.isBlank()) {
            return false;
        }
        String expected = unquote(strongEtag);
        if (expected == null) {
            return false;
        }
        for (String part : header.split(",")) {
            String token = part.trim();
            if (token.isEmpty()) {
                continue;
            }
            if ("*".equals(token)) {
                return true;
            }
            if (token.startsWith("W/")) {
                token = token.substring(2).trim();
            }
            if (expected.equals(unquote(token))) {
                return true;
            }
        }
        return false;
    }

    private static String unquote(String token) {
        if (token.length() >= 2 && token.charAt(0) == '"' && token.charAt(token.length() - 1) == '"') {
            return token.substring(1, token.length() - 1);
        }
        return null;
    }
}

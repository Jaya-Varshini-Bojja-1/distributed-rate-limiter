package com.ratelimiter.config.web;

public final class RequestIds {

    private static final ThreadLocal<String> CURRENT = new ThreadLocal<>();

    private RequestIds() {
    }

    public static void set(String requestId) {
        CURRENT.set(requestId);
    }

    public static String current() {
        String requestId = CURRENT.get();
        return requestId == null ? "unknown" : requestId;
    }

    public static void clear() {
        CURRENT.remove();
    }
}

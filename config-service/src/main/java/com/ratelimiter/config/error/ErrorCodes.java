package com.ratelimiter.config.error;

public final class ErrorCodes {

    public static final String VALIDATION_FAILED = "VALIDATION_FAILED";
    public static final String NOT_FOUND = "NOT_FOUND";
    public static final String DUPLICATE = "DUPLICATE";
    public static final String PLAN_IN_USE = "PLAN_IN_USE";
    public static final String DEFAULT_POLICY_PROTECTED = "DEFAULT_POLICY_PROTECTED";
    public static final String POLICY_VERSION_CONFLICT = "POLICY_VERSION_CONFLICT";
    public static final String CLIENT_SUSPENDED = "CLIENT_SUSPENDED";
    public static final String INTERNAL_ERROR = "INTERNAL_ERROR";

    private ErrorCodes() {
    }
}

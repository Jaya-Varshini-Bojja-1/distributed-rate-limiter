package com.ratelimiter.config.policy;

public final class DefaultPolicyRules {

    public static final String DEFAULT_PATH = "/**";

    private DefaultPolicyRules() {
    }

    public static boolean isDefault(String pathPattern, HttpMethodMatch httpMethod) {
        return DEFAULT_PATH.equals(pathPattern) && httpMethod == HttpMethodMatch.ANY;
    }

    public static boolean isProtectedChange(
            String currentPath,
            HttpMethodMatch currentMethod,
            String newPath,
            HttpMethodMatch newMethod,
            boolean newEnabled) {
        return isDefault(currentPath, currentMethod)
                && (!isDefault(newPath, newMethod) || !newEnabled);
    }
}

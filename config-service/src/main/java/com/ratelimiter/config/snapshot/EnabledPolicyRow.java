package com.ratelimiter.config.snapshot;

import com.ratelimiter.config.policy.HttpMethodMatch;

public record EnabledPolicyRow(
        long planId,
        String planCode,
        long policyId,
        String pathPattern,
        String httpMethod,
        int capacity,
        int refillTokens,
        int refillPeriodSeconds) {

    public EnabledPolicyRow(
            Long planId,
            String planCode,
            Long policyId,
            String pathPattern,
            HttpMethodMatch httpMethod,
            Integer capacity,
            Integer refillTokens,
            Integer refillPeriodSeconds) {
        this(
                planId,
                planCode,
                policyId,
                pathPattern,
                httpMethod.name(),
                capacity,
                refillTokens,
                refillPeriodSeconds);
    }
}

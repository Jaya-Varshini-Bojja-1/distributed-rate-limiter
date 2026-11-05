package com.ratelimiter.config.policy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DefaultPolicyRulesTest {

    @Test
    void onlyCatchAllAnyIsTheDefault() {
        assertTrue(DefaultPolicyRules.isDefault("/**", HttpMethodMatch.ANY));
        assertFalse(DefaultPolicyRules.isDefault("/**", HttpMethodMatch.GET));
        assertFalse(DefaultPolicyRules.isDefault("/api/**", HttpMethodMatch.ANY));
    }

    @Test
    void defaultCannotBeDisabledOrRetargeted() {
        assertTrue(DefaultPolicyRules.isProtectedChange(
                "/**", HttpMethodMatch.ANY, "/**", HttpMethodMatch.ANY, false));
        assertTrue(DefaultPolicyRules.isProtectedChange(
                "/**", HttpMethodMatch.ANY, "/api/**", HttpMethodMatch.ANY, true));
        assertTrue(DefaultPolicyRules.isProtectedChange(
                "/**", HttpMethodMatch.ANY, "/**", HttpMethodMatch.GET, true));
        assertFalse(DefaultPolicyRules.isProtectedChange(
                "/**", HttpMethodMatch.ANY, "/**", HttpMethodMatch.ANY, true));
        assertFalse(DefaultPolicyRules.isProtectedChange(
                "/api/**", HttpMethodMatch.GET, "/api/**", HttpMethodMatch.POST, false));
    }
}

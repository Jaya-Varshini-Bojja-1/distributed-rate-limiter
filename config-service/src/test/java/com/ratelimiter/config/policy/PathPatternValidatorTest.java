package com.ratelimiter.config.policy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PathPatternValidatorTest {

    @Test
    void acceptsPatternsThatStartWithSlash() {
        assertTrue(PathPatternValidator.error("/api/orders/**").isEmpty());
        assertTrue(PathPatternValidator.error("/**").isEmpty());
        assertTrue(PathPatternValidator.error("/api/{id}").isEmpty());
    }

    @Test
    void rejectsMissingSlashBlankAndInvalidSyntax() {
        assertEquals("must start with /", PathPatternValidator.error("api/orders").orElseThrow());
        assertEquals("must not be blank", PathPatternValidator.error("  ").orElseThrow());
        assertEquals("invalid path pattern", PathPatternValidator.error("/api/{").orElseThrow());
    }
}

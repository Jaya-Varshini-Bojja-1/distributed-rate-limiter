package com.ratelimiter.config.policy;

import java.util.Optional;

import org.springframework.web.util.pattern.PathPatternParser;

public final class PathPatternValidator {

    private PathPatternValidator() {
    }

    public static Optional<String> error(String pathPattern) {
        if (pathPattern == null || pathPattern.isBlank()) {
            return Optional.of("must not be blank");
        }
        if (!pathPattern.startsWith("/")) {
            return Optional.of("must start with /");
        }
        if (pathPattern.length() > 255) {
            return Optional.of("must be at most 255 characters");
        }
        try {
            new PathPatternParser().parse(pathPattern);
            return Optional.empty();
        } catch (RuntimeException exception) {
            return Optional.of("invalid path pattern");
        }
    }
}

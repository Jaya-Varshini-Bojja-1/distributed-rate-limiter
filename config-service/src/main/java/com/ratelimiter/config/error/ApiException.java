package com.ratelimiter.config.error;

import java.util.List;

import org.springframework.http.HttpStatus;

public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;
    private final List<FieldViolation> errors;

    public ApiException(HttpStatus status, String code, String detail) {
        this(status, code, detail, List.of());
    }

    public ApiException(HttpStatus status, String code, String detail, List<FieldViolation> errors) {
        super(detail);
        this.status = status;
        this.code = code;
        this.errors = errors == null ? List.of() : List.copyOf(errors);
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }

    public List<FieldViolation> getErrors() {
        return errors;
    }

    public static ApiException notFound(String detail) {
        return new ApiException(HttpStatus.NOT_FOUND, ErrorCodes.NOT_FOUND, detail);
    }

    public static ApiException validation(String field, String message) {
        return new ApiException(
                HttpStatus.BAD_REQUEST,
                ErrorCodes.VALIDATION_FAILED,
                "Request validation failed.",
                List.of(new FieldViolation(field, message)));
    }
}

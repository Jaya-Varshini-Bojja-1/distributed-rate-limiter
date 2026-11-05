package com.ratelimiter.config.error;

import java.net.URI;
import java.sql.SQLException;
import java.util.List;

import com.ratelimiter.config.web.RequestIds;

import jakarta.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private static final String UNIQUE_VIOLATION = "23505";
    private static final String CHECK_VIOLATION = "23514";

    @ExceptionHandler(ApiException.class)
    public ProblemDetail handleApi(ApiException exception, HttpServletRequest request) {
        return problem(exception.getStatus(), exception.getCode(), exception.getMessage(), request, exception.getErrors());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException exception, HttpServletRequest request) {
        List<FieldViolation> errors = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> new FieldViolation(error.getField(), error.getDefaultMessage()))
                .toList();
        return problem(
                HttpStatus.BAD_REQUEST,
                ErrorCodes.VALIDATION_FAILED,
                "Request validation failed.",
                request,
                errors);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail handleUnreadable(HttpMessageNotReadableException exception, HttpServletRequest request) {
        return problem(
                HttpStatus.BAD_REQUEST,
                ErrorCodes.VALIDATION_FAILED,
                "Request validation failed.",
                request,
                List.of());
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ProblemDetail handleOptimisticLock(OptimisticLockingFailureException exception, HttpServletRequest request) {
        return problem(
                HttpStatus.CONFLICT,
                ErrorCodes.POLICY_VERSION_CONFLICT,
                "This policy was changed by someone else. Reload it.",
                request,
                List.of());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail handleIntegrity(DataIntegrityViolationException exception, HttpServletRequest request) {
        String sqlState = sqlState(exception);
        if (UNIQUE_VIOLATION.equals(sqlState)) {
            return problem(
                    HttpStatus.CONFLICT,
                    ErrorCodes.DUPLICATE,
                    "The request conflicts with an existing record.",
                    request,
                    List.of());
        }
        if (CHECK_VIOLATION.equals(sqlState)) {
            return problem(
                    HttpStatus.BAD_REQUEST,
                    ErrorCodes.VALIDATION_FAILED,
                    "Request validation failed.",
                    request,
                    List.of());
        }
        log.error("Data integrity violation requestId={}", RequestIds.current(), exception);
        return problem(
                HttpStatus.CONFLICT,
                ErrorCodes.DUPLICATE,
                "The request conflicts with the current data.",
                request,
                List.of());
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception exception, HttpServletRequest request) {
        log.error("Unhandled error requestId={}", RequestIds.current(), exception);
        return problem(
                HttpStatus.INTERNAL_SERVER_ERROR,
                ErrorCodes.INTERNAL_ERROR,
                "An unexpected error occurred.",
                request,
                List.of());
    }

    private static ProblemDetail problem(
            HttpStatus status,
            String code,
            String detail,
            HttpServletRequest request,
            List<FieldViolation> errors) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(status.getReasonPhrase());
        problem.setType(URI.create("about:blank"));
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("code", code);
        problem.setProperty("requestId", RequestIds.current());
        if (errors != null && !errors.isEmpty()) {
            problem.setProperty("errors", errors);
        }
        return problem;
    }

    private static String sqlState(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof SQLException sqlException && sqlException.getSQLState() != null) {
                return sqlException.getSQLState();
            }
            current = current.getCause();
        }
        return null;
    }
}
